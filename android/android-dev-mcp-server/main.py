import argparse
import io
import os
import shutil
import subprocess
import shlex
import xml.etree.ElementTree as ET
from mcp.server.fastmcp import FastMCP, Image
from mcp.server.fastmcp.exceptions import ToolError
from PIL import Image as PILImage
from pydantic import Field


def resolve_adb_path() -> str:
    """Resolve adb without relying on the caller's shell PATH."""

    candidates: list[str] = []

    adb_on_path = shutil.which("adb")
    if adb_on_path:
        candidates.append(adb_on_path)

    for sdk_root in (os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME")):
        if sdk_root:
            candidates.append(os.path.join(os.path.expanduser(sdk_root), "platform-tools", "adb"))

    candidates.append(os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))

    seen: set[str] = set()
    for candidate in candidates:
        normalized = os.path.abspath(candidate)
        if normalized in seen:
            continue
        seen.add(normalized)
        if os.path.isfile(normalized) and os.access(normalized, os.X_OK):
            return normalized

    raise ToolError(
        "adb was not found. Install Android SDK platform-tools or set ANDROID_SDK_ROOT/ANDROID_HOME."
    )

def parse_args() -> tuple[str, str, int]:
    parser = argparse.ArgumentParser(description="Android Development MCP Server")
    parser.add_argument(
        "--mode",
        dest="mode",
        type=str,
        choices=["stdio", "streamable-http", "sse"],
        required=True,
        help="The mode to run the MCP server in",
    )
    parser.add_argument(
        "--temp-dir",
        dest="temp_dir",
        type=str,
        required=True,
        help="Path to a temporary directory for the MCP server",
    )
    parser.add_argument(
        "--port",
        dest="port",
        type=int,
        default=3001,
        help="The port to run the MCP server on",
    )
    args = parser.parse_args()
    temp_dir = os.path.abspath(os.path.expanduser(args.temp_dir))
    os.makedirs(temp_dir, exist_ok=True)
    return args.mode, temp_dir, args.port

def call_adb_silent(args: list[str]):
    """Helper function to call adb with common arguments when we don't need to handle the output."""

    # Run adb with some arguments. Discard both standard output (stdout=subprocess.DEVNULL)
    # and standard error (stderr=subprocess.DEVNULL). Return a CalledProcessError exception
    # if adb returns a non-zero exit code (check=True).
    subprocess.run([resolve_adb_path()] + args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def start_server(mode: str, temp_dir: str, port: int):
    mcp = FastMCP(
        name="Android Development MCP Server",
        port=port,
    )

    @mcp.tool(structured_output=True)
    def get_logcat_output(
        app_package: str = Field(description="The base package of the app to get the logs from"),
        log_level: str = Field(description="The log level to filter the events. Possible values: DEBUG, WARNING, ERROR.", default="DEBUG")
    ) -> str:
        """Retrieves the last 100 lines of logs from the connected Android device using logcat."""

        if not app_package:
            raise ToolError("The 'app_package' argument cannot be empty.")

        try:
            # Retrieve a single process ID (pidof -s) for the specified app package.
            # Capture both standard output and standard error (capture_output=True)
            # as text (text=True).
            adb_path = resolve_adb_path()
            process = subprocess.run([adb_path, "shell", "pidof", "-s", app_package], capture_output=True, text=True)
            pid = process.stdout.strip()
            if process.returncode != 0 or not pid:
                raise ToolError(f"App with package '{app_package}' not running or not found.")            
            
            log_level_map = {
                "DEBUG": "D",
                "WARNING": "W",
                "ERROR": "E",
            }

            if log_level not in log_level_map:
                raise ToolError(f"Invalid log level: {log_level}. Possible values: DEBUG, WARNING, ERROR.")

            log_level_char = log_level_map[log_level]

            # Run logcat returning immediately a dump of the current logs (-d)
            # for the default log buffer (-b default). Return only the logs that
            # are at the specified log level or above (*:{log_level_char}).
            result = subprocess.run([adb_path, "logcat", "-d", "-b", "default", f"*:{log_level_char}"], capture_output=True, text=True, check=True)

            filtered_lines = [
                line
                for line in result.stdout.splitlines()
                if pid in line
            ]

            return "\n".join(filtered_lines[-100:])
        except subprocess.CalledProcessError as e:
            raise ToolError(f"Error getting logcat output: {e.stderr}")
        except ToolError as e:
            raise e
        except Exception as e:
            raise ToolError(f"Error getting logcat output: {e}")
        
    @mcp.tool()
    def get_screenshot() -> Image:
        """
        Gets a screenshot of the connected Android device.
        Use this tool when you need to check the visual appearance of the UI.
        Prefer using the get_ui_dump tool to identify the UI elements on the screen and their position.
        """
        try:
            screenshot_path = os.path.join(temp_dir, "screenshot.png")

            # Execute adb commands to capture as PNG (-p), pull, and remove the screenshot
            call_adb_silent(["shell", "screencap", "-p", "/sdcard/screenshot.png"])
            call_adb_silent(["pull", "/sdcard/screenshot.png", screenshot_path])
            call_adb_silent(["shell", "rm", "/sdcard/screenshot.png"])

            # Open the screenshot and resize it
            scale_factor = 0.4
            with PILImage.open(screenshot_path) as img:
                new_width = int(img.width * scale_factor)
                new_height = int(img.height * scale_factor)
                resized_img = img.resize((new_width, new_height))

                # Save the resized image to a byte buffer
                buffered = io.BytesIO()
                resized_img.save(buffered, format="PNG")
                img_bytes = buffered.getvalue()

            # Clean up the local screenshot file
            os.remove(screenshot_path)

            return Image(data=img_bytes, format="png")
        except Exception as e:
            raise ToolError(f"Error getting screenshot: {e}")
        
    @mcp.tool(structured_output=True)
    def get_ui_dump(
        returned_attributes: str = Field(description="A comma-separated list of attributes to return for each XML node. "
                                            "Possible attributes: index, text, resource-id, class, package, "
                                            "content-desc, checkable, checked, clickable, enabled, focusable, "
                                            "focused, scrollable, long-clickable, password, selected, bounds, "
                                            "drawing-order, hint.")
    ) -> str:
        """
        Gets the UI hierarchy dump from the connected Android device using an XML format.
        This is useful to understand the components currently visible on the screen.
        For each node in the XML, only the attributes specified in the 'returned_attributes' argument are returned.
        """

        if not returned_attributes:
            raise ToolError("The 'returned_attributes' argument cannot be empty.")

        attributes_to_keep = [attr.strip() for attr in returned_attributes.split(',')]

        possible_attributes = {
            "index", "text", "resource-id", "class", "package", "content-desc",
            "checkable", "checked", "clickable", "enabled", "focusable", "focused",
            "scrollable", "long-clickable", "password", "selected", "bounds",
            "drawing-order", "hint"
        }

        invalid_attributes = [attr for attr in attributes_to_keep if attr not in possible_attributes]
        if invalid_attributes:
            raise ToolError(f"Invalid attribute(s) requested: {', '.join(invalid_attributes)}. "
                            f"Possible attributes are: {', '.join(possible_attributes)}")
        
        try:
            dump_path = os.path.join(temp_dir, "window_dump.xml")

            # Execute the adb commands to generate, pull and remove the UI hierarchy dump
            call_adb_silent(["shell", "uiautomator", "dump"])
            call_adb_silent(["pull", "/sdcard/window_dump.xml", dump_path])
            call_adb_silent(["shell", "rm", "/sdcard/window_dump.xml"])

            # Read the content of the dump file
            with open(dump_path, "r", encoding="utf-8") as f:
                ui_dump = f.read()

            # Clean up the local dump file
            os.remove(dump_path)

            # Parse the XML
            root = ET.fromstring(ui_dump)
            
            # Iterate over all nodes and remove unwanted attributes
            for node in root.iter():
                attributes = list(node.attrib)
                for attr in attributes:
                    if attr not in attributes_to_keep:
                        del node.attrib[attr]

            # Return the modified XML as a string
            result = ET.tostring(root, encoding="unicode")
        
            return result
        except Exception as e:
            raise ToolError(f"Error getting UI dump: {e}")
    
    @mcp.tool(structured_output=True)
    def tap_screen(
        x: int = Field(description="The x-coordinate of the point to tap"),
        y: int = Field(description="The y-coordinate of the point to tap")
    ) -> str:
        """
        Taps on the screen of the connected Android device at the given coordinates.
        This tool is useful also to give the focus to edit boxes that accept text input.
        """
        try:
            call_adb_silent(["shell", "input", "tap", str(x), str(y)])
            return f"Tapped at ({x}, {y})"
        except Exception as e:
            raise ToolError(f"Error tapping on screen: {e}")
    
    @mcp.tool(structured_output=True)
    def swipe_screen(
        x1: int = Field(description="The x-coordinate of the starting point of the swipe"),
        y1: int = Field(description="The y-coordinate of the starting point of the swipe"),
        x2: int = Field(description="The x-coordinate of the ending point of the swipe"),
        y2: int = Field(description="The y-coordinate of the ending point of the swipe")
    ) -> str:
        """Swipes on the screen of the connected Android device from a starting point to an ending point."""
        try:
            call_adb_silent(["shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2)])
            return f"Swiped from ({x1}, {y1}) to ({x2}, {y2})"
        except Exception as e:
            raise ToolError(f"Error swiping on screen: {e}")
    
    @mcp.tool(structured_output=True)
    def send_text(
        text_to_send: str = Field(description="The text to send")
    ) -> str:
        """Sends the given text to the connected Android device, as if it were typed on a keyboard."""

        if not text_to_send:
            raise ToolError("The 'text_to_send' argument cannot be empty.")

        try:
            # Escape the text to be sent to handle special characters and spaces
            escaped_text = shlex.quote(text_to_send)
            call_adb_silent(["shell", "input", "text", escaped_text])
            return f"Sent text: {text_to_send}"
        except Exception as e:
            raise ToolError(f"Error sending text: {e}")
    
    @mcp.tool(structured_output=True)
    def perform_system_action(
        action: str = Field(description="The system action to perform. Possible values: "
                            "BACK (press the system back button), "
                            "HOME (go to the home screen), "
                            "RECENT_APPS (open the recent apps view).")
    ) -> str:
        """Performs a system action on the connected Android device, such as pressing the back button, going to the home screen, or opening the recent apps view."""
        
        # Map each input action to the corresponding keycode recognized by the 'keyevent' command
        action_map = {
            "BACK": "KEYCODE_BACK",
            "HOME": "KEYCODE_HOME",
            "RECENT_APPS": "KEYCODE_RECENT_APPS",
        }

        if action not in action_map:
            raise ToolError(f"Invalid action: {action}. Possible actions: BACK, HOME, RECENT_APPS.")

        action_value = action_map[action]

        try:
            call_adb_silent(["shell", "input", "keyevent", action_value])
            return f"Performed action: {action}"
        except Exception as e:
            raise ToolError(f"Error performing action {action}: {e}")
    
    if mode == "stdio":
        print("Running in stdio mode")
        mcp.run(transport="stdio")
    elif mode == "streamable-http":
        print(f"Running in streamable-http mode on http://localhost:{port}/mcp")
        mcp.run(transport="streamable-http")
    elif mode == "sse":
        print(f"Running in SSE mode on http://localhost:{port}/sse")
        mcp.run(transport="sse")
    else:
        print(f"Unsupported mode {mode}")

if __name__ == "__main__":
    mode, temp_dir, port = parse_args()
    start_server(mode, temp_dir, port)
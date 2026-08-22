import requests
import json
import re

def fetch_and_inspect():
    url = "https://www.cricbuzz.com/cricket-match/live-scores"
    headers = {
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36"
    }
    
    try:
        response = requests.get(url, headers=headers)
        html = response.text
        
        print(f"Fetched {len(html)} bytes")
        
        # Regex to find the push calls
        # self.__next_f.push([1,"..."])
        # The content inside the string is what we want.
        
        # A simple regex might miss nested structures, but let's try to find the specific pattern used by Next.js
        # It usually looks like: self.__next_f.push([1,"4:I[...]\n"])
        
        # Let's just split by the marker and try to parse manually like the Kotlin code does, 
        # or use a regex to capture the array content.
        
        matches_found = []
        
        parts = html.split('self.__next_f.push(')
        for part in parts[1:]:
            # The part starts with the arguments to push. 
            # We need to find the end of the function call `)`
            # This is tricky with simple split, but let's try to find the JSON array.
            
            try:
                # It usually starts with `[`
                if not part.strip().startswith('['):
                    continue
                
                # Find the closing `)`
                # This is a naive parser, assuming the JSON doesn't contain `)\n` or similar that breaks this.
                # But since it's a script tag, it usually ends with `)` or `);`
                
                # Let's try to extract the string inside the array.
                # [1,"..."]
                
                # Find the first quote
                first_quote = part.find('"')
                if first_quote == -1: 
                    continue
                
                # The string content is escaped JSON.
                # We need to extract the string literal.
                
                # Let's try a different approach: Regex for the specific structure
                # self.__next_f.push([1,"<JSON_STRING>"])
                pass
            except Exception as e:
                pass

        # Let's try a more robust regex to just find the big JSON strings that look like they contain matchInfo
        # The Kotlin code parses the structure: [1, "string"]
        
        pattern = re.compile(r'self\.__next_f\.push\(\[(?:[^,]+),"(.*?)"\]\)')
        
        # This regex is tricky because of escaped quotes in the string.
        # Let's iterate manually like the Kotlin code.
        
        start_marker = "self.__next_f.push("
        start_index = 0
        
        while True:
            idx = html.find(start_marker, start_index)
            if idx == -1:
                break
            
            open_paren_idx = idx + len(start_marker) - 1
            
            # Find matching closing paren
            paren_count = 0
            in_string = False
            escape = False
            end_idx = -1
            
            for i in range(open_paren_idx, len(html)):
                char = html[i]
                
                if escape:
                    escape = False
                    continue
                
                if char == '\\':
                    escape = True
                    continue
                
                if char == '"':
                    in_string = not in_string
                    continue
                
                if not in_string:
                    if char == '(':
                        paren_count += 1
                    elif char == ')':
                        paren_count -= 1
                        if paren_count == 0:
                            end_idx = i
                            break
            
            if end_idx != -1:
                script_content = html[open_paren_idx+1:end_idx]
                try:
                    # script_content should be `[1,"..."]`
                    json_element = json.loads(script_content)
                    if isinstance(json_element, list) and len(json_element) > 1 and isinstance(json_element[1], str):
                        inner_json_str = json_element[1]
                        
                        # Clean up prefix
                        colon_index = inner_json_str.find(':')
                        if colon_index != -1 and colon_index < 10:
                            inner_json_str = inner_json_str[colon_index+1:]
                        
                        if inner_json_str.startswith("I"):
                            inner_json_str = inner_json_str[1:]
                            
                        try:
                            data = json.loads(inner_json_str)
                            find_matches_in_json(data, matches_found)
                        except json.JSONDecodeError:
                            pass
                except json.JSONDecodeError:
                    pass
                
                start_index = end_idx + 1
            else:
                start_index = idx + 1

        print(f"\nFound {len(matches_found)} matches.")
        print("-" * 80)
        print(f"{'State':<20} | {'Status':<30} | {'Title'}")
        print("-" * 80)
        
        # Sort by state to group them
        matches_found.sort(key=lambda x: x['state'])
        
        for m in matches_found:
            print(f"{m['state']:<20} | {m['status']:<30} | {m['title']}")

    except Exception as e:
        print(f"Error: {e}")

def find_matches_in_json(element, matches):
    if isinstance(element, dict):
        if "matchInfo" in element:
            try:
                match_info = element["matchInfo"]
                team1 = match_info.get("team1", {}).get("teamName", "Unknown")
                team2 = match_info.get("team2", {}).get("teamName", "Unknown")
                matches.append({
                    "matchId": match_info.get("matchId"),
                    "state": match_info.get("state", "N/A"),
                    "status": match_info.get("status", "N/A"),
                    "title": f"{team1} vs {team2}"
                })
            except Exception:
                pass
        
        for key in element:
            find_matches_in_json(element[key], matches)
            
    elif isinstance(element, list):
        for item in element:
            find_matches_in_json(item, matches)

if __name__ == "__main__":
    fetch_and_inspect()

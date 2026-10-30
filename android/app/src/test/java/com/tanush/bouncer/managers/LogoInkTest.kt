package com.tanush.bouncer.managers

import androidx.compose.ui.graphics.toArgb
import com.tanush.bouncer.ui.theme.v2.DarkSurfaceVariant
import com.tanush.bouncer.ui.theme.v2.Gray100
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The crest-reversal map and the plates it was measured against.
 *
 * `tools/team_sources.py` decides which crests vanish by testing them against TeamCrest's two
 * `surfaceVariant` plates. If a plate colour changes here and not there, the tool goes on judging
 * crests against a plate the app no longer draws, so the two are pinned together. The script is a
 * declared Gradle test input, so editing it re-runs this test.
 */
class LogoInkTest {

    @Test
    fun `dark, light and both parse and anything else is dropped`() {
        assertEquals(
            mapOf("a.png" to LogoInk.Dark, "b.png" to LogoInk.Light, "e.png" to LogoInk.Both),
            parseLogoInks(
                mapOf("a.png" to "dark", "b.png" to "light", "c.png" to "unreadable", "d.png" to "", "e.png" to "both"),
            ),
        )
    }

    @Test
    fun `the tool measures crests against the plates the app draws`() {
        val script = locate("tools/team_sources.py").readText()
        assertEquals(rgbOf(Gray100.toArgb()), plate(script, "LIGHT_PLATE"))
        assertEquals(rgbOf(DarkSurfaceVariant.toArgb()), plate(script, "DARK_PLATE"))
    }

    private fun rgbOf(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    private fun plate(script: String, name: String): Triple<Int, Int, Int> {
        val match = checkNotNull(
            Regex("""^$name\s*=\s*\(0x(\w{2}),\s*0x(\w{2}),\s*0x(\w{2})\)""", RegexOption.MULTILINE).find(script)
        ) { "$name not found in tools/team_sources.py" }
        val (r, g, b) = match.destructured
        return Triple(r.toInt(16), g.toInt(16), b.toInt(16))
    }

    /** Walks up from the module directory; a missing file is a hard failure, never a skip. */
    private fun locate(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("could not find $relativePath by walking up from ${System.getProperty("user.dir")}")
    }
}

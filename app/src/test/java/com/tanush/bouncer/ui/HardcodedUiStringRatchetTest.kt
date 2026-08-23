package com.tanush.bouncer.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A ratchet on user-facing string literals in the Compose UI. The count may fall; it may not rise.
 *
 * Android lint's `HardcodedText` does **not** cover this. It inspects `android:text` in XML
 * layouts, and firing it against `Text("Live")` in Compose was assumed and then tested: a probe
 * literal added to a live screen passed `lintDebug` cleanly. There is no built-in check, which is
 * how the app reached 712 literals across 95 files with 25 `R.string` ids and two
 * `stringResource` calls — English-only by construction rather than by decision, in an app whose
 * largest audience is the subcontinent.
 *
 * This is deliberately a ratchet rather than a threshold of zero. Extracting 712 strings is a
 * screen-by-screen project; what matters in the meantime is that the number cannot grow while it
 * is being worked through. Lower [CEILING] whenever a screen is extracted — that is the point.
 *
 * `ui/widgets/` is excluded: those are RemoteViews and Glance surfaces, which cannot read Compose
 * resources and are a separate problem with a separate fix (a resource-based token set).
 *
 * The count is a floor, not a census: the pattern matches named arguments (`title = "…"`) and not
 * positional ones (`Text("…")`), so the real number is higher. That is fine for a ratchet — it
 * still cannot rise — but do not read it as "699 strings left to extract".
 */
class HardcodedUiStringRatchetTest {

    /**
     * Current count. Only ever revise this **downward**.
     *
     * Raising it to make a build pass re-opens the exact hole this test exists to close.
     */
    private val ceiling = 666

    private val uiParameter = Regex(
        """\b(?:text|title|subtitle|label|placeholder|contentDescription)\s*=\s*"([^"]{2,})""""
    )

    @Test
    fun `hardcoded user-facing strings do not increase`() {
        val root = locate("app/src/main/kotlin/com/tanush/bouncer/ui")
        val counts = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("/widgets/") }
            .associate { it.name to uiParameter.findAll(it.readText()).count() }
            .filterValues { it > 0 }

        val total = counts.values.sum()
        val worst = counts.entries.sortedByDescending { it.value }.take(5)
            .joinToString(", ") { "${it.key}=${it.value}" }

        assertTrue(
            "Hardcoded UI strings rose to $total (ceiling $ceiling). Worst files: $worst. " +
                "Use stringResource(R.string.…) for new user-facing text. If you extracted " +
                "strings, LOWER the ceiling in this test — never raise it.",
            total <= ceiling,
        )
    }

    /**
     * Resolved by walking up from the test's working directory. A missing directory is a hard
     * failure, never a skipped test: a ratchet that quietly stops counting still reads as green.
     */
    private fun locate(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("could not find $relativePath from ${System.getProperty("user.dir")}")
    }
}

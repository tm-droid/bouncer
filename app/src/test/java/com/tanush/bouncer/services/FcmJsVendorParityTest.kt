package com.tanush.bouncer.services

import com.tanush.bouncer.managers.matchTopic
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two copies of `fcm.js` must stay byte-identical, and the device's [matchTopic] must build the
 * same topic name the server sends to.
 *
 * `match-sync` sends wicket/boundary/milestone/result alerts and `poll-live-matches` sends the start,
 * each from its own copy (a deployment contains only its own directory). A drift in `matchTopic`
 * would be completely silent: the device subscribes to one name, the server sends to another, and
 * nothing errors anywhere — which is the exact state "notifications never arrive" was in before.
 *
 * Both paths are declared as test-task inputs in `app/build.gradle.kts`, or an edit outside the
 * module leaves this task UP-TO-DATE.
 */
class FcmJsVendorParityTest {

    private val canonicalPath = "appwrite/functions/match-sync/src/fcm.js"
    private val vendoredPath = "appwrite/functions/poll-live-matches/src/fcm.js"

    @Test
    fun `both copies are found and look like the fcm module`() {
        for (path in listOf(canonicalPath, vendoredPath)) {
            val text = read(path)
            assertTrue("$path does not look like fcm.js", text.contains("export function matchTopic"))
        }
    }

    @Test
    fun `the vendored copy is byte-identical`() {
        assertEquals(
            "fcm.js drifted between $canonicalPath and $vendoredPath. Copy the canonical file over " +
                "the other — do not delete this check.",
            read(canonicalPath),
            read(vendoredPath),
        )
    }

    @Test
    fun `the server's topic rule is the one the device mirrors`() {
        // Pinned against the exact JS expression, so a change to either side fails here.
        assertTrue(
            "matchTopic in fcm.js changed; update MatchAlertPrefs.matchTopic to match, then this test",
            read(canonicalPath).contains("return `match_\${String(matchId).replace(/[^a-zA-Z0-9-_.~%]/g, \"\")}`;"),
        )
        assertEquals("match_161777", matchTopic("161777"))
        assertEquals("match_abc", matchTopic("a/b c"))
        assertEquals("match_x-y_z.~%", matchTopic("x-y_z.~%"))
        assertEquals("match_", matchTopic("é"))
    }

    private fun read(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        error("could not find $relativePath from ${System.getProperty("user.dir")} — do not delete this check")
    }
}

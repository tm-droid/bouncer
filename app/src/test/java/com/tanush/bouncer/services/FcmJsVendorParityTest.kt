package com.tanush.bouncer.services

import com.tanush.bouncer.managers.matchTopic
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device's [matchTopic] must build the same topic name the server sends to.
 *
 * `match-sync` sends every match alert — wicket, boundary, milestone, result, and (since the
 * match-list poll moved into it on 2026-09-30) the start — from its `fcm.js`. There used to be a
 * second copy in `poll-live-matches`, byte-compared here; with the function merged there is one.
 * A drift in `matchTopic` would be completely silent: the device subscribes to one name, the server
 * sends to another, and nothing errors anywhere — which is the exact state "notifications never
 * arrive" was in before.
 *
 * The path is declared as a test-task input in `app/build.gradle.kts`, or an edit outside the
 * module leaves this task UP-TO-DATE.
 */
class FcmJsVendorParityTest {

    private val canonicalPath = "appwrite/functions/match-sync/src/fcm.js"

    @Test
    fun `the server copy is found and looks like the fcm module`() {
        assertTrue("$canonicalPath does not look like fcm.js", read(canonicalPath).contains("export function matchTopic"))
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

    @Test
    fun `the device ages a push out on the server's own figures`() {
        val js = read(canonicalPath)
        // Pinned against the exact JS, so a change to either side fails here.
        assertTrue("ALERT_TTL_SECONDS changed in fcm.js", js.contains("export const ALERT_TTL_SECONDS = 300;"))
        assertTrue(
            "TTL_SECONDS_BY_TYPE changed in fcm.js",
            js.contains("const TTL_SECONDS_BY_TYPE = { match_end: 6 * 60 * 60, match_start: 30 * 60 };"),
        )
        assertEquals(300_000L, alertMaxAgeMs(MatchAlertType.WICKET))
        assertEquals(300_000L, alertMaxAgeMs(MatchAlertType.BOUNDARY))
        assertEquals(300_000L, alertMaxAgeMs(MatchAlertType.MILESTONE))
        assertEquals(30 * 60 * 1000L, alertMaxAgeMs(MatchAlertType.MATCH_START))
        assertEquals(6 * 60 * 60 * 1000L, alertMaxAgeMs(MatchAlertType.MATCH_END))
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

package com.tanush.bouncer.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the set of SharedPreferences files the app opens.
 *
 * This exists because the same key name living in two different preference files is invisible in
 * review and silent at runtime, and the app shipped two instances of it:
 *
 *  - `haptic_feedback` was written by `PreferencesHelper` into `bouncer_prefs` and read by a
 *    second facade out of `bouncer_preferences`. The Settings toggle changed nothing anywhere.
 *  - `muted_matches` existed in three files. The notification's Mute action wrote one of them;
 *    the live-notification suppression check read a file that nothing had ever written.
 *
 * Neither is a crash, neither shows up in a build, and neither is findable by searching for a key
 * name — the key names *matched*. What differed was the file. So the file set is what gets pinned:
 * adding a preference file is now a deliberate act that fails this test until the author writes it
 * down here, which is the moment to ask whether an existing file already holds these keys.
 *
 * The test resolves constants, not just literals. A guard that silently skipped
 * `getSharedPreferences(PREFS_NAME, …)` would pass while missing most of the call sites, so an
 * unresolvable argument is a failure rather than a skip.
 */
class SharedPreferencesFileInventoryTest {

    /**
     * Every preference file the app is allowed to open. Add an entry only after checking that no
     * existing file already owns the keys you are about to write.
     */
    private val expected = setOf(
        "bouncer_achievements",
        "bouncer_countdown_widget",
        "bouncer_glance_widget",
        "bouncer_lock_widget",
        "bouncer_match_notif_prefs",
        "bouncer_mute_prefs",
        "bouncer_notification_prefs",
        "bouncer_prefs",
        "bouncer_reminders",
        "bouncer_search_prefs",
        "devtools_prefs",
        "favourite_teams",
        "match_cache_disk",
        "match_subscriptions",
        "notification_inbox",
        "score_polling_cache",
        "widget_cache",
        "widget_favorites",
        "widget_muted",
        "widget_prefs",
    )

    private val callSite = Regex("""getSharedPreferences\(\s*([^,)]+)\s*,""")
    private val literalConst = Regex("""val\s+([A-Z][A-Z0-9_]*)\s*(?::\s*String\s*)?=\s*"([^"]+)"""")
    private val aliasConst = Regex("""val\s+([A-Z][A-Z0-9_]*)\s*=\s*([A-Za-z_]+(?:\.[A-Za-z_]+)+)""")

    @Test
    fun `preference files are the ones this test knows about`() {
        val sources = locate("app/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.nameWithoutExtension to it.readText().withoutComments() }

        val scan = Scan(sources)

        assertTrue(
            "Could not resolve these getSharedPreferences arguments to a file name, so this " +
                "guard cannot see those call sites: ${scan.unresolved}. Give the name a " +
                "`const val` (ideally in PrefsRegistry) so it resolves.",
            scan.unresolved.isEmpty(),
        )
        assertEquals(
            "The set of SharedPreferences files changed. Before adding one, check whether an " +
                "existing file already holds these keys — two files with the same key names is " +
                "the failure this test exists to catch, and it is silent at runtime.",
            expected.toSortedSet(),
            scan.found,
        )
    }

    /**
     * Resolves every `getSharedPreferences` argument in [sources] to the file name it names.
     *
     * Constants are looked up **per file**, because the same constant name holds different values
     * in different files: `PREF_NAME` is `bouncer_glance_widget` in one widget and
     * `favourite_teams` in a manager. A flat name-to-value map would collapse those and report a
     * file set that is wrong but green.
     */
    private inner class Scan(sources: Map<String, String>) {
        private val literals = sources.mapValues { (_, text) ->
            literalConst.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        }
        private val aliases = sources.mapValues { (_, text) ->
            aliasConst.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        }

        val found = sortedSetOf<String>()
        val unresolved = mutableListOf<String>()

        init {
            sources.forEach { (fileName, text) ->
                callSite.findAll(text).forEach { match ->
                    val argument = match.groupValues[1].trim()
                    val resolved = resolveArgument(fileName, argument)
                    if (resolved == null) unresolved += "$fileName: $argument" else found += resolved
                }
            }
        }

        private fun resolveArgument(fileName: String, argument: String): String? = when {
            argument.startsWith("\"") -> argument.trim('"')
            argument.contains('.') ->
                resolve(argument.substringBeforeLast('.'), argument.substringAfterLast('.'))
            else -> resolve(fileName, argument)
        }

        private fun resolve(owner: String, name: String, depth: Int = 0): String? {
            if (depth > MAX_ALIAS_DEPTH) return null
            literals[owner]?.get(name)?.let { return it }
            aliases[owner]?.get(name)?.let { target ->
                return resolve(
                    target.substringBeforeLast('.'), target.substringAfterLast('.'), depth + 1,
                )
            }
            // Outside the declaring file, accept a name only when it is unambiguous across the
            // whole source tree. Guessing would resolve `PREF_NAME` to whichever widget the file
            // walk happened to reach first.
            return literals.values.mapNotNull { it[name] }.toSet().singleOrNull()
        }
    }

    /**
     * Comments are stripped before scanning: `PreferencesHelper`'s KDoc quotes
     * `context.getSharedPreferences(...)`, which is prose, not a call site.
     */
    private fun String.withoutComments(): String =
        replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?<!:)//[^\n]*"""), "")

    private fun locate(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("could not find $relativePath from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val MAX_ALIAS_DEPTH = 2
    }
}

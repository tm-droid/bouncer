package com.tanush.bouncer.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tanush.bouncer.utils.PrefsRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The half of the DataStore move that can lose user data.
 *
 * `SharedPreferencesMigration` copies `bouncer_prefs` across on first access and then **deletes**
 * the XML. Every consumer of that file had to move in the same change — `PreferencesHelper`,
 * `RemindersManager`, `AppwriteReadGuard.PrefsBreakerStore`, `PrefsHighlightsSyncMarker`. One left
 * behind would not fail; it would quietly start reading defaults, which for the read guard means a
 * live 402 latch resetting itself on upgrade.
 *
 * This needs a real `Context`, so it cannot run in the JVM suite alongside
 * `BouncerPreferencesTest`. Run it before shipping a build that carries the migration:
 *   ./gradlew connectedDebugAndroidTest --tests "*BouncerPreferencesMigrationTest*"
 */
@RunWith(AndroidJUnit4::class)
class BouncerPreferencesMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearStores() {
        // BouncerApp.onCreate has already initialized and warmed the store in this process, and
        // DataStore evaluates its migrations once per instance on that instance's first read. Both
        // stores are wiped and the singleton dropped, so `initialize` below genuinely rebuilds —
        // otherwise the test writes to SharedPreferences after the migration window has closed and
        // asserts against a store that was never asked to migrate.
        PreferenceStore.resetForTest()
        context.deleteSharedPreferences(PrefsRegistry.PREFS_APP)
        File(context.filesDir, "datastore/${PrefsRegistry.PREFS_APP}.preferences_pb").delete()
    }

    @Test
    fun everyTypeSurvivesTheMigration() {
        context.getSharedPreferences(PrefsRegistry.PREFS_APP, Context.MODE_PRIVATE).edit()
            .putBoolean(com.tanush.bouncer.utils.PreferencesHelper.KEY_HAPTIC_FEEDBACK, false)
            .putInt(com.tanush.bouncer.utils.PreferencesHelper.KEY_REFRESH_INTERVAL, 15)
            .putLong(PrefsRegistry.Highlights.LAST_ROW_FETCHED_AT, 1_724_000_000_000L)
            .putString(com.tanush.bouncer.utils.PreferencesHelper.KEY_THEME_VARIANT, "Light")
            .putString("reminders", REMINDER_JSON)
            .commit()

        PreferenceStore.initialize(context)
        PreferenceStore.warmUp()

        assertFalse(
            BouncerPreferences.getBoolean(
                com.tanush.bouncer.utils.PreferencesHelper.KEY_HAPTIC_FEEDBACK, true,
            )
        )
        assertEquals(
            15,
            BouncerPreferences.getInt(
                com.tanush.bouncer.utils.PreferencesHelper.KEY_REFRESH_INTERVAL, 30,
            ),
        )
        assertEquals(
            1_724_000_000_000L,
            BouncerPreferences.getLong(PrefsRegistry.Highlights.LAST_ROW_FETCHED_AT, 0L),
        )
        assertEquals(
            "Light",
            BouncerPreferences.getString(
                com.tanush.bouncer.utils.PreferencesHelper.KEY_THEME_VARIANT, "System",
            ),
        )
        assertEquals(REMINDER_JSON, BouncerPreferences.getString("reminders", "[]"))
    }

    @Test
    fun theSharedPreferencesFileIsGoneAfterwards() = runBlocking {
        context.getSharedPreferences(PrefsRegistry.PREFS_APP, Context.MODE_PRIVATE).edit()
            .putBoolean("anything", true)
            .commit()

        PreferenceStore.initialize(context)
        PreferenceStore.warmUp()

        // Not cosmetic: while the XML survives, a consumer that was missed keeps reading a file
        // that is no longer the source of truth, and the two diverge silently.
        val xml = File(context.dataDir, "shared_prefs/${PrefsRegistry.PREFS_APP}.xml")
        assertFalse("$xml still exists after the migration", xml.exists())
        assertTrue(BouncerPreferences.getBoolean("anything", false))
    }

    private companion object {
        const val REMINDER_JSON =
            """[{"id":1,"title":"IND v AUS","body":"Starts soon","triggerAt":1724000000000}]"""
    }
}

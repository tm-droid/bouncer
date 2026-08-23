package com.tanush.bouncer.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises [BouncerPreferences] against a real DataStore over a temp file.
 *
 * The Android artifact is not needed: `datastore-preferences-core` runs on the JVM, so the storage
 * engine under test is the one that ships. What is deliberately *not* covered here is
 * `SharedPreferencesMigration`, which needs a `Context` — that is in the instrumented test, and it
 * is the half that can silently lose user data, so it is worth running before a release.
 */
class BouncerPreferencesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = PreferenceDataStoreFactory.create(scope = scope) {
            File(temp.newFolder(), "test.preferences_pb")
        }
        PreferenceStore.install(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `reads fall back to the supplied default`() {
        assertEquals(true, BouncerPreferences.getBoolean("absent", true))
        assertEquals(7, BouncerPreferences.getInt("absent", 7))
        assertEquals(9L, BouncerPreferences.getLong("absent", 9L))
        assertEquals("x", BouncerPreferences.getString("absent", "x"))
    }

    @Test
    fun `a write is visible to the very next read`() {
        // The point of the volatile snapshot. SettingsScreenV2 writes a toggle and re-reads it in
        // the same frame; if that had to wait for the durable write, the toggle would flick back.
        BouncerPreferences.putBoolean("haptic_feedback", false)
        assertEquals(false, BouncerPreferences.getBoolean("haptic_feedback", true))

        BouncerPreferences.putInt("refresh_interval", 15)
        assertEquals(15, BouncerPreferences.getInt("refresh_interval", 30))

        BouncerPreferences.putString("theme_variant", "Light")
        assertEquals("Light", BouncerPreferences.getString("theme_variant", "System"))
    }

    @Test
    fun `a write reaches the durable store`() = runBlocking {
        // Deliberately runBlocking, not runTest: the durable write happens on Dispatchers.IO and
        // runTest's virtual clock would skip the polling delay without any of that work having
        // run, so the assertion would fail on timing rather than on behaviour.
        BouncerPreferences.putLong("appwrite_breaker_opened_at_DB_READ", 1_234L)
        assertEquals(
            1_234L,
            awaitStored(longPreferencesKey("appwrite_breaker_opened_at_DB_READ")),
        )
    }

    @Test
    fun `remove clears the key whatever type wrote it`() {
        // `Preferences.Key` compares by name only, so the second write replaces the first rather
        // than sitting beside it — a name holds one value. Removal has to clear it either way.
        BouncerPreferences.putBoolean("latch", true)
        BouncerPreferences.putLong("latch", 5L)

        BouncerPreferences.remove("latch")

        assertEquals(false, BouncerPreferences.getBoolean("latch", false))
        assertEquals(0L, BouncerPreferences.getLong("latch", 0L))
    }

    @Test
    fun `a read before the first emission still sees stored data`() = runBlocking {
        // The cold-start path: something asks for a preference before the collector has emitted.
        // Seeding behind the facade and re-installing reproduces it without a timing race.
        store.edit { it[stringPreferencesKey("seeded")] = "from disk" }
        PreferenceStore.install(store)

        assertEquals("from disk", BouncerPreferences.getString("seeded", "default"))
    }

    private suspend fun <T> awaitStored(key: Preferences.Key<T>): T? {
        repeat(RETRIES) {
            store.data.first()[key]?.let { return it }
            delay(POLL_MS)
        }
        return store.data.first()[key]
    }

    private companion object {
        const val RETRIES = 50
        const val POLL_MS = 20L
    }
}

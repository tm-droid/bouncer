package com.tanush.bouncer.data.prefs

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.tanush.bouncer.utils.PrefsRegistry
import io.sentry.Sentry
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * DataStore over the app's main preference file, plus the machinery that lets it be read
 * synchronously. [BouncerPreferences] is the typed surface on top; this is the engine.
 *
 * ### Why this replaced SharedPreferences for [PrefsRegistry.PREFS_APP]
 *
 * Not because SharedPreferences re-reads disk — it does not; it parses its XML once and answers
 * from memory after that. Two things it does do:
 *
 *  - **`apply()` blocks `Activity.onPause`.** Writes are handed to a background thread, but the
 *    framework waits on every outstanding one before the activity can pause. A burst of toggles in
 *    Settings turns into a stall on the way out of the screen. DataStore's writes are not on that
 *    path.
 *  - **There is no way to observe a change.** Every consumer read a value into
 *    `remember { mutableStateOf(...) }`, so a preference changed on one screen stayed stale
 *    everywhere else until that composable happened to leave the composition. [observe] makes the
 *    value reactive, which is what the Settings screen actually needed.
 *
 * ### The synchronous surface is deliberate
 *
 * `PreferencesHelper` has 34 call sites, many inside composables, and turning all of them into
 * suspend functions in the same change as swapping the storage engine would make a storage bug and
 * a threading bug indistinguishable. So [read] answers from [snapshot], a volatile copy of the last
 * emitted [Preferences] — the same in-memory-after-first-load shape SharedPreferences had. New code
 * should prefer [observe].
 *
 * ### Migration
 *
 * [SharedPreferencesMigration] copies the whole `bouncer_prefs` XML across on first access and then
 * **deletes it**. Every consumer of that file therefore had to move in the same change —
 * `PreferencesHelper`, `RemindersManager`, `AppwriteReadGuard.PrefsBreakerStore` and
 * `PrefsHighlightsSyncMarker`. A consumer left behind would not fail; it would silently start
 * reading defaults, which for the read guard means a live 402 latch resetting itself on upgrade.
 */
object PreferenceStore {

    @Volatile private var store: DataStore<Preferences>? = null
    @Volatile private var snapshot: Preferences = emptyPreferences()
    @Volatile private var hydrated = false
    @Volatile private var collector: Job? = null

    /**
     * Writes applied in memory whose durable write has not landed yet, read on top of [snapshot].
     *
     * The first shape of this mutated [snapshot] directly, and that was wrong: a read arriving
     * before the collector's first emission goes to the store, and what comes back is the
     * pre-write state — so an optimistic write made a moment earlier was silently discarded, which
     * is exactly what the "a write is visible to the very next read" test caught. An overlay cannot
     * be clobbered by an emission, and each entry clears itself once its own write confirms.
     */
    private val pending = ConcurrentHashMap<Preferences.Key<*>, Any>()

    /** Marks a key removed in [pending]; absence there means "no pending change". */
    private object Removed

    private val lock = Any()

    /**
     * Parent of both the write scope and each DataStore's own scope.
     *
     * The DataStore gets a *child* rather than this scope directly, because DataStore holds an
     * exclusive lock on its file for as long as its scope is alive and refuses to construct a
     * second instance over the same path. Cancelling this scope to release that lock would also
     * kill every pending write, permanently — the same trap `.claude/rules/appwrite-patterns.md`
     * documents for `stop()` cancelling a scope instead of its children.
     */
    private val parentJob = SupervisorJob()
    private val scope = CoroutineScope(parentJob + Dispatchers.IO)

    @Volatile private var storeScope: CoroutineScope? = null

    /** Idempotent. Safe to call from any site that has a [Context] but no startup ordering. */
    fun initialize(context: Context) {
        if (store != null) return
        synchronized(lock) {
            if (store != null) return
            val app = context.applicationContext
            val ownScope = CoroutineScope(SupervisorJob(parentJob) + Dispatchers.IO)
            storeScope = ownScope
            install(
                PreferenceDataStoreFactory.create(
                    scope = ownScope,
                    migrations = listOf(SharedPreferencesMigration(app, PrefsRegistry.PREFS_APP)),
                    produceFile = { app.preferencesDataStoreFile(PrefsRegistry.PREFS_APP) },
                )
            )
        }
    }

    /**
     * Test seam: drop the store so the next [initialize] rebuilds it, migration and all.
     *
     * DataStore evaluates its migrations once per instance, on that instance's first read. The
     * instrumented test runs inside the app process, where `BouncerApp.onCreate` has already
     * initialized and warmed the store — so without this, a test that writes to SharedPreferences
     * and then calls [initialize] is writing after the migration window has closed, and measures
     * nothing.
     */
    @VisibleForTesting
    fun resetForTest() {
        collector?.cancel()
        collector = null
        // Join, do not just cancel: DataStore releases its file lock when the scope's job actually
        // completes, and the next initialize() constructs a second instance over the same path.
        // Cancelling without waiting throws "There are multiple DataStores active for the same
        // file" — which is how this was found.
        storeScope?.let { runBlocking { it.coroutineContext.job.cancelAndJoin() } }
        storeScope = null
        pending.clear()
        store = null
        snapshot = emptyPreferences()
        hydrated = false
    }

    /** Test seam: swap in a store over a temp file. */
    fun install(dataStore: DataStore<Preferences>) {
        // Cancel the previous collector first: without this a re-install leaves the old one alive
        // and the two race to write [snapshot], so reads answer from whichever store emitted last.
        collector?.cancel()
        pending.clear()
        store = dataStore
        hydrated = false
        snapshot = emptyPreferences()
        collector = scope.launch {
            dataStore.data
                .catch { e ->
                    // An IOException here is a corrupt or unreadable store, not a programming
                    // error. Reporting and continuing with defaults beats taking down every caller
                    // of a preference read.
                    if (e is IOException) Sentry.captureException(e) else throw e
                    emit(emptyPreferences())
                }
                .collect {
                    snapshot = it
                    hydrated = true
                }
        }
    }

    /**
     * Blocks until the store has been read once.
     *
     * Called from `BouncerApp.onCreate` so the one-time cost — including the SharedPreferences
     * migration on the first launch after upgrade — lands at a known point in startup rather than
     * under whichever composable happens to read a preference first. `getSharedPreferences` blocked
     * at the same point for the same reason: something has to parse the file.
     */
    fun warmUp() {
        current()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> read(key: Preferences.Key<T>, default: T): T {
        pending[key]?.let { return if (it === Removed) default else it as T }
        return current()[key] ?: default
    }

    fun <T> write(key: Preferences.Key<T>, value: T) {
        val store = requireStore()
        // Readable immediately — SettingsScreenV2 writes a toggle and re-reads it in the same
        // frame. `apply()` behaved the same way. What changed is that the durable write no longer
        // has to finish before the activity can pause.
        pending[key] = value as Any
        scope.launch {
            runCatching { store.edit { it[key] = value } }
                .onFailure { Sentry.captureException(it) }
            // Only if this exact write is still the pending one: a newer value must not be dropped
            // by an older write's completion.
            pending.remove(key, value as Any)
        }
    }

    fun removeKeys(names: List<String>) {
        val store = requireStore()
        // One key per name is enough: `Preferences.Key.equals` compares the name and nothing else
        // (verified against the 1.2.1 bytecode), so the type parameter takes no part in lookup or
        // removal. A name holds exactly one value, of whatever type wrote it last — the same shape
        // SharedPreferences had, ClassCastException included.
        val keys = names.map { stringPreferencesKey(it) }
        keys.forEach { pending[it] = Removed }
        scope.launch {
            runCatching { store.edit { prefs -> keys.forEach { prefs.remove(it) } } }
                .onFailure { Sentry.captureException(it) }
            keys.forEach { pending.remove(it, Removed) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> observe(key: Preferences.Key<T>, default: T): Flow<T> =
        requireStore().data
            .catch { e ->
                if (e is IOException) Sentry.captureException(e) else throw e
                emit(emptyPreferences())
            }
            .map { stored ->
                // Same overlay as the synchronous read, so an observer does not briefly see the old
                // value between an optimistic write and its durable one.
                pending[key]?.let { return@map if (it === Removed) default else it as T }
                stored[key] ?: default
            }
            .distinctUntilChanged()

    private fun current(): Preferences {
        if (hydrated) return snapshot
        val store = requireStore()
        val first = runBlocking {
            store.data
                .catch { e ->
                    if (e is IOException) Sentry.captureException(e) else throw e
                    emit(emptyPreferences())
                }
                .first()
        }
        synchronized(lock) {
            if (!hydrated) {
                snapshot = first
                hydrated = true
            }
        }
        return snapshot
    }

    private fun requireStore(): DataStore<Preferences> = checkNotNull(store) {
        "PreferenceStore.initialize(context) has not run. Call it from BouncerApp.onCreate, or " +
            "via PreferencesHelper, which initializes lazily from the Context it is given."
    }
}

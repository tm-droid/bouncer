package com.tanush.bouncer.managers

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Which alerts a match sends is the whole of "follow this match", and it is kept in two places
 * that must agree: a preferences file, and FCM topic membership. A regression in either is silent
 * — the sheet still shows the switches on and no alert arrives.
 */
class MatchSubscriptionManagerTest {

    private val prefs = InMemoryPreferences()
    private val topics = RecordingTopics()
    private lateinit var noBackupDir: File
    private lateinit var context: Context
    private lateinit var realTopics: MatchTopicSubscriber

    private val wicketsOnly = MatchAlertPrefs(wickets = true, boundaries = false, milestones = false, startAndResult = false)

    @Before
    fun setUp() {
        noBackupDir = Files.createTempDirectory("no-backup").toFile()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.noBackupFilesDir } returns noBackupDir
        realTopics = MatchSubscriptionManager.topics
        MatchSubscriptionManager.topics = topics
        startAgain()
    }

    @After
    fun tearDown() {
        MatchSubscriptionManager.topics = realTopics
        forgetInitialised()
        noBackupDir.deleteRecursively()
    }

    /** The manager is an object that initialises once; a new process is a cleared flag. */
    private fun forgetInitialised() {
        MatchSubscriptionManager::class.java.getDeclaredField("initialized").apply {
            isAccessible = true
            setBoolean(MatchSubscriptionManager, false)
        }
    }

    private fun startAgain() {
        forgetInitialised()
        MatchSubscriptionManager.initialize(context)
    }

    @Test
    fun `turning an alert on joins the topic, and turning the last one off leaves it`() {
        MatchSubscriptionManager.setAlerts("161777", wicketsOnly)
        assertEquals(listOf("+match_161777"), topics.calls)

        // Changing which alerts, with one still on, is not a topic change.
        MatchSubscriptionManager.setAlerts("161777", wicketsOnly.copy(boundaries = true))
        assertEquals(listOf("+match_161777"), topics.calls)

        MatchSubscriptionManager.setAlerts("161777", MatchAlertPrefs.NONE)
        assertEquals(listOf("+match_161777", "-match_161777"), topics.calls)
        assertFalse(MatchSubscriptionManager.alertsFor("161777").anyOn)
    }

    @Test
    fun `the choice survives a restart`() {
        MatchSubscriptionManager.setAlerts("161777", wicketsOnly)
        startAgain()
        assertEquals(wicketsOnly, MatchSubscriptionManager.alertsFor("161777"))
        assertEquals(MatchAlertPrefs.NONE, MatchSubscriptionManager.alertsFor("somebody-else"))
    }

    @Test
    fun `after a restore the topics are joined again, once`() {
        MatchSubscriptionManager.setAlerts("1", wicketsOnly)
        MatchSubscriptionManager.setAlerts("2", wicketsOnly)
        MatchSubscriptionManager.setAlerts("2", MatchAlertPrefs.NONE)
        topics.calls.clear()

        // A new phone: the preferences came across in the backup, the no-backup marker did not.
        noBackupDir.listFiles()?.forEach { it.delete() }
        startAgain()
        assertEquals("only the match whose alerts are on", listOf("+match_1"), topics.calls)

        // The same phone, next launch.
        topics.calls.clear()
        startAgain()
        assertTrue(topics.calls.isEmpty())
    }

    private class RecordingTopics : MatchTopicSubscriber {
        val calls = mutableListOf<String>()
        override fun subscribe(topic: String) { calls += "+$topic" }
        override fun unsubscribe(topic: String) { calls += "-$topic" }
    }

    /** As much of SharedPreferences as the manager uses: strings, written through an editor. */
    private class InMemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, String?>()

        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun edit(): SharedPreferences.Editor = Editor()

        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = unused()
        override fun getInt(key: String?, defValue: Int) = unused()
        override fun getLong(key: String?, defValue: Long) = unused()
        override fun getFloat(key: String?, defValue: Float) = unused()
        override fun getBoolean(key: String?, defValue: Boolean) = unused()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private fun unused(): Nothing = error("not used by MatchSubscriptionManager")

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, String?>()
            private var clearFirst = false

            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { clearFirst = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearFirst) values.clear()
                pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            }

            override fun putStringSet(key: String?, v: MutableSet<String>?) = unused()
            override fun putInt(key: String?, v: Int) = unused()
            override fun putLong(key: String?, v: Long) = unused()
            override fun putFloat(key: String?, v: Float) = unused()
            override fun putBoolean(key: String?, v: Boolean) = unused()
        }
    }
}

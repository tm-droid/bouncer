package com.tanush.bouncer

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The diagnostics switch is a privacy control: every doubt about the saved choice must resolve to
 * "nothing is sent". Its first version read a preference and treated a failed read as on.
 */
class DiagnosticsTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("diagnostics").toFile()
    }

    @After
    fun tearDown() {
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    @Test
    fun `on until switched off, and the choice is a file`() {
        assertTrue(Diagnostics.isEnabledIn(dir))
        assertFalse(Diagnostics.recordChoice(dir, enabled = false))
        assertTrue(File(dir, Diagnostics.OPT_OUT_FILE).exists())
        assertFalse(Diagnostics.isEnabledIn(dir))
    }

    @Test
    fun `switching back on removes the file`() {
        Diagnostics.recordChoice(dir, enabled = false)
        assertTrue(Diagnostics.recordChoice(dir, enabled = true))
        assertTrue(Diagnostics.isEnabledIn(dir))
    }

    @Test
    fun `no directory to look in means off, both ways`() {
        assertFalse(Diagnostics.isEnabledIn(null))
        assertFalse(Diagnostics.recordChoice(null, enabled = true))
        assertFalse(Diagnostics.recordChoice(null, enabled = false))
    }

    @Test
    fun `a choice of on that cannot be saved stays off`() {
        Diagnostics.recordChoice(dir, enabled = false)
        // The opt-out file can no longer be removed.
        assertTrue(dir.setWritable(false))
        assertFalse(Diagnostics.recordChoice(dir, enabled = true))
        assertFalse(Diagnostics.isEnabledIn(dir))
    }

    @Test
    fun `a choice of off that cannot be saved is still off for this session`() {
        val missing = File(dir, "does-not-exist")
        // createNewFile throws: the parent is not there.
        assertFalse(Diagnostics.recordChoice(missing, enabled = false))
    }
}

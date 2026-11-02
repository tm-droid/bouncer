package com.tanush.bouncer.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {

    private val ciBuild = AppUpdatePolicy.CI_VERSION_CODE_FLOOR + 140

    @Test
    fun `Play forces only a priority 5 update that is available`() {
        assertTrue(AppUpdatePolicy.playForces(ciBuild, updateAvailable = true, priority = 5))
        assertFalse(AppUpdatePolicy.playForces(ciBuild, updateAvailable = true, priority = 4))
        assertFalse(AppUpdatePolicy.playForces(ciBuild, updateAvailable = false, priority = 5))
    }

    @Test
    fun `a build below the published minimum is retired, one at or above it is not`() {
        assertTrue(AppUpdatePolicy.belowMinimum(ciBuild, ciBuild + 1))
        assertFalse(AppUpdatePolicy.belowMinimum(ciBuild, ciBuild))
        assertFalse(AppUpdatePolicy.belowMinimum(ciBuild, null))
    }

    @Test
    fun `a local build is never gated`() {
        // A local build is versionCode 8; a live minimum must not lock out a debug build.
        assertFalse(AppUpdatePolicy.belowMinimum(8, ciBuild))
        assertFalse(AppUpdatePolicy.playForces(8, updateAvailable = true, priority = 5))
    }

    @Test
    fun `the app-config file parses, and anything unreadable is no minimum`() {
        assertEquals(1234, AppUpdatePolicy.parseMinimum("""{"minVersionCode":1234,"note":"x"}""".toByteArray()))
        assertNull(AppUpdatePolicy.parseMinimum("""{"minVersionCode":0}""".toByteArray()))
        assertNull(AppUpdatePolicy.parseMinimum("""{}""".toByteArray()))
        assertNull(AppUpdatePolicy.parseMinimum("<html>".toByteArray()))
    }
}

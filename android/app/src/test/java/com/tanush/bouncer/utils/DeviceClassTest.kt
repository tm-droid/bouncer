package com.tanush.bouncer.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceClassTest {

    @Test
    fun `Play's pre-launch device is low-end`() {
        // OnePlus8Pro in the pre-launch report: 2 cores, 4 GB, no low-RAM flag.
        assertTrue(DeviceClass.isLowEnd(lowRamFlag = false, cores = 2, memoryClassMb = 256))
    }

    @Test
    fun `the low-RAM flag or a small heap is low-end`() {
        assertTrue(DeviceClass.isLowEnd(lowRamFlag = true, cores = 8, memoryClassMb = 512))
        assertTrue(DeviceClass.isLowEnd(lowRamFlag = false, cores = 8, memoryClassMb = 128))
    }

    @Test
    fun `a current flagship is not low-end`() {
        assertFalse(DeviceClass.isLowEnd(lowRamFlag = false, cores = 8, memoryClassMb = 512))
    }
}

package com.tanush.bouncer.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveActivityViewModelTest {

    @Test
    fun `selectedFormat starts as All`() {
        val vm = LiveActivityViewModel()
        assertEquals("All", vm.selectedFormat.value)
    }

    @Test
    fun `setFormat updates selectedFormat`() {
        val vm = LiveActivityViewModel()
        vm.setFormat("T20")
        assertEquals("T20", vm.selectedFormat.value)
    }

    @Test
    fun `setFormat to All resets filter`() {
        val vm = LiveActivityViewModel()
        vm.setFormat("ODI")
        vm.setFormat("All")
        assertEquals("All", vm.selectedFormat.value)
    }
}

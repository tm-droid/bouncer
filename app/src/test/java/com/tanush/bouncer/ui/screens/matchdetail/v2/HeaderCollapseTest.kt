package com.tanush.bouncer.ui.screens.matchdetail.v2

import org.junit.Assert.assertEquals
import org.junit.Test

/** The header's collapse arithmetic: who consumes a scroll, where it stops, and where a release settles. */
class HeaderCollapseTest {

    private fun math(range: Float = 100f) = HeaderCollapseMath(range)

    @Test
    fun `scrolling up collapses the header and leaves the rest to the list`() {
        val m = math()
        assertEquals(-40f, m.consumePre(-40f), 0f)
        assertEquals(-40f, m.offset, 0f)
        assertEquals(0.4f, m.fraction, 0.001f)
        assertEquals(-60f, m.consumePre(-90f), 0f)
        assertEquals(1f, m.fraction, 0f)
    }

    @Test
    fun `scrolling down is not taken before the list has moved`() {
        val m = math()
        m.consumePre(-50f)
        assertEquals(0f, m.consumePre(30f), 0f)
        assertEquals(-50f, m.offset, 0f)
    }

    @Test
    fun `leftover downward scroll expands the header and stops at zero`() {
        val m = math()
        m.consumePre(-100f)
        assertEquals(30f, m.consumePost(30f), 0f)
        assertEquals(70f, m.consumePost(500f), 0f)
        assertEquals(0f, m.offset, 0f)
        assertEquals(0f, m.consumePost(10f), 0f)
    }

    @Test
    fun `leftover upward scroll is ignored after the list`() {
        val m = math()
        assertEquals(0f, m.consumePost(-20f), 0f)
        assertEquals(0f, m.offset, 0f)
    }

    @Test
    fun `an unmeasured header consumes nothing`() {
        val m = math(0f)
        assertEquals(0f, m.consumePre(-50f), 0f)
        assertEquals(0f, m.fraction, 0f)
    }

    @Test
    fun `expand returns the header to full height`() {
        val m = math()
        m.consumePre(-80f)
        m.expand()
        assertEquals(0f, m.offset, 0f)
        assertEquals(0f, m.fraction, 0f)
    }

    @Test
    fun `a slow release settles to the nearer end`() {
        val m = math()
        m.consumePre(-30f)
        assertEquals(0f, m.settleTarget(0f), 0f)
        m.consumePre(-40f)
        assertEquals(-100f, m.settleTarget(100f), 0f)
    }

    @Test
    fun `a fast fling settles to the end it points at`() {
        val m = math()
        m.consumePre(-80f)
        assertEquals(0f, m.settleTarget(2000f), 0f)
        m.setOffset(-10f)
        assertEquals(-100f, m.settleTarget(-2000f), 0f)
    }

    @Test
    fun `a shrinking range clamps the offset`() {
        val m = math()
        m.consumePre(-100f)
        m.setRange(60f)
        assertEquals(-60f, m.offset, 0f)
        assertEquals(1f, m.fraction, 0f)
        m.setRange(0f)
        assertEquals(0f, m.offset, 0f)
    }

    @Test
    fun `a growing range keeps the offset`() {
        val m = math()
        m.consumePre(-50f)
        m.setRange(200f)
        assertEquals(-50f, m.offset, 0f)
        assertEquals(0.25f, m.fraction, 0.001f)
    }
}

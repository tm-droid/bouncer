package com.tanush.bouncer.analysis

import com.tanush.bouncer.analysis.utils.SlidingWindow
import org.junit.Assert.*
import org.junit.Test

class SlidingWindowTest {

    @Test fun `empty window has size zero`() {
        val w = SlidingWindow<Int>(10)
        assertEquals(0, w.size)
        assertTrue(w.isEmpty)
    }

    @Test fun `add single entry`() {
        val w = SlidingWindow<Int>(10)
        w.add(1000L, 42)
        assertEquals(1, w.size)
        assertEquals(42, w.values().first())
    }

    @Test fun `window does not exceed maxSize`() {
        val w = SlidingWindow<Int>(3)
        repeat(5) { i -> w.add(i.toLong(), i * 10) }
        assertEquals(3, w.size)
    }

    @Test fun `oldest entry evicted when full`() {
        val w = SlidingWindow<Int>(3)
        w.add(1L, 10)
        w.add(2L, 20)
        w.add(3L, 30)
        w.add(4L, 40)
        assertFalse(w.values().contains(10))
        assertTrue(w.values().contains(40))
    }

    @Test fun `average of values`() {
        val w = SlidingWindow<Int>(10)
        w.add(1L, 10)
        w.add(2L, 20)
        w.add(3L, 30)
        assertEquals(20.0, w.average(), 0.001)
    }

    @Test fun `average on empty window is zero`() {
        val w = SlidingWindow<Int>(10)
        assertEquals(0.0, w.average(), 0.0)
    }

    @Test fun `timestamps preserved with values`() {
        val w = SlidingWindow<Int>(10)
        w.add(999L, 55)
        assertEquals(999L, w.timestamps().first())
    }

    @Test fun `maxSize of one keeps only latest`() {
        val w = SlidingWindow<Int>(1)
        w.add(1L, 100)
        w.add(2L, 200)
        assertEquals(1, w.size)
        assertEquals(200, w.values().first())
    }
}

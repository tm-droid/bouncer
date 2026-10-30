package com.tanush.bouncer.ui.components.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class FreshnessTest {
    private val now = 1_000_000L

    private fun at(ageMs: Long, live: Boolean = true) = freshnessOf(now - ageMs, now, live)

    @Test
    fun `60 s is still fresh`() = assertEquals(Freshness.Fresh, at(60_000))

    @Test
    fun `61 s is aging`() = assertEquals(Freshness.Aging(1), at(61_000))

    @Test
    fun `180 s is still aging`() = assertEquals(Freshness.Aging(3), at(180_000))

    @Test
    fun `181 s is stale`() = assertEquals(Freshness.Stale(3), at(181_000))

    @Test
    fun `unknown timestamp hides`() = assertEquals(Freshness.Hidden, freshnessOf(null, now, true))

    @Test
    fun `not live hides whatever the age`() = assertEquals(Freshness.Hidden, at(600_000, live = false))

    @Test
    fun `clock skew reads as fresh`() = assertEquals(Freshness.Fresh, at(-5_000))
}

package com.tanush.bouncer.ui.screens.player

import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.models.RecentInnings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFormMathTest {

    // --- formRating ---------------------------------------------------------

    @Test
    fun `form rating no longer saturates above parity`() {
        // `(recent / career).coerceIn(0f, 1f)` gave BOTH of these exactly 1.0, so a batter averaging
        // 60 against a career 40 rendered an identical full ring to one averaging 41.
        val strong = formRating(60.0, 40.0)
        val marginal = formRating(41.0, 40.0)
        assertTrue("60-vs-40 ($strong) must outrank 41-vs-40 ($marginal)", strong > marginal)
        assertTrue("a rating must stay inside the ring", strong < 1.0f)
    }

    @Test
    fun `parity is the midpoint`() {
        assertEquals(0.5f, formRating(40.0, 40.0), 1e-6f)
    }

    @Test
    fun `the rating is monotone in the ratio`() {
        val ratings = listOf(10.0, 20.0, 40.0, 60.0, 100.0).map { formRating(it, 40.0) }
        assertEquals(ratings.sorted(), ratings)
    }

    @Test
    fun `twice the career rate is two thirds and half is one third`() {
        assertEquals(2f / 3f, formRating(80.0, 40.0), 1e-6f)
        assertEquals(1f / 3f, formRating(20.0, 40.0), 1e-6f)
    }

    @Test
    fun `no runs scores zero and no career figure is neutral`() {
        assertEquals(0f, formRating(0.0, 40.0), 1e-6f)
        assertEquals(0.5f, formRating(40.0, null), 1e-6f)
        assertEquals(0.5f, formRating(40.0, 0.0), 1e-6f)
    }

    // --- careerRunsPerInnings ----------------------------------------------

    @Test
    fun `career rate is weighted by innings, not a flat mean of formats`() {
        // 90 Test innings at 45 per innings, 2 T10 innings at 5. The unweighted mean of the two
        // format figures is 25; pooling gives 44.13, which is what the player actually is.
        val stats = listOf(
            FormatStats(innings = 90, runs = 4050, average = 50.0, strikeRate = 55.0),
            FormatStats(innings = 2, runs = 10, average = 5.0, strikeRate = 150.0),
        )
        val pooled = careerRunsPerInnings(stats)!!
        assertEquals(4060.0 / 92, pooled, 1e-9)
        assertTrue("the two-innings record must not drag the figure to 25", pooled > 40.0)
    }

    @Test
    fun `career rate is runs per innings, not runs per dismissal`() {
        // `FormatStats.average` is runs per dismissal and was compared directly against a recent
        // runs-per-innings figure. Not-outs make the two differ, and the mismatch flattered form.
        val stats = listOf(FormatStats(innings = 100, runs = 4000, average = 50.0))
        assertEquals(40.0, careerRunsPerInnings(stats)!!, 1e-9)
    }

    @Test
    fun `formats with no innings are ignored and an empty record is null`() {
        assertNull(careerRunsPerInnings(emptyList()))
        assertNull(careerRunsPerInnings(listOf(FormatStats(innings = 0, runs = 0))))
        assertEquals(
            30.0,
            careerRunsPerInnings(
                listOf(FormatStats(innings = 0, runs = 0), FormatStats(innings = 10, runs = 300)),
            )!!,
            1e-9,
        )
    }

    // --- weightedStrikeRate ------------------------------------------------

    @Test
    fun `strike rate is weighted by innings`() {
        val stats = listOf(
            FormatStats(innings = 90, runs = 4050, strikeRate = 55.0),
            FormatStats(innings = 2, runs = 10, strikeRate = 150.0),
        )
        // Unweighted this was 102.5 — a Test batter presented as a T20 striker.
        assertEquals((55.0 * 90 + 150.0 * 2) / 92, weightedStrikeRate(stats)!!, 1e-9)
    }

    @Test
    fun `a zero strike rate is not counted as data`() {
        val stats = listOf(
            FormatStats(innings = 10, runs = 300, strikeRate = 0.0),
            FormatStats(innings = 10, runs = 300, strikeRate = 80.0),
        )
        assertEquals(80.0, weightedStrikeRate(stats)!!, 1e-9)
        assertNull(weightedStrikeRate(listOf(FormatStats(innings = 10, strikeRate = 0.0))))
    }

    // --- trend --------------------------------------------------------------

    @Test
    fun `trend reads the recent window against the whole window`() {
        assertEquals("Rising", formTrend(listOf(90, 80, 70, 10, 5, 0)))
        assertEquals("Declining", formTrend(listOf(0, 5, 10, 70, 80, 90)))
        assertEquals("Stable", formTrend(listOf(40, 40, 40, 40, 40, 40)))
        assertEquals("Stable", formTrend(emptyList()))
    }

    // --- buildBatterForm ---------------------------------------------------

    private fun profile(recent: List<Int>, stats: Map<String, FormatStats>) = PlayerProfile(
        id = "p1", name = "Test Batter", country = "India", role = "Batsman",
        stats = stats,
        recentForm = recent.map { RecentInnings(runs = it) },
    )

    @Test
    fun `no recent form means no card`() {
        assertNull(buildBatterForm(profile(emptyList(), emptyMap())))
    }

    @Test
    fun `an in-form batter is distinguishable from a barely-in-form one`() {
        val career = mapOf("Test" to FormatStats(innings = 100, runs = 4000, strikeRate = 55.0))
        val hot = buildBatterForm(profile(listOf(60, 60, 60, 60, 60), career))
        val lukewarm = buildBatterForm(profile(listOf(41, 41, 41, 41, 41), career))
        assertNotNull(hot)
        assertNotNull(lukewarm)
        // Both rendered a full ring before: 60/40 and 41/40 both clamped to 1.0.
        assertTrue(hot!!.formRating > lukewarm!!.formRating)
    }

    @Test
    fun `a player with no career stats still gets a card`() {
        val form = buildBatterForm(profile(listOf(30, 40), emptyMap()))
        assertNotNull(form)
        assertEquals(35.0, form!!.average, 1e-9)
        assertEquals(0.5f, form.formRating, 1e-6f)
        assertEquals(0.0, form.strikeRate, 1e-9)
    }
}

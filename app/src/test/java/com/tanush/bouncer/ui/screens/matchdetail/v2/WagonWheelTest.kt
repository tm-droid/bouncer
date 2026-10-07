package com.tanush.bouncer.ui.screens.matchdetail.v2

import com.tanush.bouncer.models.Scorecard
import com.tanush.bouncer.models.WagonWheel
import com.tanush.bouncer.data.AppJson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A wheel is drawn only when it is the eight zones the drawing expects and they add up to the runs
 * it says it covers. The snapshot below is the one `bouncer-match-sync` read for Shreyas Iyer on
 * 2026-10-07 (Cricinfo match 1529230).
 */
class WagonWheelTest {

    private val iyer = WagonWheel(innings = 2, name = "Shreyas Iyer", runs = 102, balls = 55, zones = listOf(4, 1, 33, 18, 11, 27, 1, 7))

    @Test
    fun `the scorecard row parses with and without wheels`() {
        val row = """{"innings":[],"wagon":[{"innings":2,"name":"Shreyas Iyer","runs":102,"balls":55,"zones":[4,1,33,18,11,27,1,7]}]}"""
        assertEquals(listOf(iyer), AppJson.decodeFromString<Scorecard>(row).wagon)
        assertEquals(emptyList<WagonWheel>(), AppJson.decodeFromString<Scorecard>("""{"innings":[]}""").wagon)
    }

    @Test
    fun `only the selected innings' batters who scored are shown`() {
        val duck = iyer.copy(name = "A Duck", runs = 0, zones = List(WAGON_ZONES) { 0 })
        val other = iyer.copy(innings = 1, name = "Shai Hope")
        assertEquals(listOf(iyer), wagonWheelsFor(listOf(other, iyer, duck), inningsNumber = 2))
        assertEquals(listOf(other), wagonWheelsFor(listOf(other, iyer, duck), inningsNumber = 1))
    }

    @Test
    fun `a wheel that is not eight zones summing to its runs is not drawn`() {
        assertEquals(emptyList<WagonWheel>(), wagonWheelsFor(listOf(iyer.copy(runs = 101)), 2))
        assertEquals(emptyList<WagonWheel>(), wagonWheelsFor(listOf(iyer.copy(zones = listOf(102))), 2))
        assertEquals(emptyList<WagonWheel>(), wagonWheelsFor(listOf(iyer.copy(zones = listOf(110, -8, 0, 0, 0, 0, 0, 0))), 2))
        assertEquals(emptyList<WagonWheel>(), wagonWheelsFor(listOf(iyer.copy(name = " ")), 2))
    }

    @Test
    fun `a zone is weighted against the batter's own busiest zone`() {
        val weights = zoneWeights(iyer.zones)
        assertEquals(1f, weights[2], 0f)
        assertEquals(18f / 33f, weights[3], 1e-6f)
        assertEquals(List(WAGON_ZONES) { 0f }, zoneWeights(List(WAGON_ZONES) { 0 }))
    }
}

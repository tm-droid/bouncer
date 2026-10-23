package com.tanush.bouncer.data.history

import com.tanush.bouncer.data.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PastWeatherTest {
    private fun match(extra: String) =
        AppJson.decodeFromString<HistoryMatch>("""{"v":1,"id":"1","t":["A","B"]$extra}""")

    @Test
    fun `w is read from the match file`() {
        val weather = PastWeather.of(match(""","w":[31.4,2,64,18,40]""").w)
        assertEquals(PastWeather(31.4, 2.0, 64, 18, 40), weather)
    }

    @Test
    fun `a match file without w or with a short or odd w has no weather`() {
        assertNull(PastWeather.of(match("").w))
        assertNull(PastWeather.of(match(""","w":[1,2]""").w))
        assertNull(PastWeather.of(listOf(1.0, 2.0, Double.NaN, 4.0, 5.0)))
    }

    @Test
    fun `figures print without a trailing zero`() {
        assertEquals("31", 31.0.trimmed())
        assertEquals("1.5", 1.5.trimmed())
        assertEquals("0.5", 0.5.trimmed())
    }
}

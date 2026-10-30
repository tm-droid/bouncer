package com.tanush.bouncer.network

import org.junit.Assert.assertEquals
import org.junit.Test

/** The list carries no gender field, so it is read off the names. It was a constant "Men". */
class MatchGenderTest {

    @Test
    fun `a side named as a women's side makes the match a women's match`() {
        assertEquals(GENDER_WOMEN, matchGender("England tour of India, 2026", "India Women", "England Women"))
        assertEquals(GENDER_WOMEN, matchGender("", "Mumbai Indians Women", "UP Warriorz"))
    }

    @Test
    fun `so does a series named as women's cricket`() {
        assertEquals(GENDER_WOMEN, matchGender("ICC Women's T20 World Cup 2026", "India", "Australia"))
        assertEquals(GENDER_WOMEN, matchGender("Womens Big Bash League 2026", "Sydney Sixers", "Perth Scorchers"))
        assertEquals(GENDER_WOMEN, matchGender("WPL 2026", "Delhi Capitals", "Gujarat Giants"))
    }

    @Test
    fun `everything else is men's`() {
        assertEquals(GENDER_MEN, matchGender("Indian Premier League 2026", "Mumbai Indians", "Chennai Super Kings"))
        assertEquals(GENDER_MEN, matchGender("", "", ""))
    }
}

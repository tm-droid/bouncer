package com.tanush.bouncer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRoutesTest {

    @Test
    fun `a player route carries the Cricbuzz id only when there is one`() {
        assertEquals("player/Virat+Kohli", BouncerScreen.PlayerDetail.createRoute("Virat Kohli"))
        assertEquals("player/Virat+Kohli", BouncerScreen.PlayerDetail.createRoute("Virat Kohli", ""))
        assertEquals("player/Virat+Kohli?cbId=1413", BouncerScreen.PlayerDetail.createRoute("Virat Kohli", "1413"))
    }

    @Test
    fun `a search route names its tab only when asked`() {
        assertEquals("search", BouncerScreen.Search.createRoute())
        assertEquals("search?tab=NEWS", BouncerScreen.Search.createRoute("NEWS"))
    }

    @Test
    fun `search opened on a tab still highlights the tab search always did`() {
        assertEquals(BouncerScreen.getParentTab("search"), BouncerScreen.getParentTab(BouncerScreen.Search.ROUTE_PATTERN))
    }
}

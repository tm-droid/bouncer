package com.tanush.bouncer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

/**
 * ANDROID-5F. Which class of the BouncerScreen hierarchy is initialised first depends on the
 * code path, and one JVM initialises each class once — so every case runs in a class loader of
 * its own, with the named object touched before anything else.
 */
class BouncerScreenInitOrderTest {

    private fun parentTabWhenFirstTouched(firstTouched: String, route: String): Any? {
        val classPath = System.getProperty("java.class.path").split(File.pathSeparator)
            .map { File(it).toURI().toURL() }.toTypedArray()
        return URLClassLoader(classPath, ClassLoader.getSystemClassLoader().parent).use { loader ->
            val screen = "com.tanush.bouncer.ui.navigation.BouncerScreen"
            Class.forName("$screen\$$firstTouched", true, loader)
            val companion = Class.forName(screen, true, loader).getField("Companion").get(null)
            companion.javaClass.getMethod("getParentTab", String::class.java).invoke(companion, route)
        }
    }

    @Test
    fun `a primary tab touched first is still a tab`() {
        for (tab in listOf("Home", "SeriesList", "News", "More")) {
            assertEquals("series", parentTabWhenFirstTouched(tab, "series"))
        }
    }

    @Test
    fun `a More child touched first still resolves to More`() {
        for (child in listOf("Favorites", "About", "Search")) {
            assertEquals("more", parentTabWhenFirstTouched(child, "about"))
        }
    }

    @Test
    fun `a page opened from any tab belongs to none, so the bar keeps the tab it came from`() {
        for (route in listOf("player/Virat%20Kohli?cbId=1413", "team/India", "search?tab=NEWS", "notifications", "compare-players")) {
            assertEquals(null, BouncerScreen.ownerTab(route))
        }
        assertEquals("series", BouncerScreen.ownerTab("series/123"))
        assertEquals("home", BouncerScreen.ownerTab("match/42"))
    }
}

package com.tanush.bouncer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Exact-name lookups only: a wrong face or profile is worse than initials. */
class PlayerImageRegistryTest {

    private val kohli = "https://example.com/kohli.jpg"

    @Test
    fun `scorecard names match squad names through role tags, case, accents and spacing`() {
        val registry = PlayerImageRegistry()
        registry.register("1", "Beth Mooney", "https://example.com/mooney.jpg")
        registry.register("2", "José  Butler", "https://example.com/butler.jpg")

        assertEquals("https://example.com/mooney.jpg", registry.url(name = "Beth Mooney (c & wk)"))
        assertEquals("https://example.com/mooney.jpg", registry.url(name = "beth mooney (wk)"))
        assertEquals("https://example.com/butler.jpg", registry.url(name = "Jose Butler"))
        assertEquals("1", registry.playerId("BETH MOONEY (c)"))
        assertEquals("Beth Mooney", PlayerImageRegistry.withoutRoleTags("Beth Mooney (c & wk)"))
    }

    @Test
    fun `a surname alone matches nothing`() {
        val registry = PlayerImageRegistry()
        registry.register("1413", "Virat Kohli", kohli)

        assertNull(registry.url(name = "Kohli"))
        assertNull(registry.playerId("V Kohli"))
    }

    @Test
    fun `the id wins over the name, and a missing photo never clears a known one`() {
        val registry = PlayerImageRegistry()
        registry.register("1413", "Virat Kohli", kohli)
        registry.register("1413", "Virat Kohli", null)
        registry.register("99", "Someone Else", "https://example.com/else.jpg")

        assertEquals(kohli, registry.url(id = "1413", name = "Someone Else"))
        assertEquals(kohli, registry.url(name = "Virat Kohli"))
        assertNull(registry.url(id = "404", name = "Nobody"))
        registry.register("", "Blank Id", kohli)
        assertNull(registry.url(name = "Blank Id"))
        registry.register("5", "No Photo", PlayerImageRegistry.headshotUrl(MatchSquadsRsc.PLACEHOLDER_IMAGE_ID))
        assertNull(registry.url(name = "No Photo"))
        assertEquals("5", registry.playerId("No Photo"))
    }

    @Test
    fun `a renamed id no longer answers to its old name`() {
        val registry = PlayerImageRegistry()
        registry.register("7", "MS Dhoni", "https://example.com/dhoni.jpg")
        registry.register("7", "Mahendra Singh Dhoni", null)

        assertNull(registry.playerId("MS Dhoni"))
        assertEquals("https://example.com/dhoni.jpg", registry.url(name = "Mahendra Singh Dhoni"))
    }

    @Test
    fun `a name two players share opens neither`() {
        val registry = PlayerImageRegistry()
        registry.register("10", "Mohammad Nabi", "https://example.com/nabi.jpg")
        registry.register("20", "Mohammad Nabi", "https://example.com/other.jpg")

        assertNull(registry.playerId("Mohammad Nabi"))
        assertNull(registry.url(name = "Mohammad Nabi"))
        assertEquals("https://example.com/other.jpg", registry.url(id = "20", name = "Mohammad Nabi"))
    }

    @Test
    fun `the oldest entries go past the limit`() {
        val registry = PlayerImageRegistry(limit = 2)
        registry.register("1", "One Player", "https://example.com/1.jpg")
        registry.register("2", "Two Player", "https://example.com/2.jpg")
        registry.register("3", "Three Player", "https://example.com/3.jpg")

        assertNull(registry.url(name = "One Player"))
        assertEquals("https://example.com/3.jpg", registry.url(name = "Three Player"))
    }

    @Test
    fun `entries survive an encode and decode, and saved ones never displace newer`() {
        val saved = PlayerImageRegistry()
        saved.register("1413", "Virat Kohli", kohli)
        saved.register("8", "Old Name", "https://example.com/old.jpg")

        val restored = PlayerImageRegistry()
        restored.register("8", "New Name", "https://example.com/new.jpg")
        restored.decode(saved.encode())
        restored.decode("not json")

        assertEquals(kohli, restored.url(name = "Virat Kohli"))
        assertEquals("https://example.com/new.jpg", restored.url(id = "8", name = "Old Name"))
        assertNull(restored.playerId("Old Name"))
    }
}

package com.tanush.bouncer.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CricBuzzScraperTest {

    @Test
    fun `normalizeNextFlightPayload unwraps typed json object payload`() {
        assertEquals("""{"id":2353}""", normalizeNextFlightPayload("""3:I{"id":2353}"""))
    }

    @Test
    fun `normalizeNextFlightPayload ignores non json flight directives`() {
        assertNull(normalizeNextFlightPayload("""1:HL["/_next/static/css/app.css",{"as":"style"}]"""))
    }

    @Test
    fun `unwrapNextFlightPayload extracts nested json array payload`() {
        val scriptContent = """[1,"2:[[[\"$\",\"link\",\"0\",{\"rel\":\"stylesheet\"}]]]"]"""

        assertEquals("""[[["$","link","0",{"rel":"stylesheet"}]]]""", unwrapNextFlightPayload(scriptContent))
    }

    @Test
    fun `unwrapNextFlightPayload ignores non json inner payloads`() {
        val scriptContent = """[1,"1:HL[\"/_next/static/css/app.css\",{\"as\":\"style\"}]"]"""

        assertNull(unwrapNextFlightPayload(scriptContent))
    }
}

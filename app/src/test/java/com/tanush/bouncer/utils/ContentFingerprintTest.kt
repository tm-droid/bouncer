package com.tanush.bouncer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ContentFingerprintTest {

    @Test
    fun `a known 32-bit hashCode collision no longer collides`() {
        // "Aa" and "BB" have identical String.hashCode() values, and the family composes: any
        // concatenation of these two-character blocks collides with every other. Under the old
        // `text.hashCode()` cache key, all 1024 of these ten-block strings shared one key, so a
        // cached commentary line was served for nine hundred-odd unrelated contexts.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(contentFingerprint("Aa"), contentFingerprint("BB"))

        val collisions = listOf("AaAaAa", "AaAaBB", "AaBBAa", "BBAaAa", "BBBBBB")
        assertEquals(1, collisions.map { it.hashCode() }.distinct().size)
        assertEquals(collisions.size, collisions.map { contentFingerprint(it) }.distinct().size)
    }

    @Test
    fun `part boundaries are respected`() {
        // `(a + b).hashCode()` could not distinguish these; a NUL separator can.
        assertNotEquals(contentFingerprint("ab", "c"), contentFingerprint("a", "bc"))
        assertEquals(contentFingerprint("ab", "c"), contentFingerprint("ab", "c"))
    }

    @Test
    fun `the fingerprint is deterministic and fixed-width`() {
        val a = contentFingerprint("over 18.5", "Bumrah to Maxwell", "119/4")
        val b = contentFingerprint("over 18.5", "Bumrah to Maxwell", "119/4")
        assertEquals(a, b)
        assertEquals(32, a.length)
        assertEquals(a, a.lowercase())
    }

    @Test
    fun `distinct inputs give distinct fingerprints`() {
        val keys = (1..2_000).map { contentFingerprint("ball $it", "context $it") }
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `empty and no parts are handled`() {
        assertEquals(32, contentFingerprint().length)
        assertNotEquals(contentFingerprint(), contentFingerprint(""))
        assertNotEquals(contentFingerprint(""), contentFingerprint("", ""))
    }

    @Test
    fun `hex output is safe for use inside a cache key`() {
        val fingerprint = contentFingerprint("anything with :colons: and spaces")
        assertEquals(true, fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
    }
}

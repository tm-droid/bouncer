package com.tanush.bouncer.managers

import com.tanush.bouncer.data.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [TEAM_VARIANT_SUFFIXES] and [baseSide] against `tools/team_variants.json`, which the weekly logo
 * tool (`tools/team_sources.py`) reads to fill `TeamLogos.json`. The device and the tool must agree
 * on what a variant's parent is, or a crest the tool filled by the rule is not the one the device
 * would pick. The fixture is declared as a Gradle test input, so editing it re-runs this test.
 */
class TeamVariantParityTest {

    private val fixture by lazy {
        AppJson.parseToJsonElement(locate("tools/team_variants.json").readText(Charsets.UTF_8)) as JsonObject
    }

    @Test
    fun `the suffix list is the fixture's, in order`() {
        val suffixes = fixture.getValue("suffixes").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(suffixes, TEAM_VARIANT_SUFFIXES)
    }

    @Test
    fun `baseSide gives exactly what the fixture says`() {
        val cases = fixture.getValue("cases").jsonArray
        assertTrue("the fixture must not be empty", cases.isNotEmpty())
        cases.forEach { row ->
            val (input, expected) = row.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("baseSide(\"$input\")", expected, baseSide(input))
        }
    }

    /** Walks up from the module directory; a missing file is a hard failure, never a skip. */
    private fun locate(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("could not find $relativePath by walking up from ${System.getProperty("user.dir")}")
    }
}

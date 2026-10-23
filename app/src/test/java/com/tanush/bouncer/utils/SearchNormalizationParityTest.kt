package com.tanush.bouncer.utils

import com.tanush.bouncer.data.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [normalizeForSearch] against the fixture `player-search`'s own test pins its JS copy to.
 *
 * The device re-scores the server's player hits with its own scorer, so the two must agree on
 * what a name normalises to; a mirrored rule is not a pinned rule. The fixture is declared as a
 * Gradle test input, so editing it re-runs this test.
 */
class SearchNormalizationParityTest {

    private val fixturePath = "appwrite/functions/api/test/player-search/fixtures/search-normalization.json"

    @Test
    fun `normalizeForSearch produces exactly what the shared fixture says`() {
        val cases = AppJson.parseToJsonElement(locate(fixturePath).readText(Charsets.UTF_8)) as JsonObject
        val rows = cases.getValue("cases").jsonArray
        assertTrue("the fixture must not be empty", rows.isNotEmpty())
        rows.forEach { row ->
            val (input, expected) = row.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("normalizeForSearch(\"$input\")", expected, normalizeForSearch(input))
        }
    }

    @Test
    fun `editTolerance produces exactly what the shared fixture says`() {
        val cases = AppJson.parseToJsonElement(locate(fixturePath).readText(Charsets.UTF_8)) as JsonObject
        val rows = cases.getValue("tolerance").jsonArray
        assertTrue("the fixture must not be empty", rows.isNotEmpty())
        rows.forEach { row ->
            val (word, expected) = row.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("editTolerance(\"$word\")", expected.toInt(), SearchScore.editTolerance(word))
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

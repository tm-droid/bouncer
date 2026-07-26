package com.tanush.bouncer.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BouncerDatabase::class.java
    )

    @Test
    fun migrate5to7_highlightsHasNoIndex() {
        // Create v5 DB manually, add the stale index that some v5 users have
        val db = helper.createDatabase("test-db", 5)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_highlights_matched_bouncer_match_id ON highlights(matchedBouncerMatchId)")
        db.close()

        // Run migrations 5→6→7; validates schema against current entities after migration
        val migratedDb = helper.runMigrationsAndValidate(
            "test-db", 7, true,
            BouncerDatabase.MIGRATION_5_6, BouncerDatabase.MIGRATION_6_7
        )
        migratedDb.close()
        // If validation passes, the schema matches HighlightEntity (no index). Test passes.
    }

    @Test
    fun migrate8to9_createsExpectedIndices() {
        helper.createDatabase("test-db-8", 8).close()

        val db = helper.runMigrationsAndValidate(
            "test-db-8", 9, true,
            BouncerDatabase.MIGRATION_8_9
        )

        // Verify all three indices are present in sqlite_master.
        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN (?, ?, ?)",
            arrayOf(
                "index_highlights_matchedBouncerMatchId",
                "index_highlights_fetchedAt",
                "index_ai_commentary_cache_timestamp",
            )
        )
        val count = cursor.count
        cursor.close()
        db.close()

        assertEquals("Expected 3 indices after migration 8→9, found $count", 3, count)
    }

    @Test
    fun migrate9to10_addsLastAccessedAtAndPreservesRows() {
        val v9 = helper.createDatabase("test-db-9", 9)
        v9.execSQL(
            "INSERT INTO ai_commentary_cache (cacheKey, commentary, timestamp) VALUES ('k1', 'text', 123)"
        )
        v9.close()

        val db = helper.runMigrationsAndValidate(
            "test-db-9", 10, true,
            BouncerDatabase.MIGRATION_9_10
        )

        // Existing rows survive and default to 0, so they are evicted first — correct, since
        // nothing has read them since the upgrade.
        val rows = db.query("SELECT commentary, lastAccessedAt FROM ai_commentary_cache WHERE cacheKey = 'k1'")
        rows.moveToFirst()
        assertEquals("text", rows.getString(0))
        assertEquals(0L, rows.getLong(1))
        rows.close()

        val index = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name = ?",
            arrayOf("index_ai_commentary_cache_lastAccessedAt")
        )
        val indexCount = index.count
        index.close()
        db.close()

        assertEquals("Expected the lastAccessedAt index after migration 9→10", 1, indexCount)
    }
}

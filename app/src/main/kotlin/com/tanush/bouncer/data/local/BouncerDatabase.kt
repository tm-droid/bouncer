package com.tanush.bouncer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tanush.bouncer.data.local.dao.AICacheDao
import com.tanush.bouncer.data.local.dao.CommentaryDao
import com.tanush.bouncer.data.local.dao.HighlightDao
import com.tanush.bouncer.data.local.dao.HistoricalMatchCacheDao
import com.tanush.bouncer.data.local.dao.MatchDao
import com.tanush.bouncer.data.local.dao.PlayerCacheDao
import com.tanush.bouncer.data.local.dao.SeriesCacheDao
import com.tanush.bouncer.data.local.entities.AICommentaryCacheEntity
import com.tanush.bouncer.data.local.entities.CommentaryEntity
import com.tanush.bouncer.data.local.entities.HighlightEntity
import com.tanush.bouncer.data.local.entities.HistoricalMatchCacheEntity
import com.tanush.bouncer.data.local.entities.HistoricalMatchFtsEntity
import com.tanush.bouncer.data.local.entities.MatchEntity
import com.tanush.bouncer.data.local.entities.PlayerCacheEntity
import com.tanush.bouncer.data.local.entities.PlayerFtsEntity
import com.tanush.bouncer.data.local.entities.SeriesCacheEntity
import com.tanush.bouncer.data.local.entities.SeriesFtsEntity

@Database(
    entities = [
        MatchEntity::class,
        CommentaryEntity::class,
        PlayerCacheEntity::class,
        PlayerFtsEntity::class,
        SeriesCacheEntity::class,
        SeriesFtsEntity::class,
        HistoricalMatchCacheEntity::class,
        HistoricalMatchFtsEntity::class,
        AICommentaryCacheEntity::class,
        HighlightEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class BouncerDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao
    abstract fun commentaryDao(): CommentaryDao
    abstract fun playerCacheDao(): PlayerCacheDao
    abstract fun seriesCacheDao(): SeriesCacheDao
    abstract fun historicalMatchCacheDao(): HistoricalMatchCacheDao
    abstract fun aiCacheDao(): AICacheDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        @Volatile
        private var INSTANCE: BouncerDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS player_cache (playerId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, country TEXT NOT NULL, role TEXT NOT NULL, teamName TEXT NOT NULL, statsJson TEXT NOT NULL, lastUpdated INTEGER NOT NULL)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS player_fts USING fts4(playerId, name, country, role, teamName)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS player_cache_bu BEFORE UPDATE ON player_cache BEGIN DELETE FROM player_fts WHERE docid=old.rowid; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS player_cache_bd BEFORE DELETE ON player_cache BEGIN DELETE FROM player_fts WHERE docid=old.rowid; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS player_cache_au AFTER UPDATE ON player_cache BEGIN INSERT INTO player_fts(docid, playerId, name, country, role, teamName) VALUES(new.rowid, new.playerId, new.name, new.country, new.role, new.teamName); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS player_cache_ai AFTER INSERT ON player_cache BEGIN INSERT INTO player_fts(docid, playerId, name, country, role, teamName) VALUES(new.rowid, new.playerId, new.name, new.country, new.role, new.teamName); END")
                db.execSQL("CREATE TABLE IF NOT EXISTS series_cache (seriesId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, startDate TEXT NOT NULL, endDate TEXT NOT NULL, lastUpdated INTEGER NOT NULL)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS series_fts USING fts4(seriesId, name, category)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS series_cache_bu BEFORE UPDATE ON series_cache BEGIN DELETE FROM series_fts WHERE docid=old.rowid; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS series_cache_bd BEFORE DELETE ON series_cache BEGIN DELETE FROM series_fts WHERE docid=old.rowid; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS series_cache_au AFTER UPDATE ON series_cache BEGIN INSERT INTO series_fts(docid, seriesId, name, category) VALUES(new.rowid, new.seriesId, new.name, new.category); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS series_cache_ai AFTER INSERT ON series_cache BEGIN INSERT INTO series_fts(docid, seriesId, name, category) VALUES(new.rowid, new.seriesId, new.name, new.category); END")
                db.execSQL("CREATE TABLE IF NOT EXISTS historical_match_cache (matchId TEXT NOT NULL PRIMARY KEY, matchTitle TEXT NOT NULL, teams TEXT NOT NULL, venue TEXT NOT NULL, city TEXT NOT NULL, date TEXT NOT NULL, season TEXT NOT NULL, matchType TEXT NOT NULL, playerOfMatch TEXT NOT NULL, lastUpdated INTEGER NOT NULL)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS historical_match_fts USING fts4(matchId, matchTitle, teams, venue, city, season, matchType, playerOfMatch)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS historical_match_cache_bu BEFORE UPDATE ON historical_match_cache BEGIN DELETE FROM historical_match_fts WHERE docid=old.rowid; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS historical_match_cache_bd BEFORE DELETE ON historical_match_cache BEGIN DELETE FROM historical_match_fts WHERE docid=old.rowid; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS historical_match_cache_au AFTER UPDATE ON historical_match_cache BEGIN INSERT INTO historical_match_fts(docid, matchId, matchTitle, teams, venue, city, season, matchType, playerOfMatch) VALUES(new.rowid, new.matchId, new.matchTitle, new.teams, new.venue, new.city, new.season, new.matchType, new.playerOfMatch); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS historical_match_cache_ai AFTER INSERT ON historical_match_cache BEGIN INSERT INTO historical_match_fts(docid, matchId, matchTitle, teams, venue, city, season, matchType, playerOfMatch) VALUES(new.rowid, new.matchId, new.matchTitle, new.teams, new.venue, new.city, new.season, new.matchType, new.playerOfMatch); END")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS ai_commentary_cache (cacheKey TEXT NOT NULL PRIMARY KEY, commentary TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, imgUrl TEXT NOT NULL, embedUrl TEXT, fallbackUrl TEXT, source TEXT NOT NULL, channel TEXT NOT NULL, type TEXT NOT NULL, matchedBouncerMatchId TEXT, iccMatchId TEXT, iplMatchId TEXT, leagueName TEXT NOT NULL, homeTeam TEXT NOT NULL, awayTeam TEXT NOT NULL, matchStartDate TEXT NOT NULL, fetchedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_highlights_matched_bouncer_match_id ON highlights(matchedBouncerMatchId)")
            }
        }

        // Index was created in MIGRATION_3_4 but removed from HighlightEntity — drop to match entity schema.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS idx_highlights_matched_bouncer_match_id")
            }
        }

        // Add indexed isLive column to matches so getLiveMatches() stops doing a LIKE '%live%' scan.
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE matches ADD COLUMN isLive INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE matches SET isLive = 1 WHERE LOWER(status) LIKE '%live%' " +
                        "OR LOWER(currentStatus) LIKE '%live%' OR LOWER(status) LIKE '%in progress%' " +
                        "OR LOWER(currentStatus) LIKE '%in progress%'"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_matches_is_live ON matches(isLive)")
            }
        }

        // Users at v5 who had the stale index survive (it was supposed to be dropped in MIGRATION_4_5)
        // and then upgraded to v6 never had the index dropped — Room's post-migration schema validation
        // crashes the app because HighlightEntity has no @Index. Drop idempotently to fix all affected users.
        // Fixes ANDROID-1Q.
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS idx_highlights_matched_bouncer_match_id")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_matches_last_updated ON matches(lastUpdated)")
            }
        }

        // Add new migrations here; Room will apply them in order on upgrade.
        private val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

        fun getInstance(context: Context): BouncerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BouncerDatabase::class.java,
                    "bouncer_db"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

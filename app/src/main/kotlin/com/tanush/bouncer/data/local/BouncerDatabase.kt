package com.tanush.bouncer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tanush.bouncer.data.local.dao.CommentaryDao
import com.tanush.bouncer.data.local.dao.HistoricalMatchCacheDao
import com.tanush.bouncer.data.local.dao.MatchDao
import com.tanush.bouncer.data.local.dao.PlayerCacheDao
import com.tanush.bouncer.data.local.dao.SeriesCacheDao
import com.tanush.bouncer.data.local.entities.CommentaryEntity
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
        HistoricalMatchFtsEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class BouncerDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao
    abstract fun commentaryDao(): CommentaryDao
    abstract fun playerCacheDao(): PlayerCacheDao
    abstract fun seriesCacheDao(): SeriesCacheDao
    abstract fun historicalMatchCacheDao(): HistoricalMatchCacheDao

    companion object {
        @Volatile
        private var INSTANCE: BouncerDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS player_cache (playerId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, country TEXT NOT NULL, role TEXT NOT NULL, teamName TEXT NOT NULL, statsJson TEXT NOT NULL, lastUpdated INTEGER NOT NULL)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS player_fts USING fts4(content=player_cache, name, country, role, teamName)")
                db.execSQL("CREATE TABLE IF NOT EXISTS series_cache (seriesId TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, startDate TEXT NOT NULL, endDate TEXT NOT NULL, lastUpdated INTEGER NOT NULL)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS series_fts USING fts4(content=series_cache, name, category)")
                db.execSQL("CREATE TABLE IF NOT EXISTS historical_match_cache (matchId TEXT NOT NULL PRIMARY KEY, matchTitle TEXT NOT NULL, teams TEXT NOT NULL, venue TEXT NOT NULL, city TEXT NOT NULL, date TEXT NOT NULL, season TEXT NOT NULL, matchType TEXT NOT NULL, playerOfMatch TEXT NOT NULL, lastUpdated INTEGER NOT NULL)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS historical_match_fts USING fts4(content=historical_match_cache, matchTitle, teams, venue, city, season, playerOfMatch)")
            }
        }

        // Add new migrations here; Room will apply them in order on upgrade.
        private val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)

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

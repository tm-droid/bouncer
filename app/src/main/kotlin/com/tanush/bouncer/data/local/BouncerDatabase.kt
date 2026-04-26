package com.tanush.bouncer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tanush.bouncer.data.local.dao.CommentaryDao
import com.tanush.bouncer.data.local.dao.MatchDao
import com.tanush.bouncer.data.local.entities.CommentaryEntity
import com.tanush.bouncer.data.local.entities.MatchEntity

@Database(
    entities = [MatchEntity::class, CommentaryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class BouncerDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao
    abstract fun commentaryDao(): CommentaryDao

    companion object {
        @Volatile
        private var INSTANCE: BouncerDatabase? = null

        // Add new migrations here; Room will apply them in order on upgrade.
        private val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

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

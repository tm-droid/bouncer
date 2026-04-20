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
    exportSchema = false
)
abstract class BouncerDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao
    abstract fun commentaryDao(): CommentaryDao

    companion object {
        @Volatile
        private var INSTANCE: BouncerDatabase? = null

        fun getInstance(context: Context): BouncerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BouncerDatabase::class.java,
                    "bouncer_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

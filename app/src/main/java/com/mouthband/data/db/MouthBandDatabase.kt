package com.mouthband.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/** Local database (spec §4). Schemas are exported to app/schemas for migration tests. */
@Database(
    entities = [SongEntity::class, TrackEntity::class, ExportEntity::class],
    version = MouthBandDatabase.VERSION,
    exportSchema = true,
)
abstract class MouthBandDatabase : RoomDatabase() {
    abstract fun songs(): SongDao

    abstract fun tracks(): TrackDao

    abstract fun exports(): ExportDao

    companion object {
        const val VERSION = 1
        const val NAME = "mouthband.db"

        /** Every migration, oldest first. Add one per schema version bump. */
        val MIGRATIONS = emptyArray<Migration>()

        fun create(context: Context, name: String = NAME): MouthBandDatabase =
            Room.databaseBuilder(context.applicationContext, MouthBandDatabase::class.java, name)
                .apply { MIGRATIONS.forEach { addMigrations(it) } }
                .build()
    }
}

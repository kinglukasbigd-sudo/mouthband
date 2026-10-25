package com.mouthband.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mouthband.data.db.MouthBandDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test scaffold (spec §4). Every schema version is exported to `app/schemas`. When the
 * version is bumped: add the Migration to [MouthBandDatabase.MIGRATIONS], then add a test that
 * creates the old version with [helper], inserts rows with its SQL, and migrates — the
 * existing [migratesEveryVersionToTheCurrentSchema] already validates the whole chain.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MouthBandDatabase::class.java)

    @Test
    fun migratesEveryVersionToTheCurrentSchema() {
        helper.createDatabase(TEST_DB, FIRST_VERSION).use { db ->
            db.execSQL(
                "INSERT INTO songs (id, title, bpm, keyRoot, keyMode, loopBars, kitId, createdAt, updatedAt, " +
                    "coachStep, arrangementJson) VALUES (1, 'Kept', 110, 0, 'MAJOR', 2, 'POP', 1, 1, 3, " +
                    "'{\"sections\":[]}')",
            )
        }
        // Runs MIGRATIONS from version 1 up to VERSION and checks the result against the export.
        helper.runMigrationsAndValidate(TEST_DB, MouthBandDatabase.VERSION, true, *MouthBandDatabase.MIGRATIONS)
            .close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = MouthBandDatabase.create(context, TEST_DB)
        try {
            val song = runBlocking { db.songs().get(1) }
            assertEquals("Kept", song?.title)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val FIRST_VERSION = 1
    }
}

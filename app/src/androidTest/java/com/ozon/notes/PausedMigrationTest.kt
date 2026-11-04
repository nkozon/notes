package com.ozon.notes

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PausedMigrationTest {
    @Test
    fun migrationPreservesLegacyEntriesAndDefaultsNewState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .callback(object : SupportSQLiteOpenHelper.Callback(25) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE list_entries (
                                id TEXT NOT NULL PRIMARY KEY, parentId TEXT,
                                isCurrentlyWatching INTEGER NOT NULL DEFAULT 0,
                                currentProgress INTEGER, totalProgress INTEGER, progressUnit TEXT
                            )
                        """.trimIndent())
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO list_entries VALUES ('parent', NULL, 0, NULL, NULL, NULL)")
            db.execSQL("INSERT INTO list_entries VALUES ('child', 'parent', 1, 3, 12, 'episodes')")
            NoteDatabase.MIGRATION_25_26.migrate(db)
            db.query("SELECT * FROM list_entries ORDER BY id").use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("child", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                assertEquals("parent", cursor.getString(cursor.getColumnIndexOrThrow("parentId")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isCurrentlyWatching")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isPaused")))
                assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("currentProgress")))
                assertEquals(12, cursor.getInt(cursor.getColumnIndexOrThrow("totalProgress")))
                assertEquals("episodes", cursor.getString(cursor.getColumnIndexOrThrow("progressUnit")))
                assertTrue(cursor.moveToNext())
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isPaused")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isCurrentlyWatching")))
            }
        } finally {
            helper.close()
        }
    }
}

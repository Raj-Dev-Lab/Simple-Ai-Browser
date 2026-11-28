package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simpleaibrowser.sab.data.SabDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * THE upgrade-path regression test for the startup crash users hit after v1.3.x.
 *
 * A real upgrading device has a database created by Room from the v1.3 entity set
 * (user_version = 3). Opening that database runs MIGRATION_1_2 … MIGRATION_4_5 and then
 * Room validates the migrated schema against the current entities. Because
 * MIGRATION_3_4 adds NOT NULL columns with DEFAULT clauses, the entities MUST declare
 * matching @ColumnInfo(defaultValue = …) — otherwise Room throws
 * "Migration didn't properly handle downloads" on EVERY launch, permanently locking
 * upgrading users out of the app. This test reproduces that exact path on a real SQLite
 * file and asserts the upgrade succeeds with the user's data preserved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseUpgradeTest {

    @Test
    fun `upgrade from v1_3 database opens cleanly and preserves user data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("sab_browser.db").absolutePath)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // --- Build exactly the database a v1.3 app had on disk (user_version = 3). ---
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null)
        raw.execSQL(
            "CREATE TABLE `speed_dials` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `accentHex` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL)"
        )
        raw.execSQL(
            "CREATE TABLE `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `folder` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        raw.execSQL(
            "CREATE TABLE `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `visitedAt` INTEGER NOT NULL)"
        )
        // downloads as of v3 = MIGRATION_1_2 table + localUri column from MIGRATION_2_3
        raw.execSQL(
            "CREATE TABLE `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`systemDownloadId` INTEGER NOT NULL, `fileName` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                "`mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`localUri` TEXT)"
        )
        raw.execSQL(
            "CREATE TABLE `saved_tabs` (`tabId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`url` TEXT NOT NULL, `islandId` TEXT, `isActive` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`tabId`))"
        )
        raw.execSQL(
            "CREATE TABLE `saved_islands` (`islandId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`colorId` TEXT NOT NULL, `isCollapsed` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`islandId`))"
        )
        raw.execSQL(
            "CREATE TABLE `extension_records` (`extensionId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`sourceType` TEXT NOT NULL, `sourceRef` TEXT NOT NULL, `installedVersion` TEXT NOT NULL, " +
                "`latestVersion` TEXT, `latestDownloadUrl` TEXT, `homepageUrl` TEXT, `iconUrl` TEXT, " +
                "`autoUpdateEnabled` INTEGER NOT NULL, `lastCheckedAt` INTEGER NOT NULL, " +
                "`lastUpdatedAt` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`extensionId`))"
        )
        // The user's data an upgrade must preserve.
        raw.execSQL("INSERT INTO bookmarks (title, url, folder, createdAt) VALUES ('Docs', 'https://docs.example.com', 'General', 1000)")
        raw.execSQL("INSERT INTO history (title, url, visitedAt) VALUES ('Old visit', 'https://old.example.com', 2000)")
        raw.execSQL(
            "INSERT INTO downloads (systemDownloadId, fileName, url, mimeType, sizeBytes, createdAt, localUri) " +
                "VALUES (42, 'old-file.apk', 'https://example.com/old.apk', 'application/vnd.android.package-archive', 1024, 3000, '/old/path')"
        )
        raw.version = 3
        raw.close()

        // --- Open with the production builder: migrations 1→2→3→4→5 must validate. ---
        // Before the defaultValue fix this line threw
        // "IllegalStateException: Migration didn't properly handle downloads".
        val db = SabDatabase.getInstance(context)
        val conn = db.openHelper.writableDatabase
        assertEquals(5, conn.version)

        // User data preserved by the migrations.
        conn.query("SELECT title, url FROM bookmarks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Docs", c.getString(0))
            assertEquals("https://docs.example.com", c.getString(1))
        }
        conn.query("SELECT title FROM history").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Old visit", c.getString(0))
        }

        // The migrated download row: legacy taskId backfill and defaults from MIGRATION_3_4.
        conn.query("SELECT fileName, taskId, kind, status, downloadedBytes, completedAt FROM downloads").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("old-file.apk", c.getString(0))
            assertEquals("legacy-1", c.getString(1))
            assertEquals("file", c.getString(2))
            assertEquals("completed", c.getString(3))
            assertEquals(1024L, c.getLong(4))
            assertEquals(3000L, c.getLong(5))
        }

        // New inserts through the migrated schema work.
        kotlinx.coroutines.runBlocking {
            db.speedDialDao().insert(
                com.simpleaibrowser.sab.data.SpeedDialEntity(
                    title = "New", url = "https://new.example.com", accentHex = 0xFF3366FF
                )
            )
        }
        conn.query("SELECT COUNT(*) FROM speed_dials").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        conn.close()
        db.close()
    }
}

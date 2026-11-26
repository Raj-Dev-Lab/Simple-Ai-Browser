package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.simpleaibrowser.sab.core.browser.ExtensionSource
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.data.BrowserPreferences
import com.simpleaibrowser.sab.data.BrowserRepository
import com.simpleaibrowser.sab.data.SabDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationAndRepoTest {

    private lateinit var context: Context
    private lateinit var db: SabDatabase
    private lateinit var preferences: BrowserPreferences
    private lateinit var repository: BrowserRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = BrowserPreferences(context)
        repository = BrowserRepository(db, preferences)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `database migrates cleanly from v1 through v2 to v3 preserving user data`() {
        val dbName = "migration_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val callbackV1 = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `speed_dials` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `accentHex` INTEGER NOT NULL, `position` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `folder` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `visitedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `bookmarks` (`title`, `url`, `folder`, `createdAt`) VALUES ('Mozilla', 'https://mozilla.org', 'General', 1000)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callbackV1)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migrated = Room.databaseBuilder(context, SabDatabase::class.java, dbName)
            .addMigrations(SabDatabase.MIGRATION_1_2, SabDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val bookmarks = migrated.bookmarkDao().observeAll().first()
            assertEquals(1, bookmarks.size)
            assertEquals("https://mozilla.org", bookmarks.first().url)

            // Verify new v2/v3 tables & columns work cleanly after migration
            migrated.downloadDao().insert(
                com.simpleaibrowser.sab.data.DownloadEntity(
                    systemDownloadId = -1L,
                    fileName = "archive.zip",
                    url = "https://example.com/archive.zip",
                    localUri = "content://downloads/public_downloads/1"
                )
            )
            val downloads = migrated.downloadDao().observeAll().first()
            assertEquals(1, downloads.size)
            assertEquals("content://downloads/public_downloads/1", downloads.first().localUri)

            migrated.extensionRecordDao().upsert(
                com.simpleaibrowser.sab.data.ExtensionRecordEntity(
                    extensionId = "ext@test",
                    name = "Test Ext",
                    sourceType = "github",
                    sourceRef = "owner/repo",
                    installedVersion = "1.0.0"
                )
            )
            assertNotNull(migrated.extensionRecordDao().findById("ext@test"))
        }

        migrated.close()
        File(dbFile.path).delete()
    }

    @Test
    fun `recordHistory deduplicates same url and filters internal pages`() = runBlocking {
        repository.recordHistory(SAB_START_URL, "Start")
        repository.recordHistory("about:config", "Config")
        assertTrue(repository.history.first().isEmpty())

        repository.recordHistory("https://example.com/docs", "Initial Title")
        repository.recordHistory("https://other.org", "Other")
        repository.recordHistory("https://example.com/docs", "Updated Title")

        val items = repository.history.first()
        assertEquals(2, items.size)
        assertEquals("https://example.com/docs", items.first().url)
        assertEquals("Updated Title", items.first().title)
    }

    @Test
    fun `extension repository CRUD tracks source metadata, auto-update toggle, and check results`() = runBlocking {
        repository.upsertExtensionRecord(
            extensionId = "uBlock0@raymondhill.net",
            name = "uBlock Origin",
            source = ExtensionSource.AMO,
            sourceRef = "ublock-origin",
            installedVersion = "1.60.0",
            homepageUrl = "https://github.com/gorhill/uBlock",
            iconUrl = "https://example.com/icon.png"
        )

        val inserted = repository.findExtensionRecord("uBlock0@raymondhill.net")
        assertNotNull(inserted)
        assertEquals("amo", inserted?.sourceType)
        assertTrue(inserted?.autoUpdateEnabled == true)

        repository.setExtensionAutoUpdate("uBlock0@raymondhill.net", false)
        repository.updateExtensionCheckMetadata(
            extensionId = "uBlock0@raymondhill.net",
            latestVersion = "1.61.2",
            downloadUrl = "https://addons.mozilla.org/file.xpi",
            error = null
        )

        val updated = repository.findExtensionRecord("uBlock0@raymondhill.net")
        assertFalse(updated?.autoUpdateEnabled == true)
        assertEquals("1.61.2", updated?.latestVersion)
        assertEquals("https://addons.mozilla.org/file.xpi", updated?.latestDownloadUrl)
        assertTrue((updated?.lastCheckedAt ?: 0L) > 0L)

        repository.deleteExtensionRecord("uBlock0@raymondhill.net")
        assertNull(repository.findExtensionRecord("uBlock0@raymondhill.net"))
    }

    @Test
    fun `preferences persist extension auto update and clamp default zoom`() = runBlocking {
        val initial = preferences.settingsFlow.first()
        assertTrue(initial.autoUpdateExtensions)

        preferences.setAutoUpdateExtensions(false)
        preferences.setDefaultTextZoom(250)
        preferences.setLastExtensionUpdateCheckAt(123456789L)

        val updated = preferences.settingsFlow.first()
        assertFalse(updated.autoUpdateExtensions)
        assertEquals(160, updated.defaultTextZoom)
        assertEquals(123456789L, updated.lastExtensionUpdateCheckAt)
    }
}

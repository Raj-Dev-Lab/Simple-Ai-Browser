package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.simpleaibrowser.sab.core.browser.DownloadKind
import com.simpleaibrowser.sab.core.browser.DownloadManagerSettings
import com.simpleaibrowser.sab.core.browser.ExtensionSource
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.core.browser.StorageLocationMode
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
    fun `database migrates cleanly from v1 through v4 preserving user data`() {
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
            .addMigrations(SabDatabase.MIGRATION_1_2, SabDatabase.MIGRATION_2_3, SabDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val bookmarks = migrated.bookmarkDao().observeAll().first()
            assertEquals(1, bookmarks.size)
            assertEquals("https://mozilla.org", bookmarks.first().url)

            migrated.downloadDao().insert(
                com.simpleaibrowser.sab.data.DownloadEntity(
                    taskId = "task-migrated-1",
                    systemDownloadId = -1L,
                    fileName = "archive.zip",
                    url = "https://example.com/archive.zip",
                    localUri = "content://downloads/public_downloads/1",
                    kind = DownloadKind.ARCHIVE.id
                )
            )
            val downloads = migrated.downloadDao().observeAll().first()
            assertEquals(1, downloads.size)
            assertEquals("content://downloads/public_downloads/1", downloads.first().localUri)
            assertEquals("task-migrated-1", downloads.first().taskId)

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
    fun `database migrates v3 downloads table to v4 backfilling taskId downloadedBytes and completedAt`() {
        val dbName = "migration_v3_v4_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val callbackV3 = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                SabDatabase.MIGRATION_1_2.migrate(db)
                SabDatabase.MIGRATION_2_3.migrate(db)
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
                    """
                    INSERT INTO `downloads` (`systemDownloadId`, `fileName`, `url`, `mimeType`, `sizeBytes`, `createdAt`, `localUri`)
                    VALUES (42, 'ubuntu.iso', 'https://releases.ubuntu.com/ubuntu.iso', 'application/octet-stream', 4096, 987654, 'content://downloads/42')
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callbackV3)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migrated = Room.databaseBuilder(context, SabDatabase::class.java, dbName)
            .addMigrations(SabDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val all = migrated.downloadDao().getAll()
            assertEquals(1, all.size)
            val row = all.first()
            assertTrue(row.taskId.startsWith("legacy-"))
            assertEquals(4096L, row.downloadedBytes)
            assertEquals(987654L, row.completedAt)
            assertEquals("completed", row.status)
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

    @Test
    fun `download manager preferences persist and clamp bounds cleanly`() = runBlocking {
        preferences.updateDownloadSettings {
            DownloadManagerSettings(
                askBeforeDownloading = true,
                defaultConnectionsPerDownload = 64,
                maxConcurrentDownloads = 99,
                wifiOnlyDownloads = true,
                speedLimitKbps = 2048,
                autoResumeOnReconnect = false,
                storageLocationMode = StorageLocationMode.CUSTOM_TREE,
                customTreeUri = "content://com.android.externalstorage.documents/tree/primary%3AMovies",
                customTreeLabel = "Movies",
                autoOrganizeByType = true,
                notifyOnComplete = false,
                torrentSequentialDefault = true,
                torrentSeedAfterComplete = true,
                torrentMaxConnections = 999,
                torrentDownloadLimitKbps = 4096,
                torrentUploadLimitKbps = 256,
                torrentEnableDht = false,
                customUserAgent = "SAB-Downloader/1.4"
            )
        }

        val loaded = preferences.settingsFlow.first().downloadSettings
        assertTrue(loaded.askBeforeDownloading)
        assertEquals(16, loaded.defaultConnectionsPerDownload)
        assertEquals(6, loaded.maxConcurrentDownloads)
        assertTrue(loaded.wifiOnlyDownloads)
        assertEquals(2048, loaded.speedLimitKbps)
        assertFalse(loaded.autoResumeOnReconnect)
        assertEquals(StorageLocationMode.CUSTOM_TREE, loaded.storageLocationMode)
        assertEquals("Movies", loaded.customTreeLabel)
        assertTrue(loaded.autoOrganizeByType)
        assertFalse(loaded.notifyOnComplete)
        assertTrue(loaded.torrentSequentialDefault)
        assertTrue(loaded.torrentSeedAfterComplete)
        assertEquals(500, loaded.torrentMaxConnections)
        assertEquals(4096, loaded.torrentDownloadLimitKbps)
        assertEquals(256, loaded.torrentUploadLimitKbps)
        assertFalse(loaded.torrentEnableDht)
        assertEquals("SAB-Downloader/1.4", loaded.customUserAgent)
    }
}

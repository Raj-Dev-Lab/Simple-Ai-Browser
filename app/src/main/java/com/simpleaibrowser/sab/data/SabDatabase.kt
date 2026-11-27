package com.simpleaibrowser.sab.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "speed_dials")
data class SpeedDialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val accentHex: Long,
    val position: Int = 0
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val folder: String = "General",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val systemDownloadId: Long = -1L,
    val fileName: String,
    val url: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = -1L,
    val createdAt: Long = System.currentTimeMillis(),
    val localUri: String? = null,
    val taskId: String = UUID.randomUUID().toString(),
    val kind: String = "file",
    val status: String = "completed",
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val connectionsCount: Int = 1,
    val supportsRange: Boolean = false,
    val stagingPath: String = "",
    val publicUri: String? = null,
    val subfolder: String = "",
    val etag: String? = null,
    val segmentsJson: String? = null,
    val torrentInfoHash: String? = null,
    val torrentFilesJson: String? = null,
    val torrentPeers: Int = 0,
    val torrentSeeds: Int = 0,
    val sequentialTorrent: Boolean = false,
    val wifiOnly: Boolean = false,
    val errorMessage: String? = null,
    val sha256: String? = null,
    val completedAt: Long = 0L
)

@Entity(tableName = "saved_tabs")
data class SavedTabEntity(
    @PrimaryKey val tabId: String,
    val title: String,
    val url: String,
    val islandId: String? = null,
    val isActive: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "saved_islands")
data class SavedIslandEntity(
    @PrimaryKey val islandId: String,
    val name: String,
    val colorId: String,
    val isCollapsed: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "extension_records")
data class ExtensionRecordEntity(
    @PrimaryKey val extensionId: String,
    val name: String,
    val sourceType: String, // "amo" | "github" | "direct" | "builtin"
    val sourceRef: String,  // AMO guid/slug, GitHub "owner/repo", or direct XPI URL
    val installedVersion: String,
    val latestVersion: String? = null,
    val latestDownloadUrl: String? = null,
    val homepageUrl: String? = null,
    val iconUrl: String? = null,
    val autoUpdateEnabled: Boolean = true,
    val lastCheckedAt: Long = 0L,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val lastError: String? = null
)

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dials ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<SpeedDialEntity>>

    @Query("SELECT COUNT(*) FROM speed_dials")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SpeedDialEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SpeedDialEntity)

    @Query("DELETE FROM speed_dials WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 250")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url ORDER BY visitedAt DESC LIMIT 1")
    suspend fun findLatestByUrl(url: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity)

    @Query("UPDATE history SET title = :title, visitedAt = :visitedAt WHERE id = :id")
    suspend fun updateVisit(id: Long, title: String, visitedAt: Long)

    @Query("DELETE FROM history WHERE url = :url AND id != :keepId")
    suspend fun deleteOlderDuplicates(url: String, keepId: Long)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE taskId = :taskId LIMIT 1")
    suspend fun findByTaskId(taskId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE torrentInfoHash = :infoHash LIMIT 1")
    suspend fun findByInfoHash(infoHash: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadEntity): Long

    @Query(
        "UPDATE downloads SET fileName = :fileName, sizeBytes = :sizeBytes, downloadedBytes = :downloadedBytes, " +
            "speedBytesPerSec = :speedBytesPerSec, etaSeconds = :etaSeconds, status = :status, " +
            "supportsRange = :supportsRange, etag = :etag, segmentsJson = :segmentsJson, " +
            "torrentPeers = :torrentPeers, torrentSeeds = :torrentSeeds, torrentFilesJson = :torrentFilesJson, " +
            "torrentInfoHash = :torrentInfoHash, errorMessage = :errorMessage WHERE taskId = :taskId"
    )
    suspend fun updateRuntimeState(
        taskId: String,
        fileName: String,
        sizeBytes: Long,
        downloadedBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long,
        status: String,
        supportsRange: Boolean,
        etag: String?,
        segmentsJson: String?,
        torrentPeers: Int,
        torrentSeeds: Int,
        torrentFilesJson: String?,
        torrentInfoHash: String?,
        errorMessage: String?
    )

    @Query(
        "UPDATE downloads SET status = :status, speedBytesPerSec = 0, etaSeconds = -1, errorMessage = :errorMessage " +
            "WHERE taskId = :taskId"
    )
    suspend fun updateStatus(taskId: String, status: String, errorMessage: String? = null)

    @Query(
        "UPDATE downloads SET status = 'completed', downloadedBytes = :totalBytes, sizeBytes = :totalBytes, " +
            "speedBytesPerSec = 0, etaSeconds = 0, localUri = :localUri, publicUri = :publicUri, " +
            "stagingPath = :stagingPath, sha256 = :sha256, completedAt = :completedAt, errorMessage = NULL " +
            "WHERE taskId = :taskId"
    )
    suspend fun markCompleted(
        taskId: String,
        totalBytes: Long,
        localUri: String?,
        publicUri: String?,
        stagingPath: String,
        sha256: String?,
        completedAt: Long
    )

    @Query("UPDATE downloads SET sequentialTorrent = :sequential WHERE taskId = :taskId")
    suspend fun setTorrentSequential(taskId: String, sequential: Boolean)

    @Query("UPDATE downloads SET torrentFilesJson = :filesJson WHERE taskId = :taskId")
    suspend fun updateTorrentFilesJson(taskId: String, filesJson: String)

    @Query("UPDATE downloads SET fileName = :fileName, stagingPath = :stagingPath, localUri = :localUri, publicUri = :publicUri WHERE taskId = :taskId")
    suspend fun updateFileLocations(
        taskId: String,
        fileName: String,
        stagingPath: String,
        localUri: String?,
        publicUri: String?
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('completed', 'failed', 'cancelled')")
    suspend fun clearFinished()

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}

@Dao
interface SessionSnapshotDao {
    @Query("SELECT * FROM saved_tabs ORDER BY sortOrder ASC")
    suspend fun getSavedTabs(): List<SavedTabEntity>

    @Query("SELECT * FROM saved_islands ORDER BY sortOrder ASC")
    suspend fun getSavedIslands(): List<SavedIslandEntity>

    @Query("DELETE FROM saved_tabs")
    suspend fun clearSavedTabs()

    @Query("DELETE FROM saved_islands")
    suspend fun clearSavedIslands()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<SavedTabEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIslands(islands: List<SavedIslandEntity>)
}

@Dao
interface ExtensionRecordDao {
    @Query("SELECT * FROM extension_records ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ExtensionRecordEntity>>

    @Query("SELECT * FROM extension_records")
    suspend fun getAll(): List<ExtensionRecordEntity>

    @Query("SELECT * FROM extension_records WHERE extensionId = :extensionId LIMIT 1")
    suspend fun findById(extensionId: String): ExtensionRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ExtensionRecordEntity)

    @Query("UPDATE extension_records SET autoUpdateEnabled = :enabled WHERE extensionId = :extensionId")
    suspend fun setAutoUpdate(extensionId: String, enabled: Boolean)

    @Query(
        "UPDATE extension_records SET latestVersion = :latestVersion, latestDownloadUrl = :downloadUrl, " +
            "lastCheckedAt = :checkedAt, lastError = :error WHERE extensionId = :extensionId"
    )
    suspend fun updateCheckMetadata(
        extensionId: String,
        latestVersion: String?,
        downloadUrl: String?,
        checkedAt: Long,
        error: String?
    )

    @Query("DELETE FROM extension_records WHERE extensionId = :extensionId")
    suspend fun deleteById(extensionId: String)
}

@Database(
    entities = [
        SpeedDialEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        SavedTabEntity::class,
        SavedIslandEntity::class,
        ExtensionRecordEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SabDatabase : RoomDatabase() {
    abstract fun speedDialDao(): SpeedDialDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun sessionSnapshotDao(): SessionSnapshotDao
    abstract fun extensionRecordDao(): ExtensionRecordDao

    companion object {
        @Volatile
        private var instance: SabDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `systemDownloadId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_tabs` (
                        `tabId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `islandId` TEXT,
                        `isActive` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`tabId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_islands` (
                        `islandId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorId` TEXT NOT NULL,
                        `isCollapsed` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`islandId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `localUri` TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `extension_records` (
                        `extensionId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceRef` TEXT NOT NULL,
                        `installedVersion` TEXT NOT NULL,
                        `latestVersion` TEXT,
                        `latestDownloadUrl` TEXT,
                        `homepageUrl` TEXT,
                        `iconUrl` TEXT,
                        `autoUpdateEnabled` INTEGER NOT NULL,
                        `lastCheckedAt` INTEGER NOT NULL,
                        `lastUpdatedAt` INTEGER NOT NULL,
                        `lastError` TEXT,
                        PRIMARY KEY(`extensionId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `taskId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `downloads` SET `taskId` = 'legacy-' || `id` WHERE `taskId` = ''")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'file'")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'completed'")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `downloadedBytes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `downloads` SET `downloadedBytes` = CASE WHEN `sizeBytes` > 0 THEN `sizeBytes` ELSE 0 END")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `speedBytesPerSec` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `etaSeconds` INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `connectionsCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `supportsRange` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `stagingPath` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `publicUri` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `subfolder` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `etag` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `segmentsJson` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `torrentInfoHash` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `torrentFilesJson` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `torrentPeers` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `torrentSeeds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `sequentialTorrent` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `wifiOnly` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `errorMessage` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `sha256` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `completedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `downloads` SET `completedAt` = `createdAt`")
            }
        }

        fun getInstance(context: Context): SabDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SabDatabase::class.java,
                    "sab_browser.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

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
    val systemDownloadId: Long,
    val fileName: String,
    val url: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = -1L,
    val createdAt: Long = System.currentTimeMillis(),
    val localUri: String? = null
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

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
    version = 3,
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

        fun getInstance(context: Context): SabDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SabDatabase::class.java,
                    "sab_browser.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

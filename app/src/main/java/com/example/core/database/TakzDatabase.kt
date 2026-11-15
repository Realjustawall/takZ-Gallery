package com.example.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TakzDao {
    @Query("SELECT * FROM media_items ORDER BY dateModifiedSec DESC")
    fun observeAllMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY dateModifiedSec DESC")
    suspend fun getAllMediaOnce(): List<MediaItemEntity>

    @Query("SELECT contentUri FROM media_items")
    suspend fun getAllMediaUrisOnce(): List<String>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE contentUri = :uri LIMIT 1")
    suspend fun getMediaByUri(uri: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: MediaItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaIgnore(items: List<MediaItemEntity>): List<Long>

    @Update
    suspend fun updateMedia(item: MediaItemEntity)

    @Update
    suspend fun updateMediaBatch(items: List<MediaItemEntity>)

    @Query("SELECT * FROM media_items WHERE isIndexed = 0 AND isVideo = 0 AND isTrashed = 0 ORDER BY dateModifiedSec DESC LIMIT :limit")
    suspend fun getPendingImageIndex(limit: Int): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE isIndexed = 0 AND isVideo = 0 AND isTrashed = 0 AND id > :lastSeenId ORDER BY id ASC LIMIT :limit")
    suspend fun getPendingImageIndexAfter(lastSeenId: Long, limit: Int): List<MediaItemEntity>

    @Query("UPDATE media_items SET isIndexed = 0 WHERE isVideo = 0")
    suspend fun resetImageIndex()

    @Query("UPDATE media_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE media_items SET isHidden = :hidden WHERE id IN (:ids)")
    suspend fun setHiddenBatch(ids: List<Long>, hidden: Boolean)

    @Query("UPDATE media_items SET isHidden = :hidden WHERE folderName = :folderName")
    suspend fun setFolderHidden(folderName: String, hidden: Boolean)

    @Query("UPDATE media_items SET isTrashed = :trashed WHERE id IN (:ids)")
    suspend fun setTrashedBatch(ids: List<Long>, trashed: Boolean)

    @Query("DELETE FROM media_items WHERE id IN (:ids)")
    suspend fun deleteMediaPermanently(ids: List<Long>)

    @Query("UPDATE media_items SET manualTagsCsv = :manualTagsCsv WHERE id = :id")
    suspend fun updateManualTags(id: Long, manualTagsCsv: String)

    @Query("UPDATE media_items SET ocrText = :ocrText WHERE id = :id")
    suspend fun updateOcrText(id: Long, ocrText: String)

    @Query("UPDATE media_items SET editPipelineState = :pipelineState WHERE id = :id")
    suspend fun updateEditPipeline(id: Long, pipelineState: String)

    @Query("UPDATE media_items SET folderName = :newFolder WHERE id IN (:ids)")
    suspend fun moveMediaToFolder(ids: List<Long>, newFolder: String)

    @Query("UPDATE media_items SET folderName = :newFolder WHERE folderName = :oldFolder")
    suspend fun renamePhysicalFolder(oldFolder: String, newFolder: String)

    // Virtual Albums
    @Query("SELECT * FROM virtual_albums ORDER BY isPinned DESC, sortOrder ASC, createdAtSec DESC")
    fun observeVirtualAlbums(): Flow<List<VirtualAlbumEntity>>

    @Query("SELECT * FROM virtual_albums ORDER BY isPinned DESC, sortOrder ASC")
    suspend fun getVirtualAlbumsOnce(): List<VirtualAlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVirtualAlbum(album: VirtualAlbumEntity): Long

    @Update
    suspend fun updateVirtualAlbum(album: VirtualAlbumEntity)

    @Query("DELETE FROM virtual_albums WHERE albumId = :albumId")
    suspend fun deleteVirtualAlbum(albumId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbumCrossRef(ref: AlbumMediaCrossRef)

    @Query("DELETE FROM album_media_cross_ref WHERE albumId = :albumId AND mediaId = :mediaId")
    suspend fun removeMediaFromVirtualAlbum(albumId: Long, mediaId: Long)

    @Query("SELECT * FROM album_media_cross_ref")
    fun observeAllAlbumCrossRefs(): Flow<List<AlbumMediaCrossRef>>

    // Encrypted Vault
    @Query("SELECT * FROM vault_items ORDER BY addedAtSec DESC")
    fun observeVaultItems(): Flow<List<VaultItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItemEntity): Long

    @Query("DELETE FROM vault_items WHERE vaultId = :vaultId")
    suspend fun deleteVaultItem(vaultId: Long)

    // Search History
    @Query("SELECT * FROM search_history ORDER BY timestampMs DESC LIMIT 15")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Database(
    entities = [
        MediaItemEntity::class,
        VirtualAlbumEntity::class,
        AlbumMediaCrossRef::class,
        VaultItemEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TakzDatabase : RoomDatabase() {
    abstract fun takzDao(): TakzDao

    companion object {
        @Volatile
        private var INSTANCE: TakzDatabase? = null

        fun getInstance(context: Context): TakzDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TakzDatabase::class.java,
                    "takz_gallery_offline.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

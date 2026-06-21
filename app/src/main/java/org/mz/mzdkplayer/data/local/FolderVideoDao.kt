package org.mz.mzdkplayer.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderVideoDao {
    @Query("SELECT * FROM folder_videos WHERE folderSlotId = :slotId ORDER BY sortOrder ASC")
    fun getVideosBySlot(slotId: Int): Flow<List<FolderVideoEntity>>

    @Query("SELECT * FROM folder_videos WHERE folderSlotId = :slotId ORDER BY sortOrder ASC")
    suspend fun getVideosBySlotSync(slotId: Int): List<FolderVideoEntity>

    @Insert
    suspend fun insertVideo(video: FolderVideoEntity): Long

    @Update
    suspend fun updateVideo(video: FolderVideoEntity)

    @Delete
    suspend fun deleteVideo(video: FolderVideoEntity)

    @Query("DELETE FROM folder_videos WHERE id = :id")
    suspend fun deleteVideoById(id: Int)

    @Query("DELETE FROM folder_videos WHERE folderSlotId = :slotId")
    suspend fun deleteVideosBySlot(slotId: Int)

    @Update
    suspend fun updateVideos(videos: List<FolderVideoEntity>)

    @Transaction
    suspend fun replaceSlotVideos(slotId: Int, videos: List<FolderVideoEntity>) {
        deleteVideosBySlot(slotId)
        videos.forEach { insertVideo(it) }
    }
}

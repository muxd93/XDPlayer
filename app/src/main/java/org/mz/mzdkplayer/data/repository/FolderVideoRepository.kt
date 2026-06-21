package org.mz.mzdkplayer.data.repository

import org.mz.mzdkplayer.data.local.AppDatabase
import org.mz.mzdkplayer.data.local.FolderVideoEntity
import org.mz.mzdkplayer.data.local.HomeSlotEntity
import kotlinx.coroutines.flow.Flow

object FolderVideoRepository {
    private val dao by lazy { AppDatabase.getDatabase(org.mz.mzdkplayer.MzDkPlayerApplication.context).folderVideoDao() }

    fun getVideosBySlot(slotId: Int): Flow<List<FolderVideoEntity>> = dao.getVideosBySlot(slotId)

    suspend fun getVideosBySlotSync(slotId: Int): List<FolderVideoEntity> = dao.getVideosBySlotSync(slotId)

    suspend fun insertVideo(video: FolderVideoEntity): Long = dao.insertVideo(video)

    suspend fun updateVideo(video: FolderVideoEntity) = dao.updateVideo(video)

    suspend fun deleteVideo(video: FolderVideoEntity) = dao.deleteVideo(video)

    suspend fun deleteVideoById(id: Int) = dao.deleteVideoById(id)

    suspend fun deleteVideosBySlot(slotId: Int) = dao.deleteVideosBySlot(slotId)

    suspend fun updateVideos(videos: List<FolderVideoEntity>) = dao.updateVideos(videos)

    /**
     * 重排序文件夹内视频
     */
    suspend fun reorderVideos(slotId: Int, videoIds: List<Int>) {
        val videos = getVideosBySlotSync(slotId)
        val videoMap = videos.associateBy { it.id }
        val reordered = videoIds.mapIndexed { index, id ->
            videoMap[id]?.copy(sortOrder = index) ?: return@mapIndexed null
        }.filterNotNull()
        updateVideos(reordered)
    }

    /**
     * 扫描文件夹并更新视频列表
     * @param slot 栏位信息
     * @param scanFunc 实际扫描函数，返回视频列表
     */
    suspend fun scanFolder(slot: HomeSlotEntity, scanFunc: suspend () -> List<FolderVideoEntity>) {
        val newVideos = scanFunc()

        // 原子操作：删除旧视频列表并插入新视频列表
        dao.replaceSlotVideos(slot.id, newVideos.map { it.copy(folderSlotId = slot.id) })
    }
}

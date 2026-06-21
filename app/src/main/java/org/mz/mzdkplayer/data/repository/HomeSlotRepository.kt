package org.mz.mzdkplayer.data.repository

import org.mz.mzdkplayer.data.local.AppDatabase
import org.mz.mzdkplayer.data.local.HomeSlotEntity
import kotlinx.coroutines.flow.Flow
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.tool.AppLauncherHelper

object HomeSlotRepository {
    private val dao by lazy { AppDatabase.getDatabase(org.mz.mzdkplayer.MzDkPlayerApplication.context).homeSlotDao() }

    val allSlots: Flow<List<HomeSlotEntity>> = dao.getAllSlots()

    suspend fun getAllSlotsSync(): List<HomeSlotEntity> = dao.getAllSlotsSync()

    suspend fun getSlotById(id: Int): HomeSlotEntity? = dao.getSlotById(id)

    suspend fun insertSlot(slot: HomeSlotEntity): Long = dao.insertSlot(slot)

    suspend fun updateSlot(slot: HomeSlotEntity) = dao.updateSlot(slot)

    suspend fun deleteSlot(slot: HomeSlotEntity) = dao.deleteSlot(slot)

    suspend fun deleteSlotById(id: Int) = dao.deleteSlotById(id)

    suspend fun updateSlots(slots: List<HomeSlotEntity>) = dao.updateSlots(slots)

    /**
     * 确保栏位数量与设置一致，不足的补充空栏位
     */
    suspend fun ensureSlotCount(slotCount: Int) {
        val currentSlots = getAllSlotsSync()
        if (currentSlots.size < slotCount) {
            val maxSortOrder = currentSlots.maxOfOrNull { it.sortOrder } ?: -1
            for (i in currentSlots.size until slotCount) {
                insertSlot(HomeSlotEntity(
                    sortOrder = maxSortOrder + 1 + (i - currentSlots.size),
                    slotType = "empty"
                ))
            }
        } else if (currentSlots.size > slotCount) {
            // 删除多余的空栏位（从后往前删）
            val emptySlots = currentSlots.filter { it.slotType == "empty" }.sortedByDescending { it.sortOrder }
            var toRemove = currentSlots.size - slotCount
            for (slot in emptySlots) {
                if (toRemove <= 0) break
                deleteSlotById(slot.id)
                toRemove--
            }
        }
    }

    /**
     * 重排序所有栏位
     */
    suspend fun reorderSlots(slotIds: List<Int>) {
        val slots = getAllSlotsSync()
        val slotMap = slots.associateBy { it.id }
        val reordered = slotIds.mapIndexed { index, id ->
            slotMap[id]?.copy(sortOrder = index) ?: return@mapIndexed null
        }.filterNotNull()
        updateSlots(reordered)
    }

    /**
     * 清理已卸载 app 对应的栏位：重置为 empty。
     * 在收到 PACKAGE_REMOVED 广播时调用。
     */
    suspend fun cleanupUninstalledAppSlots() {
        val appSlots = getAllSlotsSync().filter { it.slotType == "app" }
        if (appSlots.isEmpty()) return
        val pm = MzDkPlayerApplication.context.packageManager
        for (slot in appSlots) {
            val pkg = slot.appPackageName ?: continue
            // 包已卸载时 getLaunchIntentForPackage 返回 null
            if (pm.getLaunchIntentForPackage(pkg) == null) {
                updateSlot(slot.copy(
                    slotType = "empty",
                    label = "",
                    appPackageName = null
                ))
            }
        }
    }
}

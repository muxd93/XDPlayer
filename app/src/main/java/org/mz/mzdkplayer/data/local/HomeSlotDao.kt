package org.mz.mzdkplayer.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeSlotDao {
    @Query("SELECT * FROM home_slots ORDER BY sortOrder ASC")
    fun getAllSlots(): Flow<List<HomeSlotEntity>>

    @Query("SELECT * FROM home_slots ORDER BY sortOrder ASC")
    suspend fun getAllSlotsSync(): List<HomeSlotEntity>

    @Query("SELECT * FROM home_slots WHERE id = :id")
    suspend fun getSlotById(id: Int): HomeSlotEntity?

    @Insert
    suspend fun insertSlot(slot: HomeSlotEntity): Long

    @Update
    suspend fun updateSlot(slot: HomeSlotEntity)

    @Delete
    suspend fun deleteSlot(slot: HomeSlotEntity)

    @Query("DELETE FROM home_slots WHERE id = :id")
    suspend fun deleteSlotById(id: Int)

    @Update
    suspend fun updateSlots(slots: List<HomeSlotEntity>)
}

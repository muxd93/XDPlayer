package org.mz.mzdkplayer.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folder_videos",
    foreignKeys = [ForeignKey(
        entity = HomeSlotEntity::class,
        parentColumns = ["id"],
        childColumns = ["folderSlotId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("folderSlotId")]
)
data class FolderVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val folderSlotId: Int,
    val sortOrder: Int,
    val videoUri: String,
    val dataSourceType: String,               // "SMB" / "FILE"
    val fileName: String,
    val connectionName: String,
    val thumbnailPath: String? = null,
    val lastScannedAt: Long = 0
)

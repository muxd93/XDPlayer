package org.mz.mzdkplayer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_slots")
data class HomeSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sortOrder: Int,
    val slotType: String,                     // "folder" / "video" / "app" / "empty"
    val label: String = "",

    // folder 类型
    val folderUri: String? = null,
    val folderDataSourceType: String? = null, // "SMB" / "FILE"
    val folderConnectionName: String? = null,
    val customThumbnailPath: String? = null,

    // video 类型
    val videoUri: String? = null,
    val videoDataSourceType: String? = null,
    val videoConnectionName: String? = null,
    val videoFileName: String? = null,

    // app 类型
    val appPackageName: String? = null
)

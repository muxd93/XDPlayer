package org.mz.mzdkplayer.ui.elder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 栏位选择器结果回传单例
 *
 * 由于 picker 界面通过 NavController 跳转，无法直接回调结果，
 * 使用此单例在 picker 完成后暂存结果，ElderHomeScreen 观察并处理。
 */
object SlotPickerResultHolder {
    data class Result(
        val slotId: Int,
        val slotType: String,            // "folder" / "video"
        val dataSourceType: String,      // "FILE" / "SMB"
        val uri: String,                 // 文件夹或视频的完整 URI
        val fileName: String,            // 文件名（video 类型用）
        val connectionName: String       // SMB 连接名（SMB 类型用）
    )

    private val _result = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = _result

    fun setResult(result: Result) {
        _result.value = result
    }

    fun consumeResult(): Result? {
        val current = _result.value
        _result.value = null
        return current
    }
}

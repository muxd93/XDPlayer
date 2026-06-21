package org.mz.mzdkplayer.tool


import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

import androidx.media3.common.util.UnstableApi


import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * Tools 是一个门面（Facade），将不同类别的工具方法委托到对应的内部 object 实现。
 *
 * 调用方保持 `Tools.xxx()` 形式不变，所有公共方法签名与历史版本一致。
 * 具体实现见：
 * - [MediaFormatTools] 文件扩展名 / 音视频格式 / 图标
 * - [LanguageTools] 语言与国家名称
 * - [NetworkTools] 网络 / 端口 / URL / URI
 * - [ImageTools] 封面 / 二维码 / 字体
 * - [ValidationTools] 连接参数校验
 *
 * 仅保留极小的纯工具函数（formatFileSize / formatTime / formatFriendlyTime /
 * toSafeFloat / toSafeInt / toSafeLong）直接在此实现。
 */
object Tools {
    // ===== MediaFormatTools 委托 =====
    fun extractFileExtension(fileName: String?): String =
        MediaFormatTools.extractFileExtension(fileName)

    @OptIn(UnstableApi::class)
    @Composable
    fun VideoBigIcon(focusedIsDir: Boolean, fileName: String?, modifier: Modifier) {
        MediaFormatTools.VideoBigIcon(focusedIsDir, fileName, modifier)
    }

    fun containsVideoFormat(input: String): Boolean =
        MediaFormatTools.containsVideoFormat(input)

    fun containsAudioFormat(input: String): Boolean =
        MediaFormatTools.containsAudioFormat(input)

    fun containsImageFileExtension(input: String): Boolean =
        MediaFormatTools.containsImageFileExtension(input)

    fun inferAudioFormatType(mimeType: String): String =
        MediaFormatTools.inferAudioFormatType(mimeType)

    fun audioFormatIconType(mimeType: String?): Int =
        MediaFormatTools.audioFormatIconType(mimeType)

    // ===== LanguageTools 委托 =====
    fun getFullLanguageName(languageCode: String?): String =
        LanguageTools.getFullLanguageName(languageCode)

    fun getCountryName(countryCode: String): String =
        LanguageTools.getCountryName(countryCode)

    // ===== NetworkTools 委托 =====
    fun getLocalIpAddress(): String? =
        NetworkTools.getLocalIpAddress()

    fun startServerOnAvailablePort(
        startPort: Int,
        maxTries: Int = 20,
        onReceive: (RemoteConfig) -> Unit
    ): Pair<RemoteInputServer, Int>? =
        NetworkTools.startServerOnAvailablePort(startPort, maxTries, onReceive)

    fun encodeUrlForPlayer(path: String): String =
        NetworkTools.encodeUrlForPlayer(path)

    fun extractFileNameFromUri(uriString: String): String =
        NetworkTools.extractFileNameFromUri(uriString)

    // ===== ImageTools 委托 =====
    fun saveCoverImageToInternalStorage(context: Context, uri: String, artworkData: ByteArray?): String? =
        ImageTools.saveCoverImageToInternalStorage(context, uri, artworkData)

    fun generateQRCode(content: String, size: Int = 512): ImageBitmap? =
        ImageTools.generateQRCode(content, size)

    fun generateQRCodeBitmap(content: String, size: Int = 512): android.graphics.Bitmap? =
        ImageTools.generateQRCodeBitmap(content, size)

    fun prepareFont(context: Context, fontName: String): String =
        ImageTools.prepareFont(context, fontName)

    // ===== ValidationTools 委托 =====
    fun validateConnectionParams(context: Context, serverAddress: String, shareName: String, aliasName: String): Boolean =
        ValidationTools.validateConnectionParams(context, serverAddress, shareName, aliasName)

    fun validateSMBConnectionParams(context: Context, serverAddress: String, shareName: String, aliasName: String): Boolean =
        ValidationTools.validateSMBConnectionParams(context, serverAddress, shareName, aliasName)

    fun validateWebConnectionParams(context: Context, serverAddress: String): Boolean =
        ValidationTools.validateWebConnectionParams(context, serverAddress)

    // ===== 小工具函数直接保留 =====
    /**
     * 将字节大小转换为人类可读的格式 (B, KB, MB, GB)
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return String.format(Locale.getDefault(),"%.2f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    /**
     * 将毫秒转为可读的时间格式 (例如: 01:25:30 或 05:12)
     * @param ms 毫秒数
     */
    fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"

        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            // 超过1小时，显示 HH:mm:ss
            String.format(Locale.getDefault(),"%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            // 不足1小时，显示 mm:ss
            String.format(Locale.getDefault(),"%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 更加人性化的时间显示
     * 如果是短视频片段显示 00:30
     * 如果是长片显示 2h 15m
     */
    fun formatFriendlyTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> formatTime(ms) // 调用上面的基础版显示 mm:ss
        }
    }

    fun String?.toSafeFloat(default: Float = 0f): Float {
        return if (this.isNullOrBlank()) default else try {
            this.toFloat()
        } catch (e: NumberFormatException) {
            default
        }
    }

    fun String?.toSafeInt(default: Int = 0): Int {
        return if (this.isNullOrBlank()) default else try {
            this.toInt()
        } catch (e: NumberFormatException) {
            default
        }
    }

    fun String?.toSafeLong(default: Long = 0L): Long {
        return if (this.isNullOrBlank()) default else try {
            this.toLong()
        } catch (e: NumberFormatException) {
            default
        }
    }

    // ===== Base64 编解码委托 =====
    /** 编码：转为 URL 安全且不带换行符的 Base64 字符串 */
    fun String.toBase64(): String =
        NetworkTools.toBase64(this)

    /** 解码：从 Base64 还原为原始字符串 */
    fun String.fromBase64(): String =
        NetworkTools.fromBase64(this)
}

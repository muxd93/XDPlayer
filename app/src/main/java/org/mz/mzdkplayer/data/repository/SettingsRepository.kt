package org.mz.mzdkplayer.data.repository


import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

// 定义一个单例或者通过 Hilt 注入，这里用简单的单例模式
object SettingsRepository {
    private const val PREF_NAME = "app_settings"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // --- 常规设置Keys ---
    private const val KEY_HIDE_DETAILS = "hide_details"

    private const val KEY_HIDE_NETWORK_SPEED = "hide_network_speed"

    // --- 播放/视频/音频 Keys ---
    private const val KEY_AUDIO_LANG = "audio_lang" // ""=Auto, "zh", "en"
    private const val KEY_SUB_LANG = "sub_lang"     // ""=Auto, "zh", "en"
    private const val KEY_VIDEO_TUNNELING = "video_tunneling" // 视频隧道模式(ExoPlayer TV常用)
    private const val KEY_AUDIO_PASSTHROUGH = "audio_passthrough" // 音频透传

    // --- 字幕设置 Keys ---
    private const val KEY_SUB_SIZE = "sub_size_sp"
    private const val KEY_SUB_COLOR = "sub_color_hex"
    private const val KEY_SUB_BG_COLOR = "sub_bg_color_hex" // 存 Hex 字符串
    private const val KEY_SUB_BOTTOM_PADDING = "sub_bottom_padding_dp"
    private const val KEY_SUB_PGS_CENTER = "sub_pgs_center"

    // --- 刮削设置 Keys ---
    private const val KEY_SOURCE_SMB = "source_smb"
    private const val KEY_SOURCE_WEBDAV = "source_webdav"
    private const val KEY_SOURCE_FTP = "source_ftp"
    private const val KEY_SOURCE_NFS = "source_nfs"
    private const val KEY_SOURCE_LOCAL = "source_local"
    private const val KEY_SOURCE_HTTP = "source_http"

    // --- Getters & Setters ---

    // 常规
    var hideDetails: Boolean
        get() = prefs.getBoolean(KEY_HIDE_DETAILS, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_DETAILS, value).apply()
    var hideNetworkSpeed: Boolean
        get() = prefs.getBoolean(KEY_HIDE_NETWORK_SPEED, true)
        set(value) = prefs.edit { putBoolean(KEY_HIDE_NETWORK_SPEED, value) }

    // 播放 - 语言
    var audioLanguage: String // "" = Auto
        get() = prefs.getString(KEY_AUDIO_LANG, "") ?: ""
        set(value) = prefs.edit { putString(KEY_AUDIO_LANG, value) }

    var subtitleLanguage: String
        get() = prefs.getString(KEY_SUB_LANG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUB_LANG, value).apply()

    // 视频 (ExoPlayer 建议)
    var enableTunneling: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_TUNNELING, true)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_TUNNELING, value).apply()

    // 音频 (ExoPlayer 建议)
    var enablePassthrough: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_PASSTHROUGH, value).apply()

    // 字幕外观
    var subtitleFontSize: Float
        get() = prefs.getFloat(KEY_SUB_SIZE, 22f)
        set(value) = prefs.edit().putFloat(KEY_SUB_SIZE, value).apply()

    var subtitleColorHex: Long
        get() = prefs.getLong(KEY_SUB_COLOR, 0xFFFFFFFF) // White
        set(value) = prefs.edit().putLong(KEY_SUB_COLOR, value).apply()

    // 默认黑色 50%透明 (ARGB: 0x80000000)
    var subtitleBgColorHex: Long
        get() = prefs.getLong(KEY_SUB_BG_COLOR, 0x80000000)
        set(value) = prefs.edit().putLong(KEY_SUB_BG_COLOR, value).apply()

    var subtitleBottomPadding: Float
        get() = prefs.getFloat(KEY_SUB_BOTTOM_PADDING, 30f)
        set(value) = prefs.edit().putFloat(KEY_SUB_BOTTOM_PADDING, value).apply()

    var forcePgsCenter: Boolean
        get() = prefs.getBoolean(KEY_SUB_PGS_CENTER, false)
        set(value) = prefs.edit().putBoolean(KEY_SUB_PGS_CENTER, value).apply()

    // 刮削源
    var enableSmb: Boolean get() = prefs.getBoolean(KEY_SOURCE_SMB, true); set(v) = prefs.edit().putBoolean(KEY_SOURCE_SMB, v).apply()
    var enableWebDav: Boolean get() = prefs.getBoolean(KEY_SOURCE_WEBDAV, true); set(v) = prefs.edit().putBoolean(KEY_SOURCE_WEBDAV, v).apply()
    var enableFtp: Boolean get() = prefs.getBoolean(KEY_SOURCE_FTP, false); set(v) = prefs.edit().putBoolean(KEY_SOURCE_FTP, v).apply()
    var enableNfs: Boolean get() = prefs.getBoolean(KEY_SOURCE_NFS, false); set(v) = prefs.edit().putBoolean(KEY_SOURCE_NFS, v).apply()
    var enableLocal: Boolean get() = prefs.getBoolean(KEY_SOURCE_LOCAL, false); set(v) = prefs.edit().putBoolean(KEY_SOURCE_LOCAL, v).apply()
    var enableHttp: Boolean get() = prefs.getBoolean(KEY_SOURCE_HTTP, false); set(v) = prefs.edit().putBoolean(KEY_SOURCE_HTTP, v).apply()
}
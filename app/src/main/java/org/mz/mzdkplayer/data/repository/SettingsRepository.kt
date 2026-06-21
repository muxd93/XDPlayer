package org.mz.mzdkplayer.data.repository


import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import java.security.MessageDigest

// 定义一个单例或者通过 Hilt 注入，这里用简单的单例模式
object SettingsRepository {
    private const val PREF_NAME = "app_settings"
    internal lateinit var prefs: SharedPreferences

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

    // 🔥 新增：默认播放器内核
    private const val KEY_DEFAULT_PLAYER = "default_player"

    // --- 刮削设置 Keys ---
    private const val KEY_SOURCE_SMB = "source_smb"
    private const val KEY_SOURCE_WEBDAV = "source_webdav"
    private const val KEY_SOURCE_FTP = "source_ftp"
    private const val KEY_SOURCE_NFS = "source_nfs"
    private const val KEY_SOURCE_LOCAL = "source_local"
    private const val KEY_SOURCE_HTTP = "source_http"
    private const val KEY_APP_LANGUAGE = "app_language"

    // 🔥 新增：优先选择本地 nfo
    private const val KEY_PRIORITIZE_LOCAL_NFO = "prioritize_local_nfo"

    // --- 适老化模式 Keys ---
    private const val KEY_APP_MODE = "app_mode"
    private const val KEY_MODE_PIN = "mode_pin"
    private const val KEY_WEB_CONFIG_ENABLED = "web_config_enabled"

    // 🔥 新增：Exo音频解码模式 (0=纯硬解, 1=硬解优先, 2=软解优先)
    private const val KEY_EXO_AUDIO_DECODE_MODE = "exo_audio_decode_mode"

    // ExoPlayer 下载缓存大小 (MB)，默认 5GB
    // 注意：缓存大小在 Application.onCreate 中一次性创建 SimpleCache，修改后需重启应用生效
    private const val KEY_EXO_CACHE_SIZE_MB = "exo_cache_size_mb"
    // --- Getters & Setters ---

    // 常规
    var hideDetails: Boolean
        get() = prefs.getBoolean(KEY_HIDE_DETAILS, false)
        set(value) = prefs.edit { putBoolean(KEY_HIDE_DETAILS, value) }
    var hideNetworkSpeed: Boolean
        get() = prefs.getBoolean(KEY_HIDE_NETWORK_SPEED, true)
        set(value) = prefs.edit { putBoolean(KEY_HIDE_NETWORK_SPEED, value) }

    // 播放 - 语言
    var audioLanguage: String // "" = Auto
        get() = prefs.getString(KEY_AUDIO_LANG, "") ?: ""
        set(value) = prefs.edit { putString(KEY_AUDIO_LANG, value) }

    var subtitleLanguage: String
        get() = prefs.getString(KEY_SUB_LANG, "") ?: ""
        set(value) = prefs.edit { putString(KEY_SUB_LANG, value) }

    // 视频 (ExoPlayer 建议)
    var enableTunneling: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_TUNNELING, false)
        set(value) = prefs.edit { putBoolean(KEY_VIDEO_TUNNELING, value) }

    // 音频 (ExoPlayer 建议)
    var enablePassthrough: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_PASSTHROUGH, false)
        set(value) = prefs.edit { putBoolean(KEY_AUDIO_PASSTHROUGH, value) }
    var exoAudioDecodeMode: Int
        get() = prefs.getInt(KEY_EXO_AUDIO_DECODE_MODE, 1) // 默认 1 (硬解优先)
        set(value) = prefs.edit { putInt(KEY_EXO_AUDIO_DECODE_MODE, value) }

    // ExoPlayer 缓存大小 (MB)，默认 5120 = 5GB
    var exoCacheSizeMb: Int
        get() = prefs.getInt(KEY_EXO_CACHE_SIZE_MB, 5120)
        set(value) = prefs.edit { putInt(KEY_EXO_CACHE_SIZE_MB, value) }
    // 字幕外观
    var subtitleFontSize: Float
        get() = prefs.getFloat(KEY_SUB_SIZE, 22f)
        set(value) = prefs.edit { putFloat(KEY_SUB_SIZE, value) }

    var subtitleColorHex: Long
        get() = prefs.getLong(KEY_SUB_COLOR, 0xFFFFFFFF) // White
        set(value) = prefs.edit { putLong(KEY_SUB_COLOR, value) }

    // 默认黑色 50%透明 (ARGB: 0x80000000)
    var subtitleBgColorHex: Long
        get() = prefs.getLong(KEY_SUB_BG_COLOR, 0x80000000)
        set(value) = prefs.edit { putLong(KEY_SUB_BG_COLOR, value) }

    var subtitleBottomPadding: Float
        get() = prefs.getFloat(KEY_SUB_BOTTOM_PADDING, 30f)
        set(value) = prefs.edit { putFloat(KEY_SUB_BOTTOM_PADDING, value) }

    var forcePgsCenter: Boolean
        get() = prefs.getBoolean(KEY_SUB_PGS_CENTER, false)
        set(value) = prefs.edit { putBoolean(KEY_SUB_PGS_CENTER, value) }

    var defaultPlayer: String
        get() = prefs.getString(KEY_DEFAULT_PLAYER, "exo") ?: "exo"
        set(value) = prefs.edit { putString(KEY_DEFAULT_PLAYER, value) }

    // 刮削源
    var enableSmb: Boolean get() = prefs.getBoolean(KEY_SOURCE_SMB, true); set(v) = prefs.edit {
        putBoolean(
            KEY_SOURCE_SMB,
            v
        )
    }
    var enableWebDav: Boolean get() = prefs.getBoolean(KEY_SOURCE_WEBDAV, true); set(v) = prefs.edit {
        putBoolean(
            KEY_SOURCE_WEBDAV,
            v
        )
    }
    var enableFtp: Boolean get() = prefs.getBoolean(KEY_SOURCE_FTP, false); set(v) = prefs.edit {
        putBoolean(
            KEY_SOURCE_FTP,
            v
        )
    }
    var enableNfs: Boolean get() = prefs.getBoolean(KEY_SOURCE_NFS, false); set(v) = prefs.edit {
        putBoolean(
            KEY_SOURCE_NFS,
            v
        )
    }
    var enableLocal: Boolean get() = prefs.getBoolean(KEY_SOURCE_LOCAL, false); set(v) = prefs.edit {
        putBoolean(
            KEY_SOURCE_LOCAL,
            v
        )
    }
    var enableHttp: Boolean get() = prefs.getBoolean(KEY_SOURCE_HTTP, false); set(v) = prefs.edit {
        putBoolean(
            KEY_SOURCE_HTTP,
            v
        )
    }
    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "") ?: "" // "" = 跟随系统
        set(value) = prefs.edit { putString(KEY_APP_LANGUAGE, value) }

    var prioritizeLocalNfo: Boolean
        get() = prefs.getBoolean(KEY_PRIORITIZE_LOCAL_NFO, false)
        set(value) = prefs.edit { putBoolean(KEY_PRIORITIZE_LOCAL_NFO, value) }

    // 适老化模式
    var appMode: String
        get() = prefs.getString(KEY_APP_MODE, "elder") ?: "elder"
        set(value) = prefs.edit { putString(KEY_APP_MODE, value) }

    var modePin: String
        get() = prefs.getString(KEY_MODE_PIN, "") ?: ""
        set(value) = prefs.edit { putString(KEY_MODE_PIN, hashPin(value)) }

    /** 验证 PIN 是否匹配 (PIN 以 SHA-256 哈希形式存储) */
    fun verifyPin(pin: String): Boolean {
        val stored = modePin
        if (stored.isEmpty()) return false
        return stored == hashPin(pin)
    }

    /** 对 PIN 做 SHA-256 哈希, 返回十六进制字符串 */
    private fun hashPin(pin: String): String {
        if (pin.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    var webConfigEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEB_CONFIG_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_WEB_CONFIG_ENABLED, value) }
}
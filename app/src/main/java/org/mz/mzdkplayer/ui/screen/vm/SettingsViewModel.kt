package org.mz.mzdkplayer.ui.screen.vm

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mz.mzdkplayer.data.repository.SettingsRepository

// 简单的数据类用于 UI 状态
data class SettingsUiState(
    val hideDetails: Boolean = false,
    val hideNetworkSpeed: Boolean = true,
    val audioLang: String = "",
    val subLang: String = "",
    val enableTunneling: Boolean = true,
    val enablePassthrough: Boolean = false,
    val subFontSize: Float = 22f,
    val subColor: Long = 0xFFFFFFFF,
    val subBgColor: Long = 0x80000000,
    val subBottomPadding: Float = 30f,
    val forcePgsCenter: Boolean = false,
    val defaultPlayer: String = "exo",
    // 刮削
    val smb: Boolean = true,
    val webdav: Boolean = true,
    val ftp: Boolean = false,
    val nfs: Boolean = false,
    val local: Boolean = false,
    val http: Boolean = false
)

class SettingsViewModel : ViewModel() {
    private val repo = SettingsRepository // 假设已在 App 启动时 init
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refreshState()
    }

    private fun refreshState() {
        _uiState.update {
            SettingsUiState(
                hideDetails = repo.hideDetails,
                hideNetworkSpeed = repo.hideNetworkSpeed,
                audioLang = repo.audioLanguage,
                subLang = repo.subtitleLanguage,
                enableTunneling = repo.enableTunneling,
                enablePassthrough = repo.enablePassthrough,
                subFontSize = repo.subtitleFontSize,
                subColor = repo.subtitleColorHex,
                subBgColor = repo.subtitleBgColorHex,
                subBottomPadding = repo.subtitleBottomPadding,
                forcePgsCenter = repo.forcePgsCenter,
                defaultPlayer = repo.defaultPlayer,
                smb = repo.enableSmb,
                webdav = repo.enableWebDav,
                ftp = repo.enableFtp,
                nfs = repo.enableNfs,
                local = repo.enableLocal,
                http = repo.enableHttp
            )
        }
    }

    // 通用的更新方法
    fun toggleHideDetails(v: Boolean) { repo.hideDetails = v; refreshState() }
    fun toggleHideNetWorkSpeed(v: Boolean) { repo.hideNetworkSpeed = v; refreshState() }
    fun setAudioLanguage(v: String) { repo.audioLanguage = v; refreshState() }
    fun setSubLanguage(v: String) { repo.subtitleLanguage = v; refreshState() }
    fun toggleTunneling(v: Boolean) { repo.enableTunneling = v; refreshState() }
    fun togglePassthrough(v: Boolean) { repo.enablePassthrough = v; refreshState() }

    fun setSubFontSize(v: Float) { repo.subtitleFontSize = v; refreshState() }
    fun setSubColor(v: Long) { repo.subtitleColorHex = v; refreshState() }
    fun setSubBgColor(v: Long) { repo.subtitleBgColorHex = v; refreshState() }
    fun setSubBottomPadding(v: Float) { repo.subtitleBottomPadding = v; refreshState() }
    fun togglePgsCenter(v: Boolean) { repo.forcePgsCenter = v; refreshState() }
    fun setDefaultPlayer(kernel: String) {
        repo.defaultPlayer = kernel
        refreshState()
    }

    // 刮削开关
    fun toggleSource(source: String, v: Boolean) {
        when(source) {
            "SMB" -> repo.enableSmb = v
            "WebDav" -> repo.enableWebDav = v
            "FTP" -> repo.enableFtp = v
            "NFS" -> repo.enableNfs = v
            "Local" -> repo.enableLocal = v
            "HTTP" -> repo.enableHttp = v
        }
        refreshState()
    }
}
package org.mz.mzdkplayer.ui.screen.vm

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mz.mzdkplayer.data.model.SMBConnection
import org.mz.mzdkplayer.data.repository.SMBConnectionRepository
import org.mz.mzdkplayer.tool.SmbDiscoveryScanner

/**
 * SMB 扫描 ViewModel
 *
 * 职责:
 * 1. 管理 SmbDiscoveryScanner 的扫描生命周期
 * 2. 对扫描结果进行智能排序
 * 3. 提供一键连接所需的数据 (匹配已保存连接)
 */
class SmbScanViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = SmbDiscoveryScanner(application)
    private val smbRepository = SMBConnectionRepository(application)

    /** 扫描状态 */
    val scanState: StateFlow<SmbDiscoveryScanner.ScanState> = scanner.scanState

    /** 发现的主机列表 (已排序) */
    val discoveredHosts: StateFlow<List<SmbDiscoveryScanner.DiscoveredSmbHost>> = scanner.discoveredHosts

    /** 已保存的连接列表 (用于匹配扫描结果) */
    private val _savedConnections = MutableStateFlow<List<SMBConnection>>(emptyList())
    val savedConnections: StateFlow<List<SMBConnection>> = _savedConnections.asStateFlow()

    init {
        loadSavedConnections()
    }

    fun loadSavedConnections() {
        viewModelScope.launch(Dispatchers.IO) {
            _savedConnections.value = smbRepository.getConnections()
        }
    }

    /**
     * 启动扫描
     */
    fun startScan() {
        loadSavedConnections() // 刷新已保存连接
        scanner.startScan()
    }

    /**
     * 停止扫描
     */
    fun stopScan() {
        scanner.stopScan()
    }

    /**
     * 获取排序后的扫描结果
     *
     * 排序策略:
     * 1. 已保存连接优先 (IP 匹配)
     * 2. 免认证优先
     * 3. 响应时间短优先
     * 4. 主机名/IP 字母序
     */
    fun getSortedHosts(): List<SmbDiscoveryScanner.DiscoveredSmbHost> {
        val savedIps = _savedConnections.value.map { it.ip }.toSet()
        return discoveredHosts.value.sortedWith(
            compareByDescending<SmbDiscoveryScanner.DiscoveredSmbHost> { it.ip in savedIps }
                .thenByDescending { !it.requiresAuth }
                .thenBy { it.responseTimeMs }
                .thenBy { it.hostname ?: it.ip }
        )
    }

    /**
     * 检查扫描发现的主机是否匹配已保存的连接
     * @return 匹配的 SMBConnection, 无匹配返回 null
     */
    fun matchSavedConnection(host: SmbDiscoveryScanner.DiscoveredSmbHost): SMBConnection? {
        return _savedConnections.value.find { it.ip == host.ip }
    }

    /**
     * 保存新连接
     */
    fun saveConnection(connection: SMBConnection) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _savedConnections.value
            // 查重: IP + 用户名 + 共享名
            val isDuplicate = existing.any {
                it.ip == connection.ip &&
                it.username == connection.username &&
                it.shareName == connection.shareName
            }
            if (isDuplicate) return@launch
            smbRepository.addConnection(connection)
            loadSavedConnections()
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanner.stopScan()
    }
}

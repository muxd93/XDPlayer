package org.mz.mzdkplayer.ui.screen.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mz.mzdkplayer.data.model.FileConnectionStatus
import org.mz.mzdkplayer.tool.SmbConnectionManager
import org.mz.mzdkplayer.tool.SmbUtils

import kotlin.collections.forEach

class SMBConViewModel : ViewModel() {
    private val _connectionStatus: MutableStateFlow<FileConnectionStatus> =
        MutableStateFlow(FileConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<FileConnectionStatus> = _connectionStatus
    private val _fileList = MutableStateFlow<List<SMBFileItem>>(emptyList())
    val fileList: StateFlow<List<SMBFileItem>> = _fileList

    private var connectionId: String? = null
    private val mutex = Mutex()  // 协程互斥锁
    private val connectionManager = SmbConnectionManager.getInstance()

    fun connectToSMB(ip: String, username: String, password: String, shareName: String) {
        viewModelScope.launch {
            _connectionStatus.value = FileConnectionStatus.Connecting
            mutex.withLock {
                try {
                    val id = "$ip:$shareName"
                    val diskShare = connectionManager.getOrConnect(id, ip, shareName, username, password)
                    if (diskShare != null) {
                        connectionId = id
                        _connectionStatus.value = FileConnectionStatus.Connected
                    } else {
                        _connectionStatus.value = FileConnectionStatus.Error("连接失败: 超过最大重试次数")
                    }
                } catch (e: Exception) {
                    Log.e("SMB", "连接失败$e", e)
                    _connectionStatus.value = FileConnectionStatus.Error("连接失败: ${e.message}")
                }
            }
        }
    }

    fun testConnectSMB(ip: String, username: String, password: String, shareName: String) {
        viewModelScope.launch {
            mutex.withLock {
                try {
                    _connectionStatus.value = FileConnectionStatus.Connecting
                    val id = "$ip:$shareName"
                    val diskShare = connectionManager.getOrConnect(id, ip, shareName, username, password)
                    if (diskShare != null) {
                        connectionId = id
                        _connectionStatus.value = FileConnectionStatus.Connected
                        listSMBFiles(SMBConfig(ip, shareName, "/", username, password))
                    } else {
                        _connectionStatus.value = FileConnectionStatus.Error("连接失败: 超过最大重试次数")
                        _fileList.value = emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("SMB", "连接失败$e", e)
                    _connectionStatus.value = FileConnectionStatus.Error("连接失败: ${e.message}")
                    _fileList.value = emptyList()
                }
            }
        }
    }

    fun listSMBFiles(config: SMBConfig) {
        viewModelScope.launch {
            if (_connectionStatus.value != FileConnectionStatus.Connected &&
                _connectionStatus.value !is FileConnectionStatus.FilesLoaded) {
                _connectionStatus.value = FileConnectionStatus.Error("未连接")
                return@launch
            }

            Log.d("listSMBFiles", "正在列出文件")
            mutex.withLock {
                try {
                    // 只更新一次状态为加载中
                    _connectionStatus.value = FileConnectionStatus.LoadingFile

                    // 在 IO 线程执行所有繁重工作
                    val files = withContext(Dispatchers.IO) {
                        try {
                            val cleanPath = config.path.let {
                                if (it == "/") "\\" else it.replace("/", "\\").trimEnd('\\')
                            }
                            // 每次都通过 Manager 获取可用 share, 避免缓存失效引用
                            val activeShare = connectionId?.let { id ->
                                connectionManager.getOrConnect(id, config.server, config.share, config.username, config.password)
                            }
                            val startTime = System.currentTimeMillis()
                            val fileList = mutableListOf<SMBFileItem>()
                            activeShare?.list(cleanPath)
                                ?.forEach { fileInfo: FileIdBothDirectoryInformation ->
                                    val fileName = fileInfo.fileName
                                    if (fileName != "." && fileName != "..") {
                                        val isDirectory = isDirectory(fileInfo.fileAttributes)
                                        val filePath = if (cleanPath == "\\") {
                                            "\\$fileName"
                                        } else {
                                            "$cleanPath\\$fileName"
                                        }

                                        fileList.add(
                                            SMBFileItem(
                                                name = fileName,
                                                fullPath = filePath.replace("\\", "/"),
                                                isDirectory = isDirectory,
                                                fileSize = fileInfo.endOfFile,
                                                server = config.server,
                                                share = config.share,
                                                username = config.username,
                                                password = config.password,
                                            )
                                        )
                                    }
                                } ?: throw Exception("SMB 客户端未初始化或连接失败")
                            val getFilesTime = System.currentTimeMillis()
                            Log.d("Performance", "获取文件耗时: ${getFilesTime - startTime}ms")


                            // 排序
                            val sortedList = fileList.sortedBy { it.name }
                            val sortTime = System.currentTimeMillis()
                            Log.d("Performance", "排序耗时: ${sortTime - getFilesTime}ms")

                            sortedList
                        } finally {

                        }
                    }

                    // 一次性更新最终状态
                    _fileList.value = files
                    _connectionStatus.value = FileConnectionStatus.FilesLoaded

                } catch (e: Exception) {
                    Log.e("SMBConViewModel", "连接失败", e)
                    _connectionStatus.value = FileConnectionStatus.Error("连接失败: ${e.message}")
                    disconnectSMB()
                }
            }
        }
    }



    // 断开连接
    // 注意: connectionManager.disconnect 是全局操作, 会移除 Manager 中的连接条目,
    // 影响所有持有该连接的组件。当前仅 SMBConViewModel 使用 SmbConnectionManager,
    // 因此此处简化处理直接断开; 若未来有多组件复用连接, 需改为引用计数管理。
    fun disconnectSMB() {
        connectionId?.let { connectionManager.disconnect(it) }
        connectionId = null
        _connectionStatus.value = FileConnectionStatus.Disconnected
        _fileList.value = emptyList()
        Log.i("SMBCON", _connectionStatus.value.toString())
    }

    fun isConnected(): Boolean {
        val id = connectionId ?: return false
        return connectionManager.getState(id) == SmbConnectionManager.ConnectionState.CONNECTED
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 销毁时断开连接（导航离开时触发，配置变更不会触发）
        disconnectSMB()
    }


    fun parseSMBPath(path: String): SMBConfig {
        // 委托给 SmbUtils.parseSMBPath，保持与 WebConfigServer 等其他调用方一致的解析逻辑
        val info = SmbUtils.parseSMBPath(path)
        return SMBConfig(
            server = info.server,
            share = info.share,
            path = info.path,
            username = info.username,
            password = info.password
        )
    }

    fun buildSMBPath(
        server: String,
        share: String,
        path: String,
        username: String,
        password: String
    ): String {
        return if (username.isNotEmpty()) {
            "smb://$username:$password@$server/$share$path"
        } else {
            "smb://$server/$share$path"
        }
    }

    // 方法1：使用 FileAttributes 常量进行位运算判断
    fun isDirectory(fileAttributes: Long): Boolean {
        return (fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
    }
}

// --- 状态枚举 ---


data class SMBConfig(
    val server: String,
    val share: String,
    val path: String,
    val username: String,
    val password: String
)

data class SMBFileItem(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val fileSize: Long = 0L,
    val server: String,
    val share: String,
    val username: String,
    val password: String
)




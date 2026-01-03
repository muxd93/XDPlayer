package org.mz.mzdkplayer.ui.screen.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.mz.mzdkplayer.data.model.FileConnectionStatus
import java.io.IOException

class FTPConViewModel : ViewModel() {

    // 状态流定义
    private val _connectionStatus: MutableStateFlow<FileConnectionStatus> = MutableStateFlow(FileConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<FileConnectionStatus> = _connectionStatus

    private val _fileList: MutableStateFlow<List<FTPFile>> = MutableStateFlow(emptyList())
    val fileList: StateFlow<List<FTPFile>> = _fileList

    // 存储当前路径（用于 UI 显示，不带前导 /）
    private val _currentPath: MutableStateFlow<String> = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath

    // FTP 相关变量
    private var ftpClient: FTPClient? = null
    private var server: String = ""
    private var username: String = ""
    private var password: String = ""
    private var port: Int = 21

    // 互斥锁，防止连接和刷新操作冲突
    private val mutex = Mutex()

    /**
     * 连接到 FTP 服务器 (优化版)
     */
    fun connectToFTP(
        server: String?,
        port: Int?,
        username: String?,
        password: String?,
        shareName: String?,
    ) {
        // 保存配置信息
        if (server != null) this.server = server
        if (port != null) this.port = port
        if (username != null) this.username = username
        if (password != null) this.password = password

        viewModelScope.launch {
            // 获取锁，确保连接过程中不会插入其他操作
            mutex.withLock {
                _connectionStatus.value = FileConnectionStatus.Connecting
                try {
                    withContext(Dispatchers.IO) {
                        // 1. 初始化 Client 并设置超时 (新增优化)
                        ftpClient = FTPClient().apply {
                            controlEncoding = "UTF-8"
                            connectTimeout = 10000 // 10秒连接超时
                            //dataTimeout = 10000    // 10秒数据传输超时
                            // controlKeepAliveTimeout = 300 // 可选：保活时间
                        }

                        Log.d("FTPConViewModel", "开始连接 $server:$port")

                        // 2. 连接
                        ftpClient?.connect(this@FTPConViewModel.server, this@FTPConViewModel.port)
                        val loginSuccess = ftpClient?.login(this@FTPConViewModel.username, this@FTPConViewModel.password) ?: false

                        if (!loginSuccess) {
                            throw IOException("FTP 登录失败: 用户名或密码错误")
                        }

                        // 3. 设置传输模式
                        ftpClient?.setFileType(FTP.BINARY_FILE_TYPE)
                        ftpClient?.enterLocalPassiveMode()

                        // 4. 计算初始路径
                        val initialPathRaw = shareName ?: ""
                        // 确保路径以 / 开头用于请求，但逻辑上处理干净
                        val targetDir = if (initialPathRaw.startsWith("/")) initialPathRaw else "/$initialPathRaw"
                        val safeTargetDir = if (targetDir.endsWith("/")) targetDir else "$targetDir/"

                        // 5. 直接拉取文件列表 (避免死锁的关键优化)
                        // 不调用 public listFiles()，而是直接调用内部逻辑
                        val files = fetchFilesInternal(safeTargetDir)

                        // 6. 更新 UI 状态
                        _fileList.value = files
                        _currentPath.value = safeTargetDir.removePrefix("/").removeSuffix("/") // UI显示用的纯净路径
                        _connectionStatus.value = FileConnectionStatus.FilesLoaded

                        Log.d("FTPConViewModel", "连接成功，初始目录: $safeTargetDir")
                    }
                } catch (e: Exception) {
                    Log.e("FTPConViewModel", "连接过程出错", e)
                    _connectionStatus.value = FileConnectionStatus.Error("连接失败: ${e.message}")

                    // --- 修复闪退的核心代码 ---
                    // 确保在 IO 线程断开连接，避免 NetworkOnMainThreadException
                    withContext(Dispatchers.IO) {
                        cleanupFTPClient()
                    }
                }
            }
        }
    }

    /**
     * 刷新/跳转目录 (优化版)
     */
    fun listFiles(path: String = "") {
        viewModelScope.launch {
            // 获取锁，防止和 connectToFTP 冲突
            mutex.withLock {
                _connectionStatus.value = FileConnectionStatus.LoadingFile
                try {
                    withContext(Dispatchers.IO) {
                        // 格式化路径
                        val targetPath = if (path.startsWith("/")) path else "/$path"
                        val dirPath = if (targetPath.endsWith("/")) targetPath else "$targetPath/"

                        // 调用内部逻辑拉取文件
                        val files = fetchFilesInternal(dirPath)

                        // 更新状态
                        _fileList.value = files
                        _currentPath.value = path.removePrefix("/")
                        _connectionStatus.value = FileConnectionStatus.FilesLoaded
                    }
                } catch (e: Exception) {
                    Log.e("FTPConViewModel", "获取列表失败: $path", e)
                    // 判断是因为连接断开还是其他原因
                    if (ftpClient?.isConnected != true) {
                        _connectionStatus.value = FileConnectionStatus.Error("连接已断开，请重新连接")
                        withContext(Dispatchers.IO) { cleanupFTPClient() }
                    } else {
                        // 连接还在，只是这个文件夹进不去，回到上一级或者仅报错
                        _connectionStatus.value = FileConnectionStatus.Error("无法访问文件夹: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 内部私有方法：执行真正的网络请求获取文件列表
     * 纯净逻辑，无锁，无协程切换，专供内部调用
     */
    @Throws(IOException::class)
    private fun fetchFilesInternal(dirPath: String): List<FTPFile> {
        if (ftpClient == null || ftpClient?.isConnected != true) {
            throw IOException("FTP 未连接")
        }

        // 尝试列出文件
        val files = ftpClient?.listFiles(dirPath) ?: throw IOException("获取文件列表返回空")

        // 过滤掉当前目录(.)和上级目录(..)以及空名称
        return files.filter {
            val name = it.name
            name != "." && name != ".." && name.isNotBlank()
        }
    }

    /**
     * 返回上一级目录
     */
    fun goBack() {
        val current = _currentPath.value
        if (current.isEmpty()) return // 已经在根目录

        // 简单的字符串处理：去掉最后一级
        val parentPath = getParentPath(current)
        listFiles(parentPath)
    }

    private fun getParentPath(current: String): String {
        // 移除末尾斜杠以防干扰
        val cleanCurrent = current.removeSuffix("/")
        val lastSlashIndex = cleanCurrent.lastIndexOf('/')
        return if (lastSlashIndex >= 0) {
            cleanCurrent.substring(0, lastSlashIndex)
        } else {
            "" // 回到根目录
        }
    }
    /**
     * 检查当前是否已连接
     */
    fun isConnected(): Boolean {
        return _connectionStatus.value == FileConnectionStatus.Connected ||
                _connectionStatus.value == FileConnectionStatus.FilesLoaded ||
                _connectionStatus.value is FileConnectionStatus.LoadingFile
    }
    /**
     * 获取完整 URL 用于播放
     */
    fun getResourceFullUrl(resourceName: String): String {
        val server = this.server
        val port = this.port
        val path = _currentPath.value
        // 拼接逻辑优化，防止多斜杠
        val cleanPath = if (path.isEmpty()) "" else if (path.endsWith("/")) path else "$path/"
        val cleanResourceName = resourceName.removePrefix("/")

        // 最终格式: ftp://user:pass@host:port/path/file
        return "ftp://$username:$password@$server:$port/$cleanPath$cleanResourceName"
    }
    /**
     * 断开与 FTP 服务器的连接
     */
    fun disconnectFTP() {
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    cleanupFTPClient()
                } catch (e: Exception) {
                    Log.w("FTPConViewModel", "断开连接时发生异常", e)
                } finally {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = FileConnectionStatus.Disconnected
                        _fileList.value = emptyList()
                        _currentPath.value = ""
                        this@FTPConViewModel.username = ""
                        this@FTPConViewModel.password = ""
                        this@FTPConViewModel.server = ""
                    }
                }
            }
        }
    }
    /**
     * 清理资源 (需在 IO 线程调用)
     */
     private fun cleanupFTPClient() {
        try {
            ftpClient?.logout()
        } catch (e: Exception) {
            // 忽略退出时的异常
        }
        try {
            if (ftpClient?.isConnected == true) {
                ftpClient?.disconnect()
            }
        } catch (e: Exception) {
            // 忽略断开时的异常
        }
        ftpClient = null
    }
}
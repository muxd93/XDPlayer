package org.mz.mzdkplayer.tool

import android.util.Log
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

/**
 * 全局 SMB 连接管理器 (单例)
 *
 * 职责:
 * 1. 管理所有活跃 SMB 连接的生命周期
 * 2. 连接健康检查 + 自动重连 (指数退避)
 * 3. 跨页面连接复用
 *
 * 使用方式:
 *   val share = SmbConnectionManager.getInstance()
 *       .getOrConnect(connId, host, shareName, username, password)
 *   // 使用 share 进行文件操作
 *   // 用完后不要手动 close, 由 Manager 统一管理
 */
class SmbConnectionManager private constructor() {

    companion object {
        private const val TAG = "SmbConnectionManager"
        private const val MAX_RETRY = 5
        private const val HEALTH_CHECK_INTERVAL_MS = 30_000L
        private const val IDLE_THRESHOLD_MS = 60_000L

        @Volatile private var instance: SmbConnectionManager? = null
        fun getInstance(): SmbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: SmbConnectionManager().also { instance = it }
            }
    }

    /** 连接状态 */
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED
    }

    /** 连接条目 */
    private data class ConnectionEntry(
        val id: String,
        val host: String,
        val shareName: String,
        val username: String,
        val password: String,
        var client: SMBClient? = null,
        var connection: Connection? = null,
        var session: Session? = null,
        var share: DiskShare? = null,
        var lastActive: Long = System.currentTimeMillis(),
        var retryCount: Int = 0
    )

    private val connections = mutableMapOf<String, ConnectionEntry>()
    private val _states = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val states: StateFlow<Map<String, ConnectionState>> = _states

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 后台健康巡检
        scope.launch {
            while (true) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                connections.values.forEach { entry ->
                    val idleTime = System.currentTimeMillis() - entry.lastActive
                    if (idleTime > IDLE_THRESHOLD_MS) {
                        // 空闲超过阈值, 检查健康
                        if (!isHealthy(entry)) {
                            Log.d(TAG, "连接 ${entry.id} 不健康, 尝试重连")
                            reconnect(entry)
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取或建立连接 (带自动重连)
     *
     * @param id 连接唯一标识 (建议用 host+shareName 或 UUID)
     * @return 可用的 DiskShare, 失败返回 null
     */
    suspend fun getOrConnect(
        id: String,
        host: String,
        shareName: String,
        username: String,
        password: String
    ): DiskShare? {
        val entry = synchronized(connections) {
            connections.getOrPut(id) {
                ConnectionEntry(id, host, shareName, username, password)
            }
        }

        // 已连接且健康
        if (isHealthy(entry)) {
            entry.lastActive = System.currentTimeMillis()
            return entry.share
        }

        // 需要连接/重连
        return reconnect(entry)
    }

    /**
     * 建立连接 (带指数退避重试)
     */
    private suspend fun reconnect(entry: ConnectionEntry): DiskShare? {
        updateState(entry.id, ConnectionState.RECONNECTING)

        // 指数退避: 1s, 2s, 4s, 8s, 16s
        while (entry.retryCount < MAX_RETRY) {
            if (entry.retryCount > 0) {
                val delayMs = minOf(1000L * (1L shl entry.retryCount), 16_000L)
                Log.d(TAG, "连接 ${entry.id} 第 ${entry.retryCount} 次重试, 等待 ${delayMs}ms")
                delay(delayMs)
            }

            try {
                // 清理旧连接
                cleanupConnection(entry)

                val clientConfig = SmbConfig.builder()
                    .withTimeout(15, TimeUnit.SECONDS)
                    .build()
                entry.client = SMBClient(clientConfig)
                entry.connection = entry.client!!.connect(entry.host)
                val auth = AuthenticationContext(
                    entry.username,
                    entry.password.toCharArray(),
                    null
                )
                entry.session = entry.connection!!.authenticate(auth)
                entry.share = entry.session!!.connectShare(entry.shareName) as DiskShare
                entry.retryCount = 0
                entry.lastActive = System.currentTimeMillis()
                updateState(entry.id, ConnectionState.CONNECTED)
                Log.i(TAG, "连接 ${entry.id} 成功")
                return entry.share
            } catch (e: Exception) {
                Log.w(TAG, "连接 ${entry.id} 失败 (第 ${entry.retryCount + 1} 次): ${e.message}")
                entry.retryCount++
            }
        }

        // 超过最大重试次数
        updateState(entry.id, ConnectionState.FAILED)
        Log.e(TAG, "连接 ${entry.id} 超过最大重试次数, 放弃")
        return null
    }

    /**
     * 健康检查: 尝试 list("\\") 测试连接是否存活
     */
    private fun isHealthy(entry: ConnectionEntry): Boolean {
        return try {
            entry.connection?.isConnected == true &&
            entry.share != null &&
            entry.share!!.list("\\").also {
                entry.lastActive = System.currentTimeMillis()
            } != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 断开指定连接
     */
    fun disconnect(id: String) {
        synchronized(connections) {
            connections[id]?.let { entry ->
                cleanupConnection(entry)
                connections.remove(id)
            }
        }
        updateState(id, ConnectionState.DISCONNECTED)
    }

    /**
     * 断开所有连接
     */
    fun disconnectAll() {
        synchronized(connections) {
            connections.keys.toList().forEach { id ->
                connections[id]?.let { cleanupConnection(it) }
            }
            connections.clear()
        }
        _states.value = emptyMap()
    }

    /**
     * 获取连接状态
     */
    fun getState(id: String): ConnectionState {
        return _states.value[id] ?: ConnectionState.DISCONNECTED
    }

    /**
     * 重置重试计数 (外部调用, 允许再次重连)
     */
    fun resetRetry(id: String) {
        synchronized(connections) {
            connections[id]?.retryCount = 0
        }
    }

    // ==================== 内部方法 ====================

    private fun cleanupConnection(entry: ConnectionEntry) {
        try { entry.share?.close() } catch (e: TransportException) {
            Log.w(TAG, "Share 已断开")
        } catch (_: Exception) {} finally {
            entry.share = null
        }
        try { entry.session?.close() } catch (e: TransportException) {
            Log.w(TAG, "Session 已断开")
        } catch (_: Exception) {} finally {
            entry.session = null
        }
        try { entry.connection?.close() } catch (e: TransportException) {
            Log.w(TAG, "Connection 已断开")
        } catch (_: Exception) {} finally {
            entry.connection = null
        }
        try { entry.client?.close() } catch (_: Exception) {} finally {
            entry.client = null
        }
    }

    private fun updateState(id: String, state: ConnectionState) {
        _states.value = _states.value.toMutableMap().apply { this[id] = state }
    }
}

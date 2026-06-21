package org.mz.mzdkplayer.tool

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * 局域网 SMB 服务自动发现扫描器
 *
 * 三阶段扫描策略:
 * 1. mDNS 发现 (_smb._tcp) — 快速, 但非所有 SMB 服务都注册
 * 2. 子网端口扫描 (445) — 全面, 逐 IP 探测
 * 3. Guest 认证探测 — 判断是否需要认证, 获取主机名
 */
class SmbDiscoveryScanner(private val context: Context) {

    companion object {
        private const val TAG = "SmbDiscoveryScanner"
        private const val SMB_PORT = 445
        private const val PORT_SCAN_TIMEOUT_MS = 800L
        private const val MDNS_DISCOVERY_MS = 3000L
        private const val AUTH_PROBE_TIMEOUT_MS = 3000L
    }

    /** 发现的 SMB 主机 */
    data class DiscoveredSmbHost(
        val ip: String,
        val hostname: String?,
        val port: Int = SMB_PORT,
        val responseTimeMs: Long,
        val requiresAuth: Boolean,
        val discoverySource: String // "mDNS" | "port-scan"
    )

    /** 扫描状态 */
    sealed class ScanState {
        object Idle : ScanState()
        data class Scanning(val foundCount: Int, val scannedCount: Int = 0, val totalCount: Int = 0) : ScanState()
        data class Finished(val hosts: List<DiscoveredSmbHost>) : ScanState()
        data class Error(val message: String) : ScanState()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredSmbHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredSmbHost>> = _discoveredHosts

    private var scanJob: Job? = null

    // mDNS resolve 队列: NsdManager.resolveService 同一时刻只能有一个活跃请求
    private val pendingResolveQueue = mutableListOf<NsdServiceInfo>()
    private var isResolving = false

    /**
     * 启动扫描, 结果通过 discoveredHosts StateFlow 增量推送
     */
    fun startScan() {
        if (scanJob?.isActive == true) return

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            _scanState.value = ScanState.Scanning(0)
            _discoveredHosts.value = emptyList()

            val foundHosts = mutableMapOf<String, DiscoveredSmbHost>()
            var scannedCount = 0
            var totalCount = 0

            fun updateHosts() {
                val sorted = foundHosts.values.sortedBy { it.responseTimeMs }
                _discoveredHosts.value = sorted
                _scanState.value = ScanState.Scanning(sorted.size, scannedCount, totalCount)
            }

            try {
                // --- 阶段1+2 并行: mDNS + 端口扫描 ---
                coroutineScope {
                    // mDNS 发现
                    launch {
                        discoverViaMdns()?.forEach { host ->
                            foundHosts[host.ip] = host
                            updateHosts()
                        }
                    }

                    // 端口扫描
                    launch {
                        val subnets = getLocalSubnets()
                        if (subnets.isNotEmpty()) {
                            totalCount = subnets.size * 254
                            scanSubnetPort(subnets,
                                onFound = { ip, latency ->
                                    if (ip !in foundHosts) {
                                        foundHosts[ip] = DiscoveredSmbHost(
                                            ip = ip,
                                            hostname = null,
                                            responseTimeMs = latency,
                                            requiresAuth = true, // 默认需要认证, 后续探测更新
                                            discoverySource = "port-scan"
                                        )
                                        updateHosts()
                                    }
                                },
                                onProgress = { scanned ->
                                    scannedCount = scanned
                                    updateHosts()
                                }
                            )
                        }
                    }
                }

                // --- 阶段3: 对发现的主机探测认证方式 ---
                val probeJobs = foundHosts.values.toList().map { host ->
                    launch(Dispatchers.IO) {
                        val probeResult = probeSmbAuth(host.ip)
                        val updated = host.copy(
                            requiresAuth = probeResult.requiresAuth,
                            hostname = probeResult.hostname ?: host.hostname
                        )
                        foundHosts[host.ip] = updated
                        updateHosts()
                    }
                }
                // 等待所有探测完成 (但不超过总超时)
                withTimeoutOrNull(15000L) {
                    probeJobs.joinAll()
                }

                _scanState.value = ScanState.Finished(_discoveredHosts.value)
            } catch (e: CancellationException) {
                // 正常取消
                _scanState.value = ScanState.Finished(_discoveredHosts.value)
            } catch (e: Exception) {
                Log.e(TAG, "扫描失败", e)
                _scanState.value = ScanState.Error(e.message ?: "扫描失败")
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanState.Idle
    }

    // ==================== mDNS 发现 ====================

    private suspend fun discoverViaMdns(): List<DiscoveredSmbHost>? {
        return withTimeoutOrNull(MDNS_DISCOVERY_MS) {
            suspendCancellableCoroutine { cont ->
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                if (nsdManager == null) {
                    if (cont.isActive) cont.resumeWith(Result.success(emptyList()))
                    return@suspendCancellableCoroutine
                }

                val found = mutableListOf<DiscoveredSmbHost>()
                pendingResolveQueue.clear()
                isResolving = false

                fun processNextResolve() {
                    if (isResolving || pendingResolveQueue.isEmpty()) return
                    val serviceInfo = pendingResolveQueue.removeAt(0)
                    isResolving = true
                    try {
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                isResolving = false
                                val host = info.host?.hostAddress
                                if (host != null) {
                                    found.add(DiscoveredSmbHost(
                                        ip = host,
                                        hostname = info.serviceName,
                                        port = info.port.takeIf { it > 0 } ?: SMB_PORT,
                                        responseTimeMs = 0,
                                        requiresAuth = true,
                                        discoverySource = "mDNS"
                                    ))
                                }
                                processNextResolve()
                            }

                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                isResolving = false
                                processNextResolve()
                            }
                        })
                    } catch (e: Exception) {
                        isResolving = false
                        Log.w(TAG, "resolveService failed: ${e.message}")
                        processNextResolve()
                    }
                }

                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onDiscoveryStopped(serviceType: String) {}

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        if (cont.isActive) cont.resumeWith(Result.success(found))
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        pendingResolveQueue.add(serviceInfo)
                        processNextResolve()
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                }

                try {
                    nsdManager.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (e: Exception) {
                    Log.w(TAG, "mDNS discover failed: ${e.message}")
                    if (cont.isActive) cont.resumeWith(Result.success(emptyList()))
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation {
                    try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
                }
            }
        }
    }

    // ==================== 子网端口扫描 ====================

    /**
     * 获取本机所有网卡的子网列表（支持多网卡环境），如 ["192.168.1", "10.0.0"]
     */
    private fun getLocalSubnets(): List<String> {
        val result = mutableSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr.isLoopbackAddress || addr is java.net.Inet6Address) continue
                    val ip = addr.hostAddress ?: continue
                    val parts = ip.split(".")
                    if (parts.size == 4) {
                        result.add("${parts[0]}.${parts[1]}.${parts[2]}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取子网失败", e)
        }
        return result.toList()
    }

    /**
     * 并发扫描多个子网的 445 端口
     */
    private suspend fun scanSubnetPort(
        subnets: List<String>,
        onFound: (ip: String, latency: Long) -> Unit,
        onProgress: (scanned: Int) -> Unit
    ) = coroutineScope {
        val semaphore = Semaphore(32)
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        subnets.flatMap { subnet ->
            (1..254).map { i ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val ip = "$subnet.$i"
                        val start = System.currentTimeMillis()
                        try {
                            val socket = java.net.Socket()
                            socket.connect(InetSocketAddress(ip, SMB_PORT), PORT_SCAN_TIMEOUT_MS.toInt())
                            socket.close()
                            val latency = System.currentTimeMillis() - start
                            onFound(ip, latency)
                        } catch (_: Exception) {
                            // 不可达, 忽略
                        }
                        onProgress(counter.incrementAndGet())
                    }
                }
            }
        }.awaitAll()
    }

    // ==================== Guest 认证探测 ====================

    data class AuthProbeResult(
        val requiresAuth: Boolean,
        val hostname: String?
    )

    /**
     * 尝试 guest 认证, 判断主机是否需要认证
     */
    private fun probeSmbAuth(ip: String): AuthProbeResult {
        val clientConfig = SmbConfig.builder()
            .withTimeout(AUTH_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        val client = SMBClient(clientConfig)

        return try {
            val connection = client.connect(ip)
            // 尝试 guest 认证
            val guestAuth = AuthenticationContext("guest", CharArray(0), null)
            val session = connection.authenticate(guestAuth)
            session.close()
            connection.close()
            client.close()
            AuthProbeResult(requiresAuth = false, hostname = null)
        } catch (e: Exception) {
            // guest 失败 = 需要认证
            try { client.close() } catch (_: Exception) {}
            AuthProbeResult(requiresAuth = true, hostname = null)
        }
    }
}

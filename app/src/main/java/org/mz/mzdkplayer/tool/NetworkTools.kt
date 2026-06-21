package org.mz.mzdkplayer.tool


import android.util.Base64
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections

internal object NetworkTools {
    /**
     * 尝试在指定端口范围内启动服务器
     * @param startPort 起始端口，例如 8888
     * @param maxTries 最大尝试次数，例如 10 次
     * @param onReceive 回调函数
     * @return 返回一个 Pair(启动成功的Server对象, 实际端口号)，如果失败返回 null
     */
    fun startServerOnAvailablePort(
        startPort: Int,
        maxTries: Int = 20,
        onReceive: (RemoteConfig) -> Unit
    ): Pair<RemoteInputServer, Int>? {
        var currentPort = startPort

        for (i in 0 until maxTries) {
            try {
                // 尝试创建并启动
                val server = RemoteInputServer(currentPort, onReceive)
                server.start()
                // 如果代码走到这里没有报错，说明启动成功了
                return Pair(server, currentPort)
            } catch (e: Exception) {
                // 启动失败（通常是 BindException 端口被占），打印日志并尝试下一个端口
                e.printStackTrace()
                currentPort++
            }
        }
        return null // 所有尝试都失败了
    }

    fun getLocalIpAddress(): String? {
        try {
            // 获取设备上所有的网络接口（网卡）
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                // 排除掉回环地址（Loopback）和未启动的网卡
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                for (inetAddress in Collections.list(addresses)) {
                    // 只找 IPv4 地址，排除 IPv6（因为目前大多数局域网环境还是 IPv4 比较稳）
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        val ip = inetAddress.hostAddress
                        // 过滤掉虚拟网卡或非内网网段（可选，通常直接返回第一个有效的 IPv4 即可）
                        if (ip != null && !ip.contains(":")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // 在 Tools.kt 中添加这个方法
    fun encodeUrlForPlayer(path: String): String {
        return try {
            val protocolSeparator = "://"
            if (path.contains(protocolSeparator)) {
                val parts = path.split(protocolSeparator)
                val protocol = parts[0]
                val hostAndPath = parts[1]

                val hostPathParts = hostAndPath.split("/", limit = 2)
                val host = hostPathParts[0]

                if (hostPathParts.size > 1) {
                    val fullPath = hostPathParts[1]

                    // 核心修复：分离路径(Path)和查询参数(Query)
                    val queryIndex = fullPath.indexOf("?")
                    val pathPart = if (queryIndex != -1) fullPath.substring(0, queryIndex) else fullPath
                    val queryPart = if (queryIndex != -1) fullPath.substring(queryIndex) else "" // 保留包含 '?' 在内的所有参数

                    // 只对纯路径部分进行编码，避免误伤 ? = & 等符号
                    val encodedPath = pathPart.split("/").joinToString("/") { segment ->
                        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                    }

                    "$protocol$protocolSeparator$host/$encodedPath$queryPart"
                } else {
                    path
                }
            } else {
                // 如果没有协议前缀，当做普通文件路径处理
                path.split("/").joinToString("/") { segment ->
                    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
                }
            }
        } catch (e: Exception) {
            path // 降级处理，报错就原样返回
        }
    }

    /**
     * 从 URI 字符串中提取文件名（最后一个 `/` 后的部分）
     */
    fun extractFileNameFromUri(uriString: String): String {
        return try {
            val lastSlashIndex = uriString.lastIndexOf('/')
            if (lastSlashIndex != -1 && lastSlashIndex < uriString.length - 1) {
                uriString.substring(lastSlashIndex + 1)
            } else {
                uriString // 如果没有找到 `/`，返回整个字符串
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "未知文件名" // 异常时的兜底名称
        }
    }

    // ===== Base64 编解码 =====

    /** 编码：转为 URL 安全且不带换行符的 Base64 字符串 */
    fun toBase64(input: String): String {
        return Base64.encodeToString(
            input.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
    }

    /** 解码：从 Base64 还原为原始字符串 */
    fun fromBase64(input: String): String {
        return try {
            String(
                Base64.decode(input, Base64.URL_SAFE or Base64.NO_WRAP),
                StandardCharsets.UTF_8
            )
        } catch (e: Exception) {
            ""
        }
    }
}

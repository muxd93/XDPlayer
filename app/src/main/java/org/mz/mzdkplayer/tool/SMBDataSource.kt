package org.mz.mzdkplayer.tool

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * 优化后的 SMB 数据源
 * 核心改进：使用 BufferedInputStream 替代手动内存拷贝，大幅提升流式读取效率。
 */
@UnstableApi
class SmbDataSource(
    private val config: SmbDataSourceConfig = SmbDataSourceConfig()
) : BaseDataSource(/* isNetwork= */ true) {

    companion object {
        private const val TAG = "SmbDataSource"

        // --- 全局静态缓存 ---
        // 保持连接复用，避免每次 Seek 都重新握手
        private var sharedSmbClient: SMBClient? = null
        private var cachedConnection: Connection? = null
        private var cachedSession: Session? = null
        private var cachedShare: DiskShare? = null

        private var currentHost: String? = null

        /**
         * 静态释放方法：供外部（如退出播放页时）调用
         */
        fun releaseGlobalResources() {
            Log.i(TAG, "Releasing GLOBAL SMB resources...")
            try { cachedShare?.close() } catch (e: Exception) {
                Log.w(TAG, "Error closing share", e)
            } finally { cachedShare = null }
            try { cachedSession?.close() } catch (e: Exception) {
                Log.w(TAG, "Error closing session", e)
            } finally { cachedSession = null }
            try { cachedConnection?.close() } catch (e: Exception) {
                Log.w(TAG, "Error closing connection", e)
            } finally { cachedConnection = null }
            try { sharedSmbClient?.close() } catch (e: Exception) {
                Log.w(TAG, "Error closing client", e)
            } finally { sharedSmbClient = null }
            currentHost = null
            Log.i(TAG, "Releasing GLOBAL SMB END...")
        }
    }

    // --- 实例变量 ---
    private var dataSpec: DataSpec? = null
    private var file: File? = null

    // 使用 BufferedInputStream 自动管理缓冲
    private var inputStream: InputStream? = null

    private var bytesToRead: Long = 0
    private var bytesRead: Long = 0
    private var opened = false

    // SMB 协议配置
    private val PREFERRED_SMB_DIALECTS = EnumSet.of(
//        SMB2Dialect.SMB_3_1_1,
//        SMB2Dialect.SMB_3_0,
//        SMB2Dialect.SMB_3_0_2,
        SMB2Dialect.SMB_2XX,
        SMB2Dialect.SMB_2_1,
        SMB2Dialect.SMB_2_0_2

    )

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.bytesRead = 0
        this.bytesToRead = 0
        transferInitializing(dataSpec)

        try {
            val uri = dataSpec.uri
            // 1. 获取全局复用的连接资源
            ensureGlobalConnection(uri)

            // 2. 解析路径并打开文件
            val path = uri.path ?: throw IOException("无效路径")
            val pathSegments = path.split("/").filter { it.isNotEmpty() }
            if (pathSegments.size < 2) throw IOException("路径必须包含共享名和文件路径")
            val filePath = pathSegments.drop(1).joinToString("/")

            file = cachedShare?.openFile(
                filePath,
                setOf(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            ) ?: throw IOException("无法打开文件: $filePath")

            // 3. 获取文件信息
            val fileInfo = file!!.fileInformation.standardInformation
            val fileLength = fileInfo.endOfFile
            val position = dataSpec.position

            if (position > fileLength) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }

            bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                fileLength - position
            }

            // 4. 构建流 (核心优化点)
            val rawInputStream = file!!.inputStream
            // 使用 BufferedInputStream 包装，缓冲区大小由 Config 决定 (建议 512KB)
            inputStream = BufferedInputStream(rawInputStream, config.bufferSizeBytes)

            // 5. 处理 Seek (跳过前面的数据)
            if (position > 0) {
                skipFully(inputStream!!, position)
            }

            opened = true
            transferStarted(dataSpec)

            return bytesToRead
        } catch (e: Exception) {
            // 遇到错误清理连接，方便下次重试
            if (e !is EOFException) {
                Log.w(TAG, "Open failed, clearing global cache. Error: ${e.message}")
                releaseGlobalResources()
            }
            // 确保流关闭
            closeQuietly()
            throw IOException("Open error: ${e.message}", e)
        }
    }

    /**
     * 循环调用 skip，确保跳过足够的字节数
     */
    @Throws(IOException::class)
    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                // 如果没跳过任何字节，可能到头了，或者流不支持
                // 尝试读一个字节来判断是否 EOF
                if (stream.read() == -1) {
                    throw EOFException()
                } else {
                    // 读到了1个字节，说明没 EOF，减少 remaining
                    remaining--
                }
            } else {
                remaining -= skipped
            }
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val bytesRemaining = bytesToRead - bytesRead
            if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        }

        val bytesToReadNow = if (bytesToRead == C.LENGTH_UNSET.toLong()) {
            readLength
        } else {
            min(bytesToRead - bytesRead, readLength.toLong()).toInt()
        }

        val stream = inputStream ?: throw IOException("Stream is null")

        try {
            // 直接从 BufferedInputStream 读取，利用其内部缓冲机制
            val bytesReadNow = stream.read(buffer, offset, bytesToReadNow)

            if (bytesReadNow == -1) {
                if (bytesToRead != C.LENGTH_UNSET.toLong()) throw EOFException()
                return C.RESULT_END_OF_INPUT
            }

            bytesRead += bytesReadNow
            bytesTransferred(bytesReadNow)
            return bytesReadNow
        } catch (e: IOException) {
            // 发生读取错误
            throw IOException("Read error", e)
        }
    }

    private fun ensureGlobalConnection(uri: Uri) {
        val host = uri.host ?: throw IOException("Host missing")
        val (user, pass) = parseUserInfo(uri)

        // 检查 Host 是否变更或连接是否断开
        if (cachedConnection == null || !cachedConnection!!.isConnected || currentHost != host) {
            Log.d(TAG, "Creating NEW SMB connection to $host")
            releaseGlobalResources()

            if (sharedSmbClient == null) {
                val clientConfig = SmbConfig.builder()
                    .withDialects(PREFERRED_SMB_DIALECTS)
                    .withMultiProtocolNegotiate(true)
                    // 增大 Socket 接收缓冲区，提高大文件吞吐量
                    .withBufferSize(config.socketBufferSizeBytes)
                    .withSoTimeout(0)
                    .withTimeout(60_000, TimeUnit.MILLISECONDS) // 连接超时
                    .build()
                sharedSmbClient = SMBClient(clientConfig)
            }

            cachedConnection = sharedSmbClient!!.connect(host)
            currentHost = host
            val authContext = AuthenticationContext(user, pass.toCharArray(), "")
            cachedSession = cachedConnection!!.authenticate(authContext)
        }

        val shareName = uri.path?.split("/")?.filter { it.isNotEmpty() }?.get(0)
            ?: throw IOException("No share name")

        if (cachedShare == null || cachedShare?.smbPath?.shareName != shareName) {
            cachedShare?.close()
            cachedShare = cachedSession?.connectShare(shareName) as? DiskShare
                ?: throw IOException("Connect share failed: $shareName")
        }
    }

    private fun parseUserInfo(uri: Uri): Pair<String, String> {
        val userInfo = uri.userInfo
        if (userInfo.isNullOrEmpty()) return Pair("guest", "")
        // 密码可能含 :, 用 limit=2 只切第一个冒号
        val parts = userInfo.split(":", limit = 2)
        return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(parts[0], "")
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        closeQuietly()
        dataSpec = null
    }

    private fun closeQuietly() {
        try {
            // 关闭 InputStream 会自动处理底层资源的释放
            inputStream?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing stream", e)
        } finally {
            inputStream = null
            // 注意：不要在这里置空 cachedShare 等全局对象，只释放当前文件的句柄
            file = null
        }
    }
}

@UnstableApi
class SmbDataSourceFactory(
    private val config: SmbDataSourceConfig = SmbDataSourceConfig()
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return SmbDataSource(config)
    }
}

/**
 * 配置类
 */
data class SmbDataSourceConfig(
    // 应用层流缓冲区：建议 512KB (512 * 1024)。
    // 这个值决定了 BufferedInputStream 一次从网络预取多少数据。
    // 太小(如8KB)会导致IO频繁，太大(如8MB)会导致首屏加载慢且容易OOM。
    val bufferSizeBytes: Int = 2 * 1024 * 1024,

    // Socket 层缓冲区 (SmbConfig.bufferSize)：建议 1MB - 4MB。
    // 这决定了底层 TCP 接收窗口的大小，对吞吐量影响很大。
    val socketBufferSizeBytes: Int = 4 * 1024 * 1024,

    val soTimeoutMs: Int = 60_000, // 读写超时 60秒
)
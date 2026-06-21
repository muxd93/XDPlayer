package org.mz.mzdkplayer.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mz.mzdkplayer.data.local.FolderVideoEntity
import org.mz.mzdkplayer.data.local.HomeSlotEntity
import org.mz.mzdkplayer.tool.SmbUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

class ThumbnailManager(private val context: Context) {

    companion object {
        private const val TAG = "ThumbnailManager"
        private const val THUMBNAIL_DIR = "thumbnails"
        private const val TARGET_WIDTH = 480
        private const val SMB_PREVIEW_SIZE = 1L * 1024 * 1024 // 1MB
        private const val JPEG_QUALITY = 80
        private const val MAX_CACHE_SIZE = 100L * 1024 * 1024 // 100MB
    }

    private val thumbnailDir: File = File(context.filesDir, THUMBNAIL_DIR)

    init {
        if (!thumbnailDir.exists()) {
            thumbnailDir.mkdirs()
        }
    }

    /**
     * 获取栏位缩略图
     * - 优先返回自定义缩略图
     * - video 类型尝试提取视频帧
     * - app 类型返回 null（UI 层用 AppLauncherHelper 获取图标）
     * - empty 类型返回 null
     */
    suspend fun getSlotThumbnail(slot: HomeSlotEntity): File? = withContext(Dispatchers.IO) {
        // 优先自定义缩略图
        slot.customThumbnailPath?.let { path ->
            val file = File(path)
            if (file.exists()) return@withContext file
        }

        when (slot.slotType) {
            "video" -> {
                val videoUri = slot.videoUri ?: return@withContext null
                val cacheKey = hashUri(videoUri)
                val cached = File(thumbnailDir, "video_$cacheKey.jpg")
                if (cached.exists()) return@withContext cached

                when (slot.videoDataSourceType) {
                    "FILE" -> extractLocalVideoThumbnail(videoUri, cacheKey)
                    "SMB" -> {
                        val connectionName = slot.videoConnectionName ?: return@withContext null
                        extractSmbVideoThumbnail(videoUri, connectionName, cacheKey)
                    }
                    else -> null
                }
            }
            "folder" -> {
                // folder 类型暂不提取缩略图，可后续扩展
                null
            }
            else -> null // app / empty
        }
    }

    /**
     * 获取视频缩略图
     * - 如果 thumbnailPath 不为空且文件存在，直接返回
     * - 否则尝试提取并缓存
     */
    suspend fun getVideoThumbnail(video: FolderVideoEntity): File? = withContext(Dispatchers.IO) {
        video.thumbnailPath?.let { path ->
            val file = File(path)
            if (file.exists()) return@withContext file
        }

        val cacheKey = hashUri(video.videoUri)
        val cached = File(thumbnailDir, "video_$cacheKey.jpg")
        if (cached.exists()) return@withContext cached

        when (video.dataSourceType) {
            "FILE" -> extractLocalVideoThumbnail(video.videoUri, cacheKey)
            "SMB" -> extractSmbVideoThumbnail(video.videoUri, video.connectionName, cacheKey)
            else -> null
        }
    }

    /**
     * 从本地视频提取缩略图帧
     * @param videoPath 本地视频文件路径或 file:// URI
     * @param cacheKey 缓存键
     * @return 缓存的缩略图文件，失败返回 null
     */
    suspend fun extractLocalVideoThumbnail(videoPath: String, cacheKey: String): File? =
        withContext(Dispatchers.IO) {
            val outputFile = File(thumbnailDir, "video_$cacheKey.jpg")
            if (outputFile.exists()) return@withContext outputFile

            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(videoPath)
                if (uri.scheme == "file" || uri.scheme == null) {
                    retriever.setDataSource(uri.path ?: videoPath)
                } else {
                    retriever.setDataSource(context, uri)
                }

                val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@withContext null

                compressAndSave(bitmap, outputFile)
                outputFile
            } catch (e: Exception) {
                Log.e(TAG, "提取本地视频缩略图失败: $videoPath", e)
                null
            } finally {
                retriever.releaseQuietly()
            }
        }

    /**
     * 从 SMB 视频提取缩略图帧
     * 下载前 1MB 到临时文件，提取帧后删除临时文件
     * @param smbUri SMB 路径（不含用户名密码的 smb://host/share/path 格式）
     * @param connectionName SMB 连接名称，用于从 SMBConnectionRepository 获取凭证
     * @param cacheKey 缓存键
     * @return 缓存的缩略图文件，失败返回 null
     */
    suspend fun extractSmbVideoThumbnail(
        smbUri: String,
        connectionName: String,
        cacheKey: String
    ): File? = withContext(Dispatchers.IO) {
        val outputFile = File(thumbnailDir, "video_$cacheKey.jpg")
        if (outputFile.exists()) return@withContext outputFile

        val connection = findSmbConnection(connectionName) ?: run {
            Log.e(TAG, "未找到 SMB 连接: $connectionName")
            return@withContext null
        }

        val fullUri = buildFullSmbUri(smbUri, connection)
        var tempFile: File? = null

        try {
            // 下载前 1MB 到临时文件
            tempFile = downloadSmbPrefix(fullUri) ?: return@withContext null

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tempFile.absolutePath)
                val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@withContext null

                compressAndSave(bitmap, outputFile)
                outputFile
            } finally {
                retriever.releaseQuietly()
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取 SMB 视频缩略图失败: $smbUri", e)
            null
        } finally {
            tempFile?.delete()
        }
    }

    /**
     * 保存自定义缩略图
     * @param slotId 栏位 ID
     * @param inputStream 图片输入流
     * @return 保存后的文件
     */
    suspend fun saveCustomThumbnail(slotId: Int, inputStream: InputStream): File =
        withContext(Dispatchers.IO) {
            val outputFile = File(thumbnailDir, "slot_$slotId.jpg")
            inputStream.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                    ?: throw IllegalArgumentException("无法解码输入流为 Bitmap")
                compressAndSave(bitmap, outputFile)
            }
            outputFile
        }

    /**
     * 清除所有缓存的缩略图
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        thumbnailDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * LRU 淘汰：缓存总大小超过 MAX_CACHE_SIZE 时，删除最旧的文件
     */
    private fun evictOldCacheIfNeeded() {
        val files = thumbnailDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var totalSize = files.sumOf { it.length() }
        for (file in files) {
            if (totalSize <= MAX_CACHE_SIZE) break
            totalSize -= file.length()
            file.delete()
        }
    }

    // ---- 内部辅助方法 ----

    /**
     * 从 SMBConnectionRepository 查找连接信息
     */
    private fun findSmbConnection(connectionName: String): org.mz.mzdkplayer.data.model.SMBConnection? {
        val repo = SMBConnectionRepository(context)
        return repo.getConnections().find { it.name == connectionName }
    }

    /**
     * 构造包含凭证的完整 SMB URI
     * 格式: smb://username:password@ip/shareName/path/to/file
     */
    private fun buildFullSmbUri(
        smbUri: String,
        connection: org.mz.mzdkplayer.data.model.SMBConnection
    ): Uri {
        val parsed = Uri.parse(smbUri)
        val host = connection.ip ?: parsed.host ?: ""
        val username = connection.username ?: "guest"
        val password = connection.password ?: ""
        val shareName = connection.shareName ?: ""

        // 从原始 URI 提取 share 之后的路径部分
        val originalPath = parsed.path ?: ""
        val pathAfterShare = if (originalPath.startsWith("/$shareName")) {
            originalPath.removePrefix("/$shareName")
        } else {
            originalPath
        }

        return Uri.parse("smb://$username:$password@$host/$shareName$pathAfterShare")
    }

    /**
     * 下载 SMB 文件前 1MB 到临时文件
     */
    private suspend fun downloadSmbPrefix(fullSmbUri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("smb_thumb_", ".tmp", context.cacheDir)
            SmbUtils.openSmbFileInputStream(fullSmbUri, "video").use { inputStream ->
                FileOutputStream(tempFile).use { out ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    while (totalRead < SMB_PREVIEW_SIZE) {
                        val toRead = minOf(buffer.size.toLong(), SMB_PREVIEW_SIZE - totalRead).toInt()
                        val bytesRead = inputStream.read(buffer, 0, toRead)
                        if (bytesRead <= 0) break
                        out.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "下载 SMB 文件前缀失败: $fullSmbUri", e)
            null
        }
    }

    /**
     * 压缩 Bitmap 并保存到文件，宽度压缩到 TARGET_WIDTH
     */
    private fun compressAndSave(bitmap: Bitmap, outputFile: File) {
        try {
            val scaled = scaleBitmap(bitmap, TARGET_WIDTH)
            try {
                FileOutputStream(outputFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            } finally {
                if (scaled !== bitmap) {
                    scaled.recycle()
                }
            }
        } finally {
            bitmap.recycle()
        }
        evictOldCacheIfNeeded()
    }

    /**
     * 按宽度等比缩放 Bitmap
     */
    private fun scaleBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width <= targetWidth) return bitmap
        val ratio = targetWidth.toFloat() / bitmap.width
        val targetHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * 对 URI 做 SHA-256 哈希，用作缓存文件名
     */
    private fun hashUri(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(uri.toByteArray())
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * 安全释放 MediaMetadataRetriever
     */
    private fun MediaMetadataRetriever.releaseQuietly() {
        try {
            release()
        } catch (e: Exception) {
            Log.e(TAG, "释放 MediaMetadataRetriever 失败", e)
        }
    }
}

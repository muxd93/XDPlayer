package org.mz.mzdkplayer.tool

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.data.local.FolderVideoEntity
import org.mz.mzdkplayer.data.local.HomeSlotEntity
import org.mz.mzdkplayer.data.model.SMBConnection
import org.mz.mzdkplayer.data.repository.FolderVideoRepository
import org.mz.mzdkplayer.data.repository.HomeSlotRepository
import org.mz.mzdkplayer.data.repository.SMBConnectionRepository
import org.mz.mzdkplayer.data.repository.ElderModeConfig
import org.mz.mzdkplayer.data.repository.SettingsRepository
import org.mz.mzdkplayer.data.repository.ThumbnailManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class WebConfigServer : NanoHTTPD(18080) {

    companion object {
        private const val TAG = "WebConfigServer"
        private val gson = Gson()
    }

    private val context by lazy { MzDkPlayerApplication.context }
    private val smbConnectionRepository by lazy { SMBConnectionRepository(context) }
    private val thumbnailManager by lazy { ThumbnailManager(context) }
    private val ioSemaphore = Semaphore(4)

    fun startServer() {
        try {
            start()
            Log.i(TAG, "WebConfigServer started on port 18080")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WebConfigServer", e)
        }
    }

    fun stopServer() {
        stop()
        Log.i(TAG, "WebConfigServer stopped")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method

        return try {
            when {
                // CORS preflight
                method == Method.OPTIONS -> handleOptions()

                // API routes
                uri.startsWith("/api/") -> handleApi(uri, method, session)

                // Config page
                uri == "/" -> serveConfigPage()

                // 404
                else -> jsonError(Response.Status.NOT_FOUND, "Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request: $uri", e)
            jsonError(Response.Status.INTERNAL_ERROR, "Internal Server Error: ${e.message}")
        }
    }

    private fun handleOptions(): Response {
        val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        return response
    }

    private fun handleApi(uri: String, method: Method, session: IHTTPSession): Response {
        val segments = uri.removePrefix("/api/").split("/").filter { it.isNotEmpty() }

        return when {
            // Slots
            uri == "/api/slots" && method == Method.GET -> getSlots()
            uri == "/api/slots/reorder" && method == Method.PUT -> reorderSlots(session)
            uri == "/api/slots" && method == Method.POST -> createSlot(session)
            segments.size == 2 && segments[0] == "slots" && method == Method.PUT -> updateSlot(segments[1].toIntOrNull(), session)
            segments.size == 2 && segments[0] == "slots" && method == Method.DELETE -> deleteSlot(segments[1].toIntOrNull())
            segments.size == 3 && segments[0] == "slots" && segments[2] == "thumbnail" && method == Method.POST ->
                uploadThumbnail(segments[1].toIntOrNull(), session)

            // Folder Videos
            segments.size == 3 && segments[0] == "slots" && segments[2] == "videos" && method == Method.GET ->
                getSlotVideos(segments[1].toIntOrNull())
            segments.size == 4 && segments[0] == "slots" && segments[2] == "videos" && segments[3] == "reorder" && method == Method.PUT ->
                reorderVideos(segments[1].toIntOrNull(), session)
            segments.size == 3 && segments[0] == "slots" && segments[2] == "videos" && method == Method.POST ->
                addVideo(segments[1].toIntOrNull(), session)
            segments.size == 4 && segments[0] == "slots" && segments[2] == "videos" && method == Method.DELETE ->
                deleteVideo(segments[1].toIntOrNull(), segments[3].toIntOrNull())
            segments.size == 3 && segments[0] == "slots" && segments[2] == "scan" && method == Method.POST ->
                scanSlot(segments[1].toIntOrNull())

            // SMB Connections
            uri == "/api/smb-connections" && method == Method.GET -> getSmbConnections()
            uri == "/api/smb-connections" && method == Method.POST -> addSmbConnection(session)
            segments.size == 2 && segments[0] == "smb-connections" && method == Method.PUT ->
                updateSmbConnection(segments[1], session)
            segments.size == 2 && segments[0] == "smb-connections" && method == Method.DELETE ->
                deleteSmbConnection(segments[1])

            // Browse
            uri.startsWith("/api/browse/smb") && method == Method.GET -> browseSmb(session)
            uri.startsWith("/api/browse/local") && method == Method.GET -> browseLocal(session)

            // Installed Apps
            uri == "/api/installed-apps" && method == Method.GET -> getInstalledApps()

            // Settings
            uri == "/api/settings" && method == Method.GET -> getSettings()
            uri == "/api/settings" && method == Method.PUT -> updateSettings(session)

            // Elder Config
            uri == "/api/elder-config" && method == Method.GET -> getElderConfig()
            uri == "/api/elder-config" && method == Method.PUT -> updateElderConfig(session)

            // System
            uri == "/api/info" && method == Method.GET -> getInfo()
            uri == "/api/qrcode" && method == Method.GET -> getQrCode()

            else -> jsonError(Response.Status.NOT_FOUND, "API not found: $uri")
        }
    }

    // ---- JSON helpers ----

    private fun jsonResponse(data: Any): Response {
        val json = gson.toJson(mapOf("ok" to true, "data" to data))
        val response = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun jsonError(status: Response.Status, message: String): Response {
        val json = gson.toJson(mapOf("ok" to false, "error" to message))
        val response = newFixedLengthResponse(status, "application/json", json)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun parseBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: ""
    }

    // ---- Slots API ----

    private fun getSlots(): Response = runBlocking {
        val slots = HomeSlotRepository.getAllSlotsSync().sortedBy { it.sortOrder }
        jsonResponse(slots)
    }

    private fun reorderSlots(session: IHTTPSession): Response = runBlocking {
        val body = parseBody(session)
        val type = object : TypeToken<Map<String, List<Int>>>() {}.type
        val map: Map<String, List<Int>> = gson.fromJson(body, type)
        val slotIds = map["slotIds"] ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "slotIds required")
        HomeSlotRepository.reorderSlots(slotIds)
        jsonResponse("reordered")
    }

    private fun createSlot(session: IHTTPSession): Response = runBlocking {
        val body = parseBody(session)
        val slot = gson.fromJson(body, HomeSlotEntity::class.java)
        val id = HomeSlotRepository.insertSlot(slot)
        jsonResponse(mapOf("id" to id))
    }

    private fun updateSlot(id: Int?, session: IHTTPSession): Response = runBlocking {
        if (id == null) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid slot id")
        val existing = HomeSlotRepository.getSlotById(id) ?: return@runBlocking jsonError(Response.Status.NOT_FOUND, "Slot not found")
        val body = parseBody(session)
        val updates = gson.fromJson(body, HomeSlotEntity::class.java)
        val merged = existing.copy(
            sortOrder = existing.sortOrder,
            slotType = updates.slotType,
            label = updates.label,
            folderUri = updates.folderUri ?: existing.folderUri,
            folderDataSourceType = updates.folderDataSourceType ?: existing.folderDataSourceType,
            folderConnectionName = updates.folderConnectionName ?: existing.folderConnectionName,
            customThumbnailPath = updates.customThumbnailPath ?: existing.customThumbnailPath,
            videoUri = updates.videoUri ?: existing.videoUri,
            videoDataSourceType = updates.videoDataSourceType ?: existing.videoDataSourceType,
            videoConnectionName = updates.videoConnectionName ?: existing.videoConnectionName,
            videoFileName = updates.videoFileName ?: existing.videoFileName,
            appPackageName = updates.appPackageName ?: existing.appPackageName
        )
        HomeSlotRepository.updateSlot(merged)
        jsonResponse("updated")
    }

    private fun deleteSlot(id: Int?): Response = runBlocking {
        if (id == null) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid slot id")
        HomeSlotRepository.deleteSlotById(id)
        jsonResponse("deleted")
    }

    private fun uploadThumbnail(id: Int?, session: IHTTPSession): Response = runBlocking {
        if (id == null) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid slot id")
        val files = HashMap<String, String>()
        session.parseBody(files)

        // NanoHTTPD puts uploaded file temp path in files map
        val tempPath = files.values.firstOrNull() ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "No file uploaded")

        val tempFile = File(tempPath)
        if (!tempFile.exists()) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Upload file not found")

        // Check file size (max 5MB)
        if (tempFile.length() > 5 * 1024 * 1024) {
            tempFile.delete()
            return@runBlocking jsonError(Response.Status.BAD_REQUEST, "File too large (max 5MB)")
        }

        // Check file type (must be image)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, options)
        if (options.outMimeType == null || !options.outMimeType.startsWith("image/")) {
            tempFile.delete()
            return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid image file")
        }

        val savedFile = thumbnailManager.saveCustomThumbnail(id, FileInputStream(tempFile))
        val slot = HomeSlotRepository.getSlotById(id)
        if (slot != null) {
            HomeSlotRepository.updateSlot(slot.copy(customThumbnailPath = savedFile.absolutePath))
        }
        jsonResponse(mapOf("path" to savedFile.absolutePath))
    }

    // ---- Folder Videos API ----

    private fun getSlotVideos(slotId: Int?): Response = runBlocking {
        if (slotId == null) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid slot id")
        val videos = FolderVideoRepository.getVideosBySlotSync(slotId).sortedBy { it.sortOrder }
        jsonResponse(videos)
    }

    private fun reorderVideos(slotId: Int?, session: IHTTPSession): Response = runBlocking {
        if (slotId == null) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid slot id")
        val body = parseBody(session)
        val type = object : TypeToken<Map<String, List<Int>>>() {}.type
        val map: Map<String, List<Int>> = gson.fromJson(body, type)
        val videoIds = map["videoIds"] ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "videoIds required")
        FolderVideoRepository.reorderVideos(slotId, videoIds)
        jsonResponse("reordered")
    }

    private fun addVideo(slotId: Int?, session: IHTTPSession): Response = runBlocking {
        if (slotId == null) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid slot id")
        val body = parseBody(session)
        val video = gson.fromJson(body, FolderVideoEntity::class.java)
        val existing = FolderVideoRepository.getVideosBySlotSync(slotId)
        val newVideo = video.copy(folderSlotId = slotId, sortOrder = existing.size)
        val id = FolderVideoRepository.insertVideo(newVideo)
        jsonResponse(mapOf("id" to id))
    }

    private fun deleteVideo(slotId: Int?, vid: Int?): Response = runBlocking {
        if (slotId == null) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid slot id")
        if (vid == null) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid video id")
        FolderVideoRepository.deleteVideoById(vid)
        jsonResponse("deleted")
    }

    private fun scanSlot(slotId: Int?): Response = runBlocking {
        ioSemaphore.acquire()
        try {
            if (slotId == null) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Invalid slot id")
            val slot = HomeSlotRepository.getSlotById(slotId) ?: return@runBlocking jsonError(Response.Status.NOT_FOUND, "Slot not found")

            if (slot.slotType != "folder") return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Slot is not a folder type")

            when (slot.folderDataSourceType) {
                "SMB" -> {
                    val connName = slot.folderConnectionName ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "No connection name")
                    val conn = smbConnectionRepository.getConnections().find { it.name == connName }
                        ?: return@runBlocking jsonError(Response.Status.NOT_FOUND, "SMB connection not found: $connName")

                    val folderPath = slot.folderUri ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "No folder URI")
                    // 使用统一的 SMB 路径解析，支持密码含 @ 和 : 的场景
                    val pathInfo = SmbUtils.parseSMBPath(folderPath)
                    val shareName = conn.shareName?.takeIf { it.isNotEmpty() } ?: pathInfo.share
                    val dirPath = pathInfo.path

                    try {
                        val entries = SmbUtils.listSmbDirectory(
                            host = conn.ip ?: pathInfo.server,
                            shareName = shareName,
                            path = dirPath,
                            username = conn.username ?: "guest",
                            password = conn.password ?: ""
                        )

                        val videoFiles = entries.filter { !it.isDirectory && Tools.containsVideoFormat(Tools.extractFileExtension(it.name)) }
                        val newVideos = videoFiles.mapIndexed { index, entry ->
                            val videoUri = "smb://${conn.ip}/$shareName${entry.path}"
                            FolderVideoEntity(
                                folderSlotId = slotId,
                                sortOrder = index,
                                videoUri = videoUri,
                                dataSourceType = "SMB",
                                fileName = entry.name,
                                connectionName = connName
                            )
                        }

                        FolderVideoRepository.scanFolder(slot) { newVideos }
                        jsonResponse(mapOf("scanned" to newVideos.size))
                    } catch (e: Exception) {
                        jsonError(Response.Status.INTERNAL_ERROR, "Scan failed: ${e.message}")
                    }
                }
                "FILE" -> {
                    val folderPath = slot.folderUri ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "No folder URI")
                    val dir = File(folderPath)
                    if (!dir.exists() || !dir.isDirectory) return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Directory not found: $folderPath")

                    val videoFiles = dir.listFiles()?.filter {
                        it.isFile && Tools.containsVideoFormat(Tools.extractFileExtension(it.name))
                    } ?: emptyList()

                    val newVideos = videoFiles.mapIndexed { index, file ->
                        FolderVideoEntity(
                            folderSlotId = slotId,
                            sortOrder = index,
                            videoUri = file.absolutePath,
                            dataSourceType = "FILE",
                            fileName = file.name,
                            connectionName = ""
                        )
                    }

                    FolderVideoRepository.scanFolder(slot) { newVideos }
                    jsonResponse(mapOf("scanned" to newVideos.size))
                }
                else -> jsonError(Response.Status.BAD_REQUEST, "Unsupported data source type: ${slot.folderDataSourceType}")
            }
        } finally {
            ioSemaphore.release()
        }
    }

    // ---- SMB Connections API ----

    private fun getSmbConnections(): Response {
        val connections = smbConnectionRepository.getConnections()
        return jsonResponse(connections)
    }

    private fun addSmbConnection(session: IHTTPSession): Response {
        val body = parseBody(session)
        val conn = gson.fromJson(body, SMBConnection::class.java)
        val withId = if (conn.id.isNullOrEmpty()) conn.copy(id = java.util.UUID.randomUUID().toString()) else conn
        smbConnectionRepository.addConnection(withId)
        return jsonResponse(withId)
    }

    private fun updateSmbConnection(id: String, session: IHTTPSession): Response {
        val body = parseBody(session)
        val updated = gson.fromJson(body, SMBConnection::class.java).copy(id = id)
        if (smbConnectionRepository.getConnectionById(id) == null) {
            return jsonError(Response.Status.NOT_FOUND, "Connection not found")
        }
        smbConnectionRepository.updateConnection(updated)
        return jsonResponse("updated")
    }

    private fun deleteSmbConnection(id: String): Response {
        smbConnectionRepository.deleteConnection(id)
        return jsonResponse("deleted")
    }

    // ---- Browse API ----

    private fun browseSmb(session: IHTTPSession): Response = runBlocking {
        ioSemaphore.acquire()
        try {
            val params = session.parameters ?: emptyMap()
            val connId = params["connId"]?.firstOrNull() ?: return@runBlocking jsonError(Response.Status.BAD_REQUEST, "connId required")
            val path = params["path"]?.firstOrNull() ?: "/"

            val conn = smbConnectionRepository.getConnections().find { it.id == connId }
                ?: return@runBlocking jsonError(Response.Status.NOT_FOUND, "Connection not found")

            try {
                val entries = SmbUtils.listSmbDirectory(
                    host = conn.ip ?: "",
                    shareName = conn.shareName ?: "",
                    path = path,
                    username = conn.username ?: "guest",
                    password = conn.password ?: ""
                )
                jsonResponse(entries)
            } catch (e: Exception) {
                jsonError(Response.Status.INTERNAL_ERROR, "Browse failed: ${e.message}")
            }
        } finally {
            ioSemaphore.release()
        }
    }

    private fun browseLocal(session: IHTTPSession): Response {
        val params = session.parameters ?: emptyMap()
        val path = params["path"]?.firstOrNull() ?: "/storage"

        val allowedRoots = listOf("/storage/emulated/0", "/sdcard", "/storage")
        val resolvedPath = File(path).canonicalPath
        if (allowedRoots.none { resolvedPath.startsWith(it) }) {
            return jsonError(Response.Status.FORBIDDEN, "Access denied")
        }

        val dir = File(resolvedPath)
        if (!dir.exists() || !dir.isDirectory) return jsonError(Response.Status.NOT_FOUND, "Directory not found")

        val entries = dir.listFiles()?.map { file ->
            mapOf(
                "name" to file.name,
                "path" to file.absolutePath,
                "isDirectory" to file.isDirectory,
                "size" to file.length()
            )
        }?.sortedWith(compareByDescending<Map<String, Any>> { it["isDirectory"] as Boolean }.thenBy { it["name"] as String })
            ?: emptyList()

        return jsonResponse(entries)
    }

    // ---- Installed Apps API ----

    private fun getInstalledApps(): Response {
        val apps = AppLauncherHelper.getLaunchableApps().map { app ->
            mapOf("packageName" to app.packageName, "label" to app.label)
        }
        return jsonResponse(apps)
    }

    // ---- Settings API ----

    private fun getSettings(): Response {
        val elderConfig = ElderModeConfig.load()
        val settings = mapOf(
            "appMode" to SettingsRepository.appMode,
            "slotCount" to elderConfig.slotCount,
            "showRecent" to elderConfig.showRecent,
            "recentCount" to elderConfig.recentCount,
            "webConfigEnabled" to SettingsRepository.webConfigEnabled
        )
        return jsonResponse(settings)
    }

    private fun updateSettings(session: IHTTPSession): Response {
        val body = parseBody(session)
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(body, type)

        // recentCount 统一由 /api/elder-config 管理，此处不再处理

        map["webConfigEnabled"]?.let {
            val enabled = it as? Boolean ?: return@let
            SettingsRepository.webConfigEnabled = enabled
            // 问题27修复：禁用webConfigEnabled后停止运行中的服务器
            if (!enabled) {
                stopServer()
            }
        }

        return jsonResponse("updated")
    }

    // ---- Elder Config API ----

    private fun getElderConfig(): Response {
        val config = ElderModeConfig.load()
        return jsonResponse(config)
    }

    private fun updateElderConfig(session: IHTTPSession): Response {
        val body = parseBody(session)
        if (body.isBlank()) {
            return jsonError(Response.Status.BAD_REQUEST, "Empty body")
        }
        // 部分更新：仅更新 Web 端可配置的参数，保留 TV 端管理的参数
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(body, type) ?: emptyMap()
        var config = ElderModeConfig.load()
        map["controlHideSeconds"]?.let { config = config.copy(controlHideSeconds = (it as? Number)?.toInt() ?: config.controlHideSeconds) }
        map["seekStepSeconds"]?.let { config = config.copy(seekStepSeconds = (it as? Number)?.toInt() ?: config.seekStepSeconds) }
        map["minSubtitleFontSize"]?.let { config = config.copy(minSubtitleFontSize = (it as? Number)?.toInt() ?: config.minSubtitleFontSize) }
        map["hideDanmaku"]?.let { config = config.copy(hideDanmaku = it as? Boolean ?: config.hideDanmaku) }
        map["hideNetworkSpeed"]?.let { config = config.copy(hideNetworkSpeed = it as? Boolean ?: config.hideNetworkSpeed) }
        map["hideAdvancedControls"]?.let { config = config.copy(hideAdvancedControls = it as? Boolean ?: config.hideAdvancedControls) }
        map["recentCount"]?.let { config = config.copy(recentCount = (it as? Number)?.toInt() ?: config.recentCount) }
        ElderModeConfig.save(config)
        return jsonResponse("updated")
    }

    // ---- System API ----

    private fun getInfo(): Response = runBlocking {
        val ip = Tools.getLocalIpAddress() ?: "unknown"
        val slots = HomeSlotRepository.getAllSlotsSync()
        val elderConfig = ElderModeConfig.load()
        val info = mapOf(
            "ip" to ip,
            "port" to 18080,
            "appMode" to SettingsRepository.appMode,
            "slotCount" to elderConfig.slotCount,
            "actualSlotCount" to slots.size,
            "showRecent" to elderConfig.showRecent,
            "webConfigEnabled" to SettingsRepository.webConfigEnabled
        )
        jsonResponse(info)
    }

    private fun getQrCode(): Response {
        val ip = Tools.getLocalIpAddress() ?: "unknown"
        val url = "http://$ip:18080"
        val bitmap = Tools.generateQRCodeBitmap(url, 512)
            ?: return jsonError(Response.Status.INTERNAL_ERROR, "Failed to generate QR code")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle()
        val imageData = baos.toByteArray()
        return newFixedLengthResponse(Response.Status.OK, "image/png",
            ByteArrayInputStream(imageData), imageData.size.toLong())
    }

    // ---- Config Page ----

    private fun serveConfigPage(): Response {
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, getConfigPageHtml())
    }

    private fun getConfigPageHtml(): String {
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MZ DK Player 配置</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;background:#f0f2f5;color:#333;display:flex;min-height:100vh}
.sidebar{width:220px;background:#1a1a2e;color:#fff;padding:20px 0;position:fixed;top:0;left:0;bottom:0;overflow-y:auto;z-index:10}
.sidebar h2{padding:0 20px 20px;font-size:18px;border-bottom:1px solid rgba(255,255,255,.1)}
.sidebar a{display:block;padding:12px 20px;color:rgba(255,255,255,.7);text-decoration:none;transition:.2s;cursor:pointer}
.sidebar a:hover,.sidebar a.active{background:rgba(255,255,255,.1);color:#fff}
.main{margin-left:220px;flex:1;padding:24px;max-width:900px}
.page{display:none}
.page.active{display:block}
h1{font-size:22px;margin-bottom:20px;color:#1a1a2e}
.card{background:#fff;border-radius:10px;padding:20px;margin-bottom:16px;box-shadow:0 1px 3px rgba(0,0,0,.08)}
.card h3{font-size:16px;margin-bottom:12px;color:#444}
.btn{padding:8px 16px;border:none;border-radius:6px;cursor:pointer;font-size:14px;transition:.2s}
.btn-primary{background:#4361ee;color:#fff}.btn-primary:hover{background:#3a56d4}
.btn-danger{background:#e74c3c;color:#fff}.btn-danger:hover{background:#c0392b}
.btn-sm{padding:5px 10px;font-size:12px}
input,select{padding:8px 12px;border:1px solid #ddd;border-radius:6px;font-size:14px;width:100%}
input:focus,select:focus{outline:none;border-color:#4361ee}
label{display:block;font-size:13px;font-weight:600;margin-bottom:4px;color:#666}
.form-group{margin-bottom:12px}
.row{display:flex;gap:12px;flex-wrap:wrap}
.col{flex:1;min-width:200px}
table{width:100%;border-collapse:collapse}
th,td{padding:10px 12px;text-align:left;border-bottom:1px solid #eee;font-size:14px}
th{background:#f8f9fa;font-weight:600;color:#555}
.badge{display:inline-block;padding:2px 8px;border-radius:4px;font-size:12px;font-weight:600}
.badge-folder{background:#e3f2fd;color:#1565c0}
.badge-video{background:#e8f5e9;color:#2e7d32}
.badge-app{background:#fff3e0;color:#e65100}
.badge-empty{background:#f5f5f5;color:#999}
.drag-handle{cursor:grab;color:#ccc;margin-right:8px}
.drag-handle:active{cursor:grabbing}
tr.dragging{opacity:.5}
.toast{position:fixed;top:20px;right:20px;padding:12px 20px;border-radius:8px;color:#fff;font-size:14px;z-index:999;animation:slideIn .3s}
.toast-success{background:#27ae60}.toast-error{background:#e74c3c}
@keyframes slideIn{from{transform:translateX(100%);opacity:0}to{transform:translateX(0);opacity:1}}
.empty-state{text-align:center;padding:40px;color:#999}
.qr-container{text-align:center;padding:20px}
.qr-container canvas{border:8px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.1)}
@media(max-width:768px){.sidebar{width:100%;position:relative;bottom:auto}.main{margin-left:0}.sidebar a{display:inline-block;padding:8px 12px}}
</style>
</head>
<body>
<nav class="sidebar">
<h2>📺 DK Player</h2>
<a class="active" onclick="showPage('dashboard')">📊 仪表盘</a>
<a onclick="showPage('slots')">🗂️ 栏位管理</a>
<a onclick="showPage('videos')">📁 文件夹管理</a>
<a onclick="showPage('connections')">🔗 连接管理</a>
<a onclick="showPage('settings')">⚙️ 设置</a>
<a onclick="showPage('elder')">👴 老人模式配置</a>
</nav>
<div class="main">

<!-- Dashboard -->
<div id="page-dashboard" class="page active">
<h1>仪表盘</h1>
<div class="card"><h3>TV 信息</h3>
<div id="info-content"><p>加载中...</p></div></div>
<div class="card"><h3>扫码配置</h3>
<div class="qr-container"><img id="qr-img" src="/api/qrcode" alt="扫码配置" style="max-width:200px;border:8px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.1)">
<p id="qr-url" style="margin-top:12px;color:#666;font-size:14px"></p></div></div>
</div>

<!-- Slots -->
<div id="page-slots" class="page">
<h1>栏位管理</h1>
<div class="card" style="margin-bottom:12px">
<div class="row">
<div class="col"><button class="btn btn-primary" onclick="showAddSlot()">+ 添加栏位</button></div>
<div class="col" style="text-align:right"><button class="btn btn-sm" onclick="saveSlotOrder()">💾 保存排序</button></div>
</div></div>
<div id="slots-list" class="card"><p class="empty-state">加载中...</p></div>
</div>

<!-- Slot Add/Edit Modal -->
<div id="slot-modal" style="display:none;position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.5);z-index:100">
<div style="background:#fff;border-radius:12px;padding:24px;max-width:500px;margin:60px auto;max-height:80vh;overflow-y:auto">
<h3 id="slot-modal-title" style="margin-bottom:16px">添加栏位</h3>
<input type="hidden" id="slot-edit-id">
<div class="form-group"><label>标签名称</label><input id="slot-label" placeholder="栏位名称"></div>
<div class="form-group"><label>栏位类型</label><select id="slot-type" onchange="onSlotTypeChange()">
<option value="empty">空</option><option value="folder">文件夹</option><option value="video">视频</option><option value="app">应用</option>
</select></div>
<div id="slot-folder-fields" style="display:none">
<div class="form-group"><label>文件夹路径</label><div style="display:flex;gap:8px"><input id="slot-folder-uri" placeholder="文件夹路径" style="flex:1"><button class="btn btn-sm" onclick="openBrowser('slot-folder-uri','folder')">浏览</button></div></div>
<div class="form-group"><label>数据源类型</label><select id="slot-folder-dst" onchange="onFolderDstChange(this.value)"><option value="FILE">本地</option><option value="SMB">SMB</option></select></div>
<div class="form-group"><label>SMB 连接</label><select id="slot-folder-conn"><option value="">选择连接...</option></select></div>
</div>
<div id="slot-video-fields" style="display:none">
<div class="form-group"><label>视频路径</label><div style="display:flex;gap:8px"><input id="slot-video-uri" placeholder="视频文件路径" style="flex:1"><button class="btn btn-sm" onclick="openBrowser('slot-video-uri','video')">浏览</button></div></div>
<div class="form-group"><label>数据源类型</label><select id="slot-video-dst" onchange="onVideoDstChange(this.value)"><option value="FILE">本地</option><option value="SMB">SMB</option></select></div>
<div class="form-group"><label>SMB 连接</label><select id="slot-video-conn"><option value="">选择连接...</option></select></div>
<div class="form-group"><label>视频文件名</label><input id="slot-video-fname" placeholder="显示名称"></div>
</div>
<div id="slot-app-fields" style="display:none">
<div class="form-group"><label>应用包名</label><select id="slot-app-pkg"><option value="">选择应用...</option></select></div>
</div>
<div class="form-group"><label>自定义缩略图</label><input type="file" id="slot-thumbnail" accept="image/*"></div>
<div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">
<button class="btn" onclick="closeSlotModal()">取消</button>
<button class="btn btn-primary" onclick="saveSlot()">保存</button>
</div>
</div></div>

<!-- Videos -->
<div id="page-videos" class="page">
<h1>文件夹管理</h1>
<div class="card"><h3>选择文件夹栏位</h3><select id="video-slot-select" onchange="loadSlotVideos()">
<option value="">选择栏位...</option></select></div>
<div id="videos-panel" style="display:none">
<div class="card" style="margin-bottom:12px"><div class="row">
<div class="col"><button class="btn btn-primary btn-sm" onclick="showAddVideo()">+ 添加视频</button></div>
<div class="col"><button class="btn btn-sm" onclick="scanFolder()">🔍 扫描文件夹</button></div>
<div class="col" style="text-align:right"><button class="btn btn-sm" onclick="saveVideoOrder()">💾 保存排序</button></div>
</div></div>
<div id="videos-list" class="card"><p class="empty-state">选择栏位后加载</p></div>
</div></div>

<!-- Connections -->
<div id="page-connections" class="page">
<h1>连接管理</h1>
<div class="card" style="margin-bottom:12px"><button class="btn btn-primary" onclick="showAddConnection()">+ 添加连接</button></div>
<div id="connections-list" class="card"><p class="empty-state">加载中...</p></div>
</div>

<!-- Connection Modal -->
<div id="conn-modal" style="display:none;position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.5);z-index:100">
<div style="background:#fff;border-radius:12px;padding:24px;max-width:500px;margin:60px auto">
<h3 id="conn-modal-title" style="margin-bottom:16px">添加连接</h3>
<input type="hidden" id="conn-edit-id">
<div class="form-group"><label>连接名称</label><input id="conn-name" placeholder="如：办公室NAS"></div>
<div class="form-group"><label>服务器地址</label><input id="conn-ip" placeholder="IP 地址"></div>
<div class="form-group"><label>用户名</label><input id="conn-user" placeholder="用户名"></div>
<div class="form-group"><label>密码</label><input id="conn-pass" type="password" placeholder="密码"></div>
<div class="form-group"><label>共享名称</label><input id="conn-share" placeholder="共享文件夹名称"></div>
<div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">
<button class="btn" onclick="closeConnModal()">取消</button>
<button class="btn btn-primary" onclick="saveConnection()">保存</button>
</div>
</div></div>

<!-- File Browser Modal -->
<div id="browser-modal" style="display:none;position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.5);z-index:100">
<div style="background:#fff;border-radius:12px;padding:24px;max-width:600px;margin:40px auto;max-height:80vh;overflow-y:auto">
<h3 style="margin-bottom:16px">浏览文件</h3>
<div id="browser-toolbar" style="display:flex;gap:8px;margin-bottom:12px;align-items:center">
<button class="btn btn-sm" onclick="browserGoUp()">⬆ 上级</button>
<span id="browser-path" style="flex:1;font-size:13px;color:#666;word-break:break-all"></span>
</div>
<div id="browser-list" style="min-height:200px;max-height:400px;overflow-y:auto;border:1px solid #eee;border-radius:6px"></div>
<div style="display:flex;gap:8px;justify-content:flex-end;margin-top:16px">
<button class="btn" onclick="closeBrowser()">取消</button>
<button class="btn btn-primary" onclick="confirmBrowser()">选择当前路径</button>
</div>
</div></div>

<!-- Settings -->
<div id="page-settings" class="page">
<h1>设置</h1>
<div class="card"><h3>模式设置</h3>
<p id="set-current-mode" style="margin-bottom:12px"></p>
<p id="set-web-config-status" style="color:#999;font-size:13px"></p>
</div>
<div class="card" style="text-align:right"><button class="btn btn-primary" onclick="saveSettings()">保存设置</button></div>
</div>

<!-- Elder Config -->
<div id="page-elder" class="page">
<h1>老人模式配置</h1>
<div class="card"><h3>播放器控制</h3>
<div class="form-group"><label>控制栏隐藏时间（秒）</label><input id="ec-control-hide" type="number" min="1" max="30"></div>
<div class="form-group"><label>快进快退步进（秒）</label><input id="ec-seek-step" type="number" min="1" max="60"></div>
<p style="color:#999;font-size:13px">自动续播和播放结束停留请在 TV 端设置</p>
</div>
<div class="card"><h3>显示设置</h3>
<div class="form-group"><label>字幕最小字号（sp）</label><input id="ec-subtitle-size" type="number" min="12" max="72"></div>
<div class="form-group"><label><input id="ec-hide-danmaku" type="checkbox"> 隐藏弹幕</label></div>
<div class="form-group"><label><input id="ec-hide-speed" type="checkbox"> 隐藏网速显示</label></div>
<div class="form-group"><label><input id="ec-hide-advanced" type="checkbox"> 隐藏高级控制按钮</label></div>
</div>
<div class="card"><h3>首页设置</h3>
<div class="form-group"><label>最近播放数量</label><input id="ec-recent-count" type="number" min="1" max="50"></div>
<p style="color:#999;font-size:13px">栏位数量和显示最近播放请在 TV 端设置</p>
</div>
<div class="card" style="text-align:right"><button class="btn btn-primary" onclick="saveElderConfig()">保存老人模式配置</button></div>
</div>

</div>

<script>
const API='';
let slotOrder=[];
let videoOrder=[];

function escapeHtml(text){if(!text)return '';var div=document.createElement('div');div.textContent=text;return div.innerHTML}
function toast(msg,ok=true){const d=document.createElement('div');d.className='toast '+(ok?'toast-success':'toast-error');d.textContent=msg;document.body.appendChild(d);setTimeout(()=>d.remove(),3000)}
async function api(path,opts={}){const r=await fetch(API+path,{...opts,headers:{'Content-Type':'application/json',...(opts.headers||{})}});const j=await r.json();if(!j.ok)throw new Error(j.error);return j.data}

function showPage(id){document.querySelectorAll('.page').forEach(p=>p.classList.remove('active'));document.getElementById('page-'+id).classList.add('active');document.querySelectorAll('.sidebar a').forEach(a=>a.classList.remove('active'));event.target.classList.add('active');if(id==='dashboard')loadDashboard();if(id==='slots')loadSlots();if(id==='videos')loadVideoSlotSelect();if(id==='connections')loadConnections();if(id==='settings')loadSettings();if(id==='elder')loadElderConfig()}

// Dashboard
async function loadDashboard(){try{const info=await api('/api/info');document.getElementById('info-content').innerHTML='<div class="row"><div class="col"><p><strong>IP 地址：</strong>'+escapeHtml(info.ip)+'</p><p><strong>端口：</strong>'+escapeHtml(String(info.port))+'</p><p><strong>当前模式：</strong>'+(info.appMode==='elder'?'老人模式':'标准模式')+'</p></div><div class="col"><p><strong>栏位数量：</strong>'+escapeHtml(String(info.actualSlotCount))+'/'+escapeHtml(String(info.slotCount))+'</p><p><strong>显示最近播放：</strong>'+(info.showRecent?'是':'否')+'</p><p><strong>Web 配置：</strong>'+(info.webConfigEnabled?'已启用':'已禁用')+'</p></div></div>';document.getElementById('qr-url').textContent='http://'+info.ip+':'+info.port+'/'}catch(e){document.getElementById('info-content').innerHTML='<p style="color:red">加载失败: '+escapeHtml(e.message)+'</p>'}}

// Slots
async function loadSlots(){try{const slots=await api('/api/slots');slotOrder=slots.map(s=>s.id);const html=slots.length?'<table><thead><tr><th></th><th>ID</th><th>标签</th><th>类型</th><th>详情</th><th>操作</th></tr></thead><tbody>'+slots.map(s=>'<tr draggable="true" data-id="'+s.id+'"><td class="drag-handle">⠿</td><td>'+s.id+'</td><td>'+escapeHtml(s.label||'-')+'</td><td><span class="badge badge-'+s.slotType+'">'+escapeHtml(s.slotType)+'</span></td><td>'+slotDetail(s)+'</td><td><button class="btn btn-sm btn-primary" onclick="editSlot('+s.id+')">编辑</button> <button class="btn btn-sm btn-danger" onclick="deleteSlot('+s.id+')">删除</button></td></tr>').join('')+'</tbody></table>':'<p class="empty-state">暂无栏位</p>';document.getElementById('slots-list').innerHTML=html;initSlotDrag()}catch(e){document.getElementById('slots-list').innerHTML='<p style="color:red">加载失败: '+escapeHtml(e.message)+'</p>'}}

function slotDetail(s){if(s.slotType==='folder')return escapeHtml(s.folderUri||'-')+' ('+escapeHtml(s.folderDataSourceType||'')+')';if(s.slotType==='video')return escapeHtml(s.videoFileName||s.videoUri||'-');if(s.slotType==='app')return escapeHtml(s.appPackageName||'-');return '-'}

function initSlotDrag(){const tbody=document.querySelector('#slots-list tbody');if(!tbody)return;let dragRow=null;tbody.querySelectorAll('tr').forEach(tr=>{tr.addEventListener('dragstart',()=>{dragRow=tr;tr.classList.add('dragging')});tr.addEventListener('dragend',()=>{tr.classList.remove('dragging');dragRow=null;updateSlotOrder()});tr.addEventListener('dragover',e=>{e.preventDefault();if(dragRow&&dragRow!==tr){const rect=tr.getBoundingClientRect();const mid=rect.top+rect.height/2;tbody.insertBefore(dragRow,e.clientY<mid?tr:tr.nextSibling)}})})}

function updateSlotOrder(){const rows=document.querySelectorAll('#slots-list tbody tr');slotOrder=Array.from(rows).map(r=>parseInt(r.dataset.id));const btn=document.querySelector('#page-slots .btn-sm');if(btn)btn.style.background='#e74c3c'}

async function saveSlotOrder(){try{await api('/api/slots/reorder',{method:'PUT',body:JSON.stringify({slotIds:slotOrder})});toast('排序已保存');const btn=document.querySelector('#page-slots .btn-sm');if(btn)btn.style.background=''}catch(e){toast('保存失败: '+e.message,false)}}

function showAddSlot(){document.getElementById('slot-modal-title').textContent='添加栏位';document.getElementById('slot-edit-id').value='';document.getElementById('slot-label').value='';document.getElementById('slot-type').value='empty';onSlotTypeChange();document.getElementById('slot-modal').style.display='block';loadApps();loadSmbConnectionOptions()}

async function editSlot(id){try{const slots=await api('/api/slots');const s=slots.find(x=>x.id===id);if(!s)return;document.getElementById('slot-modal-title').textContent='编辑栏位';document.getElementById('slot-edit-id').value=s.id;document.getElementById('slot-label').value=s.label||'';document.getElementById('slot-type').value=s.slotType;onSlotTypeChange();await loadSmbConnectionOptions();if(s.slotType==='folder'){document.getElementById('slot-folder-uri').value=s.folderUri||'';document.getElementById('slot-folder-dst').value=s.folderDataSourceType||'FILE';document.getElementById('slot-folder-conn').value=s.folderConnectionName||''}if(s.slotType==='video'){document.getElementById('slot-video-uri').value=s.videoUri||'';document.getElementById('slot-video-dst').value=s.videoDataSourceType||'FILE';document.getElementById('slot-video-conn').value=s.videoConnectionName||'';document.getElementById('slot-video-fname').value=s.videoFileName||''}if(s.slotType==='app'){await loadApps();document.getElementById('slot-app-pkg').value=s.appPackageName||''}document.getElementById('slot-modal').style.display='block'}catch(e){toast('加载失败: '+e.message,false)}}

function onSlotTypeChange(){const t=document.getElementById('slot-type').value;document.getElementById('slot-folder-fields').style.display=t==='folder'?'block':'none';document.getElementById('slot-video-fields').style.display=t==='video'?'block':'none';document.getElementById('slot-app-fields').style.display=t==='app'?'block':'none'}

function closeSlotModal(){document.getElementById('slot-modal').style.display='none'}

async function loadApps(){try{const apps=await api('/api/installed-apps');const sel=document.getElementById('slot-app-pkg');sel.innerHTML='<option value="">选择应用...</option>'+apps.map(a=>'<option value="'+escapeHtml(a.packageName)+'">'+escapeHtml(a.label)+' ('+escapeHtml(a.packageName)+')</option>').join('')}catch(e){}}

async function loadSmbConnectionOptions(){try{const conns=await api('/api/smb-connections');const html='<option value="">选择连接...</option>'+conns.map(c=>'<option value="'+escapeHtml(c.name)+'">'+escapeHtml(c.name)+'</option>').join('');document.getElementById('slot-folder-conn').innerHTML=html;document.getElementById('slot-video-conn').innerHTML=html;const addVideoConn=document.getElementById('add-video-conn');if(addVideoConn)addVideoConn.innerHTML=html}catch(e){}}

function onFolderDstChange(val){const connSel=document.getElementById('slot-folder-conn');connSel.parentElement.style.display=val==='SMB'?'block':'none'}
function onVideoDstChange(val){const connSel=document.getElementById('slot-video-conn');connSel.parentElement.style.display=val==='SMB'?'block':'none'}

async function saveSlot(){const editId=document.getElementById('slot-edit-id').value;const type=document.getElementById('slot-type').value;const label=document.getElementById('slot-label').value;const obj={sortOrder:0,slotType:type,label:label};if(type==='folder'){obj.folderUri=document.getElementById('slot-folder-uri').value;obj.folderDataSourceType=document.getElementById('slot-folder-dst').value;obj.folderConnectionName=document.getElementById('slot-folder-conn').value}if(type==='video'){obj.videoUri=document.getElementById('slot-video-uri').value;obj.videoDataSourceType=document.getElementById('slot-video-dst').value;obj.videoConnectionName=document.getElementById('slot-video-conn').value;obj.videoFileName=document.getElementById('slot-video-fname').value}if(type==='app'){obj.appPackageName=document.getElementById('slot-app-pkg').value}try{var slotId=editId;if(editId){await api('/api/slots/'+editId,{method:'PUT',body:JSON.stringify(obj)})}else{const r=await api('/api/slots',{method:'POST',body:JSON.stringify(obj)});slotId=r.id}const thumbInput=document.getElementById('slot-thumbnail');if(thumbInput.files.length>0&&slotId){const fd=new FormData();fd.append('file',thumbInput.files[0]);await fetch(API+'/api/slots/'+slotId+'/thumbnail',{method:'POST',body:fd})}closeSlotModal();loadSlots();toast(editId?'栏位已更新':'栏位已创建')}catch(e){toast('保存失败: '+e.message,false)}}

async function deleteSlot(id){if(!confirm('确定删除此栏位？'))return;try{await api('/api/slots/'+id,{method:'DELETE'});loadSlots();toast('已删除')}catch(e){toast('删除失败: '+e.message,false)}}

// Videos
async function loadVideoSlotSelect(){try{const slots=await api('/api/slots');const folders=slots.filter(s=>s.slotType==='folder');document.getElementById('video-slot-select').innerHTML='<option value="">选择栏位...</option>'+folders.map(s=>'<option value="'+s.id+'">'+escapeHtml(s.label||'栏位 '+s.id)+'</option>').join('')}catch(e){}}

async function loadSlotVideos(){const slotId=document.getElementById('video-slot-select').value;if(!slotId){document.getElementById('videos-panel').style.display='none';return}document.getElementById('videos-panel').style.display='block';try{const videos=await api('/api/slots/'+slotId+'/videos');videoOrder=videos.map(v=>v.id);if(videos.length){document.getElementById('videos-list').innerHTML='<table><thead><tr><th></th><th>ID</th><th>文件名</th><th>数据源</th><th>操作</th></tr></thead><tbody>'+videos.map(v=>'<tr draggable="true" data-id="'+v.id+'"><td class="drag-handle">⠿</td><td>'+v.id+'</td><td>'+escapeHtml(v.fileName)+'</td><td>'+escapeHtml(v.dataSourceType)+'</td><td><button class="btn btn-sm btn-danger" onclick="deleteVideo('+slotId+','+v.id+')">删除</button></td></tr>').join('')+'</tbody></table>';initVideoDrag()}else{document.getElementById('videos-list').innerHTML='<p class="empty-state">暂无视频</p>'}}catch(e){document.getElementById('videos-list').innerHTML='<p style="color:red">加载失败: '+escapeHtml(e.message)+'</p>'}}

function initVideoDrag(){const tbody=document.querySelector('#videos-list tbody');if(!tbody)return;let dragRow=null;tbody.querySelectorAll('tr').forEach(tr=>{tr.addEventListener('dragstart',()=>{dragRow=tr;tr.classList.add('dragging')});tr.addEventListener('dragend',()=>{tr.classList.remove('dragging');dragRow=null;updateVideoOrder()});tr.addEventListener('dragover',e=>{e.preventDefault();if(dragRow&&dragRow!==tr){const rect=tr.getBoundingClientRect();const mid=rect.top+rect.height/2;tbody.insertBefore(dragRow,e.clientY<mid?tr:tr.nextSibling)}})})}

function updateVideoOrder(){const rows=document.querySelectorAll('#videos-list tbody tr');videoOrder=Array.from(rows).map(r=>parseInt(r.dataset.id));const btn=document.querySelector('#videos-panel .btn-sm[onclick*="saveVideoOrder"]');if(btn)btn.style.background='#e74c3c'}

async function saveVideoOrder(){const slotId=document.getElementById('video-slot-select').value;if(!slotId)return;try{await api('/api/slots/'+slotId+'/videos/reorder',{method:'PUT',body:JSON.stringify({videoIds:videoOrder})});toast('排序已保存');const btn=document.querySelector('#videos-panel .btn-sm[onclick*="saveVideoOrder"]');if(btn)btn.style.background=''}catch(e){toast('保存失败: '+e.message,false)}}

function showAddVideo(){const slotId=document.getElementById('video-slot-select').value;if(!slotId){toast('请先选择栏位',false);return}const html='<div class="card"><h3>添加视频</h3><div class="form-group"><label>视频路径</label><div style="display:flex;gap:8px"><input id="add-video-uri" placeholder="视频文件路径" style="flex:1"><button class="btn btn-sm" onclick="openBrowser(\'add-video-uri\',\'addVideo\')">浏览</button></div></div><div class="form-group"><label>数据源类型</label><select id="add-video-dst" onchange="onAddVideoDstChange(this.value)"><option value="FILE">本地</option><option value="SMB">SMB</option></select></div><div class="form-group"><label>文件名</label><input id="add-video-fname" placeholder="显示名称"></div><div class="form-group" id="add-video-conn-group" style="display:none"><label>SMB 连接</label><select id="add-video-conn"><option value="">选择连接...</option></select></div><button class="btn btn-primary" onclick="addVideo('+slotId+')">添加</button> <button class="btn" onclick="loadSlotVideos()">取消</button></div>';document.getElementById('videos-list').innerHTML=html;loadSmbConnectionOptions()}

function onAddVideoDstChange(val){document.getElementById('add-video-conn-group').style.display=val==='SMB'?'block':'none'}

async function addVideo(slotId){const obj={videoUri:document.getElementById('add-video-uri').value,dataSourceType:document.getElementById('add-video-dst').value,fileName:document.getElementById('add-video-fname').value||'未命名',connectionName:document.getElementById('add-video-conn').value||''};try{await api('/api/slots/'+slotId+'/videos',{method:'POST',body:JSON.stringify(obj)});loadSlotVideos();toast('视频已添加')}catch(e){toast('添加失败: '+e.message,false)}}

async function deleteVideo(slotId,vid){if(!confirm('确定删除此视频？'))return;try{await api('/api/slots/'+slotId+'/videos/'+vid,{method:'DELETE'});loadSlotVideos();toast('已删除')}catch(e){toast('删除失败: '+e.message,false)}}

async function scanFolder(){const slotId=document.getElementById('video-slot-select').value;if(!slotId){toast('请先选择栏位',false);return}try{toast('正在扫描...');const r=await api('/api/slots/'+slotId+'/scan',{method:'POST'});loadSlotVideos();toast('扫描完成，发现 '+r.scanned+' 个视频')}catch(e){toast('扫描失败: '+e.message,false)}}

// Connections
async function loadConnections(){try{const conns=await api('/api/smb-connections');if(conns.length){document.getElementById('connections-list').innerHTML='<table><thead><tr><th>ID</th><th>名称</th><th>地址</th><th>用户名</th><th>共享</th><th>操作</th></tr></thead><tbody>'+conns.map(c=>'<tr><td style="font-size:11px">'+escapeHtml(c.id)+'</td><td>'+escapeHtml(c.name)+'</td><td>'+escapeHtml(c.ip)+'</td><td>'+escapeHtml(c.username)+'</td><td>'+escapeHtml(c.shareName)+'</td><td><button class="btn btn-sm btn-primary" onclick="editConnection(\''+c.id+'\')">编辑</button> <button class="btn btn-sm btn-danger" onclick="deleteConnection(\''+c.id+'\')">删除</button></td></tr>').join('')+'</tbody></table>'}else{document.getElementById('connections-list').innerHTML='<p class="empty-state">暂无连接</p>'}}catch(e){document.getElementById('connections-list').innerHTML='<p style="color:red">加载失败: '+escapeHtml(e.message)+'</p>'}}

function showAddConnection(){document.getElementById('conn-modal-title').textContent='添加连接';document.getElementById('conn-edit-id').value='';document.getElementById('conn-name').value='';document.getElementById('conn-ip').value='';document.getElementById('conn-user').value='';document.getElementById('conn-pass').value='';document.getElementById('conn-share').value='';document.getElementById('conn-modal').style.display='block'}

async function editConnection(id){try{const conns=await api('/api/smb-connections');const c=conns.find(x=>x.id===id);if(!c)return;document.getElementById('conn-modal-title').textContent='编辑连接';document.getElementById('conn-edit-id').value=c.id;document.getElementById('conn-name').value=c.name||'';document.getElementById('conn-ip').value=c.ip||'';document.getElementById('conn-user').value=c.username||'';document.getElementById('conn-pass').value=c.password||'';document.getElementById('conn-share').value=c.shareName||'';document.getElementById('conn-modal').style.display='block'}catch(e){toast('加载失败: '+e.message,false)}}

function closeConnModal(){document.getElementById('conn-modal').style.display='none'}

async function saveConnection(){const editId=document.getElementById('conn-edit-id').value;const obj={name:document.getElementById('conn-name').value,ip:document.getElementById('conn-ip').value,username:document.getElementById('conn-user').value,password:document.getElementById('conn-pass').value,shareName:document.getElementById('conn-share').value};try{if(editId){await api('/api/smb-connections/'+editId,{method:'PUT',body:JSON.stringify(obj)})}else{await api('/api/smb-connections',{method:'POST',body:JSON.stringify(obj)})}closeConnModal();loadConnections();toast(editId?'连接已更新':'连接已添加')}catch(e){toast('保存失败: '+e.message,false)}}

async function deleteConnection(id){if(!confirm('确定删除此连接？'))return;try{await api('/api/smb-connections/'+id,{method:'DELETE'});loadConnections();toast('已删除')}catch(e){toast('删除失败: '+e.message,false)}}

// File Browser
var browserTargetId=null;
var browserContext=null;
var browserCurrentPath='/';
var browserConnId=null;

function openBrowser(inputId,context){browserTargetId=inputId;browserContext=context;browserCurrentPath='/';browserConnId=null;if(context==='folder'||context==='video'){const dst=document.getElementById('slot-'+context+'-dst');if(dst&&dst.value==='SMB'){const connSel=document.getElementById('slot-'+context+'-conn');if(connSel&&connSel.value){const connName=connSel.value;loadConnIdForBrowser(connName)}else{toast('请先选择 SMB 连接',false);return}}}if(context==='addVideo'){const dst=document.getElementById('add-video-dst');if(dst&&dst.value==='SMB'){const connSel=document.getElementById('add-video-conn');if(connSel&&connSel.value){const connName=connSel.value;loadConnIdForBrowser(connName)}else{toast('请先选择 SMB 连接',false);return}}}document.getElementById('browser-modal').style.display='block';loadBrowserDir(browserCurrentPath)}

async function loadConnIdForBrowser(connName){try{const conns=await api('/api/smb-connections');const c=conns.find(x=>x.name===connName);if(c)browserConnId=c.id}catch(e){}}

async function loadBrowserDir(path){browserCurrentPath=path;document.getElementById('browser-path').textContent=path;document.getElementById('browser-list').innerHTML='<p class="empty-state">加载中...</p>';try{let entries;if(browserConnId){entries=await api('/api/browse/smb?connId='+encodeURIComponent(browserConnId)+'&path='+encodeURIComponent(path))}else{entries=await api('/api/browse/local?path='+encodeURIComponent(path))}const dirs=entries.filter(e=>e.isDirectory);const files=entries.filter(e=>!e.isDirectory);let html='';if(dirs.length){html+='<div style="padding:8px 12px;font-size:12px;color:#999;border-bottom:1px solid #eee">文件夹</div>';html+=dirs.map(d=>'<div style="padding:10px 12px;cursor:pointer;border-bottom:1px solid #f5f5f5" onmouseover="this.style.background=\'#f0f2f5\'" onmouseout="this.style.background=\'\'" onclick="loadBrowserDir(\''+escapeHtml(d.path)+'\')">📁 '+escapeHtml(d.name)+'</div>').join('')}if(files.length){html+='<div style="padding:8px 12px;font-size:12px;color:#999;border-bottom:1px solid #eee">文件</div>';html+=files.map(f=>'<div style="padding:10px 12px;cursor:pointer;border-bottom:1px solid #f5f5f5" onmouseover="this.style.background=\'#f0f2f5\'" onmouseout="this.style.background=\'\'" onclick="selectBrowserFile(\''+escapeHtml(f.path)+'\')">📄 '+escapeHtml(f.name)+'</div>').join('')}if(!dirs.length&&!files.length){html='<p class="empty-state">空目录</p>'}document.getElementById('browser-list').innerHTML=html}catch(e){document.getElementById('browser-list').innerHTML='<p style="color:red;padding:12px">加载失败: '+escapeHtml(e.message)+'</p>'}}

function browserGoUp(){if(browserCurrentPath==='/'||browserCurrentPath==='')return;const parts=browserCurrentPath.split('/').filter(Boolean);parts.pop();loadBrowserDir('/'+parts.join('/'))}

function selectBrowserFile(path){document.getElementById(browserTargetId).value=path;closeBrowser()}

function confirmBrowser(){document.getElementById(browserTargetId).value=browserCurrentPath;closeBrowser()}

function closeBrowser(){document.getElementById('browser-modal').style.display='none'}

// Settings
async function loadSettings(){try{const s=await api('/api/settings');document.getElementById('set-current-mode').textContent='当前模式：'+(s.appMode==='elder'?'老人模式':'标准模式');document.getElementById('set-web-config-status').textContent='Web 配置：'+(s.webConfigEnabled?'已启用（请在 TV 端关闭）':'已禁用（请在 TV 端启用）')}catch(e){toast('加载设置失败: '+e.message,false)}}

async function saveSettings(){try{await api('/api/settings',{method:'PUT',body:JSON.stringify({})});toast('设置已保存')}catch(e){toast('保存失败: '+e.message,false)}}

// Elder Config
async function loadElderConfig(){try{const c=await api('/api/elder-config');document.getElementById('ec-control-hide').value=c.controlHideSeconds;document.getElementById('ec-seek-step').value=c.seekStepSeconds;document.getElementById('ec-subtitle-size').value=c.minSubtitleFontSize;document.getElementById('ec-hide-danmaku').checked=c.hideDanmaku;document.getElementById('ec-hide-speed').checked=c.hideNetworkSpeed;document.getElementById('ec-hide-advanced').checked=c.hideAdvancedControls;document.getElementById('ec-recent-count').value=c.recentCount}catch(e){toast('加载老人模式配置失败: '+e.message,false)}}

async function saveElderConfig(){const obj={controlHideSeconds:parseInt(document.getElementById('ec-control-hide').value),seekStepSeconds:parseInt(document.getElementById('ec-seek-step').value),minSubtitleFontSize:parseInt(document.getElementById('ec-subtitle-size').value),hideDanmaku:document.getElementById('ec-hide-danmaku').checked,hideNetworkSpeed:document.getElementById('ec-hide-speed').checked,hideAdvancedControls:document.getElementById('ec-hide-advanced').checked,recentCount:parseInt(document.getElementById('ec-recent-count').value)};try{await api('/api/elder-config',{method:'PUT',body:JSON.stringify(obj)});toast('老人模式配置已保存')}catch(e){toast('保存失败: '+e.message,false)}}

// Init
loadDashboard()
</script>
</body>
</html>
        """.trimIndent()
    }
}

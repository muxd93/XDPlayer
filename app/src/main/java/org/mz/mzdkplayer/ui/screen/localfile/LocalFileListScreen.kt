package org.mz.mzdkplayer.ui.screen.localfile

import NoSearchResult
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.model.AudioItem
import org.mz.mzdkplayer.data.model.LocalFileLoadStatus
import org.mz.mzdkplayer.tool.Tools
import org.mz.mzdkplayer.tool.Tools.VideoBigIcon
import org.mz.mzdkplayer.ui.screen.common.FileEmptyScreen
import org.mz.mzdkplayer.ui.screen.common.FileIcon
import org.mz.mzdkplayer.ui.screen.common.FileName
import org.mz.mzdkplayer.ui.screen.common.FileSize
import org.mz.mzdkplayer.ui.screen.common.LoadingScreen
import org.mz.mzdkplayer.ui.screen.common.MediaFocusedFileName
import org.mz.mzdkplayer.ui.screen.common.VAErrorScreen


import org.mz.mzdkplayer.ui.theme.myTTFColor
import org.mz.mzdkplayer.ui.theme.MyFileListItemColor

import org.mz.mzdkplayer.ui.screen.common.TvTextField
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

@OptIn(UnstableApi::class)
@Composable
fun LocalFileListScreen(path: String?, navController: NavHostController) {
    val context = LocalContext.current
    val files = remember { mutableStateListOf<File>() }
    var status by remember { mutableStateOf<LocalFileLoadStatus>(LocalFileLoadStatus.LoadingFile) }
    var focusedFileName by remember { mutableStateOf<String?>(null) }
    var focusedIsDir by remember { mutableStateOf(false) }
    var seaText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val filteredFiles = remember(files, seaText) {
        if (seaText.isBlank()) {
            files
        } else {
            files.filter { file ->
                file.name.contains(seaText, ignoreCase = true)
            }
        }
    }
    LaunchedEffect(path) {
        status = LocalFileLoadStatus.LoadingFile
        files.clear()
        delay(300)

        val decodedPath = path?.let { URLDecoder.decode(it, "UTF-8") } ?: ""

        if (decodedPath.isEmpty()) {
            status = LocalFileLoadStatus.Error("路径为空")
            return@LaunchedEffect
        }

        try {
            // 1. 尝试 MediaStore 查询
            val mediaStoreFiles = queryMediaStore(context, decodedPath)

            if (mediaStoreFiles.isNotEmpty()) {
                files.addAll(mediaStoreFiles)
                status = LocalFileLoadStatus.FilesLoaded
                return@LaunchedEffect
            }

            // 2. 降级到文件系统 API
            val dir = File(decodedPath)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.let {
                    files.addAll(it.toList())
                    status = LocalFileLoadStatus.FilesLoaded
                } ?: run {
                    status = LocalFileLoadStatus.Error("无法读取目录内容")
                }
            } else {
                status = LocalFileLoadStatus.Error("目录不存在或不可访问")
            }
        } catch (e: Exception) {
            Log.e("LocalFileListScreen", "加载文件失败", e)
            status = LocalFileLoadStatus.Error(e.message ?: "未知错误")
        }
    }

    // 根据状态显示不同 UI
    when (status) {
        is LocalFileLoadStatus.LoadingFile -> {
            LoadingScreen(
                "正在加载本地文件", Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        is LocalFileLoadStatus.Error -> {
            val error = status as LocalFileLoadStatus.Error
            VAErrorScreen("加载失败: ${error.message}")
        }

        LocalFileLoadStatus.FilesLoaded -> {
            if (files.isEmpty()) {
                FileEmptyScreen("此目录为空")
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    LazyColumn(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxHeight()
                            .weight(0.7f)
                    ) {
                        when {
                            // 搜索无结果
                            filteredFiles.isEmpty() && seaText.isNotBlank() -> {
                                item {
                                    NoSearchResult(text = "没有匹配 \"$seaText\" 的文件")
                                }
                            }
                            // 目录本身为空（未搜索时）
                            else -> {
                                items(filteredFiles) { file ->
                                    val isDirectory = file.isDirectory
                                    val fileName = file.name
                                    val fullPath = file.absolutePath // 获取文件的绝对路径
                                    ListItem(
                                        selected = false,
                                        onClick = {
                                             coroutineScope.launch{
                                                // --- 统一编码和错误处理 ---

                                                // 1. 尝试编码完整路径 (作为 URI 使用)
                                                // 本地文件 URI 格式通常是 file:///full/path/to/file
                                                val fullFileUri = "file://$fullPath"

                                                val encodedFileUri = try {
                                                    URLEncoder.encode(fullFileUri, "UTF-8")
                                                } catch (e: Exception) {
                                                    Log.e("LocalFileListScreen", "文件URI编码失败: $e")
                                                    Toast.makeText(context, "文件URI编码失败", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }

                                                // 2. 尝试编码文件名
                                                val encodedFileName = try {
                                                    URLEncoder.encode(fileName, "UTF-8")
                                                } catch (e: Exception) {
                                                    Log.e("LocalFileListScreen", "文件名编码失败: $e")
                                                    Toast.makeText(context, "文件名编码失败", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }

                                                val fileExtension = Tools.extractFileExtension(fileName)

                                                when {
                                                    isDirectory -> {
                                                        // --- 目录点击处理 ---
                                                        // 对新的路径（完整路径）进行编码，用于导航
                                                        val encodedNewPath = try {
                                                            URLEncoder.encode(fullPath, "UTF-8")
                                                        } catch (e: Exception) {
                                                            Log.e("LocalFileListScreen", "目录路径编码失败: $e")
                                                            Toast.makeText(context, "目录路径编码失败", Toast.LENGTH_SHORT).show()
                                                            return@launch
                                                        }

                                                        Log.d("LocalFileListScreen", "Navigating to subdirectory: $fullPath")
                                                        // 导航到子目录
                                                        navController.navigate("LocalFileListScreen/$encodedNewPath")
                                                    }

                                                    Tools.containsVideoFormat(fileExtension) -> {
                                                        // 导航到视频播放器
                                                        // LocalFileListScreen 没有 connectionName 参数，传递空字符串或占位符
                                                        navController.navigate(
                                                            "VideoPlayer/$encodedFileUri/LOCAL/$encodedFileName/本地文件" // connectionName 留空
                                                        )
                                                    }

                                                    Tools.containsAudioFormat(fileExtension) -> {
                                                        // 构建音频文件列表（只包含音频文件）
                                                        // 注意：这里使用 fileList 而不是 filteredFiles，以获取完整列表
                                                        val audioFiles = files.filter { localFile ->
                                                            !localFile.isDirectory && Tools.containsAudioFormat(Tools.extractFileExtension(localFile.name))
                                                        }

                                                        // 快速查找索引
                                                        val currentAudioIndex = audioFiles.withIndex().firstOrNull { it.value.absolutePath == fullPath }?.index ?: -1

                                                        if (currentAudioIndex == -1) {
                                                            Log.e("LocalFileListScreen", "未找到文件在音频列表中: $fileName")
                                                            return@launch
                                                        }

                                                        // 构建播放列表
                                                        val audioItems = audioFiles.map { localFile ->
                                                            AudioItem(
                                                                uri = "file://${localFile.absolutePath}",
                                                                fileName = localFile.name,
                                                                dataSourceType = "LOCAL"
                                                            )
                                                        }

                                                        // 设置数据
                                                        MzDkPlayerApplication.clearStringList("audio_playlist")
                                                        MzDkPlayerApplication.setStringList("audio_playlist", audioItems)

                                                        // 导航到音频播放器
                                                        navController.navigate(
                                                            "AudioPlayer/$encodedFileUri/LOCAL/$encodedFileName/本地文件/$currentAudioIndex" // connectionName 留空
                                                        )
                                                    }

                                                    Tools.containsImageFileExtension(fileExtension) -> {
                                                        navController.navigate(
                                                            "PicViewer/$encodedFileUri/LOCAL/本地文件/$encodedFileName" // connectionName 留空
                                                        )
                                                    }

                                                    else -> {
                                                        Toast.makeText(
                                                            context,
                                                            "不支持的格式: $fileExtension",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }



                                        },
                                        colors = MyFileListItemColor(),
                                        modifier = Modifier
                                            .padding(end = 10.dp)
                                            .height(40.dp)
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    focusedFileName = file.name
                                                    focusedIsDir = file.isDirectory
                                                }
                                            },
                                        scale = ListItemDefaults.scale(
                                            scale = 1.0f,
                                            focusedScale = 1.02f
                                        ),
                                        leadingContent = {
                                            val fileExtension = Tools.extractFileExtension(file.name)
                                            FileIcon(isDirectory,fileExtension)
                                        },
                                        headlineContent = {
                                            FileName(fileName)
                                        },
                                        trailingContent = {
                                            // 只有文件才显示大小，目录可以留空或显示项数
                                            FileSize(file.isDirectory,file.length())
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.3f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        TvTextField(
                            value = seaText,
                            onValueChange = { seaText = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = myTTFColor(),
                            placeholder = "请输入文件名",
                            textStyle = TextStyle(color = Color.White),
                        )
                        VideoBigIcon(
                            focusedIsDir,
                            focusedFileName,
                            modifier = Modifier
                                .height(200.dp)
                                .fillMaxWidth()
                        )
                        MediaFocusedFileName(focusedFileName)
                    }
                }
            }
        }
    }
}

private fun queryMediaStore(context: Context, path: String): List<File> {
    val normalizedPath = if (path.endsWith("/")) path else "$path/"
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Files.getContentUri("external")
    }
    val selection = """
        ${MediaStore.Files.FileColumns.DATA} LIKE ? 
        AND ${MediaStore.Files.FileColumns.DATA} NOT LIKE ?
    """.trimIndent()
    val selectionArgs = arrayOf("$normalizedPath%", "$normalizedPath%/%")

    return context.contentResolver.query(
        collection,
        arrayOf(MediaStore.Files.FileColumns.DATA),
        selection,
        selectionArgs,
        null
    )?.use { cursor ->
        generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }
            .mapNotNull { it -> File(it).takeIf { it.exists() } }
            .toList()
    } ?: emptyList()
}
//navOptions {
//                        popUpTo("FilePage/${URLEncoder.encode(pathUri, "UTF-8")}") {
//                            inclusive = true
//                            saveState = false
//                        }
//                        launchSingleTop = true
//                        restoreState = false
//                    }
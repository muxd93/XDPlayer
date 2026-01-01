// File: HTTPLinkFileListScreen.kt

package org.mz.mzdkplayer.ui.screen.httplink

import NoSearchResult
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.model.AudioItem
import org.mz.mzdkplayer.data.model.FileConnectionStatus
import org.mz.mzdkplayer.tool.Tools
import org.mz.mzdkplayer.tool.Tools.VideoBigIcon
import org.mz.mzdkplayer.ui.screen.common.FileEmptyScreen
import org.mz.mzdkplayer.ui.screen.common.FileIcon
import org.mz.mzdkplayer.ui.screen.common.FileSize
import org.mz.mzdkplayer.ui.screen.common.LoadingScreen
import org.mz.mzdkplayer.ui.screen.common.MediaFocusedFileName
import org.mz.mzdkplayer.ui.screen.common.VAErrorScreen
import org.mz.mzdkplayer.ui.screen.vm.HTTPLinkConViewModel

import org.mz.mzdkplayer.ui.theme.myTTFColor
import org.mz.mzdkplayer.ui.theme.MyFileListItemColor

import org.mz.mzdkplayer.ui.screen.common.TvTextField
import java.net.URLEncoder


/**
 * HTTP 链接文件列表屏幕
 *
 * @param path HTTP 服务器地址和共享路径完整路径 w(e.g., "http://192.168.1.100:8080/nas/movies/")
 * @param navController 导航控制器
 */
@OptIn(UnstableApi::class)
@Composable
fun HTTPLinkFileListScreen(
    path: String?,
    navController: NavHostController,
    connectionName: String = ""
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // 使用 ViewModel
    val viewModel: HTTPLinkConViewModel = viewModel()

    // 收集 ViewModel 中的状态
    val fileList by viewModel.fileList.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()


    var focusedFileName by remember { mutableStateOf<String?>(null) }
    var focusedIsDir by remember { mutableStateOf(false) }
    var focusedMediaUri by remember { mutableStateOf("") }
    // 当传入的 serverAddressAndShare, effectiveSubPath 参数变化时，或者首次进入时，尝试加载文件列表
    var seaText by remember { mutableStateOf("") }
    //  新增：过滤后的文件列表
    val filteredFiles by remember(fileList, seaText) {
        derivedStateOf {
            if (seaText.isBlank()) {
                fileList
            } else {
                fileList.filter { file ->
                    file.name.contains(seaText, ignoreCase = true)
                }
            }
        }
    }
    // 标准化 path：确保非空时以 "/" 结尾
    val normalizedPath = path?.let { p ->
        if (p.endsWith("/")) p else "$p/"
    }

    // 如果 normalizedPath 为 null，可以提前返回或显示错误
    if (normalizedPath == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("无效的路径", color = Color.Red)
        }
        return
    }

    // 专门监听连接状态变化，连接成功后检查是否需要导航到初始子路径
    LaunchedEffect(path, connectionStatus) {
        when (connectionStatus) {
            is FileConnectionStatus.Connected -> {
                // 连接成功后，检查当前路径是否与目标路径一致

                viewModel.listFiles(normalizedPath)

            }

            is FileConnectionStatus.Disconnected -> {
                delay(300)
                viewModel.connectToHTTPLink(normalizedPath)

            }

            is FileConnectionStatus.Error -> {
                // 如果连接或加载出错，不再自动重试，等待用户操作或导航离开
                Log.e(
                    "HTTPLinkFileListScreen",
                    "Connection or listing failed: ${(connectionStatus as FileConnectionStatus.Error).message}"
                )
            }

            else -> {
                // 其他状态，如 Connecting 或 Disconnected，不做特殊处理
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 可选：在离开屏幕时断开连接或清理资源
            // ViewModel 的 onCleared 会处理清理，通常不需要在此处手动断开
            Log.d("HTTPLinkFileListScreen", "Screen disposed")
        }
    }

    // 根据连接状态渲染 UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        when (connectionStatus) {


            is FileConnectionStatus.Error -> {
                // 显示错误信息
                val errorMessage = (connectionStatus as FileConnectionStatus.Error).message
                VAErrorScreen(
                    "加载失败: $errorMessage",
                )
            }

            is FileConnectionStatus.FilesLoaded -> {
                if (fileList.isEmpty()) {
                    FileEmptyScreen("此目录为空")
                } else {
                    // 已连接，显示文件列表
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        LazyColumn(
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxHeight()
                                .weight(0.7f)
                        ) {

                            // Log.d("HTTPLinkFileListScreen", "Displaying fileList: $fileList")
                            when {
                                // 搜索无结果
                                filteredFiles.isEmpty() && seaText.isNotBlank() -> {
                                    item {
                                        NoSearchResult(text = "没有匹配 \"$seaText\" 的文件")
                                    }
                                }

                                // 目录本身为空（未搜索时）

                                else -> {
                                    items(filteredFiles) { resource ->
                                        // 这里假设 resource 有 isDirectory: Boolean 和 name: String, path: String 属性
                                        val isDirectory = resource.isDirectory
                                        val resourceName = resource.name // 这里应该已经是完整的文件/目录名
                                        // val resourcePath = resource.path // 相对于 baseUrl 的路径

                                        ListItem(
                                            selected = false,
                                            onClick = {
                                                coroutineScope.launch {
                                                    // --- 提取公共变量/准备工作 ---
                                                    val fileExtension =
                                                        Tools.extractFileExtension(resource.name)

                                                    // 目录和文件需要不同的处理方式
                                                    if (isDirectory) {
                                                        // 导航到子目录
                                                        // normalizedPath 已带 /，所以直接拼接 resourceName 即可
                                                        val newFullPath =
                                                            "${normalizedPath}${resourceName}"
                                                        val encodedNewSubPath = try {
                                                            URLEncoder.encode(newFullPath, "UTF-8")
                                                        } catch (e: Exception) {
                                                            Log.e("HTTPLinkFileListScreen", "目录路径编码失败: $e")
                                                            Toast.makeText(context, "目录路径编码失败", Toast.LENGTH_SHORT).show()
                                                            return@launch
                                                        }

                                                        // 注意：导航路由参数顺序是 connectionName 在前，encodedNewSubPath 在后
                                                        navController.navigate("HTTPLinkFileListScreen/$connectionName/$encodedNewSubPath")

                                                    } else {
                                                        // --- 文件点击处理：提取公共编码变量 ---
                                                        val fullFileUrl =
                                                            viewModel.getResourceFullUrl(resourceName)

                                                        Log.d("HTTPLinkFileListScreen", "Full file URL before encoding: $fullFileUrl")

                                                        val encodedFileUrl = try {
                                                            URLEncoder.encode(fullFileUrl, "UTF-8")
                                                        } catch (e: Exception) {
                                                            Log.e("HTTPLinkFileListScreen", "文件URL编码失败: $e")
                                                            Toast.makeText(context, "文件路径编码失败", Toast.LENGTH_SHORT).show()
                                                            return@launch
                                                        }

                                                        val encodedResourceName = try {
                                                            URLEncoder.encode(resource.name, "UTF-8")
                                                        } catch (e: Exception) {
                                                            Log.e("HTTPLinkFileListScreen", "文件名编码失败: $e")
                                                            Toast.makeText(context, "文件名编码失败", Toast.LENGTH_SHORT).show()
                                                            return@launch
                                                        }

                                                        val encodedConnectionName = try {
                                                            URLEncoder.encode(connectionName, "UTF-8")
                                                        } catch (e: Exception) {
                                                            // 几乎不会失败，但最好处理一下
                                                            Log.e("HTTPLinkFileListScreen", "连接名编码失败: $e")
                                                            Toast.makeText(context, "连接名编码失败", Toast.LENGTH_SHORT).show()
                                                            return@launch
                                                        }

                                                        when {
                                                            Tools.containsVideoFormat(fileExtension) -> {
                                                                // 导航到视频播放器
                                                                navController.navigate(
                                                                    "VideoPlayer/$encodedFileUrl/HTTP/$encodedResourceName/$encodedConnectionName"
                                                                )
                                                            }

                                                            Tools.containsAudioFormat(fileExtension) -> {
                                                                //  构建音频文件列表（只包含文件）
                                                                val audioFiles =
                                                                    fileList.filter { httpFile ->
                                                                        !httpFile.isDirectory && Tools.containsAudioFormat(
                                                                            Tools.extractFileExtension(httpFile.name)
                                                                        )
                                                                    }

                                                                // 快速查找索引（O(N) 一次查找）
                                                                val currentAudioIndex =
                                                                    audioFiles.withIndex()
                                                                        .firstOrNull { it.value.name == resource.name }
                                                                        ?.index ?: -1

                                                                if (currentAudioIndex == -1) {
                                                                    Log.e("HTTPFileListScreen", "未找到文件在音频列表中: ${resource.name}")
                                                                    return@launch
                                                                }

                                                                //  构建播放列表
                                                                val audioItems =
                                                                    audioFiles.map { httpFile ->
                                                                        AudioItem(
                                                                            uri = viewModel.getResourceFullUrl(httpFile.name),
                                                                            fileName = httpFile.name,
                                                                            dataSourceType = "HTTP"
                                                                        )
                                                                    }

                                                                // 设置数据到全局 Application
                                                                MzDkPlayerApplication.clearStringList("audio_playlist")
                                                                MzDkPlayerApplication.setStringList("audio_playlist", audioItems)

                                                                // 导航到音频播放器，带上播放列表索引
                                                                navController.navigate(
                                                                    "AudioPlayer/$encodedFileUrl/HTTP/$encodedResourceName/$encodedConnectionName/$currentAudioIndex"
                                                                )
                                                            }

                                                            Tools.containsImageFileExtension(fileExtension) -> {
                                                                navController.navigate(
                                                                    "PicViewer/$encodedFileUrl/HTTP/$encodedResourceName/$encodedConnectionName"
                                                                )
                                                            }

                                                            else -> {
                                                                Toast.makeText(
                                                                    context,
                                                                    "不支持的文件格式: $fileExtension",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
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
                                                        focusedFileName = resource.name;
                                                        focusedIsDir = isDirectory
                                                        focusedMediaUri =
                                                            viewModel.getResourceFullUrl(
                                                                resourceName
                                                            )
                                                    }
                                                },
                                            scale = ListItemDefaults.scale(
                                                scale = 1.0f,
                                                focusedScale = 1.01f
                                            ),
                                            leadingContent = {
                                                val fileExtension = Tools.extractFileExtension(resourceName)
                                                FileIcon(isDirectory,fileExtension)
                                            },
                                            headlineContent = {
                                                // 显示完整的文件名
                                                Text(
                                                    resourceName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 10.sp
                                                )
                                            },
                                            trailingContent = {
                                                // 只有文件才显示大小，目录可以留空或显示项数
                                                FileSize(isDirectory,resource.fileSize)
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

            else -> {
                LoadingScreen(
                    "正在加载HTTP文件", Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }

        }
    }
}




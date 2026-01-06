// File: HTTPLinkFileListScreen.kt

package org.mz.mzdkplayer.ui.screen.httplink

import NoSearchResult
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.model.AudioItem
import org.mz.mzdkplayer.data.model.FileConnectionStatus
import org.mz.mzdkplayer.data.repository.Resource
import org.mz.mzdkplayer.di.RepositoryProvider
import org.mz.mzdkplayer.tool.MediaInfoExtractorFormFileName
import org.mz.mzdkplayer.tool.Tools
import org.mz.mzdkplayer.tool.Tools.VideoBigIcon
import org.mz.mzdkplayer.tool.viewModelWithFactory
import org.mz.mzdkplayer.ui.screen.common.CirCleIconButton
import org.mz.mzdkplayer.ui.screen.common.FileEmptyScreen
import org.mz.mzdkplayer.ui.screen.common.FileIcon
import org.mz.mzdkplayer.ui.screen.common.FileSize
import org.mz.mzdkplayer.ui.screen.common.LoadingScreen
import org.mz.mzdkplayer.ui.screen.common.MediaFocusedFileName
import org.mz.mzdkplayer.ui.screen.common.MediaInfoLoading
import org.mz.mzdkplayer.ui.screen.common.MediaPreviewSection
import org.mz.mzdkplayer.ui.screen.common.MediaReleaseDate
import org.mz.mzdkplayer.ui.screen.common.MediaTitle
import org.mz.mzdkplayer.ui.screen.common.VAErrorScreen
import org.mz.mzdkplayer.ui.screen.vm.HTTPLinkConViewModel

import org.mz.mzdkplayer.ui.theme.myTTFColor
import org.mz.mzdkplayer.ui.theme.MyFileListItemColor

import org.mz.mzdkplayer.ui.screen.common.TvTextField
import org.mz.mzdkplayer.ui.screen.vm.AudioViewModel
import org.mz.mzdkplayer.ui.screen.vm.MovieViewModel
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel
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
    connectionName: String = "",
    settingsViewModel: SettingsViewModel = viewModel()
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
    val movieViewModel: MovieViewModel = viewModelWithFactory {
        RepositoryProvider.createMovieViewModel()
    }// 新增：获取MovieViewModel
    // 新增：电影信息状态
    val focusedMovie by movieViewModel.focusedMovie.collectAsState()
    var mediaId by remember { mutableIntStateOf(-1) }
    val settingsState by settingsViewModel.uiState.collectAsState()
    var focusedIsVideo by remember { mutableStateOf(false) }
    val isScanning by movieViewModel.isScanning.collectAsState()
    val currentScanIndex by movieViewModel.currentScanIndex.collectAsState() // 新增：引入当前进度
    val totalScanCount by movieViewModel.totalScanCount.collectAsState() // 新增：引入总数
    // 获取 AudioViewModel
    val audioViewModel: AudioViewModel = viewModelWithFactory {
        RepositoryProvider.createAudioViewModel() // 不需要 context 了
    }
    val isAudioScanning by audioViewModel.isScanning.collectAsState()
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
// 处理焦点变化和媒体播放
    LaunchedEffect(focusedFileName, focusedIsDir, focusedIsVideo, settingsState.http) {
        // 1. 基础校验：如果是目录、无文件名、或者不是视频，直接清空信息
        if (focusedFileName == null || focusedIsDir || !focusedIsVideo) {
            movieViewModel.clearFocusedMovie()
            return@LaunchedEffect
        }
        // 2. 根据设置决定策略
        // settingsState.http 为 false 代表 "禁止自动刮削/仅本地数据" (防止重复入库)
        if (settingsState.http) {
            // === 模式 A：自动刮削 (主数据源) ===
            // 场景：这是用户的主要观看路径，允许自动联网获取信息。
            Log.d("HTTPFileListScreen", "自动模式: 触发搜索/刮削: $focusedFileName")
            movieViewModel.searchFocusedMovie(
                movieName = focusedFileName,
                isDirectory = false,
                videoUri = focusedMediaUri,
                dataSourceType = "HTTP",
                connectionName = connectionName
            )

        } else {
            // === 模式 B：仅查询数据库 (防重复) ===
            // 场景：用户不希望此协议自动产生新数据，但如果之前"手动批量扫描"过，这里应该显示出来。
            Log.d("HTTPFileListScreen", "禁止自动刮削模式: 仅查询数据库: $focusedFileName")
            movieViewModel.getFocusedInfo(
                movieName = focusedFileName,
                isDirectory = false,
                videoUri = focusedMediaUri,
                dataSourceType = "HTTP",
                connectionName = connectionName
            )
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
                                        val fullFileUrl = viewModel.getResourceFullUrl(resourceName)

                                        Log.d(
                                            "HTTPLinkFileListScreen",
                                            "Full file URL before encoding: $fullFileUrl"
                                        )

                                        val encodedFileUrl = try {
                                            URLEncoder.encode(fullFileUrl, "UTF-8")
                                        } catch (e: Exception) {
                                            Log.e("HTTPLinkFileListScreen", "文件URL编码失败: $e")
                                            Toast.makeText(
                                                context,
                                                "文件路径编码失败",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@items
                                        }

                                        val encodedResourceName = try {
                                            URLEncoder.encode(resource.name, "UTF-8")
                                        } catch (e: Exception) {
                                            Log.e("HTTPLinkFileListScreen", "文件名编码失败: $e")
                                            Toast.makeText(
                                                context,
                                                "文件名编码失败",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@items
                                        }

                                        val encodedConnectionName = try {
                                            URLEncoder.encode(connectionName, "UTF-8")
                                        } catch (e: Exception) {
                                            // 几乎不会失败，但最好处理一下
                                            Log.e("HTTPLinkFileListScreen", "连接名编码失败: $e")
                                            Toast.makeText(
                                                context,
                                                "连接名编码失败",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@items
                                        }
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
                                                            Log.e(
                                                                "HTTPLinkFileListScreen",
                                                                "目录路径编码失败: $e"
                                                            )
                                                            Toast.makeText(
                                                                context,
                                                                "目录路径编码失败",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            return@launch
                                                        }

                                                        // 注意：导航路由参数顺序是 connectionName 在前，encodedNewSubPath 在后
                                                        navController.navigate("HTTPLinkFileListScreen/$connectionName/$encodedNewSubPath")

                                                    } else {
                                                        // --- 文件点击处理：提取公共编码变量 ---


                                                        when {
                                                            Tools.containsVideoFormat(fileExtension) -> {
                                                                // 检查是否有媒体信息（mediaId > 0, 且是焦点文件, 且未隐藏详情）
                                                                if (mediaId > 0 && focusedFileName == resourceName && !settingsState.hideDetails) {
                                                                    val mediaInfoFN =
                                                                        MediaInfoExtractorFormFileName.extract(
                                                                            resourceName
                                                                        )
                                                                    val route =
                                                                        if (mediaInfoFN.mediaType == "movie") {
                                                                            "MovieDetails/$encodedFileUrl/HTTP/$encodedResourceName/${encodedConnectionName}/$mediaId"
                                                                        } else {
                                                                            // 注意：这里假设 season/episode 可以安全地转换为 Int
                                                                            "TVSeriesDetails/$encodedFileUrl/HTTP/$encodedResourceName/${encodedConnectionName}/$mediaId/${mediaInfoFN.season.toInt()}/${mediaInfoFN.episode.toInt()}"
                                                                        }
                                                                    navController.navigate(route)
                                                                } else {
                                                                    // 导航到视频播放器
                                                                    navController.navigate(
                                                                        "VideoPlayer/$encodedFileUrl/HTTP/$encodedResourceName/$encodedConnectionName"
                                                                    )
                                                                }
                                                            }

                                                            Tools.containsAudioFormat(fileExtension) -> {
                                                                //  构建音频文件列表（只包含文件）
                                                                val audioFiles =
                                                                    fileList.filter { httpFile ->
                                                                        !httpFile.isDirectory && Tools.containsAudioFormat(
                                                                            Tools.extractFileExtension(
                                                                                httpFile.name
                                                                            )
                                                                        )
                                                                    }

                                                                // 快速查找索引（O(N) 一次查找）
                                                                val currentAudioIndex =
                                                                    audioFiles.withIndex()
                                                                        .firstOrNull { it.value.name == resource.name }
                                                                        ?.index ?: -1

                                                                if (currentAudioIndex == -1) {
                                                                    Log.e(
                                                                        "HTTPFileListScreen",
                                                                        "未找到文件在音频列表中: ${resource.name}"
                                                                    )
                                                                    return@launch
                                                                }

                                                                //  构建播放列表
                                                                val audioItems =
                                                                    audioFiles.map { httpFile ->
                                                                        AudioItem(
                                                                            uri = viewModel.getResourceFullUrl(
                                                                                httpFile.name
                                                                            ),
                                                                            fileName = httpFile.name,
                                                                            dataSourceType = "HTTP"
                                                                        )
                                                                    }

                                                                // 设置数据到全局 Application
                                                                MzDkPlayerApplication.clearStringList(
                                                                    "audio_playlist"
                                                                )
                                                                MzDkPlayerApplication.setStringList(
                                                                    "audio_playlist",
                                                                    audioItems
                                                                )

                                                                // 导航到音频播放器，带上播放列表索引
                                                                navController.navigate(
                                                                    "AudioPlayer/$encodedFileUrl/HTTP/$encodedResourceName/$encodedConnectionName/$currentAudioIndex"
                                                                )
                                                            }

                                                            Tools.containsImageFileExtension(
                                                                fileExtension
                                                            ) -> {
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
                                                        mediaId = -1
                                                        focusedIsVideo =
                                                            Tools.containsVideoFormat(
                                                                Tools.extractFileExtension(
                                                                    resourceName
                                                                )
                                                            )
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
                                                val fileExtension =
                                                    Tools.extractFileExtension(resourceName)
                                                FileIcon(isDirectory, fileExtension)
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
                                                FileSize(isDirectory, resource.fileSize)
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
                            // 2. 中间的海报和文字区域（包裹在一个 Column 里）
                            MediaPreviewSection(
                                focusedMovie = focusedMovie,
                                focusedFileName = focusedFileName,
                                focusedIsDir = focusedIsDir,
                                modifier = Modifier.weight(1f),
                                onMediaIdResolved = { id ->
                                    mediaId = id // 更新父组件持有的状态，供 ListItem 点击逻辑使用
                                }
                            )
                            // 3. 底部的进度和按钮区域
                            // 不再嵌套在上面的 Column 里，而是直接放在最外层 Column 的底部
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp), // 距离底部边缘一点间距
                                horizontalAlignment = Alignment.CenterHorizontally
                            )
                            {
                                // 进度显示区：固定高度 30.dp 左右，避免布局跳动

                                // 进度显示区：固定高度 30.dp 左右，避免布局跳动
                                Box(
                                    modifier = Modifier.height(30.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val progressText = when {
                                        isScanning -> if (totalScanCount > 0) "正在获取视频信息 $currentScanIndex/$totalScanCount" else "正在准备视频扫描..."
                                        isAudioScanning -> "正在解析音乐文件名..."
                                        else -> null // 返回 null 不显示
                                    }
                                    progressText?.let {
                                        Text(text = it, color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
// 按钮行
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        16.dp,
                                        Alignment.CenterHorizontally
                                    ),
                                )
                                {
                                    // 按钮行
                                    // --- 视频扫描按钮 ---
                                    CirCleIconButton(
                                        icon = painterResource(R.drawable.videoadd24dp),
                                        // 动态显示 tooltip 内容
                                        tooltip = if (isScanning && totalScanCount > 0)
                                            "正在获取信息 $currentScanIndex/$totalScanCount"
                                        else "批量添加到视频库",
                                        enable = settingsState.http,
                                        onClick = {
                                            if (!settingsState.http) {
                                                Toast.makeText(
                                                    context,
                                                    "当前数据源未开启刮削功能 请在设置中开启",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                // 1. 过滤出所有的视频文件 (不递归，只取当前层级)
                                                val videoFilesToScan = fileList.filter { file ->
                                                    !file.isDirectory &&
                                                            Tools.containsVideoFormat(
                                                                Tools.extractFileExtension(
                                                                    file.name
                                                                )
                                                            )
                                                }

                                                if (videoFilesToScan.isEmpty()) {
                                                    Toast.makeText(
                                                        context,
                                                        "当前目录没有视频文件",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    return@CirCleIconButton
                                                }

                                                // 2. 构建数据列表 Pair(fileName, fullUri)
                                                // 注意：URI 的构建规则必须和 LazyColumn 里点击时的规则完全一致
                                                val scanList = videoFilesToScan.map { file ->
                                                    file.name to viewModel.getResourceFullUrl(file.name)
                                                }

                                                // 3. 调用 ViewModel 开始后台任务
                                                Toast.makeText(
                                                    context,
                                                    "开始后台获取信息，请稍候...",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                movieViewModel.batchScrapeVideoInfo(
                                                    videoList = scanList,
                                                    dataSourceType = "HTTP",
                                                    connectionName = connectionName
                                                )
                                            }
                                        }
                                    )
                                    // --- 音乐扫描按钮 ---
                                    CirCleIconButton(
                                        icon = painterResource(R.drawable.musicnoteadd_24dp),
                                        tooltip = if (isAudioScanning) "正在解析文件名..." else "批量添加到音乐库",
                                        enable = settingsState.http,
                                        onClick = {
                                            if (!settingsState.http) {
                                                Toast.makeText(
                                                    context,
                                                    "当前数据源未开启刮削功能 请在设置中开启",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                // 1. 过滤音频文件
                                                val audioFiles = fileList.filter {
                                                    !it.isDirectory && Tools.containsAudioFormat(
                                                        Tools.extractFileExtension(
                                                            it.name
                                                        )
                                                    )
                                                }

                                                if (audioFiles.isEmpty()) {
                                                    Toast.makeText(
                                                        context,
                                                        "没有发现音频文件",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    return@CirCleIconButton
                                                }

                                                // 2. 只有文件名和URI是必须的
                                                val list = audioFiles.map {
                                                    it.name to viewModel.getResourceFullUrl(it.name)
                                                }

                                                // 3. 直接调用，瞬间完成
                                                audioViewModel.batchScrapeAudioInfo(
                                                    audioList = list,
                                                    dataSourceType = "HTTP",
                                                    connectionName = connectionName
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "已在后台添加 ${list.size} 首音乐",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    )
                                }

                            }
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




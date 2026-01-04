

package org.mz.mzdkplayer.ui.screen.webdavfile

import NoSearchResult
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import kotlinx.coroutines.launch
import org.mz.mzdkplayer.data.model.WebDavConnection
import org.mz.mzdkplayer.tool.Tools
import org.mz.mzdkplayer.ui.screen.vm.WebDavConViewModel
import java.net.URLEncoder
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.model.AudioItem
import org.mz.mzdkplayer.data.model.FileConnectionStatus
import org.mz.mzdkplayer.data.repository.Resource
import org.mz.mzdkplayer.di.RepositoryProvider
import org.mz.mzdkplayer.tool.MediaInfoExtractorFormFileName
import org.mz.mzdkplayer.tool.Tools.VideoBigIcon
import org.mz.mzdkplayer.tool.viewModelWithFactory
import org.mz.mzdkplayer.ui.screen.common.CirCleIconButton
import org.mz.mzdkplayer.ui.screen.common.FileEmptyScreen
import org.mz.mzdkplayer.ui.screen.common.FileIcon
import org.mz.mzdkplayer.ui.screen.common.FileName
import org.mz.mzdkplayer.ui.screen.common.FileSize

import org.mz.mzdkplayer.ui.screen.common.LoadingScreen
import org.mz.mzdkplayer.ui.screen.common.MediaFocusedFileName
import org.mz.mzdkplayer.ui.screen.common.MediaInfoLoading
import org.mz.mzdkplayer.ui.screen.common.MediaReleaseDate
import org.mz.mzdkplayer.ui.screen.common.MediaTitle
import org.mz.mzdkplayer.ui.screen.common.VAErrorScreen
import org.mz.mzdkplayer.ui.theme.myTTFColor
import org.mz.mzdkplayer.ui.theme.MyFileListItemColor

import org.mz.mzdkplayer.ui.screen.common.TvTextField
import org.mz.mzdkplayer.ui.screen.vm.AudioViewModel
import org.mz.mzdkplayer.ui.screen.vm.MovieViewModel
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel

@OptIn(UnstableApi::class)
@Composable
fun WebDavFileListScreen(
    // path 现在是完整的 WebDAV URL 路径
    path: String?, // e.g., "https://192.168.1.4:5006/folder1/subfolder"
    navController: NavHostController,
    webDavConnection: WebDavConnection,
    settingsViewModel: SettingsViewModel =viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: WebDavConViewModel = viewModel()

    // 收集 ViewModel 中的状态
    val fileList by viewModel.fileList.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    var focusedFileName by remember { mutableStateOf<String?>(null) }
    var focusedIsDir by remember { mutableStateOf(false) }
    var focusedMediaUri by remember { mutableStateOf("") }
    var focusedIsVideo by remember { mutableStateOf(false) }
    val movieViewModel: MovieViewModel = viewModelWithFactory {
        RepositoryProvider.createMovieViewModel()
    }// 新增：获取MovieViewModel
    // 新增：电影信息状态
    val focusedMovie by movieViewModel.focusedMovie.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    var mediaId by remember { mutableIntStateOf(-1) }
// 过滤后的文件列表
    var seaText by remember { mutableStateOf("") }

    // ✅ 新增：过滤后的文件列表
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
    val isScanning by movieViewModel.isScanning.collectAsState()
    val currentScanIndex by movieViewModel.currentScanIndex.collectAsState() // 新增：引入当前进度
    val totalScanCount by movieViewModel.totalScanCount.collectAsState() // 新增：引入总数
    val audioViewModel: AudioViewModel = viewModelWithFactory {
        RepositoryProvider.createAudioViewModel() // 不需要 context 了
    }
    val isAudioScanning by audioViewModel.isScanning.collectAsState()
    // 当传入的 path 参数变化时，或者首次进入时，尝试加载文件列表
    LaunchedEffect(path, connectionStatus) {
        Log.d(
            "WebDavFileListScreen",
            "LaunchedEffect triggered with path: $path, status: $connectionStatus"
        )

        when (connectionStatus) {
            is FileConnectionStatus.Connected -> {
                delay(300)
                // 已连接，可以安全地列出文件
                //Log.d("WebDavFileListScreen", "Already connected, listing files for path: $path")
                viewModel.listFiles(
                    path ?.trimEnd('/').plus('/'),
                    webDavConnection.username,
                    webDavConnection.password
                )
            }

            is FileConnectionStatus.Disconnected -> {
                // 未连接，尝试连接
                viewModel.connectToWebDav(
                    webDavConnection.baseUrl, // 使用连接的基础URL进行认证
                    webDavConnection.username,
                    webDavConnection.password
                )
              //  Log.d("WebDavFileListScreen", "Disconnected. Waiting for connection trigger.")
            }

            is FileConnectionStatus.Connecting -> {
                // 正在连接，等待...
               // Log.d("WebDavFileListScreen", "Connecting...")
            }

            is FileConnectionStatus.Error -> {
                // 连接或列表错误
                val errorMessage = (connectionStatus as FileConnectionStatus.Error).message
                Log.e("WebDavFileListScreen", "Error state: $errorMessage")
                Toast.makeText(context, "WebDAV 错误: $errorMessage", Toast.LENGTH_LONG).show()
            }

            else -> {}
        }
    }
    LaunchedEffect(focusedFileName, focusedIsDir, focusedIsVideo,settingsState.webdav) {
        if (focusedFileName != null && !focusedIsDir && focusedIsVideo && settingsState.webdav) {
            Log.d("WebDavFileListScreen", "触发电影搜索: $focusedFileName")

            // 非目录文件，触发电影搜索
            // [修改] 传入 focusedMediaUri 以便查询数据库
            movieViewModel.searchFocusedMovie(
                focusedFileName!!,
                false,
                focusedMediaUri,
                dataSourceType = "WEBDAV",
                connectionName=webDavConnection.name?:"未知连接"
            )
        } else {
            // 目录或无焦点，清空电影信息
            movieViewModel.clearFocusedMovie()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnectWebDav()
            Log.d("WebDavFileListScreen", "销毁")
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
                val errorMessage = (connectionStatus as FileConnectionStatus.Error).message
                VAErrorScreen("加载失败: $errorMessage")
            }

            is FileConnectionStatus.FilesLoaded -> {
                if (fileList.isEmpty()) {
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

                                else -> {
                                    items(filteredFiles) { file ->
                                        val isDirectory = file.isDirectory
                                        val fileName = file.name
                                        // 处理文件点击
                                        val fileExtension = Tools.extractFileExtension(file.name)
                                        val fullFileUrl = path ?: "" // 直接使用文件的完整路径
                                        val authenticatedUrl =viewModel.buildAuthenticatedUrl(fullFileUrl,
                                            username = webDavConnection.username?:""
                                            , password = webDavConnection.password?:"").trimEnd('/')
                                        val encodedFileUrl = URLEncoder.encode(
                                            "${authenticatedUrl}/${
                                                fileName.trimEnd('/').trimStart('/')
                                            }",
                                            "UTF-8"
                                        )
                                        val encodedFileName = URLEncoder.encode(fileName, "UTF-8")
                                        val connectionName=URLEncoder.encode(webDavConnection.name, "UTF-8")
                                        ListItem(
                                            selected = false,
                                            onClick = {
                                                coroutineScope.launch {
                                                    if (isDirectory) {
                                                        // 构建子目录的完整 URL 路径
                                                        val encodedNewPath = URLEncoder.encode("${path?.trimEnd('/') ?:""}/${fileName.trimEnd('/').trimStart('/')}/", "UTF-8")
                                                        Log.d(
                                                            "WebDavFileListScreen",
                                                            "Navigating to subdirectory: $path to ${path?.trimEnd('/')}/${fileName.trimEnd('/').trimStart('/')}/"
                                                        )
                                                        Log.d(
                                                            "WebDavFileListScreen",
                                                            "$encodedNewPath"
                                                        )
                                                        navController.navigate("WebDavFileListScreen/$encodedNewPath/${webDavConnection.username}/${webDavConnection.password}/${ URLEncoder.encode(
                                                            webDavConnection.name,
                                                            "UTF-8"
                                                        )}")
                                                    } else {
                                                        when {
                                                            Tools.containsVideoFormat(fileExtension) -> {
                                                                Log.d(
                                                                    "WebDavFileListScreen",
                                                                    file.path
                                                                )
                                                                if (mediaId > 0 && focusedFileName == file.name && !settingsState.hideDetails) {
                                                                    val mediaInfoFN =
                                                                        MediaInfoExtractorFormFileName.extract(
                                                                            file.name
                                                                        )
                                                                    if (mediaInfoFN.mediaType == "movie") {
                                                                        navController.navigate("MovieDetails/$encodedFileUrl/WEBDAV/$encodedFileName/${connectionName}/$mediaId")
                                                                    } else {
                                                                        navController.navigate("TVSeriesDetails/$encodedFileUrl/WEBDAV/$encodedFileName/${connectionName}/$mediaId/${mediaInfoFN.season.toInt()}/${mediaInfoFN.episode.toInt()}")
                                                                    }
                                                                } else {
                                                                    navController.navigate(
                                                                        "VideoPlayer/$encodedFileUrl/WEBDAV/${encodedFileName}/${connectionName}"
                                                                    )
                                                                }
                                                            }
                                                            Tools.containsAudioFormat(fileExtension) -> {
                                                                val audioFiles =
                                                                    fileList.filter { webdavFile ->
                                                                        Tools.containsAudioFormat(
                                                                            Tools.extractFileExtension(
                                                                                webdavFile.name
                                                                            )
                                                                        )
                                                                    }

                                                                val nameToIndexMap =
                                                                    audioFiles.withIndex()
                                                                        .associateBy(
                                                                            { it.value.name },
                                                                            { it.index })

                                                                val currentAudioIndex =
                                                                    nameToIndexMap[file.name] ?: -1
                                                                if (currentAudioIndex == -1) {
                                                                    Log.e(
                                                                        "WebDavFileListScreen",
                                                                        "未找到文件在音频列表中: ${file.name}"
                                                                    )
                                                                    return@launch
                                                                }
                                                                val audioItems =
                                                                    audioFiles.map { webdavFile ->
                                                                        AudioItem(
                                                                            uri = "${authenticatedUrl}/${
                                                                                webdavFile.name.trimEnd('/').trimStart('/')
                                                                            }",
                                                                            fileName = webdavFile.name,
                                                                            dataSourceType = "WEBDAV"
                                                                        )
                                                                    }

                                                                MzDkPlayerApplication.clearStringList(
                                                                    "audio_playlist"
                                                                )
                                                                MzDkPlayerApplication.setStringList(
                                                                    "audio_playlist",
                                                                    audioItems
                                                                )

                                                                navController.navigate(
                                                                    "AudioPlayer/$encodedFileUrl/WEBDAV/${encodedFileName}/${connectionName}/$currentAudioIndex"
                                                                )
                                                            }
                                                            Tools.containsImageFileExtension(fileExtension) -> {
                                                                navController.navigate(
                                                                    "PicViewer/$encodedFileUrl/WEBDAV/${encodedFileName}/${connectionName}"
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
                                                        focusedFileName = file.name
                                                        focusedIsDir = file.isDirectory
                                                        mediaId = -1
                                                        focusedIsVideo = Tools.containsVideoFormat(
                                                            Tools.extractFileExtension(
                                                                file.name
                                                            )
                                                        )
                                                        focusedMediaUri = "${authenticatedUrl}/${
                                                            fileName.trimEnd('/').trimStart('/')
                                                        }"
                                                    }
                                                },
                                            scale = ListItemDefaults.scale(
                                                scale = 1.0f,
                                                focusedScale = 1.01f
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
                                                FileSize(file.isDirectory,file.size)
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
                            // 添加弹性空间，让海报区域在垂直方向上居中
                            // 2. 中间的海报和文字区域（包裹在一个 Column 里）
                            Column(
                                modifier = Modifier.weight(1f), // 关键：让中间区域占据所有剩余空间
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center // 海报在剩余空间里垂直居中
                            ) {
                                when (val movieResult = focusedMovie) {
                                    is Resource.Success -> {
                                        val movie = movieResult.data

                                        if (movie != null && movie.posterPath != null) {
                                            mediaId = movie.id
                                            // 显示电影海报
                                            Box(
                                                Modifier
                                                    .widthIn(180.dp, 200.dp)
                                                    .border(
                                                        width = 2.dp,
                                                        color = Color.Gray.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(20.dp)
                                                    )
                                            ) {
                                                AsyncImage(
                                                    model = "https://image.tmdb.org/t/p/w500${movie.posterPath}",
                                                    contentDescription = movie.title,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(20.dp)) // 增大圆角
                                                )
                                            }

                                        } else {
                                            // 没有电影海报，显示默认视频图标
                                            VideoBigIcon(
                                                focusedIsDir,
                                                focusedFileName,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp)

                                            )
                                        }
                                    }

                                    is Resource.Loading -> {
                                        MediaInfoLoading()
                                    }

                                    is Resource.Error -> {
                                        VideoBigIcon(
                                            focusedIsDir,
                                            focusedFileName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)

                                        )
                                    }
                                }
                                when (val movieResult = focusedMovie) {
                                    is Resource.Success -> {
                                        val movie = movieResult.data
                                        if (movie != null) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                MediaTitle(movie.title)
                                                MediaReleaseDate(movie.releaseDate)
                                            }
                                        } else {
                                            MediaFocusedFileName(focusedFileName)
                                        }
                                    }

                                    else -> {
                                        MediaFocusedFileName(focusedFileName)
                                    }
                                }
                            }
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
                                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                                ) {
                                    // 按钮行
                                    // --- 视频扫描按钮 ---
                                    val fullFileUrl = path ?: "" // 直接使用文件的完整路径
                                    val authenticatedUrl =viewModel.buildAuthenticatedUrl(fullFileUrl,
                                        username = webDavConnection.username?:""
                                        , password = webDavConnection.password?:"").trimEnd('/')
                                    CirCleIconButton(
                                        icon = painterResource(R.drawable.videoadd24dp),
                                        // 动态显示 tooltip 内容
                                        tooltip = if (isScanning && totalScanCount > 0)
                                            "正在获取信息 $currentScanIndex/$totalScanCount"
                                        else "批量添加到视频库",
                                        onClick = {
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
                                                val uri = "${authenticatedUrl}/${
                                                    file.name.trimEnd('/').trimStart('/')
                                                }"
                                                file.name to uri
                                            }

                                            // 3. 调用 ViewModel 开始后台任务
                                            Toast.makeText(
                                                context,
                                                "开始后台获取信息，请稍候...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            movieViewModel.batchScrapeVideoInfo(
                                                videoList = scanList,
                                                dataSourceType = "WEBDAV",
                                                connectionName = webDavConnection.name?:"未知连接名"
                                            )
                                        }
                                    )
                                    // --- 音乐扫描按钮 ---
                                    CirCleIconButton(
                                        icon = painterResource(R.drawable.musicnoteadd_24dp),
                                        tooltip = if (isAudioScanning) "正在解析文件名..." else "批量添加到音乐库",
                                        onClick = {
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
                                                it.name to "${authenticatedUrl}/${
                                                    it.name.trimEnd('/').trimStart('/')
                                                }"
                                            }

                                            // 3. 直接调用，瞬间完成
                                            audioViewModel.batchScrapeAudioInfo(
                                                audioList = list,
                                                dataSourceType = "WEBDAV",
                                                connectionName =  webDavConnection.name?:"未知连接名"
                                            )
                                            Toast.makeText(
                                                context,
                                                "已在后台添加 ${list.size} 首音乐",
                                                Toast.LENGTH_SHORT
                                            ).show()
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
                    "正在加载WebDav文件",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
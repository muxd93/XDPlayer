package org.mz.mzdkplayer.ui.screen.smbfile

import org.mz.mzdkplayer.tool.MediaInfoExtractorFormFileName
import NoSearchResult
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavHostController
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.model.AudioItem
import org.mz.mzdkplayer.data.model.FileConnectionStatus
import org.mz.mzdkplayer.data.repository.Resource
import org.mz.mzdkplayer.di.RepositoryProvider
import org.mz.mzdkplayer.tool.Tools
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
import org.mz.mzdkplayer.ui.screen.common.MyFileDialog
import org.mz.mzdkplayer.ui.screen.common.VAErrorScreen
import org.mz.mzdkplayer.ui.screen.vm.SMBConViewModel

import org.mz.mzdkplayer.ui.theme.myTTFColor
import org.mz.mzdkplayer.ui.theme.MyFileListItemColor
import org.mz.mzdkplayer.ui.screen.common.TvTextField
import org.mz.mzdkplayer.ui.screen.vm.AudioViewModel
import org.mz.mzdkplayer.ui.screen.vm.MovieViewModel
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel
import java.net.URLDecoder
import java.net.URLEncoder

@OptIn(UnstableApi::class)
@Composable
fun SMBFileListScreen(
    path: String?,
    navController: NavHostController,
    connectionName: String = "",
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val viewModel: SMBConViewModel = viewModel()
    val files by viewModel.fileList.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    var focusedFileName by remember { mutableStateOf<String?>(null) }
    var focusedIsDir by remember { mutableStateOf(true) }
    var focusedIsVideo by remember { mutableStateOf(false) }
    var focusedMediaUri by remember { mutableStateOf("") }
    var exoPlayer: ExoPlayer? by remember { mutableStateOf(null) }
    val movieViewModel: MovieViewModel = viewModelWithFactory {
        RepositoryProvider.createMovieViewModel()
    }// 新增：获取MovieViewModel
    // 新增：电影信息状态
    val focusedMovie by movieViewModel.focusedMovie.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()

    val isScanning by movieViewModel.isScanning.collectAsState()
    val currentScanIndex by movieViewModel.currentScanIndex.collectAsState() // 新增：引入当前进度
    val totalScanCount by movieViewModel.totalScanCount.collectAsState() // 新增：引入总数
// ...
    var seaText by remember { mutableStateOf("") }
    var mediaId by remember { mutableIntStateOf(-1) }
    //  新增：过滤后的文件列表
    val filteredFiles by remember(files, seaText) {
        derivedStateOf {
            if (seaText.isBlank()) {
                files
            } else {
                files.filter { file ->
                    file.name.contains(seaText, ignoreCase = true)
                }
            }
        }
    }
    // 控制弹窗显示
    var showEditDialog by remember { mutableStateOf(false) }
    // 处理路径变化和连接状态
    LaunchedEffect(path, connectionStatus) {
        val decodedPath = try {
            URLDecoder.decode(path ?: "", "UTF-8")
        } catch (e: Exception) {
            Log.e("SMBFileListScreen", "路径解码失败: $e")
            Toast.makeText(context, "路径格式错误", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }

        if (decodedPath.isEmpty()) {
            Log.w("SMBFileListScreen", "路径为空")
            return@LaunchedEffect
        }

        // 解析SMB路径
        val smbConfig = viewModel.parseSMBPath(decodedPath)
        if (smbConfig.server.isEmpty()) {
            Log.e("SMBFileListScreen", "无效的SMB路径: $decodedPath")
            Toast.makeText(context, "无效的SMB路径", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }

        when (connectionStatus) {
            is FileConnectionStatus.Disconnected -> {
                Log.d("SMBFileListScreen", "未连接，开始连接: ${smbConfig.server}")
                delay(300)
                viewModel.connectToSMB(
                    smbConfig.server,
                    smbConfig.username,
                    smbConfig.password,
                    smbConfig.share
                )
            }

            is FileConnectionStatus.Connected -> {
                Log.d("SMBFileListScreen", "已连接，列出文件: ${smbConfig.path}")
                viewModel.listSMBFiles(smbConfig)
            }

            is FileConnectionStatus.Error -> {
                val errorMessage = (connectionStatus as FileConnectionStatus.Error).message
                Log.e("SMBFileListScreen", "连接错误: $errorMessage")
                Toast.makeText(context, "SMB错误: $errorMessage", Toast.LENGTH_LONG).show()
            }

            else -> {}
        }
    }

    // 处理焦点变化和媒体播放
    // 处理焦点变化和媒体播放
    LaunchedEffect(focusedFileName, focusedIsDir, focusedIsVideo) {
        if (focusedFileName != null && !focusedIsDir && focusedIsVideo) {
            // 非目录文件，触发电影搜索
            // [修改] 传入 focusedMediaUri 以便查询数据库
            Log.d("SMBFileListScreen", "触发电影搜索: $focusedFileName")
            movieViewModel.searchFocusedMovie(
                focusedFileName!!,
                false,
                focusedMediaUri,
                dataSourceType = "SMB",
                connectionName = connectionName
            )
        } else {
            // 目录或无焦点，清空电影信息
            movieViewModel.clearFocusedMovie()
        }
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            Log.d("SMBFileListScreen", "界面销毁，释放资源")
            exoPlayer?.release()
            viewModel.disconnectSMB()
        }
    }
// 获取 AudioViewModel
    val audioViewModel: AudioViewModel = viewModelWithFactory {
        RepositoryProvider.createAudioViewModel() // 不需要 context 了
    }
    val isAudioScanning by audioViewModel.isScanning.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
    {
        when (connectionStatus) {

            is FileConnectionStatus.FilesLoaded -> {
                if (files.isEmpty()) {

                    FileEmptyScreen("此目录为空")

                } else {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.7f),
                        )
                        {

                            LazyColumn(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxHeight()
                                    .weight(0.7f)
                            )
                            {
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
                                            val fileExtension =
                                                Tools.extractFileExtension(file.name)
                                            ListItem(
                                                selected = false,

                                                onClick = {
                                                    // --- 提取公共变量/准备工作 ---

                                                    val connectionName = connectionName

                                                    // 构造完整的 SMB URI（含用户名/密码，用于文件访问）
                                                    val fullSmbUri =
                                                        "smb://${file.username}:${file.password}@${file.server}/${file.share}${file.fullPath}"

                                                    // 尝试对 URI 和文件名进行 URL 编码
                                                    val encodedUri = try {
                                                        URLEncoder.encode(fullSmbUri, "UTF-8")
                                                    } catch (e: Exception) {
                                                        Log.e(
                                                            "SMBFileListScreen",
                                                            "URI 编码失败: $e"
                                                        )
                                                        Toast.makeText(
                                                            context,
                                                            "路径编码失败",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        return@ListItem // 编码失败，退出点击事件
                                                    }

                                                    val encodedFileName = try {
                                                        URLEncoder.encode(file.name, "UTF-8")
                                                    } catch (e: Exception) {
                                                        Log.e(
                                                            "SMBFileListScreen",
                                                            "文件名编码失败: $e"
                                                        )
                                                        Toast.makeText(
                                                            context,
                                                            "文件名编码失败",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        return@ListItem // 编码失败，退出点击事件
                                                    }

                                                    // --- 核心逻辑分支 ---
                                                    when {
                                                        file.isDirectory -> {
                                                            // 导航到子目录
                                                            val newPath = viewModel.buildSMBPath(
                                                                file.server,
                                                                file.share,
                                                                file.fullPath,
                                                                file.username,
                                                                file.password
                                                            )
                                                            // 注意：这里的 newPath 是给导航路由使用的，需要重新编码
                                                            val encodedNewPath = try {
                                                                URLEncoder.encode(newPath, "UTF-8")
                                                            } catch (e: Exception) {
                                                                Log.e(
                                                                    "SMBFileListScreen",
                                                                    "路径编码失败: $e"
                                                                )
                                                                Toast.makeText(
                                                                    context,
                                                                    "路径编码失败",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                                return@ListItem
                                                            }
                                                            navController.navigate("SMBFileListScreen/$encodedNewPath/$connectionName")
                                                        }

                                                        Tools.containsVideoFormat(fileExtension) -> {
                                                            // 处理视频文件点击
                                                            Log.d(
                                                                "SMBFileListScreen",
                                                                "connectionName:$connectionName"
                                                            )
                                                            Log.d(
                                                                "SMBFileListScreen",
                                                                "movieId:$mediaId"
                                                            ) // 假设 mediaId 在外部作用域

                                                            // 检查是否有媒体信息（mediaId > 0, 且是焦点文件, 且未隐藏详情）
                                                            if (mediaId > 0 && focusedFileName == file.name && !settingsState.hideDetails) {
                                                                val mediaInfoFN =
                                                                    MediaInfoExtractorFormFileName.extract(
                                                                        file.name
                                                                    )
                                                                val route =
                                                                    if (mediaInfoFN.mediaType == "movie") {
                                                                        "MovieDetails/$encodedUri/SMB/$encodedFileName/${connectionName}/$mediaId"
                                                                    } else {
                                                                        // 注意：这里假设 season/episode 可以安全地转换为 Int
                                                                        "TVSeriesDetails/$encodedUri/SMB/$encodedFileName/${connectionName}/$mediaId/${mediaInfoFN.season.toInt()}/${mediaInfoFN.episode.toInt()}"
                                                                    }
                                                                navController.navigate(route)
                                                            } else {
                                                                // 没有电影信息，直接播放
                                                                navController.navigate("VideoPlayer/$encodedUri/SMB/$encodedFileName/${connectionName}")
                                                            }
                                                        }

                                                        Tools.containsAudioFormat(fileExtension) -> {
                                                            // 处理音频文件点击
                                                            val audioFiles =
                                                                files.filter { smbFile ->
                                                                    Tools.containsAudioFormat(
                                                                        Tools.extractFileExtension(
                                                                            smbFile.name
                                                                        )
                                                                    )
                                                                }

                                                            // 构建文件名到索引的映射（一次构建）
                                                            val currentAudioIndex =
                                                                audioFiles.withIndex()
                                                                    .firstOrNull { it.value.name == file.name }
                                                                    ?.index ?: -1

                                                            if (currentAudioIndex == -1) {
                                                                Log.e(
                                                                    "SMBFileListScreen",
                                                                    "未找到文件在音频列表中: ${file.name}"
                                                                )
                                                                return@ListItem
                                                            }

                                                            // 构建播放列表数据
                                                            val audioItems =
                                                                audioFiles.map { smbFile ->
                                                                    AudioItem(
                                                                        uri = "smb://${smbFile.username}:${smbFile.password}@${smbFile.server}/${smbFile.share}${smbFile.fullPath}",
                                                                        fileName = smbFile.name,
                                                                        dataSourceType = "SMB"
                                                                    )
                                                                }

                                                            // 设置全局播放列表
                                                            MzDkPlayerApplication.clearStringList("audio_playlist")
                                                            MzDkPlayerApplication.setStringList(
                                                                "audio_playlist",
                                                                audioItems
                                                            )

                                                            // 传递当前音频项在播放列表中的索引并导航
                                                            navController.navigate("AudioPlayer/$encodedUri/SMB/$encodedFileName/${connectionName}/$currentAudioIndex")
                                                        }

                                                        Tools.containsImageFileExtension(
                                                            fileExtension
                                                        ) -> {
                                                            // 处理图片文件点击
                                                            navController.navigate("PicViewer/$encodedUri/SMB/$encodedFileName/${connectionName}")
                                                        }

                                                        else -> {
                                                            // 不支持的文件格式
                                                            Toast.makeText(
                                                                context,
                                                                "不支持的文件格式: $fileExtension",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (Tools.containsVideoFormat(
                                                            fileExtension
                                                        )
                                                    ) showEditDialog = true
                                                },
                                                colors = MyFileListItemColor(),
                                                modifier = Modifier
                                                    .padding(end = 10.dp)
                                                    .height(40.dp)
                                                    .onFocusChanged { focusState ->
                                                        if (focusState.isFocused) {
                                                            focusedFileName = file.name
                                                            focusedIsDir = file.isDirectory
                                                            mediaId = -1
                                                            focusedIsVideo =
                                                                Tools.containsVideoFormat(
                                                                    Tools.extractFileExtension(
                                                                        file.name
                                                                    )
                                                                )
                                                            focusedMediaUri =
                                                                "smb://${file.username}:${file.password}@${file.server}/${file.share}${file.fullPath}"
//                                                            Log.d(
//                                                                "SMBFileListScreen",
//                                                                "焦点变化: ${file.name}, 是目录: $focusedMediaUri"
//                                                            )
                                                        }
                                                    },
                                                scale = ListItemDefaults.scale(
                                                    scale = 1.0f,
                                                    focusedScale = 1.01f
                                                ),
                                                leadingContent = {
                                                    val fileExtension = Tools.extractFileExtension(file.name)
                                                    FileIcon(file.isDirectory, fileExtension)
                                                },
                                                headlineContent = {
                                                    FileName(file.name)
                                                },
                                                trailingContent = {
                                                    // 只有文件才显示大小，目录可以留空或显示项数
                                                    FileSize(file.isDirectory, file.fileSize)
                                                }
                                            )


                                        }
                                    }
                                }

                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.3f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        )
                        {
                            // 搜索框放在最上面
                            TvTextField(
                                value = seaText,
                                onValueChange = { seaText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                colors = myTTFColor(),
                                placeholder = "请输入文件名",
                                textStyle = TextStyle(color = Color.White),
                            )

                            // 2. 中间的海报和文字区域（包裹在一个 Column 里）
                            Column(
                                modifier = Modifier.weight(1f), // 关键：让中间区域占据所有剩余空间
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center // 海报在剩余空间里垂直居中
                            )
                            {

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

                                // 电影信息区域 - 居中显示
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
                                        CirCleIconButton(
                                            icon = painterResource(R.drawable.videoadd24dp),
                                            // 动态显示 tooltip 内容
                                            tooltip = if (isScanning && totalScanCount > 0)
                                                "正在获取信息 $currentScanIndex/$totalScanCount"
                                            else "批量添加到视频库",
                                            onClick = {
                                                // 1. 过滤出所有的视频文件 (不递归，只取当前层级)
                                                val videoFilesToScan = files.filter { file ->
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
                                                    val uri =
                                                        "smb://${file.username}:${file.password}@${file.server}/${file.share}${file.fullPath}"
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
                                                    dataSourceType = "SMB",
                                                    connectionName = connectionName
                                                )
                                            }
                                        )
                                        // --- 音乐扫描按钮 ---
                                        CirCleIconButton(
                                            icon = painterResource(R.drawable.musicnoteadd_24dp),
                                            tooltip = if (isAudioScanning) "正在解析文件名..." else "批量添加到音乐库",
                                            onClick = {
                                                // 1. 过滤音频文件
                                                val audioFiles = files.filter {
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
                                                    it.name to "smb://${it.username}:${it.password}@${it.server}/${it.share}${it.fullPath}"
                                                }

                                                // 3. 直接调用，瞬间完成
                                                audioViewModel.batchScrapeAudioInfo(
                                                    audioList = list,
                                                    dataSourceType = "SMB",
                                                    connectionName = connectionName
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
                            }}
                        }


            }

            is FileConnectionStatus.Error -> {
                val errorMessage = (connectionStatus as FileConnectionStatus.Error).message
                VAErrorScreen(
                    "加载失败: $errorMessage",
                )
            }

            else -> {
                LoadingScreen(
                    "正在连接SMB服务器",
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }
    }
    if (showEditDialog) {
        MyFileDialog(
            onDismiss = { showEditDialog = false },
            fileName = focusedFileName,
            onEditClick = {
                showEditDialog = false
                navController.navigate(
                    "EditTMDBInfoScreen/${
                        URLEncoder.encode(
                            focusedMediaUri,
                            "UTF-8"
                        )
                    }"
                )
            },
            onCloseClick = { showEditDialog = false }
        )
    }
}



package org.mz.mzdkplayer.ui.screen.localfile

import NoSearchResult
import android.content.Context
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.model.AudioItem
import org.mz.mzdkplayer.data.model.LocalFileLoadStatus
import org.mz.mzdkplayer.data.repository.Resource
import org.mz.mzdkplayer.di.RepositoryProvider
import org.mz.mzdkplayer.tool.MediaInfoExtractorFormFileName
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
import org.mz.mzdkplayer.ui.screen.common.VAErrorScreen


import org.mz.mzdkplayer.ui.theme.myTTFColor
import org.mz.mzdkplayer.ui.theme.MyFileListItemColor

import org.mz.mzdkplayer.ui.screen.common.TvTextField
import org.mz.mzdkplayer.ui.screen.vm.AudioViewModel
import org.mz.mzdkplayer.ui.screen.vm.MovieViewModel
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

@OptIn(UnstableApi::class)
@Composable
fun LocalFileListScreen(path: String?, navController: NavHostController, settingsViewModel: SettingsViewModel = viewModel()) {
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
    val movieViewModel: MovieViewModel = viewModelWithFactory {
        RepositoryProvider.createMovieViewModel()
    }// 新增：获取MovieViewModel
    // 新增：电影信息状态
    val focusedMovie by movieViewModel.focusedMovie.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()

    val isScanning by movieViewModel.isScanning.collectAsState()
    val currentScanIndex by movieViewModel.currentScanIndex.collectAsState() // 新增：引入当前进度
    val totalScanCount by movieViewModel.totalScanCount.collectAsState() // 新增：引入总数
    // 获取 AudioViewModel
    val audioViewModel: AudioViewModel = viewModelWithFactory {
        RepositoryProvider.createAudioViewModel() // 不需要 context 了
    }
    val isAudioScanning by audioViewModel.isScanning.collectAsState()
    var mediaId by remember { mutableIntStateOf(-1) }
    var focusedIsVideo by remember { mutableStateOf(false) }
    var focusedMediaUri by remember { mutableStateOf("") }
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
    // 处理焦点变化和媒体播放
    LaunchedEffect(focusedFileName, focusedIsDir, focusedIsVideo,settingsState.local) {
        if (focusedFileName != null && !focusedIsDir && focusedIsVideo && settingsState.local) {
            // 非目录文件，触发电影搜索
            // [修改] 传入 focusedMediaUri 以便查询数据库
            Log.d("LocalFileListScreen", "触发电影搜索: $focusedFileName")
            movieViewModel.searchFocusedMovie(
                focusedFileName!!,
                false,
                focusedMediaUri,
                dataSourceType = "LOCAL",
                connectionName = "本地文件"
            )
        } else if (focusedFileName != null && !focusedIsDir && focusedIsVideo) {
            movieViewModel.getFocusedInfo(
                focusedFileName!!,
                false,
                focusedMediaUri,
                dataSourceType = "LOCAL",
                connectionName = "本地文件"
            )
        } else{
            // 目录或无焦点，清空电影信息
            movieViewModel.clearFocusedMovie()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
    {
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
                                        // 本地文件 URI 格式通常是 file:///full/path/to/file
                                        val fullFileUri = "file://$fullPath"

                                        val encodedFileUri = try {
                                            URLEncoder.encode(fullFileUri, "UTF-8")
                                        } catch (e: Exception) {
                                            Log.e("LocalFileListScreen", "文件URI编码失败: $e")
                                            Toast.makeText(
                                                context,
                                                "文件URI编码失败",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@items
                                        }

                                        // 2. 尝试编码文件名
                                        val encodedFileName = try {
                                            URLEncoder.encode(fileName, "UTF-8")
                                        } catch (e: Exception) {
                                            Log.e("LocalFileListScreen", "文件名编码失败: $e")
                                            Toast.makeText(
                                                context,
                                                "文件名编码失败",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@items
                                        }

                                        ListItem(
                                            selected = false,
                                            onClick = {
                                                coroutineScope.launch {
                                                    // --- 统一编码和错误处理 ---

                                                    // 1. 尝试编码完整路径 (作为 URI 使用)

                                                    val fileExtension =
                                                        Tools.extractFileExtension(fileName)

                                                    when {
                                                        isDirectory -> {
                                                            // --- 目录点击处理 ---
                                                            // 对新的路径（完整路径）进行编码，用于导航
                                                            val encodedNewPath = try {
                                                                URLEncoder.encode(fullPath, "UTF-8")
                                                            } catch (e: Exception) {
                                                                Log.e(
                                                                    "LocalFileListScreen",
                                                                    "目录路径编码失败: $e"
                                                                )
                                                                Toast.makeText(
                                                                    context,
                                                                    "目录路径编码失败",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                                return@launch
                                                            }

                                                            Log.d(
                                                                "LocalFileListScreen",
                                                                "Navigating to subdirectory: $fullPath"
                                                            )
                                                            // 导航到子目录
                                                            navController.navigate("LocalFileListScreen/$encodedNewPath")
                                                        }

                                                        Tools.containsVideoFormat(fileExtension) -> {
                                                            // 检查是否有媒体信息（mediaId > 0, 且是焦点文件, 且未隐藏详情）
                                                            if (mediaId > 0 && focusedFileName == file.name && !settingsState.hideDetails) {
                                                                val mediaInfoFN =
                                                                    MediaInfoExtractorFormFileName.extract(
                                                                        file.name
                                                                    )
                                                                val route =
                                                                    if (mediaInfoFN.mediaType == "movie") {
                                                                        "MovieDetails/$encodedFileUri/LOCAL/$encodedFileName/本地文件/$mediaId"
                                                                    } else {
                                                                        // 注意：这里假设 season/episode 可以安全地转换为 Int
                                                                        "TVSeriesDetails/$encodedFileUri/LOCAL/$encodedFileName/本地文件/$mediaId/${mediaInfoFN.season.toInt()}/${mediaInfoFN.episode.toInt()}"
                                                                    }
                                                                navController.navigate(route)
                                                            } else {
                                                                navController.navigate(
                                                                    "VideoPlayer/$encodedFileUri/LOCAL/$encodedFileName/本地文件" // connectionName 留空
                                                                )
                                                            }
                                                        }

                                                        Tools.containsAudioFormat(fileExtension) -> {
                                                            // 构建音频文件列表（只包含音频文件）
                                                            // 注意：这里使用 fileList 而不是 filteredFiles，以获取完整列表
                                                            val audioFiles =
                                                                files.filter { localFile ->
                                                                    !localFile.isDirectory && Tools.containsAudioFormat(
                                                                        Tools.extractFileExtension(
                                                                            localFile.name
                                                                        )
                                                                    )
                                                                }

                                                            // 快速查找索引
                                                            val currentAudioIndex =
                                                                audioFiles.withIndex()
                                                                    .firstOrNull { it.value.absolutePath == fullPath }?.index
                                                                    ?: -1

                                                            if (currentAudioIndex == -1) {
                                                                Log.e(
                                                                    "LocalFileListScreen",
                                                                    "未找到文件在音频列表中: $fileName"
                                                                )
                                                                return@launch
                                                            }

                                                            // 构建播放列表
                                                            val audioItems =
                                                                audioFiles.map { localFile ->
                                                                    AudioItem(
                                                                        uri = "file://${localFile.absolutePath}",
                                                                        fileName = localFile.name,
                                                                        dataSourceType = "LOCAL"
                                                                    )
                                                                }

                                                            // 设置数据
                                                            MzDkPlayerApplication.clearStringList("audio_playlist")
                                                            MzDkPlayerApplication.setStringList(
                                                                "audio_playlist",
                                                                audioItems
                                                            )

                                                            // 导航到音频播放器
                                                            navController.navigate(
                                                                "AudioPlayer/$encodedFileUri/LOCAL/$encodedFileName/本地文件/$currentAudioIndex" // connectionName 留空
                                                            )
                                                        }

                                                        Tools.containsImageFileExtension(
                                                            fileExtension
                                                        ) -> {
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
                                                        mediaId = -1
                                                        focusedIsVideo =
                                                            Tools.containsVideoFormat(
                                                                Tools.extractFileExtension(
                                                                    file.name
                                                                )
                                                            )
                                                        focusedMediaUri =
                                                            "file://${file.absolutePath}"
                                                    }
                                                },
                                            scale = ListItemDefaults.scale(
                                                scale = 1.0f,
                                                focusedScale = 1.02f
                                            ),
                                            leadingContent = {
                                                val fileExtension =
                                                    Tools.extractFileExtension(file.name)
                                                FileIcon(isDirectory, fileExtension)
                                            },
                                            headlineContent = {
                                                FileName(fileName)
                                            },
                                            trailingContent = {
                                                // 只有文件才显示大小，目录可以留空或显示项数
                                                FileSize(file.isDirectory, file.length())
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
                            verticalArrangement = Arrangement.Top
                        ) {
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
                                                file.name to "file://${file.absolutePath}"
                                            }

                                            // 3. 调用 ViewModel 开始后台任务
                                            Toast.makeText(
                                                context,
                                                "开始后台获取信息，请稍候...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            movieViewModel.batchScrapeVideoInfo(
                                                videoList = scanList,
                                                dataSourceType = "LOCAL",
                                                connectionName = "本地文件"
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
                                                it.name to "file://${it.absolutePath}"
                                            }

                                            // 3. 直接调用，瞬间完成
                                            audioViewModel.batchScrapeAudioInfo(
                                                audioList = list,
                                                dataSourceType = "LOCAL",
                                                connectionName = "本地文件"
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
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
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
import org.mz.mzdkplayer.ui.screen.common.ISODialog

import org.mz.mzdkplayer.ui.screen.common.LoadingScreen
import org.mz.mzdkplayer.ui.screen.common.MediaFocusedFileName
import org.mz.mzdkplayer.ui.screen.common.MediaInfoLoading
import org.mz.mzdkplayer.ui.screen.common.MediaPreviewSection
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
import kotlin.time.Duration.Companion.milliseconds

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
    // 获取 AudioViewModel
    val audioViewModel: AudioViewModel = viewModelWithFactory {
        RepositoryProvider.createAudioViewModel() // 不需要 context 了
    }
    val isAudioScanning by audioViewModel.isScanning.collectAsState()
    var seaText by remember { mutableStateOf("") }
    var mediaId by remember { mutableIntStateOf(-1) }
    var showISODialog by remember { mutableStateOf(false) }
    var isoTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentISOUri by remember { mutableStateOf("") }
    var currentISOFileName by remember { mutableStateOf<String?>(null) }
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
            Toast.makeText(context, context.getString(R.string.ui_label_invalid_path_format), Toast.LENGTH_SHORT).show()
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
                delay(300.milliseconds)
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
                Toast.makeText(context, context.getString(R.string.ui_label_smb_error,errorMessage), Toast.LENGTH_LONG).show()
            }

            else -> {}
        }
    }

    // 处理焦点变化和媒体播放
    LaunchedEffect(focusedFileName, focusedIsDir, focusedIsVideo,settingsState.smb) {
        // 1. 基础校验：如果是目录、无文件名、或者不是视频，直接清空信息
        if (focusedFileName == null || focusedIsDir || !focusedIsVideo) {
            movieViewModel.clearFocusedMovie()
            return@LaunchedEffect
        }

        // 2. 根据设置决定策略
        // settingsState.smb 为 false 代表 "禁止自动刮削/仅本地数据" (防止重复入库)
        if (settingsState.smb) {
            // === 模式 A：自动刮削 (主数据源) ===
            // 场景：这是用户的主要观看路径，允许自动联网获取信息。
            // 非目录文件，触发电影搜索
            // [修改] 传入 focusedMediaUri 以便查询数据库
            Log.d("SMBFileListScreen", "触发电影搜索: $focusedFileName")
            movieViewModel.searchFocusedMovie(
                focusedFileName,
                false,
                focusedMediaUri,
                dataSourceType = "SMB",
                connectionName = connectionName
            )

        } else {
            // === 模式 B：仅查询数据库 (防重复) ===
            // 场景：用户不希望此协议自动产生新数据，但如果之前"手动批量扫描"过，这里应该显示出来。
            Log.d("SMBFileListScreen", "禁止自动刮削模式: 仅查询数据库: $focusedFileName")
            movieViewModel.getFocusedInfo(
                movieName = focusedFileName,
                isDirectory = false,
                videoUri = focusedMediaUri,
                dataSourceType = "SMB",
                connectionName = connectionName
            )
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
    {
        when (connectionStatus) {
            is FileConnectionStatus.Error -> {
                // 显示错误信息
                val errorMessage = (connectionStatus as FileConnectionStatus.Error).message
                VAErrorScreen(
                    stringResource(R.string.ui_label_loading_failed,errorMessage),
                )
            }
            is FileConnectionStatus.FilesLoaded -> {
                if (files.isEmpty()) {

                    FileEmptyScreen(stringResource(R.string.ui_label_directory_empty))
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
                                            NoSearchResult(text = "${stringResource(R.string.ui_label_no_match_truncated)} \"$seaText\" ${
                                                stringResource(
                                                    R.string.ui_label_files_suffix
                                                )
                                            }")
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
                                                        Toast.makeText(context, context.getString(R.string.ui_label_directory_path_encoding_failed), Toast.LENGTH_SHORT).show()
                                                        return@ListItem // 编码失败，退出点击事件
                                                    }

                                                    val encodedFileName = try {
                                                        URLEncoder.encode(file.name, "UTF-8")
                                                    } catch (e: Exception) {
                                                        Log.e(
                                                            "SMBFileListScreen",
                                                            "文件名编码失败: $e"
                                                        )
                                                        Toast.makeText(context, context.getString(R.string.ui_label_filename_encoding_failed), Toast.LENGTH_SHORT).show()
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
                                                                Toast.makeText(context, context.getString(R.string.ui_label_directory_path_encoding_failed), Toast.LENGTH_SHORT).show()
                                                                return@ListItem
                                                            }
                                                            navController.navigate("SMBFileListScreen/$encodedNewPath/$connectionName")
                                                        }

                                                        Tools.containsVideoFormat(fileExtension) -> {
                                                            val isIso = fileExtension.lowercase() == "iso"
                                                            if (isIso) {
                                                                // ====================== ISO 特殊处理 ======================
                                                                currentISOFileName = file.name
                                                                currentISOUri = fullSmbUri

                                                                // TODO: 这里未来可以接入 LibVLC 实时解析 ISO 的 Title 列表
                                                                // 目前先使用占位数据，替换成真实解析结果即可
                                                                // 示例解析逻辑可参考 MzVlcPlayer.kt 中的 parseAsync + media.getTracks()
                                                                isoTitles = listOf(
                                                                    "Title 0 - 主电影 (Main Feature)",
                                                                    "Title 1 - 额外内容 (Extras)",
                                                                    "Title 2 - 菜单 (Menu)",
                                                                    "Title 3 - 预告片 (Trailer)"
                                                                )

                                                                showISODialog = true
                                                                return@ListItem
                                                            }
                                                            // 处理视频文件点击
                                                            Log.d(
                                                                "SMBFileListScreen",
                                                                "connectionName:$connectionName"
                                                            )
//                                                            Log.d(
//                                                                "SMBFileListScreen",
//                                                                "movieId:$mediaId"
//                                                            ) // 假设 mediaId 在外部作用域

                                                            // 检查是否有媒体信息（mediaId > 0, 且是焦点文件, 且未隐藏详情）
                                                            if (mediaId > 0 && focusedFileName == file.name && !settingsState.hideDetails) {
                                                                // 💡 核心改动：从当前已经加载成功的 focusedMovie 中获取信息
                                                                val currentMedia = (focusedMovie as? Resource.Success)?.data
                                                                if (currentMedia != null) {
                                                                    val route = if (currentMedia.isMovie) {
                                                                        // 电影路由
                                                                        "MovieDetails/$encodedUri/SMB/$encodedFileName/${connectionName}/$mediaId"
                                                                    } else {
                                                                        // 剧集路由：优先使用 currentMedia 里的季集信息，如果没有则降级使用解析器（保证健壮性）
                                                                        val s = currentMedia.seasonNumber
                                                                        val e = currentMedia.episodeNumber
                                                                        "TVSeriesDetails/$encodedUri/SMB/$encodedFileName/${connectionName}/$mediaId/$s/$e"
                                                                    }
                                                                    navController.navigate(route)
                                                                } else {
                                                                    // 如果虽然有 mediaId 但对象为空（理论不应发生），降级直接播放
                                                                    navController.navigate("VideoPlayer/$encodedUri/SMB/$encodedFileName/${connectionName}")
                                                                }
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
                                                                context.getString(R.string.ui_label_unsupported_file_format,fileExtension),
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
                                                            focusedMediaUri = "smb://${file.username}:${file.password}@${file.server}/${file.share}${file.fullPath}"
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
                                placeholder =stringResource(R.string.ui_label_please_enter_filename),
                                textStyle = TextStyle(color = Color.White),
                            )
                            // --- 关键修改：添加弹簧 1 ---
                           // Spacer()
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
                            // --- 关键修改：添加弹簧 2 ---
                            //Spacer(modifier = Modifier.weight(1f))
                            // 3. 底部的进度和按钮区域
                            // 不再嵌套在上面的 Column 里，而是直接放在最外层 Column 的底部
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp), // 距离底部边缘一点间距
                                horizontalAlignment = Alignment.CenterHorizontally
                            )
                            {
                                // 进度显示区：固定高度 30.dp 左右，避免布局跳动

                                        // 进度显示区：固定高度 30.dp 左右，避免布局跳动
                                        Box(
                                            modifier = Modifier.heightIn(0.dp,30.dp).padding(bottom = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val progressText = when {
                                                isScanning -> if (totalScanCount > 0) "${stringResource(R.string.ui_label_getting_video_info)} $currentScanIndex/$totalScanCount" else stringResource(R.string.ui_label_preparing_video_scan)
                                                isAudioScanning -> stringResource(R.string.ui_label_parsing_music_filename)
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
                                )
                                {
                                        // 按钮行
                                        // --- 视频扫描按钮 ---
                                        CirCleIconButton(
                                            icon = painterResource(R.drawable.videoadd24dp),
                                            // 动态显示 tooltip 内容
                                            tooltip = if (isScanning && totalScanCount > 0)
                                                "${stringResource(R.string.ui_label_getting_info)} $currentScanIndex/$totalScanCount"
                                            else stringResource(R.string.ui_label_bulk_add_to_video_library),
                                            onClick = {
                                                if (!settingsState.smb) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.ui_label_scraping_not_enabled),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
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
                                                            context.getString(R.string.ui_label_no_video_files_in_directory),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        return@CirCleIconButton
                                                    }

                                                    // 2. 构建数据列表 Pair(fileName, fullUri)
                                                    // 注意：URI 的构建规则必须和 LazyColumn 里点击时的规则完全一致
                                                    val scanList = videoFilesToScan.map { file ->
                                                        file.name to "smb://${file.username}:${file.password}@${file.server}/${file.share}${file.fullPath}"
                                                    }

                                                    // 3. 调用 ViewModel 开始后台任务
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.ui_label_start_background_info_retrieval),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    movieViewModel.batchScrapeVideoInfo(
                                                        videoList = scanList,
                                                        dataSourceType = "SMB",
                                                        connectionName = connectionName
                                                    )
                                                }
                                            }
                                        )
                                        // --- 音乐扫描按钮 ---
                                        CirCleIconButton(
                                            icon = painterResource(R.drawable.musicnoteadd_24dp),
                                            tooltip = if (isAudioScanning) stringResource(R.string.ui_label_parsing_filename) else  stringResource(R.string.ui_label_bulk_add_to_music_library),
                                            onClick = {
                                                if (!settingsState.smb) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.ui_label_scraping_not_enabled),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
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
                                                            context.getString(R.string.ui_label_no_audio_files_found),
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
                                                        context.getString(R.string.ui_label_added_music_in_background,list.size),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        )
                                    }

                                }
                            }}
                        }


            }


            else -> {
                LoadingScreen(
                    stringResource(R.string.ui_label_connecting_to_smb_server),
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }
    }
//    if (showEditDialog) {
//        MyFileDialog(
//            onDismiss = { showEditDialog = false },
//            fileName = focusedFileName,
//            onEditClick = {
//                showEditDialog = false
//                navController.navigate(
//                    "EditTMDBInfoScreen/${
//                        URLEncoder.encode(
//                            focusedMediaUri,
//                            "UTF-8"
//                        )
//                    }"
//                )
//            },
//            onCloseClick = { showEditDialog = false }
//        )
//    }
//    if (showISODialog) {
//        ISODialog(
//            onDismiss = {
//                showISODialog = false
//                isoTitles = emptyList() // 清理数据
//            },
//            fileName = currentISOFileName,
//            titles = isoTitles,
//            currentUri = currentISOUri,
//            onTitleSelected = { selectedTitle ->
//                showISODialog = false
//                isoTitles = emptyList()
//
//                // TODO: 如果你想支持指定 Title 播放，可以在这里把 title 索引传给 VideoPlayer
//                // 例如修改导航为 "VideoPlayer/$encodedUri/SMB/$encodedFileName/${connectionName}?title=0"
//                // 然后在 MzVlcPlayer.kt 的 options 或 media.addOption(":dvd-title=${index}") 里使用
//                val encodedUri = try {
//                    URLEncoder.encode(currentISOUri, "UTF-8")
//                } catch (e: Exception) {
//                    Log.e("SMBFileListScreen", "ISO URI 编码失败: $e")
//                    return@ISODialog
//                }
//                val encodedFileName = try {
//                    URLEncoder.encode(currentISOFileName ?: "", "UTF-8")
//                } catch (e: Exception) {
//                    Log.e("SMBFileListScreen", "ISO 文件名编码失败: $e")
//                    return@ISODialog
//                }
//
//                navController.navigate("VideoPlayer/$encodedUri/SMB/$encodedFileName/${connectionName}")
//            },
//            onCloseClick = {
//                showISODialog = false
//                isoTitles = emptyList()
//            }
//        )
//    }
}



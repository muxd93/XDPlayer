package org.mz.mzdkplayer.ui.screen.library

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

import org.mz.mzdkplayer.data.local.MediaCacheEntity
import org.mz.mzdkplayer.ui.screen.common.LibraryEmpty
import org.mz.mzdkplayer.ui.screen.common.LoadingScreen
import org.mz.mzdkplayer.ui.screen.common.MediaCard
import org.mz.mzdkplayer.ui.screen.common.MyFileDialog
import org.mz.mzdkplayer.ui.screen.vm.MediaLibraryViewModel
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel
import org.mz.mzdkplayer.ui.theme.myListItemCoverColor
import java.net.URLEncoder
import java.util.Locale

// === 电影屏幕 (原生 Box 实现沉浸式列表 - 修复卡片遮挡) ===
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieLibraryScreen(
    viewModel: MediaLibraryViewModel,
    navController: NavController,
    homeNavController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val movies = viewModel.pagedMovies.collectAsLazyPagingItems()
    val movieVersions by viewModel.selectedMovieVersions.collectAsState()

    // 状态：当前获得焦点的电影 (用于更新背景)
    var focusedMovie by remember { mutableStateOf<MediaCacheEntity?>(null) }

    var showVersionDialog by remember { mutableStateOf(false) }
    var selectedMovieTitle by remember { mutableStateOf("") }
    // 状态：标记是否需要检查版本数量并执行跳转/弹窗逻辑
    var checkVersionsAfterLoad by remember { mutableStateOf(false) }
    val settingsState by settingsViewModel.uiState.collectAsState()
    val isMoviesLoading = movies.loadState.refresh == LoadState.Loading
    val isMoviesEmpty = movies.itemCount == 0
    // 控制弹窗显示
    var showEditDialog by remember { mutableStateOf(false) }

    // 开头获取 context
    val context = LocalContext.current
    // 在开头定义一个临时状态
    var isLongClickAction by remember { mutableStateOf(false) }
    // 修改后的 LaunchedEffect
    LaunchedEffect(movieVersions.size, checkVersionsAfterLoad) {
        if (checkVersionsAfterLoad && movieVersions.isNotEmpty()) {
            val versionCount = movieVersions.size

            if (isLongClickAction) {
                // === 修改后的长按逻辑 ===
                if (versionCount == 1) {
                    // 只有一个版本，允许修改
                    showEditDialog = true
                } else {
                    // 【核心修改】多个版本时，不弹窗，直接提示用户
                    Toast.makeText(
                        context,
                        "检测到多个版本，请先点击进入版本选择界面，再长按对应文件进行修改",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 可选：如果你还是想让用户选版本，但不直接进修改逻辑，可以注释掉下面这行
                    // showVersionDialog = true
                }
                isLongClickAction = false
                checkVersionsAfterLoad = false
            } else {
                // === 处理点击播放逻辑 ===
                when (versionCount) {
                    1 -> {
                        val version = movieVersions.first()
                        val encodedUri = URLEncoder.encode(version.videoUri, "UTF-8")
                        val encodedFileName = URLEncoder.encode(version.fileName, "UTF-8")
                        val connectionName = URLEncoder.encode(version.connectionName, "UTF-8")
                        if (!settingsState.hideDetails) {
                            navController.navigate("MovieDetails/$encodedUri/${version.dataSourceType}/$encodedFileName/$connectionName/${version.tmdbId}")
                        }
                        else{
                            navController.navigate("VideoPlayer/$encodedUri/${version.dataSourceType}/$encodedFileName/$connectionName")
                        }
                        checkVersionsAfterLoad = false
                        viewModel.clearSelectedMovieVersions()
                    }
                    in 2..Int.MAX_VALUE -> {
                        showVersionDialog = true
                        checkVersionsAfterLoad = false
                    }
                }
            }
        }
    }
    // 如果刚进入页面没有焦点，默认尝试获取列表第一个作为背景
    LaunchedEffect(movies.itemSnapshotList.items) {
        if (focusedMovie == null && movies.itemCount > 0) {
            focusedMovie = movies.itemSnapshotList.items.firstOrNull()
        }
    }
    if (isMoviesLoading){
        LoadingScreen(modifier = Modifier.fillMaxSize())
    }else if (isMoviesEmpty){
        LibraryEmpty(navController = homeNavController)
    }else{
        // === 使用 Box 容器来实现 ImmersiveList 的效果 ===
        Box(modifier = Modifier.fillMaxSize())
        {
            // 1. 背景层 (Background) - 位于最底层
            AnimatedContent(
                targetState = focusedMovie,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                },
                label = "BackgroundAnimation",
                modifier = Modifier.fillMaxSize()
            ) { movie ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (movie != null) {
                        val backdropUrl = movie.backdropPath ?: movie.posterPath
                        if (backdropUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    // 加上 TMDB 基础路径和尺寸
                                    .data("https://image.tmdb.org/t/p/w1280$backdropUrl")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                alpha = 0.6f // 稍微调暗一点，让文字更清晰
                            )
                        }

                        // 渐变遮罩 (让底部和左侧文字区域更清晰)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.3f),
                                            Color.Black.copy(alpha = 0.8f),
                                            Color.Black.copy(alpha = 0.95f) // 底部接近纯黑
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.9f),
                                            Color.Transparent
                                        ),
                                        endX = 1000f // 左侧文字区域加深
                                    )
                                )
                        )
                    } else {
                        // 空状态背景
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF111111))
                        )
                    }
                }
            }

            // 2. 列表层 (List) 和预留空间
            Column(modifier = Modifier.fillMaxSize()) {

                // 【修改点 2.1】增加顶部预留空间的高度，确保第一行卡片不会和顶部信息重叠
                Spacer(modifier = Modifier.height(260.dp)) // 预留出信息展示区域的高度

                // 电影列表
                LazyHorizontalGrid (
                    rows = GridCells.Fixed(1),
                    // 【修改点 2.2】设置底部内边距，确保列表底部有足够的空间，防止卡片贴边
                    // 设置左右内边距，并为卡片聚焦效果预留额外的顶部和底部空间
                    contentPadding = PaddingValues(
                        start = 32.dp,
                        end = 32.dp,
                        top = 10.dp, // 预留焦点放大空间
                        bottom = 10.dp // 底部预留，避免卡片贴边和遮挡提示文字
                    ),
                    horizontalArrangement = Arrangement.spacedBy(24.dp), // <--- 增大水平间距，让卡片更分散
                    modifier = Modifier
                        .height(260.dp).background(Color.Transparent) // 占据 Column 剩下的所有垂直空间，实现滚动
                ) {
                    items(movies.itemCount) { index ->
                        val movie = movies[index]
                        if (movie != null) {
                            MediaCard(
                                title = movie.title,
                                posterPath = movie.posterPath,
                                year = movie.releaseDate?.take(4) ?: "",
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            focusedMovie = movie
                                        }
                                    },
                                onClick = {
                                    selectedMovieTitle = movie.title
                                    viewModel.clearSelectedMovieVersions()
                                    viewModel.loadMovieVersions(movie.tmdbId)
                                    checkVersionsAfterLoad = true
                                },
                                onLongClick = {// --- 修改后的长按逻辑 ---
                                    selectedMovieTitle = movie.title
                                    // 1. 同步记录当前电影并请求版本信息
                                    focusedMovie = movie
                                    viewModel.loadMovieVersions(movie.tmdbId)

                                    // 2. 这里利用一个临时逻辑：由于 movieVersions 是在 ViewModel 里的 StateFlow
                                    // 我们需要等待它加载。但为了用户体验，我们可以直接在 LaunchedEffect 里监听
                                    // 或者简单化处理：标记当前是“长按触发”的检查
                                    isLongClickAction = true
                                    checkVersionsAfterLoad = true
                                }
                            )
                        }
                    }
                }
            }

            // 3. 电影信息展示层 (Foreground) - 位于最上层，独立于滚动内容之外
            AnimatedContent(
                targetState = focusedMovie,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                },
                label = "MovieInfoAnimation",
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
            ) { movie ->
                if (movie != null) {
                    Column(
                        modifier = Modifier
                            .padding(start = 56.dp, top = 20.dp)
                        // 【修改点 1】限制简介宽度为 480.dp，让文字更集中

                    ) {
                        Text(
                            text = "电影库",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.displaySmall, // 大标题
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 元数据行：年份 | 评分
                        val year = movie.releaseDate?.take(4) ?: ""
                        val rating = String.format(Locale.getDefault(),"%.1f", movie.voteAverage)
                        Text(
                            text = "$year  •  TMDB $rating",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFFD700) // 金色强调评分
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            modifier = Modifier.fillMaxWidth(0.7f),
                            text = movie.overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 4,

                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // 如果没有焦点电影，显示一个占位符，保持布局稳定
                    Column(
                        modifier = Modifier
                            .height(260.dp) // 保持高度和上面 Spacer(320.dp) 一致
                            .padding(start = 56.dp, top = 20.dp)
                    ) {
                        Text("电影库", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                    }
                }
            }

            // 4. 【修改点 3】滚动提示文字 (位于最底部居中)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    // 确保提示文字不会紧贴底部
                    .padding(bottom = 3.dp)
            ) {
                Text(
                    text = "长按遥控器OK键可以调出更多操作 向右滚动以查看更多内容 →",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f), // 半透明
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }




    // === 电影版本选择弹窗 (保持原样) ===
    if (showVersionDialog && movieVersions.size > 1) {
        MovieVersionSelectionDialog(
            title = selectedMovieTitle,
            versions = movieVersions,
            onDismiss = {
                showVersionDialog = false
                viewModel.clearSelectedMovieVersions()
            },
            onVersionClick = { version ->
                showVersionDialog = false
                viewModel.clearSelectedMovieVersions()

                val encodedUri = URLEncoder.encode(version.videoUri, "UTF-8")
                val encodedFileName = URLEncoder.encode(version.fileName, "UTF-8")
                val connectionName = URLEncoder.encode(version.connectionName, "UTF-8")
                if (!settingsState.hideDetails) {
                    navController.navigate("MovieDetails/$encodedUri/${version.dataSourceType}/$encodedFileName/$connectionName/${version.tmdbId}")
                }else{
                    navController.navigate("VideoPlayer/$encodedUri/${version.dataSourceType}/$encodedFileName/$connectionName")
                }
            },
            onLongClick = {
                showEditDialog = true
            }
        )
    }
    if (showEditDialog) {
        MyFileDialog(
            onDismiss = { showEditDialog = false },
            fileName = focusedMovie?.fileName,
            onEditClick = {
                showEditDialog = false
                navController.navigate("EditTMDBInfoScreen/${URLEncoder.encode(focusedMovie?.videoUri,"UTF-8")}")
            },
            onCloseClick = { showEditDialog = false }
        )
    }
}

 //弹窗组件 (保持原样)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieVersionSelectionDialog(
    title: String,
    versions: List<MediaCacheEntity>,
    onDismiss: () -> Unit,
    onVersionClick: (MediaCacheEntity) -> Unit,
    onLongClick: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .height(400.dp),
            shape = MaterialTheme.shapes.medium,
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp).widthIn(max = 600.dp)
                .height(400.dp),) {
                Text(
                    text = "$title - 选择版本",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 2,
                    fontWeight = FontWeight.Bold
                )
                // --- 新增提示文字 ---
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "提示：长按下方版本可修改该文件的 TMDB 信息",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LightGray // 使用主题色或淡蓝色强调
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (versions.isEmpty()) {
                    Text("加载中...", color = Color.Gray)
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(versions) { version ->
                            ListItem(
                                selected = false,
                                onClick = { onVersionClick(version) },
                                onLongClick = { onLongClick() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = myListItemCoverColor(),
                                overlineContent = {
                                    Text(
                                        text = "[${version.dataSourceType}] ${version.connectionName}",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = version.fileName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
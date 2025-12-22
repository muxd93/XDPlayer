package org.mz.mzdkplayer.ui.screen.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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

// === 电视剧屏幕 (原生 Box 实现沉浸式列表) ===
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLibraryScreen(
    viewModel: MediaLibraryViewModel,
    navController: NavController,
    homeNavController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val tvSeriesList = viewModel.pagedTVSeries.collectAsLazyPagingItems()
    val episodes by viewModel.selectedSeriesEpisodes.collectAsState()

    // 状态：当前获得焦点的剧集 (用于更新背景)
    var focusedTvShow by remember { mutableStateOf<MediaCacheEntity?>(null) }
    val isTvSeriesLoading = tvSeriesList.loadState.refresh == LoadState.Loading
    val isTvSeriesEmpty = tvSeriesList.itemCount == 0
    // 控制弹窗显示
    var showEpisodeDialog by remember { mutableStateOf(false) }
    var selectedSeriesName by remember { mutableStateOf("") }
    val settingsState by settingsViewModel.uiState.collectAsState()
    // 控制弹窗显示
    var showEditDialog by remember { mutableStateOf(false) }
    // 如果刚进入页面没有焦点，默认尝试获取列表第一个作为背景
    LaunchedEffect(tvSeriesList.itemSnapshotList.items) {
        if (focusedTvShow == null && tvSeriesList.itemCount > 0) {
            focusedTvShow = tvSeriesList.itemSnapshotList.items.firstOrNull()
        }
    }
    if (isTvSeriesLoading) {
        LoadingScreen(modifier = Modifier.fillMaxSize())
    } else if (isTvSeriesEmpty) {
        LibraryEmpty(navController = homeNavController, type = "tv")
    } else {
        // === 使用 Box 容器来实现 ImmersiveList 的效果 ===
        Box(modifier = Modifier.fillMaxSize())
        {
            // 1. 背景层 (Background) - 位于最底层
            AnimatedContent(
                targetState = focusedTvShow,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(
                        animationSpec = tween(
                            500
                        )
                    )
                },
                label = "BackgroundAnimation",
                modifier = Modifier.fillMaxSize()
            ) { tvShow ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (tvShow != null) {
                        val backdropUrl = tvShow.backdropPath ?: tvShow.posterPath
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

                // 增加顶部预留空间的高度，确保第一行卡片不会和顶部信息重叠
                Spacer(modifier = Modifier.height(260.dp)) // 预留出信息展示区域的高度

                // 剧集列表，使用 LazyHorizontalGrid
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(1),
                    contentPadding = PaddingValues(
                        start = 32.dp,
                        end = 32.dp,
                        top = 10.dp, // 预留焦点放大空间
                        bottom = 10.dp // 底部预留，避免卡片贴边和遮挡提示文字
                    ),
                    horizontalArrangement = Arrangement.spacedBy(24.dp), // 增大水平间距，让卡片更分散
                    modifier = Modifier
                        .height(260.dp)
                        .background(Color.Transparent) // 占据 Column 剩下的所有垂直空间
                ) {
                    items(tvSeriesList.itemCount) { index ->
                        val tvShow = tvSeriesList[index]
                        if (tvShow != null) {
                            MediaCard(
                                title = tvShow.title,
                                posterPath = tvShow.posterPath,
                                year = tvShow.releaseDate?.take(4) ?: "",
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            focusedTvShow = tvShow
                                        }
                                    },
                                onClick = {
                                    // 点击时，加载该剧集下的所有文件，并显示弹窗
                                    selectedSeriesName = tvShow.title
                                    viewModel.loadEpisodes(tvShow.tmdbId)
                                    showEpisodeDialog = true
                                },
                                onLongClick = {showEditDialog = true}
                            )
                        }
                    }
                }
            }

            // 3. 剧集信息展示层 (Foreground) - 位于最上层，独立于滚动内容之外
            AnimatedContent(
                targetState = focusedTvShow,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(
                        animationSpec = tween(
                            500
                        )
                    )
                },
                label = "TvShowInfoAnimation",
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            ) { tvShow ->
                if (tvShow != null) {
                    Column(
                        modifier = Modifier
                            .padding(start = 56.dp, top = 20.dp)
                    ) {
                        Text(
                            text = "电视剧库",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = tvShow.title,
                            style = MaterialTheme.typography.displaySmall, // 大标题
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 元数据行：年份 | 评分
                        val year = tvShow.releaseDate?.take(4) ?: ""
                        val rating = String.format("%.1f", tvShow.voteAverage)
                        Text(
                            text = "$year  •  TMDB $rating",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFFD700) // 金色强调评分
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            modifier = Modifier.fillMaxWidth(0.7f),
                            text = tvShow.overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // 如果没有焦点剧集，显示一个占位符，保持布局稳定
                    Column(
                        modifier = Modifier
                            .height(260.dp) // 保持高度一致
                            .padding(start = 56.dp, top = 20.dp)
                    ) {
                        Text(
                            "电视剧库",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray
                        )
                    }
                }
            }

            // 4. 滚动提示文字 (位于最底部居中)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
            ) {
                Text(
                    text = "向右滚动以查看更多内容 →",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f), // 半透明
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
    // === 集数选择弹窗 (保持原样) ===
    if (showEpisodeDialog) {
        EpisodeSelectionDialog(
            title = selectedSeriesName,
            episodes = episodes,
            navController = navController, // 传入 navController
            onDismiss = {
                showEpisodeDialog = false
                viewModel.clearSelectedEpisodes()
            },
            onEpisodeClick = { episode ->
                showEpisodeDialog = false
                // 跳转到现有的 TVSeriesDetailsScreen
                val encodedUri = URLEncoder.encode(episode.videoUri, "UTF-8")
                // 因为你的文件名可能作为 title 存在，这里要注意
                val fileName = "S${episode.seasonNumber}E${episode.episodeNumber}"
                val encodedFileName = URLEncoder.encode(episode.fileName, "UTF-8")
                val connectionName = URLEncoder.encode(episode.connectionName, "UTF-8")
                // 核心：这里将具体的 Uri 和 Season/Episode 传给详情页
                if (!settingsState.hideDetails) {
                    navController.navigate(
                        "TVSeriesDetails/$encodedUri/${episode.dataSourceType}/$encodedFileName/$connectionName/${episode.tmdbId}/${episode.seasonNumber}/${episode.episodeNumber}"
                    )
                } else {
                    navController.navigate("VideoPlayer/$encodedUri/${episode.dataSourceType}/$encodedFileName/$connectionName")
                }


            }
        )
    }
    if (showEditDialog) {
        MyFileDialog(
            onDismiss = { showEditDialog = false },
            fileName = focusedTvShow?.fileName,
            onEditClick = {
                showEditDialog = false
                navController.navigate("EditTMDBInfoScreen/${URLEncoder.encode(focusedTvShow?.videoUri,"UTF-8")}")
            },
            onCloseClick = { showEditDialog = false }
        )
    }
}

//弹窗组件 (保持原样，无需修改)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeSelectionDialog(
    title: String,
    episodes: List<MediaCacheEntity>,
    onDismiss: () -> Unit,
    onEpisodeClick: (MediaCacheEntity) -> Unit,
    navController: NavController
) {
    // 状态：当前选中的、准备编辑的单集
    var selectedEpisodeForEdit by remember { mutableStateOf<MediaCacheEntity?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .height(500.dp),
            shape = MaterialTheme.shapes.large,
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "$title - 选择剧集",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (episodes.isEmpty()) {
                    Text("加载中...", color = Color.Gray)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(episodes) { episode ->
                            // 【修改点】使用 ListItem 替换 Button
                            ListItem(
                                selected = false, // 焦点控制由 TV 框架自动处理
                                onClick = { onEpisodeClick(episode) },
                                onLongClick = { selectedEpisodeForEdit = episode},
                                modifier = Modifier.fillMaxWidth(),
                                colors = myListItemCoverColor(),
                                // headlineContent: 显示季集序号
                                headlineContent = {
                                    Text(
                                        text = "第 ${episode.seasonNumber} 季 第 ${episode.episodeNumber} 集",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                // supportingContent: 显示集名称（如果存在）
                                supportingContent = if (!episode.episodeName.isNullOrEmpty()) {
                                    {
                                        Text(text = episode.episodeName)
                                    }
                                } else null,
                                // trailingContent: 可以在右侧放置一个指示图标或文本
//                                trailingContent = {
//                                    Text(episode.dataSourceType,)
//                                },
                                overlineContent = {
                                    Text(
                                        "${episode.dataSourceType} · ${episode.fileName}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }

        }
    }
    // --- 在这里插入长按后的二级弹窗 ---
    if (selectedEpisodeForEdit != null) {
        MyFileDialog(
            onDismiss = { selectedEpisodeForEdit = null },
            fileName = selectedEpisodeForEdit?.fileName,
            onEditClick = {
                val episode = selectedEpisodeForEdit
                if (episode != null) {
                    val encodedUri = URLEncoder.encode(episode.videoUri, "UTF-8")
                    // 跳转到修改页面
                    navController.navigate("EditTMDBInfoScreen/$encodedUri")
                }
                selectedEpisodeForEdit = null // 关闭菜单
                onDismiss() // 如果需要，跳转后可以关闭剧集选择弹窗
            },
            onCloseClick = { selectedEpisodeForEdit = null }
        )
    }

}
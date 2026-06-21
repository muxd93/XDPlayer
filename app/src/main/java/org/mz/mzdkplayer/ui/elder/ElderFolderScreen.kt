package org.mz.mzdkplayer.ui.elder

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.local.FolderVideoEntity
import org.mz.mzdkplayer.data.local.HomeSlotEntity
import org.mz.mzdkplayer.data.local.MediaHistoryEntity
import org.mz.mzdkplayer.data.repository.FolderVideoRepository
import org.mz.mzdkplayer.data.repository.HomeSlotRepository
import org.mz.mzdkplayer.data.repository.ThumbnailManager
import java.io.File
import java.net.URLEncoder
import androidx.navigation.NavHostController

@Composable
fun ElderFolderScreen(slotId: Int, navController: NavHostController) {
    val context = LocalContext.current

    // 视频列表数据
    val videosFlow by FolderVideoRepository.getVideosBySlot(slotId).collectAsState(initial = emptyList())
    var videos by remember { mutableStateOf<List<FolderVideoEntity>>(emptyList()) }

    // 栏位信息
    var slot by remember { mutableStateOf<HomeSlotEntity?>(null) }

    // 当前焦点视频索引
    var focusedIndex by remember { mutableStateOf(0) }

    // 播放历史缓存：videoUri → MediaHistoryEntity
    var historyMap by remember { mutableStateOf<Map<String, MediaHistoryEntity>>(emptyMap()) }

    // 缩略图缓存：videoUri → File?
    var thumbnailMap by remember { mutableStateOf<Map<String, File?>>(emptyMap()) }

    // 焦点管理
    val firstItemFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }

    // 收集视频列表
    LaunchedEffect(videosFlow) {
        videos = videosFlow
    }

    // 视频列表首次加载完成时重置焦点索引 (仅在列表从空变为非空时执行)
    var videosInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(videos) {
        if (!videosInitialized && videos.isNotEmpty()) {
            focusedIndex = 0
            videosInitialized = true
        }
    }

    // 加载栏位信息
    LaunchedEffect(slotId) {
        slot = HomeSlotRepository.getSlotById(slotId)
    }

    // 加载播放历史和缩略图（增量缓存: 仅加载缓存中不存在的新视频, 避免列表变化时全量重载）
    LaunchedEffect(videos) {
        // 过滤出尚未缓存的视频, 避免重复加载
        val uncachedVideos = videos.filter { it.videoUri !in historyMap }
        if (uncachedVideos.isEmpty()) return@LaunchedEffect

        val db = org.mz.mzdkplayer.data.local.AppDatabase.getDatabase(context)
        val thumbnailManager = ThumbnailManager(context)

        val results = coroutineScope {
            uncachedVideos.map { video ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    val history = try {
                        db.mediaHistoryDao().getHistoryByUri(video.videoUri)
                    } catch (e: Exception) {
                        Log.w("ElderFolderScreen", "Failed to load history for ${video.videoUri}", e)
                        null
                    }
                    val thumbnail = try {
                        thumbnailManager.getVideoThumbnail(video)
                    } catch (e: Exception) {
                        Log.w("ElderFolderScreen", "Failed to load thumbnail for ${video.videoUri}", e)
                        null
                    }
                    Triple(video.videoUri, history, thumbnail)
                }
            }.awaitAll()
        }

        // 在已有缓存基础上追加新条目, 保留已加载的数据
        val hMap = historyMap.toMutableMap()
        val tMap = thumbnailMap.toMutableMap()
        for ((uri, history, thumbnail) in results) {
            history?.let { hMap[uri] = it }
            tMap[uri] = thumbnail
        }
        historyMap = hMap
        thumbnailMap = tMap
    }

    // 初始焦点: 有视频时聚焦第一个视频, 无视频时聚焦返回按钮
    var focusInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(videos) {
        if (!focusInitialized) {
            delay(300)
            if (videos.isNotEmpty()) {
                firstItemFocusRequester.requestFocus()
            } else {
                backFocusRequester.requestFocus()
            }
            focusInitialized = true
        }
    }

    // 返回键回到首页
    BackHandler {
        navController.popBackStack()
    }

    val currentVideo = videos.getOrNull(focusedIndex)
    val currentHistory = currentVideo?.let { historyMap[it.videoUri] }
    val currentThumbnail = currentVideo?.let { thumbnailMap[it.videoUri] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // 顶部返回区域 (Issue 9: 返回按钮与标题分离, 避免查看标题时误触返回)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            ElderTopBarButton(
                text = stringResource(R.string.elder_back),
                onClick = { navController.popBackStack() },
                focusRequester = backFocusRequester,
                icon = painterResource(id = R.drawable.baseline_arrow_back_24)
            )
            Spacer(modifier = Modifier.width(16.dp))
            // 标题: 纯文字, 不可聚焦, 避免误触返回
            Text(
                text = slot?.label?.ifEmpty { stringResource(R.string.elder_folder) }
                    ?: stringResource(R.string.elder_folder),
                color = ElderColors.textPrimary,
                fontSize = ElderDimens.titleFontSize,
                fontWeight = FontWeight.Bold
            )
        }

        // 焦点视频大图卡片
        if (currentVideo != null) {
            FocusVideoCard(
                video = currentVideo,
                thumbnailFile = currentThumbnail,
                history = currentHistory,
                onClick = {
                    navigateToPlayer(currentVideo, navController)
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.elder_no_videos),
                    color = ElderColors.textSecondary,
                    fontSize = ElderDimens.bodyFontSize
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 底部缩略图行
        if (videos.isNotEmpty()) {
            VideoThumbnailRow(
                videos = videos,
                historyMap = historyMap,
                thumbnailMap = thumbnailMap,
                focusedIndex = focusedIndex,
                firstFocusRequester = firstItemFocusRequester,
                onVideoClick = { video ->
                    navigateToPlayer(video, navController)
                },
                onFocusChange = { index ->
                    focusedIndex = index
                }
            )
        }
    }
}

private fun navigateToPlayer(
    video: FolderVideoEntity,
    navController: NavHostController
) {
    val encodedUri = URLEncoder.encode(video.videoUri, "UTF-8")
    val encodedFileName = URLEncoder.encode(video.fileName, "UTF-8")
    val encodedConnectionName = URLEncoder.encode(video.connectionName, "UTF-8")
    navController.navigate(
        "VideoPlayer/$encodedUri/${video.dataSourceType}/$encodedFileName/$encodedConnectionName"
    )
}

// ==================== 焦点视频大图卡片 ====================

@Composable
private fun FocusVideoCard(
    video: FolderVideoEntity,
    thumbnailFile: File?,
    history: MediaHistoryEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(200),
        label = "focusCardScale"
    )

    val percentage = if (history != null && history.mediaDuration > 0) {
        (history.playbackPosition.toFloat() / history.mediaDuration).coerceIn(0f, 1f)
    } else 0f

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .scale(scale),
        shape = CardDefaults.shape(shape = RoundedCornerShape(ElderDimens.cardCornerLarge)),
        colors = CardDefaults.colors(
            containerColor = ElderColors.cardBackground,
            focusedContainerColor = ElderColors.focusBackground
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(ElderDimens.focusBorderWidth, ElderColors.focusBorder),
                shape = RoundedCornerShape(ElderDimens.cardCornerLarge)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        interactionSource = interactionSource
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 缩略图
            if (thumbnailFile != null && thumbnailFile.exists()) {
                AsyncImage(
                    model = thumbnailFile,
                    contentDescription = video.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 默认视频图标
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ElderColors.cardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_movie_24),
                        contentDescription = stringResource(R.string.elder_video),
                        modifier = Modifier.size(64.dp),
                        tint = ElderColors.accent
                    )
                }
            }

            // 播放按钮叠加
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_play_arrow_24),
                    contentDescription = stringResource(R.string.elder_play),
                    modifier = Modifier.size(72.dp),
                    tint = ElderColors.textPrimary.copy(alpha = 0.9f)
                )
            }

            // 底部信息栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Color.Black.copy(alpha = 0.75f),
                        RoundedCornerShape(bottomStart = ElderDimens.cardCornerLarge, bottomEnd = ElderDimens.cardCornerLarge)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = video.fileName,
                        color = ElderColors.textPrimary,
                        fontSize = ElderDimens.bodyFontSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 进度条
                    if (percentage > 0f) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(percentage)
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color(0xFFFFC200), Color(0xFFFF9800))
                                        ),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 底部缩略图行 ====================

@Composable
private fun VideoThumbnailRow(
    videos: List<FolderVideoEntity>,
    historyMap: Map<String, MediaHistoryEntity>,
    thumbnailMap: Map<String, File?>,
    focusedIndex: Int,
    firstFocusRequester: FocusRequester,
    onVideoClick: (FolderVideoEntity) -> Unit,
    onFocusChange: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    // 当焦点索引变化时，滚动到可见
    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.height(120.dp)
    ) {
        itemsIndexed(videos, key = { _, it -> it.id }) { index, video ->
            VideoThumbnailCard(
                video = video,
                thumbnailFile = thumbnailMap[video.videoUri],
                history = historyMap[video.videoUri],
                isFirst = index == 0,
                firstFocusRequester = firstFocusRequester,
                onClick = { onVideoClick(video) },
                onFocusChanged = { focused ->
                    if (focused) onFocusChange(index)
                }
            )
        }
    }
}

@Composable
private fun VideoThumbnailCard(
    video: FolderVideoEntity,
    thumbnailFile: File?,
    history: MediaHistoryEntity?,
    isFirst: Boolean,
    firstFocusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = tween(200),
        label = "thumbScale"
    )

    val percentage = if (history != null && history.mediaDuration > 0) {
        (history.playbackPosition.toFloat() / history.mediaDuration).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(isFocused) {
        onFocusChanged(isFocused)
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(180.dp)
            .aspectRatio(16f / 10f)
            .scale(scale)
            .then(if (isFirst) Modifier.focusRequester(firstFocusRequester) else Modifier),
        shape = CardDefaults.shape(shape = RoundedCornerShape(ElderDimens.cardCornerSmall)),
        colors = CardDefaults.colors(
            containerColor = ElderColors.cardBackground,
            focusedContainerColor = ElderColors.focusBackground
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(ElderDimens.focusBorderWidth, ElderColors.focusBorder),
                shape = RoundedCornerShape(ElderDimens.cardCornerSmall)
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        interactionSource = interactionSource
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 缩略图
            if (thumbnailFile != null && thumbnailFile.exists()) {
                AsyncImage(
                    model = thumbnailFile,
                    contentDescription = video.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ElderColors.cardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_movie_24),
                        contentDescription = stringResource(R.string.elder_video),
                        modifier = Modifier.size(32.dp),
                        tint = ElderColors.accent
                    )
                }
            }

            // 文件名
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = video.fileName,
                    color = ElderColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 进度条
            if (percentage > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percentage)
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFFC200), Color(0xFFFF9800))
                                    ),
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

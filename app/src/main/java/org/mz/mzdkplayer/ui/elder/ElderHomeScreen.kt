package org.mz.mzdkplayer.ui.elder

import android.app.Activity
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.local.HomeSlotEntity
import org.mz.mzdkplayer.data.local.MediaHistoryEntity
import org.mz.mzdkplayer.data.repository.HomeSlotRepository
import org.mz.mzdkplayer.data.repository.FolderVideoRepository
import org.mz.mzdkplayer.data.repository.ElderModeConfig
import org.mz.mzdkplayer.data.repository.ModeManager
import org.mz.mzdkplayer.data.repository.SettingsRepository
import org.mz.mzdkplayer.tool.AppLauncherHelper
import org.mz.mzdkplayer.ui.screen.common.WebConfigDialog
import java.io.File
import java.net.URLEncoder
import androidx.navigation.NavHostController
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

// 行列映射：slotCount → (rows, cols)
private fun slotGridConfig(slotCount: Int): Pair<Int, Int> = when (slotCount) {
    4 -> 1 to 4
    6 -> 2 to 3
    8 -> 2 to 4
    12 -> 3 to 4
    15 -> 3 to 5
    16 -> 4 to 4
    20 -> 4 to 5
    24 -> 4 to 6
    else -> 3 to 5
}

@OptIn(UnstableApi::class)
@Composable
fun ElderHomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 通过 StateFlow 实时响应 Web 配置页面的修改
    val elderConfig by ElderModeConfig.configFlow.collectAsState()
    val slotCount = elderConfig.slotCount
    val showRecent = elderConfig.showRecent
    val recentCount = elderConfig.recentCount

    // 栏位数据
    var slots by remember { mutableStateOf<List<HomeSlotEntity>>(emptyList()) }
    val slotsFlow by HomeSlotRepository.allSlots.collectAsState(initial = emptyList())

    // 最近播放数据
    var recentVideos by remember { mutableStateOf<List<MediaHistoryEntity>>(emptyList()) }

    // 视频栏位播放历史缓存：videoUri → MediaHistoryEntity
    var videoHistoryMap by remember { mutableStateOf<Map<String, MediaHistoryEntity>>(emptyMap()) }

    // PIN 对话框状态
    var showPinDialog by remember { mutableStateOf(false) }

    // 空栏位配置对话框状态
    var showSlotConfigDialog by remember { mutableStateOf(false) }
    var configSlot by remember { mutableStateOf<HomeSlotEntity?>(null) }

    // Web 配置对话框状态
    var showWebConfigDialog by remember { mutableStateOf(false) }
    var webConfigEnabled by remember { mutableStateOf(SettingsRepository.webConfigEnabled) }

    // 长按下键计时
    var downKeyHoldStart by remember { mutableLongStateOf(0L) }

    // 焦点管理
    val firstSlotFocusRequester = remember { FocusRequester() }
    val firstRecentFocusRequester = remember { FocusRequester() }

    // 收集栏位数据
    LaunchedEffect(slotsFlow) {
        slots = slotsFlow
    }

    // 确保栏位数量正确
    LaunchedEffect(slotCount) {
        HomeSlotRepository.ensureSlotCount(slotCount)
    }

    // 收集最近播放数据
    LaunchedEffect(showRecent, recentCount) {
        if (showRecent) {
            try {
                val db = org.mz.mzdkplayer.data.local.AppDatabase.getDatabase(context)
                db.mediaHistoryDao().getVideoHistoryWithMetadata().collect { historyList ->
                    recentVideos = historyList.map { it.history }.take(recentCount)
                }
            } catch (e: Exception) {
                Log.w("ElderHomeScreen", "Failed to load recent videos", e)
                recentVideos = emptyList()
            }
        }
    }

    // 批量加载所有视频栏位的播放历史
    LaunchedEffect(slots) {
        val videoUris = slots.filter { it.slotType == "video" }.mapNotNull { it.videoUri }
        if (videoUris.isNotEmpty()) {
            try {
                val db = org.mz.mzdkplayer.data.local.AppDatabase.getDatabase(context)
                val hMap = mutableMapOf<String, MediaHistoryEntity>()
                for (uri in videoUris) {
                    db.mediaHistoryDao().getHistoryByUri(uri)?.let { hMap[uri] = it }
                }
                videoHistoryMap = hMap
            } catch (e: Exception) {
                Log.w("ElderHomeScreen", "Failed to load video slot history", e)
                videoHistoryMap = emptyMap()
            }
        }
    }

    // 初始焦点：优先聚焦最近播放行, 其次栏位网格; 一旦焦点已设置不再跳转, 避免异步加载导致焦点跳跃
    var focusDone by remember { mutableStateOf(false) }
    LaunchedEffect(slots, recentVideos) {
        if (!focusDone) {
            if (showRecent && recentVideos.isNotEmpty()) {
                firstRecentFocusRequester.requestFocus()
                focusDone = true
            } else if (slots.isNotEmpty()) {
                firstSlotFocusRequester.requestFocus()
                focusDone = true
            }
        }
    }

    // 观察文件选择器返回的结果（方案 C：TV 遥控器直接配置 folder/video 栏位）
    val pickerResult by SlotPickerResultHolder.result.collectAsState()
    LaunchedEffect(pickerResult) {
        val result = pickerResult ?: return@LaunchedEffect
        SlotPickerResultHolder.consumeResult()
        // 使用 slotsFlow（始终为最新数据库值）而非 slots（可能存在一帧延迟）
        val targetSlot = slotsFlow.find { it.id == result.slotId } ?: return@LaunchedEffect
        scope.launch {
            when (result.slotType) {
                "video" -> {
                    HomeSlotRepository.updateSlot(targetSlot.copy(
                        slotType = "video",
                        label = result.fileName,
                        videoUri = result.uri,
                        videoDataSourceType = result.dataSourceType,
                        videoConnectionName = result.connectionName,
                        videoFileName = result.fileName
                    ))
                }
                "folder" -> {
                    val updatedSlot = targetSlot.copy(
                        slotType = "folder",
                        label = result.fileName,
                        folderUri = result.uri,
                        folderDataSourceType = result.dataSourceType,
                        folderConnectionName = result.connectionName
                    )
                    HomeSlotRepository.updateSlot(updatedSlot)
                    // 扫描文件夹内的视频
                    scanFolderVideos(updatedSlot)
                }
            }
        }
    }

    // 返回键双击退出应用
    var backPressedTime by remember { mutableLongStateOf(0L) }
    var showExitToast by remember { mutableStateOf(false) }
    BackHandler {
        val now = System.currentTimeMillis()
        if (now - backPressedTime < 2000) {
            (context as? Activity)?.finish()
        } else {
            backPressedTime = now
            showExitToast = true
        }
    }

    // 退出提示自动消失
    LaunchedEffect(showExitToast) {
        if (showExitToast) {
            delay(2000)
            showExitToast = false
        }
    }

    // 全局按键监听：长按下键 5 秒触发 PIN
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                // 使用 onPreviewKeyEvent (捕获阶段) 确保 ACTION_UP 一定被处理
                // 即使子节点消费了下键事件用于焦点移动
                when (keyEvent.nativeKeyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (downKeyHoldStart == 0L) {
                                downKeyHoldStart = System.currentTimeMillis()
                            }
                        }
                        false // 不消费, 让事件继续派发
                    }
                    KeyEvent.ACTION_UP -> {
                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            downKeyHoldStart = 0L
                        }
                        false // 不消费, 让事件继续派发
                    }
                    else -> false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // 顶部栏：全部应用入口
            ElderTopBar(
                onAllAppsClick = { navController.navigate("ElderAppListPage") },
                onWebConfigClick = { showWebConfigDialog = true }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 最近播放行
            if (showRecent && recentVideos.isNotEmpty()) {
                RecentVideoRow(
                    recentVideos = recentVideos,
                    firstFocusRequester = firstRecentFocusRequester,
                    navController = navController
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 栏位网格
            val (_, cols) = slotGridConfig(slotCount)
            SlotGrid(
                slots = slots,
                cols = cols,
                videoHistoryMap = videoHistoryMap,
                firstFocusRequester = firstSlotFocusRequester,
                onSlotClick = { slot -> handleSlotClick(slot, navController) },
                onEmptySlotClick = { slot ->
                    configSlot = slot
                    showSlotConfigDialog = true
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // 退出提示
    if (showExitToast) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(ElderDimens.buttonCorner),
                colors = SurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.85f)
                ),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.elder_press_back_again_to_exit),
                    color = ElderColors.textPrimary,
                    fontSize = ElderDimens.bodyFontSize,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }

    // 长按下键切换模式提示（底部小字）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = stringResource(R.string.elder_mode_switch_hint),
            color = ElderColors.textPrimary.copy(alpha = 0.3f),
            fontSize = 12.sp
        )
    }

    // 长按下键检测
    LaunchedEffect(downKeyHoldStart) {
        if (downKeyHoldStart > 0L) {
            delay(5000)
            if (downKeyHoldStart > 0L) {
                showPinDialog = true
                downKeyHoldStart = 0L
            }
        }
    }

    // PIN 对话框
    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onPinVerified = {
                showPinDialog = false
                ModeManager.switchToStandardMode(it)
                // Bug 1 修复: recreate() 会保留 NavHost 的 savedInstanceState,
                // 导致重建后仍停留在 ElderHomePage。改为直接导航并清除返回栈。
                navController.navigate("MainPage") {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = true
                    }
                }
            }
        )
    }

    // 空栏位配置对话框
    if (showSlotConfigDialog && configSlot != null) {
        SlotConfigDialog(
            slot = configSlot!!,
            navController = navController,
            onDismiss = {
                showSlotConfigDialog = false
                configSlot = null
            },
            onConfigured = {
                showSlotConfigDialog = false
                configSlot = null
            }
        )
    }

    // Web 配置对话框
    if (showWebConfigDialog) {
        WebConfigDialog(
            enabled = webConfigEnabled,
            onToggle = {
                webConfigEnabled = it
                SettingsRepository.webConfigEnabled = it
            },
            onDismiss = { showWebConfigDialog = false }
        )
    }
}

// ==================== 顶部栏 ====================

@Composable
private fun ElderTopBar(
    onAllAppsClick: () -> Unit,
    onWebConfigClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.elder_home),
            color = ElderColors.textPrimary,
            fontSize = ElderDimens.titleFontSize,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElderTopBarButton(
                text = "Web 配置",
                onClick = onWebConfigClick,
                icon = painterResource(id = R.drawable.baseline_settings_24)
            )
            ElderTopBarButton(
                text = stringResource(R.string.elder_all_apps),
                onClick = onAllAppsClick,
                icon = painterResource(id = R.drawable.baseline_apps_24)
            )
        }
    }
}

// ==================== 最近播放行 ====================

@Composable
private fun RecentVideoRow(
    recentVideos: List<MediaHistoryEntity>,
    firstFocusRequester: FocusRequester,
    navController: NavHostController
) {
    Column {
        Text(
            text = stringResource(R.string.elder_recent),
            color = ElderColors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(recentVideos, key = { _, history -> history.mediaUri }) { index, history ->
                RecentVideoCard(
                    history = history,
                    isFirst = index == 0,
                    firstFocusRequester = firstFocusRequester,
                    onClick = {
                        val encodedUri = URLEncoder.encode(history.mediaUri, "UTF-8")
                        val encodedFileName = URLEncoder.encode(history.fileName, "UTF-8")
                        // 统一映射 protocolName → dataSourceType
                        val dataSourceType = when (history.protocolName) {
                            "本地文件" -> "LOCAL"
                            else -> history.protocolName
                        }
                        navController.navigate(
                            "VideoPlayer/$encodedUri/$dataSourceType/$encodedFileName/${URLEncoder.encode(history.connectionName, "UTF-8")}"
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun RecentVideoCard(
    history: MediaHistoryEntity,
    isFirst: Boolean,
    firstFocusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(200),
        label = "recentScale"
    )

    val percentage = history.getPlaybackPercentage() / 100f

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(160.dp)
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
        scale = CardDefaults.scale(focusedScale = 1f), // 已通过 Modifier.scale 控制
        interactionSource = interactionSource
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 文件名
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = history.fileName,
                    color = ElderColors.textPrimary,
                    fontSize = ElderDimens.captionFontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 进度条
            if (percentage > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
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

// ==================== 栏位网格 ====================

@Composable
private fun SlotGrid(
    slots: List<HomeSlotEntity>,
    cols: Int,
    videoHistoryMap: Map<String, MediaHistoryEntity>,
    firstFocusRequester: FocusRequester,
    onSlotClick: (HomeSlotEntity) -> Unit,
    onEmptySlotClick: (HomeSlotEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (slots.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.elder_loading), color = ElderColors.textSecondary, fontSize = ElderDimens.bodyFontSize)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(slots, key = { it.id }) { slot ->
            SlotCard(
                slot = slot,
                history = slot.videoUri?.let { videoHistoryMap[it] },
                isFirst = slot.id == slots.firstOrNull()?.id,
                firstFocusRequester = firstFocusRequester,
                onClick = {
                    if (slot.slotType == "empty") {
                        onEmptySlotClick(slot)
                    } else {
                        onSlotClick(slot)
                    }
                }
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun handleSlotClick(
    slot: HomeSlotEntity,
    navController: NavHostController
) {
    val context = org.mz.mzdkplayer.MzDkPlayerApplication.context
    when (slot.slotType) {
        "folder" -> {
            navController.navigate("ElderFolderPage/${slot.id}")
        }
        "video" -> {
            val encodedUri = URLEncoder.encode(slot.videoUri ?: return, "UTF-8")
            val encodedFileName = URLEncoder.encode(slot.videoFileName ?: "", "UTF-8")
            val dataSourceType = slot.videoDataSourceType ?: "SMB"
            val connectionName = slot.videoConnectionName ?: ""
            val encodedConnectionName = URLEncoder.encode(connectionName, "UTF-8")
            navController.navigate(
                "VideoPlayer/$encodedUri/$dataSourceType/$encodedFileName/$encodedConnectionName"
            )
        }
        "app" -> {
            val packageName = slot.appPackageName ?: return
            val success = AppLauncherHelper.launchApp(packageName)
            if (!success) {
                Toast.makeText(
                    context,
                    context.getString(R.string.elder_app_launch_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        "empty" -> { /* 空栏位无操作 */ }
    }
}

/**
 * 扫描文件夹内的视频并更新数据库
 * 支持 FILE 和 SMB 两种数据源类型
 */
private suspend fun scanFolderVideos(slot: HomeSlotEntity) {
    try {
        when (slot.folderDataSourceType) {
            "FILE" -> {
                val folderPath = slot.folderUri ?: return
                val dir = java.io.File(folderPath)
                if (!dir.exists() || !dir.isDirectory) return

                val videoFiles = dir.listFiles()?.filter {
                    it.isFile && org.mz.mzdkplayer.tool.Tools.containsVideoFormat(
                        org.mz.mzdkplayer.tool.Tools.extractFileExtension(it.name)
                    )
                } ?: emptyList()

                val newVideos = videoFiles.mapIndexed { index, file ->
                    org.mz.mzdkplayer.data.local.FolderVideoEntity(
                        folderSlotId = slot.id,
                        sortOrder = index,
                        videoUri = file.absolutePath,
                        dataSourceType = "FILE",
                        fileName = file.name,
                        connectionName = ""
                    )
                }
                FolderVideoRepository.scanFolder(slot) { newVideos }
            }
            "SMB" -> {
                val connName = slot.folderConnectionName ?: return
                val folderPath = slot.folderUri ?: return
                val context = org.mz.mzdkplayer.MzDkPlayerApplication.context
                val conn = org.mz.mzdkplayer.data.repository.SMBConnectionRepository(context)
                    .getConnections().find { it.name == connName } ?: return

                val pathInfo = org.mz.mzdkplayer.tool.SmbUtils.parseSMBPath(folderPath)
                val shareName = conn.shareName?.takeIf { it.isNotEmpty() } ?: pathInfo.share

                val entries = org.mz.mzdkplayer.tool.SmbUtils.listSmbDirectory(
                    host = conn.ip ?: pathInfo.server,
                    shareName = shareName,
                    path = pathInfo.path,
                    username = conn.username ?: "guest",
                    password = conn.password ?: ""
                )

                val videoFiles = entries.filter {
                    !it.isDirectory && org.mz.mzdkplayer.tool.Tools.containsVideoFormat(
                        org.mz.mzdkplayer.tool.Tools.extractFileExtension(it.name)
                    )
                }
                val newVideos = videoFiles.mapIndexed { index, entry ->
                    val videoUri = "smb://${conn.ip}/$shareName${entry.path}"
                    org.mz.mzdkplayer.data.local.FolderVideoEntity(
                        folderSlotId = slot.id,
                        sortOrder = index,
                        videoUri = videoUri,
                        dataSourceType = "SMB",
                        fileName = entry.name,
                        connectionName = connName
                    )
                }
                FolderVideoRepository.scanFolder(slot) { newVideos }
            }
        }
    } catch (e: Exception) {
        Log.w("ElderHomeScreen", "Failed to scan folder videos", e)
    }
}

// ==================== 栏位卡片 ====================

@Composable
private fun SlotCard(
    slot: HomeSlotEntity,
    history: MediaHistoryEntity?,
    isFirst: Boolean,
    firstFocusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(200),
        label = "slotScale"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1.2f)
            .scale(scale)
            .then(if (isFirst) Modifier.focusRequester(firstFocusRequester) else Modifier),
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
            // 缩略图/图标区域
            SlotThumbnail(slot, history)

            // 焦点时底部半透明信息栏
            if (isFocused && slot.label.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Color.Black.copy(alpha = 0.75f),
                            RoundedCornerShape(bottomStart = ElderDimens.cardCornerLarge, bottomEnd = ElderDimens.cardCornerLarge)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = slot.label,
                        color = ElderColors.textPrimary,
                        fontSize = ElderDimens.captionFontSize,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotThumbnail(slot: HomeSlotEntity, history: MediaHistoryEntity?) {
    when (slot.slotType) {
        "folder" -> FolderSlotThumbnail(slot)
        "video" -> VideoSlotThumbnail(slot, history)
        "app" -> AppSlotThumbnail(slot)
        "empty" -> EmptySlotThumbnail()
    }
}

@Composable
private fun FolderSlotThumbnail(slot: HomeSlotEntity) {
    val folderText = stringResource(R.string.elder_folder)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 自定义缩略图
        if (!slot.customThumbnailPath.isNullOrEmpty() && File(slot.customThumbnailPath).exists()) {
            AsyncImage(
                model = slot.customThumbnailPath,
                contentDescription = slot.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // 默认文件夹图标
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_folder_24),
                    contentDescription = folderText,
                    modifier = Modifier.size(48.dp),
                    tint = ElderColors.accent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = slot.label.ifEmpty { folderText },
                    color = ElderColors.textPrimary,
                    fontSize = ElderDimens.bodyFontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoSlotThumbnail(slot: HomeSlotEntity, history: MediaHistoryEntity?) {
    val videoText = stringResource(R.string.elder_video)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 自定义缩略图
        if (!slot.customThumbnailPath.isNullOrEmpty() && File(slot.customThumbnailPath).exists()) {
            AsyncImage(
                model = slot.customThumbnailPath,
                contentDescription = slot.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // 默认视频图标
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_movie_24),
                    contentDescription = videoText,
                    modifier = Modifier.size(48.dp),
                    tint = ElderColors.accent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = slot.label.ifEmpty { slot.videoFileName ?: videoText },
                    color = ElderColors.textPrimary,
                    fontSize = ElderDimens.bodyFontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // 播放进度条（使用传入的缓存历史数据）
        if (history != null && history.mediaDuration > 0) {
            val percentage = (history.playbackPosition.toFloat() / history.mediaDuration).coerceIn(0f, 1f)
            if (percentage > 0f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
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

@Composable
private fun AppSlotThumbnail(slot: HomeSlotEntity) {
    val packageName = slot.appPackageName ?: ""
    val appLabel = remember(packageName) {
        if (packageName.isNotEmpty()) AppLauncherHelper.getAppLabel(packageName) else ""
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (packageName.isNotEmpty()) {
                val imageBitmap = remember(packageName) {
                    val drawable = AppLauncherHelper.getAppIcon(packageName)
                    AppLauncherHelper.drawableToImageBitmap(drawable)
                }
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = appLabel,
                        modifier = Modifier.size(56.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_play_arrow_24),
                        contentDescription = "App",
                        modifier = Modifier.size(48.dp),
                        tint = ElderColors.accent
                    )
                }
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_play_arrow_24),
                    contentDescription = "App",
                    modifier = Modifier.size(48.dp),
                    tint = ElderColors.accent
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = slot.label.ifEmpty { appLabel },
                color = ElderColors.textPrimary,
                fontSize = ElderDimens.bodyFontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun EmptySlotThumbnail() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElderColors.background, RoundedCornerShape(ElderDimens.cardCornerLarge)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            color = ElderColors.textPrimary.copy(alpha = 0.3f),
            fontSize = 40.sp,
            fontWeight = FontWeight.Light
        )
    }
}

// ==================== PIN 对话框 ====================

@Composable
private fun PinDialog(
    onDismiss: () -> Unit,
    onPinVerified: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val isPinSet = ModeManager.isPinSet
    var isConfirming by remember { mutableStateOf(false) }
    var attemptCount by remember { mutableStateOf(0) }
    var isLocked by remember { mutableStateOf(false) }
    var focusResetTrigger by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val maxAttempts = 5

    // 焦点请求
    val pinFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        pinFocusRequester.requestFocus()
    }

    // 错误后重置焦点到 "1" 键
    LaunchedEffect(focusResetTrigger) {
        if (focusResetTrigger > 0) {
            pinFocusRequester.requestFocus()
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElderColors.overlayDim),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(ElderDimens.dialogCorner),
            colors = SurfaceDefaults.colors(
                containerColor = ElderColors.cardBackground
            ),
            modifier = Modifier
                .width(400.dp)
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(ElderDimens.dialogPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.elder_set_pin).takeIf { !isPinSet }
                        ?: stringResource(R.string.elder_enter_pin),
                    color = ElderColors.textPrimary,
                    fontSize = ElderDimens.dialogTitleFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isPinSet -> stringResource(R.string.elder_enter_pin_hint)
                        isConfirming -> stringResource(R.string.elder_confirm_pin_hint)
                        else -> stringResource(R.string.elder_set_pin_hint)
                    },
                    color = ElderColors.textSecondary,
                    fontSize = ElderDimens.dialogSubtitleFontSize,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(ElderDimens.dialogSpacing))

                // PIN 输入显示
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    ElderColors.focusBackground,
                                    RoundedCornerShape(ElderDimens.inputBoxCorner)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < pin.length) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(ElderColors.textPrimary, RoundedCornerShape(6.dp))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ElderDimens.dialogSpacing))

                // 数字键盘
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
                )

                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.width(72.dp).height(56.dp))
                            } else {
                                PinKeyButton(
                                    label = key,
                                    focusRequester = if (key == "1") pinFocusRequester else null,
                                    onClick = {
                                        when (key) {
                                            "⌫" -> {
                                                if (pin.isNotEmpty()) {
                                                    pin = pin.dropLast(1)
                                                    error = ""
                                                }
                                            }
                                            else -> {
                                                if (pin.length < 4 && !isLocked) {
                                                    pin += key
                                                    error = ""
                                                    if (pin.length == 4) {
                                                        if (!isPinSet) {
                                                            // 首次设置 PIN：需要二次确认
                                                            if (!isConfirming) {
                                                                firstPin = pin
                                                                pin = ""
                                                                isConfirming = true
                                                                focusResetTrigger++
                                                            } else {
                                                                // 确认输入
                                                                if (pin == firstPin) {
                                                                    onPinVerified(pin)
                                                                } else {
                                                                    error = context.getString(R.string.elder_pin_mismatch)
                                                                    pin = ""
                                                                    firstPin = ""
                                                                    isConfirming = false
                                                                    focusResetTrigger++
                                                                }
                                                            }
                                                        } else {
                                                            // 验证已有 PIN
                                                            if (ModeManager.verifyPin(pin)) {
                                                                onPinVerified(pin)
                                                            } else {
                                                                attemptCount++
                                                                if (attemptCount >= maxAttempts) {
                                                                    isLocked = true
                                                                    error = context.getString(R.string.elder_pin_too_many_attempts)
                                                                } else {
                                                                    error = context.getString(R.string.elder_pin_error)
                                                                }
                                                                pin = ""
                                                                focusResetTrigger++
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (error.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = ElderColors.error,
                        fontSize = ElderDimens.captionFontSize,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(ElderDimens.dialogSpacing))

                // 取消按钮：宽度与数字键盘对齐（3*72 + 2*12 = 240dp）
                ElderButton(
                    text = stringResource(R.string.elder_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.width(240.dp)
                )
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(ElderDimens.buttonCorner)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) ElderColors.textPrimary else ElderColors.focusBackground,
            contentColor = if (isFocused) Color.Black else ElderColors.textPrimary,
            focusedContainerColor = ElderColors.textPrimary,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.size(72.dp, 56.dp).then(focusModifier).then(modifier),
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = ElderDimens.buttonFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==================== 空栏位配置对话框 ====================

@Composable
private fun SlotConfigDialog(
    slot: HomeSlotEntity,
    navController: NavHostController,
    onDismiss: () -> Unit,
    onConfigured: () -> Unit
) {
    var selectedType by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<org.mz.mzdkplayer.tool.AppInfo>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val firstButtonFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }
    val localButtonFocusRequester = remember { FocusRequester() }

    BackHandler { onDismiss() }

    // 加载 App 列表
    LaunchedEffect(selectedType) {
        if (selectedType == "app" && apps.isEmpty() && !loadingApps) {
            loadingApps = true
            apps = withContext(kotlinx.coroutines.Dispatchers.IO) {
                AppLauncherHelper.getLaunchableApps()
            }
            loadingApps = false
        }
    }

    // Bug 2 修复: 对话框打开时立即聚焦第一个类型按钮;
    // 选择类型后聚焦对应元素 (app→列表首项, folder/video→返回按钮)
    LaunchedEffect(selectedType, apps) {
        withFrameNanos { }
        when {
            selectedType == null -> {
                firstButtonFocusRequester.requestFocus()
            }
            selectedType == "app" && apps.isNotEmpty() -> {
                firstButtonFocusRequester.requestFocus()
            }
            selectedType == "folder" || selectedType == "video" -> {
                localButtonFocusRequester.requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElderColors.overlayDim),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(ElderDimens.dialogCorner),
            colors = SurfaceDefaults.colors(
                containerColor = ElderColors.cardBackground
            ),
            modifier = Modifier
                .width(500.dp)
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(ElderDimens.dialogPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.elder_configure_slot),
                    color = ElderColors.textPrimary,
                    fontSize = ElderDimens.dialogTitleFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.elder_select_slot_type),
                    color = ElderColors.textSecondary,
                    fontSize = ElderDimens.dialogSubtitleFontSize,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(ElderDimens.dialogSpacing))

                when (selectedType) {
                    null -> {
                        // 类型选择
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SlotTypeButton(
                                label = stringResource(R.string.elder_folder),
                                icon = painterResource(id = R.drawable.baseline_folder_24),
                                focusRequester = firstButtonFocusRequester,
                                onClick = { selectedType = "folder" }
                            )
                            SlotTypeButton(
                                label = stringResource(R.string.elder_video),
                                icon = painterResource(id = R.drawable.baseline_movie_24),
                                focusRequester = null,
                                onClick = { selectedType = "video" }
                            )
                            SlotTypeButton(
                                label = stringResource(R.string.elder_app),
                                icon = painterResource(id = R.drawable.baseline_play_arrow_24),
                                focusRequester = null,
                                onClick = { selectedType = "app" }
                            )
                        }
                    }
                    "app" -> {
                        if (loadingApps) {
                            Text(
                                text = stringResource(R.string.elder_loading_apps),
                                color = ElderColors.textSecondary,
                                fontSize = ElderDimens.bodyFontSize
                            )
                        } else if (apps.isEmpty()) {
                            Text(
                                text = stringResource(R.string.elder_no_apps_found),
                                color = ElderColors.textSecondary,
                                fontSize = ElderDimens.bodyFontSize
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.elder_select_app_count, apps.size),
                                color = ElderColors.textPrimary,
                                fontSize = ElderDimens.bodyFontSize,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            // App 列表
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.height(300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                                    AppListItem(
                                        app = app,
                                        isFirst = index == 0,
                                        firstFocusRequester = firstButtonFocusRequester,
                                        onClick = {
                                            scope.launch {
                                                HomeSlotRepository.updateSlot(slot.copy(
                                                    slotType = "app",
                                                    label = app.label,
                                                    appPackageName = app.packageName
                                                ))
                                                onConfigured()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    "folder", "video" -> {
                        // 方案 C: 支持通过 TV 遥控器直接选择本地或 SMB 文件
                        val typeNameRes = if (selectedType == "folder") R.string.elder_folder else R.string.elder_video
                        val typeName = stringResource(typeNameRes)
                        Text(
                            text = stringResource(R.string.elder_select_data_source, typeName),
                            color = ElderColors.textPrimary,
                            fontSize = ElderDimens.bodyFontSize,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(ElderDimens.dialogSpacing))
                        // 数据源选择：本地 / SMB
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SlotTypeButton(
                                label = stringResource(R.string.elder_local_storage),
                                icon = painterResource(id = R.drawable.baseline_folder_24),
                                focusRequester = localButtonFocusRequester,
                                onClick = {
                                    onDismiss()
                                    navController.navigate("ElderLocalFilePicker/${slot.id}/$selectedType")
                                }
                            )
                            SlotTypeButton(
                                label = stringResource(R.string.elder_smb),
                                icon = painterResource(id = R.drawable.baseline_movie_24),
                                focusRequester = null,
                                onClick = {
                                    onDismiss()
                                    navController.navigate("ElderSmbPicker/${slot.id}/$selectedType")
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ElderDimens.dialogSpacing))

                // 底部按钮：始终居中对齐
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (selectedType != null) {
                        ElderButton(
                            text = stringResource(R.string.elder_back),
                            onClick = { selectedType = null },
                            focusRequester = backButtonFocusRequester,
                            modifier = Modifier.width(140.dp)
                        )
                    }
                    ElderButton(
                        text = stringResource(R.string.elder_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotTypeButton(
    label: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val modifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(ElderDimens.buttonCorner)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) ElderColors.textPrimary else ElderColors.focusBackground,
            contentColor = if (isFocused) Color.Black else ElderColors.textPrimary,
            focusedContainerColor = ElderColors.textPrimary,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.size(120.dp, 100.dp),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = ElderDimens.bodyFontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AppListItem(
    app: org.mz.mzdkplayer.tool.AppInfo,
    isFirst: Boolean,
    firstFocusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(ElderDimens.buttonCorner)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) ElderColors.textPrimary else ElderColors.focusBackground,
            contentColor = if (isFocused) Color.Black else ElderColors.textPrimary,
            focusedContainerColor = ElderColors.textPrimary,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFirst) Modifier.focusRequester(firstFocusRequester) else Modifier),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageBitmap = remember(app.packageName) {
                val drawable = AppLauncherHelper.getAppIcon(app.packageName)
                AppLauncherHelper.drawableToImageBitmap(drawable)
            }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = app.label,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_play_arrow_24),
                    contentDescription = app.label,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = app.label,
                    fontSize = ElderDimens.bodyFontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    fontSize = 12.sp,
                    color = if (isFocused) Color.Black.copy(alpha = 0.6f) else ElderColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

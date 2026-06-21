package org.mz.mzdkplayer.ui.elder

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.model.SMBConnection
import org.mz.mzdkplayer.data.repository.SMBConnectionRepository
import org.mz.mzdkplayer.tool.SmbUtils
import org.mz.mzdkplayer.tool.SmbDirEntry
import org.mz.mzdkplayer.tool.Tools

/**
 * 老人模式 SMB 文件选择器
 *
 * 流程：选择 SMB 连接 → 浏览目录 → 选择文件夹或视频文件
 *
 * @param slotId 栏位 ID
 * @param slotType "folder" 或 "video"
 * @param navController 导航控制器
 */
@Composable
fun ElderSmbPickerScreen(
    slotId: Int,
    slotType: String,
    navController: NavHostController
) {
    val context = LocalContext.current

    // 状态：null=选择连接, 非null=浏览目录
    var selectedConn by remember { mutableStateOf<SMBConnection?>(null) }
    var connections by remember { mutableStateOf<List<SMBConnection>>(emptyList()) }
    var currentPath by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<SmbDirEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val firstItemFocusRequester = remember { FocusRequester() }
    val selectButtonFocusRequester = remember { FocusRequester() }

    // 加载已保存的 SMB 连接
    LaunchedEffect(Unit) {
        connections = SMBConnectionRepository(context).getConnections()
    }

    // 加载目录内容
    LaunchedEffect(selectedConn, currentPath) {
        val conn = selectedConn ?: return@LaunchedEffect
        loading = true
        errorMsg = null
        try {
            entries = withContext(Dispatchers.IO) {
                SmbUtils.listSmbDirectory(
                    host = conn.ip ?: "",
                    shareName = conn.shareName ?: "",
                    path = currentPath,
                    username = conn.username ?: "guest",
                    password = conn.password ?: ""
                )
            }
        } catch (e: Exception) {
            Log.w("ElderSmbPicker", "Failed to list SMB directory", e)
            errorMsg = e.message ?: "Unknown error"
            entries = emptyList()
        } finally {
            loading = false
        }
    }

    BackHandler {
        if (selectedConn != null) {
            // 在子目录中，返回上级
            if (currentPath != "/" && currentPath.isNotEmpty()) {
                val parent = currentPath.substringBeforeLast("/", "").let {
                    if (it.isEmpty()) "/" else it
                }
                currentPath = parent
            } else {
                // 在根目录，返回连接选择
                selectedConn = null
                entries = emptyList()
            }
        } else {
            navController.popBackStack()
        }
    }

    LaunchedEffect(entries, connections) {
        withFrameNanos { }
        // 仅在有列表项时请求焦点，避免 FocusRequester 未绑定导致崩溃
        val hasItems = if (selectedConn == null) connections.isNotEmpty() else entries.isNotEmpty()
        if (hasItems) {
            firstItemFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ElderColors.background)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedConn == null) stringResource(R.string.elder_select_smb_connection)
                       else if (slotType == "folder") stringResource(R.string.elder_select_folder)
                       else stringResource(R.string.elder_select_video),
                color = ElderColors.textPrimary,
                fontSize = ElderDimens.titleFontSize,
                fontWeight = FontWeight.Bold
            )
            ElderButton(
                text = stringResource(R.string.elder_back),
                onClick = { navController.popBackStack() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 当前路径/连接显示
        Text(
            text = if (selectedConn == null) ""
                  else "${selectedConn?.name} : ${selectedConn?.shareName}${currentPath}",
            color = ElderColors.textSecondary,
            fontSize = ElderDimens.captionFontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 内容区
        if (selectedConn == null) {
            // === 连接选择列表 ===
            if (connections.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.elder_no_smb_connections),
                        color = ElderColors.textSecondary,
                        fontSize = ElderDimens.bodyFontSize
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(connections, key = { _, c -> c.id ?: c.name ?: "" }) { index, conn ->
                        SmbConnectionItem(
                            connection = conn,
                            isFirst = index == 0,
                            firstFocusRequester = firstItemFocusRequester,
                            onClick = {
                                selectedConn = conn
                                currentPath = "/"
                            }
                        )
                    }
                }
            }
        } else {
            // === 文件浏览列表 ===
            when {
                loading -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.elder_loading),
                            color = ElderColors.textSecondary,
                            fontSize = ElderDimens.bodyFontSize
                        )
                    }
                }
                errorMsg != null -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.elder_smb_load_failed),
                                color = ElderColors.error,
                                fontSize = ElderDimens.bodyFontSize
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMsg ?: "",
                                color = ElderColors.textSecondary,
                                fontSize = ElderDimens.smallCaptionFontSize
                            )
                        }
                    }
                }
                entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.elder_no_files),
                            color = ElderColors.textSecondary,
                            fontSize = ElderDimens.bodyFontSize
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(entries, key = { _, e -> e.path }) { index, entry ->
                            SmbEntryItem(
                                entry = entry,
                                isFirst = index == 0,
                                firstFocusRequester = firstItemFocusRequester,
                                onClick = {
                                    if (entry.isDirectory) {
                                        currentPath = entry.path
                                    } else {
                                        // 选中视频文件（仅 video 类型）
                                        if (slotType == "video" &&
                                            Tools.containsVideoFormat(Tools.extractFileExtension(entry.name))
                                        ) {
                                            val conn = selectedConn!!
                                            val videoUri = "smb://${conn.ip}/${conn.shareName}${entry.path}"
                                            SlotPickerResultHolder.setResult(
                                                SlotPickerResultHolder.Result(
                                                    slotId = slotId,
                                                    slotType = "video",
                                                    dataSourceType = "SMB",
                                                    uri = videoUri,
                                                    fileName = entry.name,
                                                    connectionName = conn.name ?: ""
                                                )
                                            )
                                            navController.popBackStack()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // folder 类型：底部"选择此文件夹"按钮
            if (slotType == "folder" && !loading && errorMsg == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    ElderButton(
                        text = stringResource(R.string.elder_select_this_folder),
                        onClick = {
                            val conn = selectedConn!!
                            val folderUri = "smb://${conn.ip}/${conn.shareName}${currentPath}"
                            val folderName = currentPath.trimEnd('/').substringAfterLast('/').ifEmpty { conn.shareName ?: "" }
                            SlotPickerResultHolder.setResult(
                                SlotPickerResultHolder.Result(
                                    slotId = slotId,
                                    slotType = "folder",
                                    dataSourceType = "SMB",
                                    uri = folderUri,
                                    fileName = folderName,
                                    connectionName = conn.name ?: ""
                                )
                            )
                            navController.popBackStack()
                        },
                        focusRequester = selectButtonFocusRequester,
                        icon = painterResource(id = R.drawable.baseline_folder_24)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmbConnectionItem(
    connection: SMBConnection,
    isFirst: Boolean,
    firstFocusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val modifier = if (isFirst) Modifier.focusRequester(firstFocusRequester) else Modifier

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(ElderDimens.cardCornerSmall)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) ElderColors.focusBackground else ElderColors.cardBackground,
            contentColor = ElderColors.textPrimary,
            focusedContainerColor = ElderColors.focusBackground,
            focusedContentColor = ElderColors.textPrimary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth(),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_folder_24),
                contentDescription = null,
                tint = if (isFocused) ElderColors.accent else ElderColors.textSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connection.name ?: "Unknown",
                    color = ElderColors.textPrimary,
                    fontSize = ElderDimens.bodyFontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${connection.ip} / ${connection.shareName}",
                    color = ElderColors.textSecondary,
                    fontSize = ElderDimens.smallCaptionFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.arrowright24dp),
                contentDescription = null,
                tint = ElderColors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SmbEntryItem(
    entry: SmbDirEntry,
    isFirst: Boolean,
    firstFocusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val modifier = if (isFirst) Modifier.focusRequester(firstFocusRequester) else Modifier

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(ElderDimens.cardCornerSmall)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) ElderColors.focusBackground else ElderColors.cardBackground,
            contentColor = ElderColors.textPrimary,
            focusedContainerColor = ElderColors.focusBackground,
            focusedContentColor = ElderColors.textPrimary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth(),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    id = if (entry.isDirectory) R.drawable.baseline_folder_24
                         else R.drawable.baseline_movie_24
                ),
                contentDescription = null,
                tint = if (isFocused) ElderColors.accent else ElderColors.textSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = entry.name,
                color = ElderColors.textPrimary,
                fontSize = ElderDimens.bodyFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (entry.isDirectory) {
                Icon(
                    painter = painterResource(id = R.drawable.arrowright24dp),
                    contentDescription = null,
                    tint = ElderColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


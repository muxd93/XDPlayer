package org.mz.mzdkplayer.ui.screen.smbfile

import android.annotation.SuppressLint
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.tool.SmbDiscoveryScanner
import org.mz.mzdkplayer.tool.Tools.toBase64
import org.mz.mzdkplayer.ui.screen.common.CirCleIconButton
import org.mz.mzdkplayer.ui.screen.common.ConOpPanel
import org.mz.mzdkplayer.ui.screen.common.ConnectionCard
import org.mz.mzdkplayer.ui.screen.common.ConnectionCardInfo
import org.mz.mzdkplayer.ui.screen.common.ConnectionListEmpty
import org.mz.mzdkplayer.ui.screen.common.ConnectionListTitle
import org.mz.mzdkplayer.ui.screen.common.DeleteConfirmDialog
import org.mz.mzdkplayer.ui.screen.common.FCLMainTitle
import org.mz.mzdkplayer.ui.screen.vm.SMBListViewModel
import org.mz.mzdkplayer.ui.screen.vm.SmbScanViewModel
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun SMBConListScreen(
    mainNavController: NavHostController,
    smbListViewModel: SMBListViewModel = viewModel()
) {

    val scanViewModel: SmbScanViewModel = viewModel()
    val connections by smbListViewModel.connections.collectAsState()
    val isOPanelShow by smbListViewModel.isOPanelShow.collectAsState()
    val selectedIndex by smbListViewModel.selectedIndex.collectAsState()
    val selectedId by smbListViewModel.selectedId.collectAsState()
    val listState = rememberLazyListState()

    // 扫描状态
    val scanState by scanViewModel.scanState.collectAsState()
    val discoveredHosts by scanViewModel.discoveredHosts.collectAsState()
    val savedConnections by scanViewModel.savedConnections.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(isOPanelShow) {
        Log.d("isOPanelShow", isOPanelShow.toString())
    }
    val panelFocusRequester = remember { FocusRequester() }
    val listFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isOPanelShow) {
        if (isOPanelShow) {
            delay(350.milliseconds)
            panelFocusRequester.requestFocus()
        } else {
            if (selectedIndex != -1) {
                listState.animateScrollToItem(selectedIndex)
            }
            while (listState.isScrollInProgress) {
                delay(200.milliseconds)
            }
            panelFocusRequester.freeFocus()
            listFocusRequester.requestFocus()
        }
    }

    BackHandler(enabled = isOPanelShow) {
        smbListViewModel.closeOPanel()
        panelFocusRequester.freeFocus()
    }

    // 智能排序后的扫描结果
    val sortedHosts = remember(discoveredHosts, savedConnections) {
        scanViewModel.getSortedHosts()
    }

    val isScanning = scanState is SmbDiscoveryScanner.ScanState.Scanning
    val hasContent = connections.isNotEmpty() || sortedHosts.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            // 标题
            FCLMainTitle(mainNavController = mainNavController, stringResource(R.string.ui_label_smb_file_sharing), "SMBConScreen")

            // ====== 内容区域 ======
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // --- 扫描按钮行 ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CirCleIconButton(
                        icon = painterResource(R.drawable.baseline_search_24),
                        tooltip = if (isScanning) stringResource(R.string.ui_label_stop_scan)
                                  else stringResource(R.string.ui_label_scan_lan),
                        onClick = {
                            if (isScanning) scanViewModel.stopScan()
                            else scanViewModel.startScan()
                        }
                    )
                    // 扫描状态文本
                    val scanStatusText = when (val state = scanState) {
                        is SmbDiscoveryScanner.ScanState.Scanning ->
                            if (state.totalCount > 0) {
                                "已发现 ${state.foundCount} 台，扫描进度 ${state.scannedCount}/${state.totalCount}"
                            } else {
                                stringResource(R.string.ui_label_discovered_devices, state.foundCount)
                            }
                        is SmbDiscoveryScanner.ScanState.Finished ->
                            if (sortedHosts.isEmpty()) stringResource(R.string.ui_label_no_smb_found)
                            else stringResource(R.string.ui_label_scan_complete)
                        is SmbDiscoveryScanner.ScanState.Error ->
                            stringResource(R.string.ui_label_scan_failed, state.message)
                        else -> ""
                    }
                    if (scanStatusText.isNotEmpty()) {
                        Text(
                            text = scanStatusText,
                            color = Color(0xFF999999),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 主内容区 ---
                if (!hasContent && scanState is SmbDiscoveryScanner.ScanState.Idle) {
                    ConnectionListEmpty("SMB")
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(listFocusRequester),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // === 扫描结果区 ===
                        if (sortedHosts.isNotEmpty()) {
                            item {
                                Text(
                                    text = "── ${stringResource(R.string.ui_label_discovered_devices, sortedHosts.size)} ──",
                                    color = Color(0xFF888888),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(sortedHosts) { host ->
                                DiscoveredHostCard(
                                    host = host,
                                    isSaved = savedConnections.any { it.ip == host.ip },
                                    onClick = {
                                        // 一键连接逻辑
                                        val savedConn = scanViewModel.matchSavedConnection(host)
                                        if (savedConn != null) {
                                            // 匹配已保存连接 → 直接进入文件列表
                                            val smbUri = "smb://${savedConn.username}:${savedConn.password}@${savedConn.ip}/${savedConn.shareName}/"
                                            val safePath = smbUri.toBase64()
                                            val safeConnectionName = (savedConn.name ?: host.ip).toBase64()
                                            mainNavController.navigate("SMBFileListScreen/$safePath/$safeConnectionName")
                                        } else {
                                            // 新主机 → 跳转到连接配置页, 预填 IP
                                            mainNavController.navigate(
                                                "SMBConScreen?ip=${host.ip.toBase64()}"
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // === 已保存连接区 ===
                        if (connections.isNotEmpty()) {
                            item {
                                ConnectionListTitle(connections.size)
                            }
                            itemsIndexed(connections) { index, conn ->
                                ConnectionCard(
                                    index = index,
                                    modifier = Modifier.onKeyEvent { keyEvent ->
                                        if (keyEvent.key == Key.Menu) {
                                            if (!isOPanelShow) {
                                                smbListViewModel.openOPlane()
                                                smbListViewModel.setSelectedIndex(index)
                                                smbListViewModel.setSelectedId(conn.id)
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                    connectionCardInfo = ConnectionCardInfo(
                                        name = conn.name ?: stringResource(R.string.ui_label_unknown),
                                        address = conn.ip ?: stringResource(R.string.ui_label_unknown),
                                        shareName = conn.shareName ?: stringResource(R.string.ui_label_unknown),
                                        username = conn.username ?: stringResource(R.string.ui_label_unknown),
                                    ),
                                    onClick = {
                                        Log.d("SMBConListScreen", conn.name.toString())
                                        // 统一使用 Base64 编码
                                        val smbUri = "smb://${conn.username}:${conn.password}@${conn.ip}/${conn.shareName}/"
                                        val safePath = smbUri.toBase64()
                                        val safeConnectionName = (conn.name ?: "unknown").toBase64()
                                        mainNavController.navigate("SMBFileListScreen/$safePath/$safeConnectionName")
                                        smbListViewModel.setSelectedIndex(index)
                                        smbListViewModel.setSelectedId(conn.id)
                                    },
                                    onLogClick = {
                                        Log.d("SMBClick", "SMBClickL")
                                        smbListViewModel.openOPlane()
                                        smbListViewModel.setSelectedIndex(index)
                                        smbListViewModel.setSelectedId(conn.id)
                                    },
                                    onDelete = { },
                                    isSelected = smbListViewModel.selectedIndex.collectAsState().value == index && !isOPanelShow,
                                    isOPanelShow = isOPanelShow,
                                    selectedIndex = smbListViewModel.selectedIndex.value
                                )
                            }
                        }
                    }
                }
            }
        }

        // 操作面板遮罩层: 可聚焦并消费方向键, 阻止焦点逃逸到底层列表
        if (isOPanelShow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .focusable()
                    .onKeyEvent { true }
            )
        }
        // 操作面板（右侧弹出）
        ConOpPanel(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 30.dp),
            isOPanelShow,
            panelFocusRequester,
            onClickForDel = {
                showDeleteDialog = true
                smbListViewModel.closeOPanel()
            },
            onClickForCancel = {
                smbListViewModel.closeOPanel()
            })

        // 删除确认弹窗
        if (showDeleteDialog) {
            DeleteConfirmDialog(
                title = "删除连接",
                message = "确定要删除这个 SMB 连接吗？",
                onConfirm = {
                    Log.d("selectedId", selectedId)
                    smbListViewModel.deleteConnection(selectedId)
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
    }
}

/**
 * 发现的 SMB 主机卡片
 */
@Composable
private fun DiscoveredHostCard(
    host: SmbDiscoveryScanner.DiscoveredSmbHost,
    isSaved: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF2A2A4E) else Color(0xFF1E1E1E))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) Color.White else Color(0xFF333333),
                RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧: 主机信息
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = host.hostname ?: host.ip,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 认证状态标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (host.requiresAuth) Color(0x33FF9800)
                            else Color(0x334CAF50)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (host.requiresAuth) stringResource(R.string.ui_label_requires_auth)
                               else stringResource(R.string.ui_label_no_auth),
                        color = if (host.requiresAuth) Color(0xFFFF9800)
                                else Color(0xFF4CAF50),
                        fontSize = 11.sp
                    )
                }
                // 已保存标签
                if (isSaved) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33666666))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_label_saved),
                            color = Color(0xFF999999),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = host.ip,
                    color = Color(0xFF999999),
                    fontSize = 13.sp
                )
                if (host.hostname != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "· ${host.discoverySource}",
                        color = Color(0xFF666666),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ui_label_response_time, host.responseTimeMs),
                    color = Color(0xFF666666),
                    fontSize = 11.sp
                )
            }
        }
        // 右侧: 连接箭头
        Icon(
            painter = painterResource(R.drawable.arrowright24dp),
            contentDescription = stringResource(R.string.ui_label_one_click_connect),
            tint = Color(0xFF888888),
            modifier = Modifier.size(20.dp)
        )
    }
}

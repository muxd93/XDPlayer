package org.mz.mzdkplayer.ui.screen.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.tv.material3.*
import org.mz.mzdkplayer.di.RepositoryProvider
import org.mz.mzdkplayer.tool.viewModelWithFactory
import org.mz.mzdkplayer.ui.screen.common.DashboardTopBarItemIndicator
import org.mz.mzdkplayer.ui.screen.vm.MediaHistoryViewModel
import org.mz.mzdkplayer.ui.theme.MyTabColors
import org.mz.mzdkplayer.ui.theme.myListItemCoverColor
import java.net.URLEncoder

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaHistoryScreen(
    navController: NavHostController,
    viewModel: MediaHistoryViewModel  // 记得配置依赖注入工厂
) {
    val videoHistory by viewModel.videoHistory.collectAsState()
    val audioHistory by viewModel.audioHistory.collectAsState()

    // 简单的 Tab 切换状态
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Video, 1: Audio

    val isTabRowFocused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

        Text(text = "播放历史", style = MaterialTheme.typography.headlineMedium, color = Color.White)

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Row
        TabRow(selectedTabIndex = selectedTab, indicator  = { tabPositions, _ ->
            if (selectedTab >= 0) {
                DashboardTopBarItemIndicator(
                    currentTabPosition = tabPositions[selectedTab],
                    anyTabFocused = isTabRowFocused,
                    shape = ShapeDefaults.ExtraSmall,
                    activeColor = Color(0xFF000000),
                    inactiveColor =Color(0xFFFFFFFF),
                )
            }
        }) {
            Tab(
                selected = selectedTab == 0,

                onFocus = { selectedTab = 0 },
                onClick = { selectedTab = 0 }
            ) {
                val textColor by animateColorAsState(
                    targetValue = if (selectedTab == 0) Color.Black else Color.White,
                    animationSpec = tween(durationMillis = 100)
                )
                Text("视频 (${videoHistory.size})", modifier = Modifier.padding(12.dp),color =textColor)
            }
            Tab(
                selected = selectedTab == 1,
                onFocus = { selectedTab = 1 },
                onClick = { selectedTab = 1 }
            ) {
                val textColor by animateColorAsState(
                    targetValue = if (selectedTab == 1) Color.Black else Color.White,
                    animationSpec = tween(durationMillis = 100)
                )
                Text("音频 (${audioHistory.size})", modifier = Modifier.padding(12.dp),color =textColor,)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // === 视频历史 (Grid) ===
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(videoHistory, key = { it.history.mediaUri }) { item ->
                    VideoHistoryCard(
                        historyItem = item,
                        onClick = {
                            val record = item.history
                            val encodedUri = URLEncoder.encode(record.mediaUri, "UTF-8")
                            val encodedFileName = URLEncoder.encode(record.fileName, "UTF-8")
                            // 你的导航逻辑
                            navController.navigate("VideoPlayer/$encodedUri/${record.protocolName}/$encodedFileName/${record.connectionName}")
                        }
                    )
                }
            }
        } else {
            // === 音频历史 (List) ===
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(audioHistory, key = { it.mediaUri }) { record ->
                    // 这里可以复用之前的 HistoryListItem，或者稍微简化
                    ListItem(
                        selected = false,
                        onClick = {
                            // 音频导航逻辑 (参考之前的代码)
                        },
                        colors = myListItemCoverColor(),
                        headlineContent = { Text(record.fileName) },
                        supportingContent = {
                            Text("${record.connectionName} | ${record.getPlaybackPercentage()}")
                        },
                        trailingContent = {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(org.mz.mzdkplayer.R.drawable.baseline_music_note_24),
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}
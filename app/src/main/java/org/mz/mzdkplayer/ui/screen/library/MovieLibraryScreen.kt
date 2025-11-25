package org.mz.mzdkplayer.ui.screen.library

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
// 确保导入 LazyColumn 的 items 方法
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect // 【新增】导入 LaunchedEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
// 【新增】导入 ListItem 和 ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ExperimentalTvMaterial3Api
import org.mz.mzdkplayer.data.local.MediaCacheEntity
import org.mz.mzdkplayer.ui.screen.common.MediaCard
import org.mz.mzdkplayer.ui.screen.vm.MediaLibraryViewModel
import org.mz.mzdkplayer.ui.theme.myListItemCoverColor
import java.net.URLEncoder

// === 电影屏幕 ===
@Composable
fun MovieLibraryScreen(
    viewModel: MediaLibraryViewModel,
    navController: NavController
) {
    val movies = viewModel.pagedMovies.collectAsLazyPagingItems()
    val movieVersions by viewModel.selectedMovieVersions.collectAsState()

    var showVersionDialog by remember { mutableStateOf(false) }
    var selectedMovieTitle by remember { mutableStateOf("") }
    // 【新增】状态：标记是否需要检查版本数量并执行跳转/弹窗逻辑
    var checkVersionsAfterLoad by remember { mutableStateOf(false) }

    // 【新增】逻辑：在版本加载完成后，检查版本数量
    LaunchedEffect(movieVersions.size, checkVersionsAfterLoad) {
        // 仅在请求检查且版本列表不为空时执行
        if (checkVersionsAfterLoad && movieVersions.isNotEmpty()) {
            when (movieVersions.size) {
                1 -> {
                    // 1. 只有一个版本：直接导航
                    val version = movieVersions.first()

                    // 导航逻辑（与 onVersionClick 相同）
                    val encodedUri = URLEncoder.encode(version.videoUri, "UTF-8")
                    val encodedFileName = URLEncoder.encode(version.fileName, "UTF-8")
                    val connectionName = URLEncoder.encode(version.connectionName, "UTF-8")
                    navController.navigate("MovieDetails/$encodedUri/${version.dataSourceType}/$encodedFileName/$connectionName/${version.tmdbId}")

                    // 导航后重置状态
                    checkVersionsAfterLoad = false
                    viewModel.clearSelectedMovieVersions()
                }
                in 2..Int.MAX_VALUE -> {
                    // 2. 多个版本：显示弹窗
                    showVersionDialog = true
                    checkVersionsAfterLoad = false // 弹窗已显示，停止检查
                }
            }
        }
    }

    Column(modifier = Modifier.padding(start = 32.dp, top = 24.dp, end = 32.dp)) {
        Text(
            text = "电影库",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(bottom = 50.dp)
        ) {
            items(movies.itemCount) { index ->
                val movie = movies[index]
                if (movie != null) {
                    MediaCard(
                        title = movie.title,
                        posterPath = movie.posterPath,
                        year = movie.releaseDate?.take(4) ?: "",
                        onClick = {
                            selectedMovieTitle = movie.title
                            // 【新增】清空旧版本数据，确保 LaunchedEffect 能正确响应新数据
                            viewModel.clearSelectedMovieVersions()
                            viewModel.loadMovieVersions(movie.tmdbId)
                            // 【修改】设置标志位，等待异步加载结果来决定是跳转还是弹窗
                            checkVersionsAfterLoad = true
                            // showVersionDialog = true 被移除
                        }
                    )
                }
            }
        }
    }

    // === 电影版本选择弹窗 ===
    // 【修改】只有当 showVersionDialog 为 true 且版本数 > 1 时才显示
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

                // 【新增】导航后清空状态
                viewModel.clearSelectedMovieVersions()

                val encodedUri = URLEncoder.encode(version.videoUri, "UTF-8")
                val encodedFileName = URLEncoder.encode(version.fileName, "UTF-8")
                val connectionName = URLEncoder.encode(version.connectionName, "UTF-8")

                navController.navigate("MovieDetails/$encodedUri/${version.dataSourceType}/$encodedFileName/$connectionName/${version.tmdbId}")
            }
        )
    }
}

// 新增/更新：版本选择对话框 UI (使用 ListItem)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieVersionSelectionDialog(
    title: String,
    versions: List<MediaCacheEntity>,
    onDismiss: () -> Unit,
    onVersionClick: (MediaCacheEntity) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .height(400.dp),
            shape = MaterialTheme.shapes.large,
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "$title - 选择版本",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (versions.isEmpty()) {
                    Text("加载中...", color = Color.Gray)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(versions) { version ->
                            // 【修改点】使用 ListItem 替代 Button
                            ListItem(
                                // 默认不选中，焦点的处理由 TV 框架自动完成
                                selected = false,
                                onClick = { onVersionClick(version) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = myListItemCoverColor(),
                                // overlineContent: 显示数据源类型和连接名
                                overlineContent = {
                                    Text(
                                        text = "[${version.dataSourceType}] ${version.connectionName}",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },

                                // headlineContent: 显示完整文件名 (作为主要标识)
                                headlineContent = {
                                    Text(
                                        text = version.fileName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                },

                                // supportingContent: (可选) 可以留空或显示更多细节
                                // supportingContent = { Text("点击播放此版本", color = Color.Gray) }

                                // trailingContent: (可选) 可以在右侧放置一个图标或 "选择" 文本
                                trailingContent = {
                                    Text("选择", color = Color.LightGray)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
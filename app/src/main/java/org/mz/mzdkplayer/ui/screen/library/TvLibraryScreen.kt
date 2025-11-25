package org.mz.mzdkplayer.ui.screen.library

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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.tv.material3.Button
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

@Composable
fun TvLibraryScreen(
    viewModel: MediaLibraryViewModel,
    navController: NavController
) {
    val tvSeriesList = viewModel.pagedTVSeries.collectAsLazyPagingItems()
    val episodes by viewModel.selectedSeriesEpisodes.collectAsState()

    // 控制弹窗显示
    var showEpisodeDialog by remember { mutableStateOf(false) }
    var selectedSeriesName by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(start = 32.dp, top = 24.dp, end = 32.dp)) {
        Text(
            text = "电视剧库",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(bottom = 50.dp)
        ) {
            items(tvSeriesList.itemCount) { index ->
                val tvShow = tvSeriesList[index]
                if (tvShow != null) {
                    MediaCard(
                        title = tvShow.title, // 这里的 Title 是 Series Title (因为 Group By 了)
                        posterPath = tvShow.posterPath,
                        year = tvShow.releaseDate?.take(4) ?: "", // first_air_date
                        onClick = {
                            // 点击时，加载该剧集下的所有文件，并显示弹窗
                            selectedSeriesName = tvShow.title
                            viewModel.loadEpisodes(tvShow.tmdbId)
                            showEpisodeDialog = true
                        }
                    )
                }
            }
        }
    }

    // === 集数选择弹窗 ===
    if (showEpisodeDialog) {
        EpisodeSelectionDialog(
            title = selectedSeriesName,
            episodes = episodes,
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
                navController.navigate(
                    "TVSeriesDetails/$encodedUri/${episode.dataSourceType}/$encodedFileName/$connectionName/${episode.tmdbId}/${episode.seasonNumber}/${episode.episodeNumber}"
                )
            }
        )
    }
}

// 【修改】使用 ListItem 替换 Button
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeSelectionDialog(
    title: String,
    episodes: List<MediaCacheEntity>,
    onDismiss: () -> Unit,
    onEpisodeClick: (MediaCacheEntity) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .height(500.dp),
            shape = MaterialTheme.shapes.large,
            colors = androidx.tv.material3.SurfaceDefaults.colors(
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
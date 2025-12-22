package org.mz.mzdkplayer.ui.screen.library


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.tv.material3.*
import org.mz.mzdkplayer.MzDkPlayerApplication
import org.mz.mzdkplayer.R // 确保这里引用了你的资源R
import org.mz.mzdkplayer.data.local.AudioCacheEntity
import org.mz.mzdkplayer.data.model.AudioItem
import org.mz.mzdkplayer.ui.screen.common.LibraryEmpty
import org.mz.mzdkplayer.ui.screen.vm.AudioViewModel
import org.mz.mzdkplayer.ui.theme.myListItemCoverColor
import java.net.URLEncoder

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    viewModel: AudioViewModel,
    homeNavController: NavController,
    mainNavController: NavHostController
) {
    val audioList by viewModel.allAudio.collectAsState()
    var focusedAudio by remember { mutableStateOf<AudioCacheEntity?>(null) }

    // 初始化焦点
    LaunchedEffect(audioList) {
        if (focusedAudio == null && audioList.isNotEmpty()) {
            focusedAudio = audioList.first()
        }
    }

    if (audioList.isEmpty()) {
        LibraryEmpty(navController = homeNavController, type = "music",)
    } else {
        Box(modifier = Modifier.fillMaxSize()) {

            // 1. 背景层 (用颜色渐变代替封面)
            AnimatedContent(
                targetState = focusedAudio,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
                modifier = Modifier.fillMaxSize()
            ) { au ->
                // 搞一个暗色的动态背景
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2A2A2A), Color(0xFF111111))
                            )
                        )
                )
            }

            // 2. 内容层
            Row(modifier = Modifier.fillMaxSize()) {

                // 左侧：信息展示区
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .padding(start = 56.dp, top = 80.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally // 居中显示唱片
                ) {
                    Text(
                        text = "音乐库",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.LightGray,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(40.dp))

                    // 模拟唱片封面 (由于没有真实图片，用一个圆形容器+图标)
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF333333), Color.Black)
                                )
                            )
                            .padding(4.dp) // 边框效果
                            .background(Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // 这里可以用你的 R.drawable.baseline_music_note_24 或者类似的图标
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.baseline_music_note_24),
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(80.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // 歌名
                    Text(
                        text = focusedAudio?.title ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 歌手 - 专辑
                    Text(
                        text = "${focusedAudio?.artist}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFFFD700)
                    )
                    Text(
                        text = focusedAudio?.album ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(0.7f),
                        modifier = Modifier.padding(top=4.dp)
                    )
                }

                // 右侧：列表区
                LazyColumn(
                    contentPadding = PaddingValues(top = 80.dp, bottom = 40.dp, end = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(0.65f)
                ) {
                    items(audioList) { audio ->
                        val index = audioList.indexOf(audio)
                        audio.audioUri
                        ListItem(
                            selected = false,
                            onClick = {
                                // 播放逻辑
                                val encodedUri = URLEncoder.encode(audio.audioUri, "UTF-8")
                                val encodedFn = URLEncoder.encode(audio.fileName, "UTF-8")
                                val encodedConn = URLEncoder.encode(audio.connectionName, "UTF-8")
                                // 1. 构建播放列表数据 (将数据库 Entity 映射为 AudioItem)
                                val audioItems = audioList.map { entity ->
                                    AudioItem(
                                        uri = entity.audioUri, // 直接使用数据库存储的完整 URI
                                        fileName = entity.fileName,
                                        dataSourceType = entity.dataSourceType
                                    )
                                }

                                // 2. 设置全局播放列表
                                MzDkPlayerApplication.clearStringList("audio_playlist")
                                MzDkPlayerApplication.setStringList(
                                    "audio_playlist",
                                    audioItems
                                )

                                mainNavController.navigate("AudioPlayer/$encodedUri/${audio.dataSourceType}/$encodedFn/$encodedConn/$index")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) focusedAudio = audio },
                            colors = myListItemCoverColor(),
                            headlineContent = {
                                Text(audio.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(audio.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = {
                                Text(
                                    text = "${index + 1}",
                                    color = Color.Gray,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            },
                            trailingContent = {
                                // 由于时长是0，暂时不显示或显示占位符
                                // Text("--:--")
                            }
                        )
                    }
                }
            }
        }
    }
}

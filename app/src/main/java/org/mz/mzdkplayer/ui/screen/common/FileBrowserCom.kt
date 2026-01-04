package org.mz.mzdkplayer.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.model.MediaItem
import org.mz.mzdkplayer.data.repository.Resource
import org.mz.mzdkplayer.tool.Tools
import org.mz.mzdkplayer.tool.Tools.VideoBigIcon
import org.mz.mzdkplayer.tool.Tools.formatFileSize
import kotlin.Boolean

@Composable
fun FileSize(isDirectory: Boolean = true,fileSize: Long = 1L){

    if (!isDirectory) {
        Box(
            modifier = Modifier.widthIn(min = 60.dp), // 设置最小宽度，确保单位对齐
            contentAlignment = Alignment.CenterEnd    // 文字靠右对齐
        )
        {
            Text(
                text = formatFileSize(fileSize),
                maxLines = 1,
                style = TextStyle(
                    fontSize = 10.sp,
                    textAlign = TextAlign.End,
                    color = Color.Gray
                )
            )
        }
    } else {
        //如果是目录，可以显示一个小箭头或者保持空白
        Icon(
            painter = painterResource(R.drawable.arrowright24dp),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color.Gray
        )
    }
}

@Composable
fun FileName(fileName: String=""){
    Text(
        fileName,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 10.sp
    )
}
@Composable
fun MediaInfoLoading(){
    Box(
        modifier = Modifier
            .widthIn(200.dp)
            .fillMaxHeight(0.6f)
            .background(Color.DarkGray.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "正在加载...",
            color = Color.White,
            fontSize = 12.sp
        )
    }
}
@Composable
fun MediaTitle(title: String?){
    Text(
        title?:"未知标题",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp, // 稍微减小字体
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )

}
@Composable
fun MediaReleaseDate(releaseDate: String?){
    Text(
        text = releaseDate?.substring(0, 4) ?: "N/A",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp, // 稍微减小字体
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
}

@Composable
fun MediaFocusedFileName(focusedFileName: String?){
    Text(
        focusedFileName?:"未知文件名",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
@Composable
fun FileIcon(isDirectory: Boolean = true,fileExtension: String="mp4"){
    Icon(
        painter = when {
            isDirectory -> painterResource(R.drawable.baseline_folder_24)
            Tools.containsVideoFormat(fileExtension) -> painterResource(R.drawable.moviefileicon)
            Tools.containsAudioFormat(fileExtension) -> painterResource(R.drawable.baseline_music_note_24)
            Tools.containsImageFileExtension(fileExtension) -> painterResource(R.drawable.image24dp)
            else -> painterResource(R.drawable.baseline_insert_drive_file_24)
        },
        contentDescription = null,
    )
}

@Composable
fun CirCleIconButton(
    modifier: Modifier = Modifier,
    icon: Painter,
    tooltip: String = "",
    contentDescription: String? = null,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // 1. 外层 Box 仅作为容器，不包裹 Popup 逻辑
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 2. 这里的 Surface 必须强制固定 size
        Surface(
            modifier = Modifier.size(36.dp), // 显式声明 36dp，防止被拉伸成条状
            onClick = onClick,
            interactionSource = interactionSource,
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.DarkGray,
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        ) {
            Icon(
                painter = icon,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentDescription = contentDescription,
            )
        }

        // 3. Popup 逻辑独立出来
        if (isFocused && tooltip.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, -70)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF2D2D2D).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tooltip,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun MediaPreviewSection(
    focusedMovie: Resource<MediaItem?>,
    focusedFileName: String?,
    focusedIsDir: Boolean,
    modifier: Modifier = Modifier,
    onMediaIdResolved: (Int) -> Unit // 新增回调：当解析出电影 ID 时通知外部
) {
        Column(
            modifier = modifier, // 使用传入的 modifier
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                when (focusedMovie) {
                    is Resource.Success -> {
                        val movie = focusedMovie.data
                        if (movie != null) {
                            // 关键点：通过 SideEffect 或直接在逻辑中触发回调
                            // 这样父组件的 mediaId 就会保持同步
                            LaunchedEffect(movie.id) {
                                onMediaIdResolved(movie.id)
                            }

                            if (movie.posterPath != null) {
                                Box(
                                    Modifier.border(
                                        width = 2.dp,
                                        color = Color.Gray.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                ) {
                                    AsyncImage(
                                        model = "https://image.tmdb.org/t/p/w500${movie.posterPath}",
                                        contentDescription = movie.title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .align(Alignment.Center)
                                            .clip(RoundedCornerShape(20.dp))
                                    )
                                }
                            } else {
                                VideoBigIcon(focusedIsDir, focusedFileName, Modifier.fillMaxWidth().height(200.dp))
                            }
                        } else {
                            // 如果没有匹配到电影，通知父组件重置 mediaId
                            LaunchedEffect(Unit) { onMediaIdResolved(-1) }
                            VideoBigIcon(focusedIsDir, focusedFileName, Modifier.fillMaxWidth().height(200.dp))
                        }
                    }
                    is Resource.Loading -> {
                        MediaInfoLoading()
                    }
                    is Resource.Error -> {
                        LaunchedEffect(Unit) { onMediaIdResolved(-1) }
                        VideoBigIcon(focusedIsDir, focusedFileName, Modifier.fillMaxWidth().height(200.dp))
                    }
                }
            }

            // --- 文字信息区 ---
            Box(
                modifier = Modifier.weight(0.2f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val movie = (focusedMovie as? Resource.Success)?.data
                if (movie != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MediaTitle(movie.title)
                        Spacer(modifier = Modifier.height(4.dp))
                        MediaReleaseDate(movie.releaseDate)
                    }
                } else {
                    MediaFocusedFileName(focusedFileName)
                }
            }
        }

}
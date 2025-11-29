package org.mz.mzdkplayer.ui.videoplayer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.mz.mzdkplayer.R // 确保 R.drawable.hdr_1 和 R.drawable.dolby_vision_seeklogo 等存在
import org.mz.mzdkplayer.tool.focusOnInitialVisibility
import java.util.Locale


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoTrackPanel(
    selectedIndex: Int,
    onSelectedIndexChange: (currentIndex: Int) -> Unit,
    lists: MutableList<Tracks.Group>,
    exoPlayer: ExoPlayer
) {
    // ... (状态和修饰符保持不变)
    val focusRequester = remember { FocusRequester() }
    val isVis = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .widthIn(200.dp, 500.dp)
            .heightIn(200.dp, 500.dp),
        state = listState
    ) {
        if (lists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "该文件无视频轨道",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }
            }
        } else {
            // LazyColumn滚到到当前选择位置
            coroutineScope.launch {
                listState.animateScrollToItem(index = selectedIndex)
            }
            items(lists.size) { index ->
                val format = lists[index].getTrackFormat(0)
                val videoCode = format.codecs.orEmpty()
                val videoHeight = format.height
                val videoBitmap = format.bitrate


                // *** 新增：安全地获取 ColorInfo 字段 ***
                val colorInfo = format.colorInfo

                // 使用 .getOrDefault(C.COLOR_TRANSFER_UNSET) 替代，但由于 UNSET 未解析，
                // 我们直接从 colorInfo 检查。如果 colorInfo 为 null，我们视为 UNSET。
                // 否则，取其内部值。
                val colorTransfer = colorInfo?.colorTransfer ?: C.INDEX_UNSET // C.INDEX_UNSET通常是-1
                val colorSpace = colorInfo?.colorSpace ?: C.INDEX_UNSET


                // --- 轨道信息判断 ---
                val isDolbyVision = videoCode.contains("dvh", ignoreCase = true)

                // 使用 C.COLOR_TRANSFER_ST2084 代替 C.COLOR_TRANSFER_PQ
                // ST2084 是 PQ 曲线的官方标准
                val isHdr10 = (colorTransfer == C.COLOR_TRANSFER_ST2084 ||
                        colorTransfer == C.COLOR_TRANSFER_HLG) &&
                        colorSpace == C.COLOR_SPACE_BT2020 &&
                        !isDolbyVision // 排除 DV

                // 确定视频编码类型
                val isHevc = videoCode.contains("hev", ignoreCase = true)
                val isAvc = videoCode.contains("avc", ignoreCase = true)
                val isAv1 = videoCode.contains("av0", ignoreCase = true)

                // 构造轨道名称前缀
                val qualityPrefix = when {
                    isDolbyVision -> "杜比视界"
                    isHdr10 -> "HDR" // 识别为 HDR10/HLG (非DV)
                    // 使用垂直分辨率（videoHeight）来判断质量等级
                    videoHeight >= 2160 -> "4K/UHD" // 标准 4K/UHD 高度为 2160P
                    videoHeight >= 1440 -> "2K/1440P" // 增加 1440P 等级
                    videoHeight >= 1080 -> "1080P"  // 标准 1080P 高度
                    videoHeight >= 720 -> "720P"   // 标准 720P 高度
                    else -> "标清"
                }

                ListItem(
                    modifier = if (index == selectedIndex /*选中的获取焦点*/) {
                        Modifier
                            .padding(
                                start = 15.dp,
                                end = 15.dp,
                                top = 10.dp,
                                bottom = 10.dp
                            )
                            .focusOnInitialVisibility(isVis)
                    } else Modifier.padding(
                        start = 15.dp,
                        end = 15.dp,
                        top = 10.dp,
                        bottom = 10.dp
                    ),
                    selected = false,
                    colors = ListItemDefaults.colors(
                        containerColor = Color(0, 0, 0),
                        contentColor = Color(255, 255, 255),
                        selectedContainerColor = Color(255, 255, 255),
                        selectedContentColor = Color(255, 255, 255),
                        focusedSelectedContentColor = Color(255, 255, 255),
                        focusedSelectedContainerColor = Color(255, 255, 255),
                        focusedContainerColor = Color(255, 255, 255),
                        focusedContentColor = Color(0, 0, 0)
                    ),
                    headlineContent = {
                        Text(
                            "$qualityPrefix ${videoHeight}P " +
                                    "${String.format(Locale.getDefault(), "%.1f", videoBitmap / 1000.0 / 1000.0)}Mbps"
                        )
                    },
                    leadingContent = if (selectedIndex == index) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选择",
                            )
                        }
                    } else null,
                    trailingContent = {
                        // 1. 显示 DV 图标
                        if (isDolbyVision) {
                            Icon(
                                painterResource(id = R.drawable.dolby_vision_seeklogo),
                                contentDescription = "杜比视界",
                                modifier = Modifier.height(38.dp).width(38.dp)
                            )
                        }
                        // 2. 显示 HDR 图标 (如果不是 DV)
                        else if (isHdr10) {
                            Icon(
                                painterResource(id = R.drawable.hdr_1),
                                contentDescription = "HDR",
                                modifier = Modifier.height(38.dp).width(38.dp)
                            )
                        }

                        // 3. 显示编码格式图标
                        if (isHevc) {
                            Icon(
                                painterResource(id = R.drawable.h265),
                                contentDescription = "H.265/HEVC",
                                modifier = Modifier.height(23.dp).width(46.dp)
                            )
                        } else if (isAvc) {
                            Icon(
                                painterResource(id = R.drawable.h264),
                                contentDescription = "H.264/AVC",
                                modifier = Modifier.height(23.dp).width(46.dp)

                            )
                        } else if (isAv1) {
                            Icon(
                                painterResource(id = R.drawable.av1),
                                contentDescription = "AV1",
                                modifier = Modifier.height(23.dp).width(46.dp)
                            )
                        }
                    },
                    onClick = {
                        onSelectedIndexChange(index)
                        exoPlayer.trackSelectionParameters =
                            exoPlayer.trackSelectionParameters.buildUpon().setOverrideForType(
                                TrackSelectionOverride(
                                    lists[index].mediaTrackGroup,
                                    0
                                )
                            ).build()
                    }
                )
            }

        }
    }
}
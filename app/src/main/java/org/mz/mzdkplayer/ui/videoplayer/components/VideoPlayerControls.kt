package org.mz.mzdkplayer.ui.videoplayer.components

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.data.repository.DanmakuSettingsManager
import org.mz.mzdkplayer.player.core.IMzPlayer
import org.mz.mzdkplayer.player.core.MzIsoTitle
import org.mz.mzdkplayer.tool.Tools
import org.mz.mzdkplayer.ui.screen.vm.VideoPlayerViewModel
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.platform.LocalLocale

/**
 * 视频播放器控制按钮区域
 *
 * @param isPlaying 当前播放状态
 * @param contentCurrentPosition 当前播放位置
 * @param state 视频播放器状态
 * @param focusRequester 焦点请求器
 * @param title 标题
 * @param secondaryText 副标题
 * @param tertiaryText 第三行文本
 * @param videoPlayerViewModel ViewModel
 * @param danmakuPlayer 弹幕播放器
 * @param settingsManager 弹幕设置管理器
 * @param getDanmakuConfig 获取弹幕配置的方法
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerControls(
    isPlaying: Boolean,
    contentCurrentPosition: Long,
    player: IMzPlayer,
    state: VideoPlayerState,
    focusRequester: FocusRequester,
    title: String, secondaryText: String, tertiaryText: String,
    videoPlayerViewModel: VideoPlayerViewModel,
    isoTitles: List<MzIsoTitle>,
    mediaUri: String,
    danmakuPlayer: DanmakuPlayer,
    settingsManager: DanmakuSettingsManager, // 添加设置管理器参数
    getDanmakuConfig: () -> DanmakuConfig // 添加获取配置的方法参数
)
{
    // 播放/暂停切换回调
    val onPlayPauseToggle = { shouldPlay: Boolean ->
        if (shouldPlay) {
            player.play()
        } else {
            player.pause()
        }
    }

    // 构建主框架
    VideoPlayerMainFrame(
        mediaTitle = {
            // 媒体标题区域
            VideoPlayerMediaTitle(
                title = title,
                secondaryText = secondaryText,
                tertiaryText = tertiaryText,
                type = VideoPlayerMediaTitleType.DEFAULT
            )
        },
        mediaActions = {
            // 媒体操作按钮区域 (音轨、字幕、弹幕开关)
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoPlayerControlsIcon(
                    icon = if (isoTitles.isEmpty()&& Tools.extractFileExtension(mediaUri)
                            .uppercase(LocalLocale.current.platformLocale) != "ISO"
                    ) painterResource(id = R.drawable.baseline_hd_24) else painterResource(R.drawable.blu_ray_disc) , // 高清图标
                    state = state,
                    isPlaying = isPlaying,
                    tooltipText = "视频轨道", // 增加此处
                    onClick = {
                        // 点击高清图标，显示视频轨道选择面板
                        // 如果底层解析出了 titles，说明是包含了多视频的 ISO/DVD 文件
                        if (isoTitles.isNotEmpty()) {
                            videoPlayerViewModel.selectedAorVorS = "ISO" // 走新的 ISO 视频列表
                        } else {
                            videoPlayerViewModel.selectedAorVorS = "V"   // 走普通的视频轨道
                        }
                        videoPlayerViewModel.atpVisibility = !videoPlayerViewModel.atpVisibility;
                        focusRequester.requestFocus()
                    }
                )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = painterResource(id = R.drawable.baseline_speaker_24), // 音频图标
                    state = state,
                    tooltipText = "音频轨道", // 增加此处
                    isPlaying = isPlaying,
                    onClick = {
                        // 点击音频图标，显示音频轨道选择面板
                        videoPlayerViewModel.selectedAorVorS = "A"
                        videoPlayerViewModel.atpVisibility = !videoPlayerViewModel.atpVisibility;
                        focusRequester.requestFocus()
                    }
                )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = painterResource(id = R.drawable.baseline_subtitles_24), // 字幕图标
                    state = state,
                    tooltipText = "字幕选择", // 增加此处
                    isPlaying = isPlaying,
                    onClick = {
                        // 点击字幕图标，显示字幕轨道选择面板
                        videoPlayerViewModel.selectedAorVorS = "S"
                        videoPlayerViewModel.atpVisibility = !videoPlayerViewModel.atpVisibility;
                        focusRequester.requestFocus()
                    }
                )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = if (videoPlayerViewModel.danmakuVisibility) painterResource(id = R.drawable.dmk) else painterResource(
                        id = R.drawable.dmb
                    ), // 弹幕开关图标
                    state = state,
                    tooltipText = if (videoPlayerViewModel.danmakuVisibility) "关闭弹幕" else "开启弹幕",
                    isPlaying = isPlaying,
                    onClick = {
                        // 点击弹幕开关图标，切换弹幕可见性
                        videoPlayerViewModel.danmakuVisibility =
                            !videoPlayerViewModel.danmakuVisibility

                        // 使用封装的方法获取当前配置
                        videoPlayerViewModel.danmakuConfig = getDanmakuConfig()
                        danmakuPlayer.updateConfig(videoPlayerViewModel.danmakuConfig)

                        Log.d("isPlay", isPlaying.toString())
                        // 根据播放状态和可见性控制弹幕播放器
                        if (!videoPlayerViewModel.danmakuVisibility) {
                            danmakuPlayer.pause()
                        } else {
                            if (isPlaying) {
                                danmakuPlayer.start()
                                danmakuPlayer.seekTo(contentCurrentPosition)
                            } else {
                                // 修复关闭弹幕在打开时如果视频处于暂停状态弹幕还会继续滚动
                                danmakuPlayer.seekTo(contentCurrentPosition)
                                danmakuPlayer.pause()
                            }
                        }

                        // 保存当前配置到本地，包括可见性状态
                        val currentSettings = settingsManager.loadSettings()
                        settingsManager.saveSettings(
                            videoPlayerViewModel.danmakuVisibility, // 更新可见性
                            currentSettings.selectedRatio, // 保持其他设置不变
                            currentSettings.fontSize,
                            currentSettings.transparency,
                            currentSettings.selectedTypes
                        )
                    }
                )

                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = painterResource(id = R.drawable.video_danmu_config), // 字幕图标
                    state = state,
                    isPlaying = isPlaying,
                    tooltipText = "弹幕设置", // 增加此处
                    onClick = {
                        // 点击字幕图标，显示字幕轨道选择面板
                        videoPlayerViewModel.selectedAorVorS = "D"
                        videoPlayerViewModel.atpVisibility = !videoPlayerViewModel.atpVisibility;
                        focusRequester.requestFocus()
                    }
                )

                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = painterResource(id = R.drawable.subtitles_off_24dp), // 字幕图标
                    state = state,
                    isPlaying = isPlaying,
                    // 根据状态动态显示文案
                    tooltipText = if (videoPlayerViewModel.isCusSubtitleViewVis) "隐藏自定义字幕" else "显示自定义字幕",
                    onClick = {
                        // 点击字幕图标，显示字幕轨道选择面板
                        videoPlayerViewModel.isCusSubtitleViewVis = !videoPlayerViewModel.isCusSubtitleViewVis;
                    }
                )
            }
        },
        seeker = {
            // 进度条区域
            VideoPlayerSeeker(
                focusRequester,
                state,
                isPlaying,
                onPlayPauseToggle,
                onSeek = { player.seekTo(player.duration.times(it).toLong()) }, // Seek 回调
                contentProgress = contentCurrentPosition.milliseconds, // 当前进度
                contentDuration = player.duration.milliseconds // 总时长
            )
        },
        more = null // 更多按钮 (未实现)
    )
}
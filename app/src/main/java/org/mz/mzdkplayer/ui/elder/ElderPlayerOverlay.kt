package org.mz.mzdkplayer.ui.elder

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerState

/**
 * 老人模式专用播放器控制栏覆盖层
 *
 * 设计特点：
 * - 仅 3 个大按钮：◀◀（后退）、▶/❚❚（播放/暂停）、▶▶（前进）
 * - 加粗进度条（6dp）
 * - 大字号时间显示
 * - 无弹幕、轨道、字幕开关等高级按钮
 * - 无网速显示
 */
@Composable
fun ElderPlayerOverlay(
    isPlaying: Boolean,
    contentCurrentPosition: Long,
    contentDuration: Long,
    state: VideoPlayerState,
    focusRequester: FocusRequester,
    onPlayPauseToggle: (Boolean) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeek: (Float) -> Unit,
    onExit: () -> Unit,
    seekStepMs: Long = 10_000L,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(state.controlsVisible) {
        if (state.controlsVisible) {
            focusRequester.requestFocus()
        } else {
            // 控制栏隐藏时释放焦点, 避免焦点停留在不可见按钮上
            focusRequester.freeFocus()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        BackHandler(state.controlsVisible) {
            state.hideControls()
        }

        // 半透明渐变背景
        AnimatedVisibility(state.controlsVisible, Modifier, fadeIn(), fadeOut()) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }

        // 底部控制栏
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            AnimatedVisibility(
                state.controlsVisible,
                Modifier,
                slideInVertically { it },
                slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .padding(bottom = 40.dp, top = 8.dp)
                ) {
                    // 三个大按钮 + 退出按钮
                    ElderControlButtons(
                        isPlaying = isPlaying,
                        focusRequester = focusRequester,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSeekBack = onSeekBack,
                        onSeekForward = onSeekForward,
                        onExit = onExit,
                        seekStepMs = seekStepMs
                    )

                    Spacer(Modifier.height(20.dp))

                    // 加粗进度条
                    ElderProgressBar(
                        progress = if (contentDuration > 0) contentCurrentPosition.toFloat() / contentDuration else 0f,
                        onSeek = onSeek,
                        state = state,
                        seekStepMs = seekStepMs,
                        contentDuration = contentDuration
                    )

                    Spacer(Modifier.height(8.dp))

                    // 大字号时间显示
                    ElderTimeDisplay(
                        currentPosition = contentCurrentPosition,
                        duration = contentDuration
                    )

                    Spacer(Modifier.height(8.dp))

                    // Issue 9: 进度条操作提示, 帮助老年用户理解如何快进/快退
                    Text(
                        text = stringResource(R.string.elder_progress_hint),
                        color = ElderColors.textHint,
                        fontSize = ElderDimens.captionFontSize,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 老人模式三个大按钮：后退、播放/暂停、前进, 以及退出按钮 (Issue 10)
 */
@Composable
private fun ElderControlButtons(
    isPlaying: Boolean,
    focusRequester: FocusRequester,
    onPlayPauseToggle: (Boolean) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onExit: () -> Unit,
    seekStepMs: Long = 10_000L
) {
    val seekStepSec = (seekStepMs / 1000).toInt()
    val seekBackText = stringResource(R.string.elder_seek_back, seekStepSec)
    val seekForwardText = stringResource(R.string.elder_seek_forward, seekStepSec)
    val playText = stringResource(R.string.elder_play)
    val pauseText = stringResource(R.string.elder_pause)
    val exitText = stringResource(R.string.elder_exit_player)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ◀◀ 后退
        ElderControlButton(
            icon = painterResource(id = R.drawable.baseline_arrow_back_ios_new_24),
            contentDescription = seekBackText,
            focusRequester = null,
            onClick = onSeekBack
        )

        Spacer(Modifier.size(32.dp))

        // ▶/❚❚ 播放/暂停
        ElderControlButton(
            icon = if (!isPlaying) painterResource(id = R.drawable.baseline_play_arrow_24) else painterResource(
                id = R.drawable.baseline_pause_24
            ),
            contentDescription = if (!isPlaying) playText else pauseText,
            focusRequester = focusRequester,
            onClick = { onPlayPauseToggle(!isPlaying) },
            buttonSize = 72.dp // 播放按钮更大
        )

        Spacer(Modifier.size(32.dp))

        // ▶▶ 前进
        ElderControlButton(
            icon = painterResource(id = R.drawable.baseline_arrow_forward_ios_24),
            contentDescription = seekForwardText,
            focusRequester = null,
            onClick = onSeekForward
        )

        Spacer(Modifier.size(32.dp))

        // Issue 10: 显式退出按钮, 方便老年用户退出播放
        ElderControlButton(
            icon = painterResource(id = R.drawable.close24dp),
            contentDescription = exitText,
            focusRequester = null,
            onClick = onExit,
            buttonSize = 56.dp
        )
    }
}

/**
 * 老人模式大按钮组件
 */
@Composable
private fun ElderControlButton(
    icon: Painter,
    contentDescription: String?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp = 56.dp
) {
    val interactionSource = remember { MutableInteractionSource() }

    val modifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier.size(buttonSize),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(0.9f),
            contentColor = ElderColors.textPrimary,
            focusedContainerColor = ElderColors.textPrimary,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        interactionSource = interactionSource
    ) {
        Icon(
            icon,
            modifier = Modifier
                .fillMaxSize()
                .padding(buttonSize * 0.22f),
            contentDescription = contentDescription,
            tint = LocalContentColor.current
        )
    }
}

/**
 * 老人模式加粗进度条（6dp 高度），支持点击/拖动跳转
 */
@Composable
private fun ElderProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    state: VideoPlayerState,
    seekStepMs: Long = 10_000L,
    contentDuration: Long = 0L
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            state.showControls(seconds = Int.MAX_VALUE)
        } else {
            state.showControls()
        }
    }

    // 点击/拖动跳转
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val displayProgress = dragProgress ?: progress

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp) // 扩大触摸区域
            // Issue 9: 焦点状态使用明显边框, 让老年用户清楚知道当前焦点位置
            .then(
                if (isFocused) Modifier.border(
                    width = 2.dp,
                    color = ElderColors.accent,
                    shape = RoundedCornerShape(4.dp)
                ) else Modifier
            )
            .onPreviewKeyEvent { keyEvent ->
                // 遥控器左右键调进度
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    val seekStepRatio = if (contentDuration > 0) seekStepMs.toFloat() / contentDuration else 0.05f
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
                            onSeek((displayProgress - seekStepRatio).coerceIn(0f, 1f))
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
                            onSeek((displayProgress + seekStepRatio).coerceIn(0f, 1f))
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(ratio)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        dragProgress = ratio
                    },
                    onDragEnd = {
                        dragProgress?.let { onSeek(it) }
                        dragProgress = null
                    },
                    onDragCancel = {
                        dragProgress = null
                    }
                ) { change, _ ->
                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                    dragProgress = ratio
                }
            }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 8.dp else 6.dp) // Issue 9: 焦点时进度条加粗
        ) {
            val yOffset = size.height.div(2)
            val currentProgressX = size.width.times(displayProgress)

            // 背景轨道
            drawLine(
                color = ElderColors.textPrimary.copy(alpha = 0.3f),
                start = Offset(x = 0f, y = yOffset),
                end = Offset(x = size.width, y = yOffset),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
            // 进度轨道 - Issue 9: 焦点时使用高对比度黄色, 非焦点使用白色
            drawLine(
                color = if (isFocused || dragProgress != null) ElderColors.accent else ElderColors.textPrimary,
                start = Offset(x = 0f, y = yOffset),
                end = Offset(x = currentProgressX, y = yOffset),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
            // 进度条末端圆球 - Issue 9: 焦点时圆球放大并使用黄色
            drawCircle(
                color = if (isFocused || dragProgress != null) ElderColors.accent else ElderColors.textPrimary,
                radius = if (isFocused || dragProgress != null) size.height * 1.2f else size.height * 0.9f,
                center = Offset(x = currentProgressX, y = yOffset)
            )
        }
    }
}

/**
 * 老人模式大字号时间显示
 */
@Composable
private fun ElderTimeDisplay(
    currentPosition: Long,
    duration: Long
) {
    val currentStr = formatDuration(currentPosition)
    val durationStr = formatDuration(duration)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$currentStr / $durationStr",
            color = ElderColors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "${m}:${s.toString().padStart(2, '0')}"
    }
}

package org.mz.mzdkplayer.ui.videoplayer

// 导入必要的库和组件

import android.net.TrafficStats
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key

import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults

import androidx.tv.material3.Text
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.ext.RETAINER_BILIBILI
import com.kuaishou.akdanmaku.render.SimpleRenderer
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import kotlinx.coroutines.delay
import org.mz.mzdkplayer.R
import org.mz.mzdkplayer.danmaku.DanmakuData
import org.mz.mzdkplayer.danmaku.DanmakuResponse
import org.mz.mzdkplayer.danmaku.getDanmakuXmlFromFile
import org.mz.mzdkplayer.data.model.DanmakuScreenRatio
import org.mz.mzdkplayer.data.model.MediaHistoryRecord

import org.mz.mzdkplayer.data.repository.DanmakuSettingsManager
import org.mz.mzdkplayer.player.core.IMzPlayer
import org.mz.mzdkplayer.player.exo.MzExoPlayer
import org.mz.mzdkplayer.player.vlc.MzVlcPlayer
import org.mz.mzdkplayer.tool.FtpDataSource
import org.mz.mzdkplayer.tool.SmbDataSource
import org.mz.mzdkplayer.tool.SmbUtils
import org.mz.mzdkplayer.tool.SubtitleView
import org.mz.mzdkplayer.tool.WebDavDataSource
import org.mz.mzdkplayer.tool.handleDPadKeyEvents

import org.mz.mzdkplayer.ui.screen.common.LoadingScreen
import org.mz.mzdkplayer.ui.screen.common.VAErrorScreen
import org.mz.mzdkplayer.ui.screen.vm.MediaHistoryViewModel
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel
import org.mz.mzdkplayer.ui.screen.vm.VideoPlayerStatus
import org.mz.mzdkplayer.ui.screen.vm.VideoPlayerViewModel
import org.mz.mzdkplayer.ui.theme.myIconButtonColor
import org.mz.mzdkplayer.ui.videoplayer.components.AkDanmakuPlayer
import org.mz.mzdkplayer.ui.videoplayer.components.AudioTrackPanel
import org.mz.mzdkplayer.ui.videoplayer.components.DanmakuPanel
import org.mz.mzdkplayer.ui.videoplayer.components.SubtitleTrackPanel
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerControlsIcon
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerMainFrame
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerMediaTitle
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerMediaTitleType
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerOverlay
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerPulse
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerPulseState
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerSeeker
import org.mz.mzdkplayer.ui.videoplayer.components.VideoPlayerState
import org.mz.mzdkplayer.ui.videoplayer.components.VideoTrackPanel

import org.mz.mzdkplayer.ui.videoplayer.components.rememberVideoPlayerPulseState
import org.mz.mzdkplayer.ui.videoplayer.components.rememberVideoPlayerState
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * 视频播放器主界面 Composable
 *
 * @param mediaUri 媒体文件的 URI
 * @param dataSourceType 数据源类型 (例如 "SMB", "HTTP", "NFS")
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    mediaUri: String,
    dataSourceType: String,
    fileName: String = "未知文件名",
    connectionName: String,
    mediaHistoryViewModel: MediaHistoryViewModel,
    useVlc: Boolean = false, // 开关：让用户或者设置决定用哪个内核
    settingsViewModel: SettingsViewModel = viewModel()
) {
    // 获取当前 Compose 上下文
    val context = LocalContext.current
    // 记住并创建 Player 实例
    // 1. 根据配置实例化解耦的 Player 内核
    val player: IMzPlayer = remember(useVlc, mediaUri) {
        if (useVlc) {
            MzVlcPlayer(context, mediaUri,settingsViewModel=settingsViewModel)
        } else {
            MzExoPlayer(context, mediaUri, dataSourceType, settingsViewModel)
        }
    }
    // 记住并创建视频播放器状态管理器
    val videoPlayerState = rememberVideoPlayerState(hideSeconds = 6)
    // 获取 ViewModel 实例
    val videoPlayerViewModel: VideoPlayerViewModel = viewModel()
    // 状态：是否显示 Toast 提示
    var showToast by remember { mutableStateOf(false) }
    // 状态：返回按钮按压状态，用于双击退出逻辑
    var backPressState by remember { mutableStateOf<BackPress>(BackPress.Idle) }
    // 状态：当前媒体播放位置
    var contentCurrentPosition by remember { mutableLongStateOf(0L) }
    // 状态：当前播放状态 (播放/暂停)
    var isPlaying: Boolean by remember { mutableStateOf(player.isPlaying) }

    // 网速监控相关状态
    // 状态：计算得到的网络速度 (bytes/sec)
    var networkSpeed by remember { mutableLongStateOf(0L) }
    // 状态：上一次记录的接收总字节数
    var lastTotalRxBytes by remember { mutableLongStateOf(0L) }
    // 状态：上一次记录的时间戳
    var lastTimeStamp by remember { mutableLongStateOf(0L) }

    // 记住并创建弹幕播放器实例
    val mDanmakuPlayer: DanmakuPlayer = remember { DanmakuPlayer(SimpleRenderer()) }

    // 根据媒体 URI 和数据源类型推断弹幕文件 URI
    val danmakuUri =
        if (dataSourceType == "NFS") SmbUtils.getDanmakuNfsUri(mediaUri.toUri()) else SmbUtils.getDanmakuSmbUri(
            mediaUri.toUri()
        )
    // 状态：当前的字幕组 (CueGroup)
    var currentCueGroup: CueGroup? by remember { mutableStateOf<CueGroup?>(null) }
    // 弹幕数据
    // 状态：解析后的弹幕数据列表
    var danmakuDataList by remember { mutableStateOf<List<DanmakuData>?>(null) }
    // 状态：弹幕是否已加载完成
    var isDanmakuLoaded by remember { mutableStateOf(false) }
    val pulseState = rememberVideoPlayerPulseState()

    // 弹幕设置管理器
    val settingsManager = remember { DanmakuSettingsManager(context) }

    // 从 SettingsViewModel 中获取字幕相关的状态
    val settingsState by settingsViewModel.uiState.collectAsState()


    // 记录从数据库查到的历史进度
    var historySeekPos by remember { mutableLongStateOf(0L) }
    // 控制跳转提示条的显示
    var showHistoryTip by remember { mutableStateOf(false) }
    // 增加一个标记，确保只在第一次准备好时触发计时
    var hasTriggeredTimer by remember { mutableStateOf(false) }

    // 👇 新增：标记是否是首次加载
    var isFirstLoad by remember { mutableStateOf(true) }

    val videoTracks by player.videoTracks.collectAsState()
    val audioTracks by player.audioTracks.collectAsState()
    val subtitleTracks by player.subtitleTracks.collectAsState()

    val isPlayerPlaying by player.isPlayingFlow.collectAsState()
    val playerStatus by player.playerStatus.collectAsState()
    // 构建播放器 (设置媒体源等)
    //BuilderMzPlayer(context, mediaUri, exoPlayer, dataSourceType, settingsViewModel)
    // 当 Composable 离开组合时，释放资源
    DisposableEffect(Unit) {
        onDispose {
            // 1. 获取播放器当前状态
            val currentPos = player.currentPosition
            val totalDur = player.duration

            // 2. 构建历史记录对象
            // 只要播放过（进度 > 0）且总时长有效，才保存
            if (currentPos > 0 && totalDur > 0) {
                val record = MediaHistoryRecord(
                    mediaUri = mediaUri,
                    fileName = fileName,
                    playbackPosition = currentPos,
                    mediaDuration = totalDur,
                    // 处理协议名称显示的逻辑
                    protocolName = if (dataSourceType == "LOCAL") "LOCAL" else dataSourceType,
                    connectionName = connectionName,
                    serverAddress = "test", // 如果你有真实的 server IP，请传入，否则留空或用占位符
                    mediaType = "VIDEO",    // 明确标记为视频
                    timestamp = System.currentTimeMillis()
                )

                // 3. 调用 ViewModel 保存 (ViewModel 内部会启动协程写入数据库)
                mediaHistoryViewModel.saveHistory(record)
            }

            // 4. 释放资源
            player.release()
            mDanmakuPlayer.release()

            // ⭐️ 新增：退出播放页面时，彻底关闭 SMB 连接
            // 只有在这里调用，才能既保证播放时的连接复用，又保证退出时不泄露连接
            // 统一释放所有协议的全局连接
            thread {
                try {
                    SmbDataSource.releaseGlobalResources()
                    WebDavDataSource.releaseGlobalResources()
                    FtpDataSource.releaseGlobalResources()
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayer", "Async release failed", e)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        // 调用 suspend 函数从数据库获取进度
        val historyPosition = mediaHistoryViewModel.getHistoryPosition(mediaUri)

        // 进度大于 5 秒才有必要提示跳转（避开片头）
        if (historyPosition > 5000) {
            historySeekPos = historyPosition
        }
    }

    // 辅助方法：获取当前弹幕配置
    fun getDanmakuConfig(): DanmakuConfig {
        val currentSettings = settingsManager.loadSettings()
        val screenPartValue =
            DanmakuScreenRatio.fromDisplayName(currentSettings.selectedRatio).ratioValue

        return videoPlayerViewModel.danmakuConfig.copy(
            retainerPolicy = RETAINER_BILIBILI,
            visibility = videoPlayerViewModel.danmakuVisibility,
            screenPart = screenPartValue,
            textSizeScale = currentSettings.fontSize.toFloat() / 100,
            alpha = currentSettings.transparency.toFloat() / 100,
            dataFilter = listOf(videoPlayerViewModel.createDanmakuTypeFilter(currentSettings.selectedTypes)) // 添加弹幕过滤器
        )
    }

    // 在初始化时从本地加载弹幕设置，如果没有则使用默认值
    LaunchedEffect(Unit) {
        val savedSettings = settingsManager.loadSettings()
        videoPlayerViewModel.danmakuConfig = videoPlayerViewModel.danmakuConfig.copy(
            retainerPolicy = RETAINER_BILIBILI, // 设置弹幕保留策略
            textSizeScale = savedSettings.fontSize.toFloat() / 100, // 字体缩放
            screenPart = DanmakuScreenRatio.fromDisplayName(savedSettings.selectedRatio).ratioValue, // 屏幕占用比例
            alpha = savedSettings.transparency.toFloat() / 100, // 透明度
            visibility = savedSettings.isSwitchEnabled, // 可见性
            dataFilter = listOf(videoPlayerViewModel.createDanmakuTypeFilter(savedSettings.selectedTypes)) // 添加弹幕过滤器
        )
        videoPlayerViewModel.danmakuConfig.updateFilter()
        mDanmakuPlayer.updateConfig(videoPlayerViewModel.danmakuConfig)
        // 设置ViewModel的弹幕可见性状态与本地设置一致
        videoPlayerViewModel.danmakuVisibility = savedSettings.isSwitchEnabled
    }

    // 加载弹幕数据
    LaunchedEffect(danmakuUri) {
        Log.d("danmakuUri", danmakuUri.toString())
        Log.d("mediaUri", mediaUri.toUri().toString())
        try {
            Log.d("danmakuUriScheme", danmakuUri.scheme?.lowercase().toString())
            // 根据 URI scheme 打开对应的输入流
            val inputStream: InputStream? = when (danmakuUri.scheme?.lowercase()) {
                "smb" -> {
                    // 使用 SMB 工具打开输入流
                    SmbUtils.openSmbFileInputStream(danmakuUri, "video")
                }

                "http", "https" -> {
                    // 打开 HTTP 输入流
                    when (dataSourceType) {
                        "WEBDAV" -> {
                            SmbUtils.openWebDavFileInputStream(danmakuUri, "video")
                        }

                        "HTTP" -> {
                            SmbUtils.openHTTPLinkXmlInputStream(danmakuUri.toString(), "video")
                        }

                        else -> {
                            URL(danmakuUri.toString()).openStream()
                        }
                    }
                }

                "file" -> {
                    // 打开本地文件输入流
                    context.contentResolver.openInputStream(danmakuUri)
                        ?: throw IOException("Could not open file input stream for $danmakuUri")
                }

                "ftp" -> {
                    // 使用 SMB 工具打开输入流
                    SmbUtils.openFtpFileInputStream(danmakuUri, "video")
                }

                "nfs" -> {
                    SmbUtils.openNfsFileInputStream(danmakuUri, "video")
                }

                else -> {
                    // 不支持的 scheme
                    Log.w("VideoPlayerScreen", "Unsupported scheme for danmaku URI: $danmakuUri")
                    null
                }
            }

            // 使用输入流解析弹幕 XML
            inputStream.use { stream ->
                val danmakuResponse: DanmakuResponse = getDanmakuXmlFromFile(stream)
                danmakuDataList = danmakuResponse.data
                isDanmakuLoaded = true
                Log.i(
                    "VideoPlayerScreen",
                    "Loaded ${danmakuDataList?.size ?: 0} danmaku items from $danmakuUri"
                )
            }
        } catch (e: Exception) {
            Log.e("VideoPlayerScreen", "Failed to load danmaku from $danmakuUri", e)
            isDanmakuLoaded = false // 标记加载失败
            // 可以在这里设置一个状态变量来在UI上显示加载失败
        }
    }

    // 将加载的弹幕数据发送到播放器
    // 监听播放器状态变化来启动/暂停弹幕
    var hasSentDanmaku by remember { mutableStateOf(false) }
    LaunchedEffect(isDanmakuLoaded, danmakuDataList, isPlaying, contentCurrentPosition) {
        // 如果弹幕已加载且尚未发送给播放器
        if (isDanmakuLoaded && !hasSentDanmaku && danmakuDataList != null) {
            Log.d("danmakuData", "状态弹幕")
            // 将 DanmakuData 转换为 DanmakuItemData
            val danmakuItemDataList = danmakuDataList?.map { danmakuData ->
                DanmakuItemData(
                    danmakuId = if (danmakuData.rowId != 0L) danmakuData.rowId else (Math.random() * 100000000).toLong(), // 使用解析的ID或生成随机ID
                    position = (danmakuData.time * 1000).toLong(), // 时间戳转换为毫秒
                    content = danmakuData.content, // 弹幕内容
                    mode = when (danmakuData.mode) { // 映射弹幕模式
                        4 -> DanmakuItemData.DANMAKU_MODE_CENTER_TOP
                        5 -> DanmakuItemData.DANMAKU_MODE_CENTER_BOTTOM
                        else -> DanmakuItemData.DANMAKU_MODE_ROLLING
                    },
                    textSize = danmakuData.size, // 字体大小
                    textColor = danmakuData.color, // 颜色
                )
            }
            // 更新弹幕播放器的数据
            if (danmakuItemDataList != null) {
                mDanmakuPlayer.updateData(danmakuItemDataList)
            }

            hasSentDanmaku = true
            Log.i(
                "VideoPlayerScreen",
                "Sent ${danmakuDataList?.size ?: 0} danmaku items to player."
            )
        }
    }


    // 网速监控协程
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // 初始化网络统计
            lastTotalRxBytes = TrafficStats.getTotalRxBytes()
            lastTimeStamp = System.currentTimeMillis()

            while (isPlaying) {
                delay(1000) // 每秒更新一次

                val currentRxBytes = TrafficStats.getTotalRxBytes()
                val currentTimeStamp = System.currentTimeMillis()

                // 计算网速 (bytes/second)
                val timeDiff = currentTimeStamp - lastTimeStamp
                if (timeDiff > 0) {
                    val byteDiff = currentRxBytes - lastTotalRxBytes
                    networkSpeed = (byteDiff * 1000) / timeDiff

                    // 更新状态
                    lastTotalRxBytes = currentRxBytes
                    lastTimeStamp = currentTimeStamp
                }
            }
        }
    }

    // 定义自定义字幕样式
    val customSubtitleStyle = TextStyle(
        color = Color(settingsState.subColor), // 字幕颜色为白色
        fontSize = settingsState.subFontSize.sp,     // 字体大小
        shadow = Shadow(
            color = Color.Black, // 黑色阴影
            offset = Offset(3f, 3f),
            blurRadius = 1f
        ),
    )

    // 监听 ExoPlayer 播放状态变化，并更新 `isPlaying` 状态
    // 同时定期更新 `contentCurrentPosition`
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            contentCurrentPosition = player.currentPosition
            isPlaying = player.isPlaying
        }
    }


    LaunchedEffect(isPlayerPlaying) {
        if (videoPlayerViewModel.danmakuVisibility) {
            if (isPlayerPlaying) {
                // 启动弹幕
                videoPlayerViewModel.danmakuConfig = getDanmakuConfig()
                mDanmakuPlayer.updateConfig(videoPlayerViewModel.danmakuConfig)
                mDanmakuPlayer.start()
                mDanmakuPlayer.seekTo(player.currentPosition)
            } else {
                mDanmakuPlayer.pause()
            }
        }
    }
    // 2. 监听准备就绪状态处理历史记录
    LaunchedEffect(playerStatus) {
        if (playerStatus == VideoPlayerStatus.READY) {
            isFirstLoad = false // 👇 新增：第一次准备好后，取消首次加载遮罩
            videoPlayerViewModel.updatePlayerStatus(VideoPlayerStatus.READY)

            if (historySeekPos > 0 && !hasTriggeredTimer) {
                player.seekTo(historySeekPos)
                showHistoryTip = true
                hasTriggeredTimer = true
            }
        } else if (playerStatus == VideoPlayerStatus.READY) {
// 🔥 在这里关闭加载框
            isFirstLoad = false
            videoPlayerViewModel.updatePlayerStatus(playerStatus)
        } else {
            videoPlayerViewModel.updatePlayerStatus(playerStatus)
        }
    }
    // 3. 设置错误回调
    DisposableEffect(player) {
        player.onError = { msg ->
            videoPlayerViewModel.setPlayerError(msg)
        }
        player.onCuesChanged = { cues ->
            currentCueGroup = cues as CueGroup?
            // 处理字幕显示逻辑
        }
        onDispose {
            player.onError = null
            player.onCuesChanged = null
        }
    }
    // 专门负责提示框的自动倒计时消失

    // 主 UI 布局 - 保持 PlayerView 作为底层渲染，状态覆盖层在上层
    Box(
        Modifier
            // 添加 D-Pad 事件处理
            .dPadEvents(
                player,
                videoPlayerState,
                pulseState,
                videoPlayerViewModel
            )
            .background(Color(0, 0, 0)) // 黑色背景
            .focusable() // 可获得焦点
            .fillMaxHeight()
            .fillMaxWidth()
    ) {

        // 创建焦点请求器
        val focusRequester = remember { FocusRequester() }
        val reRequester = remember { FocusRequester() }
        var videoSizePx by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current.density

        val videoSizeDp = with(density) {
            IntSize(
                width = (videoSizePx.width / density).toInt(),
                height = (videoSizePx.height / density).toInt()
            )
        }
        // 在 VideoPlayerScreen.kt 中修改或添加以下 LaunchedEffect
        LaunchedEffect(showHistoryTip) {
            if (showHistoryTip) {
                // 给一点点时间让 AnimatedVisibility 把内容挂载到树上
                delay(100)
                try {
                    reRequester.requestFocus()
                } catch (e: Exception) {
                    Log.e("VideoPlayer", "Focus request failed", e)
                }

                // 原有的 5 秒后自动消失逻辑
                delay(5000)
                showHistoryTip = false
            }
        }

        // AndroidView 包裹 PlayerView，用于显示视频 - 这是关键，确保底层渲染不受干扰
//        AndroidView(
//            factory = { context ->
//                PlayerView(context).apply {
//                    useController = false // 禁用默认控制器
//                    player = exoPlayer // 设置 ExoPlayer
//                    // 根据 ViewModel 状态设置字幕视图可见性
//                    subtitleView?.visibility = videoPlayerViewModel.isSubtitleViewVis
//                }
//            },
//            update = { playView ->
//                // 更新 PlayerView
//                playView.player = exoPlayer
//                //playView.resizeMode
//                playView.subtitleView?.visibility = videoPlayerViewModel.isSubtitleViewVis
//            },
//
//            modifier = Modifier
//                .fillMaxSize()
//                .onSizeChanged { size ->
//                    videoSizePx = size
//                    Log.d("playViewSize", videoSizePx.toString())
//                }, // 填充整个父容器
//            onRelease = {
//                // 释放资源
//                exoPlayer.release()
//            }
//        )
        player.PlayerView(modifier = Modifier
            .align(Alignment.Center)
            .onSizeChanged { size ->
                videoSizePx = size
                Log.d("playViewSize", videoSizePx.toString())
            })

        // 显示 SRT/PSG/ ASS(ASS暂时由PlayerView显示,SubtitleView不显示)
        if (videoPlayerViewModel.isCusSubtitleViewVis && !useVlc) {
            // 字幕视图，显示 SRT/PSG/ASS 字幕 (从 CueGroup 中获取)
            SubtitleView(
                cueGroup = currentCueGroup, // 传递当前字幕组
                subtitleStyle = customSubtitleStyle, // 使用自定义字幕样式(只影响srt字幕)
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = settingsState.subBottomPadding.dp), // 底部居中对齐(只影响srt字幕)
                videoSizeDp = videoSizeDp,
                backgroundColor = Color(settingsState.subBgColor),// 背景色(只影响srt字幕)
                sourceVideoHeight = player.videoHeight,
                sourceVideoWidth = player.videoWidth,
                forcePGSCenter = settingsState.forcePgsCenter

            )
        }

        //弹幕层
        AkDanmakuPlayer(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter), // 顶部居中对齐
            danmakuPlayer = mDanmakuPlayer // 传递弹幕播放器实例
        )

        // 实时网速显示
        if (isPlaying && !settingsState.hideNetworkSpeed) {
            NetworkSpeedIndicator(
                networkSpeed = networkSpeed, // 传递网络速度
                modifier = Modifier
                    .align(Alignment.TopEnd) // 右上角对齐
                    .padding(16.dp) // 内边距
            )
        }

        // 视频播放器覆盖层 (包含控制按钮等)
        VideoPlayerOverlay(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onFocusChanged {
                    videoPlayerViewModel.conFocus =
                        it.isFocused
                }, // 底部居中
            focusRequester = focusRequester, // 焦点请求器
            state = videoPlayerState, // 播放器状态
            isPlaying = isPlaying, // 播放状态
            centerButton = { VideoPlayerPulse(pulseState) }, // 中心脉冲按钮
            subtitles = { }, // 子标题 (未实现)
            controls = {
                // 控制按钮区域
                VideoPlayerControls(
                    isPlaying, // 播放状态
                    contentCurrentPosition, // 当前位置
                    player, // ExoPlayer 实例
                    videoPlayerState, // 播放器状态
                    focusRequester, // 焦点请求器
                    fileName, // 标题 (示例)
                    playerStatus.toString(), // 副标题 (示例)
                    "2022/1/20", // 第三行文本 (示例)
                    videoPlayerViewModel, // ViewModel
                    mDanmakuPlayer, // 弹幕播放器
                    settingsManager, // 弹幕设置管理器，用于保存可见性状态
                    ::getDanmakuConfig // 传递获取配置的方法
                )
            },
            atpFocus = videoPlayerViewModel.atpFocus // 音轨/字幕面板焦点状态
        )
        LaunchedEffect(Unit) {
            reRequester.requestFocus()
        }
        // 历史记录跳转提示浮窗
        AnimatedVisibility(
            visible = showHistoryTip,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, bottom = 150.dp) // 避开进度条位置
        ) {
            Button(
                modifier = Modifier.focusRequester(reRequester),
                onClick = {
                    player.seekTo(0L)
                    reRequester.freeFocus()
                    showHistoryTip = false

                },
                colors = myIconButtonColor()
            ) {
                val minutes = (historySeekPos / 1000 / 60).toString().padStart(2, '0')
                val seconds = (historySeekPos / 1000 % 60).toString().padStart(2, '0')
                Text(
                    text = "您上次看到 $minutes:$seconds 点击此处从头播放",
                    fontSize = 14.sp
                )
            }
        }
        // 显示 "再按一次退出" Toast
        if (showToast) {

            Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
            showToast = false
        }

        // 处理双击返回退出逻辑
        LaunchedEffect(key1 = backPressState) {
            if (backPressState == BackPress.InitialTouch) {
                delay(2000) // 2秒延迟
                backPressState = BackPress.Idle // 重置状态
            }
        }
        LaunchedEffect(videoPlayerViewModel.conFocus) {
            Log.d("conFocus", videoPlayerViewModel.conFocus.toString())
        }
//        BackHandler(backPressState == BackPress.Idle) { // 移除 !videoPlayerState.controlsVisible 条件
//
//
//                // 如果控制栏未显示，则执行退出逻辑
//
//                backPressState = BackPress.InitialTouch
//
//                showToast = true
//
//        }
        BackHandler(backPressState == BackPress.Idle) {
//            if (videoPlayerState.controlsVisible && !videoPlayerViewModel.conFocus) {
//                // 如果控制栏显示，则隐藏控制栏
//                //videoPlayerState.hideControls()
//            } else if (videoPlayerState.controlsVisible ){
//                // 如果控制栏不显示，则执行退出逻辑
//                videoPlayerState.hideControls()
//            }else{
            if (backPressState == BackPress.Idle && !videoPlayerState.controlsVisible) {
                backPressState = BackPress.InitialTouch
                showToast = true
            }


        }
        BackHandler(videoPlayerState.controlsVisible) {
            if (!videoPlayerViewModel.conFocus) {
                videoPlayerState.hideControls()
            }
        }


        // 音轨/字幕选择面板的动画可见性
        AnimatedVisibility(
            videoPlayerViewModel.atpVisibility, // 根据 ViewModel 状态显示/隐藏
            enter = fadeIn(), // 淡入动画
            exit = fadeOut(), // 淡出动画
            modifier = Modifier
                .widthIn(200.dp, 420.dp) // 宽度范围
                .fillMaxHeight() // 高度范围
                .align(AbsoluteAlignment.CenterRight) // 右侧居中
                // 向左偏移
                .background(
                    Color.Black.copy(0.8f), shape = RoundedCornerShape(2.dp) // 半透明黑色背景和圆角
                )
                // 处理 D-Pad 事件
                .handleDPadKeyEvents(
                    onRight = {
                        true
                    },
                    onUp = {
                        true
                    },
                    onDown = {
                        true
                    }
                )
                // 处理焦点变化
                .onFocusChanged {
                    if (it.isFocused) {
                        videoPlayerViewModel.atpFocus = it.isFocused

                    } else {
                        videoPlayerState.hideControls() // 隐藏控制栏
                        videoPlayerViewModel.atpFocus = it.isFocused
                    }
                }) {
            // 根据 ViewModel 中的选择显示不同的面板
            when (videoPlayerViewModel.selectedAorVorS) {
                "A" -> AudioTrackPanel(
                    lists = audioTracks, onTrackSelected = { track ->
                        player.selectAudioTrack(track)
                    }

                )

                "V" -> VideoTrackPanel(lists = videoTracks, onTrackSelected = { track ->
                    player.selectVideoTrack(track)
                })

                "D" -> DanmakuPanel(
                    mDanmakuPlayer, // 弹幕播放器
                    videoPlayerViewModel,
                )

                else -> {
                    SubtitleTrackPanel(
                        lists = subtitleTracks, onTrackSelected = { track ->
                            player.selectSubtitleTrack(track)
                        }
                    )
                }
            }
            // 处理返回键，隐藏面板
            BackHandler(true) {
                videoPlayerViewModel.atpVisibility = false
            }
        }

        when (val status = playerStatus) {
            is VideoPlayerStatus.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f) // 确保在最上层
                        .background(Color.Black)
                ) {
                    VAErrorScreen((playerStatus as VideoPlayerStatus.Error).toString())
                }
            }

            is VideoPlayerStatus.BUFFERING -> {
                // 显示播放中途的缓冲状态 (小窗)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                ) {
                    LoadingScreen(
                        "正在缓冲",
                        modifier = Modifier
                            .width(90.dp)
                            .height(95.dp)
                            .align(Alignment.Center)
                            .background(
                                Color.Black.copy(0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        fontSize = 16,
                        36
                    )
                }
            }

            else -> {}
        }
        if (isFirstLoad) {
            // 显示首次加载状态 (全屏)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                LoadingScreen(
                    "正在加载中 请勿操作",
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }
    }


}

/**
 * 网速显示组件
 *
 * @param networkSpeed 当前网络速度 (bytes/sec)
 * @param modifier 修饰符
 */
@Composable
fun NetworkSpeedIndicator(networkSpeed: Long, modifier: Modifier = Modifier) {
    // 格式化网速显示文本
    val speedText = when {
        networkSpeed < 1024 -> "$networkSpeed B/s"
        networkSpeed < 1024 * 1024 -> "${
            String.format(
                Locale.getDefault(),
                "%.1f",
                networkSpeed / 1024.0
            )
        } KB/s"

        else -> "${
            String.format(
                Locale.getDefault(),
                "%.1f",
                networkSpeed / (1024.0 * 1024.0)
            )
        } MB/s"
    }

    // 绘制包含网速文本的 Box
    Box(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            ) // 半透明黑色背景和圆角
            .padding(horizontal = 8.dp, vertical = 4.dp) // 内边距
    ) {
        Text(
            text = speedText, // 显示的文本
            color = Color.White, // 文本颜色
            fontSize = 14.sp // 字体大小
        )
    }
}

/**
 * 为 Modifier 添加 D-Pad 事件处理
 *
 * @param IMzPlayer IMzPlayer 实例
 * @param videoPlayerState 视频播放器状态
 * @param pulseState 脉冲状态
 * @param videoPlayerViewModel ViewModel
 * @return 添加了事件处理的 Modifier
 */
private fun Modifier.dPadEvents(
    player: IMzPlayer, // 这里把 ExoPlayer 换成 IMzPlayer
    videoPlayerState: VideoPlayerState,
    pulseState: VideoPlayerPulseState,
    videoPlayerViewModel: VideoPlayerViewModel
): Modifier = this
    .handleDPadKeyEvents(
        onLeft = {
            // 如果控制栏未显示，则快退
            if (!videoPlayerState.controlsVisible) {
                player.seekBack() // 调用接口方法
                pulseState.setType(VideoPlayerPulse.Type.BACK)
            }
        },
        onRight = {
            // 如果控制栏未显示，则快进
            if (!videoPlayerState.controlsVisible) {
                player.seekForward() // 调用接口方法
                pulseState.setType(VideoPlayerPulse.Type.FORWARD)
            }
        },
        onUp = {
            if (!videoPlayerState.controlsVisible) {
                videoPlayerViewModel.atpVisibility = true
                videoPlayerViewModel.selectedAorVorS = "A"
            }
        },
        onDown = {
            if (!videoPlayerState.controlsVisible) {
                videoPlayerViewModel.atpVisibility = true
                videoPlayerViewModel.selectedAorVorS = "D"
            }
        },
        onEnter = {
            // 暂停播放并显示控制栏
            player.pause() // 调用接口方法
            videoPlayerState.showControls()
        },
    )
    .onKeyEvent { keyEvent ->
        // 这里的逻辑主要是控制 UI 显示隐藏，不涉及播放器具体实现，保持原样即可
        when (keyEvent.key) {
            Key.Menu, Key.ButtonY -> {
                if (!videoPlayerState.controlsVisible && !videoPlayerViewModel.atpVisibility) {
                    videoPlayerState.showControls()
                    true
                } else false
            }

            else -> {
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
                    if (!videoPlayerState.controlsVisible && !videoPlayerViewModel.atpVisibility) {
                        videoPlayerState.showControls()
                        true
                    } else false
                } else false
            }
        }
    }

/**
 * 视频播放器控制按钮区域
 *
 * @param isPlaying 当前播放状态
 * @param contentCurrentPosition 当前播放位置
 * @param exoPlayer ExoPlayer 实例
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
    danmakuPlayer: DanmakuPlayer,
    settingsManager: DanmakuSettingsManager, // 添加设置管理器参数
    getDanmakuConfig: () -> DanmakuConfig // 添加获取配置的方法参数
) {
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
                    icon = painterResource(id = R.drawable.baseline_hd_24), // 高清图标
                    state = state,
                    isPlaying = isPlaying,
                    onClick = {
                        // 点击高清图标，显示视频轨道选择面板
                        videoPlayerViewModel.selectedAorVorS = "V"
                        videoPlayerViewModel.atpVisibility = !videoPlayerViewModel.atpVisibility;
                        focusRequester.requestFocus()
                    }
                )
                VideoPlayerControlsIcon(
                    modifier = Modifier.padding(start = 12.dp),
                    icon = painterResource(id = R.drawable.baseline_speaker_24), // 音频图标
                    state = state,
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
                    onClick = {
                        // 点击字幕图标，显示字幕轨道选择面板
                        videoPlayerViewModel.selectedAorVorS = "D"
                        videoPlayerViewModel.atpVisibility = !videoPlayerViewModel.atpVisibility;
                        focusRequester.requestFocus()
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

// 返回按钮按压状态枚举
sealed class BackPress {
    data object Idle : BackPress()
    data object InitialTouch : BackPress()
}




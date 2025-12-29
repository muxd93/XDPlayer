package org.mz.mzdkplayer.ui.audioplayer

import AudioPlayerControls
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.palette.graphics.Palette
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.mz.mzdkplayer.data.model.AudioInfo
import org.mz.mzdkplayer.data.model.AudioItem
import org.mz.mzdkplayer.data.model.MediaHistoryRecord

import org.mz.mzdkplayer.tool.SmbUtils
import org.mz.mzdkplayer.tool.Tools
import org.mz.mzdkplayer.tool.Tools.saveCoverImageToInternalStorage
import org.mz.mzdkplayer.tool.createArtworkBitmap
import org.mz.mzdkplayer.tool.extractAudioInfoAndLyricsFromStream
import org.mz.mzdkplayer.tool.handleDPadKeyEvents

import org.mz.mzdkplayer.ui.audioplayer.components.*


import org.mz.mzdkplayer.ui.screen.vm.AudioPlayerViewModel
import org.mz.mzdkplayer.ui.screen.vm.AudioViewModel
import org.mz.mzdkplayer.ui.screen.vm.MediaHistoryViewModel
import org.mz.mzdkplayer.ui.videoplayer.BackPress


import java.io.InputStream
import java.net.URL
import java.util.Locale

import kotlin.time.Duration.Companion.milliseconds

// --- 主要 Composable ---

@OptIn(UnstableApi::class)
@Composable
fun AudioPlayerScreen(
    mediaUri: String,
    dataSourceType: String,
    fileName: String,
    extraList: List<AudioItem>,
    currentIndex: String,
    connectionName: String,
    mediaHistoryViewModel: MediaHistoryViewModel,
    audioViewModel: AudioViewModel
) {
    val context = LocalContext.current
    val exoPlayer = rememberAudioPlayer(context, mediaUri, dataSourceType)
    val audioPlayerState = rememberAudioPlayerState(hideSeconds = 6)
    val audioPlayerViewModel: AudioPlayerViewModel = viewModel()
    var showToast by remember { mutableStateOf(false) }
    var backPressState by remember { mutableStateOf<BackPress>(BackPress.Idle) }
    var contentCurrentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying: Boolean by remember { mutableStateOf(exoPlayer.isPlaying) }
    var sampleMimeType: String by remember { mutableStateOf("") }

    var audioInfo: AudioInfo? by remember { mutableStateOf(null) } // 存储 audioInfo，包含所有音频信息
    var currentMediaUri by remember { mutableStateOf(mediaUri) }
    var currentFileName by remember { mutableStateOf(fileName) } // 替换原来的 fileName 状态
    var isAudioInfoLoading by remember { mutableStateOf(false) } // 添加加载状态

    // 添加缓存状态，用于在单曲循环时恢复音频信息
    var cachedAudioInfo by remember { mutableStateOf<AudioInfo?>(null) }
// 获取 audioSessionId
    val audioSessionId = remember(exoPlayer) { exoPlayer.audioSessionId }
    // 添加Seek状态，用于跟踪快速Seek操作
    var isSeeking by remember { mutableStateOf(false) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }
// 1. 定义一个状态来存储动态变化的 AudioSessionId
    var currentAudioSessionId by remember { mutableIntStateOf(exoPlayer.audioSessionId) }
    BuilderMzAudioPlayer(
        context,
        currentMediaUri,
        exoPlayer,
        dataSourceType,
        extraList,
        currentIndex,
        audioPlayerViewModel
    )

    DisposableEffect(Unit) {
        onDispose {
            // 1. 获取播放器当前状态
            val currentPos = exoPlayer.currentPosition
            val totalDur = exoPlayer.duration
            if (currentPos > 0 && totalDur > 0) {
                val record = MediaHistoryRecord(
                    mediaUri = mediaUri,
                    fileName = fileName,
                    playbackPosition = currentPos,
                    mediaDuration = totalDur,
                    // 处理协议名称显示的逻辑
                    protocolName = if (dataSourceType == "LOCAL") "本地文件" else dataSourceType,
                    connectionName = connectionName,
                    serverAddress = "test", // 如果你有真实的 server IP，请传入，否则留空或用占位符
                    mediaType = "AUDIO",    // 明确标记为视频
                    timestamp = System.currentTimeMillis()
                )
                // 3. 调用 ViewModel 保存 (ViewModel 内部会启动协程写入数据库)
                mediaHistoryViewModel.saveHistory(record)
            }

            exoPlayer.release()
        }
    }
// 1. 状态：是否有录音权限
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 2. 权限启动器
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "没有录音权限，将无法显示频谱动画", Toast.LENGTH_LONG).show()
        }
    }

    // 3. 进入页面时自动检查并申请
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // 加载音频信息和歌词 - 使用 currentMediaUri 作为依赖项
    LaunchedEffect(currentMediaUri, audioPlayerViewModel.selectedAtIndex) {
        Log.d("AudioPlayerScreen", "Loading audio info for URI: $currentMediaUri")
        isAudioInfoLoading = true
        val cachedEntity = withContext(Dispatchers.IO) {
            audioViewModel.getAudioCacheByUri(currentMediaUri)
        }
        if (cachedEntity != null && cachedEntity.isDetailsLoaded&& cachedEntity.sampleRate.isNotBlank()) {
            Log.d("AudioPlayerScreen", "使用数据库缓存元数据")
            // 将数据库实体转换为 AudioInfo 对象
            audioInfo = AudioInfo(
                title = cachedEntity.title,
                artist = cachedEntity.artist,
                album = cachedEntity.album,
                durationSeconds = cachedEntity.duration / 1000, // 注意单位转换
                lyrics = cachedEntity.lyrics,
                artworkData = null, // 此时没有字节流
                localCoverPath = cachedEntity.localCoverPath,
                bit = cachedEntity.bit,
                bitsPerSample = cachedEntity.bitsPerSample,
                sampleRate = cachedEntity.sampleRate
                // 我们可以在 AudioInfo 里临时存一下这个 path，或者通过其他 State 传递
            ) // 如果你给 AudioInfo 加了字段的话
            isAudioInfoLoading = false
            // 如果已经加载好了，直接跳过后面的流解析逻辑
            return@LaunchedEffect
        }
        // --- 缓存判断结束 --
        // 获取当前媒体项的 MIME 类型 - 改进逻辑
        var mimeType: String? = null

        // 首先尝试从文件扩展名推断
        val fileExtension = currentMediaUri.substringAfterLast(".", "").lowercase()
        val mimeTypeFromExtension = when (fileExtension) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            else -> null
        }

        Log.e("sampleMimeType1", currentMediaUri + (mimeTypeFromExtension ?: "null"))

        // 尝试从播放器获取实际 MIME 类型
        var retryCount = 0
        val maxRetries = 10 // 最多重试10次，每次等待100ms，总共1秒
        while (retryCount < maxRetries) {
            delay(100) // 等待100ms让播放器准备好
            val playerMimeType = exoPlayer.audioFormat?.sampleMimeType
            if (playerMimeType != null) {
                mimeType = playerMimeType
                break
            }
            retryCount++
        }

        Log.e("sampleMimeType2", currentMediaUri + (mimeType ?: "null"))

        // 如果播放器获取失败，使用从扩展名推断的 MIME 类型
        if (mimeType == null) {
            mimeType = mimeTypeFromExtension ?: "audio/mpeg" // 默认为 mp3
        }

        Log.e("sampleMimeType3", currentMediaUri + mimeType)

        withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? =
                    when (currentMediaUri.toUri().scheme?.lowercase()) {
                        "smb" -> SmbUtils.openSmbFileInputStream(
                            currentMediaUri.toUri(),
                            mimeType
                        )

                        "http", "https" -> {
                            when (dataSourceType) {
                                "WEBDAV" -> SmbUtils.openWebDavFileInputStream(
                                    currentMediaUri.toUri(),
                                    mimeType
                                )

                                "HTTP" -> SmbUtils.openHTTPLinkXmlInputStream(
                                    currentMediaUri,
                                    mimeType
                                )

                                else -> URL(currentMediaUri).openStream()
                            }
                        }

                        "file" -> context.contentResolver.openInputStream(currentMediaUri.toUri())
                            ?: throw java.io.IOException("Could not open file input stream for $currentMediaUri")

                        "ftp" -> SmbUtils.openFtpFileInputStream(
                            currentMediaUri.toUri(),
                            mimeType
                        )

                        "nfs" -> SmbUtils.openNfsFileInputStream(
                            currentMediaUri.toUri(),
                            mimeType
                        )

                        else -> {
                            Log.w(
                                "AudioPlayerScreen",
                                "Unsupported scheme for URI: $currentMediaUri"
                            )
                            null
                        }
                    }

                Log.d(
                    "AudioPlayerScreen",
                    "Opened input stream for $currentMediaUri with MIME: $mimeType"
                )

                inputStream?.use { stream ->
                    // 关键：调用工具函数获取 audioInfo，其中包含 lyrics
                    // 1. 【IO线程】耗时操作：解析流
                    val info = extractAudioInfoAndLyricsFromStream(context, stream, mimeType)

                    // 注意：虽然 Compose 允许在后台线程更新 State，但为了安全最好也在 Main 更新
                    withContext(Dispatchers.Main) {
                        audioInfo = info
                        cachedAudioInfo = info
                    }
                    // 2. 拿到 info 后，保存封面并回写数据库
                    if (info != null) {
                        Log.i("AudioPlayerScreen", "开始回写元数据")
                        // 3. 【切换回 Main 线程】获取播放器信息并提交
                        // 2. 【IO线程】耗时操作：保存图片到本地 (这是文件操作，必须在 IO 线程)
                        val savedCoverPath = saveCoverImageToInternalStorage(
                            context,
                            currentMediaUri,
                            info.artworkData
                        )
                        withContext(Dispatchers.Main) {
                            Log.i("AudioPlayerScreen", "切回主线程准备回写")

                            // 只有在主线程才能安全访问 exoPlayer
                            val currentDuration =
                                if (exoPlayer.duration != C.TIME_UNSET) exoPlayer.duration else 0L

                            // ViewModel 的调用可以在主线程，因为它内部自己会 launch(Dispatchers.IO)
                            audioViewModel.updateAudioInfo(
                                uri = currentMediaUri,
                                info = info,
                                localCoverPath = savedCoverPath,
                                duration = currentDuration,
                                isDetailsLoaded = true
                            )
                            Log.i("AudioPlayerScreen", "已触发 ViewModel 回写")
                        }
                        //Log.i("AudioPlayerScreen", "已回写元数据到数据库")
                    }
                    Log.i("AudioPlayerScreen", "Loaded audio info and lyrics for $currentMediaUri")
                } ?: Log.e("AudioPlayerScreen", "Failed to open input stream for $currentMediaUri")
            } catch (e: Exception) {
                Log.e(
                    "AudioPlayerScreen",
                    "Failed to load audio info or lyrics from $currentMediaUri",
                    e
                )
            } finally {
                isAudioInfoLoading = false
            }
        }
    }


    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        sampleMimeType = exoPlayer.audioFormat?.sampleMimeType.toString()
                        Log.d("AudioPlayerScreen", "Player ready, MIME type: $sampleMimeType")
                    }

                    Player.STATE_BUFFERING -> {}
                    Player.STATE_ENDED -> {
                        // 播放结束时，如果是单曲循环模式，恢复音频信息
                        if (exoPlayer.repeatMode == Player.REPEAT_MODE_ONE) {
                            if (cachedAudioInfo != null) {
                                audioInfo = cachedAudioInfo
                            }
                        }
                    }

                    Player.STATE_IDLE -> {}

                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                Log.d("onMediaItemTransition", mediaItem?.localConfiguration?.uri.toString())

                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    val newUri = mediaItem?.localConfiguration?.uri?.toString() ?: return
                    Log.d("onMediaItemTransition", "Transitioning to new URI: $newUri")
                    currentMediaUri = newUri
                    audioInfo = null // 重置音频信息，触发重新加载
                    isAudioInfoLoading = true // 设置加载状态
                    currentFileName = Tools.extractFileNameFromUri(newUri)

                    // ✅ 查找当前 URI 在 extraList 中的索引
                    val newIndex = extraList.indexOfFirst { it.uri == newUri }
                    if (newIndex != -1) {
                        audioPlayerViewModel.selectedAtIndex = newIndex
                    } else {
                        Log.w("AudioPlayerScreen", "Could not find index for URI: $newUri")
                    }
                } else {
                    Log.d("onMediaItemTransition", "Repeat transition - keeping current audio info")
                }
            }

            override fun onIsPlayingChanged(isExoPlaying: Boolean) {
                isPlaying = isExoPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Log.e("AudioPlayerScreen", "Player error: ${error.message}", error)

                // 如果是FLAC格式的解析错误，尝试使用通用音频格式
                if (error.cause?.message?.contains("contentIsMalformed") == true) {
                    Log.w("AudioPlayerScreen", "FLAC parsing error detected, continuing...")
                    // 不要中断播放，只是记录错误
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                // 当发生位置跳跃时，标记正在Seek
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    isSeeking = true
                    lastSeekTime = System.currentTimeMillis()
                }
            }

        })
    }
// 2. 注册监听器，专门监听 SessionId 的变化
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                super.onAudioSessionIdChanged(audioSessionId)
                Log.d("audioSessionIdC", "Got new ID: $audioSessionId")
                // 当 ID 变化且有效时，更新状态
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    currentAudioSessionId = audioSessionId
                }
            }
        }

        exoPlayer.addListener(listener)

        // 再次检查初始值（防止监听器绑定前ID已经生成）
        if (exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            currentAudioSessionId = exoPlayer.audioSessionId
        }

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }
    // 处理播放进度更新，使用防抖机制避免快速Seek时的问题
    LaunchedEffect(exoPlayer) {
        var lastUpdateTime = System.currentTimeMillis()
        val debounceDelay = 300L // 300ms防抖延迟

        while (true) {

            delay(100) // 检查间隔改为100ms

            val currentTime = System.currentTimeMillis()
            val currentPosition = exoPlayer.currentPosition

            // 检查是否仍在Seek状态中
            val isCurrentlySeeking = isSeeking && (currentTime - lastSeekTime < 600) // 1秒内认为仍在Seek

            // 只有当时间间隔超过防抖延迟且不在Seek状态时才更新位置
            if (!isCurrentlySeeking && currentTime - lastUpdateTime > debounceDelay) {
                contentCurrentPosition = currentPosition
                lastUpdateTime = currentTime
            }

            // 如果Seek状态持续超过1秒，重置Seek状态
            if (isSeeking && currentTime - lastSeekTime > 600) {
                isSeeking = false
            }
        }
    }

    // 处理Seek结束状态
    LaunchedEffect(contentCurrentPosition) {
        // 当位置更新时，重置Seek状态
        if (isSeeking) {
            isSeeking = false
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
    BackHandler(backPressState == BackPress.Idle) {
        backPressState = BackPress.InitialTouch
        showToast = true
    }

    LaunchedEffect(audioSessionId) {
        Log.d("audioSessionId", audioSessionId.toString())
    }
    val pulseState = rememberAudioPlayerPulseState()
    val focusRequester = remember { FocusRequester() }

    // 1. 处理封面图（前景小图）：使用 remember 缓存 bitmap
    val coverBitmap: Bitmap? = remember(audioInfo, currentMediaUri) {
        val data = audioInfo?.artworkData
        val path = audioInfo?.localCoverPath
        Log.d("AudioPlayerScreen",path.toString())
        if (data != null && data.isNotEmpty()) {
            createArtworkBitmap(data)
        } else if (!path.isNullOrEmpty()) {
            BitmapFactory.decodeFile(path)
        } else {
            null
        }
    }

    // 4. 根布局 Box
    Box(
        Modifier
            // 处理遥控器按键事件 (保留你原有的逻辑)
            .dPadEvents(
                exoPlayer,
                audioPlayerState,
                pulseState,
                audioPlayerViewModel,
                focusRequester
            )
            .fillMaxSize()
    )
    {
        // --- 背景层：使用 Coil 3 + AnimatedContent 替换原有的 Blur ---
        AnimatedContent(
            targetState = audioInfo, // 当音频信息变化时触发背景切换
            transitionSpec = {
                fadeIn(animationSpec = tween(700)) togetherWith fadeOut(animationSpec = tween(700))
            },
            label = "BackgroundAnimation",
            modifier = Modifier.fillMaxSize()
        ) { info ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 优先使用本地路径，其次使用字节数组
                val model = info?.localCoverPath?.ifEmpty { null } ?: info?.artworkData

                if (model != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(model)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = 0.6f // 稍微降低原图亮度
                    )
                } else {
                    // 兜底深色背景
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1C1C1C)))
                }

                // === 渐变遮罩层 (关键修改) ===
                // 1. 全局深色压暗：保证整体文字可读性，不需要像电影界面那么亮
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )

                // 2. 垂直渐变：底部加深 (保护控制按钮区域)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black.copy(alpha = 0.95f)
                                ),
                                startY = 300f // 从中间开始往下渐变
                            )
                        )
                )

                // 3. 水平渐变：右侧加深 (保护滚动歌词区域)
                // 歌词通常是白色的，背景需要足够暗
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent, // 左侧保持背景图可见度较高
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.8f) // 右侧歌词区域加深
                                ),
                                startX = 400f // 从中间偏左开始向右渐变
                            )
                        )
                )
            }
        }

        // 放在最底层作为氛围装饰 (保持不变)
        if (isPlaying && currentAudioSessionId > 0&& hasAudioPermission) {
            AudioVisualizer(
                audioSessionId = currentAudioSessionId,
                isPlaying = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(200.dp) // 高度调高一点，做背景
                    .alpha(0.15f),  // 透明度调低，不要喧宾夺主
                barCount = 100,
            )
        }

        // --- 内容层：左右分栏布局 (Apple Music 风格) ---
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [左侧栏]：占 40% 宽度，放置封面和文字信息
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Top, // 垂直居中
                horizontalAlignment = Alignment.Start     // 左对齐
            )
            {
                // 专辑封面容器
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f) // 宽度占左栏的 85%
                        .aspectRatio(1f)     // 强制保持正方形 (1:1)
                        .padding(bottom = 0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 使用你原本的封面组件 (前景小图依然使用 Bitmap)
                    AlbumCoverDisplay(coverBitmap, isPlaying)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.Start // 确保左对齐
                ) {
                    // 歌曲标题
                    Text(
                        text = if (isAudioInfoLoading) currentFileName else audioInfo?.title
                            ?: currentFileName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 歌手和专辑信息
                    Text(
                        text = buildString {
                            append(
                                if (isAudioInfoLoading) "加载中..." else audioInfo?.artist ?: "未知歌手"
                            )
                            if (!audioInfo?.album.isNullOrEmpty()) {
                                append(" — ${audioInfo?.album}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.7f) //稍微灰一点
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ✅ 新增：苹果风格音频参数徽章
                    AudioInfoBadge(audioInfo)
                    Spacer(modifier = Modifier.height(12.dp))
                    // --- 浮层：底部控制栏 ---
                    // 获取安全的时长，避免除以0错误
                    val safeDurationMs =
                        if (exoPlayer.duration != C.TIME_UNSET) exoPlayer.duration else 0L
                    AudioPlayerControls(
                        isPlaying = isPlaying,
                        contentCurrentPosition = contentCurrentPosition,
                        exoPlayer = exoPlayer,
                        state = audioPlayerState,
                        audioPlayerViewModel = audioPlayerViewModel,
                        contentDuration = safeDurationMs.milliseconds
                    )
                    // Spacer(modifier = Modifier.height(12.dp))



                    // 使用我们在 AudioPlayerControls.kt 里改造后的新布局

                }


            }


            Spacer(modifier = Modifier.width(20.dp)) // 左右栏中间的空隙

            // [右侧栏]：占 60% 宽度，放置滚动歌词
            Box(
                modifier = Modifier
                    .weight(0.6f).padding(end = 20.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopStart
            )
            {
                // 解析歌词
                val parsedLyrics = remember(audioInfo?.lyrics) {
                    if (audioInfo?.lyrics != null) {
                        parseLrc(audioInfo!!.lyrics)
                    } else {
                        emptyList()
                    }
                }

                // 歌词组件 (撑满右侧区域)
                ScrollableLyricsView(
                    currentPosition = contentCurrentPosition.milliseconds,
                    parsedLyrics = parsedLyrics,
                    topMaskColor =Color.Black.copy(alpha = 0.6f),   // 调整顶部遮罩颜色
                    bottomMaskColor = Color.Black.copy(alpha = 0.9f)  // 调整底部遮罩颜色
                )
            }
        }


        // --- 浮层：侧边播放列表 (逻辑保持不变) ---
        AnimatedVisibility(
            audioPlayerViewModel.atpVisibility,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .align(AbsoluteAlignment.CenterRight)
                .background(Color.Black.copy(0.95f)) // 背景稍微加深一点
                .handleDPadKeyEvents(
                    onRight = {  },
                    onUp = {  },
                    onDown = {  },
                    onLeft = {  }
                )
                .onFocusChanged { focusState ->
                    audioPlayerViewModel.atpFocus = focusState.isFocused
                }
        ) {
            when (audioPlayerViewModel.selectedAorVorS) {
                "L" -> AudioListPanel(
                    audioPlayerViewModel.selectedAtIndex,
                    onSelectedIndexChange = { audioPlayerViewModel.selectedAtIndex = it },
                    extraList.toMutableList(),
                    exoPlayer,
                    audioPlayerViewModel
                )
            }
            // 列表显示时的返回键拦截
            BackHandler(true) {
                audioPlayerViewModel.atpVisibility = false
                audioPlayerViewModel.atpFocus = false
                focusRequester.requestFocus()
            }
        }
    }
}


// --- D-Pad 事件修饰符 ---
private fun Modifier.dPadEvents(
    exoPlayer: ExoPlayer,
    audioPlayerState: AudioPlayerState,
    pulseState: AudioPlayerPulseState,
    audioPlayerViewModel: AudioPlayerViewModel,
    focusRequester: FocusRequester
): Modifier = handleDPadKeyEvents(
    onLeft = {
//        // 面板可见时，不处理向左事件
        if (!audioPlayerViewModel.conFocus) {
            focusRequester.requestFocus()
        }

        true
    },
    onRight = {
        if (!audioPlayerViewModel.conFocus) {
            focusRequester.requestFocus()
        }
        true
    },
    onUp = {
        true
//        if (!audioPlayerViewModel.atpVisibility) {
//            audioPlayerViewModel.atpVisibility = true
//            audioPlayerViewModel.selectedAorVorS = "L"
//            true
//        } else {
//            false
//        }
    },
    onDown = {
        true
//        if (audioPlayerViewModel.atpVisibility) {
//            audioPlayerViewModel.atpVisibility = false
//            true
//        } else {
//            false
//        }
    },
    onEnter = {
        focusRequester.requestFocus()
        exoPlayer.pause()
        true


    },
).onKeyEvent { keyEvent ->
    when (keyEvent.key) {
        Key.Menu -> {
            // 菜单键处理逻辑
            audioPlayerViewModel.atpVisibility = true
            true // 消费事件
        }

        Key.ButtonY -> {
            audioPlayerViewModel.atpVisibility = true
            true // 消费事件
        }

        else -> {
            // 检查原生键码
            when (keyEvent.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    audioPlayerViewModel.atpVisibility = true
                    true // 消费事件
                }

                else -> false
            }
        }
    }
}

// 格式化时长的工具函数
//fun formatDuration(durationMs: Int?): String {
//    // 处理 null 或无效值
//    if (durationMs == null || durationMs <= 0) return "--:--"
//    val totalSeconds = durationMs / 1000
//    val minutes = totalSeconds / 60
//    val seconds = totalSeconds % 60
//    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
//}


// ✅ 新增：苹果风格音频参数徽章组件
@Composable
fun AudioInfoBadge(audioInfo: AudioInfo?) {
    // 格式化文本：16 BIT · 44.1 KHZ · 850 KBPS
    val sampleRateValue = audioInfo?.sampleRate?.toIntOrNull()
    val infoText = "${audioInfo?.bitsPerSample ?: "--"} BIT · ${
        sampleRateValue?.let {
            Locale.getDefault().let { locale ->
                // 只有成功转换成数字了才进行除法和格式化
                String.format(locale, "%.1f KHZ", it / 1000.0)
            }
        } ?: "--"
    } · ${audioInfo?.bit ?: "--"} KBPS"

    Surface(
        colors = SurfaceDefaults.colors(
            Color.White.copy(alpha = 0.95f),
            contentColor = Color.Black
        ), // 白底
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = infoText,
            color = Color.Black, // 黑字
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
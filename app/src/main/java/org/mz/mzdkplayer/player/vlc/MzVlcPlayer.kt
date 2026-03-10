package org.mz.mzdkplayer.player.vlc

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mz.mzdkplayer.player.core.IMzPlayer
import org.mz.mzdkplayer.player.core.MzBasicTrack
import org.mz.mzdkplayer.player.core.MzVideoTrack
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel

import org.mz.mzdkplayer.ui.screen.vm.VideoPlayerStatus
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

class MzVlcPlayer(
    private val context: Context,
    mediaUri: String,
    // 如果需要，可以把语言偏好也传进来
    private val preferredAudioLang: String? = null,
    private val preferredTextLang: String? = null,
    settingsViewModel: SettingsViewModel
) : IMzPlayer {
    val isPassthroughEnabled = settingsViewModel.uiState.value.enablePassthrough // 获取当前的设置状态
    // 1. 初始化 VLC 命令行参数
    private val options = arrayListOf(
        "-vvv",
        "--http-reconnect",
        "--mediacodec-dr",
        "--vout=android_display", // 强制使用安卓原生显示输出，HDR 关键
        "--no-video-deco",        // 禁用装饰，防止 GLES 混合导致 HDR 失效
        "--aout=android_audiotrack",
        "--file-caching=3000",
        "--network-caching=3000",
        "--clock-jitter=0",
        "--clock-synchro=0"
    ).apply {
        // 如果开启了直通，在全局参数里也加上支持
        if (isPassthroughEnabled) {
            add("--spdif")
        }
        // VLC 的语言设置通常在初始化时通过参数传入
        preferredAudioLang?.let { add("--audio-language=$it") }
        preferredTextLang?.let { add("--sub-language=$it") }

    }

    private val libVLC = LibVLC(context, options)
    private val mediaPlayer = MediaPlayer(libVLC)

    // 2. 实现接口要求的状态流
    private val _playerStatus = MutableStateFlow<VideoPlayerStatus>(VideoPlayerStatus.IDLE)
    override val playerStatus: StateFlow<VideoPlayerStatus> = _playerStatus.asStateFlow()

    private val _isPlayingFlow = MutableStateFlow(false)
    override val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    private val _videoTracks = MutableStateFlow<List<MzVideoTrack>>(emptyList())
    override val videoTracks: StateFlow<List<MzVideoTrack>> = _videoTracks.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<MzBasicTrack>>(emptyList())
    override val audioTracks: StateFlow<List<MzBasicTrack>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<MzBasicTrack>>(emptyList())
    override val subtitleTracks: StateFlow<List<MzBasicTrack>> = _subtitleTracks.asStateFlow()

    // 3. 接口回调
    override var onError: ((String) -> Unit)? = null
    override var onCuesChanged: ((Any) -> Unit)? = null // VLC 不需要，留空

    init {

        mediaPlayer.setAudioDigitalOutputEnabled(isPassthroughEnabled)
        if (isPassthroughEnabled) {
            mediaPlayer.setAudioOutput("android_audiotrack")
        }
        // 设置事件监听器同步状态
        val media = Media(libVLC, mediaUri.toUri()).apply {
            setHWDecoderEnabled(true, true)
            addOption(":codec=mediacodec_ndk")

            // 3. 只有开启直通时，才注入这些 Media Option
            if (isPassthroughEnabled) {
                addOption(":audio-passthrough=1")
                addOption(":spdif=hdmi")
                addOption(":audio-passthrough=hdmi")
                addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                // 注意：如果电视不支持 TrueHD，VLC 只要看到这几个参数就会尝试透传
                // 如果透传失败就会没声音。目前最稳妥是让用户在设置里切开关。
            } else {
                addOption(":audio-passthrough=0")
            }

            addOption(":no-osd")
            addOption(":network-caching=3000")            // 实验代码里有的，建议加上
        }
        // 针对网络流优化
        media.addOption(":network-caching=3000")

        mediaPlayer.media = media
        setupEventListener()

    }
    // 在 MzVlcPlayer 类中添加成员
    private var _videoWidth = 1920
    private var _videoHeight = 1080

    // 在 VLC 视频尺寸变化回调里更新（通常是 onNewVideoLayout 或 IVLCVout.Callback）
    override val videoWidth: Int get() = _videoWidth
    override val videoHeight: Int get() = _videoHeight
    private fun setupEventListener() {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    _isPlayingFlow.value = true
                    _playerStatus.value = VideoPlayerStatus.READY
                    updateTracks() // 播放开始后刷新轨道信息
                }
                MediaPlayer.Event.Paused -> {
                    _isPlayingFlow.value = false
                }
                MediaPlayer.Event.Stopped -> {
                    _isPlayingFlow.value = false
                    _playerStatus.value = VideoPlayerStatus.ENDED
                }
                MediaPlayer.Event.Buffering -> {
                    // VLC 的 buffering 状态包含百分比，缓冲到 100% 时切回 READY
                    if (event.buffering == 100f) {
                        _isPlayingFlow.value = true // 缓冲开始，视为停止播放
                        _playerStatus.value = VideoPlayerStatus.READY
                    } else {
                        _isPlayingFlow.value = false // 缓冲结束
                        _playerStatus.value = VideoPlayerStatus.BUFFERING
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    _playerStatus.value = VideoPlayerStatus.ENDED
                }
                MediaPlayer.Event.EncounteredError -> {
                    onError?.invoke("VLC 播放出错")
                }
                // 当轨道发生变化时（例如添加了外部字幕），刷新列表
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted -> {
                    updateTracks()
                }
            }
        }
    }

    private fun updateTracks() {
        // 处理视频轨道 (VLC 视频轨道通常只读)
        //val videoTrackDescriptions = mediaPlayer.videoTracks ?: emptyArray()

        // 获取 Media 对象（你的 mediaPlayer.media 返回的就是 IMedia）
        val media = mediaPlayer.media ?: return

// ★★★ 必须先解析，否则 trackCount 几乎永远是 0 ★★★
        if (!media.isParsed) {
            media.parseAsync()                    // 本地文件用同步，很快
            // 网络文件推荐异步（下面有示例）
        }

// 获取所有详细轨道
        val trackCount = media.trackCount
        val allTracks = (0 until trackCount).mapNotNull { media.getTrack(it) }
        val currentVideoId = mediaPlayer.videoTrack
        //Log.i("VLCAT",currentVideoId.toString())
        _videoTracks.value = allTracks
            .filter { it.type == IMedia.Track.Type.Video }
            .mapIndexedNotNull { index, track ->
                val videoTrack = track as? IMedia.VideoTrack ?: return@mapIndexedNotNull null
                Log.i("VLCAT",videoTrack.id.toString())
                MzVideoTrack(
                    id = videoTrack.id.toString(),
                    index = index,
                    height = videoTrack.height,
                    bitrate = videoTrack.bitrate,
                    codecs = videoTrack.codec,
                    isSelected = videoTrack.id == currentVideoId,
                    rawData = videoTrack.id
                )
            }

        // 处理音频轨道
        //val audioTrackDescriptions = mediaPlayer.audioTracks ?: emptyArray()
        val currentAudioId = mediaPlayer.audioTrack
        _audioTracks.value = allTracks
            .filter { it.type == IMedia.Track.Type.Audio }
            .mapIndexed { index, track ->
                val audioTrack = track as IMedia.AudioTrack
                Log.i("MzVlcPlayer","${audioTrack.codec} |  ${audioTrack.fourcc}")
                MzBasicTrack(
                    id = audioTrack.id.toString(),
                    index = index,
                    language = audioTrack.language ?: "",
                    channelCount = audioTrack.channels,
                    mimeType = audioTrack.codec?:"",
                    sampleRate = audioTrack.rate,
                    bitrate = audioTrack.bitrate,

                    name = audioTrack.description ?: track.language ?: "音轨 ${index + 1}",
                    isSelected = audioTrack.id == currentAudioId,
                    rawData = audioTrack.id
                )
            }

        // 处理字幕轨道
        //val spuTrackDescriptions = mediaPlayer.spuTracks ?: emptyArray()
        val currentSpuId = mediaPlayer.spuTrack
        _subtitleTracks.value = allTracks
            .filter { it.type == IMedia.Track.Type.Text }   // 字幕通常是 Text
            .mapIndexed { index, track ->
                MzBasicTrack(
                    id = track.id.toString(),
                    index = index,
                    mimeType = track.codec,
                    language = track.language ,
                    name = track.description ?: track.language ?: "字幕 ${index + 1}",
                    isSelected = track.id == currentSpuId,
                    rawData = track.id
                )
            }
    }

    override val isPlaying: Boolean get() = mediaPlayer.isPlaying
    override val currentPosition: Long get() = mediaPlayer.time
    override val duration: Long get() = mediaPlayer.length

    override fun play() { mediaPlayer.play() }
    override fun pause() { mediaPlayer.pause() }
    override fun seekTo(positionMs: Long) { mediaPlayer.time = positionMs }
    override fun seekForward(ms: Long) { mediaPlayer.time = currentPosition + ms }
    override fun seekBack(ms: Long) { mediaPlayer.time = currentPosition - ms }

    override fun selectVideoTrack(track: MzVideoTrack) {
        mediaPlayer.videoTrack = track.rawData as? Int ?: -1
    }

    override fun selectAudioTrack(track: MzBasicTrack) {
        mediaPlayer.audioTrack = track.rawData as? Int ?: -1
        updateTracks()
    }

    override fun selectSubtitleTrack(track: MzBasicTrack) {
        mediaPlayer.spuTrack = track.rawData as? Int ?: -1
        updateTracks()
    }

    // --- 关键：实现加载外部字幕 ---
//    override fun addExternalSubtitle(uri: String, name: String) {
//        // VLC 支持直接在播放时添加 slave
//        // Type.Subtitle 表示字幕，true 表示立即选中
//        mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, uri.toUri(), true)
//        // 之后 EventListener 会触发 ESAdded，进而调用 updateTracks
//    }

    override fun release() {
        mediaPlayer.stop()
        mediaPlayer.release()
        libVLC.release()
    }

    @Composable
    override fun PlayerView(modifier: Modifier) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, true, false)
                    // 关键修改：在这里才真正开始播放，或者触发一个状态通知
                    if (!mediaPlayer.isPlaying) {
                        mediaPlayer.play()
                    }
                }
            },
            modifier = modifier.fillMaxSize(),
            onRelease = {
                mediaPlayer.detachViews()
            }
        )
    }
}
//private fun fourccToString(fourcc: Int): String {
//    if (fourcc == 0) return ""
//    return buildString {
//        append(((fourcc shr 24) and 0xFF).toChar())
//        append(((fourcc shr 16) and 0xFF).toChar())
//        append(((fourcc shr 8) and 0xFF).toChar())
//        append((fourcc and 0xFF).toChar())
//    }.trim()
//}
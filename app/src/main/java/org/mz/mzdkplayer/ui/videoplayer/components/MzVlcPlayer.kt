package org.mz.mzdkplayer.ui.videoplayer.components



import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import androidx.core.net.toUri

/**
 * VLC 播放器封装，用于桥接 Compose 和 LibVLC
 */
class MzVlcPlayer(context: Context) {
    val libVlc: LibVLC
    val mediaPlayer: MediaPlayer

    init {
        // 初始化 LibVLC 参数
        val options = ArrayList<String>()
        options.add("-vvv") // 详细日志
        // 针对 ISO/M2TS 的一些优化参数
        options.add("--rtsp-tcp")
        options.add("--network-caching=6000") // 网络缓存
        libVlc = LibVLC(context, options)
        mediaPlayer = MediaPlayer(libVlc)
    }

    fun prepare(uri: String) {
        // 处理 URI，ISO 文件通常需要特定的处理，但 VLC 对直接路径支持很好
        // 注意：如果是 SMB，VLC 内部支持 smb://，但如果你的 App 使用了复杂的自定义 DataSource (如带鉴权的)，
        // 你可能需要传递带用户名密码的 URL: smb://user:pass@ip/path
        val media = Media(libVlc, uri.toUri())

        // 针对硬件解码的设置
        media.setHWDecoderEnabled(true, false)

        mediaPlayer.media = media
        media.release() // 释放 native 引用，mediaPlayer 会持有它
    }

    fun play() {
        mediaPlayer.play()
    }

    fun pause() {
        mediaPlayer.pause()
    }

    val isPlaying: Boolean
        get() = mediaPlayer.isPlaying

    val currentPosition: Long
        get() = mediaPlayer.time

    val duration: Long
        get() = mediaPlayer.length

    fun seekTo(positionMs: Long) {
        if (mediaPlayer.isSeekable) {
            mediaPlayer.time = positionMs
        }
    }

    // 资源释放
    fun release() {
        mediaPlayer.stop()
        mediaPlayer.release()
        libVlc.release()
    }

    // 绑定 SurfaceView
    fun attachSurface(surfaceView: SurfaceView) {
        val vout = mediaPlayer.vlcVout
        vout.setVideoView(surfaceView)
        vout.attachViews()
    }

    fun detachSurface() {
        val vout = mediaPlayer.vlcVout
        vout.detachViews()
    }
}

// Compose 的 remember 函数
@Composable
fun rememberVlcPlayer(context: Context): MzVlcPlayer {
    return remember {
        MzVlcPlayer(context)
    }
}
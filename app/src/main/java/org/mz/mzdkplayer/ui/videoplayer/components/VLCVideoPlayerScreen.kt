package org.mz.mzdkplayer.ui.videoplayer.components



import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import android.net.Uri
import androidx.core.net.toUri

@Composable
fun VLCVideoPlayerScreen(
    mediaUri: String,
    fileName: String
) {
    val context = LocalContext.current

    // 1. 初始化 LibVLC 和 MediaPlayer
    val libVLC = remember {
        val options = arrayListOf("-vvv") // 开启详细日志，方便调试 M2TS 解码
        LibVLC(context, options)
    }
    val mediaPlayer = remember { MediaPlayer(libVLC) }

    // 2. 释放资源
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    // 3. 渲染视图
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            VLCVideoLayout(ctx).apply {
                // 将 VLC 的渲染关联到这个 Layout
                mediaPlayer.attachViews(this, null, true, false)

                // 加载媒体
                val uri = mediaUri.toUri()
                val media = Media(libVLC, uri).apply {
                    // M2TS 通常需要开启硬件加速
                    setHWDecoderEnabled(true, false)
                    addOption(":file-caching=3000")
                }

                mediaPlayer.media = media
                mediaPlayer.play()
            }
        }
    )
}
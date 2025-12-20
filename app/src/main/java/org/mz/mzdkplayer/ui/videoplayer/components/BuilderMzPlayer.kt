package org.mz.mzdkplayer.ui.videoplayer.components


import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.Log
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.ts.TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA
import androidx.media3.extractor.ts.TsExtractor.MODE_MULTI_PMT
import androidx.media3.extractor.ts.TsPayloadReader
import org.mz.mzdkplayer.tool.FtpDataSourceFactory
import org.mz.mzdkplayer.tool.M2TsExtractor

import org.mz.mzdkplayer.tool.NFSDataSourceFactory
import org.mz.mzdkplayer.tool.SmbDataSourceFactory
import org.mz.mzdkplayer.tool.WebDavDataSourceFactory
import org.mz.mzdkplayer.ui.screen.vm.SettingsViewModel
import org.mz.mzdkplayer.ui.screen.vm.VideoPlayerViewModel

@OptIn(UnstableApi::class)
@SuppressLint("SuspiciousIndentation")
@Composable
fun BuilderMzPlayer(
    context: Context,
    mediaUri: String,
    exoPlayer: ExoPlayer,
    dataSourceType: String,
    settingsViewModel: SettingsViewModel
) {
    //val pathStr = LocalContext.current.filesDir.toString()
    val videoPlayerViewModel: VideoPlayerViewModel = viewModel()
    // 从 SettingsViewModel 中获取字幕与音轨相关的状态
    val settingsState by settingsViewModel.uiState.collectAsState()
// ⚠️ 重点：获取与 rememberPlayer 中使用的相同的 DataSourceFactory 逻辑
    val dataSourceFactory =
        remember { selectedDataSourceFactory(mediaUri, dataSourceType, context) }
    LaunchedEffect(Unit) {

        val preferredAudioLanguage = settingsState.audioLang.ifEmpty { null }
        // --- 字幕语言设置 ---
        val preferredTextLanguage = settingsState.subLang.ifEmpty { null }
        android.util.Log.d("preferredTextLanguage", preferredTextLanguage.toString())
        val trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage(preferredTextLanguage)
            .setPreferredAudioLanguage(preferredAudioLanguage)
            .setSelectUndeterminedTextLanguage(true)//// 关键：允许选中语言为 "und" 的字幕 将 "zh" 替换为你需要的默认字幕语言代码，例如 "en" 表示英语
            .build()

        // SRT 字幕的 MIME 类型
        val mimeTypeSRT = "application/x-subrip"
        // val mimeTypeASS = "text/x-ssa"          // .ass
        // val mimeTypeVTT = "text/vtt"            // .vtt


        exoPlayer.trackSelectionParameters = trackSelectionParameters

        // 1. 构造 MediaItem.Builder
        //val mediaItemBuilder = MediaItem.Builder()
        // 2. 设置 URI
        val finalUri = finalUri(mediaUri)
        // 1. 构造初始 MediaItem，只包含 URI，不含任何 SubtitleConfiguration
        val mediaItem = MediaItem.Builder().setUri(finalUri).build()

        // ⭐️ 核心：创建手动加载字幕的 Lambda，用于传递给 SubtitleTrackPanel
//        val onLoadExternalSubtitles = remember {
//            {
//                loadExternalSubtitlesDynamically(exoPlayer, mediaUri, dataSourceFactory)
//            }
//        }

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // 4. 在这里立即尝试添加外部字幕（无需等待 onTracksChanged）
        // 因为我们只需要在 prepare 之后，而不是在 prepare 之前。
//        val externalSubtitles = findExternalSubtitles(mediaUri)
//        if (externalSubtitles.isNotEmpty()) {
//            val newMediaItem = mediaItem.buildUpon()
//                .setSubtitleConfigurations(externalSubtitles)
//                .build()
//
//            // ⭐️ 核心修改：使用 setMediaItem 替换 updateMediaItem
//            // 0 是当前播放项的索引。false 确保播放位置和状态不被重置。
//            // 注意：虽然 setMediaItem 会替换当前 MediaItem，但在 prepare 之后调用，
//            // 且不重置位置，可以达到动态更新字幕配置的目的。
//            exoPlayer.setMediaItem(newMediaItem)
//            Log.d("SubtitleLoader", "Deferred adding of ${externalSubtitles.size} external subtitles via updateMediaItem.")
//        }
        exoPlayer.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                // Update UI using current tracks.
                val trackGroups = exoPlayer.currentTracks.groups
                videoPlayerViewModel.mutableSetOfAudioTrackGroups.clear()
                videoPlayerViewModel.mutableSetOfVideoTrackGroups.clear()
                videoPlayerViewModel.mutableSetOfTextTrackGroups.clear()
                Log.e("enableTunneling", exoPlayer.isTunnelingEnabled.toString())
//                // 检测是否有SRT字幕轨道被选中
//                var hasSrtTrackSelected = false
//                var hasPGSTrackSelected = false
//                var hasASSTrackSelected = false
//                var hasVTTTrackSelected = false
                for (trackGroup in trackGroups) {
                    // Group level information.
                    val trackType = trackGroup.type
                    // 音频轨
                    if (trackType == C.TRACK_TYPE_AUDIO) {
                        videoPlayerViewModel.mutableSetOfAudioTrackGroups.add(trackGroup)
                        Log.d("TRACK_TYPE_AUDIO", trackGroup.getTrackFormat(0).toString())
                    }
                    // 视频轨
                    if (trackType == C.TRACK_TYPE_VIDEO) {
                        videoPlayerViewModel.mutableSetOfVideoTrackGroups.add(trackGroup)
                    }

                    // 字幕轨
                    if (trackType == C.TRACK_TYPE_TEXT) {
                        videoPlayerViewModel.mutableSetOfTextTrackGroups.add(trackGroup)
//
//                        if (trackGroup.isSelected) {
//                            // 获取被选中轨道的格式 (循环轨道组)
//                            for (i in 0 until trackGroup.length) {
//                                if (trackGroup.isTrackSelected(i)) {
//                                    val format = trackGroup.getTrackFormat(i)
//                                    // 检查是否是SRT格式
//                                    if (format.codecs == mimeTypeSRT || format.sampleMimeType == mimeTypeSRT) {
//                                        hasSrtTrackSelected = true
//                                        break // 找到一个SRT轨道就足够
//                                    }
//                                    if (format.codecs == "application/pgs"|| format.sampleMimeType == "application/pgs") {
//                                        hasPGSTrackSelected = true
//                                        break // 找到一个SRT轨道就足够
//                                    }
//                                    if (format.codecs == "text/x-ssa"|| format.sampleMimeType == "text/x-ssa") {
//                                        hasASSTrackSelected = true
//                                        break // 找到一个SRT轨道就足够
//                                    }
//                                    if (format.codecs == "text/vtt"|| format.sampleMimeType =="text/vtt") {
//                                        hasVTTTrackSelected = true
//                                        break // 找到一个SRT轨道就足够
//                                    }
//                                }
//                            }
//                        }
//                    }
                        // 根据是否选中了 SRT 或者 PGS轨道来设置可见性
//                    if (hasSrtTrackSelected || hasPGSTrackSelected || hasVTTTrackSelected) {
//                        Log.i("SDS1", "SubtitleView set to GONE because SRT PGS track is selected.")
//                        videoPlayerViewModel.updateSubtitleVisibility(View.GONE)
//                        videoPlayerViewModel.updateCusSubtitleVisibility(true)
//
//                    } else if (hasASSTrackSelected) {
//                        videoPlayerViewModel.updateSubtitleVisibility(View.GONE)
//                        Log.i("SDS1", "SubtitleView set to VISIBLE because ASS track is selected.")
//                        videoPlayerViewModel.updateCusSubtitleVisibility(true)
//                    } else {
//                        Log.i(
//                            "SDS1", "SubtitleView set to VISIBLE because no SRT track is selected."
//                        )
//                        videoPlayerViewModel.updateCusSubtitleVisibility(true)
//                        videoPlayerViewModel.updateSubtitleVisibility(View.GONE)
//
//                    }
                    }
                }
                if (videoPlayerViewModel.mutableSetOfTextTrackGroups.isNotEmpty() && settingsState.subLang.isEmpty()) {
                    Log.i("SD1","自动选择第一个")
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters.buildUpon().setOverrideForType(
                            TrackSelectionOverride(
                                videoPlayerViewModel.mutableSetOfTextTrackGroups[0].mediaTrackGroup,
                                0
                            )
                        ).build()
                }
                if (videoPlayerViewModel.mutableSetOfAudioTrackGroups.isNotEmpty()) {
                    for ((index, atGroup) in videoPlayerViewModel.mutableSetOfAudioTrackGroups.withIndex()) {
                        Log.d("VideoTrackGroupsID", atGroup.getTrackFormat(0).id.toString())
                        if (atGroup.isTrackSelected(0)) {
                            Log.d("sindex", index.toString())
                            videoPlayerViewModel.selectedAtIndex = index
                        }
                    }
                }
                if (videoPlayerViewModel.mutableSetOfVideoTrackGroups.isNotEmpty()) {
                    for ((index, vtGroup) in videoPlayerViewModel.mutableSetOfVideoTrackGroups.withIndex()) {
                        if (vtGroup.isTrackSelected(0)) {
                            videoPlayerViewModel.selectedVtIndex = index
                        }
                    }
                }
                if (videoPlayerViewModel.mutableSetOfTextTrackGroups.isNotEmpty()) {
                    for ((index, vtGroup) in videoPlayerViewModel.mutableSetOfTextTrackGroups.withIndex()) {
                        if (vtGroup.isTrackSelected(0)) {
                            videoPlayerViewModel.selectedStIndex = index
                        }
                    }
                }
                videoPlayerViewModel.onTracksChangedState = 1
            }


        })
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberPlayer(
    context: Context,
    mediaUri: String,
    dataSourceType: String,
    settingsViewModel: SettingsViewModel
) =
    remember(mediaUri, settingsViewModel.uiState.collectAsState().value.enableTunneling) {
        val settingsState = settingsViewModel.uiState.value // 获取当前的设置状态
        // 配置 RenderersFactory
        val renderersFactory =
            DefaultRenderersFactory(context).forceEnableMediaCodecAsynchronousQueueing().apply {
                //setMediaCodecSelector(avcAwareCodecSelector)
                setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)

            }

        // --- 1. 配置 LoadControl (80G ISO 优化缓冲策略) ---
        // 目标：将缓冲内存加大至 500MB，并设置更激进的缓冲时长。
        // 预估：80GB / 2小时 ≈ 11.1 MB/s (平均码率)
        // 50秒缓冲所需内存 ≈ 50 * 11.1 MB/s ≈ 555 MB
        val optimizedLoadControl = DefaultLoadControl.Builder()
//            .setBufferDurationsMs(
//                50_000, // 最小缓冲 50 秒 (Min Buffer)
//                75_000, // 最大缓冲 75 秒 (Max Buffer) - 增加裕量应对高码率峰值
//                1_500,  // 起播缓冲 3 秒 (Buffer for Playback)
//                5_000   // Rebuffer后缓冲 5 秒 (Buffer after Rebuffer)
//            )
            .setPrioritizeTimeOverSizeThresholds(true) // 优先保证时间长度，忽略默认的内部大小限制
            .setBackBuffer(5000, true) // 回退缓冲 5 秒，并且允许回退播放 (用于快速 Seek 后退)
            .build()
        Log.e("enableTunneling", "isSetting ${settingsState.enableTunneling}")
        // --- 3. 配置 TrackSelector 并设置 Tunneling ---
        val trackSelector = DefaultTrackSelector(context)
        val parameters = trackSelector.buildUponParameters()
            .setTunnelingEnabled(settingsState.enableTunneling) // ⚠️ 核心：根据设置开启/关闭隧道模式
            .build()
        trackSelector.setParameters(parameters)
        val isM2ts = mediaUri.endsWith(".m2ts", ignoreCase = true) ||
                mediaUri.endsWith(".mts", ignoreCase = true)
        val mediaSource = if (isM2ts) {
            val customExtractorsFactory =
                DefaultExtractorsFactory().setTsExtractorMode(MODE_MULTI_PMT)
                    .setTsExtractorTimestampSearchBytes(1024)
            val forcedTsExtractorFactory = ExtractorsFactory {
                arrayOf<Extractor>(
//                    TsExtractor(             TsExtractor.MODE_SINGLE_PMT,
//                        FLAG_EMIT_RAW_SUBTITLE_DATA,
//                        SubtitleParser.Factory.UNSUPPORTED,
//                        TimestampAdjuster(4),
//                        DefaultTsPayloadReaderFactory(FLAG_ALLOW_NON_IDR_KEYFRAMES),
//                        192)
                    M2TsExtractor()
                )

            }

            DefaultMediaSourceFactory(
                selectedDataSourceFactory(
                    mediaUri,
                    dataSourceType,
                    context
                ),
                forcedTsExtractorFactory
            )

        } else {
            DefaultMediaSourceFactory(
                selectedDataSourceFactory(
                    mediaUri,
                    dataSourceType,
                    context
                )
            )
        }

        ExoPlayer.Builder(context).setSeekForwardIncrementMs(30000).setSeekBackIncrementMs(30000)
            .setTrackSelector(trackSelector).setMediaSourceFactory(
                mediaSource
            )
            .setRenderersFactory(renderersFactory).build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE

            }
    }

//            .buildWithAssSupport(context = context, renderType = AssRenderType.LEGACY,
//                renderersFactory=renderersFactory, dataSourceFactory = dataSourceFactory).apply {
@OptIn(UnstableApi::class)
fun selectedDataSourceFactory(
    mediaUri: String,
    dataSourceType: String,
    context: Context
): DataSource.Factory {
    return if (mediaUri.startsWith("smb://") && dataSourceType == "SMB") {
        // SMB 协议
        SmbDataSourceFactory()

    } else if ((mediaUri.startsWith("file://") || mediaUri.startsWith("/")) && dataSourceType == "LOCAL") {
        // 本地文件协议或绝对路径
        DefaultDataSource.Factory(context)
    } else if ((mediaUri.startsWith("http://") || mediaUri.startsWith("https://")) && dataSourceType == "WEBDAV") {
        WebDavDataSourceFactory()
    } else if ((mediaUri.startsWith("ftp://")) && dataSourceType == "FTP") {
        FtpDataSourceFactory()
    } else if ((mediaUri.startsWith("nfs://")) && dataSourceType == "NFS") {
        NFSDataSourceFactory()
    } else if ((mediaUri.startsWith("http://") || mediaUri.startsWith("https://")) && dataSourceType == "HTTP") {
        DefaultHttpDataSource.Factory().setReadTimeoutMs(30000).setConnectTimeoutMs(30000)
    } else {
        // 其他情况（如 http/https），使用默认的 HTTP 数据源
        DefaultHttpDataSource.Factory().setReadTimeoutMs(30000).setConnectTimeoutMs(30000)
    }
}


@OptIn(UnstableApi::class)
fun findExternalSubtitles(mediaUri: String): List<MediaItem.SubtitleConfiguration> {
    val subtitles = mutableListOf<MediaItem.SubtitleConfiguration>()

    // 1. 获取基础 URI 部分（不含扩展名）
    val lastDotIndex = mediaUri.lastIndexOf('.')
    if (lastDotIndex == -1) return subtitles // 没有扩展名，无法处理

    val baseUri = mediaUri.take(lastDotIndex)

    // 定义要查找的字幕扩展名和对应的 MIME 类型
    val subtitleExtensions = mapOf(
        ".srt" to "application/x-subrip", // SRT
        ".ass" to "text/x-ssa",          // ASS/SSA
        ".ssa" to "text/x-ssa",          // ASS/SSA (另一种扩展名)
        ".vtt" to "text/vtt"             // WebVTT
    )

    // 2. 遍历可能的字幕文件
    for ((ext, mimeType) in subtitleExtensions) {
        // 构造潜在的字幕文件 URI
        val subtitleUriString = baseUri + ext
        val subtitleUri = subtitleUriString.toUri()
        val fileName = subtitleUriString.substringAfterLast('/').substringAfterLast('\\')
        // 由于 Media3/ExoPlayer 在加载 MediaItem 时会处理 URI 的有效性
        // 对于远程文件（如HTTP/SMB），只需要构造URI即可。
        // 对于本地文件，理论上你需要检查文件是否存在（但对于跨平台/网络，最简单是直接交给播放器处理）
        // 播放器会尝试加载这个URI，如果失败，它会跳过这个字幕，不会影响视频播放。

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(mimeType)
            // 你可能需要根据字幕文件的语言约定来设置语言，这里暂时留空或设置为默认
            // 例如：如果文件名是 "video.zh.srt"，则可以尝试解析 "zh"
            // 为了简单起见，我们暂时设置一个默认的 SelectionFlag
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).setLabel("[外部加载]$fileName")
            .setLanguage("zh")
            .build()

        subtitles.add(subtitleConfig)
        Log.d("SubtitleLoader", "Candidate subtitle: $subtitleUriString ($mimeType)")
    }

    return subtitles
}

fun finalUri(mediaUri: String) =
    if (mediaUri.startsWith("smb://") || mediaUri.startsWith("http://") || mediaUri.startsWith(
            "https://"
        ) || mediaUri.startsWith("ftp://") || mediaUri.startsWith("nfs://")
    ) {
        mediaUri.toUri()
    } else {
        // 处理本地文件路径
        if (mediaUri.startsWith("file://")) {
            mediaUri.toUri()
        } else {
            "file://$mediaUri".toUri()
        }
    }

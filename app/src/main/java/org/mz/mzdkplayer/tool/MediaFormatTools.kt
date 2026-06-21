package org.mz.mzdkplayer.tool


import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

import androidx.media3.common.util.UnstableApi


import org.mz.mzdkplayer.R
import java.util.Locale

internal object MediaFormatTools {
    // 文件扩展名到资源 ID 的映射表
    private val formatIconMap = mapOf(
        "mkv" to R.drawable.mkvnew,
        "mp4" to R.drawable.mp4new,
        "flv" to R.drawable.flvnew,
        "3gp" to R.drawable.n3gpnew,
        "ts" to R.drawable.tsnew,
        "m2ts" to R.drawable.m2tsnew,
        "mov" to R.drawable.movnew,
        "mp3" to R.drawable.mp3big,
        "wav" to R.drawable.wavbig,
        "flac" to R.drawable.flacnew
    )

    fun extractFileExtension(fileName: String?): String {
        if (fileName.isNullOrEmpty()) {
            return ""
        }
        // 处理可能以点结尾的文件名或隐藏文件（无扩展名）
        val lastDotIndex = fileName.lastIndexOf('.')
        if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            // 确保点不在字符串的开头（隐藏文件）且不是最后一个字符
            return fileName.substring(lastDotIndex + 1).lowercase(Locale.getDefault())
        }
        return "" // 没有扩展名

    }

    @OptIn(UnstableApi::class)
    @Composable
    fun VideoBigIcon(focusedIsDir: Boolean, fileName: String?, modifier: Modifier) {
        Log.d("fileName", fileName.toString())
        Log.d("eFileName", extractFileExtension(fileName))

        val iconRes = if (focusedIsDir) {
            R.drawable.foldernew
        } else {
            val extension = extractFileExtension(fileName)
            formatIconMap[extension] ?: R.drawable.filenew
        }

        Image(
            modifier = modifier,
            painter = painterResource(iconRes),
            contentDescription = null,
        )
    }

    fun containsVideoFormat(input: String): Boolean {
        val videoFormats = listOf("MP4", "MKV","M2TS", "3GP", "AVI", "MOV", "TS", "FLV","ISO")
        return videoFormats.any { format ->
            input.contains(format, ignoreCase = true)
        }
    }

    fun containsAudioFormat(input: String): Boolean {
        val audioFormats = listOf("MP3", "FLAC","WAV","AAC")
        return audioFormats.any { format ->
            input.contains(format, ignoreCase = true)
        }
    }

    fun containsImageFileExtension(input: String): Boolean {
        // Exoplayer 支持的图片格式的常见扩展名列表
        val supportedExtensions = listOf(
            "bmp",  // BMP
            "jpg",  // JPEG
            "jpeg", // JPEG
            "png",  // PNG
            "webp", // WebP
            "heif", // HEIF/HEIC
            "heic", // HEIF/HEIC
            "avif"  // AVIF (基准，需 Android 14+)
        )

        // 1. 将输入转为小写，并去掉可能的首尾空格
        val lowerInput = input.trim().lowercase()

        // 2. 检查这个字符串是否是支持的扩展名之一
        return supportedExtensions.contains(lowerInput)
    }
    /**
     * 根据音频轨道的 Format 信息推断具体的音频格式类型
     * @return 推断出的音频格式描述字符串
     */
    fun inferAudioFormatType(mimeType: String): String {
        //val mimeType = format.sampleMimeType ?: return "Unknown Audio Format (No MIME type)"

        return when (mimeType) {
            "audio/vnd.dts" -> "DTS" // DTS 家族格式判断
            "audio/vnd.dts.hd" -> "DTS HD" // DTS 家族格式判断
            // Dolby 家族格式判断
            "audio/true-hd" -> "Dolby TrueHD"
            "audio/ac3" -> "Dolby Digital (AC3)"
            "audio/eac3" -> "Dolby Digital Plus (E-AC3)"
            "audio/eac3-joc" -> "Dolby Digital Plus with Atmos (E-AC3 JOC)"

            // AAC 格式
            "audio/mp4a-latm" -> "AAC (Advanced Audio Coding)"

            // OPUS 格式
            "audio/opus" -> "Opus"

            // Vorbis 格式
            "audio/vorbis" -> "Vorbis"

            // FLAC 格式
            "audio/flac" -> "FLAC (Free Lossless Audio Codec)"

            // PCM 格式
            "audio/raw" -> "PCM (Uncompressed)"
            "audio/wav" -> "WAV (PCM)"
            "audio/x-wav" -> "WAV (PCM)"

            // MP3 格式
            "audio/mpeg" -> "MP3 (MPEG-1 Audio Layer III)"
            "audio/mp3" -> "MP3 (MPEG-1 Audio Layer III)"

            // 其他已知格式
            else -> mimeType.removePrefix("audio/").uppercase()
        }
    }
    /**
     * 专门推断 DTS 家族具体格式的辅助方法
     */
//    private fun inferDtsFormatType(format: Format): String {
//        val codecs = format.codecs?.lowercase() ?: ""
//
//        return when {
//            codecs.contains("dts-hd-ma") -> "DTS-HD Master Audio"
//            codecs.contains("dts-hd-hra") -> "DTS-HD High Resolution"
//            codecs.contains("dts-x") -> "DTS:X"
//            codecs.contains("dts-express") -> "DTS Express"
//            codecs.contains("dts") -> "DTS Core"
//
//            // 如果没有明确的 codecs 信息，则基于声道数等进行推测
//            format.channelCount >= 8 -> "DTS-HD "
//            format.channelCount == 6 -> "DTS Core "
//            else -> "DTS (未知DTS编码)"
//        }
//    }


    fun audioFormatIconType(mimeType: String?): Int {
        if (mimeType == null) return R.drawable.noradudio

        // 统一转小写，去掉空格，方便匹配
        val type = mimeType.lowercase().trim()

        return when {
            // --- 1. Dolby 家族 ---
            // Atmos 优先级最高
            type.contains("eac3-joc") || type.contains("atmos") -> {
                R.drawable.dolby_atmos
            }
            // TrueHD (ExoPlayer: audio/true-hd | VLC: TrueHD Audio)
            type.contains("true-hd") || type.contains("truehd") -> {
                R.drawable.logo_dolby_audio
            }
            // AC3 / A52 (ExoPlayer: audio/ac3 | VLC: A52 Audio (aka AC3))
            type.contains("ac3") || type.contains("ac-3") || type.contains("a52") -> {
                R.drawable.logo_dolby_audio
            }
            // E-AC3 / DD+
            type.contains("eac3") || type.contains("ec-3") -> {
                R.drawable.logo_dolby_audio
            }

            // --- 2. DTS 家族 ---
            // DTS-HD (ExoPlayer: audio/vnd.dts.hd | VLC 如果包含特定标识)
            type.contains("dts-hd") || type.contains("dtshd") || type.contains("master audio") -> {
                R.drawable.dts_hd_master_audio
            }
            // 普通 DTS (ExoPlayer: audio/vnd.dts | VLC: DTS Audio)
            type.contains("dts") -> {
                R.drawable.dts_1
            }

            // --- 3. 其他常见格式 ---
            // AAC (ExoPlayer: audio/mp4a-latm | VLC: AAC Audio)
            type.contains("mp4a") || type.contains("aac") -> {
                R.drawable.aac
            }
            // FLAC
            type.contains("flac") -> {
                R.drawable.hei
            }
            // MP3 (ExoPlayer: audio/mpeg | VLC: MPEG Audio layer 3)
            type.contains("mpeg") || type.contains("mp3") || type.contains("layer 3") -> {
                R.drawable.mp3
            }
            // PCM / WAV
            type.contains("pcm") || type.contains("raw") || type.contains("wav") -> {
                R.drawable.pcm_seeklogo__1_
            }

            // 默认返回
            else -> R.drawable.noradudio
        }
    }
}

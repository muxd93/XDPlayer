package org.mz.mzdkplayer.tool


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.io.FileOutputStream

internal object ImageTools {
    fun saveCoverImageToInternalStorage(context: Context, uri: String, artworkData: ByteArray?): String? {
        if (artworkData == null || artworkData.isEmpty()) return null

        try {
            val fileName = "cover_${uri.hashCode()}.webp"
            val directory = File(context.filesDir, "audio_covers")
            if (!directory.exists()) directory.mkdirs()

            val file = File(directory, fileName)
            if (file.exists()) return file.absolutePath

            // 1. 预读尺寸
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size, options)

            // 2. 初步采样缩放（减少内存占用）
            options.inSampleSize = calculateInSampleSize(options, 650, 650)
            options.inJustDecodeBounds = false
            val rawBitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size, options) ?: return null

            // 3. 执行居中裁剪并缩放到 650x650
            val finalBitmap = centerCropAndScale(rawBitmap, 650, 650)

            // 4. 保存为 WebP
            FileOutputStream(file).use { fos ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    finalBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, fos)
                } else {
                    @Suppress("DEPRECATION")
                    finalBitmap.compress(Bitmap.CompressFormat.WEBP, 80, fos)
                }
            }

            // 5. 释放内存
            if (rawBitmap != finalBitmap) rawBitmap.recycle()
            finalBitmap.recycle()

            return file.absolutePath
        } catch (e: Exception) {
            Log.e("AudioPlayerScreen", "保存裁剪后的封面失败", e)
            return null
        }
    }

    /**
     * 居中裁剪并缩放逻辑
     */
    private fun centerCropAndScale(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height

        // 计算缩放比例（取大的那一边，保证铺满目标区域）
        val scale =
            (targetWidth.toFloat() / sourceWidth).coerceAtLeast(targetHeight.toFloat() / sourceHeight)

        val scaledWidth = scale * sourceWidth
        val scaledHeight = scale * sourceHeight

        // 计算裁剪区域（在原图基础上计算居中的矩形）
        val left = (targetWidth - scaledWidth) / 2
        val top = (targetHeight - scaledHeight) / 2

        val destRect = android.graphics.RectF(left, top, left + scaledWidth, top + scaledHeight)

        // 创建目标画布
        val output = createBitmap(targetWidth, targetHeight)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, null, destRect, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))

        return output
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // 生成二维码 Bitmap
    fun generateQRCode(content: String, size: Int = 512): ImageBitmap? {
        return try {
            val bits = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bits.width
            val height = bits.height
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] =
                        if (bits[x, y]) Color.BLACK else Color.WHITE
                }
            }
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成二维码 Bitmap（直接返回 Android Bitmap，供非 Compose 场景使用）
     */
    fun generateQRCodeBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val bits = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bits.width
            val height = bits.height
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] = if (bits[x, y]) Color.BLACK else Color.WHITE
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将 Assets 里的字体拷贝到私有目录，并返回绝对路径
     */
    fun prepareFont(context: Context, fontName: String): String {
        val fontFile = File(context.filesDir, fontName)

        // 如果文件不存在才拷贝，避免每次启动都耗时
        if (!fontFile.exists()) {
            context.assets.open("fonts/$fontName").use { input ->
                fontFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return fontFile.absolutePath
    }
}

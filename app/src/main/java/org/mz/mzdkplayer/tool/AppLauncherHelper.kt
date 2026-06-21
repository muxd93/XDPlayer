package org.mz.mzdkplayer.tool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import org.mz.mzdkplayer.MzDkPlayerApplication

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

object AppLauncherHelper {
    private val context: Context
        get() = MzDkPlayerApplication.context

    private val packageManager: PackageManager
        get() = context.packageManager

    /**
     * 获取可启动的 App 列表（排除自身）
     */
    fun getLaunchableApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        val myPackageName = context.packageName

        return resolveInfos
            .filter { it.activityInfo.packageName != myPackageName }
            .map { ri ->
                AppInfo(
                    packageName = ri.activityInfo.packageName,
                    label = ri.loadLabel(packageManager).toString(),
                    icon = ri.activityInfo.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label }
    }

    /**
     * 启动指定包名的 App
     */
    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取 App 图标
     */
    fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取 App 名称
     */
    fun getAppLabel(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    /**
     * 将任意 Drawable（含 AdaptiveIconDrawable / VectorDrawable）转为 ImageBitmap。
     * 直接强转 BitmapDrawable 会丢失现代图标，需用 Canvas 绘制。
     */
    fun drawableToImageBitmap(drawable: Drawable?, sizePx: Int = 144): ImageBitmap? {
        if (drawable == null) return null
        return try {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

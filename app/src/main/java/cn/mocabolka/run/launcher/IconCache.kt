package cn.mocabolka.run.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * 应用图标磁盘缓存。
 *
 * 将解码后的图标 Bitmap 以 PNG 格式存入 `filesDir/icons/{pkg}.png`，
 * 后续启动时直接从磁盘读取，避免反复调用 [PackageManager.loadIcon]（主要性能瓶颈）。
 */
object IconCache {

    private const val DIR = "icons"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).also { it.mkdirs() }

    private fun iconFile(context: Context, pkg: String): File =
        File(dir(context), "${pkg.replace('.', '_')}.png")

    /** 从磁盘加载图标 Bitmap。返回 null 表示缓存缺失。 */
    fun loadBitmap(context: Context, packageName: String): Bitmap? {
        val file = iconFile(context, packageName)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /** 将图标 Bitmap 保存到磁盘缓存。 */
    fun saveBitmap(context: Context, packageName: String, bitmap: Bitmap) {
        runCatching {
            val file = iconFile(context, packageName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        }
    }

    /** 清除全部图标缓存（极少调用，如数据损坏恢复）。 */
    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }
}

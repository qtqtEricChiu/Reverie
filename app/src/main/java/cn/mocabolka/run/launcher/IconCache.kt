package cn.mocabolka.run.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream

/**
 * 应用图标缓存：内存 LRU + 磁盘两级。
 *
 * - 内存层：同进程内重复取图标（重进 GamePage / 详情面板、滚动复用）免磁盘解码，
 *   直接消除页面切换与列表滚动的卡顿。
 * - 磁盘层：将解码后的图标 Bitmap 以 PNG 格式存入 `filesDir/icons/{pkg}.png`，
 *   后续启动时直接从磁盘读取，避免反复调用 [android.content.pm.PackageManager.getApplicationIcon]
 *   （主要启动性能瓶颈）。
 */
object IconCache {

    private const val DIR = "icons"
    private const val MEM_MAX_ENTRIES = 256

    /** 内存位图缓存：键为包名。容量按 256 个 96×96 ARGB_8888 图标（约 9MB）估算。 */
    private val memCache = LruCache<String, Bitmap>(MEM_MAX_ENTRIES)

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).also { it.mkdirs() }

    private fun iconFile(context: Context, pkg: String): File =
        File(dir(context), "${pkg.replace('.', '_')}.png")

    /** 从内存或磁盘加载图标 Bitmap。返回 null 表示两级缓存均缺失。 */
    fun loadBitmap(context: Context, packageName: String): Bitmap? {
        memCache.get(packageName)?.let { return it }
        val file = iconFile(context, packageName)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }
            .getOrNull()?.also { memCache.put(packageName, it) }
    }

    /** 同时写入内存与磁盘缓存。 */
    fun saveBitmap(context: Context, packageName: String, bitmap: Bitmap) {
        memCache.put(packageName, bitmap)
        runCatching {
            val file = iconFile(context, packageName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        }
    }

    /** 仅写入内存缓存（用于占位图标，避免把 1×1 占位 PNG 落盘污染磁盘缓存）。 */
    fun cacheMemoryOnly(packageName: String, bitmap: Bitmap) {
        memCache.put(packageName, bitmap)
    }

    /** 收到低内存警告时释放内存缓存，保留磁盘缓存供下次启动。 */
    fun clearMemory() {
        memCache.evictAll()
    }

    /** 清除全部图标缓存（极少调用，如数据损坏恢复）。 */
    fun clear(context: Context) {
        memCache.evictAll()
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }
}

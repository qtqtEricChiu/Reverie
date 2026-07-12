package cn.mocabolka.run.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 应用列表磁盘缓存。
 *
 * 仅缓存轻量元数据（包名 / 类名 / 标签 / 是否游戏 / 分类 / 安装时间 / 是否安装），
 * **不含图标 Bitmap**——图标在后台完整刷新后再填充。
 *
 * 作用：冷启动时先 [load] 缓存秒显列表（占位图标），UI 立即渲染；
 * 后台 [save] 由 refresh() 完成后写回，避免每次启动都重新枚举 Activity + 解码全部图标。
 */
object AppCache {

    private const val FILE_NAME = "app_list_cache_v1.json"

    data class CachedApp(
        val packageName: String,
        val className: String,
        val label: String,
        val isGame: Boolean,
        val category: Int,
        val firstInstallTime: Long,
        val installed: Boolean,
        val lastUsedTime: Long = 0L,
        val versionName: String = ""
    )

    /** 从磁盘读取缓存；任何异常或文件缺失时返回 null（降级为完整刷新）。 */
    fun load(context: Context): List<CachedApp>? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val arr = JSONArray(file.readText())
        val out = ArrayList<CachedApp>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += CachedApp(
                packageName = o.getString("pkg"),
                className = o.getString("cls"),
                label = o.getString("lbl"),
                isGame = o.getBoolean("game"),
                category = o.getInt("cat"),
                firstInstallTime = o.getLong("t"),
                installed = o.getBoolean("inst"),
                lastUsedTime = o.optLong("lut", 0L),
                versionName = o.optString("vn", "")
            )
        }
        if (out.isEmpty()) null else out
    }.getOrNull()

    /** 将完整应用列表写入磁盘（后台调用，异常静默忽略）。 */
    fun save(context: Context, apps: List<AppModel>) {
        runCatching {
            val arr = JSONArray()
            for (a in apps) {
                JSONObject().apply {
                    put("pkg", a.packageName)
                    put("cls", a.className)
                    put("lbl", a.label)
                    put("game", a.isGame)
                    put("cat", a.category)
                    put("t", a.firstInstallTime)
                    put("inst", a.installed)
                    put("lut", a.lastUsedTime)
                    put("vn", a.versionName)
                    arr.put(this)
                }
            }
            File(context.filesDir, FILE_NAME).writeText(arr.toString())
        }
    }
}

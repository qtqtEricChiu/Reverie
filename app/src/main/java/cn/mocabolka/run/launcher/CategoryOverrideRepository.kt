package cn.mocabolka.run.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * 应用分类映射覆盖仓库。
 *
 * 存储「包名 → 分类标签」的覆盖表（如 "com.tencent.mm" → "社交"），
 * 由用户导出的 AI 分类文件回灌。读取发生在 [HomeViewModel.refresh] 装配阶段，
 * 覆盖系统原始 [ApplicationInfo.category]，使更多原本落入「其他」的应用归到正确 Tab。
 *
 * 持久化采用独立 SharedPreferences（JSON 字符串），与设置项隔离，便于整体清空。
 */
class CategoryOverrideRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _overrides = MutableStateFlow(load())
    /** 响应式覆盖表，供 ViewModel 装配时读取。 */
    val overrides: StateFlow<Map<String, String>> = _overrides.asStateFlow()

    /** 合并写入一组覆盖（同包名后者覆盖前者），并落盘。 */
    fun applyOverrides(map: Map<String, String>) {
        if (map.isEmpty()) return
        val merged = _overrides.value.toMutableMap().apply { putAll(map) }
        _overrides.value = merged
        save(merged)
    }

    /** 清空全部覆盖（恢复为系统原始分类）。 */
    fun clear() {
        _overrides.value = emptyMap()
        prefs.edit { remove(KEY_MAP) }
    }

    private fun load(): Map<String, String> {
        val json = prefs.getString(KEY_MAP, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { map[it] = obj.getString(it) }
            map
        }.getOrDefault(emptyMap())
    }

    private fun save(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit { putString(KEY_MAP, obj.toString()) }
    }

    companion object {
        private const val PREFS_NAME = "category_overrides"
        private const val KEY_MAP = "map"
    }
}

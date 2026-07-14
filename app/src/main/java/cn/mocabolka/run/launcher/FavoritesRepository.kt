package cn.mocabolka.run.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reverie 「精选区」的收藏（固定）数据源。
 * 持久化到 SharedPreferences，X 键切换后将应用置顶到精选区。
 */
class FavoritesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    private val _pinned = MutableStateFlow(load())
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    /** 切换某包的固定状态。 */
    fun toggle(packageName: String) {
        val next = _pinned.value.toMutableSet().apply {
            if (contains(packageName)) remove(packageName) else add(packageName)
        }
        _pinned.value = next
        prefs.edit { putStringSet(KEY, next) }
    }

    /** 清空全部收藏（C4-5）。 */
    fun clear() {
        _pinned.value = emptySet()
        prefs.edit { putStringSet(KEY, emptySet()) }
    }

    private fun load(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    private companion object {
        const val KEY = "pinned_packages"
    }
}

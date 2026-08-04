package cn.mocabolka.run.launcher

import java.util.LinkedHashMap

/**
 * 手写 LRU 缓存（不依赖任何第三方库，对齐离线构建约束）。
 * 访问序通过 [LinkedHashMap] 的 [LinkedHashMap.accessOrder] = true 维护：
 * 每次 [get] 命中会把条目移到末尾，超容量时淘汰头部最久未访问项。
 */
class LruCache<K, V>(private val maxEntries: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? = map.put(key, value)

    @Synchronized
    fun contains(key: K): Boolean = map.containsKey(key)

    @Synchronized
    fun clear() = map.clear()
}

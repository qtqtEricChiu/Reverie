package cn.mocabolka.run.launcher

import org.json.JSONObject
import java.io.File

/**
 * 按月归档文件的序列化 / 反序列化（域相关唯一部件）。
 * 沿用项目既有的 [org.json] 手工解析风格 + tmpFile → renameTo 原子写范式
 * （与 [UsageStatsRepository.save] 保持一致），不引入任何第三方库。
 *
 * 归档文件格式（archives/<domain>/YYYY/MM.json）：
 * ```json
 * {
 *   "version": 1, "year": 2025, "month": 3, "record_count": 320,
 *   "days": { "2025-03-01": { "pkg": {"duration": 3600000, "count": 42} } },
 *   "totals": { "pkg": {"duration": 108000000, "count": 1260} },
 *   "day_counts": { "2025-03-01": 8 }
 * }
 * ```
 * 年度索引（archives/<domain>/YYYY/index.json）：
 * ```json
 * { "year": 2025, "months": { "3": {"file": "03.json", "count": 2011} } }
 * ```
 *
 * 注意：冷层不含小时分布（hourly 仍是热层 90 天专有），严格遵循分层格式。
 */
object TieredSerializer {

    fun monthFileName(month: Int): String = "%02d.json".format(month)

    /** 读取某月归档（不存在 / 损坏返回 null）。 */
    fun readMonth(file: File, year: Int, month: Int): ArchiveMonth? {
        if (!file.exists()) return null
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val days = LinkedHashMap<String, Map<String, DayEntry>>()
        val jDays = root.optJSONObject("days") ?: JSONObject()
        jDays.keys().forEach { dateKey ->
            val map = LinkedHashMap<String, DayEntry>()
            val jMap = jDays.optJSONObject(dateKey) ?: JSONObject()
            jMap.keys().forEach { pkg ->
                val jo = jMap.optJSONObject(pkg) ?: JSONObject()
                map[pkg] = DayEntry(pkg, jo.optLong("duration", 0L), jo.optInt("count", 0))
            }
            days[dateKey] = map
        }
        val recordCount = root.optInt("record_count", days.values.sumOf { it.size })
        return ArchiveMonth(year, month, days, recordCount)
    }

    /** 写某月归档（原子写）。自动推导 totals / day_counts / record_count。 */
    fun writeMonth(file: File, month: ArchiveMonth) {
        val jDays = JSONObject()
        month.days.forEach { (dateKey, map) ->
            val jMap = JSONObject()
            map.forEach { (pkg, e) ->
                jMap.put(pkg, JSONObject().apply { put("duration", e.ms); put("count", e.count) })
            }
            jDays.put(dateKey, jMap)
        }
        val totals = LinkedHashMap<String, DayEntry>()
        val dayCounts = LinkedHashMap<String, Int>()
        month.days.forEach { (dateKey, map) ->
            dayCounts[dateKey] = map.size
            map.forEach { (pkg, e) ->
                val t = totals[pkg]
                totals[pkg] = if (t == null) e else DayEntry(pkg, t.ms + e.ms, t.count + e.count)
            }
        }
        val jTotals = JSONObject()
        totals.forEach { (pkg, e) ->
            jTotals.put(pkg, JSONObject().apply { put("duration", e.ms); put("count", e.count) })
        }
        val jDayCounts = JSONObject()
        dayCounts.forEach { (d, c) -> jDayCounts.put(d, c) }
        val root = JSONObject().apply {
            put("version", 1)
            put("year", month.year)
            put("month", month.month)
            put("record_count", dayCounts.values.sum())
            put("days", jDays)
            put("totals", jTotals)
            put("day_counts", jDayCounts)
        }
        atomicWrite(file, root.toString())
    }

    /** 读取年度索引：month -> recordCount（不存在返回 null）。 */
    fun readYearIndex(yDir: File): Map<Int, Long>? {
        val f = File(yDir, "index.json")
        if (!f.exists()) return null
        val root = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return null
        val out = LinkedHashMap<Int, Long>()
        val jMonths = root.optJSONObject("months") ?: return out
        jMonths.keys().forEach { m ->
            out[m.toInt()] = jMonths.optJSONObject(m)?.optLong("count", 0L) ?: 0L
        }
        return out
    }

    /** 写年度索引。 */
    fun writeYearIndex(yDir: File, year: Int, months: Map<Int, Long>) {
        val jMonths = JSONObject()
        months.toSortedMap().forEach { (m, c) ->
            jMonths.put(m.toString(), JSONObject().apply {
                put("file", monthFileName(m))
                put("count", c)
            })
        }
        val root = JSONObject().apply { put("year", year); put("months", jMonths) }
        atomicWrite(File(yDir, "index.json"), root.toString())
    }

    /** 原子写：先写临时文件再 rename；跨设备 rename 失败时降级为直接覆盖写。 */
    private fun atomicWrite(target: File, json: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        runCatching {
            tmp.writeText(json)
            if (!tmp.renameTo(target)) target.writeText(json)
        }
    }
}

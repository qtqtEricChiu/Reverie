package cn.mocabolka.run.launcher

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.UserHandle
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * 应用使用时长统计 + 持久化。
 *
 * 设计：
 * - 每次启动（或进入前台）调用 [sync]，从系统 UsageStats 拉取自 [lastSync] 以来的使用事件，
 *   按"天"拆分并累加到各应用时长，持久化到应用私有文件 `usage_stats.json`。
 * - 增量累计，不重复。去噪：小于 [MIN_VALID_DURATION_MS] 的事件跳过（闪退/快速切换）。
 * - 预聚合缓存：在 sync 时同步维护周/月/年报缓存，查询 O(1)，不再遍历所有天。
 * - 小时分布缓存：在 sync 时按小时切片累加，避免每次 getHourly() 实时拉取系统事件。
 * - JSON 写入使用临时文件 + rename 保证原子性。
 */
class UsageStatsRepository(private val context: Context) {

    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** 按天存储：dateKey(yyyy-MM-dd) -> (pkg -> ms) */
    private val days = LinkedHashMap<String, MutableMap<String, Long>>()
    /** 各应用累计总时长（含历史所有天） */
    private val totals = LinkedHashMap<String, Long>()
    /** 上次同步到的系统时间（ms），增量同步起点 */
    private var lastSync: Long = 0L

    /** ── 预聚合缓存（sync 时增量维护，查询 O(1)） ── */
    /** 本周（周一 0 点起）各应用时长缓存 */
    private var _weeklyCache = emptyList<UsageEntry>()
    /** 本月（1 号 0 点起）各应用时长缓存 */
    private var _monthlyCache = emptyList<UsageEntry>()
    /** 本年（1 月 1 号 0 点起）各应用时长缓存 */
    private var _yearlyCache = emptyList<UsageEntry>()
    /** 最近一次缓存刷新的 dateKey（yyyy-MM-dd），缓存跨天时失效 */
    private var _cacheDateKey: String = ""

    /** ── 小时分布缓存（sync 时维护） ── */
    /** 今日 24 小时分布（ms），索引即小时。仅在当天有效。 */
    private var _hourlyCache = LongArray(24)
    /** _hourlyCache 对应的日期 dateKey，非当天时失效 */
    private var _hourlyDateKey: String = ""

    private val file get() = java.io.File(context.filesDir, FILE_NAME)
    private val tmpFile get() = java.io.File(context.filesDir, "${FILE_NAME}.tmp")


    private var loaded = false

    init { /* load() 延迟到 ensureLoaded() 首次调用，避免主线程阻塞 */ }

    /** 确保数据已加载（懒加载，线程安全）。sync() / 查询方法内部自动调用。 */
    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        load()
        refreshAggregateCache()
        loaded = true
    }


    // ──────────────────────────────────────────────
    // 持久化读写
    // ──────────────────────────────────────────────
    @Synchronized
    private fun load() {
        days.clear()
        totals.clear()
        lastSync = 0L
        _weeklyCache = emptyList()
        _monthlyCache = emptyList()
        _yearlyCache = emptyList()
        _cacheDateKey = ""
        _hourlyCache = LongArray(24)
        _hourlyDateKey = ""
        runCatching {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            lastSync = root.optLong(KEY_LAST_SYNC, 0L)
            val jTotals = root.optJSONObject(KEY_TOTALS) ?: JSONObject()
            jTotals.keys().forEach { pkg ->
                totals[pkg] = jTotals.getLong(pkg)
            }
            val jDays = root.optJSONObject(KEY_DAYS) ?: JSONObject()
            jDays.keys().forEach { date ->
                val map = LinkedHashMap<String, Long>()
                val jMap = jDays.getJSONObject(date)
                jMap.keys().forEach { pkg -> map[pkg] = jMap.getLong(pkg) }
                days[date] = map
            }
        }.onFailure { e ->
            Log.w(TAG, "读取使用时长持久化失败，已忽略", e)
        }
    }

    @Synchronized
    private fun save() {
        runCatching {
            val jDays = JSONObject()
            days.forEach { (date, map) ->
                val jMap = JSONObject()
                map.forEach { (pkg, ms) -> jMap.put(pkg, ms) }
                jDays.put(date, jMap)
            }
            val jTotals = JSONObject()
            totals.forEach { (pkg, ms) -> jTotals.put(pkg, ms) }
            val root = JSONObject().apply {
                put(KEY_LAST_SYNC, lastSync)
                put(KEY_DAYS, jDays)
                put(KEY_TOTALS, jTotals)
            }
            // 原子写入：先写临时文件再 rename，避免写半截文件损坏
            tmpFile.writeText(root.toString())
            tmpFile.renameTo(file)
        }.onFailure { e ->
            Log.w(TAG, "写入使用时长持久化失败", e)
        }
    }

    // ──────────────────────────────────────────────
    // 增量同步
    // ──────────────────────────────────────────────
    /**
     * 从 [lastSync] 同步到当前时刻的使用事件。
     * - 去噪：`dur < [MIN_VALID_DURATION_MS]` 的事件跳过（闪退/快速切换）
     * - 上限：`dur > [MAX_SINGLE_DURATION_MS]` 截断（系统 bug 产生的错误时间戳）
     * - 小时分布：同步时按小时切片累加到 [_hourlyCache]（仅当天有效）
     * - 预聚合缓存：同步后刷新周/月/年报缓存
     * @return 本次新增/更新的应用包名集合。
     */
    @Synchronized
    fun sync(): Set<String> {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val start = if (lastSync > 0L) lastSync else now - SYNC_WINDOW_MS
        if (start >= now) return emptySet()

        val changed = LinkedHashSet<String>()
        val todayKey = todayKey()
        // 小时分布累加器（仅当天同步时有效）
        val hourlyAcc = if (_hourlyDateKey == todayKey) _hourlyCache else LongArray(24)
        val isTodaySync = true // 只要 start <= 今天 0 点后，小时分布可能被影响

        runCatching {
            val events = usm.queryEvents(start, now)
            val openMap = LinkedHashMap<String, Long>()
            val cal = Calendar.getInstance()
            while (events.hasNextEvent()) {
                val ev = UsageEvents.Event()
                events.getNextEvent(ev)
                val pkg = ev.packageName ?: continue
                when (ev.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        // 溢出保护：如果该包已有前台记录（事件流异常），跳过旧记录
                        if (openMap.containsKey(pkg)) {
                            Log.w(TAG, "丢弃重复 foreground 事件: $pkg")
                        }
                        openMap[pkg] = ev.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val enter = openMap.remove(pkg) ?: continue
                        var dur = ev.timeStamp - enter
                        // 去噪：< 1s 跳过（闪退/快速切换）
                        if (dur < MIN_VALID_DURATION_MS) continue
                        // 上限保护：> 24h 截断
                        if (dur > MAX_SINGLE_DURATION_MS) dur = MAX_SINGLE_DURATION_MS
                        cal.timeInMillis = enter
                        val dk = dateKey(cal)
                        val dayMap = days.getOrPut(dk) { LinkedHashMap() }
                        dayMap[pkg] = (dayMap[pkg] ?: 0L) + dur
                        totals[pkg] = (totals[pkg] ?: 0L) + dur
                        changed.add(pkg)
                        // 小时分布：仅累加当天的 foreground 切片
                        if (dk == todayKey) {
                            addToHourly(hourlyAcc, enter, ev.timeStamp)
                        }
                    }
                }
            }
            // 跨同步区间仍在前台的应用：按当前时刻截断
            openMap.forEach { (pkg, enter) ->
                var dur = now - enter
                if (dur < MIN_VALID_DURATION_MS) return@forEach
                if (dur > MAX_SINGLE_DURATION_MS) dur = MAX_SINGLE_DURATION_MS
                cal.timeInMillis = enter
                val dk = dateKey(cal)
                val dayMap = days.getOrPut(dk) { LinkedHashMap() }
                dayMap[pkg] = (dayMap[pkg] ?: 0L) + dur
                totals[pkg] = (totals[pkg] ?: 0L) + dur
                changed.add(pkg)
                if (dk == todayKey) {
                    addToHourly(hourlyAcc, enter, now)
                }
            }
            // 清理残留的 openMap 条目（超过 24h 未匹配到 BACKGROUND 的视为残留）
            val staleThreshold = now - MAX_SINGLE_DURATION_MS
            openMap.entries.removeAll { it.value < staleThreshold }

            // 关键修复：仅在实际拉到事件时才推进 lastSync。
            // 首次 sync() 可能在用户授权前就被调用（应用启动刷新循环），
            // 此时 queryEvents 因无权限返回空，但若仍推进 lastSync，
            // 会导致授权后第二次 sync 使用 lastSync（而非 30 天窗口）作为起始点——
            // 30 天历史窗口永久丢失，用户只能看到授权之后的数据。
            if (changed.isNotEmpty() || lastSync > 0L) {
                lastSync = now
            }
            // 更新小时分布缓存
            if (changed.isNotEmpty() || _hourlyDateKey != todayKey) {
                _hourlyCache = hourlyAcc
                _hourlyDateKey = todayKey
            }
            // 刷新预聚合缓存
            refreshAggregateCache()
            save()
        }.onFailure { e ->
            Log.w(TAG, "同步使用时长失败", e)
        }
        return changed
    }

    /** 把 [start, end] 区间的时长按小时切片累加到 [out] */
    private fun addToHourly(out: LongArray, startMs: Long, endMs: Long) {
        var cursor = startMs
        val cal = Calendar.getInstance()
        while (cursor < endMs) {
            cal.timeInMillis = cursor
            val hourStart = (cal.clone() as Calendar).apply {
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val hourEnd = hourStart + 60L * 60 * 1000
            val sliceEnd = minOf(endMs, hourEnd)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            out[hour] += (sliceEnd - cursor)
            cursor = sliceEnd
        }
    }

    /** 刷新周/月/年报预聚合缓存 */
    private fun refreshAggregateCache() {
        val today = todayKey()
        if (_cacheDateKey == today && _weeklyCache.isNotEmpty()) return // 当天已缓存
        _weeklyCache = doAggregateFrom(weekStartMs())
        _monthlyCache = doAggregateFrom(monthStartMs())
        _yearlyCache = doAggregateFrom(yearStartMs())
        _cacheDateKey = today
    }

    /** 实际执行聚合查询（无缓存） */
    private fun doAggregateFrom(fromMs: Long): List<UsageEntry> {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMs }
        val fromKey = dateKey(cal)
        val acc = LinkedHashMap<String, Long>()
        days.entries.filter { it.key >= fromKey }.forEach { (_, map) ->
            map.forEach { (pkg, ms) -> acc[pkg] = (acc[pkg] ?: 0L) + ms }
        }
        return acc.map { (pkg, ms) -> UsageEntry(pkg, ms) }
            .sortedByDescending { it.ms }
    }

    // ──────────────────────────────────────────────
    // 报表查询
    // ──────────────────────────────────────────────
    /** 今日（自然日 0 点起）各应用时长，降序。 */
    fun getDaily(): List<UsageEntry> { ensureLoaded(); return reportForDay(todayKey()) }

    /** 本周（周一 0 点起）各应用时长，降序。使用预聚合缓存 O(1)。 */
    fun getWeekly(): List<UsageEntry> { ensureLoaded(); return _weeklyCache }

    /** 本月（1 号 0 点起）各应用时长，降序。使用预聚合缓存 O(1)。 */
    fun getMonthly(): List<UsageEntry> { ensureLoaded(); return _monthlyCache }

    /** 本年（1 月 1 号 0 点起）各应用时长，降序。使用预聚合缓存 O(1)。 */
    fun getYearly(): List<UsageEntry> { ensureLoaded(); return _yearlyCache }

    /** 指定日（dateKey，yyyy-MM-dd）各应用时长，降序。 */
    fun getDailyFor(dateKey: String): List<UsageEntry> { ensureLoaded(); return reportForDay(dateKey) }

    /** 指定锚点日所在周（周一 0 点起）各应用时长，降序。锚点非当天时直接计算。 */
    fun getWeeklyFor(anchorMs: Long): List<UsageEntry> {
        ensureLoaded()
        return if (isToday(anchorMs)) _weeklyCache else doAggregateFrom(weekStartMs(anchorMs))
    }

    /** 指定锚点日所在月（1 号 0 点起）各应用时长，降序。 */
    fun getMonthlyFor(anchorMs: Long): List<UsageEntry> {
        ensureLoaded()
        return if (isToday(anchorMs)) _monthlyCache else doAggregateFrom(monthStartMs(anchorMs))
    }

    /** 指定锚点日所在年（1 月 1 号 0 点起）各应用时长，降序。 */
    fun getYearlyFor(anchorMs: Long): List<UsageEntry> {
        ensureLoaded()
        return if (isToday(anchorMs)) _yearlyCache else doAggregateFrom(yearStartMs(anchorMs))
    }

    /** 某应用的历史累计总时长。 */
    fun getTotal(packageName: String): Long { ensureLoaded(); return totals[packageName] ?: 0L }

    /** 今日某应用时长（用于详情面板"今日"展示）。 */
    fun getTodayFor(packageName: String): Long {
        ensureLoaded()
        return days[todayKey()]?.get(packageName) ?: 0L
    }

    /** 今日所有应用使用时长映射（R11-3，供列表项/详情面板批量读取）。 */
    fun getTodayMap(): Map<String, Long> { ensureLoaded(); return days[todayKey()] ?: emptyMap() }

    /**
     * 今日 0-23 小时各小时的使用总时长（ms），索引即小时。
     * 优先返回 sync 时缓存的 [_hourlyCache]，若缓存非当天则实时计算。
     */
    fun getHourly(): LongArray {
        ensureLoaded()
        val today = todayKey()
        if (_hourlyDateKey == today) return _hourlyCache
        // 缓存失效时实时计算（首次安装/跨天未 sync）
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return hourlyFromEvents(cal.timeInMillis, System.currentTimeMillis())
    }

    /** 最近 [n] 天（含今天）每天总时长（ms），按日期升序。索引 0 为最远一天。 */
    fun getDaySeries(n: Int): LongArray = getDaySeries(n, System.currentTimeMillis())

    /** 最近 [n] 天（含 [anchorMs] 当天）每天总时长（ms），按日期升序。索引 0 为最远一天。 */
    fun getDaySeries(n: Int, anchorMs: Long): LongArray {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMs }
        val series = LongArray(n)
        for (i in 0 until n) {
            val key = dateKey(cal)
            val total = days[key]?.values?.sum() ?: 0L
            series[n - 1 - i] = total
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return series
    }

    /** 区间 [fromKey..toKey]（含两端）所有包总时长。 */
    fun getRangeTotal(fromKey: String, toKey: String): Long {
        ensureLoaded()
        var sum = 0L
        days.entries.filter { it.key in fromKey..toKey }.forEach { (_, m) ->
            m.values.forEach { sum += it }
        }
        return sum
    }

    /** 当前累计所有历史总时长。 */
    fun getAllTimeTotal(): Long { ensureLoaded(); return totals.values.sum() }

    /**
     * 拉取区间 [fromMs, toMs] 的 UsageEvents 事件，把每个应用的 foreground 时长
     * 按"事件发生的小时"归到对应桶。返回 24 长度的 LongArray。
     * 与 getHourly() 区别：这是事件级粒度，按发生小时累加。
     */
    private fun hourlyFromEvents(fromMs: Long, toMs: Long): LongArray {
        val out = LongArray(24)
        if (!hasUsagePermission()) return out
        val events = try {
            usm.queryEvents(fromMs, toMs)
        } catch (e: Exception) {
            Log.w(TAG, "queryEvents 失败", e); return out
        }
        val lastForeground = HashMap<String, Long>()
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> lastForeground[ev.packageName] = ev.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = lastForeground.remove(ev.packageName) ?: continue
                    val end = ev.timeStamp
                    // 把 [start, end] 切片归到对应小时
                    var cursor = start
                    while (cursor < end) {
                        val cal = Calendar.getInstance().apply { timeInMillis = cursor }
                        val hourStart = (cal.clone() as Calendar).apply {
                            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val hourEnd = hourStart + 60L * 60 * 1000
                        val sliceEnd = minOf(end, hourEnd)
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        out[hour] += (sliceEnd - cursor)
                        cursor = sliceEnd
                    }
                }
            }
        }
        return out
    }

    private fun hasUsagePermission(): Boolean = try {
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        !stats.isNullOrEmpty()
    } catch (e: Exception) { false }

    private fun reportForDay(dateKey: String): List<UsageEntry> =
        days[dateKey]?.toList()
            ?.map { (pkg, ms) -> UsageEntry(pkg, ms) }
            ?.sortedByDescending { it.ms } ?: emptyList()

    private fun isToday(ms: Long): Boolean = dateKey(Calendar.getInstance().apply { timeInMillis = ms }) == todayKey()

    // ──────────────────────────────────────────────
    // 时间工具
    // ──────────────────────────────────────────────
    private fun dateKey(cal: Calendar): String =
        "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

    private fun todayKey(): String = dateKey(Calendar.getInstance())

    private fun weekStartMs(anchorMs: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = anchorMs
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun monthStartMs(anchorMs: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = anchorMs
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun yearStartMs(anchorMs: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = anchorMs
        set(Calendar.MONTH, Calendar.JANUARY); set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    data class UsageEntry(val packageName: String, val ms: Long)

    companion object {
        private const val TAG = "UsageStatsRepo"
        private const val FILE_NAME = "usage_stats.json"
        private const val KEY_LAST_SYNC = "lastSync"
        private const val KEY_DAYS = "days"
        private const val KEY_TOTALS = "totals"
        /** 首装无 lastSync 时，最多回看 30 天，避免一次拉取过久。 */
        private const val SYNC_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        /** 最小有效使用时长（ms）：< 1s 的事件跳过（闪退/快速切换）。 */
        private const val MIN_VALID_DURATION_MS = 1000L
        /** 单次最大使用时长（ms）：> 24h 截断，防止系统 bug 产生的错误时间戳。 */
        private const val MAX_SINGLE_DURATION_MS = 24L * 60 * 60 * 1000
    }
}

package cn.mocabolka.run.launcher

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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
class UsageStatsRepository(private val context: Context) : HotStore {

    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** 按天存储：dateKey(yyyy-MM-dd) -> (pkg -> ms) */
    private val days = LinkedHashMap<String, MutableMap<String, Long>>()
    /** 各应用累计总时长（含历史所有天） */
    private val totals = LinkedHashMap<String, Long>()
    /** 按天启动次数：dateKey -> (pkg -> count)。与 days 同粒度增量累计，绝不覆写。 */
    private val dayCounts = LinkedHashMap<String, MutableMap<String, Int>>()
    /** 各应用累计启动次数（含历史所有天） */
    private val totalCounts = LinkedHashMap<String, Int>()
    /** 按天持久化小时分布快照：dateKey -> HourlySnap。今日实时维护，过往日回看时保留。 */
    private val hourly = LinkedHashMap<String, HourlySnap>()
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
    /** 今日逐小时明细：每应用(时长ms, 启动次数)。仅当天有效。 */
    data class HourlyDetail(
        val ms: LongArray,
        val count: IntArray,
        val breakdown: Array<Map<String, Pair<Long, Int>>>
    )
    /** 单包在某小时桶的（时长ms, 启动次数）。可序列化。 */
    private data class HourlyPkg(val ms: Long, val cnt: Int)
    /**
     * 某天逐小时明细快照（持久化介质）：
     * [ms]/[count] 长度 24；[breakdown] 长度 24，每元素为 pkg -> (ms, count)。
     */
    private data class HourlySnap(
        val ms: LongArray,
        val count: IntArray,
        val breakdown: Array<MutableMap<String, HourlyPkg>>
    ) {
        fun toJson(): JSONObject {
            val o = JSONObject()
            o.put("m", JSONArray(ms.toList()))
            o.put("c", JSONArray(count.toList()))
            val brk = JSONArray()
            breakdown.forEach { m ->
                val jo = JSONObject()
                m.forEach { (pkg, v) -> jo.put(pkg, JSONObject().apply { put("m", v.ms); put("c", v.cnt) }) }
                brk.put(jo)
            }
            o.put("b", brk)
            return o
        }
        companion object {
            fun fromJson(o: JSONObject?): HourlySnap? {
                if (o == null) return null
                val m = (o.optJSONArray("m") ?: JSONArray()).let { a ->
                    LongArray(24) { if (it < a.length()) a.optLong(it) else 0L }
                }
                val c = (o.optJSONArray("c") ?: JSONArray()).let { a ->
                    IntArray(24) { if (it < a.length()) a.optInt(it) else 0 }
                }
                val b = (o.optJSONArray("b") ?: JSONArray()).let { arr ->
                    Array(24) { i ->
                        val jo = if (i < arr.length()) arr.optJSONObject(i) else null
                        val mp: MutableMap<String, HourlyPkg> = LinkedHashMap()
                        jo?.keys()?.forEach { pkg ->
                            val p = jo.getJSONObject(pkg)
                            mp[pkg] = HourlyPkg(p.optLong("m"), p.optInt("c"))
                        }
                        mp
                    }
                }
                return HourlySnap(m, c, b)
            }
        }
        /** 转为查询用的只读结构。 */
        fun toDetail(): HourlyDetail = HourlyDetail(
            ms, count,
            breakdown.map { it.mapValues { (_, v) -> v.ms to v.cnt } }.toTypedArray()
        )
    }
    private var _hourlyDetailCache: HourlyDetail? = null
    /** _hourlyDetailCache 对应的日期 dateKey，非当天时失效 */
    private var _hourlyDateKey: String = ""
    // 当天小时分布的实时计算缓存（短 TTL）。增量 _hourlyDetailCache 在 lastSync
    // 跨过当日早晨时段后会丢失小时数据（用户反馈：5h 总时长小时图仅显示 40 分钟），
    // 故 getHourlyDetail() 对当天改为始终实时重算 queryEvents(todayStart, now)，
    // 此处仅做 10s 窗口 memo 防止同帧 3 次 queryEvents。
    @Volatile private var _todayHourlyCache: HourlyDetail? = null
    @Volatile private var _todayHourlyCacheAt: Long = 0L
    private val _todayHourlyLock = Any()

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
        dayCounts.clear()
        totalCounts.clear()
        hourly.clear()
        lastSync = 0L
        _weeklyCache = emptyList()
        _monthlyCache = emptyList()
        _yearlyCache = emptyList()
        _cacheDateKey = ""
        _hourlyDetailCache = HourlyDetail(LongArray(24), IntArray(24), Array(24) { emptyMap() })
        _hourlyDateKey = ""
        runCatching {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val version = root.optInt("version", 1)
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
            val jCounts = root.optJSONObject(KEY_DAY_COUNTS) ?: JSONObject()
            jCounts.keys().forEach { date ->
                val map = LinkedHashMap<String, Int>()
                val jMap = jCounts.optJSONObject(date) ?: JSONObject()
                jMap.keys().forEach { pkg -> map[pkg] = jMap.getInt(pkg) }
                dayCounts[date] = map
            }
            val jTotalCounts = root.optJSONObject(KEY_TOTAL_COUNTS) ?: JSONObject()
            jTotalCounts.keys().forEach { pkg -> totalCounts[pkg] = jTotalCounts.getInt(pkg) }
            val jHourly = root.optJSONObject(KEY_HOURLY) ?: JSONObject()
            jHourly.keys().forEach { date ->
                HourlySnap.fromJson(jHourly.optJSONObject(date))?.let { hourly[date] = it }
            }
            // 旧版（version<2）迁移：365 天全量 → 热层 90 天 + 冷层按月归档
            if (version < StorageConfig.SCHEMA_VERSION) {
                runCatching {
                    val bak = java.io.File(file.parentFile, "${FILE_NAME}.bak")
                    if (!bak.exists()) file.copyTo(bak, overwrite = false)
                    val cutoff = dateKeyOf(
                        System.currentTimeMillis() - StorageConfig.HOT_WINDOW_DAYS * 24L * 60 * 60 * 1000
                    )
                    ArchiveRotation.rotateExpired(this@UsageStatsRepository, archiveDir(), cutoff)
                    save()
                }.onFailure { e -> Log.w(TAG, "旧版数据迁移失败，保留原状", e) }
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
            val jCounts = JSONObject()
            dayCounts.forEach { (date, map) ->
                val jMap = JSONObject()
                map.forEach { (pkg, c) -> jMap.put(pkg, c) }
                jCounts.put(date, jMap)
            }
            val jTotalCounts = JSONObject()
            totalCounts.forEach { (pkg, c) -> jTotalCounts.put(pkg, c) }
            val jHourly = JSONObject()
            hourly.forEach { (date, snap) -> jHourly.put(date, snap.toJson()) }
            val root = JSONObject().apply {
                put("version", StorageConfig.SCHEMA_VERSION)
                put(KEY_LAST_SYNC, lastSync)
                put(KEY_DAYS, jDays)
                put(KEY_TOTALS, jTotals)
                put(KEY_DAY_COUNTS, jCounts)
                put(KEY_TOTAL_COUNTS, jTotalCounts)
                put(KEY_HOURLY, jHourly)
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
        val prev = if (_hourlyDateKey == todayKey) _hourlyDetailCache else null
        val hourlyAcc = prev?.ms?.copyOf() ?: LongArray(24)
        val hourlyCountAcc = prev?.count?.copyOf() ?: IntArray(24)
        val hourlyBreakAcc = prev?.breakdown?.map { it.toMutableMap() }?.toTypedArray()
            ?: Array(24) { mutableMapOf<String, Pair<Long, Int>>() }
        val isTodaySync = true // 只要 start <= 今天 0 点后，小时分布可能被影响
        // 本次同步区间各天各应用启动次数累加器（增量合并到 dayCounts/totalCounts）
        val dayCountAcc = LinkedHashMap<String, MutableMap<String, Int>>()

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
                        dayCountAcc.getOrPut(dk) { LinkedHashMap() }[pkg] =
                            (dayCountAcc[dk]?.get(pkg) ?: 0) + 1
                        // 小时分布：仅累加当天的 foreground 切片
                        if (dk == todayKey) {
                            addToHourly(hourlyAcc, hourlyCountAcc, hourlyBreakAcc, enter, ev.timeStamp, pkg)
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
                dayCountAcc.getOrPut(dk) { LinkedHashMap() }[pkg] =
                    (dayCountAcc[dk]?.get(pkg) ?: 0) + 1
                if (dk == todayKey) {
                    addToHourly(hourlyAcc, hourlyCountAcc, hourlyBreakAcc, enter, now, pkg)
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
            // 审计补充：若已确认持有权限但 30 天窗口内确实无任何使用记录
            // （changed 为空的另一种合法情形），首次同步视为已完成，正常推进
            // lastSync，避免后续每次 sync 都重复扫描整个 30 天窗口。
            if (changed.isNotEmpty() || lastSync > 0L || hasUsagePermission()) {
                lastSync = now
            }
            // 更新小时分布缓存
            if (changed.isNotEmpty() || _hourlyDateKey != todayKey) {
                _hourlyDetailCache = HourlyDetail(
                hourlyAcc,
                hourlyCountAcc,
                hourlyBreakAcc.map { it.toMap() }.toTypedArray()
            )
                _hourlyDateKey = todayKey
                // 持久化今日小时分布快照（时长+次数+每应用），过往日回看时直接命中
                hourly[todayKey] = HourlySnap(
                    hourlyAcc,
                    hourlyCountAcc,
                    hourlyBreakAcc.map { mp ->
                        mp.mapValues { (_, v) -> HourlyPkg(v.first, v.second) }.toMutableMap()
                    }.toTypedArray()
                )
            }
            // 合并本次同步的各天启动次数（增量，绝不覆写历史）
            dayCountAcc.forEach { (dk, m) ->
                val dm = dayCounts.getOrPut(dk) { LinkedHashMap() }
                m.forEach { (pkg, c) ->
                    dm[pkg] = (dm[pkg] ?: 0) + c
                    totalCounts[pkg] = (totalCounts[pkg] ?: 0) + c
                }
            }
            // 数据保留上限：裁掉超过 RETENTION_DAYS 的按天明细，防止无限累积
            pruneOldDays()
            // 刷新预聚合缓存
            refreshAggregateCache()
            save()
        }.onFailure { e ->
            Log.w(TAG, "同步使用时长失败", e)
        }
        return changed
    }

    /** 把 [start, end] 区间按小时切片累加时长/次数/每应用明细。 */
    private fun addToHourly(
        ms: LongArray,
        count: IntArray,
        breakdown: Array<MutableMap<String, Pair<Long, Int>>>,
        startMs: Long,
        endMs: Long,
        pkg: String
    ) {
        var cursor = startMs
        var first = true
        val cal = Calendar.getInstance()
        while (cursor < endMs) {
            cal.timeInMillis = cursor
            val hourStart = (cal.clone() as Calendar).apply {
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val hourEnd = hourStart + 60L * 60 * 1000
            val sliceEnd = minOf(endMs, hourEnd)
            val sliceMs = sliceEnd - cursor
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            ms[hour] += sliceMs
            if (first) count[hour] += 1
            val b = breakdown[hour][pkg] ?: (0L to 0)
            breakdown[hour][pkg] = (b.first + sliceMs) to (b.second + if (first) 1 else 0)
            cursor = sliceEnd
            first = false
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
        val cnt = LinkedHashMap<String, Int>()
        days.entries.filter { it.key >= fromKey }.forEach { (dk, map) ->
            map.forEach { (pkg, ms) -> acc[pkg] = (acc[pkg] ?: 0L) + ms }
            dayCounts[dk]?.forEach { (pkg, c) -> cnt[pkg] = (cnt[pkg] ?: 0) + c }
        }
        return acc.map { (pkg, ms) -> UsageEntry(pkg, ms, cnt[pkg] ?: 0) }
            .sortedByDescending { it.ms }
    }

    // ──────────────────────────────────────────────
    // 报表查询
    // ──────────────────────────────────────────────
    /** 今日（自然日 0 点起）各应用时长，降序。 */
    @Synchronized
    fun getDaily(): List<UsageEntry> { ensureLoaded(); return reportForDay(todayKey()) }

    /** 本周（周一 0 点起）各应用时长，降序。使用预聚合缓存 O(1)。 */
    @Synchronized
    fun getWeekly(): List<UsageEntry> { ensureLoaded(); return _weeklyCache }

    /** 本月（1 号 0 点起）各应用时长，降序。使用预聚合缓存 O(1)。 */
    @Synchronized
    fun getMonthly(): List<UsageEntry> { ensureLoaded(); return _monthlyCache }

    /** 本年（1 月 1 号 0 点起）各应用时长，降序。使用预聚合缓存 O(1)。 */
    @Synchronized
    fun getYearly(): List<UsageEntry> { ensureLoaded(); return _yearlyCache }

    /** 指定日（dateKey，yyyy-MM-dd）各应用时长，降序。 */
    @Synchronized
    fun getDailyFor(dateKey: String): List<UsageEntry> { ensureLoaded(); return reportForDay(dateKey) }

    /** 指定锚点日所在周（周一 0 点起）各应用时长，降序。锚点非当天时直接计算。 */
    @Synchronized
    fun getWeeklyFor(anchorMs: Long): List<UsageEntry> {
        ensureLoaded()
        return if (isToday(anchorMs)) _weeklyCache else doAggregateFrom(weekStartMs(anchorMs))
    }

    /** 指定锚点日所在月（1 号 0 点起）各应用时长，降序。 */
    @Synchronized
    fun getMonthlyFor(anchorMs: Long): List<UsageEntry> {
        ensureLoaded()
        return if (isToday(anchorMs)) _monthlyCache else doAggregateFrom(monthStartMs(anchorMs))
    }

    /** 指定锚点日所在年（1 月 1 号 0 点起）各应用时长，降序。 */
    @Synchronized
    fun getYearlyFor(anchorMs: Long): List<UsageEntry> {
        ensureLoaded()
        return if (isToday(anchorMs)) _yearlyCache else doAggregateFrom(yearStartMs(anchorMs))
    }

    /** 某应用的历史累计总时长。 */
    @Synchronized
    fun getTotal(packageName: String): Long { ensureLoaded(); return totals[packageName] ?: 0L }

    /** 今日某应用时长（用于详情面板"今日"展示）。 */
    @Synchronized
    fun getTodayFor(packageName: String): Long {
        ensureLoaded()
        return days[todayKey()]?.get(packageName) ?: 0L
    }

    /** 今日所有应用使用时长映射（R11-3，供列表项/详情面板批量读取）。 */
    @Synchronized
    fun getTodayMap(): Map<String, Long> { ensureLoaded(); return days[todayKey()] ?: emptyMap() }

    /**
     * 今日逐小时明细（时长/次数/每应用）。
     * 关键修复（用户反馈：5h 总时长小时图仅显示 40 分钟）：
     * 增量 _hourlyDetailCache 缓存在 lastSync 越过当日早晨时段后会缺失小时数据，
     * 故对当天**始终**从系统 UsageStats 实时重算 queryEvents(todayStart, now)，
     * 10s 窗口 memo 避免同帧多次 queryEvents。
     */
    private fun getHourlyDetail(): HourlyDetail {
        ensureLoaded()
        val now = System.currentTimeMillis()
        synchronized(_todayHourlyLock) {
            val cached = _todayHourlyCache
            if (cached != null && now - _todayHourlyCacheAt < 10_000L) return cached
            val fresh = computeHourlyDetail(todayStartMs(), now)
            _todayHourlyCache = fresh
            _todayHourlyCacheAt = now
            return fresh
        }
    }

    @Synchronized
    fun getHourly(): LongArray = getHourlyDetail().ms
    @Synchronized
    fun getHourlyCounts(): IntArray = getHourlyDetail().count
    @Synchronized
    fun getHourlyAppBreakdown(): Array<Map<String, Pair<Long, Int>>> = getHourlyDetail().breakdown

    /**
     * 某天逐小时明细（时长/次数/每应用），优先命中持久化快照，今日回退到实时缓存。
     * 修复：过往日不再现场重算（避免回到今天时小时数据被重置），直接读持久化。
     */
    @Synchronized
    fun getHourlyForDate(dateKey: String): HourlyDetail {
        ensureLoaded()
        if (dateKey == todayKey()) return getHourlyDetail()
        return hourly[dateKey]?.toDetail()
            ?: HourlyDetail(LongArray(24), IntArray(24), Array(24) { emptyMap() })
    }

    /** 某天的总启动次数。 */
    @Synchronized
    fun getDayCount(dateKey: String): Int = dayCounts[dateKey]?.values?.sum() ?: 0

    /** 某应用历史累计启动次数。 */
    @Synchronized
    fun getTotalCount(packageName: String): Int { ensureLoaded(); return totalCounts[packageName] ?: 0 }

    /** 今日某应用启动次数（详情面板用）。 */
    @Synchronized
    fun getTodayCountFor(packageName: String): Int {
        ensureLoaded()
        return dayCounts[todayKey()]?.get(packageName) ?: 0
    }

    private fun computeHourlyDetail(fromMs: Long, toMs: Long): HourlyDetail {
        ensureLoaded()
        // 关键修复：原实现先调用 hourlyFromEvents 填 ms，随后事件循环又 addToHourly(ms, ...)
        // 对同一 ms 二次累加，导致小时时长被双倍计入。改为 ms/cnt/brk 全部由事件循环
        // 单一来源填充，杜绝双计。
        val ms = LongArray(24)
        val cnt = IntArray(24)
        val brk = Array(24) { mutableMapOf<String, Pair<Long, Int>>() }
        if (hasUsagePermission()) {
            val events = try { usm.queryEvents(fromMs, toMs) } catch (e: Exception) { null }
            events?.let { evs ->
                val openMap = HashMap<String, Long>()
                val ev = UsageEvents.Event()
                while (evs.hasNextEvent()) {
                    evs.getNextEvent(ev)
                    when (ev.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> openMap[ev.packageName] = ev.timeStamp
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            // 关键修复：原 ?: return@let 会因单个未配对 BACKGROUND 中断整个事件循环
                            // 导致后续所有事件丢失；改为 continue 仅跳过当前事件。
                            val enter = openMap.remove(ev.packageName) ?: continue
                            addToHourly(ms, cnt, brk, enter, ev.timeStamp, ev.packageName)
                        }
                    }
                }
                // 仍在前台的应用：按 toMs 截断（与 sync() 行为一致，确保"当前使用中"也被计入）
                openMap.forEach { (pkg, enter) ->
                    if (enter < fromMs) return@forEach
                    addToHourly(ms, cnt, brk, enter, toMs, pkg)
                }
            }
        }
        return HourlyDetail(ms, cnt, brk.map { it.toMap() }.toTypedArray())
    }

    private fun todayStartMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 某自然月（由 monthStartMs 所在月决定）内各应用累计时长 Top n 的包名列表。 */
    @Synchronized
    fun getMonthTopApps(monthStartMs: Long, n: Int = 3): List<String> {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = monthStartMs }
        val ym = "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        val acc = LinkedHashMap<String, Long>()
        for ((k, m) in days) {
            if (k.startsWith(ym)) m.forEach { (pkg, ms) -> acc[pkg] = (acc[pkg] ?: 0L) + ms }
        }
        return acc.entries.sortedByDescending { it.value }.take(n).map { it.key }
    }

    /** 最近 [n] 天（含今天）每天总时长（ms），按日期升序。索引 0 为最远一天。 */
    @Synchronized
    fun getDaySeries(n: Int): LongArray = getDaySeries(n, System.currentTimeMillis())

    /** 最近 [n] 天（含 [anchorMs] 当天）每天总时长（ms），按日期升序。索引 0 为最远一天。 */
    @Synchronized
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

    // ───────── 自然周期对齐（统计页周/月/年视图） ─────────
    // 旧实现用 trailing window（getDaySeries(n)/getMonthlyUsageList(n) 从锚点往前 n 单位），
    // 导致"选某月却显示前30天尾部窗口、选某年却显示等高分12假柱"。
    // 以下函数对齐到所选自然周/月/年区间，使图表/列表与顶部总计卡一致。

    /** 自然周（anchor 所在周周一为起点）7 天每日总时长，升序 [周一..周日]。 */
    @Synchronized
    fun getDaySeriesInWeek(anchorMs: Long): LongArray {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = weekStartMs(anchorMs) }
        return LongArray(7) { i ->
            val total = days[dateKey(cal)]?.values?.sum() ?: 0L
            cal.add(Calendar.DAY_OF_MONTH, 1)
            total
        }
    }

    /** 自然月（anchor 所在月1号为起点）全部天数每日总时长，升序。长度 28–31。 */
    @Synchronized
    fun getDaySeriesInMonth(anchorMs: Long): LongArray {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = monthStartMs(anchorMs) }
        val len = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return LongArray(len) { i ->
            val total = days[dateKey(cal)]?.values?.sum() ?: 0L
            cal.add(Calendar.DAY_OF_MONTH, 1)
            total
        }
    }

    /** 自然年（anchor 所在年1月1号为起点）12 个真实月总时长，升序 [1月..12月]。 */
    @Synchronized
    fun getMonthSeriesInYear(anchorMs: Long): LongArray {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = yearStartMs(anchorMs) }
        val totals = HashMap<String, Long>(days.size)
        for ((k, m) in days) {
            val ym = k.substring(0, 7)
            totals[ym] = (totals[ym] ?: 0L) + m.values.sum()
        }
        return LongArray(12) { i ->
            val ym = "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            val v = totals[ym] ?: 0L
            cal.add(Calendar.MONTH, 1)
            v
        }
    }

    // ───────── 启动次数序列（与上面时长序列一一对应，供 metric=次数 图表） ─────────
    /** 最近 [n] 天（含 [anchorMs] 当天）每天总启动次数，升序。 */
    @Synchronized
    fun getDayCountSeries(n: Int, anchorMs: Long): IntArray {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMs }
        val series = IntArray(n)
        for (i in 0 until n) {
            series[n - 1 - i] = dayCounts[dateKey(cal)]?.values?.sum() ?: 0
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return series
    }

    /** 自然周 7 天每日总启动次数，升序 [周一..周日]。 */
    @Synchronized
    fun getDayCountSeriesInWeek(anchorMs: Long): IntArray {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = weekStartMs(anchorMs) }
        return IntArray(7) { i ->
            val v = dayCounts[dateKey(cal)]?.values?.sum() ?: 0
            cal.add(Calendar.DAY_OF_MONTH, 1)
            v
        }
    }

    /** 自然月全部天每日总启动次数，升序。 */
    @Synchronized
    fun getDayCountSeriesInMonth(anchorMs: Long): IntArray {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = monthStartMs(anchorMs) }
        val len = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return IntArray(len) { i ->
            val v = dayCounts[dateKey(cal)]?.values?.sum() ?: 0
            cal.add(Calendar.DAY_OF_MONTH, 1)
            v
        }
    }

    /** 自然年 12 个月每月总启动次数，升序 [1月..12月]。 */
    @Synchronized
    fun getMonthCountSeriesInYear(anchorMs: Long): IntArray {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = yearStartMs(anchorMs) }
        val acc = HashMap<String, Int>(days.size)
        for ((k, m) in dayCounts) {
            val ym = k.substring(0, 7)
            acc[ym] = (acc[ym] ?: 0) + m.values.sum()
        }
        return IntArray(12) { i ->
            val ym = "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            val v = acc[ym] ?: 0
            cal.add(Calendar.MONTH, 1)
            v
        }
    }

    /** 本周/本月/本年累计启动次数（供 metric 卡）。 */
    @Synchronized
    fun getWeekCount(anchorMs: Long): Int = aggregateCountFrom(weekStartMs(anchorMs))
    @Synchronized
    fun getMonthCount(anchorMs: Long): Int = aggregateCountFrom(monthStartMs(anchorMs))
    @Synchronized
    fun getYearCount(anchorMs: Long): Int = aggregateCountFrom(yearStartMs(anchorMs))

    private fun aggregateCountFrom(fromMs: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMs }
        val fromKey = dateKey(cal)
        var sum = 0
        dayCounts.entries.filter { it.key >= fromKey }.forEach { (_, m) -> m.values.forEach { sum += it } }
        return sum
    }

    /** 自然周区间 [周一起, 周日止] 的每日 (dateMs, ms) 列表，升序。 */
    @Synchronized
    fun getWeekDailyList(anchorMs: Long): List<Pair<Long, Long>> {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = weekStartMs(anchorMs) }
        return List(7) { i ->
            val ms = days[dateKey(cal)]?.values?.sum() ?: 0L
            val d = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d to ms
        }
    }

    /** 自然月全部天 (dateMs, ms) 列表，升序 [1号..末号]。 */
    @Synchronized
    fun getMonthDailyList(anchorMs: Long): List<Pair<Long, Long>> {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = monthStartMs(anchorMs) }
        val len = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return List(len) { i ->
            val ms = days[dateKey(cal)]?.values?.sum() ?: 0L
            val d = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d to ms
        }
    }

    /** 自然年 12 个月 (monthMs, ms) 列表，升序 [1月..12月]。 */
    @Synchronized
    fun getYearMonthlyList(anchorMs: Long): List<Pair<Long, Long>> {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = yearStartMs(anchorMs) }
        val totals = HashMap<String, Long>(days.size)
        for ((k, m) in days) {
            val ym = k.substring(0, 7)
            totals[ym] = (totals[ym] ?: 0L) + m.values.sum()
        }
        return List(12) { i ->
            val ym = "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            val ms = totals[ym] ?: 0L
            val d = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            d to ms
        }
    }

    /** 最近 [n] 天（含 [anchorMs] 当天）每天总启动次数列表，倒序，供统计页列表（日视图）count 模式。 */
    @Synchronized
    fun getDailyCountList(n: Int, anchorMs: Long = System.currentTimeMillis()): List<Pair<Long, Int>> {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMs }
        val asc = ArrayList<Pair<Long, Int>>(n)
        for (i in 0 until n) {
            asc.add(cal.timeInMillis to (dayCounts[dateKey(cal)]?.values?.sum() ?: 0))
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return asc.reversed()
    }

    /** 自然周每日 (dateMs, count) 列表，升序。 */
    @Synchronized
    fun getWeekDailyCountList(anchorMs: Long): List<Pair<Long, Int>> {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = weekStartMs(anchorMs) }
        return List(7) { i ->
            val c = dayCounts[dateKey(cal)]?.values?.sum() ?: 0
            val d = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d to c
        }
    }

    /** 自然月每日 (dateMs, count) 列表，升序。 */
    @Synchronized
    fun getMonthDailyCountList(anchorMs: Long): List<Pair<Long, Int>> {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = monthStartMs(anchorMs) }
        val len = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return List(len) { i ->
            val c = dayCounts[dateKey(cal)]?.values?.sum() ?: 0
            val d = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d to c
        }
    }

    /** 自然年每月 (monthMs, count) 列表，升序。 */
    @Synchronized
    fun getYearMonthlyCountList(anchorMs: Long): List<Pair<Long, Int>> {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = yearStartMs(anchorMs) }
        val acc = HashMap<String, Int>(days.size)
        for ((k, m) in dayCounts) {
            val ym = k.substring(0, 7)
            acc[ym] = (acc[ym] ?: 0) + m.values.sum()
        }
        return List(12) { i ->
            val ym = "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            val c = acc[ym] ?: 0
            val d = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            d to c
        }
    }

    /** 区间 [fromKey..toKey]（含两端）所有包总时长。 */
    @Synchronized
    fun getRangeTotal(fromKey: String, toKey: String): Long {
        ensureLoaded()
        var sum = 0L
        days.entries.filter { it.key in fromKey..toKey }.forEach { (_, m) ->
            m.values.forEach { sum += it }
        }
        return sum
    }

    /** 当前累计所有历史总时长。 */
    @Synchronized
    fun getAllTimeTotal(): Long { ensureLoaded(); return totals.values.sum() }

    /**
     * 最近 [n] 天（含 [anchorMs] 当天）某包的每日总时长（ms），按日期升序。
     * 索引 0 为最远一天。用于 GamePage「动态」Tab 的按日游玩时间线（柱状图）。
     */
    @Synchronized
    fun getPackageDaySeries(packageName: String, n: Int, anchorMs: Long = System.currentTimeMillis()): LongArray {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMs }
        val series = LongArray(n)
        for (i in 0 until n) {
            val key = dateKey(cal)
            series[n - 1 - i] = days[key]?.get(packageName) ?: 0L
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return series
    }

    /**
     * 最近 [n] 天（含 [anchorMs] 当天）每天总时长列表，**按日期倒序**（索引 0 = 锚点当天，最新在最上）。
     * 用于统计页「每天游玩时长列表」（用户需求：最新日期在最上面）。返回 (dateMs, totalMs) 列表。
     */
    @Synchronized
    fun getDailyUsageList(n: Int, anchorMs: Long = System.currentTimeMillis()): List<Pair<Long, Long>> {
        ensureLoaded()
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMs }
        val asc = ArrayList<Pair<Long, Long>>(n)
        for (i in 0 until n) {
            val total = days[dateKey(cal)]?.values?.sum() ?: 0L
            asc.add(cal.timeInMillis to total)
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return asc.reversed()
    }

    /**
     * 最近 [n] 个月（含 [anchorMs] 所在月）每月总时长列表，**按月份倒序**（索引 0 = 锚点当月，最新在最上）。
     * 用于统计页（年视图）的「每月游玩时长列表」。返回 (monthStartMs, totalMs)。
     */
    @Synchronized
    fun getMonthlyUsageList(n: Int, anchorMs: Long = System.currentTimeMillis()): List<Pair<Long, Long>> {
        ensureLoaded()
        // 预聚合：yyyy-MM -> 总时长（避免逐月全表扫描）
        val monthTotals = HashMap<String, Long>(days.size)
        for ((k, m) in days) {
            val ym = k.substring(0, 7)
            monthTotals[ym] = (monthTotals[ym] ?: 0L) + m.values.sum()
        }
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMs }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val asc = ArrayList<Pair<Long, Long>>(n)
        for (i in 0 until n) {
            val ym = "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            asc.add(cal.timeInMillis to (monthTotals[ym] ?: 0L))
            cal.add(Calendar.MONTH, -1)
        }
        return asc.reversed()
    }

    // （已删除死代码 hourlyFromEvents：computeHourlyDetail 重构后不再被任何路径调用）

    /**
     * 确定性权限检测：直接查 AppOps 的 GET_USAGE_STATS 授权态。
     * 不再以 queryUsageStats 结果集是否为空做间接推断——
     * 旧实现（now-60s 窗口）在最近 60 秒恰好无任何使用记录时会误判为「无权限」。
     */
    private fun hasUsagePermission(): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        if (mode == android.app.AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            mode == android.app.AppOpsManager.MODE_ALLOWED
        }
    } catch (e: Exception) { false }

    private fun reportForDay(dateKey: String): List<UsageEntry> {
        val m = days[dateKey] ?: return emptyList()
        val c = dayCounts[dateKey]
        return m.map { (pkg, ms) -> UsageEntry(pkg, ms, c?.get(pkg) ?: 0) }
            .sortedByDescending { it.ms }
    }

    private fun isToday(ms: Long): Boolean = dateKey(Calendar.getInstance().apply { timeInMillis = ms }) == todayKey()

    // ──────────────────────────────────────────────
    // 时间工具
    // ──────────────────────────────────────────────
    /**
     * 冷热旋转：将超出热层窗口（[StorageConfig.HOT_WINDOW_DAYS] 天）的按天明细迁移到
     * 冷层按月归档，成功后从热层删除（days / dayCounts / hourly）。
     * totals / totalCounts 为全期累计，不受旋转影响。
     */
    private fun pruneOldDays() {
        val cutoff = dateKeyOf(System.currentTimeMillis() - StorageConfig.HOT_WINDOW_DAYS * 24L * 60 * 60 * 1000)
        ArchiveRotation.rotateExpired(this, archiveDir(), cutoff)
        // hourly 为热层专有（冷层格式不含小时分布），超出窗口者直接清除
        hourly.keys.removeAll { it < cutoff }
    }

    private fun dateKey(cal: Calendar): String =
        "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

    private fun todayKey(): String = dateKey(Calendar.getInstance())

    private fun dateKeyOf(ms: Long): String =
        dateKey(Calendar.getInstance().apply { timeInMillis = ms })

    /** 冷层根目录：filesDir/archives/usage_stats。 */
    private fun archiveDir(): java.io.File = java.io.File(context.filesDir, "archives/usage_stats")

    // ── HotStore 实现（供分层查询 / 旋转复用）──
    override fun hasHotDate(dateKey: String): Boolean = days.containsKey(dateKey)

    override fun getHotDay(dateKey: String): Map<String, DayEntry> {
        val msMap = days[dateKey] ?: return emptyMap()
        val cntMap = dayCounts[dateKey] ?: emptyMap()
        val pkgs = msMap.keys + cntMap.keys
        return pkgs.associateWith { pkg -> DayEntry(pkg, msMap[pkg] ?: 0L, cntMap[pkg] ?: 0) }
    }

    override fun hotDateKeys(): Set<String> = days.keys

    override fun removeHotDate(dateKey: String) {
        days.remove(dateKey)
        dayCounts.remove(dateKey)
        hourly.remove(dateKey)
    }

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

    data class UsageEntry(val packageName: String, val ms: Long, val count: Int = 0)

    companion object {
        private const val TAG = "UsageStatsRepo"
        private const val FILE_NAME = "usage_stats.json"
        private const val KEY_LAST_SYNC = "lastSync"
        private const val KEY_DAYS = "days"
        private const val KEY_TOTALS = "totals"
        private const val KEY_DAY_COUNTS = "dayCounts"
        private const val KEY_TOTAL_COUNTS = "totalCounts"
        private const val KEY_HOURLY = "hourly"
        /** 首装无 lastSync 时，最多回看 30 天，避免一次拉取过久。 */
        private const val SYNC_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        /** 最小有效使用时长（ms）：< 1s 的事件跳过（闪退/快速切换）。 */
        private const val MIN_VALID_DURATION_MS = 1000L
        /** 单次最大使用时长（ms）：> 24h 截断，防止系统 bug 产生的错误时间戳。 */
        private const val MAX_SINGLE_DURATION_MS = 24L * 60 * 60 * 1000
    }
}

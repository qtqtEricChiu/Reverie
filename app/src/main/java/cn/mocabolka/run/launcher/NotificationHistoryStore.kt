package cn.mocabolka.run.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * 单条通知历史记录（持久化介质，不含 PendingIntent —— Binder 对象不可序列化）。
 * [key] 为 `pkg|id|tag`，用于「同 key 同内容当天去重」（通知刷新虚高防护）。
 */
data class NotificationRecord(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val category: String = ""
)

/** 发件人聚合结果：某社交应用内某发件人（联系人/群）的通知条数。 */
data class SenderAgg(val packageName: String, val sender: String, val count: Int)

/**
 * 通知历史持久化仓库（与内存易失的 [cn.mocabolka.run.compat.RecentNotificationStore] 职责分离）。
 *
 * 架构（对齐既有分层持久化）：
 * - 热层：近 [StorageConfig.HOT_WINDOW_DAYS] 天按天明细常驻内存，查询同步零 IO；
 *   落盘 `notification_history.json`，原子写范式照搬 [UsageStatsRepository.save]。
 * - 冷层：超期按月归档 `archives/notification_history/YYYY/MM.json`，
 *   事务语义照搬 [ArchiveRotation]（先写归档成功再删热层，失败保留热层重试）；
 *   读取经手写 [LruCache] 缓存最近 [StorageConfig.ARCHIVE_CACHE_SIZE] 个月。
 *
 * 去重策略：同 key（pkg|id|tag）且 title/text 均未变化 → 视为同一通知的刷新，跳过；
 * 同 key 但内容变化（社交应用同一会话来新消息会复用通知 id 更新内容）→ 计为新记录，
 * 避免「同 key 当天只计一次」把微信整天消息压成 1 条的欠计问题。
 *
 * 写盘节流：追加只改内存并标脏，由后台协程延迟合并落盘（通知可能高频到达，
 * 逐条全量写 JSON 不可接受）；进程被杀最多丢失 [FLUSH_DELAY_MS] 内的增量。
 */
object NotificationHistoryStore {

    private const val TAG = "NotifHistoryStore"
    private const val FILE_NAME = "notification_history.json"

    /** 单日记录条数上限：防异常应用刷屏导致文件无限膨胀（超限丢弃最旧）。 */
    private const val MAX_RECORDS_PER_DAY = 1000

    /** 追加后延迟落盘窗口（ms）：窗口内的连续追加合并为一次写。 */
    private const val FLUSH_DELAY_MS = 5_000L

    @Volatile
    private var appContext: Context? = null

    /** 热层：dateKey(yyyy-MM-dd) -> 当天记录（追加序 ≈ 时间序）。 */
    private val days = LinkedHashMap<String, MutableList<NotificationRecord>>()

    /** 按天去重索引：dateKey -> (key -> 最近一次 title|text 指纹)。懒建。 */
    private val dedup = HashMap<String, HashMap<String, String>>()

    /** 冷层月归档缓存："YYYY/MM" -> (dateKey -> 记录列表)。 */
    private val archiveCache =
        LruCache<String, Map<String, List<NotificationRecord>>>(StorageConfig.ARCHIVE_CACHE_SIZE)

    private var loaded = false

    @Volatile
    private var flushJob: Job? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 数据版本号：每次有新记录写入热层时自增，UI 可订阅以刷新查询。 */
    private val _changeTick = MutableStateFlow(0L)
    val changeTick: StateFlow<Long> = _changeTick.asStateFlow()

    /** 必须在使用前注入 applicationContext（服务或 VM 初始化时调用，幂等）。 */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    // ──────────────────────────────────────────────
    // 采集入口
    // ──────────────────────────────────────────────
    /**
     * 追加一条通知记录（由 NotificationBadgeService.onNotificationPosted 调用）。
     * 同 key 同内容当天去重；title 与 text 均为空的记录（无信息量）直接忽略。
     */
    @Synchronized
    fun append(key: String, packageName: String, title: String, text: String, timestamp: Long, category: String = "") {
        if (appContext == null) return
        if (title.isBlank() && text.isBlank()) return
        ensureLoaded()
        val dk = dateKeyOf(timestamp)
        val fingerprint = "$title\u0001$text"
        val dayDedup = dedup.getOrPut(dk) {
            HashMap<String, String>().apply {
                days[dk]?.forEach { r -> put(r.key, "${r.title}\u0001${r.text}") }
            }
        }
        if (dayDedup[key] == fingerprint) return // 同一通知的无内容刷新（标题+正文均相同）
        dayDedup[key] = fingerprint
        val list = days.getOrPut(dk) { mutableListOf() }
        // 修复：同 key 通知（系统应用常以相同 id/tag 重复 post，如 com.coloros.shortcuts|3|）
        // 必须按 key 去重——找到既存同 key 记录就地更新，而非追加新记录，否则 days[dk] 会出现
        // 重复 key 的两条 NotificationRecord。下游 NotificationHistoryPage 的 LazyColumn 用 rec.key 做
        // item key，重复 key 会抛 IllegalArgumentException 致应用崩溃回桌面（周/月/年大范围视图尤其易触发）。
        val existIdx = list.indexOfFirst { it.key == key }
        if (existIdx >= 0) {
            list[existIdx] = NotificationRecord(key, packageName, title, text, timestamp, category)
        } else {
            list.add(NotificationRecord(key, packageName, title, text, timestamp, category))
            if (list.size > MAX_RECORDS_PER_DAY) list.removeAt(0)
        }
        _changeTick.value = _changeTick.value + 1
        scheduleFlush()
    }

    // ──────────────────────────────────────────────
    // 查询 API
    // ──────────────────────────────────────────────
    /** 某天各应用通知条数（热层同步零 IO；冷层日期请走 [listForDay]）。 */
    @Synchronized
    fun countByPackageForDay(dateKey: String): Map<String, Int> {
        ensureLoaded()
        val list = days[dateKey] ?: return emptyMap()
        val acc = LinkedHashMap<String, Int>()
        list.forEach { r -> acc[r.packageName] = (acc[r.packageName] ?: 0) + 1 }
        return acc
    }

    /** 日期范围 [start, end]（含两端）各应用通知条数，热 + 冷合并。 */
    suspend fun countByPackageForRange(start: String, end: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            val acc = LinkedHashMap<String, Int>()
            forEachDayInRange(start, end) { records ->
                records.forEach { r -> acc[r.packageName] = (acc[r.packageName] ?: 0) + 1 }
            }
            acc
        }

    /** 某天全部通知记录（时间倒序，最新在前）。热层同步命中，冷层读归档。 */
    suspend fun listForDay(dateKey: String): List<NotificationRecord> {
        synchronized(this) {
            ensureLoaded()
            days[dateKey]?.let { return it.sortedByDescending { r -> r.timestamp } }
        }
        return withContext(Dispatchers.IO) {
            archiveDay(dateKey).sortedByDescending { it.timestamp }
        }
    }

    /**
     * 某天社交应用的发件人 Top 聚合（热层同步零 IO）。
     * [socialPkgs] 由上层（HomeViewModel）组装：内置白名单 ∪ categoryText=="社交"。
     */
    @Synchronized
    fun topSendersForDay(dateKey: String, limit: Int, socialPkgs: Set<String>): List<SenderAgg> {
        ensureLoaded()
        return aggregateSenders(days[dateKey].orEmpty(), socialPkgs, limit)
    }

    /** 日期范围内社交应用发件人 Top 聚合，热 + 冷合并（IO 线程）。 */
    suspend fun topSendersForRange(
        start: String,
        end: String,
        limit: Int,
        socialPkgs: Set<String>
    ): List<SenderAgg> = withContext(Dispatchers.IO) {
        val all = mutableListOf<NotificationRecord>()
        forEachDayInRange(start, end) { records -> all.addAll(records) }
        aggregateSenders(all, socialPkgs, limit)
    }

    /** 当天 dateKey（yyyy-MM-dd，零填充，字典序即时间序）。 */
    fun todayKey(): String = dateKeyOf(System.currentTimeMillis())

    fun dateKeyOf(ms: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // ──────────────────────────────────────────────
    // 内部：聚合
    // ──────────────────────────────────────────────
    private fun aggregateSenders(
        records: List<NotificationRecord>,
        socialPkgs: Set<String>,
        limit: Int
    ): List<SenderAgg> {
        if (records.isEmpty() || socialPkgs.isEmpty()) return emptyList()
        // (pkg, sender) -> count
        val acc = LinkedHashMap<Pair<String, String>, Int>()
        records.forEach { r ->
            if (r.packageName !in socialPkgs) return@forEach
            val sender = SocialAppClassifier.senderOf(r) ?: return@forEach
            val k = r.packageName to sender
            acc[k] = (acc[k] ?: 0) + 1
        }
        return acc.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (k, c) -> SenderAgg(k.first, k.second, c) }
    }

    /** 遍历范围内每一天的记录（热层直读内存，冷层按涉及月份读归档）。仅 IO 线程调用。 */
    private fun forEachDayInRange(start: String, end: String, block: (List<NotificationRecord>) -> Unit) {
        val hotSnapshot: Map<String, List<NotificationRecord>>
        synchronized(this) {
            ensureLoaded()
            hotSnapshot = days.filterKeys { it in start..end }.mapValues { it.value.toList() }
        }
        hotSnapshot.values.forEach(block)
        // 冷层：范围涉及的月份中，仅取热层没有的日期（热层优先，避免重复计数）
        for ((y, m) in monthRange(start, end)) {
            val monthDays = archiveMonth(y, m) ?: continue
            monthDays.forEach { (dk, records) ->
                if (dk in start..end && !hotSnapshot.containsKey(dk)) block(records)
            }
        }
    }

    private fun forEachDayInRangeKeyed(start: String, end: String, block: (String, List<NotificationRecord>) -> Unit) {
        val hotSnapshot: Map<String, List<NotificationRecord>>
        synchronized(this) {
            ensureLoaded()
            hotSnapshot = days.filterKeys { it in start..end }.mapValues { it.value.toList() }
        }
        hotSnapshot.forEach { (dk, records) -> block(dk, records) }
        for ((y, m) in monthRange(start, end)) {
            val monthDays = archiveMonth(y, m) ?: continue
            monthDays.forEach { (dk, records) ->
                if (dk in start..end && !hotSnapshot.containsKey(dk)) block(dk, records)
            }
        }
    }

    /** 范围内按日的通知总数序列（日期升序），供通知历史趋势图使用。 */
    suspend fun dailyCountsForRange(start: String, end: String): List<Pair<String, Int>> =
        withContext(Dispatchers.IO) {
            val acc = mutableListOf<Pair<String, Int>>()
            forEachDayInRangeKeyed(start, end) { dk, records -> acc.add(dk to records.size) }
            acc.sortedBy { it.first }
        }

    /** 范围内某应用的所有通知明细（按时间倒序），供通知历史钻取子页使用。 */
    suspend fun listForRangeByPackage(start: String, end: String, pkg: String): List<NotificationRecord> =
        withContext(Dispatchers.IO) {
            val acc = mutableListOf<NotificationRecord>()
            forEachDayInRange(start, end) { records -> acc.addAll(records.filter { it.packageName == pkg }) }
            acc.sortedByDescending { it.timestamp }
        }

    /** 范围内全部通知明细（按时间升序），供通知历史钻取子页按「应用 / 发件人」筛选。 */
    suspend fun listForRange(start: String, end: String): List<NotificationRecord> =
        withContext(Dispatchers.IO) {
            val acc = mutableListOf<NotificationRecord>()
            forEachDayInRange(start, end) { records -> acc.addAll(records) }
            acc.sortedBy { it.timestamp }
        }

    // ──────────────────────────────────────────────
    // 内部：持久化（热层）
    // ──────────────────────────────────────────────
    private fun hotFile(): File? = appContext?.let { File(it.filesDir, FILE_NAME) }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val file = hotFile() ?: return
        runCatching {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val jDays = root.optJSONObject("days") ?: JSONObject()
            jDays.keys().forEach { dk ->
                val arr = jDays.optJSONArray(dk) ?: JSONArray()
                val list = ArrayList<NotificationRecord>(arr.length())
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(recordFromJson(it)) }
                }
                if (list.isNotEmpty()) days[dk] = list
            }
        }.onFailure { e -> Log.w(TAG, "读取通知历史失败，已忽略", e) }
    }

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = ioScope.launch {
            delay(FLUSH_DELAY_MS)
            persist()
        }
    }

    /** 落盘：先旋转超期日期到冷层，再原子写热层文件（照搬 UsageStatsRepository.save 范式）。 */
    @Synchronized
    private fun persist() {
        val ctx = appContext ?: return
        rotateExpired(ctx)
        runCatching {
            val jDays = JSONObject()
            days.forEach { (dk, list) ->
                val arr = JSONArray()
                list.forEach { r -> arr.put(recordToJson(r)) }
                jDays.put(dk, arr)
            }
            val root = JSONObject().apply {
                put("version", StorageConfig.SCHEMA_VERSION)
                put("days", jDays)
            }
            // 原子写入：先写临时文件再 rename，避免写半截文件损坏
            val file = File(ctx.filesDir, FILE_NAME)
            val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) file.writeText(root.toString())
        }.onFailure { e -> Log.w(TAG, "写入通知历史失败", e) }
    }

    // ──────────────────────────────────────────────
    // 内部：冷层归档
    // ──────────────────────────────────────────────
    private fun archiveRoot(ctx: Context): File = File(ctx.filesDir, "archives/notification_history")

    /**
     * 冷热旋转：超过 [StorageConfig.HOT_WINDOW_DAYS] 的日期迁入按月归档。
     * 事务语义对齐 [ArchiveRotation]：先写归档成功，再删热层该天；失败保留热层重试（幂等覆盖）。
     */
    private fun rotateExpired(ctx: Context) {
        val cutoff = dateKeyOf(
            System.currentTimeMillis() - StorageConfig.HOT_WINDOW_DAYS * 24L * 60 * 60 * 1000
        )
        val expired = days.keys.filter { it < cutoff }.sorted()
        for (dk in expired) {
            if (rotateOneDay(ctx, dk)) {
                days.remove(dk)
                dedup.remove(dk)
            }
        }
    }

    private fun rotateOneDay(ctx: Context, dateKey: String): Boolean {
        val records = days[dateKey].orEmpty()
        if (records.isEmpty()) return true
        val year = dateKey.substring(0, 4).toInt()
        val month = dateKey.substring(5, 7).toInt()
        val mFile = monthFile(ctx, year, month)
        val existing = readMonthFile(mFile) ?: emptyMap()
        val merged = LinkedHashMap(existing)
        merged[dateKey] = records.toList() // 同 key 覆盖，幂等
        return runCatching {
            writeMonthFile(mFile, year, month, merged)
            archiveCache.put("$year/$month", merged)
        }.isSuccess
    }

    private fun monthFile(ctx: Context, year: Int, month: Int): File =
        File(File(archiveRoot(ctx), year.toString()), "%02d.json".format(month))

    /** 读某月归档（LRU 缓存优先；不存在/损坏返回 null）。 */
    private fun archiveMonth(year: Int, month: Int): Map<String, List<NotificationRecord>>? {
        val key = "$year/$month"
        archiveCache.get(key)?.let { return it }
        val ctx = appContext ?: return null
        val parsed = readMonthFile(monthFile(ctx, year, month)) ?: return null
        archiveCache.put(key, parsed)
        return parsed
    }

    private fun archiveDay(dateKey: String): List<NotificationRecord> {
        val year = dateKey.substring(0, 4).toIntOrNull() ?: return emptyList()
        val month = dateKey.substring(5, 7).toIntOrNull() ?: return emptyList()
        return archiveMonth(year, month)?.get(dateKey).orEmpty()
    }

    private fun readMonthFile(file: File): Map<String, List<NotificationRecord>>? {
        if (!file.exists()) return null
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val out = LinkedHashMap<String, List<NotificationRecord>>()
        val jDays = root.optJSONObject("days") ?: return out
        jDays.keys().forEach { dk ->
            val arr = jDays.optJSONArray(dk) ?: JSONArray()
            val list = ArrayList<NotificationRecord>(arr.length())
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { list.add(recordFromJson(it)) }
            }
            out[dk] = list
        }
        return out
    }

    private fun writeMonthFile(
        file: File,
        year: Int,
        month: Int,
        daysMap: Map<String, List<NotificationRecord>>
    ) {
        val jDays = JSONObject()
        daysMap.forEach { (dk, list) ->
            val arr = JSONArray()
            list.forEach { r -> arr.put(recordToJson(r)) }
            jDays.put(dk, arr)
        }
        val root = JSONObject().apply {
            put("version", 1)
            put("year", year)
            put("month", month)
            put("record_count", daysMap.values.sumOf { it.size })
            put("days", jDays)
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) file.writeText(root.toString())
    }

    // ──────────────────────────────────────────────
    // 内部：序列化（紧凑 key 省体积）
    // ──────────────────────────────────────────────
    private fun recordToJson(r: NotificationRecord): JSONObject = JSONObject().apply {
        put("k", r.key)
        put("p", r.packageName)
        put("t", r.title)
        put("x", r.text)
        put("s", r.timestamp)
        put("c", r.category)
    }

    private fun recordFromJson(o: JSONObject): NotificationRecord = NotificationRecord(
        key = o.optString("k"),
        packageName = o.optString("p"),
        title = o.optString("t"),
        text = o.optString("x"),
        timestamp = o.optLong("s"),
        category = o.optString("c")
    )

    /** 枚举 [start, end] 两个 dateKey 之间涉及的所有 (年, 月)。 */
    private fun monthRange(start: String, end: String): List<Pair<Int, Int>> {
        val sy = start.substring(0, 4).toIntOrNull() ?: return emptyList()
        val sm = start.substring(5, 7).toIntOrNull() ?: return emptyList()
        val ey = end.substring(0, 4).toIntOrNull() ?: return emptyList()
        val em = end.substring(5, 7).toIntOrNull() ?: return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        var y = sy
        var m = sm
        while (y < ey || (y == ey && m <= em)) {
            out.add(y to m)
            m++
            if (m > 12) { m = 1; y++ }
        }
        return out
    }
}

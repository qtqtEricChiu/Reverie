package cn.mocabolka.run.launcher

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 单应用在某天的聚合（与 [UsageStatsRepository.UsageEntry] 同构，用于分层按天/按范围查询返回）。 */
data class DayEntry(val packageName: String, val ms: Long, val count: Int = 0)

/** 数据所在层。 */
enum class Tier { HOT, COLD, NONE }

/** 某年某月的归档概要（供历史回溯 UI 展示）。 */
data class MonthSummary(val year: Int, val month: Int, val recordCount: Int)

/** 冷层某月归档的内存表示。 */
data class ArchiveMonth(
    val year: Int,
    val month: Int,
    val days: LinkedHashMap<String, Map<String, DayEntry>>,
    val recordCount: Int
)

/**
 * 分层查询协调器：对上层屏蔽热层 / 冷层差异。
 * - 90 天内 → 直接读热层（零 IO，纯内存）。
 * - 90 天前 → 按需读取对应年月归档文件，并 LRU 缓存最近 [StorageConfig.ARCHIVE_CACHE_SIZE] 个月。
 * - 范围查询自动合并热层（内存）与冷层（涉及月份，缓存优先）。
 *
 * 设计为通用形态：本类只依赖 [HotStore] 与 [TieredSerializer]，与具体业务域无关；
 * 后续通知数据接入时，只需提供通知版的 [TieredSerializer] 即可复用同一协调器。
 */
class TieredQueryCoordinator(
    private val hot: HotStore,
    private val archiveDir: File, // .../archives/usage_stats（或 .../notifications）
    private val serializer: TieredSerializer
) {
    private val archiveCache = LruCache<String, ArchiveMonth>(StorageConfig.ARCHIVE_CACHE_SIZE)

    /** 查询单日（dateKey）各应用时长；90 天内走热层零 IO，否则读冷层归档。 */
    suspend fun loadForDate(dateKey: String): Map<String, DayEntry> = withContext(Dispatchers.IO) {
        if (hot.hasHotDate(dateKey)) return@withContext hot.getHotDay(dateKey)
        val (y, m) = splitYm(dateKey)
        getArchiveMonth(y, m)?.days?.get(dateKey) ?: emptyMap()
    }

    /** 查询日期范围 [startDate, endDate] 内各应用聚合时长（热 + 冷合并）。 */
    suspend fun loadForRange(startDate: String, endDate: String): Map<String, DayEntry> = withContext(Dispatchers.IO) {
        val acc = LinkedHashMap<String, DayEntry>()
        hot.hotDateKeys().forEach { dk ->
            if (dk >= startDate && dk <= endDate) {
                hot.getHotDay(dk).forEach { (pkg, e) -> acc.merge(pkg, e) }
            }
        }
        val (sy, sm) = splitYm(startDate)
        val (ey, em) = splitYm(endDate)
        for ((y, m) in monthRange(sy, sm, ey, em)) {
            val am = getArchiveMonth(y, m) ?: continue
            am.days.forEach { (dk, dayMap) ->
                if (dk >= startDate && dk <= endDate) dayMap.forEach { (pkg, e) -> acc.merge(pkg, e) }
            }
        }
        acc
    }

    /** 判断某日期在热层 / 冷层 / 不存在。 */
    fun tierOf(dateKey: String): Tier {
        if (hot.hasHotDate(dateKey)) return Tier.HOT
        val (y, m) = splitYm(dateKey)
        val idx = serializer.readYearIndex(File(archiveDir, y.toString()))
        return if (idx?.containsKey(m) == true) Tier.COLD else Tier.NONE
    }

    /** 列出归档中存在的所有年份（升序）。 */
    fun listArchiveYears(): List<Int> {
        if (!archiveDir.exists()) return emptyList()
        return archiveDir.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d{4}")) }
            ?.map { it.name.toInt() }?.sorted() ?: emptyList()
    }

    /** 列出某年所有有归档的月份概要（按月份升序）。 */
    fun listArchiveMonths(year: Int): List<MonthSummary> {
        val idx = serializer.readYearIndex(File(archiveDir, year.toString())) ?: return emptyList()
        return idx.map { (m, c) -> MonthSummary(year, m, c.toInt()) }.sortedBy { it.month }
    }

    /** 直接读取某年某月归档（含逐日明细），供历史回溯加载。 */
    suspend fun loadArchiveMonth(year: Int, month: Int): ArchiveMonth? = withContext(Dispatchers.IO) {
        getArchiveMonth(year, month)
    }

    // ── 内部 ──
    private fun getArchiveMonth(year: Int, month: Int): ArchiveMonth? {
        val key = "$year/$month"
        archiveCache.get(key)?.let { return it }
        val f = File(File(archiveDir, year.toString()), serializer.monthFileName(month))
        val am = serializer.readMonth(f, year, month) ?: return null
        archiveCache.put(key, am)
        return am
    }

    private fun LinkedHashMap<String, DayEntry>.merge(pkg: String, e: DayEntry) {
        val ex = this[pkg]
        this[pkg] = if (ex == null) e else DayEntry(pkg, ex.ms + e.ms, ex.count + e.count)
    }

    private fun splitYm(dateKey: String): Pair<Int, Int> =
        dateKey.substring(0, 4).toInt() to dateKey.substring(5, 7).toInt()

    /** 枚举 [sy/sm] 到 [ey/em]（含端点）之间所有 (年, 月) 组合。dateKey 字典序即时间序，故仅整数递推。 */
    private fun monthRange(sy: Int, sm: Int, ey: Int, em: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var y = sy
        var m = sm
        while (y < ey || (y == ey && m <= em)) {
            out.add(y to m)
            m++
            if (m > 12) {
                m = 1
                y++
            }
        }
        return out
    }
}

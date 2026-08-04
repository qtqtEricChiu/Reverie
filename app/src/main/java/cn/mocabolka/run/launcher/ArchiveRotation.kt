package cn.mocabolka.run.launcher

import java.io.File

/**
 * 冷热旋转：将热层中超过 [StorageConfig.HOT_WINDOW_DAYS] 天的按天数据迁移到对应的
 * 年月归档文件（冷层），并维护年度索引。
 *
 * - 每天最多迁移一个 dateKey，轻量；sync 保存时触发即可。
 * - 单日迁移为独立事务：先写归档成功，再删热层该天；归档写失败则保留热层该天，
 *   下次 save 重试（幂等：重读已写归档后按相同 key 覆盖，不产生重复）。
 * - 索引更新在归档写入成功后进行；若索引写失败则回滚（保留热层该天重试）。
 */
object ArchiveRotation {

    fun rotateExpired(hot: HotStore, archiveDir: File, cutoffDateKey: String) {
        val candidates = hot.hotDateKeys().filter { it < cutoffDateKey }.sorted()
        for (dateKey in candidates) {
            if (rotateOneDay(hot, archiveDir, dateKey)) hot.removeHotDate(dateKey)
        }
    }

    private fun rotateOneDay(hot: HotStore, archiveDir: File, dateKey: String): Boolean {
        val dayMap = hot.getHotDay(dateKey)
        if (dayMap.isEmpty()) return true // 无明细，直接判定成功（调用方会清除该热层日期）
        val (year, month) = splitYm(dateKey)
        val yDir = File(archiveDir, year.toString())
        val mFile = File(yDir, TieredSerializer.monthFileName(month))
        val existing = TieredSerializer.readMonth(mFile, year, month)
        val newDays = LinkedHashMap<String, Map<String, DayEntry>>().apply {
            existing?.days?.let { putAll(it) }
            put(dateKey, dayMap)
        }
        val monthObj = ArchiveMonth(year, month, newDays, newDays.values.sumOf { it.size })
        // 1) 写归档（原子）
        runCatching { TieredSerializer.writeMonth(mFile, monthObj) }.onFailure { return false }
        // 2) 更新年索引（失败回滚：保留热层该天，下次重试）
        val idx = (TieredSerializer.readYearIndex(yDir) ?: emptyMap()).toMutableMap()
        idx[month] = monthObj.recordCount.toLong()
        runCatching { TieredSerializer.writeYearIndex(yDir, year, idx) }.onFailure { return false }
        return true
    }

    private fun splitYm(dateKey: String): Pair<Int, Int> =
        dateKey.substring(0, 4).toInt() to dateKey.substring(5, 7).toInt()
}

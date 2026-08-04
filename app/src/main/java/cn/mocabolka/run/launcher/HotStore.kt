package cn.mocabolka.run.launcher

/**
 * 热层访问接口：解耦 [TieredQueryCoordinator] / [ArchiveRotation] 与具体持久化实现。
 * [UsageStatsRepository] 实现本接口后即可被分层查询与旋转逻辑复用；
 * 未来通知数据接入时，只需让通知 Store 同样实现本接口即可复用同一套协调/旋转调用。
 */
interface HotStore {
    /** 某日期是否在热层（90 天内）存在明细。 */
    fun hasHotDate(dateKey: String): Boolean

    /** 取某天各应用的聚合（时长 ms / 启动次数）。 */
    fun getHotDay(dateKey: String): Map<String, DayEntry>

    /** 热层全部日期 key（字典序即时间序）。 */
    fun hotDateKeys(): Set<String>

    /** 从热层删除某天（days / dayCounts / hourly 一并清除）。 */
    fun removeHotDate(dateKey: String)
}

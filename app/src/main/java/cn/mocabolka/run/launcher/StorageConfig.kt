package cn.mocabolka.run.launcher

/**
 * 分层持久化（热层 90 天 + 冷层按月归档）的全局常量。
 * 纯常量对象，不依赖任何运行时状态，便于各模块统一引用。
 */
object StorageConfig {
    /** 热层保留天数：90 天内的日/周/月数据常驻内存，零 IO 查询。 */
    const val HOT_WINDOW_DAYS = 90

    /** 冷层归档文件 LRU 缓存条数（最近加载的月份文件数）。 */
    const val ARCHIVE_CACHE_SIZE = 3

    /** 单个归档文件体积上限（MB）：超过仅告警，不阻断读写。 */
    const val MAX_ARCHIVE_MB_PER_FILE = 5

    /** 持久化 schema 版本：2 = 分层架构（热层 90 天 + 冷层按月归档）。 */
    const val SCHEMA_VERSION = 2
}

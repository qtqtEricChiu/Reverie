package cn.mocabolka.run.launcher

import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.ImageBitmap

data class AppModel(
    val packageName: String,
    val className: String,
    val label: String,
    val icon: ImageBitmap,
    val isFavorite: Boolean = false,
    /** 是否为游戏（FLAG_IS_GAME 或 category 标记为游戏）。 */
    val isGame: Boolean = false,
    /** ApplicationInfo.category，用于区分娱乐类（视频/音频等）。 */
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    /**
     * 解析后的分类标签（override 优先，否则取 categoryLabel(category)）。
     * 全工程统一使用此字段做 Tab / 筛选 / 计数展示，使「AI 分类映射」能覆盖系统原始分类，
     * 从而减少落入「其他」的应用。
     */
    val categoryText: String = categoryLabel(category),
    /** 是否为系统应用（FLAG_SYSTEM 且未被普通启动列表覆盖）。用于标记与导出。 */
    val isSystem: Boolean = false,
    /** 是否已安装。大屏模式会为白名单包名生成未安装的占位项。 */
    val installed: Boolean = true,
    /** 首次安装时间（ms），用于「安装时间」排序（P2-3）。 */
    val firstInstallTime: Long = 0L,
    /** 版本名（如 "1.4.2"），用于详情面板展示。 */
    val versionName: String = "",
    /** 上次启动/使用时间（ms），来自 UsageStats；0 表示未知。用于详情面板"上次游玩"。 */
    val lastUsedTime: Long = 0L,
    /** 今日前台使用时长（ms），来自 UsageStats 日报；0 表示无记录（R11-3）。 */
    val todayUsage: Long = 0L
)

/** 将 ApplicationInfo.category 映射为分组标题（P2-2）。 */
fun categoryLabel(category: Int): String = when (category) {
    ApplicationInfo.CATEGORY_GAME -> "游戏"
    ApplicationInfo.CATEGORY_VIDEO -> "影视"
    ApplicationInfo.CATEGORY_AUDIO -> "音乐"
    ApplicationInfo.CATEGORY_IMAGE -> "图片"
    ApplicationInfo.CATEGORY_SOCIAL -> "社交"
    ApplicationInfo.CATEGORY_NEWS -> "资讯"
    ApplicationInfo.CATEGORY_MAPS -> "地图"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "效率"
    else -> "其他"
}

/**
 * 将"上次使用时间"格式化为相对描述：刚刚 / N 分钟前 / N 小时前 / N 天前 / 日期。
 * ms<=0 返回 null（C1/C3 共用，详情面板与最近游玩均展示）。
 */
fun relativeLastUsed(ms: Long): String? {
    if (ms <= 0) return null
    val diff = System.currentTimeMillis() - ms
    val min = 60_000L
    val hour = 60 * min
    val day = 24 * hour
    return when {
        diff < min -> "刚刚"
        diff < hour -> "${diff / min} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 30 * day -> "${diff / day} 天前"
        else -> java.time.Instant.ofEpochMilli(ms)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}

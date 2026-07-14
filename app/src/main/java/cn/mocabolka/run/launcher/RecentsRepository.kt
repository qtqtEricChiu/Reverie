package cn.mocabolka.run.launcher

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 最近使用（近似）：第三方 Launcher 无法真实获取系统 Recent 任务，
 * 这里用 UsageStats（需 PACKAGE_USAGE_STATS 授权，否则返回空列表）做近似。
 */
class RecentsRepository(private val context: Context) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    /**
     * LauncherApps 服务用于最近应用解析主 Activity。
     * 非默认桌面且未授予 QUERY_ALL_PACKAGES 时 getSystemService 返回 null，
     * 用安全转换 `as?` 允许为 null；为 null 时最近列表该项直接跳过（退化为无图标占位）。
     */
    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    suspend fun loadRecent(limit: Int = 8): List<AppModel> = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -7) }
        val stats = runCatching {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                cal.timeInMillis,
                System.currentTimeMillis()
            )
        }.getOrNull() ?: return@withContext emptyList()

        val user = android.os.Process.myUserHandle()
        // 外层 runCatching：launcherApps.getActivityList() 在某些定制 ROM 上可能抛异常
        runCatching {
            stats.filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.lastTimeUsed }
                .map { it.packageName }
                .distinct()
                .take(limit)
                .mapNotNull { pkg ->
                    val activities = launcherApps?.let { runCatching { it.getActivityList(pkg, user) } }
                        ?.getOrNull() ?: return@mapNotNull null
                    activities.firstOrNull()?.let { ai ->
                        // ⚠️ getIcon(0) 可能返回 null（社交类常见），也可能直接抛异常
                        // （资源分离/损坏的 APK）。必须在 runCatching 内调用，否则异常会冒泡
                        // 导致协程崩溃、整个应用退出。兜底为纯色占位 Bitmap。
                        val bitmap = runCatching { ai.getIcon(0)?.toBitmap(ICON_MAX, ICON_MAX) }
                            .getOrNull()
                            ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                                .also { it.eraseColor(PLACEHOLDER_ICON_COLOR) }
                        AppModel(pkg, ai.name, ai.label.toString(), bitmap.asImageBitmap())
                    }
                }
        }.getOrNull() ?: emptyList()
    }

        companion object {
            /** 图标最大边长（px），与 AppRepository 保持一致（R13 降为 96 以降低内存峰值）。 */
            private const val ICON_MAX = 96
        }
}

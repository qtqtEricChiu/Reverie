package cn.mocabolka.run.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图标最大边长（px），超出等比缩放，兼顾清晰度与内存。
 * R13：从 192 降到 96。主列表图标显示仅 40dp、详情面板 72dp，96px 在 xxhdpi 下已足够清晰，
 * 同时将全部应用图标内存峰值降低约 4 倍（数百个应用图标场景收益明显）。
 */
private const val ICON_MAX = 96

class AppRepository(private val context: Context) {
    private val pm = context.packageManager

    /**
     * 增量加载单个应用（安装/变更后调用）。仅查询指定包名，避免全量枚举。
     * 返回 null 表示该应用已卸载或无法解析（调用方据此从列表移除）。
     */
    suspend fun loadPackage(packageName: String): AppModel? = withContext(Dispatchers.IO) {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfo = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .firstOrNull { it.activityInfo.packageName == packageName } ?: return@runCatching null
            val ai = resolveInfo.activityInfo.applicationInfo ?: return@runCatching null
            if (!ai.enabled) return@runCatching null
            val packageInfo = pm.getPackageInfo(packageName, 0)
            val label = resolveInfo.loadLabel(pm).toString().ifBlank {
                ai.loadLabel(pm).toString()
            }
            AppModel(
                packageName = packageName,
                className = resolveInfo.activityInfo.name,
                label = label,
                icon = safeIcon(resolveInfo, pm, context, packageName),
                isGame = (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                    ai.category == ApplicationInfo.CATEGORY_GAME,
                category = ai.category,
                categoryText = categoryLabel(ai.category),
                firstInstallTime = packageInfo.firstInstallTime,
                versionName = packageInfo.versionName?.toString().orEmpty()
            )
        }.getOrNull()
    }

    /**
     * 枚举应用。
     * @param includeSystem 为 true 时，在「可启动应用」之外额外枚举系统应用
     *        （FLAG_SYSTEM 且无独立启动入口被普通列表覆盖者），用于「显示系统应用」开关。
     */
    suspend fun loadApps(includeSystem: Boolean = false): List<AppModel> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AppModel>()
        runCatching {
            // 通过 MAIN/LAUNCHER Intent 枚举所有可启动应用。
            // 这比 LauncherApps.getActivityList(null, user) 更稳定：后者在部分系统上
            // 会返回空列表，而 queryIntentActivities 无需任何特殊身份即可获取全部应用。
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            // minSdk = 36 始终支持 MATCH_ALL，枚举全部可启动应用
            val flags = PackageManager.MATCH_ALL
            val resolveList = pm.queryIntentActivities(intent, flags)
            Log.d("ReverieAppRepo", "queryIntentActivities count=${resolveList.size}")

            // 同一包名可能有多个启动 Activity（例如多入口/快捷方式），按包名分组并取第一个作为主入口。
            val grouped = resolveList.groupBy { it.activityInfo.packageName }
            Log.d("ReverieAppRepo", "grouped packages count=${grouped.size}")

            for ((packageName, resolves) in grouped) {
                val resolve = resolves.firstOrNull() ?: continue
                val activityInfo = resolve.activityInfo
                val appInfo = activityInfo.applicationInfo ?: continue
                if (!appInfo.enabled) continue

                val isGame = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                        appInfo.category == ApplicationInfo.CATEGORY_GAME
                val packageInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
                val installTime = packageInfo?.firstInstallTime ?: 0L
                val versionName = packageInfo?.versionName?.toString().orEmpty()
                val label = resolve.loadLabel(pm).toString().ifBlank {
                    appInfo.loadLabel(pm).toString()
                }

                result += AppModel(
                    packageName = packageName,
                    className = activityInfo.name,
                    label = label,
                    icon = safeIcon(resolve, pm, context, packageName),
                    isGame = isGame,
                    category = appInfo.category,
                    categoryText = categoryLabel(appInfo.category),
                    firstInstallTime = installTime,
                    versionName = versionName
                )
            }

            // 补充系统应用（仅当开启开关）：枚举全部已安装包，过滤出 FLAG_SYSTEM 且
            // 未被上方「可启动列表」覆盖者，使其也能在主页展示 / 参与分类映射。
            if (includeSystem) {
                val launcherPkgs = result.map { it.packageName }.toSet()
                val installed = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
                Log.d("ReverieAppRepo", "installed packages count=${installed.size}")
                for (pi in installed) {
                    val ai = pi.applicationInfo ?: continue
                    if (ai.packageName in launcherPkgs) continue
                    val isSys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!isSys) continue
                    val launchIntent = runCatching { pm.getLaunchIntentForPackage(ai.packageName) }.getOrNull()
                    val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName)
                        .ifBlank { ai.packageName }
                    result += AppModel(
                        packageName = ai.packageName,
                        className = launchIntent?.component?.className ?: "",
                        label = label,
                        icon = safeIcon(ai, pm, context, ai.packageName),
                        isGame = (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                                ai.category == ApplicationInfo.CATEGORY_GAME,
                        category = ai.category,
                        categoryText = categoryLabel(ai.category),
                        firstInstallTime = pi.firstInstallTime,
                        versionName = pi.versionName?.toString().orEmpty(),
                        isSystem = true
                    )
                }
                Log.d("ReverieAppRepo", "after system apps result size=${result.size}")
            }
        }.onFailure {
            Log.e("ReverieAppRepo", "loadApps 外层异常", it)
        }
        Log.d("ReverieAppRepo", "loadApps result size=${result.size}")
        result.sortedBy { it.label.lowercase() }
    }
}

/**
 * 安全获取应用图标（带磁盘缓存）：优先从 [IconCache] 读取，缓存未命中时从 PackageManager 解码。
 */
private fun safeIcon(
    resolveInfo: ResolveInfo,
    pm: PackageManager,
    context: Context,
    packageName: String
): ImageBitmap {
    val placeholder = placeholderBitmap()
    // 优先读磁盘缓存
    IconCache.loadBitmap(context, packageName)?.let { bmp ->
        return bmp.asImageBitmap()
    }
    return runCatching {
        val drawable = resolveInfo.loadIcon(pm)
            ?: resolveInfo.activityInfo?.applicationInfo?.loadIcon(pm)
        val bmp = (drawable?.toBitmap(ICON_MAX, ICON_MAX) ?: placeholder)
        // 写磁盘缓存
        IconCache.saveBitmap(context, packageName, bmp)
        bmp.asImageBitmap()
    }.getOrDefault(placeholder.asImageBitmap())
}

/** 系统应用图标安全获取（带磁盘缓存）。 */
private fun safeIcon(
    appInfo: ApplicationInfo,
    pm: PackageManager,
    context: Context,
    packageName: String
): ImageBitmap {
    val placeholder = placeholderBitmap()
    // 优先读磁盘缓存
    IconCache.loadBitmap(context, packageName)?.let { bmp ->
        return bmp.asImageBitmap()
    }
    return runCatching {
        val drawable = appInfo.loadIcon(pm)
        val bmp = (drawable?.toBitmap(ICON_MAX, ICON_MAX) ?: placeholder)
        // 写磁盘缓存
        IconCache.saveBitmap(context, packageName, bmp)
        bmp.asImageBitmap()
    }.getOrDefault(placeholder.asImageBitmap())
}

/** 社交类/资源异常应用的占位图标：1×1 纯色 Bitmap（asImageBitmap 直接包装，不可 recycle）。 */
private fun placeholderBitmap(): android.graphics.Bitmap =
    android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        .also { it.eraseColor(PLACEHOLDER_ICON_COLOR) }

package cn.mocabolka.run.ui.game

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

/**
 * GamePage「个性化内容」Widget 宿主。
 *
 * 移植自 WidgetHostDemo，并按本工程规范做了精简与适配：
 * - 去掉 Demo 的 [WidgetDataCollector] 日志采集（非必需，节省体积与 token）；
 * - 绑定候选包按当前游戏动态决定（[widgetHostPackagesForGame]），而非全局固定；
 * - 保留 AppWidgetHost 生命周期、PICK 选择器、Provider 自绘列表代绑定、孤儿清理等核心能力。
 *
 * 关键点：非 Launcher 应用通过 [AppWidgetManager.ACTION_APPWIDGET_PICK] 选择器绑定 Widget，
 * 系统会自动弹出确认框，无需手动申请 BIND_APPWIDGET 权限。
 */
class WidgetHostManager(private val activity: Activity) {

    companion object {
        const val APPWIDGET_HOST_ID = 1024
        const val REQUEST_PICK_WIDGET = 1001

        /** Widget 容器宽度（dp），可由外部动态设置以适配横竖屏布局。 */
        const val DEFAULT_WIDGET_WIDTH_DP = 360
    }

    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    /** 待下个 createView 绑定的「首次刷新」回调；createView 内部同步调用 onCreateView 时注入到 HostView。 */
    private var pendingFirstRefresh: ((AppWidgetHostView) -> Unit)? = null
    private val appWidgetHost = object : AppWidgetHost(activity, APPWIDGET_HOST_ID) {
        override fun onCreateView(
            context: Context?,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView = RefreshAwareAppWidgetHostView(context ?: activity, pendingFirstRefresh)
    }

    /** Widget 容器宽度（dp）。createView / restore 时用于 updateAppWidgetSize。 */
    var widgetWidthDp: Int = DEFAULT_WIDGET_WIDTH_DP

    var onWidgetBound: ((Int, AppWidgetProviderInfo, AppWidgetHostView) -> Unit)? = null

    /** 启动宿主监听（Activity onStart 调用）。个别 ROM 上系统服务抖动可能抛出运行时异常，兜底不拖垮 Activity。 */
    fun startListening() {
        runCatching { appWidgetHost.startListening() }
            .onFailure { android.util.Log.w("GameWidgetHost", "startListening 失败: ${it.message}") }
    }

    /** 停止宿主监听（Activity onStop 调用）。 */
    fun stopListening() {
        runCatching { appWidgetHost.stopListening() }
            .onFailure { android.util.Log.w("GameWidgetHost", "stopListening 失败: ${it.message}") }
    }

    /** 弹出系统 Widget 选择器，绑定新 Widget；返回 Intent 供 ActivityResultLauncher 使用。 */
    fun pickWidget(): Intent {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    /** 在 onActivityResult 中处理 Widget 选择器返回结果。 */
    fun handlePickResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_PICK_WIDGET) return

        val extras = data?.extras ?: Bundle()
        val appWidgetId = extras.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (resultCode != Activity.RESULT_OK) {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            return
        }

        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo == null) {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            return
        }

        try {
            // 首次刷新（Provider 回写 RemoteViews）后再落地绑定，确保呈现时 Widget 已真正初始化。
            createHostViewInternal(appWidgetId, appWidgetInfo) { view ->
                onWidgetBound?.invoke(appWidgetId, appWidgetInfo, view)
            }
        } catch (e: Exception) {
            android.util.Log.w("GameWidgetHost", "创建 HostView 失败，回收 id=$appWidgetId", e)
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        }
    }

    /** 获取已绑定的全部 Widget ID（应用重启后恢复）。 */
    fun getBoundWidgets(): List<Int> = appWidgetHost.appWidgetIds.toList()

    /** 根据 ID 重建 HostView；provider 已卸载自动清理并返回 null。 */
    fun restoreWidget(appWidgetId: Int, onFirstRefresh: (AppWidgetHostView) -> Unit = {}): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        if (!isProviderPackageInstalled(info.provider)) return null
        return createHostViewInternal(appWidgetId, info, onFirstRefresh)
    }

    /** 检查 provider 所属包是否已安装。 */
    private fun isProviderPackageInstalled(provider: ComponentName): Boolean = runCatching {
        activity.packageManager.getPackageInfo(provider.packageName, 0)
        true
    }.getOrDefault(false)

    /** 清理孤儿 Widget ID（provider 已卸载或不可用）。 */
    private fun cleanOrphanId(appWidgetId: Int) {
        runCatching { appWidgetHost.deleteAppWidgetId(appWidgetId) }
    }

    /** 获取指定包名下的所有 Widget Provider。 */
    fun getProvidersForPackage(packageName: String): List<AppWidgetProviderInfo> =
        appWidgetManager.installedProviders.filter { it.provider.packageName == packageName }

    /** 根据 appWidgetId 获取 ProviderInfo（用于查询已绑定 Widget 的包名）。 */
    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        appWidgetManager.getAppWidgetInfo(appWidgetId)

    /**
     * 自绘列表点选后构造带 EXTRA_CUSTOM_INFO 的 PICK Intent，尽量缩小 PICK 列表到目标 provider。
     */
    fun pickWidgetForProvider(provider: ComponentName): Intent {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val providerInfo = appWidgetManager.installedProviders.firstOrNull { it.provider == provider }
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            if (providerInfo != null) {
                putParcelableArrayListExtra(
                    AppWidgetManager.EXTRA_CUSTOM_INFO,
                    arrayListOf(providerInfo)
                )
            }
        }
    }

    /** 获取所有已安装 Widget Provider（全量，按包过滤由调用方处理）。 */
    fun getAllProviders(): List<AppWidgetProviderInfo> = appWidgetManager.installedProviders

    /** 为已有 ID 创建 HostView（含宽度约束）。 */
    private fun createHostViewInternal(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        onFirstRefresh: (AppWidgetHostView) -> Unit = {}
    ): AppWidgetHostView {
        val widthPx = (activity.resources.displayMetrics.density * widgetWidthDp).toInt()
        pendingFirstRefresh = onFirstRefresh
        val view = try {
            appWidgetHost.createView(activity, appWidgetId, info)
        } finally {
            pendingFirstRefresh = null
        }
        view.updateAppWidgetSize(null, widthPx, -2, widthPx, -2)
        return view
    }

    /** 移除单个 Widget。 */
    fun removeWidget(appWidgetId: Int) {
        cleanOrphanId(appWidgetId)
    }

    /** 清空所有 Widget。 */
    fun clearAll() {
        appWidgetHost.appWidgetIds.toList().forEach { appWidgetHost.deleteAppWidgetId(it) }
    }
}

/**
 * Widget 绑定持久化存储（按「游戏包 + 宿主包」维度隔离 appWidgetId）。
 *
 * 背景：AppWidgetHost 全局持有所有已分配 appWidgetId，仅按宿主包（如 com.taptap）恢复会导致
 * 「无限暖暖的 TapTap 插件」与「鸣潮的 TapTap 插件」复用同一个 widgetId。本存储将每次绑定的
 * appWidgetId 记到 `gamePkg|hostPkg` 键下，重建槽位时只恢复属于当前游戏的 widget，实现真正隔离。
 */
class WidgetBindingStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(gamePkg: String, hostPkg: String) = "$gamePkg|$hostPkg"

    /** 取当前游戏 + 宿主包绑定的 appWidgetId；无记录返回 -1。 */
    fun get(gamePkg: String, hostPkg: String): Int =
        prefs.getInt(key(gamePkg, hostPkg), -1)

    /** 记录当前游戏 + 宿主包 → appWidgetId 的归属。 */
    fun put(gamePkg: String, hostPkg: String, appWidgetId: Int) {
        prefs.edit().putInt(key(gamePkg, hostPkg), appWidgetId).apply()
    }

    /** 清除当前游戏 + 宿主包的绑定记录。 */
    fun remove(gamePkg: String, hostPkg: String) {
        prefs.edit().remove(key(gamePkg, hostPkg)).apply()
    }

    companion object {
        private const val PREFS_NAME = "game_widget_bindings"
    }
}

/**
 * Widget 候选槽位（按宿主包划分）：绝区零等 mihoyo 游戏会同时出现 hyperion + taptap 两个槽位，
 * 非 mihoyo 游戏仅 taptap 一个槽位。
 */
data class WidgetHostSlot(
    val packageName: String,
    val appLabel: String,
    val isInstalled: Boolean,
    val isBound: Boolean,
    val appWidgetId: Int,
)

/** 单个 Widget Provider 的精简描述（用于自绘选择列表）。 */
data class WidgetProviderItem(
    val packageName: String,
    val className: String,
    val label: String,
    val minWidth: Int,
    val minHeight: Int,
    val previewResId: Int = 0,
)

/** 是否为 miHoYo / HoYoverse 发行的游戏（国服 com.miHoYo.* / 国际服 com.HoYoverse.*）。 */
fun isMihoyoGame(packageName: String): Boolean =
    packageName.startsWith("com.miHoYo.") || packageName.startsWith("com.HoYoverse.")

/**
 * 当前游戏可嵌入 Widget 的候选宿主包列表。
 * - mihoyo 系（绝区零国服/国际服等）：米游社 [com.mihoyo.hyperion] + TapTap [com.taptap]；
 * - 其它（无限暖暖、鸣潮等）：仅 TapTap [com.taptap]。
 */
fun widgetHostPackagesForGame(packageName: String): List<String> =
    if (isMihoyoGame(packageName)) listOf("com.mihoyo.hyperion", "com.taptap")
    else listOf("com.taptap")

/**
 * 感知首次内容到达的 AppWidgetHostView：Provider 首次回写非空的 RemoteViews（即完成「首次刷新」）时
 * 回调 [onFirstContent]，供上层在转圈 Loading 期间判定「Widget 已就绪、可呈现」（2026-07-26）。
 */
private class RefreshAwareAppWidgetHostView(
    context: Context,
    private val onFirstContent: ((AppWidgetHostView) -> Unit)? = null
) : AppWidgetHostView(context) {
    private var delivered = false
    override fun updateAppWidget(remoteViews: RemoteViews) {
        super.updateAppWidget(remoteViews)
        if (!delivered) {
            delivered = true
            onFirstContent?.invoke(this)
        }
    }
}

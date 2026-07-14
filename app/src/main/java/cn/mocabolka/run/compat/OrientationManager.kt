package cn.mocabolka.run.compat

import android.app.Activity
import android.content.Context
import android.content.Intent
import cn.mocabolka.run.ui.OrientationMode
import cn.mocabolka.run.ui.SettingsRepository

/**
 * 强制横屏功能的总控。
 *
 * 双重方向约束（系统默认场景下不闪、强制旋屏场景下双保险）：
 *  1. **系统级**：非跟随系统且已授权悬浮窗时，启动 [OrientationLockService] 挂透明占位
 *     View 强制系统整体方向（含其它 App）。
 *  2. **应用级**：无论系统级是否生效，始终把 Reverie 自身各 Activity 的
 *     `requestedOrientation` 同步为当前 [OrientationMode] 对应的 [ActivityInfo] 值。
 *     - 强制右旋 → Reverie 自身固定右旋横屏（SCREEN_ORIENTATION_REVERSE_LANDSCAPE）；
 *     - 关闭跟随系统（FOLLOW_SYSTEM）→ Reverie 自身设回 UNSPECIFIED，交还系统默认方向。
 *     这样在"应用内横屏"语义下，Reverie 一启动/回到前台即按目标方向创建窗口，不经历竖屏闪屏。
 *
 * - 每次进入前台（onResume）由 Activity 调用 [apply]，确保回到 Reverie 时方向恢复生效。
 * - 切换模式时 [onChange] 立即应用（含应用级方向）。
 */
object OrientationManager {

    /** 应用级方向缓存：最近一次应用的 ActivityInfo 方向值，供各 Activity 创建/恢复时读取。 */
    private var appOrientation: Int = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private fun start(context: Context, mode: OrientationMode) {
        val intent = Intent(context, OrientationLockService::class.java).apply {
            putExtra(OrientationLockService.KEY_MODE, mode)
        }
        runCatching {
            // minSdk = 36 始终支持前台服务（API 26+）
            context.startForegroundService(intent)
        }
    }

    private fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, OrientationLockService::class.java)) }
    }

    /**
     * 把 Reverie 自身的方向设为 [mode] 对应的 ActivityInfo 值。
     * 直接作用于当前 Activity 实例；缓存到 [appOrientation] 供其它 Reverie Activity
     * （子页面）在创建/恢复时读取并同步。
     */
    private fun applyAppOrientation(context: Context, mode: OrientationMode) {
        val info = mode.toActivityInfo()
        appOrientation = info
        (context as? Activity)?.requestedOrientation = info
    }

    /** 供子页面 Activity 在 onCreate/onResume 同步应用级方向（读缓存，无需重新决策）。 */
    fun applyAppOrientation(activity: Activity) {
        activity.requestedOrientation = appOrientation
    }

    /**
     * 供子页面 Activity 在创建/恢复时**直接按当前设置**固定自身方向，不依赖 [appOrientation]
     * 缓存的时序（避免子页面先于 MainActivity.onResume 启动时拿到初始 UNSPECIFIED）。
     * 对所有 [OrientationMode] 生效（左旋/右旋/竖屏/反向竖屏/陀螺仪/跟随系统 等），
     * 做到"强制旋屏的每个选项都对应固定 Reverie 自身方向"。
     */
    fun applyAppOrientation(activity: Activity, settings: SettingsRepository) {
        val info = settings.orientationMode.toActivityInfo()
        appOrientation = info
        activity.requestedOrientation = info
    }

    /**
     * 根据当前设置应用横屏模式（系统级 + 应用级）。
     * @return 是否缺少悬浮窗权限（true = 系统级强制未启用，但应用级方向已设）。
     *
     * 重要：返回 true **仅作日志/内部记录**，调用方 **不得** 据此跳转系统悬浮窗设置页。
     * 悬浮窗权限是可选增强（系统级强制），缺省时应用级方向依然生效，用户仍可正常使用 App。
     * 只有用户在设置页主动操作「强制旋屏」项时，才可由设置页自行引导。
     */
    fun apply(context: Context, settings: SettingsRepository): Boolean {
        val mode = settings.orientationMode
        // 应用级方向始终同步（即使系统级因缺权限未启用，应用自身方向也应正确）。
        applyAppOrientation(context, mode)
        if (mode == OrientationMode.FOLLOW_SYSTEM) {
            stop(context)
            return false
        }
        if (!OverlayPermissionHelper.canDraw(context)) {
            // 缺悬浮窗权限：不启用系统级强制（系统级旋转锁 = 增强功能），
            // 但应用级方向仍已设置，Reverie 自身仍保持目标方向。
            // 不返回 true 以触发调用方跳转 —— 初始化阶段不应因可选权限打断用户。
            stop(context)
            return false
        }
        start(context, mode)
        return false
    }

    /** 用户切换模式时调用：立即应用（已在设置页内确保权限）。 */
    fun onChange(context: Context, settings: SettingsRepository) {
        apply(context, settings)
    }
}

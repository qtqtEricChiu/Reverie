package cn.mocabolka.run.compat

import android.content.Context
import android.content.Intent
import cn.mocabolka.run.ui.OrientationMode
import cn.mocabolka.run.ui.SettingsRepository

/**
 * 强制横屏功能的总控。
 *
 * - 仅当 [OrientationMode] 非跟随系统、且已授予悬浮窗权限时，启动 [OrientationLockService]
 *   挂透明占位 View 强制系统横屏。
 * - 关闭/跟随系统时停止服务。
 * - 每次进入前台（onResume）由 Activity 调用 [apply]，确保回到 Reverie 时强制横屏恢复生效。
 */
object OrientationManager {

    /** 是否需要悬浮窗权限才能启用强制横屏（非跟随系统模式）。 */
    fun needsOverlayPermission(context: Context, mode: OrientationMode): Boolean =
        mode != OrientationMode.FOLLOW_SYSTEM && !OverlayPermissionHelper.canDraw(context)

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
     * 根据当前设置应用横屏模式。
     * @return 是否需要引导用户授予悬浮窗权限（true 时未启用强制横屏）。
     */
    fun apply(context: Context, settings: SettingsRepository): Boolean {
        val mode = settings.orientationMode
        if (mode == OrientationMode.FOLLOW_SYSTEM) {
            stop(context)
            return false
        }
        if (!OverlayPermissionHelper.canDraw(context)) {
            // 缺悬浮窗权限：不启用，提示引导
            stop(context)
            return true
        }
        start(context, mode)
        return false
    }

    /** 用户切换模式时调用：立即应用（已在设置页内确保权限）。 */
    fun onChange(context: Context, settings: SettingsRepository) {
        apply(context, settings)
    }
}

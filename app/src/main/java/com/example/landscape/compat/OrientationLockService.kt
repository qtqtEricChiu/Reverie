package cn.mocabolka.run.compat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.Space
import cn.mocabolka.run.R
import cn.mocabolka.run.ui.OrientationMode

/**
 * 强制横屏服务（借鉴 OrientationLock 开源项目精髓）。
 *
 * 精髓：在系统窗口层挂一个不可见的透明占位 View（TYPE_APPLICATION_OVERLAY），
 * 通过 `WindowManager.LayoutParams.screenOrientation` 强制系统整体旋转方向。
 * 这样所有应用（含本应用自身）都被约束为指定方向，**无需无障碍服务**，仅需悬浮窗权限。
 *
 * 本应用场景：默认 [OrientationMode.FORCE_LANDSCAPE] = SENSOR_LANDSCAPE，
 * 即仅"左旋屏 / 右旋屏"两种随陀螺仪切换的横屏，杜绝竖屏。
 */
class OrientationLockService : Service() {

    private var holderView: Space? = null
    private var currentMode: OrientationMode = OrientationMode.FOLLOW_SYSTEM

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ⚠️ 关键：startForegroundService 启动后必须在超时前调用 startForeground，
        // 否则系统抛 ForegroundServiceDidNotStartInTimeException 直接杀进程。
        startForeground(NOTIFICATION_ID, buildNotification())
        val mode = intent?.getSerializableExtra(KEY_MODE) as? OrientationMode ?: currentMode
        applyMode(mode)
        return START_STICKY
    }

    override fun onDestroy() {
        removeHolder()
        super.onDestroy()
    }

    private fun applyMode(mode: OrientationMode) {
        currentMode = mode
        if (mode == OrientationMode.FOLLOW_SYSTEM) {
            removeHolder()
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (holderView == null) {
            holderView = Space(this).apply {
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                isLongClickable = false
            }
            val params = baseParams()
            wm.addView(holderView, params)
            holderView?.visibility = View.VISIBLE
        }
        val params = (holderView?.layoutParams as? WindowManager.LayoutParams) ?: baseParams()
        params.screenOrientation = mode.toActivityInfo()
        wm.updateViewLayout(holderView, params)
    }

    private fun baseParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // minSdk = 36 使用 TYPE_APPLICATION_OVERLAY（TYPE_PHONE 已废弃）
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.RGBA_8888
        )
    }

    private fun removeHolder() {
        holderView ?: return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { wm.removeViewImmediate(holderView) }
        holderView = null
    }

    companion object {
        const val KEY_MODE = "orientation_mode"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "orientation_lock"
    }

    /** 创建不可见（低优先级、无声音）的前台服务通知，避免打扰用户。 */
    private fun buildNotification(): Notification {
        // minSdk = 36（Android 16），通知渠道 API 始终可用，无需版本守卫。
        // systemExempted 类型的前台服务仍需一条通知，但可设为最低重要性以不弹窗/不响铃
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.orientation_lock_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.orientation_lock_channel_desc)
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.orientation_lock_title))
            .setContentText(getString(R.string.orientation_lock_text))
            .setSmallIcon(R.drawable.ic_orientation_lock)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }
}

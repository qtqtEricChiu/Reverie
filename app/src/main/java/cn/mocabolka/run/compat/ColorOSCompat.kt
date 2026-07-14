package cn.mocabolka.run.compat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * 设备兼容层（以 ColorOS 为参考实现）。
 * 自启动 / 后台保活 / 悬浮窗 / 通知监听等大多无公开 API，
 * 这里通过隐式 Intent 深链跳转系统设置页，并对每个 Intent 做 resolveActivity 兜底。
 */
object ColorOSCompat {

    fun isColorOS(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val rom = getProp("ro.build.version.opporom") + getProp("ro.rom.version")
        return brand.contains("oppo") || brand.contains("oneplus") ||
                manufacturer.contains("oppo") || manufacturer.contains("oneplus") ||
                rom.contains("coloros")
    }

    private fun getProp(name: String): String = runCatching {
        val p = Runtime.getRuntime().exec("getprop $name")
        try {
            p.inputStream.bufferedReader().readText().trim()
        } finally {
            p.destroy() // 释放子进程句柄，避免句柄泄漏
        }
    }.getOrDefault("")

    /** 打开自启动 / 后台管理页（ColorOS 安全中心）。逐条尝试深链，全部失败回退到系统设置。 */
    fun openAutoStartSettings(context: Context) {
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupPermissionActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.app.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.heytap.safe",
                    "com.heytap.safe.permission.startup.StartupPermissionActivity"
                )
            ),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            if (safeStart(context, intent)) return
        }
    }

    /** 跳转通知使用权设置（通知角标监听所需）。 */
    fun openNotificationListenerSettings(context: Context) {
        safeStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /**
     * 安全地启动一个 Activity：先校验是否有可处理的组件，避免 ActivityNotFoundException。
     * 返回是否成功启动。
     */
    fun safeStart(context: Context, intent: Intent): Boolean {
        return runCatching {
            val hasComponent = intent.component != null
            val resolved = intent.resolveActivity(context.packageManager)
            when {
                hasComponent && resolved == null -> false
                resolved != null -> {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
    }
}

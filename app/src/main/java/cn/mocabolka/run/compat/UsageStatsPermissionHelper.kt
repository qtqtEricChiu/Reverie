package cn.mocabolka.run.compat

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * 使用情况访问权限（PACKAGE_USAGE_STATS）辅助：用于 Recent 近似。
 * 该权限需在系统设置中手动授予，无公开申请 API，这里提供检测与跳转。
 */
object UsageStatsPermissionHelper {
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 兜底：部分 ROM 无独立「使用情况访问」页时回退到系统设置
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

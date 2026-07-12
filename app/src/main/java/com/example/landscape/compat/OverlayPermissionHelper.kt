package cn.mocabolka.run.compat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object OverlayPermissionHelper {
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** 跳转系统悬浮窗设置页，返回是否成功启动。 */
    fun request(context: Context): Boolean {
        return ColorOSCompat.safeStart(
            context,
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }
}

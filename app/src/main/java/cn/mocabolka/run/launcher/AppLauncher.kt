package cn.mocabolka.run.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.util.Log

class AppLauncher(private val context: Context) {
    /**
     * LauncherApps 服务：用于多用户/工作资料等场景的可靠启动。
     * 非默认桌面且未授予 QUERY_ALL_PACKAGES 时 getSystemService 返回 null，
     * 此处用安全转换 `as?` 允许为 null；为 null 时退化为标准 Intent 启动。
     */
    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    /**
     * 启动应用主 Activity。
     * @return 是否启动成功。Activity 未启用 / 不存在 / 抛异常时返回 false，由调用方提示用户。
     */
    fun launch(app: AppModel, user: UserHandle = android.os.Process.myUserHandle()): Boolean {
        val component = ComponentName(app.packageName, app.className)
        // 优先通过 LauncherApps 启动（对多用户/工作资料等场景最可靠）
        val canUseLauncherApps = launcherApps != null && app.className.isNotEmpty() &&
                runCatching { launcherApps!!.isActivityEnabled(component, user) }.getOrDefault(false)
        if (canUseLauncherApps) {
            return try {
                launcherApps!!.startMainActivity(component, user, null, null)
                true
            } catch (e: SecurityException) {
                Log.w("AppLauncher", "LauncherApps 启动被拒绝: $component", e)
                // 回退到普通 Intent 启动
                launchViaIntent(app)
            } catch (e: Exception) {
                Log.w("AppLauncher", "LauncherApps 启动失败: $component", e)
                launchViaIntent(app)
            }
        }
        // LauncherApps 不可用时回退到标准 Intent 启动
        return launchViaIntent(app)
    }

    private fun launchViaIntent(app: AppModel): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("AppLauncher", "Intent 启动失败: ${app.packageName}", e)
            false
        }
    }
}

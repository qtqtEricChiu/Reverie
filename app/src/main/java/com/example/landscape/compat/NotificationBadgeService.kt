package cn.mocabolka.run.compat

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知监听服务：按 packageName 聚合各应用未读通知数（角标）。
 * 部分设备默认关闭通知使用权，需在系统设置中手动开启（见 CompatGuideActivity）。
 */
class NotificationBadgeService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        val map = getActiveNotifications()
            .orEmpty()
            .groupBy { it.packageName }
            .mapValues { it.value.size }
        BadgeStore.update(map)
    }

    companion object {
        /** 是否已在本应用开启通知监听（角标）权限。 */
        fun isEnabled(context: Context): Boolean {
            val cn = ComponentName(context, NotificationBadgeService::class.java)
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.split(":").any {
                it.contains(cn.flattenToString()) || it.contains(context.packageName)
            }
        }
    }
}

/** 对外暴露各应用角标计数的只读流，供 UI 层按需订阅。 */
object BadgeStore {
    private val _badges = MutableStateFlow<Map<String, Int>>(emptyMap())
    val badges: StateFlow<Map<String, Int>> = _badges.asStateFlow()

    internal fun update(map: Map<String, Int>) {
        _badges.value = map
    }
}

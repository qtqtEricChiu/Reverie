package cn.mocabolka.run.compat

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import cn.mocabolka.run.compat.NotificationItem
import cn.mocabolka.run.compat.RecentNotificationStore
import cn.mocabolka.run.launcher.NotificationHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知监听服务：按 packageName 聚合各应用未读通知数（角标）。
 * 部分设备默认关闭通知使用权，需在系统设置中手动开启（见 CompatGuideActivity）。
 */
class NotificationBadgeService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        NotificationHistoryStore.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recordHistory(sbn)
        publish()
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    /**
     * 服务连接（含开机 / 授权后首连 / 系统重连）：
     * 1. 补录当前全部有效通知进历史（读取存量，而非只接收新通知）——
     *    recordHistory 按 key + 同日 title/text 指纹幂等去重，重复调用安全；
     * 2. 立即重建角标，避免旧数据滞留。
     */
    override fun onListenerConnected() {
        recordAllActive()
        publish()
    }

    /** 把系统当前全部有效通知逐条写入历史（存量补录，幂等）。 */
    private fun recordAllActive() {
        runCatching {
            getActiveNotifications().orEmpty().forEach { recordHistory(it) }
        }.onFailure { android.util.Log.w("NotificationBadge", "存量通知补录失败: ${it.message}") }
    }

    /**
     * 追加通知历史记录（持久化统计用）。仅在 onNotificationPosted 中调用，
     * publish() 的角标聚合与近期流逻辑保持原样、互不影响。
     * 过滤：常驻通知（前台服务/音乐播放等 ongoing）与本应用自身的通知不计入统计。
     */
    private fun recordHistory(sbn: StatusBarNotification?) {
        val s = sbn ?: return
        runCatching {
            if (s.packageName == packageName) return
            if (s.isOngoing) return
            val extras = s.notification.extras
            NotificationHistoryStore.append(
                key = "${s.packageName}|${s.id}|${s.tag ?: ""}",
                packageName = s.packageName,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
                timestamp = s.postTime,
                category = s.notification.category ?: "",
            )
        }.onFailure { android.util.Log.w("NotificationBadge", "历史记录失败: ${it.message}") }
    }

    private fun publish() {
        // getActiveNotifications 在未连接/系统抖动时可能抛 SecurityException/RuntimeException；
        // 回调线程异常会导致监听服务崩溃重连，必须整体兜底。
        runCatching {
            val actives = getActiveNotifications().orEmpty()
            val map = actives
                .groupBy { it.packageName }
                .mapValues { it.value.size }
            BadgeStore.update(map)
            // 同步聚合最近通知（含标题/内容/跳转），供竖屏详情浮窗通知卡片使用。
            val recentItems = actives.map { sbn ->
                val n = sbn.notification
                val extras = n.extras
                NotificationItem(
                    key = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}",
                    packageName = sbn.packageName,
                    title = extras.getString(Notification.EXTRA_TITLE) ?: "",
                    text = extras.getString(Notification.EXTRA_TEXT) ?: "",
                    postTime = sbn.postTime,
                    contentIntent = n.contentIntent,
                )
            }
            RecentNotificationStore.replaceAll(recentItems)
        }.onFailure { android.util.Log.w("NotificationBadge", "角标聚合失败: ${it.message}") }
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

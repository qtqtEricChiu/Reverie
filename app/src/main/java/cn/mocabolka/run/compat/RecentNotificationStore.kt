package cn.mocabolka.run.compat

import android.app.PendingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 单条通知的轻量快照（来自 [android.service.notification.StatusBarNotification]）。
 * 仅保留详情浮窗通知卡片所需的字段，不持有整个 SBN（避免跨进程对象泄漏）。
 */
data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
)

/**
 * 最近通知的只读快照（最多 [MAX_SIZE] 条，按 [NotificationItem.postTime] 倒序）。
 * 由 [cn.mocabolka.run.compat.NotificationBadgeService] 在每次角标聚合时同步刷新，
 * 供竖屏详情浮窗的「最近通知」卡片展示与跳转。
 */
object RecentNotificationStore {
    private const val MAX_SIZE = 10
    private val _list = MutableStateFlow<List<NotificationItem>>(emptyList())
    val recentNotifications: StateFlow<List<NotificationItem>> = _list.asStateFlow()

    fun replaceAll(items: List<NotificationItem>) {
        val next = items
            .distinctBy { it.key }            // 去重：同包同 id 同 tag 只保留一条
            .sortedByDescending { it.postTime }
            .take(MAX_SIZE)
        // 无变化（按展示字段比较，忽略不稳定的 contentIntent 引用）则不 emit，避免频繁重组。
        if (sameContent(next, _list.value)) return
        _list.value = next
    }

    private fun sameContent(a: List<NotificationItem>, b: List<NotificationItem>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x.key != y.key || x.packageName != y.packageName ||
                x.title != y.title || x.text != y.text || x.postTime != y.postTime
            ) return false
        }
        return true
    }
}

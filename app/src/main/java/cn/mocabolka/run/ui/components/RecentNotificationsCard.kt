package cn.mocabolka.run.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.compat.NotificationItem
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.SurfaceTokens

/**
 * 「最近通知」卡片：列出通知监听服务聚合到的近期通知，
 * 点击任一行触发该通知原有的 contentIntent 跳转（如回到聊天 / 打开对应 App）。
 *
 * 约定：
 * - 作为竖屏详情浮窗的**平级兄弟节点**渲染在浮窗顶端之上（非浮窗内部）；横屏不展示。
 * - 调用方须先按当前详情应用 packageName 过滤，仅展示该应用自己的通知。
 * - contentIntent 为 null（如某些前台/无动作通知）→ 整行降透明度且不可点击。
 * - 列表为空时不渲染（无空白占位），由调用方保证仅在 non-empty 时传入。
 */
@Composable
fun RecentNotificationsCard(
    items: List<NotificationItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.sm))
            .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default))
            .padding(Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "最近通知",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        items.forEach { item ->
            NotificationRow(item = item, context = context)
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationItem, context: Context) {
    val iconBitmap = rememberAppIcon(context, item.packageName)
    val label = rememberAppLabel(context, item.packageName)
    val clickable = item.contentIntent != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            // 点击区域包含内边距；contentIntent 为 null 时整行降透明度且不可点击。
            .padding(Dimens.sm)
            .then(
                if (clickable) Modifier.clickable { runCatching { item.contentIntent?.send() } }
                else Modifier.alpha(0.5f)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        if (iconBitmap != null) {
            Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(28.dp))
        } else {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val title = item.title.ifBlank { label }
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = if (item.title.isNotBlank() && item.text.isNotBlank()) item.text else label
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun rememberAppIcon(context: Context, packageName: String): ImageBitmap? {
    return remember(context, packageName) {
        runCatching {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(pm.getApplicationInfo(packageName, 0))
            (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.asImageBitmap()
        }.getOrNull()
    }
}

@Composable
private fun rememberAppLabel(context: Context, packageName: String): String {
    return remember(context, packageName) {
        runCatching {
            context.packageManager
                .getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0))
                .toString()
        }.getOrDefault(packageName)
    }
}

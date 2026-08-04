package cn.mocabolka.run.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.launcher.NotificationRecord
import cn.mocabolka.run.ui.theme.Dimens
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.Calendar

/**
 * 通知历史钻取页内容（SubPageScaffold 承载）。
 *
 * 每条通知映射为 SubPageRow.Card(focusable=true)：卡片外观与焦点框由 SubPageScaffold 的
 * SubPageFocusableCard 统一提供（无需本页重复包裹 SurfaceCard / focusBorder）；手柄左摇杆逐条遍历、
 * 右摇杆滚动同步焦点、B 返回，均为框架行为。
 */
@Composable
fun NotificationHistoryPage(
    title: String,
    app: AppModel?,
    records: List<NotificationRecord>,
    onBack: () -> Unit,
    listState: LazyListState,
    rightStickFirstVisible: State<Int>,
    navigate: MutableSharedFlow<Direction>,
    reduceMotion: Boolean
) {
    // 防御去重：按 key 唯一化（保留同 key 中时间戳最新的一条）。
    // 根因修复在 NotificationHistoryStore.append（同 key 通知就地更新而非追加），
    // 此处再兜底一次，防止已落盘的旧脏数据（历史重复 key）触发 LazyColumn 重复 key 崩溃。
    val deduped = remember(records) {
        records.asReversed().distinctBy { it.key }.asReversed()
    }
    val rows = remember(deduped, app) {
        buildList {
            if (deduped.isEmpty()) {
                add(
                    SubPageRow.Static(key = "empty") {
                        Text(
                            text = "该范围内暂无通知记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            } else {
                add(
                    SubPageRow.Static(key = "summary") {
                        Text(
                            text = "本期共 ${deduped.size} 条通知",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                deduped.forEach { rec ->
                    add(
                        SubPageRow.Card(
                            key = rec.key,
                            focusable = true,
                            content = {
                                NotificationLineContent(app = app, rec = rec)
                            }
                        )
                    )
                }
            }
        }
    }
    SubPageScaffold(
        title = title,
        onBack = onBack,
        listState = listState,
        rightStickFirstVisible = rightStickFirstVisible,
        navigate = navigate,
        reduceMotion = reduceMotion,
        rows = rows
    )
}

@Composable
private fun NotificationLineContent(app: AppModel?, rec: NotificationRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = app?.icon
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(Dimens.RadiusSm))
            )
            Spacer(Modifier.width(Dimens.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            val titleText = rec.title ?: app?.label ?: ""
            if (titleText.isNotBlank()) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val bodyText = rec.text
            if (!bodyText.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(Dimens.sm))
        Text(
            text = timeOf(rec.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 通知时间格式化（与统计页一致）：今天 HH:mm / 昨天 HH:mm / M月d日 / yyyy/M/d。 */
private fun timeOf(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val now = Calendar.getInstance()
    val hm = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    val yesterday = now.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val isYesterday = cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_MONTH)
    return when {
        sameDay -> "今天 $hm"
        isYesterday -> "昨天 $hm"
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
        else -> "${cal.get(Calendar.YEAR)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    }
}

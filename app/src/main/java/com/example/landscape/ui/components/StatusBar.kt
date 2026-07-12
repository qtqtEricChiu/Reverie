package cn.mocabolka.run.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mocabolka.run.ui.theme.Dimens
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 桌面顶部状态栏：问候 + 日期（左侧） | 四选一滑块：主页/统计/搜索/设置（右侧）。
 *
 * 滑块互斥（四个选一个），支持触控左右滑动切换。
 * "136 款应用"已移除（分类 tab 自带数字统计）。
 */
@Composable
fun StatusBar(
    appCount: Int,
    modifier: Modifier = Modifier,
    /** 当前页标题：替换原先的时段问候语（下午好/早上好…），各页显示对应页面名，
     *  避免与设置页等子页面自带标题形成「双层标题」。 */
    pageTitle: String = "",
    /** 当前 Tab（HOME/STATS/SEARCH/SETTINGS） */
    currentTab: Any? = null,
    onTabChange: (Any?) -> Unit = {},
    homeTab: Any? = null,
    statsTab: Any? = null,
    searchTab: Any? = null,
    settingsTab: Any? = null,
    showGamepadHints: Boolean = false
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // R13 省电/性能：仅在「分钟边界」刷新，而非每 30 秒强制重组。统一 1 分钟间隔。
    LaunchedEffect(Unit) {
        while (true) {
            val millisToNext = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(millisToNext)
            now = LocalDateTime.now()
        }
    }

    // C1-3：用 derivedStateOf 将日期计算与重组解耦，仅在 now 的实际字段变化时派生。
    val dateText: State<String> = remember { derivedStateOf { now.format(DateTimeFormatter.ofPattern("M月d日 EEEE")) } }

    val tabs = listOf(
        QuadTab("", Icons.Default.Home, homeTab),
        QuadTab("", Icons.Default.BarChart, statsTab),
        QuadTab("\u641c\u7d22", Icons.Default.Search, searchTab),
        QuadTab("\u8bbe\u7f6e", Icons.Default.Settings, settingsTab)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧：简洁问候 + 日期（紧凑）
        // 竖屏窄屏时日期串（"M月d日 星期X"）较长，与右侧滑块争抢空间——
        // 用 weight(1f, fill=false) 约束左侧最大宽度并允许收缩，文本单行省略，
        // 避免把右侧 Tab 滑块挤出或换行。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = Dimens.sm)
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = dateText.value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // 右侧：四选一滑块（触控左右滑动 + 点击切换）；左右两侧贴 LT / RT 键位提示
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
            if (showGamepadHints) {
                TabTriggerBadge(text = "LT")
            }
            QuadTabSlider(
                tabs = tabs,
                currentTab = currentTab,
                onTabChange = onTabChange,
                showGamepadHints = showGamepadHints
            )
            if (showGamepadHints) {
                TabTriggerBadge(text = "RT")
            }
        }
    }
}

private data class QuadTab(
    val label: String,
    val icon: ImageVector?,
    val tabValue: Any?
)

/**
 * 四选一滑块：主页/统计（文字）/搜索（图标）/设置（图标）。
 * 支持触控左右滑动切换（pointerInput + detectHorizontalDragGestures）。
 * 手柄操作由外部 Trigger 事件驱动。
 */
@Composable
private fun QuadTabSlider(
    tabs: List<QuadTab>,
    currentTab: Any?,
    onTabChange: (Any?) -> Unit,
    showGamepadHints: Boolean
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.xl))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(3.dp)
            // 触控左右滑动切换 Tab
            .pointerInput(tabs) {
                val totalTabs = tabs.size
                detectHorizontalDragGestures(
                    onDragEnd = {},
                    onHorizontalDrag = { _, dragAmount ->
                        val currentIdx = tabs.indexOfFirst { it.tabValue == currentTab }
                        if (currentIdx < 0) return@detectHorizontalDragGestures
                        val threshold = 30f
                        if (dragAmount > threshold && currentIdx < totalTabs - 1) {
                            onTabChange(tabs[currentIdx + 1].tabValue!!)
                        } else if (dragAmount < -threshold && currentIdx > 0) {
                            onTabChange(tabs[currentIdx - 1].tabValue!!)
                        }
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = tab.tabValue == currentTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.xl - 3.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else Color.Transparent
                    )
                    .clickable { if (tab.tabValue != null) onTabChange(tab.tabValue) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                if (tab.icon != null) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 滑块两侧的扳机键位角标（LT / RT）：贴合 QuadTabSlider 上下居中，
 * 与 SideKeyBadge 风格一致但更紧凑（指示全局 Tab 翻页）。
 */
@Composable
private fun TabTriggerBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 1
        )
    }
}

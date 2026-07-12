package cn.mocabolka.run.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.launcher.relativeLastUsed
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.PulseSpec
import kotlinx.coroutines.delay

/**
 * 横屏主列表的单项组件（详细列表样式）。
 *
 * 视觉结构（左→右）：
 *  ┌────┬────────────┬──────┬──────┬──┐
 *  │图标│ 名称(粗体) │ 分类 │ 上次 │ ★ │ ← 收藏按钮
 *  │    │ 版本/包名  │ 游玩 │ 角标 │  │
 *  └────┴────────────┴──────┴──────┴──┘
 *
 * 动效（高帧率 / GPU 合成）：
 * - 焦点态：背景提升 + 主题色边框 + **呼吸光晕脉动**（graphicsLayer 缩放/透明度循环，零重组）
 * - 入场：切换分类/搜索时逐行淡入 + 轻微上移（按索引错峰）
 * - 收藏星标：成为收藏时弹一下（spring pop）
 * - 通知角标：出现/消失时弹性缩放
 */
@Composable
fun AppListItem(
    app: AppModel,
    isFocused: Boolean,
    isFavorite: Boolean,
    badgeCount: Int,
    highlight: String,
    onFocus: () -> Unit,
    onLaunch: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    /** 仅在已连接手柄时显示收藏按钮的 X 角标。 */
    showGamepadHints: Boolean = false,
    /** 行高；默认 76dp。 */
    rowHeight: Dp = 76.dp,
    /** 今日使用时长（ms），>0 时显示时长列（R11-3）。 */
    todayUsage: Long = 0L,
    /** 减少动态效果：冻结入场/脉动等动画（无障碍）。 */
    reduceMotion: Boolean = false,
    /** 入场动画索引（用于错峰延迟），一般传列表内位置。 */
    entranceIndex: Int = 0,
    /** 入场动画重放键：分类/搜索变化时改变以触发逐行入场。 */
    entranceKey: String = "",
    /** 跳过入场动画（快速滚动时置 true，松手后置 false）。 */
    skipEntrance: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // ── 入场动画（错峰淡入 + 轻微上移）──
    // 关键设计（平衡动画与快速跟手）：
    // 1. 错峰间隔仅 8ms/项（上限 160ms），极短延迟不拖累整体感知；
    // 2. 动画时长 220ms（MotionSpec.Fast），不阻挡滚动；
    // 3. [skipEntrance] 由 AppList 根据 LazyListState.isScrollInProgress 控制，
    //    快速滚动时跳过入场延迟 + 动画立即到位，松手后新出现的项正常执行入场。
    // reduceMotion 时立即到位。
    var appeared by remember(entranceKey) { mutableStateOf(reduceMotion || skipEntrance) }
    LaunchedEffect(entranceKey) {
        if (reduceMotion || skipEntrance) { appeared = true; return@LaunchedEffect }
        appeared = false
        val delayMs = MotionSpec.staggerDelay(entranceIndex, perMs = 8, maxMs = 160)
        if (delayMs > 0) delay(delayMs)
        appeared = true
    }
    val entAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.Fast,
        label = "itemAlpha"
    )
    val entY by animateFloatAsState(
        targetValue = if (appeared) 0f else 12f,
        animationSpec = MotionSpec.Fast,
        label = "itemY"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.99f else 1f,
        animationSpec = MotionSpec.FocusSpring,
        label = "listItemScale"
    )

    // 焦点呼吸光晕脉动系数（仅聚焦时启动无限过渡）
    // 任务 3：移除焦点色块动画 — 不再绘制背后模糊放大的色块（视觉干扰）
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotatedLabel = remember(app.label, highlight, highlightColor) {
        buildHighlighted(app.label, highlight, highlightColor)
    }

    // 收藏星标弹跳（成为收藏时 pop）
    var favPulse by remember { mutableStateOf(false) }
    LaunchedEffect(isFavorite) {
        if (isFavorite) { favPulse = true; delay(180); favPulse = false }
    }
    val favScale by animateFloatAsState(
        targetValue = if (favPulse) 1.32f else 1f,
        animationSpec = MotionSpec.PopSpring,
        label = "favPop"
    )

    // 通知角标弹性出现/消失
    val badgeScale by animateFloatAsState(
        targetValue = if (badgeCount > 0) 1f else 0f,
        animationSpec = MotionSpec.FocusSpring,
        label = "badgeScale"
    )

    Box(modifier = modifier.fillMaxWidth().height(rowHeight)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .graphicsLayer { alpha = entAlpha; translationY = entY }
                .scale(scale)
                .clip(RoundedCornerShape(Dimens.TileCornerRadius))
                .background(
                    if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = if (isFocused) 2.5.dp else 0.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                    shape = RoundedCornerShape(Dimens.TileCornerRadius)
                )
                .focusable(interactionSource = interactionSource)
                .onFocusChanged { if (it.isFocused) onFocus() }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    // 触控模式：点击仅选中并显示右侧详情（不再直接启动），启动走详情面板启动按钮
                    onClick = onFocus
                )
                .padding(horizontal = Dimens.md, vertical = Dimens.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            // ─── 1. 图标 ──────────────────────────────────
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (app.icon.width > 0) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                // 角标（弹性出现/消失）
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer { scaleX = badgeScale; scaleY = badgeScale; alpha = badgeScale }
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ─── 2. 名称 + 副标题 ────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = annotatedLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isFocused) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val subtitle = buildString {
                    append(app.categoryText)
                    if (app.versionName.isNotBlank()) {
                        append(" · v").append(app.versionName)
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ─── 3. 上次游玩 / 今日时长（可选列）──────────
            val last = relativeLastUsed(app.lastUsedTime)
            if (last != null) {
                Text(
                    text = last,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(90.dp)
                )
            }
            // 今日使用时长（R11-3）：仅当天有记录时显示，避免空占列
            if (todayUsage > 0L) {
                val usageText = formatDuration(todayUsage)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.width(72.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                    Text(
                        text = usageText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ─── 4. 收藏按钮 ─────────────────────────────
            Box {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (isFavorite) "已收藏" else "收藏",
                        tint = if (isFavorite) Color(0xFFFFD54F)
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { scaleX = favScale; scaleY = favScale }
                    )
                }
                if (showGamepadHints && isFocused) {
                    KeyBadge(
                        text = "X",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                    )
                }
            }
        }
    }
}

/** 手柄键位角标：聚焦时在条目右上角显示对应按键提示（如 "X" 收藏）。
 *  样式统一为深色圆角浮块 + 亮色按键名（与其它手柄键位浮块一致）。 */
@Composable
fun KeyBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}

/** 将时长（ms）格式化为简短可读字符串：m 分 / h 小时 m 分 / s 秒。 */
private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    if (totalSec < 60L) return "${totalSec}秒"
    val min = totalSec / 60
    if (min < 60L) return "${min}分"
    val hour = min / 60
    val rem = min % 60
    return if (rem == 0L) "${hour}小时" else "${hour}小时${rem}分"
}

/** 在 [text] 中高亮所有匹配 [query] 的子串。 */
private fun buildHighlighted(
    text: String,
    query: String,
    color: Color
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val lower = text.lowercase()
    val q = query.lowercase()
    return buildAnnotatedString {
        var idx = 0
        while (idx <= text.length) {
            val found = lower.indexOf(q, idx)
            if (found < 0) {
                append(text.substring(idx))
                break
            }
            if (found > idx) append(text.substring(idx, found))
            pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold))
            append(text.substring(found, (found + q.length).coerceAtMost(text.length)))
            pop()
            idx = found + q.length
        }
    }
}

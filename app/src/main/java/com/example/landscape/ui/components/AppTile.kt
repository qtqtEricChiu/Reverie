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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.MotionSpec

@Composable
fun AppTile(
    app: AppModel,
    onFocus: (String) -> Unit,
    onLaunch: (AppModel) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.TileSize,
    enabled: Boolean = true,
    /** 是否已收藏（在精选区固定），显示星标角标。 */
    isFavorite: Boolean = false,
    /** 通知未读数，>0 时显示红色角标。 */
    badgeCount: Int = 0,
    /** 数据驱动焦点：由父组件按 focusedPackage 传入，与 Compose 原生焦点共同决定高亮。
     *  手柄导航改用数据驱动（见 HomeViewModel.moveFocus），避免 LazyGrid 原生焦点丢失。 */
    isFocused: Boolean = false,
    /** 可选副标题（如"3 天前"），显示在名称下方（C3-2）。 */
    subtitle: String = "",
    /** 搜索关键字，命中时在名称中高亮（C5-1）。 */
    highlight: String = "",
    /** 减少动态效果：冻结焦点缩放动画（无障碍），开启时固定原始尺寸。 */
    reduceMotion: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val effectiveFocused = focused || isFocused
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val bitmap = app.icon

    // 缩放动画：按下 → 缩小，聚焦 → 明显放大，默认 → 原始
    // 统一走 MotionSpec.FocusSpring + 受 reduceMotion 守卫（无障碍）
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> MotionSpec.PressScale
            effectiveFocused -> MotionSpec.FocusScale
            else -> 1f
        },
        animationSpec = MotionSpec.FocusSpring,
        label = "tileScale"
    )

    Box {
        Column(
            modifier = modifier
                .width(size)
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus(app.packageName)
                }
                .scale(if (reduceMotion) 1f else scale)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(RoundedCornerShape(Dimens.TileCornerRadius))
                .background(
                    when {
                        effectiveFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
                .border(
                    width = if (effectiveFocused) Dimens.FocusBorderWidthSelected else Dimens.FocusBorderWidth,
                    color = when {
                        effectiveFocused -> MaterialTheme.colorScheme.primary
                        isFavorite -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                        enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(Dimens.TileCornerRadius)
                )
                // 焦点外发光：放在 clip/border 之后绘制，避免被圆角裁切掉
                .then(
                    if (effectiveFocused) Modifier.shadow(
                        elevation = Dimens.FocusGlowSpreadRadius,
                        shape = RoundedCornerShape(Dimens.TileCornerRadius),
                        spotColor = MaterialTheme.colorScheme.primary,
                        ambientColor = MaterialTheme.colorScheme.primary
                    ) else Modifier
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null, // 我们用自定义动画代替
                    onClick = { onLaunch(app) }
                )
                .padding(Dimens.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (bitmap != null && isBitmapValid(bitmap, density.density)) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    modifier = Modifier.size(size * Dimens.TileIconRatio),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = app.label,
                    modifier = Modifier.size(size * Dimens.TileIconRatio),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Dimens.xs))
            val highlightColor = MaterialTheme.colorScheme.primary
            val annotatedLabel = remember(app.label, highlight, highlightColor) {
                buildHighlighted(app.label, highlight, highlightColor)
            }
            Text(
                text = annotatedLabel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 收藏星标（左上角）
        if (isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "已收藏",
                tint = Color(0xFFFFD54F),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(18.dp)
            )
        }

        // 通知角标（右上角）
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun isBitmapValid(bitmap: ImageBitmap?, density: Float): Boolean {
    if (bitmap == null) return false
    val width = bitmap.width
    val height = bitmap.height
    return width > 0 && height > 0 && (width * density > 1 && height * density > 1)
}

/** 在 [text] 中高亮所有匹配 [query]（忽略大小写）的子串，返回带 SpanStyle 的 AnnotatedString（C5-1）。 */
private fun buildHighlighted(text: String, query: String, color: androidx.compose.ui.graphics.Color): AnnotatedString {
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

package cn.mocabolka.run.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LocalShowFocusIndicators
import cn.mocabolka.run.ui.theme.SurfaceTokens

/**
 * 统一 Material Design 3 按钮库（Reverie 专项 2026-07-13）。
 *
 * 背景：项目散落大量手搓"伪按钮"（Box + .clip + .background + .clickable），
 * 圆角/语义色/交互态（ripple / disabled）各不相同，不符合 MD3 规范。
 * 本文件收敛为 5 类标准按钮 + 1 类分段按钮，全部：
 *  - 走 MaterialTheme.shapes.medium（12dp，与 TvShapes 对齐）
 *  - 走 MaterialTheme.colorScheme 语义色（primary / onPrimary / error / container…）
 *  - 复用 MD3 原生 Button 组件的 ripple / disabled / minTouchTarget
 *  - 通过 [wrapFocusBorder] 叠加手柄焦点态（仅手柄连接时显示，不破坏 MD3 交互）
 *
 * 调用方替换清单：
 *  - 详情面板 5 个操作按钮 → ReverieOutlinedButton / ReverieFilledButton
 *  - 统计页周期滑块 → ReverieSegmentedRow + ReverieSegment
 *  - 分类 tab 滑块 → ReverieSegmentedRow + ReverieSegment
 *  - 空列表/授权/重扫按钮 → ReverieFilledButton
 *  - 各类弹窗 取消/确认 → ReverieTextButton / ReverieFilledButton
 */

/** 按钮内容统一内边距（紧凑适配 TV 大屏，横向 20dp 接近 MD3 24dp，纵向 10dp）。 */
val ReverieButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)

/** 按钮最小高度（MD3 标准 40dp touch target）。 */
val ReverieButtonMinHeight = 40.dp

/**
 * 手柄焦点边框包装：在任意内容外层叠加一层主题色描边。
 * 仅当 [focused] 为 true 时绘制，否则透明无边框（不占用额外空间、不挡 ripple）。
 * 用于给 MD3 标准按钮补充"手柄焦点反馈"（项目既有交互约定）。
 */
@Composable
fun wrapFocusBorder(
    focused: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier) {
        content()
        if (focused && LocalShowFocusIndicators.current) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.medium)
                    .border(Dimens.FocusBorderWidthSelected, color, MaterialTheme.shapes.medium)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Filled 主按钮（MD3 Button：primary 实色填充）
// ─────────────────────────────────────────────────────────────
@Composable
fun ReverieFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 手柄焦点态（叠加主题色边框），由调用方依据 focusedRow 传入。 */
    focused: Boolean = false,
    /** 危险语义（错误色填充）。 */
    danger: Boolean = false,
    icon: ImageVector? = null,
    text: String,
    content: @Composable RowScope.() -> Unit = {}
) {
    val colors = if (danger) ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError
    ) else ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
    wrapFocusBorder(focused = focused, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.defaultMinSize(minHeight = ReverieButtonMinHeight),
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            contentPadding = ReverieButtonPadding
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.xxs))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Tonal 中性填充按钮（MD3 FilledTonalButton：secondaryContainer 填充）
// ─────────────────────────────────────────────────────────────
@Composable
fun ReverieTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focused: Boolean = false,
    icon: ImageVector? = null,
    text: String,
    content: @Composable RowScope.() -> Unit = {}
) {
    wrapFocusBorder(focused = focused) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.defaultMinSize(minHeight = ReverieButtonMinHeight),
            shape = MaterialTheme.shapes.medium,
            contentPadding = ReverieButtonPadding
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.xxs))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Outlined 描边按钮（MD3 OutlinedButton：透明底 + 主题色描边）
// 用于详情面板"收藏/信息/卸载/强停"等次级操作（保留原用途，但统一规范）。
// ─────────────────────────────────────────────────────────────
@Composable
fun ReverieOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focused: Boolean = false,
    /** 危险语义（error 描边 + error 文字）。 */
    danger: Boolean = false,
    icon: ImageVector? = null,
    text: String,
    content: @Composable RowScope.() -> Unit = {}
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    wrapFocusBorder(focused = focused, color = accent) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.defaultMinSize(minHeight = ReverieButtonMinHeight),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(
                if (focused && LocalShowFocusIndicators.current) Dimens.FocusBorderWidthSelected else 1.dp, accent
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = accent,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            contentPadding = ReverieButtonPadding
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.xxs))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Text 文字按钮（MD3 TextButton：无底色，仅文字+图标）
// 用于弹窗"取消"等低强调操作。
// ─────────────────────────────────────────────────────────────
@Composable
fun ReverieTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focused: Boolean = false,
    /** 危险语义（error 文字）。 */
    danger: Boolean = false,
    text: String,
    content: @Composable RowScope.() -> Unit = {}
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    wrapFocusBorder(focused = focused, color = accent) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.defaultMinSize(minHeight = ReverieButtonMinHeight),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.textButtonColors(contentColor = accent),
            contentPadding = ReverieButtonPadding
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 分段按钮（MD3 风格单选分段控件）
// 替代手搓"tab 滑块"（PeriodTabs / 分类 tab）。
// 设计：外层 surfaceVariant 容器 + 各段等分（weight(1f)）；
// 选中段用 MD3 标准 primaryContainer 背景 + onPrimaryContainer 文字（替代原 0.22 alpha 弱背景）。
// 圆角统一 MaterialTheme.shapes.medium；单选语义由调用方 selected 控制。
// 注：项目 material3 版本（1.3.x）未提供官方 SegmentedButton，此处用 MD3 语义色自绘等价组件。
// ─────────────────────────────────────────────────────────────
@Composable
fun <T> ReverieSegmentedRow(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SurfaceTokens.segmentContainer())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSel = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelect(item) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSel) androidx.compose.ui.text.font.FontWeight.SemiBold
                                 else androidx.compose.ui.text.font.FontWeight.Medium,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 图标按钮（MD3 IconButton）
// 用于对话框关闭（×）、设置项信息（i）等场景。
// 提供 optional 圆形 surfaceVariant 背景（默认 MD3 透明背景），统一 40dp 命中区。
// ─────────────────────────────────────────────────────────────
@Composable
fun ReverieIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /** 圆形 surfaceVariant 背景（默认 false = MD3 标准透明）。 */
    tonal: Boolean = false,
    enabled: Boolean = true,
    focused: Boolean = false,
    tint: Color = if (tonal) MaterialTheme.colorScheme.onSurfaceVariant
                  else MaterialTheme.colorScheme.primary
) {
    val borderMod: Modifier = if (focused && LocalShowFocusIndicators.current) Modifier.border(
        Dimens.FocusBorderWidthSelected, MaterialTheme.colorScheme.primary,
        RoundedCornerShape(Dimens.md)
    ) else Modifier
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(borderMod)
            .then(
                if (tonal) Modifier
                    .clip(RoundedCornerShape(Dimens.md))
                    .background(SurfaceTokens.iconTonalBg())
                else Modifier
            )
            .size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint
        )
    ) {
        Icon(imageVector, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}


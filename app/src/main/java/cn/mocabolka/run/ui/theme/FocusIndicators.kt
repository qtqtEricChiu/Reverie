package cn.mocabolka.run.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * 焦点指示符（焦点框 / 焦点高亮边框）可见性开关。
 *
 * 设计意图：手柄未连接或处于纯触控模式时，不显示任何焦点框 ——
 * 焦点框是手柄导航的视觉反馈，触控 / 鼠标环境下没有"当前焦点项"的概念，
 * 始终高亮某一个列表项会造成"莫名其妙选中了一项"的困惑。
 *
 * 该值由 [LandscapeTheme] 根据手柄连接状态自动提供，各焦点组件（AppListItem、
 * AppDetailPanel、SubPage、StatsScreen 等）在绘制焦点态边框时应与
 * `LocalShowFocusIndicators.current` 做与运算。
 */
val LocalShowFocusIndicators = compositionLocalOf { true }

/**
 * 标准化焦点边框 Modifier（焦点框归一化的单一入口）。
 *
 * 仅当 [enabled] 且手柄已连接（[LocalShowFocusIndicators] 为 true）时，
 * 在调用方 Modifier 链末端叠加一层主色焦点环；手柄未连接 / 纯触控模式
 * 直接返回原始 Modifier，不绘制任何焦点框。
 *
 * 统一规范（焦点框语言）：
 *  - 线宽 = [Dimens.FocusBorderWidthSelected]（3dp），全场景一致；
 *  - 颜色 = [color]（默认 primary；圆形实心按钮可传 onPrimary 以保证对比）；
 *  - 圆角 = 调用方传入的 [shape]，与组件自身 shape 对齐（medium / lg / CircleShape…）。
 *
 * 用法：`.then(Modifier.focusBorder(focused, color, shape))`，
 * 或简写 `.focusBorder(focused)`（沿用默认 primary + medium）。
 */
@Composable
fun Modifier.focusBorder(
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = MaterialTheme.shapes.medium
): Modifier {
    val show = enabled && LocalShowFocusIndicators.current
    return if (show) this.then(Modifier.border(Dimens.FocusBorderWidthSelected, color, shape))
    else this
}

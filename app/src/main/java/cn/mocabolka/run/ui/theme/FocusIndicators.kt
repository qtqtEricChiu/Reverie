package cn.mocabolka.run.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/**
 * 焦点卡片视觉归一（B-EXT1 降级方案落点）。
 * 统一「聚焦态背景提升 + 主色焦点边框」的散落手写样板（与 [focusBorder] 同构，
 * 边框可见性同样由 [LocalShowFocusIndicators] 双重判定）。
 *
 * 仅封装视觉（background + border），不改变调用方的布局 / 交互链路
 * （focusable / onFocusChanged / clickable 仍由调用方自行管理），以零视觉回归地
 * 消除 Surface+focusBg+border 重复。
 *
 * @param focused 已与手柄可见性做过与运算的聚焦态（如 `isFocused && LocalShowFocusIndicators.current`）。
 * @param shape   卡片圆角形状（应与 clip 形状一致）。
 * @param borderWidth 聚焦态边框宽度，默认 [Dimens.FocusBorderWidthSelected]。
 * @param cardColor   未聚焦时的背景色，默认主题 surface。
 */
@Composable
fun Modifier.focusCardVisual(
    focused: Boolean,
    shape: Shape = MaterialTheme.shapes.medium,
    borderWidth: Dp = Dimens.FocusBorderWidthSelected,
    cardColor: Color = MaterialTheme.colorScheme.surface
): Modifier {
    val show = focused && LocalShowFocusIndicators.current
    return if (show) {
        this
            .background(SurfaceTokens.focusBg())
            .border(width = borderWidth, color = MaterialTheme.colorScheme.primary, shape = shape)
    } else {
        // 非焦点态完全不调用 .border()：Compose 的 border 即便 width=0.dp 也会在
        // RoundedCornerShape 边缘执行抗锯齿绘制，于浅 surface 与深背景间留下 ~1px 灰线
        // （即各卡片四周的"细边框"渲染瑕疵）。非焦点态只铺背景，绝不留边。
        this.background(cardColor)
    }
}

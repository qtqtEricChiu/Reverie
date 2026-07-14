package cn.mocabolka.run.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 瀑布屏（曲面屏）两侧安全边距工具 —— **仅处理左右两侧，底部不介入（让页面贴边）**。
 *
 * 背景：曲面屏手机（waterfall display）弯曲区主要在**左右两侧**，内容贴近会被光学扭曲、
 * 且边缘误触率高。Android 在 API 30+ 把瀑布弯曲区计入 [WindowInsets.displayCutout]
 * 的左右 inset，但很多设备报出的值偏小（甚至只报前置摄像头缺口），不足以规避曲面边缘。
 *
 * 策略：在 displayCutout 的左右 inset 与本函数内部兜底 (12dp) 之间取**较大值**作为左右内边距。
 *  - 普通直屏设备：cutout 左右为 0 → 仍保留 12dp 可见避让（避免贴边），与主页节奏一致；
 *  - 瀑布屏设备：cutout 左右较大 → 取真实弯曲区，确保关键内容（按钮、焦点框）不贴曲面边缘。
 *
 * **底部处理原则**：本工具**不**为底部加任何 padding。底部应完全贴近屏幕底边，
 * 由页面自身布局决定留白（如列表 contentPadding），不受瀑布边距影响。
 *
 * **横屏不生效**：横屏（[LocalConfiguration] 宽 ≥ 高）时一侧受状态栏/导航栏占据、
 * 另一侧也不挡阅读，强行留白纯属浪费。横屏直接返回 [this]，零开销。
 *
 * **全页面一致生效**：包括设置页（[cn.mocabolka.run.ui.components.SettingsPage]），
 * 仅提供左右两侧避让，不引入底部多余 padding。
 *
 * 该安全距离独立于设置页的挖孔屏开关（cutoutAdapt，仅控制摄像头缺口左右避让），始终生效。
 */
@Composable
fun Modifier.waterfallSafePadding(): Modifier {
    // 横屏跳过：一侧已有状态栏/导航栏占据，另一侧不挡阅读；强行留白纯属浪费。
    val configuration = LocalConfiguration.current
    if (configuration.screenWidthDp >= configuration.screenHeightDp) return this

    val density = LocalDensity.current
    val ltr = LayoutDirection.Ltr
    // 瀑布弯曲区物理对称，取 Ltr 下的 left/right 即为物理两侧安全距离（与界面 RTL 无关）。
    val cutout = WindowInsets.displayCutout
    val leftPx = cutout.getLeft(density, ltr)
    val rightPx = cutout.getRight(density, ltr)
    val fallbackPx = with(density) { 12.dp.roundToPx() }
    val startPx = max(leftPx, fallbackPx)
    val endPx = max(rightPx, fallbackPx)
    val startDp = with(density) { startPx.toDp() }
    val endDp = with(density) { endPx.toDp() }
    // 仅左右两侧避让；底部不介入，让页面贴边。
    return this.then(Modifier.padding(start = startDp, end = endDp))
}

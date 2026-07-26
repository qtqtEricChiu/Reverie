package cn.mocabolka.run.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 全屏统一边缘安全边距（瀑布屏 / 挖孔屏 / 横竖屏归一，2026-07-22 规范）。
 *
 * 规则：
 * - **绝不触碰上下**：上下均不加任何边距，状态栏 / 导航栏由系统自动适配（透明叠加）。
 * - **竖屏**：瀑布屏（曲面）取真实弯曲区 [WindowInsets.displayCutout] 左右 inset；无瀑布则回退到
 *   [Dimens.waterfallSafe] 固定值；二者取较大值，保证两侧 ≥ 安全余量且不浪费直屏空间。
 * - **横屏**：左右统一固定 [Dimens.waterfallSafe]；若 [cutoutAdapt] = true 再叠加 displayCutout
 *   左右（挖孔屏适配，绕开侧置 / 异形摄像头）。上下仍不加。
 *
 * ⚠️ 用法铁律：本 Modifier **只能作用在「内容容器」上**。外层根容器必须 `fillMaxSize()` 铺满全屏
 * （背景色 / 背景图延伸到瀑布区两侧，避免色块割裂）。切勿直接作用在带背景的根 Surface 上，
 * 否则会内缩整个盒子、露出底层 windowBackground —— 这正是此前关于页 / 权限页色块割裂的真因。
 */
@Composable
fun Modifier.edgeSafePadding(cutoutAdapt: Boolean = false): Modifier {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val ltr = LayoutDirection.Ltr
    val isLandscape = configuration.screenWidthDp >= configuration.screenHeightDp
    val cutout = WindowInsets.displayCutout
    val safePx = with(density) { Dimens.waterfallSafe.roundToPx() }
    return if (isLandscape) {
        val extraL = if (cutoutAdapt) cutout.getLeft(density, ltr) else 0
        val extraR = if (cutoutAdapt) cutout.getRight(density, ltr) else 0
        val start = with(density) { (safePx + extraL).toDp() }
        val end = with(density) { (safePx + extraR).toDp() }
        this.then(Modifier.padding(start = start, end = end))
    } else {
        val left = maxOf(cutout.getLeft(density, ltr), safePx)
        val right = maxOf(cutout.getRight(density, ltr), safePx)
        this.then(Modifier.padding(start = with(density) { left.toDp() }, end = with(density) { right.toDp() }))
    }
}

/**
 * 旧名兼容别名（指向 [edgeSafePadding]，cutoutAdapt 默认关）。新代码请直接用 [edgeSafePadding]。
 */
@Composable
@Deprecated("改用 edgeSafePadding(cutoutAdapt)，统一横竖屏与挖孔适配")
fun Modifier.waterfallSafePadding(): Modifier = edgeSafePadding()

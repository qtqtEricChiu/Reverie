package cn.mocabolka.run.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底部渐变遮罩：从透明到 [fadeColor]（[alpha]）的竖向渐变，用于内容底部压暗以保证文字可读性。
 * 取代散落在 GamePageLayouts / GameScreenshotsAll 两处的
 * `Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(0.7f))` 手写实现。
 *
 * @param fadeColor 渐变终点颜色（默认黑色）
 * @param alpha     终点不透明度（默认 0.7f，与原手写一致）
 * @param height    遮罩高度（默认 48.dp；传入 [Dp.Unspecified] 时由内容高度决定）
 * @param content   遮罩上方叠加的内容（如截图名、提示文案）
 */
@Composable
fun BottomFadeMask(
    modifier: Modifier = Modifier,
    fadeColor: Color = Color.Black,
    alpha: Float = 0.7f,
    height: Dp = 48.dp,
    content: @Composable (() -> Unit)? = null
) {
    val base = if (height == Dp.Unspecified) modifier else modifier.height(height)
    Box(
        base.fillMaxWidth().background(
            Brush.verticalGradient(0f to fadeColor.copy(alpha = 0f), 1f to fadeColor.copy(alpha = alpha))
        ),
        contentAlignment = Alignment.BottomStart
    ) {
        content?.invoke()
    }
}

package cn.mocabolka.run.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.SurfaceTokens
import kotlin.math.roundToInt

/**
 * 启动遮罩：圆形揭示（Circular Reveal）替代纯淡入。
 * 背景为低透明度的纯黑遮罩（GPU 缩放展开），中心以主题色径向柔光托起转圈与文案，
 * 取代原先生硬的半透明黑色圆盘。
 */
@Composable
fun LaunchOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    val reveal by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MotionSpec.Medium,
        label = "launchReveal"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = reveal
                    scaleY = reveal
                    alpha = if (visible) 1f else 0f
                }
                .background(SurfaceTokens.scrim())
        )
        if (visible) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer { alpha = reveal }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                0.7f to MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                1.0f to Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        "正在启动…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

/**
 * 启动加载骨架屏（替代纯转圈）：图标占位 + 三行流光骨架条（shimmer）。
 * 流光通过 [Modifier.graphicsLayer] 平移的白色高光实现，GPU 合成，高帧率。
 * [reduceMotion] 时关闭流光，仅保留静态骨架。
 */
@Composable
fun ShimmerLoading(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ShimmerChip(Modifier.size(76.dp), reduceMotion, CircleShape)
        Spacer(Modifier.height(Dimens.lg))
        ShimmerChip(Modifier.size(width = 220.dp, height = 14.dp), reduceMotion)
        Spacer(Modifier.height(Dimens.xs))
        ShimmerChip(Modifier.size(width = 160.dp, height = 12.dp), reduceMotion)
        Spacer(Modifier.height(Dimens.xs))
        ShimmerChip(Modifier.size(width = 120.dp, height = 12.dp), reduceMotion)
    }
}

@Composable
private fun ShimmerChip(
    modifier: Modifier,
    reduceMotion: Boolean,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    val density = LocalDensity.current
    val shift = with(density) { 90.dp.toPx() }
    val inf = rememberInfiniteTransition(label = "shimmer")
    val x by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart)
    )
    val off = if (reduceMotion) Offset.Zero else Offset((x * shift * 2 - shift), 0f)
    Box(
        modifier
            .clip(shape)
            .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }
                .graphicsLayer { alpha = if (reduceMotion) 0.0f else 0.55f }
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.55f),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )
    }
}

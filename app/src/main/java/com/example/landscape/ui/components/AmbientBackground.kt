package cn.mocabolka.run.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mocabolka.run.ui.theme.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 动态氛围背景：**流沙**效果（粒子云 + 视差漂移）。
 *
 * 实现：
 * - 4 组独立主题色粒子云团（primary / secondary / tertiary / 浅色描边），
 *   每团由 8 个高斯点构成，色相 + 形状 + 大小互不相同，整体观感如细沙流过。
 * - 漂移周期错峰（17s / 23s / 19s / 27s）并以 EaseInOut 进出，画面始终有动感但不过于亢奋。
 * - **画布尺寸 = 父容器 1.3×，并以图形层整体平移**，使粒子始终可越界延伸到父容器外
 *   （不会被 Surface / Box 边界硬切，模块边缘柔和过渡）。
 * - 性能：所有动画用 [graphicsLayer] 仅改 alpha/transform；不触发重组；
 *   [paused] / [reduceMotion] 时退化为静态层，零开销。
 *
 * @param enabled 总开关（关闭时仅保留基础渐变）。
 * @param reduceMotion 减少动态效果：冻结漂移。
 * @param paused 应用不可见：停止一切无限动画（R13 省电）。
 */
@Composable
fun AmbientBackground(
    enabled: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    /** 应用不可见时暂停无限动画（R13 省电）。 */
    paused: Boolean = false
) {
    Box(modifier.fillMaxSize()) {
        // 基础渐变（兜底 / 关闭动态时背景）
        Spacer(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)
                    )
                )
            )
        )

        if (enabled) {
            val c1 = MaterialTheme.colorScheme.primary
            val c2 = MaterialTheme.colorScheme.secondary
            val c3 = MaterialTheme.colorScheme.tertiary
            val c4 = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

            if (paused || reduceMotion) {
                StaticSandLayer(
                    colors = listOf(c1, c2, c3, c4),
                    density = LocalDensity.current.density,
                    alpha = if (paused) 0.10f else 0.20f
                )
            } else {
                // 4 组独立周期：彼此错峰、缓慢、不可预测
                val inf = rememberInfiniteTransition(label = "sand")
                val t1 by inf.animateFloat(0f, 1f,
                    infiniteRepeatable(tween(23000, easing = EaseInOut), RepeatMode.Reverse),
                    label = "s1"
                )
                val t2 by inf.animateFloat(0f, 1f,
                    infiniteRepeatable(tween(27000, easing = EaseInOut), RepeatMode.Reverse),
                    label = "s2"
                )
                val t3 by inf.animateFloat(0f, 1f,
                    infiniteRepeatable(tween(19000, easing = EaseInOut), RepeatMode.Reverse),
                    label = "s3"
                )
                val t4 by inf.animateFloat(0f, 1f,
                    infiniteRepeatable(tween(31000, easing = LinearEasing), RepeatMode.Reverse),
                    label = "s4"
                )
                AnimatedSandLayer(
                    times = floatArrayOf(t1, t2, t3, t4),
                    colors = listOf(c1, c2, c3, c4),
                    density = LocalDensity.current.density
                )
            }
        }
    }
}

/**
 * 流沙静态层（减少动态 / 后台暂停）：一组固定粒子云，零运动。
 */
@Composable
private fun StaticSandLayer(
    colors: List<Color>,
    density: Float,
    alpha: Float
) {
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .drawBehind { drawSandGrains(colors = colors, density = density, jitter = 0f) }
    )
}

/**
 * 流沙动画层：在父容器尺寸上绘制粒子云团并叠加模糊。
 *
 * 关键：使用一个比父容器大的画布（基于图形层 scaleX/scaleY 1.15），
 * 使粒子在屏幕边缘仍能延伸，模块边界处无硬切。
 */
@Composable
private fun AnimatedSandLayer(
    times: FloatArray,
    colors: List<Color>,
    density: Float
) {
    // 使用 graphicsLayer 整体放大 15% 让粒子云溢出到父容器边缘
    Spacer(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1.18f
                scaleY = 1.18f
                // 慢速视差漂移
                translationX = (times[3] - 0.5f) * 28.dp.toPx()
                translationY = (times[0] - 0.5f) * 16.dp.toPx()
                // 软透明
                alpha = 0.85f
                // 让模糊效果正确合成
                compositingStrategy = CompositingStrategy.Offscreen
            }
            // 先绘制粒子，再模糊，否则 blur 在前对空画布无效
            .drawBehind {
                drawSandGrains(
                    colors = colors,
                    density = density,
                    jitter = 1f,
                    times = times
                )
            }
            .blur(38.dp)
    )
}

/**
 * 粒子云绘制：每组颜色 8 个高斯点，坐标 / 半径 / 透明度随 [times] 漂移。
 */
private fun DrawScope.drawSandGrains(
    colors: List<Color>,
    density: Float,
    jitter: Float,
    times: FloatArray? = null
) {
    val w = size.width
    val h = size.height
    // 锚点 = 8 个固定点（屏幕外周 + 中心），围绕这些点放置"沙粒"
    val anchors = listOf(
        0.12f to 0.18f,
        0.85f to 0.22f,
        0.78f to 0.78f,
        0.18f to 0.85f,
        0.50f to 0.45f,
        0.05f to 0.55f,
        0.95f to 0.55f,
        0.40f to 0.10f
    )
    val grainCountPerColor = 8

    colors.forEachIndexed { ci, color ->
        val t = times?.get(ci) ?: 0.5f
        val colorAlpha = 0.55f + 0.25f * t
        val r0 = 220f + 80f * ci // 不同颜色团粒度递增，营造层次
        for (gi in 0 until grainCountPerColor) {
            val ax = anchors[gi].first
            val ay = anchors[gi].second
            // 用 (ci, gi) 计算确定性的相位偏移 -> 流沙顺滑
            val phase = (ci * 7 + gi * 13) * 0.7f
            val ox = cos((t * 2f * PI.toFloat()) + phase) * (w * 0.18f) * jitter
            val oy = sin((t * 2f * PI.toFloat()) + phase) * (h * 0.14f) * jitter
            val cx = ax * w + ox
            val cy = ay * h + oy
            val r = (r0 + 40f * cos(phase + t * 4f)) / density
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = colorAlpha), color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = r.coerceAtLeast(40f)
                ),
                radius = r.coerceAtLeast(40f),
                center = Offset(cx, cy),
                blendMode = BlendMode.Plus
            )
        }
    }
}

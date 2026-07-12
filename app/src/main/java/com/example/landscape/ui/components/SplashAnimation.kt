package cn.mocabolka.run.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mocabolka.run.R

/**
 * 全屏沉浸式开屏动画：覆盖状态栏与挖孔区，Logo 呼吸光晕 + 脉冲缩放 + 三圆点 loading 动画。
 *
 * 动画序列：
 * 1. Logo 保持显示期间持续微弱呼吸脉冲（1.00 ↔ 1.04 缩放）。
 * 2. Logo 底部散发径向柔光晕（主题色），与脉冲同步脉动。
 * 3. 品牌文字 "Reverie" 淡入显示。
 * 4. 底部三个圆点从左到右依次呼吸闪烁（loading 指示器），替换旧的"launcher"占位文字。
 * 5. [visible] 为 false 时整体淡出。
 *
 * 所有动画走 [graphicsLayer] 零重组。
 * [reduceMotion] 时退化为静态 Logo + 文字，零开销。
 */
@Composable
fun SplashOverlay(
    visible: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (reduceMotion) {
                // 减少动效时：静态 Logo + 文字
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Reverie",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    // 静态三圆点
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            } else {
                SplashAnimContent()
            }
        }
    }
}

@Composable
private fun SplashAnimContent() {
    val inf = rememberInfiniteTransition(label = "splashPulse")
    // Logo 脉冲缩放：1.00 ↔ 1.04，周期 2000ms
    val pulseScale by inf.animateFloat(
        initialValue = 1.00f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashScale"
    )
    // 光晕 alpha：0.15 ↔ 0.35，与缩放同步
    val glowAlpha by inf.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashGlow"
    )
    // 品牌文字 alpha：0.7 ↔ 1.0 缓慢呼吸
    val textAlpha by inf.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashText"
    )
    // 三圆点 loading 动画：每个圆点按顺序呼吸，形成"扫描"效果
    val dot1Alpha by inf.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashDot1"
    )
    val dot2Alpha by inf.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashDot2"
    )
    val dot3Alpha by inf.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashDot3"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo 容器：光晕 + 图标
        Box(contentAlignment = Alignment.Center) {
            // 径向光晕
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer { alpha = glowAlpha }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                0.5f to MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                1.0f to Color.Transparent
                            )
                        )
                    )
            )
            // Logo 图标（带脉冲缩放）
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale)
            )
        }
        Spacer(Modifier.height(24.dp))
        // 品牌文字
        Text(
            "Reverie",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha)
        )
        Spacer(Modifier.height(20.dp))
        // 三圆点 loading 指示器（替换 "launcher" 占位文字）
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotMod = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
            Box(modifier = dotMod.graphicsLayer { alpha = dot1Alpha }
                .background(MaterialTheme.colorScheme.primary))
            Box(modifier = dotMod.graphicsLayer { alpha = dot2Alpha }
                .background(MaterialTheme.colorScheme.primary))
            Box(modifier = dotMod.graphicsLayer { alpha = dot3Alpha }
                .background(MaterialTheme.colorScheme.primary))
        }
    }
}

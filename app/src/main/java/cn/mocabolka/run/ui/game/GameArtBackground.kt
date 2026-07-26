package cn.mocabolka.run.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * 游戏美术 4 层背景（与游戏页完全一致）：
 *   ① page_bg_raw 模糊底图（无图时回退 accent 渐变）
 *   ② 竖向暗化遮罩
 *   ③ accent 染色层
 *   ④ 顶部 96dp 压暗（保证顶栏图标在明亮 hero 上的可读性）
 *
 * 游戏页、截图"查看全部"页、SteamDB 美术各子页面共用此组件，
 * 确保背景视觉语言统一（原 SteamArtBackground，现归并到 ui/game 以体现其通用性）。
 */
@Composable
fun GameArtBackground(
    art: GameArt?,
    accent: Color,
    modifier: Modifier = Modifier,
    /** 预载背景 bitmap（切游戏时由 Activity 同步注入），命中则直接采用，跳过异步解码一帧延迟，消解背景硬切。 */
    preloadedBitmap: ImageBitmap? = null
) {
    val bgBmp = preloadedBitmap ?: rememberArtBitmap(art?.background?.source)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        if (bgBmp != null) {
            Image(
                bitmap = bgBmp,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(30.dp)
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Black))
                )
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.25f),
                    0.55f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.92f)
                )
            )
        )
        Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.10f)))
        Box(
            Modifier.fillMaxWidth().height(96.dp)
                .background(Brush.verticalGradient(0f to Color.Black.copy(0.45f), 1f to Color.Transparent))
        )
    }
}

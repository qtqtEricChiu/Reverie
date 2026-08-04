package cn.mocabolka.run.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.ui.theme.LocalAmoledPureBlack

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
    preloadedBitmap: ImageBitmap? = null,
    /**
     * 无美术时的主题化回退（游戏页专用，2026-07-28 统一规定）：
     * true 且无背景图 → 浅色主题用「白色→强调色」竖向渐变、深色/AMOLED 相反（「黑色→强调色」），
     * 且**不叠任何压暗遮罩**（遮罩只为压暗美术图而存在）；字色由调用方按主题深浅适配。
     * false（默认，SteamDB 美术各子页 / 截图页沿用）→ 旧行为：accent→纯黑渐变 + 全套压暗层。
     */
    themedNoArtFallback: Boolean = false
) {
    val bgBmp = preloadedBitmap ?: rememberArtBitmap(art?.background?.source)
    // AMOLED 纯黑模式：不含美术资源（art == null 且无预载背景）时直接纯黑，
    // 隐藏全部渐变与压暗浮层。仅作用于无美术场景；有美术页保持原有深色 + 压暗遮罩（铁律不破坏）。
    if (LocalAmoledPureBlack.current && art == null && bgBmp == null) {
        Box(modifier.fillMaxSize().background(Color.Black))
        return
    }
    // 主题化回退分支：仅「完全无美术资源」（art == null，与游戏页 lightContent 判定同源）时生效。
    // 有美术但背景图尚未解码完的一帧仍走旧暗色回退，避免白底闪现与白字冲突。
    if (themedNoArtFallback && art == null && bgBmp == null) {
        // 按当前主题实际背景明度判深浅（兼容 AMOLED 纯黑 / 莫奈动态色）。
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val base = if (dark) Color.Black else Color.White
        Box(
            modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(base, accent))
            )
        )
        return
    }
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

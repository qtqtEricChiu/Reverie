package cn.mocabolka.run.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * GamePage 专属单色系工具。
 * 设计原则：游戏 accent 是本页"主题色"，与全局 Monet 取色解耦；
 * 所有可强调表面都用 accent 的去饱和/透明变体，构成单色系视觉。
 */
object GamePageColors {
    /** 焦点环 / 文字对比色：accent 偏亮（如黄）→ 黑；偏暗（如青）→ 白。 */
    fun onAccent(accent: Color): Color =
        if (accent.luminance() > 0.5f) Color.Black else Color.White

    /** accent 的去饱和透明变体（单色系背景 / 分隔条 / 信号条用）。 */
    fun tonal(accent: Color, alpha: Float): Color = accent.copy(alpha = alpha)
}

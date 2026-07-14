package cn.mocabolka.run.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Android 莫奈（Monet）动态取色支持（minSdk = API 36 始终可用）。
 *
 * - 基于系统壁纸提取色板，生成 Material You 配色
 * - 仅在系统动态取色抛异常时回退到 Reverie 默认品牌配色（BrandColors 紫罗兰调色板）
 *
 * 行为：
 * - darkTheme = true：使用动态深色或 Reverie 深色
 * - darkTheme = false：使用动态浅色或 Reverie 浅色
 * - 动态取色不可用时，无缝回退到 [LandscapeTheme] 的默认品牌色板（BrandColors）
 */
object MonetTheme {
    /**
     * 获取当前应使用的 ColorScheme。
     * @param darkTheme 是否深色模式
     * @param forceMonet 强制使用莫奈（系统动态取色不可用时回退到默认色板）
     * @param fallbackLight 浅色回退色板
     * @param fallbackDark 深色回退色板
     */
    @Composable
    fun colorScheme(
        darkTheme: Boolean,
        forceMonet: Boolean = true,
        fallbackLight: ColorScheme = lightColorScheme(),
        fallbackDark: ColorScheme = darkColorScheme()
    ): ColorScheme {
        if (!forceMonet) return if (darkTheme) fallbackDark else fallbackLight
        // minSdk = 36 始终支持 Material You 动态取色
        val ctx = LocalContext.current
        return try {
            if (darkTheme) dynamicDarkColorScheme(ctx)
            else dynamicLightColorScheme(ctx)
        } catch (t: Throwable) {
            // 某些设备上 dynamicColorScheme 可能抛异常，回退
            if (darkTheme) fallbackDark else fallbackLight
        }
    }
}

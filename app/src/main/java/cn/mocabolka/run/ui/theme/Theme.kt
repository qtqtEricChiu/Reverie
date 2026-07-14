package cn.mocabolka.run.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import cn.mocabolka.run.gamepad.GamepadDetector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── 品牌默认配色（Material Design 3 紫罗兰调色板）──────────
// 设计规范：M3 Baseline Palette（#6750A4 紫罗兰主色），WCAG AA 对比度达标。
// 用途：作为莫奈动态取色不可用时的默认回退色板（见 reverieColorScheme → MonetTheme）。
// 注意：莫奈取色保持现状不变，仅替换默认（非莫奈）配色。
object BrandColors {
    // ── 浅色主题 ──
    object Light {
        val primary = Color(0xFF6750A4)
        val onPrimary = Color(0xFFFFFFFF)
        val primaryContainer = Color(0xFFEADDFF)
        val onPrimaryContainer = Color(0xFF21005D)

        val secondary = Color(0xFF625B71)
        val onSecondary = Color(0xFFFFFFFF)
        val secondaryContainer = Color(0xFFE8DEF8)
        val onSecondaryContainer = Color(0xFF1D192B)

        val tertiary = Color(0xFF7E5260)
        val onTertiary = Color(0xFFFFFFFF)
        val tertiaryContainer = Color(0xFFFFD8E4)
        val onTertiaryContainer = Color(0xFF31101D)

        val error = Color(0xFFB3261E)
        val onError = Color(0xFFFFFFFF)
        val errorContainer = Color(0xFFF9DEDC)
        val onErrorContainer = Color(0xFF410E0B)

        val background = Color(0xFFFFFBFE)
        val onBackground = Color(0xFF1C1B1F)
        val surface = Color(0xFFFFFBFE)
        val onSurface = Color(0xFF1C1B1F)
        val surfaceVariant = Color(0xFFE7E0EC)
        val onSurfaceVariant = Color(0xFF49454F)

        val outline = Color(0xFF79747E)
        val outlineVariant = Color(0xFFCAC4D0)
    }

    // ── 暗黑主题 ──
    object Dark {
        val primary = Color(0xFFD0BCFF)
        val onPrimary = Color(0xFF381E72)
        val primaryContainer = Color(0xFF4F378B)
        val onPrimaryContainer = Color(0xFFEADDFF)

        val secondary = Color(0xFFCCC2DC)
        val onSecondary = Color(0xFF332D41)
        val secondaryContainer = Color(0xFF4A4458)
        val onSecondaryContainer = Color(0xFFE8DEF8)

        val tertiary = Color(0xFFEFB8C8)
        val onTertiary = Color(0xFF492532)
        val tertiaryContainer = Color(0xFF633B48)
        val onTertiaryContainer = Color(0xFFFFD8E4)

        val error = Color(0xFFF2B8B5)
        val onError = Color(0xFF601410)
        val errorContainer = Color(0xFF8C1D18)
        val onErrorContainer = Color(0xFFF9DEDC)

        val background = Color(0xFF1C1B1F)
        val onBackground = Color(0xFFE6E1E5)
        val surface = Color(0xFF1C1B1F)
        val onSurface = Color(0xFFE6E1E5)
        val surfaceVariant = Color(0xFF49454F)
        val onSurfaceVariant = Color(0xFFCAC4D0)

        val outline = Color(0xFF938F99)
        val outlineVariant = Color(0xFF49454F)
    }
}

// ─── 语义色 ────────────────────────────────────────────────
val Success = Color(0xFF4ADE80)
val Warning = Color(0xFFFBBF24)
val Info = Color(0xFF38BDF8)

// ─── 图表 & 趋势色（StatsScreen 专用）─────────────────────
/** 图表强调色：粉色系，用于柱状图高亮/虚线。 */
val ChartAccent = Color(0xFFEC4899)
/** 图表常规柱色：灰色系。 */
val ChartNormal = Color(0xFF6B7280)
/** 趋势上涨色：翠绿。 */
val TrendUp = Color(0xFF10B981)
/** 趋势下跌色：红色。 */
val TrendDown = Color(0xFFEF4444)

// ─── 通用色 ────────────────────────────────────────────────
/** 收藏星标色：金色。 */
val FavoriteStar = Color(0xFFFFD54F)
/** 占位图标色：深灰色（HomeViewModel/AppRepository/RecentsRepository 共享）。 */
val PlaceholderIcon = Color(0xFF374151)

// ─── 浅色色板（品牌默认配色：M3 紫罗兰，作为莫奈回退）──────
private val LightColorScheme = lightColorScheme(
    primary = BrandColors.Light.primary,
    onPrimary = BrandColors.Light.onPrimary,
    primaryContainer = BrandColors.Light.primaryContainer,
    onPrimaryContainer = BrandColors.Light.onPrimaryContainer,
    secondary = BrandColors.Light.secondary,
    onSecondary = BrandColors.Light.onSecondary,
    secondaryContainer = BrandColors.Light.secondaryContainer,
    onSecondaryContainer = BrandColors.Light.onSecondaryContainer,
    tertiary = BrandColors.Light.tertiary,
    onTertiary = BrandColors.Light.onTertiary,
    tertiaryContainer = BrandColors.Light.tertiaryContainer,
    onTertiaryContainer = BrandColors.Light.onTertiaryContainer,
    error = BrandColors.Light.error,
    onError = BrandColors.Light.onError,
    errorContainer = BrandColors.Light.errorContainer,
    onErrorContainer = BrandColors.Light.onErrorContainer,
    background = BrandColors.Light.background,
    onBackground = BrandColors.Light.onBackground,
    surface = BrandColors.Light.surface,
    onSurface = BrandColors.Light.onSurface,
    surfaceVariant = BrandColors.Light.surfaceVariant,
    onSurfaceVariant = BrandColors.Light.onSurfaceVariant,
    surfaceContainerHighest = Color(0xFFE7E0EC),
    outline = BrandColors.Light.outline,
    outlineVariant = BrandColors.Light.outlineVariant,
    inverseSurface = Color(0xFF1C1B1F),
    inverseOnSurface = Color(0xFFE6E1E5),
    surfaceTint = BrandColors.Light.primary,
    scrim = Color.Black.copy(alpha = MotionSpec.ScrimAlpha)
)

// ─── 深色色板（品牌默认配色：M3 紫罗兰，作为莫奈回退）──────
// 表面层级：M3 深色标准背景 #1C1B1F，surface 沿用 background，
// surfaceVariant / surfaceContainerHighest 按 M3 层级递进提亮，保证卡片/浮层可读性。
private val DarkColorScheme = darkColorScheme(
    primary = BrandColors.Dark.primary,
    onPrimary = BrandColors.Dark.onPrimary,
    primaryContainer = BrandColors.Dark.primaryContainer,
    onPrimaryContainer = BrandColors.Dark.onPrimaryContainer,

    secondary = BrandColors.Dark.secondary,
    onSecondary = BrandColors.Dark.onSecondary,
    secondaryContainer = BrandColors.Dark.secondaryContainer,
    onSecondaryContainer = BrandColors.Dark.onSecondaryContainer,

    tertiary = BrandColors.Dark.tertiary,
    onTertiary = BrandColors.Dark.onTertiary,
    tertiaryContainer = BrandColors.Dark.tertiaryContainer,
    onTertiaryContainer = BrandColors.Dark.onTertiaryContainer,

    error = BrandColors.Dark.error,
    onError = BrandColors.Dark.onError,
    errorContainer = BrandColors.Dark.errorContainer,
    onErrorContainer = BrandColors.Dark.onErrorContainer,

    background = BrandColors.Dark.background,
    onBackground = BrandColors.Dark.onBackground,
    surface = BrandColors.Dark.surface,
    onSurface = BrandColors.Dark.onSurface,
    surfaceVariant = BrandColors.Dark.surfaceVariant,
    onSurfaceVariant = BrandColors.Dark.onSurfaceVariant,
    surfaceContainerHighest = Color(0xFF49454F),
    outline = BrandColors.Dark.outline,
    outlineVariant = BrandColors.Dark.outlineVariant,

    inverseSurface = BrandColors.Dark.onBackground,
    inverseOnSurface = BrandColors.Dark.background,

    surfaceTint = BrandColors.Dark.primary,
    scrim = Color.Black.copy(alpha = MotionSpec.ScrimAlpha)
)

// ─── TV/大屏专用排版 ──────────────────────────────────────
/**
 * 基于 Material3 默认 Typography 调整了所有字号，
 * 适配 TV/大屏 2-3m 观看距离：
 * - bodySmall 从 12sp → 13sp
 * - 标题和正文整体放大 15-30%
 * - lineHeight 相应增加保证可读性
 */
private val TvTypography = Typography(
    // 标题层级
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),

    // 标题
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),

    // 正文
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),

    // 标签
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

// ─── 形状系统（Material3 圆角规范，大屏加大圆角）──────────
private val TvShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun reverieColorScheme(
    darkTheme: Boolean,
    useMonet: Boolean,
    isAmoled: Boolean
): ColorScheme {
    val baseScheme = MonetTheme.colorScheme(
        darkTheme = darkTheme,
        forceMonet = useMonet,
        fallbackLight = LightColorScheme,
        fallbackDark = DarkColorScheme
    )
    return when {
        // AMOLED：无论是否莫奈，背景/表面强制纯黑以省电；强调色保留（莫奈或回退）
        isAmoled -> baseScheme.copy(
            background = Color.Black,
            onBackground = baseScheme.onBackground,
            surface = Color.Black,
            onSurface = baseScheme.onSurface,
            surfaceVariant = baseScheme.surfaceVariant.copy(alpha = 0.15f),
            onSurfaceVariant = baseScheme.onSurfaceVariant,
            surfaceContainerHighest = Color(0xFF111111),
            scrim = Color.Black.copy(alpha = MotionSpec.ScrimAlpha)
        )
        // 深色 + 开启莫奈：保留 dynamicDarkColorScheme 生成的表面色（背景/表面跟随壁纸），
        // 不再用硬编码 DarkColorScheme 覆盖 —— 否则莫奈取色在深色下"只有强调色生效、背景无效"。
        darkTheme && useMonet -> baseScheme
        // 深色 + 关闭莫奈：回退到 Reverie 默认深色色板（BrandColors 紫罗兰深色）
        darkTheme -> DarkColorScheme
        // 浅色：直接用 baseScheme（莫奈或回退浅色色板）
        else -> baseScheme
    }
}

@Composable
fun LandscapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 是否启用 Material You 莫奈取色（API 31+）。 */
    useMonet: Boolean = true,
    /** 是否启用 AMOLED 纯黑模式（背景使用 #000000 以省电）。 */
    isAmoled: Boolean = false,
    /**
     * 手柄是否连接。传入非 null 时使用调用方提供的实时值；
     * 省略（null）时由主题内部根据 [GamepadDetector] 自行计算。
     * 该值驱动 [LocalShowFocusIndicators] —— 未连接手柄时不显示任何焦点框。
     */
    gamepadConnected: Boolean? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = reverieColorScheme(darkTheme, useMonet, isAmoled)
    val ctx = LocalContext.current
    // 焦点框可见性：手柄未连接 / 纯触控模式下关闭，避免无意义的焦点高亮。
    val showFocus = gamepadConnected
        ?: GamepadDetector.gamepadConnectedFlow(ctx)
            .collectAsState(initial = GamepadDetector.isGamepadConnected()).value
    CompositionLocalProvider(LocalShowFocusIndicators provides showFocus) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TvTypography,
            shapes = TvShapes,
            content = content
        )
    }
}

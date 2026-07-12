package cn.mocabolka.run.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

// ─── 浅色色板 ──────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = Color(0xFF0369A1),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBAE6FD),
    onTertiaryContainer = Color(0xFF0C4A6E),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerHighest = Color(0xFFF1F5F9),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    surfaceTint = Color(0xFF0F766E),
    scrim = Color.Black.copy(alpha = 0.6f)
)

// ─── 深色色板（控制台/大屏风格）─────────────────────────────
private val DarkColorScheme = darkColorScheme(
    // 主色系：霓虹青
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF155E75),
    onPrimaryContainer = Color(0xFFA5F3FC),

    // 辅色系：紫色
    secondary = Color(0xFFA855F7),
    onSecondary = Color(0xFF2A0A4A),
    secondaryContainer = Color(0xFF581C87),
    onSecondaryContainer = Color(0xFFE9D5FF),

    // 第三色系
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF0C4A6E),
    tertiaryContainer = Color(0xFF0E7490),
    onTertiaryContainer = Color(0xFFBAE6FD),

    // 语义色
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),

    // 表面层级（亮度由低到高）
    background = Color(0xFF0B0F1A),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1E293B),            // 比 background 亮 ~12%
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF334155),     // 中间层级
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceContainerHighest = Color(0xFF475569),  // 最高级表面（卡片背景）

    // 轮廓
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),

    // 反色
    inverseSurface = Color(0xFFE5E7EB),
    inverseOnSurface = Color(0xFF0F172A),

    // 杂项
    surfaceTint = Color(0xFF22D3EE),
    scrim = Color.Black.copy(alpha = 0.6f)
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
fun LandscapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 是否启用 Material You 莫奈取色（API 31+）。 */
    useMonet: Boolean = true,
    /** 是否启用 AMOLED 纯黑模式（背景使用 #000000 以省电）。 */
    isAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseScheme = MonetTheme.colorScheme(
        darkTheme = darkTheme,
        forceMonet = useMonet,
        fallbackLight = LightColorScheme,
        fallbackDark = DarkColorScheme
    )
    val colorScheme = if (isAmoled && darkTheme) {
        // AMOLED 模式：将所有表面/背景色强制设为纯黑
        baseScheme.copy(
            background = Color.Black,
            onBackground = baseScheme.onBackground,
            surface = Color.Black,
            onSurface = baseScheme.onSurface,
            surfaceVariant = baseScheme.surfaceVariant.copy(alpha = 0.15f),
            onSurfaceVariant = baseScheme.onSurfaceVariant,
            surfaceContainerHighest = Color(0xFF111111),
            scrim = Color.Black.copy(alpha = 0.6f)
        )
    } else {
        baseScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TvTypography,
        shapes = TvShapes,
        content = content
    )
}

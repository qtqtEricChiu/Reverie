package cn.mocabolka.run.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一间距与尺寸系统 — 4dp 栅格规范。
 * 所有屏幕均通过此对象引用间距值，确保全局一致性。
 */
object Dimens {
    // 基础栅格单位
    const val Grid = 4f

    // 基本间距（4dp 栅格）
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp

    // 屏幕级
    val ScreenPadding = 24.dp           // 所有屏幕统一外边距
    val SectionSpacing = 16.dp          // 区块间距
    val ItemSpacing = 12.dp             // 组件内元素间距
    val CardInnerPadding = 16.dp        // 卡片内边距

    // AppTile（最大尺寸；实际按可用空间自适应 60dp ~ 该值）
    val TileSize = 96.dp
    val TileGameSize = 130.dp
    val TileGameGridSize = 100.dp
    val TileCornerRadius = 14.dp
    val TileIconRatio = 0.62f

    // StatusBar
    val StatusBarVertical = 0.dp
    val StatusBarElevation = 2.dp

    // 网格
    val GridContentPadding = 12.dp
    val GridItemSpacing = 12.dp

    // 焦点环
    val FocusBorderWidth = 1.dp
    val FocusBorderWidthSelected = 3.dp
    val FocusGlowRadius = 10.dp
    val FocusGlowSpreadRadius = 18.dp

    // Dock 收藏栏
    val DockTileSize = 64.dp
    val DetailPanelWidth = 300.dp

    // 横屏列表
    val ListItemRowHeight = 72.dp

    // ── 标准圆角（4dp 栅格，补充 MaterialTheme.shapes）──
    val RadiusXxs = 2.dp
    val RadiusXs = 4.dp
    val RadiusSm = 6.dp
    val RadiusMd = 8.dp
    val RadiusLg = 10.dp
    val RadiusXl = 12.dp
    val RadiusXxl = 14.dp
    val RadiusRound = 28.dp     // Toast / 气泡大圆角
    val RadiusCircle = 999.dp   // 圆形遮罩

    // ── 标准图标尺寸 ──
    val IconXxs = 12.dp
    val IconXs = 14.dp
    val IconSm = 16.dp
    val IconMd = 20.dp
    val IconLg = 24.dp
    val IconXl = 28.dp
    val IconXxl = 32.dp
    val IconAvatar = 36.dp      // 头像/图标方块尺寸
    val IconBadge = 40.dp       // 键位浮块 / 角标

    // ── 标准组件尺寸 ──
    val ButtonHeight = 40.dp
    val ProgressBarHeight = 4.dp
    val DividerHeight = 1.dp
    val DragHandleWidth = 40.dp
    val DragHandleHeight = 4.dp

    // ── 弹窗 ──
    val DialogMaxWidth = 460.dp
    val DialogDatePickerWidth = 560.dp
    val DialogCloseButtonSize = 28.dp
}

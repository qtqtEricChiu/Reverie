package cn.mocabolka.run.ui.theme

import android.content.Context
import android.content.res.Configuration
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
    /** 焦点态背景主色填充透明度（列表 / 卡片统一），替代散落的 0.16 / 0.22 魔法值。 */
    val FocusSurfaceAlpha = 0.20f

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
    val DialogCloseButtonSize = 28.dp
    // 统一弹窗容器参数（DialogScaffold 默认值，消除散落的魔法值）
    val DialogElevation = 6.dp          // Surface tonalElevation（景深投影）
    val DialogWidthFraction = 0.92f     // 屏幕宽度占比（widthIn 上限内的 fillMaxWidth）
    val DialogListMaxHeight = 360.dp    // 下拉列表最大高度

    // StatsScreen 宽屏判定阈值（screenWidthDp >= 此值时启用双栏布局）；统一委托 [DeviceProfile.WIDE_SCREEN_DP]。
    const val WideScreenThresholdDp = DeviceProfile.WIDE_SCREEN_DP

    // StatsScreen 左栏（应用排行）面板宽度
    val RightPanelWidth = 280.dp

    // 窄屏判定阈值（screenWidthDp < 此值时指标卡改为 2 列网格）；统一委托 [DeviceProfile.NARROW_SCREEN_DP]。
    const val NarrowScreenThresholdDp = DeviceProfile.NARROW_SCREEN_DP

    // ── GamePage 断点尺寸（2026-07-15 GamePage UI 改进方案，第八/七章）──
    // Hero 横幅高度（按断点递增）
    val HeroCompact = 180.dp
    val HeroMedium = 220.dp
    val HeroCompactLand = 240.dp
    val HeroMediumLand = 280.dp
    val HeroExpanded = 360.dp
    val HeroXL = 420.dp

    // Capsule 竖版海报宽度（按断点递增；CompactPortrait = 0 即收起）
    val CapsuleMediumPortrait = 130.dp
    val CapsuleCompactLand = 180.dp
    val CapsuleMediumLand = 220.dp
    val CapsuleExpanded = 220.dp
    val CapsuleXL = 280.dp

    // 启动按钮高度
    val LaunchButtonHeight = 56.dp
    val LaunchButtonHeightExpanded = 60.dp
    val LaunchButtonHeightXL = 64.dp

    // 电视大屏焦点环（ExtraLargeLandscape 专用，避免 1-2dp 偏细）
    val FocusBorderWidthXL = 4.dp
    val FocusGlowRadiusXL = 16.dp

    /**
     * 断点化屏幕外边距（方案第七章）：对齐 MD3 WindowSizeClass。
     * - < 1600dp：24.dp（Compact/Medium/Expanded 统一）
     * - ≥ 1600dp（TV）：48.dp，远观需宽边距
     * 保留 [ScreenPadding]=24.dp 语义不变，新增此查询以适配电视大屏。
     */
    fun screenPaddingFor(widthDp: Int): Dp = if (widthDp >= DeviceProfile.TV_MIN_DP) 48.dp else ScreenPadding

    // 瀑布屏（曲面屏）两侧最小安全余量：叠加在 displayCutout 左右 inset 之上，
    // 保证曲面边缘不被光学扭曲、避免边缘误触。普通直屏设备 cutout 为 0，仅留此值。
    // 仅在竖屏生效（横屏两侧曲面已被系统状态栏/导航栏优先占据，无需二次适配）。
    // 用户指定基准为 4dp。
    val waterfallSafe = 4.dp

    // ── 标准化页面模板（2026-07-13 专项：四页统一基线）──
    // 全屏统一水平外边距：所有页面根容器统一引用，消除左右边距漂移。
    val ContentHorizontal = 16.dp
    // 归一化标题栏标准高度（基准 = 权限页子页面标题栏）：所有子页面统一引用，消除高度漂移。
    val SubPageTopBarHeight = 56.dp
    // 标题栏（StatusBar）到页面内容顶部的标准净距（不含 StatusBar 自身 xxs padding）。
    val HeaderToContent = 16.dp
    // 底部按键指示栏标准高度（横屏模式下底部外边距强制为 0，栏自身高度占位）。
    val HintBarHeight = 48.dp
    // 底部按键指示栏与上方内容的标准分隔线高度（仅细线，无色块）。
    val HintBarDivider = 1.dp
    // 提示行内「键位浮块 → 功能描述」的间距（6dp 兼顾紧凑与可读）。
    val HintItemGap = 6.dp
    // 单个提示项内「键位浮块 → 文本」的固定间距（4dp）：此前 Hint 项内无间距导致各栏键位贴死不齐，
    // 现统一加此 gap，与 HintItemGap（项间）分离，保证所有底部提示栏键位-文本间隙一致。
    val HintBadgeGap = 4.dp
    // 提示行内「组与组」之间的间隔（16dp，与 Dimens.lg 一致，组内紧、组间松）。
    val HintGroupGap = 16.dp
    // 横屏模式下所有页面底部边距强制为 0（由 BoxWithConstraints 判定后应用）。
    // 此处仅作语义常量，实际在布局中用 isWideScreen 触发。
    val WideBottomPadding = 0.dp
    // BottomHintBar 分割线上方与内容的间距（4dp 呼吸，避免分割线紧贴内容）。
    val HintBarDividerTopGap = 4.dp
}

// ─────────────────────────────────────────────────────────────
// 设备归一化模块（单一真源）
//
// 统一「横竖屏 / 设备档位（手机·平板·电视）」的判定，集中项目内散落的硬编码尺寸阈值
// （600 / 720 / 1600 等），避免后续各处重复书写魔法值、造成判定口径漂移。
//
// 断点对齐 Material Design 3 WindowSizeClass（按 smallestScreenWidthDp）：
//   COMPACT  < 600dp            → 手机
//   MEDIUM   600–839dp          → 平板（小）
//   EXPANDED ≥ 840dp            → 大平板 / 电视
// 电视额外以「当前宽度 ≥ 1600dp」区分（远观需加宽边距、加粗焦点环）。
// ─────────────────────────────────────────────────────────────
object DeviceProfile {
    // ── 设备档位断点（唯一真源）──
    const val PHONE_MAX_DP = 600      // 手机 / 平板分界（MD3 Compact↔Medium）
    const val TABLET_MIN_DP = 600      // 平板下限（语义别名，与 PHONE_MAX_DP 同源）
    const val EXPANDED_MIN_DP = 840    // MD3 Medium↔Expanded
    const val TV_MIN_DP = 1600         // 电视（远观大屏）

    // ── 布局宽屏阈值（双栏 / 居中限宽，区别于设备档位）──
    const val WIDE_SCREEN_DP = 720     // 双栏布局 / 子页宽屏阈值
    const val NARROW_SCREEN_DP = 480   // 窄屏（指标卡 2 列网格）

    enum class Orientation { PORTRAIT, LANDSCAPE }
    enum class Class { PHONE, TABLET, TV }

    /**
     * 设备归一化快照。所有判定均以 [DeviceProfile.from] 产出的本对象为准，调用方只读派生布尔量，
     * 不自行比较尺寸。
     */
    data class Snapshot(
        val orientation: Orientation,
        val deviceClass: Class,
        val smallestWidthDp: Int,
        val widthDp: Int,
        val heightDp: Int
    ) {
        val isPortrait: Boolean get() = orientation == Orientation.PORTRAIT
        val isLandscape: Boolean get() = orientation == Orientation.LANDSCAPE
        val isPhone: Boolean get() = deviceClass == Class.PHONE
        val isTablet: Boolean get() = deviceClass == Class.TABLET
        val isTV: Boolean get() = deviceClass == Class.TV
        /** 大屏（平板 / 电视）：smallestScreenWidthDp ≥ 600，用于美术资源分层等场景。 */
        val isLargeScreen: Boolean get() = smallestWidthDp >= PHONE_MAX_DP
        /** 宽屏（双栏 / 居中限宽布局判定，区别于设备档位）。 */
        val isWideScreen: Boolean get() = widthDp >= WIDE_SCREEN_DP
    }

    /** 由 [Configuration] 推导归一化快照（Composable / 非 Context 上下文使用）。 */
    fun from(config: Configuration): Snapshot {
        val w = config.screenWidthDp
        val h = config.screenHeightDp
        val sw = config.smallestScreenWidthDp
        val orientation = if (w >= h) Orientation.LANDSCAPE else Orientation.PORTRAIT
        val deviceClass = when {
            w >= TV_MIN_DP -> Class.TV
            sw >= TABLET_MIN_DP -> Class.TABLET
            else -> Class.PHONE
        }
        return Snapshot(orientation, deviceClass, sw, w, h)
    }

    /** 由 [Context] 推导归一化快照（全局统一入口）。 */
    fun from(context: Context): Snapshot = from(context.resources.configuration)
}

/** 当前设备的归一化快照（全局统一入口，免 import [DeviceProfile] 也可调用）。 */
val Context.deviceProfile: DeviceProfile.Snapshot get() = DeviceProfile.from(this)
/** 当前 [Configuration] 的归一化快照（Composable / 非 Context 上下文使用）。 */
val Configuration.deviceProfile: DeviceProfile.Snapshot get() = DeviceProfile.from(this)

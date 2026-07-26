package cn.mocabolka.run.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局语义表面令牌（Surface Tokens）—— 归一化所有"散落的 alpha 魔法值"的单一来源。
 *
 * 问题背景：通查全代码库后发现，焦点态背景、卡片表面、遮罩（scrim）、
 * 弱化文字等大量视觉 alpha 各自硬编码（0.10 / 0.12 / 0.14 / 0.18 / 0.20 / 0.25、
 * scrim 0.55 / 0.6、surfaceVariant 0.30 / 0.45 / 0.50 / 0.95、onSurfaceVariant
 * 0.3 / 0.4 / 0.5 / 0.6 / 0.7 / 0.8、按压 0.08 等），且**存在不一致**
 * （Theme 的 scrim=0.6 与 MotionSpec.ScrimAlpha=0.55 冲突；
 * AppDetailPanel 焦点 0.10 与 AppTile 0.20 不一致）。
 *
 * 设计意图：所有"派生自 MaterialTheme.colorScheme 的带 alpha 语义色"都从这里取，
 * 形成稳定的全局变量关系 —— 改一处即全站生效，杜绝漂移。
 * 与既有资产的关系：
 *  - [Dimens.FocusSurfaceAlpha] 是焦点背景主 alpha 的唯一真值，本类 [focusBg] 引用它；
 *  - [MotionSpec.ScrimAlpha] 是遮罩 alpha 的唯一真值，本类 [scrim] 引用它；
 *  - 圆角/线宽统一引用 [Dimens] 与 [MaterialTheme.shapes]，不自造。
 *
 * 派发方式：Composable 内通过 `MaterialTheme.colorScheme` 即时派生（无状态、零重组开销），
 * 各页面（AppTile / AppListItem / AppDetailPanel / SubPage / SettingsPage / StatusBar /
 * StatsScreen / HomeScreen Toast）统一调用，不再手写 `primary.copy(alpha = Xf)`。
 */
object SurfaceTokens {

    // ── 焦点态背景（main 色填充，焦点环的内侧底色）──────────────
    /**
     * 标准焦点态背景：primary 主色填充，alpha 引用全局唯一 [Dimens.FocusSurfaceAlpha]。
     * 替代散落的 0.10 / 0.12 / 0.14 / 0.18 / 0.20 / 0.25 等写法，统一手感。
     */
    @Composable
    fun focusBg(): Color = MaterialTheme.colorScheme.primary.copy(alpha = Dimens.FocusSurfaceAlpha)

    /**
     * 强焦点背景（如 SubPage 返回钮、强调态），比 [focusBg] 略深，用于需要更高识别度的场景。
     * 固定 0.18f（此前 SubPageTopBar 的 isBackFocused 取值）。
     */
    @Composable
    fun focusBgStrong(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    /**
     * 按压态背景：比焦点更浅，用于 pointer pressed 即时反馈。固定 0.08f
     * （此前 AppTile 的 isPressed 取值）。
     */
    @Composable
    fun pressBg(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    // ── 卡片 / 表面 ───────────────────────────────────────────
    /**
     * 标准卡片表面：surfaceVariant 低 alpha，用于非聚焦态的卡片/行底色。
     * [level] 提供四档统一强度，替代散落的 0.22 / 0.30 / 0.35 / 0.45 / 0.50。
     *  - Overlay = 0.22（玻璃穿透态专用：壁纸在卡片后可见时最透档）
     *  - Subtle = 0.30（图表卡、License 卡等弱背景）
     *  - Default = 0.45（MetricCard、SubPage 静态卡、AppListItem 等）
     *  - Strong = 0.50（需要时稍重）
     */
    enum class CardLevel { Overlay, Subtle, Default, Strong }

    @Composable
    fun cardSurface(level: CardLevel = CardLevel.Default): Color {
        val a = when (level) {
            CardLevel.Overlay -> 0.22f
            CardLevel.Subtle -> 0.30f
            CardLevel.Default -> 0.45f
            CardLevel.Strong -> 0.50f
        }
        return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = a)
    }

    /**
     * 玻璃感知卡片表面：读取 [LocalGlassEnabled]，玻璃开启时自动降级到
     * [CardLevel.Subtle]（半透明，让壁纸磨砂底透出），关闭时按调用方给定
     * [level] 取实色感表面 —— 调用方零传参即可跟随全局玻璃开关。
     * 注意：焦点/按压等**状态语义**的 Strong 背景不应换用本函数（保持状态对比度）。
     */
    @Composable
    fun cardSurfaceGlassAware(level: CardLevel = CardLevel.Default): Color {
        val effective = if (LocalGlassEnabled.current) CardLevel.Subtle else level
        return cardSurface(effective)
    }

    /**
     * 卡片强表面（接近实色，如 Toast、弹出气泡）。固定 0.95f
     * （此前 HomeScreen Toast 与 SubPage 静态卡的强背景取值）。
     */
    @Composable
    fun cardSurfaceStrong(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)

    // ── 遮罩（scrim）──────────────────────────────────────────
    /**
     * 全局遮罩色：固定黑色 + [MotionSpec.ScrimAlpha]（唯一真值，此前 0.55）。
     * 替代散落的 `Color.Black.copy(alpha = 0.55f)` 与 Theme 中不一致的 `0.6f`。
     * 所有弹窗 / 抽屉 / 启动遮罩的遮罩层统一用此。
     */
    @Composable
    fun scrim(): Color = Color.Black.copy(alpha = MotionSpec.ScrimAlpha)

    // ── 弱化文字（onSurfaceVariant 的各级透明度）──────────────
    /**
     * 弱化文字色：onSurfaceVariant + 标准 alpha 档位，替代散落的
     * 0.3 / 0.4 / 0.5 / 0.6 / 0.7 / 0.8 等。
     *  - Faint = 0.5（副信息、占位）
     *  - Medium = 0.6（次级描述）
     *  - Strong = 0.7（列表副标题）
     *  - Emphasis = 0.8（近实色副文）
     */
    enum class MutedLevel { Faint, Medium, Strong, Emphasis }

    @Composable
    fun mutedOnSurface(level: MutedLevel = MutedLevel.Medium): Color {
        val a = when (level) {
            MutedLevel.Faint -> 0.5f
            MutedLevel.Medium -> 0.6f
            MutedLevel.Strong -> 0.7f
            MutedLevel.Emphasis -> 0.8f
        }
        return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = a)
    }

    /**
     * 强调色弱化文字（primary 主色的浅化版本）：用于"强调信息"而非"弱化说明"的场景
     * （如 AppListItem 今日时长、ChartAccent 趋势说明）。固定 0.9f（此前 primary.copy(0.9f) 取值）。
     */
    @Composable
    fun primaryMuted(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)

    // ── 描边（resting 1dp 浅描边）────────────────────────────
    /**
     * 标准 resting 描边色：outline + 0.30f，替代散落的 outline.copy(0.25/0.3) 写法。
     * 用于卡片/行的常态描边（与 [Dimens.FocusBorderWidth] 1dp 配合）。
     */
    @Composable
    fun restingBorderColor(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)

    /**
     * 强 resting 描边（outline + 0.25f，用于 License 卡等更弱描边）。
     */
    @Composable
    fun restingBorderColorSoft(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    /**
     * 玻璃拟态深色底（KeyBadge Glass / 浮块的统一底色）。
     * 联动 [LocalGlassEnabled]：玻璃开启 = 黑色 0.6f（半透浮块）；
     * 关闭 = 黑色 0.9f（近实色，退化为普通深色浮块）。
     * 此前 KeyBadge 两处硬编码 `Color.Black.copy(alpha = 0.6f)`，
     * 现归一化到此，改一处即全站键位浮块底色一致。
     */
    @Composable
    fun glassBg(): Color =
        if (LocalGlassEnabled.current) Color.Black.copy(alpha = 0.6f)
        else Color.Black.copy(alpha = 0.9f)

    /**
     * 图标按钮圆形 tonal 背景（ReverieIconButton tonal=true）：surfaceVariant + 0.6f。
     * 替代 Buttons.kt 中硬编码的 `surfaceVariant.copy(alpha = 0.6f)`。
     */
    @Composable
    fun iconTonalBg(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    /**
     * 分段控件容器背景（ReverieSegmentedRow）：surfaceVariant + 0.30f（Subtle 档）。
     */
    @Composable
    fun segmentContainer(): Color = cardSurface(CardLevel.Subtle)
}

/**
 * 标准 Surface 卡片模板：归一化"surfaceVariant 背景 + 1dp 浅描边 + 统一圆角"的重复模式。
 *
 * 替代散落在 SubPage / StatsScreen / AppDetailPanel 等处的 `Surface(color=surfaceVariant,
 * border=BorderStroke(1.dp, outline.copy(...)))` 手搓写法，形成稳定的"卡片外观"全局变量关系。
 *
 * @param level 表面强度（见 [SurfaceTokens.CardLevel]）
 * @param shape 圆角，默认 [Dimens.md]（与 MD3 medium 对齐）
 * @param border 是否绘制 resting 描边（默认 true；强表面如 Toast 可关闭）
 * @param content 卡片内容
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    level: SurfaceTokens.CardLevel = SurfaceTokens.CardLevel.Default,
    shape: Dp = Dimens.md,
    border: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val radius = RoundedCornerShape(shape)
    // 玻璃感知（R12）：SurfaceCard 的全部调用方均为静态内容卡（无焦点/按压状态语义），
    // 统一走 cardSurfaceGlassAware——玻璃开时半透 + 顶部高光，关时按 level 实色感。
    val glass = LocalGlassEnabled.current
    Box(
        modifier = modifier
            .clip(radius)
            .background(SurfaceTokens.cardSurfaceGlassAware(level))
            .then(
                if (border) Modifier.border(
                    width = Dimens.FocusBorderWidth,
                    color = SurfaceTokens.restingBorderColor(),
                    shape = radius
                ) else Modifier
            )
            .glassHighlight(enabled = glass),
        content = content
    )
}

/**
 * 玻璃感知卡片修饰符工厂：一行接入"圆角裁剪 + 玻璃感知表面 + 可选描边 + 玻璃高光"。
 *
 * 读取 [LocalGlassEnabled]：
 *  - 玻璃开 → 表面自动降到 Subtle 半透（透出壁纸磨砂底）+ [glassHighlight] 顶部反光；
 *  - 玻璃关 → 按 [level] 取实色感表面，无高光，与 [SurfaceCard] 视觉一致。
 *
 * 方向性说明：模糊由背景层（HomeScreen 壁纸）的 [frosted] 承担，本工厂**不含** frosted
 * —— 挂在含文字的卡片上会把内容糊掉（Compose 无 backdrop-blur）。
 *
 * @param level 玻璃关闭时的表面强度
 * @param shape 圆角形状（Dock 等异形圆角可传自定义 [RoundedCornerShape]），默认 [Dimens.lg] 全角
 * @param border 可选描边（null = 无）
 */
@Composable
fun Modifier.surfaceCardGlassAware(
    level: SurfaceTokens.CardLevel = SurfaceTokens.CardLevel.Default,
    shape: RoundedCornerShape = RoundedCornerShape(Dimens.lg),
    border: androidx.compose.foundation.BorderStroke? = null
): Modifier {
    val glass = LocalGlassEnabled.current
    return this
        .clip(shape)
        .background(SurfaceTokens.cardSurfaceGlassAware(level))
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
        .glassHighlight(enabled = glass)
}

/**
 * 覆盖式即时 Toast 模板：归一化 HomeScreen 中"单条即时 Toast"的重复实现。
 *
 * 特性（全局统一）：
 *  - 单条显示，新消息立即替换旧消息（不排队回弹）
 *  - 进入 = fadeIn + slideInVertically(1/3)；退出 = fadeOut + slideOutVertically(1/3)，统一 [MotionSpec.Fast]
 *  - 视觉 = 强表面（cardSurfaceStrong 0.95）+ 主题色描边（focusBgStrong 0.18）+ 大圆角（RadiusRound）+ 6dp 阴影
 *  - 底部 [bottomOffset] 安全留白（默认 = [Dimens.HintBarHeight]，与 BottomHintBar 高度对齐）
 *
 * 调用方只需提供 [message]（null 即隐藏），可选 [bottomOffset] / [shadowElevation] / [durationMs]。
 *
 * 修复（2026-07-14 归一化）：原实现把 `Modifier.fillMaxSize()` 挂在 `AnimatedVisibility` 上，
 * 导致 toast 占位撑满全屏、动画按全屏高度插值，呈现"扫描 toast 铺满全屏"错觉。
 * 正确做法：AnimatedVisibility 自身按内容尺寸渲染，由内层 `Box.fillMaxSize()` +
 * `contentAlignment = BottomCenter` 提供"底部居中"对齐空间。
 *
 * @param message 当前要显示的消息；为 null 时退场隐藏
 * @param durationMs 自动消失时长（提示用，实际消失由调用方控制；保留以兼容历史签名）
 * @param bottomOffset 距底部的偏移量（默认与 BottomHintBar 高度对齐）
 * @param shadowElevation 阴影高度（默认 6dp，强化"浮起"感）
 */
@Composable
fun ReverieToast(
    message: String?,
    modifier: Modifier = Modifier,
    durationMs: Long = 2500L,
    bottomOffset: Dp = Dimens.HintBarHeight,
    shadowElevation: Dp = 6.dp
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = androidx.compose.animation.fadeIn(animationSpec = MotionSpec.Fast) +
                androidx.compose.animation.slideInVertically(initialOffsetY = { it / 3 }),
        exit = androidx.compose.animation.fadeOut(animationSpec = MotionSpec.Fast) +
                androidx.compose.animation.slideOutVertically(targetOffsetY = { it / 3 })
    ) {
        // 内层 Box.fillMaxSize 负责"占满父空间 + 内容底部居中对齐"。
        // AnimatedVisibility 自身不挂 fillMaxSize，size = 包络内容（气泡），消除"占位撑满全屏"bug。
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            val radius = RoundedCornerShape(Dimens.RadiusRound)
            androidx.compose.material3.Surface(
                modifier = Modifier.padding(bottom = bottomOffset),
                shape = radius,
                color = SurfaceTokens.cardSurfaceStrong(),
                shadowElevation = shadowElevation,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = SurfaceTokens.focusBgStrong()
                )
            ) {
                androidx.compose.material3.Text(
                    text = message ?: "",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * 强调图标方块模板（Tai 风格）：归一化 StatsScreen MetricCard / LongestCard 中
 * "圆角方块 + 线性渐变强调色 + 居中图标"的重复写法。
 *
 * @param accent 强调色（chart/trend 等）
 * @param icon 图标矢量
 * @param size 方块边长，默认 [Dimens.IconAvatar] (36dp)
 */
@Composable
fun AccentIconSquare(
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.IconAvatar
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Dimens.sm))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            icon, null,
            tint = Color.White,
            modifier = Modifier.size(Dimens.IconSm)
        )
    }
}

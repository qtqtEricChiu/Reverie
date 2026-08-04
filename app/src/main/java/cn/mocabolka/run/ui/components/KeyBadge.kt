package cn.mocabolka.run.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import cn.mocabolka.run.gamepad.GamepadDetector
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LocalShowFocusIndicators
import cn.mocabolka.run.ui.theme.SurfaceTokens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * 全局唯一的手柄键位浮块。
 *
 * 此前项目里散落着 SideKeyBadge / GamepadKeyBadge / CrossKeyBadge / TabTriggerBadge /
 * DarkKeyBadge / LsKeyBadge / DirectionKeyBadge / KeyBadge / LaunchKeyBadge 等 8+ 套
 * 风格各异的"键位提示"组件，再加上若干裸文本提示（"按 B 返回" 之类），样式参差不齐。
 * 现在统一收敛到 [KeyBadge] 一个入口：[KeyToken] 描述按键语义，[KeyBadgeVariant] 描述外观。
 *
 * 视觉规范：
 * - [KeyBadgeVariant.Glass]（默认）：深色半透明圆角底 + 亮色按键名，浮在内容左上/骑缝处。
 * - [KeyBadgeVariant.Solid]：主题色实心圆（用于启动按钮上的 "A" 角标等强调场景）。
 */
enum class KeyToken {
    A, B, X, Y,
    LB, RB, LT, RT,
    LS, RS,
    UP, DOWN, LEFT, RIGHT,
    MENU, BACK,
    /** Xbox「视图 / View」键（双方块图标），统计页打开日期选择器。用自绘图标而非文字。 */
    VIEW;

    /** 渲染文字：方向键用箭头符号，其余用字母。 */
    val label: String
        get() = when (this) {
            UP -> "↑"
            DOWN -> "↓"
            LEFT -> "←"
            RIGHT -> "→"
            VIEW -> "" // VIEW 用自绘图标，不用文字
            else -> name
        }
}

enum class KeyBadgeVariant { Glass, Solid }

/**
 * 统一键位浮块。
 * @param token 按键语义（决定显示文字与默认尺寸）
 * @param variant 外观变体，默认 [KeyBadgeVariant.Glass]
 * @param modifier 调用方可自由定位（左上角 / 骑缝 / 行内）
 */
@Composable
fun KeyBadge(
    token: KeyToken,
    modifier: Modifier = Modifier,
    variant: KeyBadgeVariant = KeyBadgeVariant.Glass
) {
    when (variant) {
        KeyBadgeVariant.Solid -> SolidKeyBadge(token, modifier)
        KeyBadgeVariant.Glass -> GlassKeyBadge(token, modifier)
    }
}

/** 便捷重载：直接传字符串（如 "LT"），用于非枚举情景。 */
@Composable
fun KeyBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: KeyBadgeVariant = KeyBadgeVariant.Glass
) {
    when (variant) {
        KeyBadgeVariant.Solid -> SolidKeyBadgeText(text, modifier)
        KeyBadgeVariant.Glass -> GlassKeyBadgeText(text, modifier)
    }
}

// ── Glass：深色半透明圆角浮块（带方向箭头的圆形变体） ──────────────

@Composable
private fun GlassKeyBadge(token: KeyToken, modifier: Modifier) {
    if (token == KeyToken.VIEW) {
        // Xbox「视图」键：深色半透明圆角底 + 白色双方块视图图标（自绘）。
        Box(
            modifier = modifier
                .size(width = 26.dp, height = 22.dp)
                .clip(RoundedCornerShape(Dimens.RadiusSm))
                .background(SurfaceTokens.glassBg()),
            contentAlignment = Alignment.Center
        ) {
            XboxViewGlyph(color = Color.White)
        }
        return
    }
    GlassKeyBadgeText(token.label, modifier)
}

/**
 * Xbox「视图 / View」键标志：两个交叠的圆角矩形（前实心描边 + 后半透明），
 * 复刻 Xbox 手柄左侧双方块视图按钮的视觉语言。用 Canvas 自绘，随 [color] 着色。
 */
@Composable
private fun XboxViewGlyph(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(width = 15.dp, height = 12.dp)
    ) {
        val w = size.width
        val h = size.height
        val rectW = w * 0.62f
        val rectH = h * 0.72f
        val corner = androidx.compose.ui.geometry.CornerRadius(h * 0.14f, h * 0.14f)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.13f)
        // 后方矩形（右上，半透明）
        drawRoundRect(
            color = color.copy(alpha = 0.45f),
            topLeft = androidx.compose.ui.geometry.Offset(w - rectW, 0f),
            size = androidx.compose.ui.geometry.Size(rectW, rectH),
            cornerRadius = corner,
            style = stroke
        )
        // 前方矩形（左下，实心描边，压在后方之上）
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, h - rectH),
            size = androidx.compose.ui.geometry.Size(rectW, rectH),
            cornerRadius = corner,
            style = stroke
        )
    }
}

@Composable
private fun GlassKeyBadgeText(text: String, modifier: Modifier) {
    val isArrow = text.length == 1 && text in "↑↓←→"
    Box(
        modifier = modifier
            .then(
                if (isArrow) Modifier.size(28.dp)
                else Modifier.size(width = 26.dp, height = 22.dp)
            )
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(SurfaceTokens.glassBg()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 1
        )
    }
}

// ── Solid：主题色实心圆（强调场景，如启动按钮 A 角标） ──────────────

@Composable
private fun SolidKeyBadge(token: KeyToken, modifier: Modifier) {
    SolidKeyBadgeText(token.label, modifier)
}

@Composable
private fun SolidKeyBadgeText(text: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 统一的操作提示行：把"键位浮块 + 动作文字"连续排布，替代此前散落的裸文本提示
 * （如 "↑↓ 切换结果 · A 启动 · B 返回"）。调用方用 [Hint] DSL 描述每一对（按键→动作）。
 *
 * 用法：
 * ```
 * HintBar {
 *     Hint(KeyToken.UP, "选择")   // 支持方向键
 *     Hint(KeyToken.A,  "确认")
 *     Hint("LT",        "翻页")   // 支持裸字符串
 * }
 * ```
 */
@Composable
fun HintBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        content()
    }
}

/** 提示行中的一对「键位浮块 + 文字说明」。
 * 项内统一加 [Dimens.HintBadgeGap] 间距（键位浮块 → 文本），与行内 [Dimens.HintItemGap]
 * （项与项之间）分离，避免"项内无间距 + 行内间距"导致键位与文字贴死、各栏不齐。
 * 统一后：所有底部提示栏的「键位-文本」间隙一致，黑色玻璃底块保留（仅调间距）。 */
@Composable
fun RowScope.Hint(
    token: KeyToken,
    text: String,
    variant: KeyBadgeVariant = KeyBadgeVariant.Glass,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    KeyBadge(token = token, variant = variant)
    Spacer(Modifier.width(Dimens.HintBadgeGap))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor
    )
}

/** 提示行中的一对「字符串键位 + 文字说明」（用于 LB/RB/LT/RT 等无枚举映射的按键）。 */
@Composable
fun RowScope.Hint(
    text: String,
    action: String,
    variant: KeyBadgeVariant = KeyBadgeVariant.Glass,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    KeyBadge(text = text, variant = variant)
    Spacer(Modifier.width(Dimens.HintBadgeGap))
    Text(
        text = action,
        style = MaterialTheme.typography.labelSmall,
        color = textColor
    )
}

/** 提示行中分隔两组的间隔（组间松排，替代此前的 " · " 裸文本分隔符）。 */
@Composable
fun RowScope.HintGap() {
    Spacer(Modifier.width(Dimens.HintGroupGap))
}

/**
 * 底部专用按键指示栏：与 screen 主体**隔离**的固定槽位。
 *
 * 设计约束（用户专项调整 2026-07-13）：
 * - 规则2：不便放在按钮旁的提示统一收纳于此，与 screen 主体**仅用顶部细分割线**区隔，
 *          **不再叠加任何半透明色块**（surfaceVariant 底色已移除，避免视觉污染）。
 * - 规则3：已在按钮旁给出键位角标（A 启动 / X 收藏 / LB·RB 周期 / LT·RT 滑块）的，此处不复现。
 * - 规则4：禁止出现任何上下滚动 / 十字方向键 / 左右摇杆操作提示（如 "↑ 浏览" "↓ 浏览"）。
 *   例外（2026-07-26 用户专项补充）：搜索页 INPUT 态底部栏允许显示 "←→ 移动光标"，
 *   提示十字键（D-pad）左右移动文本光标（让位 IME）——该分工非显而易见，属功能发现型提示，
 *   不属规则4旨在剔除的"列表浏览导航"类冗余提示，故作为唯一豁免保留。
 *
 * 标准化模板：
 * - 高度固定 [Dimens.HintBarHeight]，横屏模式下底部外边距强制为 0。
 * - 顶部仅一条 1dp [outline] 细分割线（alpha 0.10f），无底色槽。
 * - 提示项用 [RowScope.Hint] / [RowScope.HintText] 填充；组间用 [RowScope.HintGap]（20dp）。
 * - 项内「键位 → 描述」间距统一为 [Dimens.HintItemGap]（4dp），行内整体 [Arrangement.spacedBy]
 *   由调用方在组内用 [RowScope.Hint] 连续排布，组间由 [HintGap] 拉开。
 *
 * 调用方应传入 `modifier = Modifier.fillMaxWidth()`，**默认不再做水平 padding**（对齐全屏基线
 * [Dimens.ContentHorizontal] 应由页面根容器统一负责，避免双重内陷——此前 BottomHintBar
 * 内部硬编码 ContentHorizontal 导致 GamePage / StatsScreen / 美术子页等根容器已加
 * ContentHorizontal/ScreenPadding 的页面出现「双倍左内陷」，与 HomeScreen 底部提示栏
 * 左边距不一致）。
 *
 * 例外：HomeScreen 因其 [cn.mocabolka.run.ui.components.StatusBar] 内部已使用
 * [Dimens.ContentHorizontal]，根容器无法重复叠加，故需显式传入
 * `horizontalPadding = Dimens.ContentHorizontal` 以保持原视觉；其它页面直接使用默认 0.dp，
 * 底部提示栏与页面内容自然对齐。
 */
@Composable
fun BottomHintBar(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.HintBarHeight)
    ) {
        // 分割线上方 4dp 呼吸间隙，避免细线紧贴内容。
        Spacer(Modifier.height(Dimens.HintBarDividerTopGap))
        // 仅一条顶部细分割线，与主体区隔（规则2"隔离"），无任何底色块。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.HintBarDivider)
                .background(SurfaceTokens.hairlineDividerColor())
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.HintItemGap)
        ) {
            content()
        }
    }
}

/** 提示行中纯文字项（无键位浮块）：用于未连接手柄时的精简提示，与 [Hint] 同容器同字号。 */
@Composable
fun RowScope.HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 统一手柄连接守卫：仅当手柄已连接时渲染 [content]。
 * 替换散落的 `if (gamepadConnected) { ... }` 模式。
 * 内部自动使用 [GamepadDetector.gamepadConnectedFlow] 响应插拔。
 *
 * @param content 手柄连接时渲染的内容
 */
@Composable
fun IfGamepad(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 使用 Flow 响应式监听，避免静态方法不响应插拔的问题；remember 防重组时重复订阅
    val gamepadFlow = remember(context) { GamepadDetector.gamepadConnectedFlow(context) }
    val gamepadConnected by gamepadFlow.collectAsState(initial = GamepadDetector.isGamepadConnected())
    if (gamepadConnected) {
        content()
    }
}

/**
 * 统一底部按键指示栏：包装 [BottomHintBar]，自动在未连接手柄时隐藏。
 * 替换散落的 `if (gamepadConnected) { BottomHintBar(...) }` 模式。
 * 内部使用 [GamepadDetector.gamepadConnectedFlow] 响应插拔。
 */
@Composable
fun GamepadBottomHintBar(
    modifier: Modifier = Modifier.fillMaxWidth(),
    horizontalPadding: Dp = Dimens.ContentHorizontal,
    content: @Composable RowScope.() -> Unit
) {
    IfGamepad {
        BottomHintBar(modifier = modifier, horizontalPadding = horizontalPadding, content = content)
    }
}

/**
 * 居中提示文本（空状态 / 错误状态 / 加载失败等）。
 * 由原 GameScreenshotsAllActivity 的 private CenteredHint 提升为全应用共享组件，
 * 统一空状态文案的版式与配色（ mutedOnSurface(Medium) ），避免各页面手写不一致的居中提示。
 *
 * @param text   提示文案
 * @param icon   可选图标（如搜索空态的 SearchOff、错误态的 Warning）
 * @param style  文本样式（默认 bodyMedium）
 * @param color  文本颜色（默认 mutedOnSurface(Medium)）
 */
@Composable
fun CenteredHint(
    text: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    icon: ImageVector? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Text(text, style = style, color = color)
        }
    }
}

/**
 * 统一手柄键位提示行：包装 [HintBar]，自动在未连接手柄时隐藏。
 * 替换散落的 `if (GamepadDetector.isGamepadConnected()) { HintBar(...) }` 模式。
 */
@Composable
fun GamepadHintBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    IfGamepad {
        HintBar(modifier = modifier, content = content)
    }
}

/**
 * 统一手柄条件渲染：包装任何需要手柄连接时才显示的内容。
 * 替换散落的 `if (gamepadConnected) { KeyBadge(...) }` 模式。
 * 与 [IfGamepad] 等价，但函数名更语义化。
 */
@Composable
fun GamepadVisible(
    content: @Composable () -> Unit
) {
    IfGamepad(content = content)
}

/**
 * 跨栏提示浮块（CrossKeyBadge）已于 2026-07-13 专项移除：
 * 跨栏指示职责已统一下沉至底部按键指示栏（"LS/RS 列表/详情" 或 "Tab 切换栏"），
 * 骑缝浮块属于重复且易与右栏内容相互遮挡的视觉噪声，不再渲染。
 * 保留此注释以说明历史决策，避免后续误重新引入。
 */

// ─────────────────────────────────────────────────────────────────────────────
// 归一化子页面标题栏（基准 = 权限页「权限」子页面所用 SubPageTopBar）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 归一化子页面标题栏（基准 = 权限页「权限」子页面所用 [SubPageTopBar]）。
 *
 * 全应用所有一/二/三级子页面统一引用此部件，保证标题栏高度、水平边距、返回按钮与
 * 退出提示完全一致，消除各页面"自绘标题栏"带来的高度漂移与风格不统一。
 *
 * 遵循手柄提示规则：
 * - 返回按钮（[SubPageBackButton]）固定居左，作为默认退出入口（不进底部提示栏）。
 * - 退出（B）提示固定置于右上角（[GamepadHintBar]，仅手柄连接时显示）。
 * - 个性化按钮（如游戏页收藏/信息/主题、设置页开关等）通过 [actions] 槽位注入；
 *   各子页面将自身的独立按钮作为单独部件引用，避免散落的自定义实现。
 *
 * @param title          标题文本
 * @param onBack         返回/退出回调（触摸点击与手柄 B 共用）
 * @param isBackFocused  返回按钮是否处于焦点高亮（自定义焦点状态机传入）
 * @param onBackFocus    返回按钮获得焦点回调（用于驱动自定义焦点状态机）
 * @param actions        标题栏右侧、退出提示前的个性化按钮槽（[RowScope]）
 * @param showExitHint   是否在右上角显示「B 返回」提示（默认 true）
 * @param contentColor   标题与图标颜色（默认 onBackground；美术页传 [Color.White]）
 * @param titleStyle     标题文字样式（默认 titleLarge）
 */
@Composable
fun SubPageTitleBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isBackFocused: Boolean = false,
    onBackFocus: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    showExitHint: Boolean = true,
    exitLabel: String = "返回",
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Row(
        modifier = Modifier.fillMaxWidth().then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubPageBackButton(
            onClick = onBack,
            isFocused = isBackFocused,
            onFocus = onBackFocus,
            contentColor = contentColor
        )
        Spacer(Modifier.width(Dimens.sm))
        Text(
            text = title,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        actions()
        if (showExitHint) {
            Spacer(Modifier.width(Dimens.sm))
            GamepadHintBar(
                modifier = Modifier.padding(end = Dimens.xs),
                content = { Hint(KeyToken.B, exitLabel, textColor = contentColor) }
            )
        }
    }
}

/**
 * 归一化标题栏返回按钮（基准 = 权限页 SubPageTopBar 的返回按钮）。
 * 固定 40dp 圆角块 + 左箭头；支持自定义焦点状态机高亮。
 * 美术背景页可传 [contentColor]=[Color.White] 与深色 [backgroundColor] 保证对比。
 */
@Composable
fun SubPageBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    onFocus: () -> Unit = {},
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    // 半透明焦点框仅在「手柄已连接 且 焦点在此」时出现，绝不常驻（纯触控/未连手柄时透明）
    backgroundColor: Color = if (isFocused && LocalShowFocusIndicators.current) SurfaceTokens.focusBgStrong() else Color.Transparent,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(Dimens.RadiusLg))
            .background(backgroundColor)
            .clickable { onFocus(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "返回",
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 归一化标题栏动作按钮：供 [SubPageTitleBar] 的 [actions] 槽位引用的独立部件。
 * 不同子页面的个性化按钮（如游戏页收藏/信息/主题）统一走此部件，避免散落自定义实现。
 */
@Composable
fun TitleBarIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    onFocus: () -> Unit = {},
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    // 半透明焦点框仅在「手柄已连接 且 焦点在此」时出现，绝不常驻（纯触控/未连手柄时透明）
    backgroundColor: Color = if (isFocused && LocalShowFocusIndicators.current) SurfaceTokens.focusBgStrong() else Color.Transparent,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(Dimens.RadiusLg))
            .background(backgroundColor)
            .clickable { onFocus(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

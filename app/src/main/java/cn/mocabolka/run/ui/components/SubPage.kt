package cn.mocabolka.run.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.gamepad.GamepadDetector
import cn.mocabolka.run.ui.components.Hint
import cn.mocabolka.run.ui.components.HintBar
import cn.mocabolka.run.ui.components.KeyBadge
import cn.mocabolka.run.ui.components.KeyToken
import cn.mocabolka.run.ui.components.ReverieFilledButton
import cn.mocabolka.run.ui.Haptics
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LocalShowFocusIndicators
import cn.mocabolka.run.ui.theme.focusBorder
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.SurfaceTokens
import cn.mocabolka.run.ui.theme.waterfallSafePadding

/**
 * 统一二级/三级全屏子页面框架。
 *
 * 设计目标（用户专项调整）：About / Licenses / CompatGuide 三页彻底统一 ——
 *  1. UI 统一：统一的 Scaffold / TopBar / Row / Card，全部走 MaterialTheme 语义色，
 *     不再各自写硬编码配色（之前 About/Licenses 自建 reverieColorScheme 导致深色/AMOLED 割裂）。
 *  2. 主题统一：本框架**不**重建 ColorScheme，完全继承根 LandscapeTheme 的
 *     darkMode / useMonet / AMOLED 取色（与主页、设置页一致）。
 *  3. 动画统一：入场 = fadeIn + slideInHorizontally(1/6) 由外层 AnimatedVisibility 驱动；
 *     内部列表逐行 fade + slideInVertically 错峰入场（staggerDelay），与 Material Design 规范一致。
 *  4. 横竖屏统一：BoxWithConstraints 响应式，始终跟随系统窗口实际尺寸
 *     （含用户强制旋屏 → 窗口尺寸即旋转后尺寸），宽屏居中限宽、竖屏收紧间距字号。
 *  5. 手柄统一：左摇杆 ↑↓ 移动焦点（focusedRow 状态机，与 CompatGuide 完全一致），
 *     右摇杆滚屏由外部 listState.scrollBy 驱动（HomeScreen 引擎），A 触发 / B 返回。
 */
object SubPageSpec {
    /** 内容最大宽度（宽屏居中）。 */
    val ContentMaxWidth = 880.dp
    /** 宽屏判定阈值（与项目全局 720dp 一致）。 */
    val WideThreshold = 720.dp
}

/**
 * 子页面行模型：完全统一 About / Licenses / CompatGuide 三类内容。
 * - [Link]：外部链接行（右侧显示 OpenInNew 图标）。
 * - [Action]：内部动作行（可选 primary 主按钮样式、可选 trailing 徽标）。
 * - [Card]：自定义内容卡片（默认可聚焦，用于 License 条目）。
 * - [Static]：静态区块（版权文本、图标展示、进度条等，默认不可聚焦）。
 */
sealed interface SubPageRow {
    val focusable: Boolean
    val key: String

    data class Link(
        override val key: String,
        val label: String,
        val desc: String = "",
        val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        val onClick: () -> Unit
    ) : SubPageRow {
        override val focusable = true
    }

    data class Action(
        override val key: String,
        val label: String,
        val desc: String = "",
        val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        val primary: Boolean = false,
        val trailing: (@Composable () -> Unit)? = null,
        val onClick: () -> Unit
    ) : SubPageRow {
        override val focusable = true
    }

    data class Card(
        override val key: String,
        val content: @Composable ColumnScope.() -> Unit,
        override val focusable: Boolean = true
    ) : SubPageRow

    data class Static(
        override val key: String,
        val content: @Composable ColumnScope.() -> Unit
    ) : SubPageRow {
        override val focusable = false
    }
}

/**
 * 统一全屏子页面。
 *
 * [title] 标题；[onBack] B 键 / 返回按钮 / 系统返回触发；[rows] 内容行；
 * [listState] 滚动状态（右摇杆由外部引擎驱动 scrollBy）；
 * [bottomAction] 便捷封装：可选的底部固定主按钮（如"完成"），钉在列表下方、不随列表滚动，
 *   并作为焦点状态机的末项（M+1）纳入手柄导航（↑↓ 可达、A 触发、焦点态高亮）。
 *   不传（null）时若也未传 [bottomBar]，布局与 About / Licenses 完全一致。
 * [bottomBar] 通用底部固定槽（技术储备）：任意自定义 Composable 内容，钉在列表下方、
 *   不随列表滚动。用于未来可能的多按钮 / 自定义底部栏等场景。
 *   - 与 [bottomAction] 互斥：两者同时传入时优先使用 [bottomBar]，[bottomAction] 被忽略。
 *   - [bottomBar] 为纯展示 / 自定义交互槽，不参与 ↑↓ 焦点状态机（其内交互由调用方自行处理）。
 *
 * 焦点状态机：focusedRow ∈ [0, M(+1)]，0 = 返回按钮，1..M = 可聚焦行（按出现顺序），
 *   若存在 [bottomAction] 则 M+1 = 底部按钮。
 */
@Composable
fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    rows: List<SubPageRow>,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    bottomAction: SubPageRow.Action? = null,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * 右摇杆滚动后由引擎回写的「首个可视项物理索引」（归一化「右摇杆停哪、焦点跟哪」）。
     * 由 Activity 的 [cn.mocabolka.run.gamepad.SubPageGamepad] 通过 onFocusSync 回调驱动。
     * 值为 rows 的物理下标；本框架会将其映射到最近的可聚焦逻辑焦点行并同步 focusedRow。
     * 传 null（默认）表示未接入引擎，行为与旧版一致（仅方向键驱动焦点）。
     */
    rightStickFirstVisible: State<Int>? = null
) {
    val focusableIndices = remember(rows) { rows.indices.filter { rows[it].focusable } }
    // 列表可聚焦行数；若存在 bottomAction 则焦点总数 +1（作为末项）。
    // bottomBar 为技术储备的通用槽，不纳入焦点状态机。
    val useBottomAction = bottomAction != null && bottomBar == null
    val M = focusableIndices.size
    val lastRow = M + if (useBottomAction) 1 else 0
    var focusedRow by remember { mutableStateOf(0) } // 0 = 返回按钮
    // 标志：本次 focusedRow 变化由右摇杆滚动同步而来（而非方向键），
    // 此时列表位置已由引擎滚动到位，须抑制 LaunchedEffect(focusedRow) 的反向 animateScrollToItem，
    // 否则会与右摇杆滚动打架（焦点同步→拉回列表→位置抖动）。
    var suppressAutoScroll by remember { mutableStateOf(false) }

    // 归一化「右摇杆停哪、焦点跟哪」（需求4）：
    // 引擎每帧回写 rightStickFirstVisible（rows 物理下标），此处映射到最近可聚焦逻辑焦点行并同步 focusedRow。
    // 映射规则：从「首个可视物理项」起向后找首个可聚焦行；找不到则向前回退，仍无则保持不动。
    // 用 snapshotFlow 去重（仅索引真正变化时同步），避免每帧无谓 recomposition。
    if (rightStickFirstVisible != null) {
        LaunchedEffect(rightStickFirstVisible, focusableIndices) {
            snapshotFlow { rightStickFirstVisible.value }.collect { physical ->
                if (physical < 0) return@collect
                // 首个可视项之后（含自身）最近的可聚焦物理行
                val fwd = focusableIndices.firstOrNull { it >= physical }
                val target = fwd ?: focusableIndices.lastOrNull { it <= physical }
                if (target != null) {
                    val logical = focusableIndices.indexOf(target) + 1 // +1：0 为返回按钮
                    if (logical in 1..lastRow && logical != focusedRow) {
                        suppressAutoScroll = true // 抑制反向滚动（列表已由引擎滚到位）
                        focusedRow = logical
                    }
                }
            }
        }
    }
    // 焦点框可见性：手柄未连接 / 纯触控模式下隐藏所有焦点框（含返回按钮）
    val showFocus = LocalShowFocusIndicators.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // 左摇杆/方向键导航与确认的震动反馈（与主页一致：tick=移动、click=确认/返回）
    val haptics = remember { Haptics(context) }


    Surface(
        // 瀑布屏（曲面屏）安全边距**内建**到此框架根布局：先 fillMaxSize 占满全屏让 Surface
        // 背景色铺到瀑布区两侧/底部（避免外层 windowBackground 透出造成"两侧无颜色"），
        // 再 waterfallSafePadding 压缩内容区。
        // 竖屏生效（横屏两侧曲面已由系统状态栏/导航栏占据，Waterfall.kt 内部自动跳过），
        // 所有复用本框架的子页面（About/Licenses/CompatGuide）自动获得，无需各自调用方传 modifier。
        modifier = modifier
            .fillMaxSize()
            .waterfallSafePadding()
            .onKeyEvent { ev ->
                val n = ev.nativeKeyEvent
                if (n.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (n.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        suppressAutoScroll = false // 方向键须正常滚动到焦点行
                        focusedRow = (focusedRow - 1).coerceAtLeast(0); haptics.tick(); true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        suppressAutoScroll = false
                        focusedRow = (focusedRow + 1).coerceAtMost(lastRow); haptics.tick(); true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                        if (focusedRow == 0) { onBack(); haptics.click() }
                        else if (useBottomAction && focusedRow == lastRow) {
                            bottomAction!!.onClick(); haptics.click()
                        } else {
                            val idx = focusableIndices.getOrNull(focusedRow - 1) ?: return@onKeyEvent true
                            when (val r = rows[idx]) {
                                is SubPageRow.Link -> { r.onClick(); haptics.click() }
                                is SubPageRow.Action -> { r.onClick(); haptics.click() }
                                // Card 为纯展示条目（如 License），聚焦态仅用于滚屏定位，A 键无动作
                                else -> {}
                            }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_BACK,
                    android.view.KeyEvent.KEYCODE_BUTTON_B -> { onBack(); haptics.click(); true }
                    else -> false
                }
            },
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 始终跟随窗口实际尺寸：用户强制旋屏时窗口尺寸即旋转后尺寸，
            // 响应式布局天然适配，无需自行处理方向锁。
            val wide = maxWidth >= SubPageSpec.WideThreshold
            val isPortrait = maxHeight > maxWidth
            val contentWidth = if (wide) SubPageSpec.ContentMaxWidth else maxWidth
            val hPad = Dimens.ContentHorizontal
            val vPad = if (wide) Dimens.md else Dimens.sm
            val titleStyle = if (wide) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.titleMedium
            val subTitleStyle = if (wide) MaterialTheme.typography.bodyMedium
                                else MaterialTheme.typography.bodySmall
            // 选项行水平内边距与列表项一致
            val rowHPad = Dimens.md
            // 选项行上下内边距与设置页 ListItem 对齐：纵横屏均 Dimens.sm(12dp)，
            // 让选项整体"高度/四周间距"观感与设置页一致。
            val rowVPad = Dimens.sm
            // 选项列表文字规格统一
            val rowTitleStyle = MaterialTheme.typography.titleMedium
            val rowDescStyle = MaterialTheme.typography.bodyMedium
            val bottomFocused = showFocus && useBottomAction && focusedRow == lastRow

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = hPad, vertical = vPad)
            ) {
                SubPageTopBar(
                    title = title,
                    onBack = onBack,
                    isBackFocused = showFocus && focusedRow == 0,
                    onBackFocus = { focusedRow = 0 },
                    titleStyle = titleStyle,
                    subTitleStyle = subTitleStyle
                )

                Spacer(Modifier.height(Dimens.sm))

                // 焦点改变 → 列表滚动到对应行（左摇杆/方向键移动焦点后保证可见）。
                // 落在底部按钮时不滚动列表（按钮固定可见）。
                // 右摇杆同步来的焦点变化（suppressAutoScroll=true）跳过：列表已由引擎滚到位，
                // 避免"焦点跟随滚动"反向把列表拉回，造成抖动（归一化关键守卫）。
                LaunchedEffect(focusedRow) {
                    if (suppressAutoScroll) { suppressAutoScroll = false; return@LaunchedEffect }
                    if (useBottomAction && focusedRow == lastRow) return@LaunchedEffect
                    val idx = if (focusedRow == 0) 0
                    else focusableIndices.getOrNull(focusedRow - 1) ?: return@LaunchedEffect
                    runCatching { listState.animateScrollToItem(idx) }
                }

                // 列表区：若底部有固定内容（bottomAction / bottomBar），则限制为剩余可用高度，使底部始终钉在底部
                LazyColumn(
                    state = listState,
                    modifier = if (useBottomAction || bottomBar != null) Modifier.weight(1f) else Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = Dimens.xs, bottom = Dimens.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                ) {
                    itemsIndexed(rows, key = { _, r -> r.key }) { index, row ->
                        val logical = focusableIndices.indexOf(index) // -1 表示不可聚焦
                        val isFocused = showFocus && logical >= 0 && focusedRow == logical + 1
                        RowEnter(index = index) {
                            when (row) {
                                is SubPageRow.Link -> SubPageFocusableRow(
                                    label = row.label,
                                    desc = row.desc,
                                    icon = row.icon,
                                    isFocused = isFocused,
                                    showExternal = true,
                                    hPad = rowHPad, vPad = rowVPad,
                                    titleStyle = rowTitleStyle, descStyle = rowDescStyle,
                                    onClick = { focusedRow = logical + 1; row.onClick() },
                                    onFocus = { focusedRow = logical + 1 }
                                )
                                is SubPageRow.Action -> SubPageFocusableRow(
                                    label = row.label,
                                    desc = row.desc,
                                    icon = row.icon,
                                    isFocused = isFocused,
                                    primary = row.primary,
                                    trailing = row.trailing,
                                    hPad = rowHPad, vPad = rowVPad,
                                    titleStyle = rowTitleStyle, descStyle = rowDescStyle,
                                    onClick = { focusedRow = logical + 1; row.onClick() },
                                    onFocus = { focusedRow = logical + 1 }
                                )
                                is SubPageRow.Card -> if (row.focusable) {
                                    SubPageFocusableCard(
                                        isFocused = isFocused,
                                        hPad = rowHPad, vPad = rowVPad,
                                        onClick = { focusedRow = logical + 1 },
                                        onFocus = { focusedRow = logical + 1 },
                                        content = row.content
                                    )
                                } else {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp, SurfaceTokens.restingBorderColorSoft()
                                        ),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) { Column(
                                        modifier = Modifier.padding(rowHPad, rowVPad),
                                        content = row.content
                                    ) }
                                }
                                is SubPageRow.Static -> Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.xxs),
                                    content = row.content
                                )
                            }
                        }
                    }
                }

                // 底部固定区（不随列表滚动）：
                // - bottomBar（通用技术储备槽）优先：渲染任意自定义内容；
                // - 否则若 bottomAction 存在，渲染标准 MD3 FilledButton 风格按钮（如"完成"）。
                //   使用 Button 而非全宽 Card 卡片，符合 MD3 设计规范：紧凑、右对齐、主题色填充。
                if (bottomBar != null) {
                    Spacer(Modifier.height(Dimens.sm))
                    bottomBar()
                } else if (useBottomAction) {
                    Spacer(Modifier.height(Dimens.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        ReverieFilledButton(
                            onClick = {
                                focusedRow = lastRow
                                bottomAction!!.onClick()
                            },
                            focused = bottomFocused,
                            text = bottomAction!!.label
                        )
                    }
                }
            }
        }
    }
}

/** 逐行入场动画：fadeIn + slideInVertically（错峰），符合 Material Design 列表入场规范。 */
@Composable
private fun RowEnter(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(MotionSpec.staggerDelay(index))
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MotionSpec.Medium, label = "rowAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (shown) 0f else 24f,
        animationSpec = MotionSpec.Medium, label = "rowOffset"
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha; translationY = offsetY }) { content() }
}

/**
 * 统一标题栏：返回按钮（聚焦态主题色高亮）+ 标题 + 右侧"B 返回"键位提示。
 * 与 CompatGuide / About / Licenses 完全一致。
 */
@Composable
private fun SubPageTopBar(
    title: String,
    onBack: () -> Unit,
    isBackFocused: Boolean,
    onBackFocus: () -> Unit,
    titleStyle: androidx.compose.ui.text.TextStyle,
    subTitleStyle: androidx.compose.ui.text.TextStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isBackFocused) SurfaceTokens.focusBgStrong()
                    else Color.Transparent
                )
                .clickable { onBackFocus(); onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(Dimens.sm))
        Text(
            title,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        // 右侧键位提示：仅手柄连接时显示，统一使用 KeyBadge 风格（不暴露裸文本）。
        GamepadHintBar(
            modifier = Modifier.padding(end = Dimens.xs),
            content = { Hint(KeyToken.B, "返回") }
        )
    }
}

/**
 * 统一可聚焦行：焦点态 = primary 边框 + 背景提升（兼容 CompatGuide 风格），
 * 鼠标/触控整行可点。primary=true 时表现为实心主按钮（如"完成"）。
 */
@Composable
private fun SubPageFocusableRow(
    label: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isFocused: Boolean,
    showExternal: Boolean = false,
    primary: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    hPad: Dp = Dimens.md,
    vPad: Dp = Dimens.sm,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall,
    descStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    val bg = when {
        primary -> MaterialTheme.colorScheme.primary
        isFocused -> SurfaceTokens.focusBg()
        else -> SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default)
    }
    // 焦点框统一走 focusBorder 助手（手柄未连接时不绘制）；resting 边框保持 1dp 浅描边。
    val restingBorder = if (primary) null
    else androidx.compose.foundation.BorderStroke(
        1.dp, SurfaceTokens.restingBorderColor()
    )
    val labelColor = if (primary) MaterialTheme.colorScheme.onPrimary
    else if (isFocused) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface
    val descColor = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
    else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(Dimens.md))
            .focusBorder(isFocused, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.md))
            .clickable { onFocus(); onClick() },
        colors = CardDefaults.cardColors(containerColor = bg),
        border = restingBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad, vertical = vPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon, contentDescription = null,
                    tint = if (primary) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(Dimens.sm))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = titleStyle,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                    color = labelColor,
                    maxLines = 1
                )
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(desc, style = descStyle, color = descColor, maxLines = 1)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(Dimens.xs))
                trailing()
            }
            if (showExternal) {
                Spacer(Modifier.width(Dimens.xs))
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = "打开外部链接",
                    tint = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 统一可聚焦卡片（License 条目）：焦点态同 SubPageFocusableRow 风格。 */
@Composable
private fun SubPageFocusableCard(
    isFocused: Boolean,
    hPad: Dp = Dimens.md,
    vPad: Dp = Dimens.sm,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = if (isFocused) MaterialTheme.colorScheme.primaryContainer
    else SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle)
    // 焦点框统一走 focusBorder 助手；resting 边框保持 1dp 浅描边。
    val restingBorder = androidx.compose.foundation.BorderStroke(
        1.dp, SurfaceTokens.restingBorderColorSoft()
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(Dimens.md))
            .focusBorder(isFocused, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.md))
            .clickable { onFocus(); onClick() },
        colors = CardDefaults.cardColors(containerColor = bg),
        border = restingBorder
    ) {
        Column(modifier = Modifier.padding(hPad, vPad)) { content() }
    }
}

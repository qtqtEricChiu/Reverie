package cn.mocabolka.run.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cn.mocabolka.run.ui.components.ReverieOutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.PulseSpec
import cn.mocabolka.run.ui.theme.rememberPulse
import kotlinx.coroutines.delay
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.launcher.relativeLastUsed
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LocalShowFocusIndicators
import cn.mocabolka.run.ui.theme.SurfaceTokens
import cn.mocabolka.run.ui.theme.FavoriteStar
import cn.mocabolka.run.ui.theme.focusBorder
import cn.mocabolka.run.ui.components.KeyBadge
import cn.mocabolka.run.ui.components.KeyBadgeVariant
import cn.mocabolka.run.ui.components.KeyToken
import cn.mocabolka.run.ui.components.rememberFocusScroller
import cn.mocabolka.run.ui.components.focusScrollerContainer
import cn.mocabolka.run.ui.components.focusScrollerItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 详情面板（21:9 宽屏右侧 / 中等屏底部浮层共享）。
 *
 * 适配 280dp 宽窄列布局：
 * - 信息卡片内部改两列 Grid 节省垂直空间
 * - "启动"主按钮使用 `defaultMinSize` 避免被裁切
 * - 整个面板可独立滚动，避免内容超出
 */
@Composable
fun AppDetailPanel(
    app: AppModel?,
    isFavorite: Boolean,
    onToggleFavorite: (String) -> Unit,
    onLaunch: (AppModel) -> Unit = {},
    launching: Boolean = false,
    badgeCount: Int = 0,
    searchInfo: String? = null,
    onOpenInfo: (AppModel) -> Unit = {},
    onUninstall: (AppModel) -> Unit = {},
    onForceStop: (AppModel) -> Unit = {},
    showUsageHint: Boolean = false,
    /** 今日使用时长（ms），>0 时显示进度条（R11-1）。 */
    todayUsage: Long = 0L,
    /** 本周使用时长（ms），用于对比进度条（R11-1）。 */
    weeklyUsage: Long = 0L,
    /** 玻璃拟态：降低表面不透明度并叠加顶部高光，营造磨砂玻璃质感（R12）。 */
    glass: Boolean = true,
    /** 当前手柄焦点所在的详情面板内按钮索引（-1 = 无；0=启动 / 1=收藏 / 2=信息 / 3=卸载 / 4=强制停止） */
    focusedButtonIndex: Int = -1,
    /** 焦点按钮改变回调（HomeScreen 用于驱动 5 个按钮之间的十字上下移动） */
    onFocusedButtonChange: (Int) -> Unit = {},
    /** 减少动态效果（无障碍）：冻结入场/焦点光晕动画。 */
    reduceMotion: Boolean = false,
    /** 详情面板滚动状态（lift 到上层，供右摇杆在焦点位于右栏时驱动滚动）。 */
    scrollState: ScrollState = androidx.compose.foundation.rememberScrollState(),
    /** 竖屏底部抽屉：限制最大高度（如 0.55f 屏高），默认不约束（宽屏右栏 fillMaxHeight）。 */
    maxHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    /** 穿透背景模式（系统壁纸作为底）：详情面板 surface 进一步降到极低 alpha，
     *  真正透出壁纸，配合 glass 提供"玻璃浮于壁纸之上"视觉。 */
    wallpaperBehind: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 归一化「焦点移动 → 滚动到可见」引擎（需求5）：
    // 详情面板是普通 ScrollState 容器，焦点(index)移动后须把焦点按钮滚入可视区，
    // 否则焦点移出窗口不可见（之前缺失，导致左摇杆在详情栏上下移动焦点时窗口不跟随）。
    // 卸载/强停已隐藏，可聚焦项收窄为 3（启动/收藏/信息），与 HomeScreen.DETAIL_BUTTON_COUNT 对齐。
    val focusScroller = rememberFocusScroller(scrollState, 3)
    LaunchedEffect(focusedButtonIndex) {
        focusScroller.bringIntoView(focusedButtonIndex)
    }

    // 焦点框可见性：手柄未连接 / 纯触控模式下不绘制任何焦点框（与全局规则一致）。
    // effectiveFocus 仅当 LocalShowFocusIndicators（=手柄已连接）为真时取 focusedButtonIndex，
    // 否则置 -1，使所有详情按钮 / 启动圆钮的焦点态一律隐藏（A 键位浮块已由 LocalShowFocusIndicators 统一守卫）。
    val effectiveFocus = if (LocalShowFocusIndicators.current) focusedButtonIndex else -1
    val surfaceColor = when {
        glass && wallpaperBehind -> SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle)
        glass -> SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle)
        else -> MaterialTheme.colorScheme.surface
    }
    // 子组件统一背景色：穿透模式时更透明（透出壁纸），配合 glass 提供"玻璃浮于壁纸之上"视觉
    val subBg = if (wallpaperBehind)
        SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle)
    else
        SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default)
    val subBgAlt = if (wallpaperBehind)
        SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle)
    else
        SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default)

    // 入场缩放/淡入（随焦点应用切换重放）
    var appeared by remember(app?.packageName) { mutableStateOf(false) }
    LaunchedEffect(app?.packageName) {
        appeared = false
        if (reduceMotion || app == null) appeared = true
        else { delay(70); appeared = true }
    }
    val entA by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.Medium, label = "detailEntA"
    )
    val entY by animateFloatAsState(
        targetValue = if (appeared) 0f else 16f,
        animationSpec = MotionSpec.Medium, label = "detailEntY"
    )

    Column(
        modifier = modifier
            .then(if (maxHeight != androidx.compose.ui.unit.Dp.Unspecified) Modifier.heightIn(max = maxHeight) else Modifier.fillMaxHeight())
            .graphicsLayer { alpha = entA; translationY = entY }
            .clip(RoundedCornerShape(Dimens.lg))
            .background(surfaceColor)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(Dimens.lg)
            )
            .padding(Dimens.md)
            .focusScrollerContainer(focusScroller)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs)
    ) {
        // 任务 4：移除详情面板顶部细横线（视觉多余）
        if (app == null) {
            // 空状态
            // C3-4：空状态图标随主题色呼吸，弱化"无内容"的空白感
            val emptyPulse = rememberPulse(true, reduceMotion, periodMs = PulseSpec.PERIOD_SLOW_MS)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Dimens.lg))
                            .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default))
                            .padding(Dimens.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Gamepad,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + emptyPulse * 0.5f),
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer { scaleX = 1f + emptyPulse * 0.06f; scaleY = 1f + emptyPulse * 0.06f }
                        )
                    }
                    Text(
                        text = "点击应用查看详情",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "选中后可启动或收藏",
                        style = MaterialTheme.typography.bodySmall,
                        color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong)
                    )
                }
            }
            return
        }

        // ─── 头部：图标 + 标题 + 分类标签 + 启动圆钮（右侧） ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            // 圆角遮罩：外层 Box 统一裁剪为圆角矩形，内层图标铺满，
            // 无论应用图标原形状（方形/圆形/异形）均呈现一致的圆角遮罩效果。
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(Dimens.TileCornerRadius))
                    .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default)),
                contentAlignment = Alignment.Center
            ) {
                if (app.icon.width > 0) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Dimens.xxs)
                            .clip(RoundedCornerShape(Dimens.TileCornerRadius - 2.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = categoryIconForText(app.categoryText),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.size(3.dp))
                            Text(
                                text = app.categoryText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = FavoriteStar,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            // 启动大圆钮（右侧）：点击启动；右上角 A 手柄键位浮块（仅聚焦该钮时出现，焦点跟随）
            Box(Modifier.focusScrollerItem(focusScroller, 0)) {
            LaunchCircleButton(
                enabled = app.installed && !launching,
                launching = launching,
                focused = effectiveFocus == 0,
                reduceMotion = reduceMotion,
                color = MaterialTheme.colorScheme.primary,
                showA = LocalShowFocusIndicators.current && effectiveFocus == 0,
                onClick = { onFocusedButtonChange(0); onLaunch(app) }
            )
            }
        }

        // 搜索命中提示
        if (searchInfo != null) {
            Text(
                text = searchInfo,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ─── 信息卡片：两列布局，节省垂直空间 ─────────
        InfoCard(
            app = app,
            badgeCount = badgeCount,
            showUsageHint = showUsageHint
        )

        // ─── 使用统计面板（R11-1）：今日 / 本周时长进度条 ──
        if (todayUsage > 0L || weeklyUsage > 0L) {
            UsageStatsCard(
                todayUsage = todayUsage,
                weeklyUsage = weeklyUsage
            )
        }

        // ─── 操作区：弹性空间占满剩余高度 ─────────
        Spacer(Modifier.weight(1f))

        // 收藏 + 信息：一行两个，按栏宽自适应（weight 均分）。
        // R2 焦点视觉归一：移除原 FocusGlowBox 的 blur 脉冲光晕（"丑"的根因），
        // 改由 ReverieOutlinedButton 的 focused 描边主导清晰焦点态，与 LaunchCircleButton
        // 新焦点环语言统一；A 浮块仅聚焦时出现在右上角（焦点跟随）。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Box(modifier = Modifier.weight(1f).focusScrollerItem(focusScroller, 1)) {
                ReverieOutlinedButton(
                    onClick = { onFocusedButtonChange(1); onToggleFavorite(app.packageName) },
                    modifier = Modifier.fillMaxWidth(),
                    focused = effectiveFocus == 1,
                    icon = Icons.Filled.Star,
                    text = if (isFavorite) "已收藏" else "收藏"
                )
                if (LocalShowFocusIndicators.current && effectiveFocus == 1) {
                    KeyBadge(
                        token = KeyToken.A,
                        variant = KeyBadgeVariant.Solid,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).focusScrollerItem(focusScroller, 2)) {
                ReverieOutlinedButton(
                    onClick = { onFocusedButtonChange(2); onOpenInfo(app) },
                    enabled = app.installed,
                    modifier = Modifier.fillMaxWidth(),
                    focused = effectiveFocus == 2,
                    icon = Icons.Filled.Info,
                    text = "信息"
                )
                if (LocalShowFocusIndicators.current && effectiveFocus == 2) {
                    KeyBadge(
                        token = KeyToken.A,
                        variant = KeyBadgeVariant.Solid,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    )
                }
            }
        }

        // ─── 卸载 / 强制停止 按钮：暂时隐藏（用户要求）──────────────
        // 原因：当前"卸载"仅能跳转系统 ACTION_DELETE 页、"强制停止"仅能跳转系统
        // "应用信息"页，无法在 App 内直接执行卸载 / 结束进程（缺少系统级权限）。
        // 在具备"不跳设置即可卸载 / 强行停止进程"的能力前，先隐藏这两个入口，
        // 避免误导用户。对应焦点导航已把 DETAIL_BUTTON_COUNT 收窄到 3（启动/收藏/信息）。
        // 若日后恢复，需同步 HomeScreen.DETAIL_BUTTON_COUNT 与 A 键 when 分支 3/4。
        //
        // Spacer(Modifier.height(Dimens.xs))
        // Box(Modifier.focusScrollerItem(focusScroller, 3)) {
        //     ReverieOutlinedButton(
        //         onClick = { onFocusedButtonChange(3); onUninstall(app) },
        //         enabled = app.installed,
        //         modifier = Modifier.fillMaxWidth(),
        //         focused = effectiveFocus == 3,
        //         danger = true,
        //         icon = Icons.Filled.Delete,
        //         text = "卸载应用"
        //     )
        //     if (LocalShowFocusIndicators.current && effectiveFocus == 3) {
        //         KeyBadge(
        //             token = KeyToken.A,
        //             variant = KeyBadgeVariant.Solid,
        //             modifier = Modifier
        //                 .align(Alignment.TopEnd)
        //                 .offset(x = 4.dp, y = (-4).dp)
        //         )
        //     }
        // }
        //
        // Box(Modifier.focusScrollerItem(focusScroller, 4)) {
        //     ReverieOutlinedButton(
        //         onClick = { onFocusedButtonChange(4); onForceStop(app) },
        //         enabled = app.installed,
        //         modifier = Modifier.fillMaxWidth(),
        //         focused = effectiveFocus == 4,
        //         icon = Icons.Filled.Stop,
        //         text = "强制停止"
        //     )
        //     if (LocalShowFocusIndicators.current && effectiveFocus == 4) {
        //         KeyBadge(
        //             token = KeyToken.A,
        //             variant = KeyBadgeVariant.Solid,
        //             modifier = Modifier
        //                 .align(Alignment.TopEnd)
        //                 .offset(x = 4.dp, y = (-4).dp)
        //         )
        //     }
        // }
    }
}

/**
 * 信息卡片：两列布局，节省垂直空间
 * - 左列：安装日期 + 上次游玩
 * - 右列：版本 + 未读数
 * - 包名独占一行
 */
@Composable
private fun InfoCard(
    app: AppModel,
    badgeCount: Int,
    showUsageHint: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 第一行：安装日期 | 上次游玩
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
            // 安装日期
            val installed = app.firstInstallTime > 0
            InfoCell(
                icon = Icons.Filled.Info,
                label = "安装",
                value = if (installed) {
                    Instant.ofEpochMilli(app.firstInstallTime)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yy-MM-dd"))
                } else "未安装",
                modifier = Modifier.weight(1f)
            )
            // 上次游玩
            val last = relativeLastUsed(app.lastUsedTime)
            InfoCell(
                icon = Icons.Filled.PlayArrow,
                label = "上次",
                value = last ?: "—",
                modifier = Modifier.weight(1f)
            )
        }

        // 第二行：版本 | 通知未读
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
            InfoCell(
                icon = Icons.Filled.Article,
                label = "版本",
                value = app.versionName.ifBlank { "—" },
                modifier = Modifier.weight(1f)
            )
            InfoCell(
                icon = Icons.Filled.Star,
                label = "未读",
                value = if (badgeCount > 0) "$badgeCount" else "无",
                valueTint = if (badgeCount > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        // 未授权使用情况提示
        if (showUsageHint && app.lastUsedTime == 0L) {
            Text(
                text = "提示：开启使用情况访问可显示最近游玩",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 包名（独占一行，等宽字体）
        Text(
            text = app.packageName,
            style = MaterialTheme.typography.labelSmall,
            color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
    }
}

/**
 * 启动大圆钮：置于详情面板头部右侧，点击启动应用。
 * 焦点态（手柄）显示呼吸光晕 + 主题色描边；右上角常驻 A 键位浮块（仅手柄连接时）。
 */
@Composable
private fun LaunchCircleButton(
    enabled: Boolean,
    launching: Boolean,
    focused: Boolean,
    reduceMotion: Boolean,
    color: Color,
    /** 是否显示 A 键位浮块（仅当手柄连接且本钮聚焦时由调用方传入 true，实现焦点跟随）。 */
    showA: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 焦点视觉重做（需求1）：去掉此前"丑"的 blur 呼吸光晕 + 跳动 scale，
    // 改为干净的 MD3 焦点环——聚焦时圆钮轻微放大(1.06x) + 双层描边（外圈半透明光环 + 内圈实色环），
    // 平滑动画（无脉冲抖动），与 outlined 按钮的 wrapFocusBorder 焦点语言统一。
    val focusScale by animateFloatAsState(
        targetValue = if (focused && !reduceMotion) 1.06f else 1f,
        animationSpec = MotionSpec.Fast, label = "launchFocusScale"
    )
    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        // 外圈柔和光环：聚焦时显示一层半透明主题色环，界定焦点范围（无 blur、无脉冲）
        if (focused) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = Dimens.FocusSurfaceAlpha))
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { scaleX = focusScale; scaleY = focusScale }
                .clip(CircleShape)
                .background(if (enabled) color else SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Strong))
                .focusBorder(focused, MaterialTheme.colorScheme.onPrimary, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "启动",
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
        }
        // A 角标：仅手柄连接且本钮聚焦、且按钮可用时显示（焦点跟随，非常驻）；
        // 未安装(disabled)时即便聚焦也不显示 A 浮块，避免误导用户按 A 却无法启动。
        if (showA && enabled) {
            KeyBadge(
                token = KeyToken.A,
                variant = KeyBadgeVariant.Solid,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
            )
        }
    }
}

/** 信息单元：图标 + 标签（小字）+ 值（主体） */
@Composable
private fun InfoCell(
    icon: ImageVector,
    label: String,
    value: String,
    valueTint: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon, contentDescription = null,
            tint = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium),
            modifier = Modifier.size(11.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = valueTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 使用统计卡片：今日 / 本周时长进度条（R11-1）。 */
@Composable
private fun UsageStatsCard(
    todayUsage: Long,
    weeklyUsage: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.sm))
            .background(SurfaceTokens.focusBg())
            .padding(Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Filled.Schedule, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "使用统计",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 今日时长 + 进度条（相对本周均值：today / (weekly/7)，封顶 100%）
        val avgDaily = if (weeklyUsage > 0) weeklyUsage / 7 else todayUsage
        val todayRatio = if (avgDaily > 0) (todayUsage.toFloat() / avgDaily).coerceIn(0f, 1f) else 0f
        UsageBar(
            label = "今日",
            valueText = formatUsage(todayUsage),
            ratio = todayRatio
        )

        // 本周时长 + 进度条（相对一个参考上限 7 小时）
        val weekRatio = (weeklyUsage.toFloat() / (7 * 3600_000L)).coerceIn(0f, 1f)
        UsageBar(
            label = "本周",
            valueText = formatUsage(weeklyUsage),
            ratio = weekRatio
        )
    }
}

/** 单条使用时长进度条。 */
@Composable
private fun UsageBar(
    label: String,
    valueText: String,
    ratio: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = ratio,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        )
    }
}

/** 时长格式化：秒 → "X分" / "X小时Y分" / "X秒"。 */
private fun formatUsage(ms: Long): String {
    val totalSec = ms / 1000
    if (totalSec < 60L) return "${totalSec}秒"
    val min = totalSec / 60
    if (min < 60L) return "${min}分"
    val hour = min / 60
    val rem = min % 60
    return if (rem == 0L) "${hour}小时" else "${hour}小时${rem}分"
}

/** 分类标签（字符串，含 AI 覆盖）→ 图标（任务 4：详情页标记跟随 categoryText）。 */
private fun categoryIconForText(categoryText: String): ImageVector = when (categoryText) {
    "游戏" -> Icons.Filled.Gamepad
    "影视" -> Icons.Filled.Movie
    "音乐" -> Icons.Filled.MusicNote
    "图片" -> Icons.Filled.Image
    "社交" -> Icons.Filled.People
    "资讯" -> Icons.Filled.Article
    "地图" -> Icons.Filled.Map
    "效率" -> Icons.Filled.Work
    else -> Icons.Filled.Android
}

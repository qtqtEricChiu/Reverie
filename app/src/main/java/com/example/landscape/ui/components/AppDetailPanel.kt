package cn.mocabolka.run.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
    val surfaceColor = when {
        glass && wallpaperBehind -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        glass -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        else -> MaterialTheme.colorScheme.surface
    }
    // 子组件统一背景色：穿透模式时更透明（透出壁纸），配合 glass 提供"玻璃浮于壁纸之上"视觉
    val subBg = if (wallpaperBehind)
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
    val subBgAlt = if (wallpaperBehind)
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)

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
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            // 启动大圆钮（右侧）：点击启动；右上角 A 手柄键位浮块
            LaunchCircleButton(
                enabled = app.installed && !launching,
                launching = launching,
                focused = focusedButtonIndex == 0,
                reduceMotion = reduceMotion,
                color = MaterialTheme.colorScheme.primary,
                onClick = { onLaunch(app) }
            )
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

        // 收藏 + 信息：一行两个，按栏宽自适应（weight 均分）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            FocusGlowBox(
                focused = focusedButtonIndex == 1,
                reduceMotion = reduceMotion,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            ) {
            OutlinedButton(
                onClick = { onToggleFavorite(app.packageName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
                    .then(
                        if (focusedButtonIndex == 1) Modifier.border(
                            2.5.dp, MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(Dimens.sm)
                        ) else Modifier
                    ),
                shape = RoundedCornerShape(Dimens.sm),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isFavorite) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = if (isFavorite) "已收藏" else "收藏",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            }
            FocusGlowBox(
                focused = focusedButtonIndex == 2,
                reduceMotion = reduceMotion,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            ) {
            OutlinedButton(
                onClick = { onOpenInfo(app) },
                enabled = app.installed,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
                    .then(
                        if (focusedButtonIndex == 2) Modifier.border(
                            2.5.dp, MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(Dimens.sm)
                        ) else Modifier
                    ),
                shape = RoundedCornerShape(Dimens.sm),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("信息", style = MaterialTheme.typography.labelMedium)
            }
            }
        }

        // 卸载按钮（独占一行，红色危险色）
        // C4-9：危险操作与常规操作之间加分隔，视觉上区分"破坏性动作"区
        Spacer(Modifier.height(Dimens.xs))
        FocusGlowBox(
            focused = focusedButtonIndex == 3,
            reduceMotion = reduceMotion,
            color = MaterialTheme.colorScheme.error
        ) {
        OutlinedButton(
            onClick = { onUninstall(app) },
            enabled = app.installed,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 36.dp)
                .then(
                    if (focusedButtonIndex == 3) Modifier.border(
                        2.5.dp, MaterialTheme.colorScheme.error,
                        RoundedCornerShape(Dimens.sm)
                    ) else Modifier
                ),
            shape = RoundedCornerShape(Dimens.sm),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text("卸载应用", style = MaterialTheme.typography.labelSmall)
        }
        }

        // 强制停止按钮（R11-7，跳转系统"应用信息"页内含强制停止；独占一行）
        FocusGlowBox(
            focused = focusedButtonIndex == 4,
            reduceMotion = reduceMotion,
            color = MaterialTheme.colorScheme.primary
        ) {
        OutlinedButton(
            onClick = { onForceStop(app) },
            enabled = app.installed,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 36.dp)
                .then(
                    if (focusedButtonIndex == 4) Modifier.border(
                        2.5.dp, MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(Dimens.sm)
                    ) else Modifier
                ),
            shape = RoundedCornerShape(Dimens.sm),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text("强制停止", style = MaterialTheme.typography.labelSmall)
        }
        }
    }
}

/**
 * 焦点呼吸光晕：聚焦时在内容之后渲染一层脉冲放大的主题色光晕（外溢出元素边界）。
 * [reduceMotion] 开启时冻结为静态常量，零开销。
 */
@Composable
private fun FocusGlowBox(
    focused: Boolean,
    reduceMotion: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier) {
        if (focused) {
            val pulse = rememberPulse(true, reduceMotion)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = pulse
                        val s = 1f + (pulse - PulseSpec.BASE) * 0.08f
                        scaleX = s
                        scaleY = s
                    }
                    .background(color)
                    .blur(14.dp)
            )
        }
        content()
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
    }
}

/** 启动按钮上的 "A" 角标：仅手柄连接时由 AppDetailPanel 渲染，提示用户按 A 启动 */
@Composable
private fun LaunchKeyBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "A",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .then(if (focused) Modifier.border(2.5.dp, color, CircleShape) else Modifier)
    ) {
        FocusGlowBox(focused = focused, reduceMotion = reduceMotion, color = color) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (enabled) color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
        }
        // A 角标：仅手柄连接时显示在圆钮右上角
        if (cn.mocabolka.run.gamepad.GamepadDetector.isGamepadConnected()) {
            LaunchKeyBadge(
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(11.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
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

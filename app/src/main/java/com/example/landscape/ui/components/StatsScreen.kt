package cn.mocabolka.run.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import androidx.compose.material3.Surface
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.launcher.UsageStatsRepository
import cn.mocabolka.run.ui.theme.AnimatedNumberText
import cn.mocabolka.run.ui.theme.ChartAccent
import cn.mocabolka.run.ui.theme.ChartNormal
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.TrendDown
import cn.mocabolka.run.ui.theme.TrendUp
import cn.mocabolka.run.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

/** 统计周期：日 / 周 / 月 / 年。 */
enum class StatsPeriod(val label: String) {
    DAILY("日"), WEEKLY("周"), MONTHLY("月"), YEARLY("年")
}

/**
 * 统计页（仿 Tai 精髓：周期 Tab + 3 指标卡 + 柱状图 + 应用排行）。
 * 完全 Compose 自绘柱状图 / 饼图，Material Design 风格 + 进入动画。
 * 不复制 Tai 外观，只复用其"用时长-周期-分布"信息层次。
 */
/** 日期锚点变更桥接：统计页日期选择器打开时，由 HomeScreen 全局手柄事件循环
 *  将 导航/确认/取消 路由到本桥接，驱动选择器内部焦点移动（完全手柄适配）。
 *  与设置页 SettingsControlBridge 同一设计思路。 */
class StatsDatePickerBridge(
    var open: Boolean = false,
    var onMove: (Direction) -> Unit = {},
    var onConfirm: () -> Unit = {},
    var onCancel: () -> Unit = {}
)

@Composable
fun StatsScreen(
    viewModel: HomeViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    /** 当前统计周期（外部 HomeScreen 持有 source-of-truth，状态栏下方 PeriodTabsRow 同步显示）。 */
    period: StatsPeriod = StatsPeriod.DAILY,
    /** 统计锚点日（ms，0:00 当天）：日视图=当天 / 周视图=所在周 / 月视图=所在月 / 年视图=所在年。 */
    anchorMs: Long = System.currentTimeMillis(),
    /** 锚点变更回调（外部 HomeScreen 持有 source-of-truth）。 */
    onAnchorChange: (Long) -> Unit = {},
    /** 日期选择器是否打开（由 HomeScreen 的 LS 键 / 本页按钮共同驱动）。 */
    datePickerOpen: Boolean = false,
    /** 日期选择器开合状态变更回调。 */
    onDatePickerOpenChange: (Boolean) -> Unit = {},
    /** 日期选择器手柄事件桥接（打开时由 HomeScreen 路由手柄事件到此处）。 */
    datePickerBridge: StatsDatePickerBridge = StatsDatePickerBridge(),
    /** 减少动态效果（无障碍）：冻结计数/入场动画。 */
    reduceMotion: Boolean = false,
    /** 左栏（总计/图表）滚动状态：宽屏双栏时由右摇杆驱动滚动（任务 14/15）。 */
    leftScrollState: ScrollState = rememberScrollState(),
    /** 排行列表焦点索引（任务 16：左摇杆上下移动焦点）。 */
    focusIndex: Int = 0,
    /** 手柄是否已连接：仅此为真时显示 LB/RB 与十字键位浮块。 */
    gamepadConnected: Boolean = false,
    /** 当前焦点是否在左栏（宽屏双栏）：用于十字左/右骑缝浮块方向。 */
    leftFocused: Boolean = true,
    /** 实际排行条目数上报（HomeScreen 用于限制左摇杆焦点上限，避免空行焦点）。 */
    onEntryCountChange: (Int) -> Unit = {},
    /** 周期切换回调（PeriodTabsRow 触控点击 / 父级 LB/RB 都路由到这里）。 */
    onSelectPeriod: (StatsPeriod) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 周期由外部驱动（HomeScreen 持有），StatsScreen 不再维护内部 var
    val activePeriod = period
    val usageGranted by viewModel.usageGranted.collectAsState()
    val apps by viewModel.apps.collectAsState()
    // 进入统计页时刷新一次（带时间闸门节流，避免每次切 Tab 都全量 sync，R13 省电）
    LaunchedEffect(Unit) { viewModel.refreshReportsThrottled() }

    // 报表按"当前周期 + 锚点日"实时计算（非快照 StateFlow，便于自由选任意日期）
    val anchorKey = remember(anchorMs, activePeriod) { anchorMs to activePeriod }
    val dailyReport = remember(anchorKey) { viewModel.dailyReportFor(dateKeyOf(anchorMs)) }
    val weeklyReport = remember(anchorKey) { viewModel.weeklyReportFor(anchorMs) }
    val monthlyReport = remember(anchorKey) { viewModel.monthlyReportFor(anchorMs) }
    val yearlyReport = remember(anchorKey) { viewModel.yearlyReportFor(anchorMs) }

    val reportEntries = viewModel.collapseSystemApps(
        when (activePeriod) {
            StatsPeriod.DAILY -> dailyReport
            StatsPeriod.WEEKLY -> weeklyReport
            StatsPeriod.MONTHLY -> monthlyReport
            StatsPeriod.YEARLY -> yearlyReport
        }
    )
    // 上报实际排行条目数（含"系统应用"聚合项），供 HomeScreen 限制左摇杆焦点上限
    LaunchedEffect(reportEntries.size) { onEntryCountChange(reportEntries.size) }
    val totalMs = reportEntries.sumOf { it.ms }
    // 指标：排除置底的"系统应用"聚合项，仅统计真实用户应用
    val rankedEntries = reportEntries.filter { it.packageName != cn.mocabolka.run.viewmodel.HomeViewModel.SYSTEM_AGGREGATE_PKG }
    val appCount = rankedEntries.count { it.ms > 0L }
    val longestMs = rankedEntries.firstOrNull()?.ms ?: 0L
    val longestPkg = rankedEntries.firstOrNull()?.packageName
    val longestLabel = longestPkg?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.label } ?: longestPkg ?: "—"

    // 小时柱状图（仅在日周期"今天"使用，其它情况显示对应日序列）
    val isAnchorToday = remember(anchorMs, activePeriod) { viewModel.isAnchorToday(anchorMs) }
    val hourly = remember(activePeriod, isAnchorToday) {
        if (activePeriod == StatsPeriod.DAILY && isAnchorToday) viewModel.hourlyToday() else LongArray(0)
    }
    val daySeries = remember(anchorKey) {
        viewModel.daySeriesFor(activePeriod, anchorMs)
    }

    val listState = listState

    // 任务 11：Stats 页横屏双栏模式（screenWidthDp >= 720 启用）
    // 左 = 总计（指标卡 + 图表）/ 右 = 应用排行 LazyColumn
    val isWideScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720

    // 任务 16：左摇杆焦点跟随——焦点变化 / 周期变化时滚动排行到焦点行
    // 宽屏：右栏排行列表 index 直接对应；窄屏：排行位于单栏 LazyColumn 的第 4 项之后。
    LaunchedEffect(focusIndex, activePeriod, isWideScreen) {
        if (reportEntries.isNotEmpty()) {
            val idx = if (isWideScreen) focusIndex else 4 + focusIndex
            runCatching { listState.scrollToItem(idx.coerceAtLeast(0)) }
        }
    }


    val rightPanelWidth = 360.dp
    val crossBadgeSize = 36.dp
    // 用 BoxWithConstraints 拿到父 Box 实际宽度，骑缝浮块 offset 跟实际布局算（不同屏幕宽度都不再错位）
    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(horizontal = Dimens.md)) {
        if (isWideScreen) {
            // 双栏布局：使用 Box 作为父容器，让 DarkArrowBadge 通过 BoxScope 绝对定位在骑缝。
            Row(modifier = Modifier.fillMaxSize()) {
                // 左栏：总计 + 图表（不可滚动小内容）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                            .verticalScroll(leftScrollState),
                        verticalArrangement = Arrangement.spacedBy(Dimens.md)
                    ) {
                        // 周期滑块 + LB/RB 手柄键位浮块（任务 15/17：移到左栏左上角，仅在手柄连接时显示 LB/RB）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Dimens.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                        ) {
                            if (gamepadConnected) {
                                DarkKeyBadge("LB")
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                PeriodTabsRow(
                                    period = activePeriod,
                                    onSelect = onSelectPeriod
                                )
                            }
                            if (gamepadConnected) {
                                DarkKeyBadge("RB")
                            }
                            // 任务 N：日期指示器按钮（右侧），点击 / LS 打开日期选择
                            DateIndicatorButton(
                                period = activePeriod,
                                anchorMs = anchorMs,
                                onClick = { onDatePickerOpenChange(true) },
                                gamepadConnected = gamepadConnected
                            )
                        }
                        // 标题
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "总计",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.width(Dimens.md))
                            if (!usageGranted) {
                                Text(
                                    text = "未授权使用情况访问",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        // 3 张指标卡（横向并列，等高对齐：任务 16）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.md)
                        ) {
                            MetricCard(
                                title = "总计",
                                value = totalMs,
                                format = { formatHours(it) },
                                unit = "小时",
                                trend = trendArrow(totalMs, activePeriod, weeklyReport.sumOf { it.ms }, monthlyReport.sumOf { it.ms }, yearlyReport.sumOf { it.ms }),
                                accent = MaterialTheme.colorScheme.primary,
                                icon = Icons.Filled.Timer,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MetricCard(
                                title = "应用量",
                                value = appCount.toLong(),
                                format = { it.toString() },
                                unit = "个",
                                trend = null,
                                accent = MaterialTheme.colorScheme.tertiary,
                                icon = Icons.Filled.Apps,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            LongestCard(
                                longestMs = longestMs,
                                longestLabel = longestLabel,
                                accent = MaterialTheme.colorScheme.secondary,
                                icon = Icons.Filled.Star,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        // 图表
                        ChartCard(
                            title = when (activePeriod) {
                                StatsPeriod.DAILY -> "今日各小时使用"
                                StatsPeriod.WEEKLY -> "近 7 天每日使用"
                                StatsPeriod.MONTHLY -> "近 30 天每日使用"
                                StatsPeriod.YEARLY -> "近 12 个月使用"
                            },
                            values = if (activePeriod == StatsPeriod.DAILY) hourly else daySeries,
                            period = activePeriod
                        )
                        Spacer(Modifier.height(Dimens.lg))
                    }
                    // 两栏中间间距
                    Spacer(Modifier.width(Dimens.md))
                    // 右栏：应用排行（独立 LazyColumn 可滚动）
                    Column(
                        modifier = Modifier
                            .width(rightPanelWidth)
                            .fillMaxSize()
                    ) {
                    Text(
                        text = "应用排行",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = Dimens.md, bottom = Dimens.xs)
                    )
                    if (reportEntries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyHint(text = if (usageGranted) "暂无${activePeriod.label}报数据"
                                              else "请在系统设置中开启『使用情况访问』")
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = Dimens.xxs, bottom = Dimens.lg
                            ),
                            verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                        ) {
                            items(reportEntries, key = { it.packageName }) { entry ->
                                AppRankRow(
                                    rank = reportEntries.indexOf(entry) + 1,
                                    entry = entry,
                                    totalMs = totalMs,
                                    label = rankEntryLabel(entry, apps),
                                    icon = apps.firstOrNull { it.packageName == entry.packageName }?.icon,
                                    entranceIndex = reportEntries.indexOf(entry),
                                    reduceMotion = reduceMotion,
                                    focused = reportEntries.indexOf(entry) == focusIndex
                                )
                            }
                        }
                    }
                }
                // 十字左/右骑缝手柄键位浮块：绝对定位在左栏右边缘（骑缝）。
                // 浮块中心对齐左栏右边缘，向左/向右各突出 halfWidth，实现骑缝视觉。
            }
            // 十字左/右骑缝手柄键位浮块：BoxWithConstraints 拿到父 Box 实际宽度，
            // 浮块中心 = 左栏右边缘（中线）。父 Box 中心 = maxWidth/2，左栏右边缘 =
            // maxWidth - rightPanelWidth - Dimens.md，故需 x 偏移 = (maxWidth - rightPanelWidth
            // - Dimens.md) - maxWidth/2 = maxWidth/2 - rightPanelWidth - Dimens.md。
            // 上面 align(Center) 已有 half-bias 效果时再 -crossBadgeSize/2 让浮块自身居中。
            if (gamepadConnected) {
                val halfW = maxWidth / 2f
                val targetCenterX = halfW - rightPanelWidth - Dimens.md
                val centerOffsetX = targetCenterX - halfW
                DarkArrowBadge(
                    leftFocused = leftFocused,
                    modifier = Modifier
                        .size(crossBadgeSize)
                        .align(Alignment.Center)
                        .offset(x = centerOffsetX)
                )
            }
        } else {
            // 中等屏：单栏原版
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = Dimens.md, bottom = Dimens.lg
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                // 周期滑块 + LB/RB 手柄键位浮块（与横屏左栏一致：仅手柄连接时显示）
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                    ) {
                        if (gamepadConnected) {
                            DarkKeyBadge("LB")
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            PeriodTabsRow(
                                period = activePeriod,
                                onSelect = onSelectPeriod
                            )
                        }
                        if (gamepadConnected) {
                            DarkKeyBadge("RB")
                        }
                        // 日期指示器按钮（右侧），点击 / LS 打开日期选择
                        DateIndicatorButton(
                            period = activePeriod,
                            anchorMs = anchorMs,
                            onClick = { onDatePickerOpenChange(true) },
                            gamepadConnected = gamepadConnected
                        )
                    }
                }
                // 3 张关键指标卡：竖屏（非宽屏）改 2 列网格（总计/应用量一行，最长使用占满），避免窄屏挤压变形。
                // 与横屏一致：行用 IntrinsicSize.Max + 各卡 fillMaxHeight 保证总计/应用量/最长等高对齐。
                item {
                    val isNarrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 480
                    if (isNarrow) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Dimens.md)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
                            ) {
                                MetricCard(
                                    title = "总计", value = totalMs,
                                    format = { formatHours(it) }, unit = "小时",
                                    trend = trendArrow(totalMs, activePeriod, weeklyReport.sumOf { it.ms }, monthlyReport.sumOf { it.ms }, yearlyReport.sumOf { it.ms }),
                                    accent = MaterialTheme.colorScheme.primary, icon = Icons.Filled.Timer,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                                MetricCard(
                                    title = "应用量", value = appCount.toLong(),
                                    format = { it.toString() }, unit = "个", trend = null,
                                    accent = MaterialTheme.colorScheme.tertiary, icon = Icons.Filled.Apps,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                            LongestCard(
                                longestMs = longestMs, longestLabel = longestLabel,
                                accent = MaterialTheme.colorScheme.secondary, icon = Icons.Filled.Star,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.md)
                        ) {
                            MetricCard(
                                title = "总计", value = totalMs,
                                format = { formatHours(it) }, unit = "小时",
                                trend = trendArrow(totalMs, activePeriod, weeklyReport.sumOf { it.ms }, monthlyReport.sumOf { it.ms }, yearlyReport.sumOf { it.ms }),
                                accent = MaterialTheme.colorScheme.primary, icon = Icons.Filled.Timer,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MetricCard(
                                title = "应用量", value = appCount.toLong(),
                                format = { it.toString() }, unit = "个", trend = null,
                                accent = MaterialTheme.colorScheme.tertiary, icon = Icons.Filled.Apps,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            LongestCard(
                                longestMs = longestMs, longestLabel = longestLabel,
                                accent = MaterialTheme.colorScheme.secondary, icon = Icons.Filled.Star,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
                item {
                    ChartCard(
                        title = when (activePeriod) {
                            StatsPeriod.DAILY -> "今日各小时使用"
                            StatsPeriod.WEEKLY -> "近 7 天每日使用"
                            StatsPeriod.MONTHLY -> "近 30 天每日使用"
                            StatsPeriod.YEARLY -> "近 12 个月使用"
                        },
                        values = if (activePeriod == StatsPeriod.DAILY) hourly else daySeries,
                        period = activePeriod
                    )
                }
                item {
                    Text(
                        text = "应用排行",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (reportEntries.isEmpty()) {
                    item {
                        EmptyHint(text = if (usageGranted) "暂无${activePeriod.label}报数据" else "请在系统设置中开启『使用情况访问』")
                    }
                } else {
                    items(reportEntries, key = { it.packageName }) { entry ->
                        AppRankRow(
                            rank = reportEntries.indexOf(entry) + 1,
                            entry = entry,
                            totalMs = totalMs,
                            label = rankEntryLabel(entry, apps),
                            icon = apps.firstOrNull { it.packageName == entry.packageName }?.icon,
                            entranceIndex = reportEntries.indexOf(entry),
                            reduceMotion = reduceMotion,
                            focused = reportEntries.indexOf(entry) == focusIndex
                        )
                    }
                }
            }
        }
    }
}

/**
 * 周期 Tab 横排
 */
@Composable
internal fun PeriodTabsRow(period: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.xl))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        StatsPeriod.entries.forEach { p ->
            val selected = p == period
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.xl - 3.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(p) }
                    .padding(vertical = Dimens.xs + 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = p.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────
// 指标卡（Tai 风格：图标方块 + 大字数值 + 单位 + 趋势箭头）
// ───────────────────────────────────────────────────────────────
@Composable
private fun MetricCard(
    title: String,
    value: Long,
    format: (Long) -> String,
    unit: String,
    trend: Trend?,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.30f),
                shape = RoundedCornerShape(Dimens.lg)
            )
            .padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs)
    ) {
        // 任务 10：移除 MetricCard 顶部细横线
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent, accent.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Dimens.sm))
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedNumberText(
                value = value,
                format = format,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        // 趋势换行（横屏不与时间挤在一行）
        if (trend != null) {
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        (if (trend.up) TrendUp else TrendDown)
                            .copy(alpha = 0.18f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    if (trend.up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    null,
                    tint = if (trend.up) TrendUp else TrendDown,
                    modifier = Modifier.size(Dimens.IconXxs)
                )
                Spacer(Modifier.width(Dimens.xxs - 1.dp))
                Text(
                    text = "${trend.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (trend.up) TrendUp else TrendDown
                )
            }
        }
    }
}

private data class Trend(val up: Boolean, val percent: Int)

/**
 * 「最长使用」指标卡（C4-1）：展示单个应用名 + 时长，比孤立数值更具信息量。
 * 应用名过长时省略，时长用 AnimatedNumberText 缓动。
 */
@Composable
private fun LongestCard(
    longestMs: Long,
    longestLabel: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.30f),
                shape = RoundedCornerShape(Dimens.lg)
            )
            .padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs)
    ) {
        // 任务 10：移除 LongestCard 顶部细横线
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Dimens.sm))
            Text("最长使用", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = longestLabel,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedNumberText(
                value = longestMs,
                format = { formatHours(it) },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(4.dp))
            Text("小时", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/**
 * 趋势环比（C4-2）：将本期总时长与上一可比周期均值比较，给出上升/下降百分比。
 * - 日：对比近 7 天日均
 * - 周：对比近 30 天周均（月总量 / 4）
 * - 月：对比近一年月均（年总量 / 12）
 * - 年：无更长基准，返回 null
 * 基准为 0 时无法计算，返回 null。
 */
private fun trendArrow(totalMs: Long, period: StatsPeriod, weeklyTotal: Long, monthlyTotal: Long, yearlyTotal: Long): Trend? {
    val base = when (period) {
        StatsPeriod.DAILY -> if (weeklyTotal > 0) weeklyTotal / 7 else 0L
        StatsPeriod.WEEKLY -> if (monthlyTotal > 0) monthlyTotal / 4 else 0L
        StatsPeriod.MONTHLY -> if (yearlyTotal > 0) yearlyTotal / 12 else 0L
        StatsPeriod.YEARLY -> return null
    }
    if (base <= 0L) return null
    val diff = totalMs - base
    val percent = ((diff.toFloat() / base) * 100).toInt()
    // 差异过小（<3%）视为持平，不显示趋势
    if (percent in -3..3) return null
    return Trend(up = diff >= 0, percent = kotlin.math.abs(percent))
}

// ───────────────────────────────────────────────────────────────
// 图表卡：自绘柱状图（带动画）
// ───────────────────────────────────────────────────────────────
@Composable
private fun ChartCard(
    title: String,
    values: LongArray,
    period: StatsPeriod
) {
    val maxV = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    // 高亮索引稳定化（R13）：避免 currentHour() 每次重组导致整张图重建 30 个动画 State
    val highlightIndex = remember(period) {
        if (period == StatsPeriod.DAILY) currentHour() else values.size - 1
    }
    // 每根柱子独立错峰生长（GPU 合成，零重组开销）
    // values 由父级 remember(period) 提供，稳定不变；本组件仅在 values 变化时重建动画 State
    val animatedRatios = values.mapIndexed { i, v ->
        val ratio = (v.toFloat() / maxV).coerceIn(0f, 1f)
        animateFloatAsState(
            targetValue = ratio,
            animationSpec = tween(600, delayMillis = MotionSpec.staggerDelay(i).toInt()),
            label = "bar$i"
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
                RoundedCornerShape(Dimens.lg)
            )
            .padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        // 竖屏（窄屏）纵向空间充裕，图表加高到 220dp 更易读；横屏/宽屏保持 180dp。
        val cfg = LocalConfiguration.current
        val chartHeight = if (cfg.screenWidthDp < cfg.screenHeightDp) 220.dp else 180.dp
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val w = maxWidth
            val h = maxHeight
            val n = values.size.coerceAtLeast(1)
            val barWidth = w.value / n * 0.6f
            val gap = w.value / n * 0.4f
            Canvas(modifier = Modifier.fillMaxSize()) {
                val accent = ChartAccent
                val normal = ChartNormal
                // 水平虚线（参考 Tai：30/60/100% 虚线）
                val dashPath = Path()
                listOf(0.33f, 0.66f, 1.0f).forEach { ratio ->
                    val y = size.height * (1f - ratio)
                    dashPath.moveTo(0f, y); dashPath.lineTo(size.width, y)
                }
                drawPath(
                    path = dashPath,
                    color = ChartAccent.copy(alpha = 0.18f),
                    style = Stroke(width = 1.2f)
                )
                values.forEachIndexed { i, v ->
                    val ratio = animatedRatios[i].value
                    val barH = size.height * ratio
                    val x = i * (barWidth + gap) + gap / 2f
                    val y = size.height - barH
                    val isHi = i == highlightIndex
                    drawRect(
                        color = if (isHi) accent else normal.copy(alpha = 0.65f),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH)
                    )
                }
            }
            // 数值标签（最大值线）
            Text(
                text = "${(maxV / 60_000)}分钟",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFEC4899),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        // X 轴刻度
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val n = values.size
            if (n == 24) (0..23).forEach { i ->
                if (i % 3 == 0) Text(
                    "$i",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) else Spacer(Modifier.width(1.dp))
            } else {
                Text("0", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${n / 2}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${n - 1}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────
// 应用排行行：图标 + 排名 + 名称 + 进度条 + 时长
// ───────────────────────────────────────────────────────────────
@Composable
private fun AppRankRow(
    rank: Int,
    entry: UsageStatsRepository.UsageEntry,
    totalMs: Long,
    label: String,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    /** 入场序号：决定错峰延迟。 */
    entranceIndex: Int = 0,
    /** 减少动态效果（无障碍）：入场动画立即到位。 */
    reduceMotion: Boolean = false,
    /** 任务 16：左摇杆焦点态（高亮 + 边框）。 */
    focused: Boolean = false
) {
    val percent = if (totalMs > 0) entry.ms.toFloat() / totalMs else 0f
    val animP by animateFloatAsState(
        targetValue = percent,
        animationSpec = tween(600),
        label = "rowAnim"
    )
    // 错峰淡入入场（reduceMotion 时立即到位）
    // 使用 MotionSpec.Fast（220ms）+ stagger 12ms/项上限 240ms，
    // 与主页列表入场动画风格统一且跟手。
    var appeared by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        if (reduceMotion) { appeared = true; return@LaunchedEffect }
        appeared = false
        delay(MotionSpec.staggerDelay(entranceIndex, perMs = 12, maxMs = 240))
        appeared = true
    }
    val entA by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.Fast, label = "rowEntA"
    )
    val entY by animateFloatAsState(
        targetValue = if (appeared) 0f else 10f,
        animationSpec = MotionSpec.Fast, label = "rowEntY"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = entA; translationY = entY }
            .clip(RoundedCornerShape(Dimens.md))
            .background(
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
            )
            .then(
                if (focused) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.md)
                ) else Modifier
            )
            .padding(horizontal = Dimens.md, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        if (icon != null) {
            androidx.compose.foundation.Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) { Text(label.take(1), style = MaterialTheme.typography.labelMedium) }
        }
        Spacer(Modifier.width(Dimens.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            LinearProgressIndicator(
                progress = { animP },
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            )
        }
        Spacer(Modifier.width(Dimens.sm))
        Text(
            text = formatHoursMs(entry.ms),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(Dimens.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 深色圆角键位浮块（LB / RB 等）：深色底 + 亮色按键名，与全局手柄键位浮块一致。
 * 仅在手柄连接时由调用方决定是否显示。
 */
@Composable
private fun DarkKeyBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
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

/**
 * 十字左/右骑缝键位浮块（深色圆角）：左栏焦点时 →（按右切右栏），
 * 右栏焦点时 ←（按左切左栏）。仅手柄连接时由调用方显示。
 */
@Composable
private fun DarkArrowBadge(
    leftFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black.copy(alpha = 0.65f))
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (leftFocused) "→" else "←",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}

// ───────────────────────────────────────────────────────────────
// 工具
// ──────────────────────────────
/**
 * 排行行显示名：系统应用聚合项（包名哨兵）固定展示"系统应用"，
 * 其余应用优先取已安装应用 label，缺失时回退包名。
 */
private fun rankEntryLabel(
    entry: UsageStatsRepository.UsageEntry,
    apps: List<cn.mocabolka.run.launcher.AppModel>
): String {
    if (entry.packageName == cn.mocabolka.run.viewmodel.HomeViewModel.SYSTEM_AGGREGATE_PKG) {
        return "系统应用"
    }
    return apps.firstOrNull { it.packageName == entry.packageName }?.label ?: entry.packageName
}

/** 把毫秒时间戳格式化为 yyyy-MM-dd（与 UsageStatsRepository.dateKey 同格式）。 */
private fun dateKeyOf(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%04d-%02d-%02d".format(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

/** 把毫秒格式化为"X.YY 小时"（Tai 风格保留两位小数）。 */
private fun formatHours(ms: Long): String {
    val hours = ms / 3_600_000.0
    return "%.2f".format(hours)
}

/** 把毫秒格式化为"X小时Y分钟Z秒"（紧凑，用于列表）。 */
private fun formatHoursMs(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when {
        h > 0 -> "${h}小时${m}分"
        m > 0 -> "${m}分${sec}秒"
        else -> "${sec}秒"
    }
}

private fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

// ───────────────────────────────────────────────────────────────
// 日期指示器按钮 + 日期选择对话框（完全手柄适配）
// ───────────────────────────────────────────────────────────────

/**
 * 统计周期锚点日标签格式化：日视图显示"M月d日"、周视图显示该周起止、
 * 月视图显示"yyyy年M月"、年视图显示"yyyy年"。
 */
private fun formatAnchorLabel(period: StatsPeriod, anchorMs: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = anchorMs }
    val sdf = java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.getDefault())
    return when (period) {
        StatsPeriod.DAILY -> sdf.format(java.util.Date(anchorMs))
        StatsPeriod.WEEKLY -> {
            // 所在周（周一~周日）起止
            val weekStart = weekStartCalendar(anchorMs)
            val ws = weekStart.clone() as java.util.Calendar
            val we = weekStart.clone() as java.util.Calendar
            we.add(java.util.Calendar.DAY_OF_MONTH, 6)
            val f = java.text.SimpleDateFormat("M.d", java.util.Locale.getDefault())
            "${f.format(ws.time)} - ${f.format(we.time)}"
        }
        StatsPeriod.MONTHLY -> "${cal.get(java.util.Calendar.YEAR)}年${cal.get(java.util.Calendar.MONTH) + 1}月"
        StatsPeriod.YEARLY -> "${cal.get(java.util.Calendar.YEAR)}年"
    }
}

/** 取 [anchorMs] 所在周的周一 0:00。 */
private fun weekStartCalendar(anchorMs: Long): java.util.Calendar =
    java.util.Calendar.getInstance().apply {
        timeInMillis = anchorMs
        firstDayOfWeek = java.util.Calendar.MONDAY
        set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }

/** 把 Calendar 归零到当天 0:00 并返回毫秒。 */
private fun zeroOfDay(cal: java.util.Calendar): Long =
    cal.clone().let {
        val c = it as java.util.Calendar
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        c.timeInMillis
    }

/**
 * 日期指示器按钮：显示在周期滑块右侧（RB 之后）。
 * 鼠标点击或手柄 LS 打开日期选择；按钮内显示当前锚点日标签，右侧带日历图标。
 */
@Composable
private fun DateIndicatorButton(
    period: StatsPeriod,
    anchorMs: Long,
    onClick: () -> Unit,
    /** 手柄是否连接：仅此为真时，按钮左侧显示【LS】键位指示浮块。 */
    gamepadConnected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val label = formatAnchorLabel(period, anchorMs)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                RoundedCornerShape(Dimens.sm)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.sm, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (gamepadConnected) {
                LsKeyBadge()
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 选择器单选项。 */
private data class PickerItem(
    val label: String,
    val ms: Long,
    val dim: Boolean = false,
    /** 副标签（用于月/年显示总时长 / 周显示周次）。 */
    val subText: String? = null,
    /** 完全禁用（未来日 / 无数据）：不可点击也不可用方向键选中。 */
    val disabled: Boolean = false
)

/** 选择器模型：选项列表 + 列数 + 标题。 */
private data class PickerModel(val items: List<PickerItem>, val columns: Int, val title: String)

/** 把毫秒时长格式化为"Xh Ym"紧凑形式（用于选择器副标签）。 */
private fun shortDurationMs(ms: Long): String {
    val totalMin = ms / 60_000L
    if (totalMin < 1L) return "0m"
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h <= 0 -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h${m}m"
    }
}

/**
 * 按周期 + 初始锚点构建选择器模型：
 * - 日：当月 6 周日历网格（42 格，含前后月溢出日，dim 标记）。
 * - 周：以初始锚点所在周为末项、向前 15 周的周列表（单列）。
 * - 月：所在年的 12 个月网格（3 列 × 4 行）。
 * - 年：以初始锚点所在年为中心、前后各若干年的年网格（4 列）。
 */
private fun buildPickerModel(
    period: StatsPeriod,
    anchorMs: Long,
    monthTotalMs: (Int, Int) -> Long = { _, _ -> 0L },
    yearTotalMs: (Int) -> Long = { 0L }
): PickerModel {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = anchorMs }
    return when (period) {
        StatsPeriod.DAILY -> {
            val monthCal = java.util.Calendar.getInstance().apply { timeInMillis = anchorMs }
            monthCal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            val firstDow = monthCal.get(java.util.Calendar.DAY_OF_WEEK)
            // 以周一为列首：把 1 号前的空格补成上月尾日
            val lead = (firstDow - java.util.Calendar.MONDAY + 7) % 7
            val start = monthCal.clone() as java.util.Calendar
            start.add(java.util.Calendar.DAY_OF_MONTH, -lead)
            // 今日 0:00 毫秒——大于该值的日期 = 未来日，全部禁用
            val todayMs = zeroOfDay(java.util.Calendar.getInstance())
            val items = (0 until 42).map { i ->
                val c = start.clone() as java.util.Calendar
                c.add(java.util.Calendar.DAY_OF_MONTH, i)
                val inMonth = c.get(java.util.Calendar.MONTH) == monthCal.get(java.util.Calendar.MONTH)
                val ms = zeroOfDay(c)
                PickerItem(
                    label = c.get(java.util.Calendar.DAY_OF_MONTH).toString(),
                    ms = ms,
                    dim = !inMonth,
                    disabled = ms > todayMs
                )
            }
            val initialIndex = items.indexOfFirst { it.ms == zeroOfDay(cal) && !it.disabled }
                .let { if (it < 0) items.indexOfFirst { !it.disabled }.coerceAtLeast(0) else it }
            PickerModel(items, 7, "${cal.get(java.util.Calendar.YEAR)}年${cal.get(java.util.Calendar.MONTH) + 1}月 · 选择日期")
        }
        StatsPeriod.WEEKLY -> {
            val weekStart = weekStartCalendar(anchorMs)
            val yearOfLast = weekStart.get(java.util.Calendar.YEAR)
            val todayMs = zeroOfDay(java.util.Calendar.getInstance())
            val items = (15 downTo 0).map { back ->
                val c = weekStart.clone() as java.util.Calendar
                c.add(java.util.Calendar.DAY_OF_MONTH, -back * 7)
                val ms = c.timeInMillis
                val end = c.clone() as java.util.Calendar
                end.add(java.util.Calendar.DAY_OF_MONTH, 6)
                val f = java.text.SimpleDateFormat("M.d", java.util.Locale.getDefault())
                val label = "${f.format(c.time)} - ${f.format(end.time)}"
                // 周次编号（按所在年）：用 c 所在年的第几个周
                val weekOfYear = c.get(java.util.Calendar.WEEK_OF_YEAR)
                PickerItem(label, ms, subText = "第${weekOfYear}周", disabled = ms > todayMs)
            }
            PickerModel(items, 1, "${yearOfLast}年 · 选择周")
        }
        StatsPeriod.MONTHLY -> {
            val year = cal.get(java.util.Calendar.YEAR)
            val items = (0 until 12).map { m ->
                val c = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, m)
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                val total = monthTotalMs(year, m + 1)
                // 未来月份禁用（与今天同年且月份更大，或未来年份——本视图锁在 year 内）
                val nowCal = java.util.Calendar.getInstance()
                val isFuture = year > nowCal.get(java.util.Calendar.YEAR) ||
                    (year == nowCal.get(java.util.Calendar.YEAR) && m > nowCal.get(java.util.Calendar.MONTH))
                PickerItem(
                    label = "${m + 1}月",
                    ms = zeroOfDay(c),
                    subText = if (total > 0) shortDurationMs(total) else "—",
                    // 未来月 或 无使用数据 → 直接灰掉不可选
                    disabled = isFuture || total == 0L
                )
            }
            val initialIndex = items.indexOfFirst { !it.disabled }.coerceAtLeast(0)
            PickerModel(items, 3, "${year}年 · 选择月份")
        }
        StatsPeriod.YEARLY -> {
            val year = cal.get(java.util.Calendar.YEAR)
            val startYear = year - 10
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val items = (0 until 16).map { i ->
                val y = startYear + i
                val c = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, y)
                    set(java.util.Calendar.MONTH, 0)
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                val total = yearTotalMs(y)
                PickerItem(
                    label = "$y",
                    ms = zeroOfDay(c),
                    subText = if (total > 0) shortDurationMs(total) else "—",
                    // 未来年 或 无使用数据 → 直接灰掉不可选
                    disabled = y > currentYear || total == 0L
                )
            }
            val initialIndex = items.indexOfFirst { !it.disabled }.coerceAtLeast(0)
            PickerModel(items, 4, "选择年份")
        }
    }
}

/**
 * 日期选择对话框（完全手柄适配）：
 * - 通过 [bridge] 接收 HomeScreen 全局手柄事件（方向键移动选择、A 确认、B 取消）；
 * - 同时支持鼠标点击单元格直接确认；
 * - 选择 UI 随周期变化：日=月日历、周=周列表、月=月网格、年=年网格。
 */
@Composable
internal fun DateRangePickerDialog(
    period: StatsPeriod,
    initialAnchorMs: Long,
    bridge: StatsDatePickerBridge,
    /** 手柄是否连接：仅 true 时显示 LS 键位提示浮块。 */
    gamepadConnected: Boolean = false,
    /** 该月总时长（ms），用于月选择器副标签。 */
    monthTotalMs: (year: Int, month1Based: Int) -> Long = { _, _ -> 0L },
    /** 该年总时长（ms），用于年选择器副标签。 */
    yearTotalMs: (year: Int) -> Long = { 0L },
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val model = remember(period, initialAnchorMs) {
        buildPickerModel(period, initialAnchorMs, monthTotalMs, yearTotalMs)
    }
    // 用 rememberSaveable 跨周期切换 / 进程重建保留选中索引（避免每次切周期跳回首项）
    var selectedIndex by rememberSaveable(period, initialAnchorMs) { mutableStateOf(0) }
    // 初始化选中项：仅在 model 真正变化（首帧 / period 切换）时定位到当前锚点
    LaunchedEffect(period, initialAnchorMs) {
        // 如果当前 selectedIndex 仍指向有效且未 disabled 的项则保持，否则定位锚点
        val currentItem = model.items.getOrNull(selectedIndex)
        if (currentItem == null || currentItem.disabled) {
            val anchorIdx = when (period) {
                StatsPeriod.DAILY -> model.items.indexOfFirst { it.ms == zeroOfDay(java.util.Calendar.getInstance().apply { timeInMillis = initialAnchorMs }) }.coerceAtLeast(0)
                StatsPeriod.MONTHLY -> java.util.Calendar.getInstance().apply { timeInMillis = initialAnchorMs }.get(java.util.Calendar.MONTH)
                else -> 0
            }
            // 如果锚点项可用则选中它，否则回退到第一个可用项
            selectedIndex = if (model.items.getOrNull(anchorIdx)?.disabled == false) {
                anchorIdx
            } else {
                model.items.indexOfFirst { !it.disabled }.coerceAtLeast(0)
            }
        }
    }
    // 将全局手柄事件桥接到本对话框（方向键移动需跳过 disabled 项，落到最近可用项）
    LaunchedEffect(bridge, model) {
        bridge.onMove = { dir ->
            val step = when (dir) {
                Direction.UP -> -model.columns
                Direction.DOWN -> model.columns
                Direction.LEFT -> -1
                Direction.RIGHT -> 1
            }
            var next = selectedIndex
            // 在边界内线性推进，至多 model.items.size 次（保证最坏情况也能找到可用项或原地不动）
            repeat(model.items.size) {
                val candidate = (next + step).coerceIn(0, model.items.lastIndex)
                if (candidate == next) return@repeat
                next = candidate
                if (!model.items[next].disabled) return@repeat
            }
            selectedIndex = next
        }
        bridge.onConfirm = {
            val cur = model.items.getOrNull(selectedIndex)
            if (cur != null && !cur.disabled) onConfirm(cur.ms)
        }
        bridge.onCancel = { onDismiss() }
    }
    DisposableEffect(Unit) {
        onDispose {
            bridge.onMove = {}; bridge.onConfirm = {}; bridge.onCancel = {}
        }
    }

    // 今天 0:00，用于 DAILY 视图的"今天"高亮描边
    val todayMs = remember { zeroOfDay(java.util.Calendar.getInstance()) }

    // 全屏黑色遮罩：indication=null 屏蔽遮罩长按水波纹触控动画，不影响入场退场与手柄焦点。
    val scrimInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = MotionSpec.ScrimAlpha))
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // 卡片：与 InfoDialog / DropdownDialog 统一——MD3 Surface（大圆角 + 景深投影 + lg 内边距）
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.92f)
                .padding(Dimens.md)
                .clickable(onClick = { })
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            // 标题行：标题 + 关闭 × 按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // 关闭按钮（鼠标/触屏）
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // DAILY 视图：周列首（一 二 三 四 五 六 日）
            if (period == StatsPeriod.DAILY) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
                    for (l in labels) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = l,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // 选项网格
            val rows = (model.items.size + model.columns - 1) / model.columns
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        for (c in 0 until model.columns) {
                            val idx = r * model.columns + c
                            if (idx < model.items.size) {
                                val item = model.items[idx]
                                val selected = idx == selectedIndex
                                val isToday = period == StatsPeriod.DAILY && item.ms == todayMs
                                // 月/年单元格：主标签 + 副标签（总时长小字）
                                val subText: String? = item.subText
                                // 选中项左侧主题色竖条（仅选中时）
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(Dimens.sm))
                                        .background(
                                            when {
                                                item.disabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f)
                                                selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            }
                                        )
                                        .then(
                                            when {
                                                item.disabled -> Modifier
                                                selected -> Modifier.border(
                                                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.sm)
                                                )
                                                isToday -> Modifier.border(
                                                    1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(Dimens.sm)
                                                )
                                                else -> Modifier
                                            }
                                        )
                                        .clickable(enabled = !item.disabled) {
                                            if (!item.disabled) {
                                                selectedIndex = idx
                                                onConfirm(item.ms)
                                            }
                                        }
                                        .padding(horizontal = Dimens.xs, vertical = Dimens.sm),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            // 选中项加勾标识
                                            if (selected && !item.disabled) {
                                                Icon(
                                                    androidx.compose.material.icons.Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = when {
                                                    item.disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                                                    selected -> MaterialTheme.colorScheme.primary
                                                    item.dim -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                textDecoration = if (item.disabled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                            )
                                        }
                                        if (subText != null) {
                                            Text(
                                                text = subText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    item.disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 底部：操作提示（手柄）+ 取消/确认按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 操作提示：连接手柄时显示对应键位浮块 + 文字说明（与 DropdownDialog 风格统一）
                if (gamepadConnected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Direction.values().take(4).forEach { dir ->
                                DirectionKeyBadge(dir)
                                if (dir != Direction.RIGHT) Spacer(Modifier.width(3.dp))
                            }
                        }
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "选择",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        DarkKeyBadge("A")
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "确认",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        DarkKeyBadge("B")
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "↑↓←→ 选择 · A 确认 · B 关闭",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                // 取消/确认 按钮（与 DropdownDialog 统一 MD3 风格）
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.sm))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable(onClick = onDismiss)
                            .defaultMinSize(minHeight = 36.dp, minWidth = 72.dp)
                            .padding(horizontal = Dimens.md, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.sm))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.sm))
                            .clickable {
                                val cur = model.items.getOrNull(selectedIndex)
                                if (cur != null && !cur.disabled) onConfirm(cur.ms)
                            }
                            .defaultMinSize(minHeight = 36.dp, minWidth = 72.dp)
                            .padding(horizontal = Dimens.md, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("确认", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

}

/** LS（左摇杆按压）键位深色圆角浮块：与 DarkKeyBadge 同风格。 */
@Composable
private fun LsKeyBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "LS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** 方向键深色圆角浮块（↑↓←→）：与 DarkKeyBadge / LsKeyBadge 同风格。 */
@Composable
private fun DirectionKeyBadge(direction: Direction, modifier: Modifier = Modifier) {
    val symbol = when (direction) {
        Direction.UP -> "↑"
        Direction.DOWN -> "↓"
        Direction.LEFT -> "←"
        Direction.RIGHT -> "→"
    }
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 1
        )
    }
}

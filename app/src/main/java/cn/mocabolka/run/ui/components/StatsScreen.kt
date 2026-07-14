package cn.mocabolka.run.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.launcher.UsageStatsRepository
import cn.mocabolka.run.ui.theme.AccentIconSquare
import cn.mocabolka.run.ui.theme.AnimatedNumberText
import cn.mocabolka.run.ui.theme.ChartAccent
import cn.mocabolka.run.ui.theme.ChartNormal
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LocalShowFocusIndicators
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.SurfaceCard
import cn.mocabolka.run.ui.theme.SurfaceTokens
import cn.mocabolka.run.ui.theme.TrendDown
import cn.mocabolka.run.ui.theme.TrendUp
import cn.mocabolka.run.ui.theme.focusBorder
import cn.mocabolka.run.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

/** 统计周期：日 / 周 / 月 / 年。 */
enum class StatsPeriod(val label: String) {
    DAILY("日"), WEEKLY("周"), MONTHLY("月"), YEARLY("年")
}

/**
 * 用户可选的周期入口（日 / 周 / 月 / 年 全部恢复）。
 * 周视图无独立日期选择器（周由"选某天 → 系统按该天所在周聚合"得到），
 * 故 DateIndicatorButton 在 WEEKLY 下不触发选择器（仅展示周区间标签）。
 * 周期滑块与 HomeScreen LB/RB 循环都使用本列表，保证入口一致。
 */
val selectableStatsPeriods: List<StatsPeriod> = listOf(
    StatsPeriod.DAILY, StatsPeriod.WEEKLY, StatsPeriod.MONTHLY, StatsPeriod.YEARLY
)

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
    var onCancel: () -> Unit = {},
    /** 右摇杆滚动回调（dy>0 向下 / dy<0 向上），由 HomeScreen 路由 RightStick 事件驱动（旧固定步长，已废弃）。 */
    var onScroll: (Float) -> Unit = {},
    /**
     * 归一化连续像素滚动（R1）：由 HomeScreen 统一「右摇杆平滑加速滚动引擎」按帧驱动，
     * 参数为本帧滚动像素增量（正=向下）。suspend 以便直接调用 LazyGridState.scrollBy。
     */
    var scrollByPx: suspend (Float) -> Unit = {},
    /**
     * R9（2026-07-14）翻页回调：负=上一页/上一月/上一年/上一区间；正=下一页/下一月/下一年/下一区间。
     * 由 HomeScreen 路由 LB/RB 事件驱动（与统计页周期切换 LB/RB 一致，
     * 让用户不需要松手跳到底部按按钮也能翻页）。
     */
    var onPage: (Int) -> Unit = {},
    /**
     * R9（2026-07-14）快捷锚点回调：跳转到"今天/本周/本月/本年"。
     * 由 HomeScreen 路由特殊快捷键（推荐 View 长按 或 特定按钮）；目前仅 dialog 内 chip 触发。
     */
    var jumpToAnchor: () -> Unit = {}
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
    /** 日期选择器是否打开（由 HomeScreen 的 View 键 / 本页按钮共同驱动）。 */
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

    // 任务 11（重构）：Stats 页横屏双栏模式（screenWidthDp >= 720 启用）
    // 需求调整（2026-07-13）：左 = 应用排行（左摇杆焦点）/ 右 = 总览总计（右摇杆滚动）。
    // 左摇杆仅在左栏(排行)移动焦点，右摇杆仅在右栏(总计)滚动，两摇杆互不越界。
    val isWideScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= Dimens.WideScreenThresholdDp

    // 左栏(排行)焦点跟随——焦点变化 / 周期变化时滚动排行到焦点行
    LaunchedEffect(focusIndex, activePeriod, isWideScreen) {
        if (reportEntries.isNotEmpty()) {
            val idx = if (isWideScreen) focusIndex else 4 + focusIndex
            runCatching { listState.scrollToItem(idx.coerceAtLeast(0)) }
        }
    }


    // 左栏（应用排行）固定宽度，右栏（总计+图表）自适应剩余空间
    val leftPanelWidth = Dimens.RightPanelWidth
    Box(modifier = modifier.fillMaxSize().padding(horizontal = Dimens.ContentHorizontal)) {
        Column(modifier = Modifier.fillMaxSize()) {
        if (isWideScreen) {
            // 双栏布局：左 = 应用排行（LazyColumn，左摇杆焦点）/ 右 = 总览总计（ScrollState，右摇杆滚动）。
            // 左栏固定宽度（280dp），右栏自适应剩余空间——让指标卡和柱状图有充足呼吸空间。
            Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                // 左栏：应用排行（独立 LazyColumn，左摇杆上下移动焦点）
                Column(
                    modifier = Modifier
                        .width(leftPanelWidth)
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
                                top = Dimens.xxs, bottom = Dimens.HintBarHeight
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
                                    focused = gamepadConnected && reportEntries.indexOf(entry) == focusIndex
                                )
                            }
                        }
                    }
                }
                // 两栏中间间距
                Box(
                    modifier = Modifier.width(8.dp),
                    contentAlignment = Alignment.Center
                ) {}
                // 右栏：总览总计（指标卡 + 图表，可滚动，右摇杆驱动滚动）
                // 使用 weight(1f) 占满左栏固定宽度后剩余的全部空间
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(leftScrollState),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md)
                ) {
                    // 右栏顶部：LB/RB 周期切换 + 日期选择按钮（横屏移至右上角，与日期按钮交换位置）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                    ) {
                        GamepadVisible { KeyBadge(text = "LB") }
                        Box(modifier = Modifier.weight(1f)) {
                            ReverieSegmentedRow(
                                items = selectableStatsPeriods,
                                selected = activePeriod,
                                onSelect = onSelectPeriod,
                                label = { it.label }
                            )
                        }
                        GamepadVisible { KeyBadge(text = "RB") }
                        // 日期指示器按钮（右上角）：点击 / View 键打开日期选择
                        // R8 修复：日期按钮本身不参与 weight 平分（避免被 SegmentedRow 挤压成"小灰点"），
                        // 容器宽度 wrapContent 跟随内容；外层 SegmentedRow 占据中间 weight(1f) 空间。
                        DateIndicatorButton(
                            period = activePeriod,
                            anchorMs = anchorMs,
                            onClick = { onDatePickerOpenChange(true) },
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                            // 周视图下点击不触发选择器（DateIndicatorButton 内部已守卫）
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
                    // 3 张指标卡
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
                }
            }
        } else {
            // 中等屏：单栏原版
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = Dimens.md, bottom = Dimens.HintBarHeight
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
                        GamepadVisible { KeyBadge(text = "LB") }
                        Box(modifier = Modifier.weight(1f)) {
                            ReverieSegmentedRow(
                                items = selectableStatsPeriods,
                                selected = activePeriod,
                                onSelect = onSelectPeriod,
                                label = { it.label }
                            )
                        }
                        GamepadVisible { KeyBadge(text = "RB") }
                        // 日期指示器按钮（右侧），点击 / View 键打开日期选择
                        // R8 修复：竖屏分支同样 wrapContentWidth 防挤压
                        DateIndicatorButton(
                            period = activePeriod,
                            anchorMs = anchorMs,
                            onClick = { onDatePickerOpenChange(true) },
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
                // 3 张关键指标卡：竖屏（非宽屏）改 2 列网格（总计/应用量一行，最长使用占满），避免窄屏挤压变形。
                // 与横屏一致：行用 IntrinsicSize.Max + 各卡 fillMaxHeight 保证总计/应用量/最长等高对齐。
                item {
                    val isNarrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < Dimens.NarrowScreenThresholdDp
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
                            focused = gamepadConnected && reportEntries.indexOf(entry) == focusIndex
                        )
                    }
                }
            }
        }

        // 统计页（STATS）专属底部按键指示栏：与主体隔离的独立槽位（规则2）。
        // 规则3：LB/RB 切周期已在周期滑块左右两侧给出角标，此处不复现。
        // 规则4：禁止任何上下滚动/方向键提示（原 "↑浏览/↓浏览" 已删除）。
        // 跨栏提示（原骑缝浮块已移除）统一置于此处；窄屏无跨栏，仅宽屏手柄连接时显示。
        // 仅保留无按钮旁落点的 A 确认(打开日期选择器后) / B 返回。
        // R9 修复（2026-07-14）：移除 LB/RB 周期提示（已在周期滑块旁浮块，避免重复）。
        GamepadBottomHintBar(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 跨栏逻辑已整体移除：不再显示 LS/RS「跨栏」提示。
            // 周视图无日期选择器，故「View 日期」提示仅在非周视图显示。
            if (activePeriod != StatsPeriod.WEEKLY) Hint(KeyToken.VIEW, "日期")
            Hint(KeyToken.B, "返回")
        }
    }
}
}

/**
 * 周期 Tab 横排（已由 ReverieSegmentedRow 统一替代，见调用处）。
 */



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
    SurfaceCard(
        modifier = modifier,
        level = SurfaceTokens.CardLevel.Default,
        shape = Dimens.lg
    ) {
        Column(
            modifier = Modifier.padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
        // 任务 10：移除 MetricCard 顶部细横线
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconSquare(accent = accent, icon = icon)
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
    SurfaceCard(
        modifier = modifier,
        level = SurfaceTokens.CardLevel.Default,
        shape = Dimens.lg
    ) {
        Column(
            modifier = Modifier.padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
        // 任务 10：移除 LongestCard 顶部细横线
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconSquare(accent = accent, icon = icon)
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
    // 性能优化（2026-07-13）：600ms → 320ms（切到 STATS Tab 时图表迅速就位，避免与 Tab 切换动画叠加卡顿）
    val animatedRatios = values.mapIndexed { i, v ->
        val ratio = (v.toFloat() / maxV).coerceIn(0f, 1f)
        animateFloatAsState(
            targetValue = ratio,
            animationSpec = tween(320, delayMillis = MotionSpec.staggerDelay(i, maxMs = 120).toInt()),
            label = "bar$i"
        )
    }
    SurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        level = SurfaceTokens.CardLevel.Subtle,
        shape = Dimens.lg
    ) {
        Column(
            modifier = Modifier.padding(Dimens.md),
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
    // 焦点框可见性：手柄未连接 / 纯触控模式不绘制焦点框（全局规则统一）。
    val focusedVis = focused && LocalShowFocusIndicators.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = entA; translationY = entY }
            .clip(RoundedCornerShape(Dimens.md))
            .background(
                if (focusedVis) MaterialTheme.colorScheme.primary.copy(alpha = Dimens.FocusSurfaceAlpha)
                else SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle)
            )
            .focusBorder(focusedVis, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.md))
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
                    .background(SurfaceTokens.focusBg()),
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
                trackColor = SurfaceTokens.pressBg()
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
            .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
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
internal fun dateKeyOf(ms: Long): String {
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
 * 日期指示器：显示在周期滑块右侧（RB 之后）。
 * 由两部分组成：
 *  - 独立的「View 视窗」浮窗 icon（参考 Xbox 双方块 View 键外观，MD 图标 Icons.Filled.FilterNone），
 *    深色半透明底、无边框，与其它浮窗（KeyBadge Glass 变体）同一样式；点击 / 手柄 View 键打开日期选择。
 *  - 右侧为普通文字的当前锚点日标签（带时钟图标），不再使用带边框的背景块。
 */
@Composable
private fun DateIndicatorButton(
    period: StatsPeriod,
    anchorMs: Long,
    onClick: () -> Unit,
    /** 左栏焦点态：焦点在日期按钮上时绘制主题色边框，手柄可见焦点反馈。 */
    focused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val label = formatAnchorLabel(period, anchorMs)
    // 重构（2026-07-14）：取消「按钮属性」，改为纯文本 + 图标行（不再用 ReverieTonalButton 的
    // tonal 背景/按压态），仅保留 clickable + 焦点边框，外观更轻、更贴合统计页极简调性。
    // 周视图（WEEKLY）无独立日期选择器：点击不触发选择器，仅展示周区间标签（文字呈 muted 态）。
    val isWeekly = period == StatsPeriod.WEEKLY
    Box(modifier = modifier.wrapContentWidth(unbounded = true)) {
        wrapFocusBorder(focused = focused) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        enabled = !isWeekly,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick
                    )
                    .defaultMinSize(minWidth = 120.dp)
                    .padding(horizontal = Dimens.sm, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.CalendarMonth, null,
                    tint = if (isWeekly) SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong)
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.IconSm)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isWeekly) SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong)
                             else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────
// 原生 Material3 日期选择器（替代自绘 DateRangePickerDialog）
// ───────────────────────────────────────────────────────────────

/**
 * 把"选中的某天(0:00 ms)"归一到当前周期锚点：
 * - DAILY  → 当天 0:00
 * - WEEKLY → 该天所在周一 0:00（保留周聚合能力，但隐藏独立周选择器入口）
 * - MONTHLY→ 当月 1 号 0:00
 * - YEARLY → 当年 1 月 1 号 0:00
 * 原生 DatePicker 只返回"选中那天的 ms"，由本函数落到各周期起始，语义与旧版 onConfirm 完全一致。
 */
private fun truncToPeriod(ms: Long, period: StatsPeriod): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return when (period) {
        StatsPeriod.DAILY -> zeroOfDay(cal)
        StatsPeriod.WEEKLY -> weekStartCalendar(ms).timeInMillis
        StatsPeriod.MONTHLY -> {
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            zeroOfDay(cal)
        }
        StatsPeriod.YEARLY -> {
            cal.set(java.util.Calendar.MONTH, 0)
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            zeroOfDay(cal)
        }
    }
}

/**
 * 在 [ms] 基础上按方向偏移天数（用于手柄方向键驱动原生 DatePicker 选中日）。
 * 返回的新毫秒仍对齐到 0:00。
 */
private fun shiftDay(ms: Long, days: Int): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    cal.add(java.util.Calendar.DAY_OF_MONTH, days)
    return zeroOfDay(cal)
}

/**
 * 原生 Material3 日期选择器封装（替代自绘 DateRangePickerDialog）。
 *
 * 设计：
 * - 日视图 → 单日 `DatePicker`（选一天即锚点）；
 * - 月/年视图 → `DateRangePicker`（选范围后取**起始日** trunc 到当月/当年首，得到锚点）。
 *   选范围而非单日，让用户能直观"圈出一个月/一年"，体验优于单日。
 * - 周视图无独立日期选择器（WEEKLY 下 DateIndicatorButton 禁用点击），故本封装不被 WEEKLY 触发；
 *   但 [truncToPeriod] 仍支持 WEEKLY 以便内部周聚合（选某天后按所在周落锚）。
 *
 * 手柄适配（关键）：项目的手柄事件走 GamepadManager 协程流，绕过了 Android 原生 KeyEvent，
 * 因此原生 DatePicker 内部的 Compose Focus 收不到 DPAD。必须由本函数显式桥接：
 * - 方向键(Navigate) → 手动算出相邻日(±1/±7天)并写回 state.selectedDateMillis；
 * - LB/RB(Shoulder=onPage) → 翻月(日) / 翻年(月年)，写回 state.displayedMonthMillis；
 * - A(Select=onConfirm) → 提交 trunc 后的锚点；B(Back=onCancel) → 关闭。
 * - 鼠标/触屏点击日历格仍由原生组件自身处理（直接选中 + 触发 Confirm 按钮）。
 *
 * 焦点框：原生组件内部"选中日"高亮属选择语义，不在全局 LocalShowFocusIndicators 焦点环约束内，
 * 与 MEMORY 中"日期选择器选中项"例外一致；底部确认/取消按钮走统一 focusBorder 规则。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NativeDatePickerDialog(
    period: StatsPeriod,
    initialAnchorMs: Long,
    bridge: StatsDatePickerBridge,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    // 仅日/月/年三类走原生选择器；周不在此打开（入口已隐藏）。
    val todayMs = remember { zeroOfDay(java.util.Calendar.getInstance()) }

    // 未来日不可选边界（与旧版"未来禁用"一致）。
    val selectableDates = remember(todayMs) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // utcTimeMillis 是 UTC 0:00；todayMs 是本地 0:00，统一按天粒度比较（留当天余量）。
                return utcTimeMillis <= todayMs + 24L * 60 * 60 * 1000
            }
        }
    }


    // 根据 period 创建对应 state（日=单选 / 月年=范围）
    val isRange = period != StatsPeriod.DAILY
    val singleState = if (!isRange) rememberDatePickerState(
        initialSelectedDateMillis = initialAnchorMs, selectableDates = selectableDates
    ) else null
    val rangeState = if (isRange) rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialAnchorMs,
        initialSelectedEndDateMillis = initialAnchorMs,
        selectableDates = selectableDates
    ) else null

    // 当前选中毫秒（实时镜像，供 onConfirm 兜底读取）
    var selectedMs by remember {
        mutableStateOf(
            if (isRange) initialAnchorMs else initialAnchorMs
        )
    }
    LaunchedEffect(singleState?.selectedDateMillis) {
        singleState?.selectedDateMillis?.let { selectedMs = it }
    }
    LaunchedEffect(rangeState?.selectedStartDateMillis) {
        rangeState?.selectedStartDateMillis?.let { selectedMs = it }
    }

    // 手柄桥接：方向键驱动选中日、LB/RB 翻页、A 确认、B 取消。
    LaunchedEffect(bridge, isRange) {
        bridge.onMove = { dir ->
            val step = when (dir) {
                Direction.UP -> -7
                Direction.DOWN -> 7
                Direction.LEFT -> -1
                Direction.RIGHT -> 1
            }
            val cur = selectedMs
            var next = shiftDay(cur, step)
            // 越过 today 上限则夹紧到 today
            if (next > todayMs) next = todayMs
            selectedMs = next
            if (isRange) rangeState?.setSelection(next, next)
            else singleState?.selectedDateMillis = next
        }
        bridge.onPage = { dir ->
            // 日视图翻月；月/年视图翻年（±12 月）
            val base = if (isRange) rangeState?.displayedMonthMillis ?: initialAnchorMs
            else singleState?.displayedMonthMillis ?: initialAnchorMs
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = base }
            if (isRange) cal.add(java.util.Calendar.YEAR, dir)
            else cal.add(java.util.Calendar.MONTH, dir)
            val newMs = zeroOfDay(cal)
            if (isRange) rangeState?.displayedMonthMillis = newMs
            else singleState?.displayedMonthMillis = newMs
        }
        bridge.onConfirm = { onConfirm(truncToPeriod(selectedMs, period)) }
        bridge.onCancel = { onDismiss() }
        bridge.jumpToAnchor = {}
        bridge.onScroll = {}
        bridge.scrollByPx = {}
    }
    DisposableEffect(Unit) {
        onDispose {
            bridge.onMove = {}; bridge.onPage = {}; bridge.onConfirm = {}
            bridge.onCancel = {}; bridge.jumpToAnchor = {}; bridge.onScroll = {}
            bridge.scrollByPx = {}
        }
    }

    DialogScaffold(
        onDismiss = onDismiss,
        bottomHintContent = {
            Hint(KeyToken.A, "确认")
            Hint(KeyToken.B, "关闭")
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val dialogMaxW = minOf(Dimens.DialogMaxWidth, maxWidth)
            val dialogMaxH = maxHeight
            Surface(
                    shape = RoundedCornerShape(Dimens.RadiusLg),
                    color = SurfaceTokens.cardSurfaceStrong(),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(max = dialogMaxW)
                        .fillMaxWidth()
                        .heightIn(max = dialogMaxH)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = Dimens.md, vertical = Dimens.sm),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                    ) {
                        if (!isRange) {
                            singleState?.let { st ->
                                DatePicker(
                                    state = st,
                                    showModeToggle = true,
                                    colors = DatePickerDefaults.colors(
                                        containerColor = SurfaceTokens.cardSurfaceStrong(),
                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                        headlineContentColor = MaterialTheme.colorScheme.onSurface,
                                        weekdayContentColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong),
                                        subheadContentColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong),
                                        yearContentColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong),
                                        currentYearContentColor = MaterialTheme.colorScheme.primary,
                                        selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                                        todayDateBorderColor = MaterialTheme.colorScheme.primary,
                                        dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                title = {
                                    Text("选择日期", style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = Dimens.sm, top = Dimens.sm))
                                }
                            )
                        }
                        } else {
                            rangeState?.let { st ->
                                DateRangePicker(
                                    state = st,
                                    showModeToggle = true,
                                    colors = DatePickerDefaults.colors(
                                        containerColor = SurfaceTokens.cardSurfaceStrong(),
                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                        headlineContentColor = MaterialTheme.colorScheme.onSurface,
                                        weekdayContentColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong),
                                        subheadContentColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong),
                                        yearContentColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong),
                                        currentYearContentColor = MaterialTheme.colorScheme.primary,
                                        selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                                        todayDateBorderColor = MaterialTheme.colorScheme.primary,
                                        dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                title = {
                                    Text(
                                        if (period == StatsPeriod.MONTHLY) "选择月份" else "选择年份",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = Dimens.sm, top = Dimens.sm))
                                }
                            )
                        }
                    }

                    // 底部：确认 / 取消按钮（与 DropdownDialog / ConfirmDialog 统一 MD3 风格）。
                    // 键位提示栏已移出卡片、固定到屏幕底部（见下方 scrim Box 内的 GamepadBottomHintBar）。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            ReverieTextButton(onClick = onDismiss, text = "取消")
                            ReverieFilledButton(
                                onClick = { onConfirm(truncToPeriod(selectedMs, period)) },
                                text = "确认",
                                enabled = selectedMs != null
                            )
                        }
                    }
                }
                }
            }
        }
    }  // close DialogScaffold

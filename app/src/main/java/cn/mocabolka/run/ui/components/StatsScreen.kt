package cn.mocabolka.run.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.launcher.UsageStatsRepository
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.ui.NotificationHistoryActivity
import cn.mocabolka.run.ui.theme.AccentIconSquare
import cn.mocabolka.run.ui.theme.AnimatedNumberText
import cn.mocabolka.run.ui.theme.ChartAccent
import cn.mocabolka.run.ui.theme.ChartNormal
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LocalShowFocusIndicators
import cn.mocabolka.run.ui.theme.MotionMachines
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.rememberStaggeredEntrance
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
    /** 右摇杆滚动回调（dy>0 向下 / dy<0 向上），由 HomeScreen 路由 RightStick 事件驱动。 */
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

/**
 * 统计页手柄焦点区（二维寻址，手柄全链路重构）。
 * - RANK：左栏应用排行（左摇杆焦点，保留既有肌肉记忆，进页默认区）。
 * - OVERVIEW：右栏总览控件区（周期/日期/视图/指标滑块）。此前手柄完全无法触达这些控件，
 *   是真正的"按钮点不了"功能死区；本区即零死区改造入口。
 */
enum class StatsFocusZone { RANK, OVERVIEW }

/**
 * 统计页子 Tab（Compose 内部切换，零 Activity / 零 Manifest）：
 * - OVERVIEW 速览：今日使用时长 / 启动次数 / 通知次数三张 Top 榜（仅今日，无周期栏）。
 * - USAGE_DURATION 使用时长：周期 + 时长趋势(图表/列表) + 应用排行(按时长)，左栏图右栏榜。
 * - LAUNCH_COUNT 启动次数：周期 + 次数趋势(图表/列表) + 应用排行(按次数)，左栏图右栏榜。
 * - NOTIFICATIONS 通知历史：周期 + 通知数趋势(图表/列表) + 应用排行(按通知数)，点击钻取；Y 按发件人聚合。
 */
enum class StatsSubTab(val label: String) {
    OVERVIEW("速览"), USAGE_DURATION("使用时长"), LAUNCH_COUNT("启动次数"), NOTIFICATIONS("通知历史")
}

/**
 * 总览控件焦点槽序号（OVERVIEW 区内 UP/DOWN 遍历顺序）。
 * 指标槽仅在"日周期·今天"存在，故实际可聚焦数由 [StatsControlBridge.overviewSlotCount] 动态给出。
 */
object StatsOverviewSlot {
    const val PERIOD = 0
    const val DATE = 1
    const val VIEW = 2
    const val METRIC = 3
    const val EXPAND = 4
}

/**
 * 统计页控件桥（对齐 [StatsDatePickerBridge] 设计）：StatsScreen 把内部"切视图 / 切指标"能力
 * 与"当前可聚焦控件数"注册进来，供 HomeScreen 手柄 A 键按焦点槽精准分发，
 * 无需把 viewMode / metricMode 提升出组件即可实现整体 UI 手柄可达。
 */
class StatsControlBridge {
    var toggleView: () -> Unit = {}
    var toggleMetric: () -> Unit = {}
    /** 当前 OVERVIEW 区可聚焦控件数（3=周期/日期/视图；4=再+指标；5=含展开全部槽，仅日·今天/有指标子 Tab）。 */
    var overviewSlotCount: Int = 4
    /** 展开全部按钮手柄焦点回调（A 键切换展开/收起），焦点落到 EXPAND 槽时生效。 */
    var expandToggle: (() -> Unit)? = null
    /** 展开全部按钮当前手柄聚焦态（供 ReverieTonalButton focused 使用，可观察状态）。 */
    var focusedExpand by mutableStateOf(false)
    /** 子 Tab 循环切换（delta=-1 上一个 / +1 下一个），由 HomeScreen LT/RT 路由驱动。 */
    var cycleSubTab: (Int) -> Unit = {}
    /** 当前子 Tab 序号（[StatsSubTab].ordinal），供 HomeScreen 按 Tab 决定键位路由。 */
    var currentSubTab: Int = 0
    /** 通知历史 Tab：Y 键图表/列表视图切换（Chart/List）。 */
    var notifToggleView: () -> Unit = {}
    /** 通知历史 Tab：聚合切换已废弃（高频联系人改为左栏嵌入卡），保留空实现兼容路由。 */
    var notifToggleAggregation: () -> Unit = {}
    /** 速览 Tab：A 键激活当前焦点 Top 卡 → 跳转对应子 Tab，返回 true 表示已消费。 */
    var overviewActivate: () -> Boolean = { false }
    /** 通知历史 Tab：A 键右limb排行钻取到该应用/发件人通知明细，返回 true 表示已消费。 */
    var notifActivate: () -> Boolean = { false }
    /** 通知历史 Tab：B 键钻取子页返回，返回 true 表示已消费。 */
    var notifBack: () -> Boolean = { false }
    /** 通知历史 Tab：当前排行焦点键（BY_APP=包名 / BY_SENDER=发件人），由排行 onFocus 上报。 */
    var notifFocusedKey: String? = null
}

/** 通知历史聚合维度（按应用 / 按发件人）。 */
internal enum class NotifAggMode { BY_APP, BY_SENDER }

/** 计算某周期 [period] 以 [anchorMs] 为锚点的 [start,end] 日期键范围（含两端）。 */
internal fun periodRangeKeys(period: StatsPeriod, anchorMs: Long): Pair<String, String> {
    val startMs = alignToPeriodStart(anchorMs, period)
    val days = when (period) {
        StatsPeriod.DAILY -> 1
        StatsPeriod.WEEKLY -> 7
        StatsPeriod.MONTHLY -> java.util.Calendar.getInstance().apply { timeInMillis = anchorMs }
            .getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        StatsPeriod.YEARLY -> 365
    }
    val endMs = shiftDay(startMs, days - 1)
    return dateKeyOf(startMs) to dateKeyOf(endMs)
}

/** 统计页统一双栏：宽屏 = 左栏(图表+列表) / 右栏(应用排行)；窄屏 = 单栏顺序叠放。 */
@Composable
internal fun StatsDualPane(
    modifier: Modifier = Modifier,
    isWideScreen: Boolean,
    leftScroll: ScrollState,
    leftPanelWidth: Dp,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit
) {
    if (isWideScreen) {
        Row(modifier = modifier.fillMaxSize()) {
            // 左栏：趋势区（图表+列表），可滚动，右摇杆驱动滚动
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(leftScroll)
                        .padding(bottom = Dimens.md),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md)
                ) { left() }
                ScrollProgressBar(leftScroll, modifier = Modifier.align(Alignment.CenterEnd))
            }
            // 两栏中间间距
            Box(modifier = Modifier.width(8.dp), contentAlignment = Alignment.Center) {}
            // 右栏：应用排行（独立 LazyColumn，左摇杆上下移动焦点）
            Column(
                modifier = Modifier
                    .width(leftPanelWidth)
                    .fillMaxSize()
                    .padding(bottom = Dimens.md)
            ) { right() }
        }
    } else {
        // 宽度统一：根容器已带 ContentHorizontal 内边距，此处不再叠加水平内边距，
        // 保证通知历史窄屏内容宽度与其他三个子 Tab 完全一致。
        Column(
            modifier = modifier.verticalScroll(leftScroll).padding(top = Dimens.md - 4.dp, bottom = Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            left()
            right()
        }
    }
}

/** 周期栏：周期分段 + 日期指示按钮（含 PERIOD/DATE 焦点槽）；LB/RB 切换板块已在宽屏 StatusBar 子 Tab 滑块独立提示。 */
@Composable
internal fun StatsPeriodBar(
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    anchorMs: Long,
    onDatePickerOpen: () -> Unit,
    focusZone: StatsFocusZone,
    overviewFocusIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), Arrangement.spacedBy(Dimens.xs), Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f)
                .focusBorder(enabled = focusZone == StatsFocusZone.OVERVIEW && overviewFocusIndex == StatsOverviewSlot.PERIOD)
        ) {
            ReverieSegmentedRow(
                items = selectableStatsPeriods,
                selected = period,
                onSelect = onPeriodChange,
                label = { it.label }
            )
        }
        DateIndicatorButton(
            period = period,
            anchorMs = anchorMs,
            onClick = onDatePickerOpen,
            modifier = Modifier.wrapContentWidth(unbounded = true)
                .focusBorder(enabled = focusZone == StatsFocusZone.OVERVIEW && overviewFocusIndex == StatsOverviewSlot.DATE)
        )
    }
}

/** 图表/列表视图切换滑块（含 VIEW 焦点槽）。 */
@Composable
internal fun StatsViewToggle(
    viewMode: StatViewMode,
    onViewModeChange: (StatViewMode) -> Unit,
    focusZone: StatsFocusZone,
    overviewFocusIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), Arrangement.spacedBy(Dimens.xs), Alignment.CenterVertically) {
        ReverieSegmentedRow(
            items = listOf(StatViewMode.Chart, StatViewMode.List),
            selected = viewMode,
            onSelect = onViewModeChange,
            icon = { if (it == StatViewMode.Chart) Icons.Filled.BarChart else Icons.Filled.List },
            showLabel = false,
            modifier = Modifier.weight(1f)
                .focusBorder(enabled = focusZone == StatsFocusZone.OVERVIEW && overviewFocusIndex == StatsOverviewSlot.VIEW)
        )
    }
}

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
    /** 焦点项包名回传（P1-2）：左摇杆导航改变 focusIndex 时上报当前聚焦排行项，供 A 键启动。 */
    onFocus: (String?) -> Unit = {},
    /** 实际排行条目数上报（HomeScreen 用于限制左摇杆焦点上限，避免空行焦点）。 */
    onEntryCountChange: (Int) -> Unit = {},
    /** 周期切换回调（PeriodTabsRow 触控点击 / 父级 LB/RB 都路由到这里）。 */
    onSelectPeriod: (StatsPeriod) -> Unit = {},
    /** 点击排行条目（触控）：用于启动对应应用。 */
    onEntryClick: (String) -> Unit = {},
    /** 当前子 Tab（速览 / 使用时长 / 启动次数 / 通知历史）：由 HomeScreen 持有 source-of-truth。
     *  横屏下该滑块会被提升到 StatusBar 与大 Tab 同行，故状态须上提到父级共享。 */
    statsSubTab: StatsSubTab = StatsSubTab.OVERVIEW,
    /** 子 Tab 切换回调（HomeScreen 持有；速览 Top 卡跳转 / 周期滑块 / 手柄也都路由到这里）。 */
    onSubTabChange: (StatsSubTab) -> Unit = {},
    /** 子 Tab 滑块是否循环（true=首尾环绕 / false=首尾停止）。 */
    subTabWrapAround: Boolean = false,
    /** 挖孔屏适配开关：透传给内部 dialog，使底部提示栏与统计页主体保持一致的左右安全边距。 */
    cutoutAdapt: Boolean = false,
    /** 是否宽屏（横屏/平板）。必须来自父级 HomeScreen 的单一判定源，
     *  驱动统计页双栏布局（横屏双栏仅在宽屏阈值以上启用，窄屏横屏保持单栏）。
     *  严禁在页内用 LocalConfiguration.screenWidthDp 自行重算，避免双源错位。 */
    isWideScreen: Boolean = false,
    /** 是否横屏（方向，宽>高）。来自父级 HomeScreen 的单一判定源，
     *  驱动统计页子 Tab 滑块收折到 StatusBar（与双栏阈值 isWideScreen 解耦）：
     *  手机横屏（宽度未达双栏阈值）也应收折子 Tab，但不触发双栏布局。
     *  必须与 StatusBar 的 showSubTabHint 用同一个 isLandscape，否则子 Tab 会错位/重复。 */
    isLandscape: Boolean = false,
    /** 手柄焦点区（RANK=左栏排行 / OVERVIEW=右栏总览控件）。手柄全链路重构。 */
    focusZone: StatsFocusZone = StatsFocusZone.RANK,
    /** OVERVIEW 区当前聚焦控件槽（见 [StatsOverviewSlot]）。 */
    overviewFocusIndex: Int = 0,
    /** 控件桥：StatsScreen 注册切视图/切指标能力与可聚焦控件数，供父级 A 键分发。 */
    control: StatsControlBridge? = null,
    modifier: Modifier = Modifier
) {
    // 周期由外部驱动（HomeScreen 持有），StatsScreen 不再维护内部 var
    val activePeriod = period
    val usageGranted by viewModel.usageGranted.collectAsState()
    val apps by viewModel.apps.collectAsState()
    // reportsVersion：后台 sync 完成后自增，作为数据重读 key，保证统计页读到最新报表
    val reportsVersion by viewModel.reportsVersion.collectAsState()
    // 进入统计页时刷新一次（带时间闸门节流，避免每次切 Tab 都全量 sync，R13 省电）
    LaunchedEffect(Unit) { viewModel.refreshReportsThrottled() }

    // 报表按"当前周期 + 锚点日"实时计算（非快照 StateFlow，便于自由选任意日期）
    // reportsVersion 加入 key：sync 完成（数据刷新）后强制重算，避免读到 sync 前的旧数据
    val anchorKey = remember(anchorMs, activePeriod, reportsVersion) { anchorMs to activePeriod to reportsVersion }
    val dailyReport = remember(anchorKey) { viewModel.dailyReportFor(dateKeyOf(anchorMs)) }
    val weeklyReport = remember(anchorKey) { viewModel.weeklyReportFor(anchorMs) }
    val monthlyReport = remember(anchorKey) { viewModel.monthlyReportFor(anchorMs) }
    // 年视图跨热冷合并，可能触及冷层归档（异步 + LRU 缓存）；热层日/周/月仍同步零 IO。
    val yearlyData by produceState<HomeViewModel.YearlyData?>(null, anchorKey, activePeriod) {
        value = withContext(Dispatchers.IO) { viewModel.loadYearlyData(anchorMs) }
    }
    val yearlyReport = yearlyData?.report ?: emptyList()

    val reportEntries = remember(anchorKey, apps, yearlyReport) {
        viewModel.collapseSystemApps(
            when (activePeriod) {
                StatsPeriod.DAILY -> dailyReport
                StatsPeriod.WEEKLY -> weeklyReport
                StatsPeriod.MONTHLY -> monthlyReport
                StatsPeriod.YEARLY -> yearlyReport
            }
        )
    }
    // 上报实际排行条目数：见下方按子 Tab 区分的 LaunchedEffect（速览 Tab 上报 Top 卡数 3）
    val totalMs = reportEntries.sumOf { it.ms }
    val totalCount = reportEntries.sumOf { it.count }
    // 日均（副标题）：本期总时长按周期天数均摊，供「总计」指标卡展示。
    val daysInPeriod = when (activePeriod) {
        StatsPeriod.DAILY -> 1
        StatsPeriod.WEEKLY -> 7
        StatsPeriod.MONTHLY -> java.util.Calendar.getInstance().apply { timeInMillis = anchorMs }
            .getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        StatsPeriod.YEARLY -> 365
    }
    val avgPerDayMs = if (totalMs > 0) totalMs / daysInPeriod else 0L
    // 单日周期时日均=总计属于冗余，仅周期 ≥ 周时展示「日均」副行
    val avgSubtitle = if (activePeriod != StatsPeriod.DAILY && avgPerDayMs > 0) "日均 ${formatHours(avgPerDayMs)}" else null
    // 指标：排除置底的"系统应用"聚合项，仅统计真实用户应用
    val rankedEntries = reportEntries.filter { it.packageName != cn.mocabolka.run.viewmodel.HomeViewModel.SYSTEM_AGGREGATE_PKG }
    val appCount = rankedEntries.count { it.ms > 0L }
    val longestMs = rankedEntries.firstOrNull()?.ms ?: 0L
    val longestPkg = rankedEntries.firstOrNull()?.packageName
    val longestLabel = longestPkg?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.label } ?: longestPkg ?: "—"

    // 小时柱状图（仅在日周期"今天"使用，其它情况显示对应日序列）
    val isAnchorToday = remember(anchorMs, activePeriod) { viewModel.isAnchorToday(anchorMs) }
    // 指标维度由子 Tab 决定（使用时长 / 启动次数），不再提供页内切换
    // 视图切换：图表 / 列表（图标 toggle，所有周期可用；仅展示其一，节省纵向空间）
    var viewMode by remember { mutableStateOf(StatViewMode.Chart) }

    val hourlyModeActive = activePeriod == StatsPeriod.DAILY && isAnchorToday
    // 子 Tab（速览 / 使用时长 / 启动次数 / 通知历史）：现由 HomeScreen 持有（受控），
    // 以支持横屏下将滑块提升进 StatusBar 与大 Tab 同行

    // 指标维度由子 Tab 决定：仅 LAUNCH_COUNT / USAGE_DURATION 更新；
    // 切换到 OVERVIEW / NOTIFICATIONS 时保持旧值，避免 AnimatedContent 退出动画期间
    // 函数层变量（metricInlineItems/chartValues/rankEntries/playtimeRows 等）提前切换为 Duration
    // 导致 LAUNCH_COUNT 退出帧渲染 Duration 数据，视觉上等同于 USAGE_DURATION 内容短暂闪现。
    // metricMode 切换与下方滚动复位整合到同一协程：原来两个独立的 LaunchedEffect(statsSubTab)
    // （一个设 metricMode、一个 scrollToItem(0)）会与 AnimatedContent 退出动画三方竞争，导致
    // 速览→其它子 Tab 卡顿。本轮把 metricMode 切换从独立 effect 移除，整合到下方统一 effect 内，
    // 仍保留「切到 OVERVIEW/NOTIFICATIONS 时保持旧值」语义（旧值由当前 metricMode 自然延续），
    // 但实际效果是 LAUNCH_COUNT ↔ USAGE_DURATION 切换时同步处理两个动作。
    var metricMode by remember { mutableStateOf(StatMetricMode.Duration) }

    // 上报实际排行条目数（按子 Tab 区分）：指标页=排行条目数；速览=3 张 Top 卡；
    // 通知历史由 NotificationTab 自行上报。
    LaunchedEffect(reportEntries.size, statsSubTab) {
        when (statsSubTab) {
            StatsSubTab.USAGE_DURATION, StatsSubTab.LAUNCH_COUNT -> onEntryCountChange(reportEntries.size)
            StatsSubTab.OVERVIEW -> onEntryCountChange(3)
            else -> {}
        }
    }

    // 手柄控件桥注册（手柄全链路重构）：每次重组刷新闭包，保证 HomeScreen 的 A 键
    // 调用命中最新 viewMode/metricMode 与当前周期；指标仅日·今天可切，故槽数动态 3/4。
    val ctx = LocalContext.current
    if (control != null) {
        val c = control
        androidx.compose.runtime.SideEffect {
            c.toggleView = {
                viewMode = if (viewMode == StatViewMode.Chart) StatViewMode.List else StatViewMode.Chart
            }
            // 指标维度已固化为子 Tab，无页内切换；保留无操作闭包以兼容手柄桥
            c.toggleMetric = {}
            c.overviewSlotCount = 3
            c.cycleSubTab = { d ->
                val n = StatsSubTab.entries.size
                val next = statsSubTab.ordinal + d
                if (subTabWrapAround) {
                    onSubTabChange(StatsSubTab.entries[(next + n) % n])
                } else {
                    val clamped = next.coerceIn(0, n - 1)
                    if (clamped != statsSubTab.ordinal) onSubTabChange(StatsSubTab.entries[clamped])
                }
            }
            c.currentSubTab = statsSubTab.ordinal
            // 通知历史 Tab：X=周期（日周月年）；Y=图表/列表视图切换；A=排行钻取；B=钻取返回
            c.notifToggleView = {
                viewMode = if (viewMode == StatViewMode.Chart) StatViewMode.List else StatViewMode.Chart
            }
            c.notifToggleAggregation = {}
            c.notifActivate = {
                val k = c.notifFocusedKey
                if (k != null && k != cn.mocabolka.run.viewmodel.HomeViewModel.SYSTEM_AGGREGATE_PKG) {
                    val pkg = k.substringBefore("\u0001")
                    val sender = if (k.contains("\u0001")) k.substringAfter("\u0001") else null
                    val (sKey, eKey) = periodRangeKeys(activePeriod, anchorMs)
                    ctx.startActivity(
                        NotificationHistoryActivity.createIntent(
                            ctx, pkg, sender, sKey, eKey,
                            if (sender != null) NotificationHistoryActivity.MODE_SENDER
                            else NotificationHistoryActivity.MODE_APP
                        )
                    )
                    true
                } else false
            }
            // 速览 Tab：A 键按焦点序号跳转对应子 Tab（0=使用时长 1=启动次数 2=通知历史）
            c.overviewActivate = {
                if (statsSubTab == StatsSubTab.OVERVIEW) {
                    onSubTabChange(
                        when (focusIndex) {
                            0 -> StatsSubTab.USAGE_DURATION
                            1 -> StatsSubTab.LAUNCH_COUNT
                            else -> StatsSubTab.NOTIFICATIONS
                        }
                    )
                    true
                } else false
            }
        }
    }
    // 进入 OVERVIEW 区时把右栏滚到顶，保证周期/日期/视图/指标控件可见（控件均在总览顶部）。
    // 速览子 Tab 关键补充（修复"左摇杆焦点未跟随滚动页面"）：
    // 原逻辑只滚双栏右栏（leftScrollState），但速览窄屏单栏下控件在 listState(LazyColumn) 内 item 0，
    // 右摇杆把 LazyColumn 滚到中间后，左摇杆切回 OVERVIEW 区时控件已滚出视口、焦点框对用户不可见。
    // 此处额外滚 listState 到 0，让 OVERVIEW 控件（速览 item 0）始终随焦点回到视口顶端。
    // 原逻辑只滚双栏右栏（leftScrollState），但速览窄屏单栏下控件在 listState(LazyColumn) 内 item 0，
    // 右摇杆把 LazyColumn 滚到中间后，左摇杆切回 OVERVIEW 区时控件已滚出视口、焦点框对用户不可见。
    // 此处额外滚 listState，让 OVERVIEW 控件始终随焦点回到视口顶端。
    // 修复（2026-08-04）：左摇杆在 OVERVIEW 区上下移动焦点（PERIOD/DATE/VIEW/METRIC/EXPAND）时，
    // 焦点槽改变必须驱动 listState 滚动到对应 item，使焦点框始终可见（原仅依赖 focusZone 改变，
    // 焦点在 OVERVIEW 区内移动时 focusZone 不变 → 滚动不触发 → 页面不跟随左摇杆焦点滚动）。
    androidx.compose.runtime.LaunchedEffect(focusZone, statsSubTab, overviewFocusIndex) {
        if (focusZone == StatsFocusZone.OVERVIEW && statsSubTab == StatsSubTab.OVERVIEW) {
            // 焦点槽 → 单栏 LazyColumn 的 item 索引映射（与下方单栏布局 item 顺序一致：
            // item0=周期+日期 / item1=指标摘要 / item2=视图切换 / item3=指标滑块(日·今天) / 之后图表/列表/Top卡）。
            val targetItem = when (overviewFocusIndex) {
                StatsOverviewSlot.PERIOD, StatsOverviewSlot.DATE -> 0
                StatsOverviewSlot.VIEW -> 2
                StatsOverviewSlot.METRIC -> 3
                else -> 0
            }
            listState.animateScrollToItem(if (targetItem < 0) 0 else targetItem)
        }
    }
    // 逐小时明细（时长 / 次数 / 每应用）：直接命中按天持久化快照，
    // 避免「点过往再回今天」时的小时数据被重置（需求 #5）。
    val hourlyKey = remember(anchorMs) { dateKeyOf(anchorMs) }
    // reportsVersion 参与 remember key：后台 sync 完成后重读当日小时明细（hourlyKey 本身仍是纯日期）
    // 关键修复：hourlyModeActive 必须纳入 key。否则从非 DAILY 切到 DAILY（anchor 仍是今天、
    // hourlyKey 不变）时，下方 remember 不会重算，仍保留 hourlyModeActive=false 时的 LongArray(0)
    // 占位值；而 playtimeRows 的 key 含 hourlyModeActive 会重算并进入 DAILY+hourly 分支直接
    // hourlyMs[h] 越界（ArrayIndexOutOfBoundsException length=0 index=0 @ L310）。
    // 同时非激活态统一返回长度 24 的零数组（而非长度 0），保证任意索引安全。
    val hourlyMs = remember(hourlyKey, reportsVersion, hourlyModeActive) {
        if (hourlyModeActive) viewModel.hourlyForDate(hourlyKey).ms else LongArray(24)
    }
    val hourlyCnt = remember(hourlyKey, reportsVersion, hourlyModeActive) {
        if (hourlyModeActive) viewModel.hourlyForDate(hourlyKey).count else IntArray(24)
    }
    val hourlyBreak = remember(hourlyKey, reportsVersion, hourlyModeActive) {
        if (hourlyModeActive) viewModel.hourlyForDate(hourlyKey).breakdown else Array(24) { emptyMap() }
    }
    val daySeries = remember(anchorKey) {
        viewModel.daySeriesFor(activePeriod, anchorMs)
    }
    // 指标维度：时长 / 次数（日周月年全部生效，切换后图表与列表共用同一维度）
    val useCount = metricMode == StatMetricMode.Count
    // 指标摘要一行版数据（启动次数页语义独立：总计=启动总次数 / 应用量=有启动记录的应用数 / 最常启动=启动最多的应用，
    // 不复用使用时长的排序结果）
    val metricAppCount = if (useCount) rankedEntries.count { it.count > 0 } else appCount
    val topMetricEntry = if (useCount) rankedEntries.filter { it.count > 0 }.maxByOrNull { it.count }
                         else rankedEntries.firstOrNull()
    val topMetricLabel = topMetricEntry?.packageName?.let { pkg ->
        apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
    } ?: "—"
    val topMetricValueText = topMetricEntry?.let { e ->
        if (useCount) "${e.count} 次" else "${formatHours(e.ms)} 小时"
    }
    val metricInlineItems = listOf(
        MetricInlineItem("总计", if (useCount) "$totalCount 次" else "${formatHours(totalMs)} 小时", avgSubtitle),
        MetricInlineItem("应用量", "$metricAppCount 个"),
        MetricInlineItem(if (useCount) "最常启动" else "最长使用", topMetricLabel, topMetricValueText)
    )
    // 图表标题与数值（随指标模式切换，图表与列表共用同一标题/数据）
    val metricWord = if (useCount) "启动次数" else "时长"
    val chartTitle = when (activePeriod) {
        StatsPeriod.DAILY -> if (hourlyModeActive) "今日各小时$metricWord" else "当日游玩$metricWord"
        StatsPeriod.WEEKLY -> "本周每日$metricWord"
        StatsPeriod.MONTHLY -> "本月每日$metricWord"
        StatsPeriod.YEARLY -> "本年每月$metricWord"
    }
    val chartValues = if (hourlyModeActive)
        (if (useCount) hourlyCnt.map { it.toLong() }.toLongArray() else hourlyMs)
    else if (useCount) (if (activePeriod == StatsPeriod.YEARLY) yearlyData?.monthCounts?.map { it.second.toLong() }?.toLongArray() ?: longArrayOf() else viewModel.dayCountSeriesFor(activePeriod, anchorMs).map { it.toLong() }.toLongArray())
    else daySeries

    // ── 环比上期（图表叠加对比）& 排行维度排序 & 洞察 ──
    val prevAnchorMs = remember(anchorMs, activePeriod) {
        shiftAnchorByPeriod(anchorMs, activePeriod, -1)
    }
    val prevValues: LongArray = remember(prevAnchorMs, activePeriod, useCount, hourlyModeActive, hourlyKey, yearlyData) {
        if (activePeriod == StatsPeriod.YEARLY) {
            longArrayOf() // 年视图跨热冷异步加载，跨年对比叠加暂置空
        } else if (hourlyModeActive) {
            val pk = dateKeyOf(shiftAnchorByPeriod(anchorMs, StatsPeriod.DAILY, -1))
            val h = viewModel.hourlyForDate(pk)
            if (useCount) h.count.map { it.toLong() }.toLongArray() else h.ms
        } else {
            if (useCount) viewModel.dayCountSeriesFor(activePeriod, prevAnchorMs)
                .map { it.toLong() }.toLongArray()
            else viewModel.daySeriesFor(activePeriod, prevAnchorMs)
        }
    }
    val rankEntries = remember(reportEntries, useCount) {
        val sysPkg = cn.mocabolka.run.viewmodel.HomeViewModel.SYSTEM_AGGREGATE_PKG
        val sys = reportEntries.filter { it.packageName == sysPkg }
        val users = reportEntries.filter { it.packageName != sysPkg }
            .sortedByDescending { if (useCount) it.count.toLong() else it.ms }
        users + sys
    }

    // 每天游玩时长列表（日视图升序 00:00→23:00，周/月逐日，年逐月）。
    // 按当前指标模式取值，隐藏 0 值项；附该时段 Top3 应用图标。
    val playtimeTitle = chartTitle
    val playtimeRows = remember(activePeriod, anchorMs, hourlyModeActive, metricMode, hourlyMs, hourlyCnt, hourlyBreak, yearlyData) {
        // 隐藏「无信息」与「未来」的列表项：月/年视图下跨度为自然周期，
        // 未来天/月尚未产生数据、历史中 0 值的天/月也视为空 → 直接不展示。
        val now = System.currentTimeMillis()
        when (activePeriod) {
            StatsPeriod.DAILY -> if (hourlyModeActive) {
                (0..23).mapNotNull { h ->
                    val v = if (useCount) hourlyCnt.getOrElse(h) { 0 }.toLong() else hourlyMs.getOrElse(h) { 0L }
                    if (v <= 0L) return@mapNotNull null
                    val top = hourlyBreak.getOrNull(h)
                        ?.entries
                        ?.sortedByDescending { if (useCount) it.value.second.toLong() else it.value.first }
                        ?.take(3)?.map { it.key } ?: emptyList()
                    PlaytimeRow("%02d:00".format(h), v, top, h)
                }
            } else {
                val list = if (useCount) viewModel.dailyCountList(1, anchorMs) else viewModel.dailyUsageList(1, anchorMs)
                list.map { (d, v) -> PlaytimeRow(formatDayLabel(d), v.toLong(), viewModel.dayAppTop(d), -1) }
            }
            StatsPeriod.WEEKLY -> {
                val list = if (useCount) viewModel.weekDailyCountList(anchorMs) else viewModel.weekDailyList(anchorMs)
                list.map { (d, v) -> PlaytimeRow(formatDayLabel(d), v.toLong(), viewModel.dayAppTop(d), -1) }
            }
            StatsPeriod.MONTHLY -> {
                val list = if (useCount) viewModel.monthDailyCountList(anchorMs) else viewModel.monthDailyList(anchorMs)
                list.filter { (d, v) -> v.toLong() > 0L && d <= now }
                    .map { (d, v) -> PlaytimeRow(formatDayLabel(d), v.toLong(), viewModel.dayAppTop(d), -1) }
            }
            StatsPeriod.YEARLY -> {
                val list = if (useCount) yearlyData?.monthCounts ?: emptyList() else yearlyData?.monthTotals ?: emptyList()
                list.filter { (d, v) -> v.toLong() > 0L && d <= now }
                    .map { (d, v) -> PlaytimeRow(formatMonthLabel(d), v.toLong(), viewModel.monthAppTop(d), -1) }
            }
        }
    }

    // 洞察卡已随完整指标卡整体归并到速览页（速览按「今日」维度自行计算），指标页不再展示。

    // 超长列表截断：> 7 个有效项时折叠并给出"展开全部"按钮。
    // 日"今天"：当前时段 + 时长/次数前 3 时段；过往：首段 + 其后 3 段。
    var expanded by remember(activePeriod, anchorMs, metricMode) { mutableStateOf(false) }
    val showExpand = playtimeRows.size > 7
    val displayRows = if (showExpand && !expanded) {
        if (hourlyModeActive) {
            val ch = currentHour()
            val cur = playtimeRows.firstOrNull { it.hour == ch }
            val top3 = playtimeRows.filter { it.hour != ch }.sortedByDescending { it.value }.take(3)
            (listOfNotNull(cur) + top3).sortedBy { it.hour }
        } else {
            playtimeRows.take(4)
        }
    } else playtimeRows

    // P1-2：把当前聚焦排行项包名回传给 HomeScreen，供手柄 A 键启动（消除功能死区）
    LaunchedEffect(focusIndex, rankEntries) {
        onFocus(rankEntries.getOrNull(focusIndex)?.packageName)
    }

    val listState = listState
    // 内容入场动画：总览卡 / 图表卡 / 列表在切换周期或视图时统一淡入 + 轻微上滑（reduceMotion 时立即到位）。
    val cardEnter = if (reduceMotion) EnterTransition.None else
        fadeIn(animationSpec = tween(280)) + slideInVertically(
            animationSpec = tween(280), initialOffsetY = { it / 10 }
        )

    // 任务 11（重构）：Stats 页横屏双栏模式（isWideScreen 由父级 HomeScreen 统一判定后透传入参）
    // 新双栏设计（2026-07-28）：左 = 总览总计（图表+列表，右摇杆滚动）/ 右 = 应用排行（左摇杆焦点）。
    // 左摇杆仅在右栏(排行)移动焦点，右摇杆仅在左栏(总计)滚动，两摇杆互不越界。
    // 注意：isWideScreen 是函数入参（来自 HomeScreen 单一真源），禁止在此用 screenWidthDp 重算。

    // 横屏↔竖屏切换时把排行列表与左栏滚动锚回顶部：宽屏右栏(应用排行)记录的滚动偏移
    // 会被窄屏统一列表复用，导致竖屏下默认停在「应用排行」段，此处在切屏时复位。
    androidx.compose.runtime.LaunchedEffect(isWideScreen) {
        runCatching { listState.scrollToItem(0) }
        runCatching { leftScrollState.scrollTo(0) }
    }
    // 滚动复位 + metricMode 切换整合：在切换子 Tab 时同步处理滚动复位与指标维度切换，
    // 单协程串行执行，避免两个 LaunchedEffect(statsSubTab) 与 AnimatedContent
    // 退出动画竞争导致切子 Tab 卡顿（特别是速览→其它子 Tab 期间旧速览页与新指标页同框渲染）。
    // 「切到 OVERVIEW/NOTIFICATIONS 时保持旧值」语义：metricMode 仅在 LAUNCH_COUNT ↔ USAGE_DURATION
    // 之间切换，OVERVIEW/NOTIFICATIONS 不会进入此分支，metricMode 自然延续旧值。
    androidx.compose.runtime.LaunchedEffect(statsSubTab) {
        // 修复（2026-08-04）：listState 在 AnimatedContent 中由新旧两页共享，退出动画期间旧 overview 页
        // 仍在引用同一 listState。若切换瞬间立即 scrollToItem(0)，会把正在滑出的旧页也跳回 0，
        // 与 panelSwitch 的 slide 退出帧争抢同一 LazyListState → 掉帧/跳动（速览→其它子 Tab 卡顿主因）。
        // 改为延迟到退出动画结束后再复位（MotionSpec.TabFade=180ms，留余量取 200ms），
        // 彻底消除同帧争抢；metricMode 同步切换仍即时执行，不影响数据正确性。
        when (statsSubTab) {
            StatsSubTab.LAUNCH_COUNT -> metricMode = StatMetricMode.Count
            StatsSubTab.USAGE_DURATION -> metricMode = StatMetricMode.Duration
            else -> { /* OVERVIEW / NOTIFICATIONS：保持旧值，保护退出动画期间外层数据一致性 */ }
        }
        if (statsSubTab != StatsSubTab.OVERVIEW) {
            kotlinx.coroutines.delay(200)
            runCatching { listState.scrollToItem(0) }
            runCatching { leftScrollState.scrollTo(0) }
        }
    }

    // 左栏（应用排行）固定宽度，右栏（总计+图表）自适应剩余空间
    val leftPanelWidth = Dimens.RightPanelWidth
    Box(modifier = modifier.fillMaxSize().padding(horizontal = Dimens.ContentHorizontal)) {
        Column(modifier = Modifier.fillMaxSize()) {
        // ── 子 Tab 栏（速览 / 使用时长 / 启动次数 / 通知历史）──
        // 横屏（isLandscape）：滑块提升进 StatusBar 与大 Tab 同行（StatusBar.subHeaderContent），
        // 故本页不渲染，避免双源不一致。
        // 竖屏（!isLandscape）：StatusBar 不展示子 Tab，此处独立成行渲染（fillMaxWidth 等分整行）。
        if (!isLandscape) {
            // 竖屏独立子 Tab 行：LB 翻到上一子 Tab / RB 翻到下一子 Tab（与横屏 subHeaderContent 同源，
            // 角标不可丢失——子 Tab 行在"彻底删除页内子 Tab 行"那次操作曾整段被删，LB/RB 角标一并丢失，
            // 本次竖屏恢复时一并补回）。
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.xs)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                ) {
                    GamepadVisible { KeyBadge(KeyToken.LB) }
                    ReverieSegmentedRow(
                        items = StatsSubTab.entries.toList(),
                        selected = statsSubTab,
                        onSelect = onSubTabChange,
                        label = { it.label },
                        fillMaxWidth = true
                    )
                    GamepadVisible { KeyBadge(KeyToken.RB) }
                }
            }
        }
        // ── 三 Tab 内容：过渡统一走全局动画机 panelSwitch（板块切换机）──
        AnimatedContent(
            targetState = statsSubTab,
            transitionSpec = {
                MotionMachines.panelSwitch(
                    MotionMachines.linearForward(initialState.ordinal, targetState.ordinal),
                    reduceMotion
                )
            },
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentKey = { it },
            label = "statsSubTab"
        ) { subTab ->
            // 退出动画期间 statsSubTab 仍为旧值：指标维度须基于 statsSubTab 派生，
            // 避免「启动次数骨架 + Duration 数据」竞态（与 455 行函数顶层 metricMode 区分）。
            val localMetricMode = if (subTab == StatsSubTab.LAUNCH_COUNT) StatMetricMode.Count else StatMetricMode.Duration
            when (subTab) {
                StatsSubTab.OVERVIEW -> StatsOverviewTab(
                    viewModel = viewModel,
                    apps = apps,
                    // 双栏/单栏判定单一真源：来自父级 HomeScreen（基于 LocalWindowInfo.containerSize），
                    // 严禁在 StatsOverviewTab 内自行重算，否则与父级不一致导致切换子 Tab 时布局结构跳变卡顿。
                    isWideScreen = isWideScreen,
                    reduceMotion = reduceMotion,
                    onOpenSubTab = { onSubTabChange(it) },
            focusIndex = if (focusZone == StatsFocusZone.RANK) focusIndex else -1,
            gamepadConnected = gamepadConnected,
            scrollState = leftScrollState,
            modifier = Modifier.fillMaxSize()
        )
                StatsSubTab.NOTIFICATIONS -> NotificationTab(
                    viewModel = viewModel,
                    apps = apps,
                    activePeriod = activePeriod,
                    anchorMs = anchorMs,
                    onDatePickerOpen = { onDatePickerOpenChange(true) },
                    viewMode = viewMode,
                    onViewModeChange = { viewMode = it },
                    focusZone = focusZone,
                    overviewFocusIndex = overviewFocusIndex,
                    focusIndex = focusIndex,
                    isWideScreen = isWideScreen,
                    gamepadConnected = gamepadConnected,
                    reduceMotion = reduceMotion,
                    control = control,
                    onFocus = onFocus,
                    onEntryCountChange = onEntryCountChange,
                    onSelectPeriod = onSelectPeriod,
                    onEntryClick = onEntryClick,
            leftScrollState = leftScrollState,
            listState = listState,
            reportsVersion = reportsVersion,
            modifier = Modifier.fillMaxSize()
        )
                StatsSubTab.USAGE_DURATION, StatsSubTab.LAUNCH_COUNT -> Column(modifier = Modifier.fillMaxSize()) {
            val metricMode = localMetricMode
            // 使用时长 / 启动次数：结构相同，指标维度由 metricMode（派生自子 Tab）驱动
            if (isWideScreen) {
            // 双栏布局：左 = 总览总计（图表+列表，右摇杆滚动）/ 右 = 应用排行（LazyColumn，左摇杆焦点）。
            // 左栏自适应剩余空间、右栏固定宽度（RightPanelWidth），与新双栏设计一致（旧版左右相反）。
            Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                // 左栏：总览总计（指标卡 + 图表/列表，可滚动，右摇杆驱动滚动）
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(leftScrollState),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md)
                ) {
                    // 左栏顶部：LB/RB 周期切换 + 日期选择按钮
                    StatsPeriodBar(
                        period = activePeriod,
                        onPeriodChange = onSelectPeriod,
                        anchorMs = anchorMs,
                        onDatePickerOpen = { onDatePickerOpenChange(true) },
                        focusZone = focusZone,
                        overviewFocusIndex = overviewFocusIndex
                    )
                    // 指标摘要一行版：总计 / 应用量 / 最长使用(最常启动)。完整指标卡与洞察已归速览页。
                    MetricSummaryInline(items = metricInlineItems)
                    // 视图切换（图表 / 列表，常驻）
                    StatsViewToggle(
                        viewMode = viewMode,
                        onViewModeChange = { viewMode = it },
                        focusZone = focusZone,
                        overviewFocusIndex = overviewFocusIndex
                    )
                    // 未授权提示（原「总计」标题行仅保留权限提示部分）
                    if (!usageGranted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "未授权使用情况访问",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            val ctx = LocalContext.current
                            ReverieFilledButton(
                                onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                                text = "去开启"
                            )
                        }
                    }
                    // 图表（标题/数值与列表共用 chartTitle/chartValues，随指标模式切换）
                    if (viewMode == StatViewMode.Chart) {
                        ChartCard(
                            title = chartTitle,
                            values = chartValues,
                            period = activePeriod,
                            metric = metricMode,
                            prevValues = prevValues,
                            showValueLabels = !hourlyModeActive && chartValues.size <= 12
                        )
                    }
                    if (viewMode == StatViewMode.List) {
                        DailyPlaytimeList(
                            title = playtimeTitle,
                            rows = displayRows,
                            apps = apps,
                            mode = metricMode,
                            reduceMotion = reduceMotion,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (showExpand && !expanded) {
                            ReverieTonalButton(
                                onClick = { expanded = true },
                                text = "展开全部 (${playtimeRows.size})",
                                modifier = Modifier.fillMaxWidth(),
                                focused = gamepadConnected && control?.focusedExpand ?: false
                            )
                            // 注册当前子 Tab 的展开回调（A 键在 EXPAND 槽触发展开/收起）
                            control?.expandToggle = { expanded = !expanded }
                        }
                    }
                }
                    ScrollProgressBar(leftScrollState, modifier = Modifier.align(Alignment.CenterEnd))
                }
                // 两栏中间间距
                Box(
                    modifier = Modifier.width(8.dp),
                    contentAlignment = Alignment.Center
                ) {}
                // 右栏：应用排行（独立 LazyColumn，左摇杆上下移动焦点）
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
                    if (rankEntries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyHint(text = if (usageGranted) "暂无${activePeriod.label}报数据"
                                              else "请在系统设置中开启『使用情况访问』")
                        }
                    } else {
                        // 整体滑块（手柄全链路重构）：右栏排行滚动纯投影，手柄永不聚焦。
                        Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = Dimens.xxs, bottom = Dimens.HintBarHeight
                            ),
                            verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                        ) {
                            items(rankEntries, key = { it.packageName }, contentType = { "appRankRow" }) { entry ->
                                AppRankRow(
                                    rank = rankEntries.indexOf(entry) + 1,
                                    entry = entry,
                                    totalMs = if (useCount) totalCount.toLong() else totalMs,
                                    useCount = useCount,
                                    label = rankEntryLabel(entry, apps),
                                    icon = apps.firstOrNull { it.packageName == entry.packageName }?.icon,
                                    entranceIndex = rankEntries.indexOf(entry),
                                    reduceMotion = reduceMotion,
                                    focused = gamepadConnected && focusZone == StatsFocusZone.RANK && rankEntries.indexOf(entry) == focusIndex,
                                    onClick = { onEntryClick(entry.packageName) }
                                )
                            }
                        }
                            ScrollProgressBar(listState, rankEntries.size, modifier = Modifier.align(Alignment.CenterEnd))
                        }
                    }
                }
            }
        } else {
            // 中等屏：单栏原版
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = Dimens.md - 4.dp, bottom = Dimens.HintBarHeight
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                // 周期滑块（日周月年）。手柄周期切换已迁到 X 键（HomeScreen X 分支 + 底部 Hint(KeyToken.X,"周期")），
                // 故此处不再显示 LB/RB 浮块，避免与 X 键提示冲突。
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).focusBorder(
                                enabled = focusZone == StatsFocusZone.OVERVIEW && overviewFocusIndex == StatsOverviewSlot.PERIOD
                            )
                        ) {
                            ReverieSegmentedRow(
                                items = selectableStatsPeriods,
                                selected = activePeriod,
                                onSelect = onSelectPeriod,
                                label = { it.label }
                            )
                        }
                        // 日期指示器按钮（右侧），点击 / View 键打开日期选择
                        // R8 修复：竖屏分支同样 wrapContentWidth 防挤压
                        DateIndicatorButton(
                            period = activePeriod,
                            anchorMs = anchorMs,
                            onClick = { onDatePickerOpenChange(true) },
                            modifier = Modifier.wrapContentWidth(unbounded = true).focusBorder(
                                enabled = focusZone == StatsFocusZone.OVERVIEW && overviewFocusIndex == StatsOverviewSlot.DATE
                            )
                        )
                    }
                }
                // 指标摘要一行版（完整指标卡与洞察已归速览页；窄屏同样一行三项，随维度切换语义）
                item(key = "metrics") {
                    AnimatedVisibility(visible = true, enter = cardEnter) {
                        MetricSummaryInline(items = metricInlineItems)
                    }
                }
                // 视图切换（图标 toggle：图表 / 列表，常驻）+ 指标切换（使用时长 / 启动次数，仅日"今天"）
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                    ) {
                        ReverieSegmentedRow(
                            items = listOf(StatViewMode.Chart, StatViewMode.List),
                            selected = viewMode,
                            onSelect = { viewMode = it },
                            icon = { if (it == StatViewMode.Chart) Icons.Filled.BarChart else Icons.Filled.List },
                            showLabel = false,
                            modifier = Modifier.weight(1f).focusBorder(
                                enabled = focusZone == StatsFocusZone.OVERVIEW && overviewFocusIndex == StatsOverviewSlot.VIEW
                            )
                        )

                    }
                }
                if (viewMode == StatViewMode.Chart) {
                    item(key = "chart_$viewMode") {
                        AnimatedVisibility(visible = true, enter = cardEnter) {
                            ChartCard(
                                title = chartTitle,
                                values = chartValues,
                                period = activePeriod,
                                metric = metricMode,
                                prevValues = prevValues,
                                showValueLabels = !hourlyModeActive && chartValues.size <= 12
                            )
                        }
                    }
                }
                if (viewMode == StatViewMode.List) {
                    item(key = "list_$viewMode") {
                        AnimatedVisibility(visible = true, enter = cardEnter) {
                            DailyPlaytimeList(
                                title = playtimeTitle,
                                rows = displayRows,
                                apps = apps,
                                mode = metricMode,
                                reduceMotion = reduceMotion
                            )
                        }
                    }
                    if (showExpand && !expanded) {
                        item {
                            ReverieTonalButton(
                                onClick = { expanded = true },
                                text = "展开全部 (${playtimeRows.size})",
                                modifier = Modifier.fillMaxWidth(),
                                focused = gamepadConnected && control?.focusedExpand ?: false
                            )
                            // 注册当前子 Tab 展开回调（指标子 Tab 生效）
                            control?.expandToggle = { expanded = !expanded }
                        }
                    }
                }
                item {
                    Text(
                        text = "应用排行",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (rankEntries.isEmpty()) {
                    item {
                        EmptyHint(text = if (usageGranted) "暂无${activePeriod.label}报数据" else "请在系统设置中开启『使用情况访问』")
                    }
                } else {
                    items(rankEntries, key = { it.packageName }, contentType = { "appRankRow" }) { entry ->
                        AppRankRow(
                            rank = rankEntries.indexOf(entry) + 1,
                            entry = entry,
                            totalMs = if (useCount) totalCount.toLong() else totalMs,
                            label = rankEntryLabel(entry, apps),
                            icon = apps.firstOrNull { it.packageName == entry.packageName }?.icon,
                            entranceIndex = rankEntries.indexOf(entry),
                            reduceMotion = reduceMotion,
                            useCount = useCount,
                            focused = gamepadConnected && focusZone == StatsFocusZone.RANK && rankEntries.indexOf(entry) == focusIndex
                        )
                    }
                }
            }
        }
                } // 右栏应用排行 Column 结束
            } // when(subTab) 结束
        } // AnimatedContent 结束

        // 统计页（STATS）专属底部按键指示栏：与主体隔离的独立槽位（规则2）。
        // 规则3：LB/RB 切周期已在周期滑块左右两侧给出角标，此处不复现。
        // 规则4：禁止任何上下滚动/方向键提示（原 "↑浏览/↓浏览" 已删除）。
        // 跨栏提示（原骑缝浮块已移除）统一置于此处；窄屏无跨栏，仅宽屏手柄连接时显示。
        // 仅保留无按钮旁落点的 VIEW「日期」独立快捷提示。
        // 规则：A 确认 / B 返回为默认键不进底部提示栏（退出见标题栏）；LB/RB/LT/RT 不在此展示。
        // R9 修复（2026-07-14）：移除 LB/RB 周期提示（已在周期滑块旁浮块，避免重复）。
        // 速览子 Tab 无额外按键提示：整体跳过 GamepadBottomHintBar，
        // 避免空内容时仍渲染底部提示栏占位（分割线 + 固定高度）空挂在页面底部。
        // 其他子 Tab 正常渲染键位提示栏。
        if (statsSubTab != StatsSubTab.OVERVIEW) {
            GamepadBottomHintBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 跨栏逻辑已整体移除：不再显示 LS/RS「跨栏」提示。
                // 所有周期均有日期选择器（周/月/年走自绘 ReveriePeriodPickerDialog，日走原生 DatePicker）。
                // 四子 Tab 键位：LB/RB 切换板块全局有效；使用时长/启动次数/通知历史三 Tab 统一 X=周期、Y=视图；
                // VIEW 日期仅非速览 Tab 有效。
                Hint(KeyToken.VIEW, "日期")
                if (statsSubTab == StatsSubTab.USAGE_DURATION || statsSubTab == StatsSubTab.LAUNCH_COUNT) {
                    Hint(KeyToken.X, "周期")
                    Hint(KeyToken.Y, "视图")
                } else if (statsSubTab == StatsSubTab.NOTIFICATIONS) {
                    // 通知历史 Tab 与另两页对齐：X=周期（日周月年）、Y=视图（图表/列表）。
                    Hint(KeyToken.X, "周期")
                    Hint(KeyToken.Y, "视图")
                }
            }
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
/** 指标摘要一行版条目：label=指标名，value=主值文本，sub=副行文本（可空）。 */
internal data class MetricInlineItem(val label: String, val value: String, val sub: String? = null)

/**
 * 指标摘要一行版（2026-07-28 结构归并）：完整指标卡归速览页后，
 * 指标页（使用时长/启动次数）与通知历史页共用本行式紧凑摘要——
 * 单张 SurfaceCard 内三项均分横排：指标名(bodySmall/onSurfaceVariant) + 主值(titleMedium) + 副行(bodySmall)。
 */
@Composable
internal fun MetricSummaryInline(items: List<MetricInlineItem>, modifier: Modifier = Modifier) {
    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        level = SurfaceTokens.CardLevel.Default,
        shape = Dimens.lg
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.md),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            items.forEach { item ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (item.sub != null) {
                        Text(
                            text = item.sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MetricCard(
    title: String,
    value: Long,
    format: (Long) -> String,
    unit: String,
    trend: Trend?,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    /** 副标题（如「日均 X」），位于标题下方、数值上方。 */
    subtitle: String? = null,
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
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
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

internal data class Trend(val up: Boolean, val percent: Int)

/**
 * 「最长使用」指标卡（C4-1）：展示单个应用名 + 时长，比孤立数值更具信息量。
 * 应用名过长时省略，时长用 AnimatedNumberText 缓动。
 */
@Composable
internal fun LongestCard(
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
internal fun ChartCard(
    title: String,
    values: LongArray,
    period: StatsPeriod,
    metric: StatMetricMode = StatMetricMode.Duration,
    prevValues: LongArray? = null,
    showValueLabels: Boolean = false
) {
    val rawMax = (values.maxOrNull() ?: 0L)
    val prevMax = (prevValues?.maxOrNull() ?: 0L)
    val maxV = (if (rawMax > prevMax) rawMax else prevMax).coerceAtLeast(1L)
    // 维度配色：时长=主色，次数=次色；切换维度时图示颜色与文案同步变化（需求 #2）
    val isCount = metric == StatMetricMode.Count
    val barAccent = if (isCount) MaterialTheme.colorScheme.secondary else ChartAccent
    val barNormal = if (isCount) MaterialTheme.colorScheme.secondary else ChartNormal
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
    val prevRatios = prevValues?.mapIndexed { i, v ->
        val ratio = (v.toFloat() / maxV).coerceIn(0f, 1f)
        animateFloatAsState(
            targetValue = ratio,
            animationSpec = tween(320, delayMillis = MotionSpec.staggerDelay(i, maxMs = 120).toInt()),
            label = "pbar$i"
        )
    }
    SurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        level = SurfaceTokens.CardLevel.Default,
        shape = Dimens.lg
    ) {
        Column(
            modifier = Modifier.padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface)
            // 图例：当前维度（时长 / 次数），切换维度时颜色与文案同步变化
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(barAccent, RoundedCornerShape(Dimens.RadiusXxs))
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isCount) "次数" else "时长",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (prevValues != null) {
                    Spacer(Modifier.width(Dimens.sm))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(barAccent.copy(alpha = 0.25f), RoundedCornerShape(Dimens.RadiusXxs))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "上期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // 竖屏（窄屏）纵向空间充裕，图表加高到 220dp 更易读；横屏/宽屏保持 180dp。
        val cfg = LocalConfiguration.current
        val chartHeight = if (cfg.screenWidthDp < cfg.screenHeightDp) 220.dp else 180.dp
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val w = maxWidth
            val h = maxHeight
            val n = values.size.coerceAtLeast(1)
            // 标签用的 dp 槽宽（Canvas 外的 Compose 布局用 dp，与柱心对齐）。
            val slotDp = w.value / n
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 关键修复：柱宽必须按 Canvas 像素尺寸 size.width 计算。原实现用 dp(w.value)
                // 当像素传入，xxhdpi 下画布宽约 1080px 而 w.value≈360，柱宽被 density(~3x)
                // 缩小 → 周视图 7 天等窄周期场景柱体过细、与 X 轴天数不匹配。
                val slot = size.width / n
                val barWidth = slot * 0.6f
                val gap = slot * 0.4f
                val accent = barAccent
                val normal = barNormal
                // 水平虚线（参考 Tai：30/60/100% 虚线）
                val dashPath = Path()
                listOf(0.33f, 0.66f, 1.0f).forEach { ratio ->
                    val y = size.height * (1f - ratio)
                    dashPath.moveTo(0f, y); dashPath.lineTo(size.width, y)
                }
                drawPath(
                    path = dashPath,
                    color = barAccent.copy(alpha = 0.18f),
                    style = Stroke(width = 1.2f)
                )
                if (prevRatios != null && prevValues != null) {
                    prevValues.forEachIndexed { i, _ ->
                        if (i < prevRatios.size) {
                            val ratio = prevRatios[i].value
                            val barH = size.height * ratio
                            val x = i * (barWidth + gap) + gap / 2f
                            val y = size.height - barH
                            drawRect(
                                color = barAccent.copy(alpha = 0.16f),
                                topLeft = Offset(x, y),
                                size = Size(barWidth * 0.7f, barH)
                            )
                        }
                    }
                }
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
            // 数值标签：每根柱顶部显示具体数值（showValueLabels 控制，≤12 柱时启用，避免拥挤）
            if (showValueLabels) {
                values.forEachIndexed { i, v ->
                    if (v <= 0L) return@forEachIndexed
                    val ratio = animatedRatios[i].value.coerceIn(0f, 1f)
                    val centerX = ((i + 0.5f) * slotDp).dp
                    val topY = (h.value * (1f - ratio)).dp - 13.dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = centerX - 11.dp, y = topY.coerceAtLeast(0.dp))
                    ) {
                        Text(
                            text = if (isCount) "$v" else run {
                                val minutes = (v / 60000).toInt()
                                if (minutes >= 60) "%.1fh".format(minutes / 60f) else "${minutes}m"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
            // 数值标签（最大值线）
            Text(
                text = if (isCount) "$maxV" else formatHoursMs(maxV),
                style = MaterialTheme.typography.labelSmall,
                color = barAccent,
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
            } else if (n == 7) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { wd ->
                    Text(wd, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else when (period) {
                StatsPeriod.YEARLY -> {
                    // 年视图：12 个月，刻度用「月」语义（1/6/12 月），避免裸索引「0 6 11」让用户误以为图表损坏
                    Text("1月", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("6月", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("12月", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatsPeriod.MONTHLY -> {
                    // 月视图：n=当月天数（28–31），刻度用「日」语义（1/中/末日）
                    Text("1", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(n + 1) / 2}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$n", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
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
}

// ───────────────────────────────────────────────────────────────
// 应用排行行：图标 + 排名 + 名称 + 进度条 + 时长
// ───────────────────────────────────────────────────────────────
@Composable
internal fun AppRankRow(
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
    focused: Boolean = false,
    /** 触控点击：启动对应应用。 */
    onClick: () -> Unit = {},
    /** 排行是否按启动次数维度展示（与图表/列表一致，需求 #8）。 */
    useCount: Boolean = false
) {
    val valueMs = if (useCount) entry.count.toLong() else entry.ms
    val percent = if (totalMs > 0) valueMs.toFloat() / totalMs else 0f
    val animP by animateFloatAsState(
        targetValue = percent,
        animationSpec = tween(600),
        label = "rowAnim"
    )
    // 错峰淡入入场（reduceMotion 时立即到位）
    // 使用 MotionSpec.Fast（220ms）+ stagger 12ms/项上限 240ms，
    // 与主页列表入场动画风格统一且跟手。
    val (entA, entY) = rememberStaggeredEntrance(
        entranceIndex = entranceIndex,
        entranceKey = "",
        reduceMotion = reduceMotion,
        perMs = 12,
        maxMs = 240,
        startOffsetY = 10f
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
            .clickable { onClick() }
            .padding(horizontal = Dimens.md, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val medal = if (rank <= 3) listOf(Color(0xFFE6B450), Color(0xFFC2C9D1), Color(0xFFCD9154))[rank - 1] else null
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(Dimens.RadiusSm))
                .background(medal ?: Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (medal != null) Color.Black else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(Dimens.xxs))
        if (icon != null) {
            androidx.compose.foundation.Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusMd))
            )
        } else if (entry.packageName == cn.mocabolka.run.viewmodel.HomeViewModel.SYSTEM_AGGREGATE_PKG) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusMd))
                    .background(SurfaceTokens.focusBg()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusMd))
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
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (useCount) "${entry.count}" else formatHoursMs(entry.ms),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (useCount) "次" else "%.0f%%".format(percent * 100),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(Dimens.md))
            .background(SurfaceTokens.cardSurfaceGlassAware(SurfaceTokens.CardLevel.Default)),
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

/** 按周期平移锚点（delta=-1 即上一周期），用于图表环比对比。 */
internal fun shiftAnchorByPeriod(ms: Long, period: StatsPeriod, delta: Int): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    when (period) {
        StatsPeriod.DAILY -> cal.add(java.util.Calendar.DAY_OF_MONTH, delta)
        StatsPeriod.WEEKLY -> cal.add(java.util.Calendar.DAY_OF_MONTH, delta * 7)
        StatsPeriod.MONTHLY -> cal.add(java.util.Calendar.MONTH, delta)
        StatsPeriod.YEARLY -> cal.add(java.util.Calendar.YEAR, delta)
    }
    return cal.timeInMillis
}

/** 把毫秒格式化为"X.YY 小时"（Tai 风格保留两位小数）。 */
internal fun formatHours(ms: Long): String {
    val hours = ms / 3_600_000.0
    return "%.2f".format(hours)
}

/** 把毫秒格式化为"X小时Y分钟Z秒"（紧凑，用于列表；速览/通知 Tab 共用）。 */
internal fun formatHoursMs(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when {
        h > 0 -> "${h}小时${m}分"
        m > 0 -> "${m}分${sec}秒"
        else -> "${sec}秒"
    }
}

/** 指标模式：使用时长 / 启动次数（图表与列表共用）。 */
internal enum class StatMetricMode { Duration, Count }

/** 视图模式：图表 / 列表（图标 toggle 切换展示样式，与指标滑块同区）。 */
internal enum class StatViewMode { Chart, List }

/** 每日/每月/每小时列表行：标签 + 数值（时长ms 或 次数）+ 该时段 Top3 应用包名 + 小时（日视图用）。 */
private data class PlaytimeRow(
    val label: String,
    val value: Long,
    val topPkgs: List<String> = emptyList(),
    val hour: Int = -1
)

/** 洞察卡：标签 + 主值 + 副值（如单位）。 */
internal data class StatInsight(val label: String, val primary: String, val secondary: String = "")

@Composable
internal fun InsightsCard(insights: List<StatInsight>) {
    if (insights.isEmpty()) return
    SurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        level = SurfaceTokens.CardLevel.Default,
        shape = Dimens.lg
    ) {
        Column(
            modifier = Modifier.padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Text("洞察", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                insights.forEach { ins ->
                    Column(
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(Dimens.md))
                            .background(SurfaceTokens.focusBg())
                            .padding(Dimens.sm)
                    ) {
                        Text(ins.label, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ins.primary, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        if (ins.secondary.isNotEmpty()) Text(
                            ins.secondary, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 日期标签：M月d日 */
private fun formatDayLabel(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
}

/** 月份标签：yyyy年M月 */
private fun formatMonthLabel(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "${cal.get(java.util.Calendar.YEAR)}年${cal.get(java.util.Calendar.MONTH) + 1}月"
}

/**
 * 统计页「游玩时长列表」：按时间倒序（最新在最上面），每行显示
 * 日期/小时/月份 + 时长条形 + 时长文字。用于日（逐小时）/ 周 / 月（逐日）/ 年（逐月）视图。
 */
@Composable
private fun DailyPlaytimeList(
    title: String,
    rows: List<PlaytimeRow>,
    apps: List<AppModel>,
    mode: StatMetricMode,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = Dimens.xs)
        )
        if (rows.isEmpty()) {
            EmptyHint(text = "暂无游玩记录")
        } else {
            val maxValue = rows.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                rows.forEachIndexed { idx, row ->
                    val ratio = (row.value.toFloat() / maxValue).coerceIn(0f, 1f)
                    AnimatedVisibility(
                        visible = true,
                        enter = if (reduceMotion) EnterTransition.None else
                            fadeIn(animationSpec = tween(200, delayMillis = MotionSpec.staggerDelay(idx, maxMs = 60).toInt()))
                                + slideInHorizontally(animationSpec = tween(200, delayMillis = MotionSpec.staggerDelay(idx, maxMs = 60).toInt()), initialOffsetX = { -12 }),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (row.value == maxValue && row.value > 0L) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle),
                                RoundedCornerShape(Dimens.sm)
                            )
                            .padding(horizontal = Dimens.sm, vertical = Dimens.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                    ) {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.widthIn(min = 64.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint),
                                    RoundedCornerShape(Dimens.RadiusXs)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .height(6.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(Dimens.RadiusXs)
                                    )
                            )
                        }
                        // 该时段 Top3 应用图标（小图，与进度条同行不换行）
                        row.topPkgs.take(3).forEach { pkg ->
                            val icon = apps.firstOrNull { it.packageName == pkg }?.icon
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.widthIn(min = 56.dp).wrapContentWidth(Alignment.End)
                        ) {
                            Text(
                                text = if (row.value <= 0L) "—" else (if (mode == StatMetricMode.Duration) formatHoursMs(row.value) else "${row.value} 次"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong),
                            )
                        if (row.value > 0L) Text(
                            text = "%.0f%%".format((row.value.toFloat() / maxValue) * 100),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        }
                    }
                    }
                }
            }
        }
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
    // 周/月/年均有独立周期选择器（自绘 ReveriePeriodPickerDialog），点击触发选择器。
    Box(modifier = modifier.wrapContentWidth(unbounded = true)) {
        wrapFocusBorder(focused = focused) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick
                    )
                    .defaultMinSize(minWidth = 120.dp)
                    .padding(horizontal = Dimens.sm, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.CalendarMonth, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.IconSm)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
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
 * 自定义周期选择器（周/月/年）：替代原生 DatePicker。
 *
 * 原生 DatePicker 是"日"粒度，且手柄事件不走原生 KeyEvent 需繁琐桥接；
 * 而统计页周/月/年视图要的是"选一个周期"而非"选一天"，原生组件粒度错配
 * （truncToPeriod 把所选日一律落到月/年首，导致同月内移动无效，且图表/列表走尾部窗口显示错乱）。
 * 本自绘选择器以"周期单位"步进（周±7天/月±1月/年±1年），LB/RB 大跳
 * （周±4周/月±1年/年±5年），A 确认、B 取消、X 回到本期，
 * 直接接入项目手柄桥接（StatsDatePickerBridge）与焦点/视觉规范，比原生更贴合且可靠。
 */
@Composable
fun ReveriePeriodPickerDialog(
    period: StatsPeriod,
    initialAnchorMs: Long,
    bridge: StatsDatePickerBridge,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    /** 挖孔屏适配开关：透传 DialogScaffold 底部提示栏安全边距。 */
    cutoutAdapt: Boolean = false
) {
    val nowPeriodStart = remember(period) { alignToPeriodStart(System.currentTimeMillis(), period) }
    var cursorMs by remember(initialAnchorMs) { mutableStateOf(alignToPeriodStart(initialAnchorMs, period)) }

    fun step(amount: Int) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = cursorMs }
        when (period) {
            StatsPeriod.WEEKLY -> cal.add(java.util.Calendar.DAY_OF_MONTH, amount * 7)
            StatsPeriod.MONTHLY -> cal.add(java.util.Calendar.MONTH, amount)
            StatsPeriod.YEARLY -> cal.add(java.util.Calendar.YEAR, amount)
            else -> cal.add(java.util.Calendar.DAY_OF_MONTH, amount)
        }
        var next = cal.timeInMillis
        if (next > nowPeriodStart) next = nowPeriodStart
        cursorMs = next
    }

    // 手柄桥接：方向键单步、LB/RB 大跳、A 确认、B 取消、X 回到本期。
    LaunchedEffect(bridge, period) {
        bridge.onMove = { dir ->
            val s = when (dir) {
                Direction.UP, Direction.LEFT -> -1
                else -> 1
            }
            step(s)
        }
        bridge.onPage = { dir -> step(dir * pageStep(period)) }
        bridge.onConfirm = { onConfirm(cursorMs) }
        bridge.onCancel = { onDismiss() }
        bridge.jumpToAnchor = { cursorMs = nowPeriodStart }
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
        cutoutAdapt = cutoutAdapt,
        unifiedContainer = false,
        bottomHintContent = {
            Hint(KeyToken.A, "确认")
            Hint(KeyToken.X, "回到本期")
            Hint(KeyToken.B, "关闭")
        }
    ) {
        Surface(
            shape = RoundedCornerShape(Dimens.RadiusLg),
            color = SurfaceTokens.cardSurfaceStrong(),
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = Dimens.DialogMaxWidth).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Dimens.md),
                verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (period) {
                        StatsPeriod.WEEKLY -> "选择周"
                        StatsPeriod.MONTHLY -> "选择月"
                        else -> "选择年"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = periodLabel(cursorMs, period),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (period == StatsPeriod.WEEKLY) {
                    Text(
                        text = weekRangeLabel(cursorMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ReverieTonalButton(onClick = { step(-pageStep(period)) }, text = "«", modifier = Modifier.weight(1f))
                    ReverieTonalButton(onClick = { step(-1) }, text = "‹", modifier = Modifier.weight(1f))
                    ReverieTonalButton(onClick = { step(1) }, text = "›", modifier = Modifier.weight(1f))
                    ReverieTonalButton(onClick = { step(pageStep(period)) }, text = "»", modifier = Modifier.weight(1f))
                }
                ReverieOutlinedButton(
                    onClick = { cursorMs = nowPeriodStart },
                    text = when (period) {
                        StatsPeriod.WEEKLY -> "回到本周"
                        StatsPeriod.MONTHLY -> "回到本月"
                        else -> "回到本年"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ReverieTonalButton(onClick = onDismiss, text = "取消", modifier = Modifier.weight(1f))
                    ReverieFilledButton(onClick = { onConfirm(cursorMs) }, text = "确认", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun pageStep(period: StatsPeriod): Int = when (period) {
    StatsPeriod.WEEKLY -> 4
    StatsPeriod.MONTHLY -> 12
    StatsPeriod.YEARLY -> 5
    else -> 1
}

private fun alignToPeriodStart(ms: Long, period: StatsPeriod): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return when (period) {
        StatsPeriod.WEEKLY -> weekStartCalendar(ms).timeInMillis
        StatsPeriod.MONTHLY -> { cal.set(java.util.Calendar.DAY_OF_MONTH, 1); zeroOfDay(cal) }
        StatsPeriod.YEARLY -> { cal.set(java.util.Calendar.MONTH, 0); cal.set(java.util.Calendar.DAY_OF_MONTH, 1); zeroOfDay(cal) }
        else -> zeroOfDay(cal)
    }
}

private fun periodLabel(ms: Long, period: StatsPeriod): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val y = cal.get(java.util.Calendar.YEAR)
    return when (period) {
        StatsPeriod.WEEKLY -> {
            val week = cal.get(java.util.Calendar.WEEK_OF_YEAR)
            "$y 年 第${week}周"
        }
        StatsPeriod.MONTHLY -> "$y 年 ${cal.get(java.util.Calendar.MONTH) + 1} 月"
        else -> "$y 年"
    }
}

private fun weekRangeLabel(ms: Long): String {
    val s = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val e = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    e.add(java.util.Calendar.DAY_OF_MONTH, 6)
    val f = { c: java.util.Calendar -> "%02d/%02d".format(c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH)) }
    return "${f(s)} – ${f(e)}"
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
    onDismiss: () -> Unit,
    /** 挖孔屏适配开关：透传 DialogScaffold 底部提示栏安全边距。 */
    cutoutAdapt: Boolean = false
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
        cutoutAdapt = cutoutAdapt,
        unifiedContainer = false,
        bottomHintContent = {
            Hint(KeyToken.A, "确认")
            Hint(KeyToken.B, "关闭")
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 与项目其它 Dialog 统一配色：容器 cardSurfaceStrong / 选中·今天 primary /
            // 普通文字 onSurface / 弱化·禁用 mutedOnSurface / 分隔线 restingBorderColorSoft，
            // 彻底摆脱 Material3 默认色（默认 divider、文本框等与其它 Dialog 不一致）。
            val pickerColors = DatePickerDefaults.colors(
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
                dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                dayContentColor = MaterialTheme.colorScheme.onSurface,
                selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                disabledDayContentColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint),
                disabledSelectedDayContentColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint),
                disabledSelectedDayContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledYearContentColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint),
            )

            val dialogMaxW = minOf(Dimens.DialogMaxWidth, maxWidth)
            // 留 6% 安全边距，避免缩放后紧贴屏幕边缘
            val dialogMaxH = maxHeight * 0.94f
            // 系统 DatePicker 的自然尺寸（Material3 固定 Dp 布局）明显大于本应用整体视觉比例，
            // 故无论内容是否溢出，统一按 baseScale 等比缩小；若缩小后仍超出可用区域，
            // 再按宽/高比例进一步缩小，保证 DatePicker + 锚点芯片 + 确认/取消 完整可见、不被裁切。
            // 注：查询系统 DPI 无效——picker 与我们的 UI 共用同一 density，偏大来自组件固有 Dp 尺寸，非密度差异。
            var contentSize by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current
            val baseScale = 0.9f
            val scale = remember(contentSize, dialogMaxW, dialogMaxH) {
                if (contentSize.width == 0 || contentSize.height == 0) baseScale
                else {
                    val w = with(density) { contentSize.width.toDp() }
                    val h = with(density) { contentSize.height.toDp() }
                    minOf(
                        if (w > dialogMaxW) dialogMaxW / w else 1f,
                        if (h > dialogMaxH) dialogMaxH / h else 1f,
                        baseScale
                    )
                }
            }
            Surface(
                    shape = RoundedCornerShape(Dimens.RadiusLg),
                    color = SurfaceTokens.cardSurfaceStrong(),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(max = dialogMaxW)
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { }
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = Dimens.md, vertical = Dimens.sm)
                            .onSizeChanged { contentSize = it },
                        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
                    ) {
                        if (!isRange) {
                            singleState?.let { st ->
                                DatePicker(
                                    state = st,
                                    showModeToggle = true,
                                    colors = pickerColors,
                                title = {
                                    Text("选择日期", style = MaterialTheme.typography.titleMedium,
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
                                    colors = pickerColors,
                                    title = {
                                        Text(
                                            if (period == StatsPeriod.MONTHLY) "选择月份" else "选择年份",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(start = Dimens.sm, top = Dimens.sm))
                                    }
                            )
                        }
                    }

                    // 快捷锚点：一键跳到今天 / 本周 / 本月 / 本年（写入选中日并同步锚点）。
                    val nowMs = remember { System.currentTimeMillis() }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.xs),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                    ) {
                        ReverieTonalButton(onClick = { val a = truncToPeriod(nowMs, StatsPeriod.DAILY); selectedMs = a; if (isRange) rangeState?.setSelection(a, a) else singleState?.selectedDateMillis = a }, text = "今天")
                        ReverieTonalButton(onClick = { val a = truncToPeriod(nowMs, StatsPeriod.WEEKLY); selectedMs = a; if (isRange) rangeState?.setSelection(a, a) else singleState?.selectedDateMillis = a }, text = "本周")
                        ReverieTonalButton(onClick = { val a = truncToPeriod(nowMs, StatsPeriod.MONTHLY); selectedMs = a; if (isRange) rangeState?.setSelection(a, a) else singleState?.selectedDateMillis = a }, text = "本月")
                        ReverieTonalButton(onClick = { val a = truncToPeriod(nowMs, StatsPeriod.YEARLY); selectedMs = a; if (isRange) rangeState?.setSelection(a, a) else singleState?.selectedDateMillis = a }, text = "本年")
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

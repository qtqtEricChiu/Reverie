package cn.mocabolka.run.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.compat.NotificationBadgeService
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.launcher.NotificationRecord
import cn.mocabolka.run.launcher.SenderAgg
import cn.mocabolka.run.launcher.UsageStatsRepository
import cn.mocabolka.run.ui.NotificationHistoryActivity
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.SurfaceCard
import cn.mocabolka.run.ui.theme.SurfaceTokens
import cn.mocabolka.run.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 统计页「通知历史」子 Tab（2026-07-28 排版对齐版，与使用时长/启动次数完全同构）：
 * - 横屏/大屏 = 左栏（周期栏 + 视图滑块 + 摘要行 + 图表/列表 + 高频联系人卡）/ 右栏（应用排行）；窄屏 = 单栏顺序。
 * - 图表沿用其他两页排版：标题走 ChartCard 内置样式（今日各小时通知数量 / 本周每日通知数量……），
 *   日周期 X 轴 0~24 逐小时，且叠加「较上期」对比（prevValues），与其他两页一致。
 * - 高频联系人卡（社交/IM 应用按通知标题聚合）嵌在左栏图表下方，点击钻取该联系人明细。
 * - 右栏应用排行固定按应用聚合；点击（或手柄 A 键）钻取应用通知明细（页内子页 + 动画机过渡，B 返回）。
 * - 数据：热层同步零 IO；跨冷层走 Dispatchers.IO（produceState 异步）。
 */
@Composable
internal fun NotificationTab(
    viewModel: HomeViewModel,
    apps: List<AppModel>,
    activePeriod: StatsPeriod,
    anchorMs: Long,
    onDatePickerOpen: () -> Unit,
    viewMode: StatViewMode,
    onViewModeChange: (StatViewMode) -> Unit,
    focusZone: StatsFocusZone,
    overviewFocusIndex: Int,
    focusIndex: Int,
    isWideScreen: Boolean,
    gamepadConnected: Boolean,
    reduceMotion: Boolean,
    control: StatsControlBridge?,
    onFocus: (String?) -> Unit,
    onEntryCountChange: (Int) -> Unit,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onEntryClick: (String) -> Unit,
    leftScrollState: ScrollState,
    listState: LazyListState,
    reportsVersion: Int,
    modifier: Modifier = Modifier
) {
    val (notifStart, notifEnd) = remember(activePeriod, anchorMs) { periodRangeKeys(activePeriod, anchorMs) }
    // 通知监听（通知使用权）权限闸门：未开启时通知历史 Tab 给出「去开启」引导（与使用情况访问权限处理一致）
    val notificationsVersion by viewModel.notificationsVersion.collectAsState()
    val ctx = LocalContext.current
    var notifEnabled by remember { mutableStateOf(NotificationBadgeService.isEnabled(ctx)) }
    LaunchedEffect(notificationsVersion, reportsVersion) {
        notifEnabled = NotificationBadgeService.isEnabled(ctx)
    }
    // 列表视图行（含各时段 Top3 应用，与使用时长/启动次数列表同构）
    val notifListRows by produceState(emptyList<NotifListRow>(), notifStart, notifEnd, activePeriod, anchorMs, reportsVersion, notificationsVersion) {
        value = withContext(Dispatchers.IO) { buildNotifListRows(viewModel, activePeriod, anchorMs) }
    }
    // 较上期对比基准（上一周期通知总数）
    val prevTotalNotif by produceState(0L, notifStart, notifEnd, reportsVersion, notificationsVersion) {
        value = withContext(Dispatchers.IO) {
            val (ps, pe) = periodRangeKeys(activePeriod, shiftAnchorByPeriod(anchorMs, activePeriod, -1))
            viewModel.notificationCountsForRange(ps, pe).values.sum().toLong()
        }
    }
    // 按应用通知数（右栏排行，BY_APP 模式）
    val notifCounts by produceState(emptyMap<String, Int>(), notifStart, notifEnd, reportsVersion, notificationsVersion) {
        value = withContext(Dispatchers.IO) { viewModel.notificationCountsForRange(notifStart, notifEnd) }
    }
    // 高频联系人聚合（左栏嵌入卡：社交/IM 应用按通知标题 group by + count）
    val notifSenders by produceState(emptyList<SenderAgg>(), notifStart, notifEnd, reportsVersion, notificationsVersion) {
        value = withContext(Dispatchers.IO) { viewModel.topSendersForRange(notifStart, notifEnd, 10) }
    }
    // 图表序列（与其他两页同构）：日=逐小时 0~24 + 昨日对比；周/月=逐日零补齐 + 上期对比；年=逐月聚合
    val chartData by produceState(NotifChartData(longArrayOf(), null), activePeriod, anchorMs, reportsVersion, notificationsVersion) {
        value = withContext(Dispatchers.IO) { buildNotifChartData(viewModel, activePeriod, anchorMs) }
    }

    val totalNotif = notifCounts.values.sum()
    // 右栏排行条目：固定按应用聚合（发件人视图已改为左栏高频联系人卡）。
    // 系统应用与使用时长/启动次数一致——未开启「显示系统应用」时全部折叠为单个「系统应用」聚合项置底，
    // 既与应用时长/启动次数排行同构，也避免把无应用信息的系统包名暴露为可点击钻取的独立行（点击会崩）。
    val showSystemApps = viewModel.settings.showSystemApps
    val systemPackages by viewModel.systemPackages.collectAsState()
    val rankEntries: List<UsageStatsRepository.UsageEntry> = remember(notifCounts, showSystemApps, systemPackages) {
        val sysPkg = cn.mocabolka.run.viewmodel.HomeViewModel.SYSTEM_AGGREGATE_PKG
        if (showSystemApps) {
            notifCounts.entries.sortedByDescending { it.value }
                .map { UsageStatsRepository.UsageEntry(it.key, it.value.toLong(), it.value) }
        } else {
            val sysSet = systemPackages.orEmpty()
            val sysTotal = notifCounts.entries.filter { it.key in sysSet }.sumOf { it.value }
            val users = notifCounts.entries.filter { it.key !in sysSet }
                .sortedByDescending { it.value }
                .map { UsageStatsRepository.UsageEntry(it.key, it.value.toLong(), it.value) }
            val out = users.toMutableList()
            if (sysTotal > 0) out.add(UsageStatsRepository.UsageEntry(sysPkg, sysTotal.toLong(), sysTotal))
            out
        }
    }
    // 摘要一行版（与使用时长/启动次数页同款）：总计 / 应用量 / 最多通知
    val topNotifEntry = remember(notifCounts) { notifCounts.entries.maxByOrNull { it.value } }
    val summaryItems by produceState(emptyList<MetricInlineItem>(), totalNotif, notifCounts, topNotifEntry, apps) {
        value = listOf(
            MetricInlineItem("总计", "$totalNotif 条"),
            MetricInlineItem("应用量", "${notifCounts.size} 个"),
            MetricInlineItem(
                "最多通知",
                topNotifEntry?.let { e -> apps.firstOrNull { it.packageName == e.key }?.label ?: e.key } ?: "—",
                topNotifEntry?.let { "${it.value} 条" }
            )
        )
    }
    LaunchedEffect(rankEntries.size) { onEntryCountChange(rankEntries.size) }
    // 焦点跟随：上报当前焦点键（供 A 键钻取 / HomeScreen 焦点模型）
    LaunchedEffect(focusIndex, rankEntries, control) {
        val key = rankEntries.getOrNull(focusIndex)?.packageName
        onFocus(key)
        control?.notifFocusedKey = key
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!notifEnabled) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(Dimens.md),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.widthIn(max = 340.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(Dimens.lg),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "未开启通知使用权",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "开启后，统计页将展示通知历史与趋势",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ReverieFilledButton(
                            onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            text = "去开启"
                        )
                    }
                }
            }
        } else {
            StatsDualPane(
            isWideScreen = isWideScreen,
            leftScroll = leftScrollState,
            leftPanelWidth = Dimens.RightPanelWidth,
            left = {
                StatsPeriodBar(
                    period = activePeriod,
                    onPeriodChange = onSelectPeriod,
                    anchorMs = anchorMs,
                    onDatePickerOpen = onDatePickerOpen,
                    focusZone = focusZone,
                    overviewFocusIndex = overviewFocusIndex
                )
                // 摘要一行版（位置照抄使用时长/启动次数页：周期栏之下、图表/列表滑块之上）
                MetricSummaryInline(items = summaryItems)
                StatsViewToggle(
                    viewMode = viewMode,
                    onViewModeChange = onViewModeChange,
                    focusZone = focusZone,
                    overviewFocusIndex = overviewFocusIndex
                )
                // 图表标题与其他两页同源：日=「今日/当日各小时通知数量」（X 轴 0~24），周/月逐日、年逐月
                val isAnchorToday = dateKeyOf(anchorMs) == dateKeyOf(System.currentTimeMillis())
                val chartTitle = when (activePeriod) {
                    StatsPeriod.DAILY -> if (isAnchorToday) "今日各小时通知数量" else "当日各小时通知数量"
                    StatsPeriod.WEEKLY -> "本周每日通知数量"
                    StatsPeriod.MONTHLY -> "本月每日通知数量"
                    StatsPeriod.YEARLY -> "本年每月通知数量"
                }
                if (viewMode == StatViewMode.Chart) {
                    // 排版照抄其他两页：标题交给 ChartCard 内置样式，叠加较上期对比（prevValues）
                    ChartCard(
                        title = chartTitle,
                        values = chartData.values,
                        period = activePeriod,
                        metric = StatMetricMode.Count,
                        prevValues = chartData.prev,
                        showValueLabels = false
                    )
                } else {
                    // 列表视图：样式与使用时长/启动次数列表同构（时段 Top3 + 进度条 + 展开全部）
                    var notifExpanded by remember(activePeriod, anchorMs) { mutableStateOf(false) }
                    val notifShowExpand = notifListRows.size > 7
                    val notifDisplayRows = if (notifShowExpand && !notifExpanded) {
                        if (activePeriod == StatsPeriod.DAILY) {
                            val ch = currentHour()
                            val cur = notifListRows.firstOrNull { it.hour == ch }
                            val top3 = notifListRows.filter { it.hour != ch }.sortedByDescending { it.value }.take(3)
                            (listOfNotNull(cur) + top3).sortedBy { it.hour }
                        } else notifListRows.take(4)
                    } else notifListRows
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = chartTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = Dimens.xs)
                        )
                        DailyNotifList(
                            rows = notifDisplayRows,
                            apps = apps,
                            reduceMotion = reduceMotion
                        )
                        if (notifShowExpand) {
                            Spacer(Modifier.height(Dimens.xs))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                ReverieTonalButton(
                                    onClick = { notifExpanded = !notifExpanded },
                                    text = if (notifExpanded) "收起" else "展开全部",
                                    focused = gamepadConnected && control?.focusedExpand ?: false
                                )
                                // 通知历史：注册展开回调（A 键 EXPAND 槽切换）
                                control?.expandToggle = { notifExpanded = !notifExpanded }
                            }
                        }
                    }
                }
                // 高频联系人卡：左栏图表下方（社交/IM 应用按通知标题聚合，点击钻取该联系人明细）
                if (notifSenders.isNotEmpty()) {
                    FrequentSendersCard(
                        senders = notifSenders,
                        apps = apps,
                        control = control,
                        gamepadConnected = gamepadConnected,
                        onSenderClick = { s ->
                            ctx.startActivity(
                                NotificationHistoryActivity.createIntent(
                                    ctx, s.packageName, s.sender, notifStart, notifEnd,
                                    NotificationHistoryActivity.MODE_SENDER
                                )
                            )
                        }
                    )
                }
            },
            right = {
                // 标题排版照抄其他两页：宽屏 titleLarge + top/bottom 内边距；窄屏 titleMedium
                if (isWideScreen) {
                    Text(
                        text = "应用排行",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = Dimens.md, bottom = Dimens.xs)
                    )
                } else {
                    Text(
                        text = "应用排行",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (rankEntries.isEmpty()) {
                    Text(
                        "本期暂无通知",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 窄屏下 right() 被包进 StatsDualPane 的 verticalScroll(Column)，
                    // fillMaxSize 会解析成无限高度导致 LazyColumn 抛 IllegalStateException。
                    // 用 BoxWithConstraints 读取约束：宽屏取到有限高度；窄屏退化为固定上限。
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val rankMaxHeight = if (maxHeight != Dp.Infinity) maxHeight else 600.dp
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = rankMaxHeight),
                            contentPadding = PaddingValues(top = Dimens.xxs, bottom = Dimens.HintBarHeight),
                            verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                        ) {
                            items(rankEntries, key = { it.packageName }, contentType = { "notifRankRow" }) { entry ->
                                val isAggregate = entry.packageName == cn.mocabolka.run.viewmodel.HomeViewModel.SYSTEM_AGGREGATE_PKG
                                val appForRow = if (isAggregate) null else apps.firstOrNull { it.packageName == entry.packageName }
                                val label = if (isAggregate) "系统应用" else (appForRow?.label ?: entry.packageName)
                                val icon = appForRow?.icon
                                AppRankRow(
                                    rank = rankEntries.indexOf(entry) + 1,
                                    entry = entry,
                                    totalMs = totalNotif.toLong(),
                                    useCount = true,
                                    label = label,
                                    icon = icon,
                                    entranceIndex = rankEntries.indexOf(entry),
                                    reduceMotion = reduceMotion,
                                    focused = gamepadConnected && focusZone == StatsFocusZone.RANK
                                            && rankEntries.indexOf(entry) == focusIndex,
                                    onClick = {
                                        // 系统应用聚合项无对应包名信息，不可钻取，点击置空
                                        if (!isAggregate) {
                                            ctx.startActivity(
                                                NotificationHistoryActivity.createIntent(
                                                    ctx, entry.packageName, null, notifStart, notifEnd,
                                                    NotificationHistoryActivity.MODE_APP
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        )
        }
    }
}



/** 高频联系人卡：社交/IM 应用按通知标题聚合的联系人排行（嵌于左栏图表下方，点击钻取该联系人明细）。 */
@Composable
private fun FrequentSendersCard(
    senders: List<SenderAgg>,
    apps: List<AppModel>,
    control: StatsControlBridge?,
    gamepadConnected: Boolean,
    onSenderClick: (SenderAgg) -> Unit
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Text(
                text = "高频联系人",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            // 联系人过多时折叠，超过 4 行给出「展开全部」按钮，避免把底部应用排行挤出屏幕
            // （与「本月每日通知数量」列表的展开全部逻辑同构）。
            var expanded by remember(senders) { mutableStateOf(false) }
            val showExpand = senders.size > 4
            val displaySenders = if (showExpand && !expanded) senders.take(4) else senders
            displaySenders.forEachIndexed { index, s ->
                val app = apps.firstOrNull { it.packageName == s.packageName }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.xs))
                        .clickable { onSenderClick(s) }
                        .padding(vertical = Dimens.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(20.dp)
                    )
                    app?.icon?.let { icon ->
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.xl).clip(RoundedCornerShape(Dimens.xs))
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.sender,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app?.label ?: s.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${s.count} 条",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (showExpand) {
                Spacer(Modifier.height(Dimens.xs))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    ReverieTonalButton(
                        onClick = { expanded = !expanded },
                        text = if (expanded) "收起" else "展开全部 (${senders.size})",
                        focused = gamepadConnected && control?.focusedExpand ?: false
                    )
                    // 高频联系人卡：注册展开回调（A 键 EXPAND 槽切换）
                    control?.expandToggle = { expanded = !expanded }
                }
            }
        }
    }
}

/** 通知图表序列容器：values=本期序列；prev=较上期对比序列（年视图为 null，与其他两页一致）。 */
internal class NotifChartData(val values: LongArray, val prev: LongArray?)

/** 构建通知图表序列（IO 线程调用）：日=逐小时 0~23 + 昨日对比；周/月=逐日零补齐 + 上期对比；年=逐月聚合。 */
private suspend fun buildNotifChartData(viewModel: HomeViewModel, period: StatsPeriod, anchorMs: Long): NotifChartData {
    return when (period) {
        StatsPeriod.DAILY -> {
            val dayKey = dateKeyOf(anchorMs)
            val cur = hourlyNotifCounts(viewModel.notificationListForRange(dayKey, dayKey))
            val prevKey = dateKeyOf(shiftAnchorByPeriod(anchorMs, StatsPeriod.DAILY, -1))
            val prev = hourlyNotifCounts(viewModel.notificationListForRange(prevKey, prevKey))
            NotifChartData(cur, if (prev.any { it > 0L }) prev else null)
        }
        StatsPeriod.WEEKLY, StatsPeriod.MONTHLY -> {
            val (s, e) = periodRangeKeys(period, anchorMs)
            val cur = zeroFilledDailyCounts(viewModel.notificationDailyCounts(s, e), s, e)
            val (ps, pe) = periodRangeKeys(period, shiftAnchorByPeriod(anchorMs, period, -1))
            val prev = zeroFilledDailyCounts(viewModel.notificationDailyCounts(ps, pe), ps, pe)
            NotifChartData(cur, if (prev.any { it > 0L }) prev else null)
        }
        StatsPeriod.YEARLY -> {
            val (s, e) = periodRangeKeys(StatsPeriod.YEARLY, anchorMs)
            val byMonth = LongArray(12)
            viewModel.notificationDailyCounts(s, e).forEach { (dk, c) ->
                val m = dk.split("-").getOrNull(1)?.toIntOrNull()
                if (m != null && m in 1..12) byMonth[m - 1] += c.toLong()
            }
            NotifChartData(byMonth, null)
        }
    }
}

/** 一日通知记录 → 逐小时计数（0~23）。 */
private fun hourlyNotifCounts(records: List<NotificationRecord>): LongArray {
    val arr = LongArray(24)
    val cal = Calendar.getInstance()
    records.forEach { r ->
        cal.timeInMillis = r.timestamp
        arr[cal.get(Calendar.HOUR_OF_DAY)]++
    }
    return arr
}

/** 按日计数零补齐为连续序列（startKey..endKey 逐日；缺失天补 0，防越界上限 62 天）。 */
private fun zeroFilledDailyCounts(counts: List<Pair<String, Int>>, startKey: String, endKey: String): LongArray {
    val map = counts.toMap()
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val start = runCatching { fmt.parse(startKey) }.getOrNull() ?: return LongArray(0)
    val end = runCatching { fmt.parse(endKey) }.getOrNull() ?: return LongArray(0)
    val cal = Calendar.getInstance().apply { time = start }
    val out = ArrayList<Long>()
    while (!cal.time.after(end) && out.size < 62) {
        out.add((map[fmt.format(cal.time)] ?: 0).toLong())
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return out.toLongArray()
}

/** 日期键 "yyyy-MM-dd" → "M月D日"。 */
private fun formatNotifDay(dk: String): String {
    val parts = dk.split("-")
    if (parts.size == 3) {
        val m = parts[1].toIntOrNull() ?: return dk
        val d = parts[2].toIntOrNull() ?: return dk
        return "${m}月${d}日"
    }
    return dk
}

/** 列表行：时段标签 + 通知数 + 该时段 Top3 应用包名（与使用时长/启动次数列表同构）。 */
private data class NotifListRow(
    val label: String,
    val value: Long,
    val topPkgs: List<String> = emptyList(),
    val hour: Int = -1
)

/** 当前小时（0~23），与启动时长/启动次数列表折叠逻辑一致。 */
private fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

/** 构建列表行（IO 线程调用）：聚合各时段计数 + Top3 应用包名。 */
private suspend fun buildNotifListRows(viewModel: HomeViewModel, period: StatsPeriod, anchorMs: Long): List<NotifListRow> {
    val (s, e) = periodRangeKeys(period, anchorMs)
    val records = viewModel.notificationListForRange(s, e)
    val cal = Calendar.getInstance()
    return when (period) {
        StatsPeriod.DAILY -> {
            val perHour = Array(24) { mutableMapOf<String, Int>() }
            records.forEach { r ->
                cal.timeInMillis = r.timestamp
                val h = cal.get(Calendar.HOUR_OF_DAY)
                perHour[h][r.packageName] = perHour[h].getOrDefault(r.packageName, 0) + 1
            }
            (0..23).mapNotNull { h ->
                val cnt = perHour[h].values.sum().toLong()
                if (cnt <= 0L) return@mapNotNull null
                val top = perHour[h].entries.sortedByDescending { it.value }.take(3).map { it.key }
                NotifListRow("%02d:00".format(h), cnt, top, h)
            }
        }
        StatsPeriod.WEEKLY, StatsPeriod.MONTHLY -> {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val start = fmt.parse(s) ?: return emptyList()
            val end = fmt.parse(e) ?: return emptyList()
            val perDay = LinkedHashMap<String, MutableMap<String, Int>>()
            val c = Calendar.getInstance().apply { time = start }
            while (!c.time.after(end)) {
                perDay[fmt.format(c.time)] = mutableMapOf()
                c.add(Calendar.DAY_OF_MONTH, 1)
            }
            records.forEach { r ->
                cal.timeInMillis = r.timestamp
                val dk = fmt.format(cal.time)
                perDay[dk]?.let { m -> m[r.packageName] = m.getOrDefault(r.packageName, 0) + 1 }
            }
            perDay.mapNotNull { (dk, m) ->
                val cnt = m.values.sum().toLong()
                if (cnt <= 0L) return@mapNotNull null
                val top = m.entries.sortedByDescending { it.value }.take(3).map { it.key }
                NotifListRow(formatNotifDay(dk), cnt, top)
            }
        }
        StatsPeriod.YEARLY -> {
            val perMonth = Array(12) { mutableMapOf<String, Int>() }
            records.forEach { r ->
                cal.timeInMillis = r.timestamp
                val m = cal.get(Calendar.MONTH)
                perMonth[m][r.packageName] = perMonth[m].getOrDefault(r.packageName, 0) + 1
            }
            (0..11).mapNotNull { mi ->
                val cnt = perMonth[mi].values.sum().toLong()
                if (cnt <= 0L) return@mapNotNull null
                val top = perMonth[mi].entries.sortedByDescending { it.value }.take(3).map { it.key }
                NotifListRow("${mi + 1}月", cnt, top)
            }
        }
    }
}

/**
 * 通知列表视图（与使用时长/启动次数列表同构）：进度条 + 该时段 Top3 应用图标 + 计数 + 占比。
 * 标题由调用方统一渲染（图表标题同源），此处 title 为空时不重复展示。
 */
@Composable
private fun DailyNotifList(
    rows: List<NotifListRow>,
    apps: List<AppModel>,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    title: String = ""
) {
    Column(modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = Dimens.xs)
            )
        }
        if (rows.isEmpty()) {
            Text(
                text = "本期暂无通知",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.RadiusXs))
                                )
                            }
                            row.topPkgs.take(3).forEach { pkg ->
                                val icon = apps.firstOrNull { it.packageName == pkg }?.icon
                                if (icon != null) {
                                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.widthIn(min = 56.dp).wrapContentWidth(Alignment.End)
                            ) {
                                Text(
                                    text = if (row.value <= 0L) "—" else "${row.value} 条",
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

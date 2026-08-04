package cn.mocabolka.run.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.SurfaceCard
import cn.mocabolka.run.ui.theme.SurfaceTokens
import cn.mocabolka.run.ui.theme.focusBorder
import cn.mocabolka.run.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统计页「速览」子 Tab（2026-07-28 结构归并版）：
 * - 左栏（速览主体）：使用时长完整指标卡（总计 / 应用量 / 最长使用）+ 洞察卡（峰值时段 / 较昨日），
 *   即从指标页整体搬入的「完整体」，均按「今日」维度计算。
 * - 右栏：今日三张 Top3 榜（使用时长 / 启动次数 / 通知次数），每张卡可点击 / A 键跳转对应子 Tab。
 * - 宽屏（>=720dp）双栏；窄屏纵向堆叠。
 * - 手柄焦点：focusIndex 0/1/2 依次对应三张 Top 卡，A 键经 StatsControlBridge.overviewActivate 跳转。
 */
@Composable
fun StatsOverviewTab(
    viewModel: HomeViewModel,
    apps: List<AppModel>,
    isWideScreen: Boolean = false,
    reduceMotion: Boolean = false,
    onOpenSubTab: (StatsSubTab) -> Unit = {},
    focusIndex: Int = -1,
    gamepadConnected: Boolean = false,
    scrollState: ScrollState? = null,
    modifier: Modifier = Modifier
) {
    val reportsVersion by viewModel.reportsVersion.collectAsState()
    val notifVersion by viewModel.notificationsVersion.collectAsState()

    val todayKey = remember(reportsVersion) { viewModel.notificationTodayKey() }
    // 今日使用报表：沿用统计页排行的收纳规则——关闭「显示系统应用」时 collapseSystemApps 会把所有
    // 系统应用聚合成一个置底的「系统应用」项（包名哨兵）。此处【保留】该聚合项，使「最长使用」与
    // Top3 榜在系统应用确实领先时显示为「系统应用」（而非像旧逻辑整块丢弃）。系统包名集合与开关
    // 用于通知榜的同类聚合（通知走独立数据源，需在此手动收折，且同样跟随「显示系统应用」开关）。
    val sysPkg = HomeViewModel.SYSTEM_AGGREGATE_PKG
    val sysPkgs by viewModel.systemPackages.collectAsState()
    val showSystemApps by viewModel.settings.showSystemAppsFlow.collectAsState()
    val todayEntries = remember(reportsVersion, apps) {
        viewModel.collapseSystemApps(viewModel.dailyReportFor(todayKey))
    }
    // ── 指标卡数据（今日 · 使用时长维度） ──
    val totalMs = remember(todayEntries) { todayEntries.sumOf { it.ms } }
    // 「应用量」与统计页一致：把「系统应用」聚合项视为整体、不计入独立应用数
    val appCount = remember(todayEntries) {
        todayEntries.filter { it.packageName != sysPkg }.count { it.ms > 0L }
    }
    val longestEntry = remember(todayEntries) { todayEntries.maxByOrNull { it.ms } }
    val longestLabel = if (longestEntry?.packageName == sysPkg) "系统应用"
    else longestEntry?.packageName?.let { pkg ->
        apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
    } ?: "—"
    // 洞察：峰值时段（今日逐小时）+ 较昨日涨跌（合并单行，无副标题）
    val hourly = remember(reportsVersion) { viewModel.hourlyToday() }
    val yesterdayTotal = remember(reportsVersion) {
        val yKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(Date(System.currentTimeMillis() - 86_400_000L))
        viewModel.collapseSystemApps(viewModel.dailyReportFor(yKey)).sumOf { it.ms }
    }
    val insights = remember(totalMs, yesterdayTotal, hourly) {
        buildList {
            val hi = hourly.withIndex().maxByOrNull { it.value }?.index ?: -1
            if (hi >= 0 && hourly[hi] > 0L) add(StatInsight("峰值时段", "%02d:00".format(hi)))
            if (totalMs > 0L && yesterdayTotal > 0L) {
                val pct = (totalMs - yesterdayTotal) * 100f / yesterdayTotal
                val sign = if (pct >= 0f) "+" else ""
                add(StatInsight("较昨日", "$sign${"%.1f".format(pct)}%"))
            } else if (totalMs > 0L && yesterdayTotal == 0L) {
                add(StatInsight("较昨日", "新数据"))
            }
        }
    }
    // ── Top3 榜数据 ──
    val topDuration = remember(todayEntries) {
        todayEntries.filter { it.ms > 0L }.sortedByDescending { it.ms }.take(3)
    }
    val topCount = remember(todayEntries) {
        todayEntries.filter { it.count > 0 }.sortedByDescending { it.count }.take(3)
    }
    // 通知榜：独立数据源（未经过 collapseSystemApps），在此手动收折——跟随「显示系统应用」开关，
    // 关闭时把系统应用按包名聚合为单个「系统应用」项再参与 Top3 排序，避免暴露原始系统包名。
    val topNotif = remember(notifVersion, reportsVersion, sysPkgs, showSystemApps) {
        val raw = viewModel.notificationTopForDay(todayKey, 50)
        if (showSystemApps) {
            raw.sortedByDescending { it.second }.take(3)
        } else {
            val user = raw.filter { it.first !in sysPkgs }
            val sysSum = raw.filter { it.first in sysPkgs }.sumOf { it.second }
            val collapsed = if (sysSum > 0) user + (sysPkg to sysSum) else user
            collapsed.sortedByDescending { it.second }.take(3)
        }
    }

    // 双栏/单栏判定单一真源：来自父级 StatsScreen/HomeScreen 的 isWideScreen 入参（基于 LocalWindowInfo.containerSize），
    // 严禁在页内用 LocalConfiguration.screenWidthDp 自行重算，避免双源不一致导致 AnimatedContent 切换子 Tab 时
    // 布局结构（双栏 Row ↔ 单栏 Column）反复切换引起卡顿，且与 StatusBar 的子 Tab 收折判定对齐。
    val isWide = isWideScreen
    if (isWide) {
        // 双栏：左 = 完整指标卡 + 洞察；右 = 今日 Top3 × 3（可点击跳子 Tab）
        Row(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState ?: rememberScrollState())
                .padding(vertical = Dimens.md),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                OverviewMetricCards(
                    totalMs = totalMs, appCount = appCount,
                    longestMs = longestEntry?.ms ?: 0L, longestLabel = longestLabel,
                    narrow = false
                )
                InsightsCard(insights = insights)
            }
            Column(
                modifier = Modifier.width(Dimens.RightPanelWidth),
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                OverviewTopCards(
                    topDuration = topDuration.map { it.packageName to formatHoursMs(it.ms) },
                    topCount = topCount.map { it.packageName to "${it.count} 次" },
                    topNotif = topNotif.map { (pkg, n) -> pkg to "$n 条" },
                    apps = apps,
                    focusIndex = focusIndex,
                    gamepadConnected = gamepadConnected,
                    onOpenSubTab = onOpenSubTab
                )
            }
        }
    } else {
        // 窄屏：纵向堆叠（指标卡 → 洞察 → Top3 × 3）；顶距较宽屏收窄 4dp，
        // 抵消 StatsScreen 子 Tab 行底部新增的 4dp 下边距，使正文绝对位置不变、仅顶部留白分界。
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState ?: rememberScrollState())
                .padding(top = Dimens.md - 4.dp, bottom = Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            OverviewMetricCards(
                totalMs = totalMs, appCount = appCount,
                longestMs = longestEntry?.ms ?: 0L, longestLabel = longestLabel,
                narrow = true
            )
            InsightsCard(insights = insights)
            OverviewTopCards(
                topDuration = topDuration.map { it.packageName to formatHoursMs(it.ms) },
                topCount = topCount.map { it.packageName to "${it.count} 次" },
                topNotif = topNotif.map { (pkg, n) -> pkg to "$n 条" },
                apps = apps,
                focusIndex = focusIndex,
                gamepadConnected = gamepadConnected,
                onOpenSubTab = onOpenSubTab
            )
        }
    }
}

/** 完整指标卡组（复用指标页同款 MetricCard / LongestCard）：宽屏一行三张；窄屏 2+1 网格。 */
@Composable
private fun OverviewMetricCards(
    totalMs: Long,
    appCount: Int,
    longestMs: Long,
    longestLabel: String,
    narrow: Boolean
) {
    if (narrow) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                MetricCard(
                    title = "总计", value = totalMs, format = { formatHours(it) }, unit = "小时",
                    trend = null, accent = MaterialTheme.colorScheme.primary, icon = Icons.Filled.Timer,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                MetricCard(
                    title = "应用量", value = appCount.toLong(), format = { it.toString() }, unit = "个",
                    trend = null, accent = MaterialTheme.colorScheme.tertiary, icon = Icons.Filled.Apps,
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
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            MetricCard(
                title = "总计", value = totalMs, format = { formatHours(it) }, unit = "小时",
                trend = null, accent = MaterialTheme.colorScheme.primary, icon = Icons.Filled.Timer,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricCard(
                title = "应用量", value = appCount.toLong(), format = { it.toString() }, unit = "个",
                trend = null, accent = MaterialTheme.colorScheme.tertiary, icon = Icons.Filled.Apps,
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

/** 三张 Top3 卡（使用时长 / 启动次数 / 通知次数），焦点序号 0/1/2 与子 Tab 跳转一一对应。 */
@Composable
private fun OverviewTopCards(
    topDuration: List<Pair<String, String>>,
    topCount: List<Pair<String, String>>,
    topNotif: List<Pair<String, String>>,
    apps: List<AppModel>,
    focusIndex: Int,
    gamepadConnected: Boolean,
    onOpenSubTab: (StatsSubTab) -> Unit
) {
    OverviewRankCard(
        title = "今日使用时长 Top3",
        rows = topDuration, apps = apps,
        emptyText = "今日暂无使用记录",
        focused = gamepadConnected && focusIndex == 0,
        onClick = { onOpenSubTab(StatsSubTab.USAGE_DURATION) }
    )
    OverviewRankCard(
        title = "今日启动次数 Top3",
        rows = topCount, apps = apps,
        emptyText = "今日暂无启动记录",
        focused = gamepadConnected && focusIndex == 1,
        onClick = { onOpenSubTab(StatsSubTab.LAUNCH_COUNT) }
    )
    OverviewRankCard(
        title = "今日通知次数 Top3",
        rows = topNotif, apps = apps,
        emptyText = "今日暂无通知，或未开启通知使用权",
        focused = gamepadConnected && focusIndex == 2,
        onClick = { onOpenSubTab(StatsSubTab.NOTIFICATIONS) }
    )
}

/**
 * 速览 Top 榜显示名：系统应用聚合项（包名哨兵）固定展示「系统应用」，
 * 其余应用优先取已安装应用 label，缺失时回退包名（与统计页排行行一致）。
 */
private fun resolveTopLabel(pkg: String, apps: List<AppModel>): String {
    if (pkg == HomeViewModel.SYSTEM_AGGREGATE_PKG) return "系统应用"
    return apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
}

/** 单张榜单卡：标题 + 至多 3 行（名次 + 图标 + 应用名 + 数值）；整卡可点击跳转对应子 Tab。 */
@Composable
private fun OverviewRankCard(
    title: String,
    rows: List<Pair<String, String>>,
    apps: List<AppModel>,
    emptyText: String,
    focused: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SurfaceCard(
        modifier = modifier
            .fillMaxWidth()
            .focusBorder(enabled = focused, shape = RoundedCornerShape(Dimens.md))
            .clip(RoundedCornerShape(Dimens.md))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (rows.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                rows.forEachIndexed { index, (pkg, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                        val label = resolveTopLabel(pkg, apps)
                        if (pkg == HomeViewModel.SYSTEM_AGGREGATE_PKG) {
                            // 系统应用聚合项：与统计页排行行一致，用 Android 图标占位，不显示原始包名
                            Box(
                                modifier = Modifier
                                    .size(Dimens.xl)
                                    .clip(RoundedCornerShape(Dimens.xs))
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
                            apps.firstOrNull { it.packageName == pkg }?.icon?.let { icon ->
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(Dimens.xl)
                                        .clip(RoundedCornerShape(Dimens.xs))
                                )
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

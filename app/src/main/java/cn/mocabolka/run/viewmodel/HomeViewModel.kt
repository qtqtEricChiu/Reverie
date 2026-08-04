package cn.mocabolka.run.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.app.usage.UsageStatsManager
import android.os.UserHandle
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.getSystemService
import cn.mocabolka.run.BuildConfig
import cn.mocabolka.run.launcher.PLACEHOLDER_ICON_COLOR
import cn.mocabolka.run.launcher.RemoteVersion
import cn.mocabolka.run.launcher.UpdateRepository
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.gamepad.DialogLayer
import cn.mocabolka.run.gamepad.GamepadEvent
import cn.mocabolka.run.gamepad.GamepadDetector
import cn.mocabolka.run.launcher.AppCache
import cn.mocabolka.run.launcher.AppLauncher
import cn.mocabolka.run.launcher.categoryLabel
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.launcher.AppRepository
import cn.mocabolka.run.util.matchesSearch
import cn.mocabolka.run.launcher.CategoryOverrideRepository
import cn.mocabolka.run.launcher.CategoryMapping
import cn.mocabolka.run.launcher.FavoritesRepository
import cn.mocabolka.run.launcher.GameWhitelist
import cn.mocabolka.run.launcher.RecentsRepository
import cn.mocabolka.run.launcher.DayEntry
import cn.mocabolka.run.launcher.MonthSummary
import cn.mocabolka.run.launcher.NotificationHistoryStore
import cn.mocabolka.run.launcher.NotificationRecord
import cn.mocabolka.run.launcher.SenderAgg
import cn.mocabolka.run.launcher.SocialAppClassifier
import cn.mocabolka.run.launcher.TieredQueryCoordinator
import cn.mocabolka.run.launcher.TieredSerializer
import cn.mocabolka.run.launcher.UsageStatsRepository
import java.io.File
import cn.mocabolka.run.compat.BadgeStore
import cn.mocabolka.run.compat.NotificationItem
import cn.mocabolka.run.compat.RecentNotificationStore
import cn.mocabolka.run.ui.components.StatsPeriod
import cn.mocabolka.run.compat.UsageStatsPermissionHelper
import cn.mocabolka.run.ui.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.util.Calendar

/** 应用列表排序方式。 */
enum class SortMode { NAME, INSTALL_TIME, CATEGORY, LAST_USED }

/**
 * 搜索页手柄焦点目标状态（2026-07-13 专项重构）。
 * - [INPUT]：焦点在搜索输入框，禁用手柄导航（navigationEnabled=false，让位 IME 光标）；
 *   但左摇杆 DOWN 作为「焦点贯通」手势仍被放行（GamepadManager.searchInputExitDown），
 *   可无缝进入下方应用列表首项；
 * - [LIST]：焦点在搜索结果列表，左摇杆/方向键控制列表焦点（上下移动、左右 no-op）。
 */
enum class SearchFocusTarget { INPUT, LIST }

/** 把毫秒时间戳格式化为 yyyy-MM-dd（与 UsageStatsRepository.dateKey 同格式）。 */
private fun dateKeyOf(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    /** 缓存 Application 引用，便于在协程 lambda 中安全访问（避免父类私有属性跨作用域不可见）。 */
    private val appContext: Application = getApplication()
    /** 手柄连接状态单一可信源（P1-1）：由 GamepadDetector 热插拔流驱动，供全工程统一读取，消除 Theme 内独立 collect 的时序空洞。 */
    private val _gamepadConnected = MutableStateFlow(GamepadDetector.isGamepadConnected())
    val gamepadConnected: StateFlow<Boolean> = _gamepadConnected.asStateFlow()
    init {
        viewModelScope.launch {
            GamepadDetector.gamepadConnectedFlow(appContext).collect { _gamepadConnected.value = it }
        }
    }
    private val repository = AppRepository(application)
    private val recentsRepo = RecentsRepository(application)
    /** 分类映射覆盖仓库：AI 回灌的「包名→分类」。装配阶段覆盖系统原始分类。 */
    private val categoryOverrides = CategoryOverrideRepository(application)
    private val launcher = AppLauncher(application)
    /** 设置仓库，供 UI 层读取/修改深色模式等偏好。 */
    val settings = SettingsRepository(application)

    /** 应用内更新：远端 version.json 版本检查仓库（2026-07-30）。 */
    private val updateRepository = UpdateRepository(application)

    /** 待提示的远端新版本；null = 无更新或已处理。HomeScreen 收集后弹 UpdateDialog。 */
    private val _pendingUpdate = MutableStateFlow<RemoteVersion?>(null)
    val pendingUpdate: StateFlow<RemoteVersion?> = _pendingUpdate.asStateFlow()

    init {
        // 启动静默检查更新：6h 间隔节流 + 「跳过此版本」过滤均在仓库层完成；
        // 网络失败静默返回 null，绝不打扰用户。
        viewModelScope.launch {
            val remote = updateRepository.checkForUpdate(BuildConfig.VERSION_CODE) ?: return@launch
            _pendingUpdate.value = remote
        }
    }

    /** 用户选「跳过此版本」：持久化该版本号（同版本不再提示）并关闭弹窗。 */
    fun skipUpdateVersion(versionCode: Int) {
        updateRepository.skipVersion(versionCode)
        _pendingUpdate.value = null
    }

    /** 用户选「稍后」/关闭弹窗：仅本次关闭，下个检查周期（6h 后启动）再提示。 */
    fun dismissUpdate() {
        _pendingUpdate.value = null
    }
    /**
     * LauncherApps 服务：用于监听安装/卸载实时回调。
     * 注意：非默认桌面且未授予 QUERY_ALL_PACKAGES 受限权限时，getSystemService 返回 null，
     * 因此此处允许为 null；为 null 时退化为 refresh() 轮询 queryIntentActivities 枚举应用，
     * 功能不受影响，仅失去实时回调。绝不能用 error() 强依赖，否则首次安装会启动即崩。
     */
    private val launcherApps = application.getSystemService<LauncherApps>()

    private val _apps = MutableStateFlow<List<AppModel>>(emptyList())
    val apps: StateFlow<List<AppModel>> = _apps.asStateFlow()

    /**
     * 系统应用包名全集（始终以 includeSystem=true 枚举得到，与「显示系统应用」开关无关）。
     * 用于统计页在"关闭显示系统应用"时，把散落在各包名下的系统应用使用时长
     * 折叠成一个置底的"系统应用"聚合项并隐藏具体系统包名（任务 13）。
     */
    private val _systemPackages = MutableStateFlow<Set<String>>(emptySet())
    val systemPackages: StateFlow<Set<String>> = _systemPackages.asStateFlow()

    private val _recents = MutableStateFlow<List<AppModel>>(emptyList())
    val recents: StateFlow<List<AppModel>> = _recents.asStateFlow()

    private val _focusedPackage = MutableStateFlow<String?>(null)
    val focusedPackage: StateFlow<String?> = _focusedPackage.asStateFlow()

    // 审计 SEC-3：去除 replay=1 —— 否则配置变更/重组重建订阅时会重放最后一条事件
    // （若为 Select/Trigger 会误启动应用/误切 Tab）。溢出时丢最旧事件，tryEmit 永不失败。
    private val _events = MutableSharedFlow<GamepadEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<GamepadEvent> = _events.asSharedFlow()

    /** 搜索页是否处于「搜索模式」（即当前 Tab = SEARCH）。Activity 据此决定是否接管导航键。 */
    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    /**
     * 搜索页手柄焦点目标状态机（2026-07-13 专项重构）。
     *
     * 替代原有的 `searchInputActive`（Boolean）+ `searchFocusOnInput`（HomeScreen 本地 var）
     * 双轨状态，统一为单源真相：
     * - [INPUT]：焦点在搜索输入框，手柄导航键让位给输入光标（navigationEnabled=false）；
     * - [LIST]：焦点在搜索结果列表，左摇杆/方向键控制列表焦点。
     *
     * MainActivity 据此动态设置 gamepad.navigationEnabled：
     *   INPUT → false（禁用手柄导航，让位 IME 光标移动）；
     *   LIST  → true（启用左摇杆穿透列表）。
     */
    // 默认 INPUT：进入搜索页时焦点落在搜索输入框并弹出软键盘，便于直接输入。
    // 用户可推左摇杆/方向键任意方向进入 LIST（结果列表），或按 Y 在 INPUT ⇄ LIST 间切换；
    // Y 在搜索页只负责输入框，绝不收藏。
    private val _searchFocusTarget = MutableStateFlow(SearchFocusTarget.INPUT)
    val searchFocusTarget: StateFlow<SearchFocusTarget> = _searchFocusTarget.asStateFlow()

    /** @param isInput true=输入框激活态，false=列表态 */
    fun setSearchFocusTarget(isInput: Boolean) {
        _searchFocusTarget.value = if (isInput) SearchFocusTarget.INPUT else SearchFocusTarget.LIST
    }

    /** 搜索关键字。 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 设置弹窗是否打开（游戏手柄事件在此状态下全部放行给弹窗焦点系统）。 */
    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    /**
     * 当前 Dialog 层级（事件栈顶层）。
     * NONE = 无 dialog 打开，事件正常进入各 Tab 分发逻辑；
     * 非 NONE = 所有事件优先路由到对应 dialog 处理器。
     * 由 HomeScreen 在 dialog 打开/关闭时更新。
     */
    private val _dialogLayer = MutableStateFlow(DialogLayer.NONE)
    val dialogLayer: StateFlow<DialogLayer> = _dialogLayer.asStateFlow()

    /** 排序方式。 */
    private val _sortMode = MutableStateFlow(SortMode.NAME)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    /** 分类筛选标签（"全部" + 各分类），由应用列表推导（C2-1）。 */
    private val _filterChips = MutableStateFlow(listOf("全部"))
    val filterChips: StateFlow<List<String>> = _filterChips.asStateFlow()

    /** 完整的分类 Tab 列表（精选 → 收藏 → 全部 → 各分类 → 其它），与 UI 一致。 */
    private val _categoryTabs = MutableStateFlow(listOf("全部", "精选"))
    val categoryTabs: StateFlow<List<String>> = _categoryTabs.asStateFlow()

    /** 当前选中的分类标签；首屏默认"精选"（手机手柄向右旋屏的推荐首屏）。 */
    private val _selectedCategory = MutableStateFlow("精选")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    /** 启动加载标志：true 时 UI 显示转圈动画，后台列表就绪后转 false 自然过渡到图标。 */
    private val _isBooting = MutableStateFlow(true)
    val isBooting: StateFlow<Boolean> = _isBooting.asStateFlow()

    /**
     * 自制开屏 Overlay 可见标志（第二幕）。
     * **初始即为 true**：冷启动首帧（setContent 组合时）Overlay 就已盖住主界面，
     * 让主界面在遮罩之下完成图标加载，避免"先露主屏、再弹 splash"的诡异时序。
     * 数据就绪（isBooting 翻转 true→false）时由 [HomeScreen] 调用 [endOverlay] 置 false 淡出，
     * 露出已加载完成的主界面。平台 Splash 在目标环境不触发退场回调，故不依赖
     * OnExitAnimationListener。
     */
    private val _showOverlay = MutableStateFlow(true)
    val showOverlay: StateFlow<Boolean> = _showOverlay.asStateFlow()

    /**
     * 结束第二幕 Overlay：数据就绪后淡出到主屏。
     * 由 [HomeScreen] 的 LaunchedEffect(isBooting) 在 isBooting 翻转（true→false）时调用。
     * 仅置 false，淡出动画由 [SplashOverlay] 的 AnimatedVisibility 负责；
     * isBooting 天然只翻转一次，无需重入守卫。
     */
    fun endOverlay() {
        _showOverlay.value = false
    }

    /**
     * 真实图标就绪标志：阶段二完整加载（真实图标 + 安装时间）完成后置 true。
     * Activity 据此控制原生 SplashScreen 的关闭时机——在此之前保持开屏，
     * 避免用户看到缓存占位图标（灰色块）或无内容帧。
     */
    private val _realReady = MutableStateFlow(false)
    val realReady: StateFlow<Boolean> = _realReady.asStateFlow()

    /** 手柄焦点所在的分类 chip 索引；-1 表示焦点在应用区（C2-4）。 */
    private val _focusedChip = MutableStateFlow(-1)
    val focusedChip: StateFlow<Int> = _focusedChip.asStateFlow()

    /** 启动失败等瞬时提示，由 UI 层收集展示 Toast。 */
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    /** 请求导入分类映射：UI 层收集后唤出系统文件选择器（SAF），由用户自行选择合规文件。 */
    private val _importRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val importRequest: SharedFlow<Unit> = _importRequest.asSharedFlow()

    /** 导出成功后分享文件：UI 层收集后调起系统分享对话框（Intent.ACTION_SEND）。 */
    private val _shareFileUri = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val shareFileUri: SharedFlow<Uri> = _shareFileUri.asSharedFlow()

    /** 启动中转场遮罩标志（C3-1）。 */
    private val _launching = MutableStateFlow(false)
    val launching: StateFlow<Boolean> = _launching.asStateFlow()

    /**
     * 启动后退出信号：当设置「启动游戏后不保留 Reverie 进程」开启且本次启动成功时发射，
     * 由 MainActivity 收集并 finishAndRemoveTask() 销毁 Reverie 任务（不保留后台）。
     */
    private val _exitAfterLaunch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitAfterLaunch: SharedFlow<Unit> = _exitAfterLaunch.asSharedFlow()

    /** 应用列表扫描中标志（C7-5，设置"重新扫描"期间置 true）。 */
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** QUERY_ALL_PACKAGES 权限是否已授予（Android 11+ 枚举全部应用需要）。 */
    private val _queryAllGranted = MutableStateFlow(isQueryAllPackagesGranted(application))
    val queryAllGranted: StateFlow<Boolean> = _queryAllGranted.asStateFlow()

    /** 精选区：白名单 + 用户收藏（X 键固定）的已安装游戏，置顶呈现。 */
    private val _featured = MutableStateFlow<List<AppModel>>(emptyList())
    val featured: StateFlow<List<AppModel>> = _featured.asStateFlow()

    /**
     * 写死的精选白名单（[GameWhitelist.packages]）是否有任意一个已安装。
     * false 时主页隐藏「精选」Tab，并把首屏回落到「收藏」（有收藏时）或「全部」（无收藏时）。
     * 一切仍以精选优先：白名单任意一个已安装即保持现状（精选 Tab 常驻、首屏精选）。
     */
    private val _whitelistFeaturedAvailable = MutableStateFlow(true)
    val whitelistFeaturedAvailable: StateFlow<Boolean> =
        _whitelistFeaturedAvailable.asStateFlow()

    /** 其余游戏 / 娱乐应用（精选区之外的全部游戏）。 */
    private val _games = MutableStateFlow<List<AppModel>>(emptyList())
    val games: StateFlow<List<AppModel>> = _games.asStateFlow()

    /** 各应用未读通知数（来自通知监听服务，C6-1），供网格/最近/收藏 tile 角标。 */
    val badges: StateFlow<Map<String, Int>> = BadgeStore.badges

    /** 最近通知快照（最多 10 条，按时间倒序），供竖屏详情浮窗通知卡片展示与跳转。 */
    val recentNotifications: StateFlow<List<NotificationItem>> =
        RecentNotificationStore.recentNotifications

    /**
     * 预聚合的今日/本周使用时长 Map（R13 性能优化）。
     * 随 refresh() 一次性计算，避免 UI 层逐行调用 todayUsageOf/weeklyUsageOf 触发 O(n²) 遍历
     * 或每次重组都全量 aggregateFrom 扫描。UI 直接按包名查 Map。
     */
    private val _todayUsageMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val todayUsageMap: StateFlow<Map<String, Long>> = _todayUsageMap.asStateFlow()
    private val _weeklyUsageMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val weeklyUsageMap: StateFlow<Map<String, Long>> = _weeklyUsageMap.asStateFlow()

    /** 上次 refreshReports() 同步时刻，用于进入统计页时的时间闸门节流（R13）。@Volatile 保证 IO 协程写入对主线程可见。 */
    @Volatile
    private var lastReportSyncMs: Long = 0L

    /** 同步进行中守卫：防止统计页快速反复进入触发并发 sync 重复扫描系统事件。 */
    private val reportSyncInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 报表数据版本号：每次后台 sync 完成后自增，UI 以其为 remember key 触发重读。 */
    private val _reportsVersion = MutableStateFlow(0)
    val reportsVersion: StateFlow<Int> = _reportsVersion.asStateFlow()

    /** 各分类 Tab 的应用数量（R11-4），与 UI 分类 Tab 顺序一致，供角标展示。 */
    private val _categoryCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<String, Int>> = _categoryCounts.asStateFlow()

    private val favoritesRepo = FavoritesRepository(application)
    private val usageStatsRepo = UsageStatsRepository(application)

    init {
        // 通知历史持久化库（与角标/近期流分离）：VM 侧兜底初始化，
        // 保证监听服务尚未拉起时统计页查询也不空指针。
        NotificationHistoryStore.init(application)
    }

    /**
     * 分层查询协调器：对上层屏蔽热层 / 冷层差异。
     * 热层 90 天内零 IO（直接读 [usageStatsRepo] 内存）；90 天前按需读按月归档并 LRU 缓存。
     */
    private val usageArchiveDir = File(application.filesDir, "archives/usage_stats")
    private val usageCoordinator = TieredQueryCoordinator(usageStatsRepo, usageArchiveDir, TieredSerializer)

    /** 年视图数据集（跨热冷合并）：各应用汇总 + 逐月时长 / 次数，供 StatsScreen 异步加载。 */
    data class YearlyData(
        val report: List<UsageStatsRepository.UsageEntry>,
        val monthTotals: List<Pair<Long, Long>>,
        val monthCounts: List<Pair<Long, Int>>
    )

    /** 历史回溯：某年某月的归档数据集。 */
    data class HistoryMonthReport(
        val year: Int,
        val month: Int,
        val apps: List<UsageStatsRepository.UsageEntry>,
        val totalMs: Long,
        val daySeries: List<Pair<String, Long>>
    )

    /** 报表数据（日报/周报/月报/年报），由 [refreshReports] 填充，供设置页展示。 */
    private val _dailyReport = MutableStateFlow<List<UsageStatsRepository.UsageEntry>>(emptyList())
    private val _weeklyReport = MutableStateFlow<List<UsageStatsRepository.UsageEntry>>(emptyList())
    private val _monthlyReport = MutableStateFlow<List<UsageStatsRepository.UsageEntry>>(emptyList())
    private val _yearlyReport = MutableStateFlow<List<UsageStatsRepository.UsageEntry>>(emptyList())
    val dailyReport: StateFlow<List<UsageStatsRepository.UsageEntry>> = _dailyReport.asStateFlow()
    val weeklyReport: StateFlow<List<UsageStatsRepository.UsageEntry>> = _weeklyReport.asStateFlow()
    val monthlyReport: StateFlow<List<UsageStatsRepository.UsageEntry>> = _monthlyReport.asStateFlow()
    val yearlyReport: StateFlow<List<UsageStatsRepository.UsageEntry>> = _yearlyReport.asStateFlow()

    /** 使用情况访问是否已授权（未授权时报表为空，UI 引导开启）。 */
    private val _usageGranted = MutableStateFlow(UsageStatsPermissionHelper.isGranted(application))
    val usageGranted: StateFlow<Boolean> = _usageGranted.asStateFlow()

    /**
     * 重新同步使用时长并刷新四份报表。每次打开"使用统计"面板或权限变更后调用。
     * R13：同步预聚合 Map（今日/本周），供 UI 按包名 O(1) 读取，避免逐行全量扫描。
     */
    fun refreshReports() {
        _usageGranted.value = UsageStatsPermissionHelper.isGranted(appContext)
        if (!_usageGranted.value) {
            _dailyReport.value = emptyList()
            _weeklyReport.value = emptyList()
            _monthlyReport.value = emptyList()
            _yearlyReport.value = emptyList()
            _todayUsageMap.value = emptyMap()
            _weeklyUsageMap.value = emptyMap()
            return
        }
        // 并发守卫：已有同步在跑则直接返回（StateFlow 更新后 UI 自会刷新）
        if (!reportSyncInFlight.compareAndSet(false, true)) return
        lastReportSyncMs = System.currentTimeMillis()
        // 增量同步（首装会回看 30 天）。sync 内部 queryEvents 为重 IO，
        // 移至 IO 线程执行，消除进入统计页时的主线程卡顿 / ANR 风险。
        viewModelScope.launch(Dispatchers.IO) {
            try {
                usageStatsRepo.sync()
                _dailyReport.value = usageStatsRepo.getDaily()
                _weeklyReport.value = usageStatsRepo.getWeekly()
                _monthlyReport.value = usageStatsRepo.getMonthly()
                _yearlyReport.value = usageStatsRepo.getYearly()
                // 预聚合 Map：今日直接复用缓存；本周一次性 aggregateFrom 后按包名建索引
                _todayUsageMap.value = usageStatsRepo.getTodayMap()
                _weeklyUsageMap.value = usageStatsRepo.getWeekly().associate { it.packageName to it.ms }
                // 版本号自增：通知统计页以新数据为 key 重读周期报表
                _reportsVersion.value = _reportsVersion.value + 1
            } finally {
                reportSyncInFlight.set(false)
            }
        }
    }

    /**
     * 进入统计页时调用：带时间闸门（默认 5 分钟内不重复 sync），避免每次切到统计 Tab
     * 都全量 queryEvents 扫描系统使用记录（R13 省电优化）。
     */
    fun refreshReportsThrottled(gateMs: Long = 5 * 60_000L) {
        val now = System.currentTimeMillis()
        if (now - lastReportSyncMs < gateMs) return
        refreshReports()
    }

    /** 详情面板用：返回某应用今日使用时长（ms），未记录返回 0。改为读预聚合 Map（O(1)）。 */
    fun todayUsageOf(packageName: String): Long = _todayUsageMap.value[packageName] ?: 0L

    /** 详情面板用：返回某应用本周（周一 0 点起）使用时长（ms），未记录返回 0。改为读预聚合 Map（O(1)）。 */
    fun weeklyUsageOf(packageName: String): Long = _weeklyUsageMap.value[packageName] ?: 0L

    // ── 统计页专用 API（Tai 风格：今日小时柱状图 + 周期概览） ─────────────
    /** 今日 24 小时分布（事件级粒度），索引即小时。 */
    fun hourlyToday(): LongArray = usageStatsRepo.getHourly()
    /** 今日逐小时启动次数（foreground 事件数，按小时统计）。 */
    fun hourlyCountToday(): IntArray = usageStatsRepo.getHourlyCounts()
    /** 今日逐小时各应用明细（时长ms, 启动次数），索引即小时。 */
    fun hourlyAppBreakdownToday(): Array<Map<String, Pair<Long, Int>>> = usageStatsRepo.getHourlyAppBreakdown()
    /** 某天（dateMs）内游玩时长 Top3 应用包名。 */
    fun dayAppTop(dateMs: Long): List<String> =
        usageStatsRepo.getDailyFor(dateKeyOf(dateMs)).take(3).map { it.packageName }
    /** 某月（monthStartMs 所在月）内游玩时长 Top3 应用包名。 */
    fun monthAppTop(monthMs: Long): List<String> = usageStatsRepo.getMonthTopApps(monthMs, 3)

    /** 最近 7 天每天总时长（ms），按日期升序（索引 0 = 6 天前，6 = 今天）。 */
    fun last7DaysTotal(): LongArray = usageStatsRepo.getDaySeries(7)

    /** 最近 30 天每天总时长（ms），按日期升序。 */
    fun last30DaysTotal(): LongArray = usageStatsRepo.getDaySeries(30)

    // ── 日期锚点查询：统计页日期选择器用（按所选日/周/月/年） ──
    fun dailyReportFor(dateKey: String): List<UsageStatsRepository.UsageEntry> =
        usageStatsRepo.getDailyFor(dateKey)
    fun weeklyReportFor(anchorMs: Long): List<UsageStatsRepository.UsageEntry> =
        usageStatsRepo.getWeeklyFor(anchorMs)
    fun monthlyReportFor(anchorMs: Long): List<UsageStatsRepository.UsageEntry> =
        usageStatsRepo.getMonthlyFor(anchorMs)
    fun yearlyReportFor(anchorMs: Long): List<UsageStatsRepository.UsageEntry> =
        usageStatsRepo.getYearlyFor(anchorMs)

    // ── 分层异步查询：跨热冷合并（年视图 / 历史回溯）──
    private fun yearRangeMs(anchorMs: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMs }
        cal.set(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.YEAR, 1); cal.add(Calendar.MILLISECOND, -1)
        return start to cal.timeInMillis
    }

    private fun monthStartMsOf(ms: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 年视图各应用汇总（热 + 冷合并，可能跨越冷层）。 */
    suspend fun yearlyReportForAsync(anchorMs: Long): List<UsageStatsRepository.UsageEntry> =
        withContext(Dispatchers.IO) {
            val (ys, ye) = yearRangeMs(anchorMs)
            usageCoordinator.loadForRange(dateKeyOf(ys), dateKeyOf(ye))
                .values
                .sortedByDescending { it.ms }
                .map { UsageStatsRepository.UsageEntry(it.packageName, it.ms, it.count) }
        }

    /** 年视图完整数据集（各应用汇总 + 逐月时长 / 次数），跨热冷合并。 */
    suspend fun loadYearlyData(anchorMs: Long): YearlyData = withContext(Dispatchers.IO) {
        val (ys, ye) = yearRangeMs(anchorMs)
        val startKey = dateKeyOf(ys)
        val endKey = dateKeyOf(ye)
        val report = usageCoordinator.loadForRange(startKey, endKey)
            .values
            .sortedByDescending { it.ms }
            .map { UsageStatsRepository.UsageEntry(it.packageName, it.ms, it.count) }
        val totals = ArrayList<Pair<Long, Long>>()
        val counts = ArrayList<Pair<Long, Int>>()
        val cal = Calendar.getInstance().apply { timeInMillis = ys }
        while (cal.timeInMillis <= ye) {
            val ms = cal.timeInMillis
            val mStartMs = monthStartMsOf(ms)
            val calEnd = Calendar.getInstance().apply {
                timeInMillis = ms
                set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, 1); add(Calendar.DAY_OF_MONTH, -1)
            }
            val m = usageCoordinator.loadForRange(dateKeyOf(mStartMs), dateKeyOf(calEnd.timeInMillis))
            totals.add(mStartMs to m.values.sumOf { it.ms })
            counts.add(mStartMs to m.values.sumOf { it.count })
            cal.add(Calendar.MONTH, 1)
        }
        YearlyData(report, totals, counts)
    }

    /** 归档中存在的年份列表（升序）。 */
    suspend fun listArchiveYears(): List<Int> = withContext(Dispatchers.IO) {
        usageCoordinator.listArchiveYears()
    }

    /** 某年有归档的月份概要。 */
    suspend fun listArchiveMonths(year: Int): List<MonthSummary> = withContext(Dispatchers.IO) {
        usageCoordinator.listArchiveMonths(year)
    }

    /** 加载某年某月归档（合并各应用汇总 + 逐日时长序列），供历史回溯展示。 */
    suspend fun loadHistoryMonth(year: Int, month: Int): HistoryMonthReport? = withContext(Dispatchers.IO) {
        val am = usageCoordinator.loadArchiveMonth(year, month) ?: return@withContext null
        val perApp = linkedMapOf<String, DayEntry>()
        am.days.forEach { (_, dayMap) ->
            dayMap.forEach { (pkg, e) ->
                val ex = perApp[pkg]
                perApp[pkg] = if (ex == null) e else DayEntry(pkg, ex.ms + e.ms, ex.count + e.count)
            }
        }
        val apps = perApp.values
            .sortedByDescending { it.ms }
            .map { UsageStatsRepository.UsageEntry(it.packageName, it.ms, it.count) }
        val totalMs = apps.sumOf { it.ms }
        val daySeries = am.days.keys.sorted().map { dk -> dk to (am.days[dk]?.values?.sumOf { it.ms } ?: 0L) }
        HistoryMonthReport(year, month, apps, totalMs, daySeries)
    }

    /**
     * 统计报表系统应用折叠：
     * - 开启「显示系统应用」时原样返回（系统应用以各自包名出现在排行中）；
     * - 关闭时，把 [report] 中属于系统应用（包名 ∈ [systemPackages]）的条目从排行中移除，
     *   且**不再追加**聚合项"系统应用"。这样用户关闭显示系统应用后，
     *   排行中完全看不见系统相关的条目，指标卡中的"应用量"也只统计用户应用。
     */
    /**
     * 系统应用隔离：关闭系统应用时，将所有系统应用外观聚合成一个【系统应用】项并置底。
     * 注意：仅做外观聚合，绝不合并各系统应用底层数据（时长/次数仍按应用独立持久化）。
     */
    fun collapseSystemApps(report: List<UsageStatsRepository.UsageEntry>): List<UsageStatsRepository.UsageEntry> {
        if (settings.showSystemApps) return report
        val sysPkgs = _systemPackages.value
        if (sysPkgs.isEmpty()) return report
        val userEntries = report.filter { it.packageName !in sysPkgs }
        val sysEntries = report.filter { it.packageName in sysPkgs }
        if (sysEntries.isEmpty()) return userEntries
        val sumMs = sysEntries.sumOf { it.ms }
        val sumCount = sysEntries.sumOf { it.count }
        val aggregated = UsageStatsRepository.UsageEntry(SYSTEM_AGGREGATE_PKG, sumMs, sumCount)
        return userEntries + aggregated
    }

    /** 按周期 + 锚点日返回图表用的"逐日/逐月时长序列"（ms），对齐到所选自然周期。 */
    fun daySeriesFor(period: StatsPeriod, anchorMs: Long): LongArray = when (period) {
        StatsPeriod.DAILY -> usageStatsRepo.getDaySeries(7, anchorMs)
        StatsPeriod.WEEKLY -> usageStatsRepo.getDaySeriesInWeek(anchorMs)
        StatsPeriod.MONTHLY -> usageStatsRepo.getDaySeriesInMonth(anchorMs)
        StatsPeriod.YEARLY -> usageStatsRepo.getMonthSeriesInYear(anchorMs)
    }

    /** 按周期 + 锚点日返回图表用的"逐日/逐月启动次数序列"，与 [daySeriesFor] 一一对应。 */
    fun dayCountSeriesFor(period: StatsPeriod, anchorMs: Long): IntArray = when (period) {
        StatsPeriod.DAILY -> usageStatsRepo.getDayCountSeries(7, anchorMs)
        StatsPeriod.WEEKLY -> usageStatsRepo.getDayCountSeriesInWeek(anchorMs)
        StatsPeriod.MONTHLY -> usageStatsRepo.getDayCountSeriesInMonth(anchorMs)
        StatsPeriod.YEARLY -> usageStatsRepo.getMonthCountSeriesInYear(anchorMs)
    }

    /** 最近 [n] 天（含锚点当天）每天总时长列表，按日期倒序（最新在最上）。供统计页每日列表。 */
    fun dailyUsageList(n: Int, anchorMs: Long = System.currentTimeMillis()): List<Pair<Long, Long>> =
        usageStatsRepo.getDailyUsageList(n, anchorMs)

    // ── 通知统计 API（NotificationHistoryStore，热层同步零 IO / 冷层 Dispatchers.IO）──

    /** 通知历史数据版本号：新记录写入时自增，UI 以其为 key 触发重读。 */
    val notificationsVersion: StateFlow<Long> = NotificationHistoryStore.changeTick

    /** 今日 dateKey（yyyy-MM-dd）。 */
    fun notificationTodayKey(): String = NotificationHistoryStore.todayKey()

    /** 某天各应用通知条数（热层同步零 IO）。 */
    fun notificationCountsForDay(dateKey: String): Map<String, Int> =
        NotificationHistoryStore.countByPackageForDay(dateKey)

    /** 某天通知条数 Top（速览榜单用），按条数降序。 */
    fun notificationTopForDay(dateKey: String, limit: Int): List<Pair<String, Int>> =
        NotificationHistoryStore.countByPackageForDay(dateKey)
            .entries.sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }

    /** 某天全部通知记录（时间倒序）。热层同步命中，冷层读归档。 */
    suspend fun notificationListForDay(dateKey: String): List<NotificationRecord> =
        NotificationHistoryStore.listForDay(dateKey)

    /** 日期范围各应用通知条数（热 + 冷合并，IO 线程）。 */
    suspend fun notificationCountsForRange(start: String, end: String): Map<String, Int> =
        NotificationHistoryStore.countByPackageForRange(start, end)

    /**
     * 组装社交包名集合：内置白名单 ∪ 全应用列表中 categoryText=="社交" 的包名
     * （含 CategoryOverrideRepository AI 回灌覆盖——覆盖已在 AppModel.categoryText 装配时生效）。
     */
    fun socialPackages(): Set<String> {
        val fromCategory = _apps.value
            .filter { SocialAppClassifier.isSocial(it.packageName, it.categoryText) }
            .map { it.packageName }
        return SocialAppClassifier.BUILTIN_SOCIAL + fromCategory
    }

    /** 某天社交应用发件人 Top 聚合（热层同步零 IO）。 */
    fun topSendersForDay(dateKey: String, limit: Int): List<SenderAgg> =
        NotificationHistoryStore.topSendersForDay(dateKey, limit, socialPackages())

    /** 日期范围社交应用发件人 Top 聚合（热 + 冷合并，IO 线程）。 */
    suspend fun topSendersForRange(start: String, end: String, limit: Int): List<SenderAgg> =
        NotificationHistoryStore.topSendersForRange(start, end, limit, socialPackages())

    /** 日期范围按日通知总数序列（日期升序），供通知历史趋势图。 */
    suspend fun notificationDailyCounts(start: String, end: String): List<Pair<String, Int>> =
        NotificationHistoryStore.dailyCountsForRange(start, end)

    /** 日期范围全部通知明细（按时间升序），供通知历史钻取子页按应用/发件人筛选。 */
    suspend fun notificationListForRange(start: String, end: String): List<NotificationRecord> =
        NotificationHistoryStore.listForRange(start, end)

    /** 最近 [n] 天（含锚点当天）每天总启动次数列表，按日期倒序。供统计页每日列表（次数模式）。 */
    fun dailyCountList(n: Int, anchorMs: Long = System.currentTimeMillis()): List<Pair<Long, Int>> =
        usageStatsRepo.getDailyCountList(n, anchorMs)

    /** 最近 [n] 个月（含锚点当月）每月总时长列表，按月份倒序（最新在最上）。供统计页（年视图）列表。 */
    fun monthlyUsageList(n: Int, anchorMs: Long = System.currentTimeMillis()): List<Pair<Long, Long>> =
        usageStatsRepo.getMonthlyUsageList(n, anchorMs)

    /** 自然周逐日 (dateMs, ms) 列表（升序），供统计页（周视图）列表。 */
    fun weekDailyList(anchorMs: Long) = usageStatsRepo.getWeekDailyList(anchorMs)

    /** 自然周逐日 (dateMs, count) 列表（升序），供统计页（周视图）列表（次数模式）。 */
    fun weekDailyCountList(anchorMs: Long) = usageStatsRepo.getWeekDailyCountList(anchorMs)

    /** 自然月逐日 (dateMs, ms) 列表（升序），供统计页（月视图）列表。 */
    fun monthDailyList(anchorMs: Long) = usageStatsRepo.getMonthDailyList(anchorMs)

    /** 自然月逐日 (dateMs, count) 列表（升序），供统计页（月视图）列表（次数模式）。 */
    fun monthDailyCountList(anchorMs: Long) = usageStatsRepo.getMonthDailyCountList(anchorMs)

    /** 自然年逐月 (monthMs, ms) 列表（升序），供统计页（年视图）列表。 */
    fun yearMonthlyList(anchorMs: Long) = usageStatsRepo.getYearMonthlyList(anchorMs)

    /** 自然年逐月 (monthMs, count) 列表（升序），供统计页（年视图）列表（次数模式）。 */
    fun yearMonthlyCountList(anchorMs: Long) = usageStatsRepo.getYearMonthlyCountList(anchorMs)

    /** 某天逐小时明细（时长/次数/每应用），过往日命中持久化快照，今日回退实时缓存。 */
    fun hourlyForDate(dateKey: String): UsageStatsRepository.HourlyDetail = usageStatsRepo.getHourlyForDate(dateKey)

    /** 当前锚点日是否为"今天"（决定 DAILY 视图是否绘制按小时柱状图）。 */
    fun isAnchorToday(anchorMs: Long): Boolean =
        dateKeyOf(anchorMs) == dateKeyOf(System.currentTimeMillis())

    /** 历史累计总时长（所有持久化数据）。 */
    fun allTimeTotalMs(): Long = usageStatsRepo.getAllTimeTotal()

    /** 当前周期：今日 0 点起的总时长（ms）。 */
    fun todayTotalMs(): Long = usageStatsRepo.getDaily().sumOf { it.ms }

    /** 当前周期：本月 1 号 0 点起的总时长（ms）。 */
    fun monthTotalMs(): Long = usageStatsRepo.getMonthly().sumOf { it.ms }

    /** 当前周期：本年 1 月 1 号 0 点起的总时长（ms）。 */
    fun yearTotalMs(): Long = usageStatsRepo.getYearly().sumOf { it.ms }

    /**
     * 强制横屏缺悬浮窗权限时提示用户（不再自动跳转系统设置页）。
     * 悬浮窗权限是系统级强制横屏的增强条件，非必需，不应打断用户操作。
     * 用户如需授权可自行在「兼容向导」或系统设置中完成。
     */
    fun requestOverlayForOrientation() {
        _toast.tryEmit("强制横屏需要悬浮窗权限才能启用系统级旋转锁定（非必需，仅增强）")
    }


    /** 收藏（固定）的包名集合，供精选区星标指示与置顶使用。 */
    val favorites: StateFlow<Set<String>> = favoritesRepo.pinned

    /** 监听应用安装/卸载/可用性变化，自动刷新列表。 */
    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String?, user: UserHandle) {
            packageName?.let { incrementalAdd(it) } ?: debouncedRefresh()
        }
        override fun onPackageRemoved(packageName: String?, user: UserHandle) {
            packageName?.let { incrementalRemove(it) } ?: debouncedRefresh()
        }
        override fun onPackageChanged(packageName: String?, user: UserHandle) {
            // 变更（更新/覆盖安装）需要刷新图标和元数据，但比全量快
            packageName?.let { incrementalUpdate(it) } ?: debouncedRefresh()
        }
        override fun onPackagesAvailable(
            packageNames: Array<out String>?, user: UserHandle, replacing: Boolean
        ) = debouncedRefresh()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?, user: UserHandle, replacing: Boolean
        ) = debouncedRefresh()
    }

    /** 安装/卸载事件常连发，合并到 400ms 窗口内只刷新一次（C7-1）。 */
    private var rescanJob: kotlinx.coroutines.Job? = null
    private fun debouncedRefresh() {
        rescanJob?.cancel()
        rescanJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400.milliseconds)
            refresh()
        }
    }

    /**
     * 增量添加单个应用。使用 [AppRepository.loadPackage] 仅查询新安装的应用，
     * 插入到当前列表并更新缓存，避免全量 [refresh()]。
     * 若增量失败（如无法解析），回退到全量刷新。
     */
    private fun incrementalAdd(packageName: String) {
        viewModelScope.launch {
            val app = repository.loadPackage(packageName) ?: run {
                debouncedRefresh(); return@launch
            }
            val overrides = categoryOverrides.overrides.value
            val finalApp = app.copy(
                categoryText = overrides[packageName] ?: app.categoryText,
                todayUsage = usageStatsRepo.getTodayFor(packageName)
            )
            val current = _apps.value.toMutableList()
            // 防重复：包名已存在时不添加（onPackageAdded 可能重复触发）
            if (current.any { it.packageName == packageName }) return@launch
            current.add(finalApp)
            _apps.value = sortApps(current)
            // 增量更新缓存
            AppCache.save(appContext, _apps.value)
            rebuildCategoryState()
        }
    }

    /**
     * 增量移除单个应用。从当前列表移除包名并更新缓存，
     * 避免全量 [refresh()]。
     */
    private fun incrementalRemove(packageName: String) {
        val current = _apps.value.toMutableList()
        val removed = current.removeAll { it.packageName == packageName }
        if (!removed) { debouncedRefresh(); return }
        _apps.value = sortApps(current)
        AppCache.save(appContext, _apps.value)
        rebuildCategoryState()
    }

    /**
     * 增量更新单个应用（覆盖安装/配置变更）。刷新该应用的图标和元数据。
     */
    private fun incrementalUpdate(packageName: String) {
        viewModelScope.launch {
            val app = repository.loadPackage(packageName) ?: run {
                // 无法解析时可能已卸载
                incrementalRemove(packageName); return@launch
            }
            val overrides = categoryOverrides.overrides.value
            val finalApp = app.copy(
                categoryText = overrides[packageName] ?: app.categoryText,
                todayUsage = usageStatsRepo.getTodayFor(packageName)
            )
            val current = _apps.value.toMutableList()
            val idx = current.indexOfFirst { it.packageName == packageName }
            if (idx < 0) {
                // 列表中没有该应用，可能是新安装
                current.add(finalApp)
            } else {
                current[idx] = finalApp
            }
            _apps.value = sortApps(current)
            AppCache.save(appContext, _apps.value)
            rebuildCategoryState()
        }
    }

    /** 增量操作后重建分类状态（Tab / 计数 / 精选）。 */
    private fun rebuildCategoryState() {
        val merged = _apps.value
        // 分类计数（精选计数须在 rebuildFeatured 之后取最新值，见下方补充）
        val counts = mutableMapOf(
            "全部" to merged.size,
            "收藏" to favorites.value.size
        )
        merged.forEach { a ->
            val c = a.categoryText
            counts[c] = (counts[c] ?: 0) + 1
        }
        val cats = listOf("全部") + merged.map { it.categoryText }.distinct()
        _filterChips.value = cats
        val wlInstalled = merged.any {
            it.packageName in GameWhitelist.packages && it.installed
        }
        _whitelistFeaturedAvailable.value = wlInstalled
        val pinned = mutableListOf<String>()
        pinned.add("全部")
        if (wlInstalled) pinned.add("精选")
        if (favorites.value.isNotEmpty()) pinned.add("收藏")
        val rest = cats.drop(1)
        _categoryTabs.value = pinned + rest
        // 精选区重建
        val allGames = merged.filter {
            it.isGame || GameWhitelist.isEntertainment(it.category) ||
                it.packageName in GameWhitelist.packages
        }
        rebuildFeatured(allGames)
        // 精选角标须在 rebuildFeatured 之后取最新 _featured，避免计数陈旧（少 1）
        counts["精选"] = _featured.value.size
        _categoryCounts.value = counts
        _games.value = allGames.filter { it.packageName !in featuredPackages() }
    }

    init {
        // 阶段一（主线程）：快速加载磁盘缓存占位；阶段二在后台协程加载真实数据。
        refresh()
        // 超时保护（R14）：如果 refresh() 阶段二协程异常/被取消永久不返回，
        // 最多 12 秒后强制就绪，避免用户永远卡在开屏。
        viewModelScope.launch {
            kotlinx.coroutines.delay(12.seconds)
            forceReady()
        }
        // registerCallback 需要 QUERY_ALL_PACKAGES，未授权会抛 SecurityException，
        // 安全降级：失败则仅丢失实时回调，不影响枚举与启动。
        runCatching { launcherApps?.registerCallback(packageCallback) }
        viewModelScope.launch { favoritesRepo.pinned.collect { rebuildFeatured() } }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { launcherApps?.unregisterCallback(packageCallback) }
    }

    /** 手动重新扫描标记：为 true 时 refresh 完成后发送数量 toast（C6-5）。 */
    private var pendingScanToast = false

    /** 手动触发重新扫描（设置弹窗"重新扫描应用"），完成后 toast 反馈。 */
    fun rescan() {
        pendingScanToast = true
        refresh()
    }

    /**
     * 扫描并发保护（C1-1）：rescan 高频触发或刷新中途再次进入时，
     * 串行化阶段二协程，避免多份 loadApps() 并发写入 _apps 造成焦点错位/数据竞态。
     *
     * R14 增强：添加 [pendingRefresh] 队列，不在刷新中时直接丢弃（用户手动 rescan 排队等待）。
     */
    @Volatile
    private var refreshInFlight = false
    /** R14：当一次刷新进行中时标记"需要再刷"，当前完成后自动触发新一轮。 */
    @Volatile
    private var pendingRefresh = false

    fun refresh() {
        if (refreshInFlight) {
            // R14：不静默丢弃，排队等待当前刷新完成后自动重刷
            pendingRefresh = true
            Log.d("ReverieVM", "refresh(): 已有扫描进行中，标记 pendingRefresh")
            return
        }
        refreshInFlight = true
        _isScanning.value = true
        _queryAllGranted.value = isQueryAllPackagesGranted(appContext)

        // 阶段一 + 阶段二统一在协程内串行执行：
        // 阶段一读磁盘缓存移至 IO 线程（R14 真正落地，原实现仍在主线程），
        // 且 runCatching 兜底——缓存文件损坏时跳过占位直接全量加载，不再让 ViewModel 构造崩溃。
        viewModelScope.launch {
            // 阶段一：快速启动路径。优先加载磁盘缓存（不含图标），
            // 让 UI 立即渲染占位列表并退出开屏动画，后台并行刷新真实数据。
            val cached = runCatching { withContext(Dispatchers.IO) { AppCache.load(appContext) } }
                .onFailure { Log.w("ReverieVM", "AppCache 读取失败，跳过占位阶段: ${it.message}") }
                .getOrNull()
            if (cached != null && _apps.value.isEmpty()) {
                // 从缓存重建轻量 AppModel（共享占位图标 + 零使用时长）；构建放 Default 线程
                val placeholderApps = withContext(Dispatchers.Default) {
                    val overrides = categoryOverrides.overrides.value
                    val todayMap = usageStatsRepo.getTodayMap()
                    cached.map { c ->
                        AppModel(
                            packageName = c.packageName,
                            className = c.className,
                            label = c.label,
                            icon = placeholderIcon(),
                            isGame = c.isGame,
                            category = c.category,
                            categoryText = overrides[c.packageName] ?: categoryLabel(c.category),
                            firstInstallTime = c.firstInstallTime,
                            installed = c.installed,
                            lastUsedTime = c.lastUsedTime,
                            versionName = c.versionName,
                            todayUsage = todayMap[c.packageName] ?: 0L
                        )
                    }
                }
                _apps.value = sortApps(placeholderApps)
                // 开屏保持可见（_isBooting 仍为 true），由阶段二完成后关闭。
                // 用户优先看到开屏动画，而非灰色占位列表，体验更一致。

                // R14：阶段一即预置焦点（基于缓存的活跃应用），避免列表渲染后无焦点项。
                if (_focusedPackage.value == null && placeholderApps.isNotEmpty()) {
                    val cachedFocus = placeholderApps.firstOrNull { it.lastUsedTime > 0L }
                        ?: placeholderApps.firstOrNull()
                    cachedFocus?.let { _focusedPackage.value = it.packageName }
                }
            }

            // 阶段二：后台完整加载（真实图标 + 安装时间 + 最新数据），完成后替换并写回缓存
            try {
                val oldApps = _apps.value
                // 一次枚举所有应用（含系统），分别提取
                val allLoaded = repository.loadApps(true)
                val showSystem = settings.showSystemApps
                val rawApps = if (showSystem) allLoaded else allLoaded.filter { !it.isSystem }
                val sysPkgs = allLoaded.filter { it.isSystem }.map { it.packageName }.toSet()
                _systemPackages.value = sysPkgs
                // 应用 AI 分类映射覆盖
                val overrides = categoryOverrides.overrides.value
                val apps = rawApps.map {
                    it.copy(categoryText = overrides[it.packageName] ?: it.categoryText)
                }
                // 真实"上次使用时间"来自系统 UsageStats（一次查询取 lastTimeUsed 映射），
                // 避免仅凭时长猜测而把时间写成"当前"导致全部显示"刚刚"。
                val lastUsedMap = queryLastUsedMap()
                // 今日时长映射（避免额外 queryUsageStats）
                val todayMap = usageStatsRepo.getTodayMap()
                val merged = apps.map { app ->
                    val lastUsed = lastUsedMap[app.packageName] ?: 0L
                    app.copy(
                        lastUsedTime = if (lastUsed > 0L) lastUsed else app.lastUsedTime,
                        todayUsage = todayMap[app.packageName] ?: 0L
                    )
                }
                _apps.value = sortApps(merged)
                AppCache.save(appContext, merged)
                // 图标已在 AppRepository.safeIcon 中自动写入 IconCache 磁盘缓存
                _todayUsageMap.value = todayMap
                // 分类计数（精选计数须在 rebuildFeatured 之后取最新值，见下方补充）
                val counts = mutableMapOf(
                    "全部" to merged.size,
                    "收藏" to favorites.value.size
                )
                merged.forEach { a ->
                    val c = a.categoryText
                    counts[c] = (counts[c] ?: 0) + 1
                }
                val cats = listOf("全部") + merged.map { it.categoryText }.distinct()
                _filterChips.value = cats
                val wlInstalled = apps.any {
                    it.packageName in GameWhitelist.packages && it.installed
                }
                _whitelistFeaturedAvailable.value = wlInstalled
                val pinned = mutableListOf<String>()
                pinned.add("全部")
                if (wlInstalled) pinned.add("精选")
                if (favorites.value.isNotEmpty()) pinned.add("收藏")
                val rest = cats.drop(1)
                _categoryTabs.value = pinned + rest
                if (_selectedCategory.value != "精选" &&
                    _selectedCategory.value != "收藏" &&
                    _selectedCategory.value != "全部" &&
                    _selectedCategory.value !in cats
                ) {
                    _selectedCategory.value = "精选"
                }
                if (!wlInstalled && _selectedCategory.value == "精选") {
                    _selectedCategory.value =
                        if (favorites.value.isNotEmpty()) "收藏" else "全部"
                }
                val allGames = apps.filter {
                    it.isGame || GameWhitelist.isEntertainment(it.category) ||
                        it.packageName in GameWhitelist.packages
                }
                rebuildFeatured(allGames)
                // 精选角标须在 rebuildFeatured 之后取最新 _featured，避免计数陈旧（少 1）
                counts["精选"] = _featured.value.size
                _categoryCounts.value = counts
                _games.value = allGames.filter { it.packageName !in featuredPackages() }
                // 最近游玩：延迟到后台并行加载，不阻塞主列表
                launch {
                    val recent = recentsRepo.loadRecent()
                    _recents.value = recent.filter { r ->
                        allGames.any { it.packageName == r.packageName }
                    }
                }
                if (pendingScanToast) {
                    pendingScanToast = false
                    _toast.tryEmit("已扫描 ${merged.size} 个应用")
                }
                // 卸载当前焦点应用时，聚焦到原同位置的下一项
                val oldFocused = _focusedPackage.value
                val stillThere = apps.any { it.packageName == oldFocused }
                if (oldFocused != null && !stillThere) {
                    val idx = oldApps.indexOfFirst { it.packageName == oldFocused }.coerceAtLeast(0)
                    _focusedPackage.value =
                        apps.getOrNull(idx)?.packageName ?: _featured.value.firstOrNull()?.packageName
                }
                if (_focusedPackage.value == null) {
                    _focusedPackage.value = _featured.value.firstOrNull()?.packageName
                        ?: settings.lastFocused.takeIf { pkg -> apps.any { it.packageName == pkg } }
                        ?: apps.firstOrNull()?.packageName
                }
                if (_focusedPackage.value == null && _focusedChip.value == -1) {
                    _focusedChip.value = 0
                }
                _isScanning.value = false
                _isBooting.value = false
            } catch (e: Exception) {
                Log.e("ReverieVM", "阶段二加载异常，已兜底恢复", e)
                _isScanning.value = false
                _isBooting.value = false
                _toast.tryEmit("部分应用加载异常：${e.localizedMessage ?: "未知错误"}")
            } finally {
                _realReady.value = true
                refreshInFlight = false
                // R14：如果 pendingRefresh 已标记（rescan 排队），立即触发新一轮刷新
                if (pendingRefresh) {
                    pendingRefresh = false
                    refresh()
                }
            }
            // 使用时长增量同步（跨天正确归属）
            runCatching { usageStatsRepo.sync() }
        }
    }

    /**
     * 一次性查询系统 UsageStats，返回各包名 -> 上次使用时间（lastTimeUsed, ms）。
     * 未授权使用情况访问时返回空 Map（此时列表"上次"字段显示空，由 UI 引导开启）。
     * 仅取近期一段区间（近 30 天）即可覆盖绝大多数真实记录，避免全量扫描开销。
     */
    private fun queryLastUsedMap(): Map<String, Long> {
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val now = System.currentTimeMillis()
        val start = now - 30L * 24 * 60 * 60 * 1000
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, now)
        }.getOrNull() ?: return emptyMap()
        val map = mutableMapOf<String, Long>()
        for (s in stats) {
            val pkg = s.packageName ?: continue
            val t = s.lastTimeUsed
            // 取区间内各包最新一次使用时间
            if (t > (map[pkg] ?: 0L)) map[pkg] = t
        }
        return map
    }

    /** 网格列数，由 UI 在布局完成后写入，供手柄方向键计算焦点位置。 */
    @Volatile
    var gridColumns = 1

    /**
     * 手柄方向键导航：数据驱动更新焦点，**不依赖 Compose 原生焦点链**。
     *
     * 当前 UI 采用横排详细列表（LazyColumn，每行一个应用），方向键语义：
     * - UP / DOWN：在列表中上下移动焦点
     * - LEFT / RIGHT：列表内已无相邻项，保留当前焦点
     */
    fun moveFocus(dir: Direction) {
        // 进入应用区时清除分类 chip 焦点（C2-4）
        if (_focusedChip.value != -1) _focusedChip.value = -1
        val list = currentVisibleList()
        if (list.isEmpty()) {
            Log.d("ReverieFocus", "moveFocus($dir): visible list empty, skip")
            return
        }

        var idx = list.indexOfFirst { it.packageName == _focusedPackage.value }
        if (idx < 0) idx = 0
        val from = list[idx].packageName

        val next = when (dir) {
            Direction.UP -> (idx - 1).coerceAtLeast(0)
            Direction.DOWN -> (idx + 1).coerceAtMost(list.lastIndex)
            Direction.LEFT, Direction.RIGHT -> idx // 单列无邻居
        }

        if (next in list.indices) {
            _focusedPackage.value = list[next].packageName
            Log.d("ReverieFocus", "moveFocus($dir): $from -> ${list[next].packageName} (idx=$next)")
        }
    }

    /**
     * 计算当前 UI 实际展示的应用列表（与 HomeScreen 可见列表保持一致）。
     * 分类为"精选/最近/收藏"时返回对应子集，否则应用搜索 + 分类过滤。
     * 全工程唯一实现，[computeVisibleList] 仅是其被私有方法调用的别名，避免两处漂移。
     */
    fun currentVisibleList(): List<AppModel> = computeVisibleList()

    /** 缓存恢复时复用同一张占位图标，避免为每个 App 各建一张 Bitmap。 */

    /** 精选包名集合 = 白名单 ∪ 用户收藏。 */
    private fun featuredPackages(): Set<String> =
        (GameWhitelist.packages + favorites.value).toSet()

    /** 根据白名单 + 收藏重建精选区（已安装且去重，白名单优先排序）。 */
    private fun rebuildFeatured(allGames: List<AppModel> = _apps.value) {
        val games = allGames.filter {
            it.isGame || GameWhitelist.isEntertainment(it.category) ||
                it.packageName in GameWhitelist.packages
        }
        val pinned = featuredPackages()
        val priority = pinned.mapNotNull { pkg ->
            games.firstOrNull { it.packageName == pkg && it.installed }
        }.sortedBy { pkg ->
            val idx = GameWhitelist.packages.indexOf(pkg.packageName)
            if (idx < 0) GameWhitelist.packages.size else idx
        }
        _featured.value = priority
    }



    /** 共享灰色占位图标（懒加载单例）：阶段一为每个应用建 AppModel 时复用同一张，N 应用不再产生 N 张位图。 */
    private val sharedPlaceholderIcon: ImageBitmap by lazy {
        val size = 48
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(PLACEHOLDER_ICON_COLOR)
        // ⚠️ asImageBitmap() 直接包装同一 Bitmap，不可 recycle（N1）
        bmp.asImageBitmap()
    }

    private fun placeholderIcon(): ImageBitmap = sharedPlaceholderIcon

    fun pushEvent(event: GamepadEvent) {
        _events.tryEmit(event)
    }

    /**
     * 手柄事件入口（仅 [cn.mocabolka.run.gamepad.GamepadManager] 调用）。
     *
     * 任一经过 [cn.mocabolka.run.gamepad.GamepadManager.isTrustedGamepadDevice] 验证的真手柄事件，
     * 都证明手柄当前在线，应立刻点亮 [gamepadConnected]（即 [cn.mocabolka.run.ui.theme.LocalShowFocusIndicators]），
     * 使焦点框（[cn.mocabolka.run.ui.theme.focusBorder] / AppTile / AppListItem 的选中描边）显示。
     *
     * 之所以需要这层驱动：单一可信源 [gamepadConnected] 目前同时由 [cn.mocabolka.run.gamepad.GamepadDetector]
     * 的热插拔广播流喂养。若该广播未及时触发或漏报（典型场景：应用启动后手柄才接入），
     * 广播流不会更新，[gamepadConnected] 会一直停在初始值 false，于是「左摇杆能正常导航、A 能进应用、
     * 但焦点框不显示」——逻辑全通，唯独 UI 焦点框缺失。
     * 任一手柄事件本身即是最可靠的设备在线证据，凭真实事件点亮可彻底消除该死区。
     * 断开复位仍由 GamepadDetector 流负责（设备移除时广播置 false）。
     */
    fun pushGamepadEvent(event: GamepadEvent) {
        if (!_gamepadConnected.value) _gamepadConnected.value = true
        _events.tryEmit(event)
    }

    // ── 分类筛选 ───────────────────────────────────────
    /** 设置选中的分类（鼠标/触摸点击 chip）。 */
    fun setCategoryFilter(cat: String) {
        _selectedCategory.value = cat
    }

    /** 清除分类栏手柄焦点（点击 chip 或进入应用区时调用）。 */
    fun clearChipFocus() {
        _focusedChip.value = -1
    }

    /** 分类栏导航（手柄 LEFT/RIGHT 切换 Tab 时进入，UP/DOWN 离开并应用分类）。
     *  现方向键 LEFT/RIGHT 直接走 [cycleCategory]，不再调用此方法；保留给鼠标聚焦后的按键场景。 */
    fun handleNavigate(dir: Direction) {
        val tabs = _categoryTabs.value
        val fc = _focusedChip.value
        when {
            fc < 0 -> {
                if (dir == Direction.LEFT || dir == Direction.RIGHT) {
                    val idx = tabs.indexOf(_selectedCategory.value).coerceAtLeast(0)
                    _focusedChip.value = idx
                } else {
                    moveInCurrentList(if (dir == Direction.UP) -1 else 1)
                }
            }
            dir == Direction.LEFT -> _focusedChip.value = (fc - 1).coerceAtLeast(0)
            dir == Direction.RIGHT -> _focusedChip.value = (fc + 1).coerceAtMost(tabs.lastIndex)
            else -> {
                _selectedCategory.value = tabs[fc]
                _focusedChip.value = -1
                val list = computeVisibleList()
                if (list.isNotEmpty()) _focusedPackage.value = list.first().packageName
            }
        }
    }

    // ── 搜索状态 ───────────────────────────────────────
    // 注：主页「下拉搜索浮层」已移除，搜索统一走 SEARCH Tab（SearchTabContent）。
    // _searchActive 现在表示「是否处于搜索 Tab（搜索模式）」，供 MainActivity 接管手柄导航。
    fun enterSearch() {
        _searchActive.value = true
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** 退出搜索模式：清词 + 关闭。由 HomeScreen 在 SEARCH Tab 内按 B 时调用。 */
    fun closeSearch() {
        _searchActive.value = false
        _query.value = ""
    }

    /** 仅切换搜索模式标志（不清搜索词），用于 Tab 切换时同步手柄导航接管状态。 */
    fun setSearchActive(active: Boolean) {
        _searchActive.value = active
    }

    // ── 设置弹窗 / 排序 ───────────────────────────────
    fun setSettingsOpen(open: Boolean) {
        _settingsOpen.value = open
    }

    /**
     * 设置当前 Dialog 层级。由 HomeScreen 在 dialog 打开/关闭时调用，
     * MainActivity 收集后同步到 GamepadManager.dialogActive。
     * 调用者负责维护正确的栈顶值：
     * - dialog 打开时设为对应层级
     * - dialog 关闭时回退到 NONE 或前一个层级
     */
    fun setDialogLayer(layer: DialogLayer) {
        _dialogLayer.value = layer
    }

    fun setSortMode(mode: SortMode) {
        if (_sortMode.value == mode) return
        _sortMode.value = mode
        _apps.value = sortApps(_apps.value)
        // 即时反馈（C5-4）
        val label = when (mode) {
            SortMode.NAME -> "已按名称排序"
            SortMode.INSTALL_TIME -> "已按安装时间排序"
            SortMode.CATEGORY -> "已按分类排序"
            SortMode.LAST_USED -> "已按最近游玩排序"
        }
        _toast.tryEmit(label)
    }

    /** 应用当前排序方式。 */
    private fun sortApps(list: List<AppModel>): List<AppModel> = when (_sortMode.value) {
        SortMode.NAME -> list.sortedBy { it.label.lowercase() }
        SortMode.INSTALL_TIME -> list.sortedByDescending { it.firstInstallTime }
        SortMode.CATEGORY -> list.sortedWith(
            compareBy({ it.categoryText }, { it.label.lowercase() })
        )
        SortMode.LAST_USED -> list.sortedByDescending { it.lastUsedTime }
    }

    fun setFocused(packageName: String?) {
        val pkg = packageName?.takeIf { it.isNotBlank() }
        _focusedPackage.value = pkg
        if (pkg != null) settings.lastFocused = pkg
    }

    /** 切换指定包名的收藏状态（影响精选区置顶）。 */
    fun toggleFavorite(packageName: String) {
        favoritesRepo.toggle(packageName)
    }

    /** 切换当前焦点游戏的收藏状态。 */
    fun toggleFavoriteOfFocused() {
        _focusedPackage.value?.let { toggleFavorite(it) }
    }

    /** 根据当前选中的分类/搜索条件，计算当前 UI 实际展示的列表（与 HomeScreen.visibleList 一致）。 */
    private fun computeVisibleList(): List<AppModel> {
        val q = _query.value
        val cat = _selectedCategory.value
        val filtered = _apps.value.filter {
            (q.isBlank() || it.matchesSearch(q)) &&
                (cat == "全部" || cat == "精选" || cat == "最近" || cat == "收藏" ||
                    it.categoryText == cat)
        }
        return when (cat) {
            "精选" -> _featured.value
            "最近" -> _recents.value
            "收藏" -> _apps.value.filter { it.packageName in favorites.value }
            else -> filtered
        }
    }

    /** 在当前可见列表中按 [step]（+1/-1）移动焦点；方向键 UP/DOWN 专用。 */
    fun moveInCurrentList(step: Int) {
        if (_focusedChip.value != -1) _focusedChip.value = -1
        val list = computeVisibleList()
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.packageName == _focusedPackage.value }
            .let { if (it < 0) 0 else it }
        val newIdx = (idx + step).coerceIn(list.indices)
        val pkg = list[newIdx].packageName
        _focusedPackage.value = pkg
        Log.d("ReverieFocus", "moveInCurrentList($step): idx=$idx -> $newIdx pkg=$pkg")
    }

    /**
     * 任务 17/20：在搜索结果列表中移动焦点（无视分类筛选，与 HomeScreen.searchResults 一致）。
     */
    fun moveInSearchList(step: Int) {
        val q = _query.value
        val list = _apps.value.filter { q.isBlank() || it.matchesSearch(q) }
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.packageName == _focusedPackage.value }
            .let { if (it < 0) 0 else it }
        val newIdx = (idx + step).coerceIn(list.indices)
        _focusedPackage.value = list[newIdx].packageName
    }

    /** 肩键 / 方向键 LEFT/RIGHT 循环切换分类 Tab，并把焦点设到新分类列表的首项。 */
    fun cycleCategory(step: Int) {
        val tabs = _categoryTabs.value
        if (tabs.size <= 1) return
        val currentIdx = tabs.indexOf(_selectedCategory.value).coerceAtLeast(0)
        val nextIdx = (currentIdx + step).mod(tabs.size)
        _selectedCategory.value = tabs[nextIdx]
        _focusedChip.value = -1
        val list = computeVisibleList()
        if (list.isNotEmpty()) _focusedPackage.value = list.first().packageName
        Log.d("ReverieFocus", "cycleCategory($step): $currentIdx -> $nextIdx cat=${tabs[nextIdx]}")
    }

    /** 清空全部收藏（设置弹窗"清空收藏"，C4-5）。 */
    fun clearFavorites() {
        favoritesRepo.clear()
    }

    /** 强制停止指定应用（R11-7，跳转系统"应用信息"页，内含强制停止按钮）。 */
    fun forceStop(packageName: String) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null))
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            _toast.tryEmit("无法打开应用信息：${it.localizedMessage ?: "未知错误"}")
        }
    }

    /** 重置所有设置项到默认值（R11-10），并 toast 反馈。 */
    fun resetSettings() {
        settings.resetAll()
        // 重置后同步内存态
        _sortMode.value = SortMode.NAME
        _toast.tryEmit("已重置所有设置")
    }

    // ── 应用分类映射（导出 / 导入 / 清除） ─────────────────
    /**
     * 导出应用列表（含分类映射）到公共下载目录。
     * 文件顶部带 AI Prompt，每行含 应用名/包名/安装来源/系统应用/当前分类/AI分类，
     * 供 AI Agent 将应用归类到既有分类，回填后通过 [requestImportCategoryMapping] 导入。
     */
    fun exportCategoryMapping() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = _apps.value
            val text = CategoryMapping.buildExportText(apps, appContext.packageManager)
            val uri = CategoryMapping.exportToFile(appContext, text)
            if (uri != null) {
                _toast.tryEmit("已导出分类表：${uri.lastPathSegment}（用 AI 填充后点导入）")
                _shareFileUri.tryEmit(uri)
            } else {
                _toast.tryEmit("导出失败：无法写入下载目录")
            }
        }
    }

    /**
     * 请求导入分类映射：仅发出信号，由 UI 层唤出系统文件选择器（SAF），
     * 用户选定 json 文件后回调 [applyImportedCategoryMapping]。
     */
    fun requestImportCategoryMapping() {
        _importRequest.tryEmit(Unit)
    }

    /**
     * 应用用户通过系统文件选择器选定的分类映射文件（json）。
     * 解析失败或无有效条目时给出对应 toast 反馈。
     */
    fun applyImportedCategoryMapping(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = CategoryMapping.importFromUri(appContext, uri)
            if (map == null) {
                _toast.tryEmit("未解析到有效分类（请选择导出的 reverie_categories.json）")
                return@launch
            }
            categoryOverrides.applyOverrides(map)
            // 回主线程触发刷新（刷新内部已读最新 override）
            kotlinx.coroutines.withContext(Dispatchers.Main) { refresh() }
            _toast.tryEmit("已导入 ${map.size} 条分类映射，正在应用…")
        }
    }

    /** 清除全部分类映射，恢复系统原始分类，并刷新。 */
    fun clearCategoryMapping() {
        categoryOverrides.clear()
        refresh()
        _toast.tryEmit("已清除全部分类映射")
    }

    fun launchApp(app: AppModel) {
        if (!app.installed) {
            _toast.tryEmit("应用未安装")
            return
        }
        _launching.value = true
        val ok = runCatching { launcher.launch(app) }.getOrDefault(false)
        if (!ok) {
            _launching.value = false
            _toast.tryEmit("无法启动 ${app.label}")
        } else {
            // 启动成功：短暂保留遮罩，随后由目标应用接管界面；若失败停留则自动解除
            // R11-6：增加 3s 超时兜底，防止目标应用未正确接管导致遮罩卡死
            viewModelScope.launch {
                kotlinx.coroutines.delay(400.milliseconds)
                _launching.value = false
            }
            viewModelScope.launch {
                kotlinx.coroutines.delay(3.seconds)
                if (_launching.value) _launching.value = false
            }
            // 行为设置：启动后不保留 Reverie 进程 —— 发射退出信号让主页销毁本任务
            if (settings.killAfterLaunch) _exitAfterLaunch.tryEmit(Unit)
        }
    }

    /**
     * 强制就绪：SplashScreen 超时保护触发时调用。
     * 确保 _realReady 置为 true，避免 refresh() 阶段二协程异常导致永久白屏。
     */
    fun forceReady() {
        if (!_realReady.value) {
            _realReady.value = true
            _isBooting.value = false
        }
    }

    fun launchFocused() {
        val pkg = _focusedPackage.value
        if (pkg == null) {
            _toast.tryEmit("未选中应用")
            Log.d("ReverieFocus", "launchFocused: no focused package")
            return
        }
        val app = _apps.value.firstOrNull { it.packageName == pkg && it.installed }
        if (app == null) {
            _toast.tryEmit("未找到可启动应用: $pkg")
            Log.d("ReverieFocus", "launchFocused: installed app not found for $pkg")
            return
        }
        Log.d("ReverieFocus", "launchFocused: ${app.label} (${app.packageName})")
        launchApp(app)
    }

    /** 刷新 QUERY_ALL_PACKAGES 授权状态，供 UI 在设置页返回后调用。 */
    fun refreshQueryAllPermission() {
        _queryAllGranted.value = isQueryAllPackagesGranted(appContext)
    }

    /** 某应用的历史累计游玩时长（ms），来自 UsageStats 聚合；未授权/无记录返回 0。 */
    fun totalPlayMs(packageName: String): Long = usageStatsRepo.getTotal(packageName)

    /** 打开 Reverie 的应用信息页，用户可手动授予"应用列表权限访问"。 */
    fun openAppInfoForPermission() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", appContext.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure { _toast.tryEmit("无法打开应用信息页") }
    }

    companion object {
        /** 统计页"系统应用"聚合项的包名哨兵值（collapseSystemApps 不再追加，保留常量供兼容）。 */
        const val SYSTEM_AGGREGATE_PKG: String = "__reverie_system_aggregate__"
    }
}

/** 检查是否持有 QUERY_ALL_PACKAGES 权限（受限权限，需用户在设置中手动授予）。 */
private fun isQueryAllPackagesGranted(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES) ==
            PackageManager.PERMISSION_GRANTED
}



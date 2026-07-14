package cn.mocabolka.run.ui

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.mocabolka.run.compat.OrientationManager
import cn.mocabolka.run.compat.UsageStatsPermissionHelper
import cn.mocabolka.run.gamepad.DialogLayer
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.gamepad.GamepadDetector
import cn.mocabolka.run.gamepad.GamepadEvent
import cn.mocabolka.run.gamepad.NavigateSource
import cn.mocabolka.run.gamepad.RightStickScroll
import cn.mocabolka.run.gamepad.RightStickScrollPhysics
import cn.mocabolka.run.gamepad.Side
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.ui.components.AmbientBackground
import cn.mocabolka.run.ui.components.AppDetailPanel
import cn.mocabolka.run.ui.components.AppListItem
import cn.mocabolka.run.ui.components.DropdownDialog
import cn.mocabolka.run.ui.components.GamepadBottomHintBar
import cn.mocabolka.run.ui.components.GamepadVisible
import cn.mocabolka.run.ui.components.Hint
import cn.mocabolka.run.ui.components.HintGap
import cn.mocabolka.run.ui.components.InfoContent
import cn.mocabolka.run.ui.components.KeyBadge
import cn.mocabolka.run.ui.components.KeyToken
import cn.mocabolka.run.ui.components.LaunchOverlay
import cn.mocabolka.run.ui.components.NativeDatePickerDialog
import cn.mocabolka.run.ui.components.ReverieFilledButton
import cn.mocabolka.run.ui.components.SettingsConfirm
import cn.mocabolka.run.ui.components.SettingsControlBridge
import cn.mocabolka.run.ui.components.SettingsPage
import cn.mocabolka.run.ui.components.SplashOverlay
import cn.mocabolka.run.ui.components.StatusBar
import cn.mocabolka.run.ui.components.wrapFocusBorder
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.ReverieToast
import cn.mocabolka.run.ui.theme.SurfaceTokens
import cn.mocabolka.run.ui.theme.focusBorder
import cn.mocabolka.run.ui.theme.waterfallSafePadding
import cn.mocabolka.run.viewmodel.HomeViewModel
import cn.mocabolka.run.viewmodel.SortMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 主屏布局（21:9 横屏大屏 / 桌面 Launcher）
 *
 * 设计理念：沉浸式主列表 + 浮层详情
 * - 顶部紧凑条：问候 + 时间（左侧） / 应用数 + 搜索（右侧）
 * - 中部全宽主列表：每行一个应用
 * - 焦点变化时，底部弹出 HUD 详情卡（1.5s 后自动隐藏，可手动锚定）
 * - 底部手柄提示栏
 *
 * 21:9 宽屏下不再使用固定宽度的左侧详情面板，避免挤压列表空间。
 * 21:9 的横向空间全部交给主列表，体现横屏扫描效率。
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onExit: () -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    val featured by viewModel.featured.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val darkMode by viewModel.settings.darkModeFlow.collectAsState()
    val searchActive by viewModel.searchActive.collectAsState()
    // 虚拟软键盘是否可见：搜索框获焦时为真、失焦时为假（用于 B 键区分"关键盘/回主页"）
    var keyboardVisible by remember { mutableStateOf(false) }
    val query by viewModel.query.collectAsState()
    val focusedPackage by viewModel.focusedPackage.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val categoryTabs by viewModel.categoryTabs.collectAsState()
    val focusedChip by viewModel.focusedChip.collectAsState()
    val launching by viewModel.launching.collectAsState()
    val isBooting by viewModel.isBooting.collectAsState()
    val showOverlay by viewModel.showOverlay.collectAsState()
    val badges by viewModel.badges.collectAsState()
    val showBadges by viewModel.settings.showBadgesFlow.collectAsState()
    val badgeOf: (String) -> Int = { pkg -> if (showBadges) badges[pkg] ?: 0 else 0 }
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    // R13：预聚合用量 Map（主列表/详情 O(1) 查表，避免逐行全量扫描）
    val todayUsageMap by viewModel.todayUsageMap.collectAsState()
    val weeklyUsageMap by viewModel.weeklyUsageMap.collectAsState()
    // 穿透背景：主界面展示系统桌面壁纸
    val wallpaperBehind by viewModel.settings.wallpaperBehindFlow.collectAsState()
    val showSystemApps by viewModel.settings.showSystemAppsFlow.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val queryAllGranted by viewModel.queryAllGranted.collectAsState()
    // 莫奈取色（Material You）：API 31+ 自动从系统壁纸提取色板（上移以避免在事件循环中被前向引用）
    val useMonet by viewModel.settings.useMonetFlow.collectAsState()
    // 视觉动效开关（R12 循环新增）：动态背景 / 玻璃拟态 / 减少动态效果
    val dynamicBackground by viewModel.settings.dynamicBackgroundFlow.collectAsState()
    val glassSurface by viewModel.settings.glassSurfaceFlow.collectAsState()
    val reduceMotion by viewModel.settings.reduceMotionFlow.collectAsState()
    // 挖孔屏适配开关（响应式）：控制主体是否加 displayCutout padding 绕开摄像头；
    // 必须 collectAsState，否则切换开关后 HomeScreen 主体 padding 不重组，表现为"开关用不了"。
    val cutoutAdapt by viewModel.settings.cutoutAdaptFlow.collectAsState()
    // 减少动态效果（直接读取 SettingsRepository.effectiveReduceMotion）
    val effectiveReduceMotion by viewModel.settings.reduceMotionFlow.collectAsState()
    // 动效开关直接使用用户设置值（节能模式已移除，不再聚合）
    val effectiveDynamicBackground = dynamicBackground
    val effectiveGlass = glassSurface
    // R13：应用可见性（Lifecycle 驱动），用于暂停氛围背景等空闲动画以省电
    val appVisible by rememberAppVisible()
    // 强制旋屏模式（全局生效，由 OrientationLockService 前台服务维持）
    val orientationMode by viewModel.settings.orientationModeFlow.collectAsState()


    val focusedApp = apps.find { it.packageName == focusedPackage }
    val favoriteApps = apps.filter { it.packageName in favorites }

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val haptics = remember { Haptics(context) }
    // 注：消息展示已改为"覆盖式即时 toast"（见 toastMsg），不再依赖 SnackbarHostState。
    // 搜索页专用的 FocusRequester，与主页 SearchFieldOrList 中的 searchFocusRequester 隔离，
    // 避免同一个 FocusRequester 绑定两个 TextField 导致 requestFocus 失效。
    val searchTabFocusRequester = remember { FocusRequester() }
    /** 搜索页焦点目标状态（从 ViewModel 读取，替代本地 searchFocusOnInput var）。 */
    val searchFocusTarget by viewModel.searchFocusTarget.collectAsState()

    // 屏幕宽度判定（21:9 宽屏 vs 中等屏）：用于事件循环中决定方向键行为（十字右键是否切到右侧面板）
    // Compose 1.8+ 推荐使用 LocalWindowInfo.containerSize 替代 Configuration.screenWidthDp
    val isWideScreen = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 720.dp }

    // 手柄连接状态：用于控制上下文键位角标（LB/RB/X/Menu）的显隐
    val gamepadConnected by GamepadDetector.gamepadConnectedFlow(context)
        .collectAsState(initial = GamepadDetector.isGamepadConnected())

    // 顶部 Tab 模式：主界面 / 设置 平行切换，不再是浮层
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    // 统计页排行 LazyColumn 滚动状态（右摇杆 / 焦点滚动统一驱动）
    val statsListState = rememberLazyListState()
    // 统计页左栏（总计/图表）滚动状态：宽屏双栏时由右摇杆驱动滚动（任务 14/15）
    val statsLeftScrollState = rememberScrollState()
    var statsPeriod by remember { mutableStateOf(cn.mocabolka.run.ui.components.StatsPeriod.DAILY) }
    // 统计页日期锚点（ms，0:00 当天）：日/周/月/年视图各自的选定日期，默认今天
    var statsAnchorMs by remember { mutableStateOf(System.currentTimeMillis()) }
    // 统计页日期选择器开关（LS 键 / 本页按钮驱动）
    var statsDatePickerOpen by remember { mutableStateOf(false) }
    // 统计页日期选择器手柄事件桥接（打开时由全局事件循环路由手柄事件）
    val statsDatePickerBridge = remember {
        mutableStateOf(cn.mocabolka.run.ui.components.StatsDatePickerBridge())
    }
    // 统计页焦点侧：跨栏逻辑已移除（2026-07-13），统计页恒用 LEFT_LIST 语义
    // （左栏=应用排行由左摇杆焦点驱动；右栏=总览总计由右摇杆滚动，互不越界）。
    // 此变量保留仅为兼容既有声明，导航逻辑恒走 LEFT_LIST 分支。
    var statsFocusSide by remember { mutableStateOf(FocusSide.LEFT_LIST) }
    // 任务 16：统计页排行列表焦点索引（左摇杆上下移动焦点）
    var statsFocusIndex by remember { mutableStateOf(0) }
    // 统计页实际排行条目数（由 StatsScreen 上报），用于限制焦点上限避免空行焦点
    var statsEntryCount by remember { mutableStateOf(20) }
    // 切换到 STATS Tab 时重置焦点侧 / 焦点到左栏首项
    LaunchedEffect(currentTab) {
        if (currentTab == MainTab.STATS) {
            statsFocusSide = FocusSide.LEFT_LIST
            statsFocusIndex = 0
        }
        // 进入 / 离开 SEARCH Tab 时同步搜索模式标志（供 MainActivity 接管手柄导航），不清词
        viewModel.setSearchActive(currentTab == MainTab.SEARCH)
    }
    // 切换统计周期时排行内容已变，焦点归零
    LaunchedEffect(statsPeriod) { statsFocusIndex = 0 }
    // 当前 Tab 可变引用：右摇杆滚动引擎在 LaunchedEffect(Unit) 中捕获首帧值会过期
    val currentTabRef = remember { mutableStateOf(currentTab) }
    LaunchedEffect(currentTab) { currentTabRef.value = currentTab }
    // 右摇杆平滑加速滚动引擎共享状态（任务 14）
    val rightStickY = remember { mutableStateOf(0f) }
    val lastRightStickAt = remember { mutableStateOf(0L) }
    // 设置页下拉弹窗的归一化滚动桥接（R1）：dropdownListState 在下方 Column scope 才声明，
    // 引擎在此之前，故用可变引用提前占位，dropdown 渲染时绑定 scrollBy，引擎按帧调用。
    val dropdownScrollByPx = remember { mutableStateOf<(suspend (Float) -> Unit)?>(null) }
    // 设置页 InfoDialog 正文滚动桥接：infoScrollState 在下方 Column scope 才声明，
    // 引擎在此之前，故用可变引用提前占位，InfoDialog 渲染时绑定 scrollBy，引擎按帧调用。
    val infoScrollByPx = remember { mutableStateOf<(suspend (Float) -> Unit)?>(null) }
    val sortMode by viewModel.sortMode.collectAsState()
    // 设置页焦点行索引：-3 = 重新扫描按钮 / 0..N = 设置项（Tab 模式无"返回"行）
    var settingsFocusRow by remember { mutableStateOf(0) }
    // 任务 33：手柄 X 键触发设置项信息弹窗的中转 state（HomeScreen 写、SettingsPage 读）
    val settingsInfoTrigger = remember { mutableStateOf<Int?>(null) }
    // 优化 3：设置页可聚焦项上限（由 SettingsPage 上报，避免焦点越界）
    var settingsMaxRow by remember { mutableStateOf(18) }
    // 设置页手柄控制桥：HomeScreen 全局事件分发器据此驱动 A 键激活 / 下拉翻页（任务 1 / 3 / 4）
    val settingsControl = remember {
        mutableStateOf(SettingsControlBridge())
    }

    // 当前 Dialog 层级：驱动事件循环优先路由到对应 dialog 处理器。
    // 所有 dialog 打开/关闭操作必须同步更新此状态（与 viewModel.setDialogLayer 联动）。
    // 必须定义在 LaunchedEffect(currentTab) 之前，因为该 Effect 引用此变量。
    var currentDialogLayer by remember { mutableStateOf<DialogLayer>(DialogLayer.NONE) }

    /**
     * 统一设置 dialog 层级，同时更新本地状态和 ViewModel 状态。
     * 调用方在 dialog 打开/关闭时调用此函数，确保 HomeScreen 事件循环与
     * MainActivity/GamepadManager 的 dialogActive 状态始终同步。
     */
    fun setDialogLayer(layer: DialogLayer) {
        currentDialogLayer = layer
        viewModel.setDialogLayer(layer)
    }

    // 切换到设置 Tab 时，重置焦点到首项（深色模式），关闭子页面
    // 切换到其它 Tab 时，强制关闭所有 dialog（防止 dialog 状态跨 Tab 泄漏）
    LaunchedEffect(currentTab) {
        if (currentTab == MainTab.SETTINGS) {
            settingsFocusRow = 0
        } else {
            // 非设置页：关闭所有可能残留的 dialog 状态
            statsDatePickerOpen = false
            if (currentDialogLayer != DialogLayer.NONE) {
                setDialogLayer(DialogLayer.NONE)
            }
        }
        viewModel.setSettingsOpen(currentTab == MainTab.SETTINGS)
    }

    // 详情浮层：可手动锚定（按详情/选择等），或 1.8s 自动消失
    var detailPinned by remember { mutableStateOf(false) }

    // 竖屏（非宽屏）底部详情抽屉开合：点击列表项或按 A 展开，按 B / 点击列表外收起。
    // 与横屏 focusSide/detailPinned 解耦，避免抽屉常驻遮挡列表。
    var portraitDrawerOpen by remember { mutableStateOf(false) }
    // 切换分类 / 搜索 / Tab 时收起竖屏抽屉，避免残留在新内容上方。
    LaunchedEffect(selectedCategory, query, currentTab) { portraitDrawerOpen = false }

    // 触摸退出二次确认：true 时显示底部气泡"再次返回退出 Reverie"，再次触发退出。
    var touchExitConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(touchExitConfirm) {
        if (touchExitConfirm) {
            // C2-2：延长到 4s，给用户更充裕的反应时间（原 3s 偏紧）
            delay(4.seconds)
            touchExitConfirm = false
        }
    }

    // 主界面焦点侧（21:9 宽屏适配）：十字右键切到右侧详情面板 / 十字左键切回主列表
    var focusSide by remember { mutableStateOf(FocusSide.LEFT_LIST) }
    // 详情面板内按钮焦点索引：-1=无 / 0=启动 / 1=收藏 / 2=信息 / 3=卸载 / 4=强制停止
    var focusedDetailButton by remember { mutableStateOf(0) }
    // 进入右面板时请求首个按钮获焦，确保手柄可见焦点（C2-3）
    LaunchedEffect(focusSide) {
        if (focusSide == FocusSide.RIGHT_PANEL) {
            focusedDetailButton = 0
        }
    }

    val allListState = rememberLazyListState()
    val featuredListState = rememberLazyListState()
    val recentsListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()
    // 设置页列表滚动状态（右摇杆在设置页时滚动此列表）
    val settingsListState = rememberLazyListState()
    // 搜索页列表滚动状态（右摇杆在搜索页时滚动此列表）。始终基于全部应用，不跟随主页分类。
    val searchListState = rememberLazyListState()
    // 搜索结果：始终在【全部应用】中匹配关键字，忽略主页当前分类筛选。
    // 与 visibleList 解耦，确保搜索页范围恒定（用户要求）。
    // 排序规则：前缀匹配 > 标签包含匹配 > 包名匹配，最相关的结果在前。
    val searchResults = remember(apps, query) {
        if (query.isBlank()) {
            // 空搜索词显示全部应用（按已有排序）
            apps
        } else {
            val q = query.lowercase()
            val matched = apps.filter {
                it.label.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true) ||
                    it.categoryText.contains(q, ignoreCase = true)
            }
            // 按相关性排序：前缀匹配优先
            matched.sortedByDescending { app ->
                val label = app.label.lowercase()
                when {
                    label == q -> 100
                    label.startsWith(q) -> 80
                    label.contains(q) -> 60
                    app.packageName.lowercase().contains(q) -> 40
                    app.categoryText.lowercase().contains(q) -> 20
                    else -> 0
                }
            }
        }
    }
    // 搜索页独立焦点包名：与主页 _focusedPackage 完全隔离，避免两页焦点互相串扰。
    // 进入搜索页 / 搜索词变化时重置到结果首项，确保默认（空词）场景也有明确焦点可手柄导航。
    var searchFocusedPackage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentTab, query) {
        if (currentTab == MainTab.SEARCH) {
            searchFocusedPackage = searchResults.firstOrNull()?.packageName
        }
    }
    // 右侧详情面板（宽屏）滚动状态：焦点在右栏时由右摇杆驱动滚动（任务 5 修复）
    val detailScrollState = rememberScrollState()
    val currentListState = when (selectedCategory) {
        "精选" -> featuredListState
        "最近" -> recentsListState
        "收藏" -> favoritesListState
        else -> allListState
    }
    // 当前列表状态的可变引用：事件循环在 LaunchedEffect(Unit) 中捕获的是首帧的
    // currentListState，分类切换后引用会过期导致右摇杆滚错列表。用 ref 同步最新值。
    val currentListStateRef = remember { mutableStateOf(currentListState) }
    LaunchedEffect(currentListState) { currentListStateRef.value = currentListState }

    // 主页列表仅按分类筛选，完全不受搜索关键字影响（搜索页独立隔离）。
    val filtered = remember(apps, selectedCategory) {
        apps.filter {
            (selectedCategory == "全部" ||
                selectedCategory == "精选" || selectedCategory == "最近" || selectedCategory == "收藏" ||
                it.categoryText == selectedCategory)
        }
    }

    val visibleList = remember(filtered, featured, recents, favorites, selectedCategory) {
        when (selectedCategory) {
            "精选" -> featured
            "最近" -> recents
            "收藏" -> favoriteApps
            else -> filtered
        }
    }

    // 抑制标志：右摇杆滚动同步 focusedPackage 时，列表位置已由引擎滚到位，
    // 须跳过下面「focusedPackage 变化 → animateScrollToItem」的反向滚动，
    // 否则会把列表拉回焦点项，抵消右摇杆滚动（主页焦点跟随失效的根因）。
    // 与子页面 SubPageScaffold.suppressAutoScroll 同一归一化策略。
    val suppressListAutoScroll = remember { mutableStateOf(false) }
    LaunchedEffect(focusedPackage, selectedCategory) {
        if (suppressListAutoScroll.value) { suppressListAutoScroll.value = false; return@LaunchedEffect }
        val i = visibleList.indexOfFirst { it.packageName == focusedPackage }
        if (i >= 0) scope.launch { currentListState.animateScrollToItem(i) }
    }

    // 归一化：统计页左栏排行焦点跟随滚动（需求5）。
    // 左摇杆上下移动 statsFocusIndex 后，必须把排行项滚动到可视区，
    // 否则焦点移出排行列表窗口（与详情面板同一类 bug）。
    LaunchedEffect(statsFocusIndex, currentTab) {
        if (currentTab == MainTab.STATS) {
            scope.launch { statsListState.animateScrollToItem(statsFocusIndex) }
        }
    }

    LaunchedEffect(selectedCategory, query) {
        if (visibleList.isNotEmpty()) {
            val i = visibleList.indexOfFirst { it.packageName == focusedPackage }
            if (i < 0) {
                viewModel.setFocused(visibleList.first().packageName)
            } else {
                currentListState.scrollToItem(i)
            }
        } else {
            viewModel.setFocused(null)
        }
    }

    // 扳机 LT/RT 防抖：记录每侧扳机是否已触发（锁定直到松开 < 0.1f 才解锁）
    val triggerLeftLock = remember { mutableStateOf(false) }
    val triggerRightLock = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            // ── 事件栈顶层分发：DialogLayer 非 NONE 时所有事件优先路由到 dialog ──
            // Trigger（LT/RT）事件在 GamepadManager 层已被 dialogActive 守卫屏蔽，
            // 此处兜底检查：即使有 Trigger 穿透，也直接 return（双保险）。
            if (currentDialogLayer != DialogLayer.NONE) {
                if (event is GamepadEvent.Trigger) return@collectLatest
                // ── 右摇杆归一化（R1）：dialog 打开时右摇杆同样只记录状态，
                //    由下方统一的「右摇杆平滑加速滚动引擎」根据 currentDialogLayer 分发到
                //    对应 dialog 的滚动目标（日期选择器网格 / 下拉列表），与主页/统计/搜索共用同一引擎，
                //    从而获得一致的「越滚越快」加速手感，杜绝固定步长的顿挫。
                if (event is GamepadEvent.RightStick) {
                    rightStickY.value = event.dy
                    lastRightStickAt.value = android.os.SystemClock.uptimeMillis()
                    return@collectLatest
                }
                when (currentDialogLayer) {
                    DialogLayer.STATS_DATE_PICKER -> {
                        when (event) {
                            is GamepadEvent.Navigate -> {
                                // 日期选择器同时接受 STICK 和 DPAD 来源（修复十字键无法导航）
                                statsDatePickerBridge.value.onMove(event.direction)
                            }
                            is GamepadEvent.Select -> statsDatePickerBridge.value.onConfirm()
                            is GamepadEvent.Back -> statsDatePickerBridge.value.onCancel()
                            // R9（2026-07-14）日期选择器打开时 LB/RB 翻月/翻年/翻区间（与统计页 LB/RB 切周期共用按键，
                            // 避免占用其它按键；用户在 dialog 中也能"不松手"快速翻到目标日期）。
                            is GamepadEvent.Shoulder -> {
                                val dir = if (event.side == Side.LEFT) -1 else 1
                                statsDatePickerBridge.value.onPage(dir)
                            }
                            else -> { /* 其它按键在 dialog 打开时不处理 */ }
                        }
                        return@collectLatest
                    }
                    DialogLayer.SETTINGS_DROPDOWN,
                    DialogLayer.SETTINGS_CONFIRM,
                    DialogLayer.SETTINGS_INFO -> {
                        handleSettingsEvent(
                            event = event,
                            currentRow = settingsFocusRow,
                            haptics = haptics,
                            onMove = { newRow -> settingsFocusRow = newRow },
                            onDismiss = {
                                currentTab = MainTab.HOME
                                setDialogLayer(DialogLayer.NONE)
                            },
                            onFavorite = { settingsInfoTrigger.value = settingsFocusRow },
                            maxRow = settingsMaxRow,
                            control = settingsControl.value,
                            dialogLayer = currentDialogLayer
                        )
                        return@collectLatest
                    }
                    DialogLayer.NONE -> { /* 不会走到这里 */ }
                }
            }

            // ── 实时焦点应用（修复 AXY 无响应根因）──
            // 注意：HomeScreen 顶层的 `focusedApp` 是普通 val（由 apps.find 计算），
            // 在 LaunchedEffect(Unit) 的协程闭包中被按值捕获为「首次组合的快照」，
            // 不会随 focusedPackage 后续变化更新——导致 HOME 列表态 A/X/Y/LS/RS 分支
            // 永远读到初始的 null 而全部跳过（搜索页不依赖 focusedApp 故正常）。
            // 这里用 state 的 apps + focusedPackage 即时重算，闭包内始终读到最新焦点应用。
            val liveFocusedApp = apps.find { it.packageName == focusedPackage }

            // ── 扳机 LT/RT：切换顶层 Tab（到边界不动）；含从设置页切出页面。──
            // 注意：扳机是压感轴（MotionEvent 每帧发射），必须用防抖锁定——
            // 收到 Trigger(LEFT, value) 后锁定左扳机，直到 value < 0.1f 松开才解锁，
            // 避免一次按压因多帧 MotionEvent 重复触发切 Tab。
            // DialogLayer 非 NONE 时 Trigger 已在顶层被屏蔽，此处仅处理无 dialog 场景。
            if (event is GamepadEvent.Trigger) {
                val leftLocked = triggerLeftLock.value
                val rightLocked = triggerRightLock.value
                if (event.side == Side.LEFT) {
                    if (event.value < 0.1f) {
                        triggerLeftLock.value = false // 松开解锁
                    } else if (!leftLocked) {
                        triggerLeftLock.value = true // 锁定
                        haptics.tick()
                        val order = listOf(MainTab.HOME, MainTab.STATS, MainTab.SEARCH, MainTab.SETTINGS)
                        val idx = order.indexOf(currentTab)
                        if (idx >= 0) {
                            val nextIdx = (idx - 1).coerceAtLeast(0)
                            if (nextIdx != idx) currentTab = order[nextIdx]
                        }
                    }
                } else {
                    if (event.value < 0.1f) {
                        triggerRightLock.value = false
                    } else if (!rightLocked) {
                        triggerRightLock.value = true
                        haptics.tick()
                        val order = listOf(MainTab.HOME, MainTab.STATS, MainTab.SEARCH, MainTab.SETTINGS)
                        val idx = order.indexOf(currentTab)
                        if (idx >= 0) {
                            val nextIdx = (idx + 1).coerceAtMost(order.lastIndex)
                            if (nextIdx != idx) currentTab = order[nextIdx]
                        }
                    }
                }
                return@collectLatest
            }
            // 设置 Tab 时：右摇杆滚动由引擎统一处理，其他事件由 handleSettingsEvent 分发。
            // 设置页 dialog 已由 DialogLayer 分支处理，此处仅处理设置页无 dialog 场景。
            if (currentTab == MainTab.SETTINGS) {
                if (event is GamepadEvent.RightStick) {
                    rightStickY.value = event.dy
                    lastRightStickAt.value = android.os.SystemClock.uptimeMillis()
                    return@collectLatest
                }
                handleSettingsEvent(
                    event,
                    currentRow = settingsFocusRow,
                    haptics = haptics,
                    onMove = { newRow -> settingsFocusRow = newRow },
                    onDismiss = { currentTab = MainTab.HOME },
                    onFavorite = { settingsInfoTrigger.value = settingsFocusRow },
                    maxRow = settingsMaxRow,
                    control = settingsControl.value,
                    dialogLayer = DialogLayer.NONE
                )
                return@collectLatest
            }
            when (event) {
                is GamepadEvent.Navigate -> {
                    haptics.tick()
                    when (currentTab) {
                        MainTab.STATS -> {
                            // 统计页（重构）：左栏=应用排行、右栏=总览总计。
                            // 跨栏逻辑已移除：左右栏由各自摇杆独立驱动，方向键只作用于左栏(排行)焦点。
                            // - 左栏(排行)：上下移动排行焦点（statsFocusIndex），左右 no-op。
                            // - 右栏(总计)：方向键 no-op（滚动由右摇杆负责）。
                            if (statsFocusSide == FocusSide.LEFT_LIST) {
                                // 左栏=应用排行：左摇杆上下移动焦点
                                when (event.direction) {
                                    Direction.UP -> statsFocusIndex = (statsFocusIndex - 1).coerceAtLeast(0)
                                    Direction.DOWN -> statsFocusIndex =
                                        (statsFocusIndex + 1).coerceAtMost((statsEntryCount - 1).coerceAtLeast(0))
                                    else -> { /* 排行左右无语义 */ }
                                }
                            } else {
                                // 右栏=总览总计：方向键不移动焦点（滚动归右摇杆）
                                /* no-op */
                            }
                        }
                        MainTab.HOME -> {
                            // 主页 Tab：十字键与左摇杆**归一化**为同一导航通道。
                            // 跨栏逻辑已整体移除（不再有 LS/RS 跨栏）：
                            // - 未进入详情态(focusSide=LEFT_LIST)：上下移动列表焦点，窄屏左右切分类。
                            // - 已进入详情态(focusSide=RIGHT_PANEL，A 点按列表项触发)：
                            //   上下在详情栏按钮间移动焦点；左右 no-op（窄屏也不切分类以免误操作）。
                            when (event.direction) {
                                Direction.UP, Direction.DOWN -> {
                                    if (focusSide == FocusSide.RIGHT_PANEL) {
                                        // 详情态：在详情栏按钮间移动焦点（首发 A 启动在 Select 分支处理）。
                                        // 需求1：改用 clamp（夹边界）而非取模循环——
                                        // 顶部启动钮(0)上拨不再跳到强停(4)，底部强停(4)下拨不动，
                                        // 更符合"焦点跟随"直觉。
                                        focusedDetailButton =
                                            if (event.direction == Direction.UP)
                                                (focusedDetailButton - 1).coerceAtLeast(0)
                                            else (focusedDetailButton + 1).coerceAtMost(DETAIL_BUTTON_COUNT - 1)
                                    } else if (event.direction == Direction.UP) {
                                        viewModel.moveInCurrentList(-1)
                                    } else {
                                        viewModel.moveInCurrentList(1)
                                    }
                                }
                                Direction.LEFT, Direction.RIGHT -> {
                                    if (focusSide == FocusSide.RIGHT_PANEL) {
                                        // 详情态：左右在同行按钮间切换焦点。
                                        // 仅收藏(1)⇄信息(2)同行，其余按钮独占行不响应左右（避免误触）。
                                        focusedDetailButton = when (focusedDetailButton) {
                                            1 -> 2
                                            2 -> 1
                                            else -> focusedDetailButton
                                        }
                                    } else if (!isWideScreen) {
                                        // 列表态窄屏左右切分类
                                        viewModel.cycleCategory(
                                            if (event.direction == Direction.LEFT) -1 else 1
                                        )
                                    }
                                }
                            }
                        }
                        MainTab.SEARCH -> {
                            // 搜索页 LIST 态：左摇杆（STICK）穿透控制下方应用列表（上下移动焦点，左右 no-op）。
                            // INPUT 态时 navigationEnabled=false，导航事件不会到达此处；
                            // 但为防止焦点状态错乱，仍做兜底：INPUT 态下不移动列表焦点。
                            if (searchFocusTarget == cn.mocabolka.run.viewmodel.SearchFocusTarget.LIST && event.source == NavigateSource.STICK) when (event.direction) {
                                Direction.UP, Direction.DOWN -> {
                                    val list = searchResults
                                    if (list.isNotEmpty()) {
                                        val idx = list.indexOfFirst { it.packageName == searchFocusedPackage }
                                            .let { if (it < 0) 0 else it }
                                        val step = if (event.direction == Direction.UP) -1 else 1
                                        val next = (idx + step).coerceIn(list.indices)
                                        searchFocusedPackage = list[next].packageName
                                        scope.launch { searchListState.animateScrollToItem(next) }
                                    }
                                }
                                Direction.LEFT, Direction.RIGHT -> { /* 搜索页不响应左右 */ }
                            }
                        }
                        else -> {
                            // SETTINGS Tab：事件已由外层 handleSettingsEvent 接管
                        }
                    }
                }
                is GamepadEvent.Select -> {
                    // 统计页：跨栏已移除，日期按钮不再可聚焦（由 View 键 / 点击打开），
                    // 故 A 在统计页无启动语义，统一 no-op（保留 tick 反馈避免无响应感）。
                    if (currentTab == MainTab.STATS) {
                        haptics.tick()
                    } else if (currentTab == MainTab.HOME && focusSide == FocusSide.RIGHT_PANEL) {
                        // 详情态：A 键首发启动当前 focus 按钮（0=启动）。焦点跟随：
                        // 进入详情态时 focusedDetailButton 已置 0，故首发 A 即启动；
                        // 上下移动焦点后，A 触发对应按钮（收藏/信息/卸载/强停）。
                        haptics.click()
                        val app = liveFocusedApp
                        if (app != null) {
                            when (focusedDetailButton) {
                                0 -> viewModel.launchApp(app)
                                1 -> viewModel.toggleFavorite(app.packageName)
                                2 -> runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", app.packageName, null)
                                        )
                                    )
                                }
                                3 -> runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_DELETE,
                                            Uri.fromParts("package", app.packageName, null)
                                        )
                                    )
                                }
                                4 -> viewModel.forceStop(app.packageName)
                            }
                        }
                    } else if (currentTab == MainTab.HOME && focusSide == FocusSide.LEFT_LIST) {
                        // 列表态：A 点按列表项 → 进入详情态（横屏右栏聚焦首钮 / 竖屏展开浮层）。
                        // 跨栏逻辑已移除：A 不再"先跨栏再启动"，而是直接进详情。
                        haptics.click()
                        if (liveFocusedApp != null) {
                            focusSide = FocusSide.RIGHT_PANEL
                            focusedDetailButton = 0
                            if (!isWideScreen) portraitDrawerOpen = true
                        }
                    } else if (currentTab == MainTab.SEARCH) {
                        // 搜索页 LIST 态：A 启动当前焦点项；INPUT 态：A 交给 IME（不启动）。
                        if (searchFocusTarget == cn.mocabolka.run.viewmodel.SearchFocusTarget.LIST) {
                            haptics.click()
                            val pkg = searchFocusedPackage
                            val app = pkg?.let { apps.firstOrNull { it.packageName == pkg && it.installed } }
                            if (app != null) viewModel.launchApp(app)
                        } else {
                            haptics.tick()
                        }
                    } else {
                        haptics.tick()
                    }
                }
                is GamepadEvent.Back -> {
                    when {
                        // ── 搜索页 B 键三态 ──
                        currentTab == MainTab.SEARCH -> {
                            when {
                                // 态1：软键盘可见 → 仅收起软键盘，保持输入框激活态
                                // （焦点仍留在输入框，可直接再按 Y 退出输入框态；左摇杆仍让位输入）
                                keyboardVisible -> {
                                    keyboardVisible = false
                                    val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                                    imm?.hideSoftInputFromWindow(view.windowToken, 0)
                                    runCatching { searchTabFocusRequester.freeFocus() }
                                    haptics.tick()
                                }
                                // 态2：INPUT 态（无键盘）→ 退到 LIST 态
                                searchFocusTarget == cn.mocabolka.run.viewmodel.SearchFocusTarget.INPUT -> {
                                    viewModel.setSearchFocusTarget(false)
                                    runCatching { searchTabFocusRequester.freeFocus() }
                                    if (searchResults.isNotEmpty()) {
                                        searchFocusedPackage = searchResults.first().packageName
                                        scope.launch { searchListState.animateScrollToItem(0) }
                                    }
                                    haptics.tick()
                                }
                                // 态3：LIST 态 → 退出搜索模式并切回主页
                                else -> {
                                    viewModel.closeSearch()
                                    currentTab = MainTab.HOME
                                    haptics.tick()
                                }
                            }
                        }
                        // 设置页内若有弹窗（下拉/确认/信息）打开，Back 优先关闭弹窗而非退出设置页
                        currentTab == MainTab.SETTINGS && settingsControl.value.hasOpenDialog -> {
                            settingsControl.value.onDismiss()
                            settingsControl.value.onDismissOverlay()
                            haptics.tick()
                        }
                        currentTab != MainTab.HOME -> { currentTab = MainTab.HOME; haptics.tick() }
                        // 竖屏详情浮层展开时按 B：收起浮层并同步退出详情态（focusSide 回 LEFT_LIST），
                        // 避免 focusSide 残留 RIGHT_PANEL 导致 effectiveFocus 误显右栏焦点框/ A 浮块（R6 修复）。
                        portraitDrawerOpen -> {
                            portraitDrawerOpen = false
                            if (focusSide == FocusSide.RIGHT_PANEL) focusSide = FocusSide.LEFT_LIST
                            haptics.tick()
                        }
                        // 详情态：B 先退回列表态（关闭右栏焦点框 / 收起竖屏浮层），而非退出 App
                        currentTab == MainTab.HOME && focusSide == FocusSide.RIGHT_PANEL -> {
                            focusSide = FocusSide.LEFT_LIST
                            if (!isWideScreen) portraitDrawerOpen = false
                            haptics.tick()
                        }
                        detailPinned -> { detailPinned = false; haptics.tick() }
                        else -> {
                    // 触摸/系统返回键退出：二次确认（底部气泡）。
                    if (touchExitConfirm) {
                        onExit()
                    } else {
                        touchExitConfirm = true
                        haptics.click()
                    }
                }
                    }
                }
                is GamepadEvent.Favorite -> {
                    when (currentTab) {
                        // 主页列表态：X 直接启动聚焦应用（需求2：应用列表 A 详情 / X 启动 / Y 收藏）。
                        // 详情态(右栏)：X 无操作——详情内所有动作统一由 A 触发当前焦点按钮，
                        // 避免 X 与「A 焦点跟随」语义冲突（收藏在详情内是焦点到收藏按钮后按 A）。
                        MainTab.HOME -> {
                            if (focusSide == FocusSide.LEFT_LIST && liveFocusedApp != null) {
                                haptics.click(); viewModel.launchFocused()
                            }
                        }
                        // 搜索页 LIST 态：X 键收藏当前焦点项
                        MainTab.SEARCH -> {
                            if (searchFocusTarget == cn.mocabolka.run.viewmodel.SearchFocusTarget.LIST) {
                                searchFocusedPackage?.let { viewModel.toggleFavorite(it) }
                            }
                            haptics.tick()
                        }
                        else -> haptics.tick()
                    }
                }
                is GamepadEvent.LeftStickPress -> {
                    // LS（左摇杆按压）：跨栏逻辑已移除。现作页面内"聚焦/进入"动作键：
                    // - 主页列表态：等价于 A，进入详情态（横屏右栏聚焦首钮 / 竖屏展开浮层）；
                    // - 主页详情态 / 其它页：无操作（避免误触）。
                    haptics.tick()
                    if (currentTab == MainTab.HOME && focusSide == FocusSide.LEFT_LIST && liveFocusedApp != null) {
                        focusSide = FocusSide.RIGHT_PANEL
                        focusedDetailButton = 0
                        if (!isWideScreen) portraitDrawerOpen = true
                    }
                }
                is GamepadEvent.RightStickPress -> {
                    // RS（右摇杆按压）：跨栏逻辑已移除，作页面内动作键。
                    // 主页列表态：与 LS 同效进入详情态；详情态无操作。
                    haptics.tick()
                    if (currentTab == MainTab.HOME && focusSide == FocusSide.LEFT_LIST && liveFocusedApp != null) {
                        focusSide = FocusSide.RIGHT_PANEL
                        focusedDetailButton = 0
                        if (!isWideScreen) portraitDrawerOpen = true
                    }
                }
                is GamepadEvent.Search -> {
                    haptics.tick()
                    when (currentTab) {
                        // 主页列表态：Y 键收藏当前聚焦应用（需求2：应用列表 A 详情 / X 启动 / Y 收藏）。
                        // 详情态(右栏)：Y 无操作（详情内收藏由 A 触发收藏按钮）。
                        MainTab.HOME -> {
                            if (focusSide == FocusSide.LEFT_LIST) {
                                liveFocusedApp?.let { viewModel.toggleFavorite(it.packageName) }
                            }
                        }
                        // 搜索页内按 Y：在 INPUT ⇄ LIST 之间切换焦点目标。
                        // 统一由 ViewModel.searchFocusTarget 驱动，不再依赖本地 searchFocusOnInput。
                        MainTab.SEARCH -> {
                            if (searchFocusTarget == cn.mocabolka.run.viewmodel.SearchFocusTarget.INPUT) {
                                // INPUT → LIST：释放输入焦点 + 收起软键盘
                                viewModel.setSearchFocusTarget(false)
                                runCatching { searchTabFocusRequester.freeFocus() }
                                val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                                imm?.hideSoftInputFromWindow(view.windowToken, 0)
                                if (searchResults.isNotEmpty()) {
                                    searchFocusedPackage = searchResults.first().packageName
                                    scope.launch { searchListState.animateScrollToItem(0) }
                                }
                            } else {
                                // LIST → INPUT：激活输入框，弹软键盘
                                viewModel.setSearchFocusTarget(true)
                                scope.launch {
                                    runCatching { searchTabFocusRequester.requestFocus() }
                                    val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                                    imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                }
                            }
                        }
                        // 其它页：Y 键无绑定（约束：仅搜索页下 Y 键有按键绑定事件）
                        else -> { /* 无操作 */ }
                    }
                }
                is GamepadEvent.ViewPress -> {
                    // View 键（Xbox 双方块按钮）：统计页打开日期选择器；其它页无操作
                    if (currentTab == MainTab.STATS) {
                        statsDatePickerOpen = true; setDialogLayer(DialogLayer.STATS_DATE_PICKER); haptics.tick()
                    } else haptics.tick()
                }
                is GamepadEvent.Shoulder -> {
                    haptics.tick()
                    when (currentTab) {
                        // 主页：LB/RB 切应用分类
                        MainTab.HOME -> viewModel.cycleCategory(if (event.side == Side.LEFT) -1 else 1)
                        // 统计页：LB/RB 切日周月年周期（任务 13，仅本页生效，不穿透其它页）
                        MainTab.STATS -> {
                            // 任务 13：LB/RB 切日周月年周期（仅本页生效）。
                            // 修复：Kotlin 负数取模问题——LEFT 在 idx=0 时 idx-1=-1，-1 % 4 = -1
                            // 导致 order[-1] IndexOutOfBounds 崩溃。统一 +order.size 后再取模避免越界。
                            val order = cn.mocabolka.run.ui.components.selectableStatsPeriods
                            val idx = order.indexOf(statsPeriod).let { if (it < 0) 0 else it }
                            val delta = if (event.side == Side.LEFT) -1 else 1
                            statsPeriod = order[(idx + delta + order.size) % order.size]
                        }
                        // 搜索页 LIST 态：LB/RB 翻页（±5 项），INPUT 态不操作
                        MainTab.SEARCH -> {
                            if (searchFocusTarget == cn.mocabolka.run.viewmodel.SearchFocusTarget.LIST) {
                                val list = searchResults
                                if (list.isNotEmpty()) {
                                    val idx = list.indexOfFirst { it.packageName == searchFocusedPackage }
                                        .let { if (it < 0) 0 else it }
                                    val step = if (event.side == Side.LEFT) -5 else 5
                                    val next = (idx + step).coerceIn(list.indices)
                                    searchFocusedPackage = list[next].packageName
                                    scope.launch { searchListState.animateScrollToItem(next) }
                                }
                            }
                        }
                        // 其它页：LB/RB 无操作，避免按键穿透
                        else -> { /* 无操作 */ }
                    }
                }
                is GamepadEvent.RightStick -> {
                    // 任务 14：仅记录右摇杆状态，真正的平滑加速滚动由下方引擎循环驱动
                    rightStickY.value = event.dy
                    lastRightStickAt.value = android.os.SystemClock.uptimeMillis()
                }
                // else 仅用于满足穷尽性。
                else -> { /* 无操作 */ }
            }
        }
    }

    // 任务 14：右摇杆平滑加速滚动引擎
    // 持续按帧积分：按住方向时速度加速至上限（且保持越久上限越高 → 越来越快），
    // 松手后平滑减速停止。覆盖全部页面及分栏：主页/搜索/设置列表、统计排行与左栏。
    suspend fun applyRightStickScroll(delta: Float) {
        if (delta == 0f) return
        runCatching {
            // ── 归一化最高优先级（R1）：dialog 打开时右摇杆滚动 dialog 内容，而非背景。──
            // 所有 dialog（日期选择器网格 / 设置下拉列表）与主页/统计/搜索共用同一加速引擎，
            // dialog 内部只暴露 suspend 像素滚动能力（scrollByPx），此处按帧喂入 delta。
            when (currentDialogLayer) {
                DialogLayer.STATS_DATE_PICKER -> {
                    statsDatePickerBridge.value.scrollByPx(delta); return
                }
                DialogLayer.SETTINGS_DROPDOWN -> {
                    dropdownScrollByPx.value?.invoke(delta); return
                }
                // 确认弹窗内容较短，无需右摇杆滚动；信息弹窗正文可能超长，右摇杆滚动其正文。
                DialogLayer.SETTINGS_CONFIRM -> return
                DialogLayer.SETTINGS_INFO -> { infoScrollByPx.value?.invoke(delta); return }
                DialogLayer.NONE -> { /* 落到下方页面级分发 */ }
            }
            when (currentTabRef.value) {
                MainTab.STATS ->
                    // 宽屏双栏（重构）：左栏=应用排行(statsListState)、右栏=总览总计(statsLeftScrollState)。
                    // 右摇杆仅在右栏(总计)滚动；左栏(排行)滚动由左摇杆焦点驱动 scrollToItem。
                    // 跨栏已移除：右摇杆恒滚右栏(总计)，不随焦点侧切换。
                    statsLeftScrollState.scrollBy(delta)
                MainTab.SEARCH -> searchListState.scrollBy(delta)
                MainTab.SETTINGS -> {
                    // 设置页列表滚动（子页面已改为独立 Activity，不再在此处理）。
                    // 隔离守卫：下拉/确认/信息弹窗打开时禁止滚动背景设置列表，
                    // 避免右摇杆穿透到设置页（即便事件层已屏蔽，此处再兜底）。
                    if (settingsControl.value.hasOpenDialog) return
                    settingsListState.scrollBy(delta)
                }
                // 主页：焦点在右栏详情面板时滚动面板，否则滚动主列表（任务 5 修复）
                else -> if (focusSide == FocusSide.RIGHT_PANEL) detailScrollState.scrollBy(delta)
                        else currentListStateRef.value.scrollBy(delta)
            }
        }
    }

    /**
     * 右摇杆滚动后同步焦点：将 [searchFocusedPackage] 或主页 [focusedPackage]
     * 更新到当前列表首个可视项，确保方向键 UP/DOWN 的焦点位置与可视位置一致。
     * 统计页由 HomeScreen 事件循环的 statsFocusIndex 同步，无需额外处理。
     */
    fun syncRightStickFocus() {
        val tab = currentTabRef.value
        if (tab == MainTab.SEARCH) {
            val idx = searchListState.firstVisibleItemIndex
            if (idx < searchResults.size) {
                searchFocusedPackage = searchResults[idx].packageName
            }
        } else if (tab == MainTab.SETTINGS) {
            // 需求4：右摇杆自由滚动设置列表后，把焦点行同步到首个可视项，
            // 让"右摇杆停在哪、焦点就跟到哪"在设置页也成立（与 HOME/SEARCH 一致）。
            // 下拉/确认/信息弹窗打开时由引擎兜底 return，此处不再重复守卫。
            val idx = settingsListState.firstVisibleItemIndex
            val max = settingsMaxRow
            if (idx in 0..max) settingsFocusRow = idx
        } else if (tab == MainTab.HOME && focusSide != FocusSide.RIGHT_PANEL) {
            val state = currentListStateRef.value
            val idx = state.firstVisibleItemIndex
            val list = visibleList
            if (idx < list.size) {
                val pkg = list[idx].packageName
                if (pkg != focusedPackage) {
                    // 置位抑制：本次 focusedPackage 变化由右摇杆滚动同步而来，
                    // 列表已滚到位，须跳过 LaunchedEffect(focusedPackage) 的反向 animateScrollToItem，
                    // 否则列表被拉回、右摇杆滚动被抵消（主页焦点跟随失效根因，已修复）。
                    suppressListAutoScroll.value = true
                    viewModel.setFocused(pkg)
                }
            }
        }
    }

    // 任务：右摇杆滚动引擎（重写）
    // - 轻推即持续滚动，并在推下的瞬间给一次震动反馈；
    // - 只要一直推着，速度上限随按住时长线性增长（越推越快）；
    // - 松手后平滑减速停止。覆盖全部页面及分栏。
    LaunchedEffect(Unit) {
        // 物理积分复用归一化引擎（RightStickScrollPhysics），与子页面 SubPageGamepad 完全同一算法/参数，
        // 保证主页/搜索/统计/设置/子页面/dialog 右摇杆手感一致，未来调参仅需改 RightStickScroll。
        val physics = RightStickScrollPhysics()
        var prevScrolling = false
        var lastTickAt = 0L
        var lastFrameAt = android.os.SystemClock.uptimeMillis()
        while (true) {
            val now = android.os.SystemClock.uptimeMillis()
            // 真实帧间隔驱动物理积分：无论帧率高低，"越滚越快"曲线表现一致，封顶防后台巨步。
            val dt = ((now - lastFrameAt)
                .coerceIn(1L, RightStickScroll.MAX_FRAME_DT_MS) / 1000f)
                .coerceAtLeast(1f / 240f)
            lastFrameAt = now
            val released = now - lastRightStickAt.value > RightStickScroll.RELEASE_MS
            val dy = if (released) 0f else rightStickY.value
            val delta = physics.step(dt, dy)
            // 震动反馈：轻推瞬间给一次；持续滚动时每 ~450ms 再轻触一次。
            if (physics.wasScrolling) {
                if (!prevScrolling) { haptics.tick(); lastTickAt = now }
                else if (now - lastTickAt > 450L) { haptics.tick(); lastTickAt = now }
            }
            prevScrolling = physics.wasScrolling
            if (delta != 0f) {
                applyRightStickScroll(delta)
                // 右摇杆滚动后同步焦点：将焦点更新到当前列表的首个可视项，
                // 确保方向键 UP/DOWN 的焦点位置与可视位置一致（「右摇杆停哪、焦点跟哪」）。
                syncRightStickFocus()
            }
            delay(RightStickScroll.FRAME_DELAY_MS.milliseconds)
        }
    }

    // 覆盖式即时 Toast：新消息立即替换旧消息显示（不排队、不逐个回弹），
    // 并通过 LaunchedEffect 计时自动消失，符合大众 App 的 toast 反馈习惯。
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        viewModel.toast.collect { msg ->
            haptics.error()
            toastMsg = msg
            toastKey++ // 触发下方计时 effect 重置
        }
    }
    LaunchedEffect(toastKey) {
        if (toastMsg != null) {
            delay(2.5.seconds)
            toastMsg = null
        }
    }

    // 系统文件选择器（SAF）：用户自行选择分类映射文件（json），选完回调 ViewModel 解析应用。
    val importCategoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) viewModel.applyImportedCategoryMapping(uri) }
    )
    LaunchedEffect(Unit) {
        viewModel.importRequest.collect {
            importCategoryLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            // 进入搜索页时立即激活输入框（INPUT 态），由 ViewModel 状态机驱动 navigationEnabled。
            viewModel.setSearchFocusTarget(true)
            scope.launch { searchTabFocusRequester.requestFocus() }
        } else {
            // 离开搜索页：复位焦点状态（无论是否手动，统一清理，避免残留影响主页导航）。
            viewModel.setSearchFocusTarget(false)
            // 显式收起软键盘，避免激活态残留导致离开后键盘仍悬浮（B/Menu 返回场景）。
            val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }



    LandscapeTheme(
        darkTheme = darkMode != DarkMode.LIGHT,
        useMonet = useMonet,
        isAmoled = darkMode == DarkMode.AMOLED,
        // 主屏已有实时手柄连接状态，直接传入驱动焦点框可见性
        gamepadConnected = gamepadConnected
    ) {
        // 根 Box：Scaffold 占满全屏，二级子菜单以 matchParentSize + 高 zIndex
        // 覆盖在 Scaffold 之上（含状态栏/挖孔区），实现"沉浸式遮罩"——黑色半透明
        // 遮罩填满整个窗口，包括 StatusBar 与系统状态栏；StatusBar 仍在 Scaffold 内
        // 显示，子菜单打开时被遮罩自然覆盖，退出时渐隐复原。
        Box(modifier = Modifier.fillMaxSize()) {
            // 动态氛围背景：在 Scaffold 之前渲染，fillMaxSize 覆盖全屏（含状态栏/挖孔区），
            // 实现真正沉浸。穿透壁纸模式时由系统壁纸替代，不渲染此层。
            if (!wallpaperBehind) {
                AmbientBackground(
                    enabled = effectiveDynamicBackground,
                    reduceMotion = effectiveReduceMotion,
                    paused = !appVisible,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Scaffold(
                // 透明容器：否则 Scaffold 默认用 colorScheme.background 绘制不透明背景层，
                // 会完全盖住下层 AmbientBackground 动态氛围背景（表现为"动态背景不生效"）。
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                // 复用 HomeScreen 顶层的 isWideScreen（基于 configuration.screenWidthDp >= 720）
                val detailSideWidth = if (isWideScreen) 300.dp else 0.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onKeyEvent { ev ->
                        val n = ev.nativeKeyEvent
                        if (n.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        when {
                            currentTab == MainTab.SETTINGS -> {
                                if (n.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                                    currentTab = MainTab.HOME; haptics.tick(); true
                                } else false
                            }
                            searchActive -> {
                                when (n.keyCode) {
                                    KeyEvent.KEYCODE_ESCAPE -> {
                                        viewModel.closeSearch(); haptics.tick(); true
                                    }
                                    KeyEvent.KEYCODE_ENTER -> {
                                        // LIST 态：Enter 启动当前焦点项；INPUT 态：交 IME（触发 onSearch）。
                                        if (searchFocusTarget == cn.mocabolka.run.viewmodel.SearchFocusTarget.LIST) {
                                            val pkg = searchFocusedPackage
                                            val app = pkg?.let { apps.firstOrNull { a -> a.packageName == pkg && a.installed } }
                                            if (app != null) { viewModel.launchApp(app); haptics.tick(); true }
                                            else false
                                        } else if (query.isBlank()) {
                                            viewModel.closeSearch(); true
                                        } else false
                                    }
                                    KeyEvent.KEYCODE_Y -> {
                                        // 搜索模式下键盘 Y：toggle INPUT ⇄ LIST（与手柄 Y 一致）
                                        if (searchFocusTarget == cn.mocabolka.run.viewmodel.SearchFocusTarget.INPUT) {
                                            viewModel.setSearchFocusTarget(false)
                                            runCatching { searchTabFocusRequester.freeFocus() }
                                            val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                                            imm?.hideSoftInputFromWindow(view.windowToken, 0)
                                        } else {
                                            viewModel.setSearchFocusTarget(true)
                                            runCatching { searchTabFocusRequester.requestFocus() }
                                        }
                                        haptics.tick()
                                        true
                                    }
                                    else -> false
                                }
                            }
                            else -> when (n.keyCode) {
                                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                                    // 主页列表态：Enter 进入详情态（与手柄 A 对齐）；
                                    // 详情态：Enter 触发当前聚焦按钮（启动等）。
                                    if (currentTab == MainTab.HOME && focusSide == FocusSide.LEFT_LIST) {
                                        if (focusedApp != null) {
                                            focusSide = FocusSide.RIGHT_PANEL
                                            focusedDetailButton = 0
                                            if (!isWideScreen) portraitDrawerOpen = true
                                            haptics.tick()
                                        } else { viewModel.launchFocused(); }
                                        true
                                    } else if (currentTab == MainTab.HOME && focusSide == FocusSide.RIGHT_PANEL) {
                                        haptics.click()
                                        val app = focusedApp
                                        if (app != null) {
                                            when (focusedDetailButton) {
                                                0 -> viewModel.launchApp(app)
                                                1 -> viewModel.toggleFavorite(app.packageName)
                                                2 -> runCatching {
                                                    context.startActivity(
                                                        Intent(
                                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                            Uri.fromParts("package", app.packageName, null)
                                                        )
                                                    )
                                                }
                                                3 -> runCatching {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_DELETE,
                                                            Uri.fromParts("package", app.packageName, null)
                                                        )
                                                    )
                                                }
                                                4 -> viewModel.forceStop(app.packageName)
                                            }
                                        }
                                        true
                                    } else { viewModel.launchFocused(); true }
                                }
                                // Tab 键：跨栏逻辑已移除，不再作跨栏入口。保留为 no-op（避免吞掉系统焦点切换）。
                                KeyEvent.KEYCODE_TAB -> false
                                KeyEvent.KEYCODE_X ->
                                    { viewModel.toggleFavoriteOfFocused(); haptics.tick(); true }
                                KeyEvent.KEYCODE_SLASH ->
                                    false
                                KeyEvent.KEYCODE_F ->
                                    if (n.isCtrlPressed) false else false
                                KeyEvent.KEYCODE_F5 -> { viewModel.rescan(); true }
                                KeyEvent.KEYCODE_ESCAPE -> {
                                    // 主页详情态：Esc 先退回列表态（与手柄 B 一致），不触发退出确认。
                                    if (currentTab == MainTab.HOME && focusSide == FocusSide.RIGHT_PANEL) {
                                        focusSide = FocusSide.LEFT_LIST
                                        if (!isWideScreen) portraitDrawerOpen = false
                                        haptics.tick(); true
                                    } else if (currentTab == MainTab.STATS && isWideScreen) {
                                        // 统计页：跨栏已移除，Esc 直接走下方退出确认（不再有右栏退回）。
                                        haptics.tick(); false
                                    } else {
                                    // 键盘 Esc 退出：与触摸一致走二次确认；手柄退出不受影响。
                                    if (touchExitConfirm) { onExit() } else { touchExitConfirm = true; haptics.click() }
                                    true
                                    }
                                }
                                else -> false
                            }
                        }
                    }
            ) {
                // 穿透背景：底层展示系统桌面壁纸（位于内容之下）
                if (wallpaperBehind) {
                    WallpaperBackground(modifier = Modifier.fillMaxSize())
                }

                // ── 主体：StatusBar（内嵌 Tab 滑块）+ AnimatedContent 切换 4 个页面 ──
                // 任务 28：挖孔屏适配——窗口层（MainActivity）恒设 LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS，
                // 内容本身可延伸到挖孔区域（即"全屏"）。是否主动绕开摄像头由 cutoutAdapt 开关决定：
                // 开启（挖孔屏适配）→ 宽屏加 displayCutout padding 让关键内容避开摄像头；
                // 关闭（全屏）→ 不加 padding，内容铺满延伸到挖孔区。
                // 竖屏（非宽屏）挖孔多在顶部中央，StatusBar 已处理状态栏区，主体不左右推挤，
                // 否则会浪费纵向空间且把内容推偏。故仅"宽屏且开启适配"才加 padding。
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isWideScreen && cutoutAdapt)
                                Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier
                        )
                        // 瀑布屏（曲面屏）安全边距：与挖孔开关解耦，对所有页面（含设置页）始终生效，
                        // 在 displayCutout inset 与 Dimens.waterfallSafe 间取较大值，
                        // 确保关键内容（焦点框/按钮/列表末项）不贴曲面边缘、避免边缘误触与光学扭曲。
                        // 设置页同样需要一致的安全余量（竖屏底部避让更高、横屏更矮）。
                        .then(Modifier.waterfallSafePadding())
                ) {
                    StatusBar(
                        appCount = apps.size,
                        // 主页用时段问候语，其余页用对应页名（避免与子页面自带标题形成「双层标题」）
                        pageTitle = when (currentTab) {
                            MainTab.HOME -> greetingByHour()
                            MainTab.STATS -> "使用统计"
                            MainTab.SEARCH -> "搜索"
                            MainTab.SETTINGS -> "设置"
                        },
                        currentTab = currentTab,
                        onTabChange = { tab ->
                            currentTab = tab as MainTab
                            haptics.tick()
                        },
                        homeTab = MainTab.HOME,
                        statsTab = MainTab.STATS,
                        searchTab = MainTab.SEARCH,
                        settingsTab = MainTab.SETTINGS,
                        showGamepadHints = gamepadConnected
                    )

                    // Tab 内容：AnimatedContent 切换主列表 / 设置
                    // 性能优化（2026-07-13）：
                    // - 过渡时长 220ms fade + 320ms slide → 140ms fade + 180ms slide（缩短 ~50%）
                    // - 横向位移 1/10 屏宽 → 1/20 屏宽（减小视觉移动量）
                    // - 原因：旧 tab 在过渡期内会继续运行所有 pulse/scale/infinite 动画，
                    //   320ms 过渡意味着双列表同时渲染 320ms，是切卡顿的主因；
                    //   缩短到 180ms 后，旧 tab 几乎"瞬间"被替换，新 tab 接管渲染。
                    // - 体感从"明显可感"降至"几乎无感"，与系统 BottomNav 持平。
                    // - reduceMotion 时退化为纯 fade（无 slide 移动）。
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            val fade = if (effectiveReduceMotion) 0 else MotionSpec.TabFade.durationMillis
                            val slideMs = if (effectiveReduceMotion) 0 else MotionSpec.TabSlideOffset.durationMillis
                            (fadeIn(animationSpec = tween(fade, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(
                                    animationSpec = tween(slideMs, easing = FastOutSlowInEasing),
                                    initialOffsetX = { dir * it / 20 }
                                )) togetherWith
                                (fadeOut(animationSpec = tween(fade, easing = FastOutSlowInEasing)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(slideMs, easing = FastOutSlowInEasing),
                                        targetOffsetX = { -dir * it / 20 }
                                    ))
                        },
                        label = "tabContent",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { tab ->
                        when (tab) {
                            MainTab.HOME -> HomeTabContent(
                                isWideScreen = isWideScreen,
                                detailSideWidth = detailSideWidth,
                                gamepadConnected = gamepadConnected,
                                categoryTabs = categoryTabs,
                                selectedCategory = selectedCategory,
                                focusedChip = focusedChip,
                                onSelectCategory = {
                                    viewModel.setCategoryFilter(it)
                                    viewModel.clearChipFocus()
                                },
                                query = query,
                                visibleList = visibleList,
                                focusedPackage = focusedPackage,
                                onFocus = viewModel::setFocused,
                                onLaunch = viewModel::launchApp,
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                favorites = favorites,
                                badgeOf = badgeOf,
                                currentListState = currentListState,
                                isEmpty = apps.isEmpty(),
                                emptyText = if (query.isNotBlank()) "没有匹配的应用"
                                    else if (selectedCategory == "收藏") "收藏夹为空"
                                    else if (queryAllGranted) "未安装任何应用"
                                    else "需要应用列表权限",
                                // 空状态引导改为中性文案，去掉裸按键符号（"按 X/Y/A" 等），
                                // 手柄键位提示已统一由 LB/RB、A/X 浮块、底部 HintBar 表达。
                                emptyHint = when {
                                    query.isNotBlank() -> "清空搜索框以查看全部"
                                    selectedCategory == "收藏" -> "选中应用即可加入收藏"
                                    !queryAllGranted -> "系统限制下无法读取全部应用，请授权"
                                    else -> "点击重新扫描以刷新"
                                },
                                queryAllGranted = queryAllGranted,
                                onOpenPermissionSettings = { viewModel.openAppInfoForPermission() },
                                onRescan = { viewModel.rescan() },
                                categoryCounts = categoryCounts,
                                focusedApp = focusedApp,
                                onToggleFavoriteFocused = { viewModel.toggleFavorite(it) },
                                launching = launching,
                                onOpenInfo = { app ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", app.packageName, null)
                                            )
                                        )
                                    }
                                },
                                onUninstall = { app ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_DELETE,
                                                Uri.fromParts("package", app.packageName, null)
                                            )
                                        )
                                    }
                                },
                                onForceStop = { app -> viewModel.forceStop(app.packageName) },
                                // 主页与搜索页完全隔离：主页不展示搜索命中提示（由搜索页自身负责）。
                                searchInfo = null,
                                todayUsageMap = todayUsageMap,
                                weeklyUsageMap = weeklyUsageMap,
                                showUsageHint = !UsageStatsPermissionHelper.isGranted(context),
                                focusSide = focusSide,
                                focusedDetailButton = focusedDetailButton,
                                effectiveReduceMotion = effectiveReduceMotion,
                                effectiveGlass = effectiveGlass,
                                wallpaperBehind = wallpaperBehind,
                                detailScrollState = detailScrollState,
                                onOpenPortraitDrawer = { portraitDrawerOpen = true },
                                onFocusedButtonChange = { idx ->
                                    // 鼠标/键盘点击右栏按钮：建立跨栏焦点态，使右栏边框与按钮高亮可见，
                                    // 并让后续手柄/键盘 A、方向键在右栏内工作。
                                    focusSide = FocusSide.RIGHT_PANEL
                                    focusedDetailButton = idx
                                }
                            )
                            MainTab.STATS ->                             cn.mocabolka.run.ui.components.StatsScreen(
                                viewModel = viewModel,
                                listState = statsListState,
                                period = statsPeriod,
                                anchorMs = statsAnchorMs,
                                onAnchorChange = { statsAnchorMs = it },
                                datePickerOpen = statsDatePickerOpen,
                                onDatePickerOpenChange = { open ->
                                    statsDatePickerOpen = open
                                    if (open) setDialogLayer(DialogLayer.STATS_DATE_PICKER)
                                    else setDialogLayer(DialogLayer.NONE)
                                },
                                datePickerBridge = statsDatePickerBridge.value,
                                reduceMotion = reduceMotion,
                                leftScrollState = statsLeftScrollState,
                                focusIndex = statsFocusIndex,
                                gamepadConnected = gamepadConnected,
                                onEntryCountChange = { statsEntryCount = it },
                                onSelectPeriod = { statsPeriod = it }
                            )
                            MainTab.SEARCH -> SearchTabContent(
                                query = query,
                                onQueryChange = viewModel::setQuery,
                                onCloseSearch = viewModel::closeSearch,
                                searchFocusRequester = searchTabFocusRequester,
                                view = view,
                                visibleList = searchResults,
                                listState = searchListState,
                                focusedPackage = searchFocusedPackage,
                                onFocus = { searchFocusedPackage = it },
                                onLaunch = { app ->
                                    // 启动走全局 launchApp，但焦点状态保持各自独立
                                    viewModel.launchApp(app)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                favorites = favorites,
                                badgeOf = badgeOf,
                                isEmpty = searchResults.isEmpty(),
                                emptyText = if (query.isNotBlank()) "没有匹配的应用"
                                    else "输入关键字搜索应用",
                                // 空状态引导改为纯内容文案，不再暴露裸按键文本（"按 B/Esc"、"按 Y" 等）；
                                // 手柄键位提示统一由顶部 LB/RB、搜索框 Y 浮块、底部 HintBar 表达。
                                showGamepadHints = gamepadConnected,
                                inputActive = searchFocusTarget == cn.mocabolka.run.viewmodel.SearchFocusTarget.INPUT,
                                keyboardVisible = keyboardVisible,
                                todayUsageMap = todayUsageMap,
                                effectiveReduceMotion = effectiveReduceMotion,
                                isWideScreen = isWideScreen,
                                portraitDrawerOpen = portraitDrawerOpen,
                                onOpenPortraitDrawer = { portraitDrawerOpen = true },
                                onKeyboardVisibleChange = { keyboardVisible = it },
                                resultCount = searchResults.size
                            )
                            MainTab.SETTINGS -> {
                                SettingsTabContent(
                                        darkMode = darkMode,
                                        onDarkModeChange = { viewModel.settings.darkMode = it },
                                        sortMode = sortMode,
                                        onSortModeChange = { viewModel.setSortMode(it) },
                                        onRescan = { viewModel.rescan() },
                                        isScanning = isScanning,
                                        appVersion = viewModel.settings.appVersion,
                                        onClearFavorites = { viewModel.clearFavorites() },
                                        onResetSettings = { viewModel.resetSettings() },
                                        showBadges = showBadges,
                                        onShowBadgesChange = { viewModel.settings.showBadges = it },
                                        useMonet = useMonet,
                                        onUseMonetChange = { viewModel.settings.useMonet = it },
                                        wallpaperBehind = wallpaperBehind,
                                        onWallpaperBehindChange = { viewModel.settings.wallpaperBehind = it },
                                        cutoutAdapt = cutoutAdapt,
                                        onCutoutAdaptChange = { viewModel.settings.cutoutAdapt = it },
                                        showSystemApps = showSystemApps,
                                        onShowSystemAppsChange = { viewModel.settings.showSystemApps = it; viewModel.rescan() },
                                        onExportCategories = { viewModel.exportCategoryMapping() },
                                        onImportCategories = { viewModel.requestImportCategoryMapping() },
                                        onClearCategories = { viewModel.clearCategoryMapping() },
                                        dynamicBackground = dynamicBackground,
                                        onDynamicBackgroundChange = { viewModel.settings.dynamicBackground = it },
                                        glassSurface = glassSurface,
                                        onGlassSurfaceChange = { viewModel.settings.glassSurface = it },
                                        reduceMotion = reduceMotion,
                                        onReduceMotionChange = { viewModel.settings.reduceMotion = it },
                                        orientationMode = orientationMode,
                                        onOrientationModeChange = { mode ->
                                            viewModel.settings.orientationMode = mode
                                            OrientationManager.onChange(context, viewModel.settings)
                                            if (OrientationManager.needsOverlayPermission(context, mode)) {
                                                viewModel.requestOverlayForOrientation()
                                            }
                                        },
                                        focusedRow = settingsFocusRow,
                                        onFocusedRowChange = { settingsFocusRow = it },
                                        listState = settingsListState,
                                        infoTrigger = settingsInfoTrigger,
                                        onRowCountChange = { settingsMaxRow = it },
                                        controlBridge = settingsControl,
                                        onClose = { currentTab = MainTab.HOME },
                                        onManagePermissions = {
                                            context.startActivity(
                                                Intent(context, cn.mocabolka.run.compat.CompatGuideActivity::class.java)
                                            )
                                        },
                                        onAboutClick = {
                                            context.startActivity(
                                                Intent(context, AboutActivity::class.java)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 启动遮罩：圆形揭示（Circular Reveal）替代纯淡入
                LaunchOverlay(visible = launching)

                // 触摸退出二次确认气泡（底部居中，不遮挡全屏）
                // 2026-07-14 归一化：复用 ReverieToast 模板，与"已扫描 N 个应用"等覆盖式 toast 同款外观/动画，
                // 避免"两份近乎一样的气泡代码 + 样式漂移"。
                ReverieToast(
                    message = if (touchExitConfirm) "再次返回退出 Reverie" else null
                )

                // 覆盖式即时 Toast：单条显示，新消息立即替换旧消息（不排队回弹）。
                // 视觉/动画统一由 ReverieToast 模板提供（强表面 + 主题色描边 + 大圆角 + 底部 48dp）。
                ReverieToast(message = toastMsg)
                }
            }
        }

        // ── 开屏动画（第二幕 Overlay）：showOverlay 初始即为 true（主界面在遮罩下加载），
        // 数据就绪（isBooting true→false）时 endOverlay() 淡出，露出已就绪主屏。
        // 这样避免"先露主屏图标、再弹 splash"的诡异时序。
        // 平台 Splash 在目标环境不触发退场回调，故改为监听 isBooting 翻转。
        // 通过 fillMaxSize + zIndex 让其填满根 Box 全部空间，与二级子菜单同级。
        // 开屏期间隐藏系统状态栏，实现真正全屏沉浸；退出时恢复。
        LaunchedEffect(isBooting) {
            if (!isBooting) viewModel.endOverlay()
        }
        LaunchedEffect(showOverlay) {
            val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
            val insetsCtrl = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            if (showOverlay) {
                insetsCtrl.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                insetsCtrl.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsCtrl.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }
        }
        SplashOverlay(
            visible = showOverlay,
            reduceMotion = reduceMotion,
            modifier = Modifier.fillMaxSize().zIndex(1f)
        )

        // ── 竖屏详情抽屉（非宽屏时）：全屏覆盖层，与二级子菜单同级 ──
        // 黑色半透明遮罩覆盖全局 Tab 标题栏与状态栏，实现沉浸效果。
        // 遮罩（点击关闭）+ 底部面板（55% 屏高，从下方滑入）。
        // 注意：此块仅在 !isWideScreen 时渲染。
        if (!isWideScreen) {
            // 半透明遮罩
            AnimatedVisibility(
                visible = portraitDrawerOpen && focusedApp != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize().zIndex(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceTokens.scrim())
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { portraitDrawerOpen = false }
                        )
                )
            }
            // 底部详情面板
            AnimatedVisibility(
                visible = portraitDrawerOpen && focusedApp != null,
                enter = fadeIn(animationSpec = MotionSpec.Medium) + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut(animationSpec = MotionSpec.Medium) + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.md, vertical = Dimens.sm)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 顶部拖拽指示条
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = Dimens.xs)
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { portraitDrawerOpen = false }
                                    )
                            )
                            AppDetailPanel(
                                app = focusedApp,
                                isFavorite = focusedApp?.let { it.packageName in favorites } ?: false,
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onLaunch = viewModel::launchApp,
                                launching = launching,
                                onOpenInfo = { app ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", app.packageName, null)
                                            )
                                        )
                                    }
                                },
                                onUninstall = { app ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_DELETE,
                                                Uri.fromParts("package", app.packageName, null)
                                            )
                                        )
                                    }
                                },
                                onForceStop = { app -> viewModel.forceStop(app.packageName) },
                                badgeCount = focusedApp?.let { badgeOf(it.packageName) } ?: 0,
                                searchInfo = null,
                                showUsageHint = !UsageStatsPermissionHelper.isGranted(context),
                                todayUsage = todayUsageMap[focusedApp?.packageName] ?: 0L,
                                weeklyUsage = weeklyUsageMap[focusedApp?.packageName] ?: 0L,
                                glass = effectiveGlass,
                                reduceMotion = effectiveReduceMotion,
                                scrollState = detailScrollState,
                                wallpaperBehind = wallpaperBehind,
                                // 竖屏详情浮层：进入详情态(focusSide=RIGHT_PANEL)时同步焦点按钮索引，
                                // 让左摇杆可在浮层内移动焦点、A 浮块焦点跟随（与横屏右栏一致）。
                                focusedButtonIndex = if (focusSide == FocusSide.RIGHT_PANEL) focusedDetailButton else -1,
                                onFocusedButtonChange = { idx -> focusedDetailButton = idx },
                                maxHeight = this@BoxWithConstraints.maxHeight * 0.55f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Dimens.lg))
                                    // 需求1：进入详情态时给整个详情右栏/浮层加焦点框，
                                    // 让手柄用户清晰感知"当前处于详情上下文"。
                                    // focusBorder 内部已 && 手柄连接守卫，故仅 focusSide 控制显隐。
                                    .focusBorder(
                                        enabled = focusSide == FocusSide.RIGHT_PANEL,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(Dimens.lg)
                                    )
                            )
                        }
                    }
                }
            }
        }

        // ── 二级子菜单：全屏覆盖层（覆盖在 Scaffold 之上，含状态栏 / 挖孔区 / StatusBar）──
        // 通过 BoxScope.fillMaxSize + zIndex 让其填满根 Box 全部空间，
        // 黑色半透明遮罩真正沉浸（不再只覆盖内容区）。统一使用 fadeIn/scaleIn 入场、
        // fadeOut 出场。包含统计页日期选择器 + 设置页三个 dialog（Confirm/Info/Dropdown）。
        // ── 设置页弹窗快照（退出动画期间保留内容） ──
        var lastConfirmSnapshot by remember { mutableStateOf<Pair<SettingsConfirm, () -> Unit>?>(null) }
        var lastInfoSnapshot by remember { mutableStateOf<InfoContent?>(null) }
        val sBridge = settingsControl.value
        if (sBridge.pendingConfirm != null) lastConfirmSnapshot = sBridge.pendingConfirm
        if (sBridge.pendingInfo != null) lastInfoSnapshot = sBridge.pendingInfo

        // 设置页弹窗 DialogLayer 同步：监听 AnimatedVisibility visible 变化，
        // 在弹窗可见/不可见时同步 DialogLayer 状态，确保 HomeScreen 事件循环正确路由。
        // 注意：三个弹窗互斥（同一时间只有一个打开），但关闭顺序可能交叠。
        // 统一在一个 LaunchedEffect 中处理优先级，避免条件竞态。
        LaunchedEffect(
            sBridge.pendingConfirm != null,
            sBridge.pendingInfo != null,
            sBridge.openDropdownRow >= 0
        ) {
            val target = when {
                sBridge.pendingConfirm != null -> DialogLayer.SETTINGS_CONFIRM
                sBridge.pendingInfo != null -> DialogLayer.SETTINGS_INFO
                sBridge.openDropdownRow >= 0 -> DialogLayer.SETTINGS_DROPDOWN
                else -> DialogLayer.NONE
            }
            if (target != currentDialogLayer) setDialogLayer(target)
        }

        // 设置页下拉弹窗的列表滚动状态：手柄上下导航已跟随焦点滚动，
        // 这里额外持有状态以便右摇杆自由滚动超长列表。
        val dropdownListState = rememberLazyListState()
        // R1 归一化：把下拉列表的 suspend 像素滚动能力挂到共享引用，供统一右摇杆加速引擎按帧驱动。
        DisposableEffect(dropdownListState) {
            dropdownScrollByPx.value = { px -> dropdownListState.scrollBy(px) }
            onDispose { dropdownScrollByPx.value = null }
        }

        // 设置页 InfoDialog 内容滚动状态：由 HomeScreen 持有并传给 InfoDialog，
        // 修复「右摇杆误控背景设置页」的异常——右摇杆应滚动 InfoDialog 正文而非底层设置列表。
        val infoScrollState = rememberScrollState()
        // R2 归一化：把 infoScrollState 的 suspend 像素滚动能力挂到共享引用，供统一右摇杆加速引擎按帧驱动。
        DisposableEffect(infoScrollState) {
            infoScrollByPx.value = { px -> infoScrollState.scrollBy(px) }
            onDispose { infoScrollByPx.value = null }
        }

        // 设置页 ConfirmDialog
        AnimatedVisibility(
            visible = sBridge.pendingConfirm != null,
            modifier = Modifier.fillMaxSize().zIndex(1f),
            enter = fadeIn(animationSpec = MotionSpec.DialogEnter)
                + scaleIn(initialScale = 0.92f, animationSpec = MotionSpec.DialogEnter,
                    transformOrigin = TransformOrigin.Center),
            exit = fadeOut(animationSpec = MotionSpec.DialogExit)
                + scaleOut(targetScale = 0.92f, animationSpec = MotionSpec.DialogExit,
                    transformOrigin = TransformOrigin.Center)
        ) {
            val snap = lastConfirmSnapshot
            if (snap != null) {
                val (confirm, action) = snap
                cn.mocabolka.run.ui.components.ConfirmDialog(
                    title = confirm.title,
                    text = confirm.text,
                    onConfirm = {
                        settingsControl.value = settingsControl.value.copy(pendingConfirm = null)
                        action()
                    },
                    onDismiss = {
                        settingsControl.value = settingsControl.value.copy(pendingConfirm = null)
                    }
                )
            }
        }

        // 设置页 InfoDialog
        AnimatedVisibility(
            visible = sBridge.pendingInfo != null,
            modifier = Modifier.fillMaxSize().zIndex(1f),
            enter = fadeIn(animationSpec = MotionSpec.DialogEnter)
                + scaleIn(initialScale = 0.92f, animationSpec = MotionSpec.DialogEnter,
                    transformOrigin = TransformOrigin.Center),
            exit = fadeOut(animationSpec = MotionSpec.DialogExit)
                + scaleOut(targetScale = 0.92f, animationSpec = MotionSpec.DialogExit,
                    transformOrigin = TransformOrigin.Center)
        ) {
            val snap = lastInfoSnapshot
            if (snap != null) {
                cn.mocabolka.run.ui.components.InfoDialog(
                    title = snap.title,
                    text = snap.text,
                    imageRes = snap.imageRes,
                    scrollState = infoScrollState,
                    onDismiss = {
                        settingsControl.value = settingsControl.value.copy(pendingInfo = null)
                    }
                )
            }
        }

        // 设置页 DropdownDialog
        AnimatedVisibility(
            visible = sBridge.openDropdownRow >= 0,
            modifier = Modifier.fillMaxSize().zIndex(1f),
            enter = fadeIn(animationSpec = MotionSpec.DialogEnter)
                + scaleIn(initialScale = 0.92f, animationSpec = MotionSpec.DialogEnter,
                    transformOrigin = TransformOrigin.Center),
            exit = fadeOut(animationSpec = MotionSpec.DialogExit)
                + scaleOut(targetScale = 0.92f, animationSpec = MotionSpec.DialogExit,
                    transformOrigin = TransformOrigin.Center)
        ) {
            DropdownDialog(
                title = sBridge.dropdownLabel,
                options = sBridge.dropdownOptions,
                optionLabel = sBridge.dropdownOptionLabel,
                selectedIndex = sBridge.dropdownSelectedIndex,
                listState = dropdownListState,
                onSelect = { v ->
                    sBridge.dropdownOnSelect(v)
                    settingsControl.value = settingsControl.value.copy(openDropdownRow = -1)
                },
                onDismiss = {
                    settingsControl.value = settingsControl.value.copy(openDropdownRow = -1)
                }
            )
        }

        // 统计页 DateRangePickerDialog
        AnimatedVisibility(
            visible = statsDatePickerOpen,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
            enter = fadeIn(animationSpec = MotionSpec.DialogEnter)
                + scaleIn(initialScale = 0.92f, animationSpec = MotionSpec.DialogEnter,
                    transformOrigin = TransformOrigin.Center),
            exit = fadeOut(animationSpec = MotionSpec.DialogExit)
                + scaleOut(targetScale = 0.92f, animationSpec = MotionSpec.DialogExit,
                    transformOrigin = TransformOrigin.Center)
        ) {
            // 日/月/年视图 → 原生 Material3 日期选择器（替代自绘 DateRangePickerDialog）。
            // 周视图入口已隐藏（周期滑块仅 DAILY/MONTHLY/YEARLY），此处不再处理 WEEKLY。
            NativeDatePickerDialog(
                period = statsPeriod,
                initialAnchorMs = statsAnchorMs,
                bridge = statsDatePickerBridge.value,
                onConfirm = { ms: Long -> statsAnchorMs = ms; statsDatePickerOpen = false; setDialogLayer(DialogLayer.NONE) },
                onDismiss = { statsDatePickerOpen = false; setDialogLayer(DialogLayer.NONE) }
            )
        }
    }

    }

/** 时段问候语：早 / 中 / 下午 / 晚上好（5-11 早，12-13 中，14-18 下午，19-4 晚上）。 */
private fun greetingByHour(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..18 -> "下午好"
        else -> "晚上好"
    }
}

@Composable
private fun CategoryTabsRow(
    tabs: List<String>,
    selected: String,
    focusedIndex: Int,
    onSelect: (String) -> Unit,
    /** 各分类应用数量（R11-4），key 为分类名。 */
    counts: Map<String, Int> = emptyMap()
) {
    if (tabs.isEmpty()) return
    var rowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val positions = remember { mutableStateMapOf<Int, Rect>() }
    val selectedIdx = tabs.indexOf(selected).coerceAtLeast(0)
    val scrollState = rememberScrollState()
    LaunchedEffect(focusedIndex, selectedIdx) {
        val t = positions[selectedIdx] ?: return@LaunchedEffect
        val center = t.left + t.width / 2f
        scrollState.animateScrollTo((center - 360f).toInt().coerceAtLeast(0))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.xs)
    ) {
        // 任务 5：移除分类下方滑动指示条（与分类事实未对应，视觉干扰）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .onGloballyPositioned { rowCoords = it },
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = tab == selected
                val count = counts[tab] ?: 0
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Strong)
                        )
                        .clickable { onSelect(tab) }
                        .padding(horizontal = Dimens.md, vertical = Dimens.xs + 2.dp)
                        .onGloballyPositioned { c ->
                            val r = rowCoords ?: return@onGloballyPositioned
                            val left = c.positionInWindow().x - r.positionInWindow().x
                            positions[index] = Rect(
                                left, 0f, left + c.size.width, c.size.height.toFloat()
                            )
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 分类数量徽标（R11-4）
                        if (count > 0) {
                            Spacer(Modifier.size(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (count > 999) "999+" else count.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppList(
    apps: List<AppModel>,
    focusedPackage: String?,
    onFocus: (String) -> Unit,
    onLaunch: (AppModel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    favorites: Set<String>,
    badgeOf: (String) -> Int,
    query: String,
    state: LazyListState,
    isEmpty: Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
    /** 空列表时的次级提示（如收藏为空引导），可选（R11-5）。 */
    emptyHint: String? = null,
    /** 空列表时：未授予 QUERY_ALL_PACKAGES 则提示授权；已授权则显示重新扫描按钮。 */
    queryAllGranted: Boolean = true,
    onOpenPermissionSettings: () -> Unit = {},
    onRescan: () -> Unit = {},
    showGamepadHints: Boolean = false,
    /** 今日时长查询（R13）：预聚合 Map，按包名 O(1) 读取。 */
    todayUsageMap: Map<String, Long> = emptyMap(),
    /** 减少动态效果（无障碍）：冻结入场/光晕动画。 */
    reduceMotion: Boolean = false,
    /** 入场重放键：分类/搜索变化时改变以触发逐行错峰入场。 */
    entranceKey: String = ""
) {
    if (isEmpty || apps.isEmpty()) {
        val emptyButtonInteraction = remember { MutableInteractionSource() }
        val emptyButtonFocused by emptyButtonInteraction.collectIsFocusedAsState()
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
                )
                // 次级提示（如"收藏"为空）：空串/空白时不渲染，避免裸按键文本占位。
                if (!emptyHint.isNullOrBlank()) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                // 空列表时提供可聚焦操作按钮，避免手柄无焦点可移
                if (emptyText != "没有匹配的应用" && emptyText != "收藏夹为空") {
                    val buttonText = if (!queryAllGranted) "前往授权" else "重新扫描"
                    wrapFocusBorder(
                        focused = showGamepadHints && emptyButtonFocused,
                        modifier = Modifier.focusable(true, emptyButtonInteraction)
                    ) {
                        ReverieFilledButton(
                            onClick = {
                                if (!queryAllGranted) onOpenPermissionSettings() else onRescan()
                            },
                            text = buttonText
                        )
                    }
                }
            }
        }
        return
    }


    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Dimens.xxs),
        verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
    ) {
        // 检测快速滚动：滚动中时跳过入场动画，让列表立即展示（跟手），
        // 松手后新出现的项恢复正常错峰入场。
        val scrolling = state.isScrollInProgress
        itemsIndexed(apps, key = { _, it -> it.packageName }) { index, app ->
            AppListItem(
                app = app,
                isFocused = app.packageName == focusedPackage,
                isFavorite = app.packageName in favorites,
                badgeCount = badgeOf(app.packageName),
                highlight = query,
                onFocus = { onFocus(app.packageName) },
                onLaunch = { onLaunch(app) },
                onToggleFavorite = { onToggleFavorite(app.packageName) },
                showGamepadHints = showGamepadHints,
                todayUsage = todayUsageMap[app.packageName] ?: 0L,
                reduceMotion = reduceMotion,
                entranceIndex = index,
                entranceKey = entranceKey,
                skipEntrance = scrolling
            )
        }
    }
}



/**
 * 设置页手柄事件分发（任务 24/25 重构后）。
 *
 * SettingsPage 内部已自管理：
 * - 单行点击（开关/按钮/下拉打开）
 * - DropdownDialog 内部 D-pad ↑↓/A/B/Enter/Esc
 * - 视觉焦点
 *
 * 故此函数仅负责 **导航 + 列表滚动**：
 * - UP / DOWN：上/下移动焦点行（±1）
 * - LEFT / RIGHT：保留（设置页无跨栏语义，但保留以兼容老手柄的"按下 D-pad RIGHT"）
 * - A / B / X / Y：放空（被内部消费）；其中 B 关闭设置页
 * - LB / RB：翻页（±3）
 * - LT / RT：放空（不与右摇杆冲突）
 * - RightStick：滚动设置页列表
 *
 * 焦点行索引约定（与 SettingsPage 内部 focusableIndices 同步）：
 * -  0 = 深色模式（Dropdown）
 * -  1 = 强制旋屏（Dropdown）
 * -  2 = 莫奈取色（Switch）
 * -  3 = 显示应用角标（Switch）
 * -  4 = 穿透背景（Switch）
 * -  5 = 挖孔屏适配（Switch）
 * -  6 = 显示系统应用（Switch）
 * -  7 = 排序方式（Dropdown）
 * -  8 = 导出应用列表（Button）
 * -  9 = 导入分类映射（Button）
 * - 10 = 清除分类映射（Button）
 * - 11 = 极致省电（Switch）
 * - 12 = 动态氛围背景（Switch）
 * - 13 = 玻璃拟态（Switch）
 * - 14 = 减少动态效果（Switch）
 * - 15 = 重新扫描（Button，任务 21 从右上角移入列表）
 * - 16 = 管理权限（Button）
 * - 17 = 清空收藏（Button）
 * - 18 = 重置所有设置（Button）
 *
 * 跨栏语义（主页）：仅十字键(D-pad)允许左右跨栏（左/右详情面板）；
 *   左摇杆左右仅等同 LB/RB 切 App 分类，不允许跨栏。
 */
private fun handleSettingsEvent(
    event: GamepadEvent,
    currentRow: Int,
    haptics: Haptics,
    onMove: (newRow: Int) -> Unit,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit = {},
    maxRow: Int = 18,
    control: SettingsControlBridge =
        SettingsControlBridge(),
    dialogLayer: DialogLayer = DialogLayer.NONE
) {
    val minRow = 0
    // ── dialogLayer 参数驱动的事件路由 ──
    // 当 dialogLayer 非 NONE 时，说明 HomeScreen 事件循环已确认当前处于 dialog 打开状态，
    // 不再需要检查 control.pendingInfo/pendingConfirm/openDropdownRow 等内部状态（双保险）。
    // DialogLayer.SETTINGS_INFO / SETTINGS_CONFIRM：Info/Confirm 弹窗，仅 Back/Select 关闭
    if (dialogLayer == DialogLayer.SETTINGS_INFO || dialogLayer == DialogLayer.SETTINGS_CONFIRM) {
        when (event) {
            is GamepadEvent.Back -> control.onDismissOverlay()
            is GamepadEvent.Select -> control.onDismissOverlay()
            else -> { /* 其它按键在弹窗内不处理 */ }
        }
        return
    }
    // DialogLayer.SETTINGS_DROPDOWN：下拉弹窗，上下导航选项 + 确认/关闭
    if (dialogLayer == DialogLayer.SETTINGS_DROPDOWN) {
        when (event) {
            is GamepadEvent.Navigate -> {
                // 下拉弹窗内：同时接受 STICK 和 DPAD 来源的导航（修复十字键无法导航下拉选项）
                when (event.direction) {
                    Direction.UP -> control.onMove(-1)
                    Direction.DOWN -> control.onMove(1)
                    else -> { /* 左右不绑定 */ }
                }
            }
            is GamepadEvent.Select -> control.onConfirm()
            is GamepadEvent.Back -> control.onDismiss()
            is GamepadEvent.Favorite -> control.onDismiss()
            // 右摇杆：由 HomeScreen 事件循环的 DialogLayer.SETTINGS_DROPDOWN 分支直接处理
            // （此处无法访问 Composable 的 dropdownListState / coroutineScope）。
            else -> { /* 其它按键在弹窗内不处理 */ }
        }
        return
    }
    // dialogLayer == NONE：设置页无 dialog 打开，旧有逻辑保持不变
    // （保留对 control.pendingInfo/pendingConfirm/openDropdownRow 的双重守卫，安全冗余）
    when (event) {
        is GamepadEvent.Navigate -> {
            // 设置页仅左摇杆（STICK）导航，十字键不进入设置页。
            if (event.source == NavigateSource.STICK) when (event.direction) {
                Direction.UP -> { haptics.tick(); onMove((currentRow - 1).coerceAtLeast(minRow)) }
                Direction.DOWN -> { haptics.tick(); onMove((currentRow + 1).coerceAtMost(maxRow)) }
                // 设置页无跨栏语义；左/右等同上/下，符合"手柄只能纵向往返"的常用交互。
                Direction.LEFT, Direction.RIGHT -> { /* 保留不绑定，避免误触 */ }
            }
        }
        is GamepadEvent.Back -> onDismiss()
        is GamepadEvent.Shoulder -> {
            // LB/RB：设置项翻页（±3）
            val page = 3
            val delta = if (event.side == Side.LEFT) -page else page
            onMove((currentRow + delta).coerceIn(minRow, maxRow))
        }
        // 右摇杆：不在此处处理，由 HomeScreen 引擎统一分发（避免双重滚动）。
        // A 键：激活当前焦点设置项（开关切换 / 打开下拉 / 触发按钮）—— 任务 1 核心修复
        is GamepadEvent.Select -> control.onActivate(currentRow)
        // Y / LT / RT：放空（语义保留）
        is GamepadEvent.Search,
        is GamepadEvent.Trigger -> { /* 无操作 */ }
        // X 键：任务 33 —— 打开当前焦点设置项的信息说明弹窗
        is GamepadEvent.Favorite -> onFavorite()
        // 其它按键在设置页不处理。
        else -> { /* 无操作 */ }
    }
}

/**
 * 列表两侧的肩键角标（LB / RB）与十字骑缝箭头、搜索框 Y 键位提示，
 * 现已统一收敛到 [cn.mocabolka.run.ui.components.KeyBadge]（见 ui/components/KeyBadge.kt）。
 */



/**
 * 穿透背景：读取系统默认桌面壁纸并铺满主界面底层。
 * 仅展示默认壁纸，不做横竖屏旋转处理。
 */
@SuppressLint("MissingPermission")
@Composable
private fun WallpaperBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wallpaperBitmap: ImageBitmap? = remember {
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            (wm.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
        }.getOrNull()
    }
    if (wallpaperBitmap != null) {
        Image(
            painter = BitmapPainter(wallpaperBitmap),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * 顶级 Tab 枚举：主界面 / 设置 平行切换（替代原浮层 SettingsDialog）。
 * 切换时使用 AnimatedContent + slideInHorizontally + fadeIn 过渡。
 */
private enum class MainTab { HOME, STATS, SEARCH, SETTINGS }

/**
 * R13 省电：观察宿主 Lifecycle，返回应用是否处于「可见」状态（STARTED/RESUMED）。
 * 用于驱动氛围背景等无限动画在后台/熄屏时暂停，消除空闲 GPU 负载。
 */
@Composable
private fun rememberAppVisible(): State<Boolean> {
    val owner = LocalLifecycleOwner.current
    val visible = remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            visible.value = event == Lifecycle.Event.ON_START ||
                    event == Lifecycle.Event.ON_RESUME
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return visible
}

/** 主页 Tab 内左右焦点侧：主列表 ↔ 右侧详情面板（21:9 宽屏专用） */
private enum class FocusSide { LEFT_LIST, RIGHT_PANEL }

/**
 * 详情面板内可聚焦按钮数（0=启动 / 1=收藏 / 2=信息）。
 * 卸载(3) / 强制停止(4) 已在 AppDetailPanel 中暂时隐藏（无法不跳设置即执行），
 * 故焦点导航上界收窄到 3；若日后恢复，改回 5 并取消 AppDetailPanel 中的注释。
 */
private const val DETAIL_BUTTON_COUNT = 3

// 右摇杆滚动物理常量已归一化至 cn.mocabolka.run.gamepad.RightStickScroll（唯一真源），
// 主页引擎与子页面 SubPageGamepad 共用，此处不再各自定义。

/**
 * Tab = HOME 时的主列表区（21:9 宽屏自适应 / 中等屏浮层）。
 * 抽出独立组件以配合 AnimatedContent 切换。
 */
@Composable
private fun HomeTabContent(
    isWideScreen: Boolean,
    detailSideWidth: Dp,
    gamepadConnected: Boolean,
    categoryTabs: List<String>,
    selectedCategory: String,
    focusedChip: Int,
    onSelectCategory: (String) -> Unit,
    query: String,
    visibleList: List<AppModel>,
    focusedPackage: String?,
    onFocus: (String) -> Unit,
    onLaunch: (AppModel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    favorites: Set<String>,
    badgeOf: (String) -> Int,
    currentListState: LazyListState,
    isEmpty: Boolean,
    emptyText: String,
    emptyHint: String,
    queryAllGranted: Boolean,
    onOpenPermissionSettings: () -> Unit,
    onRescan: () -> Unit,
    categoryCounts: Map<String, Int>,
    focusedApp: AppModel?,
    onToggleFavoriteFocused: (String) -> Unit,
    launching: Boolean,
    onOpenInfo: (AppModel) -> Unit,
    onUninstall: (AppModel) -> Unit,
    onForceStop: (AppModel) -> Unit,
    searchInfo: String?,
    todayUsageMap: Map<String, Long>,
    weeklyUsageMap: Map<String, Long>,
    showUsageHint: Boolean,
    /** 当前焦点侧（LEFT_LIST / RIGHT_PANEL），用于右侧面板焦点态边框。 */
    focusSide: FocusSide = FocusSide.LEFT_LIST,
    /** 详情面板内当前焦点按钮索引（0..4），未聚焦时 -1。 */
    focusedDetailButton: Int = -1,
    /** 减少动态效果（省电/无障碍聚合后）：由 HomeScreen 计算后传入。 */
    effectiveReduceMotion: Boolean = false,
    /** 玻璃拟态（省电聚合后）：由 HomeScreen 计算后传入。 */
    effectiveGlass: Boolean = false,
    /** 穿透背景模式：详情面板 surface 进一步透明。 */
    wallpaperBehind: Boolean = false,
    /** 右侧详情面板滚动状态（lift 到上层，供右摇杆在右栏焦点时滚动）。 */
    detailScrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    /** 打开竖屏抽屉回调（点击列表项）。 */
    onOpenPortraitDrawer: () -> Unit = {},
    /** 详情面板内按钮聚焦变化回调（鼠标点击/手柄移动按钮时上报），用于建立右栏跨栏焦点态。 */
    onFocusedButtonChange: (Int) -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        if (isWideScreen) {
            // 使用 Box 作为父容器，让 KeyBadge（方向箭头）可以绝对定位在两栏正中间（骑缝）。
            // 左栏与右栏通过 Row 水平排列，浮块通过 BoxScope.align + offset 绝对定位。
            // 偏移计算：align(Center) 使浮块中心在父 Box 中心，再偏移
            // (父Box宽度/2 - 右栏宽度 - 浮块半宽) 使中心落在左栏右边缘。
            // 但父Box宽度在 Composable 中未知，使用 maxWidth（BoxWithConstraints 提供）。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ContentHorizontal)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier.weight(1.4f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            // 分类 Tabs 行：左右两侧贴 LB / RB 键位提示
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                GamepadVisible {
                                    KeyBadge(text = "LB", modifier = Modifier.padding(end = Dimens.xs))
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    CategoryTabsRow(
                                        tabs = categoryTabs, selected = selectedCategory,
                                        focusedIndex = focusedChip, onSelect = onSelectCategory,
                                        counts = categoryCounts
                                    )
                                }
                                GamepadVisible {
                                    KeyBadge(text = "RB", modifier = Modifier.padding(start = Dimens.xs))
                                }
                            }
                            AppList(
                                apps = visibleList, focusedPackage = focusedPackage,
                                onFocus = onFocus, onLaunch = onLaunch,
                                onToggleFavorite = onToggleFavorite, favorites = favorites,
                                badgeOf = badgeOf, query = "", state = currentListState,
                                isEmpty = isEmpty, emptyText = emptyText, emptyHint = emptyHint,
                                queryAllGranted = queryAllGranted,
                                onOpenPermissionSettings = onOpenPermissionSettings,
                                onRescan = onRescan, showGamepadHints = gamepadConnected,
                                todayUsageMap = todayUsageMap,
                                reduceMotion = effectiveReduceMotion,
                                entranceKey = "$selectedCategory|$query",
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    }
                    // 左栏与右栏之间的间距（8dp 视觉呼吸，跨栏浮块已移除不再需要 24dp 骑缝空间）。
                    Box(
                        modifier = Modifier.width(8.dp),
                        contentAlignment = Alignment.Center
                    ) {}
                    AppDetailPanel(
                        app = focusedApp,
                        isFavorite = focusedApp?.let { it.packageName in favorites } ?: false,
                        onToggleFavorite = onToggleFavoriteFocused, onLaunch = onLaunch,
                        launching = launching, onOpenInfo = onOpenInfo, onUninstall = onUninstall,
                    onForceStop = onForceStop,
                    badgeCount = focusedApp?.let { badgeOf(it.packageName) } ?: 0,
                    searchInfo = searchInfo, showUsageHint = showUsageHint,
                    todayUsage = todayUsageMap[focusedApp?.packageName] ?: 0L,
                    weeklyUsage = weeklyUsageMap[focusedApp?.packageName] ?: 0L,
                    glass = effectiveGlass,
                    reduceMotion = effectiveReduceMotion,
                    scrollState = detailScrollState,
                    wallpaperBehind = wallpaperBehind,
                    focusedButtonIndex = if (focusSide == FocusSide.RIGHT_PANEL) focusedDetailButton else -1,
                    onFocusedButtonChange = onFocusedButtonChange,
                        modifier = Modifier
                            .width(detailSideWidth)
                            .fillMaxHeight()
                            .focusBorder(
                                focusSide == FocusSide.RIGHT_PANEL,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(Dimens.lg)
                            )
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxSize().padding(horizontal = Dimens.ContentHorizontal)
            ) {
                // 分类 Tabs 行：左右两侧贴 LB / RB 键位提示
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.xxs)
                ) {
                    GamepadVisible {
                        KeyBadge(text = "LB", modifier = Modifier.padding(end = Dimens.xs))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryTabsRow(
                            tabs = categoryTabs, selected = selectedCategory,
                            focusedIndex = focusedChip, onSelect = onSelectCategory,
                            counts = categoryCounts
                        )
                    }
                    GamepadVisible {
                        KeyBadge(text = "RB", modifier = Modifier.padding(start = Dimens.xs))
                    }
                }
                AppList(
                    apps = visibleList, focusedPackage = focusedPackage,
                    // 竖屏：点击列表项在选中的同时展开底部详情抽屉（触摸直觉）
                    onFocus = { pkg -> onFocus(pkg); onOpenPortraitDrawer() },
                    onLaunch = onLaunch,
                    onToggleFavorite = onToggleFavorite, favorites = favorites,
                    badgeOf = badgeOf, query = "", state = currentListState,
                    isEmpty = isEmpty, emptyText = emptyText, emptyHint = emptyHint,
                    queryAllGranted = queryAllGranted,
                    onOpenPermissionSettings = onOpenPermissionSettings,
                    onRescan = onRescan, showGamepadHints = gamepadConnected,
                    todayUsageMap = todayUsageMap,
                    reduceMotion = effectiveReduceMotion,
                    entranceKey = "$selectedCategory|$query",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 竖屏详情抽屉已移至外层 Box（HomeScreen 级），与 dialog 覆盖层同级，
            // 黑色半透明遮罩覆盖全局 Tab 标题栏与状态栏，实现沉浸效果。
            // 此处仅保留占位注释，实际渲染见外层 Box 中的 PortraitDetailOverlay。
        }

        // 主页（HOME）专属底部按键指示栏：与主体隔离的独立槽位（规则2）。
        // 跨栏逻辑已整体移除（LS/RS 不再跨栏），故不再显示 LS/RS 提示。
        // 按焦点态分两套提示（R4 补全 Y 收藏 + 详情态语义）：
        //  - 列表态(LEFT_LIST)：A 进详情 / X 直接启动 / Y 收藏 / B 返回；
        //  - 详情态(RIGHT_PANEL)：A 启动·确认焦点钮 / 摇杆 移动焦点 / B 返回列表。
        // 规则3：详情内各按钮的 A 角标已在 AppDetailPanel 按钮旁给出，此处不复现。
        // 规则4：不显示任何方向键/滚动提示。
        // 仅在手柄已连接时显示：未连手柄用户无键位提示需求，整体隐藏。
        // 使用统一的 GamepadBottomHintBar 组件（自动响应手柄插拔，与其他页面一致）。
        GamepadBottomHintBar(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (focusSide == FocusSide.RIGHT_PANEL) {
                Hint(KeyToken.A, "启动")
                HintGap()
                Hint(KeyToken.B, "返回")
            } else {
                Hint(KeyToken.A, "详情")
                Hint(KeyToken.X, "启动")
                Hint(KeyToken.Y, "收藏")
                HintGap()
                Hint(KeyToken.B, "返回")
            }
        }
        }
    }
}

/**
 * Tab = SEARCH 时的搜索页：顶部搜索框 + 搜索结果列表（复用 AppList）。
 * 搜索框默认聚焦，方向键浏览结果，A 键启动。
 */
@Composable
private fun SearchTabContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    searchFocusRequester: FocusRequester,
    view: View,
    visibleList: List<AppModel>,
    listState: LazyListState,
    focusedPackage: String?,
    onFocus: (String) -> Unit,
    onLaunch: (AppModel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    favorites: Set<String>,
    badgeOf: (String) -> Int,
    isEmpty: Boolean,
    emptyText: String,
    showGamepadHints: Boolean,
    /** 输入框是否处于激活态：决定底部提示栏文案（激活态=Y退出输入/B收键盘；列表态=Y搜索/B返回）。 */
    inputActive: Boolean = false,
    /** 软键盘是否可见：INPUT 态下用于判断 B/Y 是否指向同一事件（键盘隐藏时 B 退输入 与 Y 退出输入 等价）。 */
    keyboardVisible: Boolean = false,
    todayUsageMap: Map<String, Long>,
    /** 减少动态效果（省电/无障碍聚合后）：由 HomeScreen 计算后传入。 */
    effectiveReduceMotion: Boolean = false,
    isWideScreen: Boolean = true,
    portraitDrawerOpen: Boolean = false,
    onOpenPortraitDrawer: () -> Unit = {},
    /** 搜索框焦点变化（软键盘显隐）回调，由 HomeScreen 维护 keyboardVisible 状态。 */
    onKeyboardVisibleChange: (Boolean) -> Unit = {},
    /** 搜索结果总数（用于显示计数标签）。 */
    resultCount: Int = 0
) {
    val isSearching = query.isNotBlank()
    // 搜索页：左摇杆完全穿透搜索框 ⇄ 应用列表（由 searchFocusTarget 状态机驱动）。
    // 进入搜索页默认 INPUT 态：下面 LaunchedEffect(inputActive) 负责**真正聚焦搜索框并弹出软键盘**，
    // 修复"按 Y 呼出软键盘但搜索框未激活 → 有键盘却打不了字"的 bug。
    LaunchedEffect(inputActive) {
        if (inputActive) {
            // 真正请求焦点（让 TextField 成为焦点持有者，光标可见、IME 可输入）
            delay(60.milliseconds) // 等 TextField 组合完成再请求，避免 requestFocus 落空
            runCatching { searchFocusRequester.requestFocus() }
            // 显式弹出软键盘（部分 ROM 仅靠 focus 不会自动弹）
            val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            onKeyboardVisibleChange(true)
        } else {
            runCatching { searchFocusRequester.freeFocus() }
            val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            onKeyboardVisibleChange(false)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ContentHorizontal)
            .padding(top = Dimens.xs)
    ) {
        // 搜索框：Material Design 3 规范，OutlinedTextField + 圆角 + focus 态
        // 手柄连接时，leadingIcon 替换为 Y 按键浮块提示。
        // 注意：不再叠加额外的 .focusable()（OutlinedTextField 自带 focusable），
        // 否则会与内部 TextField 焦点系统冲突导致"获焦但无法输入"。
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocusRequester)
                .onFocusChanged { state ->
                    // 搜索框获焦 = 软键盘可见；失焦 = 收起输入法。
                    if (state.isFocused) {
                        onKeyboardVisibleChange(true)
                    } else {
                        onKeyboardVisibleChange(false)
                        val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                        imm?.hideSoftInputFromWindow(view.windowToken, 0)
                    }
                }
                .padding(bottom = Dimens.sm),
            placeholder = { Text("搜索应用 / 游戏") },
            leadingIcon = {
                if (showGamepadHints) {
                    KeyBadge(KeyToken.Y)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(Dimens.md),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    // Enter / 搜索键：启动搜索结果首项（若存在焦点应用）
                    val pkg = focusedPackage
                    val app = pkg?.let { visibleList.firstOrNull { a -> a.packageName == pkg && a.installed } }
                    if (app != null) onLaunch(app)
                },
                onDone = { view.clearFocus() }
            )
        )
        // 搜索结果计数标签（空词时显示"全部应用 (N)"）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.xs, vertical = Dimens.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isSearching) "搜索结果 ($resultCount)" else "全部应用 ($resultCount)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            if (isSearching && resultCount == 0) {
                Text(
                    text = "尝试其他关键词",
                    style = MaterialTheme.typography.labelSmall,
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong)
                )
            }
        }
        // 搜索结果列表
        AppList(
            apps = visibleList,
            focusedPackage = focusedPackage,
            // 竖屏：选中搜索结果的同时展开底部详情抽屉（与主页一致）
            onFocus = { pkg -> onFocus(pkg); if (!isWideScreen && !portraitDrawerOpen) onOpenPortraitDrawer() },
            onLaunch = onLaunch,
            onToggleFavorite = onToggleFavorite,
            favorites = favorites,
            badgeOf = badgeOf,
            query = query,
            state = listState,
            isEmpty = isEmpty,
            emptyText = emptyText,
            emptyHint = "",
            showGamepadHints = showGamepadHints,
            todayUsageMap = todayUsageMap,
            reduceMotion = effectiveReduceMotion,
            entranceKey = "search|$query",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        // 底部操作提示栏（与主体隔离，规则2）：仅手柄连接时显示。
        // 规则3：Y 键已在搜索框 leadingIcon 给出浮块，此处不复现；
        // 规则4：不显示方向键/滚动提示。
        // 输入框激活态：A 启动（首项）；
        //   - 软键盘可见：B 仅收键盘（仍激活）、Y 退出输入到列表，二者事件不同需分开；
        //   - 软键盘隐藏：B 退输入(态2) 与 Y 退出输入 指向同一事件 → 合并显示 "B/Y 退出输入"。
        // 列表态：A 启动 / Y 搜索 / B 返回（事件不同，分开）。
        GamepadBottomHintBar(
            modifier = Modifier.fillMaxWidth()
        ) {
            Hint(KeyToken.A, "启动")
            HintGap()
            if (inputActive) {
                if (keyboardVisible) {
                    Hint(KeyToken.Y, "退出输入")
                    Hint(KeyToken.B, "收键盘")
                } else {
                    Hint("B/Y", "退出输入")
                }
            } else {
                Hint(KeyToken.Y, "搜索")
                Hint(KeyToken.B, "返回")
            }
        }
    }
}

/**
 * Tab = SETTINGS 时直接渲染 SettingsPage（已是 Tab 一级，无浮层遮罩、无返回按钮）。
 */
@Composable
private fun SettingsTabContent(
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    onRescan: () -> Unit,
    isScanning: Boolean,
    appVersion: String,
    onClearFavorites: () -> Unit,
    onResetSettings: () -> Unit,
    showBadges: Boolean,
    onShowBadgesChange: (Boolean) -> Unit,
    useMonet: Boolean,
    onUseMonetChange: (Boolean) -> Unit,
    wallpaperBehind: Boolean,
    onWallpaperBehindChange: (Boolean) -> Unit,
    cutoutAdapt: Boolean,
    onCutoutAdaptChange: (Boolean) -> Unit,
    showSystemApps: Boolean = false,
    onShowSystemAppsChange: (Boolean) -> Unit = {},
    onExportCategories: () -> Unit = {},
    onImportCategories: () -> Unit = {},
    onClearCategories: () -> Unit = {},
    dynamicBackground: Boolean,
    onDynamicBackgroundChange: (Boolean) -> Unit,
    glassSurface: Boolean,
    onGlassSurfaceChange: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChange: (Boolean) -> Unit,
    orientationMode: OrientationMode,
    onOrientationModeChange: (OrientationMode) -> Unit,
    focusedRow: Int,
    onFocusedRowChange: (Int) -> Unit,
    listState: LazyListState,
    onManagePermissions: () -> Unit,
    infoTrigger: MutableState<Int?> =
        remember { mutableStateOf(null) },
    onRowCountChange: (Int) -> Unit = {},
    onClose: () -> Unit = {},
    controlBridge: MutableState<SettingsControlBridge> =
        remember { mutableStateOf(SettingsControlBridge()) },
    onAboutClick: () -> Unit = {}
) {
    SettingsPage(
        onAboutClick = onAboutClick,
        darkMode = darkMode, onDarkModeChange = onDarkModeChange,
        sortMode = sortMode, onSortModeChange = onSortModeChange,
        onRescan = onRescan, isScanning = isScanning,
        appVersion = appVersion, onClearFavorites = onClearFavorites,
        onResetSettings = onResetSettings,
        showBadges = showBadges, onShowBadgesChange = onShowBadgesChange,
        useMonet = useMonet, onUseMonetChange = onUseMonetChange,
        wallpaperBehind = wallpaperBehind, onWallpaperBehindChange = onWallpaperBehindChange,
        cutoutAdapt = cutoutAdapt, onCutoutAdaptChange = onCutoutAdaptChange,
        showSystemApps = showSystemApps, onShowSystemAppsChange = onShowSystemAppsChange,
        onExportCategories = onExportCategories,
        onImportCategories = onImportCategories,
        onClearCategories = onClearCategories,
        dynamicBackground = dynamicBackground, onDynamicBackgroundChange = onDynamicBackgroundChange,
        glassSurface = glassSurface, onGlassSurfaceChange = onGlassSurfaceChange,
        reduceMotion = reduceMotion, onReduceMotionChange = onReduceMotionChange,
        orientationMode = orientationMode, onOrientationModeChange = onOrientationModeChange,
        focusedRow = focusedRow, onFocusedRowChange = onFocusedRowChange,
        listState = listState, onManagePermissions = onManagePermissions,
        infoTrigger = infoTrigger,
        onRowCountChange = onRowCountChange,
        controlBridge = controlBridge,
        onClose = onClose,
        onDismiss = { /* Tab 模式无浮层，不响应 dismiss */ }
    )
}

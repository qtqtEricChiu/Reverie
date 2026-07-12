package cn.mocabolka.run.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import cn.mocabolka.run.ui.theme.MotionSpec
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toBitmap
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.gamepad.GamepadDetector
import cn.mocabolka.run.gamepad.GamepadEvent
import cn.mocabolka.run.gamepad.NavigateSource
import cn.mocabolka.run.gamepad.Side
import cn.mocabolka.run.compat.UsageStatsPermissionHelper
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.ui.components.AboutPage
import cn.mocabolka.run.ui.components.AmbientBackground
import cn.mocabolka.run.ui.components.AppDetailPanel
import cn.mocabolka.run.ui.components.AppListItem
import cn.mocabolka.run.ui.components.LaunchOverlay
import cn.mocabolka.run.ui.components.LicensesPage
import cn.mocabolka.run.ui.components.SettingsPage
import cn.mocabolka.run.ui.components.SplashOverlay
import cn.mocabolka.run.ui.components.StatusBar
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.viewmodel.HomeViewModel
import cn.mocabolka.run.viewmodel.SortMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    val filterChips by viewModel.filterChips.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val categoryTabs by viewModel.categoryTabs.collectAsState()
    val focusedChip by viewModel.focusedChip.collectAsState()
    val launching by viewModel.launching.collectAsState()
    val isBooting by viewModel.isBooting.collectAsState()
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
    val searchFocusRequester = remember { FocusRequester() }
    // 任务：搜索框聚焦请求计数器（Y 键 / 键盘 Y 递增），由下方 LaunchedEffect 真正执行聚焦 + 弹出输入法
    var searchFocusRequest by remember { mutableStateOf(0) }
    // 搜索页专用的 FocusRequester，与主页 SearchFieldOrList 中的 searchFocusRequester 隔离，
    // 避免同一个 FocusRequester 绑定两个 TextField 导致 requestFocus 失效。
    val searchTabFocusRequester = remember { FocusRequester() }
    /** 搜索页焦点状态：true=焦点在输入框，false=焦点在应用列表。按 Y 来回切换。 */
    var searchFocusOnInput by remember { mutableStateOf(true) }

    // 屏幕宽度判定（21:9 宽屏 vs 中等屏）：用于事件循环中决定方向键行为（十字右键是否切到右侧面板）
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 720

    // 手柄连接状态：用于控制上下文键位角标（LB/RB/X/Menu）的显隐
    val gamepadConnected by GamepadDetector.gamepadConnectedFlow(context)
        .collectAsState(initial = GamepadDetector.isGamepadConnected())

    // 顶部 Tab 模式：主界面 / 设置 平行切换，不再是浮层
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    // 设置页子页面导航：null=设置主列表，非 null=子页面
    var settingsSubPage by remember { mutableStateOf<SettingsSubPage?>(null) }
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
    // 任务 11：统计页左右焦点侧（LEFT_LIST = 总计 / RIGHT_PANEL = 应用排行）
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
    }
    // 切换统计周期时排行内容已变，焦点归零
    LaunchedEffect(statsPeriod) { statsFocusIndex = 0 }
    // 当前 Tab 可变引用：右摇杆滚动引擎在 LaunchedEffect(Unit) 中捕获首帧值会过期
    val currentTabRef = remember { mutableStateOf(currentTab) }
    LaunchedEffect(currentTab) { currentTabRef.value = currentTab }
    // 右摇杆平滑加速滚动引擎共享状态（任务 14）
    val rightStickY = remember { mutableStateOf(0f) }
    val lastRightStickAt = remember { mutableStateOf(0L) }
    val sortMode by viewModel.sortMode.collectAsState()
    // 设置页焦点行索引：-3 = 重新扫描按钮 / 0..N = 设置项（Tab 模式无"返回"行）
    var settingsFocusRow by remember { mutableStateOf(0) }
    // 任务 33：手柄 X 键触发设置项信息弹窗的中转 state（HomeScreen 写、SettingsPage 读）
    val settingsInfoTrigger = remember { mutableStateOf<Int?>(null) }
    // 优化 3：设置页可聚焦项上限（由 SettingsPage 上报，避免焦点越界）
    var settingsMaxRow by remember { mutableStateOf(18) }
    // 设置页手柄控制桥：HomeScreen 全局事件分发器据此驱动 A 键激活 / 下拉翻页（任务 1 / 3 / 4）
    val settingsControl = remember {
        androidx.compose.runtime.mutableStateOf(cn.mocabolka.run.ui.components.SettingsControlBridge())
    }
    // 切换到设置 Tab 时，重置焦点到首项（深色模式），关闭子页面
    LaunchedEffect(currentTab) {
        if (currentTab == MainTab.SETTINGS) {
            settingsFocusRow = 0
            settingsSubPage = null
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
            kotlinx.coroutines.delay(4000)
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
    // 子页面（About/Licenses）滚动状态：右摇杆在子页面打开时滚动此列表
    val subPageListState = rememberLazyListState()
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

    LaunchedEffect(focusedPackage, selectedCategory) {
        val i = visibleList.indexOfFirst { it.packageName == focusedPackage }
        if (i >= 0) scope.launch { currentListState.animateScrollToItem(i) }
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

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            // ── 全局事件（任意 Tab，含设置页，均生效）──
            // 扳机 LT/RT：切换顶层 Tab（到边界不动）；含从设置页切出页面。
            if (event is GamepadEvent.Trigger) {
                haptics.tick()
                val order = listOf(MainTab.HOME, MainTab.STATS, MainTab.SEARCH, MainTab.SETTINGS)
                val idx = order.indexOf(currentTab)
                if (idx >= 0) {
                    val nextIdx = if (event.side == Side.LEFT) {
                        (idx - 1).coerceAtLeast(0)
                    } else {
                        (idx + 1).coerceAtMost(order.lastIndex)
                    }
                    if (nextIdx != idx) currentTab = order[nextIdx]
                }
                return@collectLatest
            }
            // 统计页日期选择器打开时：所有导航/确认/取消事件路由到选择器桥接（完全手柄适配）。
            // 注意：日期选择器仅左摇杆（STICK）导航，十字键不进入。
            if (statsDatePickerOpen) {
                when (event) {
                    is GamepadEvent.Navigate -> {
                        if (event.source == NavigateSource.STICK) {
                            statsDatePickerBridge.value.onMove(event.direction)
                        }
                    }
                    is GamepadEvent.Select -> statsDatePickerBridge.value.onConfirm()
                    is GamepadEvent.Back -> statsDatePickerBridge.value.onCancel()
                    else -> { /* 其它按键在对话框打开时不处理 */ }
                }
                return@collectLatest
            }
            // 设置 Tab 时：右摇杆滚动由引擎统一处理（不在此 return），
            // 其他事件由 handleSettingsEvent 分发。
            if (currentTab == MainTab.SETTINGS) {
                if (event is GamepadEvent.RightStick) {
                    rightStickY.value = event.dy
                    lastRightStickAt.value = android.os.SystemClock.uptimeMillis()
                } else {
                    handleSettingsEvent(
                        event = event,
                        currentRow = settingsFocusRow,
                        onMove = { newRow -> settingsFocusRow = newRow },
                        onDismiss = { currentTab = MainTab.HOME },
                        onFavorite = { settingsInfoTrigger.value = settingsFocusRow },
                        maxRow = settingsMaxRow,
                        control = settingsControl.value
                    )
                }
                return@collectLatest
            }
            when (event) {
                is GamepadEvent.Navigate -> {
                    haptics.tick()
                    when (currentTab) {
                        MainTab.STATS -> {
                            // 统计页：仅左摇杆（STICK）上下移动排行列表焦点；
                            // 宽屏（双栏）时仅十字键（DPAD）左右跨栏，左摇杆左右 no-op；
                            // 非宽屏：左右 no-op（无切栏语义）。
                            when (event.direction) {
                                Direction.UP, Direction.DOWN -> {
                                    if (event.source == NavigateSource.STICK) {
                                        if (event.direction == Direction.UP) {
                                            statsFocusIndex = (statsFocusIndex - 1).coerceAtLeast(0)
                                        } else {
                                            statsFocusIndex = (statsFocusIndex + 1).coerceAtMost((statsEntryCount - 1).coerceAtLeast(0))
                                        }
                                    }
                                }
                                Direction.LEFT, Direction.RIGHT -> {
                                    if (isWideScreen && event.source == NavigateSource.DPAD) {
                                        statsFocusSide = if (event.direction == Direction.RIGHT)
                                            FocusSide.RIGHT_PANEL else FocusSide.LEFT_LIST
                                    }
                                }
                            }
                        }
                        MainTab.HOME -> {
                            // 主页 Tab：仅左摇杆（STICK）上下移动焦点；
                            // 宽屏时仅十字键（DPAD）左右跨栏，左摇杆左右 no-op；
                            // 窄屏：仅左摇杆切 App 分类，十字 no-op。
                            when (event.direction) {
                                Direction.UP, Direction.DOWN -> {
                                    if (event.source == NavigateSource.STICK) {
                                        if (focusSide == FocusSide.RIGHT_PANEL) {
                                            focusedDetailButton =
                                                if (event.direction == Direction.UP)
                                                    (focusedDetailButton - 1 + DETAIL_BUTTON_COUNT) % DETAIL_BUTTON_COUNT
                                                else (focusedDetailButton + 1) % DETAIL_BUTTON_COUNT
                                        } else if (event.direction == Direction.UP) {
                                            viewModel.moveInCurrentList(-1)
                                        } else {
                                            viewModel.moveInCurrentList(1)
                                        }
                                    }
                                }
                                Direction.LEFT, Direction.RIGHT -> {
                                    if (isWideScreen && event.source == NavigateSource.DPAD) {
                                        focusSide = if (event.direction == Direction.RIGHT)
                                            FocusSide.RIGHT_PANEL else FocusSide.LEFT_LIST
                                    } else if (!isWideScreen && event.source == NavigateSource.STICK) {
                                        // 窄屏仅左摇杆切分类
                                        viewModel.cycleCategory(
                                            if (event.direction == Direction.LEFT) -1 else 1
                                        )
                                    }
                                }
                            }
                        }
                        MainTab.SEARCH -> {
                            // 搜索页：仅左摇杆（STICK）导航列表，十字键不进入搜索页。
                            // 上下移动列表焦点；左右 no-op。
                            if (event.source == NavigateSource.STICK) when (event.direction) {
                                Direction.UP, Direction.DOWN -> {
                                    searchFocusOnInput = false
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
                    if (viewModel.searchActive.value) {
                        haptics.tick()
                    } else if (currentTab == MainTab.HOME && focusSide == FocusSide.RIGHT_PANEL) {
                        // 右侧详情面板：A 键触发当前 focus 按钮
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
                    } else if (currentTab == MainTab.HOME && !isWideScreen && !portraitDrawerOpen) {
                        // 竖屏主页：A 键第一次展开底部详情抽屉（预览详情），
                        // 抽屉已展开时再按 A 才真正启动（两段式，避免误启动）。
                        haptics.click()
                        portraitDrawerOpen = true
                    } else if (currentTab == MainTab.SEARCH) {
                        // 搜索页：A 启动搜索焦点应用（独立焦点状态，不与主页串扰）。
                        haptics.click()
                        val pkg = searchFocusedPackage
                        val app = pkg?.let { apps.firstOrNull { it.packageName == pkg && it.installed } }
                        if (app != null) viewModel.launchApp(app)
                    } else if (currentTab == MainTab.HOME) {
                        // 主页：A 启动聚焦应用。其它页（统计/设置）不响应启动。
                        haptics.click()
                        viewModel.launchFocused()
                    } else {
                        haptics.tick()
                    }
                }
                is GamepadEvent.Back -> {
                    when {
                        // 子页面（About/Licenses）打开时：B 键返回上一级子页面或关闭子页面
                        currentTab == MainTab.SETTINGS && settingsSubPage != null -> {
                            if (settingsSubPage == SettingsSubPage.LICENSES) {
                                settingsSubPage = SettingsSubPage.ABOUT
                            } else {
                                settingsSubPage = null
                            }
                            haptics.tick()
                        }
                        // 搜索界面软键盘可见时：B 先收起软键盘，不返回主页/关闭搜索
                        keyboardVisible -> {
                            keyboardVisible = false
                            val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                            imm?.hideSoftInputFromWindow(view.windowToken, 0)
                            runCatching { searchFocusRequester.freeFocus() }
                            haptics.tick()
                        }
                        // 搜索页内按 B：关闭搜索（若已激活）或切回主页（若搜索已关闭但 Tab 仍在 SEARCH）
                        currentTab == MainTab.SEARCH && viewModel.searchActive.value -> {
                            viewModel.closeSearch(); haptics.tick()
                        }
                        currentTab == MainTab.SEARCH -> {
                            // 搜索已关闭但 Tab 仍在 SEARCH：切回主页
                            currentTab = MainTab.HOME; haptics.tick()
                        }
                        // 设置页内若有弹窗（下拉/确认/信息）打开，Back 优先关闭弹窗而非退出设置页
                        currentTab == MainTab.SETTINGS && settingsControl.value.hasOpenDialog -> {
                            settingsControl.value.onDismiss()
                            settingsControl.value.onDismissOverlay()
                            haptics.tick()
                        }
                        currentTab != MainTab.HOME -> { currentTab = MainTab.HOME; haptics.tick() }
                        portraitDrawerOpen -> { portraitDrawerOpen = false; haptics.tick() }
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
                    if (currentTab == MainTab.HOME) {
                        viewModel.toggleFavoriteOfFocused(); haptics.tick()
                    } else haptics.tick()
                }
                is GamepadEvent.LeftStickPress -> {
                    // LS（左摇杆按压）：统计页打开日期选择器；其它页无操作
                    if (currentTab == MainTab.STATS) {
                        statsDatePickerOpen = true; haptics.tick()
                    } else haptics.tick()
                }
                is GamepadEvent.Search -> {
                    haptics.tick()
                    when (currentTab) {
                        // 搜索页内按 Y：切换焦点——搜索框 ⇄ 应用列表首项
                        MainTab.SEARCH -> {
                            searchFocusOnInput = !searchFocusOnInput
                            if (searchFocusOnInput) {
                                searchTabFocusRequester.requestFocus()
                            } else if (searchResults.isNotEmpty()) {
                                searchFocusedPackage = searchResults.first().packageName
                                scope.launch { searchListState.animateScrollToItem(0) }
                            }
                        }
                        // 其它页：Y 键无副作用（任务 7：Y 已解绑搜索 Tab 切换）
                        else -> { /* 无操作 */ }
                    }
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
                            val order = cn.mocabolka.run.ui.components.StatsPeriod.entries
                            val idx = order.indexOf(statsPeriod)
                            val delta = if (event.side == Side.LEFT) -1 else 1
                            statsPeriod = order[(idx + delta + order.size) % order.size]
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
            when (currentTabRef.value) {
                MainTab.STATS ->
                    // 宽屏双栏：根据焦点侧选择左栏（总计/图表）或右栏（排行）滚动。
                    // 左栏 statsLeftScrollState（ScrollState），右栏 statsListState（LazyListState）。
                    if (statsFocusSide == FocusSide.LEFT_LIST) {
                        statsLeftScrollState.scrollBy(delta)
                    } else {
                        statsListState.scrollBy(delta)
                    }
                MainTab.SEARCH -> searchListState.scrollBy(delta)
                MainTab.SETTINGS -> {
                    // 子页面打开时滚动子页面列表，否则滚动设置页列表
                    if (settingsSubPage != null) subPageListState.scrollBy(delta)
                    else settingsListState.scrollBy(delta)
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
        } else if (tab == MainTab.HOME && focusSide != FocusSide.RIGHT_PANEL) {
            val state = currentListStateRef.value
            val idx = state.firstVisibleItemIndex
            val list = visibleList
            if (idx < list.size) {
                viewModel.setFocused(list[idx].packageName)
            }
        }
    }

    // 任务：右摇杆滚动引擎（重写）
    // - 轻推即持续滚动，并在推下的瞬间给一次震动反馈；
    // - 只要一直推着，速度上限随按住时长线性增长（越推越快）；
    // - 松手后平滑减速停止。覆盖全部页面及分栏。
    LaunchedEffect(Unit) {
        var speed = 0f
        var holdTime = 0f
        var wasScrolling = false
        var lastTickAt = 0L
        while (true) {
            val now = android.os.SystemClock.uptimeMillis()
            val released = now - lastRightStickAt.value > 120L
            val dy = if (released) 0f else rightStickY.value
            val dt = 1f / 60f
            val mag = abs(dy).coerceIn(0f, 1f)
            if (mag > 0.12f) {
                holdTime += dt
                // 加速度上限随按住时长增长（最久约 +5000px/s），保持越久滚动越快
                val grow = (holdTime * 1600f).coerceAtMost(5000f)
                val cap = RIGHT_STICK_MAX_SPEED * mag + grow
                speed += dy.sign * RIGHT_STICK_ACCEL * dt
                speed = speed.coerceIn(-cap, cap)
                // 轻推瞬间给震动；持续滚动时每 ~450ms 再轻触一次反馈
                if (!wasScrolling) {
                    haptics.tick(); lastTickAt = now
                } else if (now - lastTickAt > 450L) {
                    haptics.tick(); lastTickAt = now
                }
            } else {
                holdTime = 0f
                // 惯性衰减：松开后平滑减速（fling 手感），0.92 衰减 ≈ 60fps 下约 0.5s 停
                speed *= 0.92f
                if (abs(speed) < 20f) speed = 0f
            }
            wasScrolling = mag > 0.12f
            if (speed != 0f) {
                applyRightStickScroll(speed * dt)
                // 右摇杆滚动后同步焦点：将焦点更新到当前列表的首个可视项，
                // 确保方向键 UP/DOWN 的焦点位置与可视位置一致。
                syncRightStickFocus()
            }
            delay(16)
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
            kotlinx.coroutines.delay(2500)
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
            // 激活搜索时统一走聚焦流程（requestFocus 后 TextField 自带 IME 自动弹出）
            searchFocusRequest++
        }
    }

    // 任务：Y 键（或 '/' / Ctrl+F）激活搜索框。仅 requestFocus 即可——
    // Compose 的 TextField 获得焦点后会自动弹出输入法并进入待输入状态，
    // 无需（也不应）再手动 showSoftInput（对根视图调用会导致"假软键盘"：IME 出现但不绑定搜索框）。
    // 搜索框失焦时（onFocusChanged）主动收回输入法。
    LaunchedEffect(searchFocusRequest) {
        if (searchFocusRequest > 0) {
            searchFocusRequester.requestFocus()
        }
    }



    LandscapeTheme(
        darkTheme = darkMode != DarkMode.LIGHT,
        useMonet = useMonet,
        isAmoled = darkMode == DarkMode.AMOLED
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
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { }
            ) { padding ->
                BoxWithConstraints(
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
                                        if (query.isBlank()) {
                                            viewModel.closeSearch(); true
                                        } else false
                                    }
                                    KeyEvent.KEYCODE_Y -> {
                                        // Y 键直接激活搜索框并进入待输入状态（任务 2）
                                        if (!searchActive) viewModel.toggleSearch()
                                        haptics.tick()
                                        searchFocusRequest++
                                        true
                                    }
                                    else -> false
                                }
                            }
                            else -> when (n.keyCode) {
                                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
                                    { viewModel.launchFocused(); true }
                                KeyEvent.KEYCODE_X ->
                                    { viewModel.toggleFavoriteOfFocused(); haptics.tick(); true }
                                KeyEvent.KEYCODE_SLASH ->
                                    { viewModel.toggleSearch(); haptics.tick(); true }
                                KeyEvent.KEYCODE_F ->
                                    if (n.isCtrlPressed) { viewModel.toggleSearch(); haptics.tick(); true } else false
                                KeyEvent.KEYCODE_F5 -> { viewModel.rescan(); true }
                                KeyEvent.KEYCODE_ESCAPE -> {
                                    // 键盘 Esc 退出：与触摸一致走二次确认；手柄退出不受影响。
                                    if (touchExitConfirm) { onExit() } else { touchExitConfirm = true; haptics.click() }
                                    true
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
                ) {
                    StatusBar(
                        appCount = apps.size,
                        // 主页用时段问候语，其余页用对应页名（避免与子页面自带标题形成「双层标题」）
                        pageTitle = when (currentTab) {
                            MainTab.HOME -> greetingByHour()
                            MainTab.STATS -> "使用统计"
                            MainTab.SEARCH -> "搜索"
                            MainTab.SETTINGS -> "设置"
                            else -> ""
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
                    // 使用 220ms Fast 动画 + 轻微横向位移（1/10 屏宽），
                    // 快速跟手不拖沓；reduceMotion 时退化为纯 fade（0ms 无法指定，用 Fast）。
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (fadeIn(animationSpec = MotionSpec.Fast) +
                                slideInHorizontally(
                                    animationSpec = MotionSpec.SlideOffset,
                                    initialOffsetX = { dir * it / 10 }
                                )) togetherWith
                                (fadeOut(animationSpec = MotionSpec.Fast) +
                                    slideOutHorizontally(
                                        animationSpec = MotionSpec.SlideOffset,
                                        targetOffsetX = { -dir * it / 10 }
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
                                searchActive = searchActive,
                                query = query,
                                onQueryChange = viewModel::setQuery,
                                onCloseSearch = viewModel::closeSearch,
                                searchFocusRequester = searchFocusRequester,
                                view = view,
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
                                emptyHint = when {
                                    query.isNotBlank() -> "按 Y / Esc 清空搜索"
                                    selectedCategory == "收藏" -> "选中应用按 X 即可收藏"
                                    !queryAllGranted -> "系统限制下无法读取全部应用，请授权"
                                    else -> "按 A / Enter 重新扫描"
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
                                detailPinned = detailPinned,
                                onTogglePin = { detailPinned = !detailPinned },
                                focusSide = focusSide,
                                focusedDetailButton = focusedDetailButton,
                                reduceMotion = effectiveReduceMotion,
                                glassSurface = glassSurface,
                                effectiveReduceMotion = effectiveReduceMotion,
                                effectiveGlass = effectiveGlass,
                                wallpaperBehind = wallpaperBehind,
                                detailScrollState = detailScrollState,
                                portraitDrawerOpen = portraitDrawerOpen,
                                onClosePortraitDrawer = { portraitDrawerOpen = false },
                                onOpenPortraitDrawer = { portraitDrawerOpen = true },
                                onKeyboardVisibleChange = { keyboardVisible = it }
                            )
                            MainTab.STATS ->                             cn.mocabolka.run.ui.components.StatsScreen(
                                viewModel = viewModel,
                                listState = statsListState,
                                period = statsPeriod,
                                anchorMs = statsAnchorMs,
                                onAnchorChange = { statsAnchorMs = it },
                                datePickerOpen = statsDatePickerOpen,
                                onDatePickerOpenChange = { statsDatePickerOpen = it },
                                datePickerBridge = statsDatePickerBridge.value,
                                reduceMotion = reduceMotion,
                                leftScrollState = statsLeftScrollState,
                                focusIndex = statsFocusIndex,
                                gamepadConnected = gamepadConnected,
                                leftFocused = statsFocusSide == FocusSide.LEFT_LIST,
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
                                emptyHint = if (query.isNotBlank()) "按 B / Esc 返回"
                                    else "按 Y 键打开搜索",
                                showGamepadHints = gamepadConnected,
                                todayUsageMap = todayUsageMap,
                                reduceMotion = reduceMotion,
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
                                            cn.mocabolka.run.compat.OrientationManager.onChange(context, viewModel.settings)
                                            if (cn.mocabolka.run.compat.OrientationManager.needsOverlayPermission(context, mode)) {
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
                                        onAboutClick = { settingsSubPage = SettingsSubPage.ABOUT },
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }

                // 启动遮罩：圆形揭示（Circular Reveal）替代纯淡入
                LaunchOverlay(visible = launching)

                // 触摸退出二次确认气泡（底部居中，不遮挡全屏）
                AnimatedVisibility(
                    visible = touchExitConfirm,
                    enter = fadeIn(animationSpec = MotionSpec.Fast) + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut(animationSpec = MotionSpec.Fast) + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier
                                .padding(bottom = 48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    RoundedCornerShape(24.dp)
                                ),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                "再次返回退出 Reverie",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 覆盖式即时 Toast：单条显示，新消息立即替换旧消息（不排队回弹）。
                // 统一使用退出确认气泡样式（SurfaceVariant 背景 + 主题色边框 + 大圆角）。
                AnimatedVisibility(
                    visible = toastMsg != null,
                    enter = fadeIn(animationSpec = MotionSpec.Fast) + slideInVertically(initialOffsetY = { it / 3 }),
                    exit = fadeOut(animationSpec = MotionSpec.Fast) + slideOutVertically(targetOffsetY = { it / 3 })
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier
                                .padding(bottom = 48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    RoundedCornerShape(24.dp)
                                ),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                toastMsg ?: "",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                }
            }
        }

        // ── 开屏动画：全屏沉浸式（覆盖状态栏 / 挖孔区 / StatusBar）──
        // 通过 fillMaxSize + zIndex 让其填满根 Box 全部空间，与二级子菜单同级。
        // 开屏期间隐藏系统状态栏，实现真正全屏沉浸；退出时恢复。
        LaunchedEffect(isBooting) {
            if (isBooting) {
                val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
                androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    systemBarsBehavior =
                        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
                androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).show(
                    androidx.core.view.WindowInsetsCompat.Type.statusBars()
                )
            }
        }
        SplashOverlay(
            visible = isBooting,
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
                        .background(Color.Black.copy(alpha = 0.35f))
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
                    androidx.compose.foundation.layout.BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.md, vertical = Dimens.sm)
                    ) {
                        val maxH = maxHeight * 0.55f
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 顶部拖拽指示条
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = Dimens.xs)
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
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
                                showUsageHint = !cn.mocabolka.run.compat.UsageStatsPermissionHelper.isGranted(context),
                                todayUsage = todayUsageMap[focusedApp?.packageName] ?: 0L,
                                weeklyUsage = weeklyUsageMap[focusedApp?.packageName] ?: 0L,
                                glass = effectiveGlass,
                                reduceMotion = effectiveReduceMotion,
                                scrollState = detailScrollState,
                                wallpaperBehind = wallpaperBehind,
                                focusedButtonIndex = -1,
                                maxHeight = maxH,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Dimens.lg))
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
        var lastConfirmSnapshot by remember { mutableStateOf<Pair<cn.mocabolka.run.ui.components.SettingsConfirm, () -> Unit>?>(null) }
        var lastInfoSnapshot by remember { mutableStateOf<cn.mocabolka.run.ui.components.InfoContent?>(null) }
        val sBridge = settingsControl.value
        if (sBridge.pendingConfirm != null) lastConfirmSnapshot = sBridge.pendingConfirm
        if (sBridge.pendingInfo != null) lastInfoSnapshot = sBridge.pendingInfo

        // 设置页 ConfirmDialog
        AnimatedVisibility(
            visible = sBridge.pendingConfirm != null,
            modifier = Modifier.fillMaxSize().zIndex(1f),
            enter = fadeIn(animationSpec = MotionSpec.DialogEnter)
                + scaleIn(initialScale = 0.92f, animationSpec = MotionSpec.DialogEnter,
                    transformOrigin = TransformOrigin.Center),
            exit = fadeOut(animationSpec = MotionSpec.DialogExit)
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
        ) {
            val snap = lastInfoSnapshot
            if (snap != null) {
                cn.mocabolka.run.ui.components.InfoDialog(
                    title = snap.title,
                    text = snap.text,
                    imageRes = snap.imageRes,
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
        ) {
            cn.mocabolka.run.ui.components.DropdownDialog(
                title = sBridge.dropdownLabel,
                options = sBridge.dropdownOptions,
                optionLabel = sBridge.dropdownOptionLabel,
                selectedIndex = sBridge.dropdownSelectedIndex,
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
            cn.mocabolka.run.ui.components.DateRangePickerDialog(
                period = statsPeriod,
                initialAnchorMs = statsAnchorMs,
                bridge = statsDatePickerBridge.value,
                gamepadConnected = gamepadConnected,
                monthTotalMs = { y, m -> viewModel.monthlyReportFor(
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.YEAR, y); set(java.util.Calendar.MONTH, m - 1)
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                    }.timeInMillis
                ).sumOf { it.ms } },
                yearTotalMs = { y -> viewModel.yearlyReportFor(
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.YEAR, y); set(java.util.Calendar.MONTH, 0)
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                    }.timeInMillis
                ).sumOf { it.ms } },
                onConfirm = { ms -> statsAnchorMs = ms; statsDatePickerOpen = false },
                onDismiss = { statsDatePickerOpen = false }
            )
        }
    }

        // ── 子页面覆盖层（About / Licenses）：覆盖在 StatusBar 之上，全屏沉浸 ──
        // 子页面打开时隐藏系统状态栏（类似 CompatGuideActivity），关闭时恢复。
        // 入场：fadeIn + slideInHorizontally（从右侧 1/6 屏宽滑入），与 CompatGuide 风格统一；
        // 退场：仅 fadeOut，干净利落。
        AnimatedVisibility(
            visible = settingsSubPage != null,
            modifier = Modifier.fillMaxSize().zIndex(2f),
            enter = fadeIn(animationSpec = MotionSpec.Medium) +
                slideInHorizontally(
                    animationSpec = MotionSpec.SlideOffset,
                    initialOffsetX = { it / 6 }
                ),
            exit = fadeOut(animationSpec = MotionSpec.Medium)
        ) {
            // 状态栏隐藏/恢复（子页面可见期间隐藏，关闭时恢复）
            LaunchedEffect(settingsSubPage) {
                val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
                if (settingsSubPage != null) {
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                        hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                        systemBarsBehavior =
                            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    // 子页面关闭时恢复状态栏显示
                    androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).show(
                        androidx.core.view.WindowInsetsCompat.Type.statusBars()
                    )
                }
            }
            when (settingsSubPage) {
                SettingsSubPage.ABOUT -> AboutPage(
                    appVersion = viewModel.settings.appVersion,
                    onBack = { settingsSubPage = null },
                    onOpenLicenses = { settingsSubPage = SettingsSubPage.LICENSES },
                    listState = subPageListState
                )
                SettingsSubPage.LICENSES -> LicensesPage(
                    onBack = { settingsSubPage = SettingsSubPage.ABOUT },
                    listState = subPageListState
                )
                null -> { }
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
    val density = LocalDensity.current
    var rowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val positions = remember { mutableStateMapOf<Int, Rect>() }
    val selectedIdx = tabs.indexOf(selected).coerceAtLeast(0)
    val target = positions[selectedIdx]
    val indicatorX by animateFloatAsState(
        targetValue = target?.left ?: 0f,
        animationSpec = MotionSpec.Medium, label = "tabIndX"
    )
    val indicatorW by animateFloatAsState(
        targetValue = target?.width ?: 0f,
        animationSpec = MotionSpec.Medium, label = "tabIndW"
    )
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
                        .clip(RoundedCornerShape(Dimens.xl))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 分类数量徽标（R11-4）
                        if (count > 0) {
                            Spacer(Modifier.size(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (count > 999) "999+" else count.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
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
private fun SearchFieldOrList(
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    searchFocusRequester: FocusRequester,
    view: android.view.View,
    onKeyboardVisibleChange: (Boolean) -> Unit = {}
) {
    AnimatedVisibility(
        visible = searchActive,
        enter = fadeIn(animationSpec = MotionSpec.Fast) + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut(animationSpec = MotionSpec.Fast) + slideOutVertically(targetOffsetY = { -it / 2 })
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocusRequester)
                .focusable()
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        onKeyboardVisibleChange(true)
                    } else {
                        onKeyboardVisibleChange(false)
                        val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                        imm?.hideSoftInputFromWindow(view.windowToken, 0)
                    }
                }
                .padding(bottom = Dimens.xs),
            placeholder = { Text("搜索应用 / 游戏") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { view.clearFocus() })
        )
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
    state: androidx.compose.foundation.lazy.LazyListState,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                // 次级提示（如"收藏"为空）
                if (emptyHint != null) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                // 空列表时提供可聚焦操作按钮，避免手柄无焦点可移
                if (emptyText != "没有匹配的应用" && emptyText != "收藏夹为空") {
                    val buttonText = if (!queryAllGranted) "前往授权" else "重新扫描"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.xl))
                            .background(
                                if (emptyButtonFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            )
                            .border(
                                width = 2.5.dp,
                                color = if (emptyButtonFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(Dimens.xl)
                            )
                            .clickable(
                                interactionSource = emptyButtonInteraction,
                                indication = null
                            ) {
                                if (!queryAllGranted) onOpenPermissionSettings() else onRescan()
                            }
                            .focusable(true, emptyButtonInteraction)
                            .padding(horizontal = Dimens.md, vertical = Dimens.sm)
                    ) {
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
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
 * 底部详情 HUD：21:9 宽屏之外的中等屏幕专用。
 * 浮于列表底部，可手动锚定（按"详情"按钮 / 双击）保持常驻。
 */
@Composable
private fun DetailHud(
    app: AppModel?,
    isFavorite: Boolean,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onLaunch: (AppModel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenInfo: (AppModel) -> Unit,
    onUninstall: (AppModel) -> Unit,
    onForceStop: (AppModel) -> Unit = {},
    launching: Boolean,
    badgeCount: Int,
    searchInfo: String?,
    showUsageHint: Boolean,
    todayUsage: Long = 0L,
    weeklyUsage: Long = 0L,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = app != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        // 简化版浮层：图标 + 名称 + 操作按钮（水平排列）
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(Dimens.lg))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = Dimens.md, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            // 缩略图标
            if (app != null && app.icon.width > 0) {
                androidx.compose.foundation.Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            // 名称
            Text(
                text = app?.label ?: "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            // 启动按钮
            if (app != null) {
                androidx.compose.material3.Button(
                    onClick = { if (app.installed && !launching) onLaunch(app) },
                    enabled = app.installed && !launching
                ) {
                    Text(if (launching) "启动中…" else "启动")
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = { onToggleFavorite(app.packageName) }
                ) {
                    Text(if (isFavorite) "已收藏" else "收藏")
                }
                // 详情锚定按钮
                androidx.compose.material3.TextButton(onClick = onTogglePin) {
                    Text(if (pinned) "收起" else "详情")
                }
            }
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
    onMove: (newRow: Int) -> Unit,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit = {},
    maxRow: Int = 18,
    control: cn.mocabolka.run.ui.components.SettingsControlBridge =
        cn.mocabolka.run.ui.components.SettingsControlBridge()
) {
    val minRow = 0
    // ── 下拉弹窗打开时：所有按键路由到弹窗内部（任务 1 / 3 / 4）──
    // 这样背景列表焦点行不再随 D-pad 上下移动（修复"背景焦点上移一项"），
    // 且 A 确认 / B 取消 / ↑↓ 翻页 全部由控制桥驱动。
    // 注意：设置页及下拉弹窗仅左摇杆（STICK）导航，十字键不进入设置页。
    // 优先处理非下拉弹窗（InfoDialog / ConfirmDialog），B 键应关闭弹窗而非退出设置页。
    if (control.pendingInfo != null || control.pendingConfirm != null) {
        when (event) {
            is GamepadEvent.Back -> control.onDismissOverlay()
            is GamepadEvent.Select -> control.onDismissOverlay()
            else -> { /* 其它按键在弹窗内不处理 */ }
        }
        return
    }
    if (control.openDropdownRow >= 0) {
        when (event) {
            is GamepadEvent.Navigate -> {
                if (event.source == NavigateSource.STICK) {
                    when (event.direction) {
                        Direction.UP -> control.onMove(-1)
                        Direction.DOWN -> control.onMove(1)
                        else -> { /* 左右不绑定 */ }
                    }
                }
            }
            is GamepadEvent.Select -> control.onConfirm()
            is GamepadEvent.Back -> control.onDismiss()
            is GamepadEvent.Favorite -> control.onDismiss()
            else -> { /* 其它按键在弹窗内不处理 */ }
        }
        return
    }
    when (event) {
        is GamepadEvent.Navigate -> {
            // 设置页仅左摇杆（STICK）导航，十字键不进入设置页。
            if (event.source == NavigateSource.STICK) when (event.direction) {
                Direction.UP -> onMove((currentRow - 1).coerceAtLeast(minRow))
                Direction.DOWN -> onMove((currentRow + 1).coerceAtMost(maxRow))
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
 * 列表两侧的肩键角标：LB / RB，竖排胶囊，贴在应用列表左右边缘。
 * 仅在有手柄连接时由调用方决定是否渲染。
 * 对应手柄肩键：LB 上一分类、RB 下一分类。
 * 副标签 "分类" 提示用户此按键对应分类切换。
 */
@Composable
private fun SideKeyBadge(
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
 * 任务 19：手柄按键浮块（圆角矩形 + 大字按键名）。在 OutlinedTextField 的 leadingIcon
 * 等位置代替默认图标使用，提示手柄用户该位置对应一个按键。
 * 样式统一为深色底 + 亮色按键名（与设置页 X 键位浮块一致）。
 */
@Composable
private fun GamepadKeyBadge(
    key: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}

/**
 * 十字左/右键位提示：悬浮在两栏交界正中（骑缝），深色圆角浮块。
 * 只显示一个方向箭头（无文字）：左栏焦点时 →（按右切右栏），
 * 右栏焦点时 ←（按左切左栏）。
 */
@Composable
private fun CrossKeyBadge(
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
            text = if (leftFocused) "\u2192" else "\u2190",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}

/**
 * 穿透背景：读取系统默认桌面壁纸并铺满主界面底层。
 * 仅展示默认壁纸，不做横竖屏旋转处理。
 */
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

/** 设置页子页面：点击"关于"后导航的子页面。 */
private enum class SettingsSubPage { ABOUT, LICENSES }

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

/** 详情面板内可聚焦按钮数（0=启动 / 1=收藏 / 2=信息 / 3=卸载 / 4=强制停止） */
private const val DETAIL_BUTTON_COUNT = 5

/** 右摇杆滚动：基础最大速度（px/s）与加速度（px/s²）。 */
private const val RIGHT_STICK_MAX_SPEED = 3000f
private const val RIGHT_STICK_ACCEL = 9000f

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
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    searchFocusRequester: FocusRequester,
    view: android.view.View,
    visibleList: List<AppModel>,
    focusedPackage: String?,
    onFocus: (String) -> Unit,
    onLaunch: (AppModel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    favorites: Set<String>,
    badgeOf: (String) -> Int,
    currentListState: androidx.compose.foundation.lazy.LazyListState,
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
    detailPinned: Boolean,
    onTogglePin: () -> Unit,
    /** 当前焦点侧（LEFT_LIST / RIGHT_PANEL），用于右侧面板焦点态边框。 */
    focusSide: FocusSide = FocusSide.LEFT_LIST,
    /** 详情面板内当前焦点按钮索引（0..4），未聚焦时 -1。 */
    focusedDetailButton: Int = -1,
    /** 减少动态效果（无障碍）：冻结入场/光晕动画。 */
    reduceMotion: Boolean = false,
    /** 玻璃拟态：详情面板磨砂玻璃质感。 */
    glassSurface: Boolean = true,
    /** 减少动态效果（省电/无障碍聚合后）：由 HomeScreen 计算后传入。 */
    effectiveReduceMotion: Boolean = false,
    /** 玻璃拟态（省电聚合后）：由 HomeScreen 计算后传入。 */
    effectiveGlass: Boolean = false,
    /** 穿透背景模式：详情面板 surface 进一步透明。 */
    wallpaperBehind: Boolean = false,
    /** 右侧详情面板滚动状态（lift 到上层，供右摇杆在右栏焦点时滚动）。 */
    detailScrollState: androidx.compose.foundation.ScrollState = androidx.compose.foundation.rememberScrollState(),
    /** 竖屏底部详情抽屉是否展开（宽屏忽略）。 */
    portraitDrawerOpen: Boolean = false,
    /** 关闭竖屏抽屉回调（点击列表外 / 拖拽指示条）。 */
    onClosePortraitDrawer: () -> Unit = {},
    /** 打开竖屏抽屉回调（点击列表项）。 */
    onOpenPortraitDrawer: () -> Unit = {},
    /** 搜索框焦点变化（软键盘显隐）回调，由 HomeScreen 维护 keyboardVisible 状态。 */
    onKeyboardVisibleChange: (Boolean) -> Unit = {}
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (isWideScreen) {
            // 使用 Box 作为父容器，让 CrossKeyBadge 可以绝对定位在两栏正中间（骑缝）。
            // 左栏与右栏通过 Row 水平排列，浮块通过 BoxScope.align + offset 绝对定位。
            // 偏移计算：align(Center) 使浮块中心在父 Box 中心，再偏移
            // (父Box宽度/2 - 右栏宽度 - 浮块半宽) 使中心落在左栏右边缘。
            // 但父Box宽度在 Composable 中未知，使用 maxWidth（BoxWithConstraints 提供）。
            val contentWidth = maxWidth - Dimens.md * 2 // 减去父 Box 的 horizontal padding
            // 浮块中心需偏移的量：从父中心到左栏右边缘
            // 左栏右边缘 = contentWidth - detailSideWidth
            // 父中心 = contentWidth / 2
            // 偏移 = (contentWidth - detailSideWidth) - (contentWidth / 2) = (contentWidth / 2) - detailSideWidth
            // align(Center) 已使浮块中心在父中心，offset 整体移动，不需再减半宽。
            val crossBadgeSize = 36.dp
            // 左栏右边缘 = contentWidth - detailSideWidth - Dimens.sm（spacer）
            // 父中心 = contentWidth / 2
            // 偏移 = (左栏右边缘) - (父中心) = (contentWidth - detailSideWidth - Dimens.sm) - (contentWidth / 2)
            val offsetFromCenter = (contentWidth / 2f) - detailSideWidth - Dimens.md - Dimens.sm
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.md)
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
                                if (gamepadConnected) {
                                    SideKeyBadge(text = "LB", modifier = Modifier.padding(end = Dimens.xs))
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    CategoryTabsRow(
                                        tabs = categoryTabs, selected = selectedCategory,
                                        focusedIndex = focusedChip, onSelect = onSelectCategory,
                                        counts = categoryCounts
                                    )
                                }
                                if (gamepadConnected) {
                                    SideKeyBadge(text = "RB", modifier = Modifier.padding(start = Dimens.xs))
                                }
                            }
                            SearchFieldOrList(
                                searchActive = searchActive, query = query,
                                onQueryChange = onQueryChange, onCloseSearch = onCloseSearch,
                                searchFocusRequester = searchFocusRequester, view = view,
                                onKeyboardVisibleChange = onKeyboardVisibleChange
                            )
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
                    // 左栏与右栏之间的间距（确保两栏不紧贴，视觉呼吸感）
                    Spacer(Modifier.width(Dimens.sm))
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
                        modifier = Modifier
                            .width(detailSideWidth)
                            .fillMaxHeight()
                            .then(
                                if (focusSide == FocusSide.RIGHT_PANEL) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(Dimens.lg)
                                ) else Modifier
                            )
                    )
                }
                // 十字左/右键位提示：通过 BoxScope 绝对定位在左栏右边缘（骑缝）。
                // 浮块中心对齐左栏右边缘，向左/向右各突出 halfWidth，实现骑缝视觉。
                // 偏移计算见上（contentWidth, detailSideWidth, crossBadgeSize）。
                if (gamepadConnected) {
                    CrossKeyBadge(
                        leftFocused = focusSide == FocusSide.LEFT_LIST,
                        modifier = Modifier
                            .size(crossBadgeSize)
                            .align(Alignment.Center)
                            .offset(x = offsetFromCenter)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.md)
            ) {
                // 分类 Tabs 行：左右两侧贴 LB / RB 键位提示
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (gamepadConnected) {
                        SideKeyBadge(text = "LB", modifier = Modifier.padding(end = Dimens.xs))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryTabsRow(
                            tabs = categoryTabs, selected = selectedCategory,
                            focusedIndex = focusedChip, onSelect = onSelectCategory,
                            counts = categoryCounts
                        )
                    }
                    if (gamepadConnected) {
                        SideKeyBadge(text = "RB", modifier = Modifier.padding(start = Dimens.xs))
                    }
                }
                                SearchFieldOrList(
                        searchActive = searchActive, query = query,
                        onQueryChange = onQueryChange, onCloseSearch = onCloseSearch,
                        searchFocusRequester = searchFocusRequester, view = view,
                        onKeyboardVisibleChange = onKeyboardVisibleChange
                    )
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
    view: android.view.View,
    visibleList: List<AppModel>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusedPackage: String?,
    onFocus: (String) -> Unit,
    onLaunch: (AppModel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    favorites: Set<String>,
    badgeOf: (String) -> Int,
    isEmpty: Boolean,
    emptyText: String,
    emptyHint: String,
    showGamepadHints: Boolean,
    todayUsageMap: Map<String, Long>,
    reduceMotion: Boolean = false,
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.md, vertical = Dimens.xs)
    ) {
        // 搜索框：Material Design 3 规范，OutlinedTextField + 圆角 + focus 态
        // 任务 19：手柄连接时，leadingIcon 替换为 Y 按键浮块提示（圆角矩形 + "Y"）
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocusRequester)
                .focusable()
                .onFocusChanged { state ->
                    // 搜索框获焦 = 软键盘可见；失焦 = 收起输入法。
                    // onFocusChanged 非 Composable 上下文，直接用传入的 view 取 windowToken。
                    if (state.isFocused) {
                        onKeyboardVisibleChange(true)
                    } else {
                        onKeyboardVisibleChange(false)
                        val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                        imm?.hideSoftInputFromWindow(view.windowToken, 0)
                    }
                }
                .padding(bottom = Dimens.xs),
            placeholder = { Text("搜索应用 / 游戏") },
            leadingIcon = {
                if (showGamepadHints) {
                    GamepadKeyBadge("Y")
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
            emptyHint = emptyHint,
            showGamepadHints = showGamepadHints,
            todayUsageMap = todayUsageMap,
            reduceMotion = effectiveReduceMotion,
            entranceKey = "search|$query",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        // 底部操作提示行（手柄连接时显示，与设置页风格一致）
        if (showGamepadHints) {
            Text(
                text = "↑↓ 切换结果 · Y 切换搜索框/列表 · A 启动 · B 返回",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.xxs, bottom = Dimens.xxs),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
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
    powerSave: Boolean = false,
    onPowerSaveChange: (Boolean) -> Unit = {},
    orientationMode: cn.mocabolka.run.ui.OrientationMode,
    onOrientationModeChange: (cn.mocabolka.run.ui.OrientationMode) -> Unit,
    focusedRow: Int,
    onFocusedRowChange: (Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onManagePermissions: () -> Unit,
    viewModel: HomeViewModel,
    infoTrigger: androidx.compose.runtime.MutableState<Int?> =
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) },
    onRowCountChange: (Int) -> Unit = {},
    onClose: () -> Unit = {},
    controlBridge: androidx.compose.runtime.MutableState<cn.mocabolka.run.ui.components.SettingsControlBridge> =
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(cn.mocabolka.run.ui.components.SettingsControlBridge()) },
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

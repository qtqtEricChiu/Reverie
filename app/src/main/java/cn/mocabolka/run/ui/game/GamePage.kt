package cn.mocabolka.run.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding

import cn.mocabolka.run.ui.Haptics
import cn.mocabolka.run.ui.theme.edgeSafePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import android.appwidget.AppWidgetHostView
import cn.mocabolka.run.launcher.AppModel

/** 游戏页手柄按键常量别名：统一命名，震动表与行为分发表共用，避免双处维护漂移（审计 3.3 / 3.5）。 */
private object GPKey {
    const val UP = android.view.KeyEvent.KEYCODE_DPAD_UP
    const val UP_SYS = android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP
    const val DOWN = android.view.KeyEvent.KEYCODE_DPAD_DOWN
    const val DOWN_SYS = android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN
    const val LEFT = android.view.KeyEvent.KEYCODE_DPAD_LEFT
    const val LEFT_SYS = android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT
    const val RIGHT = android.view.KeyEvent.KEYCODE_DPAD_RIGHT
    const val RIGHT_SYS = android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT
    const val CENTER = android.view.KeyEvent.KEYCODE_DPAD_CENTER
    const val ENTER = android.view.KeyEvent.KEYCODE_ENTER
    const val A = android.view.KeyEvent.KEYCODE_BUTTON_A
    const val B = android.view.KeyEvent.KEYCODE_BUTTON_B
    const val X = android.view.KeyEvent.KEYCODE_BUTTON_X
    const val Y = android.view.KeyEvent.KEYCODE_BUTTON_Y
    const val LB = android.view.KeyEvent.KEYCODE_BUTTON_L1
    const val RB = android.view.KeyEvent.KEYCODE_BUTTON_R1
    const val LT = android.view.KeyEvent.KEYCODE_BUTTON_L2
    const val RT = android.view.KeyEvent.KEYCODE_BUTTON_R2
    const val BACK = android.view.KeyEvent.KEYCODE_BACK
}

/** 统一震动映射：方向键 / 肩键 / 扳机 = tick，确认 / 返回 / 收藏 = click（审计 3.3）。 */
private fun hapticFor(code: Int) {
    when (code) {
        GPKey.UP, GPKey.UP_SYS, GPKey.DOWN, GPKey.DOWN_SYS,
        GPKey.LEFT, GPKey.LEFT_SYS, GPKey.RIGHT, GPKey.RIGHT_SYS,
        GPKey.LB, GPKey.RB, GPKey.LT, GPKey.RT -> Haptics.tick()
        GPKey.CENTER, GPKey.ENTER, GPKey.A, GPKey.B, GPKey.BACK, GPKey.X -> Haptics.click()
        else -> Unit
    }
}

/**
 * 游戏专属页面（参考 Steam 大屏幕模式「游戏库 → 游戏」界面）。
 *
 * 入口（仅主页）：
 * - 手柄：应用列表-应用行按 A → [GamePageActivity] 打开本页；
 * - 触控：直接点击应用列表该行 → 打开本页。
 *
 * 本 Composable 负责：
 * - 背景层（美术 background 模糊 + 暗化遮罩 + 强调色染色）；
 * - 焦点状态机（扁平索引 [GPSlots]，5 逻辑栏：TopBar / Launch / Stats / Tabs / TabContent），
 *   由 onKeyEvent 驱动，激活统一回调 [activate]；
 * - 经 [BoxWithConstraints] 测量视口，由 [GamePageVariant.fromConstraints] 推导断点，
 *   交 [GamePageBody] 渲染 6 档响应式布局（方案第二章）。
 *
 * 美术资源经 [GameArtProvider] 抽象：当前读 APK assets 本地文件，后续可无缝接入
 * 用户自定义（[ArtSource.File]）与线上实时链接（[ArtSource.Remote]），本组件无需改动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamePageScreen(
    app: AppModel,
    provider: GameArtProvider,
    totalPlayMs: Long,
    isFavorite: Boolean,
    gamepadConnected: Boolean,
    daySeries: LongArray,
    installed: Boolean,
    versionName: String,
    firstInstallTime: Long,
    permissions: List<String>,
    gameIndex: Int = 0,
    gameCount: Int = 0,
    onLaunch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onPinShortcut: () -> Unit = {},
    onOpenArt: (androidx.compose.ui.graphics.Color) -> Unit = {},
    onSwitchGame: (Int) -> Unit,
    widgetSlots: List<WidgetHostSlot> = emptyList(),
    widgetViews: Map<String, AppWidgetHostView?> = emptyMap(),
    availableProviders: Map<String, List<WidgetProviderItem>> = emptyMap(),
    widgetVersion: Int = 0,
    widgetLoading: Boolean = false,
    onPickWidgetProvider: (String, WidgetProviderItem) -> Unit = { _, _ -> },
    /** 社区 App 均未安装时的兜底：打开系统全量 Widget 选择器，允许绑定任意（非社区）App 的 Widget。 */
    onPickAnyWidget: () -> Unit = {},
    /** 社区 App 均未安装时 Widget 面板是否收折（按游戏记忆）；收折态 A 先展开。 */
    widgetCollapsed: Boolean = false,
    onExpandWidget: () -> Unit = {},
    screenshotGallery: GameScreenshots.ScreenshotGalleryState? = null,
    rightStickFirstVisible: State<Int> = remember { mutableStateOf(-1) },
    onActiveListState: (LazyListState) -> Unit = {},
    reduceMotion: Boolean = false,
    // 挖孔屏适配（沿用主页/设置页开关）：开启时横屏内容加 displayCutout padding 绕开摄像头
    cutoutAdapt: Boolean = true,
    /** 美术资源提取/自定义完成后触发游戏页美术重载。 */
    artVersion: Int = 0,
    /** 预载美术（切游戏命中缓存时由 Activity 注入），命中则首帧直接采用，跳过异步解码，消除黑屏过渡。 */
    preloadedArt: GameArt? = null,
    /** 预载强调色（配合 preloadedArt），首帧不降级为默认主题色。 */
    preloadedAccent: Color? = null
) {
    // 预载美术命中（切游戏）直接采用，跳过 provider 异步加载；缺失时（首入页 / 未预载）走原路径。
    var art by remember(app.packageName) { mutableStateOf(preloadedArt) }
    LaunchedEffect(app.packageName, artVersion) {
        if (preloadedArt != null && artVersion == 0) {
            art = preloadedArt
        } else {
            // 美术重提取 / 自定义主题保存（artVersion 变化）后，先精准清掉该包的游戏页位图/取色缓存，
            // 再重新加载，确保 GamePage 显示的是最新提取到的资源而非上一轮残留。
            clearArtCachesForPackage(app.packageName)
            art = provider.load(app.packageName, app.label)
        }
    }
    // 预载美术在首帧之后才到达（首屏 loadGame 异步完成、或切游戏邻居未预热命中）：
    // 直接采用，使标题栏 / 背景首帧即锁定正确强调色，消除默认色 0.5s 闪现（2026-07-26 标题栏预载）。
    LaunchedEffect(preloadedArt) {
        if (preloadedArt != null) art = preloadedArt
    }

    // 游戏名优先用本机应用名（PackageManager 本地化名，兼容多语言动态调整），
    // 仅未安装占位（label 为空）时回退美术资源名。
    val displayName = app.label.ifBlank { art?.displayName ?: app.packageName }

    // 强调色（accent）：优先采用预载强调色（与 Activity 快照一致，零额外 IO）；
    // 否则从美术图片运行时取色（logo→capsule→header→hero），manifest.accentColor 与主题色仅作兜底。
    val context = androidx.compose.ui.platform.LocalContext.current
    val fallbackAccent = art?.accentColor ?: MaterialTheme.colorScheme.primary
    var accent by remember(app.packageName) { mutableStateOf(preloadedAccent ?: fallbackAccent) }
    LaunchedEffect(art) {
        val a = art
        // 美术来自预载且已带预载强调色：直接采用预存结果，跳过运行时取色（与 Activity 快照一致，零额外 IO）。
        if (a != null && a == preloadedArt && preloadedAccent != null) {
            accent = preloadedAccent
        } else if (a != null) {
            val sources = listOfNotNull(a.logo?.source, a.capsule?.source, a.header?.source, a.hero?.source)
            accent = extractAccent(context, sources, a.accentColor ?: fallbackAccent)
        } else fallbackAccent
    }
    // 预载强调色在后置帧才到达：直接采用，标题栏首帧即无默认色闪现（2026-07-26 标题栏预载）。
    LaunchedEffect(preloadedAccent) {
        if (preloadedAccent != null) accent = preloadedAccent
    }

    val bg = art?.background
    val hero = art?.hero
    val logo = art?.logo
    val capsule = art?.capsule
    val header = art?.header

    // 内部焦点状态机（扁平索引，与 GamePageBody 同源）
    var focusIndex by remember { mutableIntStateOf(GPSlots.LAUNCH) }
    var selectedTab by remember { mutableIntStateOf(0) }
    // 每 Tab 记忆上次离开时的内容区焦点位置，切回时恢复（避免切 Tab 丢失内容区位置，P1.6）
    val tabFocusMemory = remember { intArrayOf(GPSlots.TAB_DYNAMIC, GPSlots.TAB_STATS, GPSlots.TAB_ABOUT) }

    // 进入即请求焦点，确保手柄/键盘事件稳定送达 onKeyEvent
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val data = remember(art, accent, totalPlayMs, daySeries, installed, versionName, firstInstallTime, permissions, isFavorite, reduceMotion) {
        GamePageData(
            app = app,
            displayName = displayName,
            accent = accent,
            bg = bg,
            hero = hero,
            logo = logo,
            capsule = capsule,
            header = header,
            logoPosition = art?.logoPosition,
            totalPlayMs = totalPlayMs,
            daySeries = daySeries,
            installed = installed,
            versionName = versionName,
            firstInstallTime = firstInstallTime,
            permissions = permissions,
            isFavorite = isFavorite,
            reduceMotion = reduceMotion,
            // 仅当实际存在任一美术图片时才算「有美术」，避免移除资源后残留占位块（图标+应用名降级）。
            hasArt = run {
                val a = art
                a != null && (a.background != null || a.hero != null ||
                        a.logo != null || a.capsule != null || a.header != null)
            },
            lastUsedMs = app.lastUsedTime
        )
    }

    // Widget 选择器（个性化）：openPicker 由焦点机 activate(CONTENT_START) 与触控点击（onActivate）统一触发
    var pickerEntries by remember { mutableStateOf<List<PickerEntry>>(emptyList()) }
    fun openPicker() {
        val installed = widgetSlots.filter { it.isInstalled }
        val entries = installed
            .map { s -> PickerEntry(s, availableProviders[s.packageName] ?: emptyList()) }
            .filter { it.providers.isNotEmpty() }
        if (entries.isEmpty()) {
            // 无已安装社区 App（或均无可用 Provider）：兜底走系统全量选择器，绑定其它非社区 App 的 Widget。
            onPickAnyWidget()
            return
        }
        if (entries.size == 1 && entries[0].providers.size == 1) {
            onPickWidgetProvider(entries[0].slot.packageName, entries[0].providers[0])
        } else {
            pickerEntries = entries
        }
    }

    val installedSlots = widgetSlots.filter { it.isInstalled }
    // 收折行也计入可聚焦内容：社区 App 均未安装时面板收折为一行按钮（点击/A 走系统全量选择器绑定任意 App Widget）。
    val personalizationAvailable = (installedSlots.isEmpty() && widgetSlots.isNotEmpty()) ||
        (installedSlots.isNotEmpty() &&
            (installedSlots.any { it.isBound } || installedSlots.any { (availableProviders[it.packageName] ?: emptyList()).isNotEmpty() }))
    // 截图画廊（动态 Tab 首个内容项）作为可聚焦槽：左/右选图、A 打开。计入内容项数以支持右摇杆焦点跟随。
    val galleryFocusable = screenshotGallery != null
    val contentCount = contentItemCount(data, selectedTab, if (personalizationAvailable) 1 else 0, if (galleryFocusable) 1 else 0)
    // 画廊可选项数（截图纸贴 + "查看全部"入口），用于左/右在画廊内循环切换选中的截图。
    val galleryItemCount = if (screenshotGallery != null) {
        val shots = screenshotGallery.screenshots
        val showViewAll = shots.size > 5 && screenshotGallery.onViewAll != null
        val maxShown = if (showViewAll) 5 else shots.size
        maxShown + if (showViewAll) 1 else 0
    } else 0
    // 画廊内选中的截图纸贴序号（手柄左/右在画廊内逐张切换焦点；横向滚动随焦点跟随）。
    var gallerySel by remember { mutableIntStateOf(0) }
    val lastIndex = GPSlots.CONTENT_START + contentCount - 1

    // 右摇杆滚动焦点跟随（归一化「停哪跟哪」）：引擎每帧回写首个可视项索引，
    // 映射到最近内容槽位同步焦点。TabContent 内部按首可视项判断，避免与右摇杆滚动反向拉扯。
    LaunchedEffect(rightStickFirstVisible.value) {
        val idx = rightStickFirstVisible.value
        if (idx < 0) return@LaunchedEffect
        val slot = GPSlots.CONTENT_START + idx
        if (slot <= lastIndex && focusIndex != slot) {
            focusIndex = slot
        }
    }
    // 切换游戏后内容滚动复位到顶部由 TabContent 内部按 gameKey 处理（每个 Tab 独立列表状态）。

    fun activate(slot: Int) {
        when (slot) {
            GPSlots.BACK -> onClose()
            GPSlots.APP_INFO -> onOpenAppInfo()
            GPSlots.THEME -> onOpenArt(accent)
            GPSlots.FAVORITE -> onToggleFavorite()
            GPSlots.SHORTCUT -> onPinShortcut()
            GPSlots.LAUNCH -> onLaunch()
            GPSlots.TAB_DYNAMIC -> selectedTab = 0
            GPSlots.TAB_STATS -> selectedTab = 1
            GPSlots.TAB_ABOUT -> selectedTab = 2
            // 动态 Tab 内容区：CONTENT_START=截图画廊（选中的截图/查看全部/授权），CONTENT_START+1=Widget 按钮。
            // 其它 Tab（统计/关于）内容项不触发弹窗（保持原行为）。
            else -> {
                val contentIdx = slot - GPSlots.CONTENT_START
                if (selectedTab == 0) {
                    when (contentIdx) {
                        0 -> {
                            val g = screenshotGallery
                            if (g != null) {
                                val shots = g.screenshots
                                val showViewAll = shots.size > 5 && g.onViewAll != null
                                val maxShown = if (showViewAll) 5 else shots.size
                                val sel = gallerySel.coerceIn(0, maxOf(maxShown, 0))
                                if (shots.isNotEmpty() && sel < maxShown) {
                                    g.onOpen?.invoke(shots[sel])
                                } else if (showViewAll && g.onViewAll != null && sel == maxShown) {
                                    g.onViewAll.invoke(accent)
                                } else {
                                    when (g.setupState) {
                                        GameScreenshots.SetupState.NEED_TREE -> g.onRequestTree?.invoke()
                                        GameScreenshots.SetupState.NEED_SAMPLE -> g.onRequestSample?.invoke()
                                        // 收折成一行时 A 先展开完整卡片（与触控点击同路）；已展开则保持原行为。
                                        GameScreenshots.SetupState.UNSUPPORTED ->
                                            if (g.unsupportedCollapsed) g.onExpandUnsupported() else g.onRetry()
                                        else -> g.onViewAll?.invoke(accent)
                                    }
                                }
                            }
                        }
                        1 -> if (widgetCollapsed) onExpandWidget() else openPicker()
                        else -> {}
                    }
                }
            }
        }
    }

    fun selectTabAndFocus(tab: Int) {
        // 保存当前 Tab 的内容区焦点，切回时恢复原位（P1.6）
        tabFocusMemory[selectedTab] = focusIndex
        selectedTab = tab.coerceIn(0, 2)
        focusIndex = tabFocusMemory[selectedTab].coerceIn(0, lastIndex)
    }

    // 左右切游戏：切换后焦点复位到「启动」，触控 ‹ › 与手柄 LT/RT 共用此入口
    fun doSwitch(dir: Int) {
        focusIndex = GPSlots.LAUNCH
        onSwitchGame(dir)
    }

    // 左右切 Tab（动态/统计/关于）：LB/RB 驱动，循环跨越三档并同步焦点
    fun switchTab(dir: Int) {
        selectTabAndFocus((selectedTab + dir + 3) % 3)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val variant = GamePageVariant.fromConstraints(maxWidth.value.toInt(), maxHeight.value.toInt())
        val multiColumn = variant.structure != GPStructure.PORTRAIT

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusTarget()
                .onKeyEvent { ev ->
                    val n = ev.nativeKeyEvent
                    if (n.action != android.view.KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    // 统一震动映射（与子页 subPageKeyHandler 同构）：方向键 / 肩键 / 扳机 = tick，确认 / 返回 / 收藏 = click。
                    hapticFor(n.keyCode)
                    when (n.keyCode) {
                        GPKey.UP, GPKey.UP_SYS -> {
                            focusIndex = (focusIndex - 1).coerceAtLeast(0); true
                        }
                        GPKey.DOWN, GPKey.DOWN_SYS -> {
                            focusIndex = (focusIndex + 1).coerceAtMost(lastIndex); true
                        }
                        GPKey.LEFT, GPKey.LEFT_SYS -> {
                            val next = when {
                                focusIndex == GPSlots.LAUNCH ->
                                    GPSlots.SHORTCUT
                                focusIndex in GPSlots.BACK..GPSlots.SHORTCUT ->
                                    (focusIndex - 1).coerceAtLeast(GPSlots.BACK)
                                focusIndex in GPSlots.TAB_DYNAMIC..GPSlots.TAB_ABOUT ->
                                    GPSlots.LAUNCH
                                // 截图画廊：左移逐张聚焦；首张再左移则退出画廊（横屏回启动钮 / 竖屏回 Tab）。
                                // "画廊是否可退出"的判定内聚于 ScreenshotGalleryState.hasFocusableContent（审计 4.4）。
                                selectedTab == 0 && focusIndex == GPSlots.CONTENT_START &&
                                    screenshotGallery?.hasFocusableContent == true -> {
                                    if (gallerySel > 0) { gallerySel -= 1; focusIndex }
                                    else (if (multiColumn) GPSlots.LAUNCH else GPSlots.TAB_ABOUT)
                                }
                                multiColumn && focusIndex >= GPSlots.CONTENT_START ->
                                    GPSlots.LAUNCH
                                else -> focusIndex
                            }
                            focusIndex = next; true
                        }
                        GPKey.RIGHT, GPKey.RIGHT_SYS -> {
                            val next = when {
                                focusIndex in GPSlots.BACK..GPSlots.SHORTCUT ->
                                    (focusIndex + 1).coerceAtMost(GPSlots.LAUNCH)
                                focusIndex == GPSlots.LAUNCH ->
                                    GPSlots.TAB_DYNAMIC
                                multiColumn && focusIndex in GPSlots.LAUNCH..GPSlots.TAB_ABOUT && contentCount > 0 ->
                                    GPSlots.CONTENT_START
                                // 截图画廊：右移逐张聚焦；末张（查看全部）再右移进入下一内容项（个性化面板）。
                                selectedTab == 0 && focusIndex == GPSlots.CONTENT_START &&
                                    screenshotGallery?.hasFocusableContent == true -> {
                                    val g = galleryItemCount.coerceAtLeast(1)
                                    if (gallerySel < g - 1) { gallerySel += 1; focusIndex }
                                    else (if (contentCount > 1) GPSlots.CONTENT_START + 1 else focusIndex)
                                }
                                multiColumn && focusIndex >= GPSlots.CONTENT_START ->
                                    GPSlots.LAUNCH
                                else -> focusIndex
                            }
                            focusIndex = next; true
                        }
                        GPKey.CENTER, GPKey.ENTER, GPKey.A -> {
                            // 截图画廊：逐张聚焦后直接 A 打开当前选中的截图纸贴 / 查看全部（选中态由 focusIndex==CONTENT_START 驱动）。
                            activate(focusIndex)
                            true
                        }
                        GPKey.X -> { onToggleFavorite(); true }
                        GPKey.BACK, GPKey.B -> {
                            // 画廊聚焦时 B=退出画廊聚焦（复位 gallerySel、焦点退回启动钮/Tab），而非关闭整个 GamePage；
                            // "画廊此刻是否应被 B 退出"由 ScreenshotGalleryState 判定（审计 4.4），其余场景保持 B=返回关闭。
                            if (selectedTab == 0 && focusIndex == GPSlots.CONTENT_START &&
                                screenshotGallery?.hasFocusableContent == true) {
                                gallerySel = 0
                                focusIndex = if (multiColumn) GPSlots.LAUNCH else GPSlots.TAB_DYNAMIC
                            } else {
                                onClose()
                            }
                            true
                        }
                        GPKey.Y -> { onOpenArt(accent); true }
                        GPKey.LB -> { switchTab(-1); true }
                        GPKey.RB -> { switchTab(1); true }
                        GPKey.LT -> { doSwitch(-1); true }
                        GPKey.RT -> { doSwitch(1); true }
                        else -> false
                    }
                }
        ) {
            // ── 背景层已提升到 GamePageActivity（独立 Crossfade），此处仅渲染内容层 ──

            // ── 层 1：响应式内容 ──
            // 挖孔屏适配：背景层（模糊图/遮罩/染色）保持全屏铺满延伸到挖孔区；仅内容层按开关避让：
            // 开启 + 横屏（多列）→ 加 displayCutout padding 让 TopBar/海报/按钮绕开摄像头；
            // 竖屏或关闭 → 不推挤内容。瀑布屏两侧安全边距与开关解耦、始终生效。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 全出血顶栏：不再对内容层加 statusBarsPadding，使 TopBar / Hero 直接满铺到屏幕顶边
                    // （含状态栏区域）。状态栏图标由顶部 96dp 压暗层保证可读性（MD3 边缘到边缘 + 系统栏 scrim）。
                    // 底部仍避让导航栏，避免按钮被压住。
                    .navigationBarsPadding()
                    // 统一边缘安全边距：背景层（模糊图/遮罩/染色）保持全屏铺满，
                    // 仅内容层按开关避让（竖屏瀑布 / 横屏固定 + 挖孔）。绝不碰上下。
                    .edgeSafePadding(cutoutAdapt)
            ) {
                GamePageBody(
                    data = data,
                    variant = variant,
                    focusIndex = focusIndex,
                    selectedTab = selectedTab,
                    gamepadConnected = gamepadConnected,
                    gameIndex = gameIndex,
                    gameCount = gameCount,
                    onActivate = { activate(it) },
                    onSwitchGame = { doSwitch(it) },
                    widgetSlots = widgetSlots,
                    widgetViews = widgetViews,
                    availableProviders = availableProviders,
                    widgetVersion = widgetVersion,
                    widgetLoading = widgetLoading,
                    onPickWidgetProvider = onPickWidgetProvider,
                    screenshotGallery = screenshotGallery,
                    gallerySel = gallerySel,
                    onActiveListState = onActiveListState,
                    widgetCollapsed = widgetCollapsed,
                    onExpandWidget = onExpandWidget,
                    reduceMotion = reduceMotion
                )
            }
            // Widget 选择器（个性化）：由焦点机 activate(CONTENT_START) 统一触发（触控点击经 onActivate 同路）
            if (pickerEntries.isNotEmpty()) {
                WidgetPickerSheet(
                    entries = pickerEntries,
                    accent = data.accent,
                    onPickProvider = { slot, item ->
                        onPickWidgetProvider(slot.packageName, item)
                        pickerEntries = emptyList()
                    },
                    onDismiss = { pickerEntries = emptyList() }
                )
            }
        }
    }
}

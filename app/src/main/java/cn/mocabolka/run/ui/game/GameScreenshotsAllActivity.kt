package cn.mocabolka.run.ui.game

import cn.mocabolka.run.ui.components.BottomFadeMask
import cn.mocabolka.run.ui.components.CenteredHint
import cn.mocabolka.run.ui.components.statusBarSafeTop
import androidx.compose.ui.unit.Dp

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.gamepad.SubPageGamepadHost
import cn.mocabolka.run.ui.components.GamepadBottomHintBar
import cn.mocabolka.run.ui.components.Hint
import cn.mocabolka.run.ui.components.HintGap
import cn.mocabolka.run.ui.components.KeyToken
import cn.mocabolka.run.ui.enablePredictiveBack
import cn.mocabolka.run.ui.setupEdgeToEdgeImmersive
import cn.mocabolka.run.ui.game.GameArt
import cn.mocabolka.run.ui.game.GameArtImage
import cn.mocabolka.run.ui.game.GameArtResolver
import cn.mocabolka.run.ui.game.GamePageColors
import cn.mocabolka.run.ui.game.GameScreenshots
import cn.mocabolka.run.ui.game.extractAccent
import cn.mocabolka.run.ui.game.steamart.ui.SteamArtContracts
import cn.mocabolka.run.ui.game.rememberArtBitmap
import cn.mocabolka.run.ui.game.rememberShotBitmap
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.ui.theme.MotionMachines
import cn.mocabolka.run.ui.Haptics
import cn.mocabolka.run.ui.SettingsRepository
import cn.mocabolka.run.ui.theme.SurfaceTokens
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.edgeSafePadding
import cn.mocabolka.run.ui.theme.focusBorder
import cn.mocabolka.run.ui.components.SubPageSpec
import cn.mocabolka.run.ui.components.SubPageTitleBar
import cn.mocabolka.run.ui.components.subPageKeyHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 游戏"查看全部截图 / 录屏"二级页面（独立 Activity，从 [GamePageActivity] 进入）。
 *
 * 设计原则（与 GamePage 同构，继承其背景与取色方案）：
 * - 沉浸式全屏 + 预测返回；背景层完全复用 GamePageScreen 的 4 层渲染（模糊底图 / 渐变压暗 / accent 0.10f / 顶部 96dp 遮罩）。
 * - [extractAccent] 取色贯穿全页，瀑布流缩略图焦点边框、Header 取色均来自同一 accent。
 * - 顶部居中展示 Header 图（优先 header，回退 hero / capsule）；下方瀑布流展示该游戏全部截图与录屏。
 *
 * 瀑布流响应式：列数由 BoxWithConstraints 的 maxWidth + 屏幕方向共同决定
 * （竖屏 2/3/4，横屏 3/4/5）；列越多缩略图越小。缩略图按解码位图宽高比生成不等高卡片，形成瀑布高低差。
 *
 * 手柄适配（需求：左摇杆焦点移动、右摇杆页面滚动；A 打开、B 退出；无 X/Y/LB/RB/LT/RT）：
 * - [SubPageGamepad] 负责左摇杆四向（已放开为四向）驱动焦点 + 右摇杆滚动（焦点跟随首个可视项）。
 * - 根 Box 的 onKeyEvent（原始按键）负责 A=系统默认打开图片/视频、B/Back/Esc=退出返回父级。
 */
class GameScreenshotsAllActivity : ComponentActivity() {

    companion object {
        // label / accent 透传键统一使用 SteamArtContracts（与 SteamArt 美术二级页同构，2026-07-26）：
        //   - EXTRA_LABEL  =  SteamArtContracts.EXTRA_LABEL  = "steamart.extra.LABEL"
        //   - EXTRA_ACCENT =  SteamArtContracts.EXTRA_ACCENT = "steamart.extra.ACCENT"
        // 本类不再定义自有透传常量，避免与美术资源二级页分叉。
    }
    /** 瀑布流状态提升到 Activity 字段，供 SubPageGamepad 右摇杆滚动引擎访问（与 About 同构）。 */
    private val gridState = LazyStaggeredGridState()
    /** 逻辑焦点索引（驱动缩略图焦点边框），由左摇杆 / 右摇杆 / DPad 共同维护。 */
    private val focusIndex = mutableStateOf(0)
    /** 响应式列数（由 BoxWithConstraints 写入），左摇杆左右跨列移动时使用。 */
    private val columns = mutableStateOf(3)
    /** 缩略图总数（由 composable 写入），焦点越界钳制与 A 打开取值时使用。 */
    private val shotCount = mutableStateOf(0)
    /** 右摇杆滚动后引擎回写的"首个可视项索引"（焦点跟随桥梁）。 */
    private val rightStickFirstVisible = MutableStateFlow(-1)
    /** 左摇杆 / Hat 导航事件桥梁：SubPageGamepad.onNavigate 推入，composable 订阅驱动焦点。 */
    private val navFlow = MutableSharedFlow<Direction>(extraBufferCapacity = 16)
    /** 右摇杆滚动引擎（独立 Activity 不含 HomeScreen 引擎，需自建最小滚动循环）。 */
    private var subPageGamepad: SubPageGamepadHost? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(GamePageActivity.EXTRA_PACKAGE)
        if (pkg.isNullOrBlank()) { finish(); return }

        // 透传键统一走 SteamArtContracts（与 SteamArt 美术二级页同构，2026-07-26）：标题/强调色首帧即正确。
        val label = intent.getStringExtra(SteamArtContracts.EXTRA_LABEL).takeIf { !it.isNullOrBlank() } ?: pkg
        val accentHex = intent.getStringExtra(SteamArtContracts.EXTRA_ACCENT)
        val inheritedAccent = accentHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

        // 右摇杆滚动：提前创建网格状态并交给引擎，setContent 内复用同一实例。
        subPageGamepad = SubPageGamepadHost(
            lifecycleScope,
            getState = { gridState },
            getFirstVisibleItemIndex = { gridState.firstVisibleItemIndex },
            onFocusSync = { rightStickFirstVisible.value = it },
            onNavigate = { dir -> navFlow.tryEmit(dir) }
        )
        subPageGamepad?.start()
        // 沉浸式 + 边缘到边缘
        setupEdgeToEdgeImmersive(hideStatusBar = false)
        enableEdgeToEdge()
        // 预测返回手势：从边缘滑动返回父级游戏子页面
        enablePredictiveBack { finish() }

        val settings = SettingsRepository(this@GameScreenshotsAllActivity)
        val cutoutAdapt = settings.cutoutAdapt

        // 立即全屏渲染（对齐 SteamArtHubActivity）：标题栏 + accent 渐变背景（GameArtBackground 在 art=null 时的回退）
        // 首帧即正确显示。art 在屏幕内 LaunchedEffect 异步加载，加载到位后背景自动从 accent 渐变升级为模糊底图。
        // 截图瀑布流内容（shots）扫描亦异步，属允许的缓加载。
        setContent {
            LandscapeTheme(settings = settings) {
                GameScreenshotsAllScreen(
                    pkg = pkg,
                    label = label,
                    inheritedAccent = inheritedAccent,
                    cutoutAdapt = cutoutAdapt,
                    reduceMotion = settings.reduceMotion,
                    focusIndex = focusIndex,
                    columns = columns,
                    shotCount = shotCount,
                    gridState = gridState,
                    rightStickFirstVisible = rightStickFirstVisible,
                    navFlow = navFlow,
                    onClose = { finish() },
                    onOpenShot = { shot -> GameScreenshots.open(this, shot) }
                )
            }
        }
    }

    /** 右摇杆滚动：MotionEvent 轴委托给引擎；其余交系统。 */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return if (subPageGamepad?.dispatchMotionEvent(event) == true) true else super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        subPageGamepad?.stop()
        subPageGamepad = null
        super.onDestroy()
    }
}

@Composable
private fun GameScreenshotsAllScreen(
    pkg: String,
    /** 标题：来自 Intent 透传（SteamArtContracts.EXTRA_LABEL），首帧即正确，与 SteamArt 美术二级页同构。 */
    label: String,
    /** 强调色：来自 Intent 透传（SteamArtContracts.EXTRA_ACCENT），首帧即正确；缺失时在 art 加载后用 extractAccent 推导。 */
    inheritedAccent: androidx.compose.ui.graphics.Color?,
    /** 挖孔屏适配开关（与主页 / 游戏页 / 子菜单 dialog 统一）。 */
    cutoutAdapt: Boolean,
    /** 减少动态效果开关：true 时 subPageEnter/subPageExit 退化为瞬时，与 MD3 无障碍规范一致。 */
    reduceMotion: Boolean,
    focusIndex: androidx.compose.runtime.MutableState<Int>,
    columns: androidx.compose.runtime.MutableState<Int>,
    shotCount: androidx.compose.runtime.MutableState<Int>,
    gridState: LazyStaggeredGridState,
    rightStickFirstVisible: MutableStateFlow<Int>,
    navFlow: MutableSharedFlow<Direction>,
    onClose: () -> Unit,
    onOpenShot: (GameScreenshots.Shot) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary

    // ── 美术资源：初值同步继承进程内缓存（peekGameArt，无 IO）——从游戏页进入时游戏页必已
    //    load 过同游戏 GameArt → 首帧即拿到完整背景/头图，背景跨页零延迟继承；
    //    未命中（进程重建等）才异步 load 补帧，期间 GameArtBackground 回退 accent 渐变（非纯色）。 ──
    //    说明：标题栏 / 瀑布流等前景不依赖 art，首帧即渲染（由 subPageEnter 整页渐入），不再以 art
    //    解析作为闸门——[SubPageTitleBar]+[statusBarSafeTop] 已正确处理状态栏避让，原 ready 闸门只会
    //    让标题栏在缓存未命中时硬切出现（≤800ms 视觉跳变）。
    var art by remember(pkg, label) { mutableStateOf(peekGameArt(pkg)) }
    LaunchedEffect(pkg, label) {
        if (art == null) art = GameArtResolver(context).load(pkg, label)
    }

    // ── 强调色：首帧即采用 inheritedAccent；缺失时在 art 加载到位后用 extractAccent 推导 ──
    val accentSources = remember(art) {
        art?.let { listOfNotNull(it.logo?.source, it.capsule?.source, it.header?.source, it.hero?.source) } ?: emptyList()
    }
    var accent by remember { mutableStateOf<Color>(inheritedAccent ?: primary) }
    LaunchedEffect(art, inheritedAccent) {
        accent = if (inheritedAccent != null) inheritedAccent
                 else art?.let { extractAccent(context, accentSources, it.accentColor ?: primary) } ?: primary
    }

    val header = art?.header
    val hero = art?.hero
    val capsule = art?.capsule

    // ── 加载该游戏全部截图 / 录屏 ──
    val store = remember { GameScreenshots.Store(context) }
    val treeUri = remember { store.getTreeUri() }
    val hash = remember(pkg) { store.getHash(pkg) }
    var shots by remember(pkg) { mutableStateOf<List<GameScreenshots.Shot>>(emptyList()) }
    var setupState by remember { mutableStateOf(GameScreenshots.SetupState.READY) }
    LaunchedEffect(pkg) {
        val t = treeUri
        val h = hash
        when {
            t == null -> setupState = GameScreenshots.SetupState.NEED_TREE
            h == null -> setupState = GameScreenshots.SetupState.NEED_SAMPLE
            else -> {
                setupState = GameScreenshots.SetupState.READY
                shots = GameScreenshots.scanScreenshots(context, t, h)
            }
        }
    }
    // 同步总数给 Activity（焦点边界），并钳制焦点
    LaunchedEffect(shots.size) {
        shotCount.value = shots.size
        if (focusIndex.value >= shots.size) focusIndex.value = (shots.size - 1).coerceAtLeast(0)
    }

    // ── 步进式分页：仅渲染前 visibleCount 张，滚动 / 焦点接近末尾时按 [SHOTS_PAGE_SIZE] 追加 ──
    var visibleCount by remember(pkg) { mutableStateOf(SHOTS_PAGE_SIZE) }
    LaunchedEffect(shots.size) {
        visibleCount = visibleCount.coerceIn(SHOTS_PAGE_SIZE.coerceAtMost(shots.size).coerceAtLeast(0), shots.size.coerceAtLeast(0))
        if (shots.isNotEmpty() && visibleCount == 0) visibleCount = SHOTS_PAGE_SIZE.coerceAtMost(shots.size)
    }
    // 滚动接近已加载末尾时预取下一页（网格首项为 Header，故截图 grid 索引 = 截图序号 + 1）。
    LaunchedEffect(gridState, shots.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastGridIndex ->
                val lastShotIdx = lastGridIndex - 1
                if (lastShotIdx >= visibleCount - SHOTS_PREFETCH && visibleCount < shots.size) {
                    visibleCount = (visibleCount + SHOTS_PAGE_SIZE).coerceAtMost(shots.size)
                }
            }
    }

    // 本地焦点移动：左摇杆四向 / DPad（焦点接近已加载末尾时提前扩容分页，保证目标项已渲染可滚动）
    fun moveFocus(dir: Direction) {
        val n = shotCount.value
        if (n == 0) return
        val cols = columns.value.coerceAtLeast(1)
        focusIndex.value = when (dir) {
            Direction.UP -> (focusIndex.value - 1).coerceAtLeast(0)
            Direction.DOWN -> (focusIndex.value + 1).coerceAtMost(n - 1)
            Direction.LEFT -> (focusIndex.value - cols).coerceAtLeast(0)
            Direction.RIGHT -> (focusIndex.value + cols).coerceAtMost(n - 1)
        }
        if (focusIndex.value >= visibleCount - 1 && visibleCount < n) {
            visibleCount = (focusIndex.value + SHOTS_PAGE_SIZE).coerceAtMost(n)
        }
        scope.launch { runCatching { gridState.scrollToItem(focusIndex.value + 1) } }
    }

    // 右摇杆滚动：焦点跟随首个可视项（不主动滚动，避免与滚动打架）；grid 索引需减去 Header 偏移
    val rsFocus by rightStickFirstVisible.collectAsState()
    LaunchedEffect(rsFocus) {
        val shotIdx = rsFocus - 1
        if (shotIdx in 0 until shotCount.value) focusIndex.value = shotIdx
    }
    // 左摇杆 / Hat 轴导航震动已由 SubPageGamepadHost.onNavigate 集中注入（单一来源）；
    // D-pad 路径的 tick 由 subPageKeyHandler 提供，此处不再重复。
    LaunchedEffect(Unit) { navFlow.collect { moveFocus(it) } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .subPageKeyHandler { nk ->
                when (nk.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> { moveFocus(Direction.UP); true }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> { moveFocus(Direction.DOWN); true }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> { moveFocus(Direction.LEFT); true }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                    android.view.KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> { moveFocus(Direction.RIGHT); true }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                        Haptics.click()
                        if (shotCount.value > 0) {
                            val idx = focusIndex.value.coerceIn(0, (shotCount.value - 1).coerceAtLeast(0))
                            onOpenShot(shots[idx])
                        }; true
                    }
                    android.view.KeyEvent.KEYCODE_BACK,
                    android.view.KeyEvent.KEYCODE_BUTTON_B -> { Haptics.click(); onClose(); true }
                    else -> false
                }
            }
    ) {
        val maxW = maxWidth.value.toInt()
        val orientation = LocalConfiguration.current.orientation
        val cols = computeColumns(maxW, orientation)
        columns.value = cols

        // ── 整页入场 / 出场：对齐 Licenses 等沉浸子页，走全局动画机 subPageEnter/subPageExit
        //    （fadeIn + 横滑）；背景 GameArtBackground 与前景（标题栏 + 瀑布流）一并渐入，
        //    标题栏首帧即正确（[SubPageTitleBar]+[statusBarSafeTop] 已避让状态栏），消除原 ready
        //    闸门导致的「标题栏延迟硬切」；reduceMotion 时退化为瞬时。 ──
        AnimatedVisibility(
            visible = true,
            enter = MotionMachines.subPageEnter(reduceMotion),
            exit = MotionMachines.subPageExit(reduceMotion),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── 背景层（4 层渲染，统一复用 GameArtBackground；art=null 时回退 accent 渐变，非纯色）──
            GameArtBackground(art = art, accent = accent)
            // ── 前景（标题栏 + 全部内容）：首帧即渲染，无需等待 art ──
            Column(
                Modifier.fillMaxSize()
                    .statusBarSafeTop()
                    .navigationBarsPadding()
                    .edgeSafePadding(cutoutAdapt)
            ) {
                // 顶部：归一化标题栏（白色字，叠在游戏美术上），引用 KeyBadge.SubPageTitleBar 基线。
                val wide = LocalConfiguration.current.screenWidthDp.dp >= SubPageSpec.WideThreshold
                SubPageTitleBar(
                    title = label,
                    onBack = onClose,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    titleStyle = if (wide) MaterialTheme.typography.titleLarge
                                 else MaterialTheme.typography.titleMedium
                )

                // 主体：占满剩余高度
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (setupState) {
                        GameScreenshots.SetupState.NEED_TREE ->
                            CenteredHint("请先在游戏页授权截图文件夹")
                        GameScreenshots.SetupState.NEED_SAMPLE ->
                            CenteredHint("请先在游戏页选择该游戏的样例截图")
                        GameScreenshots.SetupState.UNSUPPORTED ->
                            CenteredHint("当前设备不支持截图归类")
                        GameScreenshots.SetupState.READY -> {
                            if (shots.isEmpty()) {
                                CenteredHint("暂无该游戏的截图 / 录屏")
                            } else {
                                val visibleShots = remember(shots, visibleCount) { shots.take(visibleCount) }
                                val entranceKey = remember(pkg) { pkg }
                                val headerShown = header != null || hero != null || capsule != null
                                val visibleRange by remember(gridState) {
                                    derivedStateOf {
                                        val vis = gridState.layoutInfo.visibleItemsInfo
                                        if (vis.isEmpty()) IntRange.EMPTY else vis.minOf { it.index }..vis.maxOf { it.index }
                                    }
                                }
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(cols),
                                    state = gridState,
                                    modifier = Modifier.fillMaxSize().padding(Dimens.md),
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                                    verticalItemSpacing = Dimens.sm,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Dimens.lg)
                                ) {
                                    if (headerShown) {
                                        items(
                                            count = 1,
                                            key = { "__header__" },
                                            span = { StaggeredGridItemSpan.FullLine }
                                        ) {
                                            GameScreenshotsAllHeader(
                                                header = header,
                                                hero = hero,
                                                capsule = capsule,
                                                accent = accent,
                                                name = label,
                                                orientation = orientation,
                                                maxWidthDp = maxW
                                            )
                                        }
                                    }
                                    items(visibleShots.size, key = { visibleShots[it].uri.toString() }) { index ->
                                        val shot = visibleShots[index]
                                        val gridIndex = index + if (headerShown) 1 else 0
                                        val isInViewport = gridIndex in visibleRange
                                        ScreenshotWaterfallTile(
                                            shot = shot,
                                            accent = accent,
                                            focused = focusIndex.value == index,
                                            entranceIndex = index,
                                            entranceKey = entranceKey,
                                            isInViewport = isInViewport,
                                            skipEntrance = gridState.isScrollInProgress,
                                            onClick = { onOpenShot(shot) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 步进式分页：单页渲染截图数（首屏 + 每次追加）。 */
private const val SHOTS_PAGE_SIZE = 24
/** 步进式分页：滚动 / 焦点距已加载末尾 [SHOTS_PREFETCH] 张时提前追加下一页。 */
private const val SHOTS_PREFETCH = 8

/** 响应式列数：根据整体宽度与横竖屏方向共同决定，列越多缩略图越小。 */
private fun computeColumns(maxWidthDp: Int, orientation: Int): Int {
    return if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        when {
            maxWidthDp < 900 -> 3
            maxWidthDp < 1400 -> 4
            else -> 5
        }
    } else {
        when {
            maxWidthDp < 480 -> 2
            maxWidthDp < 720 -> 3
            else -> 4
        }
    }
}

/**
 * 顶部 Header 居中图：优先 header，回退 hero / capsule；无美术时显示 accent 面板 + 应用名。
 * 保持原始宽高比，固定高度（横屏 200dp / 竖屏 150dp），绝不裁切 / 拉伸；
 * 位图宽高比超出可用宽度时整体等比缩放（高度随之收窄）以容纳完整图像。
 */
@Composable
private fun GameScreenshotsAllHeader(
    header: GameArtImage?,
    hero: GameArtImage?,
    capsule: GameArtImage?,
    accent: Color,
    name: String,
    orientation: Int,
    maxWidthDp: Int
) {
    val src = header?.source ?: hero?.source ?: capsule?.source
    val bmp = rememberArtBitmap(src)
    // 固定高度，横/竖屏分别取值；位图就绪后按原始比例推导宽度，保持形状不变。
    val h = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 200.dp else 150.dp
    val headerShape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxWidth().padding(top = Dimens.sm, bottom = Dimens.sm),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            val ratio = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
            val avail = maxWidthDp.dp
            val desiredW = h * ratio
            // 不溢出可用宽度则固定高度；否则整体等比缩放（高度随之收窄）以容纳完整图像。
            val (finalW, finalH) = if (desiredW <= avail) desiredW to h else avail to (avail / ratio)
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(finalH)
                    .width(finalW)
                    .clip(headerShape)
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(h)
                    .clip(headerShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(Dimens.FocusBorderWidth, accent.copy(alpha = 0.4f), headerShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Dimens.md)
                )
            }
        }
    }
}

/**
 * 瀑布流单卡片：按解码位图宽高比生成不等高，配合固定列宽形成瀑布高低差。
 *
 * 滚动适配（针对"滑太快只渲染占位符" + "滚到底/半途卡住时全部缩略图刷新"问题重构）：
 * - 视口外 OR 缩略图未加载完成 → 不渲染占位容器（返回无尺寸空容器），避免空白框。
 * - 重新滑入视口「且」缩略图已加载完成 → 以步进式错峰动画（按 [entranceIndex] 错峰 8ms/项，上限 160ms）
 *   依次浮现完整卡片框架与缩略图；离开视口则复位，下次进入重放。
 * - 入场动画仅在 [shouldRender] 真实变化（滑入/滑出视口、加载完成、焦点变动）时触发；
 *   刻意排除 [skipEntrance]（gridState.isScrollInProgress）：滚动停止瞬间该标志由 true→false，
 *   若作为依赖会让所有仍留在视口内的项同时重放，造成"全部缩略图刷新"的观感。
 * - 焦点项强制渲染以确保手柄焦点可见（解码失败则落回图标占位，仍不显示空白框）。
 */
@Composable
private fun ScreenshotWaterfallTile(
    shot: GameScreenshots.Shot,
    accent: Color,
    focused: Boolean,
    entranceIndex: Int,
    entranceKey: String,
    isInViewport: Boolean,
    skipEntrance: Boolean,
    onClick: () -> Unit
) {
    val thumb = rememberShotBitmap(shot)
    val bmp = thumb.bitmap
    // 真实宽高比：优先取自 ShotThumb（缓存命中即时可用 / 图片分支 bounds 预读），
    // 确保槽位从首次组合起就占据正确高度，杜绝 0 高度突变造成的瀑布流列偏移与回滑卡顿。
    val ratio = if (thumb.ratio > 0f) thumb.ratio else 16f / 9f

    // 入场触发条件：在视口内或获焦即错峰浮现；离开视口即复位，下次进入视口重放。
    // 注意：槽位高度始终预留（不依赖加载完成），故不再以「就绪」作为入场门槛，
    // 加载中的项以占位框先入场、位图解码完成后无缝替换为真实图。
    val shouldAppear = isInViewport || focused
    var appeared by remember(entranceKey, shot.uri) { mutableStateOf(false) }
    LaunchedEffect(entranceKey, shot.uri, shouldAppear) {
        if (!shouldAppear) { appeared = false; return@LaunchedEffect }
        if (appeared) return@LaunchedEffect // 已浮现且仍满足条件（如仅滚动停止），不重放
        // 快速滚动（isScrollInProgress）时立即到位，不等待错峰延迟；此处仅读取值、不作为依赖。
        if (skipEntrance) { appeared = true; return@LaunchedEffect }
        appeared = false
        val d = MotionSpec.staggerDelay(entranceIndex, perMs = 8, maxMs = 160)
        if (d > 0) delay(d)
        appeared = true
    }

    val entAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MotionSpec.Fast,
        label = "shotAlpha"
    )
    val entY by animateFloatAsState(
        targetValue = if (appeared) 0f else 12f,
        animationSpec = MotionSpec.Fast,
        label = "shotY"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .graphicsLayer { alpha = entAlpha; translationY = entY }
            .clip(MaterialTheme.shapes.medium)
            .focusBorder(focused, accent, MaterialTheme.shapes.medium)
            .focusable()
            .clickable { onClick() }
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = shot.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (thumb.done) {
            // 解码失败：渲染图标占位框（可点击），不再等待。
            Box(
                Modifier.fillMaxSize().background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Strong)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (shot.isVideo) Icons.Filled.Videocam else Icons.Filled.Image,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(Dimens.IconMd)
                )
            }
        } else {
            // 加载中：轻量骨架占位（与卡片同色），保留槽位高度不塌陷，回滑不再引发列偏移抖动。
            Box(Modifier.fillMaxSize().background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Strong)))
        }
        if (shot.isVideo) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(Dimens.xs)
                    .background(accent.copy(alpha = 0.85f), RoundedCornerShape(Dimens.RadiusXs))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("视频", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        BottomFadeMask(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = Dimens.xs, vertical = 4.dp),
            height = Dp.Unspecified,
            content = {
                Text(
                    shot.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}

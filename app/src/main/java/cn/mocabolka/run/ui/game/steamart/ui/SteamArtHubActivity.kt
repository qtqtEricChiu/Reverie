package cn.mocabolka.run.ui.game.steamart.ui

import cn.mocabolka.run.ui.components.statusBarSafeTop

import android.content.Intent
import cn.mocabolka.run.ui.game.GameArtBackground
import android.os.Bundle
import androidx.activity.ComponentActivity
import java.io.File
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import cn.mocabolka.run.ui.SettingsRepository
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

import cn.mocabolka.run.ui.game.GameArt
import cn.mocabolka.run.ui.game.GameArtResolver
import cn.mocabolka.run.ui.game.GameArtTombstone
import cn.mocabolka.run.ui.game.clearArtCachesForPackage
import cn.mocabolka.run.ui.game.ArtChangeBus
import cn.mocabolka.run.ui.game.steamart.domain.model.LogoPosition
import cn.mocabolka.run.gamepad.GamepadDetector
import cn.mocabolka.run.ui.game.extractAccent
import androidx.compose.material3.Icon
import cn.mocabolka.run.ui.components.ReverieFilledButton
import cn.mocabolka.run.ui.components.ReverieIconButton
import cn.mocabolka.run.ui.components.SubPageSpec
import cn.mocabolka.run.ui.components.SubPageTitleBar
import cn.mocabolka.run.ui.components.subPageKeyHandler
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.LightContent
import cn.mocabolka.run.ui.theme.resolvePageTheme
import cn.mocabolka.run.ui.DarkMode
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.ScrollState
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.gamepad.SubPageActivityHost
import cn.mocabolka.run.gamepad.markInputModeGamepad
import cn.mocabolka.run.gamepad.markInputModeTouch
import cn.mocabolka.run.ui.theme.focusBorder
import kotlinx.coroutines.flow.MutableSharedFlow
import cn.mocabolka.run.ui.theme.edgeSafePadding
import cn.mocabolka.run.ui.theme.ReverieToast
import cn.mocabolka.run.ui.theme.SurfaceCard
import cn.mocabolka.run.ui.theme.SurfaceTokens

/**
 * L2 美术资源获取页（二级子页面，独立 Activity 以支持原生过渡动画）。
 * 是 SteamDB 提取流程与自定义主题的唯一入口汇合处。
 */
class SteamArtHubActivity : ComponentActivity() {
    private lateinit var childLauncher: ActivityResultLauncher<Intent>
    /** 自定义主题返回后是否需通知游戏页重载美术（仅记录，不级联关掉本二级页）。 */
    private val pendingRefreshState = mutableStateOf(false)
    /**
     * 「已应用自定义主题」提示专用计数：仅自定义主题子页回传 RESULT_OK（真有改动）时 +1。
     * 修复：原以 pendingRefreshState 布尔 false→true 沿触发 toast，同一 Hub 会话第二次从
     * 自定义页返回时值不再变化 → 不弹提示；且不能复用 hubReloadKey（清除按钮/ArtChangeBus
     * 也会自增，会误弹「已应用」）。
     */
    private val customAppliedTick = mutableStateOf(0)
    /** 二级页就地刷新信号：自定义主题返回后 +1，驱动 Hub 重新加载并展示新美术（给刷新喘息时间）。 */
    private val hubReloadKey = mutableStateOf(0)
    /** 最近一次拉起的子页是否为自定义主题（决定返回时是否级联回游戏页）。 */
    private var lastChildWasCustom = false
    /** 美术资源 Hub 页专属滚动状态（右摇杆滚动 + 焦点跟随底座，提升到字段以复用同一实例）。 */
    private val scrollState = ScrollState(0)
    /** 子页面右摇杆滚动 + 左摇杆/Hat 导航引擎（与 SubPageScaffold 同构，统一托管）。 */
    private lateinit var subPage: SubPageActivityHost
    /** 美术资源 Hub 页焦点总数（与 Composable 内 totalSlots 保持一致）。 */
    private val hubTotalSlots = 10
    /** 各槽位测量时的窗口绝对 y / 高度 / 当时滚动偏移，由 Compose onGloballyPositioned 写入；
     *  三者组合可在任意时刻折算出「槽位相对滚动内容的偏移」(与滚动无关，恒定有效)，
     *  供「窗口跟随焦点移动」与「焦点跟随右摇杆滚动」读取（与 SubPageScaffold 同构）。 */
    private val hubSlotTop = List(hubTotalSlots) { mutableStateOf<Int?>(null) }
    private val hubSlotH = List(hubTotalSlots) { mutableStateOf<Int?>(null) }
    private val hubSlotScroll = List(hubTotalSlots) { mutableStateOf<Int?>(null) }
    /** 滚动内容容器根节点在窗口中的 y（px，恒定不变），作为槽位偏移基准。 */
    private val hubContentTop = mutableStateOf<Int?>(null)
    /** 滚动内容可视高度（px），作为右摇杆视口高度来源。 */
    private val hubViewportH = mutableStateOf<Int?>(null)
    /** 逻辑焦点索引（提升到 Activity 字段，供右摇杆焦点同步回调直接写入，与 SubPageScaffold 同构）。 */
    private val focusIndex = mutableStateOf(0)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SEC-S1：包名净化——pkg 将拼入 filesDir 路径，非法包名（路径穿越等）直接拒绝进入
        val pkg = SteamArtContracts.sanitizePackage(intent.getStringExtra(SteamArtContracts.EXTRA_PACKAGE))
            ?: run { finish(); return }
        val label = intent.getStringExtra(SteamArtContracts.EXTRA_LABEL).takeIf { !it.isNullOrBlank() } ?: pkg
        val accentHex = intent.getStringExtra(SteamArtContracts.EXTRA_ACCENT)
        val inheritedAccent = accentHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        // 父级（游戏页）确认存在整页背景图：本地美术尚未落盘时也强制深色，避免浅色闪烁
        val hasPageBgFromParent = intent.getBooleanExtra(SteamArtContracts.EXTRA_HAS_PAGE_BG, false)

        childLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                if (lastChildWasCustom) {
                    // 自定义主题已落地：留在「美术资源」二级页（不级联回游戏页一级）。
                    // 就地刷新 Hub 自己的美术展示（让用户在 2 级页直接看到新主题 = 给刷新喘息时间），
                    // 并记录待刷新标记，待用户从本页返回时再通知游戏页重载美术。
                    pendingRefreshState.value = true
                    hubReloadKey.value++
                    customAppliedTick.value++
                } else {
                    // 提取完成：保持原行为，级联回到游戏页并重载美术。
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }

        val ctx = this
        val settings = SettingsRepository(this)
        val cutoutAdapt = settings.cutoutAdapt
        setContent {
            val ctx = LocalContext.current
            // remember 缓存 Flow，避免每次重组重新注册广播接收器
            val gamepadFlow = remember(ctx) { GamepadDetector.gamepadConnectedFlow(ctx) }
            // 美术在 Activity 顶层加载，使主题判定可实时跟随「当前是否有整页背景图（page_bg_raw / hero 等回归）」
            var art by remember(pkg, label) { mutableStateOf<GameArt?>(null) }
            LaunchedEffect(pkg, label, hubReloadKey.value) { art = GameArtResolver(ctx).load(pkg, label) }
            // 订阅美术变更总线：美术被新建/更换/清除时本页立即重算主题（与背后的游戏页同步）
            LaunchedEffect(Unit) {
                ArtChangeBus.changes.filter { it == pkg }.collect { hubReloadKey.value++ }
            }
            // 实时判定：有整页背景图 → 深色 + 反白部件；无任何背景图 → 跟随设置（白底正常，此时确实没有美术资源）。
            // 父页确认有 page_bg_raw 时，本地美术尚未落盘也强制深色，避免浅色闪烁。
            val hasArtEffective = art?.background != null || (art == null && hasPageBgFromParent)
            val theme = resolvePageTheme(hasArtEffective, settings)
            LandscapeTheme(
                darkTheme = theme.darkTheme,
                useMonet = settings.useMonet,
                isAmoled = settings.darkMode == DarkMode.AMOLED,
                gamepadConnected = gamepadFlow.collectAsState(false).value,
                glassSurface = settings.glassSurfaceFlow.collectAsState().value
            ) {
                LightContent(lightContent = theme.lightContent) {
                SteamArtHubScreen(
                    cutoutAdapt = cutoutAdapt,
                    themedNoArtFallback = theme.themedNoArtFallback,
                    art = art,
                    pkg = pkg,
                    label = label,
                    inheritedAccent = inheritedAccent,
                    reloadKey = hubReloadKey,
                    appliedTick = customAppliedTick,
                    scrollState = scrollState,
                    navFlow = subPage.navFlow,
                    focusIndex = focusIndex,
                    slotTop = hubSlotTop,
                    slotH = hubSlotH,
                    slotScroll = hubSlotScroll,
                    contentTop = hubContentTop,
                    viewportH = hubViewportH,
                    onBack = { if (pendingRefreshState.value) setResult(RESULT_OK); finish() },
                    onClear = { setResult(RESULT_OK) },
                    onExtract = { appId, query, logoPositionJson ->
                        this@SteamArtHubActivity.lastChildWasCustom = false
                        val i = Intent(ctx, SteamArtExtractActivity::class.java).apply {
                            putExtra(SteamArtContracts.EXTRA_PACKAGE, pkg)
                            putExtra(SteamArtContracts.EXTRA_LABEL, label)
                            if (appId != null) putExtra(SteamArtContracts.EXTRA_APP_ID, appId)
                            else putExtra(SteamArtContracts.EXTRA_QUERY, query.orEmpty())
                            if (logoPositionJson != null) putExtra(SteamArtContracts.EXTRA_LOGO_POSITION, logoPositionJson)
                            putExtra(SteamArtContracts.EXTRA_ACCENT, accentHex)
                            // 转发父页确认的背景图标记：让子页（提取/自定义）在美术落盘前也强制深色
                            putExtra(SteamArtContracts.EXTRA_HAS_PAGE_BG, hasPageBgFromParent)
                        }
                        childLauncher.launch(i)
                    },
                    onCustom = {
                        this@SteamArtHubActivity.lastChildWasCustom = true
                        val i = Intent(ctx, SteamArtCustomActivity::class.java).apply {
                            putExtra(SteamArtContracts.EXTRA_PACKAGE, pkg)
                            putExtra(SteamArtContracts.EXTRA_LABEL, label)
                            putExtra(SteamArtContracts.EXTRA_ACCENT, accentHex)
                            // 转发父页确认的背景图标记：让子页（提取/自定义）在美术落盘前也强制深色
                            putExtra(SteamArtContracts.EXTRA_HAS_PAGE_BG, hasPageBgFromParent)
                        }
                        childLauncher.launch(i)
                    }
                )
                }
            }
        }

        // 右摇杆滚动 + 左摇杆/Hat 焦点导航（与 SubPageScaffold 同构）。
        subPage = SubPageActivityHost(
            activity = this,
            getState = { scrollState },
            getFirstVisibleItemIndex = {
                val ct = hubContentTop.value
                if (ct == null) {
                    focusIndex.value
                } else {
                    val vpTop = scrollState.value
                    val vpSize = hubViewportH.value ?: (scrollState.maxValue - vpTop).coerceAtLeast(0)
                    var best = -1
                    var bestTop = Int.MAX_VALUE
                    // 跳过槽位 0（返回按钮固定在标题栏，不在滚动区内）
                    for (i in 1 until hubSlotTop.size) {
                        val ay = hubSlotTop[i].value ?: continue
                        val h = hubSlotH[i].value ?: continue
                        val s = hubSlotScroll[i].value ?: continue
                        val t = ay - ct + s
                        val b = t + h
                        if (b >= vpTop && t <= vpTop + vpSize) {
                            if (t < bestTop) { bestTop = t; best = i }
                        }
                    }
                    if (best < 0) focusIndex.value else best
                }
            },
            customOnFocusSync = { idx -> if (idx >= 0) focusIndex.value = idx },
            customOnNavigate = { dir -> lifecycleScope.launch { subPage.navFlow.emit(dir) } },
            navFlowCapacity = 0
        )
        subPage.start()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        markInputModeGamepad(event)
        if (subPage.dispatchMotionEvent(event) == true) return true
        return super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        markInputModeGamepad(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        markInputModeTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        // 系统预测返回（不注册 OnBackInvokedCallback）由系统绘制预览动画并默认 finish；
        // 若本次退出需刷新游戏页（pendingRefreshState），在此补 setResult(RESULT_OK)，等效原回调逻辑。
        if (pendingRefreshState.value && isFinishing) {
            setResult(RESULT_OK)
        }
    }

    override fun onDestroy() {
        subPage.stop()
        super.onDestroy()
    }
}

// 与 Run-SteamdbDemo 的 PRESET_GAMES 保持一致。
// 注意：下列 logoPosition 仅是「兜底默认值」，仅当 WebView 探针与商店 API 都拿不到真实定位时才会写入 meta.json；
// 一旦后续导入的新美术资源（探针/API/自定义主题里的真实定位）落盘，必定优先于这里的预设（见 ExtractAssetsUseCase 的
// webView ?: store ?: preset 优先级链）。预设值即来自 SteamDB 的真实 width_pct/height_pct，由用户 2026-07-26 提供。
private data class PresetGame(val name: String, val appId: Long, val logoPosition: LogoPosition)
private val PRESETS = listOf(
    PresetGame("无限暖暖", 3164330L, LogoPosition("BottomLeft", 17.73611f, 42.36111f)),
    PresetGame("绝区零", 4162040L, LogoPosition("BottomLeft", 37.18221f, 71.32872f)),
    PresetGame("鸣潮", 3513350L, LogoPosition("BottomLeft", 22.66531f, 45.13934f))
)

@Composable
private fun SteamArtHubScreen(
    cutoutAdapt: Boolean = false,
    themedNoArtFallback: Boolean = false,
    art: GameArt? = null,
    pkg: String,
    label: String,
    inheritedAccent: androidx.compose.ui.graphics.Color? = null,
    /** 二级页就地刷新信号（自定义主题返回后由 Activity +1），驱动 Hub 重新加载并展示新美术。 */
    reloadKey: MutableState<Int>,
    /** 「已应用自定义主题」提示计数（自定义子页真有改动返回时由 Activity +1），每次自增都重新弹提示。 */
    appliedTick: State<Int>,
    onBack: () -> Unit,
    onExtract: (appId: Long?, query: String?, logoPositionJson: String?) -> Unit,
    onCustom: () -> Unit,
    onClear: () -> Unit,
    /** 美术资源 Hub 页专属滚动状态（右摇杆滚动底座）。 */
    scrollState: ScrollState,
    /** 右摇杆 / 左摇杆 焦点导航桥接流。 */
    navFlow: MutableSharedFlow<Direction>,
    /** 逻辑焦点索引（Activity 级，供右摇杆焦点同步回写）。 */
    focusIndex: MutableState<Int>,
    /** 各槽位测量时的窗口绝对 y（px），由 onGloballyPositioned 写入。 */
    slotTop: List<MutableState<Int?>>,
    /** 各槽位测量时的高度（px）。 */
    slotH: List<MutableState<Int?>>,
    /** 各槽位测量时的滚动偏移（px）。 */
    slotScroll: List<MutableState<Int?>>,
    /** 滚动内容容器根节点在窗口中的 y（px），作为槽位偏移基准。 */
    contentTop: MutableState<Int?>,
    /** 滚动内容可视高度（px），作为右摇杆视口高度来源。 */
    viewportH: MutableState<Int?>
) {
    val ctx = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    // 应用成功提示：项目标准 ReverieToast（in-app 浮层），替代系统默认 Toast。
    // 自定义主题真有改动返回（appliedTick 自增）时弹出，同一会话多次进出每次都会重新提示。
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableStateOf(0) }
    LaunchedEffect(toastKey) { if (toastMsg != null) { delay(2500); toastMsg = null } }
    LaunchedEffect(appliedTick.value) { if (appliedTick.value > 0) { toastMsg = "已应用自定义主题"; toastKey++ } }
    val sources = remember(art) {
        art?.let {
            listOfNotNull(it.background, it.hero, it.logo, it.capsule, it.header).map { s -> s.source }
        } ?: emptyList()
    }
    // 仅当界面中存在已加载的美术资源时，清除按钮才处于激活可用状态
    val hasArt = art?.background != null || art?.hero != null || art?.logo != null ||
            art?.capsule != null || art?.header != null
    var accent by remember { mutableStateOf<Color>(inheritedAccent ?: primary) }
    LaunchedEffect(art, inheritedAccent) {
        accent = if (inheritedAccent != null) inheritedAccent
                 else art?.let { extractAccent(ctx, sources, it.accentColor ?: primary) } ?: primary
    }

    // 手柄文本输入状态提升到页面级，供焦点状态机读取（A 键触发提取）。
    var query by remember { mutableStateOf("") }
    var appIdText by remember { mutableStateOf("") }

    // ── 手柄焦点状态机（美术资源 Hub 页全面适配：焦点覆盖 / 空间导航 / 高对比焦点框 / A 确认）──
    val scope = rememberCoroutineScope()
    // 槽位：0=返回, 1=名称输入框, 2=AppID输入框, 3=搜索并提取, 4=按ID提取,
    // 5..7=热门预设(3), 8=进入自定义主题, 9=清除。
    val totalSlots = 10
    /**
     * 槽位位置追踪：把每个可交互元素测量时的窗口绝对 y / 高度 / 当时滚动偏移写入
     * [slotTop]/[slotH]/[slotScroll]，三者组合可在任意时刻折算「槽位相对滚动内容的偏移」P
     * （与当前滚动无关，恒定有效），供「窗口跟随焦点移动」与「焦点跟随右摇杆滚动」读取。
     */
    val track: (Int) -> Modifier = { i ->
        Modifier.onGloballyPositioned { c ->
            slotTop[i].value = c.positionInRoot().y.roundToInt()
            slotH[i].value = c.size.height
            slotScroll[i].value = scrollState.value
        }
    }
    /** 折算槽位 i 在内容坐标系中的 [top, bottom]（px）。 */
    fun pOf(i: Int): Pair<Int, Int>? {
        val ct = contentTop.value ?: return null
        val ay = slotTop.getOrNull(i)?.value ?: return null
        val h = slotH.getOrNull(i)?.value ?: return null
        val s = slotScroll.getOrNull(i)?.value ?: return null
        val p = ay - ct + s
        return p to (p + h)
    }
    /**
     * 窗口跟随焦点（与 SubPageScaffold/主页同构）：导航（左摇杆 / 方向键）改变焦点后，
     * 只要目标槽位未完全落在「带安全边距的可视区」内就滚动跟随（焦点在哪滚到哪），
     * 不再用「完全移出视口才滚」的保守策略——旧策略下焦点在可视区内移动时页面不跟随。
     * 带 margin 安全边距避免与右摇杆滚动（焦点跟随）互相打架形成抖动回路。
     */
    fun scrollFocusIntoView(i: Int) {
        if (i == 0) return  // 返回按钮固定在标题栏，不在滚动区内
        val pb = pOf(i) ?: return
        val (t, b) = pb
        val vpTop = scrollState.value
        val vpSize = viewportH.value ?: (scrollState.maxValue - vpTop).coerceAtLeast(0)
        val vpBot = vpTop + vpSize
        val margin = 90
        val target = when {
            b > vpBot - margin -> (b - vpSize + margin).coerceAtLeast(0)   // 底部露出 → 滚到刚好露底
            t < vpTop + margin -> (t - margin).coerceAtLeast(0)            // 顶部露出 → 滚到刚好露顶
            else -> null
        }
        if (target != null) scope.launch { scrollState.animateScrollTo(target.coerceAtMost(scrollState.maxValue)) }
    }
    fun activate(slot: Int) {
        when (slot) {
            0 -> onBack()
            1, 2 -> { /* 文本输入框：触控输入；手柄下 A 不抢占焦点 */ }
            3 -> if (query.isNotBlank()) onExtract(null, query, null)
            4 -> appIdText.toLongOrNull()?.let { onExtract(it, null, null) }
            in 5..7 -> PRESETS[slot - 5].let { onExtract(it.appId, null, it.logoPosition.toJson()) }
            8 -> onCustom()
            9 -> if (hasArt) onClear()
        }
    }
    fun moveFocus(dir: Direction) {
        val fi = focusIndex.value
        focusIndex.value = when (dir) {
            Direction.UP -> (fi - 1).coerceAtLeast(0)
            Direction.DOWN -> (fi + 1).coerceAtMost(totalSlots - 1)
            Direction.LEFT, Direction.RIGHT -> when {
                fi in 1..2 -> if (fi == 1) 2 else 1
                fi in 3..4 -> if (fi == 3) 4 else 3
                fi in 5..7 -> (fi + if (dir == Direction.RIGHT) 1 else -1).coerceIn(5, 7)
                else -> fi
            }
        }
        scrollFocusIntoView(focusIndex.value)
    }
    // 左摇杆 / Hat 轴导航震动已由 SubPageGamepadHost.onNavigate 集中注入（单一来源）；
    // D-pad 路径的 tick 由 subPageKeyHandler 提供，此处不再重复。
    LaunchedEffect(Unit) { navFlow.collect { moveFocus(it) } }

    Box(
        Modifier
            .fillMaxSize()
            .subPageKeyHandler { nk ->
                when (nk.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { moveFocus(Direction.UP); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { moveFocus(Direction.DOWN); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { moveFocus(Direction.LEFT); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { moveFocus(Direction.RIGHT); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { activate(focusIndex.value); true }
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { onBack(); true }
                    else -> false
                }
            }
    ) {
        GameArtBackground(art, accent, themedNoArtFallback = themedNoArtFallback)
    Column(
        Modifier.fillMaxSize()
            .statusBarSafeTop()
            .navigationBarsPadding()
            // 挖孔屏适配：仅当设置页「挖孔屏适配」全局开关打开时生效（与主页 EdgeToEdge 一致）。
            // 根 Box 已 fillMaxSize 铺满全屏，此处由内容容器承载 edgeSafePadding。
            .edgeSafePadding(cutoutAdapt)
            .padding(horizontal = Dimens.ContentHorizontal)
    ) {
            // 顶部：归一化标题栏（白色字，叠在游戏美术上），引用 KeyBadge.SubPageTitleBar 基线。
            // 标题栏固定，不随下方内容滚动（与二级子页统一）。
            val wide = LocalConfiguration.current.screenWidthDp.dp >= SubPageSpec.WideThreshold
            SubPageTitleBar(
                title = "美术资源",
                onBack = onBack,
                isBackFocused = focusIndex.value == 0,
                onBackFocus = { focusIndex.value = 0 },
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                titleStyle = if (wide) MaterialTheme.typography.titleLarge
                             else MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(Dimens.sm))

            // 内容区：仅本区域可滚动，标题栏保持固定
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = Dimens.lg)
                    .onGloballyPositioned { c ->
                        contentTop.value = c.positionInRoot().y.roundToInt()
                        viewportH.value = c.size.height
                    }
            ) {

            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.md).fillMaxWidth()) {
                    androidx.compose.material3.Text(
                        "SteamDB 自动提取",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { androidx.compose.material3.Text("游戏名称（搜索）") },
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.md)
                            .focusBorder(focusIndex.value == 1, accent, MaterialTheme.shapes.small)
                            .then(track(1)),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedContainerColor = SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default),
                            unfocusedContainerColor = SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default),
                            focusedLabelColor = accent,
                            cursorColor = accent,
                            // 游戏独立取色：聚焦指示线用 accent（而非穿透到全局 primary/莫奈），
                            // 未聚焦/禁用用中性 muted，彻底隔离应用默认色。
                            focusedIndicatorColor = accent,
                            unfocusedIndicatorColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint),
                            disabledIndicatorColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint)
                        )
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    OutlinedTextField(
                        value = appIdText,
                        onValueChange = { appIdText = it.filter { c -> c.isDigit() } },
                        label = { androidx.compose.material3.Text("Steam AppID（直接）") },
                        modifier = Modifier.fillMaxWidth()
                            .focusBorder(focusIndex.value == 2, accent, MaterialTheme.shapes.small)
                            .then(track(2)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedContainerColor = SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default),
                            unfocusedContainerColor = SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default),
                            focusedLabelColor = accent,
                            cursorColor = accent,
                            // 游戏独立取色：聚焦指示线用 accent（而非穿透到全局 primary/莫奈），
                            // 未聚焦/禁用用中性 muted，彻底隔离应用默认色。
                            focusedIndicatorColor = accent,
                            unfocusedIndicatorColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint),
                            disabledIndicatorColor = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint)
                        )
                    )
                    Spacer(Modifier.height(Dimens.md))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        ReverieFilledButton(
                            text = "搜索并提取",
                            onClick = { if (query.isNotBlank()) onExtract(null, query, null) },
                            enabled = query.isNotBlank(),
                            accent = accent,
                            focused = focusIndex.value == 3,
                            modifier = track(3)
                        )
                        ReverieFilledButton(
                            text = "按 ID 提取",
                            onClick = { appIdText.toLongOrNull()?.let { onExtract(it, null, null) } },
                            enabled = appIdText.toLongOrNull() != null,
                            accent = accent,
                            focused = focusIndex.value == 4,
                            modifier = track(4)
                        )
                    }
                    Spacer(Modifier.height(Dimens.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        PRESETS.forEachIndexed { i, p ->
                            AssistChip(
                                modifier = Modifier.focusBorder(focusIndex.value == 5 + i, accent, MaterialTheme.shapes.small)
                                    .then(track(5 + i)),
                                onClick = { onExtract(p.appId, null, p.logoPosition.toJson()) },
                                label = { androidx.compose.material3.Text(p.name) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = accent
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Dimens.md))
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.md).fillMaxWidth()) {
                    androidx.compose.material3.Text(
                        "自定义主题",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    androidx.compose.material3.Text(
                        "手动设定主色、背景图与 Logo 位置/尺寸，或选图拼贴专属主题。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(Dimens.md))
                    ReverieFilledButton(
                        text = "进入自定义主题",
                        onClick = onCustom,
                        accent = accent,
                        focused = focusIndex.value == 8,
                        modifier = track(8)
                    )
                }
            }

            Spacer(Modifier.height(Dimens.md))

            // 清除已加载的 SteamDB 美术资源（通用功能组件；无资源时置灰禁用）
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ClearArtButton(
                    pkg = pkg,
                    hasArt = hasArt,
                    accent = accent,
                    modifier = Modifier.focusBorder(focusIndex.value == 9, accent).then(track(9)),
                    onCleared = { reloadKey.value++; onClear() }
                )
            }
            }  // close inner scroll Column (内容滚动区)
        }

        // 应用成功提示（项目标准 in-app 浮层，非系统默认 Toast）
        ReverieToast(message = toastMsg)
    }
}

/**
 * 通用功能组件：清除通过 SteamDB 获取的美术资源数据。
 *
 * 点击后删除 `filesDir/steamart/<pkg>` 整目录（含自动提取与自定义主题资产），
 * 并打上「已清除」墓碑标记 —— 内置预设游戏(assets)在清除后不再兜底回落，
 * 真正回到无任何美术资源的最初界面。删除在 IO 线程执行，避免阻塞主线程，完成后主线程回传 [onCleared]。
 *
 * 激活逻辑：[hasArt] 为 true（界面中存在已加载的美术资源）时才可点击；否则置灰禁用。
 */
@Composable
fun ClearArtButton(
    pkg: String,
    hasArt: Boolean,
    /** 游戏独立取色：传入后按钮容器/文字/焦点框改用该色，隔离全局 primary / Monet。 */
    accent: Color? = null,
    modifier: Modifier = Modifier,
    onCleared: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    ReverieFilledButton(
        text = "清除",
        onClick = {
            scope.launch(Dispatchers.IO) {
                // ① 删除用户层资源（自动提取 + 自定义主题）整目录
                File(ctx.filesDir, "steamart/$pkg").deleteRecursively()
                // ② 打墓碑：内置预设游戏(assets)清除后不再兜底回落，真正回到无美术初始态
                GameArtTombstone.mark(ctx, pkg)
                // ③ 清进程内位图/取色缓存，杜绝残留旧图
                clearArtCachesForPackage(pkg)
                // 广播美术变更，令所有游戏页及子页立即重算主题（含背后的游戏页一级页）
                ArtChangeBus.notify(pkg)
                // 界面刷新必须在主线程回调
                withContext(Dispatchers.Main) { onCleared() }
            }
        },
        enabled = hasArt,
        accent = accent,
        modifier = modifier
    )
}

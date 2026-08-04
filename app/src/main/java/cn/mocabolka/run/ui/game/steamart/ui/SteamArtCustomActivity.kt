package cn.mocabolka.run.ui.game.steamart.ui

import cn.mocabolka.run.ui.components.statusBarSafeTop

import android.app.Activity
import cn.mocabolka.run.ui.game.GameArtBackground
import cn.mocabolka.run.ui.theme.DeviceProfile
import cn.mocabolka.run.ui.theme.deviceProfile
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import cn.mocabolka.run.ui.SettingsRepository
import cn.mocabolka.run.ui.game.steamart.domain.model.AssetKind
import cn.mocabolka.run.ui.game.steamart.domain.model.LogoPosition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.gamepad.GamepadDetector
import cn.mocabolka.run.ui.components.ReverieIconButton
import cn.mocabolka.run.ui.components.SubPageSpec
import cn.mocabolka.run.ui.components.SubPageTitleBar
import cn.mocabolka.run.ui.components.subPageKeyHandler
import cn.mocabolka.run.ui.game.ArtSource
import cn.mocabolka.run.ui.game.GameArt
import cn.mocabolka.run.ui.game.GameArtResolver
import cn.mocabolka.run.ui.game.extractAccent
import cn.mocabolka.run.ui.game.rememberArtBitmap
import cn.mocabolka.run.ui.game.steamart.ui.SteamArtContracts
import cn.mocabolka.run.ui.game.ArtChangeBus
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.LightContent
import cn.mocabolka.run.ui.theme.resolvePageTheme
import cn.mocabolka.run.ui.DarkMode
import cn.mocabolka.run.ui.theme.edgeSafePadding
import cn.mocabolka.run.ui.theme.ReverieToast
import cn.mocabolka.run.ui.theme.SurfaceCard
import cn.mocabolka.run.ui.theme.SurfaceTokens
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import org.json.JSONObject
import java.io.File

/**
 * L3 自定义主题页（三级子页面，独立 Activity）。
 * 恢复 Demo 版「选择 6 张图片」拼贴自定义主题的能力，并保留「取主色」功能：
 * - 6 张图片分别映射为 library_hero / library_logo / library_capsule / library_header / page_bg_raw /（第 6 张复用 hero）；
 * - 主色可手动从预设色板选择，未选时由 6 张图自动取主色（HSV 直方图）；
 * - 主色无美术资源时继承游戏页主色，避免回退到全局默认色。
 */

/**
 * 根据 Logo 的真实 widthPct 计算推荐 scale。
 * 仅手机竖屏生效，其他模式（横屏 / 平板竖屏）返回 1.0。
 *
 * 原理：computeLogoBox 中 ratio = boxW / referenceW，在 logoScale=1 时恰好等于 widthPct/100，
 * 因此无需模拟 HeroBanner 的完整计算链（contentH × 3.097），直接以 widthPct 判定即可。
 */
private fun recommendLogoScale(widthPct: Float, isPhonePortrait: Boolean): Float {
    if (!isPhonePortrait) return 1f
    // 没有 SteamDB 真实尺寸（默认 50% 或 0）→ 推荐 1.0
    if (widthPct <= 0f) return 1f

    val ratio = widthPct / 100f   // Logo 宽度占 banner 比例
    return when {
        ratio <= 0.1f -> 1.2f                                  // ≤10%：放大到 1.2 倍
        ratio < 0.3f  -> 1.2f - 0.2f * (ratio - 0.1f) / 0.2f  // 10%→30%：1.2 → 1.0 线性过渡
        ratio <= 0.5f -> 1f                                    // 30%→50%：保持 1.0
        ratio < 0.7f  -> 1f - 0.2f * (ratio - 0.5f) / 0.2f    // 50%→70%：1.0 → 0.8 线性过渡
        else          -> 0.8f                                  // ≥70%：缩小到 0.8 倍
    }
}

class SteamArtCustomActivity : ComponentActivity() {
    /** 自定义主题页专属滚动状态（右摇杆滚动底座）。 */
    private val scrollState = ScrollState(0)
    /** 自定义主题页焦点总数（与 Composable 内 totalSlots 保持一致）。 */
    private val customTotalSlots = 27
    /**
     * 退出保存兜底回调：由 Compose 侧注册（见 flushAndReport），返回「本次会话是否有改动」。
     * 系统手势返回不经由 requestExit（本页不注册 OnBackInvokedCallback，预测性返回交给系统），
     * 故在 [onStop] (isFinishing) 统一补一次 flush：写 meta + ArtChangeBus 广播 + RESULT_OK，
     * 保证 Hub 页能弹出「已应用自定义主题」提示且背后页面立即刷新（与 SteamArtHubActivity.onStop 同构）。
     */
    internal var flushOnExit: (() -> Boolean)? = null
    /** 各槽位测量时的窗口绝对 y / 高度 / 当时滚动偏移，由 Compose onGloballyPositioned 写入；
     *  三者组合可在任意时刻折算出「槽位相对滚动内容的偏移」(与滚动无关，恒定有效)，
     *  供「窗口跟随焦点移动」与「焦点跟随右摇杆滚动」读取（与 SubPageScaffold 同构）。 */
    private val customSlotTop = List(customTotalSlots) { mutableStateOf<Int?>(null) }
    private val customSlotH = List(customTotalSlots) { mutableStateOf<Int?>(null) }
    private val customSlotScroll = List(customTotalSlots) { mutableStateOf<Int?>(null) }
    /** 滚动内容容器根节点在窗口中的 y（px，恒定不变），作为槽位偏移基准。 */
    private val customContentTop = mutableStateOf<Int?>(null)
    /** 滚动内容可视高度（px），作为右摇杆视口高度来源。 */
    private val customViewportH = mutableStateOf<Int?>(null)
    /** 逻辑焦点索引（提升到 Activity 字段，供右摇杆焦点同步回调直接写入，与 SubPageScaffold 同构）。 */
    private val focusIndex = mutableStateOf(0)
    /** 子页右摇杆滚动 + 左摇杆/Hat 导航引擎（统一封装到 SubPageActivityHost）。 */
    private lateinit var subpage: SubPageActivityHost
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式 + 边缘到边缘由 SubPageActivityHost.start() 统一处理（预测性返回由系统默认行为接管，不注册 OnBackInvokedCallback）。
        // 退出即应用（三条路径全覆盖）：
        //  1) 手柄 B 键 / 标题栏返回钮 → requestExit（flush + 有改动才 RESULT_OK + finish）；
        //  2) 系统手势返回 → 系统默认 finish，由 onStop(isFinishing) 兜底 flush + RESULT_OK；
        //  3) 极端销毁 → flush 幂等（flushed 守卫），不会重复写盘。
        // 「已应用自定义主题」提示统一由 Hub 页（美术资源二级页）弹出。

        // SEC-S1：包名净化——pkg 将拼入 filesDir 路径（含 deleteRecursively），非法包名直接拒绝进入
        val pkg = SteamArtContracts.sanitizePackage(intent.getStringExtra(SteamArtContracts.EXTRA_PACKAGE))
            ?: run { finish(); return }
        val label =
            intent.getStringExtra(SteamArtContracts.EXTRA_LABEL).takeIf { !it.isNullOrBlank() } ?: pkg
        val accentHex = intent.getStringExtra(SteamArtContracts.EXTRA_ACCENT)
        val inheritedAccent =
            accentHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        // 父级（游戏页 / Hub）确认存在整页背景图：本地美术尚未落盘时也强制深色，避免浅色闪烁
        val hasPageBgFromParent = intent.getBooleanExtra(SteamArtContracts.EXTRA_HAS_PAGE_BG, false)

        val settings = SettingsRepository(this)
        val cutoutAdapt = settings.cutoutAdapt

        setContent {
            val themeCtx = LocalContext.current
            // remember 缓存 Flow，避免每次重组重新注册广播接收器
            val gamepadFlow = remember(themeCtx) { GamepadDetector.gamepadConnectedFlow(themeCtx) }
            // 美术在 Activity 顶层加载，使主题判定可实时跟随「当前是否有整页背景图（page_bg_raw / hero 等回归）」
            var art by remember(pkg, label) { mutableStateOf<GameArt?>(null) }
            LaunchedEffect(pkg, label) { art = GameArtResolver(themeCtx).load(pkg, label) }
            // 订阅美术变更总线：背后的游戏页/Hub 在美术变更时本页亦立即重算主题
            LaunchedEffect(Unit) {
                ArtChangeBus.changes.filter { it == pkg }.collect {
                    art = GameArtResolver(themeCtx).load(pkg, label)
                }
            }
            // 实时判定：有整页背景图 → 深色 + 反白部件；无任何背景图 → 跟随设置（白底正常，此时确实没有美术资源）
            // 父页确认有整页背景图（page_bg_raw）时，即便本地美术尚未落盘也强制深色，避免浅色闪烁
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
                SteamArtCustomScreen(
                    cutoutAdapt = cutoutAdapt,
                    themedNoArtFallback = theme.themedNoArtFallback,
                    art = art,
                    pkg = pkg,
                    label = label,
                    inheritedAccent = inheritedAccent,
                    scrollState = scrollState,
                    navFlow = subpage.navFlow,
                    focusIndex = focusIndex,
                    slotTop = customSlotTop,
                    slotH = customSlotH,
                    slotScroll = customSlotScroll,
                    contentTop = customContentTop,
                    viewportH = customViewportH
                )
                }
            }
        }

        // 右摇杆滚动 + 左摇杆/Hat 焦点导航（与 SubPageScaffold 同构）。
        subpage = SubPageActivityHost(
            activity = this,
            getState = { scrollState },
            getFirstVisibleItemIndex = {
                val ct = customContentTop.value
                if (ct == null) {
                    focusIndex.value
                } else {
                    val vpTop = scrollState.value
                    val vpSize = customViewportH.value ?: (scrollState.maxValue - vpTop).coerceAtLeast(0)
                    var best = -1
                    var bestTop = Int.MAX_VALUE
                    // 跳过槽位 0（返回按钮固定在标题栏，不在滚动区内）
                    for (i in 1 until customSlotTop.size) {
                        val ay = customSlotTop[i].value ?: continue
                        val h = customSlotH[i].value ?: continue
                        val s = customSlotScroll[i].value ?: continue
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
            customOnNavigate = { dir -> lifecycleScope.launch { subpage.navFlow.emit(dir) } },
            navFlowCapacity = 0
        )
        subpage.start()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        markInputModeGamepad(event)
        if (subpage.dispatchMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) markInputModeGamepad(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        markInputModeTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        // 系统手势返回兜底：不经由 requestExit 的退出（预测性返回由系统 finish）在此统一保存并回传。
        // flushAndReport 幂等（flushed 守卫），requestExit 已 flush 过的场景不会重复写盘。
        if (isFinishing) {
            val changed = runCatching { flushOnExit?.invoke() }.getOrNull() ?: false
            if (changed) setResult(RESULT_OK)
        }
    }

    override fun onDestroy() {
        subpage.stop()
        super.onDestroy()
    }
}

@Composable
fun SteamArtCustomScreen(
    cutoutAdapt: Boolean = false,
    themedNoArtFallback: Boolean = false,
    art: GameArt? = null,
    pkg: String,
    label: String,
    inheritedAccent: androidx.compose.ui.graphics.Color? = null,
    /** 自定义主题页专属滚动状态（右摇杆滚动底座）。 */
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
    val baseAccent = remember(art, inheritedAccent) {
        inheritedAccent ?: art?.accentColor ?: primary
    }

    // 6 张自定义图片（恢复 Demo 功能）
    var images by remember { mutableStateOf(List<Uri?>(6) { null }) }

    // 预设主色板（保留取主色功能）
    val presets = listOf(
        Color(0xFF7C5CFF), // Reverie 紫
        Color(0xFF1B9CFC), // Steam 蓝
        Color(0xFF0070D1), // PS 蓝
        Color(0xFF107C10), // Xbox 绿
        Color(0xFFE60012), // Switch 红
        Color(0xFFFF4554), // Switch 霓虹红
        Color(0xFFE50914), // Netflix 红
        Color(0xFF9146FF), // Twitch 紫
    )
    var selectedHex by remember(art) {
        mutableStateOf(art?.accentColor?.let { "#%08X".format(it.toArgb()) } ?: "")
    }

    // 自定义主题 Logo 锚定角（9 宫格，固定尺寸），默认 BottomLeft 与既有渲染一致
    var logoPin by remember { mutableStateOf("BottomLeft") }
    // 自定义主题 Logo 尺寸：无极连续值（0.5x ~ 1.5x），默认 1.0；加载时按真实尺寸推荐初始化
    var logoScale by remember { mutableStateOf(1f) }
    // 自定义主题「Logo 贴边」：每游戏独立，默认关（原行为：保留位图透明边距）
    var logoCrop by remember { mutableStateOf(false) }
    // 自定义主题 Logo 真实矩形百分比（来自 SteamDB 导入的 width_pct/height_pct）：
    // 保留并回写，避免自定义页把导入的真实尺寸抹成 0（hasSize=false → 统一 50%×50% 失真）。
    var logoWidthPct by remember { mutableStateOf(0f) }
    var logoHeightPct by remember { mutableStateOf(0f) }

    // 会话级改动标记（dirty）：仅在用户显式操作（选图 / 选色 / 改 logo 位置·尺寸·贴边）时置位。
    // 取代旧「当前值 vs 初始快照」比对——快照法会把「进入时回显已落盘图片 / 已存主色」误判为改动，
    // 导致什么都没改退出也回传 RESULT_OK、Hub 弹出虚假的「已应用自定义主题」提示。
    val dirty = remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // 手机竖屏判定（统一委托 DeviceProfile）：仅手机竖屏才根据 Logo 真实尺寸智能推荐初始 scale，
    // 横屏 / 平板竖屏恒推荐 1.0。
    val config = LocalConfiguration.current
    val prof = config.deviceProfile
    val isPhonePortrait = prof.isPortrait && prof.widthDp < DeviceProfile.PHONE_MAX_DP

    // 进入时回显：已落盘的自定义图（Uri.fromFile 供预览）+ 已保存的 logo 位置，
    // 让用户看到「当前已应用」的状态，而非每次进入都是空白。
    LaunchedEffect(pkg) {
        val d = File(ctx.filesDir, "steamart/$pkg")
        AssetKind.FOR_GAME.forEachIndexed { i: Int, kind: AssetKind ->
            // 防御：UI 仅渲染 6 槽位（2×3），FOR_GAME 实际可能多于 6 种；
            // 越界则安全跳过，杜绝 set(index) 触发 IndexOutOfBoundsException 导致进程崩溃重启。
            if (i >= images.size) return@forEachIndexed
            val f = File(d, customFileName(kind))
            if (f.exists()) images = images.toMutableList().also { it[i] = Uri.fromFile(f) }
        }
        runCatching {
            val o = JSONObject(File(d, "meta.json").readText())
            o.optString("logoPosition").takeIf { it.isNotBlank() }?.let { LogoPosition.fromJson(it) }
    }.getOrNull()?.let {
        logoPin = it.pinnedPosition
        logoCrop = it.logoCrop
        logoWidthPct = it.widthPct
        logoHeightPct = it.heightPct

        // logoScale 初始化：仅在「首次从 SteamDB 导入、尚无用户存档值」时用推荐值；
        // 用户手动调过（logoScale != 1f）或无 SteamDB 尺寸数据（widthPct<=0，纯自定义主题）时，直接用存档值，不再被覆盖。
        logoScale = if (it.logoScale != 1f || it.widthPct <= 0f) {
            it.logoScale
        } else {
            recommendLogoScale(it.widthPct, isPhonePortrait)
        }

    }
    }

    // 无手动主色时，从已选图片自动取主色（图片变化即重算）
    var autoAccent by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(images) {
        val srcs = images.filterNotNull().map { it: Uri -> ArtSource.File(it) }
        autoAccent = if (srcs.isEmpty()) null else extractAccent(ctx, srcs, baseAccent)  // srcs: List<ArtSource>
    }
    // 主色：手动 > 自动（图片）> 游戏页已有主色兜底
    val accent = runCatching {
        Color(android.graphics.Color.parseColor(selectedHex.ifBlank { "#%08X".format((autoAccent ?: baseAccent).toArgb()) }))
    }.getOrDefault(baseAccent)

    // 用可变状态持有最新值，供 flushAndReport 退出即应用时读取，避免闭包捕获首帧旧值
    val holdAccent = remember { mutableStateOf(accent) }
    val holdPin = remember { mutableStateOf(logoPin) }
    val holdScale = remember { mutableStateOf(logoScale) }
    val holdCrop = remember { mutableStateOf(logoCrop) }
    val holdWidthPct = remember { mutableStateOf(logoWidthPct) }
    val holdHeightPct = remember { mutableStateOf(logoHeightPct) }
    LaunchedEffect(accent) { holdAccent.value = accent }
    LaunchedEffect(logoPin) { holdPin.value = logoPin }
    LaunchedEffect(logoScale) { holdScale.value = logoScale }
    LaunchedEffect(logoCrop) { holdCrop.value = logoCrop }
    LaunchedEffect(logoWidthPct) { holdWidthPct.value = logoWidthPct }
    LaunchedEffect(logoHeightPct) { holdHeightPct.value = logoHeightPct }

    // 页内即时提示（项目标准 ReverieToast 浮层，替代系统 Toast）：目前用于「图片保存失败」错误反馈；
    // 「已应用自定义主题」成功提示仍由美术资源二级页（Hub）统一弹出，本页不重复。
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableStateOf(0) }
    LaunchedEffect(toastKey) { if (toastMsg != null) { delay(2500); toastMsg = null } }

    val act = ctx as ComponentActivity
    /**
     * 统一退出落盘（幂等）：有改动（dirty）才写 meta + 广播 ArtChangeBus，返回「本次会话是否有改动」。
     * flushed 守卫保证 requestExit 与 Activity.onStop（系统手势返回兜底）先后触发时只落盘一次。
     */
    val flushed = remember { mutableStateOf<Boolean?>(null) }
    fun flushAndReport(): Boolean {
        flushed.value?.let { return it }
        val changed = dirty.value
        if (changed) {
            runCatching {
                writeCustomMeta(
                    ctx, pkg, holdAccent.value, holdPin.value,
                    holdScale.value, holdCrop.value,
                    holdWidthPct.value, holdHeightPct.value
                )
            }
            // 美术已落地：广播变更，令所有游戏页及子页立即重算主题（含背后的游戏页一级页）
            ArtChangeBus.notify(pkg)
        }
        flushed.value = changed
        return changed
    }
    /** 统一退出：flush 后仅在真有改动时回传 RESULT_OK（Hub 据此弹「已应用自定义主题」），随后 finish。 */
    fun requestExit() {
        if (flushAndReport()) act.setResult(Activity.RESULT_OK)
        act.finish()
    }
    // 系统手势返回兜底：把 flush 注册给宿主 Activity，在 onStop(isFinishing) 统一补落盘 + RESULT_OK。
    // 修复：此前手势返回完全绕过保存链路（仅 onDispose 静默写 meta，不广播、不回传 RESULT_OK），
    // 导致 Hub 不弹「已应用自定义主题」、背后页面不刷新。
    val hostAct = act as? SteamArtCustomActivity
    DisposableEffect(Unit) {
        hostAct?.flushOnExit = { flushAndReport() }
        onDispose { hostAct?.flushOnExit = null }
    }

    // ── 手柄焦点状态机（自定义主题页全面适配：焦点覆盖 / 空间导航 / 高对比焦点框 / A 确认）──
    // 槽位：0=返回, 1..6=图片槽(2列×3行), 7..14=主色预设(8), 15=自动取色, 16..24=Logo位置(3×3), 25=Logo尺寸滑块, 26=Logo贴边开关。
    val totalSlots = 27
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
    val LOGO_POSITIONS = listOf(
        "TopLeft", "TopCenter", "TopRight",
        "CenterLeft", "CenterCenter", "CenterRight",
        "BottomLeft", "BottomCenter", "BottomRight"
    )
    val presetHexes = presets.map { "#%08X".format(it.toArgb()) }
    val pendingPickIdx = remember { mutableStateOf(-1) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked ->
        picked?.let { uri ->
            val idx = pendingPickIdx.value
            if (idx in 0..5) {
                val prev = images[idx]
                images = images.toMutableList().also { it[idx] = uri }
                dirty.value = true
                scope.launch(Dispatchers.IO) {
                    val ok = copyCustomImage(ctx, pkg, idx, uri)
                    if (!ok) withContext(Dispatchers.Main) {
                        // 落盘失败：回滚该槽位预览并给出页内提示（此前仅 Log.w 静默，用户无感知）
                        images = images.toMutableList().also { it[idx] = prev }
                        toastMsg = "图片保存失败，请重试"
                        toastKey++
                    }
                }
            }
        }
    }
    fun pickImage(idx: Int) { pendingPickIdx.value = idx; imageLauncher.launch("image/*") }
    fun activate(slot: Int) {
        when (slot) {
            0 -> requestExit()
            in 1..6 -> pickImage(slot - 1)
            in 7..14 -> { selectedHex = presetHexes[slot - 7]; dirty.value = true }
            15 -> { selectedHex = ""; dirty.value = true }  // 恢复自动取色（清空手动主色）
            in 16..24 -> { logoPin = LOGO_POSITIONS[slot - 16]; dirty.value = true }
            25 -> { /* 滑块：左右已调档，A 无额外动作 */ }
            26 -> { logoCrop = !logoCrop; dirty.value = true }
        }
    }
    fun moveFocus(dir: Direction) {
        val fi = focusIndex.value
        focusIndex.value = when {
            fi in 16..24 -> when (dir) {
                Direction.UP -> 16 + (((fi - 16) / 3) - 1).coerceAtLeast(0) * 3 + (fi - 16) % 3
                Direction.DOWN -> 16 + (((fi - 16) / 3) + 1).coerceAtMost(2) * 3 + (fi - 16) % 3
                Direction.LEFT -> 16 + ((fi - 16) / 3) * 3 + (((fi - 16) % 3) - 1).coerceAtLeast(0)
                Direction.RIGHT -> 16 + ((fi - 16) / 3) * 3 + (((fi - 16) % 3) + 1).coerceAtMost(2)
            }
            dir == Direction.UP -> (fi - 1).coerceAtLeast(0)
            dir == Direction.DOWN -> (fi + 1).coerceAtMost(totalSlots - 1)
            dir == Direction.LEFT || dir == Direction.RIGHT -> when {
                fi in 1..6 -> if ((fi - 1) % 2 == 0) fi + 1 else fi - 1
                fi in 7..15 -> (fi + if (dir == Direction.RIGHT) 1 else -1).coerceIn(7, 15)
                fi == 25 -> {
                    logoScale = (logoScale + if (dir == Direction.RIGHT) 0.02f else -0.02f).coerceIn(0.5f, 1.5f)
                    dirty.value = true
                    fi
                }
                else -> fi
            }
            else -> fi
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
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { requestExit(); true }
                    else -> false
                }
            }
    ) {
        // 有整页背景图 → 深色 + 压暗遮罩（泛白部件）；无任何背景图 → 走 theme 的浅色主题化兜底（白底，此时确实无美术）
        GameArtBackground(art, accent, themedNoArtFallback = themedNoArtFallback)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarSafeTop()
                .edgeSafePadding(cutoutAdapt)
                .padding(horizontal = Dimens.ContentHorizontal)
        ) {
            // 顶部：归一化标题栏（白色字，叠在游戏美术上），引用 KeyBadge.SubPageTitleBar 基线。
            // 标题栏固定，不随下方内容滚动（修复「自定义主题页标题栏跟随内容滚动」）。
            val wide = LocalConfiguration.current.screenWidthDp.dp >= SubPageSpec.WideThreshold
            SubPageTitleBar(
                title = "自定义主题",
                onBack = { requestExit() },
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

            // ── 6 张图片自定义主题 ─────────────────────────────
            Text(
                "选择 6 张图片拼贴自定义主题（${images.count { it != null }}/6）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Dimens.sm))

            // 预览：满 6 张显示拼贴，否则显示主色面板
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(Dimens.md))
                    // 修复：原误用 mutedOnSurface（onSurfaceVariant 文字色）作表面色，深色美术上几乎不可见；
                    // 改用语义卡片表面 cardSurface(Subtle)，保证深色背景上有可见浮起感。
                    .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle))
            ) {
                CustomPreview(images = images, accent = accent)
            }

            Spacer(Modifier.height(Dimens.sm))

            // 6 宫格选择
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                for (r in 0..2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        for (c in 0..1) {
                            val idx = r * 2 + c
                            CustomImageSlot(
                                index = idx,
                                kind = AssetKind.FOR_GAME[idx],
                                uri = images[idx],
                                accent = accent,
                                focused = focusIndex.value == 1 + idx,
                                onRequestPick = { pickImage(idx) },
                                modifier = Modifier.weight(1f).height(116.dp).then(track(1 + idx))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Dimens.lg))

            // ── 取主色（保留）───────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("主色", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(Dimens.sm))
                // 当前主色圆形指示
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(1.5.dp, SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis), CircleShape)
                )
                Spacer(Modifier.width(Dimens.sm))
                Text(
                    if (selectedHex.isBlank()) "（自动从图片取色）" else "已选择",
                    style = MaterialTheme.typography.bodySmall,
                    // 辅助文字统一提档 Emphasis（0.8）：深色美术背景上 0.7 以下对比度不足、发灰发虚
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis)
                )
            }
            Spacer(Modifier.height(Dimens.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                presets.forEachIndexed { i, color: Color ->
                    // 修复：Color.value 是 ULong（颜色位在高 32 位），toInt() 恒为 0；必须用 toArgb()
                    val hex = "#%08X".format(color.toArgb())
                    val selected = selectedHex.equals(hex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(40.dp)
                            .focusBorder(focusIndex.value == 7 + i, accent, CircleShape)
                            .clip(CircleShape)
                            .background(color)
                            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { selectedHex = hex; dirty.value = true }.then(track(7 + i))
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = contrastColor(color),
                                modifier = Modifier.align(Alignment.Center).size(20.dp)
                            )
                        }
                    }
                }
                // 自动取色（恢复默认主色）：清空手动主色，回落「图片自动取色 → 游戏页主色」链路。
                // writeCustomMeta 无需改动：selectedHex 为空时 accent 已回落 autoAccent/baseAccent。
                val autoSelected = selectedHex.isBlank()
                Box(
                    Modifier
                        .size(40.dp)
                        .focusBorder(focusIndex.value == 15, accent, CircleShape)
                        .clip(CircleShape)
                        .background(if (autoSelected) accent.copy(alpha = 0.25f) else Color.Transparent)
                        .border(1.5.dp, if (autoSelected) accent else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { selectedHex = ""; dirty.value = true }
                        .then(track(15)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoFixNormal,
                        contentDescription = "自动取色",
                        tint = if (autoSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(Dimens.lg))

            // ── Logo 位置（9 宫格，固定尺寸）────────────────────
            LogoPositionPicker(
                selected = logoPin,
                accent = accent,
                onPick = { logoPin = it; dirty.value = true },
                focusIndex = focusIndex.value,
                baseSlot = 16,
                cellTrack = track
            )

            Spacer(Modifier.height(Dimens.lg))

            // ── Logo 尺寸（无极连续拖动，0.5x ~ 1.5x；手柄左右键以 0.02 步进）────
            Text(
                "Logo 尺寸",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Dimens.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                Text(
                    "0.5x",
                    style = MaterialTheme.typography.bodySmall,
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis),
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
                val recommendedScale = recommendLogoScale(logoWidthPct, isPhonePortrait)
                BoxWithConstraints(Modifier.weight(1f)) {
                    Slider(
                        value = logoScale,
                        onValueChange = { logoScale = it.coerceIn(0.5f, 1.5f); dirty.value = true },
                        valueRange = 0.5f..1.5f,
                        // 去掉 steps → 无极连续拖动
                        modifier = Modifier.fillMaxWidth()
                            .focusBorder(focusIndex.value == 25, accent, MaterialTheme.shapes.small)
                            .then(track(25)),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = accent.copy(alpha = 0.2f)
                        )
                    )
                    // 推荐值标线：轨道上叠一根 2×12dp 半透明白竖线，位置随 recommendLogoScale 变化；
                    // 纯参考点、无额外交互，手柄拖近时自然覆盖。自定义主题(widthPct≤0)推荐=1.0→正中。
                    val tickFrac = ((recommendedScale - 0.5f) / 1f).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = maxWidth * tickFrac - 1.dp)
                            .width(2.dp)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                    )
                }
                Text(
                    "1.5x",
                    style = MaterialTheme.typography.bodySmall,
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis),
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
            }
            Text(
                "当前：${"%.0f".format(logoScale * 100)}%",
                style = MaterialTheme.typography.bodySmall,
                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis)
            )

            Spacer(Modifier.height(Dimens.lg))

            // ── Logo 贴边（每游戏独立开关：裁掉位图透明边距，让真实内容顶满盒子边缘）────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Logo 贴边",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Dimens.xs))
                    Text(
                        "裁掉 logo 四周透明边距，让真实内容顶满盒子（可见 logo 会变大）。每游戏独立设置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis)
                    )
                }
                Switch(
                    checked = logoCrop,
                    onCheckedChange = { logoCrop = it; dirty.value = true },
                    modifier = Modifier.focusBorder(focusIndex.value == 26, accent, MaterialTheme.shapes.small)
                        .then(track(26)),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accent,
                        checkedTrackColor = accent.copy(alpha = 0.5f),
                        uncheckedThumbColor = accent,
                        uncheckedTrackColor = accent.copy(alpha = 0.25f)
                    )
                )
            }

            // 应用说明：选图即自动保存，退出本页时自动应用主色与 logo 位置（见 flushAndReport），无需手动点击应用。
            Text(
                "选择图片即自动保存；退出本页时自动应用主色与 Logo 位置。",
                style = MaterialTheme.typography.bodySmall,
                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis)
            )
            }  // close inner scroll Column (内容滚动区)
        }

        // 页内错误提示浮层（项目标准 ReverieToast）：目前仅「图片保存失败，请重试」
        ReverieToast(message = toastMsg)
    }
}

/** 6 宫格中的单个图片槽位：点击选图，复用 Demo 版交互，并明确该美术资源的用途（图标 + 名称 + 说明）。 */
@Composable
fun CustomImageSlot(
    index: Int,
    kind: AssetKind,
    uri: Uri?,
    accent: Color,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    onRequestPick: () -> Unit
) {
    val bmp = rememberArtBitmap(uri?.let { ArtSource.File(it) })
    Column(
        modifier
            .focusBorder(focused, accent, RoundedCornerShape(Dimens.sm))
            .clip(RoundedCornerShape(Dimens.sm))
            // 修复：原误用 mutedOnSurface（onSurfaceVariant×0.6 文字色）作卡片底色，深色美术上
            // 六个槽位几乎与背景融合成「幽灵卡片」；改语义卡片表面 cardSurface(Subtle) 保证可见浮起感。
            .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle))
            .clickable { onRequestPick() }
            .padding(Dimens.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = kind.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Dimens.xxs))
                )
            } else {
                Icon(
                    when (kind) {
                        AssetKind.LIBRARY_HERO -> Icons.Filled.Image
                        AssetKind.HERO_CAPSULE -> Icons.Filled.Rectangle
                        AssetKind.LIBRARY_CAPSULE -> Icons.Filled.CropSquare
                        AssetKind.LIBRARY_HEADER -> Icons.Filled.ViewDay
                        AssetKind.LOGO -> Icons.Filled.Star
                        AssetKind.PAGE_BG_RAW -> Icons.Filled.Wallpaper
                        else -> Icons.Filled.Image
                    },
                    contentDescription = kind.label,
                    tint = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis),
                    modifier = Modifier.size(26.dp)
                )
            }
            // 序号角标
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            // 已选勾选
            if (uri != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = contrastColor(accent),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(Dimens.xxs))
        Text(
            kind.label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Text(
            kind.title,
            color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 自定义主题预览：2×3 网格逐格显示已选图（未选显示主色占位 + 序号），直观反映「部分已保存」状态。 */
@Composable
fun CustomPreview(images: List<Uri?>, accent: Color) {
    Column(Modifier.fillMaxSize()) {
        for (r in 0..2) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0..1) {
                    val idx = r * 2 + c
                    val uri = images.getOrNull(idx)
                    val bmp = rememberArtBitmap(uri?.let { ArtSource.File(it) })
                    Box(
                        Modifier.weight(1f).fillMaxSize().padding(2.dp)
                            .clip(RoundedCornerShape(Dimens.xxs))
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(accent.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${idx + 1}",
                                    color = contrastColor(accent),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 自定义主题 Logo 位置选择器：3×3 九宫格表示 Library Hero 上的锚定角，固定尺寸。 */
@Composable
fun LogoPositionPicker(
    selected: String,
    accent: Color,
    onPick: (String) -> Unit,
    focusIndex: Int = -1,
    baseSlot: Int = 0,
    cellTrack: ((Int) -> Modifier)? = null
) {
    val positions = listOf(
        "TopLeft", "TopCenter", "TopRight",
        "CenterLeft", "CenterCenter", "CenterRight",
        "BottomLeft", "BottomCenter", "BottomRight"
    )
    val cellAlign = { p: String ->
        when (p) {
            "TopLeft" -> Alignment.TopStart
            "TopCenter" -> Alignment.TopCenter
            "TopRight" -> Alignment.TopEnd
            "CenterLeft" -> Alignment.CenterStart
            "CenterCenter" -> Alignment.Center
            "CenterRight" -> Alignment.CenterEnd
            "BottomLeft" -> Alignment.BottomStart
            "BottomCenter" -> Alignment.BottomCenter
            else -> Alignment.BottomEnd
        }
    }
    Text("Logo 位置（9 宫格，固定尺寸）", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(Dimens.sm))
    Column(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(Dimens.sm))
            // 修复：原误用 mutedOnSurface（文字色）作容器底色，深色美术上不可见；改语义卡片表面。
            .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Subtle))
            .padding(Dimens.sm)
    ) {
        for (r in 0..2) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0..2) {
                    val p = positions[r * 3 + c]
                    val sel = p == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(3.dp)
                            .focusBorder(focusIndex >= 0 && focusIndex == baseSlot + (r * 3 + c), accent, RoundedCornerShape(Dimens.RadiusSm))
                            .clip(RoundedCornerShape(Dimens.RadiusSm))
                            .background(if (sel) accent.copy(alpha = 0.25f) else Color.Transparent)
                            .then(if (sel) Modifier.border(1.5.dp, accent, RoundedCornerShape(Dimens.RadiusSm)) else Modifier)
                            .clickable { onPick(p) }.then(cellTrack?.invoke(baseSlot + (r * 3 + c)) ?: Modifier),
                        contentAlignment = cellAlign(p)
                    ) {
                        // 修复：未选中点原用 onSurfaceVariant×0.5 实心圆，深色美术上 9 个点位难以辨认；
                        // 改「透明底 + outline 轮廓环」，保证全部点位可见；选中点保持 accent 实心。
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (sel) accent else Color.Transparent)
                                .border(1.5.dp, if (sel) accent else MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(Dimens.xs))
    // UI 文本全中文铁律：位置名称展示中文（内部存储仍用英文枚举串，不影响 meta 兼容）
    val posZh = mapOf(
        "TopLeft" to "左上", "TopCenter" to "上中", "TopRight" to "右上",
        "CenterLeft" to "左中", "CenterCenter" to "居中", "CenterRight" to "右中",
        "BottomLeft" to "左下", "BottomCenter" to "下中", "BottomRight" to "右下"
    )
    Text("当前：${posZh[selected] ?: selected}", style = MaterialTheme.typography.bodySmall, color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis))
}

/** 自定义主题落盘文件名：与 SteamDB 提取同名，保证「后写覆盖」天然高于自动提取与无美术资源。 */
fun customFileName(kind: AssetKind): String = when (kind) {
    AssetKind.LIBRARY_HERO -> "library_hero.jpg"      // 主视觉横幅
    AssetKind.HERO_CAPSULE -> "hero_capsule.jpg"      // 竖版海报
    AssetKind.LIBRARY_CAPSULE -> "library_capsule.jpg" // 方形封套
    AssetKind.LIBRARY_HEADER -> "library_header.jpg"  // 列表头图
    AssetKind.CAPSULE_SMALL -> "capsule_467x181.jpg" // 侧栏小图标（不参与自定义主题 6 槽，仅防误写主视觉）
    AssetKind.LOGO -> "library_logo.png"              // 标志
    AssetKind.PAGE_BG_RAW -> "page_bg_raw.jpg"        // 背景原图
    else -> "library_hero.jpg"
}

/**
 * 选图即时落盘：单张图写入对应固定文件名（覆盖同名 SteamDB 提取结果）。流式拷贝避免大图 OOM。
 * @return 是否落盘成功——失败时调用方回滚槽位预览并弹页内提示（此前返回 Unit 静默失败，用户无感知）。
 */
suspend fun copyCustomImage(
    ctx: android.content.Context,
    pkg: String,
    index: Int,
    uri: Uri
): Boolean {
    val kind = AssetKind.FOR_GAME.getOrNull(index) ?: return false
    val dir = File(ctx.filesDir, "steamart/$pkg").also { it.mkdirs() }
    val fileName = customFileName(kind)
    return runCatching {
        val ins = ctx.contentResolver.openInputStream(uri) ?: error("无法读取所选图片")
        ins.use { s -> File(dir, fileName).outputStream().use { outs -> s.copyTo(outs) } }
    }.onFailure { android.util.Log.w("SteamArtCustom", "图片落盘失败 $fileName: ${it.message}") }.isSuccess
}

/** 退出时落盘 meta（主色 + logo 位置 + 尺寸档位 + 贴边开关），保留既有 steamAppId；图片由选图时逐张落盘完成。 */
fun writeCustomMeta(
    ctx: android.content.Context,
    pkg: String,
    accent: Color,
    logoPinnedPosition: String,
    logoScale: Float = 1f,
    logoCrop: Boolean = false,
    widthPct: Float = 0f,
    heightPct: Float = 0f
) {
    val dir = File(ctx.filesDir, "steamart/$pkg").also { it.mkdirs() }
    val existing = runCatching { JSONObject(File(dir, "meta.json").readText()) }.getOrNull()
    val manifest = JSONObject().apply {
        existing?.optLong("steamAppId")?.takeIf { it >= 0L }?.let { put("steamAppId", it) }
        // 自定义主题：Logo 位置（9 宫格）+ SteamDB 导入的真实矩形百分比（width/height_pct）。
        // 携带真实尺寸后 hasSize=true，computeLogoBox 走精确尺寸分支，与 HeroBanner 表现一致；
        // 无导入尺寸时 widthPct/heightPct=0 → hasSize=false → 回落默认百分比 + logoScale。
        put("accentColor", "#%08X".format(accent.toArgb()))
        // 标记为「用户自定义」来源：游戏页据此前往自动取色（亮图黑字 / 暗图白字），与 SteamDB 美术（恒白字）区分。
        put("custom", true)
        put(
            "logoPosition",
            LogoPosition(
                pinnedPosition = logoPinnedPosition,
                widthPct = widthPct,
                heightPct = heightPct,
                fromCustom = true,
                logoScale = logoScale,
                logoCrop = logoCrop
            ).toJson().toString()
        )
    }
    File(dir, "meta.json").writeText(manifest.toString())
}

/** 取与背景对比度足够的文字颜色。 */
fun contrastColor(bg: Color): Color {
    val lum = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (lum > 0.6f) Color.Black else Color.White
}

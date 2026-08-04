package cn.mocabolka.run.ui.game.steamart.ui

import cn.mocabolka.run.ui.components.statusBarSafeTop

import android.content.Intent
import cn.mocabolka.run.ui.game.GameArtBackground
import cn.mocabolka.run.ui.game.ArtChangeBus
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import cn.mocabolka.run.ui.SettingsRepository
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.viewmodel.compose.viewModel

import cn.mocabolka.run.ui.game.GameArt
import cn.mocabolka.run.ui.game.GameArtResolver
import cn.mocabolka.run.gamepad.GamepadDetector
import cn.mocabolka.run.ui.game.extractAccent
import cn.mocabolka.run.ui.components.ReverieFilledButton
import cn.mocabolka.run.ui.components.ReverieIconButton
import cn.mocabolka.run.ui.components.SubPageSpec
import cn.mocabolka.run.ui.components.SubPageTitleBar
import cn.mocabolka.run.ui.components.subPageKeyHandler
import cn.mocabolka.run.ui.game.steamart.data.webview.CfWebViewClient
import cn.mocabolka.run.ui.game.steamart.data.webview.ExtractionBridge
import cn.mocabolka.run.ui.game.steamart.data.webview.SteamWebViewFactory
import cn.mocabolka.run.ui.game.steamart.domain.ExtractionTiming
import cn.mocabolka.run.ui.game.steamart.domain.model.ExtractionState
import cn.mocabolka.run.ui.game.steamart.domain.model.LogoPosition
import cn.mocabolka.run.ui.setupEdgeToEdgeImmersive
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.LightContent
import cn.mocabolka.run.ui.theme.resolvePageTheme
import cn.mocabolka.run.ui.DarkMode
import cn.mocabolka.run.ui.theme.edgeSafePadding
import cn.mocabolka.run.ui.theme.SurfaceCard
import cn.mocabolka.run.ui.theme.SurfaceTokens
import android.view.KeyEvent
import kotlinx.coroutines.delay

/**
 * L3 SteamDB 提取页（三级子页面，独立 Activity）。
 * 已知 appId 时直接加载 info 页跑探针；未知时先加载搜索页解析 appId。
 * 完成后资源按包名落地到 filesDir/steamart/<pkg>，自动返回并刷新游戏页。
 */
class SteamArtExtractActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdgeImmersive(hideStatusBar = false)

        // SEC-S1：包名净化——pkg 将拼入 filesDir 路径，非法包名（路径穿越等）直接拒绝进入
        val pkg = SteamArtContracts.sanitizePackage(intent.getStringExtra(SteamArtContracts.EXTRA_PACKAGE))
            ?: run { finish(); return }
        val label =
            intent.getStringExtra(SteamArtContracts.EXTRA_LABEL).takeIf { !it.isNullOrBlank() } ?: pkg
        val appId = intent.getLongExtra(SteamArtContracts.EXTRA_APP_ID, 0L).takeIf { it != 0L }
        val query = intent.getStringExtra(SteamArtContracts.EXTRA_QUERY)
        val logoPositionJson = intent.getStringExtra(SteamArtContracts.EXTRA_LOGO_POSITION)
        val accentHex = intent.getStringExtra(SteamArtContracts.EXTRA_ACCENT)
        val inheritedAccent =
            accentHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

        SteamArtGraph.ensure(application)

        val ctx = this
        val settings = SettingsRepository(this)
        val cutoutAdapt = settings.cutoutAdapt
        setContent {
            val ctx = LocalContext.current
            // remember 缓存 Flow，避免每次重组重新注册广播接收器
            val gamepadFlow = remember(ctx) { GamepadDetector.gamepadConnectedFlow(ctx) }
            // SteamDB WebView 探针页：永久深色（反白部件 + 白字状态栏），无视美术与设置
            val theme = resolvePageTheme(hasArtOrCustom = false, settings = settings, isSteamDb = true)
            LandscapeTheme(
                darkTheme = theme.darkTheme,
                useMonet = settings.useMonet,
                isAmoled = settings.darkMode == DarkMode.AMOLED,
                gamepadConnected = gamepadFlow.collectAsState(false).value,
                glassSurface = settings.glassSurfaceFlow.collectAsState().value
            ) {
                LightContent(lightContent = theme.lightContent) {
                SteamArtExtractScreen(
                    cutoutAdapt = cutoutAdapt,
                    pkg = pkg,
                    label = label,
                    appId = appId,
                    query = query,
                    logoPositionJson = logoPositionJson,
                    inheritedAccent = inheritedAccent,
                    onBack = { finish() },
                    onDone = { ArtChangeBus.notify(pkg); setResult(RESULT_OK); finish() }
                )
                }
            }
        }
    }
}

@Composable
private fun SteamArtExtractScreen(
    cutoutAdapt: Boolean = false,
    pkg: String,
    label: String,
    appId: Long?,
    query: String?,
    logoPositionJson: String? = null,
    inheritedAccent: androidx.compose.ui.graphics.Color? = null,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val ctx = LocalContext.current
    // 预设游戏的 logo 定位（JSON），提取后作为回退定位写入 meta.json
    val presetLogoPosition = remember(logoPositionJson) { logoPositionJson?.let { LogoPosition.fromJson(it) } }
    val primary = MaterialTheme.colorScheme.primary
    var art by remember(pkg, label) { mutableStateOf<GameArt?>(null) }
    LaunchedEffect(pkg, label) { art = GameArtResolver(ctx).load(pkg, label) }
    val sources = remember(art) {
        art?.let {
            listOfNotNull(it.background, it.hero, it.logo, it.capsule, it.header).map { s -> s.source }
        } ?: emptyList()
    }
    var accent by remember { mutableStateOf<Color>(inheritedAccent ?: primary) }
    LaunchedEffect(art, inheritedAccent) {
        accent = if (inheritedAccent != null) inheritedAccent
                 else art?.let { extractAccent(ctx, sources, it.accentColor ?: primary) } ?: primary
    }

    val vm: SteamArtViewModel = viewModel(factory = SteamArtGraph.viewModelFactory)
    val state by vm.state.collectAsState()
    val cfChallenge by vm.cfChallenge.collectAsState()
    var resolvedId by remember(query, appId) { mutableStateOf(appId) }
    // 防止提取完成回调被重复触发（state 在 delay 期间被其他事件重置时会重新进入）
    var doneFired by remember { mutableStateOf(false) }
    // 重试智能路由：info 提取模式下由下方 WebView 块赋值（按当前 URL 决定跳过 CF 或重载），
    // 搜索/其它模式下保持 null，回退到 vm.retry()。
    val onInfoRetry = remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(resolvedId) { resolvedId?.let { vm.start(it, pkg, presetLogoPosition) } }

    LaunchedEffect(state) {
        when {
            // 终态 Done 最高优先级：无论之前 Error 是否先触发过 doneFired=true，
            // 只要 final state=Done（资源已落盘），必须立即走 onDone 回到游戏页。
            // 修复「资源已给到游戏页、但 WebView 仍闪 Error 浮层再 finish」的体验割裂。
            state is ExtractionState.Done -> {
                if (!doneFired) {
                    doneFired = true
                    ExtractionTiming.mark("uiReceivedDone")
                    delay(300) // 让「美术资源已保存」浮层显示 300ms
                }
                ExtractionTiming.mark("onDoneFired")
                onDone()
            }
            // 兜底/备用通道已完成（哪怕部分成功）：自动退出 WebView，避免用户卡在探针界面。
            // 错误也自动退出（兜底本身已穷尽重试，不再需要用户干预）。
            state is ExtractionState.Error && !doneFired -> {
                doneFired = true
                ExtractionTiming.mark("uiReceivedDone")
                delay(2000)
                ExtractionTiming.mark("onDoneFired")
                onDone()
            }
        }
    }

    // 局部快照消除 !!：pendingQuery 非空即搜索模式
    val pendingQuery = if (resolvedId == null) query else null
    val searchMode = pendingQuery != null

    // ── 手柄焦点状态机（SteamDB 提取页：返回 + 错误重试；覆盖层不拦截 WebView 触摸）──
    val focusIndex = remember { mutableStateOf(0) }
    val onRetryAction = { onInfoRetry.value?.invoke() ?: vm.retry() }
    fun activate(slot: Int) {
        when (slot) {
            0 -> onBack()
            1 -> if (state is ExtractionState.Error) onRetryAction() else Unit
        }
    }
    fun moveFocus(up: Boolean) {
        focusIndex.value = if (up) 0 else 1
    }

    Box(
        Modifier
            .fillMaxSize()
            .subPageKeyHandler { nk ->
                when (nk.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { moveFocus(nk.keyCode == KeyEvent.KEYCODE_DPAD_UP); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { activate(focusIndex.value); true }
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { onBack(); true }
                    else -> false
                }
            }
    ) {
        GameArtBackground(art, accent)

        if (searchMode && pendingQuery != null) {
            SteamSearchWebView(
                query = pendingQuery,
                onResolved = { id -> resolvedId = id }
            )
        } else if (resolvedId != null) {
            val id = resolvedId ?: 0L
            androidx.compose.runtime.key(id) {
                val client = remember(id) { CfWebViewClient() }
                // wv 需在 bridge 之前声明：bridge 的 onLinks 闭包要引用 wv.stopLoading()，
                // 而 wv 的创建又依赖 bridge —— 用 lateinit 打破前向引用（声明在前、赋值在后）。
                lateinit var wv: WebView
                val bridge = remember(id) {
                    ExtractionBridge(
                        // 注意：此处【不】立即 stopPolling。首个 onLinks 回调时 adultGatePresent 仍为 false，
                        // 但页面可能随后触发内容提示（adult gate）→ revealAdultContent() 才注入 page_bg_raw /
                        // hero_capsule / logo 等门控资产链接。若在此过早停止轮询，这些门控链接永远扫不到，
                        // 最终只能靠商店 API 兜底补齐（表现为「探针未生效」）。轮询改由下方 LaunchedEffect(state)
                        // 在提取真正进入「下载 / 完成 / 错误」时统一停止。
                        onLinks = { urls ->
                            vm.onLinksExtracted(urls)
                        },
                        onChallenge = { vm.onChallenge() },
                        onLogoPosition = {
                            // WebView 抓到的 logo 定位：记录并以最高优先级回写 meta（覆盖 API 兜底）
                            vm.setWebViewLogoPosition(it)
                            vm.persistWebViewLogo(pkg, it)
                        },
                        onAppName = { /* 可后续显示游戏名 */ },
                        onAdultGate = { vm.onAdultGate(it) },
                        onStructuredAssets = { vm.onStructuredAssets(it) }
                    )
                }
                wv = remember(id) { SteamArtGraph.webViewFactory.create(ctx, bridge, client) }
                DisposableEffect(wv) {
                    onDispose {
                        // 提取结束 / 切 AppID：探针必须彻底停掉并清除，绝不留后台进程互相干扰。
                        // 每次提取都是独立过程（独立 Activity + 独立 WebView + 独立 ViewModel），
                        // dispose 时旧探针随旧子树一起销毁，不会与新提取的探针叠加。
                        client.stopPolling()
                        try { wv.stopLoading() } catch (_: Throwable) {}
                        try { wv.destroy() } catch (_: Throwable) {}
                        // 仅持久化 cookie，保留 Cloudflare 人机验证状态与 HTTP 缓存，确保下次热启动；
                        // 绝不 removeAllCookies 与 clearCache，也不清 WebStorage（曾双清导致每轮冷启动 → CF 重新挑战）。
                        try { CookieManager.getInstance().flush() } catch (_: Throwable) {}
                    }
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { wv },
                    modifier = Modifier.fillMaxSize()
                )
                LaunchedEffect(Unit) {
                    wv.loadUrl(SteamWebViewFactory.infoUrl(id))
                }

                // 轮询统一收口：提取真正进入「下载 / 完成 / 错误」态时才停止探针与 WebView 图片加载，
                // 确保「内容提示 → 解锁 → 门控资产注入」全过程轮询持续扫描，不再漏抓门控链接。
                // 进入 Downloading 即停 WebView 加载，保留「访问资源后下载其它变慢」的带宽让位。
                // LoadingPage / WaitingForHuman（含二次 CF 回卷）则保持/恢复轮询，持续扫描真实页面，
                // CF 过审后自动恢复提取。
                // 轮询统一收口：探针必须撑到**终态**才停——Done/Error 后任何回探都无意义。
                // Downloading 阶段必须保留探针：二次 Cloudflare challenge 经常在「下载已开始」期间
                // 才被触发（页面被 CF 二次覆盖 / /cdn-cgi/ 重定向到 challenge-platform），此时若停探针，
                // cfChallenge 永远捕获不到、永远不进 WaitingForHuman、横死 Downloading 0/8。
                //  - Downloading：仅停 WebView 图片加载让出带宽，探针持续扫描。
                //  - Done / Error：停探针 + 停 WebView，提取彻底收尾。
                //  - LoadingPage / WaitingForHuman：保持/重启扫描，覆盖 onPageFinished 还没触发的初始边界
                //    与 CF 回卷刚完成重启扫描两种情形。
                //  - Extracting：探针自 LoadingPage 起一直在跑，无需重启。
                LaunchedEffect(state) {
                    when {
                        state is ExtractionState.Done
                            || state is ExtractionState.Error -> {
                            client.stopPolling()
                            try { wv.stopLoading() } catch (_: Throwable) {}
                        }
                        state is ExtractionState.Downloading -> {
                            try { wv.stopLoading() } catch (_: Throwable) {}
                        }
                        state is ExtractionState.LoadingPage
                            || state is ExtractionState.WaitingForHuman -> {
                            client.resumePolling(wv)
                        }
                    }
                }

                // 二次 Cloudflare 挑战横幅（跨提取阶段持久显示）：
                //  - 自动路径：探针持续扫描，用户过 CF 后真实页注入即自动恢复提取；
                //  - 手动路径：点按钮立即重跑探针（不重载页面，避免重新触发 CF）。
                if (cfChallenge && state !is ExtractionState.Done && state !is ExtractionState.Error) {
                    Spacer(Modifier.height(Dimens.sm))
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(Dimens.md).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                        ) {
                            Text(
                                "检测到 Cloudflare 验证页，探针持续扫描中。通过验证后将自动提取；若已通过可手动启动。",
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            ReverieFilledButton(
                                text = "我已通过，立即提取",
                                icon = Icons.Filled.Refresh,
                                onClick = {
                                    vm.reprobeNow()
                                    client.reprobe(wv)
                                },
                                accent = accent
                            )
                        }
                    }
                }

                // 重试智能路由：若当前已在 info 页（Cloudflare 已通过），跳过 40s 验证等待，
                // 立即重跑探针并直接尝试下载；否则回退到完整「重载 + 40s CF 超时」流程。
                onInfoRetry.value = {
                    val url = wv.url ?: ""
                    if (isOnInfoPage(url, id)) {
                        client.reprobe(wv)
                        vm.retryOnInfo()
                    } else {
                        vm.retry()
                        try { wv.reload() } catch (_: Throwable) {}
                    }
                }
            }
        }

        // 顶部渐变遮罩（纯装饰背景）：从上到下 主题背景色→透明，全幅铺满。
        // 仅作背景、非功能部件：故意【不】响应挖孔屏/瀑布屏安全距离（不加 edgeSafePadding），
        // 也【不】加 clickable/pointerInput —— 触摸会穿透到下方 WebView，绝不影响 Cloudflare 验证点击。
        // 作用：压出顶部对比区，让叠在游戏美术上的标题/返回键（跟随主题的 onSurface 色）更清晰。仅本 WebView 提取界面使用。
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ⚠️ 关键：覆盖层【绝不能】拦截 WebView 的触摸，否则 Cloudflare 的
        // "验证您是真人"按钮点不到、永远过不去人机验证。
        // 因此这里故意不使用 verticalScroll（可滚动容器会吞掉 WebView 的点击），
        // 状态卡片 SurfaceCard 本身不带 clickable，空区域点击会穿透到下方 WebView；
        // 只有「返回」按钮是真正可点击的。这与 demo（仅顶部一个小 AssistChip）一致。
        // 顶部：归一化标题栏（白色字，叠在游戏美术上），引用 KeyBadge.SubPageTitleBar 基线，无渐变遮罩。
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // 挖孔屏适配：仅当设置页「挖孔屏适配」全局开关打开时生效（与主页 EdgeToEdge 一致）。
                    // 仅内容避让挖孔区，遮罩背景仍全幅铺满，不响应瀑布屏安全距离。
                    .edgeSafePadding(cutoutAdapt)
                    .statusBarSafeTop()
                    .navigationBarsPadding()
                    .padding(horizontal = Dimens.ContentHorizontal)
                    .padding(bottom = Dimens.lg)
            ) {
                // 顶部：归一化标题栏（白色字，叠在游戏美术上），引用 KeyBadge.SubPageTitleBar 基线
                val wide = LocalConfiguration.current.screenWidthDp.dp >= SubPageSpec.WideThreshold
                SubPageTitleBar(
                    title = "SteamDB 提取",
                    onBack = onBack,
                    isBackFocused = focusIndex.value == 0,
                    onBackFocus = { focusIndex.value = 0 },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    titleStyle = if (wide) MaterialTheme.typography.titleLarge
                                 else MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(Dimens.sm))
                SteamArtStatusCard(
                    state = state,
                    accent = accent,
                    onRetry = onRetryAction,
                    retryFocused = focusIndex.value == 1
                )
            }
        }
    }
}

@Composable
private fun SteamArtStatusCard(
    state: ExtractionState,
    accent: Color,
    onRetry: () -> Unit,
    retryFocused: Boolean = false
) {
    when (state) {
        is ExtractionState.Idle -> {}
        is ExtractionState.LoadingPage -> StatusLine("正在加载 SteamDB 页面…", accent)
        is ExtractionState.WaitingForHuman -> StatusLine(
            "正在完成 Cloudflare 验证，请稍候…",
            accent
        )
        is ExtractionState.Extracting -> StatusLine("正在匹配美术资源（${state.foundUrls} 项）…", accent)
        is ExtractionState.Downloading -> {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.md).fillMaxWidth()) {
                    Text("正在下载资源 ${state.done}/${state.total}", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Dimens.sm))
                    LinearProgressIndicator(
                        progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent,
                        trackColor = SurfaceTokens.mutedOnSurface()
                    )
                }
            }
        }
        is ExtractionState.Done -> {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(Dimens.md).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent)
                    Text("美术资源已保存，即将返回游戏页。", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        is ExtractionState.Error -> {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.md).fillMaxWidth()) {
                    Text("提取未完成：${state.reason}", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Dimens.sm))
                    ReverieFilledButton(
                        text = "重试",
                        icon = Icons.Filled.Refresh,
                        onClick = onRetry,
                        accent = accent,
                        focused = retryFocused
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, accent: Color) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(Dimens.md).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(10.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(Dimens.sm))
            Text(text, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * 判断 WebView 当前是否已在目标 app 的 info 页（Cloudflare 已通过）。
 * 用于「重试」智能路由：在 info 页则跳过 40s CF 等待直接重扫下载，否则回退完整重载流程。
 */
private fun isOnInfoPage(url: String?, id: Long): Boolean {
    if (url.isNullOrBlank() || id == 0L) return false
    val u = url.lowercase()
    return u.contains("steamdb.info") && u.contains("/app/$id/info")
}

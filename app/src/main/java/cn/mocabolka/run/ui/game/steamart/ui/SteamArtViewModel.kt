package cn.mocabolka.run.ui.game.steamart.ui

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.mocabolka.run.ui.game.steamart.domain.ExtractAssetsUseCase
import cn.mocabolka.run.ui.game.steamart.domain.model.ExtractionState
import cn.mocabolka.run.ui.game.steamart.domain.model.LogoPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import cn.mocabolka.run.ui.game.steamart.domain.ExtractionTiming

/**
 * SteamDB 美术资源提取视图模型。
 *
 * 生命周期：
 *  - [start]：进入页面、设置目标（appId + 包名），置 [ExtractionState.LoadingPage]，并启动 40s Cloudflare
 *    验证超时计时（对齐 Demo 的 `armMaxWait`）。若超时后仍卡在 LoadingPage / WaitingForHuman
 *    （CF 验证未通过或链接未到），自动置为 [ExtractionState.Error]，避免用户面对永无响应的页面。
 *  - [onLinksExtracted]：WebView 探针回传。空列表 → WaitingForHuman；非空 → 取消超时、进入下载。
 *  - [startWithUrls]：运行 [ExtractAssetsUseCase]，按包名落地到 `filesDir/steamart/<pkg>`。
 *  - [retry]：重新加载页面（会重置超时计时）。
 */
class SteamArtViewModel(
    private val useCase: ExtractAssetsUseCase,
    private val app: Application
) : ViewModel() {
    // 使用框架托管的 viewModelScope（自动随 onCleared 取消），替代手动裸建 CoroutineScope
    private val scope get() = viewModelScope
    private val _state = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val state: StateFlow<ExtractionState> = _state.asStateFlow()

    /**
     * 当前 WebView 是否正显示 Cloudflare 挑战页（跨提取阶段持久标记）。
     * 探针每轮若扫到 CF 标记即调 [onChallenge] 置 true；真实页注入（onLinks 非空）即清 false。
     * 驱动 Activity 的「二次 CF 横幅」与「我已通过，立即提取」手动按钮——既支持自动回卷到等待环节，
     * 也支持用户手动放行后手动启动探针（满足「自动重回对应环节 / 或给按钮」两种诉求）。
     */
    private val _cfChallenge = MutableStateFlow(false)
    val cfChallenge: StateFlow<Boolean> = _cfChallenge.asStateFlow()
    // 注意：app 用于网络质量探测（弱网时延长探针兜底），不可标为 UNUSED

    private var appId: Long = 0
    private var packageName: String = ""
    private var presetLogoPosition: LogoPosition? = null
    /** WebView 探针抓到的 logo 定位（实时值，优先级高于商店 API 兜底）。 */
    var webViewLogoPosition: LogoPosition? = null
        private set

    /** 结构化源解析（Plan B 探针）报告 JSON（每项含 asset/url/picked/options），供 debug 页强信号判定。 */
    var structuredAssetReport: String? = null
        private set

    /**
     * 探针内嵌的结构化源解析（直接解析 SteamDB info 页 table.web-assets 结构）回传的报告。
     * 仅打 Log + 存字段，不改变任何提取状态机；属于「更强壮探针」的可观测信号。
     */
    fun onStructuredAssets(json: String) {
        structuredAssetReport = json
        val n = "\"asset\"".toRegex().findAll(json).count()
        Log.i(TAG, "[Probe] structured parser found $n 项资产（Plan B 源解析生效）")
    }

    /** Cloudflare 验证超时任务（40s 总兜底）：start() 启动，进入 Extracting / Done / Error 时取消。 */
    private var cfTimeoutJob: Job? = null

    /** 探针兜底任务（12s 快速兜底）：探针 12s 内未回传链接即切 CF-free 直连，消除「卡 40s」体感卡顿。 */
    private var probeFallbackJob: Job? = null

    /** 提取任务单飞守卫：探针轮询可能多次回传链接，防止并发多流竞争写 _state。 */
    private var extractJob: Job? = null

    /** 跨线程累积的链接集合（JS 桥回调可能在非主线程运行）。 */
    private val linkLock = Any()
    private val accumulatedUrls = Collections.synchronizedSet(mutableSetOf<String>())
    /** 链接稳定计时器（每次新批次重置）。 */
    private var debounceJob: Job? = null
    /** 首链接起的硬上限计时器：即便链接持续陆续到达，提取也最迟在首链接后 LINK_SETTLE_CAP_MS 触发。 */
    private var settleCapJob: Job? = null
    /** 防抖触发守卫：避免 cap 与 debounce 双触发、或重试后旧计时器误触发。原子布尔消除协程间竞态窗口。 */
    private val extractFired = AtomicBoolean(false)
    /** 门控解锁后的额外等待计时器：解锁后延迟 GATE_CLEAR_DEBOUNCE_MS 再定稿，让受限链接注入完。 */
    private var gateClearJob: Job? = null
    /** 内容提示按钮标志：true=页面仍存在解锁按钮，阻塞提取直到解锁。 */
    private var adultGatePresent = false

    fun start(appId: Long, packageName: String, presetLogoPosition: LogoPosition? = null) {
        // 重置计时单例：否则 t0 永为 0、elapsed() 恒 0，所有探针阶段耗时/链路日志全为 "-"
        ExtractionTiming.reset()
        ExtractionTiming.mark("start")
        _cfChallenge.value = false
        this.appId = appId
        this.packageName = packageName
        this.presetLogoPosition = presetLogoPosition
        this.webViewLogoPosition = null // 重新进入/重试时清空，避免复用上一次捕获
        accumulatedUrls.clear()
        debounceJob?.cancel()
        settleCapJob?.cancel(); settleCapJob = null
        gateClearJob?.cancel(); gateClearJob = null
        extractFired.set(false)
        adultGatePresent = false
        _state.value = ExtractionState.LoadingPage
        armCloudflareTimeout()
    }

    /** WebView 探针回调：记录抓到的 logo 定位，供提取落盘时优先采用。 */
    fun setWebViewLogoPosition(pos: LogoPosition?) {
        webViewLogoPosition = pos
    }

    /** WebView 捕获晚于提取落盘时，直接以 WebView 值回写 meta（优先级最高）。 */
    fun persistWebViewLogo(packageName: String, pos: LogoPosition) {
        useCase.writeWebViewLogoPosition(packageName, pos)
    }

    /**
     * 对齐 Demo 的 `armMaxWait(40s)`：若进入 Extracting 之前仍卡在 LoadingPage / WaitingForHuman，
     * 说明 CF 验证未通过或探针一直没拿到链接——置为 Error，提示用户重试或检查网络，不再无限等待。
     * 内容提示存在时会被反复续期，给资源解锁充足时间。
     */
    private fun armCloudflareTimeout() {
        scheduleCfFallback()
        scheduleProbeFallback()
    }

    /** 重设最大等待定时器（内容提示存在时续期用）。 */
    fun armMaxWait() = armCloudflareTimeout()

    /**
     * 40s Cloudflare 总兜底：真实挑战 / 用户手动放行场景下，若仍卡在 LoadingPage / WaitingForHuman，
     * 切 CF-free 商店/CDN 直连兜底（兜底自身失败会发射明确 Error）。
     */
    private fun scheduleCfFallback() {
        cfTimeoutJob?.cancel()
        cfTimeoutJob = scope.launch {
            delay(CF_TIMEOUT_MS)
            fireFallbackIfStuck("Cloudflare 验证超时，正在切 Steam 官方 CDN 备用通道……")
        }
    }

    /**
     * 12s 探针兜底：探针在 12s 内未回传任何美术链接（CF 静默拦截 WebView、或 info 页 DOM 不含
     * steamstatic 链接）时，立即切 CF-free 直连，避免用户面对长达 40s 的「正在加载」卡顿。
     * 一旦 onLinks 拿到链接或 onChallenge 进入手动放行，本计时会被取消。
     */
    private fun scheduleProbeFallback() {
        probeFallbackJob?.cancel()
        probeFallbackJob = scope.launch {
            delay(computeProbeFallbackMs())
            fireFallbackIfStuck("未检测到页面美术资源，已自动切换备用通道获取")
        }
    }

    /**
     * 探针兜底等待时长：弱网（移动/计量网络）时 Cloudflare 挑战 + info 页加载更慢，
     * 12s 往往不够 → 自动延长到 20s，减少「过早转保底」导致质量降级；
     * WiFi/以太网等稳定网络仍用 12s，不拖慢正常流程。
     */
    private suspend fun computeProbeFallbackMs(): Long =
        if (isLikelyWeakNetwork(app)) PROBE_FALLBACK_MS_WEAK else PROBE_FALLBACK_MS

    /**
     * 网络质量启发式：非 Wifi/以太网 或 计量网络 视为弱网。minSdk=36 直接用 NetworkCapabilities。
     * 必须在 Dispatchers.Main 上执行（getActiveNetwork 等依赖主线程上下文）；
     * 先校验 ACCESS_NETWORK_STATE 权限——缺失时返回 false（按稳定网络 12s 兜底），
     * 并对 ConnectivityManager 调用包 runCatching 防止个别设备无权限仍抛 SecurityException 导致崩溃。
     */
    private suspend fun isLikelyWeakNetwork(ctx: Application): Boolean = withContext(Dispatchers.Main) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_NETWORK_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return@withContext false
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@withContext false
        val net = runCatching { cm.activeNetwork }.getOrNull() ?: return@withContext false
        val caps = runCatching { cm.getNetworkCapabilities(net) }.getOrNull() ?: return@withContext false
        val onStableTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        !onStableTransport || !unmetered
    }

    /** 若仍卡在加载/等待真人状态，触发 CF-free 兜底（由先到的计时器调用，单飞由 startStoreFallback 保证）。 */
    private fun fireFallbackIfStuck(msg: String) {
        val s = _state.value
        if (s is ExtractionState.LoadingPage || s is ExtractionState.WaitingForHuman) {
            startStoreFallback(msg)
        }
    }

    /**
     * CF-free 兜底：WebView 探针在超时窗口内没能拿到链接 / 用户没能手动过 CF 时，
     * 改用 appId 直连 Steam 官方 CDN + 商店 API 提取（不经 Cloudflare 挑战）。
     * 这是消除"WebView 被挡就永久卡死"的最终安全网。
     */
    private fun startStoreFallback(timeoutMsg: String) {
        if (extractJob?.isActive == true) return
        cfTimeoutJob?.cancel()
        probeFallbackJob?.cancel()
        debounceJob?.cancel()
        settleCapJob?.cancel(); settleCapJob = null
        extractFired.set(true) // 兜底已接管，禁止旧探针防抖再次触发
        gateClearJob?.cancel(); gateClearJob = null
        if (appId == 0L) {
            _state.value = ExtractionState.Error(timeoutMsg)
            return
        }
        _state.value = ExtractionState.Extracting(0)
        Log.i(TAG, "[Fallback] 启动 CF-free 商店/CDN 兜底：appId=$appId（trigger=$timeoutMsg）")
        extractJob = scope.launch {
            try {
                useCase.fallbackFromStore(
                    appId,
                    packageName,
                    presetLogoPosition,
                    webViewLogoPositionProvider = { webViewLogoPosition }
                ).collect { _state.value = it }
            } catch (e: Exception) {
                Log.w(TAG, "CF-free 兜底异常: ${e.message}")
                _state.value = ExtractionState.Error("备用提取异常：${e.message}")
            }
        }
    }

    /**
     * WebView 探针每次轮询都会上报当前所有链接。这里累积去重，
     * 仅在"链接稳定（一段时间无新增）"后触发提取，避免过早提取导致缺图（稳定性关键，取自 demo）。
     */
    fun onLinksExtracted(urls: List<String>) {
        val s = _state.value
        if (s !is ExtractionState.LoadingPage && s !is ExtractionState.WaitingForHuman) return
        if (urls.isEmpty()) {
            // 探针明确回传 0 链接：取消 40s CF 等待（非 CF 场景），交由 12s 探针兜底快速切备用通道，
            // 避免卡在 WaitingForHuman 直到 40s。
            cfTimeoutJob?.cancel()
            _state.value = ExtractionState.WaitingForHuman
            ExtractionTiming.markCfDetected()
            Log.i(TAG, "[Probe] onLinksExtracted: 0 链接（info 页无 steamstatic URL / CF 静默拦截）")
            return
        }
        var added = 0
        synchronized(linkLock) {
            urls.forEach { if (accumulatedUrls.add(it)) added++ }
        }
        // 探针已拿到真实链接 → 当前页不是 CF 挑战页，清标记（收起 CF 横幅 + 手动按钮）。
        _cfChallenge.value = false
        val total = synchronized(linkLock) { accumulatedUrls.size }
        Log.i(TAG, "[Probe] onLinksExtracted: 本次 ${urls.size} 条，新增 $added 条，累积 $total 条（state=${s::class.simpleName}）")
        if (added == 0) return
        ExtractionTiming.markFirstLink()
        _state.value = ExtractionState.Extracting(total)
        // 内容提示尚未解除：只累积链接、不触发提取，等 onAdultGate(false) 再定稿
        if (adultGatePresent) {
            Log.i(TAG, "[Probe] 内容提示阻断中：暂停防抖调度，等待解锁后定稿")
            return
        }
        scheduleDebouncedExtraction()
    }

    /**
     * 延迟触发提取：链接稳定（无新增）后开下。
     * 提速（2026-07-25）：每次新批次重置「稳定计时器」（EXTRACT_DEBOUNCE_MS），但在「首个链接」起算一个硬上限
     * LINK_SETTLE_CAP_MS —— 即便链接持续陆续到达（轮询多轮），提取也最迟在首链接后 LINK_SETTLE_CAP_MS 触发，
     * 消除「链接不断新增 → 防抖反复重置 → 等待被拉到 5s+」的体感卡顿。
     */
    private fun scheduleDebouncedExtraction() {
        ExtractionTiming.incDebounceResets()
        // 首链接起算硬上限（仅起一次，避免与稳定计时器叠加双触发）
        if (settleCapJob == null) {
            settleCapJob = scope.launch {
                delay(LINK_SETTLE_CAP_MS)
                fireDebouncedExtraction(byCap = true)
            }
        }
        // 稳定计时器：每次新批次重置，链接停止新增后短等即触发（快路径）
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(EXTRACT_DEBOUNCE_MS)
            fireDebouncedExtraction(byCap = false)
        }
    }

    /** 真正触发提取（cap 与 debounce 共用，单飞守卫避免重复启动）。compareAndSet 消除双触发竞态窗口。 */
    private fun fireDebouncedExtraction(byCap: Boolean = false) {
        if (!extractFired.compareAndSet(false, true)) return
        ExtractionTiming.markExtractFired(byCap)
        settleCapJob?.cancel(); settleCapJob = null
        debounceJob?.cancel()
        gateClearJob?.cancel(); gateClearJob = null
        val links = synchronized(linkLock) { accumulatedUrls.toList() }
        Log.i(TAG, "[Debounce] 链接稳定/达上限，共 ${links.size} 条，启动提取")
        startWithUrls(appId, packageName, links, presetLogoPosition)
    }

    /**
     * 内容提示按钮状态回调（来自探针 [ExtractionBridge.onAdultGate]）。
     * present=true：页面仍存在解锁按钮，延长等待并阻塞提取；
     * present=false：按钮已消失，延迟一小段让受限链接注入后触发提取。
     */
    fun onAdultGate(present: Boolean) {
        if (present) {
            adultGatePresent = true
            // 关键修复：首个 onLinks 回调时 adultGatePresent 仍为 false，已调度了防抖/硬上限计时器；
            // 此处必须取消这些已调度的计时器，否则 1.2s 后防抖会以「非门控的残缺链接集合」点火，
            // 漏掉 revealAdultContent() 之后才注入的 page_bg_raw / hero_capsule / logo 等门控资产
            // （最终只能靠商店 API 兜底补齐 → 表现为「探针未生效」）。门控解除后再统一重新调度。
            debounceJob?.cancel(); debounceJob = null
            settleCapJob?.cancel(); settleCapJob = null
            armMaxWait() // 续期，给内容解锁充足时间
        } else {
            val wasGated = adultGatePresent
            adultGatePresent = false
            if (wasGated) {
                // 对齐 Demo GATE_CLEAR_DEBOUNCE_MS：门控解锁后再等一段，让受限链接（解锁后才注入那批）
                // 全部注入完成再定稿，避免 1.2s 防抖过早触发、漏抓受限资产。
                scheduleExtractionAfterGate()
            }
        }
    }

    /**
     * 门控解锁后的定稿等待：延迟 [GATE_CLEAR_DEBOUNCE_MS] 后启动常规防抖定稿。
     * 不用「立即 scheduleDebouncedExtraction」是因为解锁后受限链接常需 >1.2s 才注入完，
     * 立即定稿会让首批量链接在 1.2s 后触发、漏掉后续受限链接（正是漏抓 root cause 之一）。
     */
    private fun scheduleExtractionAfterGate() {
        gateClearJob?.cancel()
        gateClearJob = scope.launch {
            delay(GATE_CLEAR_DEBOUNCE_MS)
            scheduleDebouncedExtraction()
        }
    }

    fun startWithUrls(appId: Long, packageName: String, urls: List<String>, presetLogoPosition: LogoPosition? = null) {
        if (extractJob?.isActive == true) return // 单飞：已有提取在跑则忽略后续回传
        this.appId = appId
        this.packageName = packageName
        cfTimeoutJob?.cancel()
        probeFallbackJob?.cancel()
        gateClearJob?.cancel(); gateClearJob = null
        _state.value = ExtractionState.Extracting(urls.size)
        Log.i(TAG, "[Extract] startWithUrls: 探针路径 appId=$appId urls=${urls.size}")
        extractJob = scope.launch {
            try {
                useCase(
                    appId,
                    packageName,
                    urls,
                    presetLogoPosition,
                    webViewLogoPositionProvider = { webViewLogoPosition }
                ).collect { _state.value = it }
            } catch (e: Exception) {
                Log.w(TAG, "提取异常: ${e.message}")
                _state.value = ExtractionState.Error("提取异常：${e.message}")
            }
        }
    }

    fun retry() {
        if (appId != 0L) start(appId, packageName)
    }

    /**
     * 「重试」优化（2026-07-24）：若 WebView 当前已在 info 页（Cloudflare 已通过），
     * 无需重新走 40s 验证等待——直接重置 logo 定位并把状态置回 LoadingPage（允许探针回传继续下载），
     * 由 Activity 侧立即重跑探针。超时改用较短的「重新扫描超时」兜底，避免误报 CF 超时。
     */
    fun retryOnInfo() {
        if (appId == 0L) return
        this.webViewLogoPosition = null
        _state.value = ExtractionState.LoadingPage
        // 重试重算：清空上一轮防抖/上限计时器与触发守卫，允许重新定稿
        debounceJob?.cancel()
        settleCapJob?.cancel(); settleCapJob = null
        gateClearJob?.cancel(); gateClearJob = null
        extractFired.set(false)
        // 已在 info 页（CF 已通过）：用 12s 探针兜底快速切备用通道，避免 20s/40s 等待误报。
        scheduleProbeFallback()
    }

    /**
     * 触发 Cloudflare 挑战：进入等待真人放行状态。
     * 加状态守卫：仅在 LoadingPage / WaitingForHuman 时生效——若兜底已在提取（Extracting/Downloading）
     * 或已 Done，探针的延迟 onChallenge 回调不得回退/覆盖已推进的状态，避免竞态回卷。
     * 同时取消 12s 探针兜底：真实挑战交由 40s 总兜底等待用户手动放行 / 自动回退。
     */
    /**
     * 触发 Cloudflare 挑战：跨提取阶段持久标记 [cfChallenge]=true（驱动 UI 横幅 + 手动探针按钮），
     * 并视当前阶段决定状态机走向：
     *  - 终态 Done/Error：Activity 即将退出，不回卷，避免打断退出流程。
     *  - WaitingForHuman：已在等待真人放行，不重复重置/重挂超时（否则每次轮询都把 40s 兜底刷新 → 永不触发）。
     *  - LoadingPage：初/次 CF 挑战（正常加载期），取消 12s 探针兜底 → 转 40s 总兜底等待用户放行 / 自动回退。
     *  - Extracting/Downloading：二次 CF 挑战——正在进行的提取针对的是已被 CF 替换掉的旧页面，无意义，
     *    清场并回卷到 WaitingForHuman（探针持续轮询），用户过 CF 或真实页注入后 onLinks 自动恢复提取，
     *    或用户点「我已通过，立即提取」手动触发。40s 总兜底仅挂一次，不被后续 onChallenge 重置。
     */
    fun onChallenge() {
        _cfChallenge.value = true
        val s = _state.value
        if (s is ExtractionState.Done || s is ExtractionState.Error) return
        if (s is ExtractionState.WaitingForHuman) return
        probeFallbackJob?.cancel()
        if (s is ExtractionState.LoadingPage) {
            _state.value = ExtractionState.WaitingForHuman
        } else {
            extractJob?.cancel()
            extractFired.set(false)
            accumulatedUrls.clear()
            _state.value = ExtractionState.WaitingForHuman
            scheduleCfFallback()
        }
    }

    /**
     * 「我已通过 Cloudflare 验证，立即提取」按钮：用户在 UI 上手动确认 CF 已通过后调用。
     * 不重载页面（重载会重新触发 CF），仅重置触发守卫 + 取消 40s 总兜底，并让 Activity 立即重跑探针；
     * 若真实页已加载，探针拿到链接即恢复提取；若 CF 仍未过，探针 0 链接 → 横幅继续等待。
     */
    fun reprobeNow() {
        if (appId == 0L) return
        _cfChallenge.value = false
        cfTimeoutJob?.cancel()
        extractFired.set(false)
        accumulatedUrls.clear()
        _state.value = ExtractionState.LoadingPage
        scheduleProbeFallback() // 已在 info 页：12s 短兜底快速切备用通道
    }

    override fun onCleared() {
        cfTimeoutJob?.cancel()
        probeFallbackJob?.cancel()
        settleCapJob?.cancel()
        gateClearJob?.cancel()
        extractJob?.cancel()
        // viewModelScope 由框架自动取消，无需手动 cancel
    }

    companion object {
        private const val TAG = "SteamArtViewModel"
        private const val CF_TIMEOUT_MS = 40_000L
        /** 探针兜底时长：稳定网络下 12s 内未回传链接即切 CF-free 直连，替代原 40s 卡顿。 */
        private const val PROBE_FALLBACK_MS = 12_000L
        /** 弱网（移动/计量网络）探针兜底：延长到 20s，给 CF 挑战 + info 页加载更充足时间，减少误转保底。 */
        private const val PROBE_FALLBACK_MS_WEAK = 20_000L
        /** 门控解锁后额外等待：让受限链接注入完再定稿（对齐 Demo 的 GATE_CLEAR_DEBOUNCE_MS）。 */
        private const val GATE_CLEAR_DEBOUNCE_MS = 3000L
        /** 链接稳定（无新增）后延迟触发提取。缩短以加快首图下载（2026-07-25 提速）。 */
        private const val EXTRACT_DEBOUNCE_MS = 1200L
        /** 首链接起算的硬上限：即便链接持续陆续到达，提取也最迟在首链接后该时长触发，避免等待被拉到 5s+。 */
        private const val LINK_SETTLE_CAP_MS = 3000L
    }
}

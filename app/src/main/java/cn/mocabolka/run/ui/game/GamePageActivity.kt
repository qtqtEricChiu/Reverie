package cn.mocabolka.run.ui.game

import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import cn.mocabolka.run.BuildConfig
import cn.mocabolka.run.ui.game.steamart.ui.SteamArtContracts
import cn.mocabolka.run.ui.game.steamart.ui.SteamArtHubActivity
import cn.mocabolka.run.ui.enablePredictiveBack
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import cn.mocabolka.run.ui.setupEdgeToEdgeImmersive
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.compat.OrientationManager
import cn.mocabolka.run.compat.OverlayPermissionHelper
import cn.mocabolka.run.gamepad.GamepadDetector
import cn.mocabolka.run.gamepad.SubPageGamepadHost
import cn.mocabolka.run.launcher.AppLauncher
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.launcher.AppRepository
import cn.mocabolka.run.launcher.FavoritesRepository
import cn.mocabolka.run.launcher.GameWhitelist
import cn.mocabolka.run.launcher.UsageStatsRepository
import cn.mocabolka.run.launcher.isGameLike
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.MotionMachines
import cn.mocabolka.run.ui.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * 游戏专属页面（参考 Steam 大屏幕「游戏库 → 游戏」界面），独立 Activity。
 *
 * 与 About / Licenses / CompatGuide 同构：独占全屏（隐藏状态栏）、LandscapeTheme 主题、
 * 入场/出场动画、预测返回手势、应用级方向跟随。主页通过 Intent（包名）启动本页，
 * 不再以 HomeScreen 下的覆盖层形式存在（符合「子页面 = 独立 Activity」的架构约定）。
 *
 * 入口仅主页：
 * - 手柄：应用列表-应用行按 A → 启动本页（由 HomeScreen 判定 shouldOpenGamePage）；
 * - 触控：直接点击应用列表该行 → 启动本页。
 *
 * 手柄/键盘交互在 [GamePageScreen] 内以 onKeyEvent 处理（与 SubPage 同构）：
 * ↑↓ 切换焦点（启动/收藏）、A/Enter 触发、B/Esc 返回、X 切换收藏。
 *
 * 美术资源经 [GameArtResolver] 抽象：当前读 APK assets 本地文件（已随 APK 打包的测试资源），
 * 后续可无缝接入用户自定义（[ArtSource.File]）与线上实时链接（[ArtSource.Remote]），本页无需改动。
 */
class GamePageActivity : ComponentActivity() {
    /** 个性化内容 Widget 宿主（类级字段，供 onStart / onStop 生命周期访问）。 */
    private lateinit var widgetHostManager: WidgetHostManager

    /** 右摇杆滚动引擎（与主页/统计归一化同一物理积分）。 */
    private var rightStickEngine: SubPageGamepadHost? = null

    /** SteamDB 美术资源重载版本号：提取 / 自定义主题完成后 +1，触发游戏页美术重读。 */
    var artVersion by mutableStateOf(0)

    /** 美术资源重载后背景层重组信号：artVersion 变化并重载缓存后 +1，
     *  驱动背景 Crossfade 重读 `gameDataCache` 最新快照（否则同包重读不触发背景刷新）。 */
    var artRefresh by mutableStateOf(0)

    /** 上次切游戏时间戳（uptime ms），用于 LT/RT 连按节流（审计 PERF-G2）。 */
    private var lastGameSwitchMs = 0L

    /** 美术资源获取页（二级 Hub）返回监听：RESULT_OK 时 artVersion++。 */
    private val steamArtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) artVersion++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        if (packageName.isNullOrBlank()) {
            finish()
            return
        }

        val settings = SettingsRepository(this)
        val favoritesRepo = FavoritesRepository(this)
        val usageRepo = UsageStatsRepository(this)
        val artResolver = GameArtResolver(this)

        // 当前展示的包名（切换时更新；首屏为启动传入的包名，可能未安装）。
        // 必须在 Widget 宿主回调前声明，避免前向引用。
        var currentPkg by mutableStateOf(packageName)

        // ── 个性化内容：Widget 宿主 ──
        widgetHostManager = WidgetHostManager(this)
        // 【P0-2 修复】提前 startListening：AppWidgetHost.createView 触发 Provider 更新广播，
        // 若宿主尚未监听则该广播丢失 → Widget 停留默认空态。故在 rebuildWidgetSlots（createView）之前就绪。
        widgetHostManager.startListening()
        // 按「游戏包 + 宿主包」隔离的绑定存储：确保无限暖暖的 TapTap 插件与鸣潮的 TapTap 插件
        // 各自独立、互不复用同一个 appWidgetId。
        val widgetBindingStore = WidgetBindingStore(this)
        // Widget HostView 缓存：键 = "游戏包|宿主包"（同 WidgetBindingStore 隔离范式）。
        // 切走时不销毁、仅停止渲染（detached 但仍向 provider 收更新保持最新）；
        // 切回命中缓存即复用同一对象，零重建、零转圈、秒显上次状态。
        val widgetHostViewCache = mutableStateMapOf<String, AppWidgetHostView?>()
        var widgetSlots by mutableStateOf(emptyList<WidgetHostSlot>())
        var widgetVersion by mutableStateOf(0)
        var availableProviders by mutableStateOf(emptyMap<String, List<WidgetProviderItem>>())
        // 进页 Widget 加载态：按游戏包名记录是否仍在「初始化 + 首次刷新」中，驱动面板转圈 Loading。
        val widgetLoading = mutableStateMapOf<String, Boolean>()
        // 初始游戏即"进入"页面，先置加载态，避免首帧空态/默认态闪现。
        widgetLoading[packageName] = true

        // ── 截图归类（替代原资源画廊内容）──
        // 持久化存储：文件夹树 Uri / 设备是否不支持 / 每包哈希后缀。
        val screenshotStore = GameScreenshots.Store(this)
        // 初始化状态机（权限页 → 初始化样例 → 呈现 / 屏蔽）。
        var screenshotState by mutableStateOf(GameScreenshots.SetupState.NEED_TREE)
        // 当前游戏已命中的截图/录屏列表。
        var screenshots by mutableStateOf(emptyList<GameScreenshots.Shot>())
        // 用户直接选取的样例文件 Uri（可能不在授权树内，扫描后单独并入结果）。
        var lastSampleUri by mutableStateOf<Uri?>(null)
        // 按「游戏包」缓存的数据快照（提前声明，便于 scanScreenshotsFor 等同步回写快照）。
        val gameDataCache = mutableStateMapOf<String, GameDataSnapshot>()

        // ── 右摇杆滚动（与主页/统计归一化同一引擎）──
        // rightStickListState 持有"当前激活 Tab"的内容列表状态（由 TabContent 上报），
        // rightStickFirstVisible 接收引擎回写的首个可视项索引，供 Compose 焦点跟随。
        val rightStickListState = mutableStateOf(LazyListState())
        val rightStickFirstVisible = mutableStateOf(-1)

        fun isPackageInstalled(pkg: String): Boolean = runCatching {
            packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)

        fun getAppLabel(pkg: String): String = runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(pkg.substringAfterLast('.'))

        fun refreshAvailableProviders(targets: List<String>) {
            availableProviders = widgetHostManager.getAllProviders()
                .filter { it.provider.packageName in targets }
                .groupBy { it.provider.packageName }
                .mapValues { (_, infos) ->
                    infos.map { info ->
                        WidgetProviderItem(
                            packageName = info.provider.packageName,
                            className = info.provider.className,
                            label = info.loadLabel(packageManager).toString(),
                            minWidth = info.minWidth,
                            minHeight = info.minHeight,
                            previewResId = info.previewImage
                        )
                    }
                }
        }

        // 按当前游戏重建候选槽位：清理僵尸 ID、恢复已绑定 HostView、刷新可用 Provider。
        // 关键：widget 归属只按「当前游戏 + 宿主包」从 widgetBindingStore 查，杜绝跨游戏复用同一个 widgetId。
        /**
         * Widget 首次刷新到达回调（由 RefreshAwareAppWidgetHostView 触发）。
         * [loadedPkg] 必须为**游戏**包名：结束该游戏的加载态，使面板在首次刷新完成后才呈现。
         * 注意：此前误传宿主包名导致加载键错位、只能等兜底超时（切回已绑游戏每次空转 ~1.5s），
         * 现已由调用方统一传入游戏包名修正。
         */
        fun onWidgetRefreshed(loadedPkg: String) {
            widgetLoading[loadedPkg] = false
        }
        /** 仅取当前游戏的 HostView 缓存，按「宿主包 → HostView」映射供 Compose 渲染（键不含游戏包）。 */
        fun currentGameWidgetViews(gamePkg: String): Map<String, AppWidgetHostView?> =
            widgetHostViewCache
                .filterKeys { it.startsWith("$gamePkg|") }
                .mapKeys { (k, _) -> k.substringAfter("|") }
        fun rebuildWidgetSlots(pkg: String) {
            val targets = widgetHostPackagesForGame(pkg)
            val slots = targets.map { p ->
                val storedId = widgetBindingStore.get(pkg, p)
                // 校验：记录有效 + 系统侧 widget 仍在 + provider 仍是该宿主包，否则视为未绑定并清理失效记录
                val info = if (storedId != -1) widgetHostManager.getAppWidgetInfo(storedId) else null
                val valid = info != null && info.provider.packageName == p
                if (storedId != -1 && !valid) {
                    widgetHostManager.removeWidget(storedId)
                    widgetBindingStore.remove(pkg, p)
                }
                val existingId = if (valid) storedId else -1
                WidgetHostSlot(
                    packageName = p,
                    appLabel = getAppLabel(p),
                    isInstalled = isPackageInstalled(p),
                    isBound = existingId != -1,
                    appWidgetId = existingId
                )
            }
            widgetSlots = slots
            slots.forEach { slot ->
                val ck = "$pkg|${slot.packageName}"
                when {
                    // 已绑定且已安装
                    slot.isBound && slot.isInstalled -> {
                        val cached = widgetHostViewCache[ck]
                        if (cached != null && cached.appWidgetId == slot.appWidgetId) {
                            // 复用上次刷新的 HostView：不重建、不转圈，立即结束加载态，保持上次状态。
                            widgetLoading[pkg] = false
                        } else {
                            // 缓存失效或首次：重建，并等待首次刷新事件结束加载态（键传游戏包，修正错位）。
                            widgetHostViewCache.remove(ck)
                            widgetHostViewCache[ck] = widgetHostManager.restoreWidget(
                                slot.appWidgetId
                            ) { _ -> onWidgetRefreshed(pkg) }?.also { widgetVersion++ }
                        }
                    }
                    // 已绑定但宿主包已卸载：清理孤儿 ID、归属记录与缓存，避免系统侧残留
                    slot.isBound && !slot.isInstalled -> {
                        widgetHostManager.removeWidget(slot.appWidgetId)
                        widgetBindingStore.remove(pkg, slot.packageName)
                        widgetHostViewCache.remove(ck)
                    }
                    else -> widgetHostViewCache.remove(ck)
                }
            }
            refreshAvailableProviders(targets)
        }

        val pickWidgetLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            widgetHostManager.handlePickResult(
                WidgetHostManager.REQUEST_PICK_WIDGET,
                result.resultCode,
                result.data
            )
        }

        widgetHostManager.onWidgetBound = { appWidgetId, info, hostView ->
            runOnUiThread {
                val pkg = info.provider.packageName
                // 绑定归属「当前游戏」；捕获成局部值，避免异步回调期间 currentPkg 已被切换
                val gamePkg = currentPkg
                // 仅接受当前游戏候选包内的绑定，避免其它页面（或历史）残留
                if (pkg !in widgetHostPackagesForGame(gamePkg)) {
                    widgetHostManager.removeWidget(appWidgetId)
                    return@runOnUiThread
                }
                // 本页每个游戏只保留一个 Widget：清理「当前游戏」其它已绑 ID 与其归属记录
                // （仅作用于本游戏槽位，绝不影响其它游戏已绑定的 widget）。
                widgetSlots.forEach { s ->
                    if (s.isBound && s.appWidgetId != appWidgetId) {
                        widgetHostManager.removeWidget(s.appWidgetId)
                        widgetBindingStore.remove(gamePkg, s.packageName)
                    }
                }
                // 记录「当前游戏 + 宿主包 → appWidgetId」归属，实现按游戏隔离
                widgetBindingStore.put(gamePkg, pkg, appWidgetId)
                widgetHostViewCache["$gamePkg|$pkg"] = hostView
                widgetSlots = widgetSlots.map {
                    if (it.packageName == pkg) it.copy(isBound = true, appWidgetId = appWidgetId) else it
                }
                widgetVersion++
                // 绑定落地即视为该游戏 Widget 就绪（首次刷新回调已在 WidgetHostManager 内触发），结束加载态。
                widgetLoading[gamePkg] = false
            }
        }

        // ── 截图归类：SAF 授权 + 样例选择 ──
        /** 在 IO 线程扫描授权文件夹，按当前游戏哈希后缀收集截图/录屏。 */
        fun scanScreenshotsFor(pkg: String) {
            val tree = screenshotStore.getTreeUri()
            val hash = screenshotStore.getHash(pkg)
            if (tree == null || hash == null) {
                screenshotState = if (tree == null) GameScreenshots.SetupState.NEED_TREE
                else GameScreenshots.SetupState.NEED_SAMPLE
                return
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val list = GameScreenshots.scanScreenshots(this@GamePageActivity, tree, hash).toMutableList()
                // 并入用户直接选取、可能不在授权树内的样例文件，保证至少呈现该张。
                lastSampleUri?.let { u ->
                    val s = GameScreenshots.shotFromUri(this@GamePageActivity, u)
                    if (s != null && list.none { it.uri == u }) list.add(0, s)
                }
                withContext(Dispatchers.Main) {
                    screenshots = list
                    screenshotState = GameScreenshots.SetupState.READY
                }
            }
        }

        /** 根据持久化状态推导当前游戏的初始化状态机（进入页面 / 切换游戏时调用）。 */
        fun refreshScreenshotState(pkg: String) {
            lastSampleUri = null
            if (screenshotStore.isDeviceUnsupported()) {
                screenshotState = GameScreenshots.SetupState.UNSUPPORTED
                screenshots = emptyList()
                return
            }
            when {
                screenshotStore.getTreeUri() == null -> {
                    screenshotState = GameScreenshots.SetupState.NEED_TREE
                    screenshots = emptyList()
                }
                screenshotStore.getHash(pkg) == null -> {
                    screenshotState = GameScreenshots.SetupState.NEED_SAMPLE
                    screenshots = emptyList()
                }
                else -> scanScreenshotsFor(pkg)
            }
        }

        // 1) 授权截图文件夹（持久化权限，避免重复弹窗）。
        val treeLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                screenshotStore.setTreeUri(uri)
                screenshotStore.clearHash(currentPkg)
                lastSampleUri = null
                screenshotState = GameScreenshots.SetupState.NEED_SAMPLE
            }
        }

        // 2) 选择一张该游戏的截图/录屏，学习其文件名后缀哈希。
        val sampleLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                val name = runCatching {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                    }
                }.getOrNull() ?: ""
                val hash = GameScreenshots.extractSuffixHash(name)
                if (hash == null) {
                    // 该设备截图文件名不含哈希后缀 → 全设备屏蔽。
                    screenshotStore.setDeviceUnsupported()
                    screenshotState = GameScreenshots.SetupState.UNSUPPORTED
                } else {
                    screenshotStore.setHash(currentPkg, hash)
                    lastSampleUri = uri
                    scanScreenshotsFor(currentPkg)
                }
            }
        }

        // 页面状态（Compose 可观测）
        var app by mutableStateOf<AppModel?>(null)
        var isFavorite by mutableStateOf(favoritesRepo.pinned.value.contains(packageName))
        var totalPlayMs by mutableStateOf(usageRepo.getTotal(packageName))
        var daySeries by mutableStateOf(LongArray(0))
        var installed by mutableStateOf(false)
        var versionName by mutableStateOf("")
        var firstInstallTime by mutableStateOf(0L)
        var permissions by mutableStateOf<List<String>>(emptyList())

        // 按「游戏包」缓存的数据快照（声明见上方截图存储后）：用于预载左右邻居游戏，
        // 使切游戏时目标页数据已就绪、动画连贯（消除先切到新页再补数据的 0.2s 缺口）。
        // LRU 淘汰序（仅主线程访问）：防止长时间左右切游戏导致快照无限累积占用内存
        val cacheOrder = ArrayDeque<String>()
        fun recordCacheUse(pkg: String) {
            cacheOrder.remove(pkg)
            cacheOrder.addLast(pkg)
            while (cacheOrder.size > GAME_CACHE_MAX) {
                val evict = cacheOrder.removeFirst()
                gameDataCache.remove(evict)
            }
        }

        // 左右切游戏：候选列表 = 已安装的游戏（白名单已装 ∪ 经本 App 分类映射识别为游戏的应用）。
        // 未安装内容（如仅注册美术资源的占位包）不进入切换循环，符合「只显示已安装」要求。
        var gameList by mutableStateOf<List<String>>(emptyList())

        // 装配数据：优先取已安装应用，未安装则基于美术资源 displayName 构造占位 AppModel，
        // 保证测试资源（绝区零 / 无限暖暖）即使未安装也能展示美术页。
        suspend fun snapshotGame(pkg: String): GameDataSnapshot {
            val loaded = AppRepository(this@GamePageActivity).loadPackage(pkg)
            val lastUsed = queryLastUsed(pkg)
            val model = loaded?.copy(lastUsedTime = lastUsed) ?: AppModel(
                packageName = pkg,
                className = "",
                label = pkg,
                icon = ImageBitmap(1, 1),
                isGame = true,
                lastUsedTime = lastUsed
            )
            // 预载美术：加载 GameArt 元数据 + 预热背景/英雄图解码 + 预热强调色取色，
            // 使切游戏时新页面首帧即采用正确背景与强调色，消除黑屏过渡。
            val art = artResolver.load(pkg, model.label)
            // 同步解码背景图并缓存，直接作为快照的 backgroundBitmap，供 GameArtBackground 首帧采用（跳过异步一帧）。
            val backgroundBitmap = art?.background?.source?.let {
                runCatching { decodeArtBitmap(this@GamePageActivity, it) }.getOrNull()
            }
            art?.hero?.source?.let { runCatching { decodeArtBitmap(this@GamePageActivity, it) } }
            val accent = art?.let { a ->
                // 与 GamePage.kt 取色 sources 顺序一致（logo→capsule→header→hero），确保 accentCache 命中
                val sources = listOfNotNull(a.logo?.source, a.capsule?.source, a.header?.source, a.hero?.source)
                if (sources.isNotEmpty()) runCatching {
                    extractAccent(this@GamePageActivity, sources, a.accentColor ?: Color(0xFF9A7BFF))
                }.getOrNull() else null
            }
            // 预载截图归类：同步扫描，使切游戏时新页面首帧即显示正确截图（无陈旧旧游戏截图）。
            // 仅在已授权树 + 已选样例时做真实扫描，否则按状态机降级（轻量）。
            val shotState: GameScreenshots.SetupState
            val shotList: List<GameScreenshots.Shot>
            when {
                screenshotStore.isDeviceUnsupported() -> {
                    shotState = GameScreenshots.SetupState.UNSUPPORTED
                    shotList = emptyList()
                }
                screenshotStore.getTreeUri() == null -> {
                    shotState = GameScreenshots.SetupState.NEED_TREE
                    shotList = emptyList()
                }
                screenshotStore.getHash(pkg) == null -> {
                    shotState = GameScreenshots.SetupState.NEED_SAMPLE
                    shotList = emptyList()
                }
                else -> {
                    val tree = screenshotStore.getTreeUri()!!
                    val hash = screenshotStore.getHash(pkg)!!
                    val list = GameScreenshots.scanScreenshots(this@GamePageActivity, tree, hash).toMutableList()
                    // 仅当前游戏并入用户直选样例（邻居预载不混入其它游戏的样例）。
                    if (pkg == currentPkg) lastSampleUri?.let { u ->
                        val s = GameScreenshots.shotFromUri(this@GamePageActivity, u)
                        if (s != null && list.none { it.uri == u }) list.add(0, s)
                    }
                    shotList = list
                    shotState = GameScreenshots.SetupState.READY
                }
            }
            return GameDataSnapshot(
                app = model,
                isFavorite = favoritesRepo.pinned.value.contains(pkg),
                totalPlayMs = usageRepo.getTotal(pkg),
                daySeries = usageRepo.getPackageDaySeries(pkg, 30),
                installed = loaded != null,
                versionName = model.versionName,
                firstInstallTime = model.firstInstallTime,
                permissions = queryPermissions(pkg),
                art = art,
                accent = accent,
                screenshotState = shotState,
                screenshots = shotList,
                backgroundBitmap = backgroundBitmap,
            )
        }

        // 加载并应用某游戏数据。preload=true 时仅写入缓存、不刷新当前页显示（用于预载邻居）。
        fun loadGame(pkg: String, preload: Boolean = false) {
            lifecycleScope.launch(Dispatchers.IO) {
                val snap = snapshotGame(pkg)
                withContext(Dispatchers.Main) {
                    gameDataCache[pkg] = snap
                    recordCacheUse(pkg)
                    if (!preload) {
                        app = snap.app
                        isFavorite = snap.isFavorite
                        totalPlayMs = snap.totalPlayMs
                        daySeries = snap.daySeries
                        installed = snap.installed
                        versionName = snap.versionName
                        firstInstallTime = snap.firstInstallTime
                        permissions = snap.permissions
                    }
                }
            }
        }

        // 从缓存即时套用某游戏数据（预载命中时同步执行，消除切游戏 0.2s 数据缺口）。
        fun applyFromCache(pkg: String): Boolean {
            val snap = gameDataCache[pkg] ?: return false
            app = snap.app
            isFavorite = snap.isFavorite
            totalPlayMs = snap.totalPlayMs
            daySeries = snap.daySeries
            installed = snap.installed
            versionName = snap.versionName
            firstInstallTime = snap.firstInstallTime
            permissions = snap.permissions
            return true
        }

        // 预载当前游戏左右相邻（循环）的 gameList 邻居，使切游戏时数据已就绪、动画连贯。
        fun preloadNeighbors(pkg: String) {
            val idx = gameList.indexOf(pkg)
            if (idx < 0) return
            for (d in listOf(-1, 1)) {
                val ni = (idx + d + gameList.size) % gameList.size
                val np = gameList[ni]
                if (!gameDataCache.containsKey(np)) loadGame(np, preload = true)
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            gameList = buildInstalledGames()
            // 列表就绪后预载相邻游戏数据，确保首次切游戏即可瞬时切换
            withContext(Dispatchers.Main) { preloadNeighbors(currentPkg) }
        }
        loadGame(packageName)
        // Widget 不再于初始装配：改为进入对应游戏页（LaunchedEffect(pkg)）时才加载，
        // 配合转圈 Loading 等「初始化 + 首次刷新」完成再呈现（2026-07-26）。
        // 当前游戏截图归类初始化（原在 loadGame 内，抽离后预载邻居不会误刷当前页）
        refreshScreenshotState(packageName)

        // 沉浸式 + 边缘到边缘（统一预测性返回手势窗口配置）
        setupEdgeToEdgeImmersive(hideStatusBar = false)
        // 预测性返回手势：系统绘制"揭示"预览动画，松手后退出本页回到主页。
        enablePredictiveBack { finish() }

        setContent {
            LandscapeTheme(settings = settings) {
                // remember 缓存 Flow 实例，避免重组时反复注册广播接收器
                val gamepadConnected by remember(this@GamePageActivity) {
                    GamepadDetector.gamepadConnectedFlow(this@GamePageActivity)
                }.collectAsState(initial = false)
                // 美术资源重载：提取 / 自定义主题 / 清除完成后 artVersion++。此处重读最新快照并写入缓存，
                // 再用 artRefresh++ 触发背景层重组 —— 否则同包（包名不变）时背景 Crossfade 不会重读 gameDataCache，
                // 导致「清除 / 提取新背景」后页面背景不立即刷新。
                LaunchedEffect(artVersion) {
                    if (artVersion > 0) {
                        val pkg = currentPkg
                        val snap = withContext(Dispatchers.IO) { snapshotGame(pkg) }
                        withContext(Dispatchers.Main) {
                            gameDataCache[pkg] = snap
                            recordCacheUse(pkg)
                            artRefresh++
                        }
                    }
                }
                val current = app
                AnimatedVisibility(
                    visible = current != null,
                    // 沉浸子页入场 / 出场统一走「动画机列表」subPage*（与 About/Licenses/CompatGuide 一致）。
                    enter = MotionMachines.subPageEnter(settings.reduceMotion),
                    exit = MotionMachines.subPageExit(settings.reduceMotion)
                ) {
                    val a = current ?: return@AnimatedVisibility
                    Box(Modifier.fillMaxSize()) {
                        // 订阅美术重载信号：artRefresh 变化时本 Box 重组，使下方背景 Crossfade 重读 gameDataCache 最新快照
                        val _artRefresh = artRefresh
                        // ── 背景层：独立 Crossfade 渐变（与内容层 pageSwitch 解耦，消除整页割裂）──
                        AnimatedContent(
                            targetState = currentPkg,
                            // 背景专用 Crossfade：380ms，比内容层 / 通用 crossfade 更长，消解背景硬切割裂感。
                            transitionSpec = { MotionMachines.backgroundCrossfade() },
                            contentKey = { it }
                        ) { pkg ->
                            val snap = gameDataCache[pkg]
                            GameArtBackground(
                                art = snap?.art,
                                accent = snap?.accent ?: MaterialTheme.colorScheme.primary,
                                // 预载背景 bitmap 同步传入：跳过异步解码一帧，首帧即完整背景图、无 accent 渐变跳变。
                                preloadedBitmap = snap?.backgroundBitmap
                            )
                        }
                        // ── 内容层：保留原 pageSwitch 整页切换 ──
                        AnimatedContent(
                        targetState = currentPkg,
                        // 「切游戏」为整页级切换，走 pageSwitch（全宽横移）；方向按最短循环距离判定。
                        transitionSpec = {
                            MotionMachines.pageSwitch(
                                forward = MotionMachines.cyclicForward(
                                    from = gameList.indexOf(initialState),
                                    to = gameList.indexOf(targetState),
                                    size = gameList.size
                                ),
                                reduceMotion = settings.reduceMotion
                            )
                        },
                        contentKey = { it }
                    ) { pkg ->
                        // 按包取数：切游戏过渡期间 old/new 两页各自持有自己的快照，
                        // 动画才不会出现「先切到新页再补数据」的 0.2s 缺口。
                        val snap = gameDataCache[pkg]
                        val a = snap?.app ?: current ?: return@AnimatedContent
                        // 记住最近查看的游戏页包名，供「设置 → 美术调试」手动打开时回显。
                        LaunchedEffect(pkg) {
                            runCatching {
                                getSharedPreferences("debug_art_overlay", MODE_PRIVATE)
                                    .edit().putString("last_pkg", pkg).apply()
                            }
                            // 进页才加载 Widget：先置加载态（转圈），待初始化 + 首次刷新完成再呈现。
                            widgetLoading[pkg] = true
                            // 【效率优化】取消建 HostView 前的固定 420ms 等待：切游戏即重建 Widget，
                            // 由 RefreshAwareAppWidgetHostView 的「首次内容到达」事件驱动加载态结束（始终检测，
                            // 不靠 delay 轮询）。不同游戏的 HostView 按包名各自独立、互不抢父（见 AndroidView
                            // update 内的 removeView 兜底），无需等过渡动画结束再重建；防抖竞态仍由 onSwitchGame
                            // 的 GAME_SWITCH_THROTTLE_MS 守卫，不受影响。
                            rebuildWidgetSlots(pkg)
                            // 当前游戏无任何「已绑定且已安装」的 Widget：无需等待刷新，直接结束加载态（呈现绑定入口）。
                            if (widgetSlots.none { it.isBound && it.isInstalled }) {
                                widgetLoading[pkg] = false
                            }
                            refreshScreenshotState(pkg)
                            // 兜底超时（收至 100ms 的 token 加载）：命中缓存基本不再初始化，转圈仅「意思一下」；
                            // 首次刷新事件通常更早结束加载态，此处仅兜底，之后由 widgetFade 渐入 Widget，消解硬切。
                            delay(WIDGET_LOADING_FALLBACK_MS)
                            widgetLoading[pkg] = false
                        }
                    GamePageScreen(
                        app = a,
                        provider = artResolver,
                        totalPlayMs = snap?.totalPlayMs ?: totalPlayMs,
                        isFavorite = snap?.isFavorite ?: isFavorite,
                        gamepadConnected = gamepadConnected,
                        daySeries = snap?.daySeries ?: daySeries,
                        installed = snap?.installed ?: installed,
                        versionName = snap?.versionName ?: versionName,
                        firstInstallTime = snap?.firstInstallTime ?: firstInstallTime,
                        permissions = snap?.permissions ?: permissions,
                        gameIndex = gameList.indexOf(pkg).coerceAtLeast(0),
                        gameCount = gameList.size,
                        onLaunch = {
                            // launchWithIntro 内部含美术解码 IO，改协程执行避免主线程 runBlocking 卡顿/ANR
                            lifecycleScope.launch {
                                val ok = launchWithIntro(a)
                                if (ok) finish()
                            }
                        },
                        onToggleFavorite = {
                            favoritesRepo.toggle(currentPkg)
                            val fav = favoritesRepo.pinned.value.contains(currentPkg)
                            isFavorite = fav
                            // 同步更新缓存快照，避免预载邻居的收藏态滞后
                            gameDataCache[currentPkg]?.let { snap ->
                                gameDataCache[currentPkg] = snap.copy(isFavorite = fav)
                            }
                        },
                        onClose = { finish() },
                        onOpenAppInfo = { openAppInfo(currentPkg) },
                        onOpenArt = { accent ->
                            openSteamArt(
                                currentPkg,
                                current?.label?.ifBlank { currentPkg } ?: currentPkg,
                                accent
                            )
                        },
                        onSwitchGame = { dir ->
                            if (gameList.isEmpty()) return@GamePageScreen
                            // 审计 PERF-G2：切游戏节流——AnimatedContent 过渡期间（~400ms）连按
                            // LT/RT 会叠加多次 loadGame/rebuildWidgetSlots/preloadNeighbors 竞态，
                            // 造成数据快照与目标包不一致。节流窗口内忽略后续切换。
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastGameSwitchMs < GAME_SWITCH_THROTTLE_MS) return@GamePageScreen
                            lastGameSwitchMs = now
                            val idx = gameList.indexOf(currentPkg).let { if (it < 0) 0 else it }
                            val ni = (idx + dir + gameList.size) % gameList.size
                            val np = gameList[ni]
                            currentPkg = np
                            // 优先从预载缓存即时套用（消除 0.2s 数据缺口）；未命中再异步加载
                            if (!applyFromCache(np)) loadGame(np)
                            // 注：rebuildWidgetSlots / refreshScreenshotState 已在 AnimatedContent 的
                            // content lambda 内 LaunchedEffect 实现（延迟 GAME_SWITCH_THROTTLE_MS 执行），
                            // 使旧页面在出场动画期间保留旧游戏 Widget / 截图，避免 Widget 中途"消失"与跨游戏串扰。
                            // 预载新游戏的左右邻居，为下一次切游戏做准备
                            preloadNeighbors(np)
                        },
                        widgetSlots = widgetSlots,
                        widgetViews = currentGameWidgetViews(currentPkg),
                        availableProviders = availableProviders,
                        widgetVersion = widgetVersion,
                        widgetLoading = widgetLoading[pkg] == true,
                        onPickWidgetProvider = { pkg, item ->
                            // 提示用户：自绘页面选中后会跳转系统 Widget 选取器，需手动完成选择。
                            android.widget.Toast.makeText(
                                this@GamePageActivity,
                                "请手动选取小组件",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            val provider = ComponentName(item.packageName, item.className)
                            val pickIntent = widgetHostManager.pickWidgetForProvider(provider)
                            pickWidgetLauncher.launch(pickIntent)
                            refreshAvailableProviders(widgetHostPackagesForGame(currentPkg))
                        },
                        screenshotGallery = GameScreenshots.ScreenshotGalleryState(
                            // 按页显示：切游戏过渡期间新页读自己快照的截图（即时、无旧游戏陈旧），
                            // 旧页保留旧截图；活动态仅作首屏未命中缓存时的兜底。
                            setupState = snap?.screenshotState ?: screenshotState,
                            screenshots = snap?.screenshots ?: screenshots,
                            onRequestTree = { treeLauncher.launch(null) },
                            onRequestSample = { sampleLauncher.launch(arrayOf("image/*", "video/*")) },
                            onOpen = { shot -> GameScreenshots.open(this@GamePageActivity, shot) },
                            onViewAll = { accent -> startScreenshotsAll(currentPkg, current?.label?.ifBlank { currentPkg } ?: currentPkg, accent) }
                        ),
                        reduceMotion = settings.reduceMotion,
                        cutoutAdapt = settings.cutoutAdapt,
                        rightStickFirstVisible = rightStickFirstVisible,
                        onActiveListState = { rightStickListState.value = it },
                        artVersion = artVersion,
                        preloadedArt = snap?.art,
                        preloadedAccent = snap?.accent
                    )
                    }
                    }
                }
            }
        }

        // 右摇杆滚动引擎：物理积分复用归一化 RightStickScrollPhysics（与主页/统计一致）。
        rightStickEngine = SubPageGamepadHost(
            scope = lifecycleScope,
            getState = { rightStickListState.value },
            getFirstVisibleItemIndex = { rightStickListState.value.firstVisibleItemIndex },
            onFocusSync = { idx -> rightStickFirstVisible.value = idx }
        )
        rightStickEngine?.start()

        // 应用级方向：直接按当前设置固定自身方向
        OrientationManager.applyAppOrientation(this, settings)
    }

    /**
     * 带横屏启动动画的启动：
     * - 竖屏（涉及横竖旋屏切换）或未取得 SYSTEM_ALERT_WINDOW 权限 → 直接启动，无动画；
     * - 横屏且已授权 → 先弹 TYPE_APPLICATION_OVERLAY 悬浮启动动画（游戏页结束后仍浮于上层），
     *   再后台启动游戏，动画悬挂 1~2 秒后自动淡出移除，覆盖游戏启动空白期且不受旋屏影响。
     */
    private suspend fun launchWithIntro(app: AppModel): Boolean {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val canOverlay = OverlayPermissionHelper.canDraw(this)
        if (!landscape || !canOverlay) {
            return AppLauncher(this).launch(app)
        }
        val ctx = this@GamePageActivity
        val resolver = GameArtResolver(this)
        // 单次切 IO 完成美术解析 + hero/logo 解码，替代原先三次主线程 runBlocking（ANR 隐患）
        val (heroBmp, logoBmp) = withContext(Dispatchers.IO) {
            val art = resolver.load(app.packageName, app.label)
            val hero = art?.hero?.let { decodeArtBitmap(ctx, it.source) }?.asAndroidBitmap()
            val logo = art?.logo?.let { decodeArtBitmap(ctx, it.source) }?.asAndroidBitmap()
            hero to logo
        }
        if (heroBmp != null) {
            GameLaunchOverlay.showArt(applicationContext, heroBmp, logoBmp, durationMs = 2000L)
        } else {
            GameLaunchOverlay.showSimple(applicationContext, app.icon.asAndroidBitmap(), app.label, durationMs = 1700L)
        }
        val ok = AppLauncher(this).launch(app)
        if (!ok) GameLaunchOverlay.remove()
        return ok
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onRestart() {
        super.onRestart()
        // 【P0-2 修复】onStop 已 stopListening；重启时恢复监听，避免切后台再回前台后 Widget 不刷新。
        widgetHostManager.startListening()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // 右摇杆轴交由归一化引擎驱动内容列表滚动（其余 MotionEvent 交系统）。
        return (rightStickEngine?.dispatchMotionEvent(event) == true) || super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        rightStickEngine?.stop()
        rightStickEngine = null
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        widgetHostManager.stopListening()
    }

    /**
     * 构建「左右切游戏」的候选列表：所有已安装、且应进入游戏页的应用包名。
     * 白名单已安装项置顶（保留国服 / 国际服排序），其余按本机名排序。
     * 仅含已安装应用，未安装的美术占位包不进入切换循环。
     */
    private suspend fun buildInstalledGames(): List<String> {
        val all = AppRepository(this@GamePageActivity).loadApps(includeSystem = false)
        val whitelistInstalled = GameWhitelist.packages.mapNotNull { pkg ->
            all.firstOrNull { it.packageName == pkg && it.installed }
        }
        val others = all.filter {
            it.installed && isGameLike(it) && it.packageName !in GameWhitelist.packages
        }.sortedBy { it.label.lowercase() }
        return (whitelistInstalled + others).map { it.packageName }
    }

    /** 查询某包最近一次前台使用时间（ms），用于「上次游玩」。无权限/无记录时返回 0。 */
    private fun queryLastUsed(pkg: String): Long {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
        val now = System.currentTimeMillis()
        val stats = runCatching { usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, 0, now) }
            .getOrNull() ?: return 0L
        // 关键修复：queryUsageStats 对同一包会返回多个时间桶（日/周/月聚合），
        // firstOrNull 取到的是最旧桶的 lastTimeUsed，导致「上次游玩」显示成很久以前。
        // 取所有桶中 lastTimeUsed 的最大值才是真实最近一次使用时间。
        return stats.filter { it.packageName == pkg }.maxOfOrNull { it.lastTimeUsed } ?: 0L
    }

    /** 跳转系统应用详情页（合并原卸载 / 强行停止入口，避免二次跳转）。 */
    private fun openAppInfo(pkg: String) {
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    /** 跳转「美术资源获取」二级页（SteamDB 提取 / 自定义主题的入口汇合）。 */
    private fun openSteamArt(pkg: String, name: String, accent: androidx.compose.ui.graphics.Color) {
        // 调试窗口（app/src/debug）不再自动弹出：用户在游戏页完成一轮操作（如导入美术）后，
        // 自行前往「设置 → 美术调试」打开该全屏窗口查看诊断快照。
        val intent = Intent(this, SteamArtHubActivity::class.java).apply {
            putExtra(SteamArtContracts.EXTRA_PACKAGE, pkg)
            putExtra(SteamArtContracts.EXTRA_LABEL, name)
            putExtra(SteamArtContracts.EXTRA_ACCENT, "#%08X".format(accent.toArgb()))
        }
        steamArtLauncher.launch(intent)
    }

    /** 查询应用声明的权限列表，无权限/异常时返回空列表。 */
    private fun queryPermissions(pkg: String): List<String> {
        val pm = packageManager
        return runCatching {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                ?.requestedPermissions?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /**
     * 单次加载的游戏数据快照。用于按「游戏包」缓存与预载邻居游戏，
     * 使左右切游戏时目标页数据已就绪、动画连贯（消除先切页面再补数据的 0.2s 缺口）。
     */
        private data class GameDataSnapshot(
        val app: AppModel,
        val isFavorite: Boolean,
        val totalPlayMs: Long,
        val daySeries: LongArray,
        val installed: Boolean,
        val versionName: String,
        val firstInstallTime: Long,
        val permissions: List<String>,
        val art: GameArt? = null,    // 预载美术（消除切游戏黑屏过渡）
        val accent: Color? = null,   // 预载强调色（首帧不降级为默认色）
        val screenshotState: GameScreenshots.SetupState? = null, // 预载截图归类状态（消除切游戏截图陈旧）
        val screenshots: List<GameScreenshots.Shot> = emptyList(), // 预载截图列表（切游戏首帧即时显示）
        val backgroundBitmap: ImageBitmap? = null, // 预载背景 bitmap（跳过异步解码一帧，消解背景 Crossfade 硬切）
    )

    /** 跳转到"查看全部截图/录屏"二级页面（与 GamePage 同栈，返回即回到本页）。
     *  透传当前游戏的独立强调色，避免新 Activity 冷启动时的默认主题色闪白。 */
    private fun startScreenshotsAll(pkg: String, name: String, accent: androidx.compose.ui.graphics.Color) {
        // 与 SteamArt 美术二级页同构：透传 EXTRA_PACKAGE / EXTRA_LABEL / EXTRA_ACCENT，截图页同步读取后立即渲染。
        val intent = android.content.Intent(this, GameScreenshotsAllActivity::class.java)
            .putExtra(EXTRA_PACKAGE, pkg)
            .putExtra(SteamArtContracts.EXTRA_ACCENT, "#%08X".format(accent.toArgb()))
            .putExtra(SteamArtContracts.EXTRA_LABEL, name)
        startActivity(intent)
    }

    companion object {
        const val EXTRA_PACKAGE = "cn.mocabolka.run.extra.GAME_PACKAGE"

        /** 游戏数据快照缓存上限（LRU）：覆盖当前 + 左右邻居 + 少量历史，防止无限增长。 */
        private const val GAME_CACHE_MAX = 12

        /** 切游戏节流窗口：略大于 AnimatedContent 过渡时长，避免过渡期数据竞态（审计 PERF-G2）。 */
        private const val GAME_SWITCH_THROTTLE_MS = 420L
        /**
         * Widget 加载兜底超时（token 加载）：命中缓存后基本不再初始化，转圈仅「意思一下」，
         * 300ms 后即结束加载态并由 widgetFade 渐入 Widget；首次刷新事件通常更早结束，此处仅兜底。
         */
        private const val WIDGET_LOADING_FALLBACK_MS = 300L
    }
}

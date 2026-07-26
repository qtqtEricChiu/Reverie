# Changelog

All notable changes to **Reverie** (formerly MRunner) are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and the project adheres to semantic versioning.

---

## [1.1.0 (3)] — 2026-07-26

**重大功能版本。** 自 1.0.0 以来最大一次演进：新增完整的**游戏详情页（GamePage）**、**SteamDB 美术提取系统**（Steam 库级 Hero / Logo 定位）、全站**玻璃拟态（Glassmorphism）**、归一化**动画机（MotionMachines）**、子页**手柄交互归一化**、以及**拼音搜索**。代码对照 Reverie 正式仓库（GitHub `v1.0.0` 基线）确认：新增 `ui/game/` 整包（GamePage 全家桶）、`ui/game/steamart/` 整包（SteamDB 提取链路）、`ui/theme/GlassState.kt` / `MotionMachines.kt`、组件 `BottomFadeMask` / `LayoutModifiers` / `ScrollProgressBar` / `SubPageKeyHandler`、工具 `util/Pinyin.kt`，并将 `gamepad/SubPageGamepad.kt` 重命名为 `SubPageGamepadHost.kt`。

### 游戏详情页 GamePage（全新模块）
- **活动与响应式骨架**：新增 `GamePageActivity` + `GamePage.kt` + `GamePageLayouts.kt`。`GamePageVariant` 由 `BoxWithConstraints` 约束推导 **6 档断点**（CompactPortrait / MediumPortrait / CompactLandscape / MediumLandscape / ExpandedLandscape 1200–1600 / ExtraLargeLandscape ≥1600 电视），分别套用 **PortraitBody / LandscapeBody** 两套骨架（07-15 横屏重构：旧 `Landscape2Body`/`Landscape3Body` 删除合并为 Steam 大屏式 `LandscapeBody` = 左 `GameLeftRail` 海报栏 + 右内容栏，LAND_2 右栏顶部 TabsRow、LAND_3 右栏顶部 Hero），横屏矮视口不再被全宽 Hero 吞掉垂直空间（根因 = 全宽 240~420dp Hero 大横幅在矮视口吞光垂直空间）。
- **架构演进（07-14）**：GamePage 初版为 HomeScreen 内 `AnimatedVisibility` 覆盖层（`DialogLayer.GAME_PAGE` 事件路由），当日即按用户决策重构为**独立 Activity**（与 About/Licenses/CompatGuide 同构：沉浸全屏 + LandscapeTheme + 预测返回），移除 GAME_PAGE 事件分支。同日奠基 `GameArt.kt` 资源抽象：`ArtSource`(Asset/File/Remote) + `GameArtProvider`/`GameArtResolver` 聚合器**接口预留**——后续 SteamDB 提取的 `FileGameArtProvider` 零改动接入即受益于此设计。精选白名单包名更正（绝区零国服 `com.miHoYo.Nap`/国际服 `com.HoYoverse.ZenlessZoneZero`、无限暖暖真实包名）。
- **焦点状态机**：`GPSlots` 扁平焦点索引（BACK / APP_INFO / FAVORITE / LAUNCH / TAB_DYNAMIC / TAB_STATS / TAB_ABOUT / CONTENT_START）驱动 `onKeyEvent`；↑↓ 栏内、←→ 跨栏（仅多列）、A 触发、B 返回、X 收藏、Y 在 TopBar⇄内容间跳、**LB/RB（L1/R1）循环切 Tab、LT/RT（L2/R2）切上/下一个游戏**（07-15 完成肩键语义对调，07-19 复核确认为最终版；LT/RT 浮块置于切游戏按钮两侧、LB/RB 浮块置于 Tab 滑块两侧，底部提示栏仅 A/B/X/Y 去重）。
- **游戏判定 `isGameLike`（07-15）**：`app.isGame || categoryText=="游戏" || 白名单`——经本应用分类映射识别为「游戏」的应用（如鸣潮）同样进游戏页；`shouldOpenGamePage`/`buildInstalledGames` 单闸门共用。
- **Tab 体系**：动态 / 统计 / 关于 三档（公告功能按用户决策全部移除，保持纯离线）。**功能范围决策（07-15）**：移除所有联网入口（成就 Play Games / 社区好友 / DLC / Saved Games / 多账号 / 内购），仅保留纯本地或系统 Intent 功能；卸载/强停合并进顶栏「应用信息」（`ACTION_APPLICATION_DETAILS_SETTINGS`），取消独立 ⋮ 菜单；截图聚合禁止任何联网图像识别。`GamePageTabs` 视觉对齐主页 `ReverieSegmentedRow`，仅手柄连接时（`GamepadVisible`）在滑块两侧放 LB/RB 键位浮块。
- **动态 Tab**：媒体截图画廊（`ScreenshotGallery`，按截图文件名后缀哈希自动归类本游戏截图/录屏，SAF `ACTION_OPEN_DOCUMENT_TREE` 授权 + `ACTION_OPEN_DOCUMENT` 选样例持久化记忆，`DocumentsContract` 遍历无需 `androidx.documentfile`）+ 每日游玩时间线 `TimelineRow`（按 UsageStats 按日聚合，MD3 ListItem 重做）+ 时长趋势 `TrendSummary`。
- **统计 Tab**：`TrendSummary`（总计 / 日均 / 最高 + 条形图），与主页统计独立。
- **关于 Tab**：版本 / 首次安装 / 权限（PackageManager `GET_PERMISSIONS`）。
- **第三方 Widget 宿主**：新增 `GameWidgetHost.kt`（`WidgetHostManager` / `WidgetHostSlot`），把社区 Widget 嵌入「个性化内容」区。`widgetHostPackagesForGame` 规则：米哈游/HoYoverse 系（含绝区零国服 `com.miHoYo.Nap`、国际服 `com.HoYoverse.ZenlessZoneZero`、原神等）→ 米游社 + TapTap 合并选择器；其它（无限暖暖、鸣潮等）→ 仅 TapTap。`PersonalizationWidgetPanel` 多 Provider 弹 `ModalBottomSheet`，单 Provider 直跳系统 Picker；Widget 贴边（`requiredWidth(整屏) + offset(-pad)` 突破外层 padding）。Widget 选择/绑定为**触控优先**，不进入手柄焦点链（规避 AndroidView 内嵌焦点冲突）。
- **Widget 按游戏隔离（07-19，核心修复）**：原按「宿主包」匹配首个已绑 id 恢复，导致「无限暖暖的 TapTap」与「鸣潮的 TapTap」复用同一 `appWidgetId` 串扰。新增 `WidgetBindingStore`（SharedPreferences，键 `gamePkg|hostPkg → appWidgetId`）按游戏记录归属；`rebuildWidgetSlots` 只查当前游戏绑定并校验 provider 有效性（失效即 `removeWidget`+清 store）；`onWidgetBound` 捕获 `gamePkg` 局部值防异步竞态，「每游戏仅一个 Widget」清理只作用当前游戏。合并选择器为「多应用多 Provider」通用模型（多应用分节 + `N 款应用 · 共 M 个 Widget` 副标题），未来加 WeGame/BiliBili 等候选无需改 UI；`WidgetPickerSheet` 控高 60% 屏 + 半展开 + 可滚动 + 沉浸隐藏状态栏（`DialogWindowProvider` 取独立 window）。
- **每日时间线与统计入口去冗余（07-15）**：GamePage 原 4 个入口落系统应用详情页（应用信息/游戏设置/两张统计卡），统计独立成 Tab 后收敛为 2 个；`StatRow`/`StatCardBig` 删除；「上次游玩显示 8 天前」根治（见修复合集）。切游戏候选 `buildInstalledGames`：白名单已装置顶、未安装不进循环、顶栏 `N/M` 序号。
- **运行时取色**：`GameArt.extractAccent` 离线手写 HSV 直方图（96px 下采样、跳过 alpha<128/近灰/过暗/过亮、36 色相桶投票、按 s*v 加权、归一化 s∈[0.55,0.9] v∈[0.5,0.85]），取色图片优先级 logo→capsule→header→hero（logo 品牌纯色最佳），`LruCache` 缓存；accent 优先级 **游戏 art > Monet > BrandColors**，本页不随 Monet 覆盖。新增 `GamePageColors.onAccent(accent)`（按 luminance 自动黑/白字）+ `tonal(accent,alpha)`，全站按钮 accent 单色系隔离。
- **UI/UX 十项审计整改（07-15，P0/P1/P2）**：Tab 选中 `accent.copy(0.20)` + `segmentContainer()` track；`WidgetPickerSheet` 标题白字改 `onSurface`（亮色主题可见）；`TimelineRow` 按 MD3 ListItem 重做（未游玩降透明无条形、游玩态 accent 8% track + 55% 细条）；`LaunchButton` 焦点框改 `onAccent(accent)`（黄钮黄框不可见根治）；「游戏截图」标题改 `onBackground` + 「N张」tonal chip；顶部 scrim 0.55→0.45；已绑 Widget 顶部 accent 10% 4dp「已个性化」信号条；截图缩略图圆角对齐 `shapes.medium`；未选中 Tab 聚焦加 accent 10% 底色信号。
- **控件归一化审计落地（07-15）**：GamePage 全部原生 `IconButton`/`AssistChip`/`TextButton`/`Button` 替换为 `ReverieIconButton`/`ReverieFilledButton`/`ReverieTextButton`/`ReverieTonalButton`；Tab 视觉对齐主活动 `ReverieSegmentedRow`；焦点框统一 `focusBorder(focused, accent)` 助手；`Buttons.kt` 登记「全站 default primary，GamePage focusColor=accent 属已登记专属例外」。
- **手柄导航横向贯通修复（07-15）**：TopBar RIGHT 从 FAVORITE 原 `coerceAtMost(FAVORITE)` 永远到不了 LAUNCH、LEFT 从 LAUNCH 不动——改为 返回/应用信息/收藏/启动 横向全贯通。
- **Hero 严格不裁切**：Hero 图 `ContentScale=FillHeight`（高度锚定，绝不上下裁切）；`useOriginalRatioHero = isXL || LAND_3`。横屏 LAND_3 顶栏 Hero 200/240dp 封顶切换；`GameLeftRail` 海报 `min(capsuleWidth*1.5, availH*0.48)` 封顶永不溢出。
- **切游戏动画**：`AnimatedContent` 按最短循环距离定方向；`gameDataCache[pkg]` + `preloadNeighbors` 相邻 2 预载；切换后焦点复位 LAUNCH、`loadGame` 原地重载，`currentPkg` 替代 `currentIndex` 跟踪（未安装不进切换循环，`buildInstalledGames` 走 `isGameLike`）。
- **切游戏 0.2s 数据缺口根治（07-19）**：原「先渲染空壳再异步填充」——`AnimatedContent` 入场瞬间 `gameData=null`，收藏星/统计/时间线闪空后再跳变。改为**先取数后动画**：`gameDataCache` 命中即同步展示，未命中先在旧画面上台后加载、数据就绪才推进 `targetState`；新增 `GAME_SWITCH_THROTTLE_MS` 节流防连按穿透。
- **挖孔/刘海适配下沉（07-19）**：竖屏 header 内容整体 `statusBarsPadding()` 下沉（Hero 图仍全屏延伸至状态栏后），返回钮/星标不再被挖孔遮挡；该模式后沉淀为 `LayoutModifiers.statusBarSafeTop`。
- **竖屏 HeroBanner 滚动折叠（07-26，collapsing header）**：竖屏 PortraitBody 的 HeroBanner 与内容 LazyColumn 为兄弟节点，根 Column 挂 `nestedScroll` 跨兄弟传递滚动偏移；`Animatable(0f)` 持有 `collapsePx`，`onPreScroll` 下滑 `snapTo` 立即收窄、`onPostScroll` 判定「主体列表到顶」即 `animateTo(0f)` 弹回全高（切游戏按包名 key 归零）。logo 用全展开参考高度 `variant.heroHeight - 2*padV` 计算、空间不足才等比缩、点「果冻 spring」回弹；banner 本身 1:1 线性跟手无弹簧（动画机铁律下 `heroScrollCollapse` 因 `Animatable` 方案而删除、未散落新动画机）。竖屏点按游戏类一律 `openGamePage`，绝不进应用详情抽屉（结合 `portraitDrawerOpen=false` 防御）。
- **预测返回统一**：根因 = 四个子 Activity 用旧式 `WindowCompat.setDecorFitsSystemWindows(false)` 未配置手势导航窗口，预测预览动画不出现（仅 `MainActivity` 的 `enableEdgeToEdge()` 生效）。新增 `ui/EdgeToEdge.kt` 的 `setupEdgeToEdgeImmersive()`（内部 `enableEdgeToEdge()` + 隐藏状态栏 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`），About/Licenses/CompatGuide/GamePage 四个 Activity 改用之；MainActivity 二次确认气泡与预测预览共存（首滑预览不退出、二次确认才 finish）；预测返回一律用 `android.window.OnBackInvoked*` 平台类型（修正 `androidx.activity:activity:1.10.0` AAR 未暴露 `OnBackInvoked*` 的编译阻断）。
- **左栏手柄焦点修复（07-20，根因修正）**：LAND_3 左栏摇杆/十字键失灵的真实根因 = `GameLeftRail` 根 `Column` 挂了 `verticalScroll(rememberScrollState())`，可滚动容器成为**真实焦点节点**吞掉 DPAD 事件、页面级 `onKeyEvent` 焦点状态机被绕过。修复 = 删除左栏 `verticalScroll`（左栏内容固定本就不应滚动），焦点全部回归 `GPSlots` 状态机。**铁律沉淀：焦点状态机页面内严禁再挂可滚动容器于焦点链上。**
- **左栏视觉演进（07-19~07-20）**：海报卡右上角收藏星标浮层（金色 `FavoriteStar`）；启动按钮 accent 主色、游戏设置/应用信息降为 tonal；`GameLeftRail` 海报高度 `min(capsuleWidth*1.5, availH*0.48)` 封顶。
- **页面级焦点跟随滚动修复（07-25）**：游戏页家族旧逻辑带「保守守卫」（完全移出视口才滚 / `firstVisibleItemIndex` 守卫）导致左摇杆焦点到哪页面不跟随、右摇杆正常。改为与 `SubPageScaffold` 同构——焦点一变即 `animateScrollToItem` / 带 `margin=90` 舒适带跟随。`ScreenshotGallery` 根加 `focusGroup` 标记（禁用 `focusable(true)` 抢根焦点），`LaunchedEffect(selectedShot)` 横向跟随滚动到「查看全部」磁贴。
- **竖屏 HeroBanner 滚动折叠（完整演进）**：`GamePageBody` 根 Column 挂 `nestedScroll` 跨兄弟节点折叠（HeroBanner 与 LazyColumn 是兄弟节点、不在同一滚动容器）；折叠态由 `collapsePx`(mutableFloatState) 演进为 `Animatable(0f)`——`onPreScroll` 下滑 `snapTo` 立即收窄、`onPostScroll` 判定「主体列表到顶」即 `animateTo(0f)` 弹回全高（无需二次滑动）；方向修正（下拉收窄、上推恢复）；`logo` 加果冻 spring（banner 仍 1:1 跟手、去掉弹簧拖影），`logoRefH` 固定全展开高度防随折叠缩小。
- **LAND_3 大屏横屏 banner 无美术时隐藏（07-26）**：`LandscapeBody` 顶部 banner 原 `if (variant.structure == GPStructure.LAND_3) HeroBanner(...)` 改为 `if (variant.structure == GPStructure.LAND_3 && data.hasArt) HeroBanner(...)`，与竖屏分支（`if (data.hasArt)`）对齐。`data.hasArt` 覆盖内置 + SteamDB 提取美术；无美术时右内容栏顶部直接接 Tab/内容、不露空横幅；LAND_2（中小横屏本无顶部 banner）不受影响。
- **竖屏 banner 恒定 3.097:1 动态高度（07-26）**：平板竖屏（如 Cities Skylines）`bannerW≈1080dp` 配固定 `heroHeight=220dp` → 容器宽高比 ≈4.91:1（远超 3.097:1），hero 居中后左右大量留白、accent 渐变透出、观感割裂。竖屏分支 `val fullH = variant.heroHeight` 改 `variant.heroHeight.coerceAtLeast(heroMinH)`（不足 3.097:1 所需即向上拔到 `heroMinH`，保持宽:高 ≤ 3.097:1）。各 variant 影响：CompactPortrait（手机竖屏）heroHeight 已大于 heroMinH → 透传、原折叠行为保留；MediumPortrait（平板竖屏）启用动态高度、折叠自动 neutral（内容区有富余、禁折叠可接受）；LAND_3 顶部 banner 独立计算（`isXL?240:200dp`）不受影响；横屏 LAND_3 走 `useOriginalRatioHero`（`bannerW/srcRatio`）本就满足 3.097:1。
- **banner 内部边距拆分（16/12dp）**：外层 banner 不加 padding 铺满、hero 铺满边缘到边缘；logo 单独内层盒 `padding(16,12)` 仅服务 logo 安全区（不推开容器外 margin）。**教训：给内容加内边距又不想产生外 margin，必须把 padding 加在内部内容容器上。**
- **清除/提取新美术后背景不立即刷新修复**：`gameDataCache` 是普通 Map 写入不触发重组、`artVersion++` 只驱动 hero/logo 重读；新增可观察信号 `artRefresh` + `LaunchedEffect(artVersion)` 用 `snapshotGame` 重读快照并 `++artRefresh` 驱动背景 `AnimatedContent` 重读——清除回退 accent 渐变、提取新文件即时换背景。
- **截图查看全部页完全镜像 SteamArtHubActivity 范式（二级子页铁律）**：EXTRA_LABEL/EXTRA_ACCENT 透传 + `setContent` 立即全屏渲染无占位 + 屏内异步 `GameArtResolver.load` + accent 渐变回退；**废除 ready 闸门**（标题栏同步已知、`statusBarSafeTop()` 已避让状态栏，闸门纯多余且造成硬切）；**背景跨页零延迟继承**（`gameArtCache` LruCache(16) 同步 `peekGameArt/peekArtBitmap`，进页首帧命中缓存即显完整美术背景，accent 渐变仅作进程重建兜底）。

### SteamDB 美术提取系统（全新模块）
- **活动链路**：`SteamArtHubActivity`（入口：提取 / 自定义 / 重新提取 / 查看全部 / 清除）→ `SteamArtExtractActivity`（WebView 探针，`keepScreenOn`、View 键日期）→ `SteamArtCustomActivity`（自定义导入）→ `GameScreenshotsAllActivity`（查看全部截图）。
- **三层架构**：`domain/`（提取任务 `ExtractAssetsUseCase` / `AssetSelector` / `ExtractionTiming`、模型 `AssetKind` / `SteamAsset` / `ExtractionState`）、`data/remote/`（商店兜底 `StoreApiClient` appdetails、`SteamCdnAssets` 静态图、`AssetDownloader`）、`data/webview/`（`CfWebViewClient` / `SearchWebViewClient` / `SteamWebViewFactory` / `SteamWebViewPolicy` / `ExtractionBridge`）、`ui/`（`SteamArtGraph` / `SteamArtViewModel` / `SteamArtContracts`）。
- **提取时序 4 阶段**：accent → library（胶囊/海报）→ logo（标志）→ background（背景）。`ExtractAssetsUseCase` 必须 `channelFlow{send}`，严禁 `flow{emit}`（跨线程崩 Flow invariant）。
- **WebView 探针与 Cloudflare**：`SteamWebViewPolicy` 白名单仅 `steamdb.info`；`CfWebViewClient` 抓取多图、连锁错误回退；CF 检测分级（强信号立即短路、弱信号 `challenge-platform` 仅零链接时判）；CF-free 兜底 `SteamCdnAssets` + 12s `scheduleProbeFallback`。探针生命周期：每次提取独立 Activity+WebView+VM，完成后彻底 tear down（`stopPolling`/`stopLoading`/`clearCache(true)`/`destroy`/`WebStorage.deleteAllData`/`CookieManager.flush`）；**只保留 cookie 过 CF，绝不 `removeAllCookies`**。
- **稳定性 hardening（07-26 用户授权）**：`SteamSearchWebView` 删除全局 `WebStorage.deleteAllData()`、改仅 flush Cookie（避免污染并发提取 WebView 的 CF 状态）；`onAdultGate(false)` 解锁后加 `GATE_CLEAR_DEBOUNCE_MS=3000L` 等待再定稿；`detectAdultGate` 补回 `mature|nsfw` 关键词；`PROBE_FALLBACK_MS` 弱网（移动/计量网络）自动延长到 20s；`extractFired` 改 `AtomicBoolean.compareAndSet` 消除双计时器竞态；`CfWebViewClient` 加 `shouldOverrideUrlLoading`（受 `SteamWebViewPolicy.isAllowedTopLevelNavigation` 限制）。`isLikelyWeakNetwork` 必须 suspend + `withContext(Dispatchers.Main)` + `ContextCompat.checkSelfPermission` 先验权限（缺失返回 false 走 12s），并对 `getActiveNetwork`/`getNetworkCapabilities` 包 `runCatching` 防个别设备无权限仍抛 SecurityException 崩溃。
- **缓存落盘**：下载至 `filesDir/steamart/<pkg>/`，JDK `HttpURLConnection`（无 okhttp/coil）；同包多 `GameArt` 合并（`GameArt.merge`）；`FileGameArtProvider` 优先 `AssetGameArtProvider`；刷新链 `RESULT_OK → artVersion++ → 重读`；`ArtSource.File` 带 `lastModified`，`clearArtCachesForPackage` 防串味。`LruCache(64)` 取色单源（`extractAccent` + `FileGameArtProvider`）。
- **主题注入点**：`GamePage` 主题 / 截图画廊 / `GameArtBackground`（原 `SteamArtBackground`）跨页一致使用取色背景。
- **可清除性增强（07-26）**：`GameArtTombstone` 墓碑标记（`filesDir/steamart_cleared/<pkg>`），「清除」先 `deleteRecursively` + `mark` + `clearArtCachesForPackage`；`GameArtResolver` 在 File 层 miss 且 `isCleared` 时直接返回 false 不回落内置 → 预设/内置游戏（无限暖暖/绝区零/鸣潮）也能真正回到无美术初始态，重新提取自动绕过墓碑。
- **提取 WebView 顶部渐变遮罩（07-26）**：`SteamArtExtractActivity` 在 WebView 之上、标题栏之下加纯装饰 `verticalGradient(Black 0.6→Transparent)` 遮罩（全幅铺满、不加挖孔适配、不加 clickable 穿透到 WebView，不影响 Cloudflare 验证点击）。
- **自定义主题资产 6 槽修复（07-26）**：`AssetKind.required` getter 排除 `CAPSULE_SMALL`（与 `HEADER` 同处理），恢复 6 槽 = hero/hero_capsule/library_capsule/library_header/logo/page_bg_raw（修复小头图槽挤占、page_bg_raw 被顶出视图的 bug）；`customFileName` 补 `CAPSULE_SMALL` 显式分支消除误写主视觉隐患。
- **自定义主题真实尺寸贯通（07-26）**：`writeCustomMeta` 补 `widthPct/heightPct` 写入、加载侧补读，SteamDB 导入 logo 携真实百分比→`computeLogoBox` 精确尺寸分支（修复自定义主题 logo 全变 50%×50%）；`logoScale` 由五档离散改**无极连续**（0.5~1.5）+ 智能推荐初值（手机竖屏按 widthPct 推荐、横屏/平板恒 1.0），且修复「推荐值无条件覆盖已存档 logoScale」的 bug。
- **自定义主题 6 槽被 CAPSULE_SMALL 挤占修复**：`AssetKind.required` 排除小头图（与 HEADER 同处理），恢复 6 槽 = hero / hero_capsule / library_capsule / library_header / logo / page_bg_raw（原小头图槽误写 `library_hero.jpg` 顶掉 page_bg_raw）。
- **自定义主题丢弃 SteamDB 真实矩形尺寸修复**：`writeCustomMeta` 补 `widthPct/heightPct` 参数 + 加载侧读取，避免所有自定义主题 logo 统一 50%×50%（仅 logoScale 能差异化）。
- **自定义主题「关掉 logo 贴边不回退」bug 修复**：`changed` 改与进入时初始快照（`initialPin/initialScaleIdx/initialCrop`）对比，修复「关掉本就开启的开关」被漏判成「没改」导致 meta 仍记 true。
- **isLikelyWeakNetwork SecurityException 崩溃修复**：`AndroidManifest` 新增 `ACCESS_NETWORK_STATE`；`isLikelyWeakNetwork` 改 `suspend` + `withContext(Main)` + 先验权限 + `runCatching` 包 `getActiveNetwork/getNetworkCapabilities`，个别设备无权限仍抛异常时返回 false 走 12s 兜底。
- **内置 gameart 仅 debug 变体可见（07-26）**：`app/src/main/assets/gameart`（内置 SteamDB 美术兜底：无限暖暖/绝区零/鸣潮 3 款 + `manifest.json`，19 文件）物理移至 `app/src/debug/assets/gameart`，AGP 自动仅并入 debug 源集、不进 release APK；无需改代码——`AssetGameArtProvider` 用相对路径 `gameart/...` 读取，`parseManifest()` 被 `runCatching{}.getOrDefault(emptyList())` 包住，release 下 manifest 缺失时 `hasArt`/`load` 优雅返 false/null 不崩。

### Logo 定位系统（Steam 库规范合规）
- **9 点双对齐体系**：`LogoPosition` 由 9 点枚举（TopLeft…BottomRight）+ 真实百分比（`widthPct`/`heightPct`，范围 -100~200 含溢出）+ `anchor` 双对齐构成，支持 Steam 库级 logo 浮于 Hero 任意位置（含跨边溢出）。字符串/百分比双表示，持久化 `meta.json`。
- **真实百分比体系**：`positionX/Y` 取 SteamDB 真实百分比（非归一化 0~1）；`computeLogoBox` 以**（去 padding 的原图全尺寸 bannerWpx/bannerHpx，严格 3.097:1）** 为基准乘积，无 autoScale 压缩、无 dynamicScale 倍率（07-26 最终复核：从旧的「banner−24dp 内边距区」改为乘全图，并移除渲染容器 `padding(HERO_LOGO_PAD)`、删除该常量，logo 按去 padding 全图坐标系锚定角对齐，1:1 复刻 SteamDB 定位）。
- **自定义主题连续滑块**：`logoScale` 由 5 档（0.6/0.8/1.0/1.2/1.4）改为**连续滑块**（默认 1.0，每游戏独立）；`logoCrop`（`cropTransparentEdges` alpha 包围盒裁剪，默认关）；JSON `logo_scale`/`logo_crop`，退出写 `meta.json`。`recommendLogoScale`：HoYo/默认/预设分级推荐。
- **预设 AppId 与 logo_position（07-26 用户校正，此前 AI 臆造值已纠正）**：无限暖暖=3164330、绝区零=4162040、鸣潮=3513350；预设 `logo_position`（仅兜底默认值，由用户 07-26 提供真实 SteamDB 值）均为 BottomLeft 的 width/height：无限暖暖=17.73611/42.36111、绝区零=37.18221/71.32872、鸣潮=22.66531/45.13934。
- **优先级（tombstone）**：Logo 落盘优先级 **WebView 探针 > 商店 API > 已导入定位(重提取前从 meta.json 捕获) > 预设兜底**；任何真实导入/提取得到的定位都覆盖预设，预设仅在所有真实来源都缺失时生效。banner 内边距 16/12 两档。
- **提取落盘**：`SteamDB` logo_position 新增第 4 解析策略（`<td>` 表格 `KEY(带 logo_position/ 前缀) → VALUE` 正则解析）；提取前先查 `GameWhitelist` 校验 AppId/戏名一致，未知 AppId 不强制提取、无匹配不提取。

### 玻璃拟态全站改造（Glassmorphism）
- 新增 `ui/theme/GlassState.kt`：主活动入口默认全开 `LocalGlassEnabled`（含 `onResume` 重写取色驱动），各子页默认关（避免性能/视觉冲突），由 `HomeViewModel` 单源驱动。
- `frosted` 用 `RenderEffect` / `Motion.blur` 实现毛玻璃；`WallpaperBackground` 改用模糊壁纸做毛玻璃底（`enableEdgeToEdge` 全屏壁纸铺满 + 模糊）。
- `SurfaceTokens` 新增 `Overlay`/`glass` 感知：玻璃表面用 `glassBg = background.copy(0.72f) + blur` 而非纯色卡片；`AppDetailPanel` 玻璃化（横屏右栏 + 竖屏浮层共用）。
- 子页（About/Licenses/CompatGuide/GamePage）明确玻璃开关策略，避免与 `SurfaceTokens.cardSurface` 冲突。

### 动画机 MotionMachines（动画归一）
- 新增 `ui/theme/MotionMachines.kt`：统一 `crossfade` / `backgroundCrossfade` 等可复用动画机器，**严禁页面散落一次性动画**（铁律：已存在则引用，不存在则先沉淀再引用）。
- `GamePage` 切游戏背景 `backgroundCrossfade` 复用；删除旧 `heroScrollCollapse`（动画机未含此效果，散落实现移除，改由布局自身处理）。
- 节庆/焦点高亮等复用既有机器，不重复自绘。

### 手柄交互归一化（子页 + 焦点 + 震动）
- `gamepad/SubPageGamepad.kt` 重命名为 `SubPageGamepadHost.kt`：子页统一薄宿主（右摇杆物理滚动 `RightStickScrollPhysics.step` + 焦点回写 `rightStickFirstVisible`），严禁子页自写摇杆解析；连发参数 `SUBPAGE_REPEAT_* = 240/1200/150ms`（主页 `180/600/80ms`）。
- **右摇杆轴污染修复（07-19）**：`SubPageGamepad.dispatchMotionEvent` 改用 `AXIS_RY`（右摇杆），剔除 `AXIS_RZ`（右扳机 RT）污染——修复「按 RT 切游戏误触发页面滚动」的真实 Bug；后续归一为 `GamepadManager` 单一真源 `rz ?: ry` 兼容双轴设备。
- `ui/components/SubPageKeyHandler.kt`：归一子页 `onKeyEvent` 处理（按键判定 `ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN`；Activity 自管 `onGenericMotionEvent`；`when` 多条件用 `||`），About/Licenses/CompatGuide/GamePage 统一接入。
- **焦点恢复**：子页恢复焦点用 `hasFocus` + `onFocusChanged`（非旧 `focused` 双标志），避免 GamePage 焦点框锁死（07-19 根因 = `collectAsState` 漏订阅 + `focused` 标志未同步解锁）。
- **震动归一 + 强度可调（07-26 升级）**：`Haptics` 单例（tick/click/error 按 `HapticIntensity` 缩放，OFF 早返回）；修正旧硬编码 `hasPrimitives=true` 致**全局零震动**的真实根因——改用 `arePrimitivesSupported(BooleanArray).all{it}` 真实探测 + `createOneShot` 兜底；删除旧 `VibratorManager` 代理、杜绝双震（旧 `view.performHapticFeedback` + `Haptics` 叠加）。
- **震动触感强度（07-26）**：设置页新增 `HapticIntensity` 下拉（强 / 中 / 弱 / 关闭，旧单开关归类为「弱」），`SettingsRepository` 持久化 + 旧 boolean 键兼容迁移；增删档只改枚举与 `Haptics` 缩放映射，调用点类型不动。
- **游戏子页面震动缺失修复（07-26）**：`Haptics.init` 仅 `MainActivity` 调一次（进程级单例，禁游戏 Activity 重 init 破坏实时档位）；`GamePage.kt` 主页面裸 `onKeyEvent` 补齐方向/肩键/扳机=tick、A/B/X=click；`SubPageGamepadHost.onNavigate` 集中注入左摇杆/Hat `Haptics.tick()`（单一出口），移除美术子页 navFlow 多余 tick 防双发。**双震铁律**：key 路径（subPageKeyHandler）与摇杆路径（navFlow/onNavigate）互不重叠，绝不在 moveFocus/activate 内再写 Haptics。
- **设置页下拉 X 键循环切换（07-26）**：`SettingsControlBridge.onCycle(Int)->Boolean`——Dropdown 行循环切换下一项（向下闭环）+ `Haptics.tick()`，非 Dropdown 行返回 false 走原 `onFavorite` 信息弹窗；`DropdownListItem` 聚焦且手柄连接时 trailing 加 `Hint(KeyToken.X,"切换")`。
- **子页标题栏归一化**：所有 2/3 级子页统一 `KeyBadge.SubPageTitleBar`（40dp 返回钮），禁额外渐变遮罩/高头/大标题块；基准 `SubPageScaffold`：标题栏固定、`Dimens.SubPageTopBarHeight=56dp`、内容 `Column(weight(1f)+verticalScroll)`。
- **右键焦点随滚动**：游戏页家族与 `SubPageScaffold` 同构（焦点变即跟随），`GamePageActivity` 切游戏 `animateScrollToItem`。

### 设置项文案精简与 info 纪律（07-26）
- **需求**：设置页全部设置项 `desc` 精简至 ≤15 中文字符（上限非固定字数，须自然长短 4~15）；必要项加 `info` dialog（`infoTitle`/`infoText`/`imageRes`）并适配配置。
- **用户纠正 info 过度（收尾）**：初版误把"≤15 字"理解成"全压 6 字"（22 项整齐 6 字机械呆板，且「排序方式」误写成「应用到全部应用」文不对题），并加了 16 项 info（违背"只做必要的"）。修正：描述恢复自然长短、顺序方式→「选择列表排序规则」；**info 仅保留「导出应用列表」一项**（JSON+AI 分类映射工作流放不下 15 字），其余 15 项全部移除，导入/清除分类映射说明并入导出项 info。长期约定已写入 MEMORY.md。
- **现状**：全部 22 项 desc 已精简（最长「恢复默认并清收藏」9 字、「版本信息、特别感谢、开源许可」12 字，均≤15）；`DropdownListItem` 聚焦 + 手柄连接时 trailing 加 `Hint(KeyToken.X,"切换")`（配合 `SettingsControlBridge.onCycle` 循环切换）。

### 组件与令牌归一化
- 新增 `ui/components/BottomFadeMask.kt`（底部渐隐遮罩，回收既有散落实现）、`ui/components/LayoutModifiers.kt`（`statusBarSafeTop` 状态栏安全区）、`ui/components/ScrollProgressBar.kt`（滚动进度条）。
- `CenteredHint` 复用统一居中提示；`DialogScaffold`（07-14 已建）继续作为全站 dialog 模板（scrim + 居中卡片 + 底部 HintBar）。
- `SurfaceTokens` 继续收敛 alpha 语义色（焦点/卡片/遮罩/静音文字/Toast），禁手写 `copy(alpha=)` 除图片 scrim；收藏星标金 `Theme.kt::FavoriteStar`。
- 手势阈值统一 `defaultGutter=16dp`；状态栏安全区 `LandscapeTheme.edgePadding`；未知应用占位（steamart/<pkg> 缓存 + 图标占位）。

### 统计页增强
- **「次数」维度**：统计新增按启动次数的维度（与时长并列）。
- **系统应用聚合**：统计聚合系统/自身包，避免污染排行。
- **逐日列表倒序**：`getDailyUsageList` / `getMonthlyUsageList`（倒序），`DailyPlaytimeList`（标题随周期动态：今日各小时 / 近7天每日 / 近30天每日 / 近12个月每月；最新最上；零值「—」）。
- **快速按钮锚点**：`StatsQuickButtons`（最近游玩→排行区 / 总计时长→列表区，均在统计页内锚点滚动，不跳设置页）；`RecentsRow` 标题可点击进统计页。

### 搜索增强（拼音搜索）
- 新增 `util/Pinyin.kt`：拼音搜索（按拼音/首字母匹配应用名），与 `label` 过滤合并。
- **搜索页手柄键位最终定稿（07-26，推翻 07-25「Y 默认 LIST」）**：进入搜索页**默认 INPUT 态**（输入框获焦 + 软键盘弹出）；推左摇杆任意方向或按 Y 进 LIST（与主页 Y=收藏分流，搜索页 Y **绝不收藏**）。A=进详情浮窗（右栏）；X：**INPUT 态=清空输入框 / LIST 态=直接启动焦点项**（含游戏均 `launchApp`，游戏页归 A，不套「游戏特殊处理」）；Y INPUT⇄LIST；B：INPUT→LIST 收键盘 / LIST→返回。`searchResults` 即渲染 `visibleList` 同源。
- **INPUT 态摇杆方向穿透根因修复（07-26）**：`GamepadManager.emitAxisDirection` 原 `canNavigate = navigationEnabled || dialogActive || (dir==DOWN && searchInputExitDown)` 在 INPUT 态丢弃非 DOWN 方向 → 改为 `searchInputExitDown`（左摇杆任意方向穿透）；D-pad 走 `onKeyEvent` 路径仅判 `navigationEnabled` → INPUT 态原生 KeyEvent 落到输入框=**移动文本光标**（让位 IME），此分工经用户明确。底部提示栏 INPUT 态加 `←→ 移动光标` 提示（BottomHintBar 规则4 唯一例外）。
- **搜索页 UI 去冗余（07-26）**：移除列表项聚焦态 Y 键位浮块（与底栏「Y 输入」冲突且搜索页 Y 非收藏）、移除计数标题栏 header 焦点节点（首项 UP 不再回 INPUT，INPUT 仅 Y 激活）；`SearchTabContent` 顶部搜索图标恒显 `Icon(Search)`（去与底栏重复的 Y 浮块）。

### 修复与稳定性合集
- **竖屏点按不弹详情抽屉**：竖屏 `AppList` 改用显式 `onOpen` lambda，非游戏走 `onFocus + onOpenPortraitDrawer`（原依赖焦点变化触发，已聚焦项二次点按不触发）。
- **设置页 LT/RT 无法切 Tab**：`MainActivity` 的 `viewModel.settingsOpen.collect{}` 原把 `gamepad.passthrough` 置 true 导致 `GamepadManager` 直接 return 不推事件流；改为恒 false（设置页是 HomeScreen 内焦点页，全部由事件循环驱动）。
- **进入其它应用重置到首页**：Manifest 移除 `stateNotNeeded`、新增 `alwaysRetainTaskState="true"`；`SettingsRepository` 新增 `lastMainTab`（持久化，resetAll 不重置），`HomeScreen.currentTab` 初始化恢复上次 Tab。
- **启动后不保留进程开关**：新增「启动游戏后不保留 Reverie 进程」（默认关），开启后 `launchApp` 成功即 `finishAndRemoveTask()`；GAME_SETTINGS 入口先尝试游戏自身 `CATEGORY_PREFERENCE`，否则回退系统应用信息页；合并卸载/强停为顶栏「应用信息」（`ACTION_APPLICATION_DETAILS_SETTINGS`）。
- **GamePage 编译阻断修复**：`EdgeToEdge.kt` 的 `OnBackInvokedCallback`/`OnBackInvokedDispatcher` 改为平台类型 `android.window.*`（API 33+），修正 `androidx.activity:activity:1.10.0` AAR 未暴露该类型。
- **上次游玩显示陈旧（「8 天前」）根治**：`queryLastUsed` 由 `firstOrNull` 命中最旧时间桶，改为 `filter{pkg}.maxOfOrNull{lastTimeUsed}` 取真实最近一次（UsageStats 同包多桶根因）。
- **权限/同步审计**：`UsageStats.hasUsagePermission` 改 `AppOpsManager` 检查；同步期限保留（不短于合理窗口）。
- **自定义图标裁剪**：`cropTransparentEdges` alpha 包围盒裁剪；未知应用占位图标。
- **邮件/微信/QQ 启动**：系统 Intent 跳转；首页返回二次确认气泡（「再次返回退出 Reverie」）。
- **构建环境约定（重要）**：离线编译遇 `Could not reserve enough space in CodeHeap` 先清 `_JAVA_OPTIONS=''`；JDK 路径 `jdk-17.0.25.6-hotspot`（注：07-19 实测为 `jdk-17.0.25.6-hotspot`）、内存紧张时 `JAVA_TOOL_OPTIONS=-Xmx1536m -XX:MaxMetaspaceSize=512m` 先 `gradlew --stop`。

### Bug 修复补充（07-19~07-26 遗漏项）
- **游戏页焦点框锁死（07-19）**：`GamePage` 焦点框不随手柄插拔解锁，根因 = `collectAsState` 漏订阅 + `focused` 标志未同步解锁；改为 `hasFocus` + `onFocusChanged` 单一驱动（见「手柄交互归一化」）。
- **截图二级子页「滚到底/半途卡住全部刷新」（07-24）**：`ScreenshotWaterfallTile` 的 `LaunchedEffect` 把 `skipEntrance`(=`isScrollInProgress`) 列为依赖，滚动停止瞬间标志翻转使所有留视口项同时重放。修复：依赖改为仅 `shouldRender`（`skipEntrance` 仅 effect 内读值）+ `if(appeared) return` 锁存；父级用 `derivedStateOf` 派生 `visibleRange` 替代逐帧全量扫描。
- **覆盖导入遗留 bg.png 导致游戏页不替换（07-24）**：`FileGameArtProvider.load` 的 `bg = PAGE_BG_RAW ?: bg.png` 让 demo 遗留的 `bg.png`(hero 副本) 始终作背景回退，覆盖导入不清旧产物→背景不更新。修复：互认改读时回退 `PAGE_BG_RAW ?: bg.png ?: LIBRARY_HERO`；`ExtractAssetsUseCase` 新增 `cleanupStaleArtifacts` 在下载完成后清孤儿文件（保留 `meta.json` 与真实美术种类）。
- **美术输入框色条穿透（07-24）**：`SteamArtHubActivity` 两处 `OutlinedTextField` 未设 `focusedIndicatorColor` → 默认回退 `scheme.primary` 穿透（全仓库 grep 0 命中）。统一补 `focusedIndicatorColor=accent` / `unfocused/disabled=SurfaceTokens.mutedOnSurface(Faint)`，彻底隔离 primary/莫奈。
- **Widget 跨游戏串扰（07-25）**：`rebuildWidgetSlots`/`refreshScreenshotState` 实际只在 `onCreate` 对初始包调用，`onSwitchGame` 走 `applyFromCache/loadGame` 均不重建；且切游戏 `LaunchedEffect(pkg)` 内仅写了 debug overlay 的 sharedprefs、rebuild/refresh 调用丢失（此前「Widget 中途消失」修复静默回归）。本次在 `LaunchedEffect` 内补回 `delay(GAME_SWITCH_THROTTLE_MS); rebuildWidgetSlots(pkg); refreshScreenshotState(pkg)`。
- **游戏页入场割裂 / 截图子页黑屏（07-25）**：`GamePageActivity` 原在 `AnimatedVisibility` 内插 `Box` 致整页割裂 → 背景层提升为独立 `AnimatedContent`（`MotionMachines.crossfade()` 复用）+ 内容层保留原 `pageSwitch`，背景 `accent` 兜底 `primary`；`GameScreenshotsAllActivity` 加首帧占位纯色（= `background_dark`）+ `LandscapeTheme` 包裹（此前无 MaterialTheme 致黑屏）。
- **SteamDB 提取链路「门控竞态漏抓」（07-26，严重）**：① `onLinks` 首个非空即 `stopPolling+stopLoading`（过早停轮询）；② `onAdultGate(true)` 原未取消已调度的防抖(1.2s)/硬上限(3s)计时器 → 1.2s 后以「非门控残缺链接」点火，门控资产（`page_bg_raw`/`hero_capsule`/`logo` 等 `revealAdultContent` 后才注入 DOM）永不被扫到 → 只能靠商店 API 兜底（表象「探针未生效」）。修复：轮询延后到 `state` 进入 `Downloading/Done/Error` 才停；`onAdultGate(true)` 取消过早计时器 + `scheduleExtractionAfterGate`(GATE_CLEAR_DEBOUNCE_MS=3s) 以完整累积链接点火。
- **提取下载性能（07-24）**：`AssetDownloader.download` 由串行单连接改为**按主机分组 + 组内 keep-alive 复用 + 组间有界并行（Semaphore=4）**，整体耗时由 Σ(握手+传输) 降为 max(各主机链路)；`CfWebViewClient.getJs()` 的 ~200 行探针改为 `private val JS_PROBE` 只构建一次（消除每轮询重建大字符串）；首个非空链接即停轮询释放 CPU/带宽给下载器。
- **截图二级页进入 ~0.5s 纯色占位延迟（07-26）**：原走「渲染→逐帧异步填充」阶梯累计 3-5 帧占位。对齐 GamePage 预载模式：`onCreate` 内单个 `lifecycleScope.launch(Dispatchers.IO)` 一次性预载 `app→art→accent→背景位图`，`setContent` 门控 `preloadState`（null→占位、非 null→一帧全量渲染）；`GamePageLayouts` 补缺失 `import kotlinx.coroutines.launch`（否则阻断全量编译）。

### 修复与稳定性补充（07-26 当日追加）
- **二级子页背景跨页零延迟继承（07-26）**：新增进程内 `gameArtCache = LruCache(16)` + 同步 `peekGameArt`/`peekArtBitmap`（无 IO），所有美术/截图子页首帧即命中缓存显示完整背景（位图也命中 `artBitmapCache`），进程重建才走异步补帧 + accent 渐变兜底；`GameScreenshotsAllActivity` 完全镜像 `SteamArtHubActivity` 范式（SteamArtContracts 透传 LABEL/ACCENT + 同步读 + 立即全屏渲染无占位 + 屏内异步 load + accent 渐变回退），废除自定义 handoff/占位盒/预载协程（此前两度偏离均引入包名泄漏与状态栏重叠 bug，沉淀铁律「二级子页必须照搬 SteamArtHub 范式」）。
- **Widget 渐入渐出 + 缓存复用（07-26）**：`MotionSpec.WidgetFade`(300ms) + `MotionMachines.widgetFade(reduceMotion)` 沉淀（先查全局动画机无同效，未散落一次性动画）；`AnimatedContent` 的 `targetState = widgetVersion to widgetLoading` 覆盖切游戏重建 + 加载→内容两条过渡，转圈 `WIDGET_LOADING_FALLBACK_MS` 改为 300ms。「加载键错位」真·性能 bug：原 `onWidgetRefreshed(宿主包名)` 与 `widgetLoading` 键（游戏包名）错配→切回空转 ~1.5s 转圈；改为传游戏包名 + `widgetHostViewCache` 键「游戏包|宿主包」复用 HostView（切走不销毁、切回秒显上次状态、零重建零转圈）。Widget 进页才 `rebuildWidgetSlots`（LaunchedEffect(pkg)），加载期间转圈，首次刷新信号 `RefreshAwareAppWidgetHostView.updateAppWidget` 回调结束加载态。
- **子页返回键半透明框修复（07-26）**：`SubPageBackButton`/`TitleBarIconButton` 的 `backgroundColor` 默认 `if(isFocused) focusBgStrong else Transparent` 未与 `LocalShowFocusIndicators` 做与运算→未连手柄时焦点状态机 focusIndex=0 仍常驻半透明底；改为 `if (isFocused && LocalShowFocusIndicators.current) SurfaceTokens.focusBgStrong() else Transparent` 全子页统一。
- **折叠 banner 到顶弹回失效修复（07-26）**：`GamePageLayouts` 的 `onActiveListState` 两处（Portrait/Landscape）原只透传未回写内部 `bodyListState`→`atTop` 恒 false→`animateTo(0f)` 永不触发；改为 `{ st -> bodyListState = st; onActiveListState(st) }` 既捕获又上报。
- **Widget 首屏/切游戏不刷新（startListening 提前，07-26）**：根因 = `WidgetHostManager.createView()`（rebuild 触发）向 Provider 请求 `ACTION_APPWIDGET_UPDATE`，但 `startListening()` 原在 `onStart` 调用、晚于 `onCreate` 的 `rebuildWidgetSlots()` → 创建 View 时宿主未监听、初始更新广播丢失 → Widget 停留默认空态。修复：`onCreate` 中 `startListening()` 提到 `rebuildWidgetSlots` 之前、`onStart` 移除（免双重注册 receiver）、新增 `onRestart` 调 `startListening`、`onStop` 保留 `stopListening`（生命周期配对 onCreate/onRestart→start、onStop→stop）。

---

## [1.0.0 (2)] — 2026-07-12

**正式发布版本。** 从 0.1.0 (1) 的「系统桌面 / 游戏大屏」原型，演进为完整的「游戏空间（Game-Space）」横屏启动器，完成 API 36 全量合规、深度手柄化、统计页、设置页重构、动效系统统一、启动性能优化与多轮崩溃修复。

### 项目定位与架构
- 由「系统桌面」方向转为纯「游戏空间」应用（移除 `HOME`/`DEFAULT` 桌面候选、桌面角色请求），通过图标启动即进入游戏空间。
- 技术栈：Kotlin + Jetpack Compose（BOM 2025.06.00，Material 3），`minSdk`/`targetSdk`/`compileSdk` 全部锁定 **API 36 (Android 16)**，包名 `cn.mocabolka.run`。
- 架构：MVVM（`HomeViewModel` + `StateFlow`）+ 自定义手柄抽象层（`GamepadManager` → 领域事件 `GamepadEvent`）+ 数据层（`launcher/`/`compat/`）。

### 手柄交互全新设计
- 完整手柄导航：左摇杆 / 方向键导航，A 启动、B 返回、X 收藏、Y 搜索、LB/RB 切换分类 Tab、LT/RT 切换主页面（主页 / 统计 / 搜索 / 设置）、右摇杆滚动整页、Menu 打开设置、左摇杆按下触发上下文动作（如统计日期选择器）。
- 上下文键位小角标（`GamepadDetector` 检测手柄连接后显示）：分类 Tabs 两侧 LB/RB、收藏按钮 X、设置齿轮 Menu、StatusBar 处 LT/RT 切 Tab 提示、左右栏交界十字浮动提示。
- 右摇杆滚动统一引擎：`applyRightStickScroll` 覆盖全部页面与分栏，按帧积分加速、松手衰减。
- 摇杆体验优化：死区 0.5→0.40、回中 0.35→0.28；长按重复两段式（180ms + 80ms 加速）。
- 手柄检测放宽：`SOURCE_CLASS_JOYSTICK` 掩码、trigger 轴备选 `AXIS_Z`/`AXIS_RZ`、`DPAD_CENTER`/`BUTTON_MODE` 映射。

### 界面与布局
- 横屏单列表主从布局：左 300dp 详情面板（`AppDetailPanel`：图标 / 名称 / 版本 / 启动 / 收藏 / 信息 / 卸载 / 强制停止 / 今日与本周时长），右横排详细列表（`AppListItem`：图标 + 名称 + 副标题 + 上次游玩 + 收藏按钮 + 角标）。
- 21:9 宽屏自适应：宽屏左右分栏，中等屏主列表 + 底部 HUD 浮层（`AnimatedVisibility` + `slideInVertically`）。
- 顶部 `StatusBar`：问候语（按时段早 / 中 / 下午 / 晚）+ 四选一滑块 Tab（主页 / 统计 / 搜索 / 设置）+ 设置齿轮。
- 搜索独立 Tab（`SearchTabContent`）：实时按 label 过滤、相关性排序（前缀 > 标签 > 包名 > 分类）、结果计数标签、Enter 直接启动首项。
- 统计页 `StatsScreen`：日报 / 周报 / 月报 / 年报、24 小时分布、趋势柱状图、周期切换（LB/RB）、日期范围选择器（无数据 / 未来项灰化禁用）。

### 设置页大重构
- `SettingsPage.kt` 重写为单一 `SettingItem` 模型（Section / Switch / Button / Dropdown / Info），全行可聚焦、可滚动，焦点由 `HomeScreen` 统一驱动（方向键移焦点、LB/RB 翻页 ±3、RightStick 滚动）。
- 下拉项（深色模式 / 强制旋屏 / 排序）点 A 弹居中 Dialog（MD3 `DropdownMenuItem` + 键位导航）。
- 危险操作二次确认（`AlertDialog` 风格 `ConfirmDialog`：清空收藏 / 重置所有设置）。
- 动效项信息弹窗（`InfoDialog`，图标点击 / 长按 / 手柄 X 三路径触发）。
- 弹性上限动态化（`onRowCountChange` 上报实际焦点行数，杜绝越界）。

### 主题与动效系统
- 深色模式四选项：跟随系统 / 浅色 / 深色 / **AMOLED 纯黑**，响应式实时切换（无需 `activity.recreate()`）。
- Material You（Monet）动态取色：API 36 始终可用，从系统壁纸提取色板，异常回退内置色板。
- 全新 `Motion.kt` 动画引擎：`MotionSpec` 统一时长 / 缓动 / 错峰算法（`staggerDelay`）、`rememberPulse` 呼吸、`AnimatedNumberText` 数字滚动、`Modifier.frosted` 玻璃、`Modifier.focusScale` 聚焦缩放、`PulseSpec` 呼吸周期。
- 全量动效一致性专项：Tab 切换 `fadeIn + slideInHorizontally`、列表行错峰入场（滚动中跳过）、弹窗遮罩统一 `ScrimAlpha(0.55f)`、标题统一 `titleLarge`、弹簧统一、× 关闭按钮、入场退场动画、屏蔽遮罩水波纹、`reduceMotion` 全链路守卫。

### 功能实现
- 应用枚举：`PackageManager.queryIntentActivities` 不依赖默认桌面身份；未授权时显示「前往授权」可聚焦按钮。
- 收藏Dock、最近游玩（Recents，UsageStats 驱动）、通知角标（`NotificationListenerService`，可选）。
- 应用使用时长统计 + 日报 / 周报 / 月报 / 年报（`UsageStatsRepository` 增量同步，`usage_stats.json` 持久化）。
- 强制横屏：`systemExempted` 前台服务（`OrientationLockService`）+ 透明 `TYPE_APPLICATION_OVERLAY` 约束系统方向，8 种方向模式；缺悬浮窗权限引导。
- 穿透背景显示系统壁纸、挖孔屏适配（`windowInsetsPadding(displayCutout)`，OPPO / 华为 ROM 特殊短边处理）。
- 高刷新率：resume 请求峰值刷新率、idle / pause 回落平衡率。
- 触觉反馈、节能模式（应用内开关，文案明确区分系统省电）。
- 兼容向导 `CompatGuideActivity`：首次启动全屏向导，5 项 OEM 权限引导、完成进度追踪、完整手柄适配、独立沉浸状态栏。
- 关于页 / 开源许可页：完全手柄导航、MD3 入场动画、沉浸状态栏。

### 启动与性能优化
- 自制定制 `SplashOverlay`（移除原生 SplashScreen 双开屏）：沉浸式全屏 + 三圆点呼吸加载动画 + `reduceMotion` 静态模式。
- 增量快速启动（Fast Boot）：`AppCache` 缓存激活、最近游玩并行延迟加载、合并两次 `loadApps()` 为一次、移除冗余 30 天 `queryUsageStats`。
- 图标磁盘缓存 `IconCache`：`filesDir/icons/{pkg}.png`，二次启动从 5s 降至 ~1-2s。
- 增量监听：`LauncherApps.Callback` 单包增删改（`incrementalAdd/Remove/Update`），失败回退全量刷新。
- 懒加载 `UsageStatsRepository`（`ensureLoaded()` 替代构造期阻塞）。
- 包体优化：R8 minify + 资源裁剪 + `arm64-v8a` 单 ABI + 删除无用 mipmap PNG（约 -724KB）+ 图标 PNG→WebP（258KB→20KB），APK 从 ~57MB 降至 ~30-41MB。

### 崩溃与稳定性修复（debug 流程）
- **启动期崩溃**：`LauncherApps` 服务在「非默认桌面 + 未授权」时返回 null，全部改为 `as? LauncherApps` 安全降级；启动崩溃 5 项运行时风险消除（协程 `runCatching` 包裹、`_realReady` finally 必置 true、3 秒超时保护、全局异常处理器避免无限递归死循环）。
- **开屏卡死**：去除 `setKeepOnScreenCondition` 阻塞，改为 `installSplashScreen()` 立即关闭、加载态交回 `HomeScreen` 内部 `isBooting` 转圈。
- **Adaptive Icon 崩溃**：`painterResource(R.mipmap.ic_launcher)` 在 Compose 不支持 → 全部改为 `R.drawable.ic_app_logo`（Splash / SettingsPage 关于我 / AboutPage 三处，2026-07-12 修）。
- **前台服务崩溃**：`OrientationLockService` 补 `startForeground` + 低优先级通知渠道（修复 "用一段时间就崩"）。
- **社交类应用崩溃**：`safeIcon` 在 `runCatching` 内部调用 `getIcon(0)`（修复资源损坏 APK 求实参即抛异常）。
- **动态氛围背景非全屏**：移至外层 Box 覆盖状态栏 / 导航栏。
- **搜索 / 设置页 B 键崩溃**：分支缺失与 `freeFocus()` 异常，补明确分支 + `runCatching`。
- **DropdownDialog 手柄失效**：闭包 stale 读取最新 `dropdownSelectedIndex` 修复。
- **状态栏沉浸残留**：`LaunchedEffect` 补 else 分支恢复状态栏。
- **主页 / 搜索页完全隔离**：独立焦点状态 `searchFocusedPackage`，解决焦点黏连与高亮错位。
- **"上次启动" 全显示 "刚刚"**：伪造 `lastUsedTime` 分支删除，新增 `queryLastUsedMap()` 取真实时间。
- **`registerReceiver`**：API 34+ 强制 `RECEIVER_NOT_EXPORTED` 标志（`GamepadDetector` 手柄插拔广播）。
- **AGP 8.x BuildConfig**：`buildFeatures.buildConfig = true` 显式开启（修复 `Unresolved reference 'BuildConfig'`）。

### 编译与合规专项
- 全量 minApi 36 合规确认：无任何 `SDK_INT >= VERSION_CODES.*` 版本守卫、无 `@TargetApi`/`@RequiresApi` 注解；残留 `Build.VERSION.SDK_INT` 仅用于设备信息显示。
- Compose 1.8.2 导入陷阱全部规避（`matchParentSize`→`fillMaxSize`、`Density.toDp`→除法、`weight` 不导入、`scrollBy`/`animateScrollBy` 显式 import、`MenuItemDefaults.colors` 移除、`return@Column` 标签重构等）。
- 删除死代码：`SettingsDialog.kt`（被 `SettingsPage` 替代）、`loadLastUsedMap()`、未用 mipmap。

### 焦点框可见性统一控制（2026-07-12）
- 新增 `ui/theme/FocusIndicators.kt`：定义 `LocalShowFocusIndicators` 全局焦点框开关；`LandscapeTheme` 新增 `gamepadConnected: Boolean? = null` 参数（传入实时值或内部 `GamepadDetector` 流自动计算），通过 `CompositionLocalProvider` 包裹 `MaterialTheme`，使所有界面（含独立 Activity）实时响应手柄插拔。
- 纯触控 / 手柄未连接时不渲染导航焦点框：`AppListItem`/`AppDetailPanel`/`SubPage`/`StatsScreen`/`HomeScreen` 的焦点态绘制点全部与 `LocalShowFocusIndicators.current` 取与；`CategoryTabsRow`（按选中态高亮，非导航焦点）与临时提示气泡边框保持不变。

### 瀑布屏（曲面屏）安全边距（2026-07-12）
- 新增 `ui/theme/Waterfall.kt` 的 `Modifier.waterfallSafePadding()`：读取 `WindowInsets.displayCutout` 左右 inset 与 `Dimens.waterfallSafe` 取 max 作为左右内边距；用 `@Composable fun Modifier.xxx()` 写法（Compose 1.8.2 中 `composed` 不可解析）。
- `Dimens.waterfallSafe` 经多轮视觉校准：16dp → 8dp（对齐系统状态栏边距）→ **6dp（竖屏）**；横屏（`screenWidthDp >= screenHeightDp`）直接短路零开销返回原 Modifier。
- 应用点：HomeScreen 根 Column、AboutPage / LicensesPage / CompatGuideActivity 的 `SubPageScaffold`，全部自动生效。

### 子页面底部固定按钮（2026-07-12）
- 需求9：`SubPageScaffold` 新增 `bottomAction: SubPageRow.Action?`，传入时列表 `LazyColumn` 改 `weight(1f)`、底部渲染固定主按钮（不随滚动），焦点状态机末项 `M+1` 触发；`CompatGuideActivity` 的"完成"按钮由列表末尾项改为底部固定。
- 需求11（技术储备）：新增通用 `bottomBar: (@Composable ColumnScope.() -> Unit)?` 槽（与 `bottomAction` 互斥），供未来任意自定义底部内容复用，不纳入 ↑↓ 焦点状态机。

### 子页面动画与竖屏修复（2026-07-12）
- **About/Licenses 切换无动画（最严重 bug）**：原单个 `AnimatedVisibility(visible = settingsSubPage != null)` 包裹 `when{}`，切 About↔Licenses 时 `settingsSubPage` 始终非 null → 入场/出场永远不触发。改为两个独立 `AnimatedVisibility`（`== ABOUT` / `== LICENSES`），左出右进连贯转场。
- **深色 / AMOLED 适配确认**：主题跟随机制本就正确，动画缺失导致"没适配"的误判；修复动画后正常（AMOLED `background=surface=Black`）。
- **CompatGuide 竖屏真正适配**：原仅控 `contentWidth`，字号/行距/按钮高度全为横屏固定值；新增 `wide` 分支变量（titleLarge→titleMedium、bodySmall→labelSmall、行距 md/sm→sm/xs、按钮 52→48dp 等），宽屏保持原排版。

### AMOLED / 深色模式莫奈背景色板（2026-07-12）
- 三档分治最终方案（经两轮误删莫奈 / 误加黑底撤回）：
  - **浅色**：完整 `dynamicLightColorScheme`（壁纸色板，不覆盖）。
  - **深色**：`baseScheme.copy(background=DarkColorScheme.background, surface=DarkColorScheme.surface, ...)`——**强调色保留莫奈**，基础色回退项目自带深蓝黑 `#0B0F1A`。
  - **AMOLED**：`copy(background=Color.Black, surface=Color.Black, ...)`——强调色保留莫奈，背景纯黑。
- 关键认知：莫奈深色 `background/surface` 是壁纸调深色版（浅紫/浅蓝等），不符合 MD3 深色规范，必须将强调色与基础色分开处理。

### 原生 Splash 大白边修复（需求15，2026-07-12）
- 现象：冷启动出现圆角白色矩形（中心小 logo）。根因：`Theme.Landscape` 父类 `colorBackground` 默认白底；`ic_app_logo` 为普通 webp 非 Adaptive Icon → Android 12+ legacy fallback 渲染产生大白边。
- 修复：接入标准 Adaptive Icon（`ic_assets/` 的 1298×1298 前景/背景 PNG → `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`，foreground/background/monochrome 三图层）；`themes.xml` 拆分 `Theme.Launcher.Starting`（`Theme.SplashScreen` parent + `windowSplashScreenBackground/AnimatedIcon` + `postSplashScreenTheme`）/ `Theme.Launcher` / `Theme.Landscape` 别名；Manifest 设 `android:icon`/`roundIcon`；新增 `androidx.core:core-splashscreen:1.0.1` 依赖。
- 结论：Adaptive Icon 在 API 36 仍必须（避免大白边 + Themed Icons 覆盖错乱）；`ic_app_logo.webp` 仍供应用内 Compose 绘制（`SplashOverlay`/`AboutPage`），不动。

### 关于 / 许可子页面强制跟随主题（2026-07-12）
- 问题：About/Licenses 呈现默认浅色蓝紫，不随浅色 / 深色 / AMOLED / 莫奈切换。
- 修复：`Theme.kt` 提取 `reverieColorScheme(darkTheme, useMonet, isAmoled)`；子页面新增 `darkMode`/`useMonet` 参数，内部用 `reverieColorScheme` 包裹独立 `MaterialTheme`（保留外层 Typography/Shapes）；`HomeScreen` 调用时传入。

### 品牌
- 应用名 `MRunner` → **Reverie**（哨兵字符串 `__mocabolka_system_aggregate__` → `__reverie_system_aggregate__`）；`strings.xml` `app_name` 已为 Reverie。

### 开屏（Splash）品牌与架构收口（2026-07-13）
- **品牌图接入**：`Theme.Launcher.Starting`（day + night）通过 `android:windowSplashScreenBrandingImage=@drawable/brandtext` 接入 2.5:1 标准品牌图（必须用 `android:` 命名空间，否则报 `attr not found`）。
- **标题栏泄漏修复**：`Theme.SplashScreen` 默认开启 ActionBar，残留 app_name 标题条。4 处主题（`Theme.Launcher.Starting` / `Theme.Launcher`，day + night）统一加 `android:windowActionBar=false` + `android:windowNoTitle=true` + `android:windowFullscreen=true`，杜绝双层标题。
- **`setMinimumVisibleDuration` 编译错误**：androidx core-splashscreen 未公开该 API（并非 API 36 公开签名），删除调用，最短显示时长统一由 `isBooting` 的 `setKeepOnScreenCondition` 控制。
- **Splash 架构最终收口**：确认平台 SplashScreen 在调试环境不生效、用户真机首幕为 OEM 桌面动画，`setOnExitAnimationListener` 永不触发 → 整条"幕接幕"为死链。最终方案：`MainActivity` 仅保留 `installSplashScreen()`，移除 `setKeepOnScreenCondition`/`setMinimumVisibleDuration`/`setOnExitAnimationListener` 三行依赖；由 `HomeScreen` 的 `LaunchedEffect(isBooting)`（数据就绪翻转）驱动第二幕 `SplashOverlay`；原生 Splash 背景对齐 `@color/splash_bg`（#0B0F1A）；原生静态 XML 色无法引用莫奈动态色（已如实记录，不假装对齐）。

### 默认配色体系 BrandColors（2026-07-13）
- 新增 M3 紫罗兰调色板作为 App 默认配色：`Theme.kt` 新增 `object BrandColors { Light / Dark }` 映射为标准 `LightColorScheme`/`DarkColorScheme`，补全 M3 键（`inverseSurface`/`inverseOnSurface`/`surfaceTint`/`scrim`/`surfaceContainerHighest`）避免运行时缺键。
- 莫奈取色保持现状：`BrandColors` 仅作为 `MonetTheme` 的 `fallbackLight`/`fallbackDark` 回退色板，莫奈优先级与逻辑不变。
- XML 同步：`colors.xml` 的 `splash_bg`/`background_dark` 由霓虹深蓝黑（#0B0F1A）改为紫罗兰深色（#1C1B1F），`surface_dark`/`primary_dark` 等对齐紫罗兰深色。

### 手柄交互大重构：跨栏逻辑移除（2026-07-13）
- 跨栏语义整体移除：LS/RS 按压改为"页面内动作键"（主页列表态进详情态），不再跨栏；键盘 Enter/Tab/Esc 跨栏全部移除；底部栏 LS/RS "切换栏" 提示移除，改 A 详情 / X 启动 / B 返回。
- 主页导航重构为列表态 / 详情态二元状态机：列表态 A 进详情、X 直接启动聚焦应用、Y 收藏；详情态 A 触发当前按钮（首发=启动）、B 退回列表态；详情态左右键在同行按钮（收藏⇄信息）间切换焦点，上下改为 clamp 夹边界（消除启动钮上拨跳到强停的反直觉），不再取模循环。
- 主页详情焦点框 + A 浮块跟随：5 处 A 浮块改为 `effectiveFocus==N` 焦点跟随（仅聚焦该钮才显，非常驻）；竖屏 `PortraitDetailOverlay` 同步 `focusedButtonIndex`（修复竖屏浮层无焦点框 / A 跟随）。
- 统计页左右摇杆分栏：宽屏左栏=排行（左摇杆焦点）、右栏=总览总计（右摇杆滚动），二者独立；LB/RB 移至右栏右上角与日期按钮交换位置；周期切 LB/RB、日期开 View 键 / 点击；统计焦点不再控制日周月年滑块 / 日期按钮（移除 `leftFocused`/`leftFocusIndex`）。
- 搜索页手柄逻辑重写：移除 TextField 多余 `.focusable()` 冲突；`LaunchedEffect(inputActive)` 真正 `requestFocus + showSoftInput` 修复"呼出软键盘但搜索框未激活打不了字"；默认进入搜索页即 INPUT 态。

### 统一按钮库 Buttons.kt（MD3 规范，2026-07-13）
- 新增 `ui/theme/Buttons.kt` 统一按钮库：5 类 MD3 标准按钮（`ReverieFilledButton`/`ReverieTonalButton`/`ReverieOutlinedButton`/`ReverieTextButton`/`ReverieIconButton`，`danger=true` 用 error 色）+ 自绘 `ReverieSegmentedRow<T>` 分段控件（官方 `SegmentedButton` 不可用）；`wrapFocusBorder` 工具统一手柄焦点边框；统一 `40.dp` 高 / `shapes.medium` 圆角。
- **关键校正**：项目 material3 实为 **1.3.2**（非此前误记为 1.8.2），官方 `SegmentedButton`/`SingleChoiceSegmentedButtonRow` 不可用，须自绘等价组件。
- 应用范围：AppDetailPanel（收藏 / 信息 / 卸载 / 强停）、HomeScreen（空列表 / DetailHud / 分类 tab 选中态）、SettingsPage（确认 / 取消 / 关闭 / 信息按钮）、SubPage（底部主按钮）、StatsScreen（周期 tab→`ReverieSegmentedRow`，删除手搓 `PeriodTabsRow`）、CompatGuideActivity、DateIndicatorButton。
- **DateIndicatorButton MD3 重写（R8，修复"小灰点"bug）**：根因为被 SegmentedRow 同行 weight 挤压成 4px 小灰点 + 括号错位让 `GamepadVisible` 误包裹整按钮。改用 `ReverieTonalButton` + `Icons.Filled.CalendarMonth`、label 周期感知、`wrapContentWidth(unbounded=true).defaultMinSize(120.dp)` 防挤压、View 提示 chip 仅浮右上角、`wrapFocusBorder` 守卫焦点态。编译零错误零警告。

### 右摇杆归一化引擎 + 详情焦点视觉归一（2026-07-13）
- 右摇杆归一化：所有 `RightStick` 事件统一只记 `rightStickY` + 时间戳，由 `applyRightStickScroll` 按 `currentDialogLayer`/`currentTab` 分发到对应滚动目标（主页列表 / 详情 / 统计右栏 / 搜索 / 设置 / dialog），消除各 dialog 旧固定步长双重滚动；二段式 grow 加速曲线（越滚越快，封顶 12000）。
- 详情栏焦点视觉归一：移除 `FocusGlowBox` 的 blur 脉冲光晕（"丑"根因），改为 `ReverieOutlinedButton.focused` 清晰描边主导；`LaunchCircleButton` 重做干净 MD3 焦点环（1.06x + 0.16f 主题色光环，无抖动）。删除死代码 `FocusGlowBox`。
- 右摇杆引擎真实时间驱动：改用真实帧间隔 `dt` 物理积分（替代写死 1/144f），在 60/120/144Hz 表现一致。
- 底部提示栏键位语义补全：`GamepadBottomHintBar` 按 `focusSide` 分态（列表态 `A详情/X启动/Y收藏/B返回`；详情态 `A启动/摇杆移动/B返回`），补全 Y 收藏提示；`LaunchCircleButton` A 浮块未安装时不显。
- 竖屏浮层 B 返回焦点态同步：收起浮层时同步 `focusSide=LEFT_LIST`，避免焦点框 / A 浮块残留。

### 统计页周视图恢复 + Dialog 归一化（2026-07-14）
- 恢复周视图：`selectableStatsPeriods` 重新包含 `WEEKLY`（日 / 周 / 月 / 年四档），`NativeDatePickerDialog` 不再被 WEEKLY 触发。
- WEEKLY 不弹选择器：`DateIndicatorButton` 重构为纯文本 + 图标行（去 tonal 背景 / 按压态），`period==WEEKLY` 时 `clickable(enabled=false)` + muted 文字 / 图标；底部 `Hint(VIEW,"日期")` 仅在 `activePeriod != WEEKLY` 显示。
- 所有 dialog 底部提示栏归一到屏幕底部：DropdownDialog / InfoDialog / ConfirmDialog / NativeDatePickerDialog 的 `GamepadBottomHintBar` 由卡片内移出，改为 scrim Box 内 `align(BottomCenter)` 固定底栏；卡片 `heightIn(max = maxHeight - HintBarHeight)` 预留；scrim 全量改引 `SurfaceTokens.scrim()`。
- InfoDialog 右摇杆滚动修复：新增 `scrollState` 参数，SETTINGS_INFO 分支由 `return` 改为 `scrollBy(delta)`，修复右摇杆误控背景设置页。
- 主页详情（右栏）底部提示栏移除 `LS` 项（焦点移动靠左摇杆，无需重复提示）。

### 全局表面令牌归一化 SurfaceTokens（2026-07-14）
- 通查发现大量散落 alpha 魔法值且不一致（`Theme.scrim` 0.6 与 `MotionSpec.ScrimAlpha` 0.55 冲突、焦点背景 0.10/0.12/.../0.25、surfaceVariant 0.30~0.95、onSurfaceVariant 0.3~0.8、收藏星标金色 `0xFFFFD54F` 等重复硬编码）。
- 新增 `ui/theme/SurfaceTokens.kt` 全局令牌层：`focusBg`/`focusBgStrong`/`pressBg`/`cardSurface(level)`/`scrim()`（引 `MotionSpec.ScrimAlpha`）/`mutedOnSurface(level)`/`restingBorderColor`/`glassBg`/`iconTonalBg`/`segmentContainer` + 可复用 `SurfaceCard`/`ReverieToast`/`AccentIconSquare`。
- 派发至 AppTile / AppListItem / StatusBar / AppDetailPanel / SubPage / SettingsPage / StatsScreen / Dock / RevealOverlay / KeyBadge / Buttons / HomeScreen / Theme 共 10+ 文件，焦点背景 / 卡片 / 遮罩 / 静音文字 / Toast 一律从 `SurfaceTokens` 取，改一处全站生效。

### 详情面板隐藏「卸载 / 强制停止」（暂时，2026-07-14）
- 原因：当前"卸载"仅跳系统 `ACTION_DELETE`、"强制停止"仅跳"应用信息"，无法在 App 内直接完成（缺系统级权限），先隐藏避免误导。
- `AppDetailPanel` 注释隐藏卸载 (index3) / 强停 (index4)，`DETAIL_BUTTON_COUNT` 5→3，焦点上界收窄到「启动 / 收藏 / 信息」；`onUninstall`/`onForceStop` 签名保留。恢复方式：改回 `DETAIL_BUTTON_COUNT=5` + 取消注释即可。

### 焦点滚动归一化 + 详情面板焦点跟随（2026-07-14）
- 新增 `ui/components/FocusScroll.kt`：`FocusScroller` + `rememberFocusScroller` + `focusScrollerContainer`/`focusScrollerItem`，归一化"焦点 index 变化 → 滚动到可见"（普通 ScrollState 容器）。
- AppDetailPanel 接入：根 Column 加容器、5 个按钮加 item、`LaunchedEffect(focusedButtonIndex){ bringIntoView(it) }`，横屏右栏 / 竖屏浮层共用同面板，修复"焦点移出窗口不可见"。
- 统计页左栏排行同类 bug 修复：新增 `LaunchedEffect(statsFocusIndex){ statsListState.animateScrollToItem(it) }`。
- 经验：手柄 focus index 驱动的普通滚动容器必须在 index 变化时主动滚动到可见，否则焦点随 index 增长移出窗口（LazyList 的 `animateScrollToItem` 习惯让人误以为会自动跟随）。

### 右摇杆滚动引擎独立 + 焦点跟随全覆盖（2026-07-14）
- 新建 `gamepad/RightStickScrollEngine.kt`：`RightStickScroll` 常量 + `RightStickScrollPhysics` 逐帧物理积分，取代原本 HomeScreen / SubPageGamepad 两套分散参数，调参仅改一处。
- `SubPageGamepad` 重写复用同物理参数；`SubPageScaffold` 新增 `rightStickFirstVisible` 把首可视物理项映射到可聚焦焦点行（`suppressAutoScroll` 防反向抖动），焦点跟随普及到 About / Licenses / CompatGuide 三子页面。
- 主页焦点跟随失效根因修复：主页主列表 `LaunchedEffect(focusedPackage)` 因 focusedPackage 变化立刻 `animateScrollToItem` 抵消右摇杆滚动，新增 `suppressListAutoScroll` 抑制标志修复（搜索页 / 设置页无此反向 effect 故本就正常）。
- 越滚越快强化：MAX_SPEED / ACCEL / GROW_* 常量上调（GROW_MAX 12000→20000，长列表长按更高终速）。

### 焦点框归一化 + 手柄未连接隐藏（2026-07-14）
- 审计发现 `wrapFocusBorder` / ReverieIconButton / AppTile / AppDetailPanel / StatsScreen 排名行 / HomeScreen 详情容器此前未接 `LocalShowFocusIndicators` 守卫。
- 新增 `Modifier.focusBorder(enabled,color,shape)`（`FocusIndicators.kt`，内部 `&& LocalShowFocusIndicators.current`）为唯一绘制入口；新增 `Dimens.FocusSurfaceAlpha=0.20f` 焦点底色令牌。
- 全场景接入守卫并归一线宽 / 底色：AppListItem (2.5dp→3dp、0.22→0.20)、AppDetailPanel (A 浮块改用响应式 `LocalShowFocusIndicators.current` 响应热插拔)、LaunchCircleButton 光晕 0.16→FocusSurfaceAlpha、SubPage Row / Card、StatsScreen 排名行、HomeScreen 详情容器等 8 文件。

### 统计页日期选择器：自绘 → 原生 Material3 重构（2026-07-14）
- 日 / 月 / 年视图改用原生 `DatePicker`/`DateRangePicker`（material3 1.3.2 可用），周视图隐藏独立入口（保留 WEEKLY 枚举供内部周聚合，选日即按 `weekStartCalendar` 落周）。
- 新增 `NativeDatePickerDialog` + `truncToPeriod` 适配器；删除自绘 `DateRangePickerDialog` 及 buildPickerModel / shiftViewingMs / formatPickerTitle / PickerItem / VerticalGridScrollbar 等 ~400 行死代码。
- 关键适配：项目手柄事件走 GamepadManager 协程流、原生 Compose Focus 收不到 DPAD，须由 `StatsDatePickerBridge` 显式桥接驱动原生 state（方向键 ±1 / ±7 天、LB/RB 翻月 / 翻年、A 确认、B 取消）；`SelectableDates` 注入未来日禁用。`assembleDebug` 通过。

### 首页 AXY 无响应根因修复（2026-07-14，致命）
- 现象：首页 HOME 列表态 A（进详情）/ X（启动）/ Y（收藏）/ LS / RS 按压全部无反应，搜索页正常。
- 根因：`HomeScreen.kt:231` 的 `val focusedApp = apps.find {...}` 被 `LaunchedEffect(Unit)` 内 `collectLatest` 协程闭包按值捕获为首次组合快照（初始 null）→ 列表态所有 AXY 分支守卫 `focusedApp != null` 永远跳过。搜索页用 MutableState `searchFocusedPackage`（闭包内读最新）故正常。
- 修复：在 `collectLatest` lambda 内即时计算 `liveFocusedApp`，Select / Favorite / LeftStickPress / RightStickPress / Search(HOME) 分支改用之。`read_lints` 0。
- 经验：`LaunchedEffect(Unit)+collectLatest` 这类"只创建一次的协程"内禁止按值捕获普通 `val` 作事件依据，须读 StateFlow / State 或即时重算。

### 构建 lint 警告修复（2026-07-14）
- `BoxWithConstraints scope is not used`：定位 HomeScreen:1138 / StatsScreen:266 两处仅依赖 `isWideScreen`（由 `LocalConfiguration` 派生、旋转自动重组），改为普通 `Box` 消除警告。
- `WallpaperManager.getDrawable MissingPermission`：读取系统壁纸所需 `READ_WALLPAPER_INTERNAL` 为系统签名权限、普通 app 不可得；正确做法 `@SuppressLint("MissingPermission")` 抑制（launcher 作壁纸宿主运行时可正常访问 + `runCatching` 兜底），不应在 manifest 声明重权限。

### 搜索页底部提示栏合并 B/Y（2026-07-14）
- `SearchTabContent` 新增 `keyboardVisible` 参数；INPUT 态 + 软键盘隐藏时 B（退输入）与 Y（退出输入）指向同一事件 ⇒ 合并为 `Hint("B/Y","退出输入")`；INPUT + 键盘可见 B 仅收键盘、Y 退出输入（分开）；LIST 态保持 Y 搜索 / B 返回。

### 底部按键提示栏间距归一 + 手柄交互收尾（2026-07-14）
- `KeyBadge.kt` 新增 `Dimens.HintBadgeGap=4.dp`（键位浮块→文本固定间隙）以 `Spacer` 内嵌于 `Hint`，与行间 `Dimens.HintItemGap=6dp`（`Arrangement.spacedBy` 控制）分离，两者不再叠加；修复此前各栏键位贴死、间距不齐（需求#5）。
- 列表项收藏浮块 X→Y 修复：`AppListItem.kt:319` 原收藏浮块键位提示使用 X（与 X=直接启动冲突），改为 Y，与"Y=收藏"语义统一。
- `syncRightStickFocus` 扩展至 SETTINGS 分支：右摇杆滚设置列表后同步 `settingsFocusRow` 到首可视项，至此右摇杆焦点跟随在全部列表型页面（HOME/SEARCH/STATS/SETTINGS）归一。

### 扫描 Toast 全屏铺满修复 + 全局 Toast 归一化（2026-07-14）
- **根因**：`SurfaceTokens.kt` 的 `ReverieToast` 给 `AnimatedVisibility` 挂了 `Modifier.fillMaxSize()`，导致占位 slot 撑满全屏；`slideInVertically { it/3 }` 的 `it` = 全屏高度，气泡从屏幕中段滑入，强化"全屏动画"错觉。
- **修复**：`AnimatedVisibility` 仅按内容尺寸渲染，`fillMaxSize` 下沉至内层 `Box`（+ `BottomCenter` 对齐），`Surface` 改用 M3 `Surface` 组件自带阴影；新增 `bottomOffset` / `shadowElevation` 参数。
- **touchExitConfirm 归一化**：`HomeScreen.kt` 独立实现（"再次返回退出 Reverie" 气泡）整段替换为 `ReverieToast`，同一份动画 / 外观 / 底部偏移。
- **清理**：删除 `HomeScreen.kt` 中已无人引用的 `import border` / `import Surface`。

### ConfirmDialog 警告框未绝对居中修复（2026-07-14）
- 症状：设置页"清空全部收藏"/"重置所有设置"等危险操作二次确认卡片锚定在屏幕左上角（非正中）。
- 根因：`SettingsPage.kt:1288` 的 `Surface` 卡片在 scrim `Box` 内未挂 `.align(Alignment.Center)`，默认 `TopStart`。同一文件 `InfoDialog` / `DropdownDialog` 正确居中，仅本 dialog 漏写。
- 修复：`Surface` 修饰符链首加 `.align(Alignment.Center)`。
- 经验：scrim Box 内所有居中展示的卡片须显式 `.align(Alignment.Center)`——`BoxScope` 默认 `TopStart`，无 align 即不居中。

### IDE Problems 四批系统化修复 — HomeScreen.kt 清零（2026-07-14）
- 从 Android Studio Problems 面板 **82+ 问题** 经 4 轮定点修复清零（read_lints 0 诊断）：
  - 删除 16 个未使用 import（`CircularProgressIndicator` / `LinearProgressIndicator` / `OutlinedTextField` / `draw.shadow` / `graphicsLayer` / `IntOffset` / `layout.offset` / `kotlin.math.{abs,sign,roundToInt}` / `toBitmap` / `HintBar` / `BottomHintBar` 等）
  - **DetailHud 整函数删除（76 行）**：`findstr DetailHud(` 全文件 0 调用，级联消除 7 个未用形参 + `Image` import
  - **FQN 全面短名化（20+ 处）**：`delay`→`Duration`（3 处）、`OrientationManager` / `CompatGuideActivity` / `AboutActivity` / `UsageStatsPermissionHelper` / `SearchFocusTarget` / `ConfirmDialog` / `InfoDialog` / `DropdownDialog` / `SettingsControlBridge` / `LazyListState` / `ScrollState` / `rememberScrollState` / `mutableStateOf` 等全路径→短名；补充缺失 `cn.mocabolka.run.ui.OrientationMode` import
  - **死形参 + 调用方同步删除**：`HomeTabContent` 删 `reduceMotion`/`glassSurface`；`SearchTabContent` 删 `emptyHint`/`reduceMotion`；`SettingsTabContent` 删 `powerSave`/`onPowerSaveChange`/`viewModel`
  - 删除未用局部 val：`filterChips`（189）、`contentWidth`（2342）、`searchFocusRequester`（230）
  - `BoxWithConstraints` scope 误判修复 2 处：1629 删除中间 val 让 lint 直接看到 `maxHeight` 被使用；2305 真正无消费者时 `BoxWithConstraints` → `Box`
- **保留为 IDE 误报**：`BoxWithConstraints scope:1650/2428`（实际使用 FQN 但 lint 缓存误判，Invalidate Caches 后消失）；`mutableStateOf(0f)/(0L)/(false)` 语义不可改

### 初始化流程全面调整（2026-07-14）
- **HomeViewModel**：`init{}` 添加 `forceReady()` 超时保护（12s），防止 `refresh()` 协程被取消后永久卡在开屏；`events` SharedFlow 新增 `replay=1`，确保迟到收集器不丢事件；`refresh()` 添加 `pendingRefresh` 排队机制，当前刷新进行中时 `rescan()` 不再静默丢弃，等待完成后自动触发新一轮；阶段一缓存加载后即预置初始焦点（基于缓存活跃应用），避免列表渲染后无焦点项。
- **MainActivity**：`settingsOpen` 收集器修复（`passthrough = open`，非设置页时恢复 `true` 让系统处理 IME / 返回手势）；移除 `maybeShowCompatGuide()` 首次启动自动弹出与 `requestOverlayForOrientation()` 调用。
- **HomeScreen**：`AppList` 新增 `isScanning` 参数，扫描中且列表为空时显示「正在扫描…」+ 进度条动画，避免空状态文字闪烁；3 个 AppList 调用点 + 搜索列表调用点全部传入 `isScanning`。

### Dialog 全局归一化整合（2026-07-14）
- 新建 `ui/components/DialogScaffold.kt`：统一全屏遮罩(scrim) + 居中卡片 + 底部 HintBar(可选) 模板，4 个 dialog 全部接入：
  - `DropdownDialog`：原 Surface 缺少 `.align(Center)` → 竖屏 TopStart 修复（DialogScaffold 内建 `Box(contentAlignment=Center)`）。
  - `ConfirmDialog`：移除不再需要的 `.align(Center)`（DialogScaffold 自动居中）。
  - `InfoDialog`：保持 `BoxWithConstraints` 用于尺寸计算，`showBottomHint=false`。
  - `NativeDatePickerDialog`：底部 `GamepadBottomHintBar` 此前缺失 `.align(BottomCenter)`，DialogScaffold 统一补齐。
  - 移除无人引用的 `scrimInteractionSource` + `MutableInteractionSource`（StatsScreen）。

### 文档与发布（2026-07-14）
- **README 重写**：根据最新 changelog（覆盖至 07-14）和完整源码，全面重写 `README.md` 为中文版，涵盖双态导航、右摇杆物理引擎、焦点框智能显示、MD3 按钮库、SurfaceTokens、Waterfall、FocusScroll、BrandColors、原生 DatePicker、Adaptive Icon + Splash 收口等 10+ 新模块。版本标记 v1.0.0 (2)。
- **v1.0.0 GitHub Release**：APK 从 `app-release.apk` 更名为 `Reverie-v1.0.0-release.apk`；创建 git tag `v1.0.0` 并推送；通过 GitHub API 创建 Release 并上传 APK asset。Release URL: https://github.com/qtqtEricChiu/Reverie/releases/tag/v1.0.0

---

## [0.1.0 (1)]

初始原型版本。

### 新增
- Android 横屏 Launcher 框架（Kotlin + Jetpack Compose），`minSdk`/`targetSdk` 占位 API 36。
- 手柄抽象层（GamepadManager / XboxMapping / GamepadEvent）、系统桌面 Home Launcher 身份。
- ColorOS / 国产 ROM 兼容层：电池优化、自启动深链、悬浮窗、通知角标、兼容向导 `CompatGuideActivity`。
- 主屏 `HomeScreen` + `AppGrid`/`AppTile`/`Dock`/`RecentsRow`/`StatusBar` + 主题。
- Steam 风格「游戏大屏模式」（Game Mode / Big Picture）：长按 Menu 触发、`GameWhitelist` 游戏识别与置顶。

### 功能性审计修复（v0.1.0 审计报告，评分 4.2→4.2/10 起步）
- **P0 启动 / 禁用 / 列表 / 卸载崩溃**：`isActivityEnabled` 校验、`enabled` 过滤、`LauncherApps.Callback` 自动刷新、未安装兜底、`try-catch` 提示。
- **P1 跨区域焦点策略、QUERY_ALL_PACKAGES 检查、非 ColorOS 引导、壁纸残留清理**。
- **P2 增强**：深色 recreate 闪烁（改为响应式主题）、排序（名称 / 安装时间）、按分类分组、搜索（Y 键 + 实时过滤）、CompatGuide 逐项完成追踪、游戏模式肩键翻页、角标 99+。
- **UI/UX 改进**：深色模式跟随系统、横屏网格行数自适应、壁纸模式、抽屉图标兜底、主动权限引导。

### 编译修复
- 替换不存在的 "Android 16 前瞻 API"（`DisplayRefresh` 帧率、`Haptics` 振动、`LocalWindow` 等）为真实可用 API。
- 本地 SDK 平台命名不匹配：`android-36.1` 拷贝为 `android-36-ext1` 让 AGP 解析。
- 桌面交互调整：屏蔽系统返回键、桌面去时钟 / 去电量（状态栏仅占位）。

### 已知局限（0.1.0）
- 依赖默认桌面身份枚举应用，非默认桌面状态下列表为空（在 1.0.0 通过 `queryIntentActivities` 解决）。
- 桌面 / 应用边界尚未清晰，后续 1.0.0 明确转向纯「游戏空间」应用。

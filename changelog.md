# Changelog

All notable changes to **Reverie** (formerly MRunner) are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and the project adheres to semantic versioning.

---

## [1.2.0 (10200)] — 2026-08-03

重大功能版本。引入四大全新能力——**完整通知历史系统**、**应用内更新（自托管 OTA）**、**手柄输入模式（标准 / 电视 / 掌机）**、**美术缓存分层重构（TieredCache）**，并配套发布工程规范化、统计页重构与多项缺陷修复。

### Added
- **通知历史系统**：新增持久化通知历史（`UsageStatsManager` + `NotificationListenerService` 交叉校验，按应用分片落盘），保存最近通知、按「应用 / 时间」检索，切前台或亮屏时智能刷新以兼顾续航。
- **通知分析界面**：新增通知频率热力图（月 → 日 → 小时三级下钻）、时段分布柱状图、Top 应用排行与峰值识别；主页底部新增「最近通知」卡片（时间轴 + 间隔着色，点按进入历史页）。
- **通知角标**：系统栏新增通知入口与未读角标（`NotificationBadgeService` 监听 POSTED/REMOVED 去重计数），新增通知监听权限引导页。
- **应用内更新（自托管 OTA）**：新增 `UpdateRepository`（`/update.json` 解析 + 超时双保险）与 `UpdateDialog`（版本 / 大小 / 更新说明展示）；启动按策略检查更新（从不 / 每周 / 总是），设置页可配置检查策略；下载后调用系统安装器。
- **手柄输入模式**：新增 `InputMode`（标准 / 电视 / 掌机），按接入设备特征自动预选并支持手动覆盖；电视模式映射轻点确认、掌机模式启用肩键快捷；设置页可切换。
- **美术缓存分层（TieredCache）**：美术资源改为内存 / 磁盘 / 远程三级缓存，内存层同步无 IO 命中以实现跨页零延迟背景；新增智能降级链路（内置兜底 + accent 渐变）。
- **动态快捷方式**：长按图标弹出「启动 / 收藏 / 游戏页」快捷操作（`ShortcutHelper`），复用缓存素材避免重复解码。
- **发布工程**：新增 GitHub Actions release 工作流（tag 触发自动构建并发布 APK 与 `update.json`）、发布流程文档、`.gitignore` 规范化。

### Changed
- **统计页重构**：重写统计页骨架，`AnimatedContent` 包裹「统计 / 通知」两 Tab 避免重建；新增统计概览（总时长 / 日均 / 峰值 + 趋势图 + 分类占比）；增补「启动次数」维度聚合与日期列表倒序稳定性。
- **GamePage 打磨**：竖屏详情改用内部底部抽屉接管（不再误触系统页）；横屏卡片按包名设 key 避免切游戏动画错位；竖屏底部 Tab 圆角与全站表面基准统一；TopBar 展开态宽度适配中等屏；HeroBanner 在 LAND_3 无美术时隐藏，竖屏 banner 高度按 3.097:1 动态下限修正平板留白。
- **主题与组件归一**：完善焦点指示体系（未连接手柄不显示焦点框）与无涟漪语义（`NoIndication`）；完善玻璃表面 token 与 edge-to-edge 全屏壁纸模糊；`DialogScaffold` 升级为全站对话框模板（集成复前台刷新）；清理旧 launcher 图标资产与冗余 Gradle 配置；`build.gradle.kts` 版本号公式化（`versionCode = MAJOR*10000 + MINOR*100 + PATCH`）。

### Fixed
- **GamePage 折叠 banner 卡死**：竖直滚动与折叠进度动画互相踩踏导致推左摇杆反复跳动——拆分为列表竖向滚动与横向指示动画完全解耦，并加滚动守卫与防抖，彻底修复。
- **统计页切换重建闪烁**：两 Tab 包裹 `AnimatedContent` 防切换重建。
- **通知角标误标**：去重计数修正，仅存在 pending 通知时显示角标。
- **竖屏详情误触系统页**：改为 GamePage 内部抽屉接管。
- **竖屏底部 Tab 圆角脱落 / TopBar 宽度局促**：统一表面基准 / 修正 `fillMaxWidth`。
- **更新对话框重复查询与闪烁**：更新状态改为单例进程级缓存，修正关闭行为避免重开重复查询。

---

## [1.1.0 (3)] — 2026-07-26

自 1.0.0 以来最大一次演进：新增完整的**游戏详情页（GamePage）**、**SteamDB 美术提取系统**（Steam 库级 Hero / Logo 定位）、全站**玻璃拟态（Glassmorphism）**、归一化**动画机（MotionMachines）**、子页**手柄交互归一化**、以及**拼音搜索**。

### 游戏详情页 GamePage（全新模块）
- **活动与响应式骨架**：新增 `GamePageActivity` / `GamePage` / `GamePageLayouts`；`GamePageVariant` 由布局约束推导 6 档断点（手机竖屏 / 平板竖屏 / 紧凑横屏 / 中等横屏 / 扩展横屏 / 电视横屏），分别套用 PortraitBody / LandscapeBody 两套骨架。横屏改为 Steam 大屏式布局（左海报栏 + 右内容栏），矮视口不再被全宽 Hero 吞掉垂直空间。
- **架构**：GamePage 实现为独立 Activity（沉浸全屏 + LandscapeTheme + 预测返回）；资源抽象 `GameArt`（ArtSource + GameArtProvider / GameArtResolver 聚合器）预留接口，后续 SteamDB 提取零改动接入；白名单包名按真实值校正（绝区零国服/国际服、无限暖暖等）。
- **焦点状态机**：`GPSlots` 扁平焦点索引驱动 `onKeyEvent`；↑↓ 栏内、←→ 跨栏、A 触发、B 返回、X 收藏、Y 在 TopBar⇄内容间跳、**LB/RB 循环切 Tab、LT/RT 切上/下一个游戏**。
- **游戏判定**：`isGameLike`（应用声明为游戏 / 分类为游戏 / 白名单）的应用均进入游戏页，单一闸门共用。
- **Tab 体系**：动态 / 统计 / 关于 三档；移除所有联网入口（成就 / 社区 / DLC / 多账号 / 内购），仅保留纯本地或系统 Intent 功能；卸载 / 强停合并进顶栏「应用信息」；截图聚合保持纯本地。Tab 视觉对齐主页分段控件，仅手柄连接时显示 LB/RB 键位提示。
- **动态 Tab**：媒体截图画廊（按文件名哈希自动归类本游戏截图/录屏，SAF 授权 + 样例持久化）、每日游玩时间线、时长趋势。
- **统计 Tab**：总计 / 日均 / 最高 + 条形图，与主页统计独立。
- **关于 Tab**：版本 / 首次安装 / 权限。
- **第三方 Widget 宿主**：把社区 Widget 嵌入「个性化内容」区；米哈游/HoYoverse 系合并米游社 + TapTap 选择器，其它仅 TapTap；多 Provider 弹底部面板，单 Provider 直跳系统 Picker；触控优先，不进入手柄焦点链。
- **Widget 按游戏隔离**：原按「宿主包」恢复绑定导致不同游戏的同一 Widget 串扰；新增 `WidgetBindingStore` 按游戏记录归属、重建时只查当前游戏绑定并校验有效性；合并选择器为「多应用多 Provider」通用模型。
- **运行时取色**：`GameArt.extractAccent` 离线 HSV 直方图取色（logo→capsule→header→hero 优先级），accent 优先级 游戏 art > Monet > BrandColors；新增 `GamePageColors.onAccent` / `tonal` 实现按钮 accent 单色系隔离。
- **UI/UX 审计整改**：Tab 选中态、Widget 选择器标题可见性、时间线按 MD3 ListItem 重做、启动按钮焦点框可见性、截图缩略图圆角、未选中 Tab 聚焦信号等十余项修正。
- **控件归一化**：GamePage 全部原生按钮替换为统一按钮库；Tab 对齐主页分段控件；焦点框统一 `focusBorder` 助手。
- **Hero 严格不裁切**：Hero 图 FillHeight 高度锚定；横屏 LAND_3 顶栏 Hero 封顶切换；左栏海报高度封顶永不溢出。
- **切游戏动画与数据**：`AnimatedContent` 按最短循环距离定方向；相邻预载 + 节流防连按；改为先取数后动画，消除切换瞬间空壳闪烁。
- **挖孔/刘海适配**：竖屏 header 内容整体避让状态栏，返回钮/星标不再被遮挡。
- **竖屏 HeroBanner 滚动折叠**：HeroBanner 与内容列表为兄弟节点，根 Column 挂 `nestedScroll` 跨兄弟折叠；下滑收窄、列表到顶弹回全高；logo 加弹性回弹，banner 跟手无弹簧。竖屏点按游戏类一律进游戏页，不进应用详情抽屉。
- **预测返回统一**：子 Activity 改用 `enableEdgeToEdge` + 隐藏状态栏的沉浸式配置，预测返回预览与二次确认气泡共存。
- **左栏手柄焦点修复**：LAND_3 左栏移除误挂的可滚动容器，焦点全部回归页面状态机。
- **页面级焦点跟随滚动**：焦点一变即带动滚动跟随，与子页同构；截图画廊根加焦点组标记避免抢占根焦点。
- **竖屏 banner 视觉修正**：LAND_3 横屏 banner 无美术时隐藏；竖屏 banner 高度按 3.097:1 动态下限修正平板留白；banner 内部边距拆分（外层铺满、logo 内层安全区）。
- **背景刷新与跨页继承**：清除/提取新美术后背景即时刷新；截图查看全部页复用主美术页范式（参数透传 + 立即全屏渲染 + 异步加载 + accent 渐变回退 + 内存缓存跨页零延迟继承）。

### SteamDB 美术提取系统（全新模块）
- **活动链路**：`SteamArtHubActivity`（入口：提取 / 自定义 / 重新提取 / 查看全部 / 清除）→ `SteamArtExtractActivity`（WebView 探针）→ `SteamArtCustomActivity`（自定义导入）→ `GameScreenshotsAllActivity`（查看全部截图）。
- **三层架构**：`domain/`（提取任务、资产选择器、提取时序、模型）、`data/remote/`（商店兜底 appdetails、Steam CDN 静态图、资产下载器）、`data/webview/`（Cloudflare 客户端、搜索客户端、WebView 策略与桥接）、`ui/`（导航图、ViewModel、页面契约）。
- **提取时序 4 阶段**：accent → library（胶囊/海报）→ logo（标志）→ background（背景）。
- **WebView 探针与 Cloudflare**：`SteamWebViewPolicy` 白名单仅 `steamdb.info`；探针抓取多图、连锁错误回退；Cloudflare 检测分级；CF-free 兜底 CDN 静态图 + 弱网延迟回退。每次提取独立 Activity+WebView+VM，完成后彻底 tear down；仅保留 cookie 过 CF，不清除全局 cookie。
- **稳定性**：Cookie 仅 flush 不清除（避免污染并发提取）；成人内容门解锁后等待再定稿；弱网探测延长回退超时；双计时器竞态消除；`isLikelyWeakNetwork` 改为 suspend + 主线程 + 先验权限 + 异常兜底（个别设备无权限也不崩溃）；`AndroidManifest` 新增 `ACCESS_NETWORK_STATE`。
- **缓存落盘**：下载至 `filesDir/steamart/<pkg>/`，JDK `HttpURLConnection`；同包多 GameArt 合并；File 提供者优先于 Asset 提供者；清除按包防串味。`LruCache` 取色单源。
- **主题注入点**：GamePage 主题 / 截图画廊 / 美术背景跨页一致使用取色背景。
- **可清除性**：墓碑标记支持预设/内置游戏真正回到无美术初始态，重新提取自动绕过墓碑。
- **提取 WebView 顶部渐变遮罩**：WebView 之上加纯装饰渐变遮罩，不影响验证点击。
- **自定义主题**：修复 6 槽资产（小头图不再挤占主视觉）；导入 logo 携真实百分比贯通精确尺寸；`logoScale` 改为连续滑块（0.5~1.5）+ 智能推荐初值；修复「关掉本就开启的开关」被漏判成未改的问题；内置 gameart 仅并入 debug 变体、不进 release APK，release 下缺失优雅降级。

### Logo 定位系统（Steam 库规范合规）
- **9 点双对齐体系**：`LogoPosition` 由 9 点枚举 + 真实百分比（含溢出）+ 双对齐构成，支持 logo 浮于 Hero 任意位置（含跨边溢出），持久化 `meta.json`。
- **真实百分比体系**：`computeLogoBox` 以去 padding 的原图全尺寸（严格 3.097:1）为基准乘积，1:1 复刻 SteamDB 定位。
- **自定义主题连续滑块**：`logoScale` 改为连续滑块（默认 1.0，每游戏独立）；`logoCrop` alpha 包围盒裁剪；`recommendLogoScale` 分级推荐。
- **预设 AppId 与 logo_position**：无限暖暖 / 绝区零 / 鸣潮 预设 AppId 与 logo 定位默认值按 SteamDB 真实值校正。
- **优先级（tombstone）**：Logo 落盘优先级 WebView 探针 > 商店 API > 已导入定位 > 预设兜底；banner 内边距 16/12 两档。
- **提取落盘**：logo_position 新增表格正则解析策略；提取前校验 AppId/戏名一致，未知 AppId 不强制提取。

### 玻璃拟态全站改造（Glassmorphism）
- 新增 `ui/theme/GlassState.kt`：主活动默认开启玻璃、各子页默认关闭（避免性能/视觉冲突），由 `HomeViewModel` 单源驱动。
- `frosted` 用 `RenderEffect` / `Motion.blur` 实现毛玻璃；`WallpaperBackground` 改用模糊壁纸做毛玻璃底（全屏壁纸铺满 + 模糊）。
- `SurfaceTokens` 新增 glass 感知：玻璃表面用半透明背景 + 模糊而非纯色卡片；`AppDetailPanel` 玻璃化（横屏右栏 + 竖屏浮层共用）。
- 子页明确玻璃开关策略，避免与卡片表面冲突。

### 动画机 MotionMachines（动画归一）
- 新增 `ui/theme/MotionMachines.kt`：统一 `crossfade` / `backgroundCrossfade` 等可复用动画机器，页面不再散落一次性动画。
- GamePage 切游戏背景复用 `backgroundCrossfade`；删除旧散落折叠实现，改由布局自身处理。
- 节庆/焦点高亮等复用既有机器，不重复自绘。

### 手柄交互归一化（子页 + 焦点 + 震动）
- `SubPageGamepad.kt` 重命名为 `SubPageGamepadHost.kt`：子页统一薄宿主（右摇杆物理滚动 + 焦点回写），子页不再自写摇杆解析。
- **右摇杆轴污染修复**：子页改用右摇杆轴（剔除右扳机污染），修复按 RT 误触发页面滚动。
- `ui/components/SubPageKeyHandler.kt`：归一子页 `onKeyEvent` 处理，About/Licenses/CompatGuide/GamePage 统一接入。
- **焦点恢复**：子页用 `hasFocus` + `onFocusChanged` 单一驱动，避免焦点框锁死。
- **震动归一 + 强度可调**：`Haptics` 单例按强度档位缩放；修正全局零震动根因（真实探测支持 + 兜底），删除旧代理、杜绝双震；设置页新增震动强度下拉（强/中/弱/关闭）。
- **游戏子页面震动缺失修复**：震动初始化仅主进程一次；游戏主页补齐按键 tick/click；子页宿主集中注入摇杆 tick 单一出口，避免双发。
- **设置页下拉 X 键循环切换**：`SettingsControlBridge.onCycle` 循环切换下一项；`DropdownListItem` 聚焦且手柄连接时显示切换提示。
- **子页标题栏归一化**：所有 2/3 级子页统一返回钮与标题栏基准（`SubPageScaffold`）。
- **右键焦点随滚动**：游戏页家族与 `SubPageScaffold` 同构（焦点变即跟随）。

### 设置项文案精简
- 设置页全部设置项描述精简为简短中文（自然长短，不超过 15 字）；仅「导出应用列表」一项保留说明弹窗（解释 JSON + 分类映射工作流），其余不再附加说明。

### 组件与令牌归一化
- 新增 `BottomFadeMask`（底部渐隐遮罩）、`LayoutModifiers`（`statusBarSafeTop` 状态栏安全区）、`ScrollProgressBar`（滚动进度条）。
- `CenteredHint` 复用统一居中提示；`DialogScaffold` 继续作为全站 dialog 模板（scrim + 居中卡片 + 底部 HintBar）。
- `SurfaceTokens` 继续收敛 alpha 语义色（焦点/卡片/遮罩/静音文字/Toast），禁手写 `copy(alpha=)`；收藏星标金统一。
- 手势阈值统一；状态栏安全区统一；未知应用占位（steamart 缓存 + 图标占位）。

### 统计页增强
- **「次数」维度**：统计新增按启动次数的维度（与时长并列）。
- **系统应用聚合**：统计聚合系统/自身包，避免污染排行。
- **逐日列表倒序**：日期列表倒序（最新最上，零值「—」）。
- **快速按钮锚点**：统计页内锚点滚动（最近游玩→排行区 / 总计时长→列表区）；RecentsRow 标题可点击进统计页。

### 搜索增强（拼音搜索）
- 新增 `util/Pinyin.kt`：拼音搜索（按拼音/首字母匹配应用名），与 label 过滤合并。
- **搜索页手柄键位**：进入搜索页默认输入态（输入框获焦 + 软键盘）；推左摇杆任意方向或按 Y 进列表态（与主页 Y=收藏分流，搜索页 Y 不收藏）。A=进详情浮窗；X：输入态清空输入框 / 列表态直接启动焦点项（含游戏）；Y 输入⇄列表切换；B：输入态收键盘 / 列表态返回。列表态 D-pad 移动文本光标。
- **搜索页 UI 去冗余**：移除列表项聚焦态 Y 键位浮块与计数标题栏焦点节点；顶部搜索图标恒显。

### 修复与稳定性合集
- **竖屏点按不弹详情抽屉**：竖屏改用显式 `onOpen` lambda，已聚焦项二次点按也能触发。
- **设置页 LT/RT 无法切 Tab**：修正事件流被误拦截，设置页全部由事件循环驱动。
- **进入其它应用重置到首页**：Manifest 移除 `stateNotNeeded`、新增 `alwaysRetainTaskState`；持久化上次主 Tab，启动恢复。
- **启动后不保留进程开关**：新增「启动游戏后不保留 Reverie 进程」（默认关）；合并卸载/强停为顶栏「应用信息」。
- **GamePage 编译阻断修复**：预测返回回调改用平台类型（`android.window.*`）。
- **上次游玩显示陈旧**：取真实最近一次使用时间（UsageStats 同包多桶根因）。
- **权限/同步审计**：使用统计权限改用 `AppOpsManager` 检查。
- **自定义图标裁剪**：alpha 包围盒裁剪；未知应用占位图标。
- **邮件/微信/QQ 启动**：系统 Intent 跳转；首页返回二次确认气泡。

### Bug 修复补充
- **游戏页焦点框锁死**：焦点框随手柄插拔解锁，改为单一驱动。
- **截图二级子页刷新异常**：滚动停止瞬间所有留视口项同时重放 → 依赖收窄为仅渲染条件 + 锁存；父级派生可见范围替代逐帧全量扫描。
- **覆盖导入遗留背景不替换**：读时回退顺序修正（`PAGE_BG_RAW ?: bg.png ?: LIBRARY_HERO`）；下载完成后清理孤儿文件。
- **美术输入框色条穿透**：统一补全 `focusedIndicatorColor`，隔离 primary/莫奈。
- **Widget 跨游戏串扰**：切游戏时补回重建/刷新调用，修复此前静默回归。
- **游戏页入场割裂 / 截图子页黑屏**：背景层提升为独立 `AnimatedContent` + 内容层保留原切换；截图子页加首帧占位 + 主题包裹。
- **SteamDB 提取「门控竞态漏抓」**：轮询延后到状态就绪才停；成人内容门解锁后取消过早计时器并以完整累积链接点火，门控资产不再漏扫。
- **提取下载性能**：下载由串行单连接改为按主机分组 + 组内 keep-alive 复用 + 组间有界并行；探针脚本只构建一次；首个非空链接即停轮询释放资源给下载器。
- **截图二级页进入占位延迟**：单协程一次性预载应用→美术→accent→背景位图，置门口控渲染，命中即一帧全量渲染。
- **二级子页背景跨页零延迟继承**：进程内内存缓存同步命中，所有美术/截图子页首帧即显完整背景；截图查看全部页复用主美术页范式（参数透传 + 立即全屏渲染 + 异步加载 + accent 渐变回退）。
- **Widget 渐入渐出 + 缓存复用**：Widget 渐入渐出动画；加载键与缓存键对齐修复切回空转；HostView 按「游戏包|宿主包」复用（切走不销毁、切回秒显）。
- **子页返回键半透明框修复**：返回钮背景与焦点指示联动，未连接手柄时不常驻半透明底。
- **折叠 banner 到顶弹回失效修复**：列表状态既捕获又上报，到顶弹回正确触发。
- **Widget 首屏/切游戏不刷新**：`startListening()` 提前到重建之前并配对生命周期，修复初始更新广播丢失。

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

### 崩溃与稳定性修复
- **启动期崩溃**：`LauncherApps` 在未授权时返回 null，全部改为安全降级；启动崩溃运行时风险消除（协程包裹、`finally` 必置就绪、超时保护、全局异常处理器）。
- **开屏卡死**：去除阻塞式保持条件，改为安装 SplashScreen 后立即关闭、加载态交回首页内部转圈。
- **Adaptive Icon 崩溃**：Compose 不支持 `painterResource(mipmap)`，改为 `R.drawable.ic_app_logo`。
- **前台服务崩溃**：`OrientationLockService` 补 `startForeground` + 低优先级通知渠道。
- **社交类应用崩溃**：资源损坏 APK 取图标异常安全包裹。
- **动态氛围背景非全屏**：移至外层 Box 覆盖状态栏 / 导航栏。
- **搜索 / 设置页 B 键崩溃**：补明确分支 + 异常包裹。
- **DropdownDialog 手柄失效**：闭包 stale 读取最新选中项修复。
- **状态栏沉浸残留**：补分支恢复状态栏。
- **主页 / 搜索页完全隔离**：独立焦点状态，解决焦点黏连与高亮错位。
- **"上次启动" 全显示 "刚刚"**：删除伪造时间分支，取真实时间。
- **`registerReceiver`**：API 34+ 强制 `RECEIVER_NOT_EXPORTED` 标志。
- **AGP 8.x BuildConfig**：显式开启 `buildConfig` 特性。

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

### AMOLED / 深色模式莫奈背景色板
- 三档分治方案：
  - **浅色**：完整 `dynamicLightColorScheme`（壁纸色板，不覆盖）。
  - **深色**：基础色回退项目自带深蓝黑，强调色保留莫奈。
  - **AMOLED**：背景纯黑，强调色保留莫奈。
- 关键认知：莫奈深色 `background/surface` 是壁纸调深色版，不符合 MD3 深色规范，必须将强调色与基础色分开处理。

### 原生 Splash 大白边修复
- 现象：冷启动出现圆角白色矩形（中心小 logo）。根因：主题父类默认白底；logo 为普通 webp 非 Adaptive Icon → 系统 legacy fallback 产生大白边。
- 修复：接入标准 Adaptive Icon（前景/背景/单色三图层）；拆分启动/主/横屏主题别名；Manifest 设 icon/roundIcon；新增 core-splashscreen 依赖。
- `ic_app_logo.webp` 仍供应用内 Compose 绘制（`SplashOverlay`/`AboutPage`）。

### 关于 / 许可子页面强制跟随主题（2026-07-12）
- 问题：About/Licenses 呈现默认浅色蓝紫，不随浅色 / 深色 / AMOLED / 莫奈切换。
- 修复：`Theme.kt` 提取 `reverieColorScheme(darkTheme, useMonet, isAmoled)`；子页面新增 `darkMode`/`useMonet` 参数，内部用 `reverieColorScheme` 包裹独立 `MaterialTheme`（保留外层 Typography/Shapes）；`HomeScreen` 调用时传入。

### 品牌
- 应用名由 `MRunner` 更名为 **Reverie**；`strings.xml` `app_name` 已为 Reverie。

### 开屏（Splash）品牌与架构收口
- **品牌图接入**：通过 `android:windowSplashScreenBrandingImage` 接入 2.5:1 标准品牌图。
- **标题栏泄漏修复**：多处主题统一关闭 ActionBar / 标题 / 全屏，杜绝双层标题。
- **Splash 架构收口**：`MainActivity` 仅保留 `installSplashScreen()`，移除依赖平台退出动画的调用；由首页数据就绪驱动第二幕 `SplashOverlay`；原生 Splash 背景对齐品牌深色。

### 默认配色体系 BrandColors
- 新增 M3 紫罗兰调色板作为 App 默认配色，补全 M3 键避免运行时缺键。
- 莫奈取色保持现状：`BrandColors` 仅作为莫奈的回退色板。
- XML 同步：背景色由霓虹深蓝黑改为紫罗兰深色。

### 手柄交互大重构：跨栏逻辑移除
- 跨栏语义整体移除：LS/RS 按压改为"页面内动作键"（主页列表态进详情态），不再跨栏；键盘跨栏全部移除；底部栏提示改为 A 详情 / X 启动 / B 返回。
- 主页导航重构为列表态 / 详情态二元状态机：列表态 A 进详情、X 直接启动、Y 收藏；详情态 A 触发当前按钮、B 退回列表态；详情态左右键在同行按钮间切换焦点，上下改为夹边界（消除启动钮上拨跳到强停的反直觉），不再取模循环。
- 主页详情焦点框 + A 浮块跟随：仅聚焦该钮才显；竖屏浮层同步焦点索引。
- 统计页左右摇杆分栏：宽屏左栏排行（左摇杆焦点）、右栏总览（右摇杆滚动），二者独立；LB/RB 移至右栏；周期切 LB/RB、日期开 View 键；统计焦点不再控制滑块。
- 搜索页手柄逻辑重写：移除 TextField 多余 focusable 冲突；真正 requestFocus + 呼出软键盘；默认进入搜索页即输入态。

### 统一按钮库 Buttons.kt（MD3 规范）
- 新增统一按钮库：5 类 MD3 标准按钮（含 danger 用 error 色）+ 自绘分段控件（官方不可用）；统一手柄焦点边框助手；统一高度 / 圆角。
- 应用范围：详情面板、首页、设置页、子页、统计页、兼容向导、日期按钮等。
- **DateIndicatorButton MD3 重写（修复"小灰点"bug）**：改用统一按钮 + 图标、周期感知 label、防挤压尺寸、View 提示仅浮右上角。

### 右摇杆归一化引擎 + 详情焦点视觉归一
- 右摇杆归一化：所有事件统一记录并分发到对应滚动目标（主页列表 / 详情 / 统计右栏 / 搜索 / 设置 / dialog），二段式加速曲线。
- 详情栏焦点视觉归一：移除 blur 脉冲光晕，改为清晰描边主导；启动按钮重做干净 MD3 焦点环。
- 右摇杆引擎真实时间驱动：改用真实帧间隔物理积分，在 60/120/144Hz 表现一致。
- 底部提示栏键位语义补全；竖屏浮层 B 返回焦点态同步。

### 统计页周视图恢复 + Dialog 归一化
- 恢复周视图（日 / 周 / 月 / 年四档）。
- 所有 dialog 底部提示栏归一到屏幕底部固定底栏；scrim 全量引统一令牌。
- InfoDialog 右摇杆滚动修复。
- 主页详情底部提示栏移除冗余项。

### 全局表面令牌归一化 SurfaceTokens
- 通查发现大量散落 alpha 魔法值且不一致。
- 新增全局令牌层：焦点/卡片/遮罩/静音文字/Toast/玻璃等语义色 + 可复用组件。
- 派发至 10+ 文件，改一处全站生效。

### 详情面板隐藏「卸载 / 强制停止」（暂时）
- 当前"卸载"/"强制停止"仅跳系统页、无法在 App 内完成，先隐藏避免误导；保留恢复方式。

### 焦点滚动归一化 + 详情面板焦点跟随
- 新增焦点滚动归一组件，归一化"焦点变化 → 滚动到可见"。
- AppDetailPanel 接入修复"焦点移出窗口不可见"；统计页左栏排行同类修复。

### 右摇杆滚动引擎独立 + 焦点跟随全覆盖
- 新建右摇杆滚动常量与物理积分引擎，取代分散参数。
- 焦点跟随普及到各子页面；主页焦点跟随失效根因修复（新增抑制标志）；越滚越快强化。

### 焦点框归一化 + 手柄未连接隐藏
- 审计发现多处未接焦点指示守卫。
- 新增 `focusBorder` 助手为唯一绘制入口；全场景接入守卫归一线宽 / 底色。

### 统计页日期选择器：自绘 → 原生 Material3 重构
- 日 / 月 / 年视图改用原生 DatePicker / DateRangePicker，删除自绘选择器约 400 行死代码。
- 手柄事件经桥接驱动原生 state；未来日禁用。`assembleDebug` 通过。

### 首页 AXY 无响应根因修复（致命）
- 首页列表态 A/X/Y/摇杆按压全部无反应（搜索页正常）。
- 根因：一次性协程闭包按值捕获初始 null 导致所有分支守卫永远跳过；改为即时计算实时聚焦应用。

### 构建 lint 警告修复
- 消除 `BoxWithConstraints scope is not used` 误用；`WallpaperManager.getDrawable` 缺权限正确做法为抑制 lint + 异常兜底，不在 manifest 声明系统签名权限。

### 搜索页底部提示栏合并 B/Y
- 输入态 + 软键盘隐藏时 B/Y 指向同一事件合并提示；输入态键盘可见时 B 仅收键盘、Y 退出输入；列表态保持 Y 搜索 / B 返回。

### 底部按键提示栏间距归一 + 手柄交互收尾
- 键位浮块→文本固定间隙与行间间距分离，修复各栏键位贴死。
- 列表项收藏浮块键位提示 X→Y，与"Y=收藏"语义统一。
- 右摇杆焦点跟随扩展至设置页，全部列表型页面归一。

### 扫描 Toast 全屏铺满修复 + 全局 Toast 归一化
- 根因：Toast 占位 slot 撑满全屏导致动画错觉。
- 修复：仅按内容尺寸渲染，补齐底部对齐 / 阴影参数；首页退出确认气泡整段替换为统一 Toast。

### ConfirmDialog 警告框未绝对居中修复
- 设置页危险操作确认卡片锚定左上角 → 补 `.align(Alignment.Center)` 绝对居中（scrim Box 内默认 TopStart）。

### HomeScreen 系统化清理
- 经多轮定点修复清零 IDE 诊断：删除未使用 import、删除无人调用的整函数与死形参、FQN 短名化、删除未用局部 val、修正 BoxWithConstraints scope 误判。

### 初始化流程全面调整
- **HomeViewModel**：添加就绪超时保护、事件 replay、刷新排队机制、缓存加载后即预置初始焦点。
- **MainActivity**：收集器修复（非设置页恢复系统处理 IME / 返回手势）；移除首次启动自动弹兼容向导。
- **HomeScreen**：AppList 新增扫描中态，避免空状态文字闪烁。

### Dialog 全局归一化整合
- 新建 DialogScaffold 统一全屏遮罩 + 居中卡片 + 底部 HintBar 模板，4 个 dialog 全部接入；移除无人引用状态。

### 文档与发布
- **README 重写**：涵盖双态导航、右摇杆物理引擎、焦点框智能显示、MD3 按钮库、SurfaceTokens、Waterfall、FocusScroll、BrandColors、原生 DatePicker、Adaptive Icon + Splash 收口等模块。版本标记 v1.0.0 (2)。
- **v1.0.0 GitHub Release**：APK 更名 `Reverie-v1.0.0-release.apk`；创建并推送 git tag `v1.0.0`；创建 Release 并上传 APK。

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

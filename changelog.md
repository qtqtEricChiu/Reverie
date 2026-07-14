# Changelog

All notable changes to **Reverie** (formerly MRunner) are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/), and the project adheres to semantic versioning.

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

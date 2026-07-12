# Landscape Launcher（横向桌面框架）

基于 **Kotlin + Jetpack Compose** 的横向 Home Launcher 框架，`minSdk/targetSdk/compileSdk = API 36`（Android 16）。
内置 **XBOX 标准 HID 手柄**输入抽象层，并对 **ColorOS / 国产 ROM** 的后台、自启动、悬浮窗、通知角标限制做了兼容处理。

---

## 一、本地调试环境准备

### 1. 开发机（PC）
- **Android Studio**：最新稳定版（如 Narwhal / Meerkat 或更新），内置 JDK 17。
- **Android SDK Platform 36**（Android 16 "Baklava"）+ **Build-Tools** + **Platform-Tools**。
  - SDK Manager → SDK Platforms 勾选 `Android 16 (API 36)`；SDK Tools 勾选 `Android SDK Build-Tools` / `Android SDK Platform-Tools`。
- **JDK 17**：Android Studio 自带；如需独立 JDK，设置 `JAVA_HOME` 指向 JDK 17。
- **Gradle 8.12**：工程已带 `gradle-wrapper.properties`。若 `gradle/wrapper/gradle-wrapper.jar` 缺失，
  在 Android Studio 中打开工程执行 "Sync Project with Gradle Files" 会自动生成；或本地执行 `gradle wrapper`。

### 2. 真机（推荐 ColorOS 设备）
- 设备：OPPO / OnePlus（ColorOS）或其他国产 ROM 机型。
- 开启 **开发者选项 → USB 调试**，并建议开启 **无线调试**（便于脱离线缆）。
- 开启 **USB 安装**（允许通过 USB 安装应用）。
- 用途：桌面角色、后台保活、手柄输入都必须在真机验证。

### 3. 模拟器（可选，横屏验证）
- 创建 **API 36** 的 AVD，硬件配置选 **Landscape**（或运行时旋转）。
- 局限：第三方 Launcher 的 Recent、后台行为在模拟器上与真机不同；**手柄测试建议用真机**。

### 4. 手柄（XBOX）
- XBOX 手柄通过 **蓝牙** 或 **USB** 连接 Android。
- 无手柄时的替代方案：
  - Android 模拟器 **Extended Controls → Phone → Game controller** 模拟手柄。
  - `adb shell input keyevent KEYCODE_DPAD_UP` 等模拟按键（A=KEYCODE_BUTTON_A，B=KEYCODE_BUTTON_B）。

---

## 二、构建与运行

```bash
# 方式 A：Android Studio
#   打开本工程 → Sync → 选中设备 → Run 'app'

# 方式 B：命令行（需 gradle-wrapper.jar 存在）
./gradlew installDebug        # Linux/macOS
gradlew.bat installDebug      # Windows
adb shell am start -n cn.mocabolka.run/.MainActivity
```

首次启动会：
1. 弹出 **桌面角色申请**（设为默认桌面）。
2. 弹出 **国产 ROM 兼容性向导**，引导逐项授权。

---

## 三、ColorOS 手动设置步骤（向导兜底失败时使用）

| 项目 | 路径（ColorOS 15 附近，各版本可能不同） |
| --- | --- |
| 设为默认桌面 | 设置 → 应用 → 默认应用 → 桌面 → 选择 Landscape Launcher；或按 Home 键时选择 |
| 电池优化白名单 | 设置 → 电池 → 更多（电池优化）→ 找到本应用 → 选择“不允许（优化）” / 设置 → 应用管理 → 本应用 → 耗电管理 → 允许后台运行 |
| 自启动 / 后台保活 | 手机管家 / 安全中心 → 权限隐私 → 自启动管理 → 开启本应用；并在“应用速冻/后台清理”中放行 |
| 悬浮窗（如启用浮动 UI） | 设置 → 权限与隐私 → 权限管理 → 悬浮窗 → 允许本应用 |
| 通知监听（角标） | 设置 → 通知与状态栏 → 通知管理 → 本应用 → 允许；通知使用权：设置 → 通知 → 高级 → 通知使用权 → 开启本应用 |

> 说明：自启动 / 后台保活 / 悬浮窗 **无公开 API**，框架通过隐式 Intent 深链跳转系统设置页，
> 并对每个 Intent 做 `resolveActivity` 兜底；若深链失效，请按上表手动开启。

---

## 四、架构概览

```
手柄 KeyEvent/MotionEvent
        │
        ▼
   GamepadManager  ──(死区/去重/轴→方向)──▶  GamepadEvent（领域事件）
        │                                          │
        ▼                                          ▼
   MainActivity                              HomeViewModel
        │                                          │
        │                                  LauncherApps / UsageStats
        ▼                                          │
   HomeScreen (Compose)  ◀── FocusManager + AppRepository
      StatusBar / AppGrid / RecentsRow / Dock
```

- **输入层**：`gamepad/` — XBOX 按键与摇杆轴量归一为领域事件，UI 不关心物理按键。
- **数据层**：`launcher/` — `AppRepository`（LauncherApps 查询 + 游戏/娱乐识别）、`GameWhitelist`（大屏模式优先包名）、`RecentsRepository`（UsageStats 近似最近使用）、`AppLauncher`（启动）。
- **UI 层**：`ui/` — Compose 横向布局，手柄驱动焦点链（D-pad → `FocusManager` 移动焦点，A 确认启动）；`GameModeScreen` 游戏大屏模式。
- **兼容层**：`compat/` — 电池优化、自启动深链、悬浮窗、通知监听的引导与安全启动。

---

## 四之二、游戏大屏模式（Steam Big Picture 风格）

一个区别于常规 Android 桌面的**游戏专用界面**，优先呈现指定游戏与所有「游戏 / 娱乐」类应用。

### 进入 / 退出
- **长按手柄 Menu 键（XBOX 的 Start / ≡ 键）≥ 500ms** → 进入大屏模式。
- 在大屏模式中：**再次长按 Menu** 或 **按 B（Back）键** → 退出。
- 短按 Menu 仍为普通 `Menu` 事件（设置页扩展点）。

### 内容来源
- **白名单置顶**：以下包名始终固定在顶部精选区（即使未安装也会以置灰「未安装」卡片呈现）：
  - `com.kurogame.mingchao`（鸣潮）
  - `com.miHoYo.Nap`（绝区零）
  - `com.papegames.infinitynikki`（无限暖暖）
  - `com.mojang.minecraftpe`（Minecraft）
- **其余游戏 / 娱乐应用**：通过 `ApplicationInfo.FLAG_IS_GAME`、`ApplicationInfo.category == CATEGORY_GAME`，
  以及娱乐类（`CATEGORY_VIDEO` / `CATEGORY_AUDIO`）自动识别，列于下方网格。
- 白名单定义在 `launcher/GameWhitelist.kt`，可按需增删。

### 操作
- D-pad / 左摇杆移动焦点；**A 键**启动选中的已安装游戏（未安装卡片不可启动）。
- 进入时焦点自动落到首个精选游戏。

### 代码位置
- `ui/GameModeScreen.kt` — 大屏模式界面（精选区 + 全部游戏/娱乐网格）。
- `viewmodel/HomeViewModel.kt` — `gameMode` / `games` 状态、`toggleGameMode()`、`launchGameFocused()`。
- `gamepad/GamepadManager.kt` — `KEYCODE_BUTTON_START` 长按检测 → `GamepadEvent.MenuLongPress`。

---

## 五、已知限制 / 后续扩展

- **Recent 任务**：第三方 Launcher 无法真实获取系统 Recent，目前用 `UsageStats`（需授权）近似，需在兼容向导中引导开启“使用情况访问”。
- **Switch 协议**：当前仅实现 XBOX 标准 HID 映射；新增协议只需在 `gamepad/` 增加映射并接入 `GamepadManager`。
- **收藏 Dock**：当前为占位（取前 6 个应用），持久化收藏需接入 `DataStore` / 数据库。
- **通知角标计数**：`NotificationBadgeService` 已注册，角标聚合逻辑为扩展点（见文件内 TODO）。

> 版本提示：若 `settings.gradle.kts` 中的 AGP / Kotlin / Compose BOM 版本在你的 SDK 中不可用，
> 请以 Android Studio 提示的可用版本为准进行修改。

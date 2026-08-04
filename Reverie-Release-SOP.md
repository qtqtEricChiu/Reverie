# Reverie 源码整理与发包 · 固定流程 (SOP)

> 独立维护的发布流水线手册，与 MEMORY.md 解耦。任何"搬运源码 / 发包"操作都按本文件执行。

## 0. 角色与路径约定
- **源工程（开发态）**：`Q:\数据\Web\Run` — 含 `app/src/debug/` 美术调试源集、`app/src/main/assets/gameart/` 预设美术资源、各类构建日志、临时脚本。
- **源码仓库（发布态）**：`Q:\数据\Web\Reverie` — GitHub 链接目标，**只放必要源码**。
- **远程**：`origin` = `https://github.com/qtqtEricChiu/Reverie`，分支 `main`。
- **身份**：包名 `cn.mocabolka.run`，应用显示名 `Reverie`，工程名 `LandscapeLauncher`（保持不变）。
- **构建环境变量**（每次新 shell 必设）：
  ```powershell
  $env:JAVA_HOME='D:\System\Microsoft Build of OpenJDK\jdk-17.0.15.6-hotspot'
  $env:ANDROID_HOME='D:\Tools\Software\AndroidSDK'
  ```

## 1. 版本号管理（铁律）
- **tag / Release 版本名必须与 app 内 `versionName` 完全一致**，绝不可自创（曾误建 `v1.0.1` 而 app 仍是 `1.0.0`，已纠正）。
- `app/build.gradle.kts` 的 `versionCode` / `versionName` 现在作为**本地回退值**：CI 从 git tag 注入 `VERSION_CODE` / `VERSION_NAME` 覆盖它。本地回退值需与计划发布的 tag 保持一致（便于本地调试与归档）。
- `versionCode` 由 tag 派生（单调递增）：`v1.2.0 -> 1*10000 + 2*100 + 0 = 10200`，与 `UpdateRepository` 的应用内更新比较逻辑兼容。
- 发包前确认版本号已递增。

## 2. 搬运（复制，非移动）
**排除清单（必须守住）**：
- 目录：`build/`、`\.gradle/`、`\.codebuddy/`、`\.idea/`、`\.kotlin/`、`app/src/debug/`、`app/src/main/assets/gameart/`
- 文件：`*.log`、`buildlog.txt`、`build_log*.txt`、`hs_err_pid*.log`、`replay_pid*.log`、`local.properties`、`check_braces.py`、`*_original.kt`、`ic_launcher.old.png`
- APK 产出 `app/release/` 靠 `.gitignore` 排除

**保护 Reverie 自有文件不被覆盖**：`readme.md`、`changelog.md`、`icon.png`（这些只在 Reverie 维护，Run 里无对应版，搬运时绝不反向覆盖）。

**标准命令**：
```powershell
cd Q:\数据\Web
robocopy "Run\app\src\main" "Reverie\app\src\main" /E /XD gameart /XF ic_launcher.old.png
robocopy "Run\gradle" "Reverie\gradle" /E
Copy-Item "Run\app\build.gradle.kts"  "Reverie\app\build.gradle.kts"  -Force
Copy-Item "Run\app\proguard-rules.pro" "Reverie\app\proguard-rules.pro" -Force
Copy-Item "Run\build.gradle.kts","Run\settings.gradle.kts","Run\gradle.properties","Run\gradlew","Run\gradlew.bat" "Reverie" -Force
```
> ⚠️ `app/build.gradle.kts` **不在** `app/src/main` 下，必须显式 Copy-Item，否则版本号/依赖不同步。
> 🔧 **实测坑**：若 Run 在搬运后新增了依赖（如 `okhttp3`、`pinyin4j`、`foundation`），Reverie 的 `app/build.gradle.kts` 仍是旧版 → 试构建在 `compileDebugKotlin` 报 `Unresolved reference 'okhttp3' / 'net'`。
> 排错：直接 `Copy-Item Run\app\build.gradle.kts Reverie\app\build.gradle.kts -Force` 重新同步即可，无需重搬源码。
> 🔧 **CI 文件同步**：`.github/workflows/release.yml`、`.gitignore`、`Reverie-Release-SOP.md` 不在 `app/src/main` 下，搬运后需单独 Copy-Item 或手动维护。

## 3. 清理 Reverie 残留
- 删除 Run 已删除但 Reverie 仍追踪的旧文件（如重命名遗留 `SubPageGamepad.kt` → `SubPageGamepadHost.kt`）。
- 每次搬运后 `git status --short` 检查 Extra / deleted，确认无 gameart / debug / 日志泄漏。

## 4. .gitignore 关键条目
```
build/  **/build/  .gradle/  local.properties
app/src/debug/
app/src/main/assets/gameart/
app/release/
*_original.kt  check_braces.py
*.log  buildlog.txt  build_log*.txt  hs_err_pid*.log  replay_pid*.log
# 签名文件（绝不可入库）
*.jks  *.keystore  keystore.properties  signing.properties
```
> ❗ `.gradlew` / `.gradlew.bat` **绝不能**忽略（构建必需脚本）。

## 5. 完整性验证 — 试构建
```powershell
cd Q:\数据\Web\Reverie
.\gradlew.bat assembleDebug --offline
```
- 成功标志：产出 `app\build\outputs\apk\debug\app-debug.apk`。
- 构建后**立即清除产出**（保持仓库精简）：
  ```powershell
  Remove-Item -Recurse -Force app\build, build -ErrorAction SilentlyContinue
  ```

## 6. 提交与 Push
```powershell
cd Q:\数据\Web\Reverie
git add -A
git status --short          # 复核：确保无 gameart/debug/日志
git commit -m "feat: ..."
git push
```
> ⚠️ 仅提交源码与 CI 配置文件；被 `.gitignore` 排除的（gameart / debug / 签名 / 日志 / build）不会进库。

## 7. CI/CD 自动发布（GitHub Actions）— 主编包路径

发布人只需一条命令：`git tag vX.Y.Z && git push --tags`。

### 7.1 一次性前置配置（仅首次）
1. **生成本地 keystore**（放 `Run` 根，被 `.gitignore` 排除，绝不入库）：
   ```bash
   keytool -genkey -v -keystore reverie-release.keystore -alias reverie \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **本地 `Run/keystore.properties`**（不入库）：
   ```properties
   storeFile=reverie-release.keystore
   storePassword=<密码>
   keyAlias=reverie
   keyPassword=<密码>
   ```
3. **GitHub Repository Secrets**（仓库 Settings → Secrets → Actions，4 个）：
   | Secret | 值 |
   |---|---|
   | `KEYSTORE_BASE64` | `Reverie-Release.keystore` 的 Base64（`[Convert]::ToBase64String([IO.File]::ReadAllBytes("reverie-release.keystore"))`） |
   | `KEYSTORE_PASSWORD` | keystore 密码 |
   | `KEY_ALIAS` | `reverie` |
   | `KEY_PASSWORD` | 密钥密码 |

> CI 在构建期将 `KEYSTORE_BASE64` 解码到临时文件（进程退出即删），**密钥绝不落库**。

### 7.2 发布流程
```powershell
cd Q:\数据\Web
# 1) 同步源码（见第 2 节），并确认 Run/app/build.gradle.kts 本地回退版本号与 tag 一致
# 2) 提交并推送源码到 main
cd Q:\数据\Web\Reverie
git add -A; git commit -m "feat: ..."; git push
# 3) 打 tag 触发 CI（tag 名必须与 versionName 一致，含 v 前缀）
git tag v1.2.0
git push origin v1.2.0
```
- CI（`ubuntu-latest` + Temurin JDK 17）自动：提取版本 → 解码 keystore 签名 → `./gradlew assembleRelease` → 重命名 `Reverie-<version>-release.apk` → 创建 GitHub Release 并上传 APK → 生成 `update/version.json` 并回推 `main`。
- 约 5 分钟完成，GitHub 通知结果。

### 7.3 应用内更新
`UpdateRepository` 读取 `Reverie/main/update/version.json`，字段 `version`(如 `"v1.2.0"`)、`versionCode`(int)、`changelog`、`downloadUrl`(Releases tag 页链接)，与 CI 产出完全一致。用户无需手动维护 `version.json`。

### 7.4 回滚
```
git push -d origin v1.2.0          # 删 tag
# GitHub Releases 页删除对应 Release（version.json 如需回退，手动改回上一版本并提交）
```

### 7.5 手动触发（workflow_dispatch）
仓库 Actions 页手动 Run 时无固定 tag，CI 自动回退到最近 tag（`git describe --tags --abbrev=0`）取版本。

---

<details>
<summary>历史方案：GitHub REST API 手动发包（已废弃，归档备查）</summary>

早期通过 P/Invoke 读 Windows 凭据管理器 PAT + `Invoke-RestMethod` 创建 Release 并上传 APK。
**中文编码坑**：`$payload` 须经 `[System.Text.Encoding]::UTF8.GetBytes` 转字节数组再传；`Invoke-RestMethod` 的 `ContentType` 须带 `charset=utf-8`；绝不可 `ConvertTo-Json | Invoke-RestMethod`（中文变 `?`）；`Invoke-WebRequest` 在非交互模式会失败，一律用 `Invoke-RestMethod`。
Release 流程：tag 指向最新 commit → 创建 `POST /repos/qtqtEricChiu/Reverie/releases` → 上传 APK `POST {upload_url}`。
> 本机实测坑（2026-07-26）：`git credential fill` 在非交互子进程返回空，须走 P/Invoke 读 `git:https://github.com`；notes 正文须用 `[System.IO.File]::ReadAllText` 取纯 string，否则 GitHub 返回 422。

</details>

## 8. 红线 / 禁区（来自 MEMORY.md 铁律）
- WebView / 探针 / 提取链路禁改（仅授权过稳定性改动）。
- 动画必须用 `ui/theme/MotionMachines.kt`，禁页面一次性动画。
- UI 文本全中文；禁删核心文本（使用时长 / 启动次数等）。
- 只改用户**明确点名**的部分，不顺手多改。
- **签名文件与密钥绝不可入库**（`.gitignore` 已排除 `*.keystore` / `keystore.properties` 等）。

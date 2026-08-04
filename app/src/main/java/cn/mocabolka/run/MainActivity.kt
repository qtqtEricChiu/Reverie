@file:Suppress("PackageDirectoryMismatch")
package cn.mocabolka.run

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cn.mocabolka.run.compat.CompatGuideActivity
import cn.mocabolka.run.compat.OrientationManager
import cn.mocabolka.run.gamepad.GamepadEvent
import cn.mocabolka.run.gamepad.GamepadManager
import cn.mocabolka.run.gamepad.InputModeManager
import cn.mocabolka.run.ui.DisplayRefresh
import cn.mocabolka.run.ui.Haptics
import cn.mocabolka.run.ui.HapticIntensity
import cn.mocabolka.run.ui.HomeScreen
import cn.mocabolka.run.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var gamepad: GamepadManager

    /** R13 省电：上次用户交互时刻。 */
    private var lastInteractionMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // 原生 SplashScreen（Theme.Launcher.Starting → postSplashScreenTheme=Theme.Launcher）。
        // installSplashScreen() 在此调用以接入系统启动画面：
        // - 在目标 OEM 系统上，原生桌面启动动画（~300ms+）即为"第一幕"，其底色由
        //   Theme.Launcher.Starting 的 windowSplashScreenBackground（@color/splash_bg）决定，
        //   已与我们的默认配色对齐（#0B0F1A），避免 splash → 首帧颜色跳变。
        // - 注意：原生 Splash 的 background 是静态 XML 色，无法引用 Compose 运行时的莫奈动态色，
        //   故"对齐莫奈"在原生层做不到，仅能对齐默认配色。
        // - 本项目以自绘 SplashOverlay（第二幕）为核心开屏动画，由 isBooting 翻转驱动，
        //   不依赖平台 Splash 的 OnExitAnimationListener（实测平台 Splash 在本环境不触发退场回调）。
        //   因此此处仅 install 以保留原生主题配置，不挂 setKeepOnScreenCondition /
        //   setOnExitAnimationListener，避免引入永不触发的死链（setMinimumVisibleDuration 亦非
        //   core-splashscreen 公开 API，最短显示时长改由 SplashOverlay 自身时长控制）。
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        gamepad = GamepadManager { event: GamepadEvent -> viewModel.pushGamepadEvent(event) }

        // 初始化全局触觉引擎（单例），注入手柄震动总开关
        Haptics.init(this, viewModel.settings.hapticIntensity)

        maybeShowCompatGuide()

        // 搜索输入框激活时（焦点在输入框、软键盘可接受输入）放开导航键（交给输入框处理光标）；
        // 搜索页列表态则保留导航键，让左摇杆穿透控制下方应用列表。
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    // 搜索页焦点目标状态机：INPUT 态禁用手柄导航（让位 IME），LIST 态恢复导航
                    viewModel.searchFocusTarget.collect { target ->
                        gamepad.navigationEnabled = target == cn.mocabolka.run.viewmodel.SearchFocusTarget.LIST
                        // 搜索 INPUT 态：仅放行左摇杆 DOWN 作为「焦点贯通」手势（从输入框无缝进入列表），
                        // 不抢占 IME 的 UP/LEFT/RIGHT 方向（保持文本光标 / 软键盘导航）。
                        gamepad.searchInputExitDown = target == cn.mocabolka.run.viewmodel.SearchFocusTarget.INPUT
                    }
                }
                // Dialog 层级监听：任何 dialog 打开时设置 dialogActive=true，
                // 由 GamepadManager 在 emit Trigger 事件前守卫（避免 dialog 打开时意外切 Tab）。
                // passthrough 保持 false（事件仍由 GamepadManager 消费并推入 ViewModel 事件流，
                // 由 HomeScreen 的事件循环根据 DialogLayer 路由到对应的 dialog 处理器）。
                launch {
                    viewModel.dialogLayer.collect { layer ->
                        gamepad.dialogActive = layer != cn.mocabolka.run.gamepad.DialogLayer.NONE
                    }
                }
                // 设置页为 HomeScreen 内自定义焦点页（非系统弹窗），其 A/B/方向键/右摇杆
                // 由 HomeScreen 的事件循环统一驱动。务必保持 passthrough=false，
                // 否则 GamepadManager.handleKeyEvent/handleMotionEvent 会直接 return false，
                // 事件被交还给系统、不再推入 ViewModel 事件流 —— 导致设置页内 LT/RT 切 Tab、
                // 方向键导航、A 确认等全部失效（回归修复）。
                // 普通 Tab（主/统计/搜索/设置）均依赖 GamepadManager 消费事件，故恒为 false；
                // 返回手势由 OnBackPressedDispatcher、IME 由 Compose 单独处理，无需 passthrough=true。
                launch {
                    viewModel.settingsOpen.collect { _ ->
                        gamepad.passthrough = false
                    }
                }
                // 行为设置：启动游戏后不保留 Reverie 进程。
                // launchApp 成功且开启该开关时发射 exitAfterLaunch，此处销毁本任务，
                // 使 Reverie 不留在后台/最近任务。稍候再 finish，让目标应用先接管前台。
                launch {
                    viewModel.exitAfterLaunch.collect {
                        kotlinx.coroutines.delay(300)
                        finishAndRemoveTask()
                    }
                }
                // 挖孔屏适配：监听设置项变化，实时切换 cutout mode
                launch {
                    viewModel.settings.cutoutAdaptFlow.collect { adapt ->
                        applyCutoutAdapt(adapt)
                    }
                }
            }
        }

        // Reverie 为主屏：打开即进入空间。预测性返回（minSdk=36 恒生效）由系统默认行为接管——
        // 不注册任何 OnBackInvokedCallback（PRIORITY_DEFAULT 会阻止系统动画），主屏执行系统返回/右滑即由系统
        // 绘制桌面预览并直接退出应用（用户决策「优先预测性返回手势」）。手柄 B 键仍由 GamepadManager 经事件流
        // 走 HomeScreen 的二次确认气泡，与系统手势路径互不干扰。

        setContent {
            HomeScreen(viewModel = viewModel, onExit = { finish() })
        }
    }

    override fun onResume() {
        super.onResume()
        // 进入空间请求高刷新率，获得满血操作手感（同时刷新交互时间戳）
        lastInteractionMs = System.currentTimeMillis()
        DisplayRefresh.applyHigh(window)
        // 应用强制横屏：应用级方向始终生效；系统级（需悬浮窗权限）为可选增强，
        // 即使缺权限也不会打断用户——用户可在此使用界面后，再到设置/兼容向导中自行授权。
        // OrientationManager.apply() 内部已不再因缺悬浮窗权限返回 true（见 OrientationManager.kt）。
        OrientationManager.apply(this, viewModel.settings)
        // 挖孔屏适配（每次进入 Reverie 确保生效）
        applyCutoutAdapt(viewModel.settings.cutoutAdapt)
    }

    /**
     * 挖孔屏适配：部分设备系统状态栏与内容区重叠时的兼容处理。
     *
     * 窗口层恒设为 [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS]，
     * 让内容本身可延伸到挖孔区域（即"全屏"）。是否主动绕开摄像头由
     * `cutoutAdapt` 开关决定，且**仅由 HomeScreen 的 displayCutout padding 控制**：
     * - 开启（挖孔屏适配）：HomeScreen 在宽屏加 displayCutout padding，关键内容避开摄像头；
     * - 关闭（全屏）：HomeScreen 不加 padding，内容铺满延伸到挖孔区（允许轻微被摄像头压）。
     *
     * 之所以窗口层恒为 ALWAYS 而非随开关切换 DEFAULT/SHORT_EDGES：
     * 旧实现关闭时回落 DEFAULT/SHORT_EDGES 会让系统把挖孔区当黑条避让，叠加 Compose 的
     * displayCutout padding（当时未受开关控制）导致"关掉开关也回不到全屏"——用户反馈的开关键失效。
     * 现改为窗口层始终允许延伸，全屏与否完全交给开关 + padding，语义清晰可控。
     */
    @Suppress("DEPRECATION")
    private fun applyCutoutAdapt(adapt: Boolean) {
        // minSdk = 36（Android 16），LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS 始终可用，无需版本守卫。
        // adapt 参数保留以匹配调用点签名；窗口延伸模式不随开关变化（恒 ALWAYS）。
        val attrs = window.attributes
        attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        // getAttributes() 返回的是拷贝，必须重新赋值回去才能生效
        window.attributes = attrs
    }

    override fun onPause() {
        super.onPause()
        // 离开空间回落平衡刷新率（R13 省电）
        DisplayRefresh.applyBalanced(window)
    }

    /** 首次启动弹出兼容向导（仅此一次），引导用户完成可选权限配置。 */
    private fun maybeShowCompatGuide() {
        val prefs = getSharedPreferences("landscape", MODE_PRIVATE)
        if (!prefs.getBoolean("compat_guide_shown", false)) {
            // commit（同步落盘）：避免进程被杀后 apply 未写入导致向导重复弹出
            prefs.edit().putBoolean("compat_guide_shown", true).commit()
            startActivity(Intent(this, CompatGuideActivity::class.java))
        }
    }

    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // R13：任意按键视为交互，刷新时间戳以维持在高刷新率
        if (event.action == KeyEvent.ACTION_DOWN) markInteraction()
        // 可信手柄按键 → 切到手柄模式（焦点指示器恢复显示）
        if (gamepad.isTrustedGamepadDevice(event.deviceId)) InputModeManager.toGamepad()
        return if (gamepad.handleKeyEvent(event)) true else super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // R13：手柄摇杆/移动视为交互
        markInteraction()
        // 可信手柄摇杆 → 切到手柄模式
        if (gamepad.isTrustedGamepadDevice(event.deviceId)) InputModeManager.toGamepad()
        return if (gamepad.handleMotionEvent(event)) true else super.onGenericMotionEvent(event)
    }

    // 触屏事件：手指按下即切到触控模式（焦点指示器全部隐藏）。纯状态更新，不拦截派发。
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            InputModeManager.toTouch()
        }
        return super.dispatchTouchEvent(ev)
    }

    /** R13：记录用户交互时刻，用于空闲刷新率回落判定。 */
    private fun markInteraction() {
        lastInteractionMs = System.currentTimeMillis()
        DisplayRefresh.applyHigh(window)
    }

    override fun onDestroy() {
        super.onDestroy()
        gamepad.dispose()
    }
}

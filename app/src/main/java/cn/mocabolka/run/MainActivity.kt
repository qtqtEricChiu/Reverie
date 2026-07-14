@file:Suppress("PackageDirectoryMismatch")
package cn.mocabolka.run

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import cn.mocabolka.run.ui.DisplayRefresh
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

        gamepad = GamepadManager { event: GamepadEvent -> viewModel.pushEvent(event) }

        maybeShowCompatGuide()

        // 搜索输入框激活时（焦点在输入框、软键盘可接受输入）放开导航键（交给输入框处理光标）；
        // 搜索页列表态则保留导航键，让左摇杆穿透控制下方应用列表。
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    // 搜索页焦点目标状态机：INPUT 态禁用手柄导航（让位 IME），LIST 态恢复导航
                    viewModel.searchFocusTarget.collect { target ->
                        gamepad.navigationEnabled = target == cn.mocabolka.run.viewmodel.SearchFocusTarget.LIST
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
                // 由 HomeScreen 的事件循环统一驱动，故 passthrough=false 以避免系统焦点干扰；
                // 非设置页面时保持 passthrough=true 让系统处理返回手势与 IME 等默认行为。
                launch {
                    viewModel.settingsOpen.collect { open ->
                        gamepad.passthrough = open
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

        // Reverie 为主屏：不再作为系统桌面，打开即进入空间（预测手势返回退出应用）。
        // 预测返回手势（Android 13+ / API 33+，本项目 minSdk=36 恒生效）：
        // 通过 onBackPressedDispatcher 注册 OnBackPressedCallback，系统会在用户执行返回手势时
        // 绘制预测动画，松手后才回调本 callback。我们将其转成 GamepadEvent.Back 推入事件流，
        // 复用 HomeScreen 现有的"二次确认气泡"逻辑（首次返回弹气泡、再次返回才退出），
        // 避免系统默认 finish() 绕过二次确认直接退出 Reverie。
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.pushEvent(GamepadEvent.Back)
                }
            }
        )

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
            prefs.edit().putBoolean("compat_guide_shown", true).apply()
            startActivity(Intent(this, CompatGuideActivity::class.java))
        }
    }

    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // R13：任意按键视为交互，刷新时间戳以维持在高刷新率
        if (event.action == KeyEvent.ACTION_DOWN) markInteraction()
        // 系统返回键（导航栏 / 实体返回键）：转为 GamepadEvent.Back，复用 HomeScreen 的
        // 二次确认退出逻辑，避免 super 默认 finish() 绕过二次确认直接关掉 Reverie。
        // 手柄 B 键走 GamepadManager(XboxMapping -> GamepadEvent.Back)，此处仅兜底层返回键。
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) viewModel.pushEvent(GamepadEvent.Back)
            return true
        }
        return if (gamepad.handleKeyEvent(event)) true else super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // R13：手柄摇杆/移动视为交互
        markInteraction()
        return if (gamepad.handleMotionEvent(event)) true else super.onGenericMotionEvent(event)
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

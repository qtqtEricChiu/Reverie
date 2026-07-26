package cn.mocabolka.run.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 统一的「边缘到边缘 + 沉浸全屏」窗口配置，所有 Activity 共用，确保预测性返回手势动画正常渲染。
 *
 * 背景（为何 demo 支持预测返回而本工程此前部分页面不支持）：
 * 预测性返回（Android 13+，本项目 minSdk=36 恒生效）的"揭示/预览"动画依赖 Activity 正确接入
 * 系统手势导航所需的边缘到边缘窗口配置。此前仅 [cn.mocabolka.run.MainActivity] 调用了系统推荐的
 * [enableEdgeToEdge]（它会把状态栏/导航栏设为透明并开启手势导航所需的窗口配置，是预测返回动画生效的关键），
 * 而 About / Licenses / CompatGuide / GamePage 四个 Activity 用旧式
 * `WindowCompat.setDecorFitsSystemWindows(window, false)` + 手动 `WindowInsetsControllerCompat.hide`
 * 仅让内容画到状态栏下方，却没配置导航栏手势区域，导致从左/右边缘滑入的返回"预览"动画无法出现。
 *
 * 本扩展统一收口：[enableEdgeToEdge]（手势导航/预测返回的关键配置）+ 可选隐藏状态栏（[hideStatusBar]；子页面传 false 以显示状态栏）。
 * `android:enableOnBackInvokedCallback="true"` 已在 manifest 的 <application> 与 Theme.Launcher 中置 true，
 * 配合此处窗口配置，预测性返回即完整生效；各 Activity 的 [androidx.activity.OnBackPressedCallback]
 * （注册为 enabled）会在用户松手后才被回调，系统手势预览动画与 Compose 退场动画可顺畅衔接。
 */
fun ComponentActivity.setupEdgeToEdgeImmersive(hideStatusBar: Boolean = true) {
    enableEdgeToEdge()
    // 挖孔屏适配下沉：与 MainActivity 一致，窗口层恒允许内容延伸到挖孔/摄像头区域（全屏），
    // 使 GamePage / About / Licenses / CompatGuide 四个子 Activity 与主页表现统一。
    // 关键内容是否主动绕开摄像头，由各页面按 cutoutAdapt 开关加 displayCutout padding 控制。
    window.attributes = window.attributes.also {
        it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
    WindowInsetsControllerCompat(window, window.decorView).apply {
        if (hideStatusBar) hide(WindowInsetsCompat.Type.statusBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/**
 * 统一的预测性返回手势注册（Android 13+ / API 33+，本项目 minSdk=36 恒生效）。
 *
 * 此前各 Activity 用 [androidx.activity.OnBackPressedCallback]，虽功能正常但系统不会绘制
 * "从左到右揭示"的预览动画（仅 ModalBottomSheet 等内部用 OnBackInvokedCallback 的组件有动画）。
 * 改用 [OnBackInvokedDispatcher] 后，从左/右边缘滑入返回时系统即实时绘制目标页预览，松手才执行
 * [onBack]，与 Compose 退场动画顺畅衔接。配合 manifest 的 enableOnBackInvokedCallback="true"。
 */
fun ComponentActivity.enablePredictiveBack(onBack: () -> Unit) {
    @Suppress("NewApi")
    onBackInvokedDispatcher.registerOnBackInvokedCallback(
        OnBackInvokedDispatcher.PRIORITY_DEFAULT
    ) { onBack() }
}

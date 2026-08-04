package cn.mocabolka.run.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.mocabolka.run.ui.theme.isDarkTheme

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
 * 配合此处窗口配置，预测性返回即完整生效：各 Activity 不注册任何 OnBackInvokedCallback（PRIORITY_DEFAULT 会阻止系统动画），
 * 系统手势预览动画与默认 finish 衔接；Compose 内部返回由各自的 BackHandler / 事件流处理。
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
        // 状态栏字体（时钟/电量/通知图标）颜色：向系统发送外观配置。
        // 浅色背景用深色字（APPEARANCE_LIGHT_STATUS_BARS=true），深色背景用浅色字（false）。
        // enableEdgeToEdge 默认 always 浅色内容（白字），在浅色模式下不可读，
        // 故按应用实际生效的深色模式覆盖（与 Theme.kt 判定逻辑保持一致）。
        isAppearanceLightStatusBars = !isAppInDarkMode()
    }
}

/**
 * 判定应用实际渲染是否为深色模式，用于决定状态栏字体的明暗。
 * 与 [cn.mocabolka.run.ui.theme.ThemeKt.LandscapeTheme] 的 darkTheme 计算保持一致：
 * - LIGHT → 浅色（黑字）
 * - DARK / AMOLED → 深色（白字）
 * - SYSTEM → 跟随系统 Configuration 夜间标记
 */
internal fun ComponentActivity.isAppInDarkMode(): Boolean {
    val systemDark = (resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    // 复用 Theme.kt 的集中判定，保持与 resolvePageTheme / LandscapeTheme(settings) 完全一致
    return runCatching { SettingsRepository(this).isDarkTheme(systemDark) }.getOrDefault(systemDark)
}



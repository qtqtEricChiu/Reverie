package cn.mocabolka.run.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.gamepad.SubPageGamepad
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.mocabolka.run.ui.components.LicensesPage
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.MotionSpec

/**
 * 开放源代码许可页（独立 Activity，与 About / CompatGuide 同构）。
 *
 * 设计原则：
 * - 沉浸式：独占全屏（隐藏系统状态栏），使用 LandscapeTheme 统一主题（深色/AMOLED/Monet）。
 * - UI 与 About / CompatGuide 彻底统一：复用 [LicensesPage] + [cn.mocabolka.run.ui.components.SubPageScaffold]。
 * - 完整手柄适配：左摇杆 ↑↓ 切换项、A 触发、B 返回（框架内置）。
 * - 入场出场动画：与 About / CompatGuide 完全一致（fadeIn + slideInHorizontally(1/6) / fadeOut + slideOutHorizontally(-1/6)）。
 * - 返回：B 键 / 系统返回 / 点击返回按钮 → 回到 [AboutActivity]（左出右进连贯转场）。
 */
class LicensesActivity : ComponentActivity() {
    /** 右摇杆滚动引擎。 */
    private lateinit var subPageGamepad: SubPageGamepad
    /** 列表状态提升到 Activity 字段，供右摇杆滚动引擎访问。 */
    private lateinit var listState: LazyListState
    /** 右摇杆滚动后引擎回写的首个可视项索引（焦点跟随桥梁）。 */
    private val rightStickFirstVisible = kotlinx.coroutines.flow.MutableStateFlow(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listState = LazyListState()
        subPageGamepad = SubPageGamepad(
            lifecycleScope,
            getState = { listState },
            onFocusSync = { rightStickFirstVisible.value = it }
        )
        subPageGamepad.start()
        // 沉浸式：隐藏状态栏，全屏显示（跟随 About 沉浸风格）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // 预测返回手势（minSdk=36 恒生效）：支持系统级返回退出，回到 AboutActivity。
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        val settings = SettingsRepository(this)
        setContent {
            // 跟随设置-主题切换（深色/浅色/AMOLED/Monet）
            LandscapeTheme(
                darkTheme = when (settings.darkMode) {
                    DarkMode.LIGHT -> false
                    DarkMode.AMOLED, DarkMode.DARK -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
                useMonet = settings.useMonet,
                isAmoled = settings.darkMode == DarkMode.AMOLED
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = MotionSpec.Medium) +
                        slideInHorizontally(
                            animationSpec = MotionSpec.SlideOffset,
                            initialOffsetX = { it / 6 }
                        ),
                    exit = fadeOut(animationSpec = MotionSpec.Medium) +
                        slideOutHorizontally(
                            animationSpec = MotionSpec.SlideOffset,
                            targetOffsetX = { -it / 6 }
                        )
                ) {
                    val rsFocus = rightStickFirstVisible.collectAsState()
                    LicensesPage(
                        onBack = { finish() },
                        listState = listState,
                        rightStickFirstVisible = rsFocus
                    )
                }
            }
        }
        // 同步应用级方向：直接按当前设置固定自身方向（覆盖全部 OrientationMode）。
        cn.mocabolka.run.compat.OrientationManager.applyAppOrientation(this, settings)
    }

    /** 右摇杆滚动：MotionEvent 轴委托给引擎；其余交系统。 */
    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        return if (subPageGamepad.dispatchMotionEvent(event)) true else super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        subPageGamepad.stop()
        super.onDestroy()
    }
}

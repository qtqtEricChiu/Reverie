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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.gamepad.SubPageGamepad
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.mocabolka.run.ui.components.AboutPage
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.MotionSpec

/**
 * 关于页（独立 Activity，与 CompatGuide 同构）。
 *
 * 设计原则：
 * - 沉浸式：独占全屏（隐藏系统状态栏），使用 LandscapeTheme 统一主题（深色/AMOLED/Monet）。
 * - UI 与 Licenses / CompatGuide 彻底统一：复用 [AboutPage] / [LicensesPage] + [cn.mocabolka.run.ui.components.SubPageScaffold]
 *   （统一 TopBar、焦点状态机、右摇杆滚屏、横竖屏响应式、入场动画）。
 * - 完整手柄适配：左摇杆 ↑↓ 切换项、A 触发、B 返回（框架内置）。
 * - 入场出场动画：与 CompatGuide 完全一致（fadeIn + slideInHorizontally(1/6) / fadeOut + slideOutHorizontally(-1/6)）。
 *   点击"开放源代码许可"跳转到 [LicensesActivity]，形成「左出右进」连贯转场。
 */
class AboutActivity : ComponentActivity() {
    /** 右摇杆滚动引擎（独立 Activity 不含 HomeScreen 引擎，需自建最小滚动循环）。 */
    private lateinit var subPageGamepad: SubPageGamepad
    /** 列表状态提升到 Activity 字段，供右摇杆滚动引擎访问（SubPageScaffold 内 listState 由此传入）。 */
    private lateinit var listState: LazyListState
    /** 右摇杆滚动后引擎回写的首个可视项索引（焦点跟随桥梁，见 SubPageScaffold）。 */
    private val rightStickFirstVisible = kotlinx.coroutines.flow.MutableStateFlow(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 右摇杆滚动：提前创建 LazyListState 并交给引擎，setContent 内复用同一实例。
        listState = LazyListState()
        subPageGamepad = SubPageGamepad(
            lifecycleScope,
            getState = { listState },
            onFocusSync = { rightStickFirstVisible.value = it }
        )
        subPageGamepad.start()
        // 沉浸式：隐藏状态栏，全屏显示（跟随 CompatGuide 沉浸风格）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // 预测返回手势（minSdk=36 恒生效）：用户从屏幕左/右边缘向内滑动时系统
        // 绘制预测动画，松手后此处执行关闭，支持系统级返回退出。
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
                val owner = LocalLifecycleOwner.current
                // 与 CompatGuide 一致的入场重播机制：ON_RESUME（如从 Licenses 返回）自增 refresh，
                // 触发逐行入场动画重播。
                var refresh by remember { mutableStateOf(0) }
                DisposableEffect(owner) {
                    val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
                    owner.lifecycle.addObserver(obs)
                    onDispose { owner.lifecycle.removeObserver(obs) }
                }
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
                    key(refresh) {
                        AboutPage(
                            onBack = { finish() },
                            onOpenLicenses = {
                                startActivity(
                                    Intent(this@AboutActivity, LicensesActivity::class.java)
                                )
                                // 不 finish：保留 About 在栈底，Licenses 返回时回到 About
                            },
                            appVersion = settings.appVersion,
                            listState = listState,
                            rightStickFirstVisible = rsFocus
                        )
                    }
                }
            }
        }
        // 同步应用级方向：直接按当前设置固定自身方向（覆盖全部 OrientationMode，
        // 含左旋/右旋/竖屏/反向竖屏/陀螺仪/跟随系统等），做到"每个选项都固定 Reverie 方向"。
        cn.mocabolka.run.compat.OrientationManager.applyAppOrientation(this, settings)
    }

    /** 右摇杆滚动：MotionEvent 轴委托给引擎；其余交系统。按键（方向/A/B）由 SubPageScaffold 处理。 */
    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        return if (subPageGamepad.dispatchMotionEvent(event)) true else super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        subPageGamepad.stop()
        super.onDestroy()
    }
}

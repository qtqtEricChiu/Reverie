package cn.mocabolka.run.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.mocabolka.run.ui.enablePredictiveBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.gamepad.SubPageGamepadHost
import kotlinx.coroutines.flow.MutableSharedFlow
import cn.mocabolka.run.ui.components.LicensesPage
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.MotionMachines

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
    private lateinit var subPageGamepad: SubPageGamepadHost
    /** 列表状态提升到 Activity 字段，供右摇杆滚动引擎访问。 */
    private lateinit var listState: LazyListState
    /** 右摇杆滚动后引擎回写的首个可视项索引（焦点跟随桥梁）。 */
    private val rightStickFirstVisible = kotlinx.coroutines.flow.MutableStateFlow(-1)
    /** 左摇杆 / Hat 导航事件桥梁：SubPageGamepad.onNavigate 推入，SubPageScaffold 订阅驱动焦点。 */
    private val navFlow = MutableSharedFlow<Direction>(extraBufferCapacity = 16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listState = LazyListState()
        subPageGamepad = SubPageGamepadHost(
            lifecycleScope,
            getState = { listState },
            getFirstVisibleItemIndex = { listState.firstVisibleItemIndex },
            onFocusSync = { rightStickFirstVisible.value = it },
            onNavigate = { dir -> navFlow.tryEmit(dir) }
        )
        subPageGamepad.start()
        // 沉浸式 + 边缘到边缘（统一预测性返回手势窗口配置）
        setupEdgeToEdgeImmersive(hideStatusBar = false)
        // 预测返回手势（minSdk=36 恒生效）：支持系统级返回退出，回到 AboutActivity。
        enablePredictiveBack { finish() }

        val settings = SettingsRepository(this)
        setContent {
            // 跟随设置-主题切换（深色/浅色/AMOLED/Monet）
            LandscapeTheme(settings = settings) {
                AnimatedVisibility(
                    visible = true,
                    // 沉浸子页入场 / 出场统一走「动画机列表」subPage*。
                    enter = MotionMachines.subPageEnter(reduceMotion = false),
                    exit = MotionMachines.subPageExit(reduceMotion = false)
                ) {
                    val rsFocus = rightStickFirstVisible.collectAsState()
                    LicensesPage(
                        onBack = { finish() },
                        listState = listState,
                        rightStickFirstVisible = rsFocus,
                        navigate = navFlow,
                        reduceMotion = settings.reduceMotion
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

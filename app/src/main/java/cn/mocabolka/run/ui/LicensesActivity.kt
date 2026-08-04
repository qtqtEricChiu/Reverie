package cn.mocabolka.run.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.gamepad.Direction
import android.view.KeyEvent
import android.view.MotionEvent
import cn.mocabolka.run.gamepad.SubPageActivityHost
import cn.mocabolka.run.gamepad.markInputModeGamepad
import cn.mocabolka.run.gamepad.markInputModeTouch
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
    private lateinit var subPage: SubPageActivityHost
    /** 列表状态提升到 Activity 字段，供右摇杆滚动引擎访问。 */
    private lateinit var listState: LazyListState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listState = LazyListState()
        subPage = SubPageActivityHost(
            activity = this,
            getState = { listState },
            getFirstVisibleItemIndex = { listState.firstVisibleItemIndex }
        )
        subPage.start()

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
                    val rsFocus = subPage.rightStickFirstVisible.collectAsState()
                    LicensesPage(
                        onBack = { finish() },
                        listState = listState,
                        rightStickFirstVisible = rsFocus,
                        navigate = subPage.navFlow,
                        reduceMotion = settings.reduceMotion
                    )
                }
            }
        }
        // 同步应用级方向：直接按当前设置固定自身方向（覆盖全部 OrientationMode）。
        cn.mocabolka.run.compat.OrientationManager.applyAppOrientation(this, settings)
    }

    /** 右摇杆滚动：MotionEvent 轴委托给引擎；其余交系统。 */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        markInputModeGamepad(event)
        return if (subPage.dispatchMotionEvent(event)) true else super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        markInputModeGamepad(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        markInputModeTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        subPage.stop()
        super.onDestroy()
    }
}

package cn.mocabolka.run.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import android.view.KeyEvent
import android.view.MotionEvent
import cn.mocabolka.run.gamepad.Direction
import cn.mocabolka.run.gamepad.SubPageActivityHost
import cn.mocabolka.run.gamepad.markInputModeGamepad
import cn.mocabolka.run.gamepad.markInputModeTouch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.MutableSharedFlow
import cn.mocabolka.run.ui.components.AboutPage
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.MotionMachines

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
    private lateinit var subPage: SubPageActivityHost
    /** 列表状态提升到 Activity 字段，供右摇杆滚动引擎访问（SubPageScaffold 内 listState 由此传入）。 */
    private lateinit var listState: LazyListState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 右摇杆滚动：提前创建 LazyListState 并交给引擎，setContent 内复用同一实例。
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
                // 与 CompatGuide 一致的入场重播机制：ON_RESUME（如从 Licenses 返回）自增 refresh，触发逐行入场动画重播。
                var refresh by rememberRefreshOnResume()
                AnimatedVisibility(
                    visible = true,
                    // 沉浸子页入场 / 出场统一走「动画机列表」subPage*。
                    enter = MotionMachines.subPageEnter(reduceMotion = false),
                    exit = MotionMachines.subPageExit(reduceMotion = false)
                ) {
                    val rsFocus = subPage.rightStickFirstVisible.collectAsState()
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
                            rightStickFirstVisible = rsFocus,
                            navigate = subPage.navFlow,
                            reduceMotion = settings.reduceMotion
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

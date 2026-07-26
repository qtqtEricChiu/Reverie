package cn.mocabolka.run.gamepad

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import cn.mocabolka.run.ui.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 子页面手柄驱动宿主。
 *
 * 归一化落点：直接持有并复用 [GamepadManager]（与 [cn.mocabolka.run.MainActivity]
 * 完全相同的单一真源引擎——右摇杆读 `rz ?: ry`、统一死区 / 信任校验 / 连发），
 * 再叠加子页面专属的「右摇杆物理滚动 + 焦点跟随回写」粘合层。
 *
 * 这就是"子页面学习 MainActivity 逻辑"的归一化实现：输入归一化 100% 来自 GamepadManager，
 * 子页面不再有自己分裂的摇杆解析。仅滚动循环与焦点回写（Compose 侧状态）需在此桥接。
 *
 * 导航连发走 [SUBPAGE_REPEAT_INTERVAL_MS] / [SUBPAGE_REPEAT_FAST_AFTER_MS] /
 * [SUBPAGE_REPEAT_FAST_INTERVAL_MS]（比主页面更平缓），修复子页焦点项稀疏时
 * "左摇杆一推焦点乱飞"的问题。
 */
class SubPageGamepadHost(
    private val scope: CoroutineScope,
    private val getState: () -> ScrollableState,
    private val getFirstVisibleItemIndex: () -> Int,
    private val onFocusSync: ((Int) -> Unit)? = null,
    private val onNavigate: ((Direction) -> Unit)? = null,
) {
    companion object {
        /** 子页导航连发：比主页面更平缓，避免稀疏焦点项被快速掠过。 */
        const val SUBPAGE_REPEAT_INTERVAL_MS = 240L
        const val SUBPAGE_REPEAT_FAST_AFTER_MS = 1200L
        const val SUBPAGE_REPEAT_FAST_INTERVAL_MS = 150L
    }

    private val rightStickY = MutableStateFlow(0f)
    private val lastRightStickAt = MutableStateFlow(0L)
    private var scrollJob: Job? = null
    private lateinit var manager: GamepadManager

    fun start() {
        // 直接复用 MainActivity 的归一化引擎（同一类、同一真源）。
        manager = GamepadManager(
            onEvent = { ev ->
                when (ev) {
                    is GamepadEvent.RightStick -> {
                        rightStickY.value = ev.dy
                        lastRightStickAt.value = SystemClock.uptimeMillis()
                    }
                    // 导航触感（左摇杆 / Hat 轴）集中于归一化真源：所有游戏子页面的左摇杆导航都经此出口，
                    // 由各子页 `subPageKeyHandler` 的 D-pad tick 互补，避免逐页散落、遗漏或重复。
                    is GamepadEvent.Navigate -> { Haptics.tick(); onNavigate?.invoke(ev.direction) }
                    else -> Unit // Action / Back / Shoulder / Trigger 由页面 Compose onKeyEvent 处理
                }
            },
            repeatIntervalMs = SUBPAGE_REPEAT_INTERVAL_MS,
            repeatFastAfterMs = SUBPAGE_REPEAT_FAST_AFTER_MS,
            repeatFastIntervalMs = SUBPAGE_REPEAT_FAST_INTERVAL_MS,
        )

        // 右摇杆滚动循环：与 HomeScreen / 统计页完全相同的归一化物理引擎 RightStickScrollPhysics。
        scrollJob = scope.launch(Dispatchers.Main.immediate) {
            val physics = RightStickScrollPhysics()
            var lastFrameAt = SystemClock.uptimeMillis()
            while (isActive) {
                val now = SystemClock.uptimeMillis()
                val dt = ((now - lastFrameAt).coerceAtLeast(1)).toFloat() / 1000f
                lastFrameAt = now
                val released = now - lastRightStickAt.value > RightStickScroll.RELEASE_MS
                if (released) rightStickY.value = 0f
                val delta = physics.step(dt, rightStickY.value)
                if (delta != 0f) {
                    getState().scrollBy(delta)
                    onFocusSync?.invoke(getFirstVisibleItemIndex())
                }
                delay(RightStickScroll.FRAME_DELAY_MS)
            }
        }
    }

    /** 由 Activity.onGenericMotionEvent 转发运动事件（摇杆 / 十字键帽）给 GamepadManager 归一化。 */
    fun dispatchMotionEvent(event: MotionEvent): Boolean = manager.handleMotionEvent(event)

    fun stop() {
        scrollJob?.cancel()
        scrollJob = null
        if (::manager.isInitialized) manager.dispose()
    }
}

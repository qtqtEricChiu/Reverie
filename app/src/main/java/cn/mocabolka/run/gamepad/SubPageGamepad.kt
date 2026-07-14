package cn.mocabolka.run.gamepad

import android.view.MotionEvent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 子页面（About / Licenses / CompatGuide）右摇杆滚动引擎。
 *
 * 三个子页面是独立 Activity，不含 HomeScreen 的全局手柄引擎，因此它们各自的
 * [LazyListState] 默认无法被右摇杆驱动。本助手在 Activity 内重建最小化的右摇杆
 * 滚动循环——但**物理积分完全复用** [RightStickScrollPhysics]（与 HomeScreen 同一真源），
 * 因此「越滚越快 + 松手惯性」手感与主页/搜索/统计/设置**完全一致**（归一化）。
 *
 * 焦点跟随（本次新增，需求4）：
 * - 右摇杆滚动后，通过 [onFocusSync] 回调把「当前列表首个可视项索引」告知调用方，
 *   由 SubPageScaffold 将焦点行同步到该项，实现"右摇杆停在哪、焦点就跟到哪"，
 *   与搜索页/设置页/主页列表行为统一。
 *
 * 键盘/方向键（DPAD_UP/DOWN/CENTER/BACK）仍由 [cn.mocabolka.run.ui.components.SubPageScaffold]
 * 的 Compose onKeyEvent 负责，本助手只接管右摇杆（MotionEvent 轴），
 * 因此 Activity 只需覆写 onGenericMotionEvent，无需覆写 dispatchKeyEvent，
 * 避免与框架的焦点状态机 / onKeyEvent 重复消费按键（如 B 键重复返回）。
 *
 * 用法：
 * 1) Activity 内持有状态：val listState = LazyListState()（提升到字段），setContent 复用同一实例；
 * 2) 创建 SubPageGamepad(scope, getState, onFocusSync) 并 start()；
 * 3) onGenericMotionEvent 内调用 gamepad.dispatchMotionEvent(event)；
 * 4) onDestroy 调 stop() 释放。
 */
class SubPageGamepad(
    private val scope: CoroutineScope,
    /** 延迟提供 LazyListState：Activity 在 setContent 之前先创建 state，引擎在 onCreate 末尾 start。 */
    private val getState: () -> LazyListState,
    /**
     * 右摇杆滚动后的焦点同步回调（可空）。参数为当前列表 firstVisibleItemIndex。
     * SubPageScaffold 传入后即可实现"焦点跟随右摇杆"（右摇杆停哪、焦点跟哪）。
     */
    private val onFocusSync: ((Int) -> Unit)? = null
) {
    /** 右摇杆最新垂直轴值（-1..1，下正上负）。 */
    private val rightStickY = MutableStateFlow(0f)
    /** 最近一次收到右摇杆输入的时间戳（用于判定松手）。 */
    private val lastRightStickAt = MutableStateFlow(0L)

    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.Main) {
            // 物理积分复用归一化引擎（与 HomeScreen 完全同一算法/参数）。
            val physics = RightStickScrollPhysics()
            var lastFrameAt = android.os.SystemClock.uptimeMillis()
            while (isActive) {
                val now = android.os.SystemClock.uptimeMillis()
                // 真实帧间隔驱动物理积分：无论帧率高低，加速曲线表现一致，封顶防后台巨步。
                val dt = ((now - lastFrameAt)
                    .coerceIn(1L, RightStickScroll.MAX_FRAME_DT_MS) / 1000f)
                    .coerceAtLeast(1f / 240f)
                lastFrameAt = now
                val released = now - lastRightStickAt.value > RightStickScroll.RELEASE_MS
                val dy = if (released) 0f else rightStickY.value
                val delta = physics.step(dt, dy)
                if (delta != 0f) {
                    // scrollBy 为 suspend 函数，需在协程作用域内调用。
                    runCatching { getState().scrollBy(delta) }
                    // 焦点跟随：滚动后把焦点同步到首个可视项（归一化「右摇杆停哪焦点跟哪」）。
                    onFocusSync?.invoke(getState().firstVisibleItemIndex)
                }
                delay(RightStickScroll.FRAME_DELAY_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Activity.onGenericMotionEvent 委托入口。
     * 仅处理右摇杆轴，其余 MotionEvent 返回 false 交由系统。
     */
    fun dispatchMotionEvent(event: MotionEvent): Boolean {
        val source = event.source
        val isGamepad = (source and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD ||
                (source and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK ||
                (source and android.view.InputDevice.SOURCE_CLASS_JOYSTICK) != 0
        if (!isGamepad) return false

        val rz = event.getAxisValue(MotionEvent.AXIS_RZ)
        val ry = event.getAxisValue(MotionEvent.AXIS_RY)
        val rightStickYVal = if (rz != 0f) rz else ry
        if (kotlin.math.abs(rightStickYVal) > 0.15f) {
            rightStickY.value = rightStickYVal
            lastRightStickAt.value = android.os.SystemClock.uptimeMillis()
            return true
        }
        return false
    }
}

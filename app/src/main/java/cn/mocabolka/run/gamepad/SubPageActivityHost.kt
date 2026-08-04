package cn.mocabolka.run.gamepad

import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.ScrollableState
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.ui.setupEdgeToEdgeImmersive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 子页 Activity 右摇杆滚动 + 左摇杆/Hat 导航引擎的统一托管封装（D-NEW1 归一化落点）。
 * 将散落在 7 个子 Activity 中的重复样板（navFlow / rightStickFirstVisible 字段声明、
 * SubPageGamepadHost 构造 + start、setupEdgeToEdgeImmersive 调用）收拢到一处。
 * 调用方只需持有本实例并在 onCreate 中构造 + start()，并引用 [navFlow] / [rightStickFirstVisible]。
 *
 * 行为与原有手写样板完全等价：构造参数原样透传给 SubPageGamepadHost，
 * [navFlow] 容量默认 16（与多数子页一致；SteamArtHub/Custom 传 0 保留原行为），
 * [rightStickFirstVisible] 为 MutableStateFlow(-1)，[start] 内依次调用
 * setupEdgeToEdgeImmersive(hideStatusBar = false) 与 SubPageGamepadHost.start()。
 *
 * @param activity 宿主 Activity（用于 lifecycleScope 与 setupEdgeToEdgeImmersive）。
 * @param getState 滚动状态提供器（LazyListState / LazyGridState / ScrollState）。
 * @param getFirstVisibleItemIndex 首个可视项索引提供器（用于右摇杆焦点跟随）。
 * @param customOnFocusSync 右摇杆焦点同步回调；默认写入 [rightStickFirstVisible]（与 5 个简单子页一致）。
 * @param customOnNavigate 左摇杆/Hat 导航回调；默认转发到 [navFlow]（tryEmit）。
 * @param navFlowCapacity navFlow 缓冲容量，默认 16（SteamArtHub/Custom 传 0）。
 */
class SubPageActivityHost(
    private val activity: ComponentActivity,
    private val getState: () -> ScrollableState,
    private val getFirstVisibleItemIndex: () -> Int,
    private val customOnFocusSync: ((Int) -> Unit)? = null,
    private val customOnNavigate: ((Direction) -> Unit)? = null,
    navFlowCapacity: Int = 16
) {
    val navFlow = MutableSharedFlow<Direction>(extraBufferCapacity = navFlowCapacity)
    val rightStickFirstVisible = MutableStateFlow(-1)
    private val host: SubPageGamepadHost = SubPageGamepadHost(
        scope = activity.lifecycleScope,
        getState = getState,
        getFirstVisibleItemIndex = getFirstVisibleItemIndex,
        onFocusSync = customOnFocusSync ?: { rightStickFirstVisible.value = it },
        onNavigate = customOnNavigate ?: { navFlow.tryEmit(it); Unit }
    )

    /** 调用方在 onCreate（super 之后）调用：配置沉浸式窗口并启动引擎。 */
    fun start() {
        activity.setupEdgeToEdgeImmersive(hideStatusBar = false)
        host.start()
    }

    /** 调用方在 onDestroy / onStop 调用：停止引擎。 */
    fun stop() = host.stop()

    /** 透传原 onGenericMotionEvent 的摇杆事件给引擎（供调用方 onGenericMotionEvent 内调用）。 */
    fun dispatchMotionEvent(event: MotionEvent): Boolean = host.dispatchMotionEvent(event)
}

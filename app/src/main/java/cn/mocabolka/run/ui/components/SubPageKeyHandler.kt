package cn.mocabolka.run.ui.components

import android.view.KeyEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import kotlinx.coroutines.delay
import cn.mocabolka.run.ui.Haptics

/**
 * SubPageKeyHandler：子页手柄焦点宿主 Modifier。
 *
 * 组合 `FocusRequester + focusTarget + 进入即请求焦点 + onKeyEvent`，
 * 消除各子页重复的 `focusRequester / requestFocus / focusTarget` 样板（SteamArtHub /
 * SteamArtExtract / SteamArtCustom / GameScreenshotsAll 同构）。
 *
 * 约定（与 SubPageScaffold / 主页同源规范一致）：
 * - [onKey] 仅接收 `ACTION_DOWN` 的按键事件，调用方不必再判 action；
 * - 返回 `true` 表示已消费该按键，`false` 表示放行。
 */
fun Modifier.subPageKeyHandler(
    onKey: (KeyEvent) -> Boolean
): Modifier = composed {
    val requester = remember { FocusRequester() }
    // hasFocus 反映「整棵子树是否仍持有焦点」（含后代节点，见下方 onFocusChanged 说明），
    // 用于区分「焦点彻底外流（被 AndroidView / 系统弹窗抢走）」与「焦点移交给合法子组件（如截图画廊）」。
    var hasFocus by remember { mutableStateOf(false) }
    // 进入即请求焦点，确保手柄 / 键盘按键稳定送达 onKeyEvent（与 GamePageScreen 同构）。
    LaunchedEffect(Unit) { requester.requestFocus() }
    // 焦点丢失自动夺回：手柄失灵根因为运行时焦点被 WebView(AndroidView) / 系统弹窗等外部组件抢走后，
    // FocusRequester 不会自动恢复，导致 onKeyEvent 再也收不到手柄事件。
    // 条件用 state.hasFocus（含后代）：仅当整棵子树都失焦（焦点彻底外流）才夺回，
    // 不会与子组件（如截图画廊）的合法聚焦互相抢焦。
    // delay(16) 错开一帧，避免与 WebView 的焦点请求形成同步死锁式抢焦。
    LaunchedEffect(hasFocus) {
        if (!hasFocus) {
            delay(16)
            runCatching { requester.requestFocus() }
        }
    }
    this
        .focusRequester(requester)
        .focusTarget()
        .onFocusChanged { state -> hasFocus = state.hasFocus }
        .onKeyEvent { ev ->
            val nk = ev.nativeKeyEvent
            if (nk.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false

            when (nk.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> Haptics.tick()
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> Haptics.click()
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_BUTTON_B -> Haptics.click()
            }

            onKey(nk)
        }
}

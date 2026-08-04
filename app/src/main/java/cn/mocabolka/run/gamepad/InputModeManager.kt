package cn.mocabolka.run.gamepad

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局输入模式管理器：以单例形式跨 Activity 持有当前输入模式，
 * 由 MainActivity 的触摸 / 手柄事件驱动，Compose 层（LandscapeTheme）直接 collectAsState 自动重组。
 *
 * 设计要点：
 * - object 单例：不随 Activity 重建而重置，模式在 Activity 间保持一致。
 * - StateFlow：仅在真实变化时更新，避免无谓重组。
 * - 初始值 Touch：冷启动 / 手柄未连接时默认触控模式，不显示无意义焦点框；
 *   手柄连接后首次按键即切到 Gamepad（单帧内，感知不到延迟）。
 */
object InputModeManager {
    private val _mode = MutableStateFlow(InputMode.Touch)
    val mode: StateFlow<InputMode> = _mode

    fun setMode(mode: InputMode) {
        if (_mode.value != mode) {
            _mode.value = mode
        }
    }

    fun toGamepad() = setMode(InputMode.Gamepad)
    fun toTouch() = setMode(InputMode.Touch)
}

/**
 * 判定设备是否为可信手柄（A3 / M4 归一化落点，原逻辑与 GamepadManager 一致）。
 * 仅依赖 InputDevice 源类型判定，不依赖任何 Activity 实例。
 */
internal fun isTrustedGamepadDevice(deviceId: Int): Boolean {
    val dev = InputDevice.getDevice(deviceId) ?: return false
    if (dev.isVirtual) return false
    val s = dev.sources
    return (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
        (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
        (s and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 ||
        (s and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
}

/**
 * 子 Activity 输入模式守卫（A3 / M4 归一化落点）：在 dispatch 链中标记当前输入模式，
 * 与 MainActivity 行为对齐，避免子页切换输入方式后回主屏时 [InputModeManager] 过期。
 * 仅做模式标记，不拦截 / 改写事件分发，也不触碰 SubPageGamepadHost 引擎。
 */
internal fun ComponentActivity.markInputModeGamepad(event: KeyEvent) {
    if (event.action == KeyEvent.ACTION_DOWN && isTrustedGamepadDevice(event.deviceId)) InputModeManager.toGamepad()
}

internal fun ComponentActivity.markInputModeGamepad(event: MotionEvent) {
    if (isTrustedGamepadDevice(event.deviceId)) InputModeManager.toGamepad()
}

internal fun ComponentActivity.markInputModeTouch(ev: MotionEvent) {
    if (ev.actionMasked == MotionEvent.ACTION_DOWN) InputModeManager.toTouch()
}

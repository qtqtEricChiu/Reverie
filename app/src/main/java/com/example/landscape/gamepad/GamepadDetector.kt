package cn.mocabolka.run.gamepad

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.InputDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 输入设备变化广播 Action（对应 @hide 的 [Intent.ACTION_INPUT_DEVICE_CHANGED]）。
 * 不同 compileSdk 下该常量可能未暴露，故直接用稳定的底层字符串字面量。
 */
private const val INPUT_DEVICE_CHANGED_ACTION = "android.hardware.input.action.INPUT_DEVICE_CHANGED"

/**
 * 手柄连接检测：用于决定是否在主屏显示"上下文键位角标"。
 *
 * 仅在检测到当前设备已连接手柄时才展示角标（LB/RB 贴列表两侧、
 * X 贴收藏按钮、Menu 贴设置齿轮），纯触屏 / 鼠标环境下自动隐藏，
 * 避免无手柄用户看到无意义提示。
 */
object GamepadDetector {

    /** 当前是否存在已连接的手柄类输入设备。 */
    fun isGamepadConnected(): Boolean {
        val ids = InputDevice.getDeviceIds()
        for (id in ids) {
            val dev = InputDevice.getDevice(id) ?: continue
            val sources = dev.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                    (sources and InputDevice.SOURCE_CLASS_JOYSTICK) != 0
            if (isGamepad) return true
        }
        return false
    }

    /**
     * 以 Flow 形式持续观察手柄连接状态变化。
     * 监听 [Intent.ACTION_INPUT_DEVICE_CHANGED]（API 16+），
     * 设备插拔时重新计算并去重发射。
     */
    fun gamepadConnectedFlow(context: Context): Flow<Boolean> = callbackFlow {
        val emit = { trySend(isGamepadConnected()) }
        emit()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == INPUT_DEVICE_CHANGED_ACTION) {
                    emit()
                }
            }
        }
        val filter = IntentFilter(INPUT_DEVICE_CHANGED_ACTION)
        // API 34+ 强制要求显式声明 receiver 可见性；此处为内部广播，使用 RECEIVER_NOT_EXPORTED
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()
}

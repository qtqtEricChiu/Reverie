package cn.mocabolka.run.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 手柄操作的精细触感反馈。
 * 使用系统预定义振动效果（VibrationEffect.createPredefined）提供更一致的手感。
 * 所有调用对 vibrator 为空安全。
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = runCatching {
        // minSdk = 36 使用 VibratorManager（API 31+）
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }.getOrNull()

    /** D-pad 焦点移动：轻 Tick（低强度），使用预定义 EFFECT_TICK。 */
    fun tick() {
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    /** A 键确认：清晰 Click（中强度），使用预定义 EFFECT_CLICK。 */
    fun click() {
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    /**
     * 错误反馈（如启动失败、操作无效）：双重短振动，区别于正常触感（G2-3）。
     * 使用预定义 EFFECT_DOUBLE_CLICK（连续两次脉冲）。
     */
    fun error() {
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    }
}

package cn.mocabolka.run.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.StateFlow

/**
 * 震动触感强度档位。
 * - 旧版是单一"手柄震动"开关（开=当前 tick 0.4 / click 1.0+0.5）。本版将其归类为 [WEAK]，
 *   并向上补充 [MEDIUM]、[STRONG]，外加 [OFF]（关闭）。
 * - `label` 供设置页下拉项展示；其余字段为各档的触觉参数（原语 scale + 兜底 oneShot 的时长/振幅）。
 */
enum class HapticIntensity(
    val label: String,
    internal val tickPrimitiveScale: Float,
    internal val clickPrimitiveHeavy: Float,
    internal val clickPrimitiveLight: Float,
    internal val tickMs: Long,
    internal val tickAmp: Int,
    internal val clickMs: Long,
    internal val clickAmp: Int,
    internal val errorMs: Long,
    internal val errorAmp: Int
) {
    STRONG("强", 1.0f, 1.0f, 1.0f, 16, 150, 34, 220, 60, 150),
    MEDIUM("中", 0.7f, 1.0f, 0.7f, 14, 95, 30, 155, 55, 110),
    WEAK("弱", 0.4f, 1.0f, 0.5f, 12, 45, 28, 110, 55, 90),
    OFF("关闭", 0f, 0f, 0f, 0, 0, 0, 0, 0, 0);

    val on: Boolean get() = this != OFF
}

object Haptics {

    private const val TAG = "Haptics"

    private var vibrator: Vibrator? = null
    private var intensityFlow: StateFlow<HapticIntensity>? = null
    private var hasVibrator: Boolean = false
    private var hasPrimitives: Boolean = false

    /** 取回单例引用，保持 `val haptics = remember { Haptics(context) }` 既有调用方式不变。 */
    operator fun invoke(context: Context): Haptics = this

    fun init(context: Context, intensity: StateFlow<HapticIntensity>) {
        this.intensityFlow = intensity

        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        // 真机探测：马达是否真实存在、是否支持原语触觉。
        // 注意：正确方法是 Vibrator.arePrimitivesSupported(int...)（返回 Boolean，表示全部给定原语是否支持）；
        // 部分旧代码误写成不存在的 hasPrimitiveSupport 而改成强制 true，导致原语被静默忽略时全局零震动。
        hasVibrator = runCatching { v.hasVibrator() }.getOrDefault(false)
        hasPrimitives = hasVibrator &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                runCatching { v.areAllPrimitivesSupported(Composition.PRIMITIVE_TICK, Composition.PRIMITIVE_CLICK) }
                    .getOrDefault(false)
        Log.d(TAG, "init: vibrator=${v != null} hasVibrator=$hasVibrator hasPrimitives=$hasPrimitives")
    }

    /** 原语不可用时（或异常时）的兜底：createOneShot 显式振幅，真机马达几乎必然有反馈。 */
    private fun oneShot(ms: Long, amplitude: Int) {
        val v = vibrator ?: return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, amplitude)) }
    }

    private val intensity: HapticIntensity
        get() = intensityFlow?.value ?: HapticIntensity.WEAK

    /** D-pad 焦点移动：轻 Tick（低强度），使用 [Composition.PRIMITIVE_TICK]。 */
    fun tick() {
        val lv = intensity
        if (!lv.on) return
        val v = vibrator ?: return

        if (hasPrimitives) {
            runCatching {
                val composition = VibrationEffect.startComposition()
                composition.addPrimitive(
                    Composition.PRIMITIVE_TICK,
                    /* scale */ lv.tickPrimitiveScale,
                    /* delay */ 0
                )
                v.vibrate(composition.compose())
            }.onFailure { oneShot(lv.tickMs, lv.tickAmp) }
        } else {
            oneShot(lv.tickMs, lv.tickAmp)
        }
    }

    /** A 键确认：双脉冲 Click（先重后轻），模拟确认的"顿挫感"。 */
    fun click() {
        val lv = intensity
        if (!lv.on) return
        val v = vibrator ?: return

        if (hasPrimitives) {
            runCatching {
                val composition = VibrationEffect.startComposition()
                // 双脉冲：先重后轻，模拟确认的"顿挫感"
                composition.addPrimitive(
                    Composition.PRIMITIVE_CLICK,
                    /* scale */ lv.clickPrimitiveHeavy,
                    /* delay */ 0
                )
                composition.addPrimitive(
                    Composition.PRIMITIVE_CLICK,
                    /* scale */ lv.clickPrimitiveLight,
                    /* delay */ 100  // ms
                )
                v.vibrate(composition.compose())
            }.onFailure { oneShot(lv.clickMs, lv.clickAmp) }
        } else {
            oneShot(lv.clickMs, lv.clickAmp)
        }
    }

    /** 错误反馈：三重脉冲 Click，紧凑的"笃笃笃"。 */
    fun error() {
        val lv = intensity
        if (!lv.on) return
        val v = vibrator ?: return

        if (hasPrimitives) {
            runCatching {
                val composition = VibrationEffect.startComposition()
                // 三重脉冲：紧凑的"笃笃笃"
                composition.addPrimitive(Composition.PRIMITIVE_CLICK, lv.clickPrimitiveHeavy, 0)
                composition.addPrimitive(Composition.PRIMITIVE_CLICK, lv.clickPrimitiveHeavy, 80)
                composition.addPrimitive(Composition.PRIMITIVE_CLICK, lv.clickPrimitiveLight, 80)
                v.vibrate(composition.compose())
            }.onFailure { oneShot(lv.errorMs, lv.errorAmp) }
        } else {
            oneShot(lv.errorMs, lv.errorAmp)
        }
    }
}

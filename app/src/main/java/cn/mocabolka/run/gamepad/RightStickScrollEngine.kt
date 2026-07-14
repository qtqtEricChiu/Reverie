package cn.mocabolka.run.gamepad

import kotlin.math.abs
import kotlin.math.sign

/**
 * 右摇杆滚动「归一化」物理引擎（唯一真源 / Single Source of Truth）。
 *
 * 背景：项目早期存在两套并行、参数各写一份的右摇杆滚动实现——
 *   ① HomeScreen 内的协程引擎（HOME / SEARCH / STATS / SETTINGS Tab）；
 *   ② SubPageGamepad（About / Licenses / CompatGuide 独立 Activity）。
 * 二者手感"口头对齐但代码分裂"，常量、加速曲线、松手惯性、节流阈值都各写一份，
 * 细节还不完全一致（dt 计算、松手阈值不同）。
 *
 * 本文件把「越滚越快 + 松手惯性」的**物理积分**收敛为一处：
 * - 常量集中在 [RightStickScroll]；
 * - 每帧积分逻辑集中在 [RightStickScrollPhysics.step]。
 * 两套引擎（HomeScreen 协程 / SubPageGamepad）都调用同一算法，
 * 保证任何页面（主页 / 搜索 / 统计 / 设置 / 子页面 / dialog）的右摇杆手感**完全一致**，
 * 且未来仅需在此处调参即可全局生效。
 *
 * 设计要点（越滚越快）：
 * - 速度上限 cap 随按住时长 holdTime **二段式**增长：线性起步（不迟钝）+ 二次加成（长按显著攀升）；
 * - 加速度 accel 也随 holdTime 轻微增大，起速更跟手；乘以推杆幅度 mag 让轻推温和、满推迅猛；
 * - 松手后速度按 [RightStickScroll.INERTIA_DECAY] 平滑衰减（fling 手感），低于阈值归零。
 */
object RightStickScroll {
    /** 死区：推杆幅度小于此值视为松手（与 GamepadManager / SubPageGamepad 采样死区呼应）。 */
    const val DEAD_ZONE = 0.12f

    /** 松手判定：距上次收到右摇杆输入超过此毫秒数即视为已松手，进入惯性衰减。 */
    const val RELEASE_MS = 120L

    /** 基础最大速度（px/s）与基础加速度（px/s²）。 */
    const val MAX_SPEED = 3200f
    const val ACCEL = 10000f

    /**
     * 「越滚越快」加成曲线（本次强化，需求4）：线性项系数、二次项系数、加成上限（px/s）。
     * - 线性项适度上调 → 起速略跟手但不失控；
     * - 二次项显著上调（3200 → 5200）→ 长按后段速度攀升更陡峭，"越滚越快"手感更明显；
     * - 上限大幅上调（12000 → 20000）→ 长列表长按可达到更高终速，快速掠过大量条目。
     */
    const val GROW_LINEAR = 2600f
    const val GROW_QUAD = 5200f
    const val GROW_MAX = 20000f

    /** 松手惯性衰减系数（每帧乘算，≈60fps 下约 0.5s 停）与停止阈值（px/s）。 */
    const val INERTIA_DECAY = 0.92f
    const val STOP_THRESHOLD = 20f

    /** 帧循环建议间隔（ms）。两套引擎统一用此，避免 7/8ms 不一致。 */
    const val FRAME_DELAY_MS = 7L

    /** 真实帧间隔封顶（ms）：防止后台卡顿恢复后单帧巨步。 */
    const val MAX_FRAME_DT_MS = 50L
}

/**
 * 右摇杆滚动物理积分器（可变状态机）。
 *
 * 每帧调用一次 [step]，传入本帧的真实时间步长 dt（秒）与当前推杆垂直分量 dy（-1..1，下正上负，
 * 已按松手规则处理为 0）。返回本帧应滚动的像素位移（speed * dt），调用方据此驱动 scrollBy。
 *
 * 用法（协程帧循环）：
 * ```
 * val physics = RightStickScrollPhysics()
 * while (isActive) {
 *     val dt = 真实帧间隔秒
 *     val dy = if (已松手) 0f else 当前右摇杆Y
 *     val delta = physics.step(dt, dy)
 *     if (delta != 0f) { scrollBy(delta); syncFocus() }
 *     delay(RightStickScroll.FRAME_DELAY_MS)
 * }
 * ```
 */
class RightStickScrollPhysics {
    /** 当前速度（px/s，带符号）。 */
    var speed = 0f
        private set
    /** 已按住时长（秒），松手清零。 */
    var holdTime = 0f
        private set
    /** 上一帧是否处于主动滚动态（供调用方做「起步震动」等边沿判断）。 */
    var wasScrolling = false
        private set

    /**
     * 推进一帧物理积分。
     * @param dt 本帧真实时间步长（秒）。
     * @param dy 当前推杆垂直分量（-1..1，已按松手规则归 0）。
     * @return 本帧应滚动的像素位移（speed * dt）；0 表示无需滚动。
     */
    fun step(dt: Float, dy: Float): Float {
        val mag = abs(dy).coerceIn(0f, 1f)
        if (mag > RightStickScroll.DEAD_ZONE) {
            holdTime += dt
            // ① 速度上限：二段式增长（线性起步 + 二次加成），封顶 GROW_MAX。
            val grow = (RightStickScroll.GROW_LINEAR * holdTime +
                RightStickScroll.GROW_QUAD * holdTime * holdTime)
                .coerceAtMost(RightStickScroll.GROW_MAX)
            val cap = RightStickScroll.MAX_SPEED * mag + grow
            // ② 加速度随 holdTime 轻微增大、随推杆幅度缩放，起速跟手且轻推温和。
            val accel = RightStickScroll.ACCEL * (1f + holdTime * 0.5f) * (0.5f + 0.5f * mag)
            speed += dy.sign * accel * dt
            speed = speed.coerceIn(-cap, cap)
            wasScrolling = true
        } else {
            holdTime = 0f
            // 松手惯性：平滑衰减到停。
            speed *= RightStickScroll.INERTIA_DECAY
            if (abs(speed) < RightStickScroll.STOP_THRESHOLD) speed = 0f
            wasScrolling = false
        }
        return if (speed != 0f) speed * dt else 0f
    }
}

package cn.mocabolka.run.gamepad

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * 手柄输入管理器：在 Activity 的 dispatchKeyEvent / onGenericMotionEvent 中接收原始输入，
 * 做死区、去重与轴→方向映射后，归一为 [GamepadEvent] 领域事件回调给上层。
 *
 * 以标准安卓手柄 API（SOURCE_GAMEPAD / SOURCE_JOYSTICK）为最优先适配基准，
 * 附带 Xbox 协议兼容，确保通用安卓手柄无需额外配置即可完整导航。
 *
 * 导航策略：
 * - 导航由左摇杆与十字键帽（hat switch）共同驱动，经由 [handleMotionEvent] 产生
 *   [GamepadEvent.Navigate]（十字键帽的 hat 轴承载导航）。
 * - 十字键实体四向按键（DOWN/UP 等）被消费但不发射导航，仅动作语义交由上层。
 * - 十字左/右键产生的 Navigate 事件仅用于 HomeScreen 的 HOME/STATS Tab 宽屏下
 *   「切换焦点栏」，是全工程极少数的十字键导航绑定场景。
 * - 动作键（A/B/X/Y/LB/RB/Menu 等）始终被消费，由上层解释。
 */
class GamepadManager(
    /**
     * 导航连发节奏（可按页面微调，见 [SubPageGamepadHost] 传入更平缓的子页节奏）：
     * - [repeatIntervalMs] 首次推杆后到第一次重复的延迟；
     * - [repeatFastAfterMs] 连续按住超过此时长后切换到"快速重复"；
     * - [repeatFastIntervalMs] 快速重复间隔（越短焦点跳得越快）。
     */
    private val repeatIntervalMs: Long = DEFAULT_REPEAT_INTERVAL_MS,
    private val repeatFastAfterMs: Long = DEFAULT_REPEAT_FAST_AFTER_MS,
    private val repeatFastIntervalMs: Long = DEFAULT_REPEAT_FAST_INTERVAL_MS,
    private val onEvent: (GamepadEvent) -> Unit,
) {
    companion object {
        /** 主页面（多列网格）默认导航连发节奏：偏快跟手。 */
        const val DEFAULT_REPEAT_INTERVAL_MS = 180L
        const val DEFAULT_REPEAT_FAST_AFTER_MS = 600L
        const val DEFAULT_REPEAT_FAST_INTERVAL_MS = 80L
    }

    /** 摇杆"起推"阈值：超过此值才判定为某方向。下调至 0.24 消除轻推无反应体感死区（保留滞后区抗抖动）。 */
    private val stickDeadZone = 0.24f
    /** 摇杆"回中"阈值：低于此值才判定为回中（须 < deadZone 维持滞回）。 */
    private val stickReleaseZone = 0.16f
    /** 十字键帽死区（保持原值，0.5）。 */
    private val hatDeadZone = 0.5f

    /** 是否接管导航键。搜索等需要文本输入的场景由上层置 false。 */
    @Volatile
    var navigationEnabled = true

    /** 全局放行：弹窗（设置等）打开时置 true，所有手柄事件交还给系统/弹窗焦点系统处理。 */
    @Volatile
    var passthrough = false

    /**
     * Dialog 活动标志：当任意 dialog（下拉/确认/信息/日期选择器）打开时为 true。
     * 与 passthrough 不同：dialog 打开时事件仍由 GamepadManager 消费（passthrough=false），
     * 但 Trigger（LT/RT）切 Tab 事件在 dialog 打开时不应穿透到 HomeScreen 事件循环。
     * 此标志由 MainActivity 根据 [DialogLayer] 设置。
     */
    @Volatile
    var dialogActive = false

    /**
     * 搜索页 INPUT 态「焦点贯通」开关：仅当搜索输入框获得焦点（searchFocusTarget==INPUT）时
     * 由 MainActivity 置 true。此时 [navigationEnabled] 为 false（让位 IME 文本光标 / 软键盘导航），
     * 但左摇杆任意方向均作为「从输入框无缝进入下方应用列表」的贯通手势被上层消费，
     * 与 LIST 态一致（左摇杆在 INPUT/LIST 两态不冲突）；D-pad 仍走 onKeyEvent 路径让位 IME。
     */
    @Volatile
    var searchInputExitDown = false

    // 导航连发节奏由构造函数参数（默认值见 companion 常量）提供，主页面保持原偏快节奏。
    /** 记录上一次重复触发的时间戳（用于节流判断）。 */
    @Volatile private var lastRepeatAt: Long = 0L
    @Volatile private var repeatStartedAt: Long = 0L

    private val handler = Handler(Looper.getMainLooper())
    /** 右摇杆滚动事件节流时间戳（C1-2）。 */
    @Volatile private var lastRightStickAt: Long = 0L
    private val RIGHT_STICK_THROTTLE_MS = 16L
    /** 扳机边沿检测状态：记录上一次是否处于「按下(>0.1f)」态，用于跨阈值时才发射事件。 */
    @Volatile private var lastLTriggerActive = false
    @Volatile private var lastRTriggerActive = false
    @Volatile
    private var lastAxisDir: Direction? = null
    /** 当前摇杆导航的来源（十字键 / 左摇杆），长按重复时沿用。 */
    @Volatile
    private var lastAxisSource: NavigateSource = NavigateSource.DPAD
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val dir = lastAxisDir
            if (dir != null && (navigationEnabled || searchInputExitDown)) {
                onEvent(GamepadEvent.Navigate(dir, lastAxisSource))
                val now = android.os.SystemClock.uptimeMillis()
                lastRepeatAt = now
                // 两段式：前 600ms 慢速 (180ms)，之后快速 (80ms)，长按时移动更跟手
                val interval = if (now - repeatStartedAt > repeatFastAfterMs)
                    repeatFastIntervalMs else repeatIntervalMs
                handler.postDelayed(this, interval)
            }
        }
    }

    /**
     * 安全校验（审计 SEC-1）：确认事件来自真实手柄/摇杆类物理设备。
     * 拦截 Instrumentation / adb input / 恶意进程注入的伪造事件（deviceId=-1 虚拟设备），
     * 防止未经物理输入即触发启动应用、切 Tab 等语义操作。
     * 非可信来源不"吞掉"，而是放行给系统按普通按键处理（功能优雅降级）。
     */
    internal fun isTrustedGamepadDevice(deviceId: Int): Boolean {
        val dev = InputDevice.getDevice(deviceId) ?: return false
        if (dev.isVirtual) return false // 虚拟键盘/注入设备一律不信任
        val s = dev.sources
        return (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (s and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 ||
            (s and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (passthrough) return false
        val keyCode = event.keyCode
        // SEC-1：仅接管来自真实手柄设备的按键；注入/键盘等来源放行给系统，
        // 不再被解释为手柄语义事件（防伪造触发敏感操作）。
        if (XboxMapping.isHandledKey(keyCode) && !isTrustedGamepadDevice(event.deviceId)) {
            return false
        }
        // 十字键（D-pad）四向实体按键：ACTION_DOWN 和 ACTION_UP 均消费，
        // 防止孤立 UP 泄漏给系统（某些设备会把在 Compose 焦点边界"无处可去"
        // 的方向键 UP 解释为返回，导致连续按键退出应用）。
        // 不产生导航事件：十字键的导航功能由 handleMotionEvent 中的 hat 轴承载。
        if (isDpadDirectionKey(keyCode)) {
            return true // DOWN 和 UP 均消费
        }
        // Menu 键（Select/Mode/Start）：映射为标准 Navigate/动作事件。
        // 退出应用改为：先回到主页，再按 B 二次确认（不再有长按 Menu 退出逻辑）。
        val mapped = XboxMapping.mapKey(keyCode, event.action)
        if (mapped == null) {
            // mapped 为 null 的两种情形：
            //  1) 属于我们接管的键的 ACTION_UP —— 必须消费，防止孤立 UP 泄漏给
            //     系统焦点/返回逻辑（连续按键退出应用的根因）。
            //  2) 完全无关的键 —— 放行给系统。
            return if (event.action == KeyEvent.ACTION_UP && XboxMapping.isHandledKey(keyCode)) {
                // 仅当接管导航时才吞掉导航键 UP；否则（如搜索输入态）放行
                navigationEnabled || !isDpadDirectionKey(keyCode)
            } else false
        }
        return when (mapped) {
            // 导航键：仅当接管时消费；否则放行（如输入框光标移动）
            is GamepadEvent.Navigate -> {
                if (navigationEnabled) {
                    Log.d("ReverieGamepad", "navigate key ${keyCode} -> ${mapped.direction} [source=DPAD 跨栏允许]")
                    onEvent(mapped)
                    true
                } else false
            }
            // 动作键一律消费
            else -> {
                Log.d("ReverieGamepad", "action key ${keyCode} -> ${mapped::class.simpleName}")
                onEvent(mapped)
                true
            }
        }
    }

    /** 判断是否为 D-pad 四向实体方向键（不含 CENTER/ENTER）。 */
    private fun isDpadDirectionKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT -> true
        else -> false
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (passthrough) return false
        val source = event.source

        // 放宽 SOURCE 检测：通过 CLASS 掩码判断是否属于摇杆/手柄类，
        // 避免部分通用安卓手柄 SOURCE 位组合不典型导致被过滤。
        val isGamepad = (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0
        if (!isGamepad) return false
        // SEC-2：event.source 可被注入方伪造，追加真实 InputDevice 校验（同 SEC-1）。
        if (!isTrustedGamepadDevice(event.deviceId)) return false

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val stickX = event.getAxisValue(MotionEvent.AXIS_X)
        val stickY = event.getAxisValue(MotionEvent.AXIS_Y)

        // 优先使用十字键帽(hat)，否则用左摇杆；记录来源用于区分跨栏语义
        // 来源判定：[hat 轴超阈] => DPAD（十字键，允许跨栏）；否则 => STICK（左摇杆，仅切分类）
        val fromHat = abs(hatX) >= hatDeadZone || abs(hatY) >= hatDeadZone
        val dx = if (fromHat) hatX else stickX
        val dy = if (fromHat) hatY else stickY
        val navSource = if (fromHat) NavigateSource.DPAD else NavigateSource.STICK
        if (Log.isLoggable("ReverieGamepad", Log.VERBOSE)) {
            Log.v(
                "ReverieGamepad",
                "axis src=$navSource fromHat=$fromHat hat=(${"%.2f".format(hatX)},${"%.2f".format(hatY)}) " +
                    "stick=(${"%.2f".format(stickX)},${"%.2f".format(stickY)})"
            )
        }
        emitAxisDirection(dx, dy, navSource)

        // 右摇杆：专用于滚动。标准安卓右摇杆为 AXIS_Z(水平)/AXIS_RZ(垂直)，
        // 部分手柄用 AXIS_RX/AXIS_RY，均尝试读取垂直分量作为滚动量。
        // 节流：MotionEvent 每帧可能多次回调，限流到右摇杆滚动事件（C1-2 ~16ms 一帧），
        // 避免一次物理推杆产生过多 RightStick 叠加导致列表滚动过快。
        // 注意：dialogActive 时仍发射右摇杆事件（不再在此屏蔽），由上层事件循环
        // 在 dialog 分支内路由到当前 dialog 的滚动（而非背景列表），从而避免误滚背景。
        val rz = event.getAxisValue(MotionEvent.AXIS_RZ)
        val ry = event.getAxisValue(MotionEvent.AXIS_RY)
        val rightStickY = if (rz != 0f) rz else ry
        if (abs(rightStickY) > 0.15f) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastRightStickAt > RIGHT_STICK_THROTTLE_MS) {
                lastRightStickAt = now
                if (Log.isLoggable("ReverieGamepad", Log.VERBOSE)) {
                    Log.v("ReverieGamepad", "RIGHT_STICK scroll dy=${"%.2f".format(rightStickY)} (职责: 仅滚动页面)")
                }
                onEvent(GamepadEvent.RightStick(rightStickY))
            }
        }

        // 扳机轴：仅 LTRIGGER/RTRIGGER（不再把 Z/RZ 误当扳机，那属于右摇杆）
        // 边沿检测：仅在「跨越 0.1f 阈值」时发射，避免每帧噪声；
        // 松开回落到阈值以下时发射一次 value=0 的事件，供上层解锁防抖锁，
        // 否则上层锁永远无法复位（首按有效、之后再按无响应）。
        // dialogActive 时屏蔽 Trigger 事件，避免 dialog 打开时意外切 Tab。
        if (!dialogActive) {
            val lTrigger = readTrigger(event, MotionEvent.AXIS_LTRIGGER, -1)
            val rTrigger = readTrigger(event, MotionEvent.AXIS_RTRIGGER, -1)
            val lActive = lTrigger > 0.1f
            val rActive = rTrigger > 0.1f
            if (lActive != lastLTriggerActive) {
                lastLTriggerActive = lActive
                if (lActive) {
                    Log.d("ReverieGamepad", "trigger LEFT $lTrigger")
                    onEvent(GamepadEvent.Trigger(Side.LEFT, lTrigger))
                } else {
                    Log.d("ReverieGamepad", "trigger LEFT released")
                    onEvent(GamepadEvent.Trigger(Side.LEFT, 0f))
                }
            }
            if (rActive != lastRTriggerActive) {
                lastRTriggerActive = rActive
                if (rActive) {
                    Log.d("ReverieGamepad", "trigger RIGHT $rTrigger")
                    onEvent(GamepadEvent.Trigger(Side.RIGHT, rTrigger))
                } else {
                    Log.d("ReverieGamepad", "trigger RIGHT released")
                    onEvent(GamepadEvent.Trigger(Side.RIGHT, 0f))
                }
            }
        }

        return true
    }

    /** 读取 trigger 轴：优先 [primaryAxis]，若为 0 则 fallback 到 [fallbackAxis]。 */
    private fun readTrigger(event: MotionEvent, primaryAxis: Int, fallbackAxis: Int): Float {
        val primary = event.getAxisValue(primaryAxis)
        return if (primary != 0f) primary else event.getAxisValue(fallbackAxis)
    }

    /**
     * 轴→方向，并触发摇杆长按连续移动的重复调度。
     *
     * 采用**滞后判定（hysteresis）**抗抖动：
     * - 当前已回中（lastAxisDir == null）：需超过较高的 [stickDeadZone] 才起推，防误触；
     * - 当前已推向某方向：需回落到较低的 [stickReleaseZone] 以下才判定回中。
     * 两个阈值之间形成"死区带"，摇杆在边缘轻微抖动不会反复触发/回中，
     * 避免频繁反复推杆时焦点乱跳与事件风暴。
     */
    private fun emitAxisDirection(dx: Float, dy: Float, source: NavigateSource) {
        val magnitude = maxOf(abs(dx), abs(dy))
        val threshold = if (lastAxisDir == null) stickDeadZone else stickReleaseZone
        val dir = when {
            magnitude < threshold -> null
            abs(dx) > abs(dy) -> if (dx > 0) Direction.RIGHT else Direction.LEFT
            else -> if (dy > 0) Direction.DOWN else Direction.UP
        }
        if (dir != lastAxisDir) {
            lastAxisDir = dir
            lastAxisSource = source
            handler.removeCallbacks(repeatRunnable)
            // dialogActive 时导航始终发射（不检查 navigationEnabled），
            // 确保 dialog 内选项切换不受搜索页 INPUT 态影响。
            // 搜索 INPUT 态：左摇杆任意方向均作为「焦点贯通」手势放行（即便 navigationEnabled=false），
            // 从输入框无缝进入下方列表（与 LIST 态一致，左摇杆在 INPUT/LIST 两态不冲突）；
            // 仅 D-pad 仍让位 IME 文本光标（D-pad 走 onKeyEvent 路径，不受 searchInputExitDown 影响）。
            val canNavigate = navigationEnabled || dialogActive ||
                searchInputExitDown
            if (dir != null && canNavigate) {
                val semantic = if (source == NavigateSource.DPAD) "跨栏允许" else "仅切分类(不跨栏)"
                Log.d("ReverieGamepad", "axis navigate -> $dir source=$source [$semantic]")
                onEvent(GamepadEvent.Navigate(dir, source))
                val now = android.os.SystemClock.uptimeMillis()
                lastRepeatAt = now
                repeatStartedAt = now
                // 首次推杆后 180ms 进入第一次重复，再之后按"长按加速"策略
                handler.postDelayed(repeatRunnable, repeatIntervalMs)
            }
        }
    }

    /** 释放资源（Activity 销毁时调用），避免泄漏主线程 Handler 回调。 */
    fun dispose() {
        handler.removeCallbacks(repeatRunnable)
        handler.removeCallbacksAndMessages(null) // 兜底：清空一切残留回调，防 Activity 泄漏
        lastAxisDir = null
        lastRepeatAt = 0L
        repeatStartedAt = 0L
        lastLTriggerActive = false
        lastRTriggerActive = false
    }
}

package cn.mocabolka.run.gamepad

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class Side { LEFT, RIGHT }

/** 导航事件来源：十字键帽(D-pad) 或 左摇杆(stick)。仅用于日志/调试，不再区分跨栏语义。 */
enum class NavigateSource { DPAD, STICK }

/**
 * 手柄物理输入归一化后的领域事件。
 * UI 只消费这些事件，不直接依赖物理按键，便于后续扩展 Switch 等协议。
 */
sealed interface GamepadEvent {
    data class Navigate(
        val direction: Direction,
        /** 来源：十字键帽(DPAD) 或 左摇杆(STICK)。归一化后两者等价，仅用于日志。 */
        val source: NavigateSource = NavigateSource.DPAD
    ) : GamepadEvent
    data object Select : GamepadEvent
    data object Back : GamepadEvent
    data class Shoulder(val side: Side) : GamepadEvent
    data class Trigger(val side: Side, val value: Float) : GamepadEvent
    /** 右摇杆：专用于列表滚动（dy>0 向下，<0 向上）。与扳机 Trigger 解耦。 */
    data class RightStick(val dy: Float) : GamepadEvent
    /**
     * 左摇杆按压（LS / L3）：上下文动作键，由当前页面自定义语义
     * （如主页/统计/搜索中作为"聚焦当前元素"的确认类操作）。
     * 注意：跨栏（LS 回左栏 / RS 到右栏）逻辑已于 2026-07-13 末轮整体移除，
     * 各页不再有"跨栏"概念，LS/RS 按压仅作页面内动作键。
     */
    data object LeftStickPress : GamepadEvent
    /**
     * 右摇杆按压（RS / R3）：上下文动作键，由当前页面自定义语义。
     * 跨栏逻辑已移除，RS 不再承担跨栏职责。
     */
    data object RightStickPress : GamepadEvent
    /** View 键（Xbox 双方块按钮 = KEYCODE_BUTTON_SELECT）：上下文动作（如统计页打开日期选择）。 */
    data object ViewPress : GamepadEvent
    /** X 键：切换当前焦点游戏的收藏（固定到精选区）。 */
    data object Favorite : GamepadEvent
    /** Y 键：打开 / 关闭搜索。 */
    data object Search : GamepadEvent
}

/**
 * Dialog 层级枚举：表示当前最上层打开的 dialog 类型。
 * 事件循环根据此枚举将事件路由到正确的 dialog 处理器。
 * NONE = 无 dialog 打开，事件正常进入各 Tab 分发逻辑。
 */
enum class DialogLayer {
    /** 无 dialog 打开 */
    NONE,
    /** 设置页下拉选择弹窗（DropdownDialog） */
    SETTINGS_DROPDOWN,
    /** 设置页确认弹窗（ConfirmDialog） */
    SETTINGS_CONFIRM,
    /** 设置页信息弹窗（InfoDialog） */
    SETTINGS_INFO,
    /** 统计页日期选择器弹窗（NativeDatePickerDialog，原生 Material3 封装） */
    STATS_DATE_PICKER
}

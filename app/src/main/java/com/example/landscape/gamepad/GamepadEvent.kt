package cn.mocabolka.run.gamepad

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class Side { LEFT, RIGHT }

/** 导航事件来源：十字键帽(D-pad) 或 左摇杆(stick)。用于区分跨栏等语义。 */
enum class NavigateSource { DPAD, STICK }

/**
 * 手柄物理输入归一化后的领域事件。
 * UI 只消费这些事件，不直接依赖物理按键，便于后续扩展 Switch 等协议。
 */
sealed interface GamepadEvent {
    data class Navigate(
        val direction: Direction,
        /** 来源：十字键帽(DPAD) 或 左摇杆(STICK)。跨栏仅允许 DPAD。 */
        val source: NavigateSource = NavigateSource.DPAD
    ) : GamepadEvent
    data object Select : GamepadEvent
    data object Back : GamepadEvent
    data class Shoulder(val side: Side) : GamepadEvent
    data class Trigger(val side: Side, val value: Float) : GamepadEvent
    /** 右摇杆：专用于列表滚动（dy>0 向下，<0 向上）。与扳机 Trigger 解耦。 */
    data class RightStick(val dy: Float) : GamepadEvent
    /** 左摇杆按压（LS / L3）：用于触发特定上下文动作（如统计页打开日期选择）。 */
    data object LeftStickPress : GamepadEvent
    /** X 键：切换当前焦点游戏的收藏（固定到精选区）。 */
    data object Favorite : GamepadEvent
    /** Y 键：打开 / 关闭搜索。 */
    data object Search : GamepadEvent
}

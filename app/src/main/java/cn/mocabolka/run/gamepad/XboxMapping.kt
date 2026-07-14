package cn.mocabolka.run.gamepad

import android.view.KeyEvent

/**
 * 手柄键码 → 领域事件映射。
 *
 * 以标准安卓手柄键码（Android Gamepad/Joystick API）为基准，兼容 Xbox HID。
 * 优先覆盖通用安卓手柄上报的常见键码，再补充 Xbox 扩展按键，
 * 确保普通安卓手柄无需额外配置即可获得完整导航体验。
 */
object XboxMapping {

/**
 * 判断某键码是否由本 Launcher 接管。
 *
 * 用于 [GamepadManager.handleKeyEvent] 消费**所有**手柄键的 ACTION_UP：
 * 只要键码属于我们管理的范围，无论 DOWN/UP 都必须消费（返回 true），
 * 否则孤立的 ACTION_UP 会泄漏给系统焦点/返回逻辑——某些设备会把在
 * Compose 焦点边界"无处可去"的方向键 UP 解释为返回，导致连续按键退出应用。
 *
 * 注意：D-pad 四向（UP/DOWN/LEFT/RIGHT）已从此列表移除——实体十字键放行给系统，
 * 导航仅由左摇杆（含 hat 十字键帽）通过 [GamepadManager.handleMotionEvent] 驱动。
 * D-pad CENTER 保留作为确认键。
 */
fun isHandledKey(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_BUTTON_B,
    KeyEvent.KEYCODE_BUTTON_X,
    KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_BUTTON_SELECT,
    KeyEvent.KEYCODE_BUTTON_MODE,
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_THUMBL,
    KeyEvent.KEYCODE_BUTTON_THUMBR -> true
    else -> false
}

fun mapKey(keyCode: Int, action: Int): GamepadEvent? {
    if (action == KeyEvent.ACTION_UP) return null
    return when (keyCode) {
        // ── 确认 / 返回 ──────────────────────────
        // （标准安卓手柄：A/B，DPAD_CENTER 也做确认）
        KeyEvent.KEYCODE_BUTTON_A -> GamepadEvent.Select
        KeyEvent.KEYCODE_DPAD_CENTER -> GamepadEvent.Select
        KeyEvent.KEYCODE_BUTTON_B -> GamepadEvent.Back

            // ── 菜单键（Select/Mode/Start）──────────
            // 不再映射为领域事件：Menu 键由 GamepadManager 直接做"长按退出"检测，
            // 短按不触发任何操作，避免误触。

            // ── 辅助按键 ────────────────────────────
            // X：收藏 /  Y：搜索
            KeyEvent.KEYCODE_BUTTON_X -> GamepadEvent.Favorite
            KeyEvent.KEYCODE_BUTTON_Y -> GamepadEvent.Search

            // ── 肩键 ────────────────────────────────
            KeyEvent.KEYCODE_BUTTON_L1 -> GamepadEvent.Shoulder(Side.LEFT)
            KeyEvent.KEYCODE_BUTTON_R1 -> GamepadEvent.Shoulder(Side.RIGHT)

            // ── 左摇杆按压（LS / L3）：触发上下文动作 ──
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadEvent.LeftStickPress
            // ── 右摇杆按压（RS / R3）：与 LS 配对的上下文动作 ──
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadEvent.RightStickPress
            // ── View 键（Xbox 双方块按钮 = SELECT）：上下文动作（统计页打开日期选择）──
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadEvent.ViewPress

            else -> null
        }
    }
}

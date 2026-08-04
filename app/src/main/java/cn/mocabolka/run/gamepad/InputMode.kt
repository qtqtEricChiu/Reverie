package cn.mocabolka.run.gamepad

/** 当前生效的输入模式（基于最近一次用户交互来源，而非物理连接状态）。 */
enum class InputMode {
    /** 触控模式：用户最近一次通过触摸屏幕交互。焦点指示器（含 Material3 原生涟漪）全部隐藏。 */
    Touch,
    /** 手柄模式：用户最近一次通过手柄按键 / 摇杆交互。焦点指示器正常显示。 */
    Gamepad
}

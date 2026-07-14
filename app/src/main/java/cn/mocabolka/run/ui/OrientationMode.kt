package cn.mocabolka.run.ui

import android.content.pm.ActivityInfo

/**
 * 屏幕方向锁定模式（嵌入设置的"强制旋屏"功能）。
 *
 * - [FOLLOW_SYSTEM]：不强制，跟随系统默认（关闭强制旋屏）。
 * - [FORCE_LANDSCAPE]：仅横屏，随陀螺仪在"左旋屏 / 右旋屏"间切换（横屏设备默认）。
 * - [FORCE_LANDSCAPE_LEFT]：固定左旋屏幕。
 * - [FORCE_LANDSCAPE_RIGHT]：固定右旋屏幕。
 * - [FORCE_PORTRAIT]：仅竖屏，随陀螺仪在"正竖屏 / 反竖屏"间切换（竖屏设备默认）。
 * - [FORCE_PORTRAIT_NORMAL]：固定正竖屏（顶部朝上）。
 * - [FORCE_PORTRAIT_REVERSE]：固定反向竖屏（顶部朝下）。
 * - [FORCE_SENSOR]：全向陀螺仪（横屏+竖屏都跟随）。
 */
enum class OrientationMode(val value: String) {
    FOLLOW_SYSTEM("system"),
    FORCE_LANDSCAPE("sensor_landscape"),
    FORCE_LANDSCAPE_LEFT("landscape"),
    FORCE_LANDSCAPE_RIGHT("reverse_landscape"),
    FORCE_PORTRAIT("sensor_portrait"),
    FORCE_PORTRAIT_NORMAL("portrait"),
    FORCE_PORTRAIT_REVERSE("reverse_portrait"),
    FORCE_SENSOR("sensor");

    fun toActivityInfo(): Int = when (this) {
        FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        FORCE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        FORCE_LANDSCAPE_LEFT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        FORCE_LANDSCAPE_RIGHT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        FORCE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        FORCE_PORTRAIT_NORMAL -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        FORCE_PORTRAIT_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        FORCE_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    companion object {
        fun fromValue(v: String?): OrientationMode =
            entries.firstOrNull { it.value == v } ?: FORCE_LANDSCAPE

        /** 下拉选项顺序（默认项排第一）。 */
        val SELECTOR_ORDER: List<OrientationMode> = listOf(
            FORCE_LANDSCAPE,
            FORCE_LANDSCAPE_RIGHT,
            FORCE_LANDSCAPE_LEFT,
            FORCE_PORTRAIT,
            FORCE_PORTRAIT_NORMAL,
            FORCE_PORTRAIT_REVERSE,
            FORCE_SENSOR,
            FOLLOW_SYSTEM
        )
    }

    /** 设置页下拉展示用的中文名称。 */
    val label: String
        get() = when (this) {
            FOLLOW_SYSTEM -> "关闭（跟随系统）"
            FORCE_LANDSCAPE -> "强制陀螺仪横屏"
            FORCE_LANDSCAPE_LEFT -> "强制左旋屏幕"
            FORCE_LANDSCAPE_RIGHT -> "强制右旋屏幕"
            FORCE_PORTRAIT -> "强制陀螺仪竖屏"
            FORCE_PORTRAIT_NORMAL -> "强制正向竖屏"
            FORCE_PORTRAIT_REVERSE -> "强制反向竖屏"
            FORCE_SENSOR -> "全向陀螺仪（横竖均随）"
        }
}

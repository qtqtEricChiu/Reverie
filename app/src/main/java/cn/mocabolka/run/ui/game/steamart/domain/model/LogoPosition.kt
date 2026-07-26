package cn.mocabolka.run.ui.game.steamart.domain.model

import org.json.JSONObject

/**
 * SteamDB 标志（logo）在横幅（library_hero）上的定位参数。
 */
data class LogoPosition(
    val pinnedPosition: String = "BottomLeft",
    val widthPct: Float = 0f,
    val heightPct: Float = 0f,
    /** true=自定义主题（保留完整 9 宫格）；false=SteamDB/API/WebView（仅适配 3 锚点）。 */
    val fromCustom: Boolean = false,
    /** Logo 尺寸档位倍率（所有来源均适用）：1.0=中等(默认)，0.6=极小，0.8=较小，1.2=较大，1.4=极大。每游戏独立。 */
    val logoScale: Float = 1f,
    /** 自定义主题「Logo 贴边」：true=裁掉位图四周透明边距，让真实内容顶满盒子边缘。每游戏独立，默认关。 */
    val logoCrop: Boolean = false
) {
    val hasSize: Boolean get() = widthPct > 0f && heightPct > 0f

    fun toJson(): String = JSONObject().apply {
        put("pinned_position", pinnedPosition)
        put("width_pct", widthPct)
        put("height_pct", heightPct)
        put("from_custom", fromCustom)
        put("logo_scale", logoScale)
        put("logo_crop", logoCrop)
    }.toString()

    companion object {
        val DEFAULT = LogoPosition("BottomLeft", 0f, 0f)

        /** 归一化 pinned_position：转小写并去除非字母字符（空格/连字符/下划线），便于容错匹配。 */
        fun normPin(s: String): String = s.lowercase().replace(Regex("[^a-z]"), "")

        fun fromJson(json: String): LogoPosition? = try {
            val o = JSONObject(json)
            val pin = o.optString("pinned_position", "BottomLeft").ifBlank { "BottomLeft" }
            val w = o.optDouble("width_pct", 0.0).toFloat()
            val h = o.optDouble("height_pct", 0.0).toFloat()
            // 旧数据无 from_custom 时，用 pinned_position 推断来源：
            // - 仅 SteamDB/API 三锚点之一（忽略大小写/空格/连字符）→ 视为 Steam 数据，走 3 锚点解析；
            // - 其余 9 宫格位置只可能来自自定义主题 → 走 9 宫格解析。
            // 这避免了「旧 WebView/API 数据被误判为自定义主题、又因 pinned_position 写法非标准而被
            // logoAlignFull 落进 else→Center（居中）」的回归。
            val knownSteam = setOf("bottomleft", "centercenter", "bottomcenter")
            val custom = if (o.has("from_custom")) o.optBoolean("from_custom", false)
                         else !knownSteam.contains(normPin(pin))
            val scale = if (o.has("logo_scale")) o.optDouble("logo_scale", 1.0).toFloat() else 1f
            val crop = if (o.has("logo_crop")) o.optBoolean("logo_crop", false) else false
            LogoPosition(pin, w, h, custom, scale, crop)
        } catch (_: Exception) {
            null
        }
    }
}

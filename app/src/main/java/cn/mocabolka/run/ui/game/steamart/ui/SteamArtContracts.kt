package cn.mocabolka.run.ui.game.steamart.ui

/**
 * SteamDB 美术资源页面的跨 Activity 参数契约（L2 美术资源获取页 → L3 提取/自定义）。
 */
object SteamArtContracts {
    /** 目标游戏包名（资源按此落地到 filesDir/steamart/<pkg>）。 */
    const val EXTRA_PACKAGE = "steamart.extra.PACKAGE"
    /** 目标游戏显示名。 */
    const val EXTRA_LABEL = "steamart.extra.LABEL"
    /** Steam appid（L3 提取页，已知 id 时直接提取）。 */
    const val EXTRA_APP_ID = "steamart.extra.APP_ID"
    /** 游戏名搜索词（L3 提取页，未知 id 时先搜索再提取）。 */
    const val EXTRA_QUERY = "steamart.extra.QUERY"
    /** 预设游戏的 LogoPosition（JSON 字符串），提取后回退定位用。 */
    const val EXTRA_LOGO_POSITION = "steamart.extra.LOGO_POSITION"
    /** 游戏页已计算并继承的主色（#AARRGGBB），子页面无美术资源时回退此色而非全局默认色。 */
    const val EXTRA_ACCENT = "steamart.extra.ACCENT"
    /** 父级（游戏页）确认存在整页背景图（page_bg_raw / hero 已落盘或回归），子页面据此在美术尚未落盘时也强制深色，避免浅色闪烁。 */
    const val EXTRA_HAS_PAGE_BG = "steamart.extra.HAS_PAGE_BG"

    /** Android 包名合法形态：字母开头的点分段，禁止路径分隔符/相对段。 */
    private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    /**
     * 安全净化包名（审计 SEC-S1）：该值来自 Intent extra 且被直接拼入
     * `filesDir/steamart/<pkg>` 文件路径。若不校验，恶意 extra（如 `../../shared_prefs`）
     * 可实现路径穿越读写任意私有目录。仅接受严格合法的 Android 包名，否则返回 null。
     */
    fun sanitizePackage(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        return if (PACKAGE_REGEX.matches(v)) v else null
    }
}

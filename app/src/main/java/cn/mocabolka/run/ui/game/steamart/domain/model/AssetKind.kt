package cn.mocabolka.run.ui.game.steamart.domain.model

/**
 * SteamDB 商店页可提取的美术资产种类。
 *
 * 匹配修复：
 *  - 保留原文源文件名：下载时取 URL 最后一段原样落地。
 *  - 保留国际化标记（如 _schinese），[AssetSelector] 通过 [preferredLang] 选择语言。
 *  - 尺寸优先 2x：选链时 `_2x` 优先于 1x。
 *  - 回滚机制：无国际化标记时回落到无语言名（见 [AssetSelector] 评分回退链）。
 *
 * 每种资产用 [keywords]（多个候选关键词）做子串匹配，取代原先脆弱的 `startsWith(keyword_)`，
 * 从而正确命中 `page_bg_raw.jpg`。
 * [PAGE_BG_RAW] 标记 [strictRaw]，强制要求文件名包含 `page_bg_raw` 且排除 `page_bg_v*`
 * 主题化变体。
 *
 * 匹配同时扫描「文件名 basename」与「URL 路径关键字」（见 [matchKind]），以兼容目录式资产 URL。
 *
 * 主页结果页与游戏页预览只使用 [FOR_GAME]（6 张），体积较小的 [HEADER] 不参与游戏页，故排除。
 *
 * `_blur` 排除：`library_hero_blur` / `library_header_blur` 等是 SteamDB 的高斯模糊背景层，
 * 从不是美术资源。早期 `contains("library_hero")` 子串匹配会把 blur 误判为 LIBRARY_HERO，
 * 进一步在 [AssetSelector] 中 schinese blur (+100) 击败 `_2x` (lang=null, +10) 成为"最佳"，
 * 导致抓到了 blur 图。一律在识别阶段排除（既不下载也不映射）。
 */
enum class AssetKind(
    /** 源站文件名关键词（用于从 URL / 文件名反推种类），可多个。 */
    val keywords: List<String>,
    /** 默认扩展名（仅作兜底）。 */
    val ext: String,
    /** Steam 原始标题（用于日志 / 调试）。 */
    val title: String,
    /** 中文展示名（用于结果页标签）。 */
    val label: String,
    /** 在游戏页内的展示顺序（越小越靠前）。 */
    val order: Int,
    /** 严格原始背景图：必须包含 `page_bg_raw`，且排除 `page_bg_v*` 主题化变体。 */
    val strictRaw: Boolean = false
) {
    LIBRARY_HERO(listOf("library_hero"), "jpg", "LIBRARY_HERO", "主视觉横幅", 0),
    HERO_CAPSULE(listOf("hero_capsule"), "jpg", "HERO_CAPSULE", "竖版海报", 1),
    LIBRARY_CAPSULE(listOf("library_capsule", "library_600x900", "capsule_616"), "jpg", "LIBRARY_CAPSULE", "方形封套", 2),
    CAPSULE_SMALL(listOf("capsule_231x87", "capsule_467x181", "capsule_small"), "jpg", "CAPSULE_SMALL", "小头图", 3),
    LIBRARY_HEADER(listOf("library_header", "library_2x", "header_2x", "header", "banner"), "jpg", "LIBRARY_HEADER", "列表头图", 4),
    LOGO(listOf("logo", "logo_", "logo."), "png", "LOGO", "标志", 5),
    PAGE_BG_RAW(listOf("page_bg_raw"), "jpg", "PAGE_BG_RAW", "背景原图", 6, strictRaw = true),
    HEADER(listOf("header", "banner"), "jpg", "HEADER", "小头图(占位)", -1);

    /**
     * 是否参与游戏页（即主页 6 图集 / 自定义主题 6 槽）。
     * - [HEADER]：仅列表头图占位，游戏页不直接渲染 → 排除。
     * - [CAPSULE_SMALL]：仅侧栏小图标（231x87），游戏页与自定义主题都不渲染 → 排除，
     *   否则它会挤进 FOR_GAME 把 PAGE_BG_RAW（背景原图）顶到第 7 位，被 2×3 UI 跳过，
     *   且自定义主题该槽因 customFileName 无此分支而误读写 library_hero.jpg（= 主视觉）。
     */
    val required: Boolean get() = this != HEADER && this != CAPSULE_SMALL

    companion object {
        /** 游戏页所需的 6 种资产，按展示顺序排列。 */
        val FOR_GAME: List<AssetKind> = entries.filter { it.required }.sortedBy { it.order }

        /** `_blur` 变体（高斯模糊背景层）从不是美术资源，识别时一律排除。 */
        private fun isBlurVariant(s: String): Boolean = s.contains("_blur")

        fun matchKind(urlOrName: String): AssetKind? {
            val raw = urlOrName.substringBefore('?')
            val name = raw.substringAfterLast('/')
            val path = raw.substringBeforeLast('/')
            // 早期 bug：library_hero_blur 被 contains("library_hero") 误判为 LIBRARY_HERO，
            // 且 schinese blur 会在 AssetSelector 中击败 _2x 入选。识别阶段直接排除。
            if (isBlurVariant(name) || isBlurVariant(path)) return null
            return entries.firstOrNull { k ->
                val hit = k.keywords.any { kw -> name.contains(kw) || path.contains(kw) }
                hit && !(k.strictRaw && raw.contains("page_bg_v"))
            }
        }

        fun fromFileName(name: String): AssetKind? {
            val base = name.substringBeforeLast('.').substringBefore('?')
            // 落盘的 blur 文件也不映射为任何美术种类（FileGameArtProvider 直接忽略）。
            if (isBlurVariant(base)) return null
            return entries.firstOrNull { k ->
                k.keywords.any { kw -> base == kw || base.startsWith("${kw}_") || base.contains(kw) }
                        && !(k.strictRaw && base.contains("page_bg_v"))
            }
        }
    }
}

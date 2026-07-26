package cn.mocabolka.run.ui.game.steamart.data.remote

import cn.mocabolka.run.ui.game.steamart.domain.model.AssetKind
import cn.mocabolka.run.ui.game.steamart.domain.model.SteamAsset
import java.net.URL

/**
 * CF-free 兜底资源构造器。
 *
 * 背景：整套美术提取原本【唯一依赖】SteamDB WebView 探针扫到 steamstatic 链接，商店 API 只是探针
 * 成功后的补充。一旦 Cloudflare 拦截 WebView / info 页 DOM 变动扫不到图，就没有任何退路，只能干等
 * 40s 报「Cloudflare 验证超时」（用户反复卡死的架构性单点故障）。
 *
 * 本对象直接用 appId 拼 Steam 官方 CDN 的【标准资源路径】。这些资源是纯静态 GET，
 * **不经过 Cloudflare 挑战**（挑战只挡 steamdb.info 的 HTML 页，不挡 shared.steamstatic.com 的图），
 * 因此可作为完全绕开 SteamDB WebView 的独立兜底通道。
 *
 * 权威 base（Steam 客户端 / 商店当前使用）：
 *   https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/<appId>/
 * 若能从商店 API 返回的 header_image / background_raw 推导出真实 host，则优先用推导值（更贴合实际 CDN）。
 */
object SteamCdnAssets {

    /** 兜底默认 base（当商店 API 不可用时）。 */
    private const val DEFAULT_ROOT =
        "https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps"

    /**
     * 从一批已知可用的资源 URL（商店 API 的 header_image / background_raw）里推导 `.../apps/<appId>` 前缀，
     * 从而拼出同 CDN host 的其它库资源；推导不出则回退默认 base。
     *
     * **关键修复（2026-07-25）**：商店 API 的 `header_image` 历史上落在
     * `shared.akamai.steamstatic.com`，而 akamai **不提供 `store_item_assets` 路径**（实测 HTTP 404，
     * 表现为 `shared.akamai.steamstatic.com` 反复 404 + DNS `result_code` 失败）。
     * 故仅当样本 host 落在可靠的 `cloudflare` / `fastly` CDN 时才采用其 host；否则（akamai 或未知）
     * 一律回退到 [DEFAULT_ROOT] 的 cloudflare host，并强制使用标准 `store_item_assets/steam` 路径，
     * 确保 library_600x900.jpg 等标准资源可被正确拉取。
     */
    fun deriveBase(appId: Long, vararg sampleUrls: String?): String {
        val marker = "/apps/$appId"
        for (u in sampleUrls) {
            if (u.isNullOrBlank()) continue
            val clean = u.substringBefore('?')
            val idx = clean.indexOf(marker)
            if (idx >= 0) {
                val host = runCatching { URL(clean.substring(0, idx)).host }.getOrNull().orEmpty()
                if (host.contains("cloudflare") || host.contains("fastly")) {
                    // 可靠 host：保留，并强制标准 store_item_assets/steam 路径（buildCandidates 仅拼该路径下的标准文件名）。
                    return "https://$host/store_item_assets/steam/$appId"
                }
            }
        }
        return "$DEFAULT_ROOT/$appId"
    }

    /** 各 AssetKind 的语言中立标准文件名（兜底 CDN 直拼用）。 */
    private val NEUTRAL_NAME: Map<AssetKind, String> = mapOf(
        AssetKind.LIBRARY_HERO to "library_hero.jpg",
        AssetKind.LIBRARY_CAPSULE to "library_600x900.jpg",
        AssetKind.LIBRARY_HEADER to "library_header.jpg",
        AssetKind.HERO_CAPSULE to "hero_capsule.jpg",
        AssetKind.CAPSULE_SMALL to "capsule_467x181.jpg",
        AssetKind.LOGO to "logo.png",
        AssetKind.HEADER to "header.jpg",
        AssetKind.PAGE_BG_RAW to "page_bg_raw.jpg"
    )

    /**
     * 存在 `_<lang>` 本地化变体的「带游戏标题文字」资产：封套 / 头图 / logo。
     * 这些图会印游戏标题，英文/中文两版视觉不同——用户切简体中文时必须优先取 `_schinese` 版，
     * 否则兜底落盘的就是「英文版界面」（正是无限暖暖走 CDN 兜底时的实际问题）。
     * 主视觉横幅（library_hero）与背景原图（page_bg_raw）通常语言中立，不做本地化以免徒增 404 往返。
     */
    private val LOCALIZABLE: Set<AssetKind> = setOf(
        AssetKind.LIBRARY_CAPSULE, AssetKind.LIBRARY_HEADER, AssetKind.HERO_CAPSULE,
        AssetKind.CAPSULE_SMALL, AssetKind.LOGO, AssetKind.HEADER
    )

    /** 该资产的语言中立标准文件名（如 library_600x900.jpg）；未收录则 null。 */
    fun neutralNameFor(kind: AssetKind): String? = NEUTRAL_NAME[kind]

    /** 在扩展名前插入 `_<lang>`：library_600x900.jpg → library_600x900_schinese.jpg。 */
    private fun localizedName(neutral: String, lang: String): String {
        val dot = neutral.lastIndexOf('.')
        return if (dot < 0) "${neutral}_$lang" else "${neutral.substring(0, dot)}_$lang${neutral.substring(dot)}"
    }

    /**
     * 构造游戏页所需资产候选。
     * 全部指定 [SteamAsset.forcedName] 为规范文件名，保证下载后 [AssetKind.fromFileName] 能正确回读分类。
     *
     * 本地化策略（2026-07-25 修「英文版界面」）：对 [LOCALIZABLE] 资产优先拼 `_<lang>` 本地化变体
     * （落地为 `<name>_<lang>.<ext>`），命中即为简体中文界面；若该变体 404，则由
     * [ExtractAssetsUseCase.fallbackFromStore] 回退到语言中立版补齐（见 buildRetryAssets）。
     *
     * @param lang          首选语言 token（如 `schinese`），来自 SteamLanguagePreference。
     * @param headerImage   商店 API 返回的 header 图 URL（作为 header 语言中立回退源）。
     * @param backgroundRaw 商店 API 返回的背景原图 URL（优先使用，API 已验证可用），缺失时构造 page_bg_raw.jpg。
     */
    fun buildCandidates(
        appId: Long,
        base: String,
        headerImage: String?,
        backgroundRaw: String?,
        lang: String
    ): List<SteamAsset> {
        val list = ArrayList<SteamAsset>(8)
        // 带标题文字的资产：优先本地化 _<lang> 变体（简体中文界面）。
        for (kind in listOf(
            AssetKind.LIBRARY_HERO, AssetKind.LIBRARY_CAPSULE, AssetKind.LIBRARY_HEADER,
            AssetKind.HERO_CAPSULE, AssetKind.CAPSULE_SMALL, AssetKind.LOGO, AssetKind.HEADER
        )) {
            val neutral = NEUTRAL_NAME[kind] ?: continue
            if (kind in LOCALIZABLE) {
                val loc = localizedName(neutral, lang)
                list.add(SteamAsset(kind, "$base/$loc", null, forcedName = loc))
            } else {
                list.add(SteamAsset(kind, "$base/$neutral", null, forcedName = neutral))
            }
        }
        // page_bg_raw：语言中立；优先用 API 的 background_raw（权威值），缺失时直拼标准 CDN 资源。
        val bg = backgroundRaw?.takeIf { it.isNotBlank() } ?: "$base/${NEUTRAL_NAME[AssetKind.PAGE_BG_RAW]}"
        list.add(SteamAsset(AssetKind.PAGE_BG_RAW, bg, null, forcedName = NEUTRAL_NAME[AssetKind.PAGE_BG_RAW]))
        return list
    }
}

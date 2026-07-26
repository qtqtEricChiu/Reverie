package cn.mocabolka.run.ui.game.steamart.domain

import cn.mocabolka.run.ui.game.steamart.domain.model.AssetKind
import cn.mocabolka.run.ui.game.steamart.domain.model.SteamAsset

/**
 * 从商店页扫描到的图片 URL 中，为每种资产挑选最优链接。
 */
class AssetSelector(
    private val preferredLangProvider: () -> String = { SteamLanguagePreference.currentToken }
) {
    private val langTokens = listOf(
        "schinese", "tchinese", "english", "japanese", "koreana", "korean", "french", "german",
        "spanish", "russian", "brazilian", "italian", "polish", "portuguese",
        "dutch", "swedish", "danish", "norwegian", "finnish", "czech", "hungarian",
        "romanian", "thai", "vietnamese", "ukrainian", "arabic", "turkish"
    )

    operator fun invoke(
        appId: Long,
        urls: List<String>,
        kinds: List<AssetKind> = AssetKind.FOR_GAME
    ): List<SteamAsset> {
        val preferredLang = preferredLangProvider()
        val parsed = urls.mapNotNull { url ->
            val kind = AssetKind.matchKind(url) ?: return@mapNotNull null
            if (kind !in kinds) return@mapNotNull null
            val name = url.substringAfterLast('/').substringBefore('?')
            val lang = langTokens.firstOrNull { name.contains("_$it") }
            val size = if (name.contains("_2x")) 2 else 1
            Cand(kind, lang, size, url)
        }

        val selected = kinds.mapNotNull { kind ->
            val candidates = parsed.filter { it.kind == kind }
            if (candidates.isEmpty()) return@mapNotNull null
            val best = candidates.maxWithOrNull(
                compareBy({ score(it.lang, it.size, preferredLang) }, { it.size == 2 })
            ) ?: return@mapNotNull null
            SteamAsset(kind, best.url, null)
        }.toMutableList()

        if (selected.none { it.kind == AssetKind.HERO_CAPSULE }) {
            selected.firstOrNull { it.kind == AssetKind.LIBRARY_CAPSULE }?.let { cap ->
                selected.add(SteamAsset(AssetKind.HERO_CAPSULE, cap.url, null, forcedName = "hero_capsule.jpg"))
            }
        }

        return selected
    }

    private fun score(lang: String?, size: Int, preferredLang: String): Int {
        var s = 0
        s += when {
            lang == preferredLang -> 100
            lang == null -> 10
            else -> 0
        }
        if (size == 2) s += 5
        return s
    }

    private data class Cand(val kind: AssetKind, val lang: String?, val size: Int, val url: String)
}

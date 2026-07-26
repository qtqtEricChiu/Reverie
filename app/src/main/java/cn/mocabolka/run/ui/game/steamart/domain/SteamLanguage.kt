package cn.mocabolka.run.ui.game.steamart.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Steam 商店资源语言选项。
 */
enum class SteamLanguage(
    val token: String,
    val abbr: String,
    val displayName: String
) {
    SIMPLIFIED_CHINESE("schinese", "SC", "简体中文"),
    TRADITIONAL_CHINESE("tchinese", "TC", "繁体中文"),
    ENGLISH("english", "EN", "英语"),
    KOREAN("koreana", "KR", "韩语"),
    JAPANESE("japanese", "JP", "日语");

    companion object {
        val DEFAULT = SIMPLIFIED_CHINESE

        fun fromToken(token: String): SteamLanguage =
            entries.firstOrNull { it.token.equals(token, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * 全局 Steam 资源语言偏好（可随时调整）。
 */
object SteamLanguagePreference {
    private val _current = MutableStateFlow(SteamLanguage.DEFAULT)
    val current = _current.asStateFlow()
    val currentToken: String get() = _current.value.token

    fun select(lang: SteamLanguage) {
        _current.value = lang
    }
}

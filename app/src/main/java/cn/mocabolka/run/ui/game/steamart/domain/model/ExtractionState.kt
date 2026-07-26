package cn.mocabolka.run.ui.game.steamart.domain.model

sealed interface ExtractionState {
    data object Idle : ExtractionState
    data object LoadingPage : ExtractionState
    data object WaitingForHuman : ExtractionState
    data class Extracting(val foundUrls: Int) : ExtractionState
    data class Downloading(val done: Int, val total: Int) : ExtractionState
    data class Done(
        val assets: List<SteamAsset>,
        val appName: String? = null,
        val logoPosition: String? = null
    ) : ExtractionState
    data class Error(val reason: String) : ExtractionState
}

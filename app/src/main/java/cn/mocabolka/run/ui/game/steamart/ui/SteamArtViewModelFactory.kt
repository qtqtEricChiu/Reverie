package cn.mocabolka.run.ui.game.steamart.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cn.mocabolka.run.ui.game.steamart.data.remote.AssetDownloader
import cn.mocabolka.run.ui.game.steamart.data.remote.StoreApiClient
import cn.mocabolka.run.ui.game.steamart.domain.AssetSelector
import cn.mocabolka.run.ui.game.steamart.domain.ExtractAssetsUseCase

class SteamArtViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val downloader = AssetDownloader(app)
        val storeApi = StoreApiClient()
        val selector = AssetSelector()
        val useCase = ExtractAssetsUseCase(
            context = app,
            downloader = downloader,
            storeApi = storeApi,
            selector = selector
        )
        return SteamArtViewModel(useCase, app) as T
    }
}

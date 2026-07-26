package cn.mocabolka.run.ui.game.steamart.ui

import android.app.Application
import cn.mocabolka.run.ui.game.steamart.data.remote.AssetDownloader
import cn.mocabolka.run.ui.game.steamart.data.remote.StoreApiClient
import cn.mocabolka.run.ui.game.steamart.data.webview.SteamWebViewFactory
import cn.mocabolka.run.ui.game.steamart.domain.AssetSelector
import cn.mocabolka.run.ui.game.steamart.domain.ExtractAssetsUseCase

/**
 * 提取功能手动 DI 容器（与 Reverie 主项目一致，不使用 Hilt）。
 * 在首个提取页 [androidx.activity.ComponentActivity] 的 onCreate 中调用 [ensure]。
 */
object SteamArtGraph {
    lateinit var webViewFactory: SteamWebViewFactory
    lateinit var downloader: AssetDownloader
    lateinit var storeApi: StoreApiClient
    lateinit var selector: AssetSelector
    lateinit var useCase: ExtractAssetsUseCase
    lateinit var viewModelFactory: SteamArtViewModelFactory

    fun ensure(app: Application) {
        if (::useCase.isInitialized) return
        webViewFactory = SteamWebViewFactory()
        downloader = AssetDownloader(app)
        storeApi = StoreApiClient()
        selector = AssetSelector()
        useCase = ExtractAssetsUseCase(
            context = app,
            downloader = downloader,
            storeApi = storeApi,
            selector = selector
        )
        viewModelFactory = SteamArtViewModelFactory(app)
    }
}

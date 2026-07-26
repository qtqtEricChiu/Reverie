package cn.mocabolka.run.ui.game.steamart.data.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

class SteamWebViewFactory {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        bridge: ExtractionBridge,
        client: CfWebViewClient
    ): WebView {
        val webView = WebView(context)
        return webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                // 与 demo 一致：使用设备默认 UA 并仅移除 "; wv" 标记，让 WebView 表现为普通浏览器，
                // 与 TLS/JA3 指纹一致，Cloudflare 才会放行。userAgentString 为平台类型，判空防罕见 ROM NPE。
                userAgentString = userAgentString?.replace("; wv", "") ?: ""
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }

            webViewClient = client
            webChromeClient = WebChromeClient()

            addJavascriptInterface(bridge, "AndroidBridge")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createSearch(
        context: Context,
        query: String,
        client: SearchWebViewClient,
        bridge: SearchBridge
    ): WebView {
        val wv = WebView(context)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = userAgentString?.replace("; wv", "") ?: ""
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            databaseEnabled = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
        wv.addJavascriptInterface(bridge, SearchBridge.NAME)
        wv.webViewClient = client
        wv.loadUrl(searchUrl(query))
        return wv
    }

    companion object {
        const val BASE = "https://steamdb.info/app/"
        fun infoUrl(appId: Long) = "$BASE$appId/info/"
        fun searchUrl(query: String) =
            "https://steamdb.info/search/?a=all&q=${java.net.URLEncoder.encode(query, "UTF-8")}&app_tab=info"
    }
}

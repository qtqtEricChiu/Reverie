package cn.mocabolka.run.ui.game.steamart.data.webview

import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 搜索页桥：JS 探针把解析出的 Steam appid 回传。
 */
class SearchBridge(private val onResolved: (Long) -> Unit) {
    @JavascriptInterface
    fun onResolved(id: String) {
        id.toLongOrNull()?.let { onResolved(it) }
    }

    companion object {
        const val NAME = "AppBridge"
    }
}

/**
 * 搜索页 WebView 客户端：
 *  - 主路径：从导航 URL 直接解析 appid（用户点击搜索结果中的游戏 → URL 变成 /app/<id>
 *    → 进入详情页即解析并触发自动提取），可靠且不依赖 JS 探针；
 *  - 兜底：仅在已处于 app 详情页时跑 JS 探针；搜索结果页不自动解析首个游戏，避免误触发。
 */
class SearchWebViewClient(private val bridge: SearchBridge) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        idFromUrl(url)?.let { bridge.onResolved(it.toString()) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // 仅 app 详情页跑探针兜底；搜索结果页不自动解析首个游戏
        if (idFromUrl(url) != null) view?.evaluateJavascript(SEARCH_PROBE, null)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString()
        idFromUrl(url)?.let { bridge.onResolved(it.toString()) }
        return !SteamWebViewPolicy.isAllowedTopLevelNavigation(url)
    }

    companion object {
        private val APP_ID_RE = Regex("/app/(\\d+)")
        private fun idFromUrl(url: String?): Long? {
            val m = APP_ID_RE.find(url ?: return null) ?: return null
            return m.groupValues[1].toLongOrNull()
        }
        private const val SEARCH_PROBE = """
(function(){
  try {
    var a = document.querySelector('a.app.go-to-store');
    if (a && a.href) {
      var m = a.href.match(/\\/app\\/(\\d+)\\/?/);
      if (m) { AppBridge.onResolved(m[1]); return; }
    }
    var links = document.querySelectorAll('a[href*="/app/"]');
    for (var i=0;i<links.length;i++){
      var m = links[i].href.match(/\\/app\\/(\\d+)\\/?/);
      if (m) { AppBridge.onResolved(m[1]); return; }
    }
  } catch(e){}
})();
"""
    }
}

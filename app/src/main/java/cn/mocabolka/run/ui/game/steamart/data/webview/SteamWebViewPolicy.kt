package cn.mocabolka.run.ui.game.steamart.data.webview

import android.net.Uri

/**
 * SteamDB 美术提取 WebView 的导航安全策略。
 *
 * 顶层导航（[android.webkit.WebViewClient.shouldOverrideUrlLoading]）只允许官方域，
 * 其余一律拦截。这样即便 Cloudflare 挑战页、注入链接或重定向试图把 WebView 带离
 * SteamDB，也会被拒，避免用户在不知情下被带往任意外部站点（钓鱼 / 恶意跳转）。
 *
 * 注意：子资源（图片 / JS / CSS）由 JS 探针 [CfWebViewClient] 内的 isAssetHost 约束，
 * 此处只守「顶层跳转」这一最关键边界。商店 appdetails 兜底走 JDK HttpURLConnection，
 * 不经过本 WebView，故无需放行 store.steampowered.com。
 */
object SteamWebViewPolicy {
    private val ALLOWED_HOSTS = setOf("steamdb.info", "www.steamdb.info")

    /** 是否允许该 URL 作为 WebView 顶层导航目标。 */
    fun isAllowedTopLevelNavigation(url: String?): Boolean {
        if (url == null) return false
        val uri = try { Uri.parse(url) } catch (_: Exception) { return false }
        val scheme = uri.scheme ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        val h = host.lowercase().trim()
        return ALLOWED_HOSTS.any { h == it || h.endsWith(".$it") }
    }
}

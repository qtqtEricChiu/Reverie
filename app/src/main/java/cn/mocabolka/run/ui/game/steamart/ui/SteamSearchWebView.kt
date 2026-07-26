package cn.mocabolka.run.ui.game.steamart.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.CookieManager
import cn.mocabolka.run.ui.game.steamart.data.webview.SearchBridge
import cn.mocabolka.run.ui.game.steamart.data.webview.SearchWebViewClient
import cn.mocabolka.run.ui.game.steamart.data.webview.SteamWebViewFactory

/**
 * 搜索页 WebView：加载 SteamDB 搜索结果，自动解析游戏 appid。
 */
@Composable
fun SteamSearchWebView(
    query: String,
    onResolved: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val bridge = remember(query) { SearchBridge(onResolved) }
    val client = remember(query) { SearchWebViewClient(bridge) }
    val wv = remember(query) {
        SteamArtGraph.webViewFactory.createSearch(ctx, query, client, bridge)
    }
    DisposableEffect(wv) {
        onDispose {
            // 搜索 WebView 同样独立：离开即彻底停掉并清除，仅保留 cookie。
            try { wv.stopLoading() } catch (_: Throwable) {}
            try { wv.clearCache(true) } catch (_: Throwable) {}
            try { wv.destroy() } catch (_: Throwable) {}
            // 仅 flush Cookie：保留提取 WebView 的 Cloudflare 验证状态（DOM Storage），
            // 不再 deleteAllData()——否则会抹掉并发运行的提取 WebView 的 CF 状态，
            // 导致下次提取重新挑战 → 卡顿 → 漏抓 → 转保底（与提取 WebView 的清理策略对齐）。
            try { CookieManager.getInstance().flush() } catch (_: Throwable) {}
        }
    }
    AndroidView(factory = { wv }, modifier = modifier.fillMaxSize())
}

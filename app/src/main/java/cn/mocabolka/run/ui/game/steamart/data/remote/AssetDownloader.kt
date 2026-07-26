package cn.mocabolka.run.ui.game.steamart.data.remote

import android.util.Log
import android.webkit.CookieManager
import cn.mocabolka.run.ui.game.steamart.domain.model.SteamAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 下载工具：把 [SteamAsset] 链接原样落地到磁盘。
 * 底层使用 OkHttp（自动重定向/keep-alive/连接池），替换了早期的 JDK HttpURLConnection。
 *
 * 性能优化（2026-07-24）：
 *  - 同一主机（host）的资产串行下载、复用底层 TCP+TLS 长连接（keep-alive，由 OkHttp 连接池自动复用）；
 *  - 不同主机的资产并行下载（受 [CONCURRENCY] 信号量限流），消除逐个资产的握手空耗，
 *    整体下载耗时由「Σ(握手+传输)」降为「max(各主机链路)」，体感显著加快。
 */
class AssetDownloader(context: android.content.Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONN_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun download(
        assets: List<SteamAsset>,
        outDir: File,
        onProgress: suspend (done: Int, total: Int) -> Unit
    ): Boolean {
        if (!outDir.exists()) outDir.mkdirs()
        val total = assets.size
        if (total == 0) return true
        val done = AtomicInteger(0)
        val ok = AtomicBoolean(true)

        val byHost = assets.groupBy { hostOf(it.url) }
        coroutineScope {
            val sem = Semaphore(CONCURRENCY)
            byHost.values.map { group ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        for (asset in group) {
                            if (!downloadOne(asset, outDir)) ok.set(false)
                            val d = done.incrementAndGet()
                            onProgress(d, total)
                        }
                    }
                }
            }.awaitAll()
        }
        return ok.get()
    }

    /**
     * 尽力下载（CF-free 兜底专用）：与 [download] 同样按主机分组并行 + keep-alive 复用，
     * 但【不因个别资产 404/失败而整体判负】。
     */
    suspend fun downloadBestEffort(assets: List<SteamAsset>, outDir: File): Int {
        if (!outDir.exists()) outDir.mkdirs()
        if (assets.isEmpty()) return 0
        val okCount = AtomicInteger(0)
        val byHost = assets.groupBy { hostOf(it.url) }
        coroutineScope {
            val sem = Semaphore(CONCURRENCY)
            byHost.values.map { group ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        for (asset in group) {
                            if (downloadOne(asset, outDir)) okCount.incrementAndGet()
                        }
                    }
                }
            }.awaitAll()
        }
        return okCount.get()
    }

    private suspend fun downloadOne(asset: SteamAsset, outDir: File): Boolean {
        val url = asset.url ?: return false
        val name = asset.forcedName
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "${asset.kind.name}.${asset.kind.ext}" }
        val target = File(outDir, name)
        return try {
            withContext(Dispatchers.IO) {
                val cookie = try { CookieManager.getInstance().getCookie(url) } catch (_: Exception) { null }
                val req = Request.Builder().url(url).header("User-Agent", UA).apply {
                    if (!cookie.isNullOrBlank()) header("Cookie", cookie)
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "下载失败 ${asset.kind}: HTTP ${resp.code}")
                        return@withContext false
                    }
                    val tmp = File(outDir, "$name.tmp")
                    resp.body?.byteStream()?.use { ins ->
                        tmp.outputStream().use { outs -> ins.copyTo(outs, COPY_BUFFER) }
                    }
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "下载异常 ${asset.kind}: ${e.message}")
            false
        }
    }

    private fun hostOf(url: String?): String =
        try { url?.let { URL(it).host } ?: "unknown" } catch (_: Exception) { "unknown" }

    companion object {
        private const val TAG = "SteamAssetDownloader"
        private const val CONN_TIMEOUT_SEC = 30L
        private const val READ_TIMEOUT_SEC = 30L
        private const val COPY_BUFFER = 8 * 1024
        /** 并行下载的并发上限：兼顾提速与移动网络下带宽/连接数限制。 */
        private const val CONCURRENCY = 4
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}

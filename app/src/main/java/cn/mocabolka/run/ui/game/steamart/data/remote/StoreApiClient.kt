package cn.mocabolka.run.ui.game.steamart.data.remote

import android.util.Log
import cn.mocabolka.run.ui.game.steamart.domain.SteamLanguagePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 商店 API 兜底：当 SteamDB 页面刮取未拿到 page_bg_raw / hero_capsule / logo 时，
 * 直接请求 store.steampowered.com/api/appdetails 取 background_raw / name / logo / logo_position。
 * 使用 JDK 内置 HttpURLConnection。
 */
class StoreApiClient {
    data class Result(
        val backgroundRaw: String? = null,
        val name: String? = null,
        val logoUrl: String? = null,
        val logoPosition: String? = null,
        /** 商店 header 图 URL：CF-free 兜底用它推导 CDN base（见 SteamCdnAssets.deriveBase）。 */
        val headerImage: String? = null
    )

    suspend fun fetch(appId: Long): Result? = withContext(Dispatchers.IO) {
        val lang = SteamLanguagePreference.currentToken
        val url = "$API_BASE?appids=$appId&cc=$COUNTRY_CODE&l=$lang"
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            // try/finally 保证 readText 抛异常时连接也能释放，避免连接泄漏
            val json = try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    Log.w(TAG, "商店 API 失败: HTTP $code")
                    return@withContext null
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val node = JSONObject(json).optJSONObject(appId.toString()) ?: return@withContext null
            if (!node.optBoolean("success")) return@withContext null
            val data = node.optJSONObject("data") ?: return@withContext null
            Result(
                backgroundRaw = data.optString("background_raw").takeIf { it.isNotBlank() },
                name = data.optString("name").takeIf { it.isNotBlank() },
                logoUrl = data.optString("logo").takeIf { it.isNotBlank() },
                logoPosition = data.optJSONObject("logo_position")?.toString(),
                headerImage = data.optString("header_image").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.w(TAG, "商店 API 异常: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "StoreApiClient"
        private const val API_BASE = "https://store.steampowered.com/api/appdetails"
        private const val COUNTRY_CODE = "cn"
        private const val TIMEOUT_MS = 15000
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}

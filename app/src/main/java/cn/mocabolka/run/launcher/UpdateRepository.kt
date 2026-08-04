package cn.mocabolka.run.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 远端版本信息模型：对应仓库 `update/version.json`（由 CI workflow 在每次 Release 时自动生成推回）。
 * 解析用 Android 内置 org.json，不引入 kotlinx-serialization 额外依赖。
 */
data class RemoteVersion(
    /** 版本名，如 "v1.2.0"。 */
    val version: String,
    /** 版本号，与 BuildConfig.VERSION_CODE 对比。 */
    val versionCode: Int,
    /** 更新说明（可为空，通常指向 Releases 页面）。 */
    val changelog: String,
    /** 下载页链接（GitHub Releases tag 页）。 */
    val downloadUrl: String
)

/**
 * 应用内版本检测仓库（2026-07-30）。
 *
 * 核心思路：**不调用 GitHub REST API**（60 次/小时/IP 速率限制），而是读取固定路径的
 * 轻量 raw JSON（无速率限制、无需 Token）：
 *   https://raw.githubusercontent.com/qtqtEricChiu/Reverie/main/update/version.json
 *
 * 策略：
 *  - 距上次成功检查 < 6h → 跳过（SharedPreferences 记录，进程重建后仍有效）；
 *  - 网络失败 → 静默返回 null，不打扰用户，且不记录检查时间（下次启动可重试）；
 *  - 远端 versionCode > 本地 且 ≠ 用户已跳过的版本 → 返回 [RemoteVersion] 供 UI 弹窗。
 */
class UpdateRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * 启动时检查更新。满足「远端更新且未被跳过」才返回非 null。
     * 全程静默容错：任何网络/解析异常都返回 null，绝不抛出。
     */
    suspend fun checkForUpdate(currentVersionCode: Int): RemoteVersion? = withContext(Dispatchers.IO) {
        if (!shouldCheck()) return@withContext null
        val remote = fetchRemoteVersion() ?: return@withContext null
        // 仅在成功取到远端后记录检查时间：失败不计时，下次启动可立即重试。
        recordCheck()
        val skipped = prefs.getInt(KEY_SKIPPED_VERSION, -1)
        if (remote.versionCode > currentVersionCode && remote.versionCode != skipped) remote else null
    }

    /** 用户点「跳过此版本」：持久化该版本号，后续同版本不再提示。 */
    fun skipVersion(versionCode: Int) {
        prefs.edit().putInt(KEY_SKIPPED_VERSION, versionCode).apply()
    }

    /** 距上次成功检查是否已超过间隔（6h）。 */
    private fun shouldCheck(): Boolean {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL_MS
    }

    private fun recordCheck() {
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /** GET version.json 并解析；任何异常静默返回 null。 */
    private fun fetchRemoteVersion(): RemoteVersion? = try {
        val request = Request.Builder().url(VERSION_JSON_URL).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "版本检查失败：HTTP ${resp.code}")
                null
            } else {
                val body = resp.body?.string()
                if (body.isNullOrBlank()) null else parse(body)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "版本检查异常（静默）：${e.message}")
        null
    }

    private fun parse(body: String): RemoteVersion? = try {
        val json = JSONObject(body)
        RemoteVersion(
            version = json.optString("version"),
            versionCode = json.optInt("versionCode", -1),
            changelog = json.optString("changelog"),
            downloadUrl = json.optString("downloadUrl")
        ).takeIf { it.versionCode > 0 }
    } catch (e: Exception) {
        Log.w(TAG, "version.json 解析失败（静默）：${e.message}")
        null
    }

    companion object {
        private const val TAG = "UpdateRepository"
        private const val PREFS_NAME = "update_check"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val KEY_SKIPPED_VERSION = "skipped_version_code"
        private const val CHECK_INTERVAL_MS = 6L * 3600_000L
        private const val TIMEOUT_SEC = 10L
        private const val VERSION_JSON_URL =
            "https://raw.githubusercontent.com/qtqtEricChiu/Reverie/main/update/version.json"
    }
}

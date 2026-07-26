package cn.mocabolka.run.ui.game.steamart.data.webview

import android.util.Log
import android.webkit.JavascriptInterface
import cn.mocabolka.run.ui.game.steamart.domain.model.LogoPosition
import org.json.JSONArray

class ExtractionBridge(
    private val onLinks: (List<String>) -> Unit,
    private val onChallenge: () -> Unit,
    private val onLogoPosition: (LogoPosition) -> Unit = {},
    private val onAppName: (String) -> Unit = {},
    private val onAdultGate: (Boolean) -> Unit = {},
    private val onStructuredAssets: (String) -> Unit = {}
) {
    @JavascriptInterface
    fun onLinks(json: String) {
        val list = mutableListOf<String>()
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { list.add(it) }
            }
        }
        // 2026-07-25：debug 页 verdict 用 "onLinks" 关键字判定探针生效；保留此 Log 便于诊断链路。
        Log.i(TAG, "[ProbeBridge] onLinks: 收到 ${list.size} 条链接")
        onLinks(list)
    }

    @JavascriptInterface
    fun onChallenge() {
        Log.i(TAG, "[ProbeBridge] onChallenge: 探针检测到 Cloudflare 挑战")
        onChallenge()
    }

    @JavascriptInterface
    fun onAdultGate(present: Boolean) {
        Log.i(TAG, "[ProbeBridge] onAdultGate: present=$present")
        onAdultGate(present)
    }

    @JavascriptInterface
    fun onLogoPosition(json: String) {
        LogoPosition.fromJson(json)?.let { onLogoPosition(it) }
    }

    @JavascriptInterface
    fun onAppName(name: String) {
        val t = name.trim()
        if (t.isNotBlank()) onAppName(t)
    }

    @JavascriptInterface
    fun onStructuredAssets(json: String) {
        // 2026-07-25：Plan B 结构化源解析报告（每项含 asset/url/picked/options），供 debug verdict 强信号。
        Log.i(TAG, "[ProbeBridge] onStructuredAssets: $json")
        onStructuredAssets(json)
    }

    companion object {
        private const val TAG = "ExtractionBridge"
    }
}
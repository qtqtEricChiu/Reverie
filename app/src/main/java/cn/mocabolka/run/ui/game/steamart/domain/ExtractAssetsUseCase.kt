package cn.mocabolka.run.ui.game.steamart.domain

import android.content.Context
import android.util.Log
import cn.mocabolka.run.ui.game.steamart.data.remote.AssetDownloader
import cn.mocabolka.run.ui.game.steamart.data.remote.SteamCdnAssets
import cn.mocabolka.run.ui.game.steamart.data.remote.StoreApiClient
import cn.mocabolka.run.ui.game.steamart.domain.model.AssetKind
import cn.mocabolka.run.ui.game.steamart.domain.model.LogoPosition
import cn.mocabolka.run.ui.game.steamart.domain.model.ExtractionState
import cn.mocabolka.run.ui.game.steamart.domain.model.SteamAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val STORE_API_TIMEOUT_MS = 15000L

/**
 * 关键美术资产：缺失即视为提取失败。
 * - CAPSULE_SMALL（小头图 231x87）不计入——仅用于小图标/侧栏，缺失不影响主 banner。
 * - HEADER / LIBRARY_HEADER 不计入——仅用于列表头图/占位，游戏页不直接依赖，
 *   像 Minecraft 的 header 文件名为 banner.jpg 等非标准命名的游戏，即使下载成功也可能被
 *   fromFileName 漏判（read/write 文件名不对称），误报「提取失败」而游戏页实际已更新。
 *   （2026-07-26 修：Minecraft 等缺标准 header 文件名却已成功更新全部资源的割裂体验）
 */
private val CRITICAL_KINDS = listOf(
    AssetKind.LIBRARY_HERO, AssetKind.PAGE_BG_RAW
)

/**
 * 美术资源提取用例。
 *
 * 探针优先（WebView 捕获的链接） + 商店 API 兜底（page_bg_raw / logo / logo_position）。
 * 下载落地目录按 [packageName] 区分（即目标游戏的包名），与 Reverie 游戏页资源体系直接对接。
 */
class ExtractAssetsUseCase(
    private val context: Context,
    private val downloader: AssetDownloader,
    private val storeApi: StoreApiClient,
    private val selector: AssetSelector
) {
    operator fun invoke(
        appId: Long,
        packageName: String,
        urls: List<String>,
        presetLogoPosition: LogoPosition? = null,
        /** WebView 探针抓到的 logo 定位（实时读取，优先级最高）。 */
        webViewLogoPositionProvider: () -> LogoPosition? = { null }
    ): Flow<ExtractionState> = channelFlow {
        send(ExtractionState.Extracting(urls.size))
        ExtractionTiming.mark("selectStart")
        val selected = selector(appId, urls)
        ExtractionTiming.mark("selectEnd")
        Log.i(TAG, "[Probe] 原始链接 ${urls.size} 条 → AssetSelector 选中 ${selected.size} 个 SteamAsset")
        if (selected.isEmpty()) {
            ExtractionTiming.markAborted()
            send(ExtractionState.Error("未找到可提取的美术资源"))
            return@channelFlow
        }
        // SEC-S1 纵深防御：规范化路径必须落在 steamart 基目录内，阻断包名路径穿越
        val baseDir = File(context.filesDir, "steamart")
        val outDir = File(baseDir, packageName)
        if (!outDir.canonicalPath.startsWith(baseDir.canonicalPath + File.separator)) {
            ExtractionTiming.markAborted()
            send(ExtractionState.Error("非法包名，已拒绝写入"))
            return@channelFlow
        }
        // 清空上一次提取残留：避免文件名变体（_chinese / _2x / 旧 1x）不同导致新旧文件共存，
        // 干扰 Resolver 的 File 槽位识别与游戏页显示（实测千恋万花+Undertale 同包名下混出 13 张图）。
        // 注：清空前先捕获已有 logo 定位（来自此前导入/提取落盘的 meta.json），确保其优先级高于下方预设兜底，
        // 满足「预设必须受导入的新美术资源覆盖」——即使本次探针与商店 API 都拿不到定位，也不会用预设覆盖已导入的定位。
        val existingLogoJson = runCatching {
            val f = File(outDir, "meta.json")
            if (f.exists()) org.json.JSONObject(f.readText()).optString("logoPosition").takeIf { it.isNotBlank() } else null
        }.getOrNull()
        if (outDir.exists()) outDir.listFiles()?.forEach { it.delete() }
        ExtractionTiming.mark("downloadStart")
        val ok = downloader.download(selected, outDir) { done, total ->
            send(ExtractionState.Downloading(done, total))
        }
        ExtractionTiming.mark("downloadEnd")
        if (!ok) {
            // 方案 C（2026-07-25）：仅当「关键资产」仍缺失才判定失败；个别非关键资源
            // （如某游戏无 capsule_231x87 小头图）下载失败不应误报整体失败，banner 已成功更新。
            val missingCritical = CRITICAL_KINDS.filter { !hasFile(outDir, it) }
            if (missingCritical.isNotEmpty()) {
                val names = missingCritical.joinToString("、") { it.label }
                send(ExtractionState.Error("缺少关键美术资源：$names"))
                return@channelFlow
            }
            Log.w(TAG, "[Probe] 个别非关键资源未下载成功，但关键资产已齐全，按成功定稿")
        }

        // 兜底：补齐探针可能漏掉的 page_bg_raw / logo。
        // 提速（2026-07-25）：探针路径通常已含全部资产，无条件调用商店 API 会阻塞最多 15s，
        // 导致「7/7 下载完后仍空等约 10s」。仅当真正缺 page_bg_raw / logo 时才联网取商店 API。
        val needsStore = !hasFile(outDir, AssetKind.PAGE_BG_RAW) || !hasFile(outDir, AssetKind.LOGO)
        val store = if (needsStore) {
            ExtractionTiming.markStoreStart()
            fetchStoreApi(appId).also { ExtractionTiming.markStoreEnd() }
        } else {
            ExtractionTiming.markStoreSkipped()
            null
        }
        val extras = mutableListOf<SteamAsset>()
        if (store?.backgroundRaw != null && !hasFile(outDir, AssetKind.PAGE_BG_RAW)) {
            extras.add(SteamAsset(AssetKind.PAGE_BG_RAW, store.backgroundRaw, null))
        }
        if (store?.logoUrl != null && !hasFile(outDir, AssetKind.LOGO)) {
            extras.add(SteamAsset(AssetKind.LOGO, store.logoUrl, null))
        }
        if (extras.isNotEmpty()) {
            downloader.download(extras, outDir) { _, _ -> }
        }

        // 持久化 logoPosition 优先级链：
        // WebView 探针 > 商店 API > 已导入定位(existingLogoJson) > 预设兜底(presetLogoPosition)
        // 即任何真实导入/提取得到的定位都覆盖预设；预设仅在所有真实来源都缺失时才生效。
        val logoJson = webViewLogoPositionProvider()?.toJson()
            ?: store?.logoPosition
            ?: existingLogoJson
            ?: presetLogoPosition?.toJson()
        if (logoJson != null) writeLogoPositionMeta(outDir, logoJson)

        val assets = buildArt(outDir)
        ExtractionTiming.markDone()
        send(ExtractionState.Done(assets, store?.name, store?.logoPosition))
    }

    /**
     * CF-free 独立兜底：完全绕开 SteamDB WebView，用 appId 直连 Steam 官方 CDN + 商店 API 提取美术。
     *
     * 触发时机：[SteamArtViewModel] 在探针 12s 内未回传链接（CF 静默拦截 / info 页无 steamstatic 链接）
     * 或 40s CF 总兜底超时后调用，完全绕开被 Cloudflare 挡住的 WebView。
     * 原理：Steam 官方 CDN（shared.steamstatic.com/store_item_assets）上的库资源是纯静态 GET，
     * **不经过 Cloudflare 挑战**，因此即使 SteamDB 站点被 CF 挡住，这条通道仍可拿到资源，
     * 彻底消除"WebView 一被挡就永久卡死 / 40s 超时无退路"的架构性单点故障。
     *
     * 采用尽力下载（[AssetDownloader.downloadBestEffort]）：冷门资产（如部分游戏无 hero_capsule）
     * 允许缺失，只要拿到 ≥1 张有效资源即视为成功。
     */
    fun fallbackFromStore(
        appId: Long,
        packageName: String,
        presetLogoPosition: LogoPosition? = null,
        webViewLogoPositionProvider: () -> LogoPosition? = { null }
    ): Flow<ExtractionState> = channelFlow {
        send(ExtractionState.Extracting(0))
        val baseDir = File(context.filesDir, "steamart")
        val outDir = File(baseDir, packageName)
        if (!outDir.canonicalPath.startsWith(baseDir.canonicalPath + File.separator)) {
            ExtractionTiming.markAborted()
            send(ExtractionState.Error("非法包名，已拒绝写入"))
            return@channelFlow
        }
        ExtractionTiming.markStoreStart()
        val store = fetchStoreApi(appId)
        ExtractionTiming.markStoreEnd()
        val base = SteamCdnAssets.deriveBase(appId, store?.headerImage, store?.backgroundRaw)
        val lang = SteamLanguagePreference.currentToken
        val candidates = SteamCdnAssets.buildCandidates(appId, base, store?.headerImage, store?.backgroundRaw, lang)
        if (candidates.isEmpty()) {
            ExtractionTiming.markAborted()
            send(ExtractionState.Error("Cloudflare 拦截且无法构造备用链接，请稍后重试"))
            return@channelFlow
        }
        if (outDir.exists()) outDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "[Fallback] 候选 ${candidates.size} 个（lang=$lang, base=$base）")
        send(ExtractionState.Downloading(0, candidates.size))
        ExtractionTiming.mark("downloadStart")
        var ok = downloader.downloadBestEffort(candidates, outDir)
        ExtractionTiming.mark("downloadEnd")
        Log.i(TAG, "[Fallback] 首轮下载成功 ${ok}/${candidates.size}")

        // 本地化优先回退：凡带标题文字的资产（封套/头图/logo）首选 `_<lang>` 本地化变体，
        // 若该变体 404（部分游戏无对应语言版），改试语言中立版补齐——避免「简体中文缺、只剩英文版」的空槽。
        // 同时兼顾 header / page_bg_raw 的 API↔CDN 单边 404 补偿，杜绝「只拿到几张却谎报成功」。
        val retryable = listOf(
            AssetKind.LIBRARY_CAPSULE, AssetKind.LIBRARY_HEADER, AssetKind.HERO_CAPSULE,
            AssetKind.CAPSULE_SMALL, AssetKind.LOGO, AssetKind.HEADER, AssetKind.PAGE_BG_RAW
        )
        val retryMissing = retryable.filter { !hasFile(outDir, it) }
        if (retryMissing.isNotEmpty()) {
            val retries = buildRetryAssets(retryMissing, base, store)
            if (retries.isNotEmpty()) {
                Log.i(TAG, "[Fallback] 本地化变体缺失 ${retryMissing.size} 种（${retryMissing.joinToString { it.label }}），回退语言中立")
                send(ExtractionState.Downloading(candidates.size - retryMissing.size, candidates.size))
                val okBefore = ok
                ok += downloader.downloadBestEffort(retries, outDir)
                Log.i(TAG, "[Fallback] 回退重试成功 ${ok - okBefore}/${retries.size}（累计 ${ok}/${candidates.size + retries.size}）")
            }
        }

        // 关键资源判定：以下资产缺失即视为提取失败（不再静默成功）。
        val critical = CRITICAL_KINDS
        // 复核：关键资源仍缺失 → 明确报错（不再静默成功，满足「资源缺失时抛出明确异常」）。
        val stillMissing = critical.filter { !hasFile(outDir, it) }
        if (stillMissing.isNotEmpty()) {
            val names = stillMissing.joinToString("、") { it.label }
            ExtractionTiming.markAborted()
            send(ExtractionState.Error("缺少关键美术资源：$names（已通过 Steam 官方 CDN 备用通道尝试获取，请稍后重试）"))
            return@channelFlow
        }
        if (ok == 0) {
            ExtractionTiming.markAborted()
            send(ExtractionState.Error("Cloudflare 拦截，Steam 官方 CDN 备用下载失败，请稍后重试"))
            return@channelFlow
        }
        val logoJson = webViewLogoPositionProvider()?.toJson()
            ?: store?.logoPosition
            ?: presetLogoPosition?.toJson()
        if (logoJson != null) writeLogoPositionMeta(outDir, logoJson)
        val assets = buildArt(outDir)
        if (assets.isEmpty()) {
            ExtractionTiming.markAborted()
            send(ExtractionState.Error("备用下载未获得可用资源，请稍后重试"))
            return@channelFlow
        }
        Log.i(TAG, "CF-free 兜底成功：$appId 落地 ${assets.size} 张（base=$base）")
        ExtractionTiming.markDone()
        send(ExtractionState.Done(assets, store?.name, store?.logoPosition))
    }

    /**
     * 为缺失资产构造回退重试清单：
     *  - 本地化资产（封套/头图/logo）的 `_<lang>` 变体 404 时 → 改试语言中立标准文件名（消除「英文版界面」空槽）。
     *  - header / page_bg_raw 额外优先商店 API 权威 URL（headerImage / backgroundRaw），再回退中立 CDN 文件。
     *  - CAPSULE_SMALL 兼试 467x181 与 231x87 两种规格（部分游戏仅提供其一）。
     */
    private fun buildRetryAssets(
        missing: List<AssetKind>,
        base: String,
        store: StoreApiClient.Result?
    ): List<SteamAsset> {
        val out = mutableListOf<SteamAsset>()
        for (k in missing) {
            when (k) {
                AssetKind.HEADER -> {
                    store?.headerImage?.takeIf { it.isNotBlank() }?.let {
                        out.add(SteamAsset(AssetKind.HEADER, it, null, forcedName = "header.jpg"))
                    }
                    out.add(SteamAsset(AssetKind.HEADER, "$base/header.jpg", null, forcedName = "header.jpg"))
                }
                AssetKind.PAGE_BG_RAW -> {
                    store?.backgroundRaw?.takeIf { it.isNotBlank() }?.let {
                        out.add(SteamAsset(AssetKind.PAGE_BG_RAW, it, null, forcedName = "page_bg_raw.jpg"))
                    }
                    out.add(SteamAsset(AssetKind.PAGE_BG_RAW, "$base/page_bg_raw.jpg", null, forcedName = "page_bg_raw.jpg"))
                }
                AssetKind.CAPSULE_SMALL -> {
                    out.add(SteamAsset(AssetKind.CAPSULE_SMALL, "$base/capsule_467x181.jpg", null, forcedName = "capsule_467x181.jpg"))
                    out.add(SteamAsset(AssetKind.CAPSULE_SMALL, "$base/capsule_231x87.jpg", null, forcedName = "capsule_231x87.jpg"))
                }
                else -> {
                    // 其它本地化资产（library_600x900 / library_header / hero_capsule / logo）：回退语言中立标准文件。
                    SteamCdnAssets.neutralNameFor(k)?.let { neutral ->
                        out.add(SteamAsset(k, "$base/$neutral", null, forcedName = neutral))
                    }
                }
            }
        }
        return out
    }

    private suspend fun fetchStoreApi(appId: Long): StoreApiClient.Result? =
        withTimeoutOrNull(STORE_API_TIMEOUT_MS) { storeApi.fetch(appId) }

    private fun hasFile(dir: File, kind: AssetKind): Boolean =
        dir.listFiles()?.any { AssetKind.fromFileName(it.name) == kind } ?: false

    /**
     * 覆盖导入清理：删除目录中「非本次导入产物」的残留文件，避免旧状态阴影新导入。
     *  - meta.json：保留（含 logoPosition，下方会按需覆盖）。
     *  - bg.png：仅当本次拿到了真实 [AssetKind.PAGE_BG_RAW] 时才删除（它一定是旧互认 hero 副本，
     *    真实 page_bg_raw 已覆盖背景；无 page_bg_raw 的自定义主题 bg.png 则保留）。
     *  - 其它未被 [AssetKind.fromFileName] 识别、又不在本次下载集合中的文件：判定为孤儿，删除。
     *  已被识别为美术种类的文件（hero/capsule/...）一律保留，避免误删有效资产。
     */
    private fun cleanupStaleArtifacts(dir: File, downloaded: List<SteamAsset>) {
        val expected = downloaded.mapNotNull { a ->
            a.forcedName ?: a.url?.substringAfterLast('/')?.substringBefore('?')
        }.toSet()
        val pageBgRawNow = hasFile(dir, AssetKind.PAGE_BG_RAW)
        dir.listFiles()?.forEach { f ->
            val name = f.name
            when {
                name == "meta.json" -> return@forEach
                name == "bg.png" -> if (pageBgRawNow) f.delete()
                AssetKind.fromFileName(name) == null && name !in expected -> f.delete()
            }
        }
    }

    private fun buildArt(outDir: File): List<SteamAsset> {
        val files = outDir.listFiles()?.toList() ?: emptyList()
        val byKind = files.mapNotNull { f ->
            val k = AssetKind.fromFileName(f.name) ?: return@mapNotNull null
            k to f
        }.toMap()
        val list = mutableListOf<SteamAsset>()
        for (kind in AssetKind.FOR_GAME) {
            val f = byKind[kind] ?: continue
            list.add(SteamAsset(kind, null, f))
        }
        return list
    }

    private fun writeLogoPositionMeta(dir: File, logoJson: String) {
        try {
            val metaFile = File(dir, "meta.json")
            val obj = if (metaFile.exists())
                runCatching { org.json.JSONObject(metaFile.readText()) }.getOrElse { org.json.JSONObject() }
            else org.json.JSONObject()
            obj.put("logoPosition", logoJson)
            // 提取即视为 SteamDB 来源：清掉可能残留的「自定义」标记，避免回退为自定义自动取色逻辑。
            obj.put("custom", false)
            metaFile.writeText(obj.toString())
        } catch (_: Exception) {
            Log.w(TAG, "写入 logoPosition meta 失败")
        }
    }

    /**
     * WebView 探针抓到的 logo 定位，在提取流程结束后（或更晚）回写，优先级高于商店 API 兜底。
     * 覆盖 WebView 捕获晚于提取落盘的场景，确保最终落盘值始终以 WebView 为准。
     */
    fun writeWebViewLogoPosition(packageName: String, pos: LogoPosition) {
        val baseDir = File(context.filesDir, "steamart")
        val outDir = File(baseDir, packageName)
        if (!outDir.canonicalPath.startsWith(baseDir.canonicalPath + File.separator)) return
        writeLogoPositionMeta(outDir, pos.toJson())
    }

    companion object {
        private const val TAG = "ExtractAssetsUseCase"
    }
}

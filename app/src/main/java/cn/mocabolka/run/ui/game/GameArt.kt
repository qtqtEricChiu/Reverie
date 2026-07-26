package cn.mocabolka.run.ui.game

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.URL
import cn.mocabolka.run.ui.game.steamart.domain.model.LogoPosition

/**
 * 游戏美术资源抽象层。
 *
 * 设计目标（用户需求）：
 * - 当前阶段「仅读取本地文件资源」——实现 [AssetGameArtProvider]（APK assets 内的图片）。
 * - 必须预留接口，后续可无缝接入：
 *   - 用户自定义资源（本地设备文件，对应 [ArtSource.File]）；
 *   - 线上实时更新资源（图片链接，对应 [ArtSource.Remote]）。
 *
 * 资源槽位对齐 Steam 大屏幕模式游戏库-游戏界面：
 * - [GameArt.background]：整页背景（`page_bg_raw`，做模糊/变暗处理）；
 * - [GameArt.hero]：顶部英雄横幅（`library_hero_{locale}_{scale}`）；
 * - [GameArt.logo]：透明标题 PNG（`logo_{locale}_{scale}`，叠加在横幅上）；
 * - [GameArt.capsule]：左侧竖版海报（优先简中 `library_capsule`，回退 `hero_capsule`）；
 * - [GameArt.header]：头部条图（`library_header_{locale}_{scale}`）。
 *
 * 文件名中的 `{locale}`（简中 `schinese`）与 `{scale}`（`2x`）为**可选标签**，
 * 由 [GameArtVariantConfig] 统一控制，预留 i18n 整体替换（见该对象说明）。
 */
sealed interface ArtSource {
    /** APK assets 内的本地文件（当前阶段主用）。path 相对 assets 根，如 "gameart/key/library_hero.jpg"。 */
    data class Asset(val path: String) : ArtSource
    /** 设备本地文件（用户自定义资源，后续接入）。 */
    // lastModified：设备文件可能被覆盖重写（重新提取 / 自定义主题保存）。
    // 携带真实 mtime 可让 source 身份随文件变化而失效，从而驱动 rememberArtBitmap 重新解码；
    // 默认 0L 表示未携带（如自定义页瞬态预览），sourceKey 会回退磁盘 mtime。
    data class File(val uri: Uri, val lastModified: Long = 0L) : ArtSource
    /** 线上图片链接（后续实时更新资源接入）。 */
    data class Remote(val url: String) : ArtSource
}

/** 单个美术资源槽位：来源 + 可选强调色（用于背景染色）。 */
data class GameArtImage(
    val source: ArtSource,
    val accent: Color? = null
)

/**
 * 一款游戏的完整美术资源集合。任一槽位为 null 表示未提供，UI 自动降级（如用品牌色生成背景）。
 */
data class GameArt(
    val key: String,
    val displayName: String,
    val background: GameArtImage?,
    val hero: GameArtImage?,
    val logo: GameArtImage?,
    val capsule: GameArtImage?,
    val header: GameArtImage?,
    val accentColor: Color? = null,
    val steamAppId: Long? = null,
    val logoPosition: LogoPosition? = null
)

/**
 * 游戏美术资源提供者接口。
 *
 * 实现可叠加（本地 assets / 用户自定义 / 线上），由 [GameArtResolver] 聚合，
 * 按优先级依次尝试，前者命中即返回（便于后续「线上覆盖本地」「用户覆盖默认」）。
 */
interface GameArtProvider {
    /** 同步判断是否有该应用的美术资源（事件循环/点击即时决策，必须轻量、不阻塞）。 */
    fun hasArt(packageName: String, label: String): Boolean

    /** 异步加载该应用的美术资源；无则返回 null。 */
    suspend fun load(packageName: String, label: String): GameArt?
}

/**
 * 当前阶段实现：从 APK `assets/gameart/` 读取本地文件。
 *
 * 资源登记表 `assets/gameart/manifest.json` 描述每个游戏：
 * - key：资源文件夹名（与 assets 下子目录一致）；
 * - packageNames：匹配的包名（任一命中即采用）；
 * - labelHints：应用显示名包含的片段（兜底匹配，覆盖包名未知场景）；
 * - accentColor：强调色（十六进制字符串）；
 * - steamAppId：来源 Steam appid（预留，后续线上同步用）。
 */
class AssetGameArtProvider(private val context: Context) : GameArtProvider {
    private data class Entry(
        val key: String,
        val displayName: String,
        val packages: List<String>,
        val labelHints: List<String>,
        val accentColor: Color?,
        val steamAppId: Long?
    )

    private val entries: List<Entry> = runCatching { parseManifest() }.getOrDefault(emptyList())

    private fun parseManifest(): List<Entry> {
        val text = context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val out = mutableListOf<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pkgList = o.optJSONArray("packageNames")?.let { (0 until it.length()).map { j -> it.getString(j) } } ?: emptyList()
            val hintList = o.optJSONArray("labelHints")?.let { (0 until it.length()).map { j -> it.getString(j) } } ?: emptyList()
            val accent = o.optString("accentColor", "").takeIf { it.isNotBlank() }?.let { parseColor(it) }
            out.add(
                Entry(
                    key = o.getString("key"),
                    displayName = o.optString("displayName", o.getString("key")),
                    packages = pkgList,
                    labelHints = hintList,
                    accentColor = accent,
                    steamAppId = o.optLong("steamAppId", -1L).takeIf { it >= 0L }
                )
            )
        }
        return out
    }

    private fun matchEntry(packageName: String, label: String): Entry? {
        val lowerLabel = label.lowercase()
        return entries.firstOrNull { e ->
            e.packages.any { it.equals(packageName, ignoreCase = true) } ||
                e.labelHints.any { lowerLabel.contains(it.lowercase()) }
        }
    }

    override fun hasArt(packageName: String, label: String): Boolean = matchEntry(packageName, label) != null

    override suspend fun load(packageName: String, label: String): GameArt? {
        val entry = matchEntry(packageName, label) ?: return null
        val dir = "$ASSET_ROOT/${entry.key}"
        val assetExists: (String) -> Boolean = { name ->
            runCatching { context.assets.open("$dir/$name").close(); true }.getOrDefault(false)
        }
        val img: (String) -> GameArtImage? = { name ->
            if (assetExists(name)) GameArtImage(ArtSource.Asset("$dir/$name"), entry.accentColor) else null
        }
        val imgTry: (List<String>) -> GameArtImage? = { names -> names.firstNotNullOfOrNull { n -> img(n) } }
        val v = GameArtVariantConfig
        return GameArt(
            key = entry.key,
            displayName = entry.displayName,
            background = img(BG_RAW),
            // 回落链：schinese_2x → 通用 2x（无 schinese）→ 旧命名兜底
            hero = imgTry(v.candidates("library_hero", "jpg", localeAware = true) + HERO_LEGACY),
            logo = imgTry(v.candidates("logo", "png", localeAware = true) + LOGO_LEGACY),
            capsule = imgTry(
                v.candidates("library_capsule", "jpg", localeAware = true) +
                    v.candidates("hero_capsule", "jpg", localeAware = false) +
                    LIB_CAPSULE_LEGACY + HERO_CAPSULE_LEGACY
            ),
            header = imgTry(v.candidates("library_header", "jpg", localeAware = true) + HEADER_LEGACY),
            accentColor = entry.accentColor,
            steamAppId = entry.steamAppId
        )
    }

    private companion object {
        const val MANIFEST_PATH = "gameart/manifest.json"
        const val ASSET_ROOT = "gameart"
        // 不带标签的资源（无 locale/scale 维度）
        const val BG_RAW = "page_bg_raw.jpg"
        // 旧命名回退（尚未更新美术资源的游戏，如 zenless_zone_zero）
        const val HERO_LEGACY = "library_hero.jpg"
        const val LOGO_LEGACY = "library_logo.png"
        const val HERO_CAPSULE_LEGACY = "hero_capsule.jpg"
        const val LIB_CAPSULE_LEGACY = "library_capsule.jpg"
        const val HEADER_LEGACY = "library_header.jpg"
    }
}

/**
 * 美术资源"变体格"配置 —— **仅代码层开关，暂不开放给用户 UI**。
 *
 * 预留 i18n：后续开放多语言时，只需把 [LOCALE_TAG] 整体替换为目标语言
 * （如 `english` / `tchinese`），[SCALE_TAG] 可切换到 `1x` 等分辨率变体，
 * 即可对所有游戏美术做**整体语言 / 分辨率替换**（替换前提是对应变体文件就位）。
 *
 * 设计要点：
 * - `schinese` 与 `2x` 均为**可选标签**——[candidates] 按 [USE_VARIANT] 与标签是否为空
 *   决定是否拼入，从而支持"无语言维度"（如 `hero_capsule`）或"低分辨率"等任意组合。
 * - 解析回落链（见 [candidates]）：**首选** `base_{locale}_{scale}`（如 `library_hero_schinese_2x.jpg`）
 *   → **通用回落**：源文件无简中变体时去掉语言标签、仅保留分辨率 `base_{scale}`（如 ZZZ 的
 *   `library_hero_2x.jpg`），**绝不自行补加不存在的 `schinese`** → **旧命名兜底** `base.{ext}`
 *   （如 `library_hero.jpg`）。[AssetGameArtProvider.load] 依次尝试，首个命中即返回。
 */
object GameArtVariantConfig {
    /** 是否启用变体格命名；false 时直接按基础名回退旧命名。 */
    const val USE_VARIANT = true
    /** 语言标签（简中优先）。空串表示不附加语言维度。 */
    const val LOCALE_TAG = "schinese"
    /** 分辨率标签。空串表示不附加分辨率维度。 */
    const val SCALE_TAG = "2x"

    /**
     * 按基础名生成**有序候选文件名列表**（首个命中即采用）：
     * 1. 首选：`base_{locale}_{scale}`（带语言+分辨率，仅 [localeAware] 时带语言）；
     * 2. 通用回落：`base_{scale}`（去掉语言标签，仅当本应带语言、却可能被源文件省略时）；
     * 3. 旧命名兜底：`base.{ext}`（无标签）。
     *
     * 关键：[localeAware]=true 但源文件**没有** `schinese` 变体时，第 2 项会自动回落到
     * 仅带分辨率的通用名（如 `library_hero_2x.jpg`），而**不会**强行拼出不存在的 `schinese`。
     *
     * @param base 资源基础名（如 `library_hero`）
     * @param ext 扩展名（如 `jpg`）
     * @param localeAware 是否带语言标签（`hero_capsule` 等无语言维度时为 false）
     */
    fun candidates(base: String, ext: String, localeAware: Boolean): List<String> {
        if (!USE_VARIANT) return listOf("$base.$ext")
        val scale = if (SCALE_TAG.isNotBlank()) "_$SCALE_TAG" else ""
        val locale = if (localeAware && LOCALE_TAG.isNotBlank()) "_$LOCALE_TAG" else ""
        val out = LinkedHashSet<String>()
        // 1) 首选：语言 + 分辨率（两者至少其一存在才拼，避免多余下划线）
        if (locale.isNotEmpty() || scale.isNotEmpty()) out += "$base$locale$scale.$ext"
        // 2) 通用回落：仅分辨率（去掉语言），仅当本应带语言且分辨率也存在时才有意义
        if (localeAware && locale.isNotEmpty() && scale.isNotEmpty()) out += "$base$scale.$ext"
        // 3) 旧命名兜底：无标签
        out += "$base.$ext"
        return out.toList()
    }
}

/**
 * 资源解析聚合器：按优先级依次询问各 [GameArtProvider]，首个命中即返回。
 *
 * 当前仅装配 [AssetGameArtProvider]（本地文件）。
 * 后续接入「用户自定义」与「线上实时」时，在此追加对应 Provider 即可，
 * 例如：GameArtResolver(listOf(userProvider, remoteProvider, assetProvider))，
 * 让「用户 > 线上 > 本地默认」的优先级自然成立。
 */
class GameArtResolver(
    private val providers: List<GameArtProvider>,
    /**
     * 「已被用户主动清除」判定：返回 true 时，若第一个（用户层 File）Provider 无资源，
     * 则不再向后回落到内置 asset 资源，真正回到「无任何美术」的最初状态。
     */
    private val isCleared: (String) -> Boolean = { false }
) : GameArtProvider {
    constructor(context: Context) :
        this(
            listOf(FileGameArtProvider(context), AssetGameArtProvider(context)),
            { pkg -> GameArtTombstone.isCleared(context, pkg) }
        )

    override fun hasArt(packageName: String, label: String): Boolean {
        providers.forEachIndexed { i, p ->
            if (p.hasArt(packageName, label)) return true
            // 用户层(第一个=File)无资源、且该包被用户主动清除 → 视为无美术，不回落内置
            if (i == 0 && isCleared(packageName)) return false
        }
        return false
    }

    override suspend fun load(packageName: String, label: String): GameArt? {
        providers.forEachIndexed { i, p ->
            val art = p.load(packageName, label)
            // 命中即写入进程内元数据缓存：二级子页（截图查看全部等）经 [peekGameArt] 同步读取，
            // 首帧即拿到完整 GameArt（背景/头图/accent 来源），实现背景跨页零延迟继承。
            if (art != null) { gameArtCache.put(packageName, art); return art }
            if (i == 0 && isCleared(packageName)) { gameArtCache.remove(packageName); return null }
        }
        gameArtCache.remove(packageName)
        return null
    }
}

/**
 * 「已清除」墓碑标记：记录用户主动清除过美术资源的包名。
 *
 * 背景：点击「清除」只删 filesDir/steamart/<pkg>，但内置预设游戏（无限暖暖/绝区零/鸣潮）
 * 在 APK assets 里仍有资源，[AssetGameArtProvider] 会兜底命中，
 * 导致「点了清除仍显示美术、回不到最初界面」。
 * 墓碑让 [GameArtResolver] 在「用户层无资源且被清除」时不再回落内置，真正回到无美术初始态；
 * 当用户重新提取/自定义（filesDir 重新写入资源）时，[FileGameArtProvider] 会直接命中，
 * 墓碑被自然绕过，无需显式删除标记。
 */
object GameArtTombstone {
    private fun dir(context: Context) = File(context.filesDir, "steamart_cleared")
    private fun marker(context: Context, pkg: String) = File(dir(context), pkg)

    fun isCleared(context: Context, pkg: String): Boolean =
        runCatching { marker(context, pkg).exists() }.getOrDefault(false)

    fun mark(context: Context, pkg: String) {
        runCatching { dir(context).mkdirs(); marker(context, pkg).createNewFile() }
    }

    fun clear(context: Context, pkg: String) {
        runCatching { marker(context, pkg).delete() }
    }
}

/** 进程内位图缓存，避免同一资源重复解码（背景/横幅较大）。 */
private val artBitmapCache = androidx.collection.LruCache<String, ImageBitmap>(32)

/** 进程内 GameArt 元数据缓存（键=包名）：[GameArtResolver.load] 命中即写入。 */
private val gameArtCache = androidx.collection.LruCache<String, GameArt>(16)

/**
 * 同步读取进程内已缓存的 [GameArt]（无 IO）。二级子页（截图查看全部等）从游戏页进入时，
 * 游戏页必已 load 过同游戏美术 → 此处命中 → 背景/头图首帧即渲染，实现背景跨页零延迟继承。
 * 未命中（如进程重建后直达）返回 null，调用方照常走异步 load 补帧。
 */
fun peekGameArt(packageName: String): GameArt? = gameArtCache.get(packageName)

/** 同步读取进程内已解码位图（无 IO）：命中时首帧即显，未命中返回 null 由异步解码补帧。 */
fun peekArtBitmap(source: ArtSource?): ImageBitmap? =
    source?.let { artBitmapCache.get(sourceKey(it)) }

/**
 * 解码一个 [ArtSource] 为 Compose [ImageBitmap]（IO 线程执行，结果入缓存）。
 * 此函数统一处理本地 assets / 设备文件 / 线上链接三种来源，
 * 后续新增来源只需在 [ArtSource] 中扩展并在本函数补充分支。
 */
suspend fun decodeArtBitmap(context: Context, source: ArtSource): ImageBitmap? {
    val cacheKey = sourceKey(source)
    artBitmapCache.get(cacheKey)?.let { return it }
    return withContext(Dispatchers.IO) {
        val bmp = runCatching {
            when (source) {
                // Remote 只走单次流式解码（两遍采样需二次下载，得不偿失），超时由 openSource 统一保障
                is ArtSource.Remote -> openSource(context, source)?.use { BitmapFactory.decodeStream(it) }
                // 本地来源两遍采样解码：长边限制在 DISPLAY_MAX_EDGE_PX 内，防超大图 OOM
                else -> decodeSampledBitmap(context, source, DISPLAY_MAX_EDGE_PX)
            }
        }.getOrNull() ?: return@withContext null
        val image = bmp.asImageBitmap()
        artBitmapCache.put(cacheKey, image)
        image
    }
}

/** 展示用解码的长边上限（px）：覆盖 2K 横屏，超出无视觉收益只耗内存。 */
private const val DISPLAY_MAX_EDGE_PX = 2560

/** 解析 "#AARRGGBB" / "#RRGGBB" 为 Compose [Color]；失败返回透明。 */
internal fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color.Transparent)

// ─────────────────────────────────────────────────────────────
// 运行时取色（替代 manifest 写死的 accentColor —— 从真实取色图片提取鲜艳主色）
//
// 背景：此前 accent 直接取 manifest.json 的十六进制常量，与画面不搭、"取色很奇怪"。
// 现改为从美术图片运行时提取：优先 logo（品牌纯色、背景透明，最能代表游戏主色），
// 依次回退 capsule / header / hero。离线工程无 androidx.palette，故手写 HSV 直方图算法：
//  - 下采样到 ~96px，遍历像素；
//  - 跳过透明（logo 背景）、近灰、过暗、过亮像素（避免取到黑/白/灰背景色）；
//  - 按 饱和度×明度 加权投票到 36 个色相桶，取票数最高桶的加权均色；
//  - 归一化到"深色 UI 友好"区间（饱和度 0.55~0.9、明度 0.5~0.85），保证对比度与观感统一。
// ─────────────────────────────────────────────────────────────

/** 提取结果缓存：键为首个来源标识，避免同游戏重复解码/计算。 */
private val accentCache = androidx.collection.LruCache<String, Color>(32)

/**
 * 从一组候选取色图片依次尝试提取鲜艳主色；全部失败时返回 [fallback]。
 * 调用方应按"最能代表主色"的顺序传入（logo → capsule → header → hero）。
 */
suspend fun extractAccent(context: Context, sources: List<ArtSource>, fallback: Color): Color {
    if (sources.isEmpty()) return fallback
    val cacheKey = sourceKey(sources.first())
    accentCache.get(cacheKey)?.let { return it }
    val result = withContext(Dispatchers.IO) {
        for (src in sources) {
            val bmp = runCatching { decodeSampledBitmap(context, src, 96) }.getOrNull() ?: continue
            // try/finally 保证取色异常时位图也能回收，避免原生内存泄漏
            val c = try { vibrantColorOf(bmp) } finally { bmp.recycle() }
            if (c != null) return@withContext c
        }
        fallback
    }
    accentCache.put(cacheKey, result)
    return result
}

/**
 * 清掉指定包名下的进程内位图 / 取色缓存（美术重提取或自定义主题保存后调用），
 * 杜绝「同一文件路径重新写入后 GamePage 仍显示旧资源」的串味问题。
 * 仅按路径 marker 精准清除该游戏（/steamart/<pkg>/），不影响其它游戏缓存。
 */
fun clearArtCachesForPackage(packageName: String) {
    val marker = "/steamart/$packageName/"
    artBitmapCache.snapshot().keys.filter { it.contains(marker) }.forEach { artBitmapCache.remove(it) }
    accentCache.snapshot().keys.filter { it.contains(marker) }.forEach { accentCache.remove(it) }
    gameArtCache.remove(packageName)
}

private fun sourceKey(source: ArtSource): String = when (source) {
    is ArtSource.Asset -> "asset:${source.path}"
    is ArtSource.File -> {
        // 设备文件可能被覆盖重写（重新提取 / 自定义主题保存）：优先用 source 携带的
        // lastModified 让缓存失效（与 rememberArtBitmap 的 source 身份保持一致），
        // 仅当未携带（=0L，如自定义页预览）时回退磁盘 mtime。
        val m = source.lastModified
        if (m > 0L) "file:${source.uri.path}#$m"
        else {
            val p = source.uri.path
            val fm = if (p != null) java.io.File(p).takeIf { it.exists() }?.lastModified() ?: 0L else 0L
            "file:$p#$fm"
        }
    }
    is ArtSource.Remote -> "remote:${source.url}"
}

/** 打开某来源的输入流（供二次读取：先读边界再解码）。 */
private fun openSource(context: Context, source: ArtSource): java.io.InputStream? = when (source) {
    is ArtSource.Asset -> context.assets.open(source.path)
    is ArtSource.File -> context.contentResolver.openInputStream(source.uri)
    is ArtSource.Remote -> URL(source.url).openConnection().apply {
        // 显式超时：默认 0（无限等待）会在弱网下卡死 IO 协程
        connectTimeout = 15_000
        readTimeout = 15_000
    }.getInputStream()
}

/** 下采样解码为 android [android.graphics.Bitmap]（长边约 [targetPx]），仅用于像素分析。 */
private fun decodeSampledBitmap(context: Context, source: ArtSource, targetPx: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openSource(context, source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longEdge <= 0) return null
    var sample = 1
    while (longEdge / (sample * 2) >= targetPx) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    return openSource(context, source)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

/**
 * 计算位图的鲜艳主色（HSV 直方图投票）。无有效彩色像素（纯灰度/全透明）时返回 null。
 */
private fun vibrantColorOf(bmp: android.graphics.Bitmap): Color? {
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return null
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)

    val buckets = 36
    val weight = DoubleArray(buckets)
    val sumS = DoubleArray(buckets)
    val sumV = DoubleArray(buckets)
    val hsv = FloatArray(3)
    var total = 0.0

    for (p in pixels) {
        val a = (p ushr 24) and 0xFF
        if (a < 128) continue // 透明像素（logo 背景）跳过
        android.graphics.Color.colorToHSV(p, hsv)
        val hue = hsv[0]; val s = hsv[1]; val v = hsv[2]
        // 过滤近灰 / 过暗 / 过亮，避免取到背景灰白黑
        if (s < 0.25f || v < 0.18f || v > 0.96f) continue
        val idx = ((hue / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
        val wgt = (s * v).toDouble()
        weight[idx] += wgt
        sumS[idx] += s * wgt
        sumV[idx] += v * wgt
        total += wgt
    }
    if (total < 1.0) return null

    var best = 0
    for (i in 1 until buckets) if (weight[i] > weight[best]) best = i
    if (weight[best] <= 0.0) return null

    val hue = ((best + 0.5f) / buckets) * 360f
    val s = (sumS[best] / weight[best]).toFloat().coerceIn(0.55f, 0.9f)
    val v = (sumV[best] / weight[best]).toFloat().coerceIn(0.5f, 0.85f)
    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v))
    return Color(rgb)
}

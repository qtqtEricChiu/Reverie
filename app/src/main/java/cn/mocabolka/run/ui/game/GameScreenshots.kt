package cn.mocabolka.run.ui.game

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 游戏截图归类功能（替代原"游戏资源画廊"的内容）。
 *
 * 设计依据（用户需求）：
 * 1. 权限页：应用向用户发起 Pictures/Screenshots 系统截图文件夹授权（SAF [Intent.ACTION_OPEN_DOCUMENT_TREE]，
 *    并 [ContentResolver.takePersistableUriPermission] 持久化，避免每次重授权）。
 * 2. 初始化：向用户索要一张该游戏的截图/录屏（如 `Screenshot_2026-07-15-18-32-49-65_0dfddd8a234be85a617c2f29d20ccb80`），
 *    记忆其文件名后缀哈希值 `0dfddd8a234be85a617c2f29d20ccb80`；此后该设备上所有后缀为该哈希的截图/视频均标记为该应用截图。
 * 3. 呈现：扫描授权文件夹内所有文件名后缀哈希命中的截图/视频，用户点击即跳转第三方查看器（系统图片/视频播放器）。
 * 4. 不支持（按游戏）：若某游戏样例文件文件名不含哈希后缀，仅对该游戏屏蔽截图归类，不影响其它游戏的截图照常归类。
 *
 * 关键约束：
 * - 哈希后缀为文件名末尾 `_<16~64位十六进制>`（可带扩展名），与具体文件名前缀/时间无关，设备级稳定。
 * - 授权范围限定在用户选择的截图树，[scanScreenshots] 仅在该树内递归匹配，不触碰无关目录。
 * - 打开第三方查看器时通过 [Intent.FLAG_GRANT_READ_URI_PERMISSION] 将单 Uri 的一次性读权限委托给目标应用。
 */
object GameScreenshots {

    /** 单张已归类截图/录屏（来自系统截图文件夹，按文件名哈希后缀匹配当前游戏）。 */
    data class Shot(
        val uri: Uri,
        val name: String,
        val mime: String,
        val isVideo: Boolean = mime.startsWith("video", ignoreCase = true)
    )

    /**
     * 截图缩略图加载结果。
     * @param bitmap 解码成功的位图；null 表示尚未完成或解码失败。
     * @param ratio  真实宽高比（宽/高），用于瀑布流槽位提前预留高度（避免加载完成前 0 高度突变导致列偏移抖动）。
     * @param done   true=已完成一次加载尝试（无论成功或失败）。
     */
    data class ShotThumb(val bitmap: ImageBitmap?, val ratio: Float = 16f / 9f, val done: Boolean)

    /** 截图归类功能初始化状态机（驱动"权限页→初始化→呈现/屏蔽"流程）。 */
    enum class SetupState {
        /** 尚未授权截图文件夹（需走权限页）。 */
        NEED_TREE,
        /** 已授权文件夹，但尚未选择该游戏的样例截图/录屏以学习哈希后缀。 */
        NEED_SAMPLE,
        /** 已就绪：已学习哈希后缀，可按后缀扫描并呈现。 */
        READY,
        /** 该游戏截图文件名不含哈希后缀，无法自动归类，仅屏蔽该游戏。 */
        UNSUPPORTED
    }

    /** 游戏页"动态"Tab 内截图画廊的 UI 状态（由 Activity 装配后下传 Compose）。 */
data class ScreenshotGalleryState(
    val setupState: SetupState,
    val screenshots: List<Shot>,
    val onRequestTree: () -> Unit,
    val onRequestSample: () -> Unit,
    val onOpen: (Shot) -> Unit,
    val onViewAll: ((androidx.compose.ui.graphics.Color) -> Unit)? = null,
    /** UNSUPPORTED（截图文件名无哈希后缀）时是否收折成一行（按应用记忆，默认收折）。 */
    val unsupportedCollapsed: Boolean = false,
    /** 收折行被点击/激活时展开完整卡片（仅当前应用）。 */
    val onExpandUnsupported: () -> Unit = {},
    /** 展开后「重试」：清除该游戏的不支持标记与哈希，重新进入初始化流程（重选样例）。 */
    val onRetry: () -> Unit = {}
)

    /** 持久化存储（SharedPreferences，零新增依赖）。 */
    class Store(context: Context) {
        private val prefs = context.getSharedPreferences("game_screenshots_v1", Context.MODE_PRIVATE)

        fun getTreeUri(): Uri? {
            val s = prefs.getString(KEY_TREE, null) ?: return null
            return runCatching { Uri.parse(s) }.getOrNull()
        }
        fun setTreeUri(uri: Uri) = prefs.edit().putString(KEY_TREE, uri.toString()).apply()
        fun clearTreeUri() = prefs.edit().remove(KEY_TREE).apply()

        fun isUnsupported(pkg: String): Boolean = prefs.getBoolean(unsupportedKey(pkg), false)
        fun setUnsupported(pkg: String) = prefs.edit().putBoolean(unsupportedKey(pkg), true).apply()
        fun clearUnsupported(pkg: String) = prefs.edit().remove(unsupportedKey(pkg)).apply()

        fun getHash(pkg: String): String? = prefs.getString(hashKey(pkg), null)
        fun setHash(pkg: String, hash: String) = prefs.edit().putString(hashKey(pkg), hash).apply()
        fun clearHash(pkg: String) = prefs.edit().remove(hashKey(pkg)).apply()

        private fun hashKey(pkg: String) = "hash_$pkg"
        companion object {
            private const val KEY_TREE = "tree_uri"
            private fun unsupportedKey(pkg: String) = "unsupported_$pkg"
        }
    }

    /**
     * 从文件名提取"系统截图后缀哈希"（形如 `_0dfddd8a...` 的 16~64 位十六进制串，可带扩展名）。
     * 返回小写哈希；不匹配（无后缀）返回 null（用于判定设备不支持）。
     */
    fun extractSuffixHash(fileName: String): String? {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val m = SUFFIX_HASH_RE.find(base) ?: return null
        return m.groupValues[1].lowercase()
    }

    /**
     * 递归扫描已授权截图树，返回文件名后缀哈希命中 [hash] 的全部截图/录屏。
     * 使用框架 [DocumentsContract] + [ContentResolver] 遍历（不依赖 androidx.documentfile）。
     * 限制扫描深度，避免遍历整个 DCIM；命中结果按文件名降序（最新在前）。
     */
    fun scanScreenshots(context: Context, treeUri: Uri, hash: String): List<Shot> {
        val cr = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
        val target = hash.lowercase()
        val out = mutableListOf<Shot>()
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        fun walk(parentDocId: String, depth: Int) {
            if (depth > MAX_SCAN_DEPTH) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            runCatching {
                cr.query(childrenUri, cols, null, null, null)?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (c.moveToNext()) {
                        val docId = c.getString(idIdx)
                        val name = c.getString(nameIdx) ?: return@use
                        val mime = (c.getString(mimeIdx) ?: "").ifBlank { guessMime(name) }
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            walk(docId, depth + 1)
                        } else if (extractSuffixHash(name) == target) {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            out.add(Shot(uri, name, mime))
                        }
                    }
                }
            }
        }
        walk(rootId, 0)
        out.sortByDescending { it.name }
        return out
    }

    /** 跳转第三方查看器打开单张截图/录屏（授予该 Uri 的一次性读权限）。 */
    fun open(context: Context, shot: Shot) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(shot.uri, shot.mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /** 由任意可读 Uri（如用户直接选取、可能不在授权树内的样例文件）构造 [Shot]（查询显示名与类型）。 */
    fun shotFromUri(context: Context, uri: Uri): Shot? {
        val cr = context.contentResolver
        val name = runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            }
        }.getOrNull()
        if (name.isNullOrBlank()) return null
        val mime = runCatching { cr.getType(uri) }.getOrNull() ?: guessMime(name)
        return Shot(uri, name, mime)
    }

    /** 进程内缩略图缓存，避免横向画廊滚动时重复解码（Key=Uri，上限 [THUMB_CACHE_MAX] 张）。 */
    private val thumbCache = object {
        private val map = LinkedHashMap<String, ImageBitmap>(THUMB_CACHE_MAX, 0.75f, true)
        fun get(uri: Uri): ImageBitmap? = synchronized(map) { map[uri.toString()] }
        fun put(uri: Uri, bmp: ImageBitmap) = synchronized(map) {
            if (map.size >= THUMB_CACHE_MAX) map.remove(map.keys.first())
            map[uri.toString()] = bmp
        }
    }

    /** 加载截图缩略图（图片采样解码 / 视频用系统缩略图），供画廊呈现；命中缓存直接返回。 */
    suspend fun loadThumbnail(context: Context, shot: Shot): ShotThumb {
        thumbCache.get(shot.uri)?.let { bmp ->
            // 缓存命中：位图立即可用，宽高比由位图尺寸即时推导（回滑重入视口时 0 等待、槽位高度稳定）。
            val ratio = if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 16f / 9f
            return ShotThumb(bmp, ratio, true)
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                if (shot.isVideo) {
                    // 视频：优先系统缩略图（MediaStore 快），失败/为空时回退 MediaMetadataRetriever 提取首帧，
                    // 彻底解决录屏文件默认灰屏（尤其 SAF 文档 Uri 的 loadThumbnail 常返回失败）问题。
                    val sys = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        runCatching {
                            context.contentResolver
                                .loadThumbnail(shot.uri, Size(THUMB_MAX, THUMB_MAX), null)
                                .asImageBitmap()
                        }.getOrNull()
                    } else null
                    val bmp = sys ?: extractVideoFrame(context, shot.uri)
                    val ratio = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 16f / 9f
                    bmp?.also { thumbCache.put(shot.uri, it) }
                    ShotThumb(bmp, ratio, true)
                } else {
                    // 两次开流代替 readBytes()：原图可达数十 MB，整字节数组入内存有 OOM 风险
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(shot.uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, opts)
                    }
                    // 预取宽高比：在重解码前即可确定槽位高度，避免瀑布流列偏移抖动（尤其向上回滑大量项重新入视口时）。
                    val boundsRatio = if (opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight.toFloat() else 16f / 9f
                    // 采样率按长边计算（原实现只看宽度，高瘦截图会欠采样）
                    val longEdge = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
                    val opts2 = BitmapFactory.Options().apply {
                        inSampleSize = (longEdge / THUMB_MAX).coerceAtLeast(1)
                    }
                    val bmp = context.contentResolver.openInputStream(shot.uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, opts2)?.asImageBitmap()
                    }
                    bmp?.also { thumbCache.put(shot.uri, it) }
                    ShotThumb(bmp, boundsRatio, true)
                }
            }.getOrDefault(ShotThumb(null, 16f / 9f, true))
        }
    }

    /** 用 [MediaMetadataRetriever] 从视频提取首帧作为封面（SAF 文档 Uri 走 openFileDescriptor）。 */
    private fun extractVideoFrame(context: Context, uri: Uri): ImageBitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        THUMB_MAX,
                        THUMB_MAX
                    )
                } else {
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                frame?.asImageBitmap()
            }
        } catch (e: Exception) {
            // 留痕而非静默：视频帧提取失败（编码不支持/文件损坏）需可排查
            android.util.Log.w("GameScreenshots", "视频首帧提取失败 $uri: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun guessMime(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }

    private val SUFFIX_HASH_RE = Regex("_([0-9a-fA-F]{16,64})(?:\\.[^.]+)?$")
    private const val MAX_SCAN_DEPTH = 4
    private const val THUMB_MAX = 512
    private const val THUMB_CACHE_MAX = 64
}

/**
 * 画廊聚焦时 B 键是否应消费（复位选图并退出画廊聚焦，而非关闭整个页面）。
 *
 * 该判定内聚于画廊自身状态（[GameScreenshots.ScreenshotGalleryState]），避免在全局按键 handler 中
 * 散落画廊特有语义（审计 4.4：B 键退出画廊逻辑从全局 handler 移至 ScreenshotGallery 组件内部）。
 * 与 [GamePage] 中 `galleryItemCount > 0` 的原始判定语义完全一致。
 */
val GameScreenshots.ScreenshotGalleryState.hasFocusableContent: Boolean
    get() {
        val shots = screenshots
        val showViewAll = shots.size > 5 && onViewAll != null
        return shots.isNotEmpty() || showViewAll
    }

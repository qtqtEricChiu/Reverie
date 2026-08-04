package cn.mocabolka.run.ui.game

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.ui.graphics.Color as ComposeColor
import cn.mocabolka.run.ui.game.steamart.domain.model.AssetKind
import cn.mocabolka.run.ui.game.steamart.domain.model.LogoPosition
import java.io.File

/**
 * 基于「设备上文件」的游戏美术资源提供器。
 *
 * 资源由 SteamDB 提取流程（[cn.mocabolka.run.ui.game.steamart]）按包名落地到
 * `filesDir/steamart/<packageName>/`，也可由自定义主题写入（bg.png + meta.json）。
 * 优先级高于 [AssetGameArtProvider]（APK 内置资源），即「用户拉取 / 自定义」覆盖内置。
 */
class FileGameArtProvider(private val context: Context) : GameArtProvider {
    private fun dir(pkg: String) = File(context.filesDir, "steamart").resolve(pkg)

    override fun hasArt(packageName: String, label: String): Boolean {
        val d = dir(packageName)
        if (!d.exists() || !d.isDirectory) return false
        return d.listFiles()?.any { f ->
            val n = f.name
            n == "meta.json" || n == "bg.png" || AssetKind.fromFileName(n) != null
        } ?: false
    }

    override suspend fun load(packageName: String, label: String): GameArt? {
        // 注意：load 现在可能返回 null。原实现无论目录是否为空都返回 GameArt，导致失败/中断的
        // 提取（仅建立空目录）屏蔽掉 AssetGameArtProvider 的内置 5 个槽位，造成游戏页白屏/降级
        // （截图诊断 Q1：内置有 5 个槽位却被 File(0) 屏蔽）。
        // 新约定：空目录 = 这次提取/导入没成功 → 让 fallback（内置）接管，而不是硬塞一个全空 GameArt。
        // 但保留「只覆盖主色/logo（meta.json 已写入，无图片）」这种偏置语义：仍返回 GameArt，让上游沿用
        // 自定义 accent/logoPosition。
        val d = dir(packageName)
        val files = d.listFiles()?.toList() ?: emptyList()
        val byKind = files.mapNotNull { f ->
            val k = AssetKind.fromFileName(f.name) ?: return@mapNotNull null
            k to f
        }.toMap()
        val meta = readMeta(d)

        if (byKind.isEmpty()) {
            if (meta == null) return null
            // 仅 meta.json（纯主色/logo 覆盖），返回仅含自定义元数据、图片全空的 GameArt。
            return GameArt(
                key = packageName,
                displayName = label,
                background = null,
                hero = null,
                logo = null,
                capsule = null,
                header = null,
                accentColor = meta.accentColor,
                steamAppId = meta.steamAppId,
                logoPosition = meta.logoPosition,
                isCustom = meta.custom
            )
        }

        val bg = byKind[AssetKind.PAGE_BG_RAW]
            ?: File(d, "bg.png").takeIf { it.exists() }
            // 互认/回归逻辑：老游戏无 page_bg_raw、也无自定义 bg.png 时，以同目录其它整图
            // 依次回归为整页背景（优先 library_hero，其次 capsule，再次 header），避免背景全黑 + 取色退化；
            // GamePage / SteamArtBackground 的取色源均已含 hero，故取色一并恢复。
            ?: byKind[AssetKind.LIBRARY_HERO]
            ?: byKind[AssetKind.LIBRARY_CAPSULE]
            ?: byKind[AssetKind.HERO_CAPSULE]
            ?: byKind[AssetKind.LIBRARY_HEADER]
        val hero = byKind[AssetKind.LIBRARY_HERO]
        val logo = byKind[AssetKind.LOGO]
        val capsule = byKind[AssetKind.LIBRARY_CAPSULE] ?: byKind[AssetKind.HERO_CAPSULE]
        val header = byKind[AssetKind.LIBRARY_HEADER]

        // 携带真实 lastModified：重新提取 / 自定义主题保存后文件被覆盖，mtime 变化会让
        // ArtSource.File 身份变化，从而驱动 rememberArtBitmap 重新解码（否则同路径永远命中旧缓存）。
        val file = { f: File -> ArtSource.File(Uri.fromFile(f), f.lastModified()) }

        return GameArt(
            key = packageName,
            displayName = label,
            background = bg?.let { GameArtImage(file(it)) },
            hero = hero?.let { GameArtImage(file(it)) },
            logo = logo?.let { GameArtImage(file(it)) },
            capsule = capsule?.let { GameArtImage(file(it)) },
            header = header?.let { GameArtImage(file(it)) },
            accentColor = meta?.accentColor,
            steamAppId = meta?.steamAppId,
            logoPosition = meta?.logoPosition,
            isCustom = meta?.custom == true
        )
    }

    private fun readMeta(dir: File): Meta? {
        val f = File(dir, "meta.json")
        if (!f.exists()) return null
        return try {
            val o = org.json.JSONObject(f.readText())
            // optString 缺省用 ""：传 null 会使整链被推断为 Nothing?，accentColor 分支实际失效
            val hex = o.optString("accentColor").takeIf { it.isNotBlank() }
            Meta(
                accentColor = hex?.let { runCatching { ComposeColor(AndroidColor.parseColor(it)) }.getOrNull() },
                steamAppId = o.optLong("steamAppId", -1L).takeIf { it >= 0 },
                logoPosition = o.optString("logoPosition").takeIf { it.isNotBlank() }?.let { LogoPosition.fromJson(it) },
                custom = o.optBoolean("custom", false)
            )
        } catch (e: Exception) {
            // meta.json 损坏时留痕，便于排查游戏页主色丢失
            android.util.Log.w("FileGameArtProvider", "meta.json 解析失败: ${e.message}")
            null
        }
    }

    private data class Meta(
        val accentColor: ComposeColor?,
        val steamAppId: Long?,
        val logoPosition: LogoPosition? = null,
        val custom: Boolean = false
    )
}

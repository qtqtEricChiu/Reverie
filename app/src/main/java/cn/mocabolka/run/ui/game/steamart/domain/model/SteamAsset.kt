package cn.mocabolka.run.ui.game.steamart.domain.model

import java.io.File

/**
 * 单一 SteamDB 资产。
 *
 * 选链阶段（[AssetSelector]）仅确定 [url]，[localFile] 为 null；
 * 下载完成后由 [ExtractAssetsUseCase] 用真实本地文件重建，故两者均为可空。
 */
data class SteamAsset(
    val kind: AssetKind,
    val url: String?,
    val localFile: File?,
    /**
     * 保底落地文件名：当本资产是其它种类的保底替身（如 HERO_CAPSULE 缺失时借用 LIBRARY_CAPSULE 链接）
     * 时，用此字段强制指定下载文件名，避免与原始种类落地同名文件导致互相覆盖 / 回读错分类。
     * 为 null 时 [AssetDownloader] 回退到 URL basename 原样落地。
     */
    val forcedName: String? = null
)

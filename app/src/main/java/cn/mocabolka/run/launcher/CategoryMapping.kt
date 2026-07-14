package cn.mocabolka.run.launcher

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * 应用分类映射文件的导出 / 导入工具。
 *
 * 设计目标（用户诉求）：
 * 1. 导出「应用名 / 包名 / 安装来源 / 系统应用 / 当前分类 / AI分类」清单到公共下载目录；
 * 2. 文件用 JSON 格式（而非 txt），结构稳定，AI Agent 修改不易破坏整文件结构完整性；
 * 3. 顶部以 JSON 字段 `_guide` 携带给 AI 的指令，AI 只需填充每个 app 的 `ai` 字段；
 * 4. 导入通过系统文件选择器（SAF）由用户自行选择合规文件，不再硬编码读取固定路径。
 *
 * 位置固定为下载目录的 [FILE_NAME]（导出时覆盖写入；导入改由用户从系统选择器指定）。
 */
object CategoryMapping {
    /** 允许 AI 填写的目标分类，必须与 [categoryLabel] 输出集合完全一致。 */
    val CATEGORIES = listOf("游戏", "影视", "音乐", "图片", "社交", "资讯", "地图", "效率", "其他")

    const val FILE_NAME = "reverie_categories.json"

    /** 构建可导出的分类映射 JSON（含 AI 指令字段 _guide）。 */
    fun buildExportText(apps: List<AppModel>, pm: PackageManager): String {
        val guide = buildString {
            append("你是一名应用分类助手。请逐行阅读 apps 数组，根据每个应用的 label/package/installer ")
            append("判断真实类型，只修改每个 app 对象的 ai 字段为下列之一（不要新建分类、不要改动其他字段）：")
            append(CATEGORIES.joinToString(" / "))
            append("。规则：1)能明确识别的填对应分类；2)工具与娱乐兼具按主要用途归类；")
            append("3)无法识别填「其他」；4)当前分类已正确的可重复填写或留空；5)保持合法 JSON，仅改 ai。")
        }
        val root = JSONObject().apply {
            put("_guide", guide)
            put("categories", JSONArray(CATEGORIES))
            val arr = JSONArray()
            for (a in apps) {
                val installer = runCatching {
                    pm.getInstallSourceInfo(a.packageName).installingPackageName
                }.getOrNull() ?: "未知"
                arr.put(JSONObject().apply {
                    put("label", a.label)
                    put("package", a.packageName)
                    put("installer", installer)
                    put("system", a.isSystem)
                    put("current", a.categoryText)
                    put("ai", "")
                })
            }
            put("apps", arr)
        }
        return root.toString(2)
    }

    /**
     * 导出到公共下载目录（MediaStore，无需存储权限）。
     * @return 导出文件的 content:// Uri；失败返回 null。
     */
    fun exportToFile(context: Context, content: String): Uri? = runCatching {
        val resolver = context.contentResolver
        val existingId = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(FILE_NAME), null
        )?.use { c ->
            if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID)) else null
        }
        val uri = if (existingId != null) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                .appendPath(existingId.toString()).build()
        } else {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        }
        resolver.openOutputStream(uri, "wt")?.use { os ->
            os.write(content.toByteArray())
        } ?: return null
        if (existingId == null) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }
        uri
    }.getOrNull()

    /**
     * 从用户通过系统文件选择器选定的 URI 读取并解析分类映射。
     * @return 包名→分类 映射；解析失败或无有效条目返回 null。
     */
    fun importFromUri(context: Context, uri: Uri): Map<String, String>? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            parse(stream.bufferedReader().readText())
        }
    }.getOrNull()

    /** 解析 JSON 文本：遍历 apps 数组，取 package 与 ai 字段（需属于合法分类）。 */
    fun parse(text: String): Map<String, String>? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val arr = root.optJSONArray("apps") ?: return null
        val map = mutableMapOf<String, String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val pkg = obj.optString("package", "").trim()
            val ai = obj.optString("ai", "").trim()
            if (pkg.isNotBlank() && ai.isNotBlank() && ai in CATEGORIES) {
                map[pkg] = ai
            }
        }
        return if (map.isEmpty()) null else map
    }
}

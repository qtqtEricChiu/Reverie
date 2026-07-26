package cn.mocabolka.run.util

import cn.mocabolka.run.launcher.AppModel
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType

/**
 * 拼音搜索支持：将应用标签的汉字转为全拼与首字母，
 * 使搜索"设置"可通过"sz"（首字母）或"she…"（全拼前缀）命中。
 */
data class PinyinInfo(val full: String, val initials: String)

private val pinyinFormat = HanyuPinyinOutputFormat().apply {
    caseType = HanyuPinyinCaseType.LOWERCASE
    toneType = HanyuPinyinToneType.WITHOUT_TONE
}

private val cache = mutableMapOf<String, PinyinInfo>()

/** 计算字符串的拼音：全拼（小写）+ 首字母。CJK 字符取首个读音，其余字符原样保留。 */
fun pinyinOf(text: String): PinyinInfo = cache.getOrPut(text) {
    val full = StringBuilder()
    val initials = StringBuilder()
    for (ch in text) {
        if (ch.code in 0x4E00..0x9FFF) {
            val arr = PinyinHelper.toHanyuPinyinStringArray(ch, pinyinFormat)
            if (arr != null && arr.isNotEmpty()) {
                val py = arr[0]
                full.append(py)
                initials.append(py.first())
            } else {
                // 生僻字（字典未收录）：保留原字符，仍可按汉字本身匹配
                full.append(ch)
            }
        } else {
            full.append(ch)
            if (ch.isLetter()) initials.append(ch)
        }
    }
    PinyinInfo(full.toString().lowercase(), initials.toString().lowercase())
}

/**
 * 判断应用是否命中搜索词：
 * - 标签 / 包名 / 分类：原样（子串）匹配，兼容英文标签与包名片段；
 * - 拼音全拼 / 首字母：采用**前缀**匹配，避免子串过宽松导致"出来的应用和拼音对不上"。
 *   例：搜"sz" → 仅命中首字母前缀为 sz 的应用（设置）；搜"she" → 仅命中全拼前缀 she*（设置…）。
 */
fun AppModel.matchesSearch(q: String): Boolean {
    val ql = q.lowercase().trim()
    if (ql.isBlank()) return true
    if (label.contains(ql, ignoreCase = true)) return true
    if (packageName.contains(ql, ignoreCase = true)) return true
    if (categoryText.contains(ql, ignoreCase = true)) return true
    val py = pinyinOf(label)
    return py.full.startsWith(ql) || py.initials.startsWith(ql)
}

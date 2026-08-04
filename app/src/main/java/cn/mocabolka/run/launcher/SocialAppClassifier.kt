package cn.mocabolka.run.launcher

/**
 * 社交应用判定 + 通知发件人解析。
 *
 * 社交判定 = 内置包名白名单 ∪ 分类标签「社交」：
 * - 白名单兜底：即使系统/AI 映射未把微信归为社交，聚合也不丢。
 * - 分类扩展：[cn.mocabolka.run.launcher] 的 AppModel.categoryText 是全工程统一分类标签
 *   （CATEGORY_SOCIAL → "社交"，且 CategoryOverrideRepository 的 AI 回灌可覆盖），
 *   由上层（HomeViewModel）组装社交包名集合传入，本类不反向依赖 AppRepository。
 *
 * 发件人解析策略（针对国产社交通知的现实形态）：
 * 1. 优先取 title：微信/QQ 单聊 title 即发件人昵称，群聊 title 即群名。
 * 2. 若 title 等于应用自身显示名（如「微信」——折叠/摘要通知），回退从 text 解析
 *    `发件人: 内容` / `[n条] 发件人: 内容` 格式取真实发件人。
 * 3. 解析失败（无冒号分隔等）返回 null，该条不参与发件人聚合。
 */
object SocialAppClassifier {

    /** 内置社交包名白名单（微信/QQ 优先，附常见 IM）。 */
    val BUILTIN_SOCIAL: Set<String> = setOf(
        "com.tencent.mm",              // 微信
        "com.tencent.mobileqq",        // QQ
        "com.tencent.wework",          // 企业微信
        "com.alibaba.android.rimet",   // 钉钉
        "com.ss.android.lark",         // 飞书
        "org.telegram.messenger",      // Telegram
        "com.whatsapp",                // WhatsApp
    )

    /** 白名单应用的自身显示名（title 命中这些值时视为折叠/摘要通知，回退解析 text）。 */
    private val SELF_NAMES: Set<String> = setOf(
        "微信", "WeChat",
        "QQ", "QQ消息",
        "企业微信", "WeCom",
        "钉钉", "DingTalk",
        "飞书", "Lark", "Feishu",
        "Telegram",
        "WhatsApp",
    )

    /** 折叠通知 text 的条数前缀，如 `[2条] 张三: 内容` / `[2条]张三: 内容`。 */
    private val BUNDLE_PREFIX = Regex("""^\[\d+条]\s*""")

    /** `发件人: 内容` 分隔（中英文冒号均可；发件人段不含冒号，取最短匹配）。 */
    private val SENDER_PATTERN = Regex("""^(.+?)[:：]\s*""")

    /**
     * 社交判定：包名在内置白名单，或统一分类标签为「社交」。
     * [categoryText] 传 null 表示上层无分类信息，此时仅白名单兜底。
     */
    fun isSocial(packageName: String, categoryText: String?): Boolean =
        packageName in BUILTIN_SOCIAL || categoryText == "社交"

    /**
     * 从通知记录解析发件人（联系人/群名）。返回 null 表示无法可靠解析。
     */
    fun senderOf(record: NotificationRecord): String? {
        val title = record.title.trim()
        // 情形 1：title 就是发件人/群名（微信、QQ 的常规单聊/群聊通知）
        if (title.isNotEmpty() && title !in SELF_NAMES) return title
        // 情形 2：折叠/摘要通知（title 为应用自身名或空），回退解析 text
        var text = record.text.trim()
        if (text.isEmpty()) return null
        text = text.replace(BUNDLE_PREFIX, "")
        val m = SENDER_PATTERN.find(text) ?: return null
        val sender = m.groupValues[1].trim()
        return sender.takeIf { it.isNotEmpty() && it.length <= 40 && it !in SELF_NAMES }
    }
}

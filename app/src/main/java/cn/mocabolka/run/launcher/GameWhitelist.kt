package cn.mocabolka.run.launcher

/**
 * 游戏大屏模式优先呈现的包名（置顶固定）。
 * 即便未安装也会以「未安装」占位卡片呈现，方便用户识别。
 */
object GameWhitelist {
    val packages: List<String> = listOf(
        "com.kurogame.mingchao",      // 鸣潮
        "com.miHoYo.Nap",             // 绝区零
        "com.papegames.infinitynikki", // 无限暖暖
        "com.mojang.minecraftpe"       // Minecraft
    )

    /** 判断某个 category 是否属于「娱乐相关」（视频 / 音频）。 */
    fun isEntertainment(category: Int): Boolean =
        category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO ||
            category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO
}

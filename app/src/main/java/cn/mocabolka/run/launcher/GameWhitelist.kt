package cn.mocabolka.run.launcher

/**
 * 游戏大屏模式优先呈现的包名（置顶固定）。
 * 即便未安装也会以「未安装」占位卡片呈现，方便用户识别。
 */
object GameWhitelist {
    val packages: List<String> = listOf(
        "com.kurogame.mingchao",             // 鸣潮（国服 / CN）
        "com.kurogame.wutheringwaves.global", // 鸣潮（国际服 / Google Play）
        "com.miHoYo.Nap",                    // 绝区零（国服 / CN）
        "com.HoYoverse.ZenlessZoneZero",     // 绝区零（国际服 / Google Play）
        "com.papegames.infinitynikki",       // 无限暖暖（国服 / CN）
        "com.infinitynikki.gp",              // 无限暖暖（国际服 / Google Play）
        "com.mojang.minecraftpe"             // Minecraft（全球同包名）
    )

    /** 判断某个 category 是否属于「娱乐相关」（视频 / 音频）。 */
    fun isEntertainment(category: Int): Boolean =
        category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO ||
            category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO
}

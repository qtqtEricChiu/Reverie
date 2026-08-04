package cn.mocabolka.run.ui.game

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon

/**
 * 桌面快捷方式基础设施（独立于游戏页，供任意调用方固定「游戏快捷方式」）。
 *
 * 快捷方式语义：
 * - 每游戏唯一 id（`game_{包名}`），重复固定会覆盖旧图标而非新增；
 * - Intent 指向 [GamePageActivity]，携带包名 + 自动启动标记：
 *   用户从桌面点击后先短暂展示游戏页（背景/详情），随后自动以开场动画启动游戏。
 */
object ShortcutHelper {

    /**
     * 固定游戏快捷方式到桌面。
     *
     * @param context 任意 Context（内部取系统 [ShortcutManager]）。
     * @param packageName 游戏包名（作为唯一 id 的组成与目标页参数）。
     * @param label 快捷方式名称（游戏名，桌面图标下方文字）。
     * @param iconBitmap 快捷方式图标位图（游戏应用图标）。
     * @return 系统是否受理固定请求（部分桌面不支持固定时返回 false）。
     */
    fun pinGameShortcut(
        context: Context,
        packageName: String,
        label: String,
        iconBitmap: Bitmap
    ): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false

        // Intent 指向游戏页并带自动启动标记；快捷方式 Intent 必须显式 action。
        val intent = Intent(context, GamePageActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(GamePageActivity.EXTRA_PACKAGE, packageName)
            putExtra(GamePageActivity.EXTRA_AUTO_LAUNCH, true)
        }

        val info = ShortcutInfo.Builder(context, "game_$packageName")
            .setShortLabel(label)
            .setIcon(Icon.createWithBitmap(iconBitmap))
            .setIntent(intent)
            .build()

        return runCatching { manager.requestPinShortcut(info, null) }.getOrDefault(false)
    }
}

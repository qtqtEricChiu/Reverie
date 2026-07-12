package cn.mocabolka.run.ui

import android.view.Display
import android.view.Window

/**
 * 自适应刷新率：Reverie 在交互时请求高刷新率以获得满血手感，
 * 静止（空闲）一段时间后回落到「平衡刷新率」以省电（R13）。
 *
 * 通过 WindowManager.LayoutParams.preferredDisplayModeId 选择对应刷新率的显示模式
 * （Display.Mode / preferredDisplayModeId 自 API 23 起可用，全版本安全）。
 *
 * 平衡刷新率策略：优先选择最接近 60Hz 的模式（肉眼流畅且显著低于 90/120Hz 的功耗）；
 * 若本屏最高刷新率本就不高于 60Hz，则回落到该屏最低刷新率。
 */
object DisplayRefresh {
    /** 交互态：请求本分辨率下的最高刷新率。 */
    fun applyHigh(window: Window) = apply(window, high = true)

    /** 空闲态：回落到平衡刷新率（最接近 60Hz）。 */
    fun applyBalanced(window: Window) = apply(window, high = false)

    private fun apply(window: Window, high: Boolean) {
        val display: Display = window.windowManager.defaultDisplay
        val modes = display.supportedModes.toList()
        if (modes.isEmpty()) return
        // 仅在本屏当前分辨率下选择刷新率，避免双屏/折叠屏选到另一块屏的模式（P1-9）
        val current = display.mode ?: return
        val sameRes = modes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }.takeIf { it.isNotEmpty() } ?: modes
        val target = if (high) {
            sameRes.maxByOrNull { it.refreshRate }
        } else {
            // 平衡模式：优先最接近 60Hz，避免直接掉到最低导致可见卡顿
            sameRes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }
                ?: sameRes.minByOrNull { it.refreshRate }
        } ?: return
        val params = window.attributes
        params.preferredDisplayModeId = target.modeId
        window.attributes = params
    }

    /** 进入/退出大屏模式时调用：high=true 请求高刷，false 回落平衡。 */
    fun applySafe(window: Window?, high: Boolean) {
        val w = window ?: return
        if (high) applyHigh(w) else applyBalanced(w)
    }
}

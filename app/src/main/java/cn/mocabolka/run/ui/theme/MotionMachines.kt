package cn.mocabolka.run.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp

/**
 * 动画机列表（Motion Machines）——全局唯一的「自绘制动画」过渡工厂。
 *
 * 定位：
 * - [MotionSpec] / [PulseSpec] 是「时长 / 缓动 / 位移量」的**单一数值来源**；
 * - [MotionMachines] 是在其之上封装的**可复用过渡机**（[EnterTransition] /
 *   [ExitTransition] / [ContentTransform]），供全局各页面（主页、二级子页、三级子页）
 *   统一引用，杜绝在各调用点手写 `fadeIn + slideInHorizontally { ... }` 的散落逻辑。
 *
 * 归一化原则：
 * 1. 所有 **Tab / 分段 / 板块内容切换** 一律走 [panelSwitch]（主页基准：1/[PANEL_SLIDE_DIVISOR] 屏宽横移 + 淡入淡出）。
 * 2. 所有 **整页级横向切换**（如游戏页「切游戏」）走 [pageSwitch]（全宽横移，视觉更强，时长/缓动一致）。
 * 3. 所有 **沉浸子页 Activity 入场 / 出场**（About / Licenses / CompatGuide / GamePage）走 [subPageEnter] / [subPageExit]。
 * 4. 所有 **居中弹窗** 走 [dialogEnter] / [dialogExit]（MD3：scaleIn 0.92→1 + 对称 fade）。
 * 5. 所有 **底部浮层 / Toast** 走 [bottomSheetEnter] / [bottomSheetExit] / [toastEnter] / [toastExit]。
 * 6. 方向判定统一走 [linearForward] / [cyclicForward]，禁止各处手写 `if (target > initial) 1 else -1`。
 *
 * 所有过渡机均以 [reduceMotion] 收口：开启时退化为瞬时切换（[EnterTransition.None] / [ExitTransition.None]），
 * 与 MD3 无障碍规范一致。
 */
object MotionMachines {

    // ── 位移比例常量（主页基准，全局唯一） ──
    /** 板块切换横向位移比例：屏宽 1/20（跟手、几乎无感，主页基准）。 */
    const val PANEL_SLIDE_DIVISOR = 20
    /** 沉浸子页入场横向位移比例：屏宽 1/6（页面级，视觉更明显）。 */
    const val SUBPAGE_SLIDE_DIVISOR = 6
    /** 弹窗缩放起点（MD3：0.92→1）。 */
    const val DIALOG_SCALE = 0.92f

    // ─────────────────────────────────────────────────────────────
    // 1. 板块切换机（Tab / 分段内容）—— 主页基准，全局统一
    // ─────────────────────────────────────────────────────────────
    /**
     * 【板块切换机】主页 / 子页 / 二三级子页的 Tab、分段内容切换统一动画。
     *
     * 与主页滑块（QuadTabSlider）切换动画完全一致：
     * 淡入淡出 [MotionSpec.TabFade] + 1/[PANEL_SLIDE_DIVISOR] 屏宽横向位移 [MotionSpec.TabSlideOffset]。
     *
     * @param forward true=新内容自右侧滑入（前进）；false=自左侧滑入（后退）。
     * @param reduceMotion true=退化为瞬时切换（无位移 / 无淡入）。
     */
    fun panelSwitch(forward: Boolean, reduceMotion: Boolean): ContentTransform {
        if (reduceMotion) return EnterTransition.None togetherWith ExitTransition.None
        val dir = if (forward) 1 else -1
        return (fadeIn(animationSpec = MotionSpec.TabFade) +
            slideInHorizontally(animationSpec = MotionSpec.TabSlideOffset) { dir * it / PANEL_SLIDE_DIVISOR }) togetherWith
            (fadeOut(animationSpec = MotionSpec.TabFade) +
                slideOutHorizontally(animationSpec = MotionSpec.TabSlideOffset) { -dir * it / PANEL_SLIDE_DIVISOR })
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 整页切换机（切游戏等）—— 全宽横移，与板块共用时长/缓动
    // ─────────────────────────────────────────────────────────────
    /**
     * 【整页切换机】整页级横向切换（如游戏页 LT/RT「切游戏」）。
     * 位移量为全屏宽（视觉更强），时长 / 缓动与 [panelSwitch] 保持同族。
     *
     * @param forward true=前进（自右侧滑入）；false=后退。
     * @param reduceMotion true=退化为瞬时切换。
     */
    fun pageSwitch(forward: Boolean, reduceMotion: Boolean): ContentTransform {
        if (reduceMotion) return EnterTransition.None togetherWith ExitTransition.None
        val dir = if (forward) 1 else -1
        return (fadeIn(animationSpec = MotionSpec.TabFade) +
            slideInHorizontally(animationSpec = MotionSpec.SlideOffset) { dir * it } +
            scaleIn(
                initialScale = MotionSpec.PageEnterScale,
                transformOrigin = TransformOrigin.Center,
                animationSpec = MotionSpec.PageScale
            )) togetherWith
            (fadeOut(animationSpec = MotionSpec.TabFade) +
                slideOutHorizontally(animationSpec = MotionSpec.SlideOffset) { -dir * it } +
                scaleOut(
                    targetScale = MotionSpec.PageExitScale,
                    transformOrigin = TransformOrigin.Center,
                    animationSpec = MotionSpec.PageScale
                ))
    }

    /**
     * 【交叉淡入机】同位内容切换（如游戏头图 ↔ 版头 header 切换）：纯淡入淡出、无位移。
     * 用于「原地替换」而非「左右翻页」的场景。
     */
    fun crossfade(): ContentTransform =
        fadeIn(animationSpec = MotionSpec.TabFade) togetherWith fadeOut(animationSpec = MotionSpec.TabFade)

    /**
     * 【背景交叉淡入机】游戏页背景图切换专用：比通用 [crossfade] 更长（[MotionSpec.BackgroundCrossfade] 380ms），
     * 消解背景图 Crossfade 中途先展示纯 accent 渐变、再跳变为 bitmap 的硬切感。
     */
    fun backgroundCrossfade(): ContentTransform =
        fadeIn(animationSpec = MotionSpec.BackgroundCrossfade) togetherWith
            fadeOut(animationSpec = MotionSpec.BackgroundCrossfade)

    /**
     * 【Widget 渐显机】个性化面板 Widget 的「加载转圈 ↔ Widget 内容」「切游戏复用」切换：
     * 纯淡入淡出、无位移（[MotionSpec.WidgetFade] 300ms），消解「转圈骤停 → Widget 突现」
     * 及 `key(widgetVersion)` 强制重建导致的硬切割裂感。
     *
     * @param reduceMotion true=退化为瞬时切换（无淡入淡出），与 MD3 无障碍规范一致。
     */
    fun widgetFade(reduceMotion: Boolean): ContentTransform =
        if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
        else fadeIn(animationSpec = MotionSpec.WidgetFade) togetherWith
            fadeOut(animationSpec = MotionSpec.WidgetFade)

    // ─────────────────────────────────────────────────────────────
    // 3. 沉浸子页 Activity 入场 / 出场
    // ─────────────────────────────────────────────────────────────
    /** 【子页入场机】沉浸子页（About / Licenses / CompatGuide / GamePage）根 AnimatedVisibility 入场。 */
    fun subPageEnter(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) EnterTransition.None
        else fadeIn(animationSpec = MotionSpec.Medium) +
            slideInHorizontally(animationSpec = MotionSpec.SlideOffset) { it / SUBPAGE_SLIDE_DIVISOR }

    /** 【子页出场机】与 [subPageEnter] 对称。 */
    fun subPageExit(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) ExitTransition.None
        else fadeOut(animationSpec = MotionSpec.Medium) +
            slideOutHorizontally(animationSpec = MotionSpec.SlideOffset) { -it / SUBPAGE_SLIDE_DIVISOR }

    // ─────────────────────────────────────────────────────────────
    // 4. 居中弹窗（MD3：scaleIn 0.92→1 + 对称 fade）
    // ─────────────────────────────────────────────────────────────
    /** 【弹窗入场机】所有居中弹窗（Confirm / Info / Dropdown / DateRange）统一入场。 */
    fun dialogEnter(): EnterTransition =
        fadeIn(animationSpec = MotionSpec.DialogEnter) +
            scaleIn(
                initialScale = DIALOG_SCALE,
                animationSpec = MotionSpec.DialogEnter,
                transformOrigin = TransformOrigin.Center
            )

    /** 【弹窗出场机】与 [dialogEnter] 对称。 */
    fun dialogExit(): ExitTransition =
        fadeOut(animationSpec = MotionSpec.DialogExit) +
            scaleOut(
                targetScale = DIALOG_SCALE,
                animationSpec = MotionSpec.DialogExit,
                transformOrigin = TransformOrigin.Center
            )

    // ─────────────────────────────────────────────────────────────
    // 5. 底部浮层 / Toast（自底部滑入）
    // ─────────────────────────────────────────────────────────────
    /** 【底部浮层入场机】详情抽屉 / 底部面板：淡入 + 自底部完整滑入。 */
    fun bottomSheetEnter(): EnterTransition =
        fadeIn(animationSpec = MotionSpec.Medium) +
            slideInVertically(animationSpec = MotionSpec.SlideOffset) { it }

    /** 【底部浮层出场机】与 [bottomSheetEnter] 对称。 */
    fun bottomSheetExit(): ExitTransition =
        fadeOut(animationSpec = MotionSpec.Medium) +
            slideOutVertically(animationSpec = MotionSpec.SlideOffset) { it }

    /** 【Toast 入场机】轻量提示：快速淡入 + 自底部轻微上滑（1/3 高）。 */
    fun toastEnter(): EnterTransition =
        fadeIn(animationSpec = MotionSpec.Fast) +
            slideInVertically(animationSpec = MotionSpec.TabSlideOffset) { it / 3 }

    /** 【Toast 出场机】与 [toastEnter] 对称。 */
    fun toastExit(): ExitTransition =
        fadeOut(animationSpec = MotionSpec.Fast) +
            slideOutVertically(animationSpec = MotionSpec.TabSlideOffset) { it / 3 }

    // ─────────────────────────────────────────────────────────────
    // 6. 方向判定辅助（消除各处手写 dir 逻辑）
    // ─────────────────────────────────────────────────────────────
    /** 线性方向：目标索引更大 → 前进（自右滑入）。用于主页 Tab、游戏页 Tab 等有序切换。 */
    fun linearForward(from: Int, to: Int): Boolean = to > from

    /**
     * 循环最短距离方向：用于环形列表（如游戏页「切游戏」）。
     * 前进（RT / 下一款）始终自右滑入，后退（LT / 上一款）始终自左；
     * 循环到头（末→首）仍保持前进方向，避免反向闪跳。
     * 任一索引无效时默认前进。
     */
    fun cyclicForward(from: Int, to: Int, size: Int): Boolean {
        if (from < 0 || to < 0 || size <= 0) return true
        return ((to - from + size) % size) <= size / 2
    }

    // ─────────────────────────────────────────────────────────────
    // 7. 数据波动（数值缓动）—— 数字随数据源变化时的计数缓动
    // ─────────────────────────────────────────────────────────────
    /**
     * 【数据波动机】数值随数据源（指标维度 / 统计周期）变化时的缓动规格，全局唯一来源。
     *
     * 统计页「使用时长 / 启动次数」切换、日 / 周 / 月 / 年周期切换、各指标卡（总计 / 最长使用）
     * 的计数缓动一律经此引用，杜绝在 [AnimatedNumberText] 等处手写
     * `tween(600, FastOutSlowInEasing)` 的散落计时。
     *
     * [reduceMotion] 时退化为瞬时（[tween] 0ms），与 MD3 无障碍规范一致。
     */
    const val NUMBER_FLUCTUATION_MS = 600

    fun numberFluctuation(reduceMotion: Boolean): AnimationSpec<Float> =
        if (reduceMotion) tween(0) else tween(NUMBER_FLUCTUATION_MS, easing = FastOutSlowInEasing)

    // ─────────────────────────────────────────────────────────────
    // 8. 头图比例适配（HeroBanner header 切换时容器高度插值）
    // ─────────────────────────────────────────────────────────────
    /** 头图 header 切换时容器高度的插值时长（与 demo 对齐：420ms）。 */
    const val HERO_HEADER_RESIZE_MS = 420

    /**
     * 【头图比例适配机】HeroBanner 点击切换 header 版头图时，容器高度在
     * hero 高度与 header 自身比例高度（约 460×215）之间插值，
     * 实现「取消裁切、完整保留 header 原比例」的平滑过渡。
     *
     * [reduceMotion] 时退化为瞬时（tween(0)），与 MD3 无障碍规范一致。
     */
    fun heroHeaderResize(reduceMotion: Boolean): AnimationSpec<Dp> =
        if (reduceMotion) tween(0, easing = FastOutSlowInEasing)
        else tween(HERO_HEADER_RESIZE_MS, easing = FastOutSlowInEasing)
}

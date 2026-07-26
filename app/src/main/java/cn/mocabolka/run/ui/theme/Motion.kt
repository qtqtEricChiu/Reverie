package cn.mocabolka.run.ui.theme

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 统一动效引擎（高帧率 / GPU 合成优先）。
 *
 * 设计原则：
 * 1. 无限循环动画一律走 [rememberInfiniteTransition] + [Modifier.graphicsLayer]，
 *    仅改变图形层变换（translate/scale/alpha/rotation），**不触发 Composable 重组**，
 *    因此在 60/120Hz 屏上都能满帧运行。
 * 2. 所有循环动画均受 [reduceMotion] 控制；开启时退化为静态常量，避免晕动症。
 * 3. 模糊（玻璃拟态）使用 [Modifier.blur]（Android 12+ RenderEffect，GPU 高效）。
 * 4. **MD3 动画规范一致性**：所有过渡时长 / 缓动 / 遮罩透明度 / 入场错峰均收敛到
 *    [MotionSpec] 与 [PulseSpec] 单一来源，组件不得再硬编码散落的时长与 easing。
 */

/** 标准动画规格常量（全局唯一来源）。 */
object MotionSpec {
    // ── 基础过渡时长（MD3 推荐区间）──
    /** 快速反馈（开关、小元素），220ms。 */
    val Fast = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
    /** 标准过渡（列表入场、卡片），360ms。 */
    val Medium = tween<Float>(durationMillis = 360, easing = FastOutSlowInEasing)
    /** 缓慢过渡（页面级），520ms。 */
    val Slow = tween<Float>(durationMillis = 520, easing = EaseInOut)

    // ── 弹窗专用（MD3：scaleIn 0.92→1 + fadeIn 对称，居中锚点）──
    /** 弹窗入场时长，320ms。 */
    val DialogEnter = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
    /** 弹窗出场时长，240ms。 */
    val DialogExit = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
    /** Tab / 大区块切换滑动时长，320ms（Float 版本，用于 alpha/scale）。 */
    val Slide = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
    /** 滑动偏移版本（IntOffset），用于 slideInHorizontally / slideInVertically 的 animationSpec。 */
    val SlideOffset = tween<IntOffset>(durationMillis = 320, easing = FastOutSlowInEasing)

    // ── Tab 切换专用（性能优化：缩短时长 + 减小位移）──
    /** Tab 切换 fade 时长：从 220ms 缩到 140ms，更跟手。 */
    val TabFade = tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)
    /** Tab 切换 slide 时长：从 320ms 缩到 180ms，避免旧 tab 长时间驻留导致双列表重绘。 */
    val TabSlideOffset = tween<IntOffset>(durationMillis = 180, easing = FastOutSlowInEasing)

    // ── 背景切换专用（游戏页背景图 Crossfade）──
    /** 背景图 Crossfade 时长：380ms，比通用 [TabFade]（140ms）更长，消解背景硬切的视觉割裂。 */
    val BackgroundCrossfade = tween<Float>(durationMillis = 380, easing = EaseInOut)

    // ── Widget 渐显专用（个性化面板 Widget 加载 / 内容切换）──
    /** Widget 渐入渐出时长：300ms（EaseInOut），消解「加载转圈 → Widget 突现 / 切游戏复用」的硬切感。 */
    val WidgetFade = tween<Float>(durationMillis = 300, easing = EaseInOut)

    // ── 整页切换专用（切游戏）：横向滑动 + 淡入淡出之上的纵深缩放 ──
    /** 整页切换缩放时长，与 [SlideOffset] 同族（320ms，纵深感）。 */
    val PageScale = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
    /** 整页切换进入起始缩放（0.94→1：旧页退场、新页浮出）。 */
    const val PageEnterScale = 0.94f
    /** 整页切换退场目标缩放（1→0.94）。 */
    const val PageExitScale = 0.94f

    // ── 弹簧（焦点缩放 / 角标弹跳）──
    /** 焦点态缩放弹簧：中等刚度 + 中等回弹（全组件统一手感）。 */
    val FocusSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    /** 角标 / 收藏弹跳弹簧：高刚度（更利落）。 */
    val PopSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // ── 视觉常量 ──
    /** 遮罩（scrim）统一透明度：所有弹窗 / 启动遮罩共用，避免层级混乱。 */
    const val ScrimAlpha = 0.55f

    /** 入场错峰延迟（ms）：统一算法，消除散落的 6/18/24ms 硬编码。
     *  为平衡跟手感与入场动画，[perMs] 从 24 降至 12（更快完成错峰），
     *  [maxMs] 从 480 降至 240（长列表前 20 项即有入场感）。
     *  [index] 行序号，[perMs] 每行间隔，[maxMs] 上限避免长列表卡顿。 */
    fun staggerDelay(index: Int, perMs: Int = 12, maxMs: Int = 240): Long =
        (index.coerceAtLeast(0) * perMs).coerceAtMost(maxMs).toLong()

    /** 焦点缩放幅度（统一）：聚焦态相对原始尺寸的放大比例。 */
    const val FocusScale = 1.14f
    /** 按下缩放幅度（统一）。 */
    const val PressScale = 0.95f
}

/**
 * 呼吸光晕统一参数（C3-1）：列表项与详情面板按钮共用同一组极值，
 * 保证全局焦点反馈节奏一致，避免两处脉动幅度/周期漂移造成视觉割裂。
 */
object PulseSpec {
    /** 光晕最低 alpha（静止基准）。 */
    const val MIN = 0.32f
    /** 光晕最高 alpha（峰值）。 */
    const val MAX = 0.9f
    /** 标准呼吸周期（ms），全局统一。 */
    const val PERIOD_MS = 1600
    /** 缓慢呼吸周期（ms），用于空状态等弱化场景。 */
    const val PERIOD_SLOW_MS = 2200
    /** 用于 [androidx.compose.ui.graphics.graphicsLayer] 的缩放换算基准（与 MIN 对应）。 */
    const val BASE = 0.62f
}

/**
 * 是否应减少动态效果。当前由用户设置驱动（未来可并入系统无障碍）。
 * 任意一处开启即冻结所有无限循环动画。
 */
@Composable
fun rememberReduceMotion(userReduce: Boolean): Boolean = userReduce

/**
 * 呼吸光晕脉动系数（[PulseSpec.MIN]..[PulseSpec.MAX] 循环）。
 * 仅在 [active] 且未开启减少动态时启动无限过渡，否则返回静态常量，零开销。
 * 周期默认 [PulseSpec.PERIOD_MS]（C3-1 全局统一）。
 */
@Composable
fun rememberPulse(active: Boolean, reduceMotion: Boolean, periodMs: Int = PulseSpec.PERIOD_MS): Float {
    if (!active || reduceMotion) return PulseSpec.BASE
    val inf = rememberInfiniteTransition(label = "pulse")
    return inf.animateFloat(
        initialValue = PulseSpec.MIN,
        targetValue = PulseSpec.MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseVal"
    ).value
}

/**
 * 焦点缩放修饰：统一所有可聚焦元素的"聚焦/按下"缩放手感。
 * - [active]：是否处于聚焦态（聚焦放大 [MotionSpec.FocusScale]，否则原始）。
 * - [pressed]：是否按下（按下缩到 [MotionSpec.PressScale]）。
 * - [reduceMotion]：开启时退化为固定 1f（零缩放动画，避免晕动症）。
 * 内部走 [animateFloatAsState] + 统一 [MotionSpec.FocusSpring]，零重组开销。
 */
@Composable
fun Modifier.focusScale(
    active: Boolean,
    pressed: Boolean = false,
    reduceMotion: Boolean = false
): Modifier {
    val target = when {
        pressed -> MotionSpec.PressScale
        active -> MotionSpec.FocusScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = if (reduceMotion) 1f else target,
        animationSpec = MotionSpec.FocusSpring,
        label = "focusScale"
    )
    return this.then(scale(scale))
}

/**
 * 数字滚动文本：以缓动方式从旧值计数到新值（高帧率）。
 * [reduceMotion] 时立即到位（不计数），符合无障碍规范。
 */
@Composable
fun AnimatedNumberText(
    value: Long,
    format: (Long) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    reduceMotion: Boolean = false
) {
    // 数字波动动画：计时规格统一走全局动画机 [MotionMachines.numberFluctuation]，
    // 不再硬编码 tween(600, FastOutSlowInEasing)。统计页「使用时长/启动次数」切换、
    // 日周月年周期切换均经此获得一致计数缓动，且不触发组件树重载。
    val f by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = MotionMachines.numberFluctuation(reduceMotion),
        label = "numCount"
    )
    androidx.compose.material3.Text(
        text = format(f.toLong()),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * 玻璃拟态修饰（磨砂模糊层）：对**本图层内容**施加高斯模糊。
 *
 * 重要方向性说明：Compose / Android 的模糊只能作用于图层**自身内容**
 * （无 backdrop-blur API），因此该修饰符必须挂在**背景层**
 * （如 HomeScreen 壁纸 Image）上，让被卡片覆盖的底图整体磨砂；
 * 前景卡片只做 alpha 半透明 + [glassHighlight] 高光，两层叠加即为完整的
 * iOS/macOS 风格磨砂玻璃链路。**严禁挂在含文字的前景容器上**（文字会被糊掉）。
 *
 * - 基于 [androidx.compose.ui.draw.blur]（GPU RenderEffect，minSdk=36 恒可用，无需版本守卫）。
 * - [enabled]=false 时退化为无操作（零开销）。
 */
fun Modifier.frosted(enabled: Boolean, blurRadius: Dp = 18.dp): Modifier = this.then(
    if (!enabled) Modifier
    else Modifier.blur(blurRadius)
)

/**
 * 玻璃高光修饰：在内容之上叠加「顶部光源反射」——白色渐变自顶向下衰减
 * （0.12f → 0.04f → 透明，止于 40% 高度），模拟玻璃面板上缘受光。
 * 与 [frosted]（挂背景层）配套构成磨砂玻璃质感；本修饰符挂**前景卡片**，
 * 纯 draw 阶段叠加，不触发重组、不影响命中测试。
 * [enabled]=false 时退化为无操作（零开销）。
 */
fun Modifier.glassHighlight(enabled: Boolean): Modifier = this.then(
    if (!enabled) Modifier
    else Modifier.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.4f
            )
        )
    }
)

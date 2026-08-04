package cn.mocabolka.run.ui.game

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 游戏页启动动画悬浮窗（横屏 / 竖屏通用）。
 *
 * 以 TYPE_APPLICATION_OVERLAY 在「应用进程」内持有（不依赖 Activity 实例），
 * 故游戏页 finish 后动画仍浮于游戏上层；游戏后台启动、动画悬挂 1~2 秒后淡出移除，
 * 既能覆盖游戏启动空白期，又不受横竖旋屏切换影响。
 *
 * 版本：
 * - [showLayered]：有 hero（横竖通用）→ hero 全屏背景 + logo + 应用名（无前景 capsule / header）；
 * - [showSimple]：无美术 → 图标 + 应用名（不变）；
 * - [showDeferred]：可折叠设备 → 先启动游戏、Reverie 退后台后再按实际方向选版，避免折叠/展开方向错位。
 *
 * 仅在「已授权 SYSTEM_ALERT_WINDOW」时由调用方启用；否则调用方直接启动游戏、无动画。
 */
object GameLaunchOverlay {

    private var wm: WindowManager? = null
    private var root: View? = null

    /** 延迟淡出任务；show() 复用/remove() 时必须取消，避免旧任务误移除新窗口。 */
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    private val fade = AccelerateDecelerateInterpolator()

    /**
     * 分层启动动画（横竖通用，美术资源版）：
     * - [hero] 全屏背景（CENTER_CROP）+ 半透明压暗遮罩；
     * - [logo] 与 [name] 居中纵向堆叠浮于 hero 之上；
     * 悬挂 [durationMs] 后淡出移除。
     */
    fun showLayered(
        context: Context,
        hero: android.graphics.Bitmap,
        logo: android.graphics.Bitmap?,
        name: String,
        portrait: Boolean,
        durationMs: Long = 2000L
    ) {
        val ctx = context.applicationContext
        val density = ctx.resources.displayMetrics.density

        val heroView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(
                runCatching { hero.copy(android.graphics.Bitmap.Config.ARGB_8888, false) }
                    .getOrDefault(hero)
            )
            alpha = 0f
            scaleX = 1.06f
            scaleY = 1.06f
        }
        val scrim = View(ctx).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            alpha = 0f
        }
        val container = FrameLayout(ctx).apply {
            addView(heroView)
            addView(scrim)
        }

        // logo + 应用名：居中纵向堆叠浮于 hero 之上（无前景 capsule / header）。
        val stack = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // logo：仅缩小悬浮窗启动标，不动游戏页主体图标。
        val logoView = logo?.let { bmp ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(bmp)
                alpha = 0f
                scaleX = 0.6f
                scaleY = 0.6f
            }
        }
        val nameView = TextView(ctx).apply {
            text = name
            setTextColor(0xFFE8E8EC.toInt())
            textSize = if (portrait) 20f else 22f
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = 16f * density
        }
        logoView?.let {
            stack.addView(
                it,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ((if (portrait) 16 else 12) * density).roundToInt() }
            )
        }
        stack.addView(
            nameView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).roundToInt() }
        )
        container.addView(
            stack,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        logoView?.let {
            it.animate().alpha(1f).scaleX(0.6f).scaleY(0.6f)
                .setDuration(320).setStartDelay(420).setInterpolator(fade).start()
        }
        nameView.animate().alpha(1f).translationY(0f)
            .setDuration(320).setStartDelay(420).setInterpolator(fade).start()
        heroView.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(260).setInterpolator(fade).start()
        scrim.animate().alpha(0.6f).setDuration(240).setStartDelay(200).start()

        show(ctx, container, durationMs)
    }

    /** 无美术：纯应用图标 + 应用名字（深色渐变底），悬挂 [durationMs] 后淡出移除。
     *  [isLandscape] 为 false（竖屏降级）时图标略缩小、字号与间距收窄，避免竖屏下偏上。 */
    fun showSimple(
        context: Context,
        icon: android.graphics.Bitmap,
        name: String,
        isLandscape: Boolean = true,
        durationMs: Long = 1500L
    ) {
        val ctx = context.applicationContext
        val density = ctx.resources.displayMetrics.density
        val bg = View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF101015.toInt(), 0xFF050507.toInt())
            )
        }
        val iconView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(icon)
            alpha = 0f
            scaleX = 0.82f
            scaleY = 0.82f
        }
        val nameView = TextView(ctx).apply {
            text = name
            setTextColor(0xFFE8E8EC.toInt())
            textSize = if (isLandscape) 22f else 20f
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = 16f * density
        }
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val iconSize = ((if (isLandscape) 124 else 112) * density).roundToInt()
        iconView.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        column.addView(iconView)
        column.addView(
            nameView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = ((if (isLandscape) 20 else 16) * density).roundToInt() }
        )
        val container = FrameLayout(ctx).apply {
            addView(bg)
            addView(
                column,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }
        iconView.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(280).setInterpolator(fade).start()
        nameView.animate().alpha(1f).translationY(0f)
            .setDuration(320).setStartDelay(140).setInterpolator(fade).start()

        show(ctx, container, durationMs)
    }

    private fun show(ctx: Context, container: FrameLayout, durationMs: Long) {
        remove()
        val windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            windowManager.addView(container, params)
            wm = windowManager
            root = container
        } catch (e: Exception) {
            android.util.Log.w("GameLaunchOverlay", "悬浮窗添加失败，跳过启动动画", e)
            return
        }
        val runnable = Runnable {
            // 仅当自己仍是当前窗口时才执行淡出，避免误移除后来者
            if (root === container) {
                container.animate().alpha(0f).setDuration(240).withEndAction { remove() }.start()
            }
        }
        dismissRunnable = runnable
        handler.postDelayed(runnable, durationMs)
    }

    /**
     * 可折叠设备专用：先启动游戏并让 Reverie 退到后台，再按「实际屏幕方向」展示启动动画，
     * 避免折叠/展开过程中动画方向（折叠态竖屏）与游戏最终方向（展开态横屏）错位。
     *
     * 实现：注册 [Application.ActivityLifecycleCallbacks] 统计前台 Activity 数，归零（即 Reverie 整体
     * 退后台、游戏已在前台）即触发 [onReady]，并注销回调。另设 3s 超时兜底：回调未触发（如游戏与
     * Reverie 同任务不触发退后台）也强制按当前方向展示，杜绝动画永不出现。
     *
     * 注：项目未引入 lifecycle-process，故用 ActivityLifecycleCallbacks 替代 ProcessLifecycleOwner，
     * 无新增依赖。
     */
    fun showDeferred(
        context: Context,
        onReady: (Context, Int) -> Unit
    ) {
        val appCtx = context.applicationContext as Application
        var fired = false
        var callbacks: Application.ActivityLifecycleCallbacks? = null
        var timeoutRunnable: Runnable? = null
        val fire: () -> Unit = fireLambda@{
            if (fired) return@fireLambda
            fired = true
            timeoutRunnable?.let { handler.removeCallbacks(it) }
            callbacks?.let { appCtx.unregisterActivityLifecycleCallbacks(it) }
            // 此时 Reverie 已退后台，取真实屏幕方向供调用方选版
            val orientation = appCtx.resources.configuration.orientation
            onReady(appCtx, orientation)
        }
        callbacks = object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) { started++ }
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {
                started--
                if (started <= 0) fire()
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        }
        appCtx.registerActivityLifecycleCallbacks(callbacks)
        // 3s 超时兜底：回调未触发也按当前方向展示
        timeoutRunnable = Runnable { fire() }
        handler.postDelayed(timeoutRunnable, 3000L)
    }

    /** 立即移除悬浮窗（如启动失败回滚）。removeView 可能因窗口已脱离抛异常，统一兜底。 */
    fun remove() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        val r = root
        val manager = wm
        wm = null
        root = null
        if (r != null && manager != null) {
            runCatching { manager.removeViewImmediate(r) }
                .onFailure { android.util.Log.w("GameLaunchOverlay", "悬浮窗移除失败: ${it.message}") }
        }
    }
}

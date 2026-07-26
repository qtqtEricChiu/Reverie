package cn.mocabolka.run.ui.game

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 游戏页横屏启动动画悬浮窗。
 *
 * 以 TYPE_APPLICATION_OVERLAY 在「应用进程」内持有（不依赖 Activity 实例），
 * 故游戏页 finish 后动画仍浮于游戏上层；游戏后台启动、动画悬挂 1~2 秒后淡出移除，
 * 既能覆盖游戏启动空白期，又不受横竖旋屏切换影响。
 *
 * 仅在「横屏 + 已授权 SYSTEM_ALERT_WINDOW」时由调用方启用；否则调用方直接启动游戏、无动画。
 */
object GameLaunchOverlay {

    private var wm: WindowManager? = null
    private var root: View? = null

    /** 延迟淡出任务；show() 复用/remove() 时必须取消，避免旧任务误移除新窗口。 */
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    private val fade = AccelerateDecelerateInterpolator()

    /** 有美术：hero 铺满 → 半透明黑遮罩 → logo 浮现，悬挂 [durationMs] 后淡出移除。 */
    fun showArt(
        context: Context,
        hero: android.graphics.Bitmap,
        logo: android.graphics.Bitmap?,
        durationMs: Long = 1700L
    ) {
        val ctx = context.applicationContext
        val heroView = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(hero)
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
        logo?.let { bmp ->
            val logoView = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(bmp)
                alpha = 0f
                // 仅缩小悬浮窗上的启动 logo，不动游戏页主体图标。
                scaleX = 0.6f
                scaleY = 0.6f
            }
            container.addView(
                logoView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            logoView.animate()
                .alpha(1f).scaleX(0.6f).scaleY(0.6f)
                .setDuration(320).setStartDelay(420).setInterpolator(fade)
                .start()
        }
        heroView.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(260).setInterpolator(fade).start()
        scrim.animate().alpha(0.62f).setDuration(240).setStartDelay(200).start()

        show(ctx, container, durationMs)
    }

    /** 无美术：纯应用图标 + 应用名字（深色渐变底），悬挂 [durationMs] 后淡出移除。 */
    fun showSimple(
        context: Context,
        icon: android.graphics.Bitmap,
        name: String,
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
            textSize = 22f
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = 16f * density
        }
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val iconSize = (124 * density).roundToInt()
        iconView.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        column.addView(iconView)
        column.addView(
            nameView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (20 * density).roundToInt() }
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

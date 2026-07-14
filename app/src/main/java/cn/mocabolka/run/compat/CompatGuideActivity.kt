package cn.mocabolka.run.compat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.compat.ColorOSCompat
import cn.mocabolka.run.compat.OverlayPermissionHelper
import cn.mocabolka.run.ui.CompatItem
import cn.mocabolka.run.ui.SettingsRepository
import cn.mocabolka.run.ui.components.SubPageRow
import cn.mocabolka.run.ui.components.SubPageScaffold
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.Success
import cn.mocabolka.run.gamepad.SubPageGamepad

/**
 * 权限引导页（部分设备需额外系统权限配置）。
 *
 * 设计原则：
 * - 由 Settings → 管理权限 主动进入（R14 起不再首次启动自动弹出），也可由其它页面需要权限时拉起。
 * - 沉浸式：独占全屏（隐藏系统状态栏），使用 LandscapeTheme 统一主题（深色/AMOLED/Monet）。
 * - UI 与 About / Licenses 彻底统一：复用 [SubPageScaffold] 框架
 *   （统一 TopBar、焦点状态机、右摇杆滚屏、横竖屏响应式、入场动画）。
 * - 完整手柄适配：左摇杆 ↑↓ 切换项、A 触发、B 返回（框架内置）。
 */
class CompatGuideActivity : ComponentActivity() {
    /** 右摇杆滚动引擎。 */
    private lateinit var subPageGamepad: SubPageGamepad
    /** 列表状态提升到 Activity 字段，供右摇杆滚动引擎访问（CompatGuideScreen 内 listState 由此传入）。 */
    private lateinit var listState: LazyListState
    /** 右摇杆滚动后引擎回写的首个可视项索引（焦点跟随桥梁）。 */
    private val rightStickFirstVisible = kotlinx.coroutines.flow.MutableStateFlow(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listState = LazyListState()
        subPageGamepad = SubPageGamepad(
            lifecycleScope,
            getState = { listState },
            onFocusSync = { rightStickFirstVisible.value = it }
        )
        subPageGamepad.start()
        // 沉浸式：隐藏状态栏，全屏显示权限引导（跟随 Splash 沉浸风格）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // 预测返回手势（minSdk=36 恒生效）：注册回调，用户从屏幕左/右边缘向内滑动时系统
        // 绘制预测动画，松手后此处执行关闭，兼容向导支持系统级返回退出。
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        val settings = SettingsRepository(this)
        setContent {
            // 跟随设置-主题切换（深色/浅色/AMOLED），使用 LandscapeTheme 统一主题
            LandscapeTheme(
                darkTheme = when (settings.darkMode) {
                    cn.mocabolka.run.ui.DarkMode.LIGHT -> false
                    cn.mocabolka.run.ui.DarkMode.AMOLED -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
                useMonet = settings.useMonet,
                isAmoled = settings.darkMode == cn.mocabolka.run.ui.DarkMode.AMOLED
            ) {
                val rsFocus = rightStickFirstVisible.collectAsState()
                CompatGuideScreen(
                    context = this,
                    settings = SettingsRepository(this),
                    onClose = { finish() },
                    listState = listState,
                    rightStickFirstVisible = rsFocus
                )
            }
        }
        // 同步应用级方向：直接按当前设置固定自身方向（覆盖全部 OrientationMode）。
        cn.mocabolka.run.compat.OrientationManager.applyAppOrientation(this, SettingsRepository(this))
    }

    /** 右摇杆滚动：MotionEvent 轴委托给引擎；其余交系统。 */
    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        return if (subPageGamepad.dispatchMotionEvent(event)) true else super.onGenericMotionEvent(event)
    }

    override fun onDestroy() {
        subPageGamepad.stop()
        super.onDestroy()
    }
}

@Composable
private fun CompatGuideScreen(
    context: Context,
    settings: SettingsRepository,
    onClose: () -> Unit,
    listState: LazyListState,
    rightStickFirstVisible: State<Int>? = null
) {
    val owner = LocalLifecycleOwner.current
    var refresh by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val isColorOS = ColorOSCompat.isColorOS()
    // listState 由 Activity 提升到字段，供右摇杆滚动引擎驱动（与 About / Licenses 一致）。

    // 与 About / Licenses 完全一致的入场动画由根 AnimatedVisibility 提供（见下方）。
    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = cn.mocabolka.run.ui.theme.MotionSpec.Medium
        ) + androidx.compose.animation.slideInHorizontally(
            animationSpec = cn.mocabolka.run.ui.theme.MotionSpec.SlideOffset,
            initialOffsetX = { it / 6 }
        )
    ) {
        key(refresh) {
            val battery = BatteryOptimizationHelper.isIgnoring(context)
            val usage = UsageStatsPermissionHelper.isGranted(context)
            val overlay = OverlayPermissionHelper.canDraw(context)
            val notification = NotificationBadgeService.isEnabled(context)
            val autostart = false

            val items = listOf(
                CompatItemData(CompatItem.BATTERY, "电池优化白名单", battery,
                    {
                        BatteryOptimizationHelper.requestIgnore(context)
                        settings.setCompatDone(CompatItem.BATTERY, true)
                    },
                    "允许后台运行"),
                CompatItemData(CompatItem.AUTOSTART, "自启动 / 后台保活", autostart,
                    {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        settings.setCompatDone(CompatItem.AUTOSTART, true)
                    },
                    if (isColorOS) "本应用设置页" else "系统设置手动开启"),
                CompatItemData(CompatItem.OVERLAY, "悬浮窗权限", overlay,
                    {
                        if (OverlayPermissionHelper.request(context)) {
                            settings.setCompatDone(CompatItem.OVERLAY, true)
                        }
                    },
                    "强制横屏系统级锁定所需"),
                CompatItemData(CompatItem.USAGE, "使用情况访问", usage,
                    {
                        UsageStatsPermissionHelper.openSettings(context)
                        settings.setCompatDone(CompatItem.USAGE, true)
                    },
                    "最近使用列表所需"),
                CompatItemData(CompatItem.NOTIFICATION, "通知监听（角标）", notification,
                    {
                        ColorOSCompat.openNotificationListenerSettings(context)
                        settings.setCompatDone(CompatItem.NOTIFICATION, true)
                    },
                    "系统设置开启")
            )
            items.forEach { if (it.granted) settings.setCompatDone(it.key, true) }
            val doneCount = items.count { it.granted || settings.isCompatDone(it.key) }
            val total = items.size

            // 顶部进度条 + 说明（静态区块）
            val banner = SubPageRow.Static(key = "banner") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                ) {
                    Text(
                        "配置进度",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$doneCount / $total",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { doneCount.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                if (!isColorOS) {
                    Spacer(Modifier.height(Dimens.xs))
                    Text(
                        "提示：部分「去设置」按钮可能跳转到通用系统设置页。" +
                                "如找不到对应选项，请在系统「设置 → 应用 → 权限」中手动开启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.height(Dimens.sm))
            }

            val rows = buildList<SubPageRow> {
                add(banner)
                items.forEach { it2 ->
                    val done = it2.granted || settings.isCompatDone(it2.key)
                    add(
                        SubPageRow.Action(
                            key = "compat_${it2.key}",
                            label = it2.title,
                            desc = it2.desc,
                            trailing = {
                                CompatBadge(granted = it2.granted, done = done)
                            },
                            onClick = it2.onClick
                        )
                    )
                }
            }

            SubPageScaffold(
                title = "权限",
                onBack = onClose,
                // 底部固定"完成"主按钮（钉在列表下方，不随列表滚动；手柄 ↑↓ 可达、A 触发）
                bottomAction = SubPageRow.Action(
                    key = "compat_complete",
                    label = "完成",
                    primary = true,
                    onClick = onClose
                ),
                rows = rows,
                listState = listState,
                rightStickFirstVisible = rightStickFirstVisible
            )
        }
    }
}

/** 权限项右侧状态徽标：已开启（绿）/ 去设置（主题色）。 */
@Composable
private fun CompatBadge(granted: Boolean, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (done && !granted) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已完成",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (granted) Success.copy(alpha = 0.20f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
                .padding(horizontal = Dimens.sm, vertical = Dimens.xxs)
        ) {
            Text(
                text = if (granted) "已开启" else "去设置",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) Success else MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class CompatItemData(
    val key: String,
    val title: String,
    val granted: Boolean,
    val onClick: () -> Unit,
    val desc: String
)

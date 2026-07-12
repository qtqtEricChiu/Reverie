package cn.mocabolka.run.compat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.mocabolka.run.compat.ColorOSCompat
import cn.mocabolka.run.ui.CompatItem
import cn.mocabolka.run.ui.SettingsRepository
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.Success

/**
 * 权限引导页（部分设备需额外系统权限配置）。
 *
 * 设计原则：
 * - 首次启动由 MainActivity.maybeShowCompatGuide() 在 setContent 之前直接拉起，
 *   不先经过 HomeScreen，避免闪烁。
 * - 本页独占：标题改为 "权限"、隐藏系统状态栏、纯本页排版（响应式 720dp 分界）。
 * - 完整手柄适配：方向键 ↑↓ 切换项、A 触发、B 返回；focusedRow 状态机驱动。
 * - 不再使用浮动手柄气泡（已迁移到上下文角标方案，删除遗留实现）。
 */
class CompatGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                isAmoled = settings.darkMode == cn.mocabolka.run.ui.DarkMode.AMOLED
            ) {
                CompatGuideScreen(
                    context = this,
                    settings = SettingsRepository(this),
                    onClose = { finish() }
                )
            }
        }
    }
}

/**
 * 焦点行索引约定：
 *  - 0 = 顶部"返回"按钮
 *  - 1..N = 各权限项（与 items 顺序一致）
 *  - N+1 = 底部"完成"按钮
 */
private const val BACK_ROW = 0
private const val COMPLETE_ROW_BASE = 1 // 占位，会在 Composable 内根据 items 数量推导

@Composable
private fun CompatGuideScreen(
    context: Context,
    settings: SettingsRepository,
    onClose: () -> Unit
) {
    val owner = LocalLifecycleOwner.current
    var refresh by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val isColorOS = ColorOSCompat.isColorOS()
    // 当前手柄焦点行索引；首次进入默认聚焦第一项权限（A 即进入对应选项，
    // 退出统一由 B 键或聚焦到"完成"按钮触发，避免误触返回）。
    var focusedRow by remember { mutableStateOf(1) }
    val listState = rememberLazyListState()
    val view = LocalView.current
    // 每项 + 完成按钮 + 返回按钮各持有一个 FocusRequester，确保焦点与 focusedRow 严格同步
    val backFocusRequester = remember { FocusRequester() }
    val completeFocusRequester = remember { FocusRequester() }
    val rowFocusRequesters = remember { mutableListOf<FocusRequester>() }

    // 全页入场动画：从右侧滑入 + 渐入，与设置页子页面风格统一
    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = cn.mocabolka.run.ui.theme.MotionSpec.Medium
        ) + androidx.compose.animation.slideInHorizontally(
            animationSpec = cn.mocabolka.run.ui.theme.MotionSpec.SlideOffset,
            initialOffsetX = { it / 6 }
        )
    ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        key(refresh) {
            val battery = BatteryOptimizationHelper.isIgnoring(context)
            val usage = UsageStatsPermissionHelper.isGranted(context)
            val overlay = OverlayPermissionHelper.canDraw(context)
            val notification = NotificationBadgeService.isEnabled(context)
            val autostart = false

            val items = listOf(
                CompatItemData(CompatItem.BATTERY, "电池优化白名单", battery,
                    { BatteryOptimizationHelper.requestIgnore(context); settings.setCompatDone(CompatItem.BATTERY, true) },
                    "允许后台运行"),
                CompatItemData(CompatItem.AUTOSTART, "自启动 / 后台保活", autostart,
                    {
                        // 任务 32：跳转本应用的应用管理页（设置 → 应用 → Reverie），
                        // 用户可在此开启自启动 / 后台电池优化白名单等。
                        runCatching {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        settings.setCompatDone(CompatItem.AUTOSTART, true)
                    },
                    if (isColorOS) "本应用设置页" else "系统设置手动开启"),
                CompatItemData(CompatItem.OVERLAY, "悬浮窗权限", overlay,
                    { if (OverlayPermissionHelper.request(context)) {
                          settings.setCompatDone(CompatItem.OVERLAY, true)
                      }
                      // 无法跳转时静默失败：退出兼容引导后主流程会按需引导悬浮窗授权，
                      // 不再用 Toast 打断（任务：移除离开应用时的 Toast 提示）。
                    },
                    "强制横屏、浮动 UI 所需"),
                CompatItemData(CompatItem.USAGE, "使用情况访问", usage,
                    { UsageStatsPermissionHelper.openSettings(context); settings.setCompatDone(CompatItem.USAGE, true) },
                    "最近使用列表所需"),
                CompatItemData(CompatItem.NOTIFICATION, "通知监听（角标）", notification,
                    { ColorOSCompat.openNotificationListenerSettings(context); settings.setCompatDone(CompatItem.NOTIFICATION, true) },
                    "系统设置开启")
            )
            items.forEach { if (it.granted) settings.setCompatDone(it.key, true) }
            val doneCount = items.count { it.granted || settings.isCompatDone(it.key) }
            val total = items.size
            val completeRowIdx = total + 1 // 0=返回, 1..total=items, total+1=完成

            // 焦点约束在合法范围内（items 数量变更后）
            LaunchedEffect(total) {
                focusedRow = focusedRow.coerceIn(0, completeRowIdx)
            }

            // 同步物理焦点到 focusedRow 对应的元素（方向键移动 focusedRow 后请求焦点，
            // 再由 onFocusChanged 反向确认，避免「显示焦点与逻辑焦点不匹配」导致 A 键误触发返回）。
            LaunchedEffect(focusedRow) {
                val req = when (focusedRow) {
                    BACK_ROW -> backFocusRequester
                    completeRowIdx -> completeFocusRequester
                    in 1..total -> rowFocusRequesters.getOrNull(focusedRow - 1) ?: return@LaunchedEffect
                    else -> return@LaunchedEffect
                }
                runCatching { req.requestFocus() }
            }


            // 列表滚动跟随焦点：普通项滚动到对应索引，完成按钮滚动到列表末尾
            LaunchedEffect(focusedRow) {
                if (focusedRow == completeRowIdx) {
                    // 完成按钮在 LazyColumn 之后，滚动到底部确保其可见
                    listState.animateScrollToItem(items.lastIndex)
                } else {
                    val listIdx = focusedRow - 1
                    if (listIdx in items.indices) listState.animateScrollToItem(listIdx)
                }
            }

            // 手柄按键：方向键移焦点 / A 触发 / B 返回
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        when (ev.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                focusedRow = (focusedRow - 1).coerceAtLeast(0)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                focusedRow = (focusedRow + 1).coerceAtMost(completeRowIdx)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                triggerRow(focusedRow, items, onClose)
                                true
                            }
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> { onClose(); true }
                            else -> false
                        }
                    }
            ) {
                // 响应式排版：≥720dp 居中限定最大宽度 880dp，中屏全宽
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val wide = maxWidth >= 720.dp
                    val contentWidth = if (wide) 880.dp else maxWidth
                    val horizontalPad = if (wide) Dimens.xl else Dimens.md

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = contentWidth)
                            .fillMaxHeight()
                            .padding(horizontal = horizontalPad, vertical = Dimens.md)
                    ) {
                        // 标题栏：简洁返回图标 + 标题
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompatIconButton(
                                onClick = onClose,
                                isFocused = focusedRow == BACK_ROW,
                                onFocused = { focusedRow = BACK_ROW },
                                modifier = Modifier.focusRequester(backFocusRequester)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(Dimens.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "权限",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                    if (isColorOS) "当前设备兼容性配置"
                    else "部分设置项可能因设备差异无法自动跳转",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(Modifier.height(Dimens.sm))

                        // 进度
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

                        // 列表
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = Dimens.xxs),
                            verticalArrangement = Arrangement.spacedBy(Dimens.xs)
                        ) {
                            items(items, key = { it.key }) { item ->
                                val listIdx = items.indexOf(item) + 1
                                val done = item.granted || settings.isCompatDone(item.key)
                                // 确保每行的 FocusRequester 数量足够
                                while (rowFocusRequesters.size < items.size) {
                                    rowFocusRequesters.add(remember { FocusRequester() })
                                }
                                CompatRow(
                                    title = item.title,
                                    desc = item.desc,
                                    granted = item.granted,
                                    done = done,
                                    isFocused = focusedRow == listIdx,
                                    onFocus = { focusedRow = listIdx },
                                    focusRequester = rowFocusRequesters[listIdx - 1],
                                    onClick = item.onClick
                                )
                            }
                        }

                        Spacer(Modifier.height(Dimens.xs))

                        // 底部"完成"按钮
                        CompleteBar(
                            isFocused = focusedRow == completeRowIdx,
                            onFocus = { focusedRow = completeRowIdx },
                            onClick = onClose,
                            focusRequester = completeFocusRequester
                        )
                    }
                }
            }
        }
    }
}
}

/** 手柄 A 键在当前焦点行触发的动作。 */
private fun triggerRow(
    row: Int,
    items: List<CompatItemData>,
    onClose: () -> Unit
) {
    when (row) {
        BACK_ROW -> onClose()
        in 1..items.size -> items[row - 1].onClick()
        items.size + 1 -> onClose()
    }
}

/** 简洁图标按钮：聚焦时显示主题色边框。 */
@Composable
private fun CompatIconButton(
    onClick: () -> Unit,
    isFocused: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() },
        contentAlignment = Alignment.Center
    ) { content() }
}

private data class CompatItemData(
    val key: String,
    val title: String,
    val granted: Boolean,
    val onClick: () -> Unit,
    val desc: String
)

/**
 * 兼容项单行：手柄 + 鼠标双模可点。
 * - 焦点态：主题色边框 + 背景提升
 * - 鼠标/触屏：整行可点击
 * - 手柄：focusable() + 配合外层 onFocus 回调
 */
@Composable
private fun CompatRow(
    title: String,
    desc: String,
    granted: Boolean,
    done: Boolean,
    isFocused: Boolean,
    onFocus: () -> Unit,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.md))
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = if (isFocused)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.md, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态点
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (granted) Success else MaterialTheme.colorScheme.outline)
            )
            Spacer(Modifier.width(Dimens.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(Dimens.xs))
            if (done && !granted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已完成",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            // 右侧状态徽标
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
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
}

/** 底部"完成"按钮：手柄可聚焦 / A 键触发。 */
@Composable
private fun CompleteBar(
    isFocused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(Dimens.md))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusRequester(focusRequester)
            // 直接响应 A / Enter 键：当此按钮获得焦点时按 A 直接关闭
            .onKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onClick()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "完成",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

package cn.mocabolka.run.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.edgeSafePadding
import cn.mocabolka.run.ui.theme.SurfaceTokens

/**
 * 统一 dialog 骨架：全屏遮罩（scrim）+ 居中卡片 + 底部按键指示栏（可选）。
 *
 * 归一化此前 4 份 dialog（DropdownDialog / ConfirmDialog / InfoDialog / NativeDatePickerDialog）
 * 各自重复的「scrim Box → 居中 Surface → BottomHintBar」三层代码。
 *
 * 默认即渲染统一的居中 MD3 Surface 容器（shape / color / tonalElevation /
 * widthIn / fillMaxWidth / padding 均由可选参数带默认值控制，无需各 dialog 重复书写）。
 * DropdownDialog / ConfirmDialog / UpdateDialog 直接传内容即可：
 * ```
 * DialogScaffold(onDismiss = onDismiss) {
 *     Column(Modifier.padding(Dimens.lg)) { /* dialog 内容 */ }
 * }
 * ```
 *
 * 需要 [BoxWithConstraints] 计算最大宽/高（InfoDialog / StatsScreen 日期·周期选择器）
 * 或自定义容器外观时，传 [unifiedContainer] = false 保留自身 Surface：
 * ```
 * DialogScaffold(onDismiss = onDismiss, showBottomHint = false, unifiedContainer = false) {
 *     BoxWithConstraints(...) { Surface(...) { ... } }
 * }
 * ```
 *
 * 预测性返回手势（Android 13+）：Dialog 打开时从屏幕边缘向内滑动，
 * Dialog 会跟随手势进度做「遮罩淡出 + 卡片缩小」动画；滑过系统阈值即 dismiss，
 * 松手未过阈值则弹回原位。仅消费 Dialog 可见期间的手势，关闭后随组件树移除自动释放，
 * Activity 级预测返回恢复。手柄 B 键 dismiss 路径不变，二者正交。
 *
 * @param onDismiss 点击遮罩（或按 B / 滑回手势确认）关闭的回调
 * @param showBottomHint 是否显示底部手柄提示栏（默认 true）
 * @param bottomHintContent 底部提示栏内容（默认 A 确认 / B 关闭）
 * @param content 卡片内容（在内层居中的 Box 内，可自由使用 BoxScope 布局 / 嵌套 BoxWithConstraints）
 */
@Composable
fun DialogScaffold(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showBottomHint: Boolean = true,
    /** 挖孔屏适配开关：透传宿主页面设置，使底部提示栏与页面其他内容保持一致的左右安全边距。 */
    cutoutAdapt: Boolean = false,
    /** 是否由本组件渲染统一居中 Surface 容器（默认 true）。
     *  false 时跳过内置 Surface，由调用方自行渲染容器（如 BoxWithConstraints 计算尺寸、自定义表面）。 */
    unifiedContainer: Boolean = true,
    // ── 统一容器参数（默认值对齐既有 MD3 弹窗规范，消除散落魔法值）──
    containerShape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    containerElevation: Dp = Dimens.DialogElevation,
    containerMaxWidth: Dp = Dimens.DialogMaxWidth,
    containerWidthFraction: Float = Dimens.DialogWidthFraction,
    containerPadding: Dp = Dimens.md,
    bottomHintContent: @Composable RowScope.() -> Unit = {
        Hint(KeyToken.A, "确认")
        Hint(KeyToken.B, "关闭")
    },
    content: @Composable () -> Unit
) {
    val scrimInteractionSource = remember { MutableInteractionSource() }

    // 预测性返回手势进度：跟随手指实时贴合（snapTo，无缓动拖影），
    // 松手未过阈值则 animateTo(0) 弹回原位。
    val backProgress = remember { Animatable(0f) }

    PredictiveBackHandler { progressFlow ->
        try {
            progressFlow.collect { backProgress.snapTo(it.progress) }
            onDismiss() // 滑过系统阈值，确认返回
        } catch (_: CancellationException) {
            backProgress.animateTo(0f, tween(durationMillis = 150)) // 弹回原位
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceTokens.scrim())
            // 遮罩 + 卡片 + 提示栏整体随手势淡出；graphicsLayer 仅更新图层，不重组 content()
            .graphicsLayer { alpha = 1f - backProgress.value * 0.8f }
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismiss
            )
    ) {
        // 居中容器：卡片跟随手势缩小（仅缩放，alpha 由外层遮罩统一控制，避免双重 alpha）。
        // contentAlignment = Center 让子元素（无显式 .align 时）自动居中，
        // 与同文件 DropdownDialog / ConfirmDialog 的「.align(Center)」等价，
        // 与 InfoDialog / NativeDatePickerDialog 的「BoxWithConstraints(contentAlignment=Center)」一致。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f - backProgress.value * 0.18f
                    scaleX = s
                    scaleY = s
                }
                .padding(Dimens.md),
            contentAlignment = Alignment.Center
        ) {
            if (unifiedContainer) {
                // 统一居中 Surface 容器：点击不冒泡（onClick = {} 阻断冒泡到遮罩 dismiss）
                Surface(
                    shape = containerShape,
                    color = containerColor,
                    tonalElevation = containerElevation,
                    modifier = Modifier
                        .widthIn(max = containerMaxWidth)
                        .fillMaxWidth(containerWidthFraction)
                        .padding(containerPadding)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { }
                        )
                ) {
                    content()
                }
            } else {
                content()
            }
        }
        // 底部按键指示栏：固定在屏幕底部，不参与居中布局。
        if (showBottomHint) {
            GamepadBottomHintBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .edgeSafePadding(cutoutAdapt)
            ) {
                bottomHintContent()
            }
        }
    }
}

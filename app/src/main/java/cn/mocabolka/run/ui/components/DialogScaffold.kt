package cn.mocabolka.run.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.SurfaceTokens

/**
 * 统一 dialog 骨架：全屏遮罩（scrim）+ 居中卡片 + 底部按键指示栏（可选）。
 *
 * 归一化此前 4 份 dialog（DropdownDialog / ConfirmDialog / InfoDialog / NativeDatePickerDialog）
 * 各自重复的「scrim Box → 居中 Surface → BottomHintBar」三层代码。
 *
 * 典型用法（DropdownDialog / ConfirmDialog）：
 * ```
 * DialogScaffold(onDismiss = onDismiss) {
 *     Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
 *             tonalElevation = 6.dp,
 *             modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(0.92f).padding(Dimens.md)) {
 *         // dialog 内容
 *     }
 * }
 * ```
 *
 * 需要 [BoxWithConstraints] 计算最大宽/高时（InfoDialog / NativeDatePickerDialog）：
 * ```
 * DialogScaffold(onDismiss = onDismiss, showBottomHint = false) {
 *     BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
 *         Surface(...) { ... }
 *     }
 * }
 * ```
 *
 * @param onDismiss 点击遮罩（或按 B）关闭的回调
 * @param showBottomHint 是否显示底部手柄提示栏（默认 true）
 * @param bottomHintContent 底部提示栏内容（默认 A 确认 / B 关闭）
 * @param content 卡片内容（在内层居中的 Box 内，可自由使用 BoxScope 布局 / 嵌套 BoxWithConstraints）
 */
@Composable
fun DialogScaffold(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showBottomHint: Boolean = true,
    bottomHintContent: @Composable RowScope.() -> Unit = {
        Hint(KeyToken.A, "确认")
        Hint(KeyToken.B, "关闭")
    },
    content: @Composable BoxScope.() -> Unit
) {
    val scrimInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceTokens.scrim())
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismiss
            )
    ) {
        // 居中容器：提供给卡片居中的 BoxScope 环境。
        // contentAlignment = Center 让子元素（无显式 .align 时）自动居中，
        // 与同文件 DropdownDialog / ConfirmDialog 的「.align(Center)」等价，
        // 与 InfoDialog / NativeDatePickerDialog 的「BoxWithConstraints(contentAlignment=Center)」一致。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.md),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
        // 底部按键指示栏：固定在屏幕底部，不参与居中布局。
        if (showBottomHint) {
            GamepadBottomHintBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                bottomHintContent()
            }
        }
    }
}

package cn.mocabolka.run.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.ui.theme.Dimens

/**
 * 状态栏安全避让修饰符：在状态栏高度基础上上移 12dp（与子页面标题栏/顶部内容的视觉间距），
 * 并 clamp 到非负。外层 Column 已统一处理状态栏避让，此修饰符用于个别仍需显式内缩的顶部元素，
 * 避免在各页面手写 `padding(top = (statusBarTop - 12.dp).coerceAtLeast(0.dp)` 的重复与漂移。
 */
fun Modifier.statusBarSafeTop(): Modifier = composed {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val offset = top - 12.dp
    this.padding(top = if (offset < 0.dp) 0.dp else offset)
}

/**
 * 装饰性描边（accent tonal outline），独立于焦点系统——
 * 随触控/手柄状态常显，不会因手柄拔出而消失（区别于 focusBorder）。
 * 仅封装「细线 accent 描边」这一重复模式，颜色由调用方按语义计算。
 */
@Composable
fun Modifier.accentOutline(
    color: Color,
    shape: Shape = MaterialTheme.shapes.medium,
    width: Dp = Dimens.FocusBorderWidth
): Modifier = this.border(width, color, shape)

package cn.mocabolka.run.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp

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

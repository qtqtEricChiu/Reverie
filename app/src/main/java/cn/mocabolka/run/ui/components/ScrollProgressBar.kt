package cn.mocabolka.run.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 整体滚动进度滑块（手柄全链路重构核心组件）。
 *
 * 设计铁律（审计重构原则一）：**滑块 = 滚动位置的只读投影，永不接受焦点、永不接受手势**。
 * 手柄焦点系统完全不认识本组件——它没有焦点态，从根上杜绝"聚焦到滑块后按钮点不动"的
 * 焦点陷阱死区。滚动真源（LazyListState / ScrollState）变化时，thumb 位置由 [derivedStateOf]
 * 惰性投影，不产生额外重组风暴。
 *
 * 统计页左栏排行(LazyListState)、右栏总览(ScrollState)、窄屏单栏(LazyListState) 统一复用本组件，
 * 保证"整体滑块"视觉一致；调用方仅需把栏的滚动 state 传入并用 Box 叠放在栏右缘即可。
 */
private val ScrollbarWidth = 3.dp
private val ScrollbarThumbRadius = 2.dp
private const val THUMB_MIN_RATIO = 0.08f

@Composable
private fun scrollbarThumbColor() =
    // 与既有列表滚动条同规：低对比只读指示，不抢视觉。
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

/**
 * LazyColumn 滚动进度投影（左栏排行 / 窄屏单栏）。
 * 内容不足一屏（[itemCount] ≤ 可视项数）时自动隐藏，避免出现无意义满格滑块。
 */
@Composable
fun ScrollProgressBar(
    state: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val layoutInfo = state.layoutInfo
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty() || itemCount <= visible.size) return
    val viewport = visible.size
    val first = state.firstVisibleItemIndex
    val thumbRatio = (viewport.toFloat() / itemCount).coerceIn(THUMB_MIN_RATIO, 1f)
    val maxOffset = (itemCount - viewport).toFloat().coerceAtLeast(1f)
    val offsetRatio = (first.toFloat() / maxOffset).coerceIn(0f, 1f)
    ScrollbarTrack(
        modifier = modifier,
        offsetRatio = offsetRatio,
        thumbRatio = thumbRatio
    )
}

/**
 * Column(verticalScroll) 滚动进度投影（右栏总览）。
 * ScrollState 不暴露视口/内容高度，故 thumb 高度用固定 [thumbFraction] 估算，仅指示位置。
 * 内容未溢出（maxValue ≤ 0）时自动隐藏。
 */
@Composable
fun ScrollProgressBar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    thumbFraction: Float = 0.18f
) {
    if (state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE) return
    val offsetRatio by remember(state) {
        derivedStateOf { (state.value.toFloat() / state.maxValue).coerceIn(0f, 1f) }
    }
    val thumbRatio = thumbFraction.coerceIn(THUMB_MIN_RATIO, 1f)
    // 顶部占位比例：把 [0,1] 的滚动进度映射到 thumb 可移动区间 (1 - thumbRatio)。
    ScrollbarTrack(
        modifier = modifier,
        offsetRatio = offsetRatio * (1f - thumbRatio),
        thumbRatio = thumbRatio
    )
}

/**
 * 滑块轨道渲染（内部）：三段式 顶部留白 / thumb / 底部留白，均相对父高按比例填充。
 * [offsetRatio] 为 thumb 顶端距轨道顶端的占比，[thumbRatio] 为 thumb 自身占比，两者之和 ≤ 1。
 */
@Composable
private fun ScrollbarTrack(
    modifier: Modifier,
    offsetRatio: Float,
    thumbRatio: Float
) {
    val top = offsetRatio.coerceIn(0f, 1f - thumbRatio)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(ScrollbarWidth)
    ) {
        if (top > 0f) Spacer(Modifier.fillMaxHeight(top))
        Box(
            Modifier
                .fillMaxHeight(thumbRatio)
                .background(scrollbarThumbColor(), RoundedCornerShape(ScrollbarThumbRadius))
        )
    }
}

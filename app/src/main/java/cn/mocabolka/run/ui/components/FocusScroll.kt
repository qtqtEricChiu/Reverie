package cn.mocabolka.run.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot

/**
 * 归一化「焦点移动 → 滚动到可见」引擎（需求5）。
 *
 * 适用场景：详情面板等使用普通 `Column` + `verticalScroll(ScrollState)`（非 LazyList）的容器，
 * 焦点（手柄 focus index）移动后，容器不会自动跟随滚动，导致焦点项滚出窗口不可见。
 * 主页主列表 / 搜索列表是 LazyList，由 `animateScrollToItem` 处理；此处补齐普通 ScrollState 的等价能力。
 *
 * 用法：
 * 1. 用 [rememberFocusScroller] 创建实例（记住 scrollState）。
 * 2. 滚动容器根节点加 [FocusScroller.containerPlaced]。
 * 3. 每个可聚焦项加 [FocusScroller.itemPlaced]，传入其焦点 index。
 * 4. 在 `LaunchedEffect(focusedIndex) { scroller.bringIntoView(focusedIndex) }` 中调用。
 *
 * 计算逻辑：读取焦点项与滚动容器在 Root 中的坐标差 + 当前滚动值，得到焦点项相对容器内容顶部的
 * 偏移；若焦点项顶/底超出可视视口，则 `animateScrollTo` 把其滚动到视口顶/底对齐（已可见则不滚动）。
 */
class FocusScroller(
    private val scrollState: ScrollState,
    itemCount: Int
) {
    private val containerCoords = mutableStateOf<LayoutCoordinates?>(null)
    private val itemCoords = mutableStateOf<Array<LayoutCoordinates?>>(Array(itemCount) { null })

    /** 滚动容器根节点调用，记录其坐标。 */
    fun containerPlaced(coords: LayoutCoordinates) {
        containerCoords.value = coords
    }

    /** 第 [index] 个可聚焦项调用，记录其坐标。 */
    fun itemPlaced(index: Int, coords: LayoutCoordinates) {
        if (index in itemCoords.value.indices) itemCoords.value[index] = coords
    }

    /**
     * 将第 [index] 个焦点项滚动到可视区域（若已在可视区则不产生滚动）。
     * 须在 Compose scope 内调用（内部使用 suspend 动画）。
     */
    suspend fun bringIntoView(index: Int) {
        if (index < 0) return
        val item = itemCoords.value.getOrNull(index) ?: return
        val container = containerCoords.value ?: return
        val containerTopInRoot = container.positionInRoot().y
        // 焦点项相对「容器内容顶部」的偏移（含当前滚动值）。
        val relTop = (item.positionInRoot().y - containerTopInRoot + scrollState.value)
        val itemH = item.size.height.toFloat()
        val viewport = scrollState.viewportSize.toFloat()
        val visibleTop = scrollState.value.toFloat()
        val visibleBottom = visibleTop + viewport
        val itemBottom = relTop + itemH
        // 已在视口内（含一点冗余缓冲）则不滚动。
        val margin = 4f
        val target = when {
            relTop < visibleTop + margin -> relTop
            itemBottom > visibleBottom - margin -> (itemBottom - viewport)
            else -> return
        }.toInt().coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target)
    }
}

/**
 * 创建并记住一个 [FocusScroller]，绑定到给定 [scrollState]（普通滚动容器）。
 */
@Composable
fun rememberFocusScroller(scrollState: ScrollState, itemCount: Int): FocusScroller {
    return remember(scrollState) { FocusScroller(scrollState, itemCount) }
}

/** 便捷 Modifier：滚动容器根节点挂载，记录坐标。 */
fun Modifier.focusScrollerContainer(scroller: FocusScroller): Modifier =
    this.onPlaced { scroller.containerPlaced(it) }

/** 便捷 Modifier：第 [index] 个可聚焦项挂载，记录坐标。 */
fun Modifier.focusScrollerItem(scroller: FocusScroller, index: Int): Modifier =
    this.onPlaced { scroller.itemPlaced(index, it) }

package cn.mocabolka.run.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * ON_RESUME 时自增的刷新计数（D-NEW2 归一化落点）。
 * 消除 3 个子页 Composable 中 `lifecycle.addObserver { ON_RESUME -> refresh++ }` 的重复样板。
 * 返回 [MutableState]<Int>（以 `by` 委托即可像原 `var refresh by remember { mutableStateOf(0) }` 一样使用），
 * 值每次 onResume 自增 1，调用方据此触发入场动画重播等重组 / 数据重载。
 * 与原实现行为完全等价：同一 LocalLifecycleOwner + 同一 ON_RESUME 判定 + 同等的 add/removeObserver 清理。
 */
@Composable
fun rememberRefreshOnResume(): MutableState<Int> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refresh = remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh.intValue++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return refresh
}

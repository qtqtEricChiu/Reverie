package cn.mocabolka.run.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.launcher.relativeLastUsed
import cn.mocabolka.run.ui.theme.Dimens

@Composable
fun RecentsRow(
    apps: List<AppModel>,
    favorites: Set<String>,
    badges: Map<String, Int>,
    onFocus: (String) -> Unit,
    onLaunch: (AppModel) -> Unit,
    onRequestPermission: () -> Unit,
    /** "最近游玩"标题点击：跳转到统计页（第二栏），而非任何设置页。 */
    onHeaderClick: () -> Unit = {},
    /** 当前手柄焦点包名，用于高亮；数据驱动焦点（见 HomeViewModel.moveFocus）。 */
    focusedPackage: String? = null,
    /** 减少动态效果：冻结 AppTile 缩放动画（无障碍）。 */
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding)
    ) {
        // 标题可点击：进入统计页（第二栏），明确不跳应用/系统设置页
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onHeaderClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "最近游玩",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "查看统计",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (apps.isEmpty()) {
            Column(
                modifier = Modifier.padding(top = Dimens.xs),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                Text(
                    text = "未授权使用情况访问，无法显示最近应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Button(onClick = onRequestPermission) {
                    Text("前往系统设置授权")
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(top = Dimens.xs),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                apps.take(8).forEach { app ->
                    AppTile(
                        app = app,
                        onFocus = onFocus,
                        onLaunch = onLaunch,
                        isFavorite = favorites.contains(app.packageName),
                        badgeCount = badges[app.packageName] ?: 0,
                        isFocused = app.packageName == focusedPackage,
                        subtitle = relativeLastUsed(app.lastUsedTime) ?: "",
                        reduceMotion = reduceMotion
                    )
                }
            }
        }
    }
}

package cn.mocabolka.run.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        Text(
            text = "最近游玩",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
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

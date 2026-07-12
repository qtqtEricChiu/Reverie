package cn.mocabolka.run.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.ui.theme.Dimens

@Composable
fun Dock(
    favorites: List<AppModel>,
    onFocus: (String) -> Unit,
    onLaunch: (AppModel) -> Unit,
    /** 各应用未读通知数（角标），key 为包名。 */
    badges: Map<String, Int> = emptyMap(),
    /** 当前手柄焦点包名，用于高亮；数据驱动焦点（见 HomeViewModel.moveFocus）。 */
    focusedPackage: String? = null,
    /** 减少动态效果：冻结 AppTile 缩放动画（无障碍）。 */
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = Dimens.lg, topEnd = Dimens.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.md)
    ) {
        Text(
            text = "收藏",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (favorites.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = " 选中应用后按 X 收藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                favorites.forEach { app ->
                    AppTile(
                        app = app,
                        onFocus = onFocus,
                        onLaunch = onLaunch,
                        size = Dimens.DockTileSize,
                        isFavorite = true,
                        isFocused = app.packageName == focusedPackage,
                        badgeCount = badges[app.packageName] ?: 0,
                        reduceMotion = reduceMotion
                    )
                }
            }
        }
    }
}

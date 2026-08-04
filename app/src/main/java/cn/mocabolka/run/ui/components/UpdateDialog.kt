package cn.mocabolka.run.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.BuildConfig
import cn.mocabolka.run.launcher.RemoteVersion
import cn.mocabolka.run.ui.theme.Dimens

/**
 * 应用内更新提示弹窗（2026-07-30）：检测到远端新版本时弹出。
 * 排版逐项照抄 [ConfirmDialog]（全屏遮罩 + 居中 MD3 Surface + 图标标题行 + 右对齐按钮行），
 * 仅图标换为系统更新图标（primary 色，非危险语义）。
 *
 * 手柄键位（由 HomeScreen 事件循环按 DialogLayer.UPDATE 路由，触控按钮不内嵌键位文本）：
 *  - A = 立即更新（打开下载页）；
 *  - B = 稍后（本次关闭，下个检查周期再提示）；
 *  - X = 跳过此版本（同版本不再提示）。
 */
@Composable
fun UpdateDialog(
    remoteVersion: RemoteVersion,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    /** 挖孔屏适配开关：透传 DialogScaffold 底部提示栏安全边距。 */
    cutoutAdapt: Boolean = false
) {
    DialogScaffold(
        onDismiss = onDismiss,
        cutoutAdapt = cutoutAdapt,
        bottomHintContent = {
            Hint(KeyToken.A, "立即更新")
            Hint(KeyToken.X, "跳过此版本")
            Hint(KeyToken.B, "稍后")
        }
    ) {
        Column(
                modifier = Modifier.padding(Dimens.lg),
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                // 更新图标 + 标题行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.SystemUpdate, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    Text("发现新版本 ${remoteVersion.version}", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    "当前版本：${BuildConfig.VERSION_NAME}（构建号 ${BuildConfig.VERSION_CODE}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (remoteVersion.changelog.isNotBlank()) {
                    Text(remoteVersion.changelog, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 10)
                }
                // 按钮行（触控可点，不内嵌键位文本；键位指示统一走底部 Hint 浮块）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReverieTextButton(onClick = onSkip, text = "跳过此版本")
                    Spacer(Modifier.width(Dimens.xs))
                    ReverieTextButton(onClick = onDismiss, text = "稍后")
                    Spacer(Modifier.width(Dimens.xs))
                    ReverieTextButton(onClick = onDownload, text = "立即更新")
                }
            }
    }  // close DialogScaffold
}

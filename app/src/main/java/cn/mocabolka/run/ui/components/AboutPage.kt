package cn.mocabolka.run.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.R
import cn.mocabolka.run.gamepad.Direction
import kotlinx.coroutines.flow.Flow

/**
 * 关于页面（全屏子页面）。
 *
 * 完全复用 [SubPageScaffold] 框架，继承根 LandscapeTheme 的 darkMode / useMonet /
 * AMOLED 取色——不再自建 ColorScheme（此前自建导致深色/AMOLED 割裂）。
 * 入场出场动画、左摇杆焦点、右摇杆滚屏、横竖屏适配均由框架统一处理。
 */
@Composable
fun AboutPage(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    appVersion: String,
    listState: LazyListState = rememberLazyListState(),
    /** 右摇杆滚动后引擎回写的首个可视项索引（焦点跟随，见 SubPageScaffold）。 */
    rightStickFirstVisible: androidx.compose.runtime.State<Int>? = null,
    /** 左摇杆 / Hat 导航流（焦点跟随，见 SubPageScaffold）。 */
    navigate: Flow<Direction>? = null,
    /** 减少动态效果（晕动症）：由宿主 Activity 透传，控制逐行入场动画。 */
    reduceMotion: Boolean = false
) {
    val context = LocalContext.current
    val versionText = if (appVersion.isNotBlank()) "v$appVersion" else ""

    val rows = listOf<SubPageRow>(
        // 图标 + 名称（静态展示）
        SubPageRow.Static(key = "brand") {
            Spacer(Modifier.height(24.dp))
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "Reverie 图标",
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialTheme.shapes.large)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Reverie",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                versionText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        },
        // 项目 GitHub 仓库（置顶于 icon 正下方）
        SubPageRow.Link(
            key = "github",
            label = "GitHub",
            desc = "项目开源代码仓库",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qtqtEricChiu/Reverie"))
                    )
                }
            }
        ),
        SubPageRow.Static(key = "div1") {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        },
        SubPageRow.Static(key = "thanks_title") {
            SectionTitle("Special Thanks")
        },
        // Tai 外部链接
        SubPageRow.Link(
            key = "tai",
            label = "Tai",
            desc = "软件使用时长统计工具 · MIT License",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Planshit/Tai"))
                    )
                }
            }
        ),
        // SteamDB 外部链接（游戏元数据 / 美术资源来源）
        SubPageRow.Link(
            key = "steamdb",
            label = "SteamDB",
            desc = "游戏元数据与美术资源来源 · steamdb.info",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://steamdb.info/"))
                    )
                }
            }
        ),
        SubPageRow.Static(key = "div2") {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        },
        SubPageRow.Static(key = "copyright_title") {
            SectionTitle("版权信息")
        },
        SubPageRow.Static(key = "copyright") {
            Text(
                "© MocaBolka 2026",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "co-created with CodeBuddy",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        },
        SubPageRow.Static(key = "div3") {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        },
        SubPageRow.Static(key = "legal_title") {
            SectionTitle("法律信息")
        },
        // 开放源代码许可入口
        SubPageRow.Action(
            key = "licenses",
            label = "开放源代码许可",
            desc = "查看本应用使用的开源软件许可",
            icon = Icons.Filled.Code,
            onClick = onOpenLicenses
        ),
        SubPageRow.Static(key = "bottom") {
            Spacer(Modifier.height(24.dp))
        }
    )

    SubPageScaffold(
        title = "关于",
        onBack = onBack,
        rows = rows,
        listState = listState,
            rightStickFirstVisible = rightStickFirstVisible,
            navigate = navigate,
            reduceMotion = reduceMotion
        )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

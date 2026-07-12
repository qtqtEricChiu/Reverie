package cn.mocabolka.run.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.R
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.MotionSpec

/**
 * 关于页面（全屏子页面）
 *
 * MD3 规范实现：
 * - 顶部 SubPageTopBar（Surface + ArrowBack + titleLarge）
 * - LazyColumn 居中内容（应用图标、名称版本、致谢、版权、开源许可入口）
 * - 完整手柄适配：每项 ClickableRow 可聚焦 + 焦点态 primaryContainer 高亮 + A 键打开链接
 * - 右摇杆滚动由 HomeScreen 的 subPageListState 驱动
 * - 所有颜色使用 MaterialTheme.colorScheme，跟随用户主题偏好
 */
@Composable
fun AboutPage(
    appVersion: String,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    // 全页使用 background 色（跟随主题），子页面由 HomeScreen AnimatedVisibility 统一入场动画
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // MD3 Surface 标题栏（与 LicensesPage 共享 SubPageTopBar）
        SubPageTopBar(title = "关于", onBack = onBack)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 应用图标 & 名称
            item {
                Spacer(Modifier.height(48.dp))
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "Reverie 图标",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.large)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Reverie",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (appVersion.isNotBlank()) "v$appVersion" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
            }

            // 分割线
            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = Dimens.xs)
                )
            }

            // Special Thanks 区域
            item {
                SectionTitle("Special Thanks")
                Spacer(Modifier.height(Dimens.xs))
            }

            // Tai 项目
            item {
                ClickableRow(
                    label = "Tai",
                    desc = "软件使用时长统计工具 · MIT License",
                    isExternal = true,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Planshit/Tai"))
                        context.startActivity(intent)
                    }
                )
            }

            // 分割线
            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = Dimens.xs)
                )
            }

            // 版权信息
            item {
                SectionTitle("版权信息")
                Spacer(Modifier.height(Dimens.xs))
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
                Spacer(Modifier.height(24.dp))
            }

            // 分割线
            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = Dimens.xs)
                )
            }

            // 开放源代码许可入口
            item {
                SectionTitle("法律信息")
                Spacer(Modifier.height(Dimens.xs))
                ClickableRow(
                    label = "开放源代码许可",
                    desc = "查看本应用使用的开源软件许可",
                    icon = Icons.Filled.Code,
                    isExternal = false,
                    onClick = onOpenLicenses
                )
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

/**
 * 统一全屏子页面标题栏（与 AboutPage / LicensesPage 共享）。
 * MD3 Surface + titleLarge + AutoMirrored ArrowBack + 返回键位提示。
 */
@Composable
fun SubPageTopBar(title: String, onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.xs, vertical = Dimens.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // 手柄提示：B 返回
            Text(
                "B 返回",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(end = Dimens.sm)
            )
        }
    }
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

/**
 * MD3 Surface 可点击行：支持手柄焦点 + 外部链接图标。
 * 聚焦时 primaryContainer 高亮 + 轻微 elevation 提升。
 */
@Composable
private fun ClickableRow(
    label: String,
    desc: String,
    icon: ImageVector? = null,
    isExternal: Boolean = true,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val focusElevation by animateFloatAsState(
        targetValue = if (isFocused) 4f else 1f,
        animationSpec = MotionSpec.FocusSpring,
        label = "clickableElev"
    )

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
        tonalElevation = focusElevation.dp,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(Dimens.sm))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 外部链接显示 OpenInNew 图标，内部导航不显示
            if (isExternal) {
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = "打开外部链接",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

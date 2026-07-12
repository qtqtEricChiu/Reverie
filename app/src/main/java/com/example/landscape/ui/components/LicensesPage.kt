package cn.mocabolka.run.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.MotionSpec

/**
 * 开放源代码许可页面（全屏子页面）
 *
 * MD3 规范实现：
 * - 顶部 SubPageTopBar（Surface + ArrowBack + titleLarge）
 * - LazyColumn 展示所有开源组件信息
 * - 每个 LicenseCard 可手柄聚焦 + 焦点态 primaryContainer 高亮
 * - 右摇杆滚动由 HomeScreen 的 subPageListState 驱动
 * - 所有颜色使用 MaterialTheme.colorScheme，跟随用户主题偏好
 */
@Composable
fun LicensesPage(
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    // 全页使用 background 色（跟随主题）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // MD3 Surface 标题栏（与 AboutPage 共享 SubPageTopBar）
        SubPageTopBar(title = "开放源代码许可", onBack = onBack)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.md)
        ) {
            item {
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "本应用使用了以下开源软件组件，并对其创作者表示感谢。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Dimens.md))
            }

            itemsIndexed(licenses) { index, license ->
                LicenseCard(license)
                if (index < licenses.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun LicenseCard(license: OssLicense) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val focusElevation by animateFloatAsState(
        targetValue = if (isFocused) 4f else 1f,
        animationSpec = MotionSpec.FocusSpring,
        label = "licElev"
    )

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
        tonalElevation = focusElevation.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.md)
        ) {
            Text(
                license.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                license.copyright,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                license.license,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (license.notice.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    license.notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** 开源软件组件信息。 */
data class OssLicense(
    val name: String,
    val copyright: String,
    val license: String,
    val notice: String = ""
)

/**
 * 开源软件组件清单。
 * 数据来源：Gradle 依赖解析 + 各依赖的 LICENSE/NOTICE 文件。
 * 涵盖 compile/runtime 依赖及其传递依赖的主要许可。
 */
private val licenses = listOf(
    OssLicense(
        name = "Kotlin",
        copyright = "Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.",
        license = "Apache License 2.0"
    ),
    OssLicense(
        name = "Android Jetpack (AndroidX)",
        copyright = "Copyright 2025 The Android Open Source Project",
        license = "Apache License 2.0"
    ),
    OssLicense(
        name = "Compose Multiplatform (Jetpack Compose)",
        copyright = "Copyright 2025 The Android Open Source Project",
        license = "Apache License 2.0"
    ),
    OssLicense(
        name = "Material Components for Android (Material 3)",
        copyright = "Copyright 2025 The Android Open Source Project",
        license = "Apache License 2.0"
    ),
    OssLicense(
        name = "Kotlin Coroutines",
        copyright = "Copyright 2016-2025 Jetbrains s.r.o.",
        license = "Apache License 2.0"
    ),
    OssLicense(
        name = "Gradle",
        copyright = "Copyright 2010-2025 Gradle, Inc.",
        license = "Apache License 2.0"
    ),
    OssLicense(
        name = "Android Gradle Plugin (AGP)",
        copyright = "Copyright 2025 The Android Open Source Project",
        license = "Apache License 2.0"
    ),
    OssLicense(
        name = "Tai",
        copyright = "Copyright (c) 2022 Plan shit",
        license = "MIT License",
        notice = "受 Tai 项目启发，其软件使用时长统计设计思想为本应用的统计功能提供了参考。"
    )
)

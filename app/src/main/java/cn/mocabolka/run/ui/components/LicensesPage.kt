package cn.mocabolka.run.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.ui.theme.Dimens

/**
 * 开放源代码许可页面（全屏子页面）。
 *
 * 完全复用 [SubPageScaffold] 框架，继承根 LandscapeTheme 取色（深色 / AMOLED / Monet 统一）。
 * 每个开源组件作为可聚焦卡片（左摇杆在卡片间移动、右摇杆滚屏），
 * 入场出场动画、横竖屏适配由框架统一处理。
 */
@Composable
fun LicensesPage(
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    /** 右摇杆滚动后引擎回写的首个可视项索引（焦点跟随，见 SubPageScaffold）。 */
    rightStickFirstVisible: androidx.compose.runtime.State<Int>? = null
) {
    val rows = buildList<SubPageRow> {
        add(
            SubPageRow.Static(key = "intro") {
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "本应用使用了以下开源软件组件，并对其创作者表示感谢。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Dimens.md))
            }
        )
        licenses.forEachIndexed { i, lic ->
            add(
                SubPageRow.Card(
                    key = "lic_$i",
                    focusable = true,
                    content = {
                        Text(
                            lic.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            lic.copyright,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            lic.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lic.notice.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                lic.notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                )
            )
            if (i < licenses.lastIndex) {
                add(
                    SubPageRow.Static(key = "div_$i") {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }
        }
        add(SubPageRow.Static(key = "bottom") { Spacer(Modifier.height(24.dp)) })
    }

    SubPageScaffold(
        title = "开放源代码许可",
        onBack = onBack,
        rows = rows,
        listState = listState,
        rightStickFirstVisible = rightStickFirstVisible
    )
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

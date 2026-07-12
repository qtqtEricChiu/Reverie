package cn.mocabolka.run.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mocabolka.run.ui.DarkMode
import cn.mocabolka.run.ui.OrientationMode
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.MotionSpec
import cn.mocabolka.run.viewmodel.SortMode
import kotlinx.coroutines.delay

/** 危险按钮点击后需二次确认的文案。 */
data class SettingsConfirm(
    val title: String,
    val text: String
)

/** 信息对话框内容（标题 + 正文 + 可选图片）。 */
data class InfoContent(
    val title: String,
    val text: String,
    val imageRes: Int? = null
)

/**
 * 设置页手柄控制桥：HomeScreen 的全局事件分发器通过此桥驱动设置页内部交互，
 * 因为 GamepadManager 在 Activity 层消费了实体按键（A/X/方向键），Compose 焦点
 * 的 onKeyEvent 收不到手柄事件。
 *
 * 所有 dialog 状态（openDropdownRow / confirmDialog / infoDialog / dropdownSelectedIndex）
 * 也在此桥内托管，HomeScreen 可直接读取状态在顶层 Box 渲染沉浸式子菜单遮罩，
 * 覆盖状态栏与挖孔区，实现全屏沉浸效果。
 */
data class SettingsControlBridge(
    val openDropdownRow: Int = -1,
    val onMove: (Int) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onActivate: (Int) -> Unit = {},
    /** 是否有任意弹窗（下拉/确认/信息）打开，供 HomeScreen 的 Back 键优先关闭弹窗。 */
    val hasOpenDialog: Boolean = false,
    /** 关闭当前打开的非下拉弹窗（确认/信息），下拉由 onDismiss 负责。 */
    val onDismissOverlay: () -> Unit = {},
    /** ── 提升到桥内的 dialog 状态，供 HomeScreen 在顶层 Box 沉浸渲染 ── */
    /** 当前下拉列表中选中项索引（-1 = 无）。 */
    val dropdownSelectedIndex: Int = 0,
    /** 确认对话框待处理内容（含动作闭包），null = 无。 */
    val pendingConfirm: Pair<SettingsConfirm, () -> Unit>? = null,
    /** 信息对话框内容（标题 + 正文 + 可选图片），null = 无。 */
    val pendingInfo: InfoContent? = null,
    /** ── 当前打开的 dropdown 内容（供 HomeScreen 渲染 DropdownDialog） ── */
    val dropdownLabel: String = "",
    val dropdownOptions: List<Any> = emptyList(),
    val dropdownOptionLabel: (Any) -> String = { it.toString() },
    val dropdownOnSelect: (Any) -> Unit = {}
)

private sealed class SettingItem {
    data class Section(val label: String) : SettingItem()
    data class Switch(
        val label: String,
        val desc: String,
        val icon: ImageVector?,
        val checked: Boolean,
        val onChange: (Boolean) -> Unit,
        val infoTitle: String = "",
        val infoText: String = "",
        val disabled: Boolean = false,
        val disabledHint: String = ""
    ) : SettingItem()
    data class Button(
        val label: String,
        val desc: String,
        val icon: ImageVector?,
        val onClick: () -> Unit,
        val danger: Boolean = false,
        val confirm: SettingsConfirm? = null,
        val infoTitle: String = "",
        val infoText: String = "",
        val imageRes: Int? = null
    ) : SettingItem()
    data class Dropdown(
        val label: String,
        val desc: String,
        val icon: ImageVector?,
        val value: Any,
        val options: List<Any>,
        val optionLabel: (Any) -> String,
        val onSelect: (Any) -> Unit
    ) : SettingItem()
    data class Info(
        val label: String,
        val desc: String,
        val icon: ImageVector? = null,
        val iconRes: Int? = null
    ) : SettingItem()
}

/**
 * 全屏设置页（Material Design 3 规范实现 + 完整手柄适配）。
 *
 * 设计规范：
 * - **设置项类型**：每个设置项是 ListItem 承载的「单行」可聚焦单元（开关/按钮/下拉选择）。
 *   取消之前自绘的"radio 列表"与"selector 内嵌面板"，统一为 MD3 风格的
 *   [DropdownDialog]（居中浮层 + DropdownMenuItem 列表）—— A 键打开，D-pad 上/下切换选项，
 *   A 确认，B/Back 关闭，焦点自动落在当前选中项上。
 * - **手柄焦点**：单一全局 [focusedRow] 索引（HomeScreen 驱动），每行可被 [FocusRequester]
 *   请求获焦并自动滚动到视野中（解决"显示焦点与逻辑焦点不匹配"）。
 * - **重新扫描**：移入列表，避免右上角"误触"。
 * - **分节标题**：以 [SectionHeader] 小标题分割"外观/显示/..."，不再用大字+小字混排（任务 22）。
 */
@Composable
fun SettingsPage(
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    onRescan: () -> Unit,
    isScanning: Boolean = false,
    onManagePermissions: () -> Unit,
    appVersion: String = "",
    onClearFavorites: () -> Unit = {},
    onResetSettings: () -> Unit = {},
    showBadges: Boolean = true,
    onShowBadgesChange: (Boolean) -> Unit = {},
    useMonet: Boolean = true,
    onUseMonetChange: (Boolean) -> Unit = {},
    wallpaperBehind: Boolean = false,
    onWallpaperBehindChange: (Boolean) -> Unit = {},
    cutoutAdapt: Boolean = true,
    onCutoutAdaptChange: (Boolean) -> Unit = {},
    showSystemApps: Boolean = false,
    onShowSystemAppsChange: (Boolean) -> Unit = {},
    onExportCategories: () -> Unit = {},
    onImportCategories: () -> Unit = {},
    onClearCategories: () -> Unit = {},
    orientationMode: OrientationMode = OrientationMode.FORCE_LANDSCAPE,
    onOrientationModeChange: (OrientationMode) -> Unit = {},
    dynamicBackground: Boolean = true,
    onDynamicBackgroundChange: (Boolean) -> Unit = {},
    glassSurface: Boolean = true,
    onGlassSurfaceChange: (Boolean) -> Unit = {},
    reduceMotion: Boolean = false,
    onReduceMotionChange: (Boolean) -> Unit = {},
    focusedRow: Int = -1,
    onFocusedRowChange: (Int) -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    onDismiss: () -> Unit,
    /** 标题栏关闭按钮（触摸用户退出设置页入口）：Tab 模式下回到主页。 */
    onClose: () -> Unit = onDismiss,
    onRowCountChange: (Int) -> Unit = {},
    infoTrigger: androidx.compose.runtime.MutableState<Int?> =
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) },
    /** 手柄事件控制桥：由 HomeScreen 的全局事件分发器驱动（A 键激活 / 下拉翻页），
     *  解决 GamepadManager 在 Activity 层消费实体按键、Compose 焦点 onKeyEvent 收不到的问题。 */
    controlBridge: androidx.compose.runtime.MutableState<SettingsControlBridge> =
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(SettingsControlBridge()) },
    /** 关于页面打开请求：点击关于按钮时触发 */
    onAboutClick: () -> Unit = {}
) {
    val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    // ── 设置项模型 ─────────────────────────────────────────
    // 全部统一为"单行可聚焦"模型：每个项的 index 就是 focusableRow。
    // darkMode / sortMode / orientation 全部走 DropdownDialog 交互。
    val aboutClick by rememberUpdatedState(onAboutClick)
    val items = remember(
        darkMode, sortMode, orientationMode, useMonet, showBadges,
        wallpaperBehind, cutoutAdapt, showSystemApps, dynamicBackground,
        glassSurface, reduceMotion, isScanning
    ) {
        mutableListOf<SettingItem>().apply {
            // ─ 外观 ─
            add(SettingItem.Section("外观"))
            add(SettingItem.Dropdown(
                label = "深色模式",
                desc = "跟随系统 / 浅色 / 深色 / AMOLED 纯黑",
                icon = Icons.Filled.DarkMode,
                value = darkMode,
                options = DarkMode.entries.toList(),
                optionLabel = { (it as DarkMode).label },
                onSelect = { onDarkModeChange(it as DarkMode) }
            ))
            add(SettingItem.Dropdown(
                label = "强制旋屏",
                desc = "离开 Reverie 后仍将保持生效（需悬浮窗权限）",
                icon = Icons.Filled.ScreenRotation,
                value = orientationMode,
                options = OrientationMode.SELECTOR_ORDER,
                optionLabel = { (it as OrientationMode).label },
                onSelect = { onOrientationModeChange(it as OrientationMode) }
            ))
            add(SettingItem.Switch(
                label = "莫奈取色（跟随壁纸）",
                desc = "从系统壁纸提取色板；关闭后使用 Reverie 自定义配色",
                icon = Icons.Filled.Palette,
                checked = useMonet, onChange = onUseMonetChange
            ))
            add(SettingItem.Switch(
                label = "显示应用角标",
                desc = "未读通知数",
                icon = Icons.Filled.Notifications,
                checked = showBadges, onChange = onShowBadgesChange
            ))
            add(SettingItem.Switch(
                label = "穿透背景（显示桌面壁纸）",
                desc = "开启后主界面以系统默认壁纸作为背景",
                icon = Icons.Filled.Wallpaper,
                checked = wallpaperBehind, onChange = onWallpaperBehindChange
            ))
            add(SettingItem.Switch(
                label = "挖孔屏适配",
                desc = "开启：关键内容绕开前置摄像头；关闭：内容铺满延伸至挖孔区（全屏）",
                icon = Icons.Filled.Security,
                checked = cutoutAdapt, onChange = onCutoutAdaptChange
            ))

            // ─ 动效（节能模式已移除，三项独立开关）──
            add(SettingItem.Section("动效"))
            add(SettingItem.Switch(
                label = "动态氛围背景",
                desc = "缓慢漂移的柔光渐变；关闭后退回静态渐变",
                icon = Icons.Filled.Gradient,
                checked = dynamicBackground, onChange = onDynamicBackgroundChange,
                infoTitle = "动态氛围背景",
                infoText = "在主界面底层渲染一层缓慢漂移的柔光渐变，营造氛围感。" +
                        "关闭后退回静态纯色渐变，可显著降低 GPU 负载与耗电。" +
                        "该效果为循环动画，遵循系统「减少动态效果」设置。"
            ))
            add(SettingItem.Switch(
                label = "玻璃拟态",
                desc = "磨砂玻璃景深与高光描边，让前景浮于背景之上",
                icon = Icons.Filled.Wallpaper,
                checked = glassSurface, onChange = onGlassSurfaceChange,
                infoTitle = "玻璃拟态",
                infoText = "为卡片、面板叠加磨砂玻璃质感的景深与高光描边，" +
                        "让前景内容浮于背景之上。关闭后改为纯色表面，" +
                        "视觉更扁平、性能开销更低。"
            ))
            add(SettingItem.Switch(
                label = "减少动态效果",
                desc = "开启后冻结呼吸光晕/漂移等循环动画（晕动症与低性能设备友好）",
                icon = Icons.Filled.MotionPhotosOff,
                checked = reduceMotion, onChange = onReduceMotionChange,
                infoTitle = "减少动态效果",
                infoText = "开启后将冻结呼吸光晕、背景漂移、图标按压缩放等所有循环动画，" +
                        "仅保留必要的过渡与即时反馈。适合晕动症用户，" +
                        "或在低性能设备上提升流畅度。该设置会与系统「省电模式」叠加生效。"
            ))

            // ─ 应用列表 ─
            add(SettingItem.Section("应用列表"))
            add(SettingItem.Switch(
                label = "显示系统应用",
                desc = "开启后主列表额外包含系统应用（设置、系统服务等）",
                icon = Icons.Filled.Apps,
                checked = showSystemApps, onChange = onShowSystemAppsChange
            ))
            add(SettingItem.Dropdown(
                label = "排序方式",
                desc = "应用到主列表的所有应用",
                icon = Icons.Filled.Sort,
                value = sortMode,
                options = SortMode.entries.toList(),
                optionLabel = { (it as SortMode).label },
                onSelect = { onSortModeChange(it as SortMode) }
            ))
            add(SettingItem.Button(
                label = "导出应用列表",
                desc = "导出 应用名/包名/安装来源/分类 到下载目录，供 AI 填充分类",
                icon = Icons.Filled.FileDownload,
                onClick = onExportCategories,
                infoTitle = "导出 / 导入分类映射",
                infoText = "导出：枚举本机所有应用（含系统应用），生成 reverie_categories.json 至下载目录。\n" +
                        "每行包含 应用名 / 包名 / 安装来源 / 是否系统应用 / 当前分类 / AI 分类，\n" +
                        "供你交给大模型按既有分类标签回填 ai_category 字段。\n" +
                        "导入：AI 回填完成后通过「导入分类映射」按钮选定 json，\n" +
                        "Reverie 会按 ai_category 覆写分类。\n" +
                        "清除：恢复为系统默认分类（清除所有 AI 覆盖）。",
                imageRes = cn.mocabolka.run.R.drawable.img_export_categories_guide
            ))
            add(SettingItem.Button(
                label = "导入分类映射",
                desc = "读取下载目录 reverie_categories.json 中 AI 回填的分类",
                icon = Icons.Filled.FileUpload,
                onClick = onImportCategories
            ))
            add(SettingItem.Button(
                label = "清除分类映射",
                desc = "清空 AI 分类覆盖，恢复系统原始分类",
                icon = Icons.Filled.DeleteSweep,
                onClick = onClearCategories
            ))

            // ─ 工具 ─
            add(SettingItem.Section("工具"))
            add(SettingItem.Button(
                label = if (isScanning) "扫描中…" else "重新扫描",
                desc = "重新枚举已安装应用列表",
                icon = Icons.Filled.Refresh,
                onClick = onRescan
            ))
            add(SettingItem.Button(
                label = "权限管理",
                desc = "电池优化、自启动、悬浮窗、使用情况访问、通知角标等",
                icon = Icons.Filled.Security,
                onClick = onManagePermissions
            ))
            add(SettingItem.Button(
                label = "清空全部收藏",
                desc = "此操作不可撤销",
                icon = Icons.Filled.Star,
                onClick = onClearFavorites,
                danger = true,
                confirm = SettingsConfirm(
                    title = "清空全部收藏？",
                    text = "将移除你收藏的所有应用，此操作不可撤销。"
                )
            ))
            add(SettingItem.Button(
                label = "重置所有设置",
                desc = "恢复默认外观/排序/显示，并清空收藏",
                icon = Icons.Filled.SettingsBackupRestore,
                onClick = onResetSettings,
                danger = true,
                confirm = SettingsConfirm(
                    title = "重置所有设置？",
                    text = "将恢复默认的外观、排序与显示选项，并清空全部收藏。" +
                            "此操作不可撤销。"
                )
            ))

            // ─ 关于 ─
            add(SettingItem.Section("关于"))
            add(SettingItem.Button(
                label = "关于 Reverie",
                desc = "版本信息、特别感谢、开源许可",
                icon = Icons.Filled.Info,
                onClick = { aboutClick() }
            ))
        }.toList()
    }

    // 仅「可聚焦」项拥有 focusableRow 索引（过滤掉 Section 标题）
    val focusableIndices = items.indices.filter { items[it] !is SettingItem.Section }

    // 向宿主上报可聚焦项数量上限（优化 3：避免手柄焦点越界到空行）
    LaunchedEffect(focusableIndices.size) {
        onRowCountChange(focusableIndices.lastIndex)
    }

    // 滚动到当前焦点（任务 26：focusedRow 与可视焦点 100% 一致——scrollOffset 让目标行
    // 出现在视图中段而不是顶部，避免连续两次 focus 时位置来回跳）
    LaunchedEffect(focusedRow) {
        if (focusedRow >= 0) {
            val i = focusableIndices.getOrNull(focusedRow) ?: return@LaunchedEffect
            listState.animateScrollToItem(i, scrollOffset = -80)
        }
    }

    // 弹窗状态已全部提升到 SettingsControlBridge（由 HomeScreen 在顶层 Box 沉浸渲染）。
    // 此处 SideEffect 将回调绑定到 bridge 字段，HomeScreen 读取 bridge 状态决定渲染。
    // 手柄 X 键（由 HomeScreen 经 infoTrigger 下发焦点行号）触发对应项的信息弹窗
    LaunchedEffect(infoTrigger.value) {
        val row = infoTrigger.value ?: return@LaunchedEffect
        infoTrigger.value = null
        val idx = focusableIndices.getOrNull(row) ?: return@LaunchedEffect
        val item = items.getOrNull(idx)
        if (item is SettingItem.Switch && item.infoTitle.isNotBlank()) {
            // 直接写到桥内 pendingInfo 字段，HomeScreen 读取后渲染 InfoDialog
            controlBridge.value = controlBridge.value.copy(
                pendingInfo = InfoContent(
                    title = item.infoTitle,
                    text = item.infoText
                )
            )
        } else if (item is SettingItem.Button && item.infoTitle.isNotBlank()) {
            // 按钮型设置项也支持 info 弹窗（含可选说明图）
            controlBridge.value = controlBridge.value.copy(
                pendingInfo = InfoContent(
                    title = item.infoTitle,
                    text = item.infoText,
                    imageRes = item.imageRes
                )
            )
        }
    }

    // 将手柄控制桥注册到 HomeScreen（每次重组刷新，回调读取最新 state）。
    // 这样 A 键激活 / 下拉上下翻页 / 确认 / 取消 全部由 HomeScreen 的全局事件分发驱动，
    // 不再依赖收不到的 Compose 焦点 onKeyEvent（任务 1 / 3 / 4 的根因修复）。
    // 注意：openDropdownRow/dropdownSelectedIndex 等可变状态直接由回调写入桥字段，
    // 不再有本地 remember 副本，HomeScreen 从桥字段读取最新值。
    SideEffect {
        val bridge = controlBridge.value
        val row = bridge.openDropdownRow
        // 从 items 中读取当前 dropdown item 的内容（label / options / onSelect）
        val dropdownItem = if (row >= 0) {
            val idx = focusableIndices.getOrNull(row)
            if (idx != null) items.getOrNull(idx) as? SettingItem.Dropdown else null
        } else null
        controlBridge.value = SettingsControlBridge(
            openDropdownRow = row,
            onMove = { dir ->
                val idx = focusableIndices.getOrNull(row) ?: return@SettingsControlBridge
                val item = items[idx] as? SettingItem.Dropdown ?: return@SettingsControlBridge
                // 从桥读取最新选中索引，避免 SideEffect 闭包 stale 问题
                val currentSel = controlBridge.value.dropdownSelectedIndex
                controlBridge.value = controlBridge.value.copy(
                    dropdownSelectedIndex = (currentSel + dir).coerceIn(0, item.options.lastIndex)
                )
            },
            onConfirm = {
                val idx = focusableIndices.getOrNull(row) ?: return@SettingsControlBridge
                val item = items[idx] as? SettingItem.Dropdown ?: return@SettingsControlBridge
                // 从桥读取最新选中索引，避免 SideEffect 闭包 stale 问题
                val currentSel = controlBridge.value.dropdownSelectedIndex
                item.onSelect(item.options[currentSel])
                controlBridge.value = controlBridge.value.copy(openDropdownRow = -1)
            },
            onDismiss = {
                controlBridge.value = controlBridge.value.copy(openDropdownRow = -1)
            },
            onActivate = { activateRow ->
                val idx = focusableIndices.getOrNull(activateRow) ?: return@SettingsControlBridge
                when (val item = items[idx]) {
                    is SettingItem.Switch -> if (!item.disabled) item.onChange(!item.checked)
                    is SettingItem.Button -> {
                        if (item.confirm != null) {
                            controlBridge.value = controlBridge.value.copy(
                                pendingConfirm = item.confirm to item.onClick
                            )
                        } else {
                            item.onClick()
                        }
                    }
                    is SettingItem.Dropdown -> {
                        controlBridge.value = controlBridge.value.copy(
                            openDropdownRow = activateRow,
                            dropdownSelectedIndex = (item.options.indexOf(item.value)).coerceAtLeast(0),
                            dropdownLabel = item.label,
                            dropdownOptions = item.options,
                            dropdownOptionLabel = item.optionLabel,
                            dropdownOnSelect = item.onSelect
                        )
                    }
                    else -> {}
                }
            },
            hasOpenDialog = row >= 0 ||
                bridge.pendingConfirm != null ||
                bridge.pendingInfo != null,
            onDismissOverlay = {
                controlBridge.value = controlBridge.value.copy(
                    pendingConfirm = null,
                    pendingInfo = null
                )
            },
            dropdownSelectedIndex = bridge.dropdownSelectedIndex,
            pendingConfirm = bridge.pendingConfirm,
            pendingInfo = bridge.pendingInfo,
            dropdownLabel = dropdownItem?.label ?: "",
            dropdownOptions = dropdownItem?.options ?: emptyList(),
            dropdownOptionLabel = dropdownItem?.optionLabel ?: { it.toString() },
            dropdownOnSelect = dropdownItem?.onSelect ?: {}
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.lg, vertical = Dimens.md)
    ) {
        // 注：页面标题（"设置"）已上移至此栏 StatusBar 的页标题位，避免与子页面标题
        // 形成「双层标题」；B 返回键位提示与关闭 × 按钮一并移除（由 Tab 滑块 / 手柄 B 返回）。
    // 入场错峰键：设置页切换（Tab 进入/重新扫描）时触发所有项重新入场
    val entranceKey = "settings|${System.identityHashCode(items)}"

    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Dimens.xxl),
        verticalArrangement = Arrangement.spacedBy(Dimens.xs)
    ) {
        itemsIndexed(items, key = { index, item ->
            // 用类型+标签+索引作为唯一 key，确保列表项正确更新
            when (item) {
                is SettingItem.Section -> "section_${item.label}_$index"
                is SettingItem.Switch -> "switch_${item.label}_$index"
                is SettingItem.Button -> "button_${item.label}_$index"
                is SettingItem.Dropdown -> "dropdown_${item.label}_$index"
                is SettingItem.Info -> "info_${item.label}_$index"
            }
        }) { index, item ->
            val rowIdx = focusableIndices.indexOf(index)
            val isFocused = rowIdx == focusedRow
            // 入场错峰动画（与 AppListItem 统一：淡入 + 轻微上移）
            // 使用 MotionSpec.Fast（220ms）+ stagger 8ms/项上限 160ms，
            // 快速滚动跳过（由 LazyColumn scroll state 控制）。
            var appeared by remember(entranceKey) { mutableStateOf(reduceMotion) }
            LaunchedEffect(entranceKey) {
                if (reduceMotion) { appeared = true; return@LaunchedEffect }
                appeared = false
                delay(MotionSpec.staggerDelay(index, perMs = 8, maxMs = 160))
                appeared = true
            }
            val entAlpha by animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = MotionSpec.Fast, label = "setEntA"
            )
            val entY by animateFloatAsState(
                targetValue = if (appeared) 0f else 10f,
                animationSpec = MotionSpec.Fast, label = "setEntY"
            )
            Box(modifier = Modifier.graphicsLayer { alpha = entAlpha; translationY = entY }) {
                when (item) {
                    is SettingItem.Section -> SectionHeader(item.label)
                    is SettingItem.Switch -> SwitchListItem(
                        label = item.label,
                        desc = item.desc,
                        icon = item.icon,
                        checked = item.checked,
                        isFocused = isFocused,
                        focusRequester = remember { FocusRequester() },
                        onFocus = { onFocusedRowChange(rowIdx) },
                        onCheckedChange = { item.onChange(it) },
                        onInfo = if (item.infoTitle.isNotBlank()) {
                            { controlBridge.value = controlBridge.value.copy(
                                pendingInfo = InfoContent(title = item.infoTitle, text = item.infoText)
                            ) }
                        } else null,
                        disabled = item.disabled,
                        disabledHint = item.disabledHint
                    )
                    is SettingItem.Button -> ButtonListItem(
                        label = item.label,
                        desc = item.desc,
                        icon = item.icon,
                        isFocused = isFocused,
                        danger = item.danger,
                        focusRequester = remember { FocusRequester() },
                        onFocus = { onFocusedRowChange(rowIdx) },
                        onClick = {
                            if (item.confirm != null) {
                                controlBridge.value = controlBridge.value.copy(
                                    pendingConfirm = item.confirm to item.onClick
                                )
                            } else {
                                item.onClick()
                            }
                        },
                        onInfo = if (item.infoTitle.isNotBlank()) {
                            {
                                controlBridge.value = controlBridge.value.copy(
                                    pendingInfo = InfoContent(
                                        title = item.infoTitle,
                                        text = item.infoText,
                                        imageRes = item.imageRes
                                    )
                                )
                            }
                        } else null
                    )
                    is SettingItem.Dropdown -> DropdownListItem(
                        label = item.label,
                        desc = item.desc,
                        icon = item.icon,
                        current = item.optionLabel(item.value),
                        isFocused = isFocused,
                        expanded = controlBridge.value.openDropdownRow == rowIdx,
                        focusRequester = remember { FocusRequester() },
                        onFocus = { onFocusedRowChange(rowIdx) },
                        onOpen = {
                            controlBridge.value = controlBridge.value.copy(
                                openDropdownRow = rowIdx,
                                dropdownSelectedIndex = (item.options.indexOf(item.value)).coerceAtLeast(0),
                                dropdownLabel = item.label,
                                dropdownOptions = item.options,
                                dropdownOptionLabel = item.optionLabel,
                                dropdownOnSelect = item.onSelect
                            )
                        }
                    )
                    is SettingItem.Info -> InfoListItem(
                        label = item.label,
                        desc = item.desc,
                        icon = item.icon,
                        iconRes = item.iconRes
                    )
                }
            }
        }
        item { Spacer(Modifier.height(Dimens.xl)) }
    }
}
}

// DarkMode 中文名（供 DropdownDialog 显示）
private val DarkMode.label: String
    get() = when (this) {
        DarkMode.SYSTEM -> "跟随系统"
        DarkMode.LIGHT -> "浅色"
        DarkMode.DARK -> "深色"
        DarkMode.AMOLED -> "AMOLED 纯黑"
    }

// SortMode 中文名
private val SortMode.label: String
    get() = when (this) {
        SortMode.NAME -> "按名称"
        SortMode.INSTALL_TIME -> "按安装时间"
        SortMode.CATEGORY -> "按分类"
        SortMode.LAST_USED -> "按最近游玩"
    }

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.md, bottom = Dimens.xxs, start = Dimens.xxs)
    )
}

// ═══════════════════════════════════════════════════════════════
// 单行 ListItem：开关
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SwitchListItem(
    label: String,
    desc: String,
    icon: ImageVector?,
    checked: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onInfo: (() -> Unit)? = null,
    disabled: Boolean = false,
    disabledHint: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    // 节能锁定时背景略微变暗，但不至于全灰，保持与 unchecked 状态的视觉区分
    val surfaceColor = when {
        disabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentAlpha = if (disabled) 0.75f else 1f
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = surfaceColor,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (isFocused && !disabled) Modifier.border(
                    Dimens.FocusBorderWidthSelected,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.shapes.medium
                ) else Modifier
            )
            .combinedClickable(
                enabled = !disabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) },
                // 信息说明在锁定态也应可查看（长按 / X 键），不受 disabled 限制
                onLongClick = { onInfo?.invoke() }
            )
            .focusable(interactionSource = interactionSource, enabled = !disabled)
    ) {
        ListItem(
            leadingContent = icon?.let {
                {
                    Icon(it, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                        modifier = Modifier.size(24.dp))
                }
            },
            headlineContent = {
                Text(label, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))
            },
            supportingContent = {
                Column {
                    if (desc.isNotBlank()) {
                        Text(desc, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha))
                    }
                    if (disabledHint.isNotBlank()) {
                        Spacer(Modifier.height(Dimens.xxs))
                        Text(disabledHint, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                    }
                }
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                ) {
                    Switch(
                        checked = checked,
                        // Switch 本身设为 disabled：避免其消费指针事件导致外层 Surface 的
                        // combinedClickable 收不到点击（点击穿透 bug）；切换完全由 Surface 接管。
                        // disabled 灰化通过显式指定 disabled colors = 正常色规避，保持"可点"视觉。
                        enabled = false,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledCheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            disabledCheckedTrackColor = MaterialTheme.colorScheme.primary,
                            disabledUncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    if (onInfo != null) {
                        // 任务 33：信息按钮 + 手柄 X 键位提示浮块
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onInfo() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Info, null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (isFocused) {
                            // 仅在焦点态显示 X 键位提示（统一深色圆角按键浮块风格）
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "X",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 单行 ListItem：按钮
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ButtonListItem(
    label: String,
    desc: String,
    icon: ImageVector?,
    isFocused: Boolean,
    danger: Boolean,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onInfo: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val accent = if (danger) MaterialTheme.colorScheme.error
                 else MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isFocused) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (isFocused) Modifier.border(
                    Dimens.FocusBorderWidthSelected, accent, MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        ListItem(
            leadingContent = icon?.let {
                {
                    Icon(it, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
            },
            headlineContent = {
                Text(label, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium, color = accent)
            },
            supportingContent = if (desc.isNotBlank()) {
                {
                    Text(desc, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else null,
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                ) {
                    if (onInfo != null) {
                        // 信息说明按钮（点击/手柄 X 键触发 InfoDialog）
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onInfo() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Info, null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (isFocused) {
                            // 仅在焦点态显示 X 键位提示
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "X",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 单行 ListItem：下拉选择（点 A 打开弹窗）
// ═══════════════════════════════════════════════════════════════
@Composable
private fun DropdownListItem(
    label: String,
    desc: String,
    icon: ImageVector?,
    current: String,
    isFocused: Boolean,
    expanded: Boolean,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onOpen: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isFocused || expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (isFocused || expanded) Modifier.border(
                    Dimens.FocusBorderWidthSelected,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpen
            )
            .focusable(interactionSource = interactionSource)
    ) {
        ListItem(
            leadingContent = icon?.let {
                {
                    Icon(it, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            },
            headlineContent = {
                Text(label, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            },
            supportingContent = if (desc.isNotBlank()) {
                {
                    Text(desc, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else null,
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                ) {
                    Text(
                        current,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun InfoListItem(label: String, desc: String, icon: ImageVector?, iconRes: Int?) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            leadingContent = {
                when {
                    iconRes != null -> Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                    icon != null -> Icon(icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            },
            headlineContent = {
                Text(label, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            },
            supportingContent = if (desc.isNotBlank()) {
                {
                    Text(desc, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Dropdown 弹窗（任务 24/25：MD3 + 完整手柄/键盘适配）
// A 键打开；D-pad ↑↓ 移焦点；A/Enter 确认；B/Esc 关闭。
// ═══════════════════════════════════════════════════════════════
@Composable
fun DropdownDialog(
    title: String,
    options: List<Any>,
    optionLabel: (Any) -> String,
    selectedIndex: Int,
    onSelect: (Any) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    // 跟随手柄焦点滚动到选中项（任务 3-c：窗口跟随焦点滚动）
    LaunchedEffect(selectedIndex) {
        if (options.isNotEmpty()) listState.animateScrollToItem(selectedIndex)
    }
    // 与统计页日期选择器统一：全屏遮罩（点击空白关闭，indication=null 屏蔽长按触控水波纹动画）+ 居中 MD3 Surface。
    val scrimInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = MotionSpec.ScrimAlpha))
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // 卡片：限制最大宽度（精致）、柔光渐变背景、点击不冒泡关闭
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.92f)
                .padding(Dimens.md)
                // 卡片本体点击不冒泡到遮罩（避免误关）
                .clickable(onClick = { })
        ) {
            Column(
                modifier = Modifier.padding(Dimens.lg),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close, contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.xxs))
                Text(
                    text = "↑↓ 切换 · A 确认 · B 关闭",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                // 选项列表：可滚动 + 跟随焦点滚动 + 右侧滚动条（任务 3-b / 3-c）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = if (options.size > 6) 10.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
                    ) {
                        itemsIndexed(options) { i, opt ->
                            val isHighlighted = i == selectedIndex
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        optionLabel(opt),
                                        fontWeight = if (isHighlighted) FontWeight.SemiBold
                                                     else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = if (isHighlighted) {
                                    {
                                        Icon(
                                            Icons.Filled.Check, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else null,
                                onClick = { onSelect(opt) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isHighlighted) Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                                MaterialTheme.shapes.small
                                            )
                                        else Modifier
                                    )
                            )
                        }
                    }
                    VerticalScrollbar(listState, options.size)
                }
                Spacer(Modifier.height(Dimens.xs))
            }
        }
    }
}

/**
 * 轻量滚动条：根据 LazyList 可见项比例绘制右侧滑块（任务 3-b）。
 * 仅在内容溢出（项数 > 可视项数）时出现。
 */
@Composable
private fun VerticalScrollbar(state: LazyListState, itemCount: Int) {
    val layoutInfo = state.layoutInfo
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty() || itemCount <= visible.size) return
    val viewport = visible.size
    val first = state.firstVisibleItemIndex
    val thumbRatio = (viewport.toFloat() / itemCount).coerceIn(0.08f, 1f)
    val maxOffset = (itemCount - viewport).toFloat().coerceAtLeast(1f)
    val offsetRatio = (first.toFloat() / maxOffset).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(3.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.fillMaxHeight(offsetRatio))
        Box(
            Modifier
                .fillMaxHeight(thumbRatio)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.fillMaxHeight((1f - offsetRatio - thumbRatio).coerceAtLeast(0f)))
    }
}

/**
 * 危险操作二次确认对话框（任务 30 / 30.5）：MD3 AlertDialog。
 * 重置所有设置 / 清空全部收藏 等不可撤销操作必须先经此确认。
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 与 DropdownDialog / InfoDialog 统一：全屏遮罩（ScrimAlpha）+ 居中 Surface
    // indication=null 屏蔽遮罩长按水波纹触控动画（不影响入场退场与手柄焦点）。
    val scrimInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = MotionSpec.ScrimAlpha))
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.92f)
                .padding(Dimens.md)
                .clickable(onClick = { })
        ) {
            Column(
                modifier = Modifier.padding(Dimens.lg),
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                // 警告图标 + 标题行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    Text(title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text(text, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("A 确认 · B 关闭",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(Dimens.xs))
                    TextButton(onClick = onConfirm) {
                        Text("确认", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * 选项说明对话框（任务 33）：与 DropdownDialog / 日期选择器统一为
 * 全屏遮罩 + 居中 MD3 Surface（大圆角 + 景深投影 + lg 内边距）。
 * 由信息图标点击 / 长按选项 / 手柄 X 键触发。
 */
@Composable
fun InfoDialog(
    title: String,
    text: String,
    imageRes: Int? = null,
    onDismiss: () -> Unit
) {
    // 与 DropdownDialog / 日期选择器一致的遮罩 + 居中 Surface
    // indication=null 屏蔽遮罩长按水波纹触控动画。
    val scrimInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = MotionSpec.ScrimAlpha))
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.92f)
                .padding(Dimens.md)
                .clickable(onClick = { })
        ) {
            Column(
                modifier = Modifier.padding(Dimens.lg),
                verticalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Filled.Info, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    // 右上角关闭按钮（与日期选择器一致）
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close, contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (imageRes != null) {
                    Spacer(Modifier.height(Dimens.xs))
                    // 说明图（提示卡片样式）
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = imageRes),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    "B 关闭",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


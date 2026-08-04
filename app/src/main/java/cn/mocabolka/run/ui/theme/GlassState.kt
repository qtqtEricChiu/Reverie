package cn.mocabolka.run.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * 玻璃拟态全局开关（CompositionLocal 静默注入，参照 [LocalShowFocusIndicators] 模式）。
 *
 * 由 [LandscapeTheme] 顶层 Provider 注入（数据源 = SettingsRepository.glassSurfaceFlow），
 * 任何组件内部 `LocalGlassEnabled.current` 即可获知玻璃开关状态，
 * 新增组件零函数签名改动，消灭逐层 `glass: Boolean` 参数透传链。
 *
 * 默认 true（与 SettingsRepository KEY_GLASS 默认值一致）。
 */
val LocalGlassEnabled = compositionLocalOf { true }

/**
 * 穿透壁纸模式（壁纸在卡片后方可见）：玻璃拟态的"背景是否有磨砂底可透出"开关。
 *
 * 同样以 CompositionLocal 静默注入（与 [LocalGlassEnabled] 同一范式），让任意嵌套组件
 * 零参数透传即可读取。由持有 [cn.mocabolka.run.viewmodel.HomeViewModel] 的顶层页面
 * （HomeScreen）经 [LocalWallpaperBehind] Provider 注入其 `wallpaperBehind` 状态。
 *
 * 与 [LocalGlassEnabled] 配合：玻璃开启 + 穿透壁纸 = 卡片表面进一步降到
 * [cn.mocabolka.run.ui.theme.SurfaceTokens.CardLevel.Overlay]（最透档），让磨砂壁纸清晰透出，
 * 与 AppDetailPanel 的玻璃处理保持一致；二者缺一则仅降到 Subtle。
 *
 * 默认 false（与 SettingsRepository KEY_WALLPAPER_BEHIND 默认值一致）。
 */
val LocalWallpaperBehind = compositionLocalOf { false }

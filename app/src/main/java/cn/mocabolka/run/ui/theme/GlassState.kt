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

package cn.mocabolka.run.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DarkMode { SYSTEM, LIGHT, DARK, AMOLED }

/** 兼容向导各项的持久化 key（P2-6 逐项完成追踪）。 */
object CompatItem {
    const val BATTERY = "battery"
    const val AUTOSTART = "autostart"
    const val OVERLAY = "overlay"
    const val USAGE = "usage"
    const val NOTIFICATION = "notification"
    const val SCREENSHOT = "screenshot"
    val ALL = listOf(BATTERY, AUTOSTART, OVERLAY, USAGE, NOTIFICATION, SCREENSHOT)
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context

    /** 当前应用版本名（如 "1.0.0"），供"关于"展示（C3-4）。 */
    val appVersion: String
        get() = runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
                .toString()
        }.getOrDefault("")

    private val _darkMode = MutableStateFlow(
        DarkMode.entries[prefs.getInt(KEY_DARK_MODE, DarkMode.SYSTEM.ordinal)]
    )
    var darkMode: DarkMode
        get() = _darkMode.value
        set(value) {
            _darkMode.value = value
            prefs.edit { putInt(KEY_DARK_MODE, value.ordinal) }
        }
    /** 响应式深色模式，供主题实时切换（P2-5 去除 recreate 闪烁）。 */
    val darkModeFlow: StateFlow<DarkMode> = _darkMode.asStateFlow()

    var permissionRationaleShown: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_RATIONALE, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMISSION_RATIONALE, value) }

    /** 最后焦点应用包名（进程死亡后恢复焦点）。 */
    var lastFocused: String
        get() = prefs.getString(KEY_LAST_FOCUSED, "") ?: ""
        set(value) = prefs.edit { putString(KEY_LAST_FOCUSED, value) }

    /**
     * 最后停留的主 Tab（HOME/STATS/SEARCH/SETTINGS 的枚举名）。
     * 进程被系统回收后冷启动，HomeScreen 据此恢复到用户离开前的页面，
     * 避免"回到应用又从首页重新进入"的割裂感。
     */
    var lastMainTab: String
        get() = prefs.getString(KEY_LAST_MAIN_TAB, "") ?: ""
        set(value) = prefs.edit { putString(KEY_LAST_MAIN_TAB, value) }

    /** 是否显示应用通知角标（C6-4，统一开关）。 */
    private val _showBadges = MutableStateFlow(prefs.getBoolean(KEY_SHOW_BADGES, true))
    val showBadgesFlow: StateFlow<Boolean> = _showBadges.asStateFlow()
    var showBadges: Boolean
        get() = _showBadges.value
        set(value) {
            _showBadges.value = value
            prefs.edit { putBoolean(KEY_SHOW_BADGES, value) }
        }

    /** 震动触感强度（默认弱 = 旧版单一"开启"档的行为）。 */
    private val _hapticIntensityFlow = MutableStateFlow(
        // 兼容旧版 boolean 键：开启->弱(=旧行为)，关闭->关闭；新键优先
        runCatching { HapticIntensity.valueOf(prefs.getString(KEY_HAPTIC_INTENSITY, "") ?: "") }
            .getOrNull()
            ?: if (prefs.contains(KEY_HAPTICS))
                if (prefs.getBoolean(KEY_HAPTICS, true)) HapticIntensity.WEAK else HapticIntensity.OFF
            else HapticIntensity.WEAK
    )

    val hapticIntensity: StateFlow<HapticIntensity> get() = _hapticIntensityFlow

    fun setHapticIntensity(value: HapticIntensity) {
        _hapticIntensityFlow.value = value
        prefs.edit { putString(KEY_HAPTIC_INTENSITY, value.name) }
    }

    /** 是否已提示过 QUERY_ALL_PACKAGES 被撤销（P1-5）。 */
    var queryAllPackagesWarned: Boolean
        get() = prefs.getBoolean(KEY_QUERY_ALL_PACKAGES_WARNED, false)
        set(value) = prefs.edit { putBoolean(KEY_QUERY_ALL_PACKAGES_WARNED, value) }

    /**
     * 强制旋屏模式（嵌入设置的"强制旋屏"功能）。
     * 默认随设备形态智能选择：竖屏设备（手机）默认 [OrientationMode.FORCE_PORTRAIT]，
     * 横屏设备（平板/电视）默认 [OrientationMode.FORCE_LANDSCAPE]。
     * 已显式设置过的用户持久值优先。
     */
    private val defaultOrientationMode: OrientationMode
        get() {
            val cfg = appContext.resources.configuration
            val isNaturalPortrait = cfg.screenHeightDp >= cfg.screenWidthDp
            return if (isNaturalPortrait) OrientationMode.FORCE_PORTRAIT
            else OrientationMode.FORCE_LANDSCAPE
        }
    private val _orientationMode =
        MutableStateFlow(
            prefs.getString(KEY_ORIENTATION_MODE, null)
                ?.let { OrientationMode.fromValue(it) }
                ?: defaultOrientationMode
        )
    val orientationModeFlow: StateFlow<OrientationMode> = _orientationMode.asStateFlow()
    var orientationMode: OrientationMode
        get() = _orientationMode.value
        set(value) {
            _orientationMode.value = value
            prefs.edit { putString(KEY_ORIENTATION_MODE, value.value) }
        }

    /**
     * 是否启用 Material You 莫奈取色（minSdk = 36 始终支持动态取色）。
     * 默认 true，系统动态取色不可用时自动回退到 Reverie 自定义色板。
     */
    private val _useMonet = MutableStateFlow(prefs.getBoolean(KEY_USE_MONET, true))
    val useMonetFlow: StateFlow<Boolean> = _useMonet.asStateFlow()
    var useMonet: Boolean
        get() = _useMonet.value
        set(value) {
            _useMonet.value = value
            prefs.edit { putBoolean(KEY_USE_MONET, value) }
        }

    /** 兼容向导某项是否已标记为完成（P2-6）。 */
    fun isCompatDone(key: String): Boolean =
        prefs.getStringSet(KEY_COMPAT_DONE, emptySet())?.contains(key) == true

    /**
     * 穿透背景：主界面展示系统桌面壁纸（而非纯色/渐变背景）。
     * 默认 false（沿用当前不透明背景）。
     */
    private val _wallpaperBehind = MutableStateFlow(prefs.getBoolean(KEY_WALLPAPER_BEHIND, false))
    val wallpaperBehindFlow: StateFlow<Boolean> = _wallpaperBehind.asStateFlow()
    var wallpaperBehind: Boolean
        get() = _wallpaperBehind.value
        set(value) {
            _wallpaperBehind.value = value
            prefs.edit { putBoolean(KEY_WALLPAPER_BEHIND, value) }
        }

    /**
     * 挖孔屏适配（横屏时内容延伸到摄像头区域）。
     * 开启（默认）：LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS，内容充满全屏；
     * 关闭：回退系统默认安全区约束（LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT）。
     */
    private val _cutoutAdapt = MutableStateFlow(prefs.getBoolean(KEY_CUTOUT_ADAPT, true))
    val cutoutAdaptFlow: StateFlow<Boolean> = _cutoutAdapt.asStateFlow()
    var cutoutAdapt: Boolean
        get() = _cutoutAdapt.value
        set(value) {
            _cutoutAdapt.value = value
            prefs.edit { putBoolean(KEY_CUTOUT_ADAPT, value) }
        }

    /**
     * 显示系统应用：开启后主列表额外包含系统应用（设置、系统服务等 FLAG_SYSTEM 应用）。
     * 默认关闭（仅展示用户可启动的应用），避免系统组件淹没列表。
     */
    private val _showSystemApps = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SYSTEM, false))
    val showSystemAppsFlow: StateFlow<Boolean> = _showSystemApps.asStateFlow()
    var showSystemApps: Boolean
        get() = _showSystemApps.value
        set(value) {
            _showSystemApps.value = value
            prefs.edit { putBoolean(KEY_SHOW_SYSTEM, value) }
        }


    /**
     * 动态氛围背景（缓慢漂移的柔光渐变）。默认开启。
     * 关闭后主界面退化为纯静态垂直渐变，省电且对晕动症更友好。
     */
    private val _dynamicBackground = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_BG, true))
    val dynamicBackgroundFlow: StateFlow<Boolean> = _dynamicBackground.asStateFlow()
    var dynamicBackground: Boolean
        get() = _dynamicBackground.value
        set(value) {
            _dynamicBackground.value = value
            prefs.edit { putBoolean(KEY_DYNAMIC_BG, value) }
        }

    /**
     * 玻璃拟态（磨砂玻璃景深）：详情面板/状态栏的模糊光晕与高光描边。
     * 默认开启；低端设备可关闭以换取性能。
     */
    private val _glassSurface = MutableStateFlow(prefs.getBoolean(KEY_GLASS, true))
    val glassSurfaceFlow: StateFlow<Boolean> = _glassSurface.asStateFlow()
    var glassSurface: Boolean
        get() = _glassSurface.value
        set(value) {
            _glassSurface.value = value
            prefs.edit { putBoolean(KEY_GLASS, value) }
        }

    /**
     * 减少动态效果：冻结所有无限循环动画（呼吸光晕 / 漂移背景等），
     * 仅保留必要的过渡。晕动症与低性能设备友好。默认关闭（动态效果全开）。
     */
    private val _reduceMotion = MutableStateFlow(prefs.getBoolean(KEY_REDUCE_MOTION, false))
    val reduceMotionFlow: StateFlow<Boolean> = _reduceMotion.asStateFlow()
    var reduceMotion: Boolean
        get() = _reduceMotion.value
        set(value) {
            _reduceMotion.value = value
            prefs.edit { putBoolean(KEY_REDUCE_MOTION, value) }
        }

    /**
     * 启动游戏/应用后不保留 Reverie 进程：开启后，从主页启动任意应用成功即销毁
     * Reverie 任务（finishAndRemoveTask），不在后台保留，避免占用内存/最近任务。
     * 默认关闭（保留进程，返回即恢复上次页面——配合 lastMainTab 冷启动恢复）。
     */
    private val _killAfterLaunch = MutableStateFlow(prefs.getBoolean(KEY_KILL_AFTER_LAUNCH, false))
    val killAfterLaunchFlow: StateFlow<Boolean> = _killAfterLaunch.asStateFlow()
    var killAfterLaunch: Boolean
        get() = _killAfterLaunch.value
        set(value) {
            _killAfterLaunch.value = value
            prefs.edit { putBoolean(KEY_KILL_AFTER_LAUNCH, value) }
        }

    /**
     * 实际生效的「减少动态效果」：直接返回 [reduceMotion]（节能模式已移除）。
     */
    val effectiveReduceMotion: Boolean get() = _reduceMotion.value

    fun setCompatDone(key: String, done: Boolean) {
        val set = prefs.getStringSet(KEY_COMPAT_DONE, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (done) set.add(key) else set.remove(key)
        prefs.edit { putStringSet(KEY_COMPAT_DONE, set) }
    }

    /**
     * 重置所有设置项到默认值（R11-10）。
     * 重建各 StateFlow 初始值并清空 prefs（保留 lastFocused 与 compat 完成态体验）。
     */
    fun resetAll() {
        prefs.edit {
            putInt(KEY_DARK_MODE, DarkMode.SYSTEM.ordinal)
            putBoolean(KEY_SHOW_BADGES, true)
            putString(KEY_HAPTIC_INTENSITY, HapticIntensity.WEAK.name)
            putBoolean(KEY_USE_MONET, true)
            putString(KEY_ORIENTATION_MODE, defaultOrientationMode.value)
            putBoolean(KEY_WALLPAPER_BEHIND, false)
            putBoolean(KEY_CUTOUT_ADAPT, true)
            putBoolean(KEY_SHOW_SYSTEM, false)
            putBoolean(KEY_DYNAMIC_BG, true)
            putBoolean(KEY_GLASS, true)
            putBoolean(KEY_REDUCE_MOTION, false)
            putBoolean(KEY_KILL_AFTER_LAUNCH, false)
        }
        _darkMode.value = DarkMode.SYSTEM
        _showBadges.value = true
        _hapticIntensityFlow.value = HapticIntensity.WEAK
        _useMonet.value = true
        _orientationMode.value = defaultOrientationMode
        _wallpaperBehind.value = false
        _cutoutAdapt.value = true
        _dynamicBackground.value = true
        _glassSurface.value = true
        _reduceMotion.value = false
        _killAfterLaunch.value = false
    }

    /**
     * 游戏页 banner 选择记忆：默认 hero+logo（false），用户点击切到 header（true）后按包名长期保存。
     * library hero+logo 的 logo 匹配机制偶尔会出问题，故开放给用户自行选择更顺眼的版式，
     * 退出子页面 / 切换游戏后再回来仍保持上次选择。
     */
    fun isBannerHeader(pkg: String): Boolean =
        prefs.getStringSet(KEY_BANNER_HEADER_PKGS, emptySet())?.contains(pkg) == true

    fun setBannerHeader(pkg: String, header: Boolean) {
        val set = prefs.getStringSet(KEY_BANNER_HEADER_PKGS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (header) set.add(pkg) else set.remove(pkg)
        prefs.edit { putStringSet(KEY_BANNER_HEADER_PKGS, set) }
    }

    companion object {
        private const val PREFS_NAME = "launcher_settings"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_PERMISSION_RATIONALE = "permission_rationale_shown"
        private const val KEY_LAST_FOCUSED = "last_focused"
        private const val KEY_LAST_MAIN_TAB = "last_main_tab"
        private const val KEY_QUERY_ALL_PACKAGES_WARNED = "query_all_packages_warned"
        private const val KEY_SHOW_BADGES = "show_badges"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_HAPTIC_INTENSITY = "haptic_intensity"
        private const val KEY_COMPAT_DONE = "compat_done"
        private const val KEY_USE_MONET = "use_monet"
        private const val KEY_ORIENTATION_MODE = "orientation_mode"
        private const val KEY_WALLPAPER_BEHIND = "wallpaper_behind"
        private const val KEY_SHOW_SYSTEM = "show_system_apps"
        private const val KEY_CUTOUT_ADAPT = "cutout_adapt"
        private const val KEY_DYNAMIC_BG = "dynamic_background"
        private const val KEY_GLASS = "glass_surface"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_KILL_AFTER_LAUNCH = "kill_after_launch"
        private const val KEY_BANNER_HEADER_PKGS = "banner_header_pkgs"
    }
}

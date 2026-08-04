package cn.mocabolka.run.ui.game

import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.launcher.formatDuration
import cn.mocabolka.run.ui.SettingsRepository
import cn.mocabolka.run.ui.components.BottomFadeMask
import cn.mocabolka.run.ui.components.GamepadBottomHintBar
import cn.mocabolka.run.ui.components.GamepadVisible
import cn.mocabolka.run.ui.components.Hint
import cn.mocabolka.run.ui.components.KeyBadge
import cn.mocabolka.run.ui.components.KeyToken
import cn.mocabolka.run.ui.components.ReverieFilledButton
import cn.mocabolka.run.ui.components.ReverieIconButton
import cn.mocabolka.run.ui.components.ReverieTextButton
import cn.mocabolka.run.ui.components.SubPageBackButton
import cn.mocabolka.run.ui.components.accentOutline
import cn.mocabolka.run.ui.components.statusBarSafeTop
import cn.mocabolka.run.ui.game.GamePageVariant.Companion.fromConstraints
import cn.mocabolka.run.ui.game.steamart.domain.model.LogoPosition
import cn.mocabolka.run.ui.theme.DeviceProfile
import cn.mocabolka.run.ui.theme.Dimens
import cn.mocabolka.run.ui.theme.FavoriteStar
import cn.mocabolka.run.ui.theme.LocalAmoledPureBlack
import cn.mocabolka.run.ui.theme.MotionMachines
import cn.mocabolka.run.ui.theme.SurfaceCard
import cn.mocabolka.run.ui.theme.SurfaceTokens
import cn.mocabolka.run.ui.theme.focusBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * GamePage 响应式布局层（2026-07-15 UI 改进方案）。
 *
 * - [GamePageVariant]：6 档断点（CompactPortrait / MediumPortrait / CompactLandscape /
 *   MediumLandscape / ExpandedLandscape / ExtraLargeLandscape），由 `BoxWithConstraints`
 *   测得的 maxWidth × maxHeight 经 [fromConstraints] 推导。
 * - [GamePageData]：页面全部数据（美术 / 统计 / 元信息 / 时间线），由 [GamePageActivity]
 *   装配后下传，本层不持有状态。
 * - [GamePageBody]：唯一渲染入口，按 variant 选择 PORTRAIT / LAND_2 / LAND_3 三种结构骨架，
 *   子组件（Hero / 启动钮 / 统计卡 / Tab / 时间线 / 关于）在骨架间复用。
 *
 * 焦点：焦点态以扁平索引 [GPSlots] 表达（与 GamePage.kt 内 onKeyEvent 同源），
 * 本层仅按 `focusIndex == GPSlots.X` 绘制焦点框，激活统一回调 [onActivate]。
 */

// ─────────────────────────────────────────────────────────────
// 断点变体
// ─────────────────────────────────────────────────────────────
enum class GamePageVariant {
    CompactPortrait,
    MediumPortrait,
    CompactLandscape,
    MediumLandscape,
    ExpandedLandscape,
    ExtraLargeLandscape;

    val structure: GPStructure
        get() = when (this) {
            CompactPortrait, MediumPortrait -> GPStructure.PORTRAIT
            CompactLandscape, MediumLandscape -> GPStructure.LAND_2
            ExpandedLandscape, ExtraLargeLandscape -> GPStructure.LAND_3
        }

    val isPortrait: Boolean get() = structure == GPStructure.PORTRAIT
    val isXL: Boolean get() = this == ExtraLargeLandscape

    /** 屏幕外边距（方案第七章）。 */
    val screenPadding: Dp get() = Dimens.screenPaddingFor(maxWidth)

    /** Hero 横幅高度。 */
    val heroHeight: Dp
        get() = when (this) {
            CompactPortrait -> Dimens.HeroCompact
            MediumPortrait -> Dimens.HeroMedium
            CompactLandscape -> Dimens.HeroCompactLand
            MediumLandscape -> Dimens.HeroMediumLand
            ExpandedLandscape -> Dimens.HeroExpanded
            ExtraLargeLandscape -> Dimens.HeroXL
        }

    /** Capsule 海报宽度（CompactPortrait = 0，即收起改用顶部图标 + 名称）。 */
    val capsuleWidth: Dp
        get() = when (this) {
            CompactPortrait -> 0.dp
            MediumPortrait -> Dimens.CapsuleMediumPortrait
            CompactLandscape -> Dimens.CapsuleCompactLand
            MediumLandscape -> Dimens.CapsuleMediumLand
            ExpandedLandscape -> Dimens.CapsuleExpanded
            ExtraLargeLandscape -> Dimens.CapsuleXL
        }

    /** 启动按钮高度（方案 §4.1）。 */
    val launchHeight: Dp
        get() = when (this) {
            CompactPortrait, MediumPortrait, CompactLandscape, MediumLandscape -> Dimens.LaunchButtonHeight
            ExpandedLandscape -> Dimens.LaunchButtonHeightExpanded
            ExtraLargeLandscape -> Dimens.LaunchButtonHeightXL
        }

    /** 焦点环线宽（电视加粗，方案 §4.6）。 */
    val focusBorderWidth: Dp get() = if (isXL) Dimens.FocusBorderWidthXL else Dimens.FocusBorderWidthSelected

    /** Tab 是否并回顶部状态栏（Expanded+ 横向 segmented，方案 §4.5）。 */
    val tabInTopBar: Boolean get() = structure == GPStructure.LAND_3

    private val maxWidth: Int
        get() = when (this) {
            CompactPortrait -> 400
            MediumPortrait -> 700
            CompactLandscape -> 700
            MediumLandscape -> 1000
            ExpandedLandscape -> 1400
            ExtraLargeLandscape -> 1800
        }

    companion object {
        /** 由 maxWidth(maxWidthDp) × maxHeight(maxHeightDp) 推导断点（方案第二章）。 */
        fun fromConstraints(maxWidthDp: Int, maxHeightDp: Int): GamePageVariant {
            val landscape = maxWidthDp >= maxHeightDp
            return when {
                !landscape && maxWidthDp < DeviceProfile.PHONE_MAX_DP -> CompactPortrait
                !landscape -> MediumPortrait
                landscape && maxWidthDp < DeviceProfile.EXPANDED_MIN_DP -> CompactLandscape
                landscape && maxWidthDp < 1200 -> MediumLandscape
                landscape && maxWidthDp < DeviceProfile.TV_MIN_DP -> ExpandedLandscape
                else -> ExtraLargeLandscape
            }
        }
    }
}

/** 三种结构骨架。 */
enum class GPStructure { PORTRAIT, LAND_2, LAND_3 }

// ─────────────────────────────────────────────────────────────
// 焦点槽位（扁平索引，与 GamePage.kt onKeyEvent 同源）
// ─────────────────────────────────────────────────────────────
object GPSlots {
    const val BACK = 0
    const val APP_INFO = 1
    const val THEME = 2
    const val FAVORITE = 3
    /** 固定到桌面（游戏快捷方式），位于收藏之后。 */
    const val SHORTCUT = 4
    const val LAUNCH = 5
    const val TAB_DYNAMIC = 6
    const val TAB_STATS = 7
    const val TAB_ABOUT = 8
    const val CONTENT_START = 9
}

/**
 * 当前 Tab 的可聚焦内容项数量（用于焦点索引上界与滚动）。
 * - 动态：截图画廊（1 项，可聚焦：左/右选图、A 打开）+ 个性化 widget 面板（personalizationCount 项）；
 * - 统计：时长趋势概览（可聚焦）+ 日期时间线行（可聚焦）→ daySeries.size + 1；
 * - 关于：4 项。
 */
fun contentItemCount(data: GamePageData, tab: Int, personalizationCount: Int = 0, galleryCount: Int = 0): Int = when (tab) {
    0 -> galleryCount + personalizationCount
    1 -> data.daySeries.size + 1
    2 -> 4
    else -> 0
}

// ─────────────────────────────────────────────────────────────
// 页面数据（由 Activity 装配）
// ─────────────────────────────────────────────────────────────
data class GamePageData(
    val app: AppModel,
    val displayName: String,
    val accent: Color,
    val bg: GameArtImage?,
    val hero: GameArtImage?,
    val logo: GameArtImage?,
    val capsule: GameArtImage?,
    val header: GameArtImage?,
    val totalPlayMs: Long,
    val daySeries: LongArray,
    val installed: Boolean,
    val versionName: String,
    val firstInstallTime: Long,
    val permissions: List<String>,
    val isFavorite: Boolean,
    val reduceMotion: Boolean,
    /** 是否拥有本工程自带美术资源（绝区零 / 无限暖暖等）；拥有时隐藏 app icon + 应用名。 */
    val hasArt: Boolean = false,
    /** 上次游玩时间（ms），用于启动钮「上次游玩」副标题。 */
    val lastUsedMs: Long = 0L,
    /** Steam logo_position（锚定角 + 框尺寸百分比），供 HeroBanner 定位 logo。 */
    val logoPosition: LogoPosition? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GamePageData
        return app.packageName == other.app.packageName &&
            displayName == other.displayName && accent == other.accent &&
            totalPlayMs == other.totalPlayMs && daySeries.contentEquals(other.daySeries) &&
            header == other.header &&
            installed == other.installed && versionName == other.versionName &&
            firstInstallTime == other.firstInstallTime &&
            permissions == other.permissions && isFavorite == other.isFavorite &&
            reduceMotion == other.reduceMotion && hasArt == other.hasArt &&
            lastUsedMs == other.lastUsedMs && logoPosition == other.logoPosition
    }

    override fun hashCode(): Int {
        var result = app.packageName.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + accent.hashCode()
        result = 31 * result + totalPlayMs.hashCode()
        result = 31 * result + (header?.hashCode() ?: 0)
        result = 31 * result + daySeries.contentHashCode()
        result = 31 * result + installed.hashCode()
        result = 31 * result + versionName.hashCode()
        result = 31 * result + firstInstallTime.hashCode()
        result = 31 * result + permissions.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + reduceMotion.hashCode()
        result = 31 * result + hasArt.hashCode()
        result = 31 * result + lastUsedMs.hashCode()
        result = 31 * result + (logoPosition?.hashCode() ?: 0)
        return result
    }
}

// ─────────────────────────────────────────────────────────────
// 解码工具（原 GamePage.kt 私有，提升为布局层共享）
// ─────────────────────────────────────────────────────────────
@Composable
internal fun rememberArtBitmap(source: ArtSource?): ImageBitmap? {
    val context = LocalContext.current
    // 初值同步探测进程内解码缓存（peekArtBitmap，无 IO）：跨页复用同一资源时首帧即显，
    // 消除「先渐变/纯色一帧、再闪入图片」的割裂；未命中才走异步解码补帧。
    var bmp by remember(source) { mutableStateOf(peekArtBitmap(source)) }
    LaunchedEffect(source) {
        if (bmp == null) bmp = if (source != null) decodeArtBitmap(context, source) else null
    }
    return bmp
}

private fun absoluteDate(ms: Long): String {
    if (ms <= 0) return ""
    return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}

/** 相对"距今"描述（启动钮「上次游玩」副标题用）。 */
private fun relativeFromNow(ms: Long): String {
    if (ms <= 0) return "未知"
    val diff = System.currentTimeMillis() - ms
    if (diff < 60_000L) return "刚刚"
    val min = diff / 60_000L
    if (min < 60) return "${min} 分钟前"
    val hr = min / 60
    if (hr < 24) return "${hr} 小时前"
    val day = hr / 24
    if (day < 30) return "${day} 天前"
    val mon = day / 30
    if (mon < 12) return "${mon} 个月前"
    return "${mon / 12} 年前"
}

// ─────────────────────────────────────────────────────────────
// 主入口：GamePageBody
// ─────────────────────────────────────────────────────────────
@Composable
fun GamePageBody(
    data: GamePageData,
    variant: GamePageVariant,
    focusIndex: Int,
    selectedTab: Int,
    gamepadConnected: Boolean,
    gameIndex: Int = 0,
    gameCount: Int = 0,
    onActivate: (Int) -> Unit,
    onSwitchGame: (Int) -> Unit,
    widgetSlots: List<WidgetHostSlot> = emptyList(),
    widgetViews: Map<String, AppWidgetHostView?> = emptyMap(),
    availableProviders: Map<String, List<WidgetProviderItem>> = emptyMap(),
    widgetVersion: Int = 0,
    widgetLoading: Boolean = false,
    onPickWidgetProvider: (String, WidgetProviderItem) -> Unit = { _, _ -> },
    screenshotGallery: GameScreenshots.ScreenshotGalleryState? = null,
    /** 焦点机：截图画廊内选中的截图纸贴序号（左/右在画廊内逐张切换焦点）。 */
    gallerySel: Int = 0,
    /** 当前激活 Tab 内容列表状态上报（供 Activity 右摇杆引擎驱动）。 */
    onActiveListState: (androidx.compose.foundation.lazy.LazyListState) -> Unit = {},
    /** 社区 App 均未安装时是否收折（按游戏记忆）；收折态整行可点展开。 */
    widgetCollapsed: Boolean = false,
    onExpandWidget: () -> Unit = {},
    reduceMotion: Boolean = false
) {
    val pad = variant.screenPadding

    // 滚动折叠：竖屏有美术时 HeroBanner 高度随主体 LazyColumn 滚动量收缩（0.5x~1.5x 无关，仅高度插值）。
    // collapseAnim 在 GamePageBody 作用域声明，但仅在 hero wrapper 内读取，故滚动只重组合 hero 子树，
    // 不触发整页（含 PortraitBody）重组。切换游戏（appPkg 变）自动归零，避免上一款残留折叠态。
    val density = LocalDensity.current
    val collapseMaxPx = with(density) { (variant.heroHeight - 60.dp).toPx() }.coerceAtLeast(1f)
    val scope = rememberCoroutineScope()
    // 捕获主体列表（PortraitBody/LandscapeBody 内激活 Tab 的 LazyListState）用于判定「是否到顶」，
    // 由下方 onActiveListState 透传捕获（同时继续上报外层）。
    var bodyListState by remember { mutableStateOf<LazyListState?>(null) }
    // 折叠态用 Animatable：下滑 snapTo 立即跟手收窄，到顶 animateTo(0f) 直接弹到底（无视力度，spring 收尾微弹）。
    // 切游戏（appPkg 变）通过 remember key 重建 Animatable(0f) 自动归零，避免上一款残留折叠态。
    val collapseAnim = remember(data.app.packageName) { Animatable(0f) }
    // restore 进行中标记：防止 onPreScroll 的 snapTo 打断正在运行的弹回动画（见下方竞争处理）。
    val isRestoring = remember(data.app.packageName) { mutableStateOf(false) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset, source: NestedScrollSource
            ): Offset {
                // 接受 Drag 与 Fling：惯性滚动（fling）期间也要让 banner 跟手收窄，否则松手后折叠态卡住。
                // 恢复动画进行中不干涉，避免 snapTo 取消正在跑的 animateTo(0f) 导致 banner 停在半路。
                val isUserScroll =
                    source == NestedScrollSource.Drag || source == NestedScrollSource.Fling
                if (isUserScroll && available.y < 0 && !isRestoring.value) {
                    // 手指下滑 → snapTo 立即跟手收窄
                    scope.launch {
                        collapseAnim.snapTo(
                            (collapseAnim.value - available.y).coerceIn(0f, collapseMaxPx)
                        )
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset {
                // 主体列表已到顶（首 item 在顶端、无偏移）→ 立即弹回全高，无需再额外上滑一下。
                // 关键：用「列表到顶」判定而非 available.y>0（后者需到顶后再滑一下才有剩余量 → 两下）。
                // 接受 Drag 与 Fling：惯性滚动（fling）回顶也要触发恢复（P0 主因）。
                // 阈值改用 collapseMaxPx*0.05f：展开不足 95% 才触发恢复，全展开时（≈0）本就跳过，
                // 同时避免在轻微浮差（<5%）上每帧重复 launch animateTo。
                // isRestoring 守卫：恢复动画进行中不再重复 launch。
                val atTop = bodyListState?.let {
                    it.firstVisibleItemIndex == 0 && it.firstVisibleItemScrollOffset == 0
                } == true
                val isUserScroll =
                    source == NestedScrollSource.Drag || source == NestedScrollSource.Fling
                if (isUserScroll && atTop && collapseAnim.value > collapseMaxPx * 0.05f && !isRestoring.value) {
                    isRestoring.value = true
                    scope.launch {
                        collapseAnim.animateTo(0f)
                        isRestoring.value = false
                    }
                }
                return Offset.Zero
            }
        }
    }

    val collapseProgress = (collapseAnim.value / collapseMaxPx).coerceIn(0f, 1f)


    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 内容层（左右安全边距在此控制；挖孔/瀑布在 GamePage.kt 根层处理）
        // 竖屏有美术不再全出血：改为顶栏 banner 形式（见下方内容流渲染）。
        // 顶部避让系统状态栏：statusBarsPadding 使 TopBar / 竖屏 banner 紧贴状态栏下方，
        // 不再与状态栏图标重叠（背景层保持全出血铺满，96dp 压暗层保证状态栏可读性）。
        // 顶部避让系统状态栏：用户要求整体上移约 12dp（所有界面统一），
        // 故只保留 (状态栏高 - 12dp)，标题栏更贴近状态栏但仍在状态栏之下。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollConnection)
                .statusBarSafeTop()
                .padding(horizontal = pad)
        ) {
            // 顶部状态栏：返回 / (游戏名) / 应用信息 / 收藏
            TopBar(
            data = data,
            variant = variant,
            focusIndex = focusIndex,
            selectedTab = selectedTab,
            gamepadConnected = gamepadConnected,
            gameIndex = gameIndex,
            gameCount = gameCount,
            onActivate = onActivate,
            onSwitchGame = onSwitchGame
        )

        Spacer(Modifier.height(Dimens.sm))

        // 主体区：竖屏走纵向流（大横幅 + Tab + 内容）；横屏走「左海报栏 + 右内容栏」，
        // 各自撑满高度，不再让全宽大横幅吃掉横屏本就有限的垂直空间。
        if (variant.isPortrait) {
            if (data.hasArt) {
                // 竖屏有美术：顶栏 banner 形式（不再全出血）。hero 作圆角横幅，
                // logo 按 LogoPosition 定位（见 HeroBanner），点击头图切换 header 版。
                // 滚动折叠：随主体 LazyColumn 滚动量收缩高度（仅在 wrapper 内读取 collapsePx → 仅重组合 hero 子树）。
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val bannerW = maxWidth
                    val heroMinH = bannerW / 3.097f   // 3.097:1 最小高度（折叠触底）
                    // 【P- 横幅恒定 3.097:1】固定 heroHeight 在平板/大屏竖屏（MediumPortrait,
                    // bannerW 远大于 heroHeight=220dp）下不满足 3.097:1，启用动态高度：
                    // fullH = max(heroHeight, heroMinH)。Hero 始终按高度铺满（FillHeight），
                    // 容器只在水平方向做左右裁切——不允许上下裁切、不让 hero 去适应超宽部件。
                    // CompactPortrait 因 heroHeight=180dp 已 > heroMinH(~130dp)，coerceAtLeast 透传不动。
                    val fullH = variant.heroHeight.coerceAtLeast(heroMinH)

                    val progress = collapseProgress
                    // 1:1 跟随手指：直接按滚动进度线性插值高度，不做弹簧补间（避免拖影/追手感；
                    // 滑多少缩多少、到顶 animateTo(0f) 直接弹回全高）。
                    val targetH = lerp(fullH, heroMinH, progress)

                    HeroBanner(
                        hero = data.hero,
                        logo = data.logo,
                        displayName = data.displayName,
                        accent = data.accent,
                        height = targetH,
                        variant = variant,
                        logoPosition = data.logoPosition,
                        header = data.header,
                        appPkg = data.app.packageName,
                        reduceMotion = data.reduceMotion
                    )
                }
                // banner 与下方滑块之间保留适度间距（各行不紧贴），
                // 但不再是此前 HeroLogoZone 造成的超大空白。
                Spacer(Modifier.height(Dimens.md))
            }
            if (!variant.tabInTopBar) {
                GamePageTabs(
                    selectedTab = selectedTab,
                    focusIndex = focusIndex,
                    gamepadConnected = gamepadConnected,
                    onActivate = onActivate,
                    accent = data.accent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            PortraitBody(
                modifier = Modifier.weight(1f),
                data = data, variant = variant, focusIndex = focusIndex,
                selectedTab = selectedTab, gamepadConnected = gamepadConnected,
                onActivate = onActivate,
                onActiveListState = { st -> bodyListState = st; onActiveListState(st) },
                widgetSlots = widgetSlots, widgetViews = widgetViews,
                availableProviders = availableProviders, widgetVersion = widgetVersion,
                onPickWidgetProvider = onPickWidgetProvider,
                widgetLoading = widgetLoading,
                screenshotGallery = screenshotGallery,
                widgetCollapsed = widgetCollapsed, onExpandWidget = onExpandWidget
            )
        } else {
            LandscapeBody(
                modifier = Modifier.weight(1f),
                data = data, variant = variant, focusIndex = focusIndex,
                selectedTab = selectedTab, gamepadConnected = gamepadConnected,
                onActivate = onActivate,
                onActiveListState = { st -> bodyListState = st; onActiveListState(st) },
                widgetSlots = widgetSlots, widgetViews = widgetViews,
                availableProviders = availableProviders, widgetVersion = widgetVersion,
                onPickWidgetProvider = onPickWidgetProvider,
                widgetLoading = widgetLoading,
                screenshotGallery = screenshotGallery,
                widgetCollapsed = widgetCollapsed, onExpandWidget = onExpandWidget
            )
        }

        // 底部手柄提示：统一引用 GamepadBottomHintBar 基线（带分割线 + 固定高度，自动响应手柄插拔）。
        // 规则：A 启动 / B 返回为默认键不进底部提示栏；仅展示 X 收藏 / Y 导入主题 独立快捷键。
        GamepadBottomHintBar(Modifier.fillMaxWidth()) {
            Hint(KeyToken.X, "收藏")
            Hint(KeyToken.Y, "导入主题")
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 顶部状态栏
// ─────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    data: GamePageData,
    variant: GamePageVariant,
    focusIndex: Int,
    selectedTab: Int,
    gamepadConnected: Boolean,
    gameIndex: Int = 0,
    gameCount: Int = 0,
    onActivate: (Int) -> Unit,
    onSwitchGame: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回：引用归一化 SubPageBackButton（基线部件）
        SubPageBackButton(
            onClick = { onActivate(GPSlots.BACK) },
            isFocused = focusIndex == GPSlots.BACK,
            onFocus = { onActivate(GPSlots.BACK) },
            contentColor = data.accent
        )

        // 左右切游戏（触控按钮；手柄用 LT / RT），LT/RT 浮块置于两侧（仅手柄连接）
        Row(verticalAlignment = Alignment.CenterVertically) {
            GamepadVisible { KeyBadge(text = "LT") }
            ReverieIconButton(onClick = { onSwitchGame(-1) }, imageVector = Icons.Filled.ChevronLeft, contentDescription = "上一个游戏", tint = data.accent)
            if (gameCount > 1) {
                Text(
                    text = "${gameIndex + 1}/$gameCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = Dimens.xs)
                )
            }
            ReverieIconButton(onClick = { onSwitchGame(1) }, imageVector = Icons.Filled.ChevronRight, contentDescription = "下一个游戏", tint = data.accent)
            GamepadVisible { KeyBadge(text = "RT") }
        }

        // 游戏名（仅 Expanded+ 中部显示，避免窄屏拥挤）
        if (variant.tabInTopBar) {
            Spacer(Modifier.width(Dimens.md))
            Text(
                text = data.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Tab 并入顶栏（主页同款分段滑块 + 两侧 LB/RB 浮块）
            GamePageTabs(
                selectedTab = selectedTab,
                focusIndex = focusIndex,
                gamepadConnected = gamepadConnected,
                onActivate = onActivate,
                accent = data.accent,
                fill = false
            )
            Spacer(Modifier.width(Dimens.md))
        } else {
            Spacer(Modifier.weight(1f))
        }

        // 应用信息 / 游戏设置：两者均跳转系统应用详情页（游戏自有设置入口不存在时回退同一页），
        // 故合并为单个「应用信息」按钮，避免重复入口。
        Box {
            ReverieIconButton(
                onClick = { onActivate(GPSlots.APP_INFO) },
                imageVector = Icons.Filled.Info,
                contentDescription = "应用信息",
                focused = focusIndex == GPSlots.APP_INFO,
                focusColor = data.accent,
                tint = data.accent
            )
        }

        Spacer(Modifier.width(Dimens.xs))

        // 美术主题（SteamDB 提取 / 自定义入口）
        Box {
            ReverieIconButton(
                onClick = { onActivate(GPSlots.THEME) },
                imageVector = Icons.Filled.Palette,
                contentDescription = "美术主题",
                focused = focusIndex == GPSlots.THEME,
                focusColor = data.accent,
                tint = data.accent
            )
        }

        Spacer(Modifier.width(Dimens.xs))

        // 收藏
        Box {
            ReverieIconButton(
                onClick = { onActivate(GPSlots.FAVORITE) },
                imageVector = if (data.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "收藏",
                focused = focusIndex == GPSlots.FAVORITE,
                focusColor = data.accent,
                tint = if (data.isFavorite) FavoriteStar else data.accent
            )
        }

        Spacer(Modifier.width(Dimens.xs))

        // 固定到桌面（游戏快捷方式：点击后先展示游戏页再自动启动）
        Box {
            ReverieIconButton(
                onClick = { onActivate(GPSlots.SHORTCUT) },
                imageVector = Icons.Filled.AddToHomeScreen,
                contentDescription = "固定到桌面",
                focused = focusIndex == GPSlots.SHORTCUT,
                focusColor = data.accent,
                tint = data.accent
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Hero 横幅
// ─────────────────────────────────────────────────────────────
@Composable
private fun HeroBanner(
    hero: GameArtImage?,
    logo: GameArtImage?,
    displayName: String,
    accent: Color,
    height: Dp,
    variant: GamePageVariant,
    reduceMotion: Boolean,
    /** Steam 商店返回的 logo 定位（锚定角 + 框尺寸百分比），空则回退左下角。 */
    logoPosition: LogoPosition? = null,
    /** 点击头图后切换展示的 header 版头图。 */
    header: GameArtImage? = null,
    /** 当前游戏包名：用于按游戏记忆 banner 选择（hero+logo 默认 / header 持久）。 */
    appPkg: String = "",
    modifier: Modifier = Modifier,
    clip: Boolean = true
) {
    // 按游戏记忆：默认 hero+logo（false），用户切到 header 后长期保存；
    // 切游戏时 appPkg 变化 → 重新读取该游戏的已保存选择。
    val context = LocalContext.current
    val bannerSettings = remember { SettingsRepository(context) }
            var showHeader by remember(appPkg) {
                mutableStateOf(appPkg.isNotBlank() && bannerSettings.isBannerHeader(appPkg))
            }
            // 响应式：跨游戏切回 / 外部变更（如设置项、对话返回）即时同步版式，无需重入 HeroBanner。
            val bannerSet by bannerSettings.bannerHeaderFlow.collectAsState()
            LaunchedEffect(appPkg, bannerSet) {
                showHeader = appPkg.isNotBlank() && bannerSet.contains(appPkg)
            }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(if (clip) Modifier.clip(RoundedCornerShape(Dimens.RadiusXl)) else Modifier)
            .clickable(enabled = header != null) {
                val next = !showHeader
                showHeader = next
                if (appPkg.isNotBlank()) bannerSettings.setBannerHeader(appPkg, next)
            }
    ) {
        val bannerW = maxWidth
        // 真实 hero 源尺寸（优先下载到的位图，回退 Steam 库标配 3840×1240）
        val heroBmp = rememberArtBitmap(hero?.source)
        val heroSrcW = heroBmp?.width?.toFloat() ?: HERO_CANVAS_W
        val heroSrcH = heroBmp?.height?.toFloat() ?: HERO_CANVAS_H
        val srcRatio = (heroSrcW / heroSrcH).coerceAtLeast(1f)

        // 超大屏（XL）与所有 LAND_3（平板/大屏双栏）：一律按源图原始比例 3.097:1 展示，
        // 整图高度铺满 banner、取消上下裁切（与 hero 图 FillHeight 一致，无左右空白、无裁切）。
        // 仅竖屏等其余结构沿用调用方传入的固定高度。
        val originalRatioH = bannerW / srcRatio
        val useOriginalRatioHero = variant.isXL || variant.structure == GPStructure.LAND_3
        val resolvedH = if (useOriginalRatioHero) originalRatioH else height

        // header 真实比例约 460×215（≈2.14:1），按当前宽度换算目标高度，
        // 完整保留其原比例、取消直接裁切
        val headerRatio = 460f / 215f
        val headerTargetH = (bannerW / headerRatio).coerceAtMost(resolvedH)

        // 容器高度在「hero 高度」与「header 比例高度」之间插值，实现比例平滑适配（引用全局动画机）
        val targetH by animateDpAsState(
            targetValue = if (showHeader) headerTargetH else resolvedH,
            animationSpec = MotionMachines.heroHeaderResize(reduceMotion),
            label = "heroHeaderHeight"
        )

        // banner 框架：填满父容器、不对「框本身」产生 margin（避免 banner 外圈被挤出一段空白）。
        Box(
            Modifier
                .fillMaxWidth()
                .height(targetH)
        ) {
            // ── 底层 hero：铺满整个 banner 边缘到边缘（不让 16/12dp 安全边距影响 hero 展示） ──
            Box(Modifier.fillMaxSize()) {
                if (heroBmp != null) {
                    // 永远以「高度」为基准缩放：整图高度始终铺满 banner 框、绝不做上下裁切；
                    // 宽度随比例延展，仅在框比图窄时左右裁切、框比图宽时左右留白（由 Alignment 居中）。
                    Image(bitmap = heroBmp, contentDescription = null, contentScale = ContentScale.FillHeight, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.5f)))
                }
            }

            // ── logo 层：仅 logo 享受 16/12dp 安全边距（不让 logo 贴到 banner 圆角边）──
            // logo 坐标系统一使用「扣掉 16/12dp 后的内容区」(bannerW-2*padH, resolvedH-2*padV)，
            // 与 hero 的 1:1 百分比定位不变——hero 铺满，logo 在 hero 上方的「安全内容区」内 9 点定位。
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(horizontal = BANNER_INNER_PAD_H, vertical = BANNER_INNER_PAD_V)) {
                    val logoBmp = rememberArtBitmap(logo?.source)
                    if (logoBmp != null) {
                        // 【Logo 贴边】：按每游戏 logoCrop 开关裁掉透明边距；默认关（原行为不变）
                        val drawnBmp = remember(logoBmp, logoPosition?.logoCrop) {
                            if (logoPosition?.logoCrop == true) cropTransparentEdges(logoBmp) else logoBmp
                        }
                        val density: Density = LocalDensity.current
                        val contentH: Dp = resolvedH - BANNER_INNER_PAD_V * 2
                        // 【排障期保留】旧算法：宽/高各自独立乘可视区（contentW / contentH），
                        //   竖屏时可视区非 3.097:1 → logo 盒宽高比被拉伸失真，先注释不清理。
                        // val contentW: Dp = bannerW - BANNER_INNER_PAD_H * 2
                        // val bannerWpx: Float = with(density) { contentW.toPx() }
                        // val bannerHpx: Float = with(density) { contentH.toPx() }
                        // val box = computeLogoBox(logoPosition, variant, bannerWpx, bannerHpx, density)
                        // 新算法：以「全展开高度」为唯一基准算 logo 画框（不随 banner 折叠而缩小），
                        // 再由下方 clamp 判断「当前内容区放不下才等比缩小」。
                        val logoRefH: Dp = variant.heroHeight - BANNER_INNER_PAD_V * 2
                        val box = computeLogoBox(logoPosition, logoRefH, density)
                        // 保底缩放：空间够（contentH ≥ logo 画框高度）保持原大小；放不下才等比缩到刚好塞入，
                        // 上滑恢复时 contentH 涨回 → logoScale 自动回到 1f 跟着放大。
                        val logoScaleRaw = if (contentH > 0.dp && box.h > contentH) contentH / box.h else 1f
                        // 果冻效果：logo 缩放套弹簧（中等回弹 + 低刚度），banner 收窄到临界点时 logo 带弹跳缩小，
                        // 恢复全高时回弹放大回原始大小。
                        val logoScale by animateFloatAsState(
                            targetValue = logoScaleRaw,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "logoScaleJelly"
                        )
                        val logoW: Dp = box.w * logoScale
                        val logoH: Dp = box.h * logoScale
                        // 双对齐：部件本身按 box.alignment 在 banner 安全内容区 9 点定位；
                        // 部件内的 logo 图片同样显式传 alignment = box.alignment，使其在 Fit（contain、保持比例）letterbox 时
                        // 贴住同一 9 宫格角，而非默认居中。logoCrop 开启时位图已裁到内容边，等价于填满部件、对齐无感。
                        Image(
                            bitmap = drawnBmp,
                            contentDescription = displayName,
                            alignment = box.alignment,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .align(box.alignment)
                                .size(logoW, logoH)
                        )
                    }
                }
            }

            // ── 上层：header 切换覆盖（crossfade 仅控可见性，始终填满 banner 边缘到边缘；
            //     show=false 不渲染以露出下层 hero+logo）。取消直接裁切，完整保留 header 原比例 ──
            AnimatedContent(
                targetState = showHeader,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { MotionMachines.crossfade() },
                label = "heroHeaderToggle"
            ) { isHeader ->
                if (isHeader && header != null) {
                    val headerBmp = rememberArtBitmap(header.source)
                    if (headerBmp != null) {
                        // header 原图约 2.14:1，缩到 banner 容器（近似 3.097:1）后更宽，
                        // FillWidth 自动裁掉上下多余部分，全展开时容器高度近似 header 原生比例、几乎不裁。
                        Image(bitmap = headerBmp, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.5f)))
                    }
                }
                // isHeader=false：不渲染，露出下层 hero+logo
            }
        }
    }
}

/** logo 在 banner 内的布局框（锚定角 + 宽高）。 */
private data class LogoBox(
    val alignment: Alignment,
    val w: Dp,
    val h: Dp
)

/**
 * 依据 Steam logo_position 计算 logo 框。
 * pinned_position 指定锚定角；width_pct/height_pct 为该角对应框宽/框高占 banner 的百分比
 * （修复 demo 把百分比当成偏移量导致的错位）。
 */
/**
 * 完全忠于 logoPosition.pinned_position：统一走 9 宫格解析器（logoAlignFull），
 * 不屏蔽任何锚点、不静默回退 BottomLeft。
 *  - 3 个 Steam 锚点（BottomLeft/CenterCenter/BottomCenter）与自定义 9 宫格一视同仁；
 *  - 未知写法一律回退 Center（居中），避免「设了 Center 却跑到左下」这类静默错位；
 *  - null（无 logoPosition 数据）才回退 Steam 默认 BottomLeft。
 */
private fun resolveLogoAlign(pinned: String?): Alignment {
    return logoAlignFull(LogoPosition.normPin(pinned ?: "BottomLeft"))
}

/** 完整 9 宫格解析（自定义主题用），大小写/空格/连字符容错（先 normPin 归一）。 */
private fun logoAlignFull(n: String): Alignment {
    val top = n.startsWith("top")
    val bottom = n.startsWith("bottom")
    val left = n.endsWith("left")
    val right = n.endsWith("right")
    return when {
        top && left -> Alignment.TopStart
        top && right -> Alignment.TopEnd
        top -> Alignment.TopCenter
        bottom && left -> Alignment.BottomStart
        bottom && right -> Alignment.BottomEnd
        bottom -> Alignment.BottomCenter
        left -> Alignment.CenterStart
        right -> Alignment.CenterEnd
        else -> Alignment.Center
    }
}

/** 无 SteamDB/API 尺寸 / 无强制尺寸时，logo 矩形部件默认占 hero 内区宽/高的百分比。仍乘 logoScale。 */
private val LOGO_DEFAULT_W_PCT = 50f
private val LOGO_DEFAULT_H_PCT = 60f

/**
 * banner 内部安全边距（2026-07-26 由旧 24dp 统一 HERO_LOGO_PAD 拆分）：
 * 左右 16dp、上下 12dp。作用于 banner 内容区，使 hero/logo/header 整体内缩，
 * 不与 banner 圆角边缘贴边；logo 坐标系统一改用「扣边距后的内容区」以保持 1:1 相对定位。
 */
private val BANNER_INNER_PAD_H = 16.dp
private val BANNER_INNER_PAD_V = 12.dp

/**
 * Steam Library Hero 超宽图基准源尺寸（Steam 库标配 3840×1240）。
 * 实际渲染时优先用真正下载到的 hero 位图尺寸（heroBmp.width/height）做几何映射，
 * 这里仅作回退值。
 */
private const val HERO_CANVAS_W = 3840f
private const val HERO_CANVAS_H = 1240f

/*
 * 旧方案：人工缩放倍率（dynamicScale 0.8~2.0）与设备目标占比兜底。
 * 2026-07-24 晚重构后已弃用——改为“真实百分比 × 超宽图实际尺寸 × Hero 显示缩放(纯几何)”直接映射，
 * 不再有任何放大倍率（=1.0 倍展示）。保留以备回退参考：
 *
 * private const val HERO_CANVAS_ASPECT = HERO_CANVAS_W / HERO_CANVAS_H
 * private const val BASELINE_SCALE = 1.0f
 * private const val MIN_SCALE = 0.8f
 * private const val MAX_SCALE = 1.5f
 * private const val TARGET_RATIO_PHONE = 0.75f
 * private const val TARGET_RATIO_TABLET = 0.60f
 * private const val TARGET_RATIO_TV = 0.50f
 * private fun targetLogoRatio(variant: GamePageVariant): Float = when {
 *     variant.isXL -> TARGET_RATIO_TV
 *     variant.structure == GPStructure.LAND_3 || variant == GamePageVariant.MediumLandscape -> TARGET_RATIO_TABLET
 *     else -> TARGET_RATIO_PHONE
 * }
 */

/**
 * 【Logo 贴边】裁剪：裁掉位图四周完全透明（alpha<=阈值）的边距，让真实内容顶满其盒子边缘。
 * 纯几何裁剪（不缩放）；对无透明边 / 全透明 / 解码失败的情况安全返回原图。
 * 注意几何后果：裁剪后 ContentScale.Fit 把真实内容铺满盒子 → 可见 logo 变大，同时贴住锚定边缘（消除悬浮透明边距）。
 */
private fun cropTransparentEdges(src: ImageBitmap, threshold: Int = 0): ImageBitmap {
    val w = src.width
    val h = src.height
    if (w == 0 || h == 0) return src
    val bmp = runCatching { src.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return src
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) {
        val rowBase = y * w
        for (x in 0 until w) {
            val a = (pixels[rowBase + x] ushr 24) and 0xFF
            if (a > threshold) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < minX || maxY < minY) return src // 全透明
    val cw = maxX - minX + 1
    val ch = maxY - minY + 1
    if (cw >= w && ch >= h) return src // 无透明边，无需裁剪
    return runCatching { Bitmap.createBitmap(bmp, minX, minY, cw, ch).asImageBitmap() }.getOrNull() ?: src
}

/**
 * 由 logoPosition 计算 logo 在横幅内的矩形盒子（对齐 + 尺寸）。
 * 移植 Steam 客户端 Library Hero + Logo 双层叠加规范：
 *  - 宽高百分比【直接乘原图全尺寸 bannerWpx/bannerHpx（严格 3.097:1）】算出盒宽/盒高（宽高百分比都用上）；
 *    SteamDB 的 width_pct/height_pct 本就是相对原图全尺寸的比例，logo 也按「去 padding 的全图容器」锚定角对齐，
 *    才能 1:1 复刻 Steam 客户端里 logo 的真实位置。不再扣减 HERO_LOGO_PAD 内边距（2026-07-26 按用户要求改回全图乘积）。
 *  - 不做任何体量归一化：原 autoScaleLogo(tanh 压向 38%) 已移除——SteamDB/API/WebView 提取的 logo
 *    严格按 SteamDB 真实百分比 × 原图全尺寸 × logoScale 显示（与 Steam 客户端一致、与自定义主题一致），
 *    避免大 logo 被压成「特别小」；旧 dynamicScale 0.8~2.0 人工倍率亦禁用；
 *  - 盒定位严格按 pinned_position 角（align），保证可见、不被裁出；
 *  - 自定义主题无强制尺寸 / 无尺寸兜底：默认 50%×60%，仍乘 logoScale 缩放档位；
 */
private fun computeLogoBox(
    pos: LogoPosition?,
    contentH: Dp,
    @Suppress("UNUSED_PARAMETER") density: Density
): LogoBox {
    val align = resolveLogoAlign(pos?.pinnedPosition)

    // 基准框：以内容区高度为唯一基准，按原图比例 3.097:1 反推完整基准框（与 Steam 原图比例一致）。
    // 竖屏时可视区并非 3.097:1，但 logo 的 width_pct/height_pct 本就是相对「原图全尺寸」的比例，
    // 因此必须用同一比例的基准框反推，才能保持 logo 自身的宽高比不被拉伸失真。
    val referenceW = contentH * 3.097f
    val referenceH = contentH

    val wPct = if (pos != null && pos.hasSize) pos.widthPct / 100f else 0.5f
    val hPct = if (pos != null && pos.hasSize) pos.heightPct / 100f else 0.5f
    val scale = pos?.logoScale ?: 1f

    val boxW = referenceW * wPct * scale
    val boxH = referenceH * hPct * scale

    // 不做上限 clamp——允许 logo 盒子超出可视区，超出部分由 pinnedPosition 对齐 + clip 裁剪处理。
    val w = boxW.coerceAtLeast(48.dp)
    val h = boxH.coerceAtLeast(32.dp)

    return LogoBox(align, w, h)
}

/*
 * 【排障期保留·勿删】旧 computeLogoBox：宽/高各自独立乘可视区像素（bannerWpx/bannerHpx），
 * 竖屏可视区非 3.097:1 时会把 logo 盒的宽高比拉伸失真。新实现以内容区高度为唯一基准反推。
 *
private fun computeLogoBox(
    pos: LogoPosition?,
    variant: GamePageVariant,
    bannerWpx: Float,
    bannerHpx: Float,
    density: Density
): LogoBox {
    val align = resolveLogoAlign(pos?.pinnedPosition)

    val rawW = pos?.widthPct ?: 22.665f
    val rawH = pos?.heightPct ?: 45.139f

    val (boxWpx, boxHpx) = when {
        pos != null && pos.hasSize ->
            ((rawW / 100f) * bannerWpx * pos.logoScale) to
                ((rawH / 100f) * bannerHpx * pos.logoScale)
        pos != null ->
            (LOGO_DEFAULT_W_PCT / 100f * bannerWpx * pos.logoScale) to
                (LOGO_DEFAULT_H_PCT / 100f * bannerHpx * pos.logoScale)
        else ->
            (LOGO_DEFAULT_W_PCT / 100f * bannerWpx) to (LOGO_DEFAULT_H_PCT / 100f * bannerHpx)
    }

    val minWPx = density.run { 48.dp.toPx() }
    val minHPx = density.run { 32.dp.toPx() }
    val wPx = boxWpx.coerceAtMost(bannerWpx).coerceAtLeast(minWPx)
    val hPx = boxHpx.coerceAtMost(bannerHpx).coerceAtLeast(minHPx)
    return LogoBox(
        align,
        density.run { wPx.toDp() },
        density.run { hPx.toDp() }
    )
}
*/

// ─────────────────────────────────────────────────────────────
// Tab 滑块（动态 / 统计 / 关于）：视觉对齐主活动 ReverieSegmentedRow
//（SurfaceTokens.segmentContainer 容器 + MD3 primaryContainer 选中），
// 并在滑块两侧放置 LB / RB 手柄键位浮块（仅手柄连接时显示），
// 与统计页周期滑块一致。手柄焦点态沿用 GamePage 专属 accent 焦点框
//（区别于「选中」态的 primaryContainer，二者不冲突）。
// ─────────────────────────────────────────────────────────────
@Composable
private fun GamePageTabs(
    selectedTab: Int,
    focusIndex: Int,
    gamepadConnected: Boolean,
    onActivate: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    /** true=滑块填充剩余宽度（竖屏/横屏正文顶部）；false=固定宽度（顶栏内嵌）。 */
    fill: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
    ) {
        // 滑块左侧：LB 键位浮块（仅手柄连接）
        GamepadVisible { KeyBadge(text = "LB") }
        Row(
            modifier = (if (fill) Modifier.weight(1f) else Modifier.width(184.dp))
                .clip(MaterialTheme.shapes.medium)
                // 取色隔离：滑块轨道用游戏 accent 的淡化底，不沿用主界面 segmentContainer 的 surfaceVariant 主界面取色
                .background(GamePageColors.tonal(accent, 0.12f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GP_TABS.forEachIndexed { i, label ->
                val slot = tabSlot(i)
                val selected = selectedTab == i
                val focused = focusIndex == slot
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (selected) GamePageColors.tonal(accent, 0.22f)
                        else if (focused) GamePageColors.tonal(accent, 0.10f)
                        else Color.Transparent
                    )
                        .focusBorder(focused, accent, MaterialTheme.shapes.medium)
                        .clickable { onActivate(slot) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        // 选中文本：随主题深浅联动（MaterialTheme 在上层已按 lc 强制 onSurface=White），
                        // 避免浅色 accent.onAccent→Black 在深色模式下黑底难读；pill 背景（accent 取色）已足够标识选中。
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // 滑块右侧：RB 键位浮块（仅手柄连接）
        GamepadVisible { KeyBadge(text = "RB") }
    }
}

/** Tab 索引 → 焦点槽位（3 档：动态 / 统计 / 关于）。 */
private fun tabSlot(i: Int): Int = when (i) {
    0 -> GPSlots.TAB_DYNAMIC
    1 -> GPSlots.TAB_STATS
    else -> GPSlots.TAB_ABOUT
}

private val GP_TABS = listOf("动态", "统计", "关于")

// ─────────────────────────────────────────────────────────────
// 结构骨架：竖屏（Compact / Medium Portrait）
// ─────────────────────────────────────────────────────────────
@Composable
private fun PortraitBody(
    modifier: Modifier = Modifier,
    data: GamePageData,
    variant: GamePageVariant,
    focusIndex: Int,
    selectedTab: Int,
    gamepadConnected: Boolean,
    onActivate: (Int) -> Unit,
    onActiveListState: (androidx.compose.foundation.lazy.LazyListState) -> Unit = {},
    widgetSlots: List<WidgetHostSlot> = emptyList(),
    widgetViews: Map<String, AppWidgetHostView?> = emptyMap(),
    availableProviders: Map<String, List<WidgetProviderItem>> = emptyMap(),
    widgetVersion: Int = 0,
    widgetLoading: Boolean = false,
    onPickWidgetProvider: (String, WidgetProviderItem) -> Unit = { _, _ -> },
    screenshotGallery: GameScreenshots.ScreenshotGalleryState? = null,
    gallerySel: Int = 0,
    widgetCollapsed: Boolean = false,
    onExpandWidget: () -> Unit = {},

) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部图标 + 名称（替代竖屏崩塌的 capsule）。
        // 自带美术资源的游戏（绝区零 / 无限暖暖等）由 Hero 横幅承载视觉，不再显示 app icon + 应用名。
        if (!data.hasArt) {
            Spacer(Modifier.height(Dimens.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                val capBmp = rememberArtBitmap(data.capsule?.source)
                if (variant.capsuleWidth > 0.dp && capBmp != null) {
                    Image(
                        bitmap = capBmp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(variant.capsuleWidth)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(Dimens.RadiusLg))
                            .accentOutline(GamePageColors.tonal(data.accent, 0.30f), RoundedCornerShape(Dimens.RadiusLg))
                    )
                } else {
                    // 无美术海报时改用本机应用 icon（替代原文字头像）
                    AppIconImage(
                        icon = data.app.icon,
                        accent = data.accent,
                        name = data.displayName,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Dimens.RadiusLg))
                            .accentOutline(GamePageColors.tonal(data.accent, 0.30f), RoundedCornerShape(Dimens.RadiusLg))
                    )
                }
                Text(
                    text = data.displayName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(Dimens.md))

        // 启动按钮（全宽）
        LaunchButton(
            focused = focusIndex == GPSlots.LAUNCH,
            accent = data.accent,
            text = launchLabel(data),
            height = variant.launchHeight,
            isXL = variant.isXL,
            gamepadConnected = gamepadConnected,
            lastUsedMs = data.lastUsedMs,
            onClick = { onActivate(GPSlots.LAUNCH) }
        )

        // 竖屏移除常驻「版本 / 总游玩」元信息行：统计页已有总游玩、关于页已有版本，避免重复。
        Spacer(Modifier.height(Dimens.md))

        // Tab 内容（可滚动）：切换 Tab 用 AnimatedContent 横向滑动过渡（与主页/统计一致）
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.fillMaxWidth().weight(1f),
            // 板块切换统一走「动画机列表」panelSwitch（主页基准），与主页/统计一致。
            transitionSpec = {
                MotionMachines.panelSwitch(
                    forward = MotionMachines.linearForward(initialState, targetState),
                    reduceMotion = data.reduceMotion
                )
            },
            contentKey = { it }
        ) { tab ->
            TabContent(
                data = data,
                variant = variant,
                selectedTab = tab,
                focusIndex = focusIndex,
                gamepadConnected = gamepadConnected,
                onActivate = onActivate,
                onActiveListState = onActiveListState,
                isActive = tab == selectedTab,
                gameKey = data.app.packageName,
                modifier = Modifier.fillMaxWidth().weight(1f),
                widgetSlots = widgetSlots, widgetViews = widgetViews,
                availableProviders = availableProviders, widgetVersion = widgetVersion,
                onPickWidgetProvider = onPickWidgetProvider,
                widgetLoading = widgetLoading,
                screenshotGallery = screenshotGallery,
                gallerySel = gallerySel
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 结构骨架：横屏统一布局（左海报栏 + 右内容栏，撑满高度）
//
// 横屏为本产品主形态。此前的「全宽大横幅 + 下方两栏」在横屏矮视口下会被大横幅吃光高度、
// 内容被挤扁溢出。现统一为 Steam 大屏式：
//  - 左栏 [GameLeftRail]：海报（高度自适应封顶，永不溢出）+ 标题 + 启动按钮 + 元信息，可纵向滚动兜底；
//  - 右栏：LAND_2 顶部 Tab 行 + 内容；LAND_3（Expanded/XL 大屏）顶部一条紧凑 Hero + 内容（Tab 已并入顶栏）。
// 两栏均 fillMaxHeight，充分利用横向空间。
// ─────────────────────────────────────────────────────────────
@Composable
private fun LandscapeBody(
    modifier: Modifier = Modifier,
    data: GamePageData,
    variant: GamePageVariant,
    focusIndex: Int,
    selectedTab: Int,
    gamepadConnected: Boolean,
    onActivate: (Int) -> Unit,
    onActiveListState: (androidx.compose.foundation.lazy.LazyListState) -> Unit = {},
    widgetSlots: List<WidgetHostSlot> = emptyList(),
    widgetViews: Map<String, AppWidgetHostView?> = emptyMap(),
    availableProviders: Map<String, List<WidgetProviderItem>> = emptyMap(),
    widgetVersion: Int = 0,
    widgetLoading: Boolean = false,
    onPickWidgetProvider: (String, WidgetProviderItem) -> Unit = { _, _ -> },
    screenshotGallery: GameScreenshots.ScreenshotGalleryState? = null,
    gallerySel: Int = 0,
    widgetCollapsed: Boolean = false,
    onExpandWidget: () -> Unit = {},

) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.lg)
    ) {
        // 左海报栏（固定宽，撑满高度）
        GameLeftRail(
            modifier = Modifier.width(variant.capsuleWidth).fillMaxHeight(),
            data = data,
            variant = variant,
            focusIndex = focusIndex,
            gamepadConnected = gamepadConnected,
            onActivate = onActivate
        )

        // 右内容栏（占据剩余宽度，撑满高度）
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            // 大屏（LAND_3）：顶部一条高度封顶的 Hero，展示美术与 logo，且不吞内容高度。
            // 无美术资源（hasArt=false）时隐藏 banner 部件、不空置占位（与竖屏分支一致）；
            // 此时右内容栏顶部直接接 Tab/内容，避免一条空横幅占位。
            if (variant.structure == GPStructure.LAND_3 && data.hasArt) {
                HeroBanner(
                    hero = data.hero,
                    logo = data.logo,
                    displayName = data.displayName,
                    accent = data.accent,
                    height = if (variant.isXL) 240.dp else 200.dp,
                    variant = variant,
                    logoPosition = data.logoPosition,
                    header = data.header,
                    appPkg = data.app.packageName,
                    reduceMotion = data.reduceMotion
                )
            }
            // 中小横屏（LAND_2）：Tab 行置于内容顶部（大屏 Tab 已并入顶栏）
            if (!variant.tabInTopBar) {
                GamePageTabs(
                    selectedTab = selectedTab,
                    focusIndex = focusIndex,
                    gamepadConnected = gamepadConnected,
                    onActivate = onActivate,
                    accent = data.accent,
                    modifier = Modifier.fillMaxWidth()
                )
            }


            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // 板块切换统一走「动画机列表」panelSwitch（主页基准），横屏补上 reduceMotion 守卫。
                transitionSpec = {
                    MotionMachines.panelSwitch(
                        forward = MotionMachines.linearForward(initialState, targetState),
                        reduceMotion = data.reduceMotion
                    )
                },
                contentKey = { it }
            ) { tab ->
                TabContent(
                    data = data,
                    variant = variant,
                    selectedTab = tab,
                    focusIndex = focusIndex,
                    gamepadConnected = gamepadConnected,
                    onActivate = onActivate,
                    onActiveListState = onActiveListState,
                    isActive = tab == selectedTab,
                    gameKey = data.app.packageName,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    widgetSlots = widgetSlots, widgetViews = widgetViews,
                    availableProviders = availableProviders, widgetVersion = widgetVersion,
                    onPickWidgetProvider = onPickWidgetProvider,
                    widgetLoading = widgetLoading,
                    screenshotGallery = screenshotGallery,
                    gallerySel = gallerySel,
                    widgetCollapsed = widgetCollapsed, onExpandWidget = onExpandWidget
                )
            }
        }
    }
}

/**
 * 横屏左海报栏：有美术 = 放大竖版海报(主体) + 启动钮；无美术 = 图标(居中) + 应用名 + 启动钮。
 * 海报优先撑满栏宽（2:3），高度封顶可用高度 62%，高度受限时按比例回缩宽度，永不畸变/溢出；
 * 整栏不做 verticalScroll（可滚动容器会抢占真实焦点吞掉 DPAD，致手柄焦点失效）。左栏保持极简：应用名并入 TopBar / 关于页，上次游玩由启动钮副标题承载。
 */
@Composable
private fun GameLeftRail(
    modifier: Modifier = Modifier,
    data: GamePageData,
    variant: GamePageVariant,
    focusIndex: Int,
    gamepadConnected: Boolean,
    onActivate: (Int) -> Unit
) {
    BoxWithConstraints(modifier) {
        val availH = maxHeight
        val availW = maxWidth
        // 海报放大：优先撑满栏宽（2:3 竖版海报），高度封顶可用高度 62%，
        // 高度受限时按比例回缩宽度，始终保持 2:3 不畸变；简化左栏信息后海报成为绝对主角。
        val posterH = (availW * 1.5f).coerceAtMost(availH * 0.62f)
        val posterW = posterH * (2f / 3f)
        val capBmp = rememberArtBitmap(data.capsule?.source)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = Dimens.sm),
            verticalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            // 仅「有美术」绘制海报；无美术横屏移除海报占位符，仅保留「图标 + 游戏名 + 继续游戏」简约布局
            if (data.hasArt) {
                // Hero 卡片：圆角 + accent 描边 + accent 柔光（放大后视觉主体）
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(posterW)
                        .height(posterH)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(Dimens.RadiusLg),
                            ambientColor = GamePageColors.tonal(data.accent, 0.30f),
                            spotColor = GamePageColors.tonal(data.accent, 0.30f)
                        )
                        .clip(RoundedCornerShape(Dimens.RadiusLg))
                        .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Default))
                        .accentOutline(
                            GamePageColors.tonal(data.accent, 0.35f),
                            RoundedCornerShape(Dimens.RadiusLg)
                        )
                ) {
                    if (capBmp != null) {
                        Image(bitmap = capBmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(GamePageColors.tonal(data.accent, 0.4f)), contentAlignment = Alignment.Center) {
                            AppIconImage(
                                icon = data.app.icon,
                                accent = data.accent,
                                name = data.displayName,
                                modifier = Modifier.size(posterW * 0.5f).clip(RoundedCornerShape(Dimens.RadiusLg))
                            )
                        }
                    }
                }
            }

            if (data.hasArt) {
                // 有美术：左栏仅 = 海报 + 启动钮。应用名已并入 TopBar，
                // 总游玩/上次游玩行移除；上次游玩改为启动钮副标题（见 LaunchButton）。
                LaunchButton(
                    focused = focusIndex == GPSlots.LAUNCH,
                    accent = data.accent,
                    text = launchLabel(data),
                    height = variant.launchHeight,
                    isXL = variant.isXL,
                    gamepadConnected = gamepadConnected,
                    lastUsedMs = data.lastUsedMs,
                    onClick = { onActivate(GPSlots.LAUNCH) }
                )
            } else {
                // 无美术：图标水平+垂直居中，应用名(下)，启动钮紧贴(不再置底)。
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AppIconImage(
                        icon = data.app.icon,
                        accent = data.accent,
                        name = data.displayName,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(Dimens.RadiusMd))
                    )
                    Spacer(Modifier.height(Dimens.sm))
                    Text(
                        text = data.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(Dimens.md))
                    LaunchButton(
                        focused = focusIndex == GPSlots.LAUNCH,
                        accent = data.accent,
                        text = launchLabel(data),
                        height = variant.launchHeight,
                        isXL = variant.isXL,
                        gamepadConnected = gamepadConnected,
                        lastUsedMs = data.lastUsedMs,
                        onClick = { onActivate(GPSlots.LAUNCH) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 启动按钮（accent 主色，方案 §4.1）
// ─────────────────────────────────────────────────────────────
@Composable
private fun LaunchButton(
    focused: Boolean,
    accent: Color,
    text: String,
    height: Dp,
    isXL: Boolean,
    gamepadConnected: Boolean,
    onClick: () -> Unit,
    lastUsedMs: Long = 0L
) {
    val onAccent = GamePageColors.onAccent(accent)
    val showSubtitle = lastUsedMs > 0L
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.medium)
        .background(accent)
        .focusBorder(focused, onAccent, MaterialTheme.shapes.medium)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = onAccent)
                Text(
                    text = text,
                    color = onAccent,
                    style = if (isXL) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (showSubtitle) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "上次游玩：${relativeFromNow(lastUsedMs)}",
                    color = onAccent.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/** 启动按钮文案（方案 §4.1）：未安装→安装游戏；已装无记录→启动游戏；有记录→继续游戏。 */
private fun launchLabel(data: GamePageData): String = when {
    !data.installed -> "安装游戏"
    data.totalPlayMs > 0 -> "继续游戏"
    else -> "启动游戏"
}

// ─────────────────────────────────────────────────────────────
// Tab 内容（动态 / 关于）
// ─────────────────────────────────────────────────────────────
@Composable
private fun TabContent(
    data: GamePageData,
    variant: GamePageVariant,
    selectedTab: Int,
    focusIndex: Int,
    gamepadConnected: Boolean,
    onActivate: (Int) -> Unit,
    onActiveListState: (androidx.compose.foundation.lazy.LazyListState) -> Unit = {},
    /** 是否为"激活中"的 Tab（进入态）。仅激活态上报列表状态，避免切换过渡期间旧 Tab 残留上报污染 rightStickListState。 */
    isActive: Boolean = true,
    gameKey: String,
    modifier: Modifier = Modifier,
    widgetSlots: List<WidgetHostSlot> = emptyList(),
    widgetViews: Map<String, AppWidgetHostView?> = emptyMap(),
    availableProviders: Map<String, List<WidgetProviderItem>> = emptyMap(),
    widgetVersion: Int = 0,
    widgetLoading: Boolean = false,
    onPickWidgetProvider: (String, WidgetProviderItem) -> Unit = { _, _ -> },
    screenshotGallery: GameScreenshots.ScreenshotGalleryState? = null,
    /** 焦点机：截图画廊内选中的截图纸贴序号（左/右在画廊内切换）。 */
    gallerySel: Int = 0,
    /** 社区 App 均未安装时是否收折（按游戏记忆）；收折态整行可点展开。 */
    widgetCollapsed: Boolean = false,
    onExpandWidget: () -> Unit = {},

) {
    // 每个 Tab 独立持有列表状态（AnimatedContent 切换时互不打扰右摇杆滚动）。
    val listState = remember { androidx.compose.foundation.lazy.LazyListState() }
    // 上报当前激活列表状态，供 Activity 右摇杆引擎驱动滚动。仅激活态上报，
    // 避免 Tab 切换过渡期间旧（退出）Tab 残留上报覆盖掉新 Tab 的列表状态。
    LaunchedEffect(Unit) { if (isActive) onActiveListState(listState) }
    // 切换游戏后内容滚动复位到顶部，避免残留上一份内容的滚动偏移。
    LaunchedEffect(gameKey) {
        runCatching { listState.scrollToItem(0) }
    }

    // 焦点跟随滚动（与 SubPageScaffold/主页同构）：焦点一旦进入内容区并变化，立即把对应项滚入可视区。
    // 采用无条件 animateScrollToItem（焦点在哪滚到哪），不再用 firstVisibleItemIndex 守卫——
    // 旧守卫在「焦点位于可视区内移动」「或右摇杆 onFocusSync 已把焦点对齐到首可视项」时会抑制滚动，
    // 表现为「左摇杆焦点动了但页面不跟随」。右摇杆滚动回写焦点时本项已位于首可视位，animate 为 0 距离无副作用。
    val contentBase = GPSlots.CONTENT_START
    val focusedContent = if (focusIndex >= contentBase) focusIndex - contentBase else -1
    LaunchedEffect(focusedContent) {
        if (focusedContent >= 0) runCatching { listState.animateScrollToItem(focusedContent) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        if (selectedTab == 0) {
            // 动态：游戏截图画廊（可聚焦：左/右选图、A 打开）+ 个性化 Widget（可聚焦）
            item {
                ScreenshotGallery(
                    gallery = screenshotGallery,
                    variant = variant,
                    accent = data.accent,
                    focused = focusIndex == contentBase,
                    selectedShot = if (selectedTab == 0 && focusIndex == contentBase) gallerySel else -1
                )
            }
            item {
                // Widget 渐显机：targetState 同时纳入 widgetVersion 与 widgetLoading —— 既覆盖切游戏
                // key(widgetVersion) 重建（旧 widget 淡出、新 widget 淡入），也覆盖「加载转圈 → Widget 内容」
                // 这一步，消除转圈骤停后 Widget 突现的硬切；走全局动画机 widgetFade（260ms，带 reduceMotion 收口）。
                AnimatedContent(
                    targetState = widgetVersion to widgetLoading,
                    transitionSpec = { MotionMachines.widgetFade(data.reduceMotion) },
                    contentKey = { it },
                    label = "widgetSwitch"
                ) { (wv, ld) ->
                    PersonalizationWidgetPanel(
                        modifier = Modifier.fillMaxWidth(),
                        slots = widgetSlots,
                        views = widgetViews,
                        availableProviders = availableProviders,
                        widgetVersion = wv,
                        loading = ld,
                        accent = data.accent,
                        onPickWidgetProvider = onPickWidgetProvider,
                        focused = focusIndex == contentBase + 1,
                        onActivate = onActivate,
                        screenPadding = variant.screenPadding,
                        widgetCollapsed = widgetCollapsed,
                        onExpandWidget = onExpandWidget
                    )
                }
            }
        } else if (selectedTab == 1) {
            // 统计：时长趋势（顶） + 日期时间线（逆序：今天最上，可聚焦，焦点索引连续）
            val series = data.daySeries
            if (series.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = Dimens.lg), contentAlignment = Alignment.Center) {
                        Text("暂无游玩数据", style = MaterialTheme.typography.bodyMedium, color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium))
                    }
                }
            } else {
                // 时长趋势概览置于最上（可聚焦，slot = contentBase + 0）
                item {
                    TrendSummary(
                        data = data,
                        variant = variant,
                        focused = focusIndex == contentBase,
                        onActivate = { onActivate(contentBase) }
                    )
                }
                val max = (series.maxOrNull() ?: 0L).coerceAtLeast(1L)
                // 逆序展示：displayOrder[0] = 今天（原末位索引），越远越在下
                val seriesList = series.toList()
                val displayOrder = List(series.size) { series.size - 1 - it }
                itemsIndexed(displayOrder, key = { _, origIndex -> origIndex }) { j, origIndex ->
                    val ms = seriesList[origIndex]
                    val slot = contentBase + 1 + j
                    TimelineRow(
                        index = origIndex,
                        total = series.size,
                        ms = ms,
                        max = max,
                        accent = data.accent,
                        focused = focusIndex == slot,
                        gamepadConnected = gamepadConnected,
                        onActivate = { onActivate(slot) }
                    )
                }
            }
        } else {
            // 关于
            item {
                val slot = contentBase + 0
                AboutRow(Icons.Filled.Info, "版本", data.versionName.ifBlank { "未知" }, accent = data.accent, focused = focusIndex == slot, gamepadConnected = gamepadConnected) { onActivate(slot) }
            }
            item {
                val slot = contentBase + 1
                AboutRow(Icons.Filled.DateRange, "首次安装", if (data.firstInstallTime > 0) absoluteDate(data.firstInstallTime) else "未知", accent = data.accent, focused = focusIndex == slot, gamepadConnected = gamepadConnected) { onActivate(slot) }
            }
            item {
                val slot = contentBase + 2
                val permText = if (data.permissions.isEmpty()) "无" else "共 ${data.permissions.size} 项"
                AboutRow(Icons.Filled.Security, "权限", permText, accent = data.accent, focused = focusIndex == slot, gamepadConnected = gamepadConnected) { onActivate(slot) }
            }
            item {
                val slot = contentBase + 3
                AboutRow(Icons.Filled.Label, "包名", data.app.packageName, accent = data.accent, focused = focusIndex == slot, gamepadConnected = gamepadConnected) { onActivate(slot) }
            }
        }
    }
}

@Composable
private fun TimelineRow(
    index: Int,
    total: Int,
    ms: Long,
    max: Long,
    accent: Color,
    focused: Boolean,
    gamepadConnected: Boolean,
    onActivate: () -> Unit
) {
    val dateLabel = dayLabelFromEnd(index, total)
    val played = ms > 0L
    // 按 MD3 ListItem 三态：游玩态画 accent 细条；未游玩态降透明度、无条形（禁用观感）。
    val frac = if (played) (ms.toFloat() / max).coerceIn(0f, 1f) else 0f
    SurfaceCard(
        modifier = Modifier
            .focusBorder(focused, accent, RoundedCornerShape(Dimens.RadiusLg))
            .clickable { onActivate() },
        level = if (focused) SurfaceTokens.CardLevel.Default else SurfaceTokens.CardLevel.Subtle,
        shape = Dimens.RadiusLg,
        border = false
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.CardInnerPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (played) SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong) else SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (played) formatDuration(ms) else "未游玩",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (played) SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Emphasis) else SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint)
                )
            }
            if (played) {
                Spacer(Modifier.height(Dimens.xs))
                // 整段 track accent 8% + 已播放段 accent 55%，细条 6dp 圆角 3dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(Dimens.RadiusXs))
                        .background(GamePageColors.tonal(accent, 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .height(6.dp)
                            .clip(RoundedCornerShape(Dimens.RadiusXs))
                            .background(GamePageColors.tonal(accent, 0.55f))
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    focused: Boolean,
    gamepadConnected: Boolean,
    onActivate: () -> Unit
) {
    SurfaceCard(
        modifier = Modifier
            .focusBorder(focused, accent, RoundedCornerShape(Dimens.RadiusLg))
            .clickable { onActivate() },
        level = if (focused) SurfaceTokens.CardLevel.Default else SurfaceTokens.CardLevel.Subtle,
        shape = Dimens.RadiusLg,
        border = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.CardInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(Dimens.IconMd))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Strong))
            Spacer(Modifier.weight(1f))
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 统一「收折成一行」入口行：截图区（初始化无哈希后缀 UNSUPPORTED）与
 * Widget 区（社区 App 均未安装）共用同一样式，保证两种收折态视觉一致。
 * 保持按钮形状：SurfaceCard + 全行可点 + 手柄焦点框；左侧图标 + 标题，右侧短提示 + 箭头。
 */
@Composable
internal fun CollapsedSectionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String,
    accent: Color,
    focused: Boolean = false,
    trailing: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.KeyboardArrowRight,
    modifier: Modifier = Modifier,
    /** 整行点击（用于「收折行 → 展开」）。为 null 时整行不可点（由右侧按钮承接操作）。 */
    onClick: (() -> Unit)? = null,
    /** 右侧操作按钮文案；非空时渲染按钮并取代箭头图标（整行不再可点）。 */
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    SurfaceCard(
        level = if (focused) SurfaceTokens.CardLevel.Default else SurfaceTokens.CardLevel.Subtle,
        shape = Dimens.RadiusLg,
        modifier = modifier.focusBorder(focused, accent, RoundedCornerShape(Dimens.RadiusLg))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
                .padding(horizontal = Dimens.CardInnerPadding, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(Dimens.IconMd))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (actionLabel != null && onAction != null) {
                ReverieFilledButton(
                    onClick = onAction,
                    accent = accent,
                    text = actionLabel,
                    focused = focused
                )
            } else {
                Icon(
                    trailing,
                    contentDescription = null,
                    tint = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium),
                    modifier = Modifier.size(Dimens.IconMd)
                )
            }
        }
    }
}

/**
 * 个性化内容面板：支持绑定一个第三方 Widget。
 * - 米游社发行游戏：候选包为 米游社 [com.mihoyo.hyperion] + TapTap [com.taptap]，同时展示两款应用供选择；
 * - 其它游戏：仅 TapTap [com.taptap]。
 * 已绑定：Widget 贴边渲染 + 底部「via 应用名 (左) + 更换按钮 (右)」；
 * 未绑定：「绑定 Widget」按钮（accent），多应用时进入合并选择器。
 * 系统 Widget Picker 为触控对话框，交互触控优先，规避 AndroidView 内嵌焦点冲突。
 */
@Composable
private fun PersonalizationWidgetPanel(
    modifier: Modifier = Modifier,
    slots: List<WidgetHostSlot>,
    views: Map<String, AppWidgetHostView?>,
    availableProviders: Map<String, List<WidgetProviderItem>>,
    widgetVersion: Int,
    accent: Color,
    onPickWidgetProvider: (String, WidgetProviderItem) -> Unit,
    /** 焦点机：当前 Widget 按钮是否获得焦点（扁平 GPSlots）。驱动面板内 Widget 按钮的手柄焦点框。 */
    focused: Boolean = false,
    /** 焦点机激活回调（与 TimelineRow 同构）：A/Enter 触发，统一走 GamePageScreen.activate。 */
    onActivate: (Int) -> Unit = {},
    /** 社区 App 均未安装时是否收折（按游戏记忆）；收折态整行可点展开。 */
    widgetCollapsed: Boolean = false,
    /** 收折态整行点击 → 展开。 */
    onExpandWidget: () -> Unit = {},
    screenPadding: Dp = 0.dp,
    loading: Boolean = false,
) {
    // 进页加载态：Widget 初始化 + 首次刷新未完成前，呈现转圈 Loading，
    // 避免空态/默认态提前暴露（2026-07-26）。
    if (loading) {
        Box(
            modifier = modifier.fillMaxWidth().padding(vertical = Dimens.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                CircularProgressIndicator(
                    color = accent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = "正在加载组件…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    // 已安装候选包（米游社发行游戏可能为 2：mihoyo + taptap；其它仅 taptap）。
    val installedSlots = slots.filter { it.isInstalled }
    if (installedSlots.isEmpty()) {
        // 有候选社区 App 但未安装：收折成一行（与截图区同构，保持一行）。
        // 收折态整行可点展开；展开态左文字提示 + 右「绑定其它 Widget」按钮（点击经 activate → openPicker 兜底）。
        if (slots.isNotEmpty()) {
            if (widgetCollapsed) {
                CollapsedSectionRow(
                    icon = Icons.Filled.Widgets,
                    title = "个性化内容",
                    hint = "未安装 · 点击展开",
                    accent = accent,
                    focused = focused,
                    trailing = Icons.Filled.KeyboardArrowDown,
                    modifier = modifier,
                    onClick = onExpandWidget
                )
            } else {
                CollapsedSectionRow(
                    icon = Icons.Filled.Widgets,
                    title = "个性化内容",
                    hint = "安装对应社区 App 后可在此绑定其 Widget",
                    accent = accent,
                    focused = focused,
                    modifier = modifier,
                    actionLabel = "绑定其它 Widget",
                    onAction = { onActivate(GPSlots.CONTENT_START + 1) }
                )
            }
        }
        return
    }
    // 展示位：优先显示已绑定的 Widget；若未绑定则取首个已安装作为默认预览位
    val displaySlot = installedSlots.firstOrNull { it.isBound } ?: installedSlots.first()

    // 多应用（如米游社 + TapTap）打开合并选择器；单应用 1 provider 时直接跳系统选择器。
    // 注：openPicker / pickerEntries / WidgetPickerSheet 已上移至 GamePageScreen，
    // 由焦点机 activate(CONTENT_START) 统一触发（触控点击经 onActivate 同路），此处仅保留展示与激活入口。
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        if (displaySlot.isBound) {
            val hostView = views[displaySlot.packageName]
            if (hostView != null) {
                val configuration = LocalConfiguration.current
                // 正常宽度：取管理器默认 Widget 宽度（与 host view 创建时一致），
                // 并限制在面板可用宽度内；高度由 Widget 自身比例决定（WRAP_CONTENT），
                // 不再拉伸到全屏、也不再负偏移贴边。
                val maxWidthDp = (configuration.screenWidthDp - 2 * screenPadding.value)
                    .toInt().coerceAtLeast(120)
                val widgetWidthDp = WidgetHostManager.DEFAULT_WIDGET_WIDTH_DP
                    .coerceAtMost(maxWidthDp)
                key(widgetVersion) {
                    Box(
                        modifier = Modifier
                            .width(widgetWidthDp.dp)
                            .wrapContentHeight()
                    ) {
                        AndroidView(
                            factory = { ctx -> FrameLayout(ctx).apply { id = android.R.id.widget_frame } },
                            update = { layout ->
                                layout.removeAllViews()
                                (hostView.parent as? android.view.ViewGroup)?.removeView(hostView)
                                hostView.layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT
                                )
                                layout.addView(hostView)
                                val w = widgetWidthDp.coerceAtLeast(1)
                                hostView.updateAppWidgetSize(null, w, -2, w, -2)
                            },
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        )
                    }
                }
            }
            // 底部：via 居中在上 + 更换按钮居中在下（整体水平居中，仍位于 Widget 下方）
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                Text(
                    text = "via ${displaySlot.appLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
                )
                ReverieFilledButton(
                    onClick = { onActivate(GPSlots.CONTENT_START + 1) },
                    focused = focused,
                    icon = Icons.Filled.Refresh,
                    text = "更换",
                    accent = accent
                )
            }
        } else {
            // 未绑定：多应用（米游社 + TapTap）打开合并选择器；单应用走直接绑/单选
            val anyProviders = installedSlots.any { (availableProviders[it.packageName] ?: emptyList()).isNotEmpty() }
            if (!anyProviders) {
                Text(
                    text = "当前游戏暂无可绑定的 Widget",
                    style = MaterialTheme.typography.labelSmall,
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
                )
            } else {
                ReverieFilledButton(
                    onClick = { onActivate(GPSlots.CONTENT_START + 1) },
                    focused = focused,
                    icon = Icons.Filled.Add,
                    text = "绑定 Widget",
                    accent = accent
                )
            }
        }
    }

}

/** Widget 选择器条目：(候选应用槽位, 该应用下可用 Provider 列表)。 */
internal data class PickerEntry(val slot: WidgetHostSlot, val providers: List<WidgetProviderItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetPickerSheet(
    entries: List<PickerEntry>,
    accent: Color,
    onPickProvider: (WidgetHostSlot, WidgetProviderItem) -> Unit,
    onDismiss: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    // 扁平化所有可选项为可聚焦列表（末尾追加一个关闭按钮槽位），统一走焦点框语言。
    val flatItems = remember(entries) {
        entries.flatMapIndexed { i, e -> e.providers.map { j -> i to j } }
    }
    val closeSlot = flatItems.size
    var focusIdx by remember { mutableStateOf(-1) }
    val cardRequesters = remember(flatItems) { List(flatItems.size) { FocusRequester() } }
    val closeRequester = remember { FocusRequester() }

    fun requestFocusAt(idx: Int) {
        focusIdx = idx
        if (idx == closeSlot) closeRequester.requestFocus() else cardRequesters.getOrNull(idx)?.requestFocus()
    }

    fun onKey(ev: KeyEvent): Boolean {
        val ne = ev.nativeKeyEvent
        if (ne.action != android.view.KeyEvent.ACTION_DOWN) return false
        when (val code = ne.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                requestFocusAt(if (focusIdx < 0) 0 else (focusIdx + 1) % (closeSlot + 1)); return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                requestFocusAt(if (focusIdx < 0) closeSlot else (focusIdx - 1 + (closeSlot + 1)) % (closeSlot + 1)); return true
            }
            // A 选择 / 关闭；Enter 交回各卡片 clickable 处理，避免与 A 重复触发。
            android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_ENTER -> {
                if (focusIdx == closeSlot || focusIdx < 0) onDismiss()
                else {
                    val target = entries.flatMap { e -> e.providers.map { e.slot to it } }[focusIdx]
                    if (target != null) onPickProvider(target.first, target.second)
                }
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_B, android.view.KeyEvent.KEYCODE_BACK -> { onDismiss(); return true }
            else -> return false
        }
    }

    // 右摇杆滚动（独立 window 自管）：ModalBottomSheet 为独立 Dialog 窗口，Activity 右摇杆引擎收不到其 MotionEvent，
    // 故在此自管 —— 捕获 dialog 窗口的 MotionEvent，驱动 LazyColumn 滚动，并将焦点实时跟随到首可视项（焦点跟随滚动）。
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view, lazyListState) {
        // ModalBottomSheet 为独立 Dialog 窗口，Activity 右摇杆引擎收不到其 MotionEvent；
        // 故自管：从内容视图逐级上溯到窗口根视图（DecorView，parent 为 null），在其上挂载右摇杆监听。
        var root: android.view.View? = view
        while (root?.parent is android.view.View) root = root.parent as android.view.View
        val listener = android.view.View.OnGenericMotionListener { _, event ->
            if (event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK) {
                val y = event.getAxisValue(android.view.MotionEvent.AXIS_RZ) + (-event.getAxisValue(android.view.MotionEvent.AXIS_Y))
                if (kotlin.math.abs(y) > 0.15f) {
                    lazyListState.dispatchRawDelta(y * 26f)
                    val first = lazyListState.firstVisibleItemIndex
                    if (first != focusIdx && first in 0 until closeSlot) requestFocusAt(first)
                }
                true // 消费手柄右摇杆轴事件，避免穿透到下层 Activity 引擎
            } else false
        }
        root?.setOnGenericMotionListener(listener)
        onDispose { root?.setOnGenericMotionListener(null) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // skipPartiallyExpanded = false 让抽屉停在半展开状态（把手在屏幕中部、内容超长可滚动），
        // 不再被强制全屏；最大高度上限提到屏幕 4/5 左右。
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = RoundedCornerShape(topStart = Dimens.RadiusXl, topEnd = Dimens.RadiusXl),
        // 取色跟随上一级游戏页：把手用 accent 色；容器改回默认不透明表面（避免半透明透出底层页面）
        dragHandle = { BottomSheetDefaults.DragHandle(color = GamePageColors.tonal(accent, 0.6f)) }
    ) {
        // 与游戏页同步：不再隐藏系统状态栏，恢复状态栏显示（沉浸体验一致）。
        val configuration = LocalConfiguration.current
        // 最大高度上限提升至屏幕 4/5（原 3/5），其余保持原半展开停靠行为。
        val maxSheetHeight = (configuration.screenHeightDp * 0.8f).dp
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .focusable()
                .onKeyEvent { onKey(it) }
                .padding(horizontal = Dimens.md)
                .padding(bottom = Dimens.lg),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "选择 Widget",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = accent
                        )
                        Text(
                            text = if (entries.size == 1) {
                                "${entries[0].slot.appLabel} (${entries[0].slot.packageName})"
                            } else {
                                "${entries.size} 款应用 · 共 ${flatItems.size} 个 Widget"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
                        )
                    }
                    ReverieIconButton(
                        onClick = onDismiss,
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.focusRequester(closeRequester)
                    )
                }
            }
            item { HorizontalDivider(color = SurfaceTokens.restingBorderColor()) }
            entries.forEachIndexed { idx, entry ->
                if (entries.size > 1) {
                    // 多应用：每款应用单独成节
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (idx == 0) 0.dp else Dimens.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                        ) {
                            Text(
                                text = entry.slot.appLabel,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "(${entry.slot.packageName})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
                            )
                        }
                    }
                }
                if (entry.providers.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${entry.slot.appLabel} 没有可用的 Widget",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
                            )
                        }
                    }
                } else {
                    items(entry.providers.size) { j ->
                        val provider = entry.providers[j]
                        val flatIdx = (entries.take(idx).sumOf { it.providers.size }) + j
                        WidgetProviderCard(
                            provider = provider,
                            accent = accent,
                            focused = focusIdx == flatIdx,
                            onClick = { onPickProvider(entry.slot, provider) },
                            modifier = Modifier
                                .focusRequester(cardRequesters[flatIdx])
                                .focusable()
                        )
                    }
                }
            }
        }
    }
    // 进入即聚焦首个可选项，确保手柄按键稳定送达。
    LaunchedEffect(Unit) { if (flatItems.isNotEmpty()) cardRequesters[0].requestFocus() }
}

/** 加载 Widget Provider 的预览缩略图（取自 provider 包资源 previewImage），无则 null。 */
@Composable
private fun rememberProviderPreview(provider: WidgetProviderItem): ImageBitmap? {
    val context = LocalContext.current
    var preview by remember(provider.packageName, provider.previewResId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(provider.packageName, provider.previewResId) {
        preview = null
        if (provider.previewResId != 0) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val res = context.packageManager.getResourcesForApplication(provider.packageName)
                    val drawable = res.getDrawable(provider.previewResId, null)
                    val bmp = if (drawable is BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val w = drawable.intrinsicWidth.coerceAtLeast(1)
                        val h = drawable.intrinsicHeight.coerceAtLeast(1)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                            val c = Canvas(it)
                            drawable.setBounds(0, 0, w, h)
                            drawable.draw(c)
                        }
                    }
                    preview = bmp.asImageBitmap()
                }
            }
        }
    }
    return preview
}

@Composable
private fun WidgetProviderCard(
    provider: WidgetProviderItem,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false
) {
    val preview = rememberProviderPreview(provider)
    SurfaceCard(
        level = if (focused) SurfaceTokens.CardLevel.Default else SurfaceTokens.CardLevel.Subtle,
        shape = Dimens.RadiusMd,
        modifier = modifier.focusBorder(focused, accent, RoundedCornerShape(Dimens.RadiusMd))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(Dimens.CardInnerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = provider.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(Dimens.RadiusMd))
                        .background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Strong))
                )
                Spacer(Modifier.width(Dimens.sm))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = provider.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = provider.className.substringAfterLast('.'),
                    style = MaterialTheme.typography.labelSmall,
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "规格: ${provider.minWidth} × ${provider.minHeight} dp",
                    style = MaterialTheme.typography.labelSmall,
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint)
                )
            }
            Icon(Icons.Filled.Add, contentDescription = "选择", tint = accent)
        }
    }
}

/**
 * 应用 icon 渲染（替代原文字头像）：本机应用 icon 优先，1×1 占位时回退首字母色块。
 * modifier 须已携带尺寸（调用方控制大小与裁切圆角）。
 */
@Composable
private fun AppIconImage(
    icon: ImageBitmap,
    accent: Color,
    name: String,
    modifier: Modifier = Modifier
) {
    if (icon.width > 1 && icon.height > 1) {
        Image(bitmap = icon, contentDescription = name, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier = modifier.background(accent.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            Text(
                text = name.firstOrNull()?.toString().orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

/**
 * 游戏截图画廊（替代原资源画廊，形式保留为横向滚动画廊）。
 * 依据 [GameScreenshots.ScreenshotGalleryState] 呈现四种状态：
 * - NEED_TREE：权限页（请求授权系统截图文件夹）；
 * - NEED_SAMPLE：初始化（选择一张样例截图/录屏以学习哈希后缀）；
 * - READY 且有截图：横向滚动缩略图，点击跳转第三方查看器；
 * - READY 无截图 / UNSUPPORTED：空状态 / 设备不支持提示。
 */
@Composable
private fun ScreenshotGallery(
    gallery: GameScreenshots.ScreenshotGalleryState?,
    variant: GamePageVariant,
    accent: Color,
    /** 焦点机：当前焦点是否落在截图画廊块（左摇杆在内容区时高亮）。 */
    focused: Boolean = false,
    /** 焦点机：画廊内选中截图纸贴序号（左/右在画廊内切换，0..displayShots.size）。 */
    selectedShot: Int = 0
) {
    val state = gallery?.setupState ?: GameScreenshots.SetupState.READY
    val shots = gallery?.screenshots ?: emptyList()
    // 重置绑定确认框可见态：仅「已绑定截图（READY 且有截图）」时角标可点并弹出确认；
    // 未初始化 / 初始化阶段（NEED_TREE / NEED_SAMPLE / UNSUPPORTED）点击无反应。
    var showResetDialog by remember { mutableStateOf(false) }
    // 任务一：超过 5 张时仅展示最新 5 张，并在第 6 位放置"查看全部"入口。
    val showViewAll = shots.size > 5 && gallery?.onViewAll != null
    val displayShots = if (showViewAll) shots.take(5) else shots
    val tileW = when {
        variant.isXL -> 200.dp
        variant.structure == GPStructure.LAND_3 -> 170.dp
        variant.isPortrait -> 140.dp
        else -> 160.dp
    }
    // 「收折成一行」：截图初始化无哈希后缀（UNSUPPORTED）时整块收折为一行按钮（与 Widget 收折行同一样式）。
    // 点击（触控）或焦点机 A（activate → onExpandUnsupported）展开完整卡片；不点则该应用始终保持一行。
    if (state == GameScreenshots.SetupState.UNSUPPORTED && gallery?.unsupportedCollapsed == true) {
        CollapsedSectionRow(
            icon = Icons.Filled.Image,
            title = "游戏截图",
            hint = "不可用 · 点击展开",
            accent = accent,
            focused = focused,
            trailing = Icons.Filled.KeyboardArrowDown,
            onClick = gallery.onExpandUnsupported
        )
        return
    }
    SurfaceCard(
        level = SurfaceTokens.CardLevel.Subtle,
        shape = Dimens.RadiusLg,
        // focusGroup：向 Compose 焦点系统声明画廊为合法焦点组（为右摇杆焦点贯通 / TalkBack 预留基建），
        // 不创建焦点目标、不消费按键——实际按键仍由 GamePageScreen.onKeyEvent 统一代理（归一化铁律）。
        // 刻意不用 focusable(true)：真实焦点目标会从根 Box.focusTarget() 抢焦点、截断按键流。
        modifier = Modifier.focusGroup().focusBorder(focused, accent, RoundedCornerShape(Dimens.RadiusLg))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.CardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, tint = accent, modifier = Modifier.size(Dimens.IconMd))
                Text(
                    text = "游戏截图",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                val countLabel = when (state) {
                    GameScreenshots.SetupState.READY -> if (shots.isEmpty()) "暂无该游戏截图" else "${shots.size} 张"
                    GameScreenshots.SetupState.NEED_TREE -> "需授权"
                    GameScreenshots.SetupState.NEED_SAMPLE -> "需初始化"
                    GameScreenshots.SetupState.UNSUPPORTED -> "不可用"
                }
                // 取色逻辑：背景 / 描边统一用 accent 派生（GamePageColors.tonal），不再用中性 iconTonalBg，
                // 使角标随游戏取色（accent）联动；已绑定截图时填充更深。
                // 文字统一用主题 onSurface（onAccent 仅适用于 solid accent 背景，tonal 透明底上浅色 accent 会返回黑字、深色页难读）。
                // 触摸事件：仅「已绑定截图（READY 且有截图）」可点击，弹出重置绑定确认框；
                // 未初始化 / 初始化阶段（NEED_TREE / NEED_SAMPLE / UNSUPPORTED）点击无反应。
                val canResetBinding = state == GameScreenshots.SetupState.READY && shots.isNotEmpty()
                val badgeBg = if (canResetBinding) GamePageColors.tonal(accent, 0.22f) else GamePageColors.tonal(accent, 0.10f)
                val badgeBorder = if (canResetBinding) GamePageColors.tonal(accent, 0.6f) else GamePageColors.tonal(accent, 0.3f)
                val badgeText = if (canResetBinding) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(badgeBg)
                        .accentOutline(badgeBorder, androidx.compose.foundation.shape.CircleShape)
                        .clickable(enabled = canResetBinding, onClick = { showResetDialog = true })
                        .padding(horizontal = Dimens.sm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = countLabel, style = MaterialTheme.typography.labelSmall, color = badgeText)
                }
            }
            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("重置游戏截图绑定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                    text = { Text("是否重置该游戏截图绑定？将清除当前游戏的截图归类记录，需重新选择样例初始化。", style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        ReverieFilledButton(onClick = { showResetDialog = false; gallery?.onRetry?.invoke() }, danger = true, text = "确认")
                    },
                    dismissButton = {
                        ReverieTextButton(onClick = { showResetDialog = false }, text = "取消")
                    }
                )
            }

            when (state) {
                GameScreenshots.SetupState.NEED_TREE ->
                    ScreenshotPermissionCard(onRequest = { gallery?.onRequestTree?.invoke() }, accent = accent)
                GameScreenshots.SetupState.NEED_SAMPLE ->
                    ScreenshotInitCard(onRequest = { gallery?.onRequestSample?.invoke() }, accent = accent)
                GameScreenshots.SetupState.UNSUPPORTED ->
                    ScreenshotUnsupportedCard(accent = accent, onRetry = { gallery?.onRetry?.invoke() })
                GameScreenshots.SetupState.READY -> {
                    if (shots.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = Dimens.lg), contentAlignment = Alignment.Center) {
                            Text(
                                "未找到该游戏的截图 / 录屏",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
                            )
                        }
                    } else {
                        // 画廊横向列表：手柄选图时滚动到选中项（selectedShot），并高亮选中的磁贴。
                        val galleryListState = remember { androidx.compose.foundation.lazy.LazyListState() }
                        // 横向滚动随焦点跟随：焦点在哪滚到哪（仅画廊聚焦 selectedShot>=0 时执行）。
                        LaunchedEffect(selectedShot) { if (selectedShot >= 0) runCatching { galleryListState.animateScrollToItem(selectedShot) } }
                        LazyRow(state = galleryListState, horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            itemsIndexed(displayShots, key = { _, shot -> shot.uri.toString() }) { idx, shot ->
                                ScreenshotTile(
                                    shot = shot,
                                    tileW = tileW,
                                    accent = accent,
                                    selected = selectedShot == idx,
                                    onClick = { gallery?.onOpen?.invoke(shot) }
                                )
                            }
                            if (showViewAll) {
                                item {
                                    ScreenshotViewAllTile(
                                        count = shots.size,
                                        tileW = tileW,
                                        accent = accent,
                                        selected = selectedShot == displayShots.size,
                                        onClick = { gallery?.onViewAll?.invoke(accent) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotTile(
    shot: GameScreenshots.Shot,
    tileW: Dp,
    accent: Color,
    /** 焦点机：当前磁贴被手柄选中（更强 accent 描边）。 */
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val bmp = rememberShotBitmap(shot).bitmap
    Box(
        modifier = Modifier
            .width(tileW)
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .accentOutline(GamePageColors.tonal(accent, if (selected) 1f else 0.3f), MaterialTheme.shapes.medium)
            .clickable { onClick() }
    ) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = shot.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(SurfaceTokens.cardSurface(SurfaceTokens.CardLevel.Strong)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (shot.isVideo) Icons.Filled.Videocam else Icons.Filled.Image,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(Dimens.IconMd)
                )
            }
        }
        if (shot.isVideo) {
            Box(
                Modifier.align(Alignment.TopStart).padding(Dimens.xs)
                    .background(accent.copy(alpha = 0.85f), RoundedCornerShape(Dimens.RadiusXs))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // solid accent 背景（0.85f）→ 用 onAccent 取对比色：浅色 accent 返回黑字、深色 accent 返回白字，均可读
                Text("视频", style = MaterialTheme.typography.labelSmall, color = GamePageColors.onAccent(accent))
            }
        }
        BottomFadeMask(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = Dimens.xs, vertical = 4.dp),
            height = Dp.Unspecified,
            content = {
                Text(
                    shot.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}

/**
 * 任务一："查看全部"入口磁贴（位于截图画廊第 6 位）。
 * 等高色块 + accent 描边，居中显示图标与"查看全部 (N)"文案，点击跳转到二级页面。
 */
@Composable
private fun ScreenshotViewAllTile(
    count: Int,
    tileW: Dp,
    accent: Color,
    /** 焦点机：当前磁贴被手柄选中（更强 accent 描边）。 */
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(tileW)
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .accentOutline(GamePageColors.tonal(accent, if (selected) 1f else 0.3f), MaterialTheme.shapes.medium)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(Dimens.IconMd)
            )
            Text(
                "查看全部",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                // 该磁贴无填充背景（仅 accent 描边），文字应随主题深浅：白底浅色主题下黑字、深色页白字
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "$count 张",
                style = MaterialTheme.typography.labelSmall,
                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium)
            )
        }
    }
}

@Composable
internal fun rememberShotBitmap(shot: GameScreenshots.Shot): GameScreenshots.ShotThumb {
    val context = LocalContext.current
    var state by remember(shot.uri) { mutableStateOf(GameScreenshots.ShotThumb(null, 16f / 9f, false)) }
    LaunchedEffect(shot.uri) {
        state = GameScreenshots.loadThumbnail(context, shot)
    }
    return state
}

@Composable
private fun ScreenshotPermissionCard(onRequest: () -> Unit, accent: Color) {
    SurfaceCard(level = SurfaceTokens.CardLevel.Default, shape = Dimens.RadiusMd) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.CardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = accent, modifier = Modifier.size(Dimens.IconMd))
                Text("授权截图文件夹", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f))
            }
            Text(
                "本功能按截图文件名后缀哈希自动归类该游戏的截图 / 录屏。请授权访问系统 Pictures / Screenshots 文件夹（仅读取，不会修改你的文件）。",
                style = MaterialTheme.typography.labelSmall,
                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium),
                lineHeight = 18.sp
            )
            ReverieFilledButton(
                onClick = onRequest,
                icon = Icons.Filled.Add,
                text = "授权截图文件夹",
                accent = accent,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ScreenshotInitCard(onRequest: () -> Unit, accent: Color) {
    SurfaceCard(level = SurfaceTokens.CardLevel.Default, shape = Dimens.RadiusMd) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.CardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                Icon(Icons.Filled.Image, contentDescription = null, tint = accent, modifier = Modifier.size(Dimens.IconMd))
                Text("初始化该游戏截图", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "请选择一张属于该游戏的截图或录屏。应用将记忆文件名末尾的哈希后缀，此后本设备上该后缀的全部截图 / 录屏都会归类到此游戏。",
                style = MaterialTheme.typography.labelSmall,
                color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium),
                lineHeight = 18.sp
            )
            ReverieFilledButton(
                onClick = onRequest,
                icon = Icons.Filled.Add,
                text = "选择一张截图 / 录屏",
                accent = accent,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ScreenshotUnsupportedCard(accent: Color, onRetry: () -> Unit = {}) {
    SurfaceCard(level = SurfaceTokens.CardLevel.Default, shape = Dimens.RadiusMd) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.CardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(Dimens.IconMd))
                Text(
                    "本游戏的截图文件名不含哈希后缀，无法自动归类到该游戏；如为误判，可重试重新初始化。",
                    style = MaterialTheme.typography.labelSmall,
                    color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium),
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            ReverieFilledButton(
                onClick = onRetry,
                accent = accent,
                text = "重试",
                focused = false,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/** 时长趋势概览：总计 / 日均 / 最高 + 近 N 天条形图。聚焦 slot = contentBase + 0（审计：此前不可聚焦，DOWN 跳过）。 */
@Composable
private fun TrendSummary(
    data: GamePageData,
    variant: GamePageVariant,
    focused: Boolean = false,
    onActivate: () -> Unit = {}
) {
    val series = data.daySeries
    if (series.isEmpty()) return
    val total = series.sum()
    val active = series.count { it > 0L }
    val avg = if (active > 0) total / active else 0L
    val best = series.maxOrNull() ?: 0L
    val max = best.coerceAtLeast(1L)
    val window = if (variant.isXL || variant.structure == GPStructure.LAND_3) series.size.coerceAtMost(30) else series.size.coerceAtMost(14)
    val recent = series.takeLast(window)
    SurfaceCard(
        modifier = Modifier
            .focusBorder(focused, data.accent, RoundedCornerShape(Dimens.RadiusLg))
            .clickable { onActivate() },
        level = if (focused) SurfaceTokens.CardLevel.Default else SurfaceTokens.CardLevel.Subtle,
        shape = Dimens.RadiusLg,
        border = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.CardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            Text("时长趋势", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                TrendStat("总计", if (total > 0) formatDuration(total) else "暂无", Modifier.weight(1f))
                TrendStat("日均", if (avg > 0) formatDuration(avg) else "暂无", Modifier.weight(1f))
                TrendStat("最高", if (best > 0) formatDuration(best) else "暂无", Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().height(if (variant.isXL) 72.dp else 56.dp)
            ) {
                recent.forEach { ms ->
                    val frac = (ms.toFloat() / max).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(frac.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(Dimens.RadiusXs))
                            .background(
                                if (LocalAmoledPureBlack.current) SolidColor(data.accent)
                                else Brush.verticalGradient(listOf(data.accent, GamePageColors.tonal(data.accent, 0.5f)))
                            )
                    )
                }
            }
            Text("近 ${window} 天", style = MaterialTheme.typography.labelSmall, color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Faint))
        }
    }
}

@Composable
private fun TrendStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = SurfaceTokens.mutedOnSurface(SurfaceTokens.MutedLevel.Medium))
    }
}

/** 由时间线索引推导日期标签（index 0 为最远一天，末位为今天）。 */
private fun dayLabelFromEnd(index: Int, total: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_MONTH, -(total - 1 - index))
    return Instant.ofEpochMilli(cal.timeInMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd"))
}

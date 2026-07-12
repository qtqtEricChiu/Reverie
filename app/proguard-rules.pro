# ──────────────────────────────────────────
# Reverie ProGuard / R8 保留规则
# ──────────────────────────────────────────
# release 构建启用 isMinifyEnabled + isShrinkResources 后，R8 会
# 混淆/压缩代码并移除未使用的资源。以下规则确保关键组件不被移除。

# ── Android 组件 ──
-keep class cn.mocabolka.run.MainActivity { *; }
-keep class cn.mocabolka.run.compat.CompatGuideActivity { *; }
-keep class cn.mocabolka.run.compat.NotificationBadgeService { *; }
-keep class cn.mocabolka.run.compat.OrientationLockService { *; }

# ── ViewModel ──
-keep class cn.mocabolka.run.viewmodel.HomeViewModel { *; }

# ── 数据模型（序列化/反序列化用） ──
-keep class cn.mocabolka.run.launcher.AppModel { *; }
-keep class cn.mocabolka.run.launcher.AppCache$CachedApp { *; }
-keep class cn.mocabolka.run.launcher.UsageStatsRepository$UsageEntry { *; }
-keep class cn.mocabolka.run.launcher.OssLicense { *; }

# ── Compose 运行时 ──
# Compose 编译器插件生成的代码需要保留
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ── Kotlin 协程 ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── JSON 序列化 ──
-keep class org.json.** { *; }

# ── Material Icons ──
# 保留项目中实际使用的 Material 图标类（R8 自动 tree-shake 未使用的）
-keep class androidx.compose.material.icons.filled.** { *; }
-keep class androidx.compose.material.icons.automirrored.filled.** { *; }
-keep class androidx.compose.material.icons.outlined.** { *; }

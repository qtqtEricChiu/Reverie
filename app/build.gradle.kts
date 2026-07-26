plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cn.mocabolka.run"
    compileSdk = 36
    // 使用 android-36.1 平台，补齐 FRAME_RATE_CATEGORY_*/RampSegment 等 API 36 符号

    defaultConfig {
        applicationId = "cn.mocabolka.run"
        minSdk = 36
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // AGP 8.x 默认关闭 BuildConfig 生成；LauncherApplication 用到 BuildConfig.DEBUG 需显式开启
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // API 36 设备最低 Android 16，100% 为 64 位 ARM 设备。
    splits {
        abi {
            isEnable = false // 不分包，单一 APK
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    // SplashScreen 兼容库：提供 Theme.SplashScreen parent 与安装入口。
    // minSdk=36 时系统已原生支持 SplashScreen，但主题 parent 仍需该库提供。
    // 在 API 31+ 设备上 installSplashScreen 内部 no-op、仅做主题切换；可视为零开销。
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 使用 extended 图标集（含全部 Material 图标）。
    // Release 构建通过 R8 minify 自动 tree-shake 未使用的图标类，实际增量可忽略。
    implementation("androidx.compose.material:material-icons-extended")
    // 瀑布流（LazyVerticalStaggeredGrid）所在基础库（material3 已传递依赖，此处显式声明以保证 API 可解析）。
    implementation("androidx.compose.foundation:foundation")

    // 拼音搜索：汉字转拼音（全拼 + 首字母），支持按拼音/首字母检索应用。
    implementation("com.belerweb:pinyin4j:2.5.1")

    // OkHttp：SteamDB 资产下载器底层（替换 JDK HttpURLConnection，提供自动重定向/keep-alive/连接池）。
    // 排除 OkHttp 传递引入的旧版 kotlin-stdlib-jdk7/jdk8:1.9.10（离线缓存无此版本会致 --offline 构建失败）；
    // 本项目已用 kotlin-stdlib 2.1.20，jdk7/jdk8 早已合并进主 stdlib，排除后功能无损。
    implementation("com.squareup.okhttp3:okhttp:4.12.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }

    debugImplementation("androidx.compose.ui:ui-tooling")
}

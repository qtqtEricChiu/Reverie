package cn.mocabolka.run

import android.app.Application
import android.util.Log
import cn.mocabolka.run.BuildConfig

class LauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局未捕获异常处理器：先打印真实异常，再委托给系统原始处理器（避免递归死循环）
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ReverieApp", "未捕获异常：thread=${thread.name}", throwable)
            originalHandler?.uncaughtException(thread, throwable)
        }
        // API 36 启动期诊断：仅 debug 构建打印，release 静默以免噪声（C5-8）。
        // minSdk = 36 恒为 API 36，无需版本守卫。
        if (BuildConfig.DEBUG) {
            Log.i(
                "ReverieApp",
                "启动：targetSdk=36 预测返回已启用(Manifest enableOnBackInvokedCallback=true)"
            )
        }
    }
}

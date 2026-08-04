package cn.mocabolka.run.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import cn.mocabolka.run.gamepad.Direction
import android.view.KeyEvent
import android.view.MotionEvent
import cn.mocabolka.run.gamepad.SubPageActivityHost
import cn.mocabolka.run.gamepad.markInputModeGamepad
import cn.mocabolka.run.gamepad.markInputModeTouch
import cn.mocabolka.run.launcher.AppModel
import cn.mocabolka.run.launcher.AppRepository
import cn.mocabolka.run.launcher.NotificationHistoryStore
import cn.mocabolka.run.launcher.NotificationRecord
import cn.mocabolka.run.launcher.SocialAppClassifier
import cn.mocabolka.run.ui.components.NotificationHistoryPage
import cn.mocabolka.run.ui.theme.LandscapeTheme
import cn.mocabolka.run.ui.theme.MotionMachines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * 通知历史钻取页（按应用 / 发件人筛选的具体通知列表）。
 *
 * 独立 Activity（以 About / Licenses 子页为基准）：
 * - 真全屏 + 统一子页观感（SubPageScaffold 的 colorScheme.background，与 About 子页家族一致，
 *   不再是从主页氛围背景里抠出的"tab 内死面板"，消除全屏感与背景割裂）。
 * - 手柄归一化：复用 SubPageGamepadHost（与 MainActivity 同源引擎），列表行为由 SubPageScaffold 统一接管
 *   （B 返回 / 左摇杆焦点遍历 / 右摇杆滚动同步焦点），补齐此前行 focused=false 完全进不去列表的缺陷。
 * - 数据：NotificationHistoryStore 实时（changeTick 重查）+ AppRepository.loadPackage 独立取图标/名称，
 *   Intent 透传 pkg/sender/startKey/endKey/mode，解耦 StatsScreen 内部范围计算。
 */
class NotificationHistoryActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PACKAGE = "extra_pkg"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_START_KEY = "extra_start"
        const val EXTRA_END_KEY = "extra_end"
        const val EXTRA_MODE = "extra_mode"
        const val MODE_APP = "app"
        const val MODE_SENDER = "sender"

        fun createIntent(
            ctx: Context,
            pkg: String,
            sender: String?,
            startKey: String,
            endKey: String,
            mode: String
        ): Intent = Intent(ctx, NotificationHistoryActivity::class.java).apply {
            putExtra(EXTRA_PACKAGE, pkg)
            if (sender != null) putExtra(EXTRA_SENDER, sender)
            putExtra(EXTRA_START_KEY, startKey)
            putExtra(EXTRA_END_KEY, endKey)
            putExtra(EXTRA_MODE, mode)
        }
    }

    private lateinit var subPage: SubPageActivityHost
    private lateinit var listState: LazyListState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val sender = intent.getStringExtra(EXTRA_SENDER)
        val startKey = intent.getStringExtra(EXTRA_START_KEY) ?: ""
        val endKey = intent.getStringExtra(EXTRA_END_KEY) ?: ""
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_APP
        if (pkg == null) {
            finish()
            return
        }

        listState = LazyListState()
        subPage = SubPageActivityHost(
            activity = this,
            getState = { listState },
            getFirstVisibleItemIndex = { listState.firstVisibleItemIndex }
        )
        subPage.start()

        val settings = SettingsRepository(this)
        setContent {
            LandscapeTheme(settings = settings) {
                // 与 About / Licenses 一致的入场重播机制：ON_RESUME 自增 refresh 触发逐行入场动画重播。
                var refresh by rememberRefreshOnResume()
                val changeVersion by NotificationHistoryStore.changeTick.collectAsState()
                val records by produceState<List<NotificationRecord>>(
                    initialValue = emptyList(),
                    key1 = listOf(changeVersion, startKey, endKey, pkg, sender)
                ) {
                    value = withContext(Dispatchers.IO) {
                        val base = NotificationHistoryStore.listForRange(startKey, endKey)
                        if (sender != null) {
                            base.filter { it.packageName == pkg && SocialAppClassifier.senderOf(it) == sender }
                        } else {
                            base.filter { it.packageName == pkg }
                        }
                    }
                }
                val app by produceState<AppModel?>(initialValue = null, key1 = pkg) {
                    value = withContext(Dispatchers.IO) {
                        AppRepository(this@NotificationHistoryActivity).loadPackage(pkg)
                    }
                }
                val title = if (mode == MODE_SENDER && sender != null) "发件人 · $sender"
                else (app?.label ?: pkg)
                AnimatedVisibility(
                    visible = true,
                    // 沉浸子页入场 / 出场统一走「动画机列表」subPage*。
                    enter = MotionMachines.subPageEnter(reduceMotion = false),
                    exit = MotionMachines.subPageExit(reduceMotion = false)
                ) {
                    val rsFocus = subPage.rightStickFirstVisible.collectAsState()
                    key(refresh) {
                        NotificationHistoryPage(
                            title = title,
                            app = app,
                            records = records,
                            onBack = { finish() },
                            listState = listState,
                            rightStickFirstVisible = rsFocus,
                            navigate = subPage.navFlow,
                            reduceMotion = settings.reduceMotion
                        )
                    }
                }
            }
        }
        cn.mocabolka.run.compat.OrientationManager.applyAppOrientation(this, settings)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        markInputModeGamepad(event)
        return if (subPage.dispatchMotionEvent(event)) true else super.onGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        markInputModeGamepad(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        markInputModeTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        subPage.stop()
        super.onDestroy()
    }
}

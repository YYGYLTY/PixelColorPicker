package com.pixelcolorpicker

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixelcolorpicker.ui.AboutScreen
import com.pixelcolorpicker.ui.CodeScreen
import com.pixelcolorpicker.ui.CropScreen
import com.pixelcolorpicker.ui.EditorScreen
import com.pixelcolorpicker.ui.HomeScreen
import com.pixelcolorpicker.ui.HistoryScreen
import com.pixelcolorpicker.ui.MainViewModel
import com.pixelcolorpicker.ui.PaletteScreen
import com.pixelcolorpicker.ui.SettingsScreen
import com.pixelcolorpicker.ui.SubScreen
import com.pixelcolorpicker.ui.Tab
import com.pixelcolorpicker.ui.components.BottomNav
import com.pixelcolorpicker.ui.theme.PcpDecelerate
import com.pixelcolorpicker.ui.theme.PixelColorPickerTheme
import com.pixelcolorpicker.util.DebugLog
import com.pixelcolorpicker.util.LocaleHelper
import com.pixelcolorpicker.util.RobustImagePickContract

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.init(this)
        applyEdgeToEdge()
        // 监听每次布局：若系统把内容容器按系统栏内缩（MIUI 重建后偶发），立即清除。
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            clearSystemBarInset()
        }
        setContent {
            AppRoot()
        }
    }

    override fun onResume() {
        super.onResume()
        applyEdgeToEdge()
        // MIUI 在「Activity 重建（如切换语言）/恢复」后可能延迟覆盖窗口标志，
        // 导致内容区底部被缩回、导航栏区域露出空黑。这里在恢复后的多个时间点重申，
        // 确保内容始终延伸到屏幕最底部（导航栏区域透明）。
        window.decorView.post { applyEdgeToEdge() }
        window.decorView.postDelayed({ applyEdgeToEdge() }, 350L)
        window.decorView.postDelayed({ applyEdgeToEdge() }, 1100L)
    }

    override fun onPostResume() {
        super.onPostResume()
        applyEdgeToEdge()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyEdgeToEdge()
    }

    /**
     * 统一应用「内容延伸到系统栏 + 透明系统栏 + 禁用对比度蒙层」。
     * 幂等，可安全多次调用（用于对抗 MIUI 在重建后的延迟覆盖）。
     */
    @Suppress("DEPRECATION")
    private fun applyEdgeToEdge() {
        enableEdgeToEdge(
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // 显式声明（Android 15 上 enableEdgeToEdge 不再清除系统栏背景，需手动补）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        // MIUI 在 Activity 重建后可能把 content 容器按导航栏高度内缩（内容区底部被裁掉约 66px，
        // 导致「小白条下面露出窗口底色」）。这里把 decor 与 content 容器的 fitsSystemWindows
        // 强制置 false 并重新分发 insets，保证内容始终铺满整屏。
        (window.decorView as? android.view.ViewGroup)?.let { decor ->
            decor.fitsSystemWindows = false
            val content = decor.findViewById<android.view.View>(android.R.id.content)
            content?.fitsSystemWindows = false
        }
        window.decorView.requestApplyInsets()
        // 诊断日志：记录窗口真实状态（便于无需触碰设备即可定位问题）
        val navInset = ViewCompat.getRootWindowInsets(window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: -1
        val content = window.decorView.findViewById<android.view.View>(android.R.id.content)
        DebugLog.log(
            "edge2edge: decorFits=${window.decorView.fitsSystemWindows} " +
                "navColor=${Integer.toHexString(window.navigationBarColor)} " +
                "navInset=$navInset " +
                "contentFits=${content?.fitsSystemWindows} " +
                "contentSize=${content?.width}x${content?.height}",
        )
    }

    /**
     * 清除系统栏内缩：MIUI 在 Activity 重建后可能把 content 容器按「状态栏+导航栏」内缩
     * （典型 ~138px + ~60px），导致底部露出窗口底色（"小白条下面一层黑"）。
     *
     * 关键：清除 padding 的同时必须把容器的 fitsSystemWindows 置 false，
     * 否则容器会"消费"掉 insets，Compose 侧拿不到状态栏/导航栏 inset（标题顶到状态栏、导航卡沉底）。
     * 幂等：只有发现非零内缩/未关闭的 fits 才会修改，且仅在确有改动时才重分发 insets（避免抖动）。
     */
    @Suppress("DEPRECATION")
    private fun clearSystemBarInset() {
        runCatching {
            var changed = false
            val decor = window.decorView
            val frame = decor.findViewById<android.view.View>(android.R.id.content) ?: return
            val targets = mutableListOf<Pair<String, android.view.View>>()
            targets += "decor" to decor
            if (decor is android.view.ViewGroup) {
                for (i in 0 until decor.childCount) targets += "decorChild$i" to decor.getChildAt(i)
            }
            targets += "content" to frame
            if (frame is android.view.ViewGroup) {
                for (i in 0 until frame.childCount) targets += "contentChild$i" to frame.getChildAt(i)
            }
            for ((tag, v) in targets) {
                // 1) 停止消费系统栏 insets（让 Compose 侧仍能拿到 inset）
                if (v.fitsSystemWindows) {
                    DebugLog.log("clearFits($tag): was true")
                    v.fitsSystemWindows = false
                    changed = true
                }
                // 2) 清除内缩 padding
                if (v.paddingTop != 0 || v.paddingBottom != 0) {
                    DebugLog.log("clearInset($tag): padTop=${v.paddingTop} padBottom=${v.paddingBottom}")
                    v.setPadding(v.paddingLeft, 0, v.paddingRight, 0)
                    changed = true
                }
                // 3) 清除 margin 内缩
                (v.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                    if (lp.topMargin != 0 || lp.bottomMargin != 0) {
                        DebugLog.log("clearMargin($tag): top=${lp.topMargin} bottom=${lp.bottomMargin}")
                        lp.topMargin = 0
                        lp.bottomMargin = 0
                        v.layoutParams = lp
                        changed = true
                    }
                }
            }
            // 4) 仅在确有改动时重新分发 insets（避免无谓重排/抖动）
            if (changed) window.decorView.requestApplyInsets()
        }
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current

    // 主题模式：跟随系统 / 浅色 / 深色（由设置页切换，持久化）
    val darkTheme = when (viewModel.themeMode) {
        MainViewModel.THEME_LIGHT -> false
        MainViewModel.THEME_DARK -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    PixelColorPickerTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppContent(viewModel = viewModel, context = context)
        }
    }
}

@Composable
private fun AppContent(viewModel: MainViewModel, context: android.content.Context) {

    // 图片选择器：使用稳健的文档选择器（规避 MIUI 照片选择器结果不回传的 bug）
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = RobustImagePickContract(),
    ) { uri ->
        DebugLog.log("AppRoot: picker callback, uri=$uri")
        if (uri != null) {
            viewModel.loadImage(uri)
        }
    }

    fun launchPicker() {
        try {
            pickImageLauncher.launch(Unit)
        } catch (t: Throwable) {
            DebugLog.log("AppRoot: launchPicker failed: $t")
            Toast.makeText(
                context,
                context.getString(R.string.toast_picker_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 系统返回键
    BackHandler(enabled = viewModel.subScreen != SubScreen.None) {
        viewModel.navigateBack()
    }

    // 启动时静默检查更新（失败不打扰）
    LaunchedEffect(Unit) { viewModel.checkForUpdates() }

    // 标签页状态保持（切 Tab 后回来：滚动位置等 rememberSaveable 状态不丢）
    val tabStateHolder = rememberSaveableStateHolder()

    Box(modifier = Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = viewModel.subScreen,
        transitionSpec = {
            if (targetState != SubScreen.None) {
                // 进入子页面：从右侧轻滑入 + 淡入
                (slideInHorizontally(tween(260, easing = PcpDecelerate), initialOffsetX = { it / 5 }) +
                    fadeIn(tween(260, easing = PcpDecelerate)))
                    .togetherWith(fadeOut(tween(160)))
            } else {
                // 返回：淡入 + 向右轻滑出
                fadeIn(tween(240, easing = PcpDecelerate))
                    .togetherWith(slideOutHorizontally(tween(260, easing = PcpDecelerate), targetOffsetX = { it / 5 }) +
                        fadeOut(tween(200)))
            }
        },
        label = "subScreen",
        modifier = Modifier.fillMaxSize(),
    ) { screen ->
        when (screen) {
        SubScreen.Crop -> {
            CropScreen(
                viewModel = viewModel,
                onBack = { viewModel.navigateBack() },
            )
        }
        SubScreen.Editor -> {
            EditorScreen(
                viewModel = viewModel,
                onBack = { viewModel.navigateBack() },
                onSave = { viewModel.saveEditorArt() },
            )
        }
        SubScreen.Settings -> {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { viewModel.navigateBack() },
            )
        }
        SubScreen.History -> {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { viewModel.navigateBack() },
            )
        }
        SubScreen.None -> {
            Box(modifier = Modifier.fillMaxSize()) {
                // 内容区（全高：内容延伸到屏幕底部，可出现在悬浮导航与手势条后方）
                AnimatedContent(
                    targetState = viewModel.tab,
                    transitionSpec = {
                        (fadeIn(tween(240, easing = PcpDecelerate)) +
                            scaleIn(tween(240, easing = PcpDecelerate), initialScale = 0.985f))
                            .togetherWith(fadeOut(tween(160)))
                    },
                    label = "tab",
                    modifier = Modifier.fillMaxSize(),
                ) { tab ->
                    tabStateHolder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            Tab.Home -> HomeScreen(
                                viewModel = viewModel,
                                onPickImage = { launchPicker() },
                                onOpenSettings = { viewModel.openSettings() },
                            )
                            Tab.Palette -> PaletteScreen(viewModel = viewModel)
                            Tab.Code -> CodeScreen(viewModel = viewModel)
                            Tab.About -> AboutScreen(genCount = viewModel.localGenCount)
                        }
                    }
                }

                // 悬浮底部导航（底边距 17dp：tab 栏底到小白条上缘 ≈ 小白条下缘到屏幕底，视觉对称）
                BottomNav(
                    current = viewModel.tab,
                    onSelect = { viewModel.tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 系统 inset 驱动（导航栏 16dp + 1dp 间距）：无论系统处于全屏还是内缩模式，位置都一致
                        .navigationBarsPadding()
                        .padding(bottom = 1.dp),
                )
            }
        }
        }
    }

    // 更新提示弹窗
    viewModel.appUpdate?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text(stringResource(R.string.update_title_fmt, info.versionName)) },
            text = {
                Text(
                    if (info.notes.isBlank()) {
                        stringResource(R.string.update_notes_fallback)
                    } else {
                        info.notes
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissUpdate()
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(info.url),
                            ),
                        )
                    } catch (t: Throwable) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_open_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) {
                    Text(stringResource(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.skipUpdateVersion() }) {
                    Text(stringResource(R.string.update_ignore))
                }
            },
        )
    }
    }
    }
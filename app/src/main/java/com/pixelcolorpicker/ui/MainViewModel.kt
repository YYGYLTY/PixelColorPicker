package com.pixelcolorpicker.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelcolorpicker.R
import com.pixelcolorpicker.image.BitmapLoader
import com.pixelcolorpicker.image.PixelAlgorithm
import com.pixelcolorpicker.image.Pixelator
import com.pixelcolorpicker.model.PixelArt
import com.pixelcolorpicker.model.PixelGrid
import com.pixelcolorpicker.util.DebugLog
import com.pixelcolorpicker.util.PngExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 底部导航 Tab。 */
enum class Tab { Home, Palette, Code, About }

/** 全屏子页面。 */
enum class SubScreen { None, Crop, Editor, Settings, History }

/** 编辑器工具。 */
enum class EditorTool { Pencil, Eraser, Eyedropper, Fill }

/** 历史记录条目（磁盘持久化：files/history/hist_<time>_<w>x<h>.png）。 */
class HistoryEntry(
    val file: java.io.File,
    val time: Long,
    val width: Int,
    val height: Int,
)

/**
 * 应用主 ViewModel。
 *
 * 首页流程：选图 → 裁剪（按画布比例）→ 展示框微调 → 生成像素画 → 色板 / 编码 / 保存。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    // ---------- 导航 ----------

    var tab by mutableStateOf(Tab.Home)
    var subScreen by mutableStateOf(SubScreen.None)
        private set

    fun navigateBack(): Boolean {
        return when (subScreen) {
            SubScreen.None -> false
            SubScreen.Crop -> {
                subScreen = SubScreen.None
                true
            }
            SubScreen.Editor -> {
                subScreen = SubScreen.None
                true
            }
            SubScreen.Settings -> {
                subScreen = SubScreen.None
                true
            }
            SubScreen.History -> {
                // 从历史返回编辑器
                subScreen = SubScreen.Editor
                true
            }
        }
    }

    /** 打开设置页。 */
    fun openSettings() {
        subScreen = SubScreen.Settings
    }

    // ---------- 画布尺寸（持久化 + 上限 999 + 使用历史） ----------

    private val prefs = app.getSharedPreferences("pixel_prefs", 0)

    var canvasWidth by mutableStateOf(prefs.getInt("canvas_width", 16).coerceIn(1, MAX_CANVAS_SIZE))
        private set
    var canvasHeight by mutableStateOf(prefs.getInt("canvas_height", 16).coerceIn(1, MAX_CANVAS_SIZE))
        private set

    /** 选图裁剪后是否自动生成像素画（设置项，持久化；默认关闭）。 */
    var autoGenerate by mutableStateOf(prefs.getBoolean("auto_generate", false))
        private set

    fun updateAutoGenerate(enabled: Boolean) {
        autoGenerate = enabled
        prefs.edit().putBoolean("auto_generate", enabled).apply()
    }

    /**
     * 主题模式：0=跟随系统，1=浅色，2=深色（设置项，持久化；默认跟随系统）。
     */
    var themeMode by mutableStateOf(prefs.getInt("theme_mode", THEME_SYSTEM))
        private set

    fun updateThemeMode(mode: Int) {
        themeMode = mode
        prefs.edit().putInt("theme_mode", mode).apply()
    }

    /** 首页操作行（更换图片/裁剪/网格）是否展开（跨页面/切 Tab 保持）。 */
    var homeActionsExpanded by mutableStateOf(true)

    /** 首页滚动位置（跨页面/切 Tab 保持）。 */
    var homeScrollY by mutableIntStateOf(0)

    /** 尺寸使用历史（复刻旧版 size_history："16×16:5;32×32:3"）。 */
    var sizeHistory by mutableStateOf(parseHistory(prefs.getString("size_history", "") ?: ""))
        private set

    /** 自定义常用尺寸预设（字符串 "W×H"；可经「…」保存）。 */
    private var customPreset0 by mutableStateOf(prefs.getString("preset_0", "") ?: "")
    private var customPreset1 by mutableStateOf(prefs.getString("preset_1", "") ?: "")

    /**
     * 设置画布尺寸。
     *
     * 已生成像素画时：
     *  - **同比例**（如 16×16 → 32×32）：直接重新采样成新尺寸的像素画 ✓
     *  - **不同比例**（如 16×16 → 1×999）：不改变画面，仅记录新比例，提示用户重新框选
     *
     * @return true=同比例已重采样；false=比例不同（需要重新框选）
     */
    fun setCanvasSize(w: Int, h: Int): Boolean {
        val nw = w.coerceIn(1, MAX_CANVAS_SIZE)
        val nh = h.coerceIn(1, MAX_CANVAS_SIZE)
        if (nw == canvasWidth && nh == canvasHeight) return true
        val hadArt = isPixelated && croppedBitmap != null
        if (hadArt) {
            // 已生成像素画：判断是否同比例
            val sameRatio = canvasWidth.toLong() * nh == canvasHeight.toLong() * nw
            canvasWidth = nw
            canvasHeight = nh
            saveCanvasSize()
            recordSizeHistory()
            if (sameRatio) {
                // 同比例：直接按新尺寸重新采样（像素画等比缩放，不变形）
                onImageParamsChanged()
                return true
            }
            // 不同比例：保持画面不变，仅记录比例（下次裁剪使用），由调用方提示重新框选
            return false
        }
        canvasWidth = nw
        canvasHeight = nh
        saveCanvasSize()
        recordSizeHistory()
        onImageParamsChanged()
        return true
    }

    private fun saveCanvasSize() {
        prefs.edit()
            .putInt("canvas_width", canvasWidth)
            .putInt("canvas_height", canvasHeight)
            .apply()
    }

    /** 记录一次尺寸使用（复刻旧版 recordSizeHistory）。 */
    fun recordSizeHistory() {
        val key = "${canvasWidth}×${canvasHeight}"
        val map = sizeHistory.toMutableMap()
        map[key] = (map[key] ?: 0) + 1
        sizeHistory = map
        val sb = StringBuilder()
        map.forEach { (k, v) ->
            if (sb.isNotEmpty()) sb.append(';')
            sb.append(k).append(':').append(v)
        }
        prefs.edit().putString("size_history", sb.toString()).apply()
    }

    /**
     * 常用尺寸预设：优先自定义预设（可经「…」保存），不足时用历史使用次数 top2，
     * 再不足用默认 16×16 / 32×32 补齐。
     */
    fun presetSize(index: Int): Pair<Int, Int> {
        val custom = parseSizeKey(if (index == 0) customPreset0 else customPreset1)
        if (custom != null) return custom
        val sorted = sizeHistory.entries
            .sortedByDescending { it.value }
            .mapNotNull { parseSizeKey(it.key) }
        return when {
            sorted.size > index -> sorted[index]
            index == 0 -> 16 to 16
            else -> 32 to 32
        }
    }

    /** 保存自定义常用尺寸（新值进预设1，旧预设1顺延到预设2），并应用。 */
    fun saveCustomPreset(w: Int, h: Int) {
        val nw = w.coerceIn(1, MAX_CANVAS_SIZE)
        val nh = h.coerceIn(1, MAX_CANVAS_SIZE)
        val old0 = customPreset0.ifEmpty {
            presetSize(0).let { "${it.first}×${it.second}" }
        }
        customPreset1 = old0
        customPreset0 = "$nw×$nh"
        prefs.edit()
            .putString("preset_0", customPreset0)
            .putString("preset_1", customPreset1)
            .apply()
        setCanvasSize(nw, nh)
    }

    private fun parseHistory(raw: String): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        if (raw.isEmpty()) return map
        for (e in raw.split(';')) {
            val parts = e.split(':')
            if (parts.size == 2) {
                val v = parts[1].toIntOrNull() ?: continue
                map[parts[0]] = v
            }
        }
        return map
    }

    private fun parseSizeKey(key: String): Pair<Int, Int>? {
        val parts = key.split('×')
        if (parts.size != 2) return null
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        return w to h
    }

    // ---------- 图片状态 ----------

    /** 用户选择的原图。 */
    var sourceBitmap by mutableStateOf<Bitmap?>(null)
        private set

    /** 裁剪后的图片（展示框的内容源）。 */
    var croppedBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    /** 是否已生成像素画。 */
    var isPixelated by mutableStateOf(false)
        private set

    /** 是否已显示网格。 */
    var gridEnabled by mutableStateOf(true)

    /** 首页初始展开动画是否已播放（复刻旧版 Fragment 行为：只在首次进入时播放）。 */
    var homeIntroPlayed by mutableStateOf(false)

    /** 首页欢迎动画是否已播放（复刻旧版：只在首次进入时播放）。 */
    var homeWelcomePlayed by mutableStateOf(false)

    // ---------- 本地统计（仅存本机，不上传） ----------

    /** 累计生成像素画次数。 */
    var localGenCount by mutableStateOf(prefs.getInt("stats_gen_count", 0))
        private set

    // ---------- 更新检测 ----------

    data class AppUpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val notes: String,
    )

    /** 检测到的新版本（null = 无）。 */
    var appUpdate by mutableStateOf<AppUpdateInfo?>(null)
        private set

    private var updateCheckStarted = false

    /** 启动时调用一次：静默检查更新（失败不打扰用户）。 */
    fun checkForUpdates() {
        if (updateCheckStarted) return
        updateCheckStarted = true
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { com.pixelcolorpicker.util.UpdateChecker.fetch() }
                ?: return@launch
            val current = currentVersionCode()
            val skipped = prefs.getInt("update_skip_code", 0)
            if (info.versionCode > current && info.versionCode != skipped) {
                appUpdate = AppUpdateInfo(info.versionCode, info.versionName, info.url, info.notes)
            }
        }
    }

    /** 稍后再说：仅关闭本次弹窗。 */
    fun dismissUpdate() {
        appUpdate = null
    }

    /** 忽略此版本：以后不再提示该版本。 */
    fun skipUpdateVersion() {
        appUpdate?.let { prefs.edit().putInt("update_skip_code", it.versionCode).apply() }
        appUpdate = null
    }

    private fun currentVersionCode(): Int = try {
        val ctx = getApplication<Application>()
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            pi.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode
        }
    } catch (t: Throwable) {
        0
    }

    /** 当前算法（默认最近邻：保留像素、清晰锐利、速度最快）。 */
    var algorithm by mutableStateOf(PixelAlgorithm.Nearest)
        private set

    /** 自定义算法：采样内核（最近邻/双线性/双三次/Lanczos）。 */
    var customKernel by mutableStateOf(PixelAlgorithm.Bilinear)
        private set

    /** 自定义算法：采样密度（每轴采样数 1~8）。 */
    var customSamples by mutableStateOf(4)
        private set

    /** 微调：缩放。 */
    var scale by mutableStateOf(1f)
        private set

    /** 微调：平移（占源宽/高比例）。 */
    var offsetX by mutableStateOf(0f)
        private set
    var offsetY by mutableStateOf(0f)
        private set

    /** 微调：旋转角度。 */
    var rotation by mutableStateOf(0f)
        private set

    // ---------- 两段式变换预览 ----------
    // 显示层按「当前参数 vs 预览参数」的几何差对预览图做实时变换（零重采样），
    // 手势结束后才重新采样一次，避免拖动中反复重采样导致卡顿。

    /** 当前预览 Bitmap 对应的采样缩放。 */
    var previewScale by mutableStateOf(1f)
        private set

    /** 当前预览 Bitmap 对应的采样平移。 */
    var previewOffsetX by mutableStateOf(0f)
        private set
    var previewOffsetY by mutableStateOf(0f)
        private set

    /** 当前预览 Bitmap 对应的采样旋转。 */
    var previewRotation by mutableStateOf(0f)
        private set

    /** 像素化预览（展示框使用，可能被降采样）。 */
    var pixelPreview by mutableStateOf<Bitmap?>(null)
        private set

    // ---------- 预览结果缓存（进入编辑器 / 计算完整网格时直接复用，避免二次全量像素化） ----------

    /** 预览对应的像素数据（与 previewXxx 参数、来源图严格一致）。 */
    private var previewPixels: IntArray? = null
    private var previewCounts: HashMap<Int, Int>? = null
    private var previewPixelsW = 0
    private var previewPixelsH = 0
    private var previewPixelsSrc: Bitmap? = null
    private var previewParams: FloatArray? = null
    private var previewAlgo: PixelAlgorithm? = null
    private var previewCustomKernel: PixelAlgorithm? = null
    private var previewCustomSamples = 0

    /** 完整分辨率像素网格（色板 / 编码 / 导出使用）。 */
    var pixelGrid by mutableStateOf<PixelGrid?>(null)
        private set

    /** 完整网格是否需要重算。 */
    var gridDirty by mutableStateOf(true)
        private set

    /** 正在计算完整网格。 */
    var computingGrid by mutableStateOf(false)
        private set

    private var previewJob: Job? = null

    /** 后台同色计数任务（完成后回填预览缓存/网格）。 */
    private var previewCountsJob: Job? = null
    private var gridJob: Job? = null

    private var srcPixelsCache: IntArray? = null
    private var srcPixelsCacheFor: Bitmap? = null

    // ---------- 图片加载 ----------

    fun loadImage(uri: Uri) {
        DebugLog.log("loadImage: start uri=$uri")
        isLoading = true
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    BitmapLoader.loadBitmap(getApplication(), uri)
                } catch (t: Throwable) {
                    DebugLog.log("loadImage: decode exception: $t")
                    null
                }
            }
            DebugLog.log("loadImage: decoded=${bitmap?.width}x${bitmap?.height}")
            if (bitmap != null) {
                sourceBitmap?.recycle()
                sourceBitmap = bitmap
                // 换图：清理上一张图的所有派生状态，避免"换图后进画布编辑还是旧内容"
                previewJob?.cancel()
                gridJob?.cancel()
                croppedBitmap?.recycle()
                croppedBitmap = null
                srcPixelsCache = null
                srcPixelsCacheFor = null
                pixelPreview?.recycle()
                pixelPreview = null
                pixelGrid = null
                gridDirty = true
                isPixelated = false
                isLoading = false
                subScreen = SubScreen.Crop
            } else {
                isLoading = false
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.toast_load_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /** 重新裁剪当前图片。 */
    fun recrop() {
        if (sourceBitmap == null && croppedBitmap != null) {
            // 空白画布绘制的情况：把当前画布内容作为源图来裁剪
            sourceBitmap = croppedBitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        }
        if (sourceBitmap != null) {
            subScreen = SubScreen.Crop
        }
    }

    /** 应用裁剪结果（复刻旧版：裁剪后立即生成像素画）。 */
    fun applyCrop(bitmap: Bitmap) {
        croppedBitmap?.recycle()
        croppedBitmap = bitmap
        srcPixelsCache = null
        srcPixelsCacheFor = null
        isPixelated = false // 先关，避免 resetTransform 触发旧图刷新
        resetTransform()
        pixelPreview?.recycle()
        pixelPreview = null
        pixelGrid = null
        gridDirty = true
        subScreen = SubScreen.None
        tab = Tab.Home
        // 自动生成（复刻旧版 cropLauncher 回调 → generatePixelArt；可在设置中关闭）
        recordSizeHistory()
        if (autoGenerate) {
            isPixelated = true
            refreshPreviewDebounced()
        }
    }

    // ---------- 微调（复刻旧版：按钮步进 + 边界检查） ----------

    private fun requireImage(): Bitmap? = croppedBitmap

    /** 方向键：每次移动 0.1 比例（复刻旧版 MOVE_STEP_RATIO）。 */
    fun panStep(dx: Float, dy: Float) {
        val src = requireImage() ?: return
        val nx = offsetX + dx
        val ny = offsetY + dy
        if (isOutOfBounds(nx, ny, src)) {
            Toast.makeText(
                getApplication(),
                getApplication<Application>().getString(R.string.toast_edge_reached),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        offsetX = nx
        offsetY = ny
        onImageParamsChanged()
    }

    /** 拖拽调整模式：开启后可在预览框内拖动调整图片区域并重新生成。 */
    var dragAdjustEnabled by mutableStateOf(false)

    /** 拖拽调整无空间提示的时间戳（2.5 秒内只提示一次）。 */
    private var lastDragHintAt = 0L

    /** 拖拽调整：把预览框内的拖动像素量换算为采样偏移并重新生成像素画（内容跟随手指）。 */
    fun dragAdjustBy(dxPx: Float, dyPx: Float, boxW: Float, boxH: Float) {
        val src = requireImage() ?: return
        if (boxW <= 0f || boxH <= 0f) return
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val targetRatio = canvasWidth.toFloat() / canvasHeight.toFloat()
        val maxWinW: Float
        val maxWinH: Float
        if (srcW / srcH > targetRatio) {
            maxWinH = srcH
            maxWinW = srcH * targetRatio
        } else {
            maxWinW = srcW
            maxWinH = srcW / targetRatio
        }
        val winW = maxWinW / scale
        val winH = maxWinH / scale
        val limX = ((srcW - winW) / 2f / srcW).coerceAtLeast(0f)
        val limY = ((srcH - winH) / 2f / srcH).coerceAtLeast(0f)
        // 完全没有可移动空间（图片已完整覆盖采样窗口）：提示先放大
        if (limX <= 0f && limY <= 0f) {
            val now = System.currentTimeMillis()
            if (now - lastDragHintAt > 2500L) {
                lastDragHintAt = now
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.drag_zoom_first_hint),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }
        // 先把当前偏移约束到合法范围，再叠加拖动增量（内容跟随手指）
        offsetX = offsetX.coerceIn(-limX, limX)
        offsetY = offsetY.coerceIn(-limY, limY)
        val dOffX = -(dxPx / boxW) * (winW / srcW)
        val dOffY = -(dyPx / boxH) * (winH / srcH)
        val nx = (offsetX + dOffX).coerceIn(-limX, limX)
        val ny = (offsetY + dOffY).coerceIn(-limY, limY)
        if (nx == offsetX && ny == offsetY) return
        offsetX = nx
        offsetY = ny
        onImageParamsChanged()
    }

    /** 拖拽调整：双指捏合缩放采样区域，并重新生成像素画。 */
    fun dragAdjustZoom(factor: Float) {
        val src = requireImage() ?: return
        val ns = (scale * factor).coerceIn(MIN_SAMPLE_SCALE, MAX_SAMPLE_SCALE)
        if (ns == scale) return
        scale = ns
        // 缩放后把偏移重新约束到合法范围
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val targetRatio = canvasWidth.toFloat() / canvasHeight.toFloat()
        val maxWinW: Float
        val maxWinH: Float
        if (srcW / srcH > targetRatio) {
            maxWinH = srcH
            maxWinW = srcH * targetRatio
        } else {
            maxWinW = srcW
            maxWinH = srcW / targetRatio
        }
        val winW = maxWinW / scale
        val winH = maxWinH / scale
        val limX = ((srcW - winW) / 2f / srcW).coerceAtLeast(0f)
        val limY = ((srcH - winH) / 2f / srcH).coerceAtLeast(0f)
        offsetX = offsetX.coerceIn(-limX, limX)
        offsetY = offsetY.coerceIn(-limY, limY)
        onImageParamsChanged()
    }

    /** 放大（复刻旧版 ZOOM_STEP = +0.3）。 */
    fun zoomInStep() {
        requireImage() ?: return
        scale = (scale + ZOOM_STEP).coerceAtMost(MAX_SAMPLE_SCALE)
        onImageParamsChanged()
    }

    /** 缩小（复刻旧版：最小 0.1，低于时提示「已缩至最小」）。 */
    fun zoomOutStep() {
        requireImage() ?: return
        if (scale - ZOOM_STEP < MIN_SAMPLE_SCALE) {
            Toast.makeText(
                getApplication(),
                getApplication<Application>().getString(R.string.toast_min_scale),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        scale = (scale - ZOOM_STEP).coerceAtLeast(MIN_SAMPLE_SCALE)
        onImageParamsChanged()
    }

    /** 旋转（复刻旧版 ROTATE_STEP = 90°）。仅在画布为正方形时可用（非正方形旋转会变形）。 */
    fun rotateStep(deg: Float): Boolean {
        requireImage() ?: return false
        // 仅正方形画布允许旋转：非正方形旋转会导致像素画被裁切/变形
        if (canvasWidth != canvasHeight) return false
        rotation = ((rotation + deg) % 360f + 360f) % 360f
        // 关键：旋转是 90° 整数步进，重采样前先把「预览基准角」同步到新角度，
        // 否则显示层会算出 dispRot = 旧预览角 - 新角度（如 -90°），出现「先反向快速转一次再转回来」。
        previewRotation = rotation
        onImageParamsChanged()
        return true
    }

    /** 重置（复刻旧版 resetSample：无图时提示）。 */
    fun resetSample() {
        requireImage() ?: return
        resetTransform()
    }

    /** 采样窗口是否越界（复刻旧版 isOutOfBounds）。 */
    private fun isOutOfBounds(offsetX: Float, offsetY: Float, src: Bitmap): Boolean {
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val targetRatio = canvasWidth.toFloat() / canvasHeight.toFloat()
        val maxWinW: Float
        val maxWinH: Float
        if (srcW / srcH > targetRatio) {
            maxWinH = srcH
            maxWinW = srcH * targetRatio
        } else {
            maxWinW = srcW
            maxWinH = srcW / targetRatio
        }
        val winW = maxWinW / scale
        val winH = maxWinH / scale
        val centerX = srcW / 2f + offsetX * srcW
        val centerY = srcH / 2f + offsetY * srcH
        val left = centerX - winW / 2f
        val top = centerY - winH / 2f
        val right = left + winW
        val bottom = top + winH
        return left < 0f || top < 0f || right > srcW || bottom > srcH
    }

    fun resetTransform() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotation = 0f
        onImageParamsChanged()
    }

    /** 手势拖动中：只更新参数（不触发重采样，显示层做零成本变换预览）。 */
    fun panByPreview(dx: Float, dy: Float) {
        offsetX = (offsetX + dx).coerceIn(-2f, 2f)
        offsetY = (offsetY + dy).coerceIn(-2f, 2f)
    }

    /** 手势缩放中：只更新参数（不触发重采样）。 */
    fun zoomByPreview(factor: Float) {
        scale = (scale * factor).coerceIn(MIN_SAMPLE_SCALE, MAX_SAMPLE_SCALE)
    }

    /** 手势结束：提交参数并重新采样预览（参数未变则跳过）。 */
    fun commitTransform() {
        if (scale == previewScale && offsetX == previewOffsetX &&
            offsetY == previewOffsetY && rotation == previewRotation
        ) {
            return
        }
        previewJob?.cancel()
        if (isPixelated && croppedBitmap != null) {
            previewJob = viewModelScope.launch { computePreview() }
        }
        gridDirty = true
        pixelGrid = null
    }

    fun selectAlgorithm(algo: PixelAlgorithm) {
        if (algorithm == algo) return
        algorithm = algo
        onImageParamsChanged()
    }

    /** 选择/调整「自定义算法」：设置采样内核与采样密度并立即生效。 */
    fun selectCustomAlgorithm(kernel: PixelAlgorithm, samples: Int) {
        customKernel = if (kernel == PixelAlgorithm.Custom) PixelAlgorithm.Bilinear else kernel
        customSamples = samples.coerceIn(1, 8)
        algorithm = PixelAlgorithm.Custom
        onImageParamsChanged()
    }

    private fun onImageParamsChanged() {
        if (isPixelated && croppedBitmap != null) {
            refreshPreviewDebounced()
        }
        gridDirty = true
        pixelGrid = null
    }

    // ---------- 生成 ----------

    /** 点击「生成像素画」：首次生成时回到初始视角；已生成过则保留用户调整，只重新采样。 */
    fun generate() {
        if (croppedBitmap == null) return
        // 已生成像素画且比例与当前裁剪不一致（用户改了比例但没重新框选）→ 提示重新框选
        if (isPixelated && croppedBitmap != null) {
            val cropRatio = croppedBitmap!!.width.toDouble() / croppedBitmap!!.height
            val canvasRatio = canvasWidth.toDouble() / canvasHeight
            if (kotlin.math.abs(cropRatio - canvasRatio) > 0.0001) {
                pendingReCropHint = true
                return
            }
        }
        recordSizeHistory()
        // 本地统计 +1（仅存本机）
        localGenCount += 1
        prefs.edit().putInt("stats_gen_count", localGenCount).apply()
        // 仅在「尚未生成」时重置变换（复刻旧版首次生成行为）；
        // 已生成过则保留用户当前的缩放/平移/旋转，避免再次点击时画面被重置。
        if (!isPixelated) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            rotation = 0f
        }
        isPixelated = true
        refreshPreviewDebounced()
        gridDirty = true
        pixelGrid = null
    }

    /** 一次性提示标志：生成时因比例不符需要重新框选。 */
    var pendingReCropHint by mutableStateOf(false)
        private set

    fun consumeReCropHint() {
        pendingReCropHint = false
    }

    private fun refreshPreviewDebounced() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(140)
            computePreview()
        }
    }

    private suspend fun computePreview() {
        val src = croppedBitmap ?: return
        // 已是当前状态（来源/尺寸/参数/算法全部一致）→ 无需重算
        if (previewMatchesCurrent()) return
        val srcPixels = getSourcePixels(src) ?: return
        // 快照本次采样参数（保证「预览参数」与预览 Bitmap 内容严格一致）
        val s = scale
        val ox = offsetX
        val oy = offsetY
        val rot = rotation
        val algo = algorithm
        val (pw, ph) = Pixelator.previewSize(canvasWidth, canvasHeight, PREVIEW_MAX_EDGE)
        val px = withContext(Dispatchers.Default) {
            Pixelator.pixelate(
                src = srcPixels,
                srcW = src.width,
                srcH = src.height,
                outW = pw,
                outH = ph,
                scale = s,
                offsetX = ox,
                offsetY = oy,
                rotationDeg = rot,
                algorithm = algo,
                customKernel = customKernel,
                customSamples = customSamples,
            )
        }
        pixelPreview?.recycle()
        pixelPreview = BitmapLoader.toBitmap(px, pw, ph)
        // 缓存像素（与预览参数严格一致）；同色计数转后台统计，不阻塞显示
        previewPixels = px
        previewCounts = null
        previewPixelsW = pw
        previewPixelsH = ph
        previewPixelsSrc = src
        previewParams = floatArrayOf(s, ox, oy, rot)
        previewAlgo = algo
        previewCustomKernel = customKernel
        previewCustomSamples = customSamples
        scheduleCounts(px)
        // 记录预览参数：显示层据此计算「当前参数 vs 预览参数」的实时变换差
        previewScale = s
        previewOffsetX = ox
        previewOffsetY = oy
        previewRotation = rot
    }

    /**
     * 自定义算法实时预览：用小尺寸渲染当前图片（供算法设置弹窗使用）。
     * 建议在后台线程调用；无图片时返回 null。
     */
    fun renderCustomPreview(kernel: PixelAlgorithm, samples: Int, maxEdge: Int = 96): Bitmap? {
        val src = croppedBitmap ?: return null
        val srcPixels = getSourcePixels(src) ?: return null
        val maxDim = maxOf(canvasWidth, canvasHeight)
        val s = (maxEdge.toFloat() / maxDim).coerceAtMost(1f)
        val outW = maxOf(1, (canvasWidth * s).toInt())
        val outH = maxOf(1, (canvasHeight * s).toInt())
        val px = Pixelator.pixelate(
            src = srcPixels,
            srcW = src.width,
            srcH = src.height,
            outW = outW,
            outH = outH,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            rotationDeg = rotation,
            algorithm = PixelAlgorithm.Custom,
            customKernel = kernel,
            customSamples = samples,
        )
        return BitmapLoader.toBitmap(px, outW, outH)
    }

    private fun getSourcePixels(src: Bitmap): IntArray? {
        val cached = srcPixelsCache
        if (cached != null && srcPixelsCacheFor === src) return cached
        return try {
            val pixels = BitmapLoader.extractPixels(src)
            srcPixelsCache = pixels
            srcPixelsCacheFor = src
            pixels
        } catch (t: Throwable) {
            null
        }
    }

    // ---------- 完整像素网格 ----------

    /** 确保完整分辨率的像素网格已计算（供色板 / 编码 / 导出使用）。 */
    fun ensurePixelGrid(onReady: (() -> Unit)? = null) {
        if (!gridDirty && pixelGrid != null) {
            onReady?.invoke()
            return
        }
        val src = croppedBitmap
        if (src == null || !isPixelated) {
            onReady?.invoke()
            return
        }
        gridJob?.cancel()
        gridJob = viewModelScope.launch {
            computingGrid = true
            // 快速路径 1：与当前参数一致的预览缓存已就绪
            cachedGridForCurrent()?.let {
                pixelGrid = it
                gridDirty = false
                computingGrid = false
                onReady?.invoke()
                return@launch
            }
            // 快速路径 2：等待进行中的预览计算完成，直接复用其结果
            previewJob?.join()
            cachedGridForCurrent()?.let {
                pixelGrid = it
                gridDirty = false
                computingGrid = false
                onReady?.invoke()
                return@launch
            }
            // 主动刷新一次预览（同一套计算顺带填充缓存），再构建网格
            computePreview()
            cachedGridForCurrent()?.let {
                pixelGrid = it
                gridDirty = false
                computingGrid = false
                onReady?.invoke()
                return@launch
            }
            val srcPixels = getSourcePixels(src)
            if (srcPixels == null) {
                computingGrid = false
                return@launch
            }
            val w = canvasWidth
            val h = canvasHeight
            val pixels = withContext(Dispatchers.Default) {
                Pixelator.pixelate(
                    src = srcPixels,
                    srcW = src.width,
                    srcH = src.height,
                    outW = w,
                    outH = h,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    rotationDeg = rotation,
                    algorithm = algorithm,
                    customKernel = customKernel,
                    customSamples = customSamples,
                )
            }
            pixelGrid = PixelGrid(w, h, pixels, null)
            gridDirty = false
            computingGrid = false
            onReady?.invoke()
            scheduleCounts(pixels)
        }
    }

    /** 保存当前像素画到相册（自动确保完整网格已计算）。 */
    fun savePixelArtToAlbum() {
        val ctx = getApplication<Application>()
        ensurePixelGrid {
            val grid = pixelGrid
            if (grid == null) {
                Toast.makeText(ctx, ctx.getString(R.string.save_need_generate), Toast.LENGTH_SHORT).show()
            } else {
                viewModelScope.launch {
                    val maxDim = maxOf(grid.width, grid.height)
                    // 整数倍放大到约 1024（最近邻）：相册里保持像素锐利，不被平滑发糊
                    val scale = (1024 / maxDim).coerceIn(1, 64)
                    val uri = withContext(Dispatchers.Default) {
                        PngExporter.exportPng(
                            context = ctx,
                            pixels = grid.pixels,
                            width = grid.width,
                            height = grid.height,
                            fileName = "PixelColorPicker_${grid.width}x${grid.height}_${System.currentTimeMillis()}.png",
                            scale = scale,
                        )
                    }
                    Toast.makeText(
                        ctx,
                        ctx.getString(if (uri != null) R.string.saved_to_album else R.string.save_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    // ---------- 空白画布编辑器 ----------

    var editorArt by mutableStateOf(PixelArt.empty(16, 16))
        private set

    var editorSelected by mutableStateOf(-1)
        private set

    /** 最近一次「已保存到历史」的画布快照（用于判断退出时是否需要提示保存）。 */
    private var lastSavedArt: IntArray? = null

    /** 最近一次保存写入的历史文件（被删除后标记自动失效）。 */
    private var lastSavedFile: java.io.File? = null

    /** 本次进入编辑器后是否已建立 baseline（用于区分"还没载入完"与"已载入且无修改"）。 */
    private var editorLoaded = false

    /** 是否有可保存的内容（画布里有非透明像素）。 */
    private fun editorHasContent(): Boolean {
        val px = editorArt.snapshot()
        for (p in px) if ((p ushr 24) and 0xFF > 0) return true
        return false
    }

    /**
     * 退出编辑器时是否需要询问保存。
     *
     * 规则：**只有相对上次保存/载入有修改时才询问**；没有修改（或尚未载入）则静默退出，
     * 避免重复弹窗与重复保存同一幅画。
     */
    fun editorNeedsSavePrompt(): Boolean {
        val loaded = editorLoaded
        val saved = lastSavedArt
        val fileOk = lastSavedFile?.exists() == true
        val changed = if (!loaded) {
            false
        } else if (saved == null || !fileOk) {
            // 从未保存过，或保存的历史文件已被删除：有内容就提示
            editorHasContent()
        } else {
            !editorArt.snapshot().contentEquals(saved)
        }
        com.pixelcolorpicker.util.DebugLog.log(
            "exitPrompt: loaded=$loaded fileOk=$fileOk changed=$changed (art=${editorArt.width}x${editorArt.height})",
        )
        return changed
    }

    var editorColor by mutableStateOf(0xFFFFFFFF.toInt())
        private set

    var editorTool by mutableStateOf(EditorTool.Pencil)
        private set

    /** 打开画布编辑：已有像素画则编辑它，否则新建空白画布。 */
    fun openEditor() {
        com.pixelcolorpicker.util.DebugLog.log(
            "openEditor: start gridDirty=$gridDirty pixelGrid=${pixelGrid != null} " +
                "isPixelated=$isPixelated cropped=${croppedBitmap != null} " +
                "canvas=${canvasWidth}x$canvasHeight",
        )
        subScreen = SubScreen.Editor
        editorSelected = -1
        editorLoaded = false
        viewModelScope.launch {
            // 直接同步计算网格（不用异步任务，彻底避免竞态/取消导致 gridDirty 卡住）
            val grid = computePixelGridNow()
            com.pixelcolorpicker.util.DebugLog.log(
                "openEditor: computed grid=${grid != null} subScreen=$subScreen",
            )
            if (subScreen != SubScreen.Editor) return@launch // 用户已退出
            if (grid != null) {
                editorArt = PixelArt.from(grid.width, grid.height, grid.pixels)
            } else {
                editorArt = PixelArt.empty(canvasWidth, canvasHeight)
            }
            editorLoaded = true
            editorSelected = -1
            com.pixelcolorpicker.util.DebugLog.log(
                "openEditor: loaded ${editorArt.width}x${editorArt.height} loaded=true",
            )
        }
    }

    /**
     * 预览缓存是否已与当前状态一致（来源图 / 输出尺寸 / 采样参数 / 算法）。
     * 一致时无需重新像素化。
     */
    private fun previewMatchesCurrent(): Boolean {
        val src = croppedBitmap ?: return false
        if (previewPixels == null) return false
        if (previewPixelsSrc !== src) return false
        if (previewAlgo != algorithm) return false
        if (previewCustomKernel != customKernel || previewCustomSamples != customSamples) return false
        val p = previewParams ?: return false
        if (p[0] != scale || p[1] != offsetX || p[2] != offsetY || p[3] != rotation) return false
        val (pw, ph) = Pixelator.previewSize(canvasWidth, canvasHeight, PREVIEW_MAX_EDGE)
        if (previewPixelsW != pw || previewPixelsH != ph) return false
        return true
    }

    /**
     * 后台统计同色计数并回填（不阻塞主流程）。
     * 完成后写入预览缓存；若当前网格用的正是这份像素，则同步回填网格（色板 ×N 自动刷新）。
     */
    private fun scheduleCounts(pixels: IntArray) {
        previewCountsJob?.cancel()
        previewCountsJob = viewModelScope.launch {
            val counts = withContext(Dispatchers.Default) { PixelGrid.computeCounts(pixels) }
            if (previewPixels === pixels) previewCounts = counts
            pixelGrid?.let { g -> if (g.pixels === pixels) g.updateCounts(counts) }
        }
    }

    /**
     * 若缓存的预览像素与当前参数完全一致，则直接构建完整网格（跳过全量像素化）。
     * 参数包括：来源图、画布尺寸、采样缩放/平移/旋转、算法。
     */
    private fun cachedGridForCurrent(): PixelGrid? {
        val px = previewPixels ?: return null
        val src = croppedBitmap ?: return null
        if (previewPixelsSrc !== src) return null
        val w = canvasWidth
        val h = canvasHeight
        if (previewPixelsW != w || previewPixelsH != h) return null
        val p = previewParams ?: return null
        if (p[0] != scale || p[1] != offsetX || p[2] != offsetY || p[3] != rotation) return null
        if (previewAlgo != algorithm) return null
        if (previewCustomKernel != customKernel || previewCustomSamples != customSamples) return null
        return PixelGrid(w, h, px, previewCounts)
    }

    /**
     * 同步计算完整像素网格（阻塞直到完成）。
     * 与 ensurePixelGrid 的区别：不使用可被取消的后台任务，避免 gridDirty 永久为 true。
     * 无像素画时返回 null。
     */
    private suspend fun computePixelGridNow(): PixelGrid? {
        // 已就绪且干净：直接复用
        if (!gridDirty && pixelGrid != null) return pixelGrid
        val src = croppedBitmap ?: return null
        if (!isPixelated) return null
        // 快速路径 1：与当前参数一致的预览缓存已就绪
        cachedGridForCurrent()?.let {
            pixelGrid = it
            gridDirty = false
            return it
        }
        // 快速路径 2：等待进行中的预览计算完成，直接复用其结果（避免重复全量像素化）
        previewJob?.join()
        cachedGridForCurrent()?.let {
            pixelGrid = it
            gridDirty = false
            return it
        }
        // 主动刷新一次预览（同一套计算顺带填充缓存），再构建网格
        computePreview()
        cachedGridForCurrent()?.let {
            pixelGrid = it
            gridDirty = false
            return it
        }
        // 兜底：预览计算未产出（图源异常等）时独立计算
        val srcPixels = getSourcePixels(src) ?: return null
        val w = canvasWidth
        val h = canvasHeight
        val s = scale
        val ox = offsetX
        val oy = offsetY
        val rot = rotation
        val algo = algorithm
        val px = withContext(Dispatchers.Default) {
            Pixelator.pixelate(
                src = srcPixels,
                srcW = src.width,
                srcH = src.height,
                outW = w,
                outH = h,
                scale = s,
                offsetX = ox,
                offsetY = oy,
                rotationDeg = rot,
                algorithm = algo,
                customKernel = customKernel,
                customSamples = customSamples,
            )
        }
        val grid = PixelGrid(w, h, px, null)
        pixelGrid = grid
        gridDirty = false
        scheduleCounts(px)
        return grid
    }

    /** 保存编辑器画布：写入首页展示 + 记录保存历史（不退出编辑器）。 */
    fun saveEditorArt() {
        applyEditorArtToHome()
        // 仅当「内容有变化」或「历史文件已被删除」时才写入历史，避免重复保存
        val art = editorArt
        val px = art.snapshot()
        val saved = lastSavedArt
        val stillSaved = saved != null && lastSavedFile?.exists() == true && px.contentEquals(saved)
        if (!stillSaved) {
            lastSavedFile = persistHistory(px, art.width, art.height)
            lastSavedArt = px
        }
    }

    /**
     * 把编辑器画布应用到首页展示（不写保存历史、不退出编辑器）。
     * 用于编辑器里的「添加图片到首页」。
     */
    fun addEditorArtToHome() {
        applyEditorArtToHome()
    }

    /** 把编辑器画布同步到首页展示（内部共用）。 */
    private fun applyEditorArtToHome() {
        val art = editorArt
        val pixels = art.snapshot()
        val bmp = try {
            android.graphics.Bitmap.createBitmap(
                art.width, art.height, android.graphics.Bitmap.Config.ARGB_8888,
            ).apply {
                setPixels(pixels, 0, art.width, 0, 0, art.width, art.height)
            }
        } catch (t: Throwable) {
            return
        }
        // 取消进行中的预览 / 网格任务，避免异步结果覆盖
        previewJob?.cancel()
        gridJob?.cancel()
        croppedBitmap?.recycle()
        croppedBitmap = bmp
        srcPixelsCache = null
        srcPixelsCacheFor = null
        // 采样参数归零（显示端按 1:1 绘制预览）
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotation = 0f
        isPixelated = true
        // 预览：直接用画布位图副本（保持锐利、颜色不变）
        pixelPreview?.recycle()
        pixelPreview = try {
            bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        } catch (t: Throwable) {
            null
        }
        previewScale = 1f
        previewOffsetX = 0f
        previewOffsetY = 0f
        previewRotation = 0f
        // 完整网格：直接采用画布像素（颜色零变化）；计数后台统计
        pixelGrid = PixelGrid(art.width, art.height, pixels, null)
        gridDirty = false
        // 同步预览缓存：来源=画布位图本身、参数已归零、像素=画布像素（进入编辑器可直接复用）
        previewPixels = pixels
        previewPixelsW = art.width
        previewPixelsH = art.height
        previewPixelsSrc = bmp
        previewParams = floatArrayOf(1f, 0f, 0f, 0f)
        previewAlgo = algorithm
        previewCounts = null
        scheduleCounts(pixels)
        recordSizeHistory()
    }

    // ---------- 自定义色板（编辑器，长期保存） ----------

    /** 自定义色板（最新在前，最多 24 个）。 */
    val customPalette = mutableStateListOf<Int>()

    init {
        val saved = prefs.getString("editor_palette", "") ?: ""
        if (saved.isNotEmpty()) {
            saved.split(",").forEach { s -> s.trim().toIntOrNull()?.let { customPalette.add(it) } }
        }
    }

    private fun saveEditorPalette() {
        prefs.edit().putString("editor_palette", customPalette.joinToString(",")).apply()
    }

    /** 把当前颜色加入色板（已存在则移到最前；最多 24 个）。 */
    fun addEditorPaletteColor() {
        val c = editorColor
        if (customPalette.contains(c)) customPalette.remove(c)
        customPalette.add(0, c)
        while (customPalette.size > 24) customPalette.removeAt(customPalette.size - 1)
        saveEditorPalette()
    }

    /** 从色板移除颜色。 */
    fun removeEditorPaletteColor(c: Int) {
        customPalette.remove(c)
        saveEditorPalette()
    }

    // ---------- 保存历史（磁盘持久化） ----------

    /** 历史记录列表（倒序，最新在前）。 */
    val historyEntries = mutableStateListOf<HistoryEntry>()

    /** 打开保存历史界面。 */
    fun openHistory() {
        refreshHistory()
        subScreen = SubScreen.History
    }

    /** 刷新历史列表（扫描磁盘目录）。 */
    fun refreshHistory() {
        val dir = historyDir() ?: return
        val list = dir.listFiles { f -> f.name.startsWith("hist_") && f.name.endsWith(".png") }
            ?.mapNotNull { parseHistoryFile(it) }
            ?.sortedByDescending { it.time }
            ?: emptyList()
        historyEntries.clear()
        historyEntries.addAll(list)
    }

    /** 删除一条历史记录。 */
    fun deleteHistoryEntry(entry: HistoryEntry) {
        entry.file.delete()
        refreshHistory()
    }

    /** 载入历史记录到编辑器。 */
    fun loadHistoryEntry(entry: HistoryEntry) {
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                try {
                    val bmp = android.graphics.BitmapFactory.decodeFile(entry.file.absolutePath)
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    bmp.recycle()
                    px
                } catch (t: Throwable) {
                    null
                }
            } ?: return@launch
            editorArt = PixelArt.from(entry.width, entry.height, decoded)
            // 从历史载入 = 已保存过，退出时不再提示保存
            lastSavedArt = editorArt.snapshot()
            lastSavedFile = entry.file
            editorSelected = -1
            subScreen = SubScreen.Editor
        }
    }

    private fun historyDir(): java.io.File? {
        val d = java.io.File(getApplication<Application>().filesDir, "history")
        if (!d.exists() && !d.mkdirs()) return null
        return d
    }

    private fun parseHistoryFile(f: java.io.File): HistoryEntry? {
        // hist_<time>_<w>x<h>.png
        val parts = f.nameWithoutExtension.split("_")
        if (parts.size < 3) return null
        val time = parts[1].toLongOrNull() ?: return null
        val dims = parts[2].split("x")
        val w = dims.getOrNull(0)?.toIntOrNull() ?: return null
        val h = dims.getOrNull(1)?.toIntOrNull() ?: return null
        return HistoryEntry(f, time, w, h)
    }

    /** 持久化保存（PNG 写入 files/history）。返回写入的文件（失败返回 null）。 */
    private fun persistHistory(pixels: IntArray, w: Int, h: Int): java.io.File? {
        val dir = historyDir() ?: return null
        val file = java.io.File(dir, "hist_${System.currentTimeMillis()}_${w}x${h}.png")
        return try {
            val bmp = BitmapLoader.toBitmap(pixels, w, h)
            java.io.FileOutputStream(file).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
            file
        } catch (t: Throwable) {
            file.delete()
            null
        }
    }

    fun editorSetColor(color: Int) {
        editorColor = color
    }

    fun editorSetTool(tool: EditorTool) {
        editorTool = tool
    }

    fun editorSelect(x: Int, y: Int) {
        editorSelected = y * editorArt.width + x
    }

    fun editorTouch(x: Int, y: Int) {
        when (editorTool) {
            EditorTool.Pencil -> {
                editorArt.setPixel(x, y, editorColor)
                editorSelected = y * editorArt.width + x
            }
            EditorTool.Eraser -> {
                editorArt.setPixel(x, y, 0x00000000)
                editorSelected = y * editorArt.width + x
            }
            EditorTool.Eyedropper -> {
                val c = editorArt.get(x, y)
                if ((c ushr 24) and 0xFF > 0) editorColor = c
                editorSelected = y * editorArt.width + x
            }
            EditorTool.Fill -> {
                editorFloodFill(x, y, editorColor)
                editorSelected = y * editorArt.width + x
            }
        }
    }

    /** 开始一次笔画（拖动期间撤销合并为整笔）。 */
    fun editorStrokeStart() = editorArt.beginStroke()

    /** 结束笔画。 */
    fun editorStrokeEnd() = editorArt.endStroke()

    fun editorUndo() = editorArt.undo()

    fun editorRedo() = editorArt.redo()

    fun editorClear() = editorArt.clear()

    fun editorFillAll() = editorArt.fill(editorColor)

    private fun editorFloodFill(startX: Int, startY: Int, newColor: Int) {
        val target = editorArt.get(startX, startY)
        if (target == newColor) return
        val w = editorArt.width
        val h = editorArt.height
        val visited = BooleanArray(w * h)
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(startX to startY)
        val updates = mutableListOf<Triple<Int, Int, Int>>()
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeLast()
            if (x !in 0 until w || y !in 0 until h) continue
            val idx = y * w + x
            if (visited[idx]) continue
            if (editorArt.get(x, y) != target) continue
            visited[idx] = true
            updates.add(Triple(x, y, newColor))
            stack.addLast(x + 1 to y)
            stack.addLast(x - 1 to y)
            stack.addLast(x to y + 1)
            stack.addLast(x to y - 1)
        }
        editorArt.setPixels(updates)
    }

    override fun onCleared() {
        super.onCleared()
        sourceBitmap?.recycle()
        sourceBitmap = null
        croppedBitmap?.recycle()
        croppedBitmap = null
        pixelPreview?.recycle()
        pixelPreview = null
    }

    companion object {
        /** 主题模式：跟随系统 / 浅色 / 深色。 */
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        /** 展示框预览的最大边长（1024：保证 ≤1024 画布的预览与编辑器显示完全一致）。 */
        private const val PREVIEW_MAX_EDGE = 1024

        /** 画布尺寸上限。 */
        private const val MAX_CANVAS_SIZE = 999

        /** 缩放步进（复刻旧版 ZOOM_STEP）。 */
        private const val ZOOM_STEP = 0.3f

        /** 最小缩放（1 = 完整显示整张图；再小会超出图片边缘范围，故不允许）。 */
        private const val MIN_SAMPLE_SCALE = 1f

        /** 最大缩放（性能保护）。 */
        private const val MAX_SAMPLE_SCALE = 12f
    }
}
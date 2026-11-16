package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.ai.ImageSegmentationEngine
import com.example.core.database.MediaItemEntity
import com.example.core.localization.BilingualStrings
import com.example.core.media.DrawingPathData
import com.example.core.media.PhotoEditPipelineState
import com.example.core.media.PhotoProcessingEngine
import com.example.core.media.StickerItemData
import com.example.ui.TakzGalleryViewModel
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

@Composable
fun PhotoEditorScreen(
    item: MediaItemEntity,
    initialTab: String,
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings
) {
    BackHandler { viewModel.closePhotoEditor() }

    val resolvedInitialTab = remember(initialTab, item.isVideo) {
        if (item.isVideo && initialTab == "ADJUST") "VIDEO" else initialTab
    }
    var activeTab by remember(resolvedInitialTab) { mutableStateOf(resolvedInitialTab) }
    var sourceBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var renderedBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var fastSourceBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var isPreviewRendering by remember(item.id) { mutableStateOf(false) }
    var showOriginalBefore by remember { mutableStateOf(false) }

    // Non-destructive PhotoEditPipelineState with Undo/Redo stack
    var pipelineState by remember(item.id) {
        mutableStateOf(PhotoEditPipelineState.deserialize(item.editPipelineState))
    }
    val undoStack = remember { mutableStateListOf<PhotoEditPipelineState>() }
    val redoStack = remember { mutableStateListOf<PhotoEditPipelineState>() }
    var lastAdjustmentAt by remember(item.id) { mutableLongStateOf(0L) }

    fun updatePipeline(newState: PhotoEditPipelineState) {
        if (newState == pipelineState) return
        val now = android.os.SystemClock.uptimeMillis()
        // A slider gesture belongs to ONE undo entry, rather than 60+ entries a second.
        if (undoStack.isEmpty() || now - lastAdjustmentAt > 650L) {
            undoStack.add(pipelineState)
            if (undoStack.size > 25) undoStack.removeAt(0)
        }
        lastAdjustmentAt = now
        redoStack.clear()
        pipelineState = newState
    }

    // Samsung One UI Video Studio state
    var videoTrimStart by remember(item.id) { mutableFloatStateOf(0.0f) }
    var videoTrimEnd by remember(item.id) { mutableFloatStateOf(1.0f) }
    var videoSpeed by remember(item.id) { mutableFloatStateOf(1.0f) }
    var videoTargetResolution by remember(item.id) {
        mutableStateOf(
            when {
                item.width >= 3800 -> "4K"
                item.width >= 1900 -> "1080p"
                else -> "720p"
            }
        )
    }
    var videoMuteAudio by remember(item.id) { mutableStateOf(false) }

    // AI Cutout & Object Eraser sub-mode ("CUTOUT", "SPOT_ERASER", "COLOR_POP")
    var aiToolMode by remember { mutableStateOf("CUTOUT") }
    var bgMode by remember { mutableStateOf("TRANSPARENT") } // "TRANSPARENT", "SOLID", "BLUR"
    var bgSolidColorInt by remember { mutableIntStateOf(android.graphics.Color.parseColor("#3E82F7")) }
    var featherRadius by remember { mutableIntStateOf(3) }
    var maskSmoothing by remember { mutableIntStateOf(2) }
    var brushIsRestore by remember { mutableStateOf(false) }
    var brushRadiusNorm by remember { mutableFloatStateOf(0.06f) }
    var brushHardness by remember { mutableFloatStateOf(0.8f) }
    val manualMaskStrokes = remember { mutableStateListOf<ImageSegmentationEngine.BrushStroke>() }

    fun saveCurrentCutout(overwrite: Boolean, format: String = "PNG") {
        viewModel.saveAiCutout(
            item = item,
            pipelineState = pipelineState,
            mode = bgMode,
            solidColor = bgSolidColorInt,
            featherRadius = featherRadius,
            smoothingLevel = maskSmoothing,
            strokes = manualMaskStrokes.toList(),
            format = format,
            overwrite = overwrite
        )
    }
    var segmentedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Pen & Sticker state
    var activePenColorInt by remember { mutableIntStateOf(android.graphics.Color.parseColor("#3E82F7")) }
    var activePenThickness by remember { mutableFloatStateOf(0.012f) }
    // Overlay stroke points are drawn directly by Compose; image pixels render
    // only once after the gesture finishes, avoiding costly work on drag frames.
    val livePenStroke = remember(item.id) { mutableStateListOf<Pair<Float, Float>>() }

    // Decode off the UI thread. A cached 420px working copy provides immediate
    // low-cost previews while the 960px source gives a detailed settled preview.
    LaunchedEffect(item.id) {
        sourceBitmap = null
        renderedBitmap = null
        fastSourceBitmap = null
        val loaded = withContext(Dispatchers.IO) {
            viewModel.libraryManager.loadSampledBitmap(item.contentUri, maxDimension = 960)
        }
        sourceBitmap = loaded
        fastSourceBitmap = loaded?.let { original ->
            withContext(Dispatchers.Default) {
                val longest = maxOf(original.width, original.height)
                if (longest <= 420) original else {
                    val scale = 420f / longest
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * scale).toInt().coerceAtLeast(1),
                        (original.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                }
            }
        }
    }

    // Latest-state-wins preview: obsolete renders cannot replace newer adjustments.
    // During slider movement only the 420px preview is computed. Once the gesture
    // settles, perform the same image pipeline at 960px with the original source.
    LaunchedEffect(sourceBitmap, fastSourceBitmap, pipelineState) {
        val src = sourceBitmap ?: return@LaunchedEffect
        val quick = fastSourceBitmap ?: return@LaunchedEffect
        if (pipelineState.isIdentity()) {
            renderedBitmap = src
            isPreviewRendering = false
            return@LaunchedEffect
        }
        isPreviewRendering = true
        try {
            delay(45L)
            val draft = withContext(Dispatchers.Default) {
                PhotoProcessingEngine.renderPipeline(quick, pipelineState) { coroutineContext[Job]?.isActive == false }
            }
            renderedBitmap = draft
            delay(240L)
            if (quick !== src) {
                val refined = withContext(Dispatchers.Default) {
                    PhotoProcessingEngine.renderPipeline(src, pipelineState) { coroutineContext[Job]?.isActive == false }
                }
                renderedBitmap = refined
            }
        } finally {
            isPreviewRendering = false
        }
    }

    // Recompute AI Background Removal preview when parameters or strokes change
    LaunchedEffect(
        sourceBitmap,
        renderedBitmap,
        activeTab,
        aiToolMode,
        bgMode,
        bgSolidColorInt,
        featherRadius,
        maskSmoothing,
        manualMaskStrokes.size
    ) {
        if (activeTab != "BG_REMOVE" || aiToolMode != "CUTOUT") {
            segmentedPreviewBitmap = null
            return@LaunchedEffect
        }
        delay(100L)
        val src = renderedBitmap ?: sourceBitmap ?: return@LaunchedEffect
        val strokesSnapshot = manualMaskStrokes.toList()
        withContext(Dispatchers.Default) {
            val alphaMask = viewModel.segmentationEngine.generateAlphaMask(
                source = src,
                featherRadiusPx = featherRadius,
                smoothingLevel = maskSmoothing,
                manualStrokes = strokesSnapshot
            )
            segmentedPreviewBitmap = viewModel.segmentationEngine.compositeBackground(
                source = src,
                alphaMask = alphaMask,
                mode = bgMode,
                solidColor = bgSolidColorInt
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("photo_editor_screen")
    ) {
        // Samsung One UI Top Editor Bar
        Surface(
            color = Color(0xFF141417),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.closePhotoEditor() },
                        modifier = Modifier.testTag("editor_close_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column {
                        Text(
                            text = when (activeTab) {
                                "VIDEO" -> strings.tr("One UI Video Studio", "ویرایشگر ویدیو سامسونگ")
                                "BG_REMOVE" -> strings.tr("AI Cutout & Object Eraser", "برش سوژه و پاک‌کن هوشمند")
                                else -> strings.tr("One UI Photo Studio", "ویرایشگر حرفه‌ای تصویر")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        val outW = (item.width * pipelineState.resizeScalePercent) / 100
                        val outH = (item.height * pipelineState.resizeScalePercent) / 100
                        Text(
                            text = "${outW}×${outH} • ${item.fileName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF9EA3AE),
                            maxLines = 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Undo
                    IconButton(
                        onClick = {
                            if (activeTab == "BG_REMOVE" && aiToolMode == "CUTOUT" && manualMaskStrokes.isNotEmpty()) {
                                manualMaskStrokes.removeAt(manualMaskStrokes.lastIndex)
                            } else if (undoStack.isNotEmpty()) {
                                val prev = undoStack.removeAt(undoStack.lastIndex)
                                redoStack.add(pipelineState)
                                pipelineState = prev
                                lastAdjustmentAt = 0L
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color.White)
                    }

                    // Redo
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.removeAt(redoStack.lastIndex)
                                undoStack.add(pipelineState)
                                pipelineState = next
                                lastAdjustmentAt = 0L
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = Color.White)
                    }

                    // Before / After Toggle
                    IconButton(
                        onClick = { showOriginalBefore = !showOriginalBefore },
                        modifier = Modifier.testTag("before_after_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Before/After",
                            tint = if (showOriginalBefore) Color(0xFF3E82F7) else Color.White
                        )
                    }

                    // Reset All
                    IconButton(
                        onClick = {
                            manualMaskStrokes.clear()
                            featherRadius = 3
                            maskSmoothing = 2
                            videoTrimStart = 0f
                            videoTrimEnd = 1f
                            videoSpeed = 1f
                            updatePipeline(PhotoEditPipelineState())
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
                    }

                    // Samsung One UI Dual Save Controls: "Save Copy" + "Save (Overwrite Original)"
                    FilledTonalButton(
                        onClick = {
                            val bmp = if (activeTab == "BG_REMOVE" && aiToolMode == "CUTOUT") {
                                segmentedPreviewBitmap ?: renderedBitmap ?: sourceBitmap
                            } else {
                                renderedBitmap ?: sourceBitmap
                            }
                            if (!item.isVideo && activeTab == "BG_REMOVE" && aiToolMode == "CUTOUT") {
                                saveCurrentCutout(overwrite = false)
                            } else if (bmp != null) {
                                if (item.isVideo || activeTab == "VIDEO") {
                                    viewModel.saveEditedVideoAndExport(
                                        item = item,
                                        pipelineState = pipelineState,
                                        previewBitmap = bmp,
                                        trimStartRatio = videoTrimStart,
                                        trimEndRatio = videoTrimEnd,
                                        playbackSpeed = videoSpeed,
                                        targetResolutionLabel = videoTargetResolution,
                                        muteAudio = videoMuteAudio
                                    )
                                } else {
                                    viewModel.saveNonDestructiveEditAndExport(item, pipelineState, bmp, "JPEG")
                                }
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .testTag("save_copy_top_button")
                    ) {
                        Text(strings.tr("Save Copy", "ذخیره کپی"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val bmp = if (activeTab == "BG_REMOVE" && aiToolMode == "CUTOUT") {
                                segmentedPreviewBitmap ?: renderedBitmap ?: sourceBitmap
                            } else {
                                renderedBitmap ?: sourceBitmap
                            }
                            if (!item.isVideo && activeTab == "BG_REMOVE" && aiToolMode == "CUTOUT") {
                                saveCurrentCutout(overwrite = true)
                            } else if (bmp != null) {
                                if (item.isVideo || activeTab == "VIDEO") {
                                    viewModel.saveOverwriteOriginalVideo(
                                        item = item,
                                        pipelineState = pipelineState,
                                        previewBitmap = bmp,
                                        trimStartRatio = videoTrimStart,
                                        trimEndRatio = videoTrimEnd,
                                        playbackSpeed = videoSpeed,
                                        targetResolutionLabel = videoTargetResolution,
                                        muteAudio = videoMuteAudio
                                    )
                                } else {
                                    viewModel.saveOverwriteOriginalPhoto(
                                        item = item,
                                        pipelineState = pipelineState,
                                        processedBitmap = bmp,
                                        format = if (activeTab == "BG_REMOVE" && aiToolMode == "CUTOUT") "PNG" else "JPEG"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .testTag("save_overwrite_original_button")
                    ) {
                        Text(strings.tr("Save", "ذخیره اصلی"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Interactive Preview Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
                .clip(RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Transparency checkerboard background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tile = 20.dp.toPx()
                val cols = (size.width / tile).toInt() + 1
                val rows = (size.height / tile).toInt() + 1
                for (r in 0..rows) {
                    for (c in 0..cols) {
                        val isLight = (r + c) % 2 == 0
                        drawRect(
                            color = if (isLight) Color(0xFF1E2028) else Color(0xFF14151A),
                            topLeft = Offset(c * tile, r * tile),
                            size = Size(tile, tile)
                        )
                    }
                }
            }

            val displayBitmap = when {
                showOriginalBefore -> sourceBitmap
                activeTab == "BG_REMOVE" && aiToolMode == "CUTOUT" -> segmentedPreviewBitmap ?: renderedBitmap ?: sourceBitmap
                else -> renderedBitmap ?: sourceBitmap
            }

            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(
                            displayBitmap,
                            activeTab,
                            pipelineState,
                            aiToolMode,
                            brushIsRestore,
                            brushRadiusNorm,
                            brushHardness,
                            activePenColorInt,
                            activePenThickness
                        ) {
                            if (activeTab == "BG_REMOVE" && aiToolMode == "CUTOUT") {
                                detectTapGestures { pos ->
                                    val (nx, ny) = mapPreviewTouch(pos, size.width, size.height, displayBitmap)
                                        ?: return@detectTapGestures
                                    manualMaskStrokes.add(
                                        ImageSegmentationEngine.BrushStroke(
                                            normX = nx,
                                            normY = ny,
                                            radiusNorm = brushRadiusNorm,
                                            isRestore = brushIsRestore,
                                            hardness = brushHardness
                                        )
                                    )
                                }
                            } else if (activeTab == "BG_REMOVE" && aiToolMode == "SPOT_ERASER") {
                                detectTapGestures { pos ->
                                    val (nx, ny) = mapPreviewTouch(pos, size.width, size.height, displayBitmap)
                                        ?: return@detectTapGestures
                                    val updatedSpots = pipelineState.spotHealPoints + (nx to ny)
                                    updatePipeline(pipelineState.copy(spotHealPoints = updatedSpots))
                                }
                            } else if (activeTab == "ANNOTATE") {
                                detectTapGestures { pos ->
                                    if (pipelineState.placedStickers.isNotEmpty()) {
                                        val (rawX, rawY) = mapPreviewTouch(pos, size.width, size.height, displayBitmap)
                                            ?: return@detectTapGestures
                                        val nx = rawX.coerceIn(0.1f, 0.9f)
                                        val ny = rawY.coerceIn(0.15f, 0.9f)
                                        val list = pipelineState.placedStickers.toMutableList()
                                        val last = list.last()
                                        list[list.lastIndex] = last.copy(normX = nx, normY = ny)
                                        updatePipeline(pipelineState.copy(placedStickers = list))
                                    }
                                }
                            }
                        }
                        .pointerInput(activeTab, displayBitmap, activePenColorInt, activePenThickness, pipelineState) {
                            if (activeTab == "ANNOTATE") {
                                val strokePoints = mutableListOf<Pair<Float, Float>>()
                                detectDragGestures(
                                    onDragStart = { start ->
                                        strokePoints.clear()
                                        livePenStroke.clear()
                                        mapPreviewTouch(start, size.width, size.height, displayBitmap)
                                            ?.let {
                                                strokePoints.add(it)
                                                livePenStroke.add(it)
                                            }
                                    },
                                    onDragEnd = {
                                        if (strokePoints.size >= 2) {
                                            updatePipeline(pipelineState.copy(
                                                drawingPaths = pipelineState.drawingPaths + DrawingPathData(
                                                    pointsNorm = strokePoints.toList(),
                                                    colorInt = activePenColorInt,
                                                    strokeWidthNorm = activePenThickness
                                                )
                                            ))
                                        }
                                        strokePoints.clear()
                                        livePenStroke.clear()
                                    },
                                    onDragCancel = {
                                        strokePoints.clear()
                                        livePenStroke.clear()
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        mapPreviewTouch(change.position, size.width, size.height, displayBitmap)
                                            ?.let {
                                                strokePoints.add(it)
                                                livePenStroke.add(it)
                                            }
                                    }
                                )
                            }
                        }
                )
            }

            if (activeTab == "ANNOTATE" && livePenStroke.size > 1 && displayBitmap != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scale = min(
                        size.width / displayBitmap.width,
                        size.height / displayBitmap.height
                    )
                    val dw = displayBitmap.width * scale
                    val dh = displayBitmap.height * scale
                    val ox = (size.width - dw) / 2f
                    val oy = (size.height - dh) / 2f
                    val path = Path()
                    val first = livePenStroke.first()
                    path.moveTo(ox + first.first * dw, oy + first.second * dh)
                    for (pt in livePenStroke.drop(1)) {
                        path.lineTo(ox + pt.first * dw, oy + pt.second * dh)
                    }
                    drawPath(
                        path = path,
                        color = Color(activePenColorInt),
                        style = Stroke(
                            width = (activePenThickness * min(dw, dh)).coerceAtLeast(1f),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            if (isPreviewRendering && !showOriginalBefore) {
                Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Text(
                        strings.tr("Updating preview…", "در حال به‌روزرسانی پیش‌نمایش…"),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (showOriginalBefore) {
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                ) {
                    Text(
                        text = strings.tr("ORIGINAL (Before)", "تصویر اصلی (قبل از ویرایش)"),
                        color = Color(0xFF3E82F7),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Samsung One UI Bottom Pure-Icon Tool Dock (No Text Clutter)
        Surface(
            color = Color(0xFF17171A),
            modifier = Modifier.fillMaxWidth()
        ) {
            val editorTabs = listOf(
                Triple("VIDEO", strings.tr("Video Studio", "برش ویدیو"), Icons.Default.Videocam),
                Triple("CROP", strings.tr("Crop", "برش"), Icons.Default.CropRotate),
                Triple("ADJUST", strings.tr("Tone", "نور و رنگ"), Icons.Default.Tune),
                Triple("FILTERS", strings.tr("Filters", "فیلترها"), Icons.Default.FilterVintage),
                Triple("BG_REMOVE", strings.tr("Cutout & Eraser", "حذف پس‌زمینه"), Icons.Default.AutoFixHigh),
                Triple("ANNOTATE", strings.tr("Stickers & Pen", "استیکر و قلم"), Icons.Default.Draw),
                Triple("CURVES", strings.tr("Curves & Resize", "منحنی و اندازه"), Icons.AutoMirrored.Filled.ShowChart),
                Triple("HSL", strings.tr("HSL", "رنگ HSL"), Icons.Default.Palette)
            )
            // Scrollable Samsung-style labeled bottom dock. Every tool remains
            // reachable on narrow displays and labels are visible (not icon-only).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for ((tabKey, tabDesc, tabIcon) in editorTabs) {
                    if (item.isVideo || tabKey != "VIDEO") {
                        val selected = activeTab == tabKey
                        Surface(
                            onClick = { activeTab = tabKey },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) Color(0xFF3578E5) else Color(0xFF23252C),
                            modifier = Modifier
                                .width(76.dp)
                                .testTag("editor_tab_${tabKey.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = tabIcon,
                                    contentDescription = tabDesc,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    tabDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Tool Controls Panel
        Surface(
            color = Color(0xFF121215),
            modifier = Modifier
                .fillMaxWidth()
                .height(295.dp)
        ) {
            when (activeTab) {
                "VIDEO" -> OneUiVideoStudioControls(
                    item = item,
                    trimStart = videoTrimStart,
                    trimEnd = videoTrimEnd,
                    onTrimChange = { s, e ->
                        videoTrimStart = s
                        videoTrimEnd = e
                    },
                    playbackSpeed = videoSpeed,
                    onSpeedChange = { videoSpeed = it },
                    targetResolution = videoTargetResolution,
                    onResolutionChange = { videoTargetResolution = it },
                    muteAudio = videoMuteAudio,
                    onMuteChange = { videoMuteAudio = it },
                    strings = strings,
                    onCaptureFrame = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveNonDestructiveEditAndExport(item, pipelineState, bmp, "PNG")
                        }
                    },
                    onOverwriteVideo = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveOverwriteOriginalVideo(
                                item = item,
                                pipelineState = pipelineState,
                                previewBitmap = bmp,
                                trimStartRatio = videoTrimStart,
                                trimEndRatio = videoTrimEnd,
                                playbackSpeed = videoSpeed,
                                targetResolutionLabel = videoTargetResolution,
                                muteAudio = videoMuteAudio
                            )
                        }
                    },
                    onExportVideo = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveEditedVideoAndExport(
                                item = item,
                                pipelineState = pipelineState,
                                previewBitmap = bmp,
                                trimStartRatio = videoTrimStart,
                                trimEndRatio = videoTrimEnd,
                                playbackSpeed = videoSpeed,
                                targetResolutionLabel = videoTargetResolution,
                                muteAudio = videoMuteAudio
                            )
                        }
                    }
                )
                "BG_REMOVE" -> BackgroundAndEraserControls(
                    aiToolMode = aiToolMode,
                    onToolModeChange = { aiToolMode = it },
                    state = pipelineState,
                    onUpdatePipeline = ::updatePipeline,
                    bgMode = bgMode,
                    onBgModeChange = { bgMode = it },
                    onSolidColorSelect = { bgSolidColorInt = it },
                    featherRadius = featherRadius,
                    onFeatherChange = { featherRadius = it },
                    maskSmoothing = maskSmoothing,
                    onSmoothingChange = { maskSmoothing = it },
                    brushIsRestore = brushIsRestore,
                    onBrushModeChange = { brushIsRestore = it },
                    brushRadiusNorm = brushRadiusNorm,
                    onBrushRadiusChange = { brushRadiusNorm = it },
                    strings = strings,
                    onOverwriteOriginal = {
                        if (aiToolMode == "CUTOUT") {
                            saveCurrentCutout(overwrite = true)
                        } else {
                            val bmp = renderedBitmap ?: sourceBitmap
                            if (bmp != null) viewModel.saveOverwriteOriginalPhoto(item, pipelineState, bmp, "PNG")
                        }
                    },
                    onExportPng = {
                        if (aiToolMode == "CUTOUT") saveCurrentCutout(overwrite = false, format = "PNG")
                        else (renderedBitmap ?: sourceBitmap)?.let {
                            viewModel.saveNonDestructiveEditAndExport(item, pipelineState, it, "PNG")
                        }
                    },
                    onExportWebp = {
                        if (aiToolMode == "CUTOUT") saveCurrentCutout(overwrite = false, format = "WEBP")
                        else (renderedBitmap ?: sourceBitmap)?.let {
                            viewModel.saveNonDestructiveEditAndExport(item, pipelineState, it, "WEBP")
                        }
                    }
                )
                "ADJUST" -> BasicAdjustmentsControls(
                    state = pipelineState,
                    onUpdate = ::updatePipeline,
                    strings = strings,
                    onOverwrite = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveOverwriteOriginalPhoto(item, pipelineState, bmp, "JPEG")
                        }
                    },
                    onExport = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveNonDestructiveEditAndExport(item, pipelineState, bmp, "JPEG")
                        }
                    }
                )
                "CURVES" -> RgbCurvesAndResizeControls(
                    item = item,
                    state = pipelineState,
                    onUpdate = ::updatePipeline,
                    strings = strings,
                    onOverwrite = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveOverwriteOriginalPhoto(item, pipelineState, bmp, "JPEG")
                        }
                    },
                    onExport = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveNonDestructiveEditAndExport(item, pipelineState, bmp, "JPEG")
                        }
                    }
                )
                "HSL" -> HslAndGradingControls(
                    state = pipelineState,
                    onUpdate = ::updatePipeline,
                    strings = strings
                )
                "CROP" -> GeometryCropAndPerspectiveControls(
                    state = pipelineState,
                    onUpdate = ::updatePipeline,
                    strings = strings
                )
                "FILTERS" -> FilterPresetsControls(
                    state = pipelineState,
                    onUpdate = ::updatePipeline,
                    strings = strings,
                    onOverwrite = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveOverwriteOriginalPhoto(item, pipelineState, bmp, "JPEG")
                        }
                    },
                    onExport = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveNonDestructiveEditAndExport(item, pipelineState, bmp, "JPEG")
                        }
                    }
                )
                "ANNOTATE" -> StickersPenAndTextControls(
                    state = pipelineState,
                    onUpdate = ::updatePipeline,
                    activePenColorInt = activePenColorInt,
                    onPenColorChange = { activePenColorInt = it },
                    activePenThickness = activePenThickness,
                    onPenThicknessChange = { activePenThickness = it },
                    strings = strings,
                    onOverwrite = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveOverwriteOriginalPhoto(item, pipelineState, bmp, "PNG")
                        }
                    },
                    onExport = {
                        val bmp = renderedBitmap ?: sourceBitmap
                        if (bmp != null) {
                            viewModel.saveNonDestructiveEditAndExport(item, pipelineState, bmp, "PNG")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OneUiVideoStudioControls(
    item: MediaItemEntity,
    trimStart: Float,
    trimEnd: Float,
    onTrimChange: (Float, Float) -> Unit,
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    targetResolution: String,
    onResolutionChange: (String) -> Unit,
    muteAudio: Boolean,
    onMuteChange: (Boolean) -> Unit,
    strings: BilingualStrings,
    onCaptureFrame: () -> Unit,
    onOverwriteVideo: () -> Unit,
    onExportVideo: () -> Unit
) {
    val baseDurSec = (item.durationMs.coerceAtLeast(15_000L) / 1000.0)
    val trimmedFraction = (trimEnd - trimStart).coerceIn(0.1f, 1.0f)
    val effectiveDurSec = (baseDurSec * trimmedFraction / playbackSpeed).toInt().coerceAtLeast(1)
    val resFactor = when (targetResolution) {
        "4K" -> 1.0f
        "1080p" -> 0.52f
        "720p" -> 0.28f
        "480p" -> 0.16f
        else -> 0.65f
    }
    val estSizeBytes = (item.sizeBytes * trimmedFraction * resFactor * (if (muteAudio) 0.92f else 1.0f)).toLong()
    val estMb = estSizeBytes / (1024.0 * 1024.0)
    val origMb = item.sizeBytes / (1024.0 * 1024.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = strings.tr(
                        "Duration: ${effectiveDurSec}s  •  Speed: ${playbackSpeed}x",
                        "مدت خروجی: $effectiveDurSec ثانیه  •  سرعت: ${playbackSpeed}x"
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = strings.tr(
                        String.format(Locale.US, "Size: %.2f MB → %.2f MB (%s)", origMb, estMb, targetResolution),
                        String.format(Locale.US, "حجم: %.2f مگابایت ← %.2f مگابایت (%s)", origMb, estMb, targetResolution)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF3E82F7)
                )
            }
            FilledTonalButton(
                onClick = onCaptureFrame,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.tr("Save Frame", "ذخیره فریم"))
            }
        }

        // Video Timeline Trim Sliders
        EditorSliderRow(
            label = strings.tr("Trim Start (${(trimStart * 100).toInt()}%)", "نقطه شروع برش ویدیو (${(trimStart * 100).toInt()}٪)"),
            value = trimStart,
            valueRange = 0f..0.85f,
            onValueChange = { newStart ->
                val clampedEnd = trimEnd.coerceAtLeast(newStart + 0.1f)
                onTrimChange(newStart, clampedEnd)
            }
        )
        EditorSliderRow(
            label = strings.tr("Trim End (${(trimEnd * 100).toInt()}%)", "نقطه پایان برش ویدیو (${(trimEnd * 100).toInt()}٪)"),
            value = trimEnd,
            valueRange = 0.15f..1.0f,
            onValueChange = { newEnd ->
                val clampedStart = trimStart.coerceAtMost(newEnd - 0.1f)
                onTrimChange(clampedStart, newEnd)
            }
        )

        // Playback Speed Selector (Samsung Video Editor 0.25x .. 2.0x)
        Text(
            text = strings.tr("Playback Speed:", "سرعت پخش ویدیو (اسلوموشن / دور تند):"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f).forEach { spd ->
                FilterChip(
                    selected = playbackSpeed == spd,
                    onClick = { onSpeedChange(spd) },
                    label = { Text("${spd}x") }
                )
            }
        }

        // Video Resolution & Compressor Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("4K", "1080p", "720p", "480p").forEach { res ->
                    FilterChip(
                        selected = targetResolution == res,
                        onClick = { onResolutionChange(res) },
                        label = { Text(res) }
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = muteAudio,
                onClick = { onMuteChange(!muteAudio) },
                leadingIcon = {
                    Icon(
                        if (muteAudio) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(if (muteAudio) strings.tr("Muted", "بی‌صدا") else strings.tr("Audio On", "صدادار")) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOverwriteVideo,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("overwrite_edited_video_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.tr("Save (Original)", "ذخیره روی همین فایل"), maxLines = 1)
            }
            FilledTonalButton(
                onClick = onExportVideo,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("export_edited_video_button")
            ) {
                Text(strings.tr("Save as Copy", "ذخیره فایل جداگانه"), maxLines = 1)
            }
        }
    }
}

@Composable
private fun BackgroundAndEraserControls(
    aiToolMode: String,
    onToolModeChange: (String) -> Unit,
    state: PhotoEditPipelineState,
    onUpdatePipeline: (PhotoEditPipelineState) -> Unit,
    bgMode: String,
    onBgModeChange: (String) -> Unit,
    onSolidColorSelect: (Int) -> Unit,
    featherRadius: Int,
    onFeatherChange: (Int) -> Unit,
    maskSmoothing: Int,
    onSmoothingChange: (Int) -> Unit,
    brushIsRestore: Boolean,
    onBrushModeChange: (Boolean) -> Unit,
    brushRadiusNorm: Float,
    onBrushRadiusChange: (Float) -> Unit,
    strings: BilingualStrings,
    onOverwriteOriginal: () -> Unit,
    onExportPng: () -> Unit,
    onExportWebp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sub-mode switcher: AI Cutout | Object/Spot Eraser | Color Pop
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = aiToolMode == "CUTOUT",
                onClick = { onToolModeChange("CUTOUT") },
                label = { Text(strings.tr("AI Cutout BG", "حذف پس‌زمینه")) }
            )
            FilterChip(
                selected = aiToolMode == "SPOT_ERASER",
                onClick = { onToolModeChange("SPOT_ERASER") },
                label = { Text(strings.tr("Object / Spot Eraser", "پاک‌کن اشیاء و لکه")) }
            )
            FilterChip(
                selected = state.colorPopSubjectOnly,
                onClick = { onUpdatePipeline(state.copy(colorPopSubjectOnly = !state.colorPopSubjectOnly)) },
                label = { Text(strings.tr("Color Pop (Subject)", "تفکیک رنگ سوژه (Color Pop)")) }
            )
        }

        if (aiToolMode == "SPOT_ERASER") {
            Text(
                text = strings.tr(
                    "Tap directly on any unwanted object or spot on the image above to erase & heal it.",
                    "روی هر لکه یا شیء ناخواسته در تصویر بالا ضربه بزنید تا هوشمندانه پاک و ترمیم شود."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9EA3AE)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (state.spotHealPoints.isNotEmpty()) {
                            onUpdatePipeline(state.copy(spotHealPoints = state.spotHealPoints.dropLast(1)))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(strings.tr("Undo Last Spot (${state.spotHealPoints.size})", "لغو آخرین نقطه (${state.spotHealPoints.size})"))
                }
                FilledTonalButton(
                    onClick = { onUpdatePipeline(state.copy(spotHealPoints = emptyList())) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.tr("Clear Spots", "پاک کردن همه"))
                }
            }
        } else {
            // Backdrop mode chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = bgMode == "TRANSPARENT",
                    onClick = { onBgModeChange("TRANSPARENT") },
                    label = { Text(strings.tr("Transparent Alpha", "شفاف (بدون پس‌زمینه)")) }
                )
                FilterChip(
                    selected = bgMode == "BLUR",
                    onClick = { onBgModeChange("BLUR") },
                    label = { Text(strings.tr("Bokeh Blur BG", "مات‌سازی بوکه")) }
                )
                FilterChip(
                    selected = bgMode == "SOLID",
                    onClick = { onBgModeChange("SOLID") },
                    label = { Text(strings.tr("Solid Color BG", "رنگ یکدست")) }
                )
            }

            if (bgMode == "SOLID") {
                val backdrops = listOf(
                    "#FFFFFF" to Color.White,
                    "#000000" to Color.Black,
                    "#3E82F7" to Color(0xFF3E82F7),
                    "#10B981" to Color(0xFF10B981),
                    "#F59E0B" to Color(0xFFF59E0B),
                    "#8B5CF6" to Color(0xFF8B5CF6)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((hex, col) in backdrops) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(1.5.dp, Color.White, CircleShape)
                                .clickable { onSolidColorSelect(android.graphics.Color.parseColor(hex)) }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.tr("Mask Brush:", "قلم اصلاح ماسک:"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                FilterChip(
                    selected = !brushIsRestore,
                    onClick = { onBrushModeChange(false) },
                    label = { Text(strings.tr("Erase BG", "پاک‌کن")) }
                )
                FilterChip(
                    selected = brushIsRestore,
                    onClick = { onBrushModeChange(true) },
                    label = { Text(strings.tr("Restore Subject", "بازگردانی سوژه")) }
                )
            }

            EditorSliderRow(
                label = strings.tr("Edge Feathering", "نرمی لبه (Feather)"),
                value = featherRadius.toFloat(),
                valueRange = 0f..10f,
                onValueChange = { onFeatherChange(it.toInt()) }
            )
            EditorSliderRow(
                label = strings.tr("Mask Smoothing", "هموارسازی ماسک"),
                value = maskSmoothing.toFloat(),
                valueRange = 0f..8f,
                onValueChange = { onSmoothingChange(it.toInt()) }
            )
            EditorSliderRow(
                label = strings.tr("Brush Size", "اندازه قلم"),
                value = brushRadiusNorm,
                valueRange = 0.02f..0.16f,
                onValueChange = onBrushRadiusChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOverwriteOriginal,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.tr("Save Original", "ذخیره روی اصلی"), maxLines = 1)
            }
            FilledTonalButton(
                onClick = onExportPng,
                modifier = Modifier
                    .weight(1f)
                    .testTag("export_transparent_png_button")
            ) {
                Text(strings.tr("Copy PNG", "کپی PNG"), maxLines = 1)
            }
            FilledTonalButton(
                onClick = onExportWebp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("export_transparent_webp_button")
            ) {
                Text(strings.tr("Copy WebP", "کپی WebP"), maxLines = 1)
            }
        }
    }
}

@Composable
private fun BasicAdjustmentsControls(
    state: PhotoEditPipelineState,
    onUpdate: (PhotoEditPipelineState) -> Unit,
    strings: BilingualStrings,
    onOverwrite: () -> Unit,
    onExport: () -> Unit
) {
    // One active adjustment at a time keeps the photo large and all controls reachable.
    val toolNames = listOf(
        "EXPOSURE" to strings.tr("Exposure", "نوردهی"),
        "BRIGHTNESS" to strings.tr("Brightness", "روشنایی"),
        "CONTRAST" to strings.tr("Contrast", "کنتراست"),
        "HIGHLIGHTS" to strings.tr("Highlights", "نقاط روشن"),
        "SHADOWS" to strings.tr("Shadows", "سایه‌ها"),
        "SATURATION" to strings.tr("Saturation", "اشباع"),
        "VIBRANCE" to strings.tr("Vibrance", "شادابی"),
        "WARMTH" to strings.tr("Temperature", "گرمی"),
        "TINT" to strings.tr("Tint", "ته‌رنگ"),
        "SHARPNESS" to strings.tr("Sharpness", "وضوح"),
        "CLARITY" to strings.tr("Clarity", "شفافیت"),
        "DEHAZE" to strings.tr("Dehaze", "مه‌زدایی"),
        "VIGNETTE" to strings.tr("Vignette", "تیرگی گوشه"),
        "GRAIN" to strings.tr("Grain", "دانه فیلم"),
        "FADE" to strings.tr("Fade", "مات")
    )
    var selectedTool by remember { mutableStateOf("BRIGHTNESS") }
    val current = when (selectedTool) {
        "EXPOSURE" -> state.exposure
        "CONTRAST" -> state.contrast
        "HIGHLIGHTS" -> state.highlights
        "SHADOWS" -> state.shadows
        "SATURATION" -> state.saturation
        "VIBRANCE" -> state.vibrance
        "WARMTH" -> state.warmthTemp
        "TINT" -> state.tint
        "SHARPNESS" -> state.sharpness
        "CLARITY" -> state.clarity
        "DEHAZE" -> state.dehaze
        "VIGNETTE" -> state.vignette
        "GRAIN" -> state.grain
        "FADE" -> state.fade
        else -> state.brightness
    }
    fun applyValue(value: Float) {
        onUpdate(when (selectedTool) {
            "EXPOSURE" -> state.copy(exposure = value)
            "CONTRAST" -> state.copy(contrast = value)
            "HIGHLIGHTS" -> state.copy(highlights = value)
            "SHADOWS" -> state.copy(shadows = value)
            "SATURATION" -> state.copy(saturation = value)
            "VIBRANCE" -> state.copy(vibrance = value)
            "WARMTH" -> state.copy(warmthTemp = value)
            "TINT" -> state.copy(tint = value)
            "SHARPNESS" -> state.copy(sharpness = value)
            "CLARITY" -> state.copy(clarity = value)
            "DEHAZE" -> state.copy(dehaze = value)
            "VIGNETTE" -> state.copy(vignette = value)
            "GRAIN" -> state.copy(grain = value)
            "FADE" -> state.copy(fade = value)
            else -> state.copy(brightness = value)
        })
    }
    val range = if (selectedTool in setOf("VIGNETTE", "GRAIN", "FADE")) 0f..1f else -1f..1f
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for ((key, name) in toolNames) {
                FilterChip(
                    selected = selectedTool == key,
                    onClick = { selectedTool = key },
                    label = { Text(name, maxLines = 1) },
                    modifier = Modifier.testTag("adjust_${key.lowercase()}")
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                toolNames.first { it.first == selectedTool }.second,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Text(
                "${(current * 100f).toInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF72A9FF)
            )
        }
        Slider(
            value = current.coerceIn(range.start, range.endInclusive),
            onValueChange = ::applyValue,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().testTag("active_adjustment_slider")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { applyValue(0f) },
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.tr("Reset adjustment", "بازنشانی این ابزار"), maxLines = 1)
            }
            FilledTonalButton(
                onClick = {
                    onUpdate(state.copy(
                        exposure = 0.12f, contrast = 0.18f,
                        highlights = -0.14f, shadows = 0.22f,
                        saturation = 0.14f, vibrance = 0.20f,
                        clarity = 0.16f, sharpness = 0.18f
                    ))
                },
                modifier = Modifier.weight(1f).testTag("samsung_auto_enhance_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(strings.tr("Auto", "خودکار"))
            }
        }
    }
}

@Composable
private fun RgbCurvesAndResizeControls(
    item: MediaItemEntity,
    state: PhotoEditPipelineState,
    onUpdate: (PhotoEditPipelineState) -> Unit,
    strings: BilingualStrings,
    onOverwrite: () -> Unit,
    onExport: () -> Unit
) {
    var selectedChannel by remember { mutableStateOf("MASTER") }
    val activePoints = when (selectedChannel) {
        "R" -> state.curveRed
        "G" -> state.curveGreen
        "B" -> state.curveBlue
        else -> state.curveMaster
    }

    val curveColor = when (selectedChannel) {
        "R" -> Color(0xFFFF5252)
        "G" -> Color(0xFF69F0AE)
        "B" -> Color(0xFF40C4FF)
        else -> Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Samsung One UI Image Resizer (100%, 75%, 50%, 25%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.tr("Resize Resolution:", "تغییر رزولوشن خروجی:"),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(100, 75, 50, 25).forEach { pct ->
                    FilterChip(
                        selected = state.resizeScalePercent == pct,
                        onClick = { onUpdate(state.copy(resizeScalePercent = pct)) },
                        label = { Text("$pct%") }
                    )
                }
            }
        }

        val outW = (item.width * state.resizeScalePercent) / 100
        val outH = (item.height * state.resizeScalePercent) / 100
        Text(
            text = strings.tr(
                "Output Dimensions: ${outW} × ${outH} px",
                "ابعاد جدید تصویر: ${outW} × ${outH} پیکسل"
            ),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF3E82F7)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("MASTER" to "RGB", "R" to "Red", "G" to "Green", "B" to "Blue").forEach { (ch, lbl) ->
                    FilterChip(
                        selected = selectedChannel == ch,
                        onClick = { selectedChannel = ch },
                        label = { Text(lbl) }
                    )
                }
            }
            AssistChip(
                onClick = {
                    val linear = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                    onUpdate(
                        when (selectedChannel) {
                            "R" -> state.copy(curveRed = linear)
                            "G" -> state.copy(curveGreen = linear)
                            "B" -> state.copy(curveBlue = linear)
                            else -> state.copy(curveMaster = linear)
                        }
                    )
                },
                label = { Text(strings.tr("Reset Curve", "خطی")) }
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1B202C))
                .pointerInput(selectedChannel, activePoints) {
                    detectDragGestures { change, _ ->
                        val nx = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val ny = (1f - (change.position.y / size.height.coerceAtLeast(1))).coerceIn(0f, 1f)
                        val ptIdx = (nx * 4f + 0.5f).toInt().coerceIn(0, 4)
                        val updated = activePoints.toMutableList().apply { this[ptIdx] = ny }
                        onUpdate(
                            when (selectedChannel) {
                                "R" -> state.copy(curveRed = updated)
                                "G" -> state.copy(curveGreen = updated)
                                "B" -> state.copy(curveBlue = updated)
                                else -> state.copy(curveMaster = updated)
                            }
                        )
                    }
                }
        ) {
            drawLine(
                color = Color.Gray.copy(alpha = 0.4f),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
                strokeWidth = 2f
            )
            val path = Path()
            for (i in 0..4) {
                val x = (i / 4f) * size.width
                val y = (1f - activePoints[i]) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(color = curveColor, radius = 10f, center = Offset(x, y))
            }
            drawPath(path = path, color = curveColor, style = Stroke(width = 4f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOverwrite,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.tr("Save Original", "ذخیره روی همین فایل"), maxLines = 1)
            }
            FilledTonalButton(
                onClick = onExport,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.tr("Save Copy (${outW}×${outH})", "ذخیره کپی جدا"), maxLines = 1)
            }
        }
    }
}

@Composable
private fun HslAndGradingControls(
    state: PhotoEditPipelineState,
    onUpdate: (PhotoEditPipelineState) -> Unit,
    strings: BilingualStrings
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EditorSliderRow(strings.tr("HSL Hue Shift (°)", "چرخش فام رنگی (Hue)"), state.hslHueShift, -180f..180f) {
            onUpdate(state.copy(hslHueShift = it))
        }
        EditorSliderRow(strings.tr("Vibrance (Smart Saturation)", "طراوت هوشمند رنگ (Vibrance)"), state.vibrance, -1f..1f) {
            onUpdate(state.copy(vibrance = it))
        }
        EditorSliderRow(strings.tr("Green-Magenta Tint", "تینت سبز / سرخابی"), state.tint, -1f..1f) {
            onUpdate(state.copy(tint = it))
        }
        EditorSliderRow(strings.tr("Gamma Curve", "گامای تصویر (Gamma)"), state.gamma, 0.5f..2.0f) {
            onUpdate(state.copy(gamma = it))
        }
    }
}

@Composable
private fun GeometryCropAndPerspectiveControls(
    state: PhotoEditPipelineState,
    onUpdate: (PhotoEditPipelineState) -> Unit,
    strings: BilingualStrings
) {
    val ratios = listOf("FREE", "1:1", "4:3", "16:9", "3:4", "9:16")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (r in ratios) {
                FilterChip(
                    selected = state.cropAspectRatio == r,
                    onClick = { onUpdate(state.copy(cropAspectRatio = r)) },
                    label = { Text(r) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onUpdate(state.copy(rotationDegrees = (state.rotationDegrees + 90) % 360)) }
            ) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.tr("90°", "چرخش ۹۰°"))
            }
            FilledTonalButton(
                onClick = { onUpdate(state.copy(flipHorizontal = !state.flipHorizontal)) }
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.tr("Flip H", "افقی"))
            }
            FilledTonalButton(
                onClick = { onUpdate(state.copy(flipVertical = !state.flipVertical)) }
            ) {
                Text(strings.tr("Flip V", "عمودی"))
            }
        }

        EditorSliderRow(
            label = strings.tr("Straighten Angle (°)", "تراز افق و زاویه چرخش"),
            value = state.straightenAngle,
            valueRange = -25f..25f,
            onValueChange = { onUpdate(state.copy(straightenAngle = it)) }
        )
        EditorSliderRow(
            label = strings.tr("Horizontal Perspective", "پرسپکتیو افقی (Keystone H)"),
            value = state.perspectiveHorizontal,
            valueRange = -1f..1f,
            onValueChange = { onUpdate(state.copy(perspectiveHorizontal = it)) }
        )
        EditorSliderRow(
            label = strings.tr("Vertical Perspective", "پرسپکتیو عمودی (Keystone V)"),
            value = state.perspectiveVertical,
            valueRange = -1f..1f,
            onValueChange = { onUpdate(state.copy(perspectiveVertical = it)) }
        )
    }
}

@Composable
private fun FilterPresetsControls(
    state: PhotoEditPipelineState,
    onUpdate: (PhotoEditPipelineState) -> Unit,
    strings: BilingualStrings,
    onOverwrite: () -> Unit,
    onExport: () -> Unit
) {
    val presets = listOf(
        "NONE" to strings.tr("Original", "اصلی"),
        "AUTO_ENHANCE" to strings.tr("Auto Enhance", "خودکار سامسونگ"),
        "WARM" to strings.tr("Warm", "گرم"),
        "COOL" to strings.tr("Cool", "سرد"),
        "VIVID" to strings.tr("Vivid", "زنده و شفاف"),
        "SOFT" to strings.tr("Soft", "نرم و لطیف"),
        "NOIR_BW" to strings.tr("Classic B&W", "سیاه‌وسفید"),
        "VINTAGE_GOLD" to strings.tr("Vintage Gold", "طلایی کلاسیک"),
        "CYBER_TEAL" to strings.tr("Cyber Teal", "فیروزه‌ای نئون"),
        "MATTE_FILM" to strings.tr("Matte Film", "آنالوگ مات"),
        "PERSIAN_TURQUOISE" to strings.tr("Turquoise", "فیروزه‌ای ایرانی")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((key, label) in presets) {
                FilterChip(
                    selected = state.filterPreset == key,
                    onClick = { onUpdate(state.copy(filterPreset = key)) },
                    label = { Text(label) }
                )
            }
        }

        EditorSliderRow(
            label = strings.tr("Filter Intensity", "شدت اعمال فیلتر"),
            value = state.filterIntensity,
            valueRange = 0f..1f,
            onValueChange = { onUpdate(state.copy(filterIntensity = it)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOverwrite,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.tr("Save Original", "ذخیره روی همین فایل"), maxLines = 1)
            }
            FilledTonalButton(
                onClick = onExport,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.tr("Save as Copy", "ذخیره فایل جدا"), maxLines = 1)
            }
        }
    }
}

@Composable
private fun StickersPenAndTextControls(
    state: PhotoEditPipelineState,
    onUpdate: (PhotoEditPipelineState) -> Unit,
    activePenColorInt: Int,
    onPenColorChange: (Int) -> Unit,
    activePenThickness: Float,
    onPenThicknessChange: (Float) -> Unit,
    strings: BilingualStrings,
    onOverwrite: () -> Unit,
    onExport: () -> Unit
) {
    val stickerList = listOf("⭐", "❤️", "🔥", "✨", "🎉", "👑", "🌿", "📸", "🎬", "💯")
    val penColors = listOf(
        "#3E82F7" to Color(0xFF3E82F7),
        "#FFFFFF" to Color.White,
        "#FBBF24" to Color(0xFFFBBF24),
        "#10B981" to Color(0xFF10B981),
        "#A855F7" to Color(0xFF8B5CF6),
        "#F97316" to Color(0xFFF97316)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Samsung Gallery Stickers Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.tr("Stickers (Tap to add, tap photo to move):", "استیکرها (افزودن و جابه‌جایی روی عکس):"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            if (state.placedStickers.isNotEmpty() || state.drawingPaths.isNotEmpty()) {
                AssistChip(
                    onClick = { onUpdate(state.copy(placedStickers = emptyList(), drawingPaths = emptyList())) },
                    label = { Text(strings.tr("Clear Ink/Stickers", "پاک کردن استیکر و قلم")) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (sym in stickerList) {
                Surface(
                    onClick = {
                        val offset = (state.placedStickers.size % 5) * 0.08f
                        val newSticker = StickerItemData(
                            symbol = sym,
                            normX = (0.35f + offset).coerceIn(0.15f, 0.85f),
                            normY = (0.35f + offset).coerceIn(0.15f, 0.85f),
                            scale = 1.0f
                        )
                        onUpdate(state.copy(placedStickers = state.placedStickers + newSticker))
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1F222A)
                ) {
                    Text(
                        text = sym,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 2. Pen Color & Thickness
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = strings.tr("Pen Color (Drag on image):", "رنگ قلم نوری (کشیدن روی عکس):"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((hex, col) in penColors) {
                    val cInt = android.graphics.Color.parseColor(hex)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(
                                width = if (activePenColorInt == cInt) 2.5.dp else 1.dp,
                                color = if (activePenColorInt == cInt) Color.White else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable {
                                onPenColorChange(cInt)
                                onUpdate(state.copy(overlayTextColor = cInt))
                            }
                    )
                }
            }
        }

        EditorSliderRow(
            label = strings.tr("Pen Thickness", "ضخامت قلم نوری"),
            value = activePenThickness,
            valueRange = 0.005f..0.035f,
            onValueChange = onPenThicknessChange
        )

        // 3. Text Overlay & Border
        OutlinedTextField(
            value = state.overlayText,
            onValueChange = { onUpdate(state.copy(overlayText = it)) },
            label = { Text(strings.tr("Text Overlay", "متن روی تصویر (فارسی / انگلیسی)")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        EditorSliderRow(
            label = strings.tr("Frame Border", "ضخامت کادر دور عکس"),
            value = state.borderWidthRatio,
            valueRange = 0f..0.06f,
            onValueChange = { onUpdate(state.copy(borderWidthRatio = it)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.tr("Show Watermark", "درج نام گالری در گوشه تصویر"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Switch(
                checked = state.watermarkEnabled,
                onCheckedChange = { onUpdate(state.copy(watermarkEnabled = it)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOverwrite,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.tr("Save Original", "ذخیره روی همین فایل"), maxLines = 1)
            }
            FilledTonalButton(
                onClick = onExport,
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.tr("Save as Copy", "ذخیره فایل جدا"), maxLines = 1)
            }
        }
    }
}

// ContentScale.Fit letterboxes the image: map touches to actual displayed
// pixels, not the full viewport. Ignore taps in the padding around the photo.
private fun mapPreviewTouch(
    point: Offset,
    viewportWidth: Int,
    viewportHeight: Int,
    bitmap: Bitmap
): Pair<Float, Float>? {
    if (viewportWidth <= 0 || viewportHeight <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return null
    val scale = min(
        viewportWidth.toFloat() / bitmap.width,
        viewportHeight.toFloat() / bitmap.height
    )
    val drawnWidth = bitmap.width * scale
    val drawnHeight = bitmap.height * scale
    val left = (viewportWidth - drawnWidth) / 2f
    val top = (viewportHeight - drawnHeight) / 2f
    val x = (point.x - left) / drawnWidth
    val y = (point.y - top) / drawnHeight
    if (x !in 0f..1f || y !in 0f..1f) return null
    return x to y
}

@Composable
private fun EditorSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White)
            Text(
                text = "%.2f".format(Locale.US, value),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF3E82F7),
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.height(26.dp)
        )
    }
}

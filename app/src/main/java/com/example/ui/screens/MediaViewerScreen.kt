package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.database.MediaItemEntity
import com.example.core.localization.BilingualStrings
import com.example.core.localization.JalaliCalendar
import com.example.core.media.PhotoProcessingEngine
import com.example.ui.TakzGalleryViewModel
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MediaViewerScreen(
    item: MediaItemEntity,
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings
) {
    BackHandler { viewModel.closeMediaViewer() }

    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val compareId by viewModel.compareMediaId.collectAsStateWithLifecycle()
    val isFa = settings.isPersianActive

    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
    var fitMode by remember { mutableStateOf("FIT") } // "FIT", "CROP"
    var showChrome by remember { mutableStateOf(true) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf("NONE") } // "NONE", "INFO", "OCR", "COMPARE_PICK"

    // Inline Video Playback state
    var isVideoPlaying by remember(item.id) { mutableStateOf(false) }
    var videoProgress by remember(item.id) { mutableFloatStateOf(0f) }

    var nativeVideo by remember(item.id) { mutableStateOf<VideoView?>(null) }
    DisposableEffect(item.id) {
        onDispose { nativeVideo?.stopPlayback(); nativeVideo = null }
    }
    LaunchedEffect(isVideoPlaying, nativeVideo) {
        val player = nativeVideo ?: return@LaunchedEffect
        if (isVideoPlaying) player.start() else player.pause()
    }
    LaunchedEffect(item.id, nativeVideo) {
        while (true) {
            delay(200)
            val player = nativeVideo ?: continue
            if (player.duration > 0) {
                videoProgress = (player.currentPosition.toFloat() / player.duration).coerceIn(0f, 1f)
                if (player.isPlaying != isVideoPlaying) isVideoPlaying = player.isPlaying
            }
        }
    }

    var histogram by remember(item.id) { mutableStateOf<PhotoProcessingEngine.HistogramData?>(null) }
    var colorPalette by remember(item.id) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) {
            val bmp = viewModel.libraryManager.loadSampledBitmap(item.contentUri, 320)
            if (bmp != null) {
                histogram = PhotoProcessingEngine.computeHistogram(bmp)
                colorPalette = PhotoProcessingEngine.extractColorPalette(bmp)
            }
        }
    }

    val compareItem = remember(allMedia, compareId) {
        allMedia.find { it.id == compareId }
    }

    val imgModel = remember(item.contentUri) {
        val u = Uri.parse(item.contentUri)
        if (u.scheme == "file") File(u.path ?: "") else item.contentUri
    }

    val formattedDate = remember(item.dateCapturedSec, settings.calendarSystem, isFa) {
        JalaliCalendar.formatTimestamp(item.dateCapturedSec, settings.calendarSystem, isFa, "DAY")
    }
    val smartLocation = remember(item.id, isFa) {
        item.extractSmartLocation(isFa).label(isFa)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("media_viewer_screen")
    ) {
        // Main Image/Video or Side-by-Side Comparison Canvas
        if (compareItem != null) {
            val compModel = remember(compareItem.contentUri) {
                val u = Uri.parse(compareItem.contentUri)
                if (u.scheme == "file") File(u.path ?: "") else compareItem.contentUri
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AsyncImage(
                        model = imgModel,
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Surface(color = Color.Black.copy(alpha = 0.75f), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "A: ${item.fileName}\n${item.width}×${item.height}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AsyncImage(
                        model = compModel,
                        contentDescription = compareItem.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Surface(color = Color.Black.copy(alpha = 0.75f), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "B: ${compareItem.fileName}\n${compareItem.width}×${compareItem.height}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = {
                                if (activeSheet != "NONE") {
                                    activeSheet = "NONE"
                                } else {
                                    showChrome = !showChrome
                                }
                            },
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(item.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.parse(item.contentUri))
                                setOnPreparedListener { mp -> mp.isLooping = false }
                                setOnCompletionListener { isVideoPlaying = false; videoProgress = 1f }
                                nativeVideo = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = imgModel,
                        contentDescription = item.fileName,
                        contentScale = if (fitMode == "CROP") ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                }
            }
        }

        // Samsung One UI Top Minimal Overlay Bar
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { viewModel.closeMediaViewer() },
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "📍 $smartLocation",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Top-Right Quick Action Icons (Clean Icon-Only, Samsung One UI Style)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.openPhotoEditor(item.id, tab = "BG_REMOVE") },
                            modifier = Modifier.testTag("open_bg_removal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = strings.tr("AI Cutout & Eraser", "برش سوژه و پاک‌کن"),
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { activeSheet = if (activeSheet == "OCR") "NONE" else "OCR" },
                            modifier = Modifier.testTag("open_ocr_sheet_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = strings.tr("Extract Text", "استخراج متن تصویر"),
                                tint = if (activeSheet == "OCR") Color(0xFF3E82F7) else Color.White
                            )
                        }

                        IconButton(
                            onClick = { viewModel.encryptMediaToVault(item) },
                            modifier = Modifier.testTag("viewer_encrypt_vault_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = strings.tr("Move to Secure Vault", "انتقال به گاوصندوق"),
                                tint = Color.White
                            )
                        }

                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.tr("Details & Histogram", "جزئیات و هیستوگرام")) },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        activeSheet = "INFO"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.tr("Compare Side-by-Side", "مقایسه دو تصویر")) },
                                    leadingIcon = { Icon(Icons.Default.Compare, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (compareItem != null) viewModel.startComparisonWith(null)
                                        else activeSheet = "COMPARE_PICK"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.tr("Set as Wallpaper", "تنظیم تصویر زمینه")) },
                                    leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.setAsWallpaper(item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (fitMode == "FIT") strings.tr("Fill Screen", "پر کردن صفحه")
                                            else strings.tr("Fit to Screen", "اندازه اصلی")
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        fitMode = if (fitMode == "FIT") "CROP" else "FIT"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Samsung One UI Video Scrubber Pill (Only shown for Videos, right above the 5-icon bottom bar)
        AnimatedVisibility(
            visible = showChrome && item.isVideo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 78.dp, start = 20.dp, end = 20.dp)
        ) {
            val totalSec = (item.durationMs / 1000L).coerceAtLeast(1L)
            val curSec = (totalSec * videoProgress).toLong()
            val timeStr = String.format(
                Locale.US,
                "%02d:%02d / %02d:%02d",
                curSec / 60,
                curSec % 60,
                totalSec / 60,
                totalSec % 60
            )
            Surface(
                color = Color(0xFF17171A).copy(alpha = 0.90f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { isVideoPlaying = !isVideoPlaying },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White
                        )
                    }
                    Slider(
                        value = videoProgress,
                        onValueChange = {
                            videoProgress = it
                            nativeVideo?.let { player ->
                                if (player.duration > 0) player.seekTo((player.duration * it).toInt())
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    IconButton(
                        onClick = { viewModel.extractVideoFrameOrMotionSlide(item) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("capture_video_frame_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = strings.tr("Capture Frame", "ذخیره فریم"),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // EXACT Samsung One UI Gallery Bottom 5-Icon Bar (Pure Icons, Zero Text Clutter)
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xFF0E0E11).copy(alpha = 0.92f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Favorite (Heart)
                    IconButton(
                        onClick = { viewModel.toggleFavorite(item) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = strings.tr("Favorite", "برگزیده"),
                            tint = if (item.isFavorite) Color(0xFF3E82F7) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 2. Edit (Pencil -> Opens Photo or Video Studio)
                    IconButton(
                        onClick = {
                            viewModel.openPhotoEditor(
                                item.id,
                                tab = if (item.isVideo) "VIDEO" else "ADJUST"
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_photo_editor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = strings.tr("Edit", "ویرایش"),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 3. Share (Opens Samsung One UI Privacy Share Sheet with Location/Camera EXIF removal toggle)
                    IconButton(
                        onClick = { activeSheet = if (activeSheet == "SHARE_PRIVACY") "NONE" else "SHARE_PRIVACY" },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_share_privacy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = strings.tr("Share", "اشتراک‌گذاری"),
                            tint = if (activeSheet == "SHARE_PRIVACY") Color(0xFF3E82F7) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 4. Delete (Trash)
                    IconButton(
                        onClick = { viewModel.moveSelectedToTrash(listOf(item.id)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = strings.tr("Delete", "حذف"),
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }

                    // 5. Details / EXIF & AI Info (i)
                    IconButton(
                        onClick = { activeSheet = if (activeSheet == "INFO") "NONE" else "INFO" },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_exif_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = strings.tr("Details", "جزئیات"),
                            tint = if (activeSheet == "INFO") Color(0xFF3E82F7) else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Interactive Sheets (OCR / EXIF & Tags / Privacy Share / Compare Picker)
        if (activeSheet != "NONE") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (activeSheet == "INFO") 520.dp else 440.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp, start = 12.dp, end = 12.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                when (activeSheet) {
                    "OCR" -> OcrInspectorPanel(
                        item = item,
                        viewModel = viewModel,
                        strings = strings,
                        context = context,
                        onClose = { activeSheet = "NONE" }
                    )
                    "SHARE_PRIVACY" -> PrivacyShareSheetPanel(
                        item = item,
                        viewModel = viewModel,
                        strings = strings,
                        isFa = isFa,
                        onClose = { activeSheet = "NONE" }
                    )
                    "INFO" -> ExifAndTagsPanel(
                        item = item,
                        histogram = histogram,
                        colorPalette = colorPalette,
                        viewModel = viewModel,
                        strings = strings,
                        isFa = isFa,
                        onClose = { activeSheet = "NONE" }
                    )
                    "COMPARE_PICK" -> ComparePhotoPickerPanel(
                        currentItem = item,
                        allMedia = allMedia.filter { !it.isTrashed && !it.isHidden && it.id != item.id },
                        strings = strings,
                        onSelectCompare = { second ->
                            viewModel.startComparisonWith(second.id)
                            activeSheet = "NONE"
                        },
                        onClose = { activeSheet = "NONE" }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyShareSheetPanel(
    item: MediaItemEntity,
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings,
    isFa: Boolean,
    onClose: () -> Unit
) {
    val detailed = remember(item.id, item.exifSummary, isFa) { item.parseDetailedMetadata(isFa) }
    var stripLocation by remember { mutableStateOf(true) }
    var stripCameraExif by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.tr("Share & Privacy Protection", "اشتراک‌گذاری و حذف جزئیات خصوصی"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.tr(
                        "📍 Location: ${detailed.locationName} (${detailed.gpsCoordinates})",
                        "📍 موقعیت مکانی: ${detailed.locationName} (${detailed.gpsCoordinates})"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stripLocation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strings.tr(
                        "📷 Camera: ${detailed.cameraMake} ${detailed.cameraModel} • ${detailed.apertureFStop} • ${detailed.shutterSpeed} • ${detailed.isoSensitivity}",
                        "📷 دوربین: ${detailed.cameraMake} ${detailed.cameraModel} • ${detailed.apertureFStop} • ${detailed.shutterSpeed} • ${detailed.isoSensitivity}"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stripCameraExif) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Toggle 1: Remove Location / GPS Data
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.tr("Remove Location Data (GPS)", "حذف موقعیت مکانی و مختصات GPS"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = strings.tr(
                            "Strips latitude, longitude, and city tags before sharing",
                            "مختصات جغرافیایی و نام شهر را هنگام ارسال حذف می‌کند"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = stripLocation,
                    onCheckedChange = { stripLocation = it },
                    modifier = Modifier.testTag("toggle_strip_location_switch")
                )
            }
        }

        // Toggle 2: Remove Camera & Technical EXIF Data
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.tr("Remove Camera & Lens EXIF", "حذف اطلاعات اختصاصی دوربین و لنز"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = strings.tr(
                            "Strips camera model, lens, ISO, aperture, and shutter speed",
                            "مدل گوشی/دوربین، لنز، ISO، دیافراگم و سرعت شاتر را پاک می‌کند"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = stripCameraExif,
                    onCheckedChange = { stripCameraExif = it },
                    modifier = Modifier.testTag("toggle_strip_camera_switch")
                )
            }
        }

        Button(
            onClick = {
                viewModel.shareMediaWithPrivacyOptions(
                    item = item,
                    stripLocation = stripLocation,
                    stripCameraExif = stripCameraExif
                )
                onClose()
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("share_with_privacy_button")
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (stripLocation || stripCameraExif) {
                    strings.tr("Share Clean (Privacy Protected)", "اشتراک‌گذاری امن (بدون اطلاعات خصوصی)")
                } else {
                    strings.tr("Share Original File", "اشتراک‌گذاری فایل اصلی")
                },
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    viewModel.stripMetadataPermanently(
                        item = item,
                        stripLocation = stripLocation,
                        stripCameraExif = stripCameraExif
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("strip_metadata_from_file_button")
            ) {
                Text(
                    strings.tr("Strip from This File", "حذف دائمی از همین فایل"),
                    maxLines = 1
                )
            }
            FilledTonalButton(
                onClick = {
                    viewModel.libraryManager.shareMediaItem(item)
                    onClose()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    strings.tr("Quick Share Original", "ارسال با کل جزئیات"),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OcrInspectorPanel(
    item: MediaItemEntity,
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings,
    context: Context,
    onClose: () -> Unit
) {
    var editableOcr by remember(item.id, item.ocrText) { mutableStateOf(item.ocrText) }
    val parsedOcr = remember(editableOcr) {
        viewModel.ocrEngine.parsePatterns(editableOcr)
    }

    fun copyToClipboard(label: String, text: String) {
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cb?.setPrimaryClip(ClipData.newPlainText(label, text))
        viewModel.showStatusMessage("Copied: $text")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.tr("Recognized Image Text", "متن شناسایی‌شده در تصویر"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        if (parsedOcr.verificationCodes.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (code in parsedOcr.verificationCodes) {
                    AssistChip(
                        onClick = { copyToClipboard("OTP", code) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(code, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        val quickEntities = parsedOcr.urls + parsedOcr.emails + parsedOcr.phoneNumbers + parsedOcr.dates
        if (quickEntities.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (entity in quickEntities) {
                    AssistChip(
                        onClick = { copyToClipboard("Entity", entity) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(entity) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = editableOcr,
            onValueChange = { editableOcr = it },
            label = { Text(strings.tr("Text", "متن قابل کپی و ویرایش")) },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .testTag("ocr_editable_text_field")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { copyToClipboard("OCR Text", editableOcr) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.tr("Copy", "کپی متن"))
            }
            Button(
                onClick = { viewModel.saveUpdatedOcrText(item, editableOcr) },
                modifier = Modifier.weight(1f)
            ) {
                Text(strings.tr("Save", "ذخیره"))
            }
        }
    }
}

@Composable
private fun ExifAndTagsPanel(
    item: MediaItemEntity,
    histogram: PhotoProcessingEngine.HistogramData?,
    colorPalette: List<String>,
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings,
    isFa: Boolean,
    onClose: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var newTagInput by remember { mutableStateOf("") }
    val aiTags = remember(item.aiTagsPipe) { item.parseAiTags() }
    val manualTags = remember(item.manualTagsCsv) { item.parseManualTags() }
    val detailed = remember(item.id, item.exifSummary, item.sizeBytes, item.width, item.height, isFa) {
        item.parseDetailedMetadata(isFa)
    }
    val capturedDateJalali = remember(item.dateCapturedSec, isFa) {
        JalaliCalendar.formatTimestamp(item.dateCapturedSec, com.example.core.localization.CalendarSystem.JALALI, isFa, "DAY")
    }
    val capturedDateGregorian = remember(item.dateCapturedSec, isFa) {
        JalaliCalendar.formatTimestamp(item.dateCapturedSec, com.example.core.localization.CalendarSystem.GREGORIAN, isFa, "DAY")
    }
    val modifiedDateStr = remember(item.dateModifiedSec, settings.calendarSystem, isFa) {
        JalaliCalendar.formatTimestamp(item.dateModifiedSec, settings.calendarSystem, isFa, "DAY")
    }
    val sizeMbStr = remember(item.sizeBytes) {
        String.format(Locale.US, "%.2f MB (%,d KB • %,d B)", item.sizeBytes / (1024.0 * 1024.0), item.sizeBytes / 1024L, item.sizeBytes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.tr("Full Technical Details & EXIF", "جزئیات کامل فایل، دوربین و مکان"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // 1. Complete File & Resolution Details Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = strings.tr("📄 File & Resolution Specifications", "📄 مشخصات کامل فایل و رزولوشن"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E82F7)
                )
                DetailKeyValueRow(strings.tr("File Name", "نام فایل"), item.fileName)
                DetailKeyValueRow(strings.tr("Folder / Album", "پوشه"), item.folderName)
                DetailKeyValueRow(strings.tr("Full File Path", "مسیر دقیق فایل"), detailed.filePath)
                DetailKeyValueRow(strings.tr("Exact Size", "حجم دقیق"), sizeMbStr)
                DetailKeyValueRow(
                    strings.tr("Resolution", "رزولوشن و مگاپیکسل"),
                    "${item.width} × ${item.height} px  •  ${detailed.megapixelsLabel}  (${detailed.aspectRatioLabel})"
                )
                DetailKeyValueRow(strings.tr("MIME Format", "فرمت فایل"), item.mimeType)
                if (item.isVideo) {
                    val durSec = (item.durationMs / 1000L).coerceAtLeast(1L)
                    DetailKeyValueRow(
                        strings.tr("Video Duration", "مدت زمان ویدیو"),
                        String.format(Locale.US, "%02d:%02d (%d sec)", durSec / 60, durSec % 60, durSec)
                    )
                }
                DetailKeyValueRow(strings.tr("Captured (Shamsi)", "تاریخ ثبت (شمسی)"), capturedDateJalali)
                DetailKeyValueRow(strings.tr("Captured (Gregorian)", "تاریخ ثبت (میلادی)"), capturedDateGregorian)
                DetailKeyValueRow(strings.tr("Last Modified", "آخرین ویرایش"), modifiedDateStr)
            }
        }

        // 2. Complete Camera, Lens & Exposure EXIF Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.tr("📷 Camera, Lens & Exposure EXIF", "📷 مشخصات دوربین، لنز و نوردهی"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3E82F7)
                    )
                    if (detailed.isCameraStripped) {
                        Text(
                            text = strings.tr("Stripped", "پاک‌شده"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                DetailKeyValueRow(strings.tr("Camera Make", "سازنده دوربین"), detailed.cameraMake)
                DetailKeyValueRow(strings.tr("Camera Model", "مدل دوربین / دستگاه"), detailed.cameraModel)
                DetailKeyValueRow(strings.tr("Lens Aperture", "گشودگی دیافراگم (Aperture)"), detailed.apertureFStop)
                DetailKeyValueRow(strings.tr("Shutter Speed", "سرعت شاتر (Shutter)"), detailed.shutterSpeed)
                DetailKeyValueRow(strings.tr("ISO Sensitivity", "حساسیت سنسور (ISO)"), detailed.isoSensitivity)
                DetailKeyValueRow(strings.tr("Focal Length", "فاصله کانونی لنز"), detailed.focalLength)
                DetailKeyValueRow(strings.tr("Color Profile", "پروفایل رنگی"), detailed.colorSpace)
                DetailKeyValueRow(strings.tr("White Balance", "تراز سفیدی"), detailed.whiteBalance)
                DetailKeyValueRow(strings.tr("Flash Status", "وضعیت فلاش"), detailed.flashState)
            }
        }

        // 3. Location, GPS Coordinates & 1-Tap Privacy Stripper Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = strings.tr("📍 Location, GPS & Privacy", "📍 موقعیت مکانی، مختصات و حریم خصوصی"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E82F7)
                )
                DetailKeyValueRow(strings.tr("Place / City", "مکان / شهر"), detailed.locationName)
                DetailKeyValueRow(strings.tr("GPS Coordinates", "مختصات دقیق GPS"), detailed.gpsCoordinates)
                DetailKeyValueRow(
                    strings.tr("Privacy Status", "وضعیت متادیتا"),
                    if (detailed.isLocationStripped && detailed.isCameraStripped) {
                        strings.tr("Clean (Location & Camera Removed)", "پاک‌سازی‌شده (فاقد مکان و اطلاعات دوربین)")
                    } else {
                        strings.tr("Contains Original Metadata", "حاوی اطلاعات مکان و دوربین")
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.stripMetadataPermanently(
                                item = item,
                                stripLocation = true,
                                stripCameraExif = false
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("details_strip_location_button")
                    ) {
                        Text(strings.tr("Remove Location", "حذف مکان (GPS)"), maxLines = 1)
                    }
                    Button(
                        onClick = {
                            viewModel.stripMetadataPermanently(
                                item = item,
                                stripLocation = true,
                                stripCameraExif = true
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("details_strip_all_metadata_button")
                    ) {
                        Text(strings.tr("Strip GPS & Camera", "حذف مکان و دوربین"), maxLines = 1)
                    }
                }
            }
        }

        // 4. RGB Histogram & Dominant Color Palette Card
        if (histogram != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = strings.tr("🎨 RGB Histogram & Color Palette", "🎨 نمودار هیستوگرام RGB و پالت رنگ"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3E82F7)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        val maxBin = (histogram.lumBins.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
                        fun drawChannel(bins: IntArray, color: Color) {
                            val path = Path()
                            val stepX = size.width / 63f
                            for (i in 0..63) {
                                val x = i * stepX
                                val y = size.height - (bins[i] / maxBin).coerceIn(0f, 1f) * size.height
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color = color, style = Stroke(width = 3f))
                        }
                        drawChannel(histogram.rBins, Color(0xFFFF5252))
                        drawChannel(histogram.gBins, Color(0xFF69F0AE))
                        drawChannel(histogram.bBins, Color(0xFF40C4FF))
                    }

                    if (colorPalette.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (hex in colorPalette) {
                                val swatchColor = runCatching {
                                    Color(android.graphics.Color.parseColor(hex))
                                }.getOrDefault(Color.Gray)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = hex,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. AI Visual Quality, Cryptographic Hashes & Tags Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = strings.tr("🧠 AI Quality & Cryptographic Signatures", "🧠 تحلیل هوش مصنوعی و امضای دیجیتال"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E82F7)
                )
                DetailKeyValueRow(
                    strings.tr("Sharpness Score", "امتیاز وضوح و فوکوس"),
                    String.format(Locale.US, "%.1f / 100", item.sharpnessScore)
                )
                DetailKeyValueRow(strings.tr("Dominant Color", "کد رنگ غالب"), item.dominantColorHex)
                DetailKeyValueRow(
                    strings.tr("Perceptual Hash (pHash)", "هش بصری (pHash-64)"),
                    item.perceptualHash64.toULong().toString(16).uppercase()
                )
                if (item.sha256Hash.isNotBlank()) {
                    DetailKeyValueRow(
                        strings.tr("SHA-256 Digest", "هش امنیتی SHA-256"),
                        item.sha256Hash.take(24) + "…"
                    )
                }
            }
        }

        // AI Tags
        for (tag in aiTags) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isFa) tag.labelFa else tag.labelEn,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                LinearProgressIndicator(
                    progress = { tag.confidence },
                    modifier = Modifier
                        .width(90.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(tag.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // User Custom Tags
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (mTag in manualTags) {
                AssistChip(
                    onClick = { viewModel.removeManualTag(item, mTag) },
                    label = { Text("$mTag ✕") }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newTagInput,
                onValueChange = { newTagInput = it },
                placeholder = { Text(strings.tr("Add tag…", "افزودن تگ…")) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("custom_tag_input")
            )
            Button(
                onClick = {
                    viewModel.addManualTag(item, newTagInput)
                    newTagInput = ""
                },
                modifier = Modifier.testTag("add_custom_tag_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun DetailKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun ComparePhotoPickerPanel(
    currentItem: MediaItemEntity,
    allMedia: List<MediaItemEntity>,
    strings: BilingualStrings,
    onSelectCompare: (MediaItemEntity) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.tr("Select Photo to Compare", "انتخاب تصویر دوم برای مقایسه"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (candidate in allMedia) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCompare(candidate) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = candidate.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${candidate.width}×${candidate.height}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

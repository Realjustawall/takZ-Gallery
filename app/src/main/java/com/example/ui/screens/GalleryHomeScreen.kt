package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.database.MediaItemEntity
import com.example.core.localization.BilingualStrings
import com.example.core.localization.JalaliCalendar
import com.example.core.localization.PersianNormalizer
import com.example.ui.TakzGalleryViewModel
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryHomeScreen(
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val filterChip by viewModel.galleryFilterChip.collectAsStateWithLifecycle()
    val folderFilter by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val locationFilter by viewModel.selectedLocationFilter.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedMediaIds.collectAsStateWithLifecycle()
    val slideshowActive by viewModel.slideshowActive.collectAsStateWithLifecycle()

    val isFa = settings.isPersianActive

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFromPhotoPicker(uri)
        }
    }

    val filteredMedia = remember(allMedia, filterChip, folderFilter, locationFilter, isFa) {
        allMedia.filter { item ->
            when {
                folderFilter != null -> !item.isTrashed && !item.isHidden && item.folderName == folderFilter
                locationFilter != null -> !item.isTrashed && !item.isHidden && item.extractSmartLocation(isFa).label(isFa) == locationFilter
                else -> when (filterChip) {
                    "FAVORITES" -> !item.isTrashed && !item.isHidden && item.isFavorite
                    "VIDEOS" -> !item.isTrashed && !item.isHidden && item.isVideo
                    "RECENT" -> !item.isTrashed && !item.isHidden
                    "HIDDEN" -> !item.isTrashed && item.isHidden
                    "TRASH" -> item.isTrashed
                    else -> !item.isTrashed && !item.isHidden
                }
            }
        }
    }

    // Smart Temporal (DAY / MONTH / YEAR) or Local Metadata Location (LOCATION) Grouping
    val groupedBySmartMode = remember(filteredMedia, settings.timelineGrouping, settings.calendarSystem, isFa) {
        if (settings.timelineGrouping == "LOCATION") {
            filteredMedia.groupBy { item ->
                item.extractSmartLocation(isFa).label(isFa)
            }
        } else {
            filteredMedia.groupBy { item ->
                JalaliCalendar.formatTimestamp(
                    epochSeconds = item.dateModifiedSec,
                    calendarSystem = settings.calendarSystem,
                    isPersianLang = isFa,
                    mode = settings.timelineGrouping
                )
            }
        }
    }

    if (slideshowActive && filteredMedia.isNotEmpty()) {
        BackHandler { viewModel.setSlideshowActive(false) }
        FullscreenSlideshowOverlay(
            items = filteredMedia,
            strings = strings,
            onClose = { viewModel.setSlideshowActive(false) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("gallery_home_screen")
    ) {
        // Samsung One UI 6.1 Refined Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).testTag("ai_index_status_badge")) {
                Text(
                    text = folderFilter ?: locationFilter ?: strings.tr("Pictures", "تصاویر"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val countText = PersianNormalizer.formatNumber(filteredMedia.size, isFa)
                val vidCount = filteredMedia.count { it.isVideo }
                val vidText = PersianNormalizer.formatNumber(vidCount, isFa)
                Text(
                    text = if (vidCount > 0) {
                        strings.tr("$countText items • $vidText videos", "$countText مورد • $vidText ویدیو")
                    } else {
                        strings.tr("$countText photos", "$countText تصویر")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // One UI Action Icons Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.testTag("import_photo_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = strings.tr("Add", "افزودن"),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = { viewModel.selectBottomTab("SEARCH") }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = strings.tr("Search", "جستجو"),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = {
                        val nextLayout = when (settings.layoutMode) {
                            "GRID" -> "MASONRY"
                            "MASONRY" -> "LIST"
                            else -> "GRID"
                        }
                        viewModel.updateSettings { it.copy(layoutMode = nextLayout) }
                    },
                    modifier = Modifier.testTag("layout_mode_button")
                ) {
                    Icon(
                        imageVector = when (settings.layoutMode) {
                            "MASONRY" -> Icons.AutoMirrored.Filled.ViewQuilt
                            "LIST" -> Icons.Default.ViewAgenda
                            else -> Icons.Default.GridView
                        },
                        contentDescription = strings.tr("Layout", "چیدمان"),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = { viewModel.openSettings() },
                    modifier = Modifier.testTag("open_dedicated_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = strings.tr("Settings", "تنظیمات"),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Samsung One UI Smart Temporal & Location Grouping Switcher (روزانه | ماهانه | سالانه | مکان‌ها)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("timeline_group_button")
        ) {
            val groupingModes = listOf(
                "DAY" to strings.tr("Daily", "روزانه"),
                "MONTH" to strings.tr("Monthly", "ماهانه"),
                "YEAR" to strings.tr("Yearly", "سالانه"),
                "LOCATION" to strings.tr("Locations", "مکان‌ها")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for ((modeKey, modeLabel) in groupingModes) {
                    val isSelected = settings.timelineGrouping == modeKey
                    Surface(
                        onClick = { viewModel.updateSettings { it.copy(timelineGrouping = modeKey) } },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("group_mode_${modeKey.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (modeKey == "LOCATION") {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = modeLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Filter Pills Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (folderFilter != null) {
                Surface(
                    onClick = { viewModel.openFolderFilter(null) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "$folderFilter ✕",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
            if (locationFilter != null) {
                Surface(
                    onClick = { viewModel.openLocationFilter(null) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "📍 $locationFilter ✕",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            val filterOptions = listOf(
                "ALL" to strings.tr("All", "همه"),
                "VIDEOS" to strings.tr("Videos", "ویدیوها"),
                "FAVORITES" to strings.tr("Favorites", "برگزیده‌ها"),
                "RECENT" to strings.tr("Recent", "اخیر"),
                "HIDDEN" to strings.tr("Hidden", "مخفی"),
                "TRASH" to strings.tr("Trash", "سطل زباله")
            )
            for ((key, label) in filterOptions) {
                val selected = folderFilter == null && locationFilter == null && filterChip == key
                Surface(
                    onClick = { viewModel.setGalleryFilterChip(key) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("filter_chip_${key.lowercase()}")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Multi-selection action bar
        AnimatedVisibility(visible = selectedIds.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.tr("${selectedIds.size} selected", "${PersianNormalizer.formatNumber(selectedIds.size, isFa)} انتخاب‌شده"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (filterChip == "TRASH") {
                        FilledTonalButton(onClick = { viewModel.restoreFromTrash(selectedIds.toList()) }) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.tr("Restore", "بازگردانی"))
                        }
                        Button(onClick = { viewModel.deletePermanently(selectedIds.toList()) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.tr("Delete", "حذف دائم"))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = { viewModel.createCollageFromSelected() },
                            modifier = Modifier.testTag("batch_collage_button")
                        ) {
                            Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.tr("Collage", "کلاژ"))
                        }
                        FilledTonalButton(onClick = { viewModel.batchConvertSelected("WEBP") }) {
                            Icon(Icons.Default.FilterFrames, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WebP")
                        }
                        FilledTonalButton(
                            onClick = {
                                val targetHidden = filterChip != "HIDDEN"
                                viewModel.setHiddenState(selectedIds.toList(), targetHidden)
                            }
                        ) {
                            Icon(
                                if (filterChip == "HIDDEN") Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (filterChip == "HIDDEN") strings.tr("Unhide", "نمایش") else strings.tr("Hide", "مخفی"))
                        }
                        FilledTonalButton(onClick = { viewModel.moveSelectedToTrash(selectedIds.toList()) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.tr("Delete", "حذف"))
                        }
                    }
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
        }

        // Main Samsung Gallery Timeline & Smart Grouping Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (settings.memoriesEnabled && filterChip == "ALL" && folderFilter == null && locationFilter == null && filteredMedia.isNotEmpty()) {
                item(key = "memories_header") {
                    OneUiStoriesRow(
                        items = filteredMedia.filter { it.isFavorite }.ifEmpty { filteredMedia.take(5) },
                        isFa = isFa,
                        strings = strings,
                        onOpenItem = { viewModel.openMediaViewer(it.id) },
                        onStartSlideshow = { viewModel.setSlideshowActive(true) }
                    )
                }
            }

            for ((groupHeader, groupItems) in groupedBySmartMode) {
                item(key = "header_$groupHeader") {
                    val isLocMode = settings.timelineGrouping == "LOCATION"
                    val subCaption = if (isLocMode) {
                        val gps = groupItems.firstOrNull()?.extractSmartLocation(isFa)?.gpsCoordinates.orEmpty()
                        val dateRange = groupItems.firstOrNull()?.let {
                            JalaliCalendar.formatTimestamp(it.dateModifiedSec, settings.calendarSystem, isFa, "MONTH")
                        }.orEmpty()
                        listOf(gps, dateRange).filter { it.isNotBlank() }.joinToString("  •  ")
                    } else {
                        val distinctPlaces = groupItems.map { it.extractSmartLocation(isFa).label(isFa).substringBefore(" •") }
                            .distinct()
                            .take(2)
                            .joinToString("، ")
                        distinctPlaces
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isLocMode) Icons.Default.LocationOn else Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = groupHeader,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (subCaption.isNotBlank()) {
                                    Text(
                                        text = if (isLocMode) subCaption else "📍 $subCaption",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = PersianNormalizer.formatNumber(groupItems.size, isFa),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (settings.layoutMode == "LIST") {
                    items(groupItems, key = { it.id }) { item ->
                        MediaListRowItem(
                            item = item,
                            isSelected = item.id in selectedIds,
                            isFa = isFa,
                            cornerRadiusDp = settings.cornerRadiusDp,
                            onClick = {
                                if (selectedIds.isNotEmpty()) viewModel.toggleSelection(item.id)
                                else viewModel.openMediaViewer(item.id)
                            },
                            onLongClick = { viewModel.toggleSelection(item.id) },
                            onToggleFav = { viewModel.toggleFavorite(item) }
                        )
                    }
                } else {
                    val cols = settings.gridColumns.coerceIn(2, 5)
                    val rows = groupItems.chunked(cols)
                    items(rows) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 1.5.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            for (item in rowItems) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OneUiGridThumbnail(
                                        item = item,
                                        isSelected = item.id in selectedIds,
                                        isMasonry = settings.layoutMode == "MASONRY",
                                        cornerRadiusDp = settings.cornerRadiusDp,
                                        onClick = {
                                            if (selectedIds.isNotEmpty()) viewModel.toggleSelection(item.id)
                                            else viewModel.openMediaViewer(item.id)
                                        },
                                        onLongClick = { viewModel.toggleSelection(item.id) },
                                        onToggleFav = { viewModel.toggleFavorite(item) }
                                    )
                                }
                            }
                            repeat(cols - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OneUiStoriesRow(
    items: List<MediaItemEntity>,
    isFa: Boolean,
    strings: BilingualStrings,
    onOpenItem: (MediaItemEntity) -> Unit,
    onStartSlideshow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = strings.tr("Stories & Highlights", "داستان‌ها و لحظات منتخب"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                onClick = onStartSlideshow,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("start_slideshow_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = strings.tr("Slideshow", "پخش اسلایدشو"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { "mem_${it.id}" }) { item ->
                val imgModel = remember(item.contentUri) {
                    val u = Uri.parse(item.contentUri)
                    if (u.scheme == "file") File(u.path ?: "") else item.contentUri
                }
                val locCity = remember(item.id, isFa) {
                    item.extractSmartLocation(isFa).label(isFa).substringBefore(" •")
                }
                Box(
                    modifier = Modifier
                        .width(142.dp)
                        .height(182.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onOpenItem(item) }
                ) {
                    AsyncImage(
                        model = imgModel,
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "📍 $locCity",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    val tagLabel = item.parseAiTags().firstOrNull()?.let {
                        strings.tr(it.labelEn, it.labelFa)
                    } ?: item.folderName
                    Text(
                        text = tagLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OneUiGridThumbnail(
    item: MediaItemEntity,
    isSelected: Boolean,
    isMasonry: Boolean,
    cornerRadiusDp: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFav: () -> Unit
) {
    val ratio = if (isMasonry && item.height > 0 && item.width > 0) {
        (item.width.toFloat() / item.height.toFloat()).coerceIn(0.75f, 1.35f)
    } else {
        1f
    }
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val imgModel = remember(item.contentUri) {
        val u = Uri.parse(item.contentUri)
        if (u.scheme == "file") File(u.path ?: "") else item.contentUri
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("media_card_${item.id}")
    ) {
        AsyncImage(
            model = imgModel,
            contentDescription = item.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(Color.White, CircleShape)
            )
        } else if (item.isFavorite) {
            IconButton(
                onClick = onToggleFav,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (item.isVideo) {
            val totalSec = (item.durationMs / 1000L).coerceAtLeast(1L)
            val mm = totalSec / 60L
            val ss = totalSec % 60L
            val durStr = "%02d:%02d".format(java.util.Locale.US, mm, ss)
            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = durStr,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else if (item.ocrText.isNotBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = "OCR",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(3.dp)
                        .size(11.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaListRowItem(
    item: MediaItemEntity,
    isSelected: Boolean,
    isFa: Boolean,
    cornerRadiusDp: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFav: () -> Unit
) {
    val imgModel = remember(item.contentUri) {
        val u = Uri.parse(item.contentUri)
        if (u.scheme == "file") File(u.path ?: "") else item.contentUri
    }
    val locText = remember(item.id, isFa) {
        item.extractSmartLocation(isFa).label(isFa)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(cornerRadiusDp.coerceAtLeast(16).dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imgModel,
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "📍 $locText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.folderName} • ${PersianNormalizer.formatNumber((item.sizeBytes / 1024).toInt(), isFa)} KB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFav) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FullscreenSlideshowOverlay(
    items: List<MediaItemEntity>,
    strings: BilingualStrings,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(items.size) {
        while (true) {
            delay(3200)
            currentIndex = (currentIndex + 1) % items.size
        }
    }
    val current = items[currentIndex % items.size]
    val imgModel = remember(current.contentUri) {
        val u = Uri.parse(current.contentUri)
        if (u.scheme == "file") File(u.path ?: "") else current.contentUri
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = imgModel,
            contentDescription = current.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentIndex + 1} / ${items.size}",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            FilledTonalButton(onClick = onClose) {
                Text(strings.tr("Close", "بستن"))
            }
        }
    }
}

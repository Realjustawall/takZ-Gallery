package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.Locale

private fun formatExactStorage(bytes: Long, isFa: Boolean): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val raw = if (mb >= 1.0) {
        String.format(Locale.US, "%.2f MB (%d KB)", mb, bytes / 1024)
    } else {
        String.format(Locale.US, "%d KB (%d B)", (bytes / 1024).coerceAtLeast(1), bytes)
    }
    return if (isFa) {
        PersianNormalizer.formatDigits(
            raw.replace("MB", "مگابایت").replace("KB", "کیلوبایت").replace("B", "بایت"),
            true
        )
    } else {
        raw
    }
}

private fun formatShortSize(bytes: Long, isFa: Boolean): String {
    val kb = (bytes / 1024).coerceAtLeast(1)
    val mb = bytes / (1024.0 * 1024.0)
    val raw = if (mb >= 1.0) {
        String.format(Locale.US, "%.2f MB", mb)
    } else {
        "$kb KB"
    }
    return if (isFa) {
        PersianNormalizer.formatDigits(raw.replace("MB", "مگابایت").replace("KB", "کیلوبایت"), true)
    } else {
        raw
    }
}

private fun formatDurationBadge(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(1L)
    val mm = totalSec / 60L
    val ss = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mm, ss)
}

private fun videoResolutionTag(width: Int, height: Int): String {
    val maxDim = maxOf(width, height)
    return when {
        maxDim >= 3800 -> "4K UHD"
        maxDim >= 2500 -> "QHD 2K"
        maxDim >= 1900 -> "1080p FHD"
        maxDim >= 1200 -> "720p HD"
        else -> "SD"
    }
}

@Composable
fun AlbumsScreen(
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val virtualAlbums by viewModel.virtualAlbums.collectAsStateWithLifecycle()
    val isFa = settings.isPersianActive

    // Album Organization Mode: "FOLDERS", "LOCATIONS", "TEMPORAL"
    var albumTabMode by remember { mutableStateOf("FOLDERS") }
    var temporalGranularity by remember { mutableStateOf("MONTH") } // "DAY", "MONTH", "YEAR"

    var showCreateAlbumForm by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var newAlbumSmartRule by remember { mutableStateOf("") }
    var newAlbumIsSmart by remember { mutableStateOf(true) }

    val activeMedia = remember(allMedia) { allMedia.filter { !it.isTrashed && !it.isHidden } }
    val folderGroups = remember(activeMedia) {
        activeMedia.groupBy { it.folderName }
            .entries
            .sortedByDescending { entry -> entry.value.sumOf { it.sizeBytes } }
    }
    val locationGroups = remember(activeMedia, isFa) {
        activeMedia.groupBy { it.extractSmartLocation(isFa).label(isFa) }
            .entries
            .sortedByDescending { it.value.size }
    }
    val temporalGroups = remember(activeMedia, temporalGranularity, settings.calendarSystem, isFa) {
        activeMedia.groupBy { item ->
            JalaliCalendar.formatTimestamp(
                epochSeconds = item.dateModifiedSec,
                calendarSystem = settings.calendarSystem,
                isPersianLang = isFa,
                mode = temporalGranularity
            )
        }.entries.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("albums_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = strings.tr("Albums", "آلبوم‌ها"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = strings.tr(
                        "${folderGroups.size} folders • ${locationGroups.size} locations",
                        "${PersianNormalizer.formatNumber(folderGroups.size, isFa)} پوشه • ${PersianNormalizer.formatNumber(locationGroups.size, isFa)} مکان"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = { showCreateAlbumForm = !showCreateAlbumForm },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("toggle_create_album_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.tr("New Album", "آلبوم جدید"))
            }
        }

        // Samsung One UI Smart Album Mode Segmented Switcher (پوشه‌ها | مکان‌ها | زمان‌بندی هوشمند)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            val modes = listOf(
                Triple("FOLDERS", strings.tr("Folders", "پوشه‌ها"), Icons.Default.Folder),
                Triple("LOCATIONS", strings.tr("Locations", "آلبوم مکانی"), Icons.Default.LocationOn),
                Triple("TEMPORAL", strings.tr("Temporal", "آلبوم زمانی"), Icons.Default.CalendarMonth)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for ((key, title, icon) in modes) {
                    val selected = albumTabMode == key
                    Surface(
                        onClick = { albumTabMode = key },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("album_tab_${key.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (albumTabMode == "TEMPORAL") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "DAY" to strings.tr("Daily Albums", "آلبوم‌های روزانه"),
                    "MONTH" to strings.tr("Monthly Albums", "آلبوم‌های ماهانه"),
                    "YEAR" to strings.tr("Yearly Albums", "آلبوم‌های سالانه")
                ).forEach { (gKey, gLabel) ->
                    FilterChip(
                        selected = temporalGranularity == gKey,
                        onClick = { temporalGranularity = gKey },
                        label = { Text(gLabel) }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showCreateAlbumForm) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = strings.tr("New Album", "ایجاد آلبوم جدید"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedTextField(
                                value = newAlbumName,
                                onValueChange = { newAlbumName = it },
                                label = { Text(strings.tr("Title", "نام آلبوم")) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_album_name_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(strings.tr("Auto-Match by Tags", "افزودن خودکار با تگ"))
                                Switch(
                                    checked = newAlbumIsSmart,
                                    onCheckedChange = { newAlbumIsSmart = it }
                                )
                            }
                            if (newAlbumIsSmart) {
                                OutlinedTextField(
                                    value = newAlbumSmartRule,
                                    onValueChange = { newAlbumSmartRule = it },
                                    label = { Text(strings.tr("Tags (e.g. car tree)", "تگ‌ها (مثلاً: درخت جنگل)")) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.createVirtualAlbum(
                                        nameEn = newAlbumName,
                                        nameFa = newAlbumName,
                                        isSmart = newAlbumIsSmart,
                                        smartQuery = newAlbumSmartRule
                                    )
                                    newAlbumName = ""
                                    newAlbumSmartRule = ""
                                    showCreateAlbumForm = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_create_album_button")
                            ) {
                                Text(strings.tr("Save", "ذخیره"))
                            }
                        }
                    }
                }
            }

            when (albumTabMode) {
                "LOCATIONS" -> {
                    val locRows = locationGroups.chunked(2)
                    items(locRows) { rowEntries ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            for ((locLabel, items) in rowEntries) {
                                val gps = items.firstOrNull()?.extractSmartLocation(isFa)?.gpsCoordinates.orEmpty()
                                val bytes = items.sumOf { it.sizeBytes }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.updateSettings { it.copy(timelineGrouping = "LOCATION") }
                                            viewModel.openLocationFilter(locLabel)
                                        },
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OneUiMosaicAlbumCover(
                                        items = items,
                                        badgeText = "📍 ${PersianNormalizer.formatNumber(items.size, isFa)}"
                                    )
                                    Text(
                                        text = locLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$gps  •  ${formatShortSize(bytes, isFa)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (rowEntries.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                "TEMPORAL" -> {
                    val tempRows = temporalGroups.chunked(2)
                    items(tempRows) { rowEntries ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            for ((periodTitle, items) in rowEntries) {
                                val bytes = items.sumOf { it.sizeBytes }
                                val places = items.map { it.extractSmartLocation(isFa).label(isFa).substringBefore(" •") }
                                    .distinct()
                                    .take(2)
                                    .joinToString("، ")
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.updateSettings { it.copy(timelineGrouping = temporalGranularity) }
                                            viewModel.selectBottomTab("PHOTOS")
                                        },
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OneUiMosaicAlbumCover(
                                        items = items,
                                        badgeText = PersianNormalizer.formatNumber(items.size, isFa)
                                    )
                                    Text(
                                        text = periodTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "📍 $places • ${formatShortSize(bytes, isFa)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (rowEntries.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                else -> {
                    val folderRows = folderGroups.chunked(2)
                    items(folderRows) { rowEntries ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            for ((folderName, items) in rowEntries) {
                                val folderBytes = items.sumOf { it.sizeBytes }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.openFolderFilter(folderName) }
                                        .testTag("folder_card_$folderName"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OneUiMosaicAlbumCover(
                                        items = items,
                                        badgeText = PersianNormalizer.formatNumber(items.size, isFa)
                                    )
                                    Text(
                                        text = folderName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${PersianNormalizer.formatNumber(items.size, isFa)}  •  ${formatShortSize(folderBytes, isFa)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (rowEntries.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (virtualAlbums.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.tr("Smart Albums", "آلبوم‌های هوشمند"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        items(virtualAlbums, key = { "valbum_${it.albumId}" }) { album ->
                            val matchedItems = remember(activeMedia, album) {
                                if (album.isSmartRule && album.smartRuleQuery.isNotBlank()) {
                                    viewModel.searchRankingEngine.searchAndRank(
                                        allMedia = activeMedia,
                                        rawQuery = album.smartRuleQuery
                                    ).map { it.media }
                                } else {
                                    activeMedia.filter { it.isFavorite }
                                }
                            }
                            val albumBytes = matchedItems.sumOf { it.sizeBytes }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (album.isSmartRule && album.smartRuleQuery.isNotBlank()) {
                                            viewModel.updateSearchQuery(album.smartRuleQuery.split(" ").firstOrNull() ?: "")
                                            viewModel.selectBottomTab("SEARCH")
                                        } else {
                                            viewModel.setGalleryFilterChip("FAVORITES")
                                            viewModel.selectBottomTab("PHOTOS")
                                        }
                                    },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (album.isSmartRule) Icons.Default.AutoAwesome else Icons.Default.PhotoAlbum,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isFa) album.nameFa else album.nameEn,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = strings.tr(
                                                "${matchedItems.size} items • ${formatShortSize(albumBytes, false)}",
                                                "${PersianNormalizer.formatNumber(matchedItems.size, isFa)} مورد • ${formatShortSize(albumBytes, true)}"
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteVirtualAlbum(album.albumId) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun OneUiMosaicAlbumCover(
    items: List<MediaItemEntity>,
    badgeText: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (items.size >= 4) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (r in 0..1) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (c in 0..1) {
                            val sub = items[r * 2 + c]
                            val m = remember(sub.contentUri) {
                                val u = Uri.parse(sub.contentUri)
                                if (u.scheme == "file") File(u.path ?: "") else sub.contentUri
                            }
                            AsyncImage(
                                model = m,
                                contentDescription = sub.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        } else if (items.isNotEmpty()) {
            val cover = items.first()
            val m = remember(cover.contentUri) {
                val u = Uri.parse(cover.contentUri)
                if (u.scheme == "file") File(u.path ?: "") else cover.contentUri
            }
            AsyncImage(
                model = m,
                contentDescription = cover.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }

        Surface(
            color = Color.Black.copy(alpha = 0.62f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

private data class StorageCategorySlice(
    val filterKey: String,
    val titleEn: String,
    val titleFa: String,
    val count: Int,
    val bytes: Long,
    val color: Color
)

@Composable
fun SmartCleanupScreen(
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()
    val isFa = settings.isPersianActive

    // Active section filter pill ("ALL", "VIDEOS", "PHOTOS", "DUPLICATES", "SCREENSHOTS", "BLURRY", "FOLDERS")
    var selectedSection by remember { mutableStateOf("ALL") }

    val activeMedia = remember(allMedia) { allMedia.filter { !it.isTrashed && !it.isHidden } }
    val trashedMedia = remember(allMedia) { allMedia.filter { it.isTrashed } }
    val duplicateGroups = remember(activeMedia) {
        viewModel.similarityEngine.findDuplicateAndSimilarGroups(activeMedia)
    }

    val duplicateCopyIds = remember(duplicateGroups) {
        duplicateGroups.flatMap { grp ->
            grp.items.filter { it.id != grp.recommendedBestId }.map { it.id }
        }
    }

    // Separate Videos and Photos ranked by size
    val largestVideos = remember(activeMedia) {
        activeMedia.filter { it.isVideo }.sortedByDescending { it.sizeBytes }
    }
    val largestPhotos = remember(activeMedia) {
        activeMedia.filter { !it.isVideo }.sortedByDescending { it.sizeBytes }
    }
    val screenshotAndDocItems = remember(activeMedia) {
        activeMedia.filter {
            !it.isVideo && (it.folderName.equals("Screenshots", true) || it.ocrText.isNotBlank())
        }.sortedByDescending { it.sizeBytes }
    }
    val blurryOrLowSharpnessItems = remember(activeMedia) {
        activeMedia.filter { !it.isVideo && it.sharpnessScore in 0.1f..22.0f }
            .sortedBy { it.sharpnessScore }
    }

    val totalActiveBytes = remember(activeMedia) { activeMedia.sumOf { it.sizeBytes } }
    val totalVideoBytes = remember(largestVideos) { largestVideos.sumOf { it.sizeBytes } }
    val totalPhotoBytes = remember(largestPhotos) { largestPhotos.sumOf { it.sizeBytes } }
    val totalTrashedBytes = remember(trashedMedia) { trashedMedia.sumOf { it.sizeBytes } }
    val totalVaultBytes = remember(vaultItems) { vaultItems.sumOf { it.originalSizeBytes } }
    val grandTotalBytes = (totalActiveBytes + totalTrashedBytes + totalVaultBytes).coerceAtLeast(1L)

    val reclaimableDuplicateBytes = remember(duplicateGroups) {
        duplicateGroups.sumOf { group ->
            group.items.filter { it.id != group.recommendedBestId }.sumOf { it.sizeBytes }
        }
    }

    val categorySlices = remember(
        largestVideos,
        largestPhotos,
        screenshotAndDocItems,
        duplicateCopyIds,
        vaultItems,
        trashedMedia
    ) {
        val dupSet = duplicateCopyIds.toSet()
        val duplicates = activeMedia.filter { it.id in dupSet }
        val nonDupPhotos = largestPhotos.filter { it.id !in dupSet }
        val scrIds = screenshotAndDocItems.map { it.id }.toSet()
        val regularPhotos = nonDupPhotos.filter { it.id !in scrIds }

        listOf(
            StorageCategorySlice(
                filterKey = "VIDEOS",
                titleEn = "Largest Videos (4K / FHD)",
                titleFa = "بزرگ‌ترین ویدیوها (4K / FHD)",
                count = largestVideos.size,
                bytes = largestVideos.sumOf { it.sizeBytes },
                color = Color(0xFF3E82F7) // Samsung Blue
            ),
            StorageCategorySlice(
                filterKey = "PHOTOS",
                titleEn = "Camera & High-Res Photos",
                titleFa = "عکس‌های دوربین و باکیفیت",
                count = regularPhotos.size,
                bytes = regularPhotos.sumOf { it.sizeBytes },
                color = Color(0xFF34D399) // One UI Emerald
            ),
            StorageCategorySlice(
                filterKey = "SCREENSHOTS",
                titleEn = "Screenshots & Text Docs",
                titleFa = "اسکرین‌شات‌ها و تصاویر متنی",
                count = screenshotAndDocItems.size,
                bytes = screenshotAndDocItems.sumOf { it.sizeBytes },
                color = Color(0xFF8B5CF6) // One UI Violet
            ),
            StorageCategorySlice(
                filterKey = "DUPLICATES",
                titleEn = "Duplicate Copies (Reclaimable)",
                titleFa = "نسخه‌های تکراری (قابل آزادسازی)",
                count = duplicates.size,
                bytes = duplicates.sumOf { it.sizeBytes },
                color = Color(0xFFFBBF24) // One UI Gold
            ),
            StorageCategorySlice(
                filterKey = "FOLDERS",
                titleEn = "Private Vault & Trash",
                titleFa = "گاوصندوق و سطل زباله",
                count = vaultItems.size + trashedMedia.size,
                bytes = totalVaultBytes + totalTrashedBytes,
                color = Color(0xFF22C5D6) // One UI Teal
            )
        ).filter { it.count > 0 || it.bytes > 0L }
    }

    val folderBreakdown = remember(activeMedia) {
        activeMedia.groupBy { it.folderName }
            .map { (folder, list) ->
                Triple(folder, list.size, list.sumOf { it.sizeBytes })
            }
            .sortedByDescending { it.third }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("smart_cleanup_screen")
    ) {
        // Samsung One UI Cleanup Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = strings.tr("Storage & Cleanup", "مدیریت فضا و پاک‌سازی"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = strings.tr(
                        "Videos: ${formatShortSize(totalVideoBytes, false)} • Photos: ${formatShortSize(totalPhotoBytes, false)}",
                        "ویدیوها: ${formatShortSize(totalVideoBytes, true)} • عکس‌ها: ${formatShortSize(totalPhotoBytes, true)}"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (duplicateCopyIds.isNotEmpty()) {
                Button(
                    onClick = { viewModel.moveSelectedToTrash(duplicateCopyIds) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("clean_all_duplicates_button")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.tr(
                            "Clean (${formatShortSize(reclaimableDuplicateBytes, false)})",
                            "حذف تکراری (${formatShortSize(reclaimableDuplicateBytes, true)})"
                        ),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Interactive Cleanup Category Filter Pills
        val sectionTabs = listOf(
            "ALL" to strings.tr("All Overview", "همه بخش‌ها"),
            "VIDEOS" to strings.tr("Largest Videos (${largestVideos.size})", "بزرگ‌ترین ویدیوها (${PersianNormalizer.formatNumber(largestVideos.size, isFa)})"),
            "PHOTOS" to strings.tr("Largest Photos (${largestPhotos.size})", "بزرگ‌ترین عکس‌ها (${PersianNormalizer.formatNumber(largestPhotos.size, isFa)})"),
            "DUPLICATES" to strings.tr("Duplicates (${duplicateGroups.size})", "تکراری‌ها (${PersianNormalizer.formatNumber(duplicateGroups.size, isFa)})"),
            "SCREENSHOTS" to strings.tr("Screenshots (${screenshotAndDocItems.size})", "اسکرین‌شات‌ها (${PersianNormalizer.formatNumber(screenshotAndDocItems.size, isFa)})"),
            "BLURRY" to strings.tr("Low Quality (${blurryOrLowSharpnessItems.size})", "تار و کم‌کیفیت (${PersianNormalizer.formatNumber(blurryOrLowSharpnessItems.size, isFa)})"),
            "FOLDERS" to strings.tr("Folders & Trash", "پوشه‌ها و سطل زباله")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((key, title) in sectionTabs) {
                val active = selectedSection == key
                Surface(
                    onClick = { selectedSection = key },
                    shape = RoundedCornerShape(20.dp),
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("cleanup_filter_${key.lowercase()}")
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. One UI Device Care Storage Breakdown Card (Always visible in ALL)
            if (selectedSection == "ALL") {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = strings.tr("Total Gallery Storage", "کل فضای مصرف‌شده گالری"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatExactStorage(grandTotalBytes, isFa),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = strings.tr(
                                            "${activeMedia.size} items (${largestVideos.size} videos)",
                                            "${PersianNormalizer.formatNumber(activeMedia.size, isFa)} فایل (${PersianNormalizer.formatNumber(largestVideos.size, isFa)} ویدیو)"
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Multi-segment One UI storage bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                for (slice in categorySlices) {
                                    val weightRatio = (slice.bytes.toFloat() / grandTotalBytes.toFloat()).coerceAtLeast(0.04f)
                                    Box(
                                        modifier = Modifier
                                            .weight(weightRatio)
                                            .fillMaxHeight()
                                            .background(slice.color)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (slice in categorySlices) {
                                    val pct = ((slice.bytes.toDouble() / grandTotalBytes.toDouble()) * 100.0)
                                    val pctStr = if (isFa) {
                                        PersianNormalizer.formatDigits(String.format(Locale.US, "%.1f%%", pct), true)
                                    } else {
                                        String.format(Locale.US, "%.1f%%", pct)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                            .clickable { selectedSection = slice.filterKey }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(slice.color)
                                            )
                                            Text(
                                                text = if (isFa) slice.titleFa else slice.titleEn,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "(${PersianNormalizer.formatNumber(slice.count, isFa)})",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Text(
                                            text = "${formatShortSize(slice.bytes, isFa)} • $pctStr",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. LARGEST VIDEOS SECTION (بزرگ‌ترین ویدیوها — با قابلیت فشرده‌سازی و برش ویدیو یا حذف)
            if ((selectedSection == "ALL" || selectedSection == "VIDEOS") && largestVideos.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("largest_videos_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = strings.tr("Largest Videos", "بزرگ‌ترین ویدیوها"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = strings.tr(
                                                "Total video space: ${formatExactStorage(totalVideoBytes, false)}",
                                                "مجموع حجم ویدیوها: ${formatExactStorage(totalVideoBytes, true)}"
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            for ((idx, video) in largestVideos.withIndex()) {
                                VideoCleanupRowItem(
                                    rank = idx + 1,
                                    video = video,
                                    grandTotalBytes = grandTotalBytes,
                                    isFa = isFa,
                                    strings = strings,
                                    onOpenViewer = { viewModel.openMediaViewer(video.id) },
                                    onOpenVideoEditor = { viewModel.openPhotoEditor(video.id, tab = "VIDEO") },
                                    onDelete = { viewModel.moveSelectedToTrash(listOf(video.id)) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. DUPLICATE & SIMILAR PHOTOS SECTION
            if ((selectedSection == "ALL" || selectedSection == "DUPLICATES") && duplicateGroups.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.tr(
                                "Duplicates & Similar (${duplicateGroups.size})",
                                "عکس‌های تکراری و مشابه (${PersianNormalizer.formatNumber(duplicateGroups.size, isFa)})"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = strings.tr(
                                "Free up ${formatShortSize(reclaimableDuplicateBytes, false)}",
                                "آزادسازی ${formatShortSize(reclaimableDuplicateBytes, true)}"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(duplicateGroups, key = { "dup_${it.recommendedBestId}_${it.groupType}" }) { group ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("duplicate_group_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (group.groupType == "EXACT") {
                                            strings.tr("Exact Duplicate", "تکراری یکسان")
                                        } else {
                                            strings.tr(
                                                "Similar (${group.similarityPercent}%)",
                                                "مشابه (${PersianNormalizer.formatNumber(group.similarityPercent, isFa)}٪)"
                                            )
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                if (group.items.size >= 2) {
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.openMediaViewer(group.items[0].id)
                                            viewModel.startComparisonWith(group.items[1].id)
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.tr("Compare", "مقایسه"), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (item in group.items) {
                                    val isBest = item.id == group.recommendedBestId
                                    val imgModel = remember(item.contentUri) {
                                        val u = Uri.parse(item.contentUri)
                                        if (u.scheme == "file") File(u.path ?: "") else item.contentUri
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { viewModel.openMediaViewer(item.id) }
                                        ) {
                                            AsyncImage(
                                                model = imgModel,
                                                contentDescription = item.fileName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (isBest) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Star,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = strings.tr("Best", "بهترین"),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = item.fileName,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${formatShortSize(item.sizeBytes, isFa)} • ${item.width}×${item.height}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!isBest) {
                                            FilledTonalButton(
                                                onClick = { viewModel.moveSelectedToTrash(listOf(item.id)) },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(strings.tr("Remove", "حذف نسخه"), style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. LARGEST PHOTOS SECTION (بزرگ‌ترین عکس‌ها)
            if ((selectedSection == "ALL" || selectedSection == "PHOTOS") && largestPhotos.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.tr("Largest Photos", "بزرگ‌ترین عکس‌ها به ترتیب حجم"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatShortSize(totalPhotoBytes, isFa),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            val displayPhotos = if (selectedSection == "PHOTOS") largestPhotos else largestPhotos.take(6)
                            for ((idx, item) in displayPhotos.withIndex()) {
                                PhotoCleanupRowItem(
                                    rank = idx + 1,
                                    item = item,
                                    grandTotalBytes = grandTotalBytes,
                                    isFa = isFa,
                                    strings = strings,
                                    onOpenViewer = { viewModel.openMediaViewer(item.id) },
                                    onOpenEditor = { viewModel.openPhotoEditor(item.id, tab = "CURVES") },
                                    onDelete = { viewModel.moveSelectedToTrash(listOf(item.id)) }
                                )
                            }
                        }
                    }
                }
            }

            // 5. SCREENSHOTS & TEXT DOCUMENTS SECTION (اسکرین‌شات‌ها و اسناد متنی)
            if ((selectedSection == "ALL" || selectedSection == "SCREENSHOTS") && screenshotAndDocItems.isNotEmpty()) {
                item {
                    val scrBytes = screenshotAndDocItems.sumOf { it.sizeBytes }
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.tr(
                                            "Screenshots & Receipts (${screenshotAndDocItems.size})",
                                            "اسکرین‌شات‌ها و رسیدها (${PersianNormalizer.formatNumber(screenshotAndDocItems.size, isFa)})"
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatExactStorage(scrBytes, isFa),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.moveSelectedToTrash(screenshotAndDocItems.map { it.id }) },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.tr("Clean All", "حذف همه"), style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            for ((idx, item) in screenshotAndDocItems.withIndex()) {
                                PhotoCleanupRowItem(
                                    rank = idx + 1,
                                    item = item,
                                    grandTotalBytes = grandTotalBytes,
                                    isFa = isFa,
                                    strings = strings,
                                    onOpenViewer = { viewModel.openMediaViewer(item.id) },
                                    onOpenEditor = { viewModel.openPhotoEditor(item.id, tab = "CROP") },
                                    onDelete = { viewModel.moveSelectedToTrash(listOf(item.id)) }
                                )
                            }
                        }
                    }
                }
            }

            // 6. LOW QUALITY / BLURRY CANDIDATES SECTION (تصاویر تار یا کم‌کیفیت)
            if ((selectedSection == "ALL" || selectedSection == "BLURRY") && blurryOrLowSharpnessItems.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = strings.tr(
                                    "Low Focus / Soft Sharpness (${blurryOrLowSharpnessItems.size})",
                                    "تصاویر با وضوح پایین یا تار (${PersianNormalizer.formatNumber(blurryOrLowSharpnessItems.size, isFa)})"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            for ((idx, item) in blurryOrLowSharpnessItems.withIndex()) {
                                PhotoCleanupRowItem(
                                    rank = idx + 1,
                                    item = item,
                                    grandTotalBytes = grandTotalBytes,
                                    isFa = isFa,
                                    strings = strings,
                                    onOpenViewer = { viewModel.openMediaViewer(item.id) },
                                    onOpenEditor = { viewModel.openPhotoEditor(item.id, tab = "ADJUST") },
                                    onDelete = { viewModel.moveSelectedToTrash(listOf(item.id)) }
                                )
                            }
                        }
                    }
                }
            }

            // 7. FOLDER BREAKDOWN & TRASH MANAGEMENT
            if (selectedSection == "ALL" || selectedSection == "FOLDERS") {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = strings.tr("Usage by Folder", "مصرف دقیق فضا به تفکیک پوشه"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            for ((folderName, count, bytes) in folderBreakdown) {
                                val ratio = (bytes.toFloat() / totalActiveBytes.coerceAtLeast(1L).toFloat()).coerceIn(0.03f, 1f)
                                val pct = (ratio * 100f).toInt()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.openFolderFilter(folderName) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "$folderName (${PersianNormalizer.formatNumber(count, isFa)})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${formatExactStorage(bytes, isFa)} • ${PersianNormalizer.formatNumber(pct, isFa)}٪",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(5.dp))
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                if (trashedMedia.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.tr(
                                            "Trash Bin (${trashedMedia.size} items)",
                                            "سطل زباله (${PersianNormalizer.formatNumber(trashedMedia.size, isFa)} مورد)"
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatExactStorage(totalTrashedBytes, isFa),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { viewModel.restoreFromTrash(trashedMedia.map { it.id }) },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.tr("Restore", "بازگردانی"))
                                    }
                                    Button(
                                        onClick = { viewModel.emptyTrash() },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.tr("Empty Trash", "تخلیه کامل"))
                                    }
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
private fun VideoCleanupRowItem(
    rank: Int,
    video: MediaItemEntity,
    grandTotalBytes: Long,
    isFa: Boolean,
    strings: BilingualStrings,
    onOpenViewer: () -> Unit,
    onOpenVideoEditor: () -> Unit,
    onDelete: () -> Unit
) {
    val imgModel = remember(video.contentUri) {
        val u = Uri.parse(video.contentUri)
        if (u.scheme == "file") File(u.path ?: "") else video.contentUri
    }
    val resBadge = videoResolutionTag(video.width, video.height)
    val durBadge = formatDurationBadge(video.durationMs)
    val shareRatio = (video.sizeBytes.toFloat() / grandTotalBytes.toFloat()).coerceIn(0.04f, 1f)
    val pct = ((video.sizeBytes.toDouble() / grandTotalBytes.toDouble()) * 100.0)
    val pctText = if (isFa) {
        PersianNormalizer.formatDigits(String.format(Locale.US, "%.1f%%", pct), true)
    } else {
        String.format(Locale.US, "%.1f%%", pct)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .clickable { onOpenViewer() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = imgModel,
                    contentDescription = video.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = durBadge,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = resBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${PersianNormalizer.formatNumber(rank, isFa)}. ${video.fileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${video.width}×${video.height} • $durBadge • ${video.folderName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatExactStorage(video.sizeBytes, isFa)}  •  $pctText",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { shareRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenVideoEditor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("compress_trim_video_${video.id}")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.tr("Trim / Compress Video", "ویرایش و کاهش حجم ویدیو"),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            FilledTonalButton(
                onClick = onDelete,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(strings.tr("Delete", "حذف"), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PhotoCleanupRowItem(
    rank: Int,
    item: MediaItemEntity,
    grandTotalBytes: Long,
    isFa: Boolean,
    strings: BilingualStrings,
    onOpenViewer: () -> Unit,
    onOpenEditor: () -> Unit,
    onDelete: () -> Unit
) {
    val imgModel = remember(item.contentUri) {
        val u = Uri.parse(item.contentUri)
        if (u.scheme == "file") File(u.path ?: "") else item.contentUri
    }
    val pct = ((item.sizeBytes.toDouble() / grandTotalBytes.toDouble()) * 100.0)
    val pctStr = if (isFa) {
        PersianNormalizer.formatDigits(String.format(Locale.US, "%.1f%%", pct), true)
    } else {
        String.format(Locale.US, "%.1f%%", pct)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable { onOpenViewer() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = PersianNormalizer.formatNumber(rank, isFa),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp)
        )
        AsyncImage(
            model = imgModel,
            contentDescription = item.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.folderName} • ${item.width}×${item.height}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${formatExactStorage(item.sizeBytes, isFa)} • $pctStr",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onOpenEditor) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = strings.tr("Edit / Resize", "ویرایش و تغییر اندازه"),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.ai.SearchRankingEngine
import com.example.core.localization.BilingualStrings
import com.example.core.localization.PersianNormalizer
import com.example.ui.TakzGalleryViewModel
import java.io.File

data class DetectedTagItem(
    val keyNormalized: String,
    val labelFa: String,
    val labelEn: String,
    val count: Int
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchAndAiScreen(
    viewModel: TakzGalleryViewModel,
    strings: BilingualStrings
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val rankedResults by viewModel.rankedSearchResults.collectAsStateWithLifecycle()
    val referenceImage by viewModel.referenceImage.collectAsStateWithLifecycle()
    val photoSearchPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) viewModel.searchUsingImage(uri) }
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()

    val isFa = settings.isPersianActive
    var selectedTagKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Suggestions are intentionally limited to top hits, not every row in a large
    // gallery. Parsing thousands of tags during composition previously caused jank.
    val detectedTagsForQuery = remember(rankedResults, query, referenceImage) {
        if (query.isBlank() || referenceImage != null) {
            emptyList()
        } else {
            val normQuery = PersianNormalizer.normalizeForSearch(query)
            val words = normQuery.split(" ").filter { it.length >= 2 }
            val known = LinkedHashMap<String, DetectedTagItem>()
            for (res in rankedResults.take(24)) {
                val labels = res.media.parseAiTags().map { it.labelFa to it.labelEn } +
                    res.media.parseManualTags().map { it to it }
                for ((fa, en) in labels) {
                    val key = PersianNormalizer.normalizeForSearch(en)
                    if (key.isBlank()) continue
                    val faNorm = PersianNormalizer.normalizeForSearch(fa)
                    val matches = words.any { key.contains(it) || faNorm.contains(it) } ||
                        normQuery.contains(key) || normQuery.contains(faNorm)
                    if (!matches) continue
                    val prev = known[key]
                    known[key] = DetectedTagItem(
                        keyNormalized = key,
                        labelFa = fa,
                        labelEn = en,
                        count = (prev?.count ?: 0) + 1
                    )
                    if (known.size >= 48) break
                }
            }
            known.values.sortedByDescending { it.count }.take(6)
        }
    }

    // Suggestions are optional: never force a guessed label that hides relevant photos.
    LaunchedEffect(query) {
        selectedTagKeys = emptySet()
    }

    val displayedResults = remember(rankedResults, selectedTagKeys) {
        if (selectedTagKeys.isEmpty()) rankedResults
        else rankedResults.filter { result ->
            val keys = buildSet {
                result.media.parseAiTags().forEach { tag ->
                    add(PersianNormalizer.normalizeForSearch(tag.labelEn))
                    add(PersianNormalizer.normalizeForSearch(tag.labelFa))
                }
                result.media.parseManualTags().forEach {
                    add(PersianNormalizer.normalizeForSearch(it))
                }
            }
            selectedTagKeys.any { it in keys }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("search_and_ai_screen")
    ) {
        // Samsung One UI Search Header & Pill Search Input
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = strings.tr("Search", "جستجو"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = {
                    Text(
                        text = strings.tr("Search photos or text…", "جستجو در تصاویر…"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                selectedTagKeys = emptySet()
                                viewModel.updateSearchQuery("")
                            },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.commitSearchHistory(query) }),
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("semantic_search_input")
            )

            Text(
                text = strings.tr(
                    "Try: cat on a sofa  •  \"invoice 2024\"  •  type:video  •  color:blue  •  after:2024-01-01",
                    "نمونه: گربه روی مبل  •  \"شماره فاکتور\"  •  type:video  •  color:blue  •  after:2024-01-01"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {
                        photoSearchPicker.launch(
                            ActivityResultContracts.PickVisualMedia.Request(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                    },
                    label = {
                        Text(strings.tr("Search using a photo", "جست‌وجو با انتخاب عکس"))
                    },
                    modifier = Modifier.testTag("search_by_photo")
                )
                if (referenceImage != null) {
                    TextButton(onClick = { viewModel.clearImageSearch() }) {
                        Text(strings.tr("Clear photo", "حذف عکس مرجع"))
                    }
                }
            }
            if (referenceImage != null) {
                Text(
                    strings.tr(
                        "Finding visually similar photos already indexed on this device",
                        "جست‌وجوی عکس‌های مشابهِ پردازش‌شده در این دستگاه"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Optional detected tags, never auto-selected
            AnimatedVisibility(visible = query.isNotBlank() && detectedTagsForQuery.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        detectedTagsForQuery.forEachIndexed { idx, tagItem ->
                            val isSelected = tagItem.keyNormalized in selectedTagKeys
                            val displayLabel = if (isFa) tagItem.labelFa else tagItem.labelEn
                            val countLabel = PersianNormalizer.formatNumber(tagItem.count, isFa)

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val next = selectedTagKeys.toMutableSet()
                                    if (isSelected) {
                                        next.remove(tagItem.keyNormalized)
                                    } else {
                                        next.add(tagItem.keyNormalized)
                                    }
                                    selectedTagKeys = next
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                } else null,
                                label = {
                                    Text(
                                        text = "$displayLabel ($countLabel)",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("detected_tag_chip_$idx")
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (history.isNotEmpty() && query.isBlank()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.tr("Recent Searches", "جستجوهای اخیر"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.clearSearchHistory() }) {
                            Text(strings.tr("Clear", "پاک کردن"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (h in history) {
                            AssistChip(
                                onClick = { viewModel.updateSearchQuery(h.queryText) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = { Text(h.queryText, style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                val countStr = PersianNormalizer.formatNumber(displayedResults.size, isFa)
                Text(
                    text = if (query.isBlank()) {
                        strings.tr("All Photos ($countStr)", "همه تصاویر ($countStr)")
                    } else {
                        strings.tr("Results ($countStr)", "نتایج یافت‌شده ($countStr)")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (displayedResults.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.tr("No matching photos found", "تصویری یافت نشد"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Samsung One UI 3-Column Clean Photo Grid for Search Results
                val rows = displayedResults.chunked(3)
                items(rows) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (res in rowItems) {
                            val item = res.media
                            val imgModel = remember(item.contentUri) {
                                val u = Uri.parse(item.contentUri)
                                if (u.scheme == "file") File(u.path ?: "") else item.contentUri
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.openMediaViewer(item.id) }
                                    .testTag("search_result_${item.id}")
                            ) {
                                AsyncImage(
                                    model = imgModel,
                                    contentDescription = item.fileName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

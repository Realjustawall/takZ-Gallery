package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ai.ImageEmbeddingEngine
import com.example.core.ai.ImageQualityEngine
import com.example.core.ai.ImageSegmentationEngine
import com.example.core.ai.ImageTaggingEngine
import com.example.core.ai.OCRRecognitionEngine
import com.example.core.ai.SearchRankingEngine
import com.example.core.ai.SimilarityEngine
import com.example.core.ai.TextEmbeddingEngine
import com.example.core.database.AlbumMediaCrossRef
import com.example.core.database.MediaItemEntity
import com.example.core.database.SearchHistoryEntity
import com.example.core.database.TakzDatabase
import com.example.core.database.VaultItemEntity
import com.example.core.database.VirtualAlbumEntity
import com.example.core.localization.AppLanguage
import com.example.core.localization.CalendarSystem
import com.example.core.media.MediaLibraryManager
import com.example.core.ai.BackgroundAiScheduler
import com.example.core.media.PhotoEditPipelineState
import com.example.core.media.PhotoProcessingEngine
import com.example.core.security.VaultCryptoEngine
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UserSettingsState(
    val language: AppLanguage = AppLanguage.PERSIAN,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentPreset: AccentPreset = AccentPreset.CYAN_PRISM,
    val customNeonHue: Float = 215f,
    val dynamicColor: Boolean = false,
    val calendarSystem: CalendarSystem = CalendarSystem.JALALI,
    val gridColumns: Int = 3,
    val cornerRadiusDp: Int = 8,
    val timelineGrouping: String = "DAY", // "DAY", "MONTH", "YEAR"
    val layoutMode: String = "GRID", // "GRID", "MASONRY", "LIST"
    val reduceMotion: Boolean = false,
    val hapticEnabled: Boolean = true,
    val blockVaultScreenshots: Boolean = true,
    val memoriesEnabled: Boolean = true
) {
    val isPersianActive: Boolean
        get() = when (language) {
            AppLanguage.PERSIAN -> true
            AppLanguage.ENGLISH -> false
            AppLanguage.SYSTEM -> java.util.Locale.getDefault().language == "fa"
        }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
class TakzGalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("takz_oneui_settings_v3", Application.MODE_PRIVATE)
    private val db = TakzDatabase.getInstance(context)
    val dao = db.takzDao()

    val imageEmbeddingEngine = ImageEmbeddingEngine(context)
    val textEmbeddingEngine = TextEmbeddingEngine(context)
    val imageTaggingEngine = ImageTaggingEngine()
    val ocrEngine = OCRRecognitionEngine(context)
    val segmentationEngine = ImageSegmentationEngine(context)
    val similarityEngine = SimilarityEngine()
    val qualityEngine = ImageQualityEngine()
    val searchRankingEngine = SearchRankingEngine(textEmbeddingEngine)
    val vaultCryptoEngine = VaultCryptoEngine(context)

    val libraryManager = MediaLibraryManager(
        context = context,
        dao = dao,
        imageEmbeddingEngine = imageEmbeddingEngine,
        textEmbeddingEngine = textEmbeddingEngine,
        imageTaggingEngine = imageTaggingEngine,
        ocrEngine = ocrEngine,
        segmentationEngine = segmentationEngine,
        similarityEngine = similarityEngine,
        qualityEngine = qualityEngine,
        vaultCryptoEngine = vaultCryptoEngine
    )

    private val _settings = MutableStateFlow(loadSettingsFromPrefs())
    val settings: StateFlow<UserSettingsState> = _settings.asStateFlow()

    val allMedia: StateFlow<List<MediaItemEntity>> = dao.observeAllMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val virtualAlbums: StateFlow<List<VirtualAlbumEntity>> = dao.observeVirtualAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumCrossRefs: StateFlow<List<AlbumMediaCrossRef>> = dao.observeAllAlbumCrossRefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultItems: StateFlow<List<VaultItemEntity>> = dao.observeVaultItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = dao.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val indexingStatus = libraryManager.indexingStatus

    // Navigation & active screens
    private val _activeBottomTab = MutableStateFlow("PHOTOS")
    val activeBottomTab: StateFlow<String> = _activeBottomTab.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _galleryFilterChip = MutableStateFlow("ALL") // "ALL", "FAVORITES", "VIDEOS", "RECENT", "HIDDEN", "TRASH"
    val galleryFilterChip: StateFlow<String> = _galleryFilterChip.asStateFlow()

    private val _selectedFolderFilter = MutableStateFlow<String?>(null)
    val selectedFolderFilter: StateFlow<String?> = _selectedFolderFilter.asStateFlow()

    private val _selectedLocationFilter = MutableStateFlow<String?>(null)
    val selectedLocationFilter: StateFlow<String?> = _selectedLocationFilter.asStateFlow()

    private val _selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMediaIds: StateFlow<Set<Long>> = _selectedMediaIds.asStateFlow()

    private val _openedMediaId = MutableStateFlow<Long?>(null)
    val openedMediaId: StateFlow<Long?> = _openedMediaId.asStateFlow()

    private val _compareMediaId = MutableStateFlow<Long?>(null)
    val compareMediaId: StateFlow<Long?> = _compareMediaId.asStateFlow()

    private val _editingMediaId = MutableStateFlow<Long?>(null)
    val editingMediaId: StateFlow<Long?> = _editingMediaId.asStateFlow()

    private val _initialEditorTab = MutableStateFlow("ADJUST")
    val initialEditorTab: StateFlow<String> = _initialEditorTab.asStateFlow()

    private val _slideshowActive = MutableStateFlow(false)
    val slideshowActive: StateFlow<Boolean> = _slideshowActive.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilters = MutableStateFlow(SearchRankingEngine.SearchFilters())
    val searchFilters: StateFlow<SearchRankingEngine.SearchFilters> = _searchFilters.asStateFlow()

    val rankedSearchResults: StateFlow<List<SearchRankingEngine.RankedSearchResult>> =
        combine(allMedia, _searchQuery.debounce(240).distinctUntilChanged(), _searchFilters) { media, query, filters ->
            searchRankingEngine.searchAndRank(media, query, filters)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Vault state
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _isPinConfigured = MutableStateFlow(vaultCryptoEngine.isPinConfigured())
    val isPinConfigured: StateFlow<Boolean> = _isPinConfigured.asStateFlow()

    init {
        BackgroundAiScheduler.schedule(context)
        // Only MediaStore metadata is loaded here; photo decode, OCR and AI stay idle-only.
        viewModelScope.launch(Dispatchers.IO) { libraryManager.initializeLibraryMetadata() }
    }

    fun refreshGalleryAfterPermission() {
        viewModelScope.launch(Dispatchers.IO) {
            libraryManager.scanDeviceMediaStore()
            BackgroundAiScheduler.schedule(context)
        }
    }

    fun showStatusMessage(msg: String?) {
        _statusBannerMessage.value = msg
    }

    fun selectBottomTab(tab: String) {
        _isSettingsOpen.value = false
        _activeBottomTab.value = tab
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun setGalleryFilterChip(chip: String) {
        _galleryFilterChip.value = chip
        _selectedFolderFilter.value = null
        _selectedLocationFilter.value = null
    }

    fun openFolderFilter(folderName: String?) {
        _selectedFolderFilter.value = folderName
        _selectedLocationFilter.value = null
        if (folderName != null) {
            _activeBottomTab.value = "PHOTOS"
        }
    }

    fun openLocationFilter(locationLabel: String?) {
        _selectedLocationFilter.value = locationLabel
        _selectedFolderFilter.value = null
        if (locationLabel != null) {
            _activeBottomTab.value = "PHOTOS"
        }
    }

    fun toggleSelection(mediaId: Long) {
        val current = _selectedMediaIds.value.toMutableSet()
        if (mediaId in current) current.remove(mediaId) else current.add(mediaId)
        _selectedMediaIds.value = current
    }

    fun clearSelection() {
        _selectedMediaIds.value = emptySet()
    }

    fun openMediaViewer(mediaId: Long) {
        _openedMediaId.value = mediaId
        _compareMediaId.value = null
    }

    fun closeMediaViewer() {
        _openedMediaId.value = null
        _compareMediaId.value = null
    }

    fun startComparisonWith(secondMediaId: Long?) {
        _compareMediaId.value = secondMediaId
    }

    fun openPhotoEditor(mediaId: Long, tab: String = "ADJUST") {
        _initialEditorTab.value = tab
        _editingMediaId.value = mediaId
    }

    fun closePhotoEditor() {
        _editingMediaId.value = null
    }

    fun setSlideshowActive(active: Boolean) {
        _slideshowActive.value = active
    }

    // Search operations
    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun commitSearchHistory(q: String) {
        if (q.isNotBlank()) {
            viewModelScope.launch {
                dao.insertSearchHistory(SearchHistoryEntity(queryText = q.trim()))
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { dao.clearSearchHistory() }
    }

    fun updateSearchFilters(newFilters: SearchRankingEngine.SearchFilters) {
        _searchFilters.value = newFilters
    }

    // Media operations
    fun toggleFavorite(item: MediaItemEntity) {
        viewModelScope.launch {
            dao.setFavorite(item.id, !item.isFavorite)
        }
    }

    fun addManualTag(item: MediaItemEntity, newTag: String) {
        val clean = newTag.trim().replace(",", "")
        if (clean.isBlank()) return
        val updated = (item.parseManualTags() + clean).distinct().joinToString(",")
        viewModelScope.launch {
            dao.updateManualTags(item.id, updated)
            showStatusMessage("Added tag: $clean")
        }
    }

    fun removeManualTag(item: MediaItemEntity, tagToRemove: String) {
        val updated = item.parseManualTags().filterNot { it.equals(tagToRemove, ignoreCase = true) }.joinToString(",")
        viewModelScope.launch {
            dao.updateManualTags(item.id, updated)
        }
    }

    fun saveUpdatedOcrText(item: MediaItemEntity, editedOcrText: String) {
        viewModelScope.launch {
            dao.updateOcrText(item.id, editedOcrText)
            showStatusMessage("Saved OCR text locally")
        }
    }

    fun moveSelectedToTrash(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            dao.setTrashedBatch(ids, true)
            clearSelection()
            if (_openedMediaId.value in ids) closeMediaViewer()
            showStatusMessage("${ids.size} item(s) moved to Trash")
        }
    }

    fun restoreFromTrash(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            dao.setTrashedBatch(ids, false)
            clearSelection()
            showStatusMessage("Restored ${ids.size} item(s)")
        }
    }

    fun deletePermanently(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val (deleted, denied) = libraryManager.deleteOwnedMediaFiles(ids)
            clearSelection()
            if (_openedMediaId.value in ids && deleted > 0) closeMediaViewer()
            showStatusMessage("Deleted $deleted files; $denied external files require Android system permission")
        }
    }

    fun setHiddenState(ids: List<Long>, hidden: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            dao.setHiddenBatch(ids, hidden)
            clearSelection()
            showStatusMessage(if (hidden) "Hidden from main gallery" else "Unhidden to main gallery")
        }
    }

    fun moveItemsToFolder(ids: List<Long>, targetFolder: String) {
        if (ids.isEmpty() || targetFolder.isBlank()) return
        viewModelScope.launch {
            dao.moveMediaToFolder(ids, targetFolder.trim())
            clearSelection()
            showStatusMessage("Moved ${ids.size} item(s) to $targetFolder")
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            dao.renamePhysicalFolder(oldName, newName.trim())
            showStatusMessage("Renamed folder to ${newName.trim()}")
        }
    }

    fun createVirtualAlbum(nameEn: String, nameFa: String, isSmart: Boolean, smartQuery: String) {
        if (nameEn.isBlank() && nameFa.isBlank()) return
        viewModelScope.launch {
            dao.insertVirtualAlbum(
                VirtualAlbumEntity(
                    nameEn = nameEn.ifBlank { nameFa },
                    nameFa = nameFa.ifBlank { nameEn },
                    isSmartRule = isSmart,
                    smartRuleQuery = smartQuery
                )
            )
            showStatusMessage("Created virtual album: ${nameEn.ifBlank { nameFa }}")
        }
    }

    fun deleteVirtualAlbum(albumId: Long) {
        viewModelScope.launch {
            dao.deleteVirtualAlbum(albumId)
            showStatusMessage("Deleted virtual album (underlying photos preserved)")
        }
    }

    fun addMediaToVirtualAlbum(albumId: Long, mediaIds: List<Long>) {
        viewModelScope.launch {
            for (mId in mediaIds) {
                dao.insertAlbumCrossRef(AlbumMediaCrossRef(albumId = albumId, mediaId = mId))
            }
            clearSelection()
            showStatusMessage("Added to virtual album")
        }
    }

    fun createCollageFromSelected() {
        val ids = _selectedMediaIds.value
        val items = allMedia.value.filter { it.id in ids }
        if (items.isEmpty()) return
        viewModelScope.launch {
            val collage = libraryManager.createCollageFromSelection(items)
            clearSelection()
            if (collage != null) {
                showStatusMessage("Created studio collage: ${collage.fileName}")
            }
        }
    }

    fun batchConvertSelected(format: String) {
        val ids = _selectedMediaIds.value
        val items = allMedia.value.filter { it.id in ids }
        if (items.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            for (item in items) {
                val bmp = withContext(Dispatchers.IO) { libraryManager.loadSampledBitmap(item.contentUri, 1200) }
                if (bmp != null) {
                    libraryManager.saveExportedBitmap(bmp, prefix = "Batch_${item.id}", format = format, folderName = "Converted")
                    count++
                }
            }
            clearSelection()
            showStatusMessage("Converted $count photo(s) to $format")
        }
    }

    fun importFromPhotoPicker(uri: Uri) {
        viewModelScope.launch {
            val imported = libraryManager.importPickedUri(uri)
            if (imported != null) {
                showStatusMessage("Imported; AI tagging queued: ${imported.fileName}")
            }
        }
    }

    fun triggerReindexAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.resetImageIndex()
                libraryManager.scanDeviceMediaStore()
            }
            BackgroundAiScheduler.schedule(context)
            showStatusMessage("Queued AI re-indexing for idle charging time")
        }
    }

    fun saveNonDestructiveEditAndExport(
        item: MediaItemEntity,
        pipelineState: PhotoEditPipelineState,
        processedBitmap: Bitmap,
        exportFormat: String = "JPEG"
    ) {
        viewModelScope.launch {
            dao.updateEditPipeline(item.id, pipelineState.serialize())
            val fullResolution = withContext(Dispatchers.Default) {
                libraryManager.loadSampledBitmap(item.contentUri, 4096)?.let {
                    PhotoProcessingEngine.renderPipeline(it, pipelineState)
                } ?: processedBitmap
            }
            val exported = libraryManager.saveExportedBitmap(
                bitmap = fullResolution,
                prefix = "Edited_${item.fileName.substringBeforeLast('.')}",
                format = exportFormat,
                folderName = "TakZ Studio Edits"
            )
            closePhotoEditor()
            openMediaViewer(exported.id)
            showStatusMessage("Saved as new copy: ${exported.fileName}")
        }
    }

    /**
     * Overwrites the original photo file in place (Samsung One UI "Save / Overwrite Original").
     */
    fun saveOverwriteOriginalPhoto(
        item: MediaItemEntity,
        pipelineState: PhotoEditPipelineState,
        processedBitmap: Bitmap,
        format: String = "JPEG"
    ) {
        viewModelScope.launch {
            val serialized = pipelineState.serialize()
            val fullResolution = withContext(Dispatchers.Default) {
                libraryManager.loadSampledBitmap(item.contentUri, 4096)?.let {
                    PhotoProcessingEngine.renderPipeline(it, pipelineState)
                } ?: processedBitmap
            }
            val updated = try { libraryManager.overwriteExistingBitmap(
                originalItem = item,
                bitmap = fullResolution,
                format = format,
                pipelineSerialized = serialized
            ) } catch (e: Exception) {
                showStatusMessage("Photo save failed: ${e.message}")
                return@launch
            }
            closePhotoEditor()
            openMediaViewer(updated.id)
            showStatusMessage("Photo saved safely: ${updated.fileName}")
        }
    }

    fun saveBackgroundRemovedExport(
        originalItem: MediaItemEntity,
        compositedBitmap: Bitmap,
        exportFormat: String // "PNG" or "WEBP"
    ) {
        viewModelScope.launch {
            val exported = libraryManager.saveExportedBitmap(
                bitmap = compositedBitmap,
                prefix = "Cutout_${originalItem.fileName.substringBeforeLast('.')}",
                format = exportFormat,
                folderName = "AI Cutouts"
            )
            closePhotoEditor()
            openMediaViewer(exported.id)
            showStatusMessage("Exported transparent $exportFormat: ${exported.fileName}")
        }
    }

    fun setAsWallpaper(item: MediaItemEntity) {
        viewModelScope.launch {
            val ok = libraryManager.setAsWallpaper(item)
            showStatusMessage(if (ok) "Wallpaper updated: ${item.fileName}" else "Saved wallpaper preview")
        }
    }

    fun saveEditedVideoAndExport(
        item: MediaItemEntity,
        pipelineState: PhotoEditPipelineState,
        previewBitmap: Bitmap,
        trimStartRatio: Float,
        trimEndRatio: Float,
        playbackSpeed: Float,
        targetResolutionLabel: String,
        muteAudio: Boolean
    ) {
        viewModelScope.launch {
            dao.updateEditPipeline(item.id, pipelineState.serialize())
            val exported = try { libraryManager.saveExportedVideo(
                originalItem = item,
                previewFrame = previewBitmap,
                trimStartRatio = trimStartRatio,
                trimEndRatio = trimEndRatio,
                playbackSpeed = playbackSpeed,
                targetResolutionLabel = targetResolutionLabel,
                muteAudio = muteAudio
            ) } catch (e: Exception) {
                showStatusMessage("Video export unavailable: ${e.message}")
                return@launch
            }
            closePhotoEditor()
            openMediaViewer(exported.id)
            showStatusMessage("Saved as new video copy (${targetResolutionLabel} • ${playbackSpeed}x): ${exported.fileName}")
        }
    }

    /**
     * Overwrites the original video file in place (Samsung One UI "Save / Overwrite Original").
     */
    fun saveOverwriteOriginalVideo(
        item: MediaItemEntity,
        pipelineState: PhotoEditPipelineState,
        previewBitmap: Bitmap,
        trimStartRatio: Float,
        trimEndRatio: Float,
        playbackSpeed: Float,
        targetResolutionLabel: String,
        muteAudio: Boolean
    ) {
        viewModelScope.launch {
            val serialized = pipelineState.serialize()
            val updated = try { libraryManager.overwriteExistingVideo(
                originalItem = item,
                previewFrame = previewBitmap,
                trimStartRatio = trimStartRatio,
                trimEndRatio = trimEndRatio,
                playbackSpeed = playbackSpeed,
                targetResolutionLabel = targetResolutionLabel,
                muteAudio = muteAudio,
                pipelineSerialized = serialized
            ) } catch (e: Exception) {
                showStatusMessage("Photo save failed: ${e.message}")
                return@launch
            }
            closePhotoEditor()
            openMediaViewer(updated.id)
            showStatusMessage("Original preserved; saved edited video copy: ${updated.fileName}")
        }
    }

    fun shareMediaWithPrivacyOptions(
        item: MediaItemEntity,
        stripLocation: Boolean,
        stripCameraExif: Boolean
    ) {
        viewModelScope.launch {
            libraryManager.shareMediaItemWithPrivacy(
                item = item,
                stripLocation = stripLocation,
                stripCameraExif = stripCameraExif
            )
            val msg = when {
                stripLocation && stripCameraExif -> "Sharing clean file without GPS & Camera EXIF"
                stripLocation -> "Sharing file without GPS location"
                stripCameraExif -> "Sharing file without Camera EXIF"
                else -> "Sharing original file"
            }
            showStatusMessage(msg)
        }
    }

    fun stripMetadataPermanently(
        item: MediaItemEntity,
        stripLocation: Boolean,
        stripCameraExif: Boolean
    ) {
        viewModelScope.launch {
            val updated = libraryManager.stripMetadataFromItem(
                item = item,
                stripLocation = stripLocation,
                stripCameraExif = stripCameraExif
            )
            showStatusMessage("Removed metadata from ${updated.fileName}")
        }
    }

    fun emptyTrash() {
        val trashedIds = allMedia.value.filter { it.isTrashed }.map { it.id }
        if (trashedIds.isEmpty()) return
        deletePermanently(trashedIds)
    }

    fun extractVideoFrameOrMotionSlide(item: MediaItemEntity) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { libraryManager.extractActualVideoFrame(item, 0L) }
            if (bmp != null) {
                val saved = libraryManager.saveExportedBitmap(
                    bitmap = bmp,
                    prefix = "Frame_${item.fileName.substringBeforeLast('.')}",
                    format = "PNG",
                    folderName = "Extracted Frames"
                )
                showStatusMessage("Extracted frame: ${saved.fileName}")
            }
        }
    }

    // Vault Operations
    fun setupOrChangeVaultPin(pin: String): Boolean {
        if (vaultCryptoEngine.isPinConfigured() && !_isVaultUnlocked.value) return false
        val ok = vaultCryptoEngine.setOrUpdatePin(pin)
        if (ok) {
            _isPinConfigured.value = true
            _isVaultUnlocked.value = true
            showStatusMessage("Vault AES-256 PIN configured via PBKDF2-SHA256")
        }
        return ok
    }

    fun unlockVault(pin: String): Boolean {
        val verified = vaultCryptoEngine.verifyPin(pin)
        if (verified) {
            _isVaultUnlocked.value = true
        }
        return verified
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
    }

    fun encryptMediaToVault(item: MediaItemEntity) {
        viewModelScope.launch {
            val v = try { libraryManager.moveItemToSecureVault(item) } catch (e: Exception) {
                showStatusMessage("Vault encryption failed: ${e.message}")
                return@launch
            }
            if (v != null) {
                if (_openedMediaId.value == item.id) closeMediaViewer()
                showStatusMessage("Encrypted ${item.fileName} with AES-256-GCM into Secure Vault")
            } else showStatusMessage("Only app-owned imported files can be securely moved; external files cannot be erased safely")
        }
    }

    fun decryptVaultItemToGallery(vaultItem: VaultItemEntity) {
        viewModelScope.launch {
            val restored = try { libraryManager.restoreItemFromSecureVault(vaultItem) } catch (e: Exception) {
                showStatusMessage("Vault restore failed: ${e.message}")
                return@launch
            }
            if (restored != null) {
                showStatusMessage("Decrypted & restored ${restored.fileName}")
            }
        }
    }

    // Settings updates
    fun updateSettings(transform: (UserSettingsState) -> UserSettingsState) {
        val updated = transform(_settings.value)
        _settings.value = updated
        saveSettingsToPrefs(updated)
    }

    fun resetSettingsToDefault() {
        val def = UserSettingsState()
        _settings.value = def
        saveSettingsToPrefs(def)
        showStatusMessage("Reset settings to studio defaults")
    }

    private fun loadSettingsFromPrefs(): UserSettingsState {
        val langCode = prefs.getString("language", AppLanguage.PERSIAN.code) ?: AppLanguage.PERSIAN.code
        val themeId = prefs.getString("themeMode", ThemeMode.DARK.id) ?: ThemeMode.DARK.id
        val accentId = prefs.getString("accentPreset", AccentPreset.CYAN_PRISM.id) ?: AccentPreset.CYAN_PRISM.id
        val calId = prefs.getString("calendarSystem", CalendarSystem.JALALI.id) ?: CalendarSystem.JALALI.id
        return UserSettingsState(
            language = AppLanguage.entries.find { it.code == langCode } ?: AppLanguage.PERSIAN,
            themeMode = ThemeMode.entries.find { it.id == themeId } ?: ThemeMode.DARK,
            accentPreset = AccentPreset.entries.find { it.id == accentId } ?: AccentPreset.CYAN_PRISM,
            customNeonHue = prefs.getFloat("customNeonHue", 215f),
            dynamicColor = prefs.getBoolean("dynamicColor", false),
            calendarSystem = CalendarSystem.entries.find { it.id == calId } ?: CalendarSystem.JALALI,
            gridColumns = prefs.getInt("gridColumns", 3).coerceIn(2, 5),
            cornerRadiusDp = prefs.getInt("cornerRadiusDp", 8).coerceIn(0, 24),
            timelineGrouping = prefs.getString("timelineGrouping", "DAY") ?: "DAY",
            layoutMode = prefs.getString("layoutMode", "GRID") ?: "GRID",
            reduceMotion = prefs.getBoolean("reduceMotion", false),
            hapticEnabled = prefs.getBoolean("hapticEnabled", true),
            blockVaultScreenshots = prefs.getBoolean("blockVaultScreenshots", true),
            memoriesEnabled = prefs.getBoolean("memoriesEnabled", true)
        )
    }

    private fun saveSettingsToPrefs(s: UserSettingsState) {
        prefs.edit()
            .putString("language", s.language.code)
            .putString("themeMode", s.themeMode.id)
            .putString("accentPreset", s.accentPreset.id)
            .putFloat("customNeonHue", s.customNeonHue)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putString("calendarSystem", s.calendarSystem.id)
            .putInt("gridColumns", s.gridColumns)
            .putInt("cornerRadiusDp", s.cornerRadiusDp)
            .putString("timelineGrouping", s.timelineGrouping)
            .putString("layoutMode", s.layoutMode)
            .putBoolean("reduceMotion", s.reduceMotion)
            .putBoolean("hapticEnabled", s.hapticEnabled)
            .putBoolean("blockVaultScreenshots", s.blockVaultScreenshots)
            .putBoolean("memoriesEnabled", s.memoriesEnabled)
            .apply()
    }
}

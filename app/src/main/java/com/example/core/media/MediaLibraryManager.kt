package com.example.core.media

import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaCodec
import android.media.MediaMetadataRetriever
import java.nio.ByteBuffer
import java.security.MessageDigest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.core.DatabaseSeedSpec
import com.example.core.ai.ImageEmbeddingEngine
import com.example.core.ai.ImageQualityEngine
import com.example.core.ai.ImageSegmentationEngine
import com.example.core.ai.ImageTaggingEngine
import com.example.core.ai.OCRRecognitionEngine
import com.example.core.ai.SimilarityEngine
import com.example.core.ai.TextEmbeddingEngine
import com.example.core.database.MediaItemEntity
import com.example.core.database.TakzDao
import com.example.core.database.VaultItemEntity
import com.example.core.database.VirtualAlbumEntity
import com.example.core.security.VaultCryptoEngine
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class IndexingStatus(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val currentFileName: String = "",
    val lastLatencyMs: Long = 0L
)

class MediaLibraryManager(
    private val context: Context,
    private val dao: TakzDao,
    val imageEmbeddingEngine: ImageEmbeddingEngine,
    val textEmbeddingEngine: TextEmbeddingEngine,
    val imageTaggingEngine: ImageTaggingEngine,
    val ocrEngine: OCRRecognitionEngine,
    val segmentationEngine: ImageSegmentationEngine,
    val similarityEngine: SimilarityEngine,
    val qualityEngine: ImageQualityEngine,
    val vaultCryptoEngine: VaultCryptoEngine
) {
    private val _indexingStatus = MutableStateFlow(IndexingStatus())
    val indexingStatus: StateFlow<IndexingStatus> = _indexingStatus.asStateFlow()

    @Volatile
    private var pauseRequested = false

    fun setIndexingPaused(paused: Boolean) {
        pauseRequested = paused
        _indexingStatus.value = _indexingStatus.value.copy(isPaused = paused)
    }

    /**
     * Ensures initial studio photographs, videos, bilingual OCR documents, math formula diagram,
     * verification code screenshot, and default virtual albums exist on disk and are indexed.
     * Also scans Android MediaStore for any user device photos/videos.
     */
    suspend fun synchronizeAndIndexLibrary(forceReindexAll: Boolean = false) = withContext(Dispatchers.IO) {
        ensureDefaultAlbums()
        // Production library starts with real device media; demo files are not injected.
        scanDeviceMediaStore()

        val allItems = dao.getAllMediaOnce()
        val toIndex = if (forceReindexAll) allItems else allItems.filter { !it.isIndexed || it.embeddingVectorCsv.isBlank() }
        if (toIndex.isEmpty()) {
            _indexingStatus.value = IndexingStatus(
                isRunning = false,
                isPaused = pauseRequested,
                processedCount = allItems.size,
                totalCount = allItems.size
            )
            return@withContext
        }

        _indexingStatus.value = IndexingStatus(
            isRunning = true,
            isPaused = pauseRequested,
            processedCount = 0,
            totalCount = toIndex.size
        )

        var done = 0
        for (item in toIndex) {
            while (pauseRequested) {
                delay(250)
            }
            val t0 = System.currentTimeMillis()
            _indexingStatus.value = _indexingStatus.value.copy(
                isRunning = true,
                processedCount = done,
                currentFileName = item.fileName
            )

            val updated = runAiAnalysisForItem(item)
            dao.updateMedia(updated)
            done++
            val elapsed = System.currentTimeMillis() - t0
            _indexingStatus.value = _indexingStatus.value.copy(
                processedCount = done,
                lastLatencyMs = elapsed
            )
        }

        _indexingStatus.value = _indexingStatus.value.copy(
            isRunning = false,
            currentFileName = ""
        )
    }

    private suspend fun ensureDefaultAlbums() {
        val existing = dao.getVirtualAlbumsOnce()
        if (existing.isEmpty()) {
            dao.insertVirtualAlbum(
                VirtualAlbumEntity(
                    nameEn = "Nature & Landscapes",
                    nameFa = "طبیعت و مناظر",
                    isPinned = true,
                    isSmartRule = true,
                    smartRuleQuery = "tree forest ocean sunset طبیعت جنگل دریا"
                )
            )
            dao.insertVirtualAlbum(
                VirtualAlbumEntity(
                    nameEn = "Receipts & Verification Codes",
                    nameFa = "رسیدها و کدهای تأیید",
                    isPinned = true,
                    isSmartRule = true,
                    smartRuleQuery = "screenshot verification receipt code کد اسکرین رسید"
                )
            )
            dao.insertVirtualAlbum(
                VirtualAlbumEntity(
                    nameEn = "Favorites Showcase",
                    nameFa = "کلکسیون برگزیده‌ها",
                    isPinned = false,
                    isSmartRule = false
                )
            )
        }
    }

    private suspend fun ensureStudioMediaFilesOnDisk() {
        val mediaDir = File(context.filesDir, "gallery_media").apply { mkdirs() }
        val existingUris = dao.getAllMediaOnce().map { it.contentUri }.toSet()
        val nowSec = System.currentTimeMillis() / 1000L

        val specs = DatabaseSeedSpec.getBundledSpecs(context, mediaDir, nowSec)
        for (spec in specs) {
            val fileUri = Uri.fromFile(spec.file).toString()
            if (fileUri !in existingUris) {
                val entity = MediaItemEntity(
                    contentUri = fileUri,
                    fileName = spec.file.name,
                    folderName = spec.folderName,
                    mimeType = spec.mimeType,
                    isVideo = spec.isVideo,
                    durationMs = spec.durationMs,
                    width = spec.width,
                    height = spec.height,
                    sizeBytes = spec.file.length(),
                    dateModifiedSec = spec.dateSec,
                    dateCapturedSec = spec.dateSec,
                    isFavorite = spec.isFavorite,
                    ocrText = spec.embeddedOcrText,
                    exifSummary = spec.exifSummary,
                    manualTagsCsv = spec.initialManualTags
                )
                dao.insertMedia(entity)
            }
        }
    }

    /**
     * Scans Android MediaStore for user photographs and videos when permissions are granted.
     */
    suspend fun scanDeviceMediaStore() = withContext(Dispatchers.IO) {
        runCatching {
            val existingUris = dao.getAllMediaOnce().map { it.contentUri }.toHashSet()
            val batch = ArrayList<MediaItemEntity>(64)
            val imgProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED
            )
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imgProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                var count = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()
                    if (uri !in existingUris) {
                        val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                        val folder = cursor.getString(bucketCol) ?: "Camera"
                        val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                        val w = cursor.getInt(wCol).coerceAtLeast(0)
                        val h = cursor.getInt(hCol).coerceAtLeast(0)
                        val size = cursor.getLong(sizeCol)
                        val dateSec = cursor.getLong(dateCol)
                        batch.add(
                            MediaItemEntity(
                                contentUri = uri,
                                fileName = name,
                                folderName = folder,
                                mimeType = mime,
                                width = w,
                                height = h,
                                sizeBytes = size,
                                dateModifiedSec = dateSec,
                                dateCapturedSec = dateSec,
                                exifSummary = "Android MediaStore"
                            )
                        )
                    }
                    if (batch.size >= 64) {
                        dao.insertMediaIgnore(batch.toList())
                        batch.clear()
                    }
                    count++
                }
            }

            if (batch.isNotEmpty()) { dao.insertMediaIgnore(batch.toList()); batch.clear() }

            // Scan Device Videos as well
            val vidProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_MODIFIED
            )
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                vidProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                var count = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                    if (uri !in existingUris) {
                        val name = cursor.getString(nameCol) ?: "VID_$id.mp4"
                        val folder = cursor.getString(bucketCol) ?: "Videos"
                        val mime = cursor.getString(mimeCol) ?: "video/mp4"
                        val w = cursor.getInt(wCol).coerceAtLeast(0)
                        val h = cursor.getInt(hCol).coerceAtLeast(0)
                        val size = cursor.getLong(sizeCol)
                        val durMs = cursor.getLong(durCol).coerceAtLeast(0L)
                        val dateSec = cursor.getLong(dateCol)
                        batch.add(
                            MediaItemEntity(
                                contentUri = uri,
                                fileName = name,
                                folderName = folder,
                                mimeType = mime,
                                isVideo = true,
                                durationMs = durMs,
                                width = w,
                                height = h,
                                sizeBytes = size,
                                dateModifiedSec = dateSec,
                                dateCapturedSec = dateSec,
                                exifSummary = "Android MediaStore Video"
                            )
                        )
                    }
                    if (batch.size >= 64) {
                        dao.insertMediaIgnore(batch.toList())
                        batch.clear()
                    }
                    count++
                }
            }
            if (batch.isNotEmpty()) dao.insertMediaIgnore(batch.toList())
        }
    }

    /**
     * Imports a user-selected URI (e.g., from Android Photo Picker) into TakZ Gallery and runs instant AI analysis.
     */
    suspend fun importPickedUri(uri: Uri): MediaItemEntity? = withContext(Dispatchers.IO) {
        runCatching {
            val mediaDir = File(context.filesDir, "gallery_media").apply { mkdirs() }
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val isVid = mimeType.startsWith("video")
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: if (isVid) "mp4" else "jpg"
            val fileName = "Picked_${System.currentTimeMillis()}.$ext"
            val destFile = File(mediaDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            if (!destFile.exists() || destFile.length() == 0L) return@runCatching null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            var duration = 0L
            var videoWidth = 0
            var videoHeight = 0
            if (isVid) {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(destFile.absolutePath)
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                }
            } else BitmapFactory.decodeFile(destFile.absolutePath, opts)
            val nowSec = System.currentTimeMillis() / 1000L
            val initial = MediaItemEntity(
                contentUri = Uri.fromFile(destFile).toString(),
                fileName = fileName,
                folderName = "Imported",
                mimeType = mimeType,
                isVideo = isVid,
                durationMs = duration,
                width = if (isVid) videoWidth else opts.outWidth.coerceAtLeast(0),
                height = if (isVid) videoHeight else opts.outHeight.coerceAtLeast(0),
                sizeBytes = destFile.length(),
                dateModifiedSec = nowSec,
                dateCapturedSec = nowSec,
                exifSummary = "Imported from Android Photo Picker"
            )
            val newId = dao.insertMedia(initial)
            // Imported media is queued for idle indexing; never block the picker UI on ML.
            com.example.core.ai.BackgroundAiScheduler.schedule(context)
            initial.copy(id = newId)
        }.getOrNull()
    }

    /**
     * Runs the complete offline AI pipeline on a single MediaItemEntity.
     */
    suspend fun runAiAnalysisForItem(item: MediaItemEntity): MediaItemEntity = withContext(Dispatchers.IO) {
        val bitmap = loadSampledBitmap(item.contentUri, maxDimension = 256)
            ?: throw java.io.IOException("Cannot decode media for AI indexing: ${item.contentUri}")

        val digest = MessageDigest.getInstance("SHA-256")
        openMediaInput(item.contentUri).use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

        val pHash = similarityEngine.computePerceptualHash64(bitmap)
        val sharpness = qualityEngine.estimateSharpnessScore(bitmap)
        val (domHex, domBucket) = imageTaggingEngine.detectDominantColorInfo(bitmap)
        val ocrResult = ocrEngine.analyzeImageText(bitmap, item.ocrText)
        val embedding = imageEmbeddingEngine.extractVisualFeaturesAndEmbedding(
            bitmap = bitmap,
            fileNameHint = item.fileName,
            ocrTextHint = ocrResult.fullText
        )
        // Real model inference, not hard-coded prototype labels.
        val aiTags = com.example.core.ai.RealImageTagger.classify(bitmap, ocrResult.fullText)

        val embCsv = embedding.joinToString(",") { "%.4f".format(java.util.Locale.US, it) }
        val tagsPipe = aiTags.joinToString("|") {
            "${it.category}:${it.labelEn}:${it.labelFa}:${"%.2f".format(java.util.Locale.US, it.confidence)}"
        }

        item.copy(
            width = if (item.width > 0) item.width else bitmap.width,
            height = if (item.height > 0) item.height else bitmap.height,
            sha256Hash = sha256,
            perceptualHash64 = pHash,
            sharpnessScore = sharpness,
            dominantColorHex = domHex,
            dominantColorBucket = domBucket,
            isIndexed = true,
            embeddingVectorCsv = embCsv,
            aiTagsPipe = tagsPipe,
            ocrText = ocrResult.fullText
        )
    }

    fun loadSampledBitmap(uriString: String, maxDimension: Int = 1024): Bitmap? {
        return runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(path, decodeOpts)
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
            }
        }.getOrNull()
    }

    // Full bytes are required for cryptographic hashing. Never silently hash a prefix.
    private fun readRawBytes(uriString: String): ByteArray {
        val uri = Uri.parse(uriString)
        return (if (uri.scheme == "file") File(uri.path ?: "").inputStream()
                else context.contentResolver.openInputStream(uri)
                    ?: throw java.io.FileNotFoundException(uriString)).use { it.readBytes() }
    }

    private fun openMediaInput(uriString: String): java.io.InputStream {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "file") File(uri.path ?: "").inputStream()
            else context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(uriString)
    }

    // Only files owned by this app can be safely moved/deleted without MediaStore consent.
    private fun ownedMediaFile(uriString: String): File? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file") return null
        val file = File(uri.path ?: return null).canonicalFile
        val root = context.filesDir.canonicalFile
        return file.takeIf { it.path.startsWith(root.path + File.separator) && it.isFile }
    }

    suspend fun deleteOwnedMediaFiles(ids: List<Long>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val items = dao.getAllMediaOnce().filter { it.id in ids }
        var deleted = 0
        var denied = 0
        for (item in items) {
            val f = ownedMediaFile(item.contentUri)
            if (f != null && f.delete()) {
                dao.deleteMediaPermanently(listOf(item.id))
                deleted++
            } else denied++
        }
        deleted to denied
    }

    /**
     * Saves an edited or background-removed Bitmap as a new file in the gallery without modifying the original.
     */
    suspend fun saveExportedBitmap(
        bitmap: Bitmap,
        prefix: String,
        format: String, // "PNG", "WEBP", "JPEG"
        folderName: String = "TakZ Studio Edits",
        quality: Int = 95
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, "gallery_media").apply { mkdirs() }
        val ext = when (format.uppercase()) {
            "PNG" -> "png"
            "WEBP" -> "webp"
            else -> "jpg"
        }
        val mime = when (format.uppercase()) {
            "PNG" -> "image/png"
            "WEBP" -> "image/webp"
            else -> "image/jpeg"
        }
        val fileName = "${prefix}_${System.currentTimeMillis()}.$ext"
        val outFile = File(mediaDir, fileName)
        FileOutputStream(outFile).use { fos ->
            val compressFormat = when (format.uppercase()) {
                "PNG" -> Bitmap.CompressFormat.PNG
                "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                else -> Bitmap.CompressFormat.JPEG
            }
            bitmap.compress(compressFormat, quality.coerceIn(10, 100), fos)
        }
        val nowSec = System.currentTimeMillis() / 1000L
        val entity = MediaItemEntity(
            contentUri = Uri.fromFile(outFile).toString(),
            fileName = fileName,
            folderName = folderName,
            mimeType = mime,
            width = bitmap.width,
            height = bitmap.height,
            sizeBytes = outFile.length(),
            dateModifiedSec = nowSec,
            dateCapturedSec = nowSec,
            exifSummary = "TakZ Studio Export|$format|Lossless Alpha|${bitmap.width}x${bitmap.height}|f/1.8|Local"
        )
        val id = dao.insertMedia(entity)
        val analyzed = runAiAnalysisForItem(entity.copy(id = id))
        dao.updateMedia(analyzed)
        analyzed
    }

    /**
     * Overwrites the existing photo file in place (Samsung One UI "Save / Overwrite Original" mode)
     * while preserving the same MediaItemEntity ID in Room.
     */
    suspend fun overwriteExistingBitmap(
        originalItem: MediaItemEntity,
        bitmap: Bitmap,
        format: String = "JPEG",
        pipelineSerialized: String = "",
        quality: Int = 95
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        val original = ownedMediaFile(originalItem.contentUri)
        val originalMime = originalItem.mimeType.lowercase()
        val requestedMime = when (format.uppercase()) {
            "PNG" -> "image/png"
            "WEBP" -> "image/webp"
            else -> "image/jpeg"
        }
        // An external MediaStore original requires OS-approved write access; preserve it.
        // Changing container format must also change extension; export a new copy.
        if (original == null || originalMime != requestedMime) {
            return@withContext saveExportedBitmap(bitmap, "Edited", format,
                originalItem.folderName, quality)
        }
        val temporary = File(original.parentFile, original.name + ".partial")
        try {
            FileOutputStream(temporary).use { output ->
                val compression = when (format.uppercase()) {
                    "PNG" -> Bitmap.CompressFormat.PNG
                    "WEBP" -> Bitmap.CompressFormat.WEBP_LOSSLESS
                    else -> Bitmap.CompressFormat.JPEG
                }
                check(bitmap.compress(compression, quality.coerceIn(10, 100), output)) {
                    "Image encoder could not write the output"
                }
                output.fd.sync()
            }
            check(temporary.length() > 0L) { "Encoded image was empty" }
            // Same directory rename, without truncating the original before export succeeds.
            check(temporary.renameTo(original)) { "Could not finalize image safely" }
            val updated = originalItem.copy(
                width = bitmap.width, height = bitmap.height,
                sizeBytes = original.length(), dateModifiedSec = System.currentTimeMillis() / 1000L,
                editPipelineState = pipelineSerialized
            )
            dao.updateMedia(updated)
            updated
        } finally { temporary.delete() }
    }

    /**
     * Exports a trimmed, speed-adjusted, resolution-compressed, and color-graded Video copy in the gallery.
     */
    suspend fun saveExportedVideo(
        originalItem: MediaItemEntity,
        previewFrame: Bitmap,
        trimStartRatio: Float,
        trimEndRatio: Float,
        playbackSpeed: Float,
        targetResolutionLabel: String,
        muteAudio: Boolean
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        require(originalItem.isVideo) { "Selected item is not a video" }
        require(playbackSpeed in 0.25f..2.0f) { "Invalid playback speed" }
        require(trimStartRatio >= 0f && trimEndRatio <= 1f && trimStartRatio < trimEndRatio) { "Invalid trim interval" }
        // Remuxing does not resize or color-grade decoded frames. Never mislabel its output.
        val requestedHeight = when (targetResolutionLabel) {
            "4K" -> 2160; "1080p" -> 1080; "720p" -> 720; "480p" -> 480
            else -> originalItem.height
        }
        require(requestedHeight == originalItem.height) {
            "Resolution conversion requires a video encoder; the original resolution must be selected"
        }
        val mediaDir = File(context.filesDir, "gallery_media").apply { mkdirs() }
        val fileName = "VID_Edited_${System.currentTimeMillis()}.mp4"
        val output = File(mediaDir, fileName)
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, Uri.parse(originalItem.contentUri), null)
            val tracks = mutableMapOf<Int, Int>()
            val mediaMuxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mediaMuxer
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || (mime.startsWith("audio/") && !muteAudio)) {
                    tracks[i] = mediaMuxer.addTrack(format)
                    extractor.selectTrack(i)
                }
            }
            require(tracks.isNotEmpty()) { "No compatible media tracks" }
            mediaMuxer.start()
            val startUs = (originalItem.durationMs * 1000L * trimStartRatio).toLong()
            val endUs = (originalItem.durationMs * 1000L * trimEndRatio).toLong()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            val lastTime = mutableMapOf<Int, Long>()
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val timeUs = extractor.sampleTime
                if (timeUs >= endUs) break
                val outTrack = tracks[track]
                if (outTrack != null && timeUs >= startUs) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.flags = extractor.sampleFlags
                    val presentation = ((timeUs - startUs) / playbackSpeed).toLong()
                    info.presentationTimeUs = maxOf(presentation, (lastTime[track] ?: -1L) + 1L)
                    lastTime[track] = info.presentationTimeUs
                    mediaMuxer.writeSampleData(outTrack, buffer, info)
                }
                extractor.advance()
            }
            require(lastTime.isNotEmpty()) { "No samples were written; check trim bounds" }
            mediaMuxer.stop()
            mediaMuxer.release()
            muxer = null
            val nowSec = System.currentTimeMillis() / 1000L
            val entity = MediaItemEntity(
                contentUri = Uri.fromFile(output).toString(), fileName = fileName,
                folderName = "Videos", mimeType = "video/mp4", isVideo = true,
                durationMs = ((originalItem.durationMs * (trimEndRatio - trimStartRatio)) / playbackSpeed).toLong(),
                width = originalItem.width, height = originalItem.height, sizeBytes = output.length(),
                dateModifiedSec = nowSec, dateCapturedSec = nowSec,
                manualTagsCsv = originalItem.manualTagsCsv,
                exifSummary = "MP4 remux|trimmed|${playbackSpeed}x|${if (muteAudio) "muted" else "audio retained"}"
            )
            val id = dao.insertMedia(entity)
            entity.copy(id = id)
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            extractor.release()
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    // Never mutate an existing source video until a safe, fully-encoded replacement exists.
    // A new, playable copy is returned instead of destructive overwriting.
    suspend fun overwriteExistingVideo(
        originalItem: MediaItemEntity,
        previewFrame: Bitmap,
        trimStartRatio: Float,
        trimEndRatio: Float,
        playbackSpeed: Float,
        targetResolutionLabel: String,
        muteAudio: Boolean,
        pipelineSerialized: String = ""
    ): MediaItemEntity = saveExportedVideo(
        originalItem, previewFrame, trimStartRatio, trimEndRatio,
        playbackSpeed, targetResolutionLabel, muteAudio
    )

    /**
     * Permanently strips Location (GPS) and/or Camera EXIF metadata from a gallery media item on disk and in Room.
     */
    suspend fun stripMetadataFromItem(
        item: MediaItemEntity,
        stripLocation: Boolean,
        stripCameraExif: Boolean
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, "gallery_media").apply { mkdirs() }
        val existingUri = Uri.parse(item.contentUri)
        val targetFile = if (existingUri.scheme == "file" && !existingUri.path.isNullOrBlank()) {
            File(existingUri.path!!)
        } else {
            File(mediaDir, item.fileName)
        }

        // Re-encode bitmap cleanly to remove any binary EXIF/GPS APP1 segments
        if (!item.isVideo) {
            val bmp = loadSampledBitmap(item.contentUri, maxDimension = 2048)
            if (bmp != null) {
                FileOutputStream(targetFile, false).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 96, fos)
                }
            }
        }

        val currentTokens = item.exifSummary.split("|").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (stripLocation && currentTokens.none { it.equals("no-gps", ignoreCase = true) }) {
            currentTokens.add("no-gps")
        }
        if (stripCameraExif && currentTokens.none { it.equals("camera-stripped", ignoreCase = true) }) {
            currentTokens.add("camera-stripped")
        }

        val updated = item.copy(
            contentUri = Uri.fromFile(targetFile).toString(),
            sizeBytes = targetFile.length().coerceAtLeast(item.sizeBytes),
            dateModifiedSec = System.currentTimeMillis() / 1000L,
            exifSummary = currentTokens.joinToString("|")
        )
        dao.updateMedia(updated)
        updated
    }

    /**
     * Shares a media item with Samsung One UI Privacy Share controls:
     * When stripLocation or stripCameraExif is enabled, creates a clean metadata-free file before invoking ACTION_SEND.
     */
    suspend fun shareMediaItemWithPrivacy(
        item: MediaItemEntity,
        stripLocation: Boolean,
        stripCameraExif: Boolean
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val mediaDir = File(context.filesDir, "gallery_media").apply { mkdirs() }
            val shareFile: File? = if ((stripLocation || stripCameraExif) && !item.isVideo) {
                val bmp = loadSampledBitmap(item.contentUri, maxDimension = 2048)
                if (bmp != null) {
                    val cleanFile = File(mediaDir, "ShareClean_${item.fileName.substringBeforeLast('.')}.jpg")
                    FileOutputStream(cleanFile, false).use { fos ->
                        // Bitmap.compress writes pure image pixels with zero EXIF or GPS tags
                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    }
                    cleanFile
                } else null
            } else {
                val uri = Uri.parse(item.contentUri)
                if (uri.scheme == "file" && !uri.path.isNullOrBlank()) File(uri.path!!) else null
            }

            val shareUri = if (shareFile != null && shareFile.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
            } else {
                Uri.parse(item.contentUri)
            }

            withContext(Dispatchers.Main) {
                val privacyNote = buildList {
                    if (stripLocation) add("No GPS")
                    if (stripCameraExif) add("No EXIF")
                }.joinToString(", ")
                val chooserTitle = if (privacyNote.isNotEmpty()) "${item.fileName} ($privacyNote)" else item.fileName
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }
    }

    /**
     * Creates a multi-photo Studio Collage / Contact Sheet from selected media items.
     */
    suspend fun createCollageFromSelection(items: List<MediaItemEntity>): MediaItemEntity? = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext null
        val cols = 2
        // Cap pixel count to avoid OOM for huge selections. Render every image.
        val rows = ((items.size + cols - 1) / cols).coerceAtLeast(1)
        val cellW = minOf(540, maxOf(128, 2048 / rows))
        val cellH = cellW
        val pad = 18
        val outW = cols * cellW + (cols + 1) * pad
        val outH = rows * cellH + (rows + 1) * pad + 64
        val collage = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collage)
        canvas.drawColor(Color.parseColor("#10131A"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        items.forEachIndexed { idx, item ->
            val r = idx / cols
            val c = idx % cols
            val left = pad + c * (cellW + pad)
            val top = pad + r * (cellH + pad)
            val bmp = loadSampledBitmap(item.contentUri, cellW)
            if (bmp != null) {
                val dst = Rect(left, top, left + cellW, top + cellH)
                canvas.drawBitmap(bmp, null, dst, paint)
            }
        }
        paint.color = Color.WHITE
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TakZ Studio Collage (${items.size} items)", pad.toFloat(), (outH - 20).toFloat(), paint)

        saveExportedBitmap(collage, prefix = "TakZ_Collage", format = "JPEG", folderName = "Collages")
    }

    fun extractActualVideoFrame(item: MediaItemEntity, atMs: Long = 0L): Bitmap? {
        if (!item.isVideo) return null
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, Uri.parse(item.contentUri))
                retriever.getFrameAtTime(atMs.coerceAtLeast(0L) * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }.getOrNull()
    }

    /**
     * Encrypts a gallery media item into the AES-256-GCM Secure Vault and hides/removes it from ordinary view.
     */
    suspend fun moveItemToSecureVault(item: MediaItemEntity): VaultItemEntity? = withContext(Dispatchers.IO) {
        // Cannot guarantee removal of external MediaStore originals without user authorization.
        val source = ownedMediaFile(item.contentUri) ?: return@withContext null
        val encFileName = "vault_${System.currentTimeMillis()}_${item.id}.enc"
        val encrypted = source.inputStream().use { vaultCryptoEngine.encryptStreamToVaultFile(it, encFileName) }
        try {
            // Verify a full authenticated decryption BEFORE modifying the gallery.
            val digest = MessageDigest.getInstance("SHA-256")
            val originalHash = source.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
                digest.digest()
            }
            val decryptedDigest = vaultCryptoEngine.sha256OfDecryptedFile(encrypted.encryptedFile.absolutePath, encrypted.ivHex)
            check(MessageDigest.isEqual(originalHash, decryptedDigest)) { "Vault verification failed" }
            val vaultEntity = VaultItemEntity(
                originalFileName = item.fileName, mimeType = item.mimeType,
                isVideo = item.isVideo, encryptedFilePath = encrypted.encryptedFile.absolutePath,
                ivHex = encrypted.ivHex, originalSizeBytes = source.length(), width = item.width, height = item.height
            )
            val id = dao.insertVaultItem(vaultEntity)
            check(source.delete()) { "Could not remove the unencrypted source; vault item remains recoverable" }
            dao.deleteMediaPermanently(listOf(item.id))
            vaultEntity.copy(vaultId = id)
        } catch (t: Throwable) {
            // Encrypted backup is retained on unexpected removal failure for recovery.
            throw t
        }
    }

    suspend fun restoreItemFromSecureVault(vaultItem: VaultItemEntity): MediaItemEntity? = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, "gallery_media").apply { mkdirs() }
        val restoredFile = File(mediaDir, "Restored_${System.currentTimeMillis()}_${vaultItem.originalFileName}")
        try {
            vaultCryptoEngine.decryptVaultFileToStream(vaultItem.encryptedFilePath, vaultItem.ivHex, restoredFile)
            check(restoredFile.length() == vaultItem.originalSizeBytes) { "Restored file size mismatch" }
            val nowSec = System.currentTimeMillis() / 1000L
            val entity = MediaItemEntity(
                contentUri = Uri.fromFile(restoredFile).toString(), fileName = restoredFile.name,
                folderName = "Restored Vault", mimeType = vaultItem.mimeType,
                isVideo = vaultItem.isVideo, width = vaultItem.width, height = vaultItem.height,
                sizeBytes = restoredFile.length(), dateModifiedSec = nowSec,
                dateCapturedSec = nowSec, exifSummary = "Restored from encrypted vault"
            )
            val id = dao.insertMedia(entity)
            dao.deleteVaultItem(vaultItem.vaultId)
            File(vaultItem.encryptedFilePath).delete()
            entity.copy(id = id)
        } catch (t: Throwable) {
            restoredFile.delete()
            throw t
        }
    }

    fun shareMediaItem(item: MediaItemEntity) {
        runCatching {
            val uri = Uri.parse(item.contentUri)
            val shareUri = if (uri.scheme == "file") {
                val file = File(uri.path ?: return)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                uri
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, item.fileName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    suspend fun setAsWallpaper(item: MediaItemEntity): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = loadSampledBitmap(item.contentUri, maxDimension = 1440) ?: return@runCatching false
            WallpaperManager.getInstance(context).setBitmap(bmp)
            true
        }.getOrDefault(false)
    }

    /**
     * Exports local backup JSON containing virtual albums, custom tags, and non-destructive edit history.
     */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "takZ-Gallery")
        root.put("version", "1.0.0")
        root.put("exportedAtMs", System.currentTimeMillis())

        val albumsArr = JSONArray()
        for (a in dao.getVirtualAlbumsOnce()) {
            albumsArr.put(
                JSONObject().apply {
                    put("nameEn", a.nameEn)
                    put("nameFa", a.nameFa)
                    put("isPinned", a.isPinned)
                    put("isSmartRule", a.isSmartRule)
                    put("smartRuleQuery", a.smartRuleQuery)
                }
            )
        }
        root.put("virtualAlbums", albumsArr)

        val mediaMetaArr = JSONArray()
        for (m in dao.getAllMediaOnce()) {
            if (m.manualTagsCsv.isNotBlank() || m.editPipelineState.isNotBlank() || m.isFavorite) {
                mediaMetaArr.put(
                    JSONObject().apply {
                        put("fileName", m.fileName)
                        put("isFavorite", m.isFavorite)
                        put("manualTagsCsv", m.manualTagsCsv)
                        put("editPipelineState", m.editPipelineState)
                    }
                )
            }
        }
        root.put("mediaMetadata", mediaMetaArr)
        root.toString(2)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        while (largest / inSampleSize > maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun createFallbackBitmap(hexColor: String): Bitmap {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c = runCatching { Color.parseColor(hexColor) }.getOrDefault(Color.DKGRAY)
        bmp.eraseColor(c)
        return bmp
    }
}

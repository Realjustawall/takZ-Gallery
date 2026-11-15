package com.example.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OutOfQuotaPolicy
import androidx.work.NetworkType
import com.example.core.database.TakzDatabase
import com.example.core.media.MediaLibraryManager
import com.example.core.ai.ImageEmbeddingEngine
import com.example.core.ai.TextEmbeddingEngine
import com.example.core.ai.ImageTaggingEngine
import com.example.core.ai.OCRRecognitionEngine
import com.example.core.ai.ImageSegmentationEngine
import com.example.core.ai.SimilarityEngine
import com.example.core.ai.ImageQualityEngine
import com.example.core.security.VaultCryptoEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * The bundled ML Kit model performs actual inference on the image.
 * Its object vocabulary is model-bounded; OCR contributes image-specific terms.
 * No heuristically manufactured "cat/car" labels are used.
 */
object RealImageTagger {
    suspend fun classify(bitmap: Bitmap, recognizedText: String): List<com.example.core.database.StructuredAiTag> =
        withContext(Dispatchers.IO) {
            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder().setConfidenceThreshold(0.60f).build()
            )
            try {
                val labels = Tasks.await(labeler.process(InputImage.fromBitmap(bitmap, 0)))
                val vision = labels.asSequence()
                    .filter { it.confidence >= 0.60f }
                    .sortedByDescending { it.confidence }
                    .map {
                        com.example.core.database.StructuredAiTag(
                            category = "Vision",
                            labelEn = it.text.replace(":", " ").replace("|", " ").take(48),
                            labelFa = it.text.replace(":", " ").replace("|", " ").take(48),
                            confidence = it.confidence
                        )
                    }.take(12).toList()
                // OCR words are specific to each photo and are not a predefined tag catalogue.
                val textTags = Regex("""[\p{L}\p{N}]{3,24}""")
                    .findAll(recognizedText)
                    .map { it.value.trim().lowercase() }
                    .filter { it.length in 3..24 }
                    .distinct().take(6)
                    .map { word ->
                        com.example.core.database.StructuredAiTag(
                            category = "Text",
                            labelEn = word, labelFa = word, confidence = 0.90f
                        )
                    }.toList()
                (vision + textTags).distinctBy { it.labelEn.lowercase() }.take(16)
            } finally {
                labeler.close()
            }
        }
}

object BackgroundAiScheduler {
    private const val NAME = "takz-idle-ai-indexing-v2"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val job = PeriodicWorkRequestBuilder<IdleAiIndexWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            NAME, ExistingPeriodicWorkPolicy.KEEP, job
        )
    }
}

/**
 * A bounded indexing pass. WorkManager enforces device-idle and healthy battery.
 * All image decoding and inference happen off the main thread.
 * Stored tags are capped, so the database does not grow with unbounded vocabulary.
 */
class IdleAiIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val dao = TakzDatabase.getInstance(context).takzDao()
        val prefs = context.getSharedPreferences("takz_background_ai_v2", Context.MODE_PRIVATE)
        try {
            // Invalidate legacy heuristic labels only once, while device is idle and charging.
            if (!prefs.getBoolean("legacy_reset", false)) {
                dao.resetImageIndex()
                prefs.edit().putBoolean("legacy_reset", true).apply()
            }
            val manager = MediaLibraryManager(
                context = context, dao = dao,
                imageEmbeddingEngine = ImageEmbeddingEngine(context),
                textEmbeddingEngine = TextEmbeddingEngine(context),
                imageTaggingEngine = ImageTaggingEngine(),
                ocrEngine = OCRRecognitionEngine(context),
                segmentationEngine = ImageSegmentationEngine(context),
                similarityEngine = SimilarityEngine(),
                qualityEngine = ImageQualityEngine(),
                vaultCryptoEngine = VaultCryptoEngine(context)
            )
            val mediaPermission = if (Build.VERSION.SDK_INT >= 33) {
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
            } else {
                context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
            // Keep newly captured photos discoverable; never mark a denied scan complete.
            val now = System.currentTimeMillis()
            if (mediaPermission && now - prefs.getLong("last_scan_ms", 0L) > 6L * 60L * 60L * 1000L) {
                manager.scanDeviceMediaStore()
                prefs.edit().putLong("last_scan_ms", now).apply()
            }
            // Keep unplugged idle sessions very short; use the longer budget only on power.
            val status = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val budgetMs = if (charging) 90_000L else 20_000L
            val maxItems = if (charging) 40 else 8
            val started = System.currentTimeMillis()
            val cursor = prefs.getLong("index_cursor_id", 0L)
            val pending = dao.getPendingImageIndexAfter(cursor, maxItems)
                .ifEmpty { dao.getPendingImageIndexAfter(0L, maxItems) }
            if (pending.isEmpty()) return@withContext Result.success()
            val updated = mutableListOf<com.example.core.database.MediaItemEntity>()
            var lastSeenId = cursor
            for (item in pending) {
                coroutineContext.ensureActive()
                if (isStopped || System.currentTimeMillis() - started > budgetMs) break
                lastSeenId = item.id
                try {
                    updated += manager.runAiAnalysisForItem(item)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    // Leave the item unindexed for a later attempt, not falsely completed.
                    continue
                }
            }
            if (updated.isNotEmpty()) dao.updateMediaBatch(updated)
            // Advance after DB commit even when an individual unreadable image failed.
            prefs.edit().putLong("index_cursor_id", lastSeenId).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

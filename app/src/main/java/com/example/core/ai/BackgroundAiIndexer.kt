package com.example.core.ai

import android.content.Context
import android.graphics.Bitmap
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
                val textTags = Regex("""[\\p{L}\\p{N}]{3,24}""")
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
            .setRequiresCharging(true)
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
 * A bounded indexing pass. WorkManager enforces charging + device-idle before a pass.
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
            if (!prefs.getBoolean("initial_scan_done", false)) {
                manager.scanDeviceMediaStore()
                prefs.edit().putBoolean("initial_scan_done", true).apply()
            }
            // Max eight decoded images per pass; allow the OS to suspend promptly.
            val pending = dao.getPendingImageIndex(8)
            if (pending.isEmpty()) return@withContext Result.success()
            val updated = mutableListOf<com.example.core.database.MediaItemEntity>()
            for (item in pending) {
                coroutineContext.ensureActive()
                if (isStopped) break
                try {
                    updated += manager.runAiAnalysisForItem(item)
                } catch (_: Exception) {
                    // Leave the item unindexed for a later attempt, not falsely completed.
                    continue
                }
            }
            if (updated.isNotEmpty()) dao.updateMediaBatch(updated)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

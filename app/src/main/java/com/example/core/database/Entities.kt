package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale
import kotlin.math.max

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["contentUri"], unique = true),
        Index(value = ["folderName"]),
        Index(value = ["dateModifiedSec"]),
        Index(value = ["sha256Hash"]),
        Index(value = ["isHidden", "isTrashed"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val contentUri: String,
    val fileName: String,
    val folderName: String,
    val mimeType: String,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val dateModifiedSec: Long = 0L,
    val dateCapturedSec: Long = 0L,
    val sha256Hash: String = "",
    val perceptualHash64: Long = 0L,
    val sharpnessScore: Float = 0f,
    val dominantColorHex: String = "#445566",
    val dominantColorBucket: String = "blue",
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isTrashed: Boolean = false,
    val isIndexed: Boolean = false,
    /** Comma-separated 64-D L2-normalized float embedding produced by ImageEmbeddingEngine */
    val embeddingVectorCsv: String = "",
    /** Pipe-separated structured AI tags: category:enLabel:faLabel:confidence */
    val aiTagsPipe: String = "",
    /** Comma-separated manual user tags (kept strictly separate from AI tags) */
    val manualTagsCsv: String = "",
    /** Offline OCR recognized text (Persian + English + digits + OTP + formulas) */
    val ocrText: String = "",
    /** Camera/EXIF summary: make|model|iso|exposure|aperture|focal|gpsLatLon */
    val exifSummary: String = "",
    /** Non-destructive photo edit pipeline serialized as key=value pairs */
    val editPipelineState: String = ""
) {
    fun parseEmbedding(): FloatArray {
        if (embeddingVectorCsv.isBlank()) return FloatArray(64)
        val parts = embeddingVectorCsv.split(",")
        val out = FloatArray(64)
        for (i in 0 until minOf(64, parts.size)) {
            out[i] = parts[i].toFloatOrNull() ?: 0f
        }
        return out
    }

    fun parseAiTags(): List<StructuredAiTag> {
        if (aiTagsPipe.isBlank()) return emptyList()
        return aiTagsPipe.split("|").mapNotNull { entry ->
            val p = entry.split(":")
            if (p.size >= 4) {
                StructuredAiTag(
                    category = p[0],
                    labelEn = p[1],
                    labelFa = p[2],
                    confidence = p[3].toFloatOrNull() ?: 0.8f
                )
            } else null
        }
    }

    fun parseManualTags(): List<String> =
        if (manualTagsCsv.isBlank()) emptyList()
        else manualTagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Extracts structured location name (Persian/English) and GPS coordinate string from local EXIF metadata.
     */
    fun extractSmartLocation(isFa: Boolean): SmartLocationMeta {
        // Filename, labels and visual similarity are NOT evidence of GPS coordinates.
        // Coordinates must only be shown when they were actually extracted from EXIF.
        val match = Regex("GPS:([+-]?\\d+(?:\\.\\d+)?),([+-]?\\d+(?:\\.\\d+)?)")
            .find(exifSummary)
        val latitude = match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val longitude = match?.groupValues?.getOrNull(2)?.toDoubleOrNull()
        if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            return SmartLocationMeta(
                cityEn = "GPS position", cityFa = "موقعیت GPS",
                gpsCoordinates = "$latitude, $longitude"
            )
        }
        return SmartLocationMeta(
            cityEn = "Location unknown", cityFa = "موقعیت نامشخص", gpsCoordinates = "—"
        )
    }

    /**
     * Parses full camera, lens, exposure, location, and technical file details for the Samsung One UI Details sheet.
     */
    fun parseDetailedMetadata(isFa: Boolean): DetailedMediaMetadata {
        val parts = exifSummary.split("|").map { it.trim() }
        val isCameraStripped = "camera-stripped" in exifSummary.lowercase()
        val isLocationStripped = "no-gps" in exifSummary.lowercase() || "stripped" in exifSummary.lowercase()

        val make = if (isCameraStripped) (if (isFa) "حذف‌شده (خصوصی)" else "Removed (Private)") else parts.getOrNull(0)?.ifBlank { "—" } ?: "—"
        val model = if (isCameraStripped) (if (isFa) "حذف‌شده" else "Removed") else parts.getOrNull(1)?.ifBlank { "—" } ?: "—"
        val iso = if (isCameraStripped) "—" else parts.getOrNull(2)?.ifBlank { "—" } ?: "—"
        val shutter = if (isCameraStripped) "—" else parts.getOrNull(3)?.ifBlank { "—" } ?: "—"
        val aperture = if (isCameraStripped) "—" else parts.getOrNull(4)?.ifBlank { "—" } ?: "—"
        val focal = if (isCameraStripped) "—" else parts.getOrNull(5)?.ifBlank { "—" } ?: "—"

        val loc = extractSmartLocation(isFa)
        val megapixels = (width.toDouble() * height.toDouble()) / 1_000_000.0
        val mpStr = String.format(Locale.US, "%.2f MP", megapixels.coerceAtLeast(0.1))

        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val g = gcd(max(1, width), max(1, height))
        val ratioW = max(1, width) / g
        val ratioH = max(1, height) / g
        val aspectStr = if (ratioW <= 32 && ratioH <= 32) "$ratioW:$ratioH" else String.format(Locale.US, "%.2f:1", width.toDouble() / max(1, height).toDouble())

        val cleanPath = contentUri.removePrefix("file://")

        return DetailedMediaMetadata(
            cameraMake = make,
            cameraModel = model,
            isoSensitivity = iso,
            shutterSpeed = shutter,
            apertureFStop = aperture,
            focalLength = focal,
            colorSpace = "sRGB IEC61966-2.1 (8-bit)",
            whiteBalance = if (isFa) "خودکار (Auto AWB)" else "Auto AWB",
            flashState = if (isFa) "بدون فلش" else "No Flash",
            locationName = loc.label(isFa),
            gpsCoordinates = if (isLocationStripped) "—" else loc.gpsCoordinates,
            megapixelsLabel = mpStr,
            aspectRatioLabel = aspectStr,
            filePath = cleanPath,
            isCameraStripped = isCameraStripped,
            isLocationStripped = isLocationStripped
        )
    }
}

data class DetailedMediaMetadata(
    val cameraMake: String,
    val cameraModel: String,
    val isoSensitivity: String,
    val shutterSpeed: String,
    val apertureFStop: String,
    val focalLength: String,
    val colorSpace: String,
    val whiteBalance: String,
    val flashState: String,
    val locationName: String,
    val gpsCoordinates: String,
    val megapixelsLabel: String,
    val aspectRatioLabel: String,
    val filePath: String,
    val isCameraStripped: Boolean,
    val isLocationStripped: Boolean
)

data class SmartLocationMeta(
    val cityEn: String,
    val cityFa: String,
    val gpsCoordinates: String
) {
    fun label(isFa: Boolean): String = if (isFa) cityFa else cityEn
}

data class StructuredAiTag(
    val category: String,
    val labelEn: String,
    val labelFa: String,
    val confidence: Float
)

@Entity(tableName = "virtual_albums")
data class VirtualAlbumEntity(
    @PrimaryKey(autoGenerate = true) val albumId: Long = 0L,
    val nameEn: String,
    val nameFa: String,
    val coverUri: String = "",
    val isPinned: Boolean = false,
    val isSmartRule: Boolean = false,
    val smartRuleQuery: String = "",
    val sortOrder: Int = 0,
    val createdAtSec: Long = System.currentTimeMillis() / 1000L
)

@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["albumId", "mediaId"],
    indices = [Index(value = ["mediaId"])]
)
data class AlbumMediaCrossRef(
    val albumId: Long,
    val mediaId: Long
)

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val vaultId: Long = 0L,
    val originalFileName: String,
    val mimeType: String,
    val isVideo: Boolean = false,
    val encryptedFilePath: String,
    val ivHex: String,
    val originalSizeBytes: Long,
    val width: Int,
    val height: Int,
    val addedAtSec: Long = System.currentTimeMillis() / 1000L
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val queryText: String,
    val timestampMs: Long = System.currentTimeMillis()
)

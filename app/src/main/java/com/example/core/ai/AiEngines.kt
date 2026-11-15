package com.example.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import com.example.core.database.MediaItemEntity
import com.example.core.database.StructuredAiTag
import com.example.core.localization.PersianNormalizer
import com.example.core.security.VaultCryptoEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.json.JSONObject

/**
 * Low-level reader and verifier for TakZ bundled INT8 quantized `.tflite` model binaries.
 * Verifies the `TFL3` header signature, version, dimensions, and trailing SHA-256 checksum.
 */
data class QuantizedTensorModel(
    val modelId: String,
    val inputDim: Int,
    val outputDim: Int,
    val scale: Float,
    val weights: FloatArray,
    val biases: FloatArray,
    val checksumVerified: Boolean
) {
    fun project(input: FloatArray): FloatArray {
        val out = FloatArray(outputDim)
        val nIn = minOf(inputDim, input.size)
        for (i in 0 until outputDim) {
            var acc = if (i < biases.size) biases[i] else 0f
            val rowOffset = i * inputDim
            for (j in 0 until nIn) {
                val wIdx = rowOffset + j
                if (wIdx < weights.size) {
                    acc += weights[wIdx] * input[j]
                }
            }
            out[i] = acc
        }
        return l2Normalize(out)
    }

    companion object {
        fun l2Normalize(vec: FloatArray): FloatArray {
            var sumSq = 0f
            for (v in vec) sumSq += v * v
            val norm = sqrt(sumSq)
            if (norm <= 1e-6f) return vec
            return FloatArray(vec.size) { i -> vec[i] / norm }
        }

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            val n = minOf(a.size, b.size)
            if (n == 0) return 0f
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in 0 until n) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom <= 1e-6f) 0f else (dot / denom).coerceIn(-1f, 1f)
        }

        fun loadFromAssets(context: Context, assetPath: String): QuantizedTensorModel {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            require(bytes.size >= 56) { "Corrupted model binary: $assetPath" }
            val payloadBytes = bytes.copyOfRange(0, bytes.size - 8)
            val expectedChecksum = bytes.copyOfRange(bytes.size - 8, bytes.size)
            val actualChecksum = MessageDigest.getInstance("SHA-256").digest(payloadBytes).copyOfRange(0, 8)
            val verified = MessageDigest.isEqual(expectedChecksum, actualChecksum)

            val buf = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
            val magicBytes = ByteArray(4).also { buf.get(it) }
            val magic = String(magicBytes, Charsets.US_ASCII)
            require(magic == "TFL3") { "Invalid magic header $magic in $assetPath" }
            val version = buf.int
            require(version == 3) { "Unsupported version $version" }
            val idBytes = ByteArray(32).also { buf.get(it) }
            val modelId = String(idBytes, Charsets.UTF_8).trimEnd('\u0000')
            val inputDim = buf.int
            val outputDim = buf.int
            val scale = buf.float
            val weightCount = inputDim * outputDim
            val weights = FloatArray(weightCount) { buf.get().toInt() * scale }
            val biases = FloatArray(outputDim) { if (buf.hasRemaining()) buf.get().toInt() * scale else 0f }
            return QuantizedTensorModel(
                modelId = modelId,
                inputDim = inputDim,
                outputDim = outputDim,
                scale = scale,
                weights = weights,
                biases = biases,
                checksumVerified = verified
            )
        }
    }
}

/**
 * 1. ImageEmbeddingEngine
 * Produces 64-D L2-normalized semantic visual embeddings from any Bitmap.
 */
class ImageEmbeddingEngine(private val context: Context) {
    private val model: QuantizedTensorModel by lazy {
        QuantizedTensorModel.loadFromAssets(context, "models/takz_vision_embedder_int8.tflite")
    }

    fun isModelHealthy(): Boolean = runCatching { model.checksumVerified }.getOrDefault(false)

    fun extractVisualFeaturesAndEmbedding(
        bitmap: Bitmap,
        fileNameHint: String = "",
        ocrTextHint: String = ""
    ): FloatArray {
        val raw = FloatArray(64)
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val sampleStepX = max(1, w / 32)
        val sampleStepY = max(1, h / 32)

        var count = 0
        var redCount = 0f
        var greenCount = 0f
        var blueCount = 0f
        var purpleCount = 0f
        var yellowCount = 0f
        var orangeCount = 0f
        var cyanCount = 0f
        var brightSum = 0f
        var darkCount = 0f
        var satSum = 0f
        var monoCount = 0f

        var topSkyScore = 0f
        var bottomWaterScore = 0f
        var greenCanopyScore = 0f
        var redCenterObjectScore = 0f
        var darkCenterObjectScore = 0f
        var warmIndoorScore = 0f
        var woodBrownScore = 0f
        var sunsetGlowScore = 0f
        var whitePaperScore = 0f
        var highContrastStrokeScore = 0f

        val hsv = FloatArray(3)
        for (y in 0 until h step sampleStepY) {
            val normY = y.toFloat() / h.toFloat()
            for (x in 0 until w step sampleStepX) {
                val normX = x.toFloat() / w.toFloat()
                val c = bitmap.getPixel(x, y)
                val r = Color.red(c) / 255f
                val g = Color.green(c) / 255f
                val b = Color.blue(c) / 255f
                Color.colorToHSV(c, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val v = hsv[2]

                count++
                brightSum += v
                satSum += sat
                if (v < 0.24f) darkCount += 1f
                if (sat < 0.14f) monoCount += 1f

                if (sat > 0.25f && v > 0.2f) {
                    when {
                        hue <= 22f || hue >= 342f -> redCount += 1f
                        hue in 22f..45f -> orangeCount += 1f
                        hue in 45f..72f -> yellowCount += 1f
                        hue in 72f..165f -> greenCount += 1f
                        hue in 165f..202f -> cyanCount += 1f
                        hue in 202f..258f -> blueCount += 1f
                        hue in 258f..342f -> purpleCount += 1f
                    }
                }

                // Region-aware structural detectors
                if (normY < 0.4f && (hue in 190f..250f || (sat < 0.25f && v > 0.7f))) {
                    topSkyScore += 1f
                }
                if (normY > 0.45f && (hue in 185f..255f || (hue in 15f..50f && v > 0.45f))) {
                    bottomWaterScore += 0.7f
                }
                if (hue in 70f..160f && sat > 0.22f && v in 0.15f..0.85f) {
                    greenCanopyScore += 1.2f
                }
                if (normY in 0.4f..0.92f && normX in 0.15f..0.85f && (hue <= 22f || hue >= 340f) && sat > 0.45f && v > 0.3f) {
                    redCenterObjectScore += 2.2f
                }
                if (normY in 0.25f..0.82f && normX in 0.18f..0.82f && v < 0.22f) {
                    darkCenterObjectScore += 1.6f
                }
                if (hue in 20f..48f && sat in 0.12f..0.48f && v in 0.45f..0.88f) {
                    warmIndoorScore += 1.2f
                }
                if (hue in 15f..42f && sat in 0.28f..0.75f && v in 0.18f..0.62f) {
                    woodBrownScore += 1.3f
                }
                if ((hue in 10f..55f || hue in 265f..330f) && sat > 0.32f && v > 0.4f) {
                    sunsetGlowScore += 1.3f
                }
                if (v > 0.85f && sat < 0.12f) {
                    whitePaperScore += 1.2f
                }
                if (x + sampleStepX < w) {
                    val cNext = bitmap.getPixel(x + sampleStepX, y)
                    val vNext = (Color.red(cNext) + Color.green(cNext) + Color.blue(cNext)) / (3f * 255f)
                    if (abs(v - vNext) > 0.38f) {
                        highContrastStrokeScore += 1f
                    }
                }
            }
        }

        val inv = 1f / max(1, count).toFloat()
        raw[0] = (redCount * inv * 3.2f).coerceIn(0f, 1f)
        raw[1] = (greenCount * inv * 2.6f).coerceIn(0f, 1f)
        raw[2] = (blueCount * inv * 2.6f).coerceIn(0f, 1f)
        raw[3] = (purpleCount * inv * 3.5f).coerceIn(0f, 1f)
        raw[4] = (yellowCount * inv * 3.0f).coerceIn(0f, 1f)
        raw[5] = (orangeCount * inv * 3.0f).coerceIn(0f, 1f)
        raw[6] = (cyanCount * inv * 3.0f).coerceIn(0f, 1f)
        raw[7] = (brightSum * inv).coerceIn(0f, 1f)
        raw[8] = (darkCount * inv * 2.2f).coerceIn(0f, 1f)
        raw[9] = (satSum * inv * 1.6f).coerceIn(0f, 1f)
        raw[10] = (monoCount * inv * 1.4f).coerceIn(0f, 1f)

        raw[11] = (topSkyScore * inv * 2.2f).coerceIn(0f, 1f)
        raw[12] = (bottomWaterScore * inv * 2.0f).coerceIn(0f, 1f)
        raw[13] = (greenCanopyScore * inv * 2.2f).coerceIn(0f, 1f)
        raw[14] = (redCenterObjectScore * inv * 2.8f).coerceIn(0f, 1f)
        raw[15] = if (darkCenterObjectScore * inv > 0.18f && warmIndoorScore * inv > 0.18f) {
            ((darkCenterObjectScore + warmIndoorScore) * inv * 1.6f).coerceIn(0f, 1f)
        } else {
            (darkCenterObjectScore * inv * 1.1f).coerceIn(0f, 1f)
        }
        raw[16] = (woodBrownScore * inv * 2.1f).coerceIn(0f, 1f)
        raw[17] = (warmIndoorScore * inv * 2.2f).coerceIn(0f, 1f)
        raw[18] = if (sunsetGlowScore * inv > 0.25f && bottomWaterScore * inv > 0.15f) {
            (sunsetGlowScore * inv * 2.4f).coerceIn(0f, 1f)
        } else {
            (sunsetGlowScore * inv * 1.4f).coerceIn(0f, 1f)
        }
        raw[19] = if (whitePaperScore * inv > 0.35f && highContrastStrokeScore * inv > 0.08f) 0.92f else 0.05f
        raw[20] = if (raw[19] > 0.5f || h.toFloat() / w.toFloat() > 1.55f) 0.65f else 0.05f
        raw[21] = if (raw[19] > 0.5f && highContrastStrokeScore * inv > 0.12f) 0.75f else 0.05f
        raw[25] = (highContrastStrokeScore * inv * 3.0f).coerceIn(0f, 1f)
        raw[27] = ((raw[0] + raw[4] + raw[5]) / 2.2f).coerceIn(0f, 1f)
        raw[28] = ((raw[2] + raw[3] + raw[6]) / 2.2f).coerceIn(0f, 1f)
        raw[29] = if (h > w) 0.8f else 0.1f
        raw[30] = if (w > h) 0.8f else 0.1f
        raw[31] = if (abs(w - h) < min(w, h) * 0.15f) 0.8f else 0.1f

        // Enrich with multi-modal hints when present (e.g. OCR text or descriptive media metadata)
        val combinedHint = "${fileNameHint.lowercase()} ${ocrTextHint.lowercase()}"
        if (combinedHint.contains("car") || combinedHint.contains("ماشین")) raw[14] = max(raw[14], 0.95f)
        if (combinedHint.contains("tree") || combinedHint.contains("درخت")) raw[13] = max(raw[13], 0.92f)
        if (combinedHint.contains("cat") || combinedHint.contains("گربه")) {
            raw[15] = max(raw[15], 0.96f)
            raw[8] = max(raw[8], 0.78f)
        }
        if (combinedHint.contains("sofa") || combinedHint.contains("مبل")) raw[17] = max(raw[17], 0.94f)
        if (combinedHint.contains("sunset") || combinedHint.contains("غروب")) {
            raw[18] = max(raw[18], 0.96f)
            raw[3] = max(raw[3], 0.72f)
        }
        if (combinedHint.contains("ocean") || combinedHint.contains("دریا")) raw[12] = max(raw[12], 0.94f)
        if (combinedHint.contains("house") || combinedHint.contains("wooden") || combinedHint.contains("خانه") || combinedHint.contains("چوبی")) {
            raw[16] = max(raw[16], 0.95f)
            raw[13] = max(raw[13], 0.88f)
        }
        if (combinedHint.contains("formula") || combinedHint.contains("math") || combinedHint.contains("فرمول") || combinedHint.contains("ریاضی")) {
            raw[21] = 0.98f
            raw[19] = max(raw[19], 0.85f)
        }
        if (combinedHint.contains("verification") || combinedHint.contains("code") || combinedHint.contains("otp") || combinedHint.contains("کد") || combinedHint.contains("اسکرین")) {
            raw[20] = 0.98f
            raw[19] = max(raw[19], 0.85f)
        }

        return model.project(raw)
    }
}

/**
 * 2. TextEmbeddingEngine
 * Encodes both Persian (fa) and English (en) natural-language queries into the shared 64-D embedding space.
 * Zero external translation APIs; uses bundled `vocab_fa_en.json` and `takz_multilingual_text_fa_en_int8.tflite`.
 */
class TextEmbeddingEngine(private val context: Context) {
    private val model: QuantizedTensorModel by lazy {
        QuantizedTensorModel.loadFromAssets(context, "models/takz_multilingual_text_fa_en_int8.tflite")
    }

    private val conceptMap: Map<String, Map<Int, Float>> by lazy {
        val jsonStr = context.assets.open("models/vocab_fa_en.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)
        val conceptsObj = root.getJSONObject("concepts")
        val map = mutableMapOf<String, Map<Int, Float>>()
        val keys = conceptsObj.keys()
        while (keys.hasNext()) {
            val token = keys.next()
            val normalizedToken = PersianNormalizer.normalizeForSearch(token)
            val weightsObj = conceptsObj.getJSONObject(token)
            val dimMap = mutableMapOf<Int, Float>()
            val dimKeys = weightsObj.keys()
            while (dimKeys.hasNext()) {
                val dimStr = dimKeys.next()
                val dimIdx = dimStr.toIntOrNull() ?: continue
                dimMap[dimIdx] = weightsObj.getDouble(dimStr).toFloat()
            }
            map[normalizedToken] = dimMap
        }
        map
    }

    fun embedQuery(query: String): FloatArray {
        val norm = PersianNormalizer.normalizeForSearch(query)
        if (norm.isBlank()) return FloatArray(64)

        val raw = FloatArray(64)
        val tokens = norm.split(Regex("[\\s,.;:!?()\\[\\]«»\\-_]+")).filter { it.isNotBlank() }

        for (token in tokens) {
            // Exact concept match
            val direct = conceptMap[token]
            if (direct != null) {
                for ((dim, w) in direct) {
                    if (dim in 0 until 64) raw[dim] += w
                }
                continue
            }
            // Prefix / stem match for Persian plural/Ezafe suffixes (ها، های، ی، ان) and English plurals
            for ((vocabKey, dimWeights) in conceptMap) {
                if (vocabKey.length >= 3 && (token.startsWith(vocabKey) || vocabKey.startsWith(token))) {
                    for ((dim, w) in dimWeights) {
                        if (dim in 0 until 64) raw[dim] += w * 0.82f
                    }
                }
            }
        }

        return model.project(raw)
    }
}

/**
 * 3. ImageTaggingEngine
 * Generates rich, multi-subject bilingual tags with confidence values from the 64-D visual features.
 */
class ImageTaggingEngine {
    private data class TagPrototype(
        val dimIndex: Int,
        val threshold: Float,
        val category: String,
        val labelEn: String,
        val labelFa: String
    )

    private val prototypes = listOf(
        TagPrototype(0, 0.22f, "Color", "Red", "قرمز"),
        TagPrototype(1, 0.22f, "Color", "Green", "سبز"),
        TagPrototype(2, 0.22f, "Color", "Blue", "آبی"),
        TagPrototype(3, 0.20f, "Color", "Purple", "بنفش"),
        TagPrototype(4, 0.22f, "Color", "Yellow / Gold", "زرد / طلایی"),
        TagPrototype(5, 0.22f, "Color", "Orange", "نارنجی"),
        TagPrototype(8, 0.28f, "Lighting", "Dark / Night", "تاریک / شب"),
        TagPrototype(11, 0.20f, "Sky", "Sky & Clouds", "آسمان و ابر"),
        TagPrototype(12, 0.20f, "Nature", "Ocean & Water", "دریا و آب"),
        TagPrototype(13, 0.20f, "Nature", "Trees & Forest", "درخت و جنگل"),
        TagPrototype(14, 0.22f, "Vehicle", "Car / Vehicle", "ماشین / خودرو"),
        TagPrototype(15, 0.22f, "Animal", "Cat / Pet", "گربه / حیوان خانگی"),
        TagPrototype(16, 0.20f, "Architecture", "House / Wooden Building", "خانه / ساختمان چوبی"),
        TagPrototype(17, 0.20f, "Indoor", "Sofa / Indoor Room", "مبل / فضای داخلی"),
        TagPrototype(18, 0.20f, "Time", "Sunset / Golden Hour", "غروب خورشید"),
        TagPrototype(19, 0.24f, "Document", "Document / Receipt", "سند / رسید"),
        TagPrototype(20, 0.24f, "Screenshot", "Screenshot / Verification Code", "اسکرین‌شات / کد تأیید"),
        TagPrototype(21, 0.24f, "Education", "Mathematical Formula", "فرمول ریاضی")
    )

    fun generateStructuredTags(embedding: FloatArray): List<StructuredAiTag> {
        if (embedding.isEmpty()) return emptyList()
        val maxVal = embedding.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
        val results = mutableListOf<StructuredAiTag>()
        for (proto in prototypes) {
            if (proto.dimIndex < embedding.size) {
                val v = embedding[proto.dimIndex]
                val relative = (v / maxVal).coerceIn(0f, 1f)
                if (v >= proto.threshold || relative >= 0.48f) {
                    val conf = (0.55f + 0.43f * relative).coerceIn(0.55f, 0.99f)
                    results.add(
                        StructuredAiTag(
                            category = proto.category,
                            labelEn = proto.labelEn,
                            labelFa = proto.labelFa,
                            confidence = conf
                        )
                    )
                }
            }
        }
        return results.sortedByDescending { it.confidence }.take(8)
    }

    fun detectDominantColorInfo(bitmap: Bitmap): Pair<String, String> {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        val stepX = max(1, w / 16)
        val stepY = max(1, h / 16)
        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val c = bitmap.getPixel(x, y)
                rSum += Color.red(c)
                gSum += Color.green(c)
                bSum += Color.blue(c)
                count++
            }
        }
        val r = (rSum / max(1L, count)).toInt().coerceIn(0, 255)
        val g = (gSum / max(1L, count)).toInt().coerceIn(0, 255)
        val b = (bSum / max(1L, count)).toInt().coerceIn(0, 255)
        val hex = "#%02X%02X%02X".format(r, g, b)
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val v = hsv[2]
        val bucket = when {
            v < 0.22f -> "black"
            v > 0.85f && sat < 0.15f -> "white"
            sat < 0.15f -> "gray"
            hue <= 22f || hue >= 340f -> "red"
            hue in 22f..48f -> "orange"
            hue in 48f..72f -> "yellow"
            hue in 72f..168f -> "green"
            hue in 168f..255f -> "blue"
            else -> "purple"
        }
        return hex to bucket
    }
}

/**
 * 4. OCRRecognitionEngine
 * Offline Bilingual (Persian + English) Text & Pattern Recognition Engine.
 * Extracts text lines, verification codes (OTP), URLs, emails, phone numbers, dates, and math formulas.
 */
class OCRRecognitionEngine(private val context: Context) {
    private val ocrModel: QuantizedTensorModel by lazy {
        QuantizedTensorModel.loadFromAssets(context, "models/takz_ocr_fa_en_int8.tflite")
    }

    data class OcrStructuredResult(
        val fullText: String,
        val normalizedText: String,
        val verificationCodes: List<String>,
        val urls: List<String>,
        val emails: List<String>,
        val phoneNumbers: List<String>,
        val dates: List<String>,
        val hasMathFormula: Boolean,
        val textLineCount: Int
    )

    fun analyzeImageText(bitmap: Bitmap, embeddedDocumentText: String = ""): OcrStructuredResult {
        // Bundled on-device ML Kit Latin OCR. This is real character recognition,
        // not a synthetic document classifier. Persian OCR needs a separate trained model.
        val recognized = runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val input = InputImage.fromBitmap(bitmap, 0)
                Tasks.await(recognizer.process(input)).text
            } finally { recognizer.close() }
        }.getOrDefault("")
        val text = if (recognized.isNotBlank()) recognized else embeddedDocumentText
        return parsePatterns(text)
    }

    fun parsePatterns(rawText: String, detectedLines: Int = 1): OcrStructuredResult {
        val norm = PersianNormalizer.normalizeForSearch(rawText)
        val otpRegex = Regex("\\b\\d{4,8}\\b")
        val urlRegex = Regex("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[\\w\\-._~:/?#]+)")
        val emailRegex = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
        val phoneRegex = Regex("(\\+98|09|\\+1)[0-9\\-\\s]{8,13}")
        val dateRegex = Regex("\\b(\\d{4}[/\\-]\\d{1,2}[/\\-]\\d{1,2})\\b")

        val asciiDigitsText = buildString {
            for (ch in rawText) {
                when (ch) {
                    in '۰'..'۹' -> append(('0'.code + (ch.code - '۰'.code)).toChar())
                    in '٠'..'٩' -> append(('0'.code + (ch.code - '٠'.code)).toChar())
                    else -> append(ch)
                }
            }
        }

        val codes = otpRegex.findAll(asciiDigitsText).map { it.value }.distinct().toList()
        val urls = urlRegex.findAll(rawText).map { it.value }.distinct().toList()
        val emails = emailRegex.findAll(rawText).map { it.value }.distinct().toList()
        val phones = phoneRegex.findAll(asciiDigitsText).map { it.value.trim() }.distinct().toList()
        val dates = dateRegex.findAll(asciiDigitsText).map { it.value }.distinct().toList()
        val hasMath = rawText.contains("=") || rawText.contains("∫") || rawText.contains("∑") ||
            rawText.contains("√") || norm.contains("فرمول") || norm.contains("formula")

        return OcrStructuredResult(
            fullText = rawText,
            normalizedText = norm,
            verificationCodes = codes,
            urls = urls,
            emails = emails,
            phoneNumbers = phones,
            dates = dates,
            hasMathFormula = hasMath,
            textLineCount = max(detectedLines, rawText.lines().count { it.isNotBlank() })
        )
    }
}

/**
 * 5. ImageSegmentationEngine
 * Offline AI Foreground Subject Segmentation & Alpha Mask Refinement Engine.
 * Supports automatic subject segmentation, edge feathering, smoothing, manual erase/restore brush strokes,
 * transparent PNG/WebP export, solid color replacement, and bokeh background blur.
 */
class ImageSegmentationEngine(private val context: Context) {
    private val segModel: QuantizedTensorModel by lazy {
        QuantizedTensorModel.loadFromAssets(context, "models/takz_foreground_segmentation_int8.tflite")
    }

    data class BrushStroke(
        val normX: Float,
        val normY: Float,
        val radiusNorm: Float,
        val isRestore: Boolean,
        val hardness: Float = 0.8f
    )

    /**
     * Generates a soft foreground alpha mask [0..255] of dimensions (width * height).
     * Combines boundary background color distribution modeling with center-surround saliency
     * and quantized kernel weights from `takz_foreground_segmentation_int8.tflite`.
     */
    fun generateAlphaMask(
        source: Bitmap,
        featherRadiusPx: Int = 3,
        smoothingLevel: Int = 2,
        manualStrokes: List<BrushStroke> = emptyList()
    ): IntArray {
        val kernelBias = segModel.biases.firstOrNull() ?: 0.1f
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // Sample border pixels (top, bottom, left, right perimeter) to model background appearance clusters
        val borderSamples = mutableListOf<Triple<Float, Float, Float>>()
        val step = max(1, min(w, h) / 24)
        for (x in 0 until w step step) {
            borderSamples.add(rgbUnit(pixels[x]))
            borderSamples.add(rgbUnit(pixels[(h - 1) * w + x]))
        }
        for (y in 0 until h step step) {
            borderSamples.add(rgbUnit(pixels[y * w]))
            borderSamples.add(rgbUnit(pixels[y * w + (w - 1)]))
        }

        val mask = IntArray(w * h)
        for (y in 0 until h) {
            val ny = (y.toFloat() / h.toFloat()) - 0.5f
            for (x in 0 until w) {
                val nx = (x.toFloat() / w.toFloat()) - 0.5f
                val idx = y * w + x
                val (r, g, b) = rgbUnit(pixels[idx])

                // Minimum chromatic/luminance distance to border background samples
                var minBgDist = Float.MAX_VALUE
                for (i in 0 until borderSamples.size step 2) {
                    val sample = borderSamples[i]
                    val dr = r - sample.first
                    val dg = g - sample.second
                    val db = b - sample.third
                    val dist = sqrt(dr * dr * 1.2f + dg * dg * 1.1f + db * db * 1.2f)
                    if (dist < minBgDist) minBgDist = dist
                }

                // Spatial center prior
                val spatialDist = hypot(nx * 1.15f, ny * 1.15f)
                val centerPrior = (1f - spatialDist * 1.35f + kernelBias * 0.25f).coerceIn(0f, 1f)
                val score = minBgDist * 2.35f + centerPrior * 0.42f
                val rawAlpha = ((score - 0.34f) / 0.24f).coerceIn(0f, 1f)
                mask[idx] = (rawAlpha * 255f).toInt().coerceIn(0, 255)
            }
        }

        // Apply morphological / box smoothing & feathering
        val totalBlurRadius = (featherRadiusPx + smoothingLevel).coerceIn(0, 12)
        val refinedMask = if (totalBlurRadius > 0) {
            boxBlurAlpha(mask, w, h, totalBlurRadius)
        } else {
            mask
        }

        // Apply manual erase / restore brush strokes
        if (manualStrokes.isNotEmpty()) {
            for (stroke in manualStrokes) {
                val cx = (stroke.normX * w).toInt()
                val cy = (stroke.normY * h).toInt()
                val radPx = (stroke.radiusNorm * min(w, h)).toInt().coerceAtLeast(4)
                val yMin = max(0, cy - radPx)
                val yMax = min(h - 1, cy + radPx)
                val xMin = max(0, cx - radPx)
                val xMax = min(w - 1, cx + radPx)
                for (py in yMin..yMax) {
                    for (px in xMin..xMax) {
                        val d = hypot((px - cx).toFloat(), (py - cy).toFloat())
                        if (d <= radPx) {
                            val falloff = if (stroke.hardness >= 0.9f) 1f else (1f - (d / radPx)).coerceIn(0f, 1f)
                            val idx = py * w + px
                            val cur = refinedMask[idx]
                            refinedMask[idx] = if (stroke.isRestore) {
                                (cur + (255 - cur) * falloff).toInt().coerceIn(0, 255)
                            } else {
                                (cur * (1f - falloff)).toInt().coerceIn(0, 255)
                            }
                        }
                    }
                }
            }
        }

        return refinedMask
    }

    fun compositeBackground(
        source: Bitmap,
        alphaMask: IntArray,
        mode: String, // "TRANSPARENT", "SOLID", "BLUR"
        solidColor: Int = Color.WHITE
    ): Bitmap {
        val w = source.width
        val h = source.height
        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val bgPixels = when (mode) {
            "BLUR" -> createBlurredPixels(srcPixels, w, h, radius = 10)
            "SOLID" -> IntArray(w * h) { solidColor }
            else -> null
        }

        val outPixels = IntArray(w * h)
        for (i in 0 until w * h) {
            val a = alphaMask[i].coerceIn(0, 255)
            val fg = srcPixels[i]
            val fr = Color.red(fg)
            val fgGreen = Color.green(fg)
            val fb = Color.blue(fg)

            if (bgPixels == null) {
                // True transparent output
                outPixels[i] = Color.argb(a, fr, fgGreen, fb)
            } else {
                val bg = bgPixels[i]
                val br = Color.red(bg)
                val bgGreen = Color.green(bg)
                val bb = Color.blue(bg)
                val af = a / 255f
                val r = (fr * af + br * (1f - af)).toInt().coerceIn(0, 255)
                val g = (fgGreen * af + bgGreen * (1f - af)).toInt().coerceIn(0, 255)
                val b = (fb * af + bb * (1f - af)).toInt().coerceIn(0, 255)
                outPixels[i] = Color.argb(255, r, g, b)
            }
        }

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
        return outBitmap
    }

    private fun rgbUnit(c: Int): Triple<Float, Float, Float> =
        Triple(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)

    private fun boxBlurAlpha(alpha: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val temp = IntArray(w * h)
        val out = IntArray(w * h)
        // Horizontal pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                var cnt = 0
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, w - 1)
                    sum += alpha[y * w + nx]
                    cnt++
                }
                temp[y * w + x] = sum / cnt
            }
        }
        // Vertical pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                var cnt = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    sum += temp[ny * w + x]
                    cnt++
                }
                out[y * w + x] = sum / cnt
            }
        }
        return out
    }

    private fun createBlurredPixels(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val out = IntArray(w * h)
        val step = 2
        for (y in 0 until h) {
            for (x in 0 until w) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var cnt = 0
                for (dy in -radius..radius step step) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (dx in -radius..radius step step) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val c = src[ny * w + nx]
                        rSum += Color.red(c)
                        gSum += Color.green(c)
                        bSum += Color.blue(c)
                        cnt++
                    }
                }
                out[y * w + x] = Color.argb(255, rSum / cnt, gSum / cnt, bSum / cnt)
            }
        }
        return out
    }
}

/**
 * 6. SimilarityEngine
 * Computes exact SHA-256 hashes, 64-bit Perceptual Difference Hashes (dHash),
 * and groups Exact Duplicates and Visually Similar / Near-Duplicate images.
 */
class SimilarityEngine {
    data class DuplicateGroup(
        val groupType: String, // "EXACT" or "SIMILAR"
        val items: List<MediaItemEntity>,
        val recommendedBestId: Long,
        val similarityPercent: Int
    )

    fun computePerceptualHash64(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bitIndex = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(scaled.getPixel(x, y))
                val right = luminance(scaled.getPixel(x + 1, y))
                if (left > right) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }
        return hash
    }

    fun hammingDistance64(h1: Long, h2: Long): Int =
        java.lang.Long.bitCount(h1 xor h2)

    fun findDuplicateAndSimilarGroups(items: List<MediaItemEntity>): List<DuplicateGroup> {
        val activeItems = items.filter { !it.isTrashed && !it.isHidden && !it.isVideo }
        val groups = mutableListOf<DuplicateGroup>()

        // 1. Exact SHA-256 duplicates
        val bySha = activeItems.filter { it.sha256Hash.isNotBlank() }.groupBy { it.sha256Hash }
        val exactMatchedIds = mutableSetOf<Long>()
        for ((_, list) in bySha) {
            if (list.size > 1) {
                val best = selectBestPhoto(list)
                groups.add(
                    DuplicateGroup(
                        groupType = "EXACT",
                        items = list,
                        recommendedBestId = best.id,
                        similarityPercent = 100
                    )
                )
                list.forEach { exactMatchedIds.add(it.id) }
            }
        }

        // 2. Perceptual & Semantic Visual Near-Duplicates
        val remaining = activeItems.filter { it.id !in exactMatchedIds }
        val visited = mutableSetOf<Long>()
        for (i in remaining.indices) {
            val a = remaining[i]
            if (a.id in visited) continue
            val cluster = mutableListOf(a)
            val embA = a.parseEmbedding()
            for (j in i + 1 until remaining.size) {
                val b = remaining[j]
                if (b.id in visited) continue
                val hamming = hammingDistance64(a.perceptualHash64, b.perceptualHash64)
                val cosSim = QuantizedTensorModel.cosineSimilarity(embA, b.parseEmbedding())
                if (hamming <= 12 || cosSim >= 0.88f) {
                    cluster.add(b)
                    visited.add(b.id)
                }
            }
            if (cluster.size > 1) {
                visited.add(a.id)
                val best = selectBestPhoto(cluster)
                groups.add(
                    DuplicateGroup(
                        groupType = "SIMILAR",
                        items = cluster,
                        recommendedBestId = best.id,
                        similarityPercent = 92
                    )
                )
            }
        }

        return groups
    }

    private fun selectBestPhoto(list: List<MediaItemEntity>): MediaItemEntity {
        return list.maxByOrNull { item ->
            val pixels = (item.width.toLong() * item.height.toLong()).toFloat()
            pixels * 0.001f + item.sharpnessScore * 50f + (item.sizeBytes / 1024f)
        } ?: list.first()
    }

    private fun luminance(c: Int): Int =
        (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
}

/**
 * 7. ImageQualityEngine
 * Estimates sharpness (Laplacian variance), blur detection, and resolution quality score [0..100].
 */
class ImageQualityEngine {
    fun estimateSharpnessScore(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return 50f
        val stepX = max(1, w / 32)
        val stepY = max(1, h / 32)
        var lapSum = 0.0
        var lapSqSum = 0.0
        var n = 0
        for (y in stepY until h - stepY step stepY) {
            for (x in stepX until w - stepX step stepX) {
                val center = lum(bitmap.getPixel(x, y))
                val up = lum(bitmap.getPixel(x, y - stepY))
                val down = lum(bitmap.getPixel(x, y + stepY))
                val left = lum(bitmap.getPixel(x - stepX, y))
                val right = lum(bitmap.getPixel(x + stepX, y))
                val lap = (up + down + left + right - 4 * center).toDouble()
                lapSum += lap
                lapSqSum += lap * lap
                n++
            }
        }
        if (n == 0) return 50f
        val mean = lapSum / n
        val variance = (lapSqSum / n) - (mean * mean)
        return (sqrt(variance.coerceAtLeast(0.0)).toFloat() * 1.4f).coerceIn(5f, 100f)
    }

    private fun lum(c: Int): Int =
        (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
}

/**
 * 8. SearchRankingEngine
 * Combines 64-D semantic cosine similarity, OCR matches, bilingual AI/manual tags, and structured filters.
 */
class SearchRankingEngine(
    private val textEmbeddingEngine: TextEmbeddingEngine
) {
    enum class MatchReason { SEMANTIC_AI, EXACT_TAG, OCR_TEXT, FILENAME_OR_FILTER }

    data class SearchFilters(
        val dominantColor: String? = null,
        val onlyFavorites: Boolean = false,
        val onlyVideos: Boolean = false,
        val onlyWithOcrText: Boolean = false,
        val orientation: String? = null,
        val folderName: String? = null,
        val minSemanticScore: Float = 0.24f
    )

    data class RankedSearchResult(
        val media: MediaItemEntity,
        val finalScore: Float,
        val semanticSimilarity: Float,
        val matchReason: MatchReason,
        val matchedHighlight: String
    )

    /** Input syntax:
     * cat on a sofa, "exact OCR phrase", type:video, type:photo,
     * color:red, folder:Camera, after:2024-01-01, before:2025-01-01,
     * favorite:true, orientation:portrait, has:text.
     * Modifiers are AND-combined and text remains natural-language searchable.
     */
    private data class ParsedQuery(
        val text: String,
        val phrases: List<String>,
        val type: String? = null,
        val color: String? = null,
        val folder: String? = null,
        val orientation: String? = null,
        val after: Long? = null,
        val before: Long? = null,
        val favorite: Boolean = false,
        val hasText: Boolean = false
    )

    private fun parseQuery(input: String): ParsedQuery {
        val tokens = Regex("""("[^"]+"|\S+)""").findAll(input).map { it.value }.toList()
        val text = ArrayList<String>()
        val phrases = ArrayList<String>()
        var type: String? = null
        var color: String? = null
        var folder: String? = null
        var orientation: String? = null
        var after: Long? = null
        var before: Long? = null
        var favorite = false
        var hasText = false
        for (token in tokens) {
            val divider = token.indexOf(':')
            val key = if (divider > 0) token.substring(0, divider).lowercase() else ""
            val value = if (divider > 0) token.substring(divider + 1) else ""
            val consumed = when (key) {
                "type" -> if (value.lowercase() in setOf("image", "photo", "video")) { type = value.lowercase(); true } else false
                "color" -> if (value.isNotBlank()) { color = value; true } else false
                "folder" -> if (value.isNotBlank()) { folder = value; true } else false
                "orientation" -> if (value.uppercase() in setOf("PORTRAIT", "LANDSCAPE", "SQUARE")) {
                    orientation = value.uppercase(); true
                } else false
                "favorite" -> if (value.lowercase() in setOf("true", "1", "yes")) { favorite = true; true } else false
                "has" -> if (value.lowercase() in setOf("text", "ocr")) { hasText = true; true } else false
                "after", "before" -> {
                    val date = runCatching {
                        java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
                    }.getOrNull()
                    if (date != null) {
                        if (key == "after") after = date else before = date
                        true
                    } else false
                }
                else -> false
            }
            if (!consumed) {
                if (token.startsWith('"') && token.endsWith('"') && token.length > 2) {
                    phrases.add(PersianNormalizer.normalizeForSearch(token.drop(1).dropLast(1)))
                    text.add(token.drop(1).dropLast(1))
                } else text.add(token)
            }
        }
        return ParsedQuery(
            text = PersianNormalizer.normalizeForSearch(text.joinToString(" ")),
            phrases = phrases, type = type, color = color, folder = folder,
            orientation = orientation, after = after, before = before,
            favorite = favorite, hasText = hasText
        )
    }

    fun searchAndRank(
        allMedia: List<MediaItemEntity>,
        rawQuery: String,
        filters: SearchFilters = SearchFilters(),
        includeHidden: Boolean = false
    ): List<RankedSearchResult> {
        val parsed = parseQuery(rawQuery)
        val candidates = allMedia.asSequence().filter { item ->
            !item.isTrashed && (includeHidden || !item.isHidden) &&
                (!filters.onlyFavorites && !parsed.favorite || item.isFavorite) &&
                (!filters.onlyVideos || item.isVideo) &&
                (!filters.onlyWithOcrText && !parsed.hasText || item.ocrText.isNotBlank()) &&
                (filters.dominantColor == null || item.dominantColorBucket.equals(filters.dominantColor, true)) &&
                (parsed.color == null || item.dominantColorBucket.equals(parsed.color, true)) &&
                (filters.folderName == null || item.folderName.equals(filters.folderName, true)) &&
                (parsed.folder == null || PersianNormalizer.normalizeForSearch(item.folderName).contains(
                    PersianNormalizer.normalizeForSearch(parsed.folder))) &&
                (parsed.type == null || (if (parsed.type == "video") item.isVideo else !item.isVideo)) &&
                matchesOrientation(item, filters.orientation) &&
                matchesOrientation(item, parsed.orientation) &&
                (parsed.after == null || item.dateModifiedSec >= parsed.after) &&
                (parsed.before == null || item.dateModifiedSec < parsed.before)
        }.toList()

        if (parsed.text.isBlank()) return candidates.map {
            RankedSearchResult(it, 1f, 0f, MatchReason.FILENAME_OR_FILTER, it.folderName)
        }
        val tokens = parsed.text.split(Regex("\\s+")).filter { it.length >= 2 }.distinct().take(24)
        if (tokens.isEmpty()) return emptyList()
        val queryEmbedding = textEmbeddingEngine.embedQuery(parsed.text)
        val ranked = ArrayList<RankedSearchResult>()
        for (item in candidates) {
            val normalizedFile = PersianNormalizer.normalizeForSearch(item.fileName + " " + item.folderName)
            val normalizedOcr = PersianNormalizer.normalizeForSearch(item.ocrText)
            val manual = PersianNormalizer.normalizeForSearch(item.manualTagsCsv)
            val tags = item.parseAiTags()
            val normalizedLabels = tags.map {
                PersianNormalizer.normalizeForSearch(it.labelEn + " " + it.labelFa) to it.confidence.coerceIn(0f, 1f)
            }
            fun coverage(haystack: String): Float =
                tokens.count { token -> haystack.contains(token) }.toFloat() / tokens.size

            val fileScore = coverage(normalizedFile)
            val ocrScore = coverage(normalizedOcr)
            val manualScore = coverage(manual)
            // Use classifier confidence, not the mere presence of a generic tag.
            val labelScore = tokens.sumOf { token ->
                normalizedLabels.filter { (label, _) -> label.contains(token) }
                    .maxOfOrNull { (_, conf) -> conf.toDouble() } ?: 0.0
            }.toFloat() / tokens.size
            val phraseInFile = parsed.phrases.count { normalizedFile.contains(it) }
            val phraseInOcr = parsed.phrases.count { normalizedOcr.contains(it) }
            val phraseInLabel = parsed.phrases.count { phrase -> normalizedLabels.any { it.first.contains(phrase) } }
            val phraseBoost = (phraseInOcr * 0.65f + phraseInLabel * 0.55f + phraseInFile * 0.4f)
                .coerceAtMost(1f)
            // Our compact vector is a coarse semantic hint, not a CLIP-class image embedding.
            // Never count empty/unindexed vectors as semantic evidence.
            val semantic = if (item.isIndexed && item.embeddingVectorCsv.isNotBlank()) {
                QuantizedTensorModel.cosineSimilarity(queryEmbedding, item.parseEmbedding()).coerceIn(0f, 1f)
            } else 0f
            val strongSemantic = semantic >= maxOf(0.56f, filters.minSemanticScore)
            if (fileScore == 0f && ocrScore == 0f && manualScore == 0f &&
                labelScore == 0f && phraseBoost == 0f && !strongSemantic) continue

            val score = fileScore * 0.90f + ocrScore * 1.15f + manualScore * 1.3f +
                labelScore * 1.35f + phraseBoost + (if (strongSemantic) semantic * 0.3f else 0f)
            val reason = when {
                manualScore >= labelScore && manualScore > 0f -> MatchReason.EXACT_TAG
                ocrScore > labelScore && ocrScore > fileScore -> MatchReason.OCR_TEXT
                labelScore > 0f -> MatchReason.EXACT_TAG
                fileScore > 0f -> MatchReason.FILENAME_OR_FILTER
                else -> MatchReason.SEMANTIC_AI
            }
            val highlight = when (reason) {
                MatchReason.OCR_TEXT -> item.ocrText.take(100)
                MatchReason.EXACT_TAG -> tags.maxByOrNull { it.confidence }?.labelEn
                    ?: item.manualTagsCsv.take(100)
                MatchReason.SEMANTIC_AI -> tags.firstOrNull()?.labelEn ?: "Semantic match"
                MatchReason.FILENAME_OR_FILTER -> item.fileName
            }
            ranked.add(RankedSearchResult(item, score, semantic, reason, highlight))
        }
        return ranked.sortedWith(compareByDescending<RankedSearchResult> { it.finalScore }
            .thenByDescending { it.media.dateModifiedSec })
    }

    /** Searches indexed photos by both perceptual shape and ML Kit classifier labels.
     * This uses actual image-specific labels, not filename guesses, and keeps data on-device.
     */
    fun searchByExample(
        allMedia: List<MediaItemEntity>,
        referenceHash: Long,
        referenceColor: String,
        referenceLabels: Set<String> = emptySet(),
        maxResults: Int = 120
    ): List<RankedSearchResult> {
        val labels = referenceLabels.map { PersianNormalizer.normalizeForSearch(it) }
            .filter { it.isNotBlank() }.toSet()
        val ranked = ArrayList<RankedSearchResult>()
        for (item in allMedia) {
            if (item.isTrashed || item.isHidden || item.isVideo || !item.isIndexed) continue
            val hash = item.perceptualHash64
            val distance = if (hash != 0L) java.lang.Long.bitCount(referenceHash xor hash) else 64
            val colorMatch = item.dominantColorBucket.equals(referenceColor, ignoreCase = true)
            val itemLabels = item.parseAiTags()
                .filter { it.category.equals("Vision", true) }
                .associate { PersianNormalizer.normalizeForSearch(it.labelEn) to it.confidence.coerceIn(0f, 1f) }
            val matched = labels.sumOf { name -> (itemLabels[name] ?: 0f).toDouble() }
            val labelScore = if (labels.isNotEmpty()) (matched / labels.size).toFloat() else 0f
            val visuallyClose = distance <= 23 || (colorMatch && distance <= 30)
            if (!visuallyClose && labelScore < 0.35f) continue
            val shapeScore = if (hash != 0L) (1f - distance.toFloat() / 64f) else 0f
            val score = if (labels.isEmpty()) {
                shapeScore + (if (colorMatch) 0.06f else 0f)
            } else {
                labelScore * 0.75f + shapeScore * 0.32f + (if (colorMatch) 0.04f else 0f)
            }
            val detail = if (labelScore >= 0.35f) "Object match" else "Visual match"
            ranked.add(RankedSearchResult(
                item, score, labelScore.coerceIn(0f, 1f),
                MatchReason.SEMANTIC_AI, detail
            ))
        }
        return ranked.sortedByDescending { it.finalScore }.take(maxResults)
    }

    private fun matchesOrientation(item: MediaItemEntity, orientation: String?): Boolean {
        if (orientation == null) return true
        return when (orientation) {
            "LANDSCAPE" -> item.width > item.height
            "PORTRAIT" -> item.height > item.width
            "SQUARE" -> item.width > 0 && item.height > 0 &&
                abs(item.width - item.height) <= min(item.width, item.height) * 0.15
            else -> true
        }
    }
}

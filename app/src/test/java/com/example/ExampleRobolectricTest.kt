package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.core.ai.ImageEmbeddingEngine
import com.example.core.ai.ImageQualityEngine
import com.example.core.ai.ImageSegmentationEngine
import com.example.core.ai.ImageTaggingEngine
import com.example.core.ai.OCRRecognitionEngine
import com.example.core.ai.QuantizedTensorModel
import com.example.core.ai.SearchRankingEngine
import com.example.core.ai.SimilarityEngine
import com.example.core.ai.TextEmbeddingEngine
import com.example.core.database.MediaItemEntity
import com.example.core.media.PhotoEditPipelineState
import com.example.core.media.PhotoProcessingEngine
import com.example.core.security.VaultCryptoEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and quantized tflite model checksums`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("takZ-Gallery", context.getString(R.string.app_name))

        val visionModel = QuantizedTensorModel.loadFromAssets(context, "models/takz_vision_embedder_int8.tflite")
        val textModel = QuantizedTensorModel.loadFromAssets(context, "models/takz_multilingual_text_fa_en_int8.tflite")
        val segModel = QuantizedTensorModel.loadFromAssets(context, "models/takz_foreground_segmentation_int8.tflite")
        val ocrModel = QuantizedTensorModel.loadFromAssets(context, "models/takz_ocr_fa_en_int8.tflite")

        assertTrue(visionModel.checksumVerified)
        assertTrue(textModel.checksumVerified)
        assertTrue(segModel.checksumVerified)
        assertTrue(ocrModel.checksumVerified)
    }

    @Test
    fun `bilingual persian and english semantic search retrieves matching visual concepts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val textEngine = TextEmbeddingEngine(context)
        val rankingEngine = SearchRankingEngine(textEngine)

        // Create synthetic indexed items for Red Car + Tree vs Black Cat + Sofa vs Sunset + Ocean
        val carEmb = textEngine.embedQuery("red car parked beside a tree")
        val catEmb = textEngine.embedQuery("black cat sleeping on a sofa")
        val sunsetEmb = textEngine.embedQuery("sunset over the ocean")

        val items = listOf(
            MediaItemEntity(
                id = 1L,
                contentUri = "file:///car.jpg",
                fileName = "Red_Car_Tree.jpg",
                folderName = "Camera",
                mimeType = "image/jpeg",
                isIndexed = true,
                embeddingVectorCsv = carEmb.joinToString(",")
            ),
            MediaItemEntity(
                id = 2L,
                contentUri = "file:///cat.jpg",
                fileName = "Black_Cat_Sofa.jpg",
                folderName = "Camera",
                mimeType = "image/jpeg",
                isIndexed = true,
                embeddingVectorCsv = catEmb.joinToString(",")
            ),
            MediaItemEntity(
                id = 3L,
                contentUri = "file:///sunset.jpg",
                fileName = "Sunset_Ocean.jpg",
                folderName = "Travel",
                mimeType = "image/jpeg",
                isIndexed = true,
                embeddingVectorCsv = sunsetEmb.joinToString(",")
            )
        )

        // Query in Persian: "ماشین قرمز کنار درخت"
        val persianCarResults = rankingEngine.searchAndRank(items, "ماشین قرمز کنار درخت")
        assertTrue(persianCarResults.isNotEmpty())
        assertEquals(1L, persianCarResults.first().media.id)

        // Query in Persian: "گربه سیاه روی مبل"
        val persianCatResults = rankingEngine.searchAndRank(items, "گربه سیاه روی مبل")
        assertTrue(persianCatResults.isNotEmpty())
        assertEquals(2L, persianCatResults.first().media.id)

        // Query in English: "A sunset over the ocean"
        val englishSunsetResults = rankingEngine.searchAndRank(items, "A sunset over the ocean")
        assertTrue(englishSunsetResults.isNotEmpty())
        assertEquals(3L, englishSunsetResults.first().media.id)
    }

    @Test
    fun `offline ocr engine extracts persian and english verification codes and urls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ocrEngine = OCRRecognitionEngine(context)
        val res = ocrEngine.parsePatterns(
            "اسکرین شات کد تأیید: ۸۴۹۲۰۱ | Support: info@takz.org | https://takz.org/verify | +98 912 345 6789"
        )
        assertTrue(res.verificationCodes.contains("849201"))
        assertTrue(res.emails.contains("info@takz.org"))
        assertTrue(res.urls.contains("https://takz.org/verify"))
        assertTrue(res.phoneNumbers.isNotEmpty())
    }

    @Test
    fun `offline background removal generates alpha mask and exports transparent bitmap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val segEngine = ImageSegmentationEngine(context)
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        // Draw red subject in center
        for (y in 20..44) {
            for (x in 20..44) {
                bmp.setPixel(x, y, Color.RED)
            }
        }
        val mask = segEngine.generateAlphaMask(bmp, featherRadiusPx = 2, smoothingLevel = 1)
        assertEquals(64 * 64, mask.size)
        val cutout = segEngine.compositeBackground(bmp, mask, mode = "TRANSPARENT")
        assertEquals(64, cutout.width)
        assertEquals(64, cutout.height)
    }

    @Test
    fun `vault crypto engine hashes pin via pbkdf2 and encrypts-decrypts aes-256-gcm payload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoEngine(context)
        assertTrue(crypto.setOrUpdatePin("2580"))
        assertTrue(crypto.verifyPin("2580"))
        assertFalse(crypto.verifyPin("9999"))

        val plain = "Secret TakZ Photo Payload Bytes 12345".toByteArray()
        val enc = crypto.encryptBytesToVaultFile(plain, "unit_test_vault.enc")
        assertTrue(enc.encryptedFile.exists())
        val dec = crypto.decryptVaultFileToBytes(enc.encryptedFile.absolutePath, enc.ivHex)
        assertArrayEquals(plain, dec)
    }
    @Test
    fun `vault streams a multi megabyte file without truncation and detects tampering`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoEngine(context)
        val source = ByteArray(3 * 1024 * 1024 + 17) { (it % 251).toByte() }
        val encrypted = source.inputStream().use {
            crypto.encryptStreamToVaultFile(it, "stream_integration_test.enc")
        }
        val restored = java.io.File(context.cacheDir, "stream_restore_test.bin")
        crypto.decryptVaultFileToStream(encrypted.encryptedFile.absolutePath, encrypted.ivHex, restored)
        assertArrayEquals(source, restored.readBytes())
        assertEquals(source.size.toLong(), restored.length())
        val originalBytes = encrypted.encryptedFile.readBytes()
        originalBytes[originalBytes.size / 2] = (originalBytes[originalBytes.size / 2].toInt() xor 1).toByte()
        encrypted.encryptedFile.writeBytes(originalBytes)
        var rejected = false
        try {
            crypto.decryptVaultFileToStream(encrypted.encryptedFile.absolutePath, encrypted.ivHex, restored)
        } catch (_: Exception) { rejected = true }
        assertTrue("GCM authentication must detect tampered ciphertext", rejected)
    }

    @Test
    fun `advanced search respects typed operators, real OCR and private media`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val search = SearchRankingEngine(TextEmbeddingEngine(context))
        val data = listOf(
            MediaItemEntity(
                id = 200L, contentUri = "content://image/200",
                fileName = "invoice.jpg", folderName = "Receipts", mimeType = "image/jpeg",
                isIndexed = true, isFavorite = true,
                ocrText = "Order 876543 - 2024", dateModifiedSec = 1709251200L,
                dominantColorBucket = "white"
            ),
            MediaItemEntity(
                id = 201L, contentUri = "content://video/201",
                fileName = "invoice.mp4", folderName = "Movies", mimeType = "video/mp4",
                isVideo = true, ocrText = "Order 876543",
                dateModifiedSec = 1709251200L
            ),
            MediaItemEntity(
                id = 202L, contentUri = "content://image/202",
                fileName = "secret-invoice.jpg", folderName = "Private", mimeType = "image/jpeg",
                isHidden = true, ocrText = "Order 876543"
            )
        )
        assertEquals(
            listOf(200L),
            search.searchAndRank(data, "\"order 876543\" type:photo favorite:true after:2024-01-01")
                .map { it.media.id }
        )
        assertEquals(
            listOf(201L),
            search.searchAndRank(data, "type:video has:text folder:Movies")
                .map { it.media.id }
        )
        assertTrue(search.searchAndRank(data, "type:photo before:2020-01-01").isEmpty())
    }

    @Test
    fun `visual photo search finds similar hashes while respecting hidden photos`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val search = SearchRankingEngine(TextEmbeddingEngine(context))
        val data = listOf(
            MediaItemEntity(id = 301L, contentUri = "file:///first.jpg", fileName = "first.jpg",
                folderName = "Camera", mimeType = "image/jpeg", isIndexed = true,
                perceptualHash64 = 0x7F00FF00L, dominantColorBucket = "blue"),
            MediaItemEntity(id = 302L, contentUri = "file:///hidden.jpg", fileName = "hidden.jpg",
                folderName = "Camera", mimeType = "image/jpeg", isIndexed = true,
                perceptualHash64 = 0x7F00FF00L, dominantColorBucket = "blue", isHidden = true)
        )
        val ranked = search.searchByExample(data, 0x7F00FF00L, "blue")
        assertEquals(listOf(301L), ranked.map { it.media.id })
        assertTrue(ranked.first().finalScore > 0.95f)
    }

}

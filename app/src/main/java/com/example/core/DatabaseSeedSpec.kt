package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.R
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

data class BundledMediaSpec(
    val file: File,
    val folderName: String,
    val mimeType: String,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
    val width: Int,
    val height: Int,
    val dateSec: Long,
    val isFavorite: Boolean,
    val embeddedOcrText: String,
    val exifSummary: String,
    val initialManualTags: String
)

object DatabaseSeedSpec {

    fun getBundledSpecs(context: Context, mediaDir: File, baseTimeSec: Long): List<BundledMediaSpec> {
        val list = mutableListOf<BundledMediaSpec>()

        // 1. Red Car parked beside a tree (from generated photorealistic drawable)
        val redCarFile = copyDrawableToMediaFile(
            context,
            R.drawable.img_sample_red_car_1794738662658,
            File(mediaDir, "Red_Car_Beside_Oak_Tree.jpg")
        )
        list.add(
            BundledMediaSpec(
                file = redCarFile,
                folderName = "Camera",
                mimeType = "image/jpeg",
                width = 1200,
                height = 900,
                dateSec = baseTimeSec - 3600L * 4,
                isFavorite = true,
                embeddedOcrText = "THE COBBLE STREET 1740",
                exifSummary = "Sony|Alpha A7 IV|ISO 100|1/500s|f/2.8|35mm|35.6892° N, 51.3890° E",
                initialManualTags = "red car,tree,street,ماشین قرمز,درخت"
            )
        )

        // 2. Black Cat sleeping on a sofa (from generated photorealistic drawable)
        val blackCatFile = copyDrawableToMediaFile(
            context,
            R.drawable.img_sample_black_cat_1794738662860,
            File(mediaDir, "Black_Cat_Sleeping_On_Sofa.jpg")
        )
        list.add(
            BundledMediaSpec(
                file = blackCatFile,
                folderName = "Camera",
                mimeType = "image/jpeg",
                width = 1200,
                height = 900,
                dateSec = baseTimeSec - 3600L * 18,
                isFavorite = true,
                embeddedOcrText = "",
                exifSummary = "Canon|EOS R6 Mark II|ISO 400|1/160s|f/1.8|50mm|Indoor Studio",
                initialManualTags = "black cat,sofa,sleeping,pet,گربه سیاه,مبل"
            )
        )

        // 3. Golden Sunset over the Ocean (from generated photorealistic drawable)
        val sunsetFile = copyDrawableToMediaFile(
            context,
            R.drawable.img_sample_sunset_ocean_1794738664497,
            File(mediaDir, "Golden_Sunset_Over_Ocean.jpg")
        )
        list.add(
            BundledMediaSpec(
                file = sunsetFile,
                folderName = "Travel & Nature",
                mimeType = "image/jpeg",
                width = 1280,
                height = 720,
                dateSec = baseTimeSec - 86400L * 2,
                isFavorite = true,
                embeddedOcrText = "",
                exifSummary = "Google|Pixel 9 Pro|ISO 64|1/1000s|f/1.7|24mm|Caspian Coast",
                initialManualTags = "sunset,ocean,sea,purple sky,غروب خورشید,دریا"
            )
        )

        // 4. Cozy Wooden House in the Forest (from generated photorealistic drawable)
        val woodenHouseFile = copyDrawableToMediaFile(
            context,
            R.drawable.img_sample_wooden_house_1794738664256,
            File(mediaDir, "Wooden_House_In_Misty_Forest.jpg")
        )
        list.add(
            BundledMediaSpec(
                file = woodenHouseFile,
                folderName = "Travel & Nature",
                mimeType = "image/jpeg",
                width = 900,
                height = 1200,
                dateSec = baseTimeSec - 86400L * 5,
                isFavorite = false,
                embeddedOcrText = "",
                exifSummary = "Fujifilm|X-T5|ISO 160|1/250s|f/2.0|23mm|Hyrcanian Forest",
                initialManualTags = "wooden house,forest,trees,cabin,خانه چوبی,جنگل"
            )
        )

        // 5. Screenshot with a Verification Code & Receipt (Persian + English OCR showcase)
        val otpFile = File(mediaDir, "Screenshot_Verification_Code_849201.png")
        if (!otpFile.exists() || otpFile.length() == 0L) {
            renderOtpScreenshotBitmap(otpFile)
        }
        list.add(
            BundledMediaSpec(
                file = otpFile,
                folderName = "Screenshots",
                mimeType = "image/png",
                width = 720,
                height = 1280,
                dateSec = baseTimeSec - 86400L * 1,
                isFavorite = false,
                embeddedOcrText = "اسکرین شات کد تأیید ورود: 849201\nTakZ Security Verification Code: 849201\nReceipt ID: INV-2026-9940\nDate: 2026/11/15 - ۱۴۰۵/۰۸/۲۴\nContact: security@takzgallery.org | +98 912 345 6789\nhttps://takzgallery.org/verify",
                exifSummary = "Android|System Screenshot|sRGB|PNG-24|720x1280|Local",
                initialManualTags = "screenshot,verification code,receipt,اسکرین شات,کد تأیید,رسید"
            )
        )

        // 6. Image containing a Mathematical Formula (Persian + English OCR & Diagram showcase)
        val formulaFile = File(mediaDir, "Mathematical_Formula_Gaussian_Integral.png")
        if (!formulaFile.exists() || formulaFile.length() == 0L) {
            renderMathFormulaBitmap(formulaFile)
        }
        list.add(
            BundledMediaSpec(
                file = formulaFile,
                folderName = "Documents",
                mimeType = "image/png",
                width = 960,
                height = 720,
                dateSec = baseTimeSec - 86400L * 8,
                isFavorite = false,
                embeddedOcrText = "فرمول ریاضی انتگرال گاوسی و معادله اویلر\nMathematical Formula:\n∫_{-∞}^{+∞} e^{-x²} dx = √π\nE = m·c²   |   e^{iπ} + 1 = 0   |   ∑_{n=1}^{∞} 1/n² = π²/6",
                exifSummary = "TakZ Scan|Document Board|sRGB|960x720|Local",
                initialManualTags = "mathematical formula,math,equation,document,فرمول ریاضی,معادله"
            )
        )

        // 7. Purple Rainy Street at Night
        val purpleNightFile = File(mediaDir, "Purple_Rainy_Street_Night.jpg")
        if (!purpleNightFile.exists() || purpleNightFile.length() == 0L) {
            renderPurpleRainyNightBitmap(purpleNightFile)
        }
        list.add(
            BundledMediaSpec(
                file = purpleNightFile,
                folderName = "Camera",
                mimeType = "image/jpeg",
                width = 960,
                height = 720,
                dateSec = baseTimeSec - 86400L * 30,
                isFavorite = true,
                embeddedOcrText = "NEON STUDIO BLVD",
                exifSummary = "Leica|Q3|ISO 1600|1/60s|f/1.7|28mm|Night Mode",
                initialManualTags = "purple,rainy street,night,بنفش,خیابان بارانی,شب"
            )
        )

        // 8. Exact Duplicate of Red Car (for testing SHA-256 Exact Duplicate Detection in Smart Cleanup)
        val duplicateCarFile = File(mediaDir, "Red_Car_Beside_Oak_Tree_BackupCopy.jpg")
        if (!duplicateCarFile.exists() || duplicateCarFile.length() == 0L) {
            redCarFile.copyTo(duplicateCarFile, overwrite = true)
        }
        list.add(
            BundledMediaSpec(
                file = duplicateCarFile,
                folderName = "Downloads",
                mimeType = "image/jpeg",
                width = 1200,
                height = 900,
                dateSec = baseTimeSec - 86400L * 12,
                isFavorite = false,
                embeddedOcrText = "THE COBBLE STREET 1740",
                exifSummary = "Sony|Alpha A7 IV|ISO 100|1/500s|f/2.8|35mm|Duplicate Copy",
                initialManualTags = "duplicate,red car,tree"
            )
        )

        // 9. 4K UHD Video: Caspian Sunset Ocean Waves (Largest Video #1 - 2.45 MB)
        val videoSunsetFile = createVideoContainerWithPoster(
            context = context,
            drawableResId = R.drawable.img_sample_sunset_ocean_1794738664497,
            targetFile = File(mediaDir, "VID_4K_Caspian_Sunset_Waves_60fps.mp4"),
            targetByteSize = 2_480_000L
        )
        list.add(
            BundledMediaSpec(
                file = videoSunsetFile,
                folderName = "Videos",
                mimeType = "video/mp4",
                isVideo = true,
                durationMs = 104_000L, // 01:44
                width = 3840,
                height = 2160,
                dateSec = baseTimeSec - 3600L * 6,
                isFavorite = true,
                embeddedOcrText = "",
                exifSummary = "Samsung|Galaxy S24 Ultra|4K UHD 60fps|HEVC 10-bit|Stereo AAC",
                initialManualTags = "video,sunset,ocean,4k,ویدیو,غروب,دریا"
            )
        )

        // 10. QHD Drone Video: Hyrcanian Misty Forest Flight (Largest Video #2 - 1.85 MB)
        val videoForestFile = createVideoContainerWithPoster(
            context = context,
            drawableResId = R.drawable.img_sample_wooden_house_1794738664256,
            targetFile = File(mediaDir, "VID_QHD_Forest_Drone_Flight.mp4"),
            targetByteSize = 1_890_000L
        )
        list.add(
            BundledMediaSpec(
                file = videoForestFile,
                folderName = "Videos",
                mimeType = "video/mp4",
                isVideo = true,
                durationMs = 76_000L, // 01:16
                width = 2560,
                height = 1440,
                dateSec = baseTimeSec - 86400L * 3,
                isFavorite = false,
                embeddedOcrText = "",
                exifSummary = "DJI|Mavic 3 Pro|2.7K 60fps|H.264|Hyrcanian Forest",
                initialManualTags = "video,forest,drone,nature,ویدیو,جنگل,طبیعت"
            )
        )

        // 11. 1080p Slow-Motion Video: Classic Red Car Drive (Largest Video #3 - 1.38 MB)
        val videoCarFile = createVideoContainerWithPoster(
            context = context,
            drawableResId = R.drawable.img_sample_red_car_1794738662658,
            targetFile = File(mediaDir, "VID_SlowMo_Red_Car_Drive_120fps.mp4"),
            targetByteSize = 1_410_000L
        )
        list.add(
            BundledMediaSpec(
                file = videoCarFile,
                folderName = "Camera",
                mimeType = "video/mp4",
                isVideo = true,
                durationMs = 48_000L, // 00:48
                width = 1920,
                height = 1080,
                dateSec = baseTimeSec - 86400L * 4,
                isFavorite = false,
                embeddedOcrText = "",
                exifSummary = "Samsung|Galaxy S24 Ultra|1080p 120fps Slow-Mo|H.264|Street",
                initialManualTags = "video,car,slow motion,ویدیو,ماشین,حرکت آهسته"
            )
        )

        return list
    }

    private fun copyDrawableToMediaFile(context: Context, drawableResId: Int, targetFile: File): File {
        if (targetFile.exists() && targetFile.length() > 0L) return targetFile
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = BitmapFactory.decodeResource(context.resources, drawableResId, opts)
        if (bmp != null) {
            FileOutputStream(targetFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 94, fos)
            }
        }
        return targetFile
    }

    /**
     * Writes a high-resolution JPEG poster stream followed by padded MP4 stream bytes so
     * both BitmapFactory/Coil thumbnail decoding AND realistic multi-megabyte video storage analysis work seamlessly.
     */
    private fun createVideoContainerWithPoster(
        context: Context,
        drawableResId: Int,
        targetFile: File,
        targetByteSize: Long
    ): File {
        if (targetFile.exists() && targetFile.length() >= targetByteSize) return targetFile
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = BitmapFactory.decodeResource(context.resources, drawableResId, opts)
        if (bmp != null) {
            FileOutputStream(targetFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 96, fos)
            }
        }
        runCatching {
            RandomAccessFile(targetFile, "rw").use { raf ->
                if (raf.length() < targetByteSize) {
                    raf.setLength(targetByteSize)
                }
            }
        }
        return targetFile
    }

    private fun renderOtpScreenshotBitmap(targetFile: File) {
        val w = 720
        val h = 1280
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#F5F7FA"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#111622")
        canvas.drawRect(0f, 0f, w.toFloat(), 140f, paint)
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TakZ Secure Banking • پیامک کد تأیید", 40f, 90f, paint)

        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(44f, 190f, w - 44f, 1080f), 28f, 28f, paint)

        paint.color = Color.parseColor("#00796B")
        paint.textSize = 28f
        canvas.drawText("اسکرین شات دارای کد تأیید (Verification OTP)", 80f, 270f, paint)

        paint.color = Color.parseColor("#E0F2F1")
        canvas.drawRoundRect(RectF(80f, 320f, w - 80f, 470f), 20f, 20f, paint)
        paint.color = Color.parseColor("#004D40")
        paint.textSize = 68f
        canvas.drawText("849201", 215f, 420f, paint)

        paint.color = Color.parseColor("#263238")
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("کد تأیید ورود شما: ۸۴۹۲۰۱", 80f, 550f, paint)
        canvas.drawText("Receipt Invoice: #INV-2026-9940", 80f, 620f, paint)
        canvas.drawText("Date: 2026/11/15  |  تاریخ: ۱۴۰۵/۰۸/۲۴", 80f, 690f, paint)
        canvas.drawText("Phone: +98 912 345 6789", 80f, 760f, paint)
        canvas.drawText("Email: security@takzgallery.org", 80f, 830f, paint)
        canvas.drawText("URL: https://takzgallery.org/verify", 80f, 900f, paint)

        FileOutputStream(targetFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderMathFormulaBitmap(targetFile: File) {
        val w = 960
        val h = 720
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#FAF9F5"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#E2E6EE")
        paint.strokeWidth = 2f
        for (x in 0..w step 48) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), paint)
        for (y in 0..h step 48) canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), paint)

        paint.color = Color.parseColor("#1A237E")
        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        canvas.drawText("Gaussian Integral & Euler Mathematical Formula", 70f, 110f, paint)
        canvas.drawText("فرمول ریاضی انتگرال گاوسی و رابطه اویلر", 70f, 170f, paint)

        paint.color = Color.parseColor("#0D1321")
        paint.textSize = 54f
        canvas.drawText("∫ e^(-x²) dx = √π", 140f, 310f, paint)
        canvas.drawText("e^(iπ) + 1 = 0     E = mc²", 140f, 430f, paint)
        canvas.drawText("∑ (1 / n²) = π² / 6", 140f, 550f, paint)

        FileOutputStream(targetFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderPurpleRainyNightBitmap(targetFile: File) {
        val w = 960
        val h = 720
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.parseColor("#18082C"),
                Color.parseColor("#4A148C"),
                Color.parseColor("#7B1FA2"),
                Color.parseColor("#2A0944")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        val neonColors = listOf("#E040FB", "#AA00FF", "#CE93D8", "#7C4DFF", "#EA80FC")
        for (i in 0 until 28) {
            paint.color = Color.parseColor(neonColors[i % neonColors.size])
            paint.alpha = 145
            val cx = ((i * 97) % w).toFloat()
            val cy = (220 + (i * 53) % (h - 240)).toFloat()
            val r = (18 + (i % 5) * 14).toFloat()
            canvas.drawCircle(cx, cy, r, paint)
        }

        paint.color = Color.argb(110, 230, 210, 255)
        paint.strokeWidth = 2.5f
        for (i in 0 until 75) {
            val rx = ((i * 37) % w).toFloat()
            val ry = ((i * 71) % h).toFloat()
            canvas.drawLine(rx, ry, rx - 14f, ry + 42f, paint)
        }

        FileOutputStream(targetFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) }
    }
}

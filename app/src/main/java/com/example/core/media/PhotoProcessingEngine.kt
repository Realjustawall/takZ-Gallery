package com.example.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

data class DrawingPathData(
    val pointsNorm: List<Pair<Float, Float>>,
    val colorInt: Int = Color.CYAN,
    val strokeWidthNorm: Float = 0.012f
)

data class StickerItemData(
    val symbol: String,
    val normX: Float = 0.5f,
    val normY: Float = 0.5f,
    val scale: Float = 1.0f
)

data class PhotoEditPipelineState(
    // Basic adjustments (-1f .. +1f unless noted)
    val brightness: Float = 0f,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val warmthTemp: Float = 0f,
    val tint: Float = 0f,
    val sharpness: Float = 0f,
    val clarity: Float = 0f,
    val dehaze: Float = 0f,
    val gamma: Float = 1.0f, // 0.5f .. 2.0f
    val vignette: Float = 0f, // 0f .. 1f
    val grain: Float = 0f, // 0f .. 1f
    val fade: Float = 0f, // 0f .. 1f
    // RGB Tone Curves (5 control points y-values at x = 0, 0.25, 0.5, 0.75, 1.0)
    val curveMaster: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val curveRed: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val curveGreen: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val curveBlue: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    // HSL & Split Toning
    val hslHueShift: Float = 0f, // -180f .. +180f
    val splitShadowColorHex: String = "#000000",
    val splitHighlightColorHex: String = "#FFFFFF",
    val splitBalance: Float = 0f,
    // Geometry & Perspective (Samsung Gallery Transform tools)
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val straightenAngle: Float = 0f, // -25f .. +25f
    val perspectiveHorizontal: Float = 0f, // -1f .. +1f
    val perspectiveVertical: Float = 0f, // -1f .. +1f
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropAspectRatio: String = "FREE", // "FREE", "1:1", "4:3", "16:9", "3:4", "9:16"
    val resizeScalePercent: Int = 100, // 100, 75, 50, 25
    // Samsung One UI Creative Filters, Color Pop & Spot Eraser
    val filterPreset: String = "NONE", // "NONE", "AUTO_ENHANCE", "WARM", "COOL", "SOFT", "VIVID", "NOIR_BW", "VINTAGE_GOLD", "CYBER_TEAL", "MATTE_FILM", "PERSIAN_TURQUOISE"
    val filterIntensity: Float = 0.85f,
    val colorPopSubjectOnly: Boolean = false,
    val spotHealPoints: List<Pair<Float, Float>> = emptyList(),
    // Decorations (Stickers, Pen, Text, Frame)
    val overlayText: String = "",
    val overlayTextColor: Int = Color.WHITE,
    val watermarkEnabled: Boolean = false,
    val borderWidthRatio: Float = 0f,
    val drawingPaths: List<DrawingPathData> = emptyList(),
    val placedStickers: List<StickerItemData> = emptyList()
) {
    fun isIdentity(): Boolean =
        brightness == 0f && exposure == 0f && contrast == 0f && saturation == 0f &&
            vibrance == 0f && highlights == 0f && shadows == 0f && warmthTemp == 0f &&
            tint == 0f && sharpness == 0f && clarity == 0f && dehaze == 0f &&
            gamma == 1.0f && vignette == 0f && grain == 0f && fade == 0f &&
            hslHueShift == 0f && rotationDegrees == 0 && straightenAngle == 0f &&
            perspectiveHorizontal == 0f && perspectiveVertical == 0f &&
            !flipHorizontal && !flipVertical && cropAspectRatio == "FREE" &&
            resizeScalePercent == 100 && filterPreset == "NONE" && !colorPopSubjectOnly &&
            spotHealPoints.isEmpty() && overlayText.isBlank() && !watermarkEnabled &&
            borderWidthRatio == 0f && drawingPaths.isEmpty() && placedStickers.isEmpty() &&
            curveMaster == listOf(0f, 0.25f, 0.5f, 0.75f, 1f) &&
            curveRed == listOf(0f, 0.25f, 0.5f, 0.75f, 1f) &&
            curveGreen == listOf(0f, 0.25f, 0.5f, 0.75f, 1f) &&
            curveBlue == listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    fun serialize(): String {
        return listOf(
            "br=$brightness",
            "ex=$exposure",
            "co=$contrast",
            "sa=$saturation",
            "vi=$vibrance",
            "hi=$highlights",
            "sh=$shadows",
            "te=$warmthTemp",
            "ti=$tint",
            "sp=$sharpness",
            "cl=$clarity",
            "dh=$dehaze",
            "ga=$gamma",
            "vg=$vignette",
            "gr=$grain",
            "fd=$fade",
            "hs=$hslHueShift",
            "ro=$rotationDegrees",
            "st=$straightenAngle",
            "ph=$perspectiveHorizontal",
            "pv=$perspectiveVertical",
            "fh=$flipHorizontal",
            "fv=$flipVertical",
            "cr=$cropAspectRatio",
            "rs=$resizeScalePercent",
            "fp=$filterPreset",
            "fi=$filterIntensity",
            "cp=$colorPopSubjectOnly",
            "ot=${overlayText.replace(";", ",")}",
            "wm=$watermarkEnabled",
            "bw=$borderWidthRatio",
            "cm=${curveMaster.joinToString(",")}",
            "cR=${curveRed.joinToString(",")}",
            "cG=${curveGreen.joinToString(",")}",
            "cB=${curveBlue.joinToString(",")}"
        ).joinToString(";")
    }

    companion object {
        fun deserialize(raw: String): PhotoEditPipelineState {
            if (raw.isBlank()) return PhotoEditPipelineState()
            val map = raw.split(";").mapNotNull {
                val idx = it.indexOf('=')
                if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else null
            }.toMap()

            fun parseCurve(key: String): List<Float> {
                val s = map[key] ?: return listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                val vals = s.split(",").mapNotNull { it.toFloatOrNull() }
                return if (vals.size == 5) vals else listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            }

            return PhotoEditPipelineState(
                brightness = map["br"]?.toFloatOrNull() ?: 0f,
                exposure = map["ex"]?.toFloatOrNull() ?: 0f,
                contrast = map["co"]?.toFloatOrNull() ?: 0f,
                saturation = map["sa"]?.toFloatOrNull() ?: 0f,
                vibrance = map["vi"]?.toFloatOrNull() ?: 0f,
                highlights = map["hi"]?.toFloatOrNull() ?: 0f,
                shadows = map["sh"]?.toFloatOrNull() ?: 0f,
                warmthTemp = map["te"]?.toFloatOrNull() ?: 0f,
                tint = map["ti"]?.toFloatOrNull() ?: 0f,
                sharpness = map["sp"]?.toFloatOrNull() ?: 0f,
                clarity = map["cl"]?.toFloatOrNull() ?: 0f,
                dehaze = map["dh"]?.toFloatOrNull() ?: 0f,
                gamma = map["ga"]?.toFloatOrNull() ?: 1.0f,
                vignette = map["vg"]?.toFloatOrNull() ?: 0f,
                grain = map["gr"]?.toFloatOrNull() ?: 0f,
                fade = map["fd"]?.toFloatOrNull() ?: 0f,
                hslHueShift = map["hs"]?.toFloatOrNull() ?: 0f,
                rotationDegrees = map["ro"]?.toIntOrNull() ?: 0,
                straightenAngle = map["st"]?.toFloatOrNull() ?: 0f,
                perspectiveHorizontal = map["ph"]?.toFloatOrNull() ?: 0f,
                perspectiveVertical = map["pv"]?.toFloatOrNull() ?: 0f,
                flipHorizontal = map["fh"]?.toBooleanStrictOrNull() ?: false,
                flipVertical = map["fv"]?.toBooleanStrictOrNull() ?: false,
                cropAspectRatio = map["cr"] ?: "FREE",
                resizeScalePercent = map["rs"]?.toIntOrNull() ?: 100,
                filterPreset = map["fp"] ?: "NONE",
                filterIntensity = map["fi"]?.toFloatOrNull() ?: 0.85f,
                colorPopSubjectOnly = map["cp"]?.toBooleanStrictOrNull() ?: false,
                overlayText = map["ot"] ?: "",
                watermarkEnabled = map["wm"]?.toBooleanStrictOrNull() ?: false,
                borderWidthRatio = map["bw"]?.toFloatOrNull() ?: 0f,
                curveMaster = parseCurve("cm"),
                curveRed = parseCurve("cR"),
                curveGreen = parseCurve("cG"),
                curveBlue = parseCurve("cB")
            )
        }
    }
}

object PhotoProcessingEngine {

    data class HistogramData(
        val rBins: IntArray,
        val gBins: IntArray,
        val bBins: IntArray,
        val lumBins: IntArray
    )

    fun computeHistogram(bitmap: Bitmap): HistogramData {
        val rBins = IntArray(64)
        val gBins = IntArray(64)
        val bBins = IntArray(64)
        val lumBins = IntArray(64)
        val w = bitmap.width
        val h = bitmap.height
        val stepX = max(1, w / 48)
        val stepY = max(1, h / 48)
        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val c = bitmap.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val l = (r * 299 + g * 587 + b * 114) / 1000
                rBins[(r / 4).coerceIn(0, 63)]++
                gBins[(g / 4).coerceIn(0, 63)]++
                bBins[(b / 4).coerceIn(0, 63)]++
                lumBins[(l / 4).coerceIn(0, 63)]++
            }
        }
        return HistogramData(rBins, gBins, bBins, lumBins)
    }

    fun extractColorPalette(bitmap: Bitmap): List<String> {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val regions = listOf(
            0.2f to 0.2f,
            0.8f to 0.2f,
            0.5f to 0.5f,
            0.2f to 0.8f,
            0.8f to 0.8f
        )
        return regions.map { (nx, ny) ->
            val x = (nx * (w - 1)).toInt().coerceIn(0, w - 1)
            val y = (ny * (h - 1)).toInt().coerceIn(0, h - 1)
            val c = bitmap.getPixel(x, y)
            "#%02X%02X%02X".format(Color.red(c), Color.green(c), Color.blue(c))
        }.distinct()
    }

    /**
     * Applies the full non-destructive Samsung One UI Photo & Video EditPipelineState to a source Bitmap.
     */
    fun renderPipeline(source: Bitmap, state: PhotoEditPipelineState): Bitmap {
        // 1. Geometry transformations (Rotation, Straighten, Perspective, Flip, Aspect Crop, Resize)
        val working = applyGeometry(source, state)
        val w = working.width
        val h = working.height
        val pixels = IntArray(w * h)
        working.getPixels(pixels, 0, w, 0, 0, w, h)

        // 2. Spot Healer / Object Eraser (Samsung Gallery Object Eraser / Spot Fix)
        if (state.spotHealPoints.isNotEmpty()) {
            val patchRadius = (min(w, h) * 0.045f).toInt().coerceIn(6, 36)
            val ringOffset = (patchRadius * 1.45f).toInt()
            for ((nx, ny) in state.spotHealPoints) {
                val cx = (nx * w).toInt().coerceIn(0, w - 1)
                val cy = (ny * h).toInt().coerceIn(0, h - 1)
                for (dy in -patchRadius..patchRadius) {
                    val py = cy + dy
                    if (py !in 0 until h) continue
                    for (dx in -patchRadius..patchRadius) {
                        val px = cx + dx
                        if (px !in 0 until w) continue
                        val dist = hypot(dx.toFloat(), dy.toFloat())
                        if (dist <= patchRadius) {
                            val blend = (1f - (dist / patchRadius)).pow(1.4f)
                            // Sample surrounding clean ring pixels
                            val sx1 = (cx - ringOffset).coerceIn(0, w - 1)
                            val sx2 = (cx + ringOffset).coerceIn(0, w - 1)
                            val sy1 = (cy - ringOffset).coerceIn(0, h - 1)
                            val sy2 = (cy + ringOffset).coerceIn(0, h - 1)
                            val c1 = pixels[py * w + sx1]
                            val c2 = pixels[py * w + sx2]
                            val c3 = pixels[sy1 * w + px]
                            val c4 = pixels[sy2 * w + px]
                            val avgR = (Color.red(c1) + Color.red(c2) + Color.red(c3) + Color.red(c4)) / 4
                            val avgG = (Color.green(c1) + Color.green(c2) + Color.green(c3) + Color.green(c4)) / 4
                            val avgB = (Color.blue(c1) + Color.blue(c2) + Color.blue(c3) + Color.blue(c4)) / 4
                            val orig = pixels[py * w + px]
                            val nr = (Color.red(orig) * (1f - blend) + avgR * blend).toInt().coerceIn(0, 255)
                            val ng = (Color.green(orig) * (1f - blend) + avgG * blend).toInt().coerceIn(0, 255)
                            val nb = (Color.blue(orig) * (1f - blend) + avgB * blend).toInt().coerceIn(0, 255)
                            pixels[py * w + px] = Color.argb(255, nr, ng, nb)
                        }
                    }
                }
            }
        }

        // Build 256-entry lookup tables for RGB curves
        val lutM = buildCurveLut(state.curveMaster)
        val lutR = buildCurveLut(state.curveRed)
        val lutG = buildCurveLut(state.curveGreen)
        val lutB = buildCurveLut(state.curveBlue)

        val expMult = 2.0f.pow(state.exposure * 1.5f)
        val contrastFactor = (1f + state.contrast * 0.85f + state.clarity * 0.35f + state.dehaze * 0.3f + state.sharpness * 0.25f).coerceAtLeast(0.15f)
        val invGamma = (1f / state.gamma.coerceIn(0.4f, 2.5f))
        val hsv = FloatArray(3)
        val rng = Random(42)

        for (y in 0 until h) {
            val ny = (y.toFloat() / h.toFloat()) - 0.5f
            for (x in 0 until w) {
                val nx = (x.toFloat() / w.toFloat()) - 0.5f
                val idx = y * w + x
                val c = pixels[idx]
                val a = Color.alpha(c)
                var r = Color.red(c) / 255f
                var g = Color.green(c) / 255f
                var b = Color.blue(c) / 255f

                // Exposure & Brightness & Dehaze
                r = (r * expMult + state.brightness * 0.35f - state.dehaze * 0.06f)
                g = (g * expMult + state.brightness * 0.35f - state.dehaze * 0.06f)
                b = (b * expMult + state.brightness * 0.35f - state.dehaze * 0.06f)

                // Shadows & Highlights recovery
                val lum = (0.299f * r + 0.587f * g + 0.114f * b).coerceIn(0f, 1f)
                if (state.shadows != 0f) {
                    val shadowWeight = (1f - lum).pow(1.6f)
                    val delta = state.shadows * 0.35f * shadowWeight
                    r += delta; g += delta; b += delta
                }
                if (state.highlights != 0f) {
                    val highWeight = lum.pow(1.6f)
                    val delta = state.highlights * 0.35f * highWeight
                    r += delta; g += delta; b += delta
                }

                // Contrast & Clarity around midpoint 0.5
                r = (r - 0.5f) * contrastFactor + 0.5f
                g = (g - 0.5f) * contrastFactor + 0.5f
                b = (b - 0.5f) * contrastFactor + 0.5f

                // White balance: Temperature (Warmth) & Tint
                r += state.warmthTemp * 0.18f
                b -= state.warmthTemp * 0.18f
                g += state.tint * 0.14f

                // Gamma
                r = r.coerceIn(0f, 1f).pow(invGamma)
                g = g.coerceIn(0f, 1f).pow(invGamma)
                b = b.coerceIn(0f, 1f).pow(invGamma)

                // Fade (lift blacks)
                if (state.fade > 0f) {
                    val lift = state.fade * 0.18f
                    r = lift + r * (1f - lift)
                    g = lift + g * (1f - lift)
                    b = lift + b * (1f - lift)
                }

                // Apply RGB Curves LUT
                val ri0 = (r.coerceIn(0f, 1f) * 255f).toInt()
                val gi0 = (g.coerceIn(0f, 1f) * 255f).toInt()
                val bi0 = (b.coerceIn(0f, 1f) * 255f).toInt()
                r = lutM[lutR[ri0]] / 255f
                g = lutM[lutG[gi0]] / 255f
                b = lutM[lutB[bi0]] / 255f

                // HSL & Saturation / Vibrance
                if (state.saturation != 0f || state.vibrance != 0f || state.hslHueShift != 0f) {
                    Color.RGBToHSV(
                        (r * 255f).toInt().coerceIn(0, 255),
                        (g * 255f).toInt().coerceIn(0, 255),
                        (b * 255f).toInt().coerceIn(0, 255),
                        hsv
                    )
                    hsv[0] = (hsv[0] + state.hslHueShift + 360f) % 360f
                    val vibBoost = state.vibrance * (1f - hsv[1]) * 0.6f
                    hsv[1] = (hsv[1] * (1f + state.saturation) + vibBoost).coerceIn(0f, 1f)
                    val rgbInt = Color.HSVToColor(hsv)
                    r = Color.red(rgbInt) / 255f
                    g = Color.green(rgbInt) / 255f
                    b = Color.blue(rgbInt) / 255f
                }

                // Samsung Gallery Filter Presets
                if (state.filterPreset != "NONE" && state.filterIntensity > 0f) {
                    val fi = state.filterIntensity.coerceIn(0f, 1f)
                    var fr = r; var fg = g; var fb = b
                    when (state.filterPreset) {
                        "AUTO_ENHANCE" -> {
                            fr = ((r - 0.48f) * 1.18f + 0.51f).coerceIn(0f, 1f)
                            fg = ((g - 0.48f) * 1.16f + 0.51f).coerceIn(0f, 1f)
                            fb = ((b - 0.48f) * 1.20f + 0.51f).coerceIn(0f, 1f)
                        }
                        "WARM" -> {
                            fr = (r * 1.12f + 0.04f).coerceIn(0f, 1f)
                            fg = (g * 1.03f + 0.01f).coerceIn(0f, 1f)
                            fb = (b * 0.88f).coerceIn(0f, 1f)
                        }
                        "COOL" -> {
                            fr = (r * 0.90f).coerceIn(0f, 1f)
                            fg = (g * 1.02f + 0.02f).coerceIn(0f, 1f)
                            fb = (b * 1.15f + 0.04f).coerceIn(0f, 1f)
                        }
                        "SOFT" -> {
                            fr = (0.06f + r * 0.90f).coerceIn(0f, 1f)
                            fg = (0.06f + g * 0.90f).coerceIn(0f, 1f)
                            fb = (0.07f + b * 0.90f).coerceIn(0f, 1f)
                        }
                        "NOIR_BW" -> {
                            val gray = (0.299f * r + 0.587f * g + 0.114f * b)
                            val cGray = ((gray - 0.5f) * 1.25f + 0.5f).coerceIn(0f, 1f)
                            fr = cGray; fg = cGray; fb = cGray
                        }
                        "VIVID" -> {
                            fr = ((r - 0.5f) * 1.24f + 0.5f).coerceIn(0f, 1f)
                            fg = ((g - 0.5f) * 1.20f + 0.5f).coerceIn(0f, 1f)
                            fb = ((b - 0.5f) * 1.25f + 0.5f).coerceIn(0f, 1f)
                        }
                        "VINTAGE_GOLD" -> {
                            fr = (r * 1.12f + 0.05f).coerceIn(0f, 1f)
                            fg = (g * 1.03f + 0.02f).coerceIn(0f, 1f)
                            fb = (b * 0.84f).coerceIn(0f, 1f)
                        }
                        "CYBER_TEAL" -> {
                            fr = (r * 0.92f).coerceIn(0f, 1f)
                            fg = (g * 1.08f + 0.03f).coerceIn(0f, 1f)
                            fb = (b * 1.16f + 0.05f).coerceIn(0f, 1f)
                        }
                        "MATTE_FILM" -> {
                            fr = 0.08f + r * 0.86f
                            fg = 0.07f + g * 0.86f
                            fb = 0.09f + b * 0.85f
                        }
                        "PERSIAN_TURQUOISE" -> {
                            fr = (r * 0.94f + 0.02f).coerceIn(0f, 1f)
                            fg = (g * 1.12f + 0.04f).coerceIn(0f, 1f)
                            fb = (b * 1.10f + 0.04f).coerceIn(0f, 1f)
                        }
                    }
                    r = r * (1f - fi) + fr * fi
                    g = g * (1f - fi) + fg * fi
                    b = b * (1f - fi) + fb * fi
                }

                // Samsung Gallery Color Point / Color Pop (Subject stays in color, outer background becomes B&W)
                if (state.colorPopSubjectOnly) {
                    val radialDist = hypot(nx * 1.55f, ny * 1.45f)
                    val maxCh = max(r, max(g, b))
                    val minCh = min(r, min(g, b))
                    val sat = if (maxCh > 0.01f) (maxCh - minCh) / maxCh else 0f
                    val isSubject = radialDist < 0.44f || (radialDist < 0.62f && sat > 0.32f)
                    if (!isSubject) {
                        val gray = (0.299f * r + 0.587f * g + 0.114f * b).coerceIn(0f, 1f)
                        r = gray; g = gray; b = gray
                    } else {
                        // Slightly boost subject color pop
                        r = ((r - 0.5f) * 1.12f + 0.5f).coerceIn(0f, 1f)
                        g = ((g - 0.5f) * 1.12f + 0.5f).coerceIn(0f, 1f)
                        b = ((b - 0.5f) * 1.12f + 0.5f).coerceIn(0f, 1f)
                    }
                }

                // Vignette
                if (state.vignette > 0f) {
                    val dist = hypot(nx * 1.4f, ny * 1.4f)
                    val vigFactor = (1f - state.vignette * (dist * dist)).coerceIn(0.15f, 1f)
                    r *= vigFactor; g *= vigFactor; b *= vigFactor
                }

                // Film Grain
                if (state.grain > 0f) {
                    val noise = (rng.nextFloat() - 0.5f) * state.grain * 0.22f
                    r += noise; g += noise; b += noise
                }

                pixels[idx] = Color.argb(
                    a,
                    (r * 255f).toInt().coerceIn(0, 255),
                    (g * 255f).toInt().coerceIn(0, 255),
                    (b * 255f).toInt().coerceIn(0, 255)
                )
            }
        }

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        // Draw Samsung Gallery Decorations (Stickers, Pen Drawings, Frame Border, Text Overlay, Watermark)
        if (
            state.borderWidthRatio > 0f ||
            state.drawingPaths.isNotEmpty() ||
            state.placedStickers.isNotEmpty() ||
            state.overlayText.isNotBlank() ||
            state.watermarkEnabled
        ) {
            val canvas = Canvas(outBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Freehand drawing paths
            for (path in state.drawingPaths) {
                paint.style = Paint.Style.STROKE
                paint.color = path.colorInt
                paint.strokeWidth = max(3f, path.strokeWidthNorm * min(w, h))
                paint.strokeCap = Paint.Cap.ROUND
                for (i in 0 until path.pointsNorm.size - 1) {
                    val p1 = path.pointsNorm[i]
                    val p2 = path.pointsNorm[i + 1]
                    canvas.drawLine(p1.first * w, p1.second * h, p2.first * w, p2.second * h, paint)
                }
            }

            // Stickers
            if (state.placedStickers.isNotEmpty()) {
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                val baseStickerSize = max(36f, min(w, h) * 0.12f)
                for (sticker in state.placedStickers) {
                    paint.textSize = baseStickerSize * sticker.scale.coerceIn(0.5f, 2.5f)
                    val sx = sticker.normX.coerceIn(0.08f, 0.92f) * w
                    val sy = sticker.normY.coerceIn(0.12f, 0.92f) * h
                    canvas.drawText(sticker.symbol, sx, sy, paint)
                }
                paint.textAlign = Paint.Align.LEFT
            }

            // Frame border
            if (state.borderWidthRatio > 0f) {
                paint.style = Paint.Style.STROKE
                paint.color = Color.WHITE
                paint.strokeWidth = state.borderWidthRatio * min(w, h)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }

            // Text overlay
            if (state.overlayText.isNotBlank()) {
                paint.style = Paint.Style.FILL
                paint.color = state.overlayTextColor
                paint.textSize = max(22f, min(w, h) * 0.065f)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.setShadowLayer(6f, 0f, 2f, Color.BLACK)
                val tw = paint.measureText(state.overlayText)
                canvas.drawText(state.overlayText, (w - tw) / 2f, h * 0.88f, paint)
            }

            // Watermark
            if (state.watermarkEnabled) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(190, 255, 255, 255)
                paint.textSize = max(16f, min(w, h) * 0.038f)
                canvas.drawText("takZ-Gallery", w * 0.04f, h * 0.95f, paint)
            }
        }

        return outBitmap
    }

    private fun applyGeometry(source: Bitmap, state: PhotoEditPipelineState): Bitmap {
        var current = source
        val matrix = Matrix()
        var needsMatrix = false

        val sx = if (state.flipHorizontal) -1f else 1f
        val sy = if (state.flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            matrix.postScale(sx, sy, current.width / 2f, current.height / 2f)
            needsMatrix = true
        }

        val totalRot = (state.rotationDegrees + state.straightenAngle)
        if (abs(totalRot) > 0.01f) {
            matrix.postRotate(totalRot, current.width / 2f, current.height / 2f)
            needsMatrix = true
        }

        if (abs(state.perspectiveHorizontal) > 0.01f || abs(state.perspectiveVertical) > 0.01f) {
            matrix.postSkew(
                state.perspectiveHorizontal * 0.22f,
                state.perspectiveVertical * 0.22f,
                current.width / 2f,
                current.height / 2f
            )
            needsMatrix = true
        }

        if (needsMatrix) {
            current = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        }

        if (state.cropAspectRatio != "FREE") {
            val targetRatio = when (state.cropAspectRatio) {
                "1:1" -> 1f
                "4:3" -> 4f / 3f
                "16:9" -> 16f / 9f
                "3:4" -> 3f / 4f
                "9:16" -> 9f / 16f
                else -> 0f
            }
            if (targetRatio > 0f) {
                val cw = current.width
                val ch = current.height
                val currentRatio = cw.toFloat() / ch.toFloat()
                val cropW: Int
                val cropH: Int
                if (currentRatio > targetRatio) {
                    cropH = ch
                    cropW = (ch * targetRatio).toInt().coerceIn(1, cw)
                } else {
                    cropW = cw
                    cropH = (cw / targetRatio).toInt().coerceIn(1, ch)
                }
                val x0 = ((cw - cropW) / 2).coerceAtLeast(0)
                val y0 = ((ch - cropH) / 2).coerceAtLeast(0)
                current = Bitmap.createBitmap(current, x0, y0, cropW, cropH)
            }
        }

        val scalePct = state.resizeScalePercent.coerceIn(25, 100)
        if (scalePct < 100) {
            val nw = (current.width * scalePct / 100).coerceAtLeast(64)
            val nh = (current.height * scalePct / 100).coerceAtLeast(64)
            current = Bitmap.createScaledBitmap(current, nw, nh, true)
        }

        return current
    }

    private fun buildCurveLut(points: List<Float>): IntArray {
        val pts = if (points.size == 5) points else listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val lut = IntArray(256)
        for (i in 0..255) {
            val x = i / 255f
            val scaled = x * 4f
            val seg = scaled.toInt().coerceIn(0, 3)
            val t = (scaled - seg).coerceIn(0f, 1f)
            val y = pts[seg] * (1f - t) + pts[seg + 1] * t
            lut[i] = (y.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        }
        return lut
    }
}

package com.example.engine.sam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import com.example.model.SamCutoutSpec
import com.example.model.SamTapPoint
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SamSegmentationOutcome(
    val updatedSpec: SamCutoutSpec,
    val maskBitmap: Bitmap?,
    val subjectCount: Int
)

/**
 * AI Subject Cutout (SAM-style) engine backed by Google ML Kit Subject Segmentation.
 * Supports:
 * - One-tap auto-isolation of all foreground subjects
 * - Interactive tap-to-include / tap-to-exclude subject points in normalized (0..1) coordinates
 * - Real-time edge Choke (erode/dilate), Feather (soft alpha falloff), and Invert refinement
 * - Clean PNG mask caching per layer + deterministic fallback when Play Services model isn't ready
 */
object SamCutoutSegmenter {

    private data class CachedSegmentation(
        val width: Int,
        val height: Int,
        val foregroundConfidence: FloatArray,
        val subjectMasks: List<Pair<IntArray, FloatArray>> // bounds/confidences per detected subject
    )

    private val memoryCache = HashMap<String, CachedSegmentation>()

    suspend fun runCutout(
        context: Context,
        layerId: String,
        sourceBitmap: Bitmap,
        spec: SamCutoutSpec
    ): SamSegmentationOutcome = withContext(Dispatchers.Default) {
        val w = sourceBitmap.width.coerceAtLeast(1)
        val h = sourceBitmap.height.coerceAtLeast(1)
        val cacheKey = "${layerId}_${w}x${h}_${sourceBitmap.generationId}"

        val segData = memoryCache[cacheKey] ?: run {
            val computed = segmentWithMlKitOrFallback(sourceBitmap)
            if (memoryCache.size > 8) memoryCache.clear()
            memoryCache[cacheKey] = computed
            computed
        }

        val combinedMask = buildAlphaMaskArray(
            seg = segData,
            isolateAll = spec.isolateAllSubjects,
            tapPoints = spec.tapPoints
        )

        val refinedMask = refineMask(
            mask = combinedMask,
            width = segData.width,
            height = segData.height,
            choke = spec.choke,
            feather = spec.feather,
            invert = spec.invert
        )

        val maskBmp = createMaskBitmap(refinedMask, segData.width, segData.height)
        val maskFile = saveMaskBitmapToCache(context, layerId, maskBmp)

        val updatedSpec = spec.copy(
            enabled = true,
            maskUriString = Uri.fromFile(maskFile).toString(),
            detectedSubjectCount = segData.subjectMasks.size.coerceAtLeast(1)
        )
        SamSegmentationOutcome(
            updatedSpec = updatedSpec,
            maskBitmap = maskBmp,
            subjectCount = updatedSpec.detectedSubjectCount
        )
    }

    /**
     * Pure JVM-safe cutout generator for unit tests and offline synchronous preview.
     */
    fun computeRefinedConfidenceMask(
        width: Int,
        height: Int,
        pixels: IntArray,
        spec: SamCutoutSpec
    ): FloatArray {
        val seg = buildSaliencyFallbackSegmentation(width, height, pixels)
        val raw = buildAlphaMaskArray(seg, spec.isolateAllSubjects, spec.tapPoints)
        return refineMask(raw, width, height, spec.choke, spec.feather, spec.invert)
    }

    private suspend fun segmentWithMlKitOrFallback(bitmap: Bitmap): CachedSegmentation {
        val w = bitmap.width
        val h = bitmap.height
        val isRobolectric = Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
        if (!isRobolectric) {
            val mlResult = runCatching {
                val options = SubjectSegmenterOptions.Builder()
                    .enableForegroundConfidenceMask()
                    .enableMultipleSubjects(
                        SubjectSegmenterOptions.SubjectResultOptions.Builder()
                            .enableConfidenceMask()
                            .build()
                    )
                    .build()
                val client = SubjectSegmentation.getClient(options)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val result = awaitTask(client.process(inputImage))
                extractSegmentationData(w, h, result)
            }.getOrNull()
            if (mlResult != null) return mlResult
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return buildSaliencyFallbackSegmentation(w, h, pixels)
    }

    private fun extractSegmentationData(
        w: Int,
        h: Int,
        result: SubjectSegmentationResult
    ): CachedSegmentation {
        val fgBuf: FloatBuffer? = result.foregroundConfidenceMask
        val fgArr = FloatArray(w * h)
        if (fgBuf != null) {
            fgBuf.rewind()
            val count = min(fgArr.size, fgBuf.remaining())
            fgBuf.get(fgArr, 0, count)
        }
        val subjectsList = result.subjects.mapNotNull { subj ->
            val buf = subj.confidenceMask ?: return@mapNotNull null
            val sw = subj.width.coerceAtLeast(1)
            val sh = subj.height.coerceAtLeast(1)
            val arr = FloatArray(sw * sh)
            buf.rewind()
            buf.get(arr, 0, min(arr.size, buf.remaining()))
            val bounds = intArrayOf(subj.startX, subj.startY, sw, sh)
            bounds to arr
        }
        return CachedSegmentation(w, h, fgArr, subjectsList)
    }

    private fun buildSaliencyFallbackSegmentation(
        w: Int,
        h: Int,
        pixels: IntArray
    ): CachedSegmentation {
        // Compute border color reference for clean foreground saliency separation
        var borderR = 0f
        var borderG = 0f
        var borderB = 0f
        var borderCount = 0
        for (x in 0 until w) {
            val top = pixels[x]
            val bot = pixels[(h - 1) * w + x]
            borderR += Color.red(top) + Color.red(bot)
            borderG += Color.green(top) + Color.green(bot)
            borderB += Color.blue(top) + Color.blue(bot)
            borderCount += 2
        }
        val avgR = borderR / max(borderCount, 1)
        val avgG = borderG / max(borderCount, 1)
        val avgB = borderB / max(borderCount, 1)

        val fgArr = FloatArray(w * h)
        val cx = w * 0.5f
        val cy = h * 0.5f
        val maxDist = hypot(cx, cy).coerceAtLeast(1f)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val px = pixels[row + x]
                val a = Color.alpha(px) / 255f
                if (a < 0.05f) {
                    fgArr[row + x] = 0f
                    continue
                }
                val dr = Color.red(px) - avgR
                val dg = Color.green(px) - avgG
                val db = Color.blue(px) - avgB
                val colorDiff = kotlin.math.sqrt(dr * dr + dg * dg + db * db) / 255f
                val centerBias = 1f - (hypot(x - cx, y - cy) / maxDist) * 0.55f
                val score = ((colorDiff * 2.1f) * centerBias).coerceIn(0f, 1f)
                fgArr[row + x] = if (score > 0.42f) ((score - 0.35f) / 0.3f).coerceIn(0f, 1f) else 0f
            }
        }
        return CachedSegmentation(
            width = w,
            height = h,
            foregroundConfidence = fgArr,
            subjectMasks = listOf(intArrayOf(0, 0, w, h) to fgArr)
        )
    }

    private fun buildAlphaMaskArray(
        seg: CachedSegmentation,
        isolateAll: Boolean,
        tapPoints: List<SamTapPoint>
    ): FloatArray {
        val w = seg.width
        val h = seg.height
        val out = if (isolateAll || tapPoints.none { it.include }) {
            seg.foregroundConfidence.copyOf()
        } else {
            FloatArray(w * h) { 0f }
        }

        if (seg.subjectMasks.isNotEmpty() && tapPoints.isNotEmpty()) {
            for (pt in tapPoints) {
                val px = (pt.x.coerceIn(0f, 1f) * (w - 1)).roundToInt()
                val py = (pt.y.coerceIn(0f, 1f) * (h - 1)).roundToInt()

                var matchedAnySubject = false
                for ((bounds, maskArr) in seg.subjectMasks) {
                    val sx = bounds[0]
                    val sy = bounds[1]
                    val sw = bounds[2]
                    val sh = bounds[3]
                    if (px in sx until (sx + sw) && py in sy until (sy + sh)) {
                        val localConf = maskArr[(py - sy) * sw + (px - sx)]
                        if (localConf >= 0.35f) {
                            matchedAnySubject = true
                            for (y in 0 until sh) {
                                val dstRow = (sy + y) * w
                                val srcRow = y * sw
                                for (x in 0 until sw) {
                                    val c = maskArr[srcRow + x]
                                    if (pt.include) {
                                        out[dstRow + sx + x] = max(out[dstRow + sx + x], c)
                                    } else if (c > 0.35f) {
                                        out[dstRow + sx + x] = min(out[dstRow + sx + x], 1f - c)
                                    }
                                }
                            }
                        }
                    }
                }

                // Local brush/region fallback if tap didn't hit a discrete subject bounding mask
                if (!matchedAnySubject) {
                    val radius = (min(w, h) * 0.16f).roundToInt().coerceAtLeast(8)
                    val y0 = max(0, py - radius)
                    val y1 = min(h - 1, py + radius)
                    val x0 = max(0, px - radius)
                    val x1 = min(w - 1, px + radius)
                    for (y in y0..y1) {
                        for (x in x0..x1) {
                            val dist = hypot((x - px).toFloat(), (y - py).toFloat())
                            if (dist <= radius) {
                                val falloff = 1f - (dist / radius)
                                val idx = y * w + x
                                if (pt.include) {
                                    out[idx] = max(out[idx], max(seg.foregroundConfidence[idx], falloff))
                                } else {
                                    out[idx] = out[idx] * (1f - falloff)
                                }
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    private fun refineMask(
        mask: FloatArray,
        width: Int,
        height: Int,
        choke: Float,
        feather: Float,
        invert: Boolean
    ): FloatArray {
        var cur = mask
        // Simple fast 4-neighbor choke (erode for positive, dilate for negative)
        val chokeSteps = (abs(choke) * 4f).roundToInt().coerceIn(0, 4)
        if (chokeSteps > 0) {
            val isErode = choke > 0f
            repeat(chokeSteps) {
                val next = FloatArray(cur.size)
                for (y in 0 until height) {
                    val row = y * width
                    val yUp = max(0, y - 1) * width
                    val yDn = min(height - 1, y + 1) * width
                    for (x in 0 until width) {
                        val xL = max(0, x - 1)
                        val xR = min(width - 1, x + 1)
                        val c = cur[row + x]
                        val n1 = cur[yUp + x]
                        val n2 = cur[yDn + x]
                        val n3 = cur[row + xL]
                        val n4 = cur[row + xR]
                        next[row + x] = if (isErode) {
                            min(c, min(min(n1, n2), min(n3, n4)))
                        } else {
                            max(c, max(max(n1, n2), max(n3, n4)))
                        }
                    }
                }
                cur = next
            }
        }

        // Feather pass (box smooth)
        val featherPasses = (feather * 5f).roundToInt().coerceIn(0, 5)
        if (featherPasses > 0) {
            repeat(featherPasses) {
                val next = FloatArray(cur.size)
                for (y in 0 until height) {
                    val row = y * width
                    val yUp = max(0, y - 1) * width
                    val yDn = min(height - 1, y + 1) * width
                    for (x in 0 until width) {
                        val xL = max(0, x - 1)
                        val xR = min(width - 1, x + 1)
                        val sum = cur[row + x] * 2f + cur[yUp + x] + cur[yDn + x] + cur[row + xL] + cur[row + xR]
                        next[row + x] = (sum / 6f).coerceIn(0f, 1f)
                    }
                }
                cur = next
            }
        }

        if (invert) {
            val inv = FloatArray(cur.size)
            for (i in cur.indices) inv[i] = 1f - cur[i]
            cur = inv
        }
        return cur
    }

    private fun createMaskBitmap(mask: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val v = (mask[i] * 255f).roundToInt().coerceIn(0, 255)
            // White with alpha = v so it displays nicely and feeds GL mask cleanly via alpha/luminance
            pixels[i] = Color.argb(v, v, v, v)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun saveMaskBitmapToCache(context: Context, layerId: String, maskBitmap: Bitmap): File {
        val dir = File(context.cacheDir, "sam_masks").apply { if (!exists()) mkdirs() }
        val outFile = File(dir, "sam_${layerId}_${System.currentTimeMillis()}.png")
        outFile.outputStream().use { out ->
            maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return outFile
    }

    fun loadMaskBitmap(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val path = Uri.parse(uriString).path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { res ->
            if (cont.isActive) cont.resume(res)
        }.addOnFailureListener { err ->
            if (cont.isActive) cont.resumeWithException(err)
        }
    }
}

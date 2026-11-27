package com.example.engine.sam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.example.data.MediaImportHelper
import com.example.model.EditorLayer
import com.example.model.MediaClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resolves a source Bitmap from an EditorLayer (photo, video frame at current progress, or solid swatch)
 * and runs scaled ML Kit Subject Segmentation / SAM cutout safely off the main thread.
 */
object SamLayerBitmapResolver {

    suspend fun extractLayerSourceBitmap(
        context: Context,
        layer: EditorLayer,
        stageProgress: Float
    ): Bitmap? = withContext(Dispatchers.IO) {
        val raw = when (val clip = layer.mediaClip) {
            is MediaClip.Video -> {
                val seekMs = clip.startTrimMs + (stageProgress.coerceIn(0f, 1f) * clip.trimmedDurationMs).toLong()
                MediaImportHelper.extractVideoFrame(clip.uriString, seekMs, context)
            }
            is MediaClip.Photo -> decodeUriBitmap(context, clip.uriString)
            null -> {
                val bg = layer.backgroundColorArgb?.toInt() ?: return@withContext null
                Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { bmp ->
                    Canvas(bmp).drawColor(bg)
                }
            }
        } ?: return@withContext null

        // Downscale oversized camera photos/4K frames so ML Kit + pixel refinement stay under <60ms
        val maxDim = 720
        val w = raw.width.coerceAtLeast(1)
        val h = raw.height.coerceAtLeast(1)
        if (w <= maxDim && h <= maxDim) return@withContext raw
        val scale = minOf(maxDim.toFloat() / w.toFloat(), maxDim.toFloat() / h.toFloat())
        val dstW = (w * scale).toInt().coerceAtLeast(64)
        val dstH = (h * scale).toInt().coerceAtLeast(64)
        Bitmap.createScaledBitmap(raw, dstW, dstH, true)
    }

    private fun decodeUriBitmap(context: Context, uriString: String): Bitmap? {
        if (uriString.isBlank()) return null
        val parsed = Uri.parse(uriString)
        val path = parsed.path
        if (parsed.scheme == "file" && path != null && File(path).exists()) {
            return BitmapFactory.decodeFile(path)
        }
        if (parsed.scheme == null && File(uriString).exists()) {
            return BitmapFactory.decodeFile(uriString)
        }
        return runCatching {
            context.contentResolver.openInputStream(parsed)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
}

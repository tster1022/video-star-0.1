package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.gl.ShaderPassGlEffect
import com.example.model.AspectRatioMode
import com.example.model.MediaClip
import com.example.model.MotionPreset
import com.example.model.TimelineScene
import com.example.model.VideoProject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object SceneBitmapRenderer {

    fun computeOutputSize(aspectRatio: AspectRatioMode, resolution: String): Pair<Int, Int> {
        val baseShort = when (resolution.lowercase()) {
            "4k" -> 1080
            "720p" -> 540
            else -> 720
        }
        val (w, h) = when (aspectRatio) {
            AspectRatioMode.SQUARE -> baseShort to baseShort
            AspectRatioMode.PORTRAIT -> baseShort to ((baseShort * 16f / 9f).toInt())
            AspectRatioMode.PORTRAIT_3_4 -> baseShort to ((baseShort * 4f / 3f).toInt())
            AspectRatioMode.PORTRAIT_4_5 -> baseShort to ((baseShort * 5f / 4f).toInt())
            AspectRatioMode.LANDSCAPE_4_3 -> ((baseShort * 4f / 3f).toInt()) to baseShort
            AspectRatioMode.LANDSCAPE -> ((baseShort * 16f / 9f).toInt()) to baseShort
        }
        val evenW = (w / 16) * 16
        val evenH = (h / 16) * 16
        return evenW.coerceAtLeast(320) to evenH.coerceAtLeast(320)
    }

    fun computeHardwareOutputSize(aspectRatio: AspectRatioMode, resolution: String): Pair<Int, Int> {
        val baseShort = when (resolution.lowercase()) {
            "4k" -> 2160
            "720p" -> 720
            else -> 1080
        }
        val (w, h) = when (aspectRatio) {
            AspectRatioMode.SQUARE -> baseShort to baseShort
            AspectRatioMode.PORTRAIT -> baseShort to ((baseShort * 16f / 9f).toInt())
            AspectRatioMode.PORTRAIT_3_4 -> baseShort to ((baseShort * 4f / 3f).toInt())
            AspectRatioMode.PORTRAIT_4_5 -> baseShort to ((baseShort * 5f / 4f).toInt())
            AspectRatioMode.LANDSCAPE_4_3 -> ((baseShort * 4f / 3f).toInt()) to baseShort
            AspectRatioMode.LANDSCAPE -> ((baseShort * 16f / 9f).toInt()) to baseShort
        }
        val evenW = (w / 16) * 16
        val evenH = (h / 16) * 16
        return evenW.coerceAtLeast(320) to evenH.coerceAtLeast(320)
    }

    fun decodeSourceBitmap(
        uriString: String?,
        isVideo: Boolean,
        mediaTimeMs: Long
    ): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val path = Uri.parse(uriString).path ?: return null
        val file = File(path)
        if (!file.exists()) return null

        return if (!isVideo) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(
                    mediaTimeMs.coerceAtLeast(0L) * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    fun computeMotionOffsets(scene: TimelineScene, progress: Float): FloatArray {
        val m = scene.motion
        if (m.preset == MotionPreset.NONE || m.intensity <= 0.01f) return floatArrayOf(0f, 0f, 1f, 0f)
        val phase = progress * m.speed * 2f * PI.toFloat() * 3f
        val amp = m.intensity.coerceIn(0f, 1f)
        return when (m.preset) {
            MotionPreset.NONE -> floatArrayOf(0f, 0f, 1f, 0f)
            MotionPreset.SHAKE_H -> floatArrayOf(sin(phase * 2.5f) * 32f * amp, 0f, 1f, 0f)
            MotionPreset.SHAKE_V -> floatArrayOf(0f, cos(phase * 2.5f) * 32f * amp, 1f, 0f)
            MotionPreset.PENDULUM -> floatArrayOf(sin(phase) * 18f * amp, 0f, 1f, sin(phase) * 12f * amp)
            MotionPreset.ZOOM_BOUNCE -> floatArrayOf(0f, 0f, 1f + kotlin.math.abs(sin(phase)) * 0.25f * amp, 0f)
            MotionPreset.PULSE -> floatArrayOf(0f, 0f, 1f + ((sin(phase) + 1f) * 0.14f * amp), 0f)
            MotionPreset.DRIFT -> floatArrayOf(sin(phase * 0.7f) * 24f * amp, cos(phase * 0.5f) * 18f * amp, 1.04f, sin(phase * 0.4f) * 4f * amp)
        }
    }

    fun renderSceneFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: TimelineScene,
        localProgress: Float,
        sourceBitmap: Bitmap?
    ) {
        val activeTransform = scene.transform.evaluatedAt(localProgress)
        val bgArgb = activeTransform.bgColorArgb.toInt()
        canvas.drawColor(bgArgb)

        val motionVals = computeMotionOffsets(scene, localProgress)
        val totalOffsetX = activeTransform.offsetX + motionVals[0]
        val totalOffsetY = activeTransform.offsetY + motionVals[1]
        val totalScale = activeTransform.scale * motionVals[2]
        val totalRot = activeTransform.rotation + motionVals[3]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        applyEffectColorMatrix(paint, scene, localProgress)

        val activePasses = scene.resolvedEffectPasses()
        val hasMotionTile = activeTransform.mirrorEdges || activePasses.any { it.effectId == "motion_tile" }

        if (sourceBitmap != null) {
            val drawBitmap = { tileX: Int, tileY: Int, mirrorX: Boolean, mirrorY: Boolean ->
                val bw = sourceBitmap.width.toFloat().coerceAtLeast(1f)
                val bh = sourceBitmap.height.toFloat().coerceAtLeast(1f)
                val fitScale = if (activeTransform.isFitMode) {
                    minOf(width / bw, height / bh)
                } else {
                    maxOf(width / bw, height / bh)
                }
                val matrix = Matrix()
                matrix.postTranslate(-bw / 2f, -bh / 2f)
                val hFlip = (if (activeTransform.flipH) -1f else 1f) * (if (mirrorX) -1f else 1f)
                val vFlip = (if (activeTransform.flipV) -1f else 1f) * (if (mirrorY) -1f else 1f)
                matrix.postScale(fitScale * totalScale * hFlip, fitScale * totalScale * vFlip)
                matrix.postRotate(totalRot)
                matrix.postTranslate(
                    width / 2f + totalOffsetX + tileX * width,
                    height / 2f + totalOffsetY + tileY * height
                )
                canvas.drawBitmap(sourceBitmap, matrix, paint)
            }

            if (hasMotionTile) {
                for (tx in -1..1) {
                    for (ty in -1..1) {
                        drawBitmap(tx, ty, (tx and 1) != 0, (ty and 1) != 0)
                    }
                }
            } else {
                drawBitmap(0, 0, false, false)
            }
        } else if (scene.isMultiLayerComposite && scene.layers.isNotEmpty()) {
            for (layer in scene.layers) {
                layer.backgroundColorArgb?.let { argb ->
                    val layerPaint = Paint().apply {
                        color = argb.toInt()
                        alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                    }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), layerPaint)
                }
                layer.mediaClip?.let { lc ->
                    val bmp = decodeSourceBitmap(lc.uriString, lc is MediaClip.Video, (localProgress * lc.effectiveDurationMs).toLong())
                    if (bmp != null) {
                        val bw = bmp.width.toFloat().coerceAtLeast(1f)
                        val bh = bmp.height.toFloat().coerceAtLeast(1f)
                        val fit = minOf(width / bw, height / bh)
                        val m = Matrix().apply {
                            postTranslate(-bw / 2f, -bh / 2f)
                            postScale(
                                fit * layer.scale * (if (layer.isMirrored) -1f else 1f),
                                fit * layer.scale * (if (layer.isFlippedVertical) -1f else 1f)
                            )
                            postRotate(layer.rotation)
                            postTranslate(width / 2f + layer.offsetX, height / 2f + layer.offsetY)
                        }
                        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                        }
                        val samBmp = if (layer.samCutout.isActive) {
                            com.example.engine.sam.SamCutoutSegmenter.loadMaskBitmap(layer.samCutout.maskUriString)
                        } else null
                        if (samBmp != null) {
                            val saveId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                            canvas.drawBitmap(bmp, m, lp)
                            val scaledMask = Bitmap.createScaledBitmap(samBmp, bmp.width, bmp.height, true)
                            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                            }
                            canvas.drawBitmap(scaledMask, m, maskPaint)
                            scaledMask.recycle()
                            samBmp.recycle()
                            canvas.restoreToCount(saveId)
                        } else {
                            canvas.drawBitmap(bmp, m, lp)
                        }
                        bmp.recycle()
                    }
                }
                val rawTxt = layer.effectiveText
                val durSec = ((layer.mediaClip?.effectiveDurationMs ?: scene.effectiveDurationMs) / 1000f).coerceAtLeast(0.2f)
                val txt = com.example.engine.effects.shaders.ShaderUniformEvaluator.formatTextForEffects(
                    baseText = rawTxt,
                    passes = layer.effectPasses,
                    progress = localProgress,
                    durationSec = durSec
                ).takeIf { rawTxt.isNotBlank() || layer.effectPasses.any { p -> p.isEnabled && p.effectId.startsWith("am_text_") } } ?: ""
                if (txt.isNotBlank()) {
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = layer.textSpec.textColorArgb.toInt()
                        textSize = (layer.textSpec.fontSizeSp * (width / 360f)).coerceAtLeast(20f)
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                        setShadowLayer(layer.textSpec.shadowRadius, 0f, 3f, Color.BLACK)
                    }
                    canvas.drawText(
                        txt,
                        width / 2f + layer.offsetX,
                        height / 2f + layer.offsetY,
                        tp
                    )
                }
            }
        }

        drawEffectOverlays(canvas, width, height, scene, localProgress)
    }

    private fun applyEffectColorMatrix(paint: Paint, scene: TimelineScene, progress: Float) {
        val passes = scene.resolvedEffectPasses()
        val hasCustomColor = !scene.colorGrading.isNeutral
        if (passes.isEmpty() && !hasCustomColor) return

        val combined = ColorMatrix()
        var modified = false
        if (hasCustomColor) {
            combined.postConcat(ColorMatrix(scene.colorGrading.toColorMatrixArray()))
            modified = true
        }
        for (fx in passes) {
            val k = fx.evaluateIntensityAt(progress)
            if (k <= 0.01f) continue
            val cm = ColorMatrix()
            val hit = when (fx.effectId) {
                "bw" -> { cm.setSaturation(1f - k); true }
                "exposure_flash" -> {
                    val boost = 1f + k * 0.9f
                    cm.setScale(boost, boost, boost, 1f); true
                }
                "hue_shift" -> {
                    val angle = k * 180f
                    val rad = Math.toRadians(angle.toDouble())
                    val cosVal = cos(rad).toFloat()
                    val sinVal = sin(rad).toFloat()
                    cm.set(
                        floatArrayOf(
                            0.213f + cosVal * 0.787f - sinVal * 0.213f, 0.715f - cosVal * 0.715f - sinVal * 0.715f, 0.072f - cosVal * 0.072f + sinVal * 0.928f, 0f, 0f,
                            0.213f - cosVal * 0.213f + sinVal * 0.143f, 0.715f + cosVal * 0.285f + sinVal * 0.140f, 0.072f - cosVal * 0.072f - sinVal * 0.283f, 0f, 0f,
                            0.213f - cosVal * 0.213f - sinVal * 0.787f, 0.715f - cosVal * 0.715f + sinVal * 0.715f, 0.072f + cosVal * 0.928f + sinVal * 0.072f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    true
                }
                "duotone" -> {
                    cm.setSaturation(0.15f)
                    val tint = ColorMatrix().apply { setScale(1.1f + 0.3f * k, 0.75f, 1.15f + 0.25f * k, 1f) }
                    cm.postConcat(tint); true
                }
                "glow_bloom" -> {
                    val boost = 1f + k * 0.35f
                    cm.setScale(boost, boost, boost, 1f); true
                }
                "color_adjust" -> {
                    cm.setSaturation(0.5f + fx.param("saturation", 0.7f) * 1.2f * k)
                    val bright = 0.7f + fx.param("brightness", 0.55f) * 0.65f
                    val sc = ColorMatrix().apply { setScale(bright, bright, bright, 1f) }
                    cm.postConcat(sc); true
                }
                else -> false
            }
            if (hit) {
                combined.postConcat(cm)
                modified = true
            }
        }
        if (modified) paint.colorFilter = ColorMatrixColorFilter(combined)
    }

    private fun drawEffectOverlays(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: TimelineScene,
        progress: Float
    ) {
        scene.resolvedEffectPasses().forEach { fx ->
            val k = fx.evaluateIntensityAt(progress)
            if (k <= 0.01f) return@forEach

            when (fx.effectId) {
                "vignette" -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            width / 2f,
                            height / 2f,
                            maxOf(width, height) * 0.65f,
                            intArrayOf(Color.TRANSPARENT, Color.argb((210 * k).toInt(), 0, 0, 0)),
                            floatArrayOf(0.45f, 1.0f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
                }
                "scanlines", "vhs_tape" -> {
                    val p = Paint().apply {
                        color = Color.argb((110 * k).toInt(), 0, 0, 0)
                        strokeWidth = 2f
                    }
                    var y = 0f
                    while (y < height) {
                        canvas.drawLine(0f, y, width.toFloat(), y, p)
                        y += 8f
                    }
                    if (fx.effectId == "vhs_tape") {
                        val barY = ((progress * 1.4f) % 1f) * height
                        val noisePaint = Paint().apply { color = Color.argb((90 * k).toInt(), 255, 255, 255) }
                        canvas.drawRect(0f, barY, width.toFloat(), barY + 10f, noisePaint)
                    }
                }
                "exposure_flash" -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                        shader = RadialGradient(
                            width * (0.2f + 0.6f * progress),
                            height * 0.25f,
                            width * 0.7f,
                            intArrayOf(Color.argb((190 * k).toInt(), 255, 110, 60), Color.TRANSPARENT),
                            null,
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
                }
                "rgb_split", "glitch" -> {
                    val shift = (width * 0.025f * k)
                    val pRed = Paint().apply { color = Color.argb((70 * k).toInt(), 255, 0, 80) }
                    val pCyan = Paint().apply { color = Color.argb((70 * k).toInt(), 0, 230, 255) }
                    canvas.drawRect(0f, 0f, shift, height.toFloat(), pRed)
                    canvas.drawRect(width - shift, 0f, width.toFloat(), height.toFloat(), pCyan)
                }
            }
        }
    }
}

object SnapshotExporter {
    suspend fun captureAndSaveSnapshot(
        context: Context,
        project: VideoProject,
        scene: TimelineScene?,
        localPositionMs: Long
    ): String = withContext(Dispatchers.IO) {
        val (w, h) = SceneBitmapRenderer.computeOutputSize(project.aspectRatio, "1080p")
        val outputBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        if (scene != null) {
            val mediaTimeMs = when (val c = scene.clip) {
                is MediaClip.Video -> {
                    if (scene.isReversed) (c.endTrimMs - localPositionMs).coerceIn(c.startTrimMs, c.endTrimMs)
                    else (c.startTrimMs + localPositionMs).coerceIn(c.startTrimMs, c.endTrimMs)
                }
                is MediaClip.Photo -> 0L
                null -> 0L
            }
            val src = SceneBitmapRenderer.decodeSourceBitmap(
                uriString = scene.mediaUriString ?: scene.clip?.uriString,
                isVideo = scene.isVideo,
                mediaTimeMs = mediaTimeMs
            )
            val progress = (localPositionMs.toFloat() / scene.effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
            SceneBitmapRenderer.renderSceneFrame(canvas, w, h, scene, progress, src)
            src?.recycle()
        } else {
            canvas.drawColor(Color.BLACK)
        }

        val fileName = "VideoStar_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/VideoStar")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                outputBitmap.recycle()
                return@withContext "Pictures/VideoStar/$fileName"
            }
        }

        val fallbackDir = File(context.filesDir, "snapshots").apply { if (!exists()) mkdirs() }
        val fallbackFile = File(fallbackDir, fileName)
        fallbackFile.outputStream().use { out ->
            outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        outputBitmap.recycle()
        fallbackFile.absolutePath
    }
}

object Mp4VideoExporter {

    suspend fun exportProjectToMp4(
        context: Context,
        project: VideoProject,
        onProgress: (Float) -> Unit
    ): String = exportProjectToMp4WithUri(context, project, onProgress).first

    suspend fun exportProjectToMp4WithUri(
        context: Context,
        project: VideoProject,
        onProgress: (Float) -> Unit
    ): Pair<String, Uri?> = withContext(Dispatchers.IO) {
        val scenes = project.scenes
        if (scenes.isEmpty()) error("لا توجد مشاهد في المشروع للتصدير")

        val tempDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val tempMp4 = File(tempDir, "export_${System.currentTimeMillis()}.mp4")

        val canUseMedia3Gl = !Build.FINGERPRINT.contains("robolectric", ignoreCase = true) &&
            scenes.all { !it.isMultiLayerComposite && !it.isReversed && it.colorGrading.isNeutral && !it.mediaUriString.isNullOrBlank() }

        val media3Succeeded = if (canUseMedia3Gl) {
            runCatching {
                exportWithMedia3Transformer(
                    context = context,
                    project = project,
                    outputFile = tempMp4,
                    onProgress = onProgress
                )
            }.getOrDefault(false)
        } else false

        if (!media3Succeeded) {
            exportWithMediaCodecFallback(
                context = context,
                project = project,
                outputFile = tempMp4,
                onProgress = onProgress
            )
        }

        // Publish to MediaStore Movies/VideoStar
        onProgress(0.95f)
        val outName = "VideoStar_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, outName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoStar")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    tempMp4.inputStream().use { inp -> inp.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                tempMp4.delete()
                onProgress(1.0f)
                return@withContext "Movies/VideoStar/$outName" to uri
            }
        }

        onProgress(1.0f)
        tempMp4.absolutePath to Uri.fromFile(tempMp4)
    }

    @OptIn(UnstableApi::class)
    private suspend fun exportWithMedia3Transformer(
        context: Context,
        project: VideoProject,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        val (outW, outH) = SceneBitmapRenderer.computeHardwareOutputSize(project.aspectRatio, project.resolution)
        val fps = project.frameRate.coerceIn(24, 60)

        val videoItems = project.scenes.map { scene ->
            val uriStr = scene.mediaUriString ?: scene.clip?.uriString ?: return@withContext false
            val clip = scene.clip
            val mediaBuilder = MediaItem.Builder().setUri(Uri.parse(uriStr))
            if (clip is MediaClip.Video) {
                mediaBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startTrimMs.coerceAtLeast(0L))
                        .setEndPositionMs(clip.endTrimMs.coerceAtLeast(clip.startTrimMs + 200L))
                        .build()
                )
            } else {
                mediaBuilder.setImageDurationMs(scene.effectiveDurationMs.coerceAtLeast(200L))
            }

            val videoEffects = mutableListOf<Effect>()
            videoEffects += Presentation.createForWidthAndHeight(
                outW,
                outH,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )

            val t = scene.transform
            if (t.scale != 1f || t.rotation != 0f || t.flipH || t.flipV) {
                videoEffects += ScaleAndRotateTransformation.Builder()
                    .setScale(
                        t.scale * (if (t.flipH) -1f else 1f),
                        t.scale * (if (t.flipV) -1f else 1f)
                    )
                    .setRotationDegrees(-t.rotation)
                    .build()
            }

            val durUs = scene.effectiveDurationMs.coerceAtLeast(200L) * 1000L
            scene.resolvedEffectPasses().forEach { pass ->
                videoEffects += ShaderPassGlEffect(pass = pass, sceneDurationUs = durUs)
            }

            EditedMediaItem.Builder(mediaBuilder.build())
                .setDurationUs(durUs)
                .setFrameRate(fps)
                .setEffects(Effects(emptyList(), videoEffects))
                .setRemoveAudio(project.audioConfig.clipVolume <= 0.01f)
                .build()
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        sequences += EditedMediaItemSequence.Builder(videoItems).build()

        val songUriStr = project.audioConfig.songUriString
        if (!songUriStr.isNullOrBlank()) {
            val totalDurUs = project.totalTimelineDurationMs.coerceAtLeast(500L) * 1000L
            val songItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(songUriStr)))
                .setDurationUs(totalDurUs)
                .setRemoveVideo(true)
                .build()
            sequences += EditedMediaItemSequence.Builder(listOf(songItem)).build()
        }

        val composition = Composition.Builder(sequences).build()
        val deferred = CompletableDeferred<Boolean>()
        if (outputFile.exists()) outputFile.delete()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (!deferred.isCompleted) deferred.complete(true)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (!deferred.isCompleted) deferred.complete(false)
                }
            })
            .build()

        transformer.start(composition, outputFile.absolutePath)

        val progressHolder = ProgressHolder()
        while (!deferred.isCompleted) {
            val state = transformer.getProgress(progressHolder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.92f))
            }
            delay(80L)
        }
        deferred.await()
    }

    private fun exportWithMediaCodecFallback(
        context: Context,
        project: VideoProject,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        val scenes = project.scenes
        val (width, height) = SceneBitmapRenderer.computeOutputSize(project.aspectRatio, project.resolution)
        val fps = project.frameRate.coerceIn(24, 60)
        val totalDurationMs = project.totalTimelineDurationMs.coerceAtLeast(500L)
        val totalFrames = ((totalDurationMs / 1000.0) * fps).toInt().coerceIn(15, fps * 180)

        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val targetBitrate = (width * height * 6f * project.compressionQuality.bitrateFactor).toInt().coerceAtLeast(800_000)
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var muxerStarted = false

        var audioExtractor: MediaExtractor? = null
        var audioTrackIndex = -1
        val songUriStr = project.audioConfig.songUriString
        if (!songUriStr.isNullOrBlank()) {
            val songPath = Uri.parse(songUriStr).path
            if (songPath != null && File(songPath).exists()) {
                runCatching {
                    val ext = MediaExtractor()
                    ext.setDataSource(songPath)
                    for (t in 0 until ext.trackCount) {
                        val tf = ext.getTrackFormat(t)
                        val m = tf.getString(MediaFormat.KEY_MIME) ?: ""
                        if (m.startsWith("audio/")) {
                            ext.selectTrack(t)
                            audioTrackIndex = muxer.addTrack(tf)
                            audioExtractor = ext
                            break
                        }
                    }
                }
            }
        }

        val bufferInfo = MediaCodec.BufferInfo()
        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)

        try {
            val sceneWindows = mutableListOf<Triple<Long, Long, TimelineScene>>()
            var cursor = 0L
            for (sc in scenes) {
                val end = cursor + sc.effectiveDurationMs
                sceneWindows.add(Triple(cursor, end, sc))
                cursor = end
            }

            val retrievers = mutableMapOf<String, MediaMetadataRetriever>()
            for (sc in scenes) {
                val uriStr = sc.mediaUriString ?: sc.clip?.uriString
                if (sc.isVideo && !uriStr.isNullOrBlank() && !retrievers.containsKey(uriStr)) {
                    val p = Uri.parse(uriStr).path
                    if (p != null && File(p).exists()) {
                        runCatching {
                            val r = MediaMetadataRetriever()
                            r.setDataSource(p)
                            retrievers[uriStr] = r
                        }
                    }
                }
            }

            for (frameIdx in 0 until totalFrames) {
                val timeMs = (frameIdx * 1000L) / fps
                val window = sceneWindows.firstOrNull { timeMs >= it.first && timeMs < it.second }
                    ?: sceneWindows.last()
                val scene = window.third
                val localMs = (timeMs - window.first).coerceAtLeast(0L)
                val localProgress = (localMs.toFloat() / scene.effectiveDurationMs.toFloat()).coerceIn(0f, 1f)

                val mediaTimeMs = when (val c = scene.clip) {
                    is MediaClip.Video -> {
                        val span = c.trimmedDurationMs.coerceAtLeast(200L)
                        val rel = (localProgress * span).toLong()
                        if (scene.isReversed) (c.endTrimMs - rel).coerceIn(c.startTrimMs, c.endTrimMs)
                        else (c.startTrimMs + rel).coerceIn(c.startTrimMs, c.endTrimMs)
                    }
                    else -> 0L
                }

                val uriStr = scene.mediaUriString ?: scene.clip?.uriString
                val srcBitmap: Bitmap? = if (scene.isVideo && uriStr != null && retrievers.containsKey(uriStr)) {
                    runCatching {
                        retrievers[uriStr]?.getFrameAtTime(mediaTimeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }.getOrNull()
                } else {
                    SceneBitmapRenderer.decodeSourceBitmap(uriStr, scene.isVideo, mediaTimeMs)
                }

                SceneBitmapRenderer.renderSceneFrame(
                    canvas = frameCanvas,
                    width = width,
                    height = height,
                    scene = scene,
                    localProgress = localProgress,
                    sourceBitmap = srcBitmap
                )
                srcBitmap?.recycle()

                val surfaceCanvas = inputSurface.lockCanvas(null)
                try {
                    surfaceCanvas.drawBitmap(frameBitmap, 0f, 0f, null)
                } finally {
                    inputSurface.unlockCanvasAndPost(surfaceCanvas)
                }

                drainEncoder(encoder, muxer, bufferInfo, endOfStream = false, onFormatReady = { outFormat ->
                    if (!muxerStarted) {
                        videoTrackIndex = muxer.addTrack(outFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }, getVideoTrackIndex = { videoTrackIndex }, isMuxerStarted = { muxerStarted })

                onProgress(((frameIdx + 1).toFloat() / totalFrames.toFloat()) * 0.9f)
            }

            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufferInfo, endOfStream = true, onFormatReady = { outFormat ->
                if (!muxerStarted) {
                    videoTrackIndex = muxer.addTrack(outFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }, getVideoTrackIndex = { videoTrackIndex }, isMuxerStarted = { muxerStarted })

            val ext = audioExtractor
            if (ext != null && audioTrackIndex >= 0 && muxerStarted) {
                val audioBuf = ByteBuffer.allocate(128 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                val maxUs = totalDurationMs * 1000L
                while (true) {
                    val size = ext.readSampleData(audioBuf, 0)
                    val sampleTime = ext.sampleTime
                    if (size < 0 || sampleTime > maxUs) break
                    audioInfo.offset = 0
                    audioInfo.size = size
                    audioInfo.presentationTimeUs = sampleTime
                    audioInfo.flags = ext.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, audioBuf, audioInfo)
                    ext.advance()
                }
            }

            retrievers.values.forEach { runCatching { it.release() } }
        } finally {
            frameBitmap.recycle()
            runCatching { audioExtractor?.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onFormatReady: (MediaFormat) -> Unit,
        getVideoTrackIndex: () -> Int,
        isMuxerStarted: () -> Boolean
    ) {
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    onFormatReady(encoder.outputFormat)
                }
                status >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(status)
                    if (encodedData != null && bufferInfo.size > 0 && isMuxerStarted()) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(getVideoTrackIndex(), encodedData, bufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(status, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }
}

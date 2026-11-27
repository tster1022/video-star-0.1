package com.example.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.example.data.MediaImportHelper
import com.example.engine.ae.AeEffectEngine
import com.example.engine.effects.shaders.ShaderUniformEvaluator
import com.example.engine.packs.CustomFontRegistry
import com.example.engine.render.StudioRenderErrorBoundary
import com.example.engine.sam.SamCutoutSegmenter
import com.example.model.AeLayerKind
import com.example.model.EditorLayer
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.MediaClip
import com.example.model.ShaderEffectPass
import com.example.model.TextLayerSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.tan

private const val MAX_AE_LIVE_VIDEO_DECODERS = 3

/**
 * Unified real-time OpenGL ES 3.0 multi-layer compositor for After Effects mode.
 * Composites all visible AE layers bottom-to-top in one shared GPU surface:
 * - Video layers via hardware ExoPlayer OES SurfaceTextures + poster fallback
 * - Photo, Text (with live dynamic text effects), Shape, Element, and Draw layers via GPU 2D textures
 * - Adjustment layers executing all 110+ AE shaders & custom GLSL packs directly onto underlying layers
 * - True GPU blend modes (Normal, Add, Multiply, Screen, Overlay), 3D tilt, masks, borders & drop shadows
 * - Live uBackgroundTex sampling of the real underlying composition for Copy/Magnify Background shaders
 */
@Composable
fun AeCompositeGlStage(
    layers: List<EditorLayer>,
    currentPositionMs: Long,
    isPlaying: Boolean,
    clipVolume: Float,
    speedMultiplier: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val isDisposed = remember { AtomicBoolean(false) }

    var glViewRef by remember { mutableStateOf<AeCompositeGlTextureView?>(null) }
    val layerSurfaces = remember { mutableStateMapOf<String, Surface>() }

    val renderer = remember {
        AeCompositeGlRenderer(
            onLayerSurfaceReady = { layerId, surfaceTexture ->
                if (isDisposed.get()) return@AeCompositeGlRenderer
                val surface = runCatching { Surface(surfaceTexture) }.getOrNull()
                    ?: return@AeCompositeGlRenderer
                mainHandler.post {
                    if (isDisposed.get() || glViewRef?.isAvailable != true) {
                        runCatching { surface.release() }
                    } else {
                        val prev = layerSurfaces.put(layerId, surface)
                        if (prev != null && prev !== surface) {
                            runCatching { prev.release() }
                        }
                    }
                }
            },
            onRequestRender = {
                if (!isDisposed.get()) {
                    glViewRef?.requestRender()
                }
            }
        )
    }

    // Release surfaces on dispose
    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            layerSurfaces.values.forEach { s -> runCatching { s.release() } }
            layerSurfaces.clear()
        }
    }

    // Drive up to MAX_AE_LIVE_VIDEO_DECODERS hardware video players + poster bitmaps for visible video layers
    val visibleVideoLayers = remember(layers) {
        layers.filter { it.isVisible && it.layerKind == AeLayerKind.MEDIA && it.mediaClip is MediaClip.Video }
            .take(MAX_AE_LIVE_VIDEO_DECODERS)
    }

    visibleVideoLayers.forEach { layer ->
        val vidClip = layer.mediaClip as? MediaClip.Video ?: return@forEach
        key(layer.id, vidClip.uriString) {
            val isSingleZeroLayerAtEnd = layers.size == 1 &&
                layer.startTimeMs == 0L &&
                currentPositionMs >= layer.effectiveLayerDurationMs
            val isActive = layer.isActiveAt(currentPositionMs) || isSingleZeroLayerAtEnd
            val localPosMs = ((currentPositionMs - layer.startTimeMs) * layer.speedMultiplier)
                .toLong()
                .coerceIn(0L, layer.effectiveLayerDurationMs)

            AeLayerVideoPlayerDriver(
                context = context,
                layerId = layer.id,
                clip = vidClip,
                surface = layerSurfaces[layer.id],
                localPosMs = localPosMs,
                isPlaying = isPlaying && isActive,
                volume = (clipVolume * layer.audioVolume).coerceIn(0f, 1.5f),
                speed = (speedMultiplier * layer.speedMultiplier).coerceIn(0.25f, 3f),
                isDisposed = isDisposed,
                renderer = renderer,
                onRequestRender = { glViewRef?.requestRender() }
            )
        }
    }

    // Decode Photo bitmaps & additional overflow video posters
    val bitmapTargetLayers = remember(layers, visibleVideoLayers) {
        val liveIds = visibleVideoLayers.map { it.id }.toSet()
        layers.filter { layer ->
            layer.isVisible &&
                layer.layerKind == AeLayerKind.MEDIA &&
                layer.mediaClip != null &&
                layer.id !in liveIds
        }
    }

    bitmapTargetLayers.forEach { layer ->
        val clip = layer.mediaClip ?: return@forEach
        key(layer.id, clip.uriString) {
            LaunchedEffect(layer.id, clip.uriString) {
                val bmp = withContext(Dispatchers.IO) {
                    StudioRenderErrorBoundary.safeEvaluate("ae_photo_decode", null) {
                        val raw = if (clip is MediaClip.Video) {
                            MediaImportHelper.extractVideoFrame(clip.uriString, clip.startTrimMs, context, maxDimPx = 720)
                        } else {
                            MediaImportHelper.decodeSampledBitmap(clip.uriString, context, maxDimPx = 720)
                        }
                        val resolved = raw ?: ProceduralTestGridFactory.createTestGridBitmap(512, 512)
                        normalizeAeBitmapForGl(resolved)
                    }
                }
                if (bmp != null && !isDisposed.get()) {
                    renderer.setLayerBitmap(layer.id, bmp)
                    glViewRef?.requestRender()
                }
            }
        }
    }

    // Load SAM cutout masks for any layers that have active SAM segmentation
    layers.forEach { layer ->
        if (layer.isVisible && layer.samCutout.isActive) {
            key("sam_${layer.id}_${layer.samCutout.maskUriString}") {
                LaunchedEffect(layer.id, layer.samCutout.maskUriString, layer.samCutout.enabled) {
                    val maskBmp = withContext(Dispatchers.IO) {
                        SamCutoutSegmenter.loadMaskBitmap(layer.samCutout.maskUriString)
                    }
                    renderer.setLayerSamMask(layer.id, maskBmp)
                    glViewRef?.requestRender()
                }
            }
        }
    }

    LaunchedEffect(layers, currentPositionMs, isPlaying, speedMultiplier) {
        renderer.updateComposition(layers, currentPositionMs)
        glViewRef?.requestRender()
    }

    AndroidView(
        factory = { ctx ->
            AeCompositeGlTextureView(
                context = ctx,
                renderer = renderer,
                onBeforeSurfaceDestroy = {
                    layerSurfaces.values.forEach { s -> runCatching { s.release() } }
                    layerSurfaces.clear()
                }
            ).apply {
                glViewRef = this
            }
        },
        update = { view ->
            glViewRef = view
            renderer.updateComposition(layers, currentPositionMs)
            view.requestRender()
        },
        modifier = modifier
    )
}

@Composable
private fun AeLayerVideoPlayerDriver(
    context: Context,
    layerId: String,
    clip: MediaClip.Video,
    surface: Surface?,
    localPosMs: Long,
    isPlaying: Boolean,
    volume: Float,
    speed: Float,
    isDisposed: AtomicBoolean,
    renderer: AeCompositeGlRenderer,
    onRequestRender: () -> Unit
) {
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestLocalPosMs by rememberUpdatedState(localPosMs)
    var discoveredDurationMs by remember(clip.uriString) { mutableStateOf(0L) }
    var hasPlayerError by remember(clip.uriString) { mutableStateOf(false) }

    val exoPlayer = remember(layerId, clip.uriString) {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            ExoPlayer.Builder(context)
                .setAudioAttributes(attrs, false)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(clip.uriString)))
                    repeatMode = Player.REPEAT_MODE_OFF
                    prepare()
                }
        }.getOrNull()
    }

    // Extract instant poster bitmap while hardware video decoder prepares
    LaunchedEffect(layerId, clip.uriString) {
        hasPlayerError = false
        discoveredDurationMs = 0L
        renderer.resetLayerVideoState(layerId)
        val bmp = withContext(Dispatchers.IO) {
            StudioRenderErrorBoundary.safeEvaluate("ae_vid_poster", null) {
                val raw = MediaImportHelper.extractVideoFrame(
                    clip.uriString,
                    clip.startTrimMs,
                    context,
                    maxDimPx = 720
                )
                val resolved = raw ?: ProceduralTestGridFactory.createTestGridBitmap(512, 512)
                normalizeAeBitmapForGl(resolved)
            }
        }
        if (bmp != null && !isDisposed.get()) {
            renderer.setLayerBitmap(layerId, bmp)
            onRequestRender()
        }
    }

    LaunchedEffect(exoPlayer, surface) {
        val player = exoPlayer ?: return@LaunchedEffect
        runCatching {
            if (surface != null && surface.isValid && !isDisposed.get()) {
                player.setVideoSurface(surface)
                val safeStart = clip.startTrimMs.coerceAtLeast(0L)
                val safeEnd = clip.endTrimMs.takeIf { it > safeStart + 120L }
                    ?: discoveredDurationMs.takeIf { it > safeStart + 120L }
                    ?: Long.MAX_VALUE
                player.seekTo((safeStart + latestLocalPosMs).coerceIn(safeStart, safeEnd))
            } else {
                player.setVideoSurface(null)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0 && !isDisposed.get()) {
                    renderer.setLayerVideoDimensions(layerId, videoSize.width, videoSize.height)
                    onRequestRender()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val player = exoPlayer ?: return
                if (isDisposed.get()) return
                if (playbackState == Player.STATE_READY) {
                    val dur = runCatching { player.duration }.getOrDefault(C.TIME_UNSET)
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        discoveredDurationMs = dur
                    }
                    if (!latestIsPlaying) {
                        val safeStart = clip.startTrimMs.coerceAtLeast(0L)
                        val safeEnd = clip.endTrimMs.takeIf { it > safeStart + 120L }
                            ?: dur.takeIf { it != C.TIME_UNSET && it > safeStart + 120L }
                            ?: Long.MAX_VALUE
                        val targetAbs = (safeStart + latestLocalPosMs).coerceIn(safeStart, safeEnd)
                        runCatching {
                            if (abs(player.currentPosition - targetAbs) > 45L) {
                                player.seekTo(targetAbs)
                            }
                        }
                        onRequestRender()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasPlayerError = true
                renderer.resetLayerVideoState(layerId)
                onRequestRender()
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            runCatching { exoPlayer?.removeListener(listener) }
            runCatching { exoPlayer?.playWhenReady = false }
            runCatching { exoPlayer?.setVideoSurface(null) }
            runCatching { exoPlayer?.release() }
        }
    }

    // Playback / speed / volume / loop wrap sync
    LaunchedEffect(exoPlayer, isPlaying, volume, speed, hasPlayerError, clip.uriString) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (hasPlayerError) {
            runCatching { player.playWhenReady = false }
            return@LaunchedEffect
        }
        runCatching {
            player.volume = volume.coerceIn(0f, 1f)
            player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3f))
            if (isPlaying) {
                val safeStart = clip.startTrimMs.coerceAtLeast(0L)
                val safeEnd = clip.endTrimMs.takeIf { it > safeStart + 120L }
                    ?: discoveredDurationMs.takeIf { it > safeStart + 120L }
                    ?: Long.MAX_VALUE
                player.setSeekParameters(SeekParameters.EXACT)
                val targetAbs = (safeStart + latestLocalPosMs).coerceIn(safeStart, safeEnd)
                if (player.playbackState == Player.STATE_ENDED || abs(player.currentPosition - targetAbs) > 250L) {
                    player.seekTo(targetAbs)
                }
                player.playWhenReady = true
                while (isActive && !isDisposed.get()) {
                    delay(75L)
                    val desired = (safeStart + latestLocalPosMs).coerceIn(safeStart, safeEnd)
                    val cur = runCatching { player.currentPosition }.getOrDefault(desired)
                    if (player.playbackState == Player.STATE_ENDED || abs(cur - desired) > 350L) {
                        runCatching {
                            player.seekTo(desired)
                            player.playWhenReady = true
                        }
                    }
                }
            } else {
                player.playWhenReady = false
            }
        }
    }

    // Paused scrub sync
    val pausedSeekMs = if (isPlaying) Long.MIN_VALUE else localPosMs
    LaunchedEffect(exoPlayer, pausedSeekMs, hasPlayerError, clip.uriString) {
        if (pausedSeekMs == Long.MIN_VALUE || hasPlayerError) return@LaunchedEffect
        val player = exoPlayer ?: return@LaunchedEffect
        val safeStart = clip.startTrimMs.coerceAtLeast(0L)
        val safeEnd = clip.endTrimMs.takeIf { it > safeStart + 120L }
            ?: discoveredDurationMs.takeIf { it > safeStart + 120L }
            ?: Long.MAX_VALUE
        val targetAbs = (safeStart + pausedSeekMs).coerceIn(safeStart, safeEnd)
        delay(28L)
        runCatching {
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            if (abs(player.currentPosition - targetAbs) > 45L) {
                player.seekTo(targetAbs)
            }
        }
        delay(120L)
        runCatching {
            player.setSeekParameters(SeekParameters.EXACT)
            if (abs(player.currentPosition - targetAbs) > 35L) {
                player.seekTo(targetAbs)
            }
        }
    }
}

private class AeCompositeGlRenderer(
    private val onLayerSurfaceReady: (String, SurfaceTexture) -> Unit,
    private val onRequestRender: () -> Unit
) {
    private data class CompositionSnapshot(
        val layers: List<EditorLayer>,
        val positionMs: Long
    )

    private class VideoEntry(
        val oesTexId: Int,
        val surfaceTexture: SurfaceTexture
    ) {
        @Volatile var frameAvailable: Boolean = false
        @Volatile var hasReceivedFrame: Boolean = false
        @Volatile var videoWidth: Int = 0
        @Volatile var videoHeight: Int = 0
        val oesMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    }

    @Volatile
    private var snapshot = CompositionSnapshot(emptyList(), 0L)

    @Volatile
    private var isReleased = false

    private var surfaceWidth = 720
    private var surfaceHeight = 720

    private val bitmapLock = Any()
    private val pendingBitmaps = HashMap<String, Bitmap>()
    private val pendingMasks = HashMap<String, Bitmap?>()

    private val layerBitmapTexIds = HashMap<String, Int>()
    private val layerBitmapDims = HashMap<String, Pair<Int, Int>>()
    private val layerMaskTexIds = HashMap<String, Int>()
    private val layerTextTexIds = HashMap<String, Int>()
    private val layerTextKeys = HashMap<String, String>()
    private val videoEntries = ConcurrentHashMap<String, VideoEntry>()

    private var reusableCanvasBitmap: Bitmap? = null

    private var oesProgram = 0
    private var blitProgram = 0
    private var screenBlitProgram = 0
    private var samMaskProgram = 0
    private var shapeMaskProgram = 0
    private var compositeBlendProgram = 0
    private val shaderProgramCache = HashMap<String, Int>()

    // FBOs: 0..1 pingFbo (per-layer effects), 2..3 compFbo (composition accumulation), 4 historyFbo
    private val fboIds = IntArray(5)
    private val fboTexIds = IntArray(5)
    private var fboWidth = 0
    private var fboHeight = 0

    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val bitmapFlipYMatrix = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
        Matrix.translateM(this, 0, 0f, 1f, 0f)
        Matrix.scaleM(this, 0, 1f, -1f, 1f)
    }
    private val sourceScaleMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val mvpMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val projMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val modelMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private val vertexData: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_VERTICES)
            position(0)
        }

    fun updateComposition(layers: List<EditorLayer>, positionMs: Long) {
        snapshot = CompositionSnapshot(layers, positionMs)
    }

    fun setLayerBitmap(layerId: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        synchronized(bitmapLock) {
            pendingBitmaps[layerId] = bitmap
        }
    }

    fun setLayerSamMask(layerId: String, maskBitmap: Bitmap?) {
        synchronized(bitmapLock) {
            pendingMasks[layerId] = maskBitmap
        }
    }

    fun setLayerVideoDimensions(layerId: String, w: Int, h: Int) {
        videoEntries[layerId]?.let {
            if (w > 0 && h > 0) {
                it.videoWidth = w
                it.videoHeight = h
            }
        }
    }

    fun resetLayerVideoState(layerId: String) {
        videoEntries[layerId]?.let {
            it.hasReceivedFrame = false
            it.frameAvailable = false
            it.videoWidth = 0
            it.videoHeight = 0
        }
    }

    fun onContextCreated() {
        isReleased = false
        shaderProgramCache.clear()
        layerBitmapTexIds.clear()
        layerBitmapDims.clear()
        layerMaskTexIds.clear()
        layerTextTexIds.clear()
        layerTextKeys.clear()
        videoEntries.clear()

        oesProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.PASSTHROUGH_OES_FRAGMENT_SHADER)
        if (oesProgram == 0) {
            oesProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_COMPAT, ShaderLibrary.PASSTHROUGH_OES_FRAGMENT_SHADER_COMPAT)
        }
        blitProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.PASSTHROUGH_2D_FRAGMENT_SHADER)
        if (blitProgram == 0) {
            blitProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_COMPAT, ShaderLibrary.PASSTHROUGH_2D_FRAGMENT_SHADER_COMPAT)
        }
        screenBlitProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.PASSTHROUGH_PREMULT_2D_FRAGMENT_SHADER)
        if (screenBlitProgram == 0) screenBlitProgram = blitProgram
        samMaskProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.SAM_MASK_APPLY_FRAGMENT_SHADER)
        shapeMaskProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.MASK_SHAPE_FRAGMENT_SHADER)
        compositeBlendProgram = buildProgram(ShaderLibrary.COMPOSITE_VERTEX_SHADER_3D, ShaderLibrary.COMPOSITE_BLEND_FRAGMENT_SHADER)
    }

    fun onViewportChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        allocateFbos(surfaceWidth, surfaceHeight)
    }

    fun onDraw() {
        if (isReleased || blitProgram == 0) return
        try {
            drawCompositeInternal()
        } catch (t: Throwable) {
            StudioRenderErrorBoundary.recordSuppressedError("ae_gl_draw", t)
        }
    }

    private fun drawCompositeInternal() {
        val snap = snapshot
        val allLayers = snap.layers
        val layersById = allLayers.associateBy { it.id }

        val (targetW, targetH) = computeFboSize(surfaceWidth, surfaceHeight)
        if (fboWidth != targetW || fboHeight != targetH) {
            allocateFbos(surfaceWidth, surfaceHeight)
        }

        // Flush pending bitmap & mask uploads
        val bitmapsToUpload: List<Pair<String, Bitmap>>
        val masksToUpload: List<Pair<String, Bitmap?>>
        synchronized(bitmapLock) {
            bitmapsToUpload = pendingBitmaps.entries.map { it.key to it.value }
            pendingBitmaps.clear()
            masksToUpload = pendingMasks.entries.map { it.key to it.value }
            pendingMasks.clear()
        }
        bitmapsToUpload.forEach { (id, bmp) ->
            if (!bmp.isRecycled) uploadLayerBitmapTex(id, bmp)
        }
        masksToUpload.forEach { (id, maskBmp) ->
            if (maskBmp != null && !maskBmp.isRecycled) {
                uploadLayerMaskTex(id, maskBmp)
            } else {
                layerMaskTexIds.remove(id)?.let { old -> GLES30.glDeleteTextures(1, intArrayOf(old), 0) }
            }
        }

        // Lazily initialize OES SurfaceTextures for visible video layers + pull pending frames
        val activeVideoLayerIds = allLayers
            .filter { it.isVisible && it.layerKind == AeLayerKind.MEDIA && it.mediaClip is MediaClip.Video }
            .take(MAX_AE_LIVE_VIDEO_DECODERS)
            .map { it.id }
            .toSet()

        activeVideoLayerIds.forEach { vidId ->
            val entry = videoEntries.getOrPut(vidId) { createOesEntry(vidId) }
            if (entry.frameAvailable) {
                runCatching {
                    entry.surfaceTexture.updateTexImage()
                    entry.surfaceTexture.getTransformMatrix(entry.oesMatrix)
                }
                entry.frameAvailable = false
            }
        }

        // Clear compFbo[0] to opaque dark composition background
        var compCurIdx = 2
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[compCurIdx])
        GLES30.glViewport(0, 0, fboWidth, fboHeight)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        // Render active visible layers bottom-to-top
        val bottomToTop = allLayers.filter { it.isVisible }.asReversed()
        for (layer in bottomToTop) {
            if (layer.layerKind == AeLayerKind.AUDIO || layer.layerKind == AeLayerKind.NULL) continue
            val isSingleZeroLayerAtEnd = allLayers.size == 1 &&
                layer.startTimeMs == 0L &&
                snap.positionMs >= layer.effectiveLayerDurationMs
            val isCurrentlyActive = layer.isActiveAt(snap.positionMs) || isSingleZeroLayerAtEnd
            if (!isCurrentlyActive) continue

            val layerDurMs = layer.effectiveLayerDurationMs.coerceAtLeast(300L)
            val rawLocalMs = ((snap.positionMs - layer.startTimeMs) * layer.speedMultiplier).toLong().coerceIn(0L, layerDurMs)
            val localProgress = (rawLocalMs.toFloat() / layerDurMs.toFloat()).coerceIn(0f, 1f)
            val durationSec = (layerDurMs / 1000f).coerceAtLeast(0.2f)

            val evalTransform = AeEffectEngine.composeAeLayerTransformAt(layer, snap.positionMs)
            if (evalTransform.opacity <= 0.005f) continue

            val passes = layer.effectPasses.filter { it.isEnabled && it.effectId != "none" }
            val quantPass = passes.firstOrNull { it.effectId == "am_time_quantization" }
            val (evalProgress, evalTimeSec) = if (quantPass != null) {
                ShaderUniformEvaluator.computeQuantizedProgress(quantPass, localProgress, durationSec)
            } else {
                localProgress to (localProgress * durationSec)
            }

            if (layer.layerKind == AeLayerKind.ADJUSTMENT) {
                // Copy current compFbo into ping[0], run Adjustment shader stack, and composite back
                copyFbo(fboTexIds[compCurIdx], fboIds[0])
                val outPingIdx = runShaderPasses(
                    passes = passes,
                    startPingIdx = 0,
                    bgTexId = fboTexIds[compCurIdx],
                    maskTexId = layerMaskTexIds[layer.id],
                    hasSamMask = layer.samCutout.isActive && layerMaskTexIds.containsKey(layer.id),
                    localProgress = localProgress,
                    evalProgress = evalProgress,
                    evalTimeSec = evalTimeSec,
                    durationSec = durationSec
                )
                val maskedPingIdx = applyLayerMaskIfNeeded(layer, outPingIdx)
                compCurIdx = compositeIntoCompFbo(
                    layer = layer,
                    parentLayer = layer.parentLayerId?.let { layersById[it] },
                    evalTransform = evalTransform,
                    layerTexId = fboTexIds[maskedPingIdx],
                    compCurIdx = compCurIdx,
                    applyTransformMatrix = false
                )
                continue
            }

            // Blit layer visual source into pingFbo[0] (transparent outside bounds)
            val blitted = blitLayerSourceToPing0(layer, evalProgress, durationSec)
            if (!blitted) continue

            var pingCur = 0
            // Optional SAM cutout matte
            val samTex = layerMaskTexIds[layer.id]
            if (layer.samCutout.isActive && samTex != null && samMaskProgram != 0) {
                val nextPing = 1 - pingCur
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[nextPing])
                GLES30.glViewport(0, 0, fboWidth, fboHeight)
                GLES30.glClearColor(0f, 0f, 0f, 0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                GLES30.glUseProgram(samMaskProgram)
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(samMaskProgram, "uTexMatrix"), 1, false, identityMatrix, 0)
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(samMaskProgram, "uSourceTexMatrix"), 1, false, identityMatrix, 0)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[pingCur])
                GLES30.glUniform1i(GLES30.glGetUniformLocation(samMaskProgram, "uTexture"), 0)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, samTex)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(samMaskProgram, "uMaskTex"), 1)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(samMaskProgram, "uFeather"), layer.samCutout.feather.coerceIn(0f, 1f))
                GLES30.glUniform1i(GLES30.glGetUniformLocation(samMaskProgram, "uInvertMask"), if (layer.samCutout.invert) 1 else 0)
                drawFullscreenQuad()
                pingCur = nextPing
            }

            // Apply shape/linear mask BEFORE shader chain or AFTER shader chain
            pingCur = applyLayerMaskIfNeeded(layer, pingCur)

            // Execute all enabled shader passes on this layer, passing REAL underlying compFbo as uBackgroundTex
            pingCur = runShaderPasses(
                passes = passes,
                startPingIdx = pingCur,
                bgTexId = fboTexIds[compCurIdx],
                maskTexId = samTex,
                hasSamMask = layer.samCutout.isActive && samTex != null,
                localProgress = localProgress,
                evalProgress = evalProgress,
                evalTimeSec = evalTimeSec,
                durationSec = durationSec
            )

            // Composite layer into compFbo with Transform + 3D + Blend Mode + Border + Shadow
            compCurIdx = compositeIntoCompFbo(
                layer = layer,
                parentLayer = layer.parentLayerId?.let { layersById[it] },
                evalTransform = evalTransform,
                layerTexId = fboTexIds[pingCur],
                compCurIdx = compCurIdx,
                applyTransformMatrix = true
            )
        }

        // Snapshot into global history buffer for Echo / Luma Stamper
        copyFbo(fboTexIds[compCurIdx], fboIds[4])

        // Final blit to screen framebuffer 0 (premultiplied alpha for TextureView)
        val outProg = screenBlitProgram.takeIf { it != 0 } ?: blitProgram
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(outProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(outProg, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(outProg, "uSourceTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(outProg, "uBgColor"), 0f, 0f, 0f, 1f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[compCurIdx])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(outProg, "uTexture"), 0)
        drawFullscreenQuad()
    }

    private fun blitLayerSourceToPing0(
        layer: EditorLayer,
        evalProgress: Float,
        durationSec: Float
    ): Boolean {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[0])
        GLES30.glViewport(0, 0, fboWidth, fboHeight)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val passes = layer.effectPasses
        val hasTextFx = passes.any {
            it.isEnabled && it.effectId in setOf("am_text_count", "am_text_progress", "am_text_randomizer", "am_text_timecode")
        }

        if (layer.layerKind == AeLayerKind.TEXT || (hasTextFx && layer.layerKind != AeLayerKind.MEDIA)) {
            val formatted = ShaderUniformEvaluator.formatTextForEffects(
                baseText = layer.effectiveText.ifBlank { "Sample Text" },
                passes = passes,
                progress = evalProgress,
                durationSec = durationSec
            )
            val texId = ensureDynamicTextTexture(layer.id, formatted, layer.textSpec)
            buildAspectMatrix(fboWidth, fboHeight, true, sourceScaleMatrix)
            draw2dSource(texId, sourceScaleMatrix, bitmapFlipYMatrix)
            return true
        }

        if (layer.layerKind in setOf(AeLayerKind.SHAPE, AeLayerKind.ELEMENT, AeLayerKind.DRAW)) {
            val texId = ensureVectorGraphicTexture(layer)
            buildAspectMatrix(fboWidth, fboHeight, true, sourceScaleMatrix)
            draw2dSource(texId, sourceScaleMatrix, bitmapFlipYMatrix)
            return true
        }

        // MEDIA layer
        val videoEntry = if (layer.mediaClip is MediaClip.Video) videoEntries[layer.id] else null
        val useLiveOes = videoEntry != null && videoEntry.hasReceivedFrame && oesProgram != 0
        if (useLiveOes && videoEntry != null) {
            val srcW = videoEntry.videoWidth.takeIf { it > 0 }
                ?: layerBitmapDims[layer.id]?.first
                ?: fboWidth
            val srcH = videoEntry.videoHeight.takeIf { it > 0 }
                ?: layerBitmapDims[layer.id]?.second
                ?: fboHeight
            val fit = layer.mediaClip?.isFitMode ?: false
            buildAspectMatrix(srcW, srcH, fit, sourceScaleMatrix)
            GLES30.glUseProgram(oesProgram)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(oesProgram, "uTexMatrix"), 1, false, sourceScaleMatrix, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(oesProgram, "uSourceTexMatrix"), 1, false, videoEntry.oesMatrix, 0)
            GLES30.glUniform4f(GLES30.glGetUniformLocation(oesProgram, "uBgColor"), 0f, 0f, 0f, 0f)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoEntry.oesTexId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(oesProgram, "uOesTexture"), 0)
            drawFullscreenQuad()
            return true
        }

        val bmpTexId = layerBitmapTexIds[layer.id]
        if (bmpTexId != null) {
            val dims = layerBitmapDims[layer.id] ?: (fboWidth to fboHeight)
            val fit = layer.mediaClip?.isFitMode ?: false
            buildAspectMatrix(dims.first, dims.second, fit, sourceScaleMatrix)
            draw2dSource(bmpTexId, sourceScaleMatrix, bitmapFlipYMatrix)
            return true
        }
        return false
    }

    private fun draw2dSource(texId: Int, texMatrix: FloatArray, srcMatrix: FloatArray) {
        GLES30.glUseProgram(blitProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uTexMatrix"), 1, false, texMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uSourceTexMatrix"), 1, false, srcMatrix, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(blitProgram, "uBgColor"), 0f, 0f, 0f, 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(blitProgram, "uTexture"), 0)
        drawFullscreenQuad()
    }

    private fun applyLayerMaskIfNeeded(layer: EditorLayer, pingCur: Int): Int {
        if (layer.maskMode == MaskMode.NONE || shapeMaskProgram == 0) return pingCur
        if (layer.maskMode == MaskMode.SHAPE && layer.maskShape == MaskShape.NONE) return pingCur
        val nextPing = 1 - pingCur
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[nextPing])
        GLES30.glViewport(0, 0, fboWidth, fboHeight)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val maskModeIdx = when (layer.maskMode) {
            MaskMode.NONE -> 0
            MaskMode.SHAPE -> 1
            MaskMode.LINEAR -> 2
        }
        val maskShapeIdx = when (layer.maskShape) {
            MaskShape.NONE -> 0
            MaskShape.CIRCLE -> 1
            MaskShape.ROUNDED_SQUARE -> 2
            MaskShape.HEART -> 3
            MaskShape.STAR -> 4
            MaskShape.TRIANGLE -> 5
            MaskShape.DIAMOND -> 6
        }
        GLES30.glUseProgram(shapeMaskProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shapeMaskProgram, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shapeMaskProgram, "uSourceTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[pingCur])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shapeMaskProgram, "uTexture"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shapeMaskProgram, "uMaskMode"), maskModeIdx)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shapeMaskProgram, "uMaskShape"), maskShapeIdx)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shapeMaskProgram, "uFeather"), layer.maskFeather.coerceIn(0f, 1f))
        GLES30.glUniform1i(GLES30.glGetUniformLocation(shapeMaskProgram, "uInvertMask"), if (layer.maskInvert) 1 else 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shapeMaskProgram, "uAngleRad"), Math.toRadians(layer.maskLinearAngle.toDouble()).toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(shapeMaskProgram, "uAspect"), fboWidth.toFloat() / fboHeight.coerceAtLeast(1).toFloat())
        drawFullscreenQuad()
        return nextPing
    }

    private fun runShaderPasses(
        passes: List<ShaderEffectPass>,
        startPingIdx: Int,
        bgTexId: Int,
        maskTexId: Int?,
        hasSamMask: Boolean,
        localProgress: Float,
        evalProgress: Float,
        evalTimeSec: Float,
        durationSec: Float
    ): Int {
        var currentInputIdx = startPingIdx
        for (pass in passes) {
            if (!pass.isEnabled || pass.effectId == "none") continue
            try {
                val evaluated = ShaderUniformEvaluator.evaluate(
                    pass = pass,
                    progress = localProgress,
                    sceneDurationSec = durationSec,
                    quantizedProgressOverride = evalProgress,
                    quantizedTimeOverrideSec = evalTimeSec
                )
                if (evaluated.intensity <= 0.005f) continue
                val prog = getOrCompileShader(pass.effectId)
                if (prog == 0) continue

                val nextTargetIdx = 1 - currentInputIdx
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[nextTargetIdx])
                GLES30.glViewport(0, 0, fboWidth, fboHeight)
                GLES30.glClearColor(0f, 0f, 0f, 0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                GLES30.glUseProgram(prog)
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(prog, "uTexMatrix"), 1, false, identityMatrix, 0)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[currentInputIdx])
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uTexture"), 0)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bgTexId)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uBackgroundTex"), 1)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[4])
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uHistoryTex"), 2)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (hasSamMask && maskTexId != null) maskTexId else fboTexIds[currentInputIdx])
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uMaskTex"), 3)

                GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, "uTexSize"), fboWidth.toFloat(), fboHeight.toFloat())
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uTime"), evaluated.effectiveTimeSec)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uDuration"), durationSec)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uIntensity"), evaluated.intensity)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uProgress"), evaluated.effectiveProgress)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uParam1"), evaluated.legacyParam1)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uParam2"), evaluated.legacyParam2)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uParam3"), evaluated.legacyParam3)

                val paramsLoc = GLES30.glGetUniformLocation(prog, "uParams")
                if (paramsLoc >= 0) GLES30.glUniform1fv(paramsLoc, 12, evaluated.params, 0)
                val colorsLoc = GLES30.glGetUniformLocation(prog, "uColors")
                if (colorsLoc >= 0) GLES30.glUniform4fv(colorsLoc, 3, evaluated.colors, 0)
                val modesLoc = GLES30.glGetUniformLocation(prog, "uModes")
                if (modesLoc >= 0) GLES30.glUniform1iv(modesLoc, 4, evaluated.modes, 0)

                GLES30.glUniform4f(GLES30.glGetUniformLocation(prog, "uTintColor"), 1f, 0.16f, 0.52f, 1f)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uBlendMode"), pass.blendMode.glIndex)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uHasMask"), if (hasSamMask) 1 else 0)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uHasBg"), 1)

                drawFullscreenQuad()
                currentInputIdx = nextTargetIdx
            } catch (t: Throwable) {
                StudioRenderErrorBoundary.recordSuppressedError("ae_gl_pass_${pass.effectId}", t)
            }
        }
        return currentInputIdx
    }

    private fun compositeIntoCompFbo(
        layer: EditorLayer,
        parentLayer: EditorLayer?,
        evalTransform: com.example.engine.ae.AeEvaluatedLayerTransform,
        layerTexId: Int,
        compCurIdx: Int,
        applyTransformMatrix: Boolean
    ): Int {
        if (compositeBlendProgram == 0) return compCurIdx
        val nextCompIdx = if (compCurIdx == 2) 3 else 2

        // Step A: Preserve existing composition pixels across the full target FBO
        copyFbo(fboTexIds[compCurIdx], fboIds[nextCompIdx])

        // Step B: Blend transformed/3D layer onto nextCompIdx reading base from compCurIdx
        if (applyTransformMatrix) {
            buildLayerMvpMatrix(layer, parentLayer, evalTransform, mvpMatrix)
        } else {
            Matrix.setIdentityM(mvpMatrix, 0)
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[nextCompIdx])
        GLES30.glViewport(0, 0, fboWidth, fboHeight)
        GLES30.glUseProgram(compositeBlendProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(compositeBlendProgram, "uMvpMatrix"), 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(compositeBlendProgram, "uTexMatrix"), 1, false, identityMatrix, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, layerTexId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeBlendProgram, "uTexture"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[compCurIdx])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeBlendProgram, "uBaseTex"), 1)

        GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeBlendProgram, "uOpacity"), evalTransform.opacity.coerceIn(0f, 1f))
        GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeBlendProgram, "uBlendMode"), layer.blendMode.glIndex)

        val borderUv = if (applyTransformMatrix && layer.borderWidthDp > 0.2f) {
            (layer.borderWidthDp / 240f).coerceIn(0.002f, 0.08f)
        } else 0f
        val bc = layer.borderColorArgb.toInt()
        GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeBlendProgram, "uBorderWidthUv"), borderUv)
        GLES30.glUniform4f(
            GLES30.glGetUniformLocation(compositeBlendProgram, "uBorderColor"),
            AndroidColor.red(bc) / 255f,
            AndroidColor.green(bc) / 255f,
            AndroidColor.blue(bc) / 255f,
            AndroidColor.alpha(bc) / 255f
        )
        val shadowOffY = if (applyTransformMatrix && layer.shadowOpacity > 0.01f) {
            -(layer.shadowOffsetYDp / 280f).coerceIn(-0.08f, 0.08f)
        } else 0f
        GLES30.glUniform2f(GLES30.glGetUniformLocation(compositeBlendProgram, "uShadowOffsetUv"), 0f, shadowOffY)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(compositeBlendProgram, "uShadowOpacity"),
            if (applyTransformMatrix) layer.shadowOpacity.coerceIn(0f, 1f) else 0f
        )

        drawFullscreenQuad()
        return nextCompIdx
    }

    private fun copyFbo(srcTexId: Int, dstFboId: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dstFboId)
        GLES30.glViewport(0, 0, fboWidth, fboHeight)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(blitProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uSourceTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(blitProgram, "uBgColor"), 0f, 0f, 0f, 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTexId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(blitProgram, "uTexture"), 0)
        drawFullscreenQuad()
    }

    private fun buildAspectMatrix(srcW: Int, srcH: Int, isFitMode: Boolean, out: FloatArray) {
        Matrix.setIdentityM(out, 0)
        if (srcW <= 0 || srcH <= 0 || fboWidth <= 0 || fboHeight <= 0) return
        val srcAspect = srcW.toFloat() / srcH.toFloat()
        val dstAspect = fboWidth.toFloat() / fboHeight.toFloat()
        var ax = 1f
        var ay = 1f
        if (abs(srcAspect - dstAspect) > 0.01f) {
            if (isFitMode) {
                if (srcAspect > dstAspect) ay = srcAspect / dstAspect
                else ax = dstAspect / srcAspect
            } else {
                if (srcAspect > dstAspect) ax = dstAspect / srcAspect
                else ay = srcAspect / dstAspect
            }
        }
        Matrix.translateM(out, 0, 0.5f, 0.5f, 0f)
        Matrix.scaleM(out, 0, ax, ay, 1f)
        Matrix.translateM(out, 0, -0.5f, -0.5f, 0f)
    }

    private fun buildLayerMvpMatrix(
        layer: EditorLayer,
        parent: EditorLayer?,
        eval: com.example.engine.ae.AeEvaluatedLayerTransform,
        out: FloatArray
    ) {
        val w = surfaceWidth.coerceAtLeast(1).toFloat()
        val h = surfaceHeight.coerceAtLeast(1).toFloat()
        val aspect = w / h

        var totalOffX = eval.offsetX
        var totalOffY = eval.offsetY
        var totalScale = eval.scale
        var totalRotZ = eval.rotation
        if (parent != null) {
            val parentHelper = layer.effectPasses.firstOrNull { it.isEnabled && it.effectId == "am_parenting_helper" }
            val pEval = AeEffectEngine.composeAeLayerTransformAt(parent, snapshot.positionMs)
            val fPos = parentHelper?.evaluateParamAt("follow_pos", 0f, 1f, 0f, 2f) ?: 1f
            val fScale = parentHelper?.evaluateParamAt("follow_scale", 0f, 1f, 0f, 2f) ?: 1f
            val fRot = parentHelper?.evaluateParamAt("follow_rot", 0f, 1f, 0f, 2f) ?: 1f
            totalOffX += pEval.offsetX * fPos
            totalOffY += pEval.offsetY * fPos
            totalScale *= (1f + (pEval.scale - 1f) * fScale)
            totalRotZ += pEval.rotation * fRot
        }

        val fov = if (layer.is3DEnabled) layer.perspectiveFov.coerceIn(20f, 110f) else 45f
        val zCam = 1f / tan(Math.toRadians((fov / 2f).toDouble())).toFloat()
        Matrix.perspectiveM(projMatrix, 0, fov, aspect, 0.05f, 100f)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -zCam)

        val worldOffX = (totalOffX / h) * 2f
        val worldOffY = -(totalOffY / h) * 2f
        Matrix.translateM(modelMatrix, 0, worldOffX, worldOffY, 0f)

        val pivX = (layer.anchorX - 0.5f) * 2f * aspect
        val pivY = -(layer.anchorY - 0.5f) * 2f
        if (abs(pivX) > 0.001f || abs(pivY) > 0.001f) {
            Matrix.translateM(modelMatrix, 0, pivX, pivY, 0f)
        }
        if (abs(totalRotZ) > 0.01f) {
            Matrix.rotateM(modelMatrix, 0, -totalRotZ, 0f, 0f, 1f)
        }
        if (layer.is3DEnabled || abs(layer.rotationX) > 0.05f || abs(layer.rotationY) > 0.05f) {
            if (abs(layer.rotationY) > 0.01f) Matrix.rotateM(modelMatrix, 0, layer.rotationY, 0f, 1f, 0f)
            if (abs(layer.rotationX) > 0.01f) Matrix.rotateM(modelMatrix, 0, layer.rotationX, 1f, 0f, 0f)
        }
        val sx = totalScale.coerceIn(0.05f, 8f) * eval.scaleXSign
        val sy = totalScale.coerceIn(0.05f, 8f) * eval.scaleYSign
        Matrix.scaleM(modelMatrix, 0, sx, sy, 1f)
        if (abs(pivX) > 0.001f || abs(pivY) > 0.001f) {
            Matrix.translateM(modelMatrix, 0, -pivX, -pivY, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, aspect, 1f, 1f)

        Matrix.multiplyMM(out, 0, projMatrix, 0, modelMatrix, 0)
    }

    private fun ensureDynamicTextTexture(layerId: String, text: String, spec: TextLayerSpec): Int {
        val w = fboWidth.coerceIn(256, 960)
        val h = fboHeight.coerceIn(256, 960)
        val key = "txt_${text}_${spec.textColorArgb}_${spec.strokeColorArgb}_${spec.strokeWidthPx}_${spec.fontSizeSp}_${spec.fontStyleId}_${w}x${h}"
        val texId = layerTextTexIds.getOrPut(layerId) { create2dTexture() }
        if (layerTextKeys[layerId] == key) return texId

        val bmp = acquireReusableCanvasBitmap(w, h)
        bmp.eraseColor(AndroidColor.TRANSPARENT)
        val canvas = Canvas(bmp)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.textColorArgb.toInt()
            textSize = (spec.fontSizeSp * (w / 240f)).coerceIn(26f, 150f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            CustomFontRegistry.resolveAndroidTypeface(spec.fontStyleId)?.let { typeface = it }
            setShadowLayer(spec.shadowRadius.coerceAtLeast(4f), 0f, 4f, AndroidColor.argb(190, 0, 0, 0))
        }
        val fm = fillPaint.fontMetrics
        val baseline = h / 2f - (fm.ascent + fm.descent) / 2f
        if (spec.strokeWidthPx > 0.5f) {
            val strokePaint = Paint(fillPaint).apply {
                style = Paint.Style.STROKE
                strokeWidth = (spec.strokeWidthPx * 2f).coerceIn(1f, 20f)
                color = spec.strokeColorArgb.toInt()
                clearShadowLayer()
            }
            canvas.drawText(text, w / 2f, baseline, strokePaint)
        }
        canvas.drawText(text, w / 2f, baseline, fillPaint)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        layerTextKeys[layerId] = key
        return texId
    }

    private fun ensureVectorGraphicTexture(layer: EditorLayer): Int {
        val w = fboWidth.coerceIn(256, 960)
        val h = fboHeight.coerceIn(256, 960)
        val bgArgb = layer.backgroundColorArgb?.toInt() ?: when (layer.layerKind) {
            AeLayerKind.SHAPE -> 0xFF00E676.toInt()
            AeLayerKind.ELEMENT -> 0xFF29B6F6.toInt()
            else -> 0xFFFF2A85.toInt()
        }
        val key = "vec_${layer.layerKind}_${bgArgb}_${w}x${h}"
        val texId = layerTextTexIds.getOrPut(layer.id) { create2dTexture() }
        if (layerTextKeys[layer.id] == key) return texId

        val bmp = acquireReusableCanvasBitmap(w, h)
        bmp.eraseColor(AndroidColor.TRANSPARENT)
        val canvas = Canvas(bmp)
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.24f

        when (layer.layerKind) {
            AeLayerKind.SHAPE -> {
                val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        rect.left, rect.top, rect.right, rect.bottom,
                        AndroidColor.argb(120, AndroidColor.red(bgArgb), AndroidColor.green(bgArgb), AndroidColor.blue(bgArgb)),
                        AndroidColor.argb(200, 124, 92, 255),
                        Shader.TileMode.CLAMP
                    )
                }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (minOf(w, h) * 0.015f).coerceIn(3f, 12f)
                    color = bgArgb
                }
                val corner = radius * 0.34f
                canvas.drawRoundRect(rect, corner, corner, fillPaint)
                canvas.drawRoundRect(rect, corner, corner, strokePaint)
            }
            AeLayerKind.ELEMENT -> {
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (minOf(w, h) * 0.016f).coerceIn(3f, 12f)
                    color = bgArgb
                }
                canvas.drawCircle(cx, cy, radius * 0.95f, ringPaint)
                ringPaint.alpha = 150
                canvas.drawCircle(cx, cy, radius * 0.62f, ringPaint)
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    alpha = 210
                }
                canvas.drawCircle(cx, cy, radius * 0.14f, dotPaint)
            }
            else -> {
                // DRAW smooth neon wave stroke
                val path = AndroidPath().apply {
                    moveTo(cx - radius * 1.1f, cy + radius * 0.35f)
                    cubicTo(
                        cx - radius * 0.45f, cy - radius * 0.95f,
                        cx + radius * 0.45f, cy + radius * 0.95f,
                        cx + radius * 1.1f, cy - radius * 0.35f
                    )
                }
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = (minOf(w, h) * 0.042f).coerceIn(6f, 24f)
                    color = AndroidColor.argb(120, AndroidColor.red(bgArgb), AndroidColor.green(bgArgb), AndroidColor.blue(bgArgb))
                }
                val corePaint = Paint(glowPaint).apply {
                    strokeWidth = (minOf(w, h) * 0.02f).coerceIn(3f, 12f)
                    color = bgArgb
                }
                canvas.drawPath(path, glowPaint)
                canvas.drawPath(path, corePaint)
            }
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        layerTextKeys[layer.id] = key
        return texId
    }

    private fun acquireReusableCanvasBitmap(w: Int, h: Int): Bitmap {
        val existing = reusableCanvasBitmap
        if (existing != null && !existing.isRecycled && existing.width == w && existing.height == h) {
            return existing
        }
        existing?.recycle()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            reusableCanvasBitmap = it
        }
    }

    private fun createOesEntry(layerId: String): VideoEntry {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(tex[0])
        val entry = VideoEntry(tex[0], st)
        st.setOnFrameAvailableListener {
            if (!isReleased) {
                entry.frameAvailable = true
                entry.hasReceivedFrame = true
                onRequestRender()
            }
        }
        onLayerSurfaceReady(layerId, st)
        return entry
    }

    private fun create2dTexture(): Int {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return tex[0]
    }

    private fun uploadLayerBitmapTex(layerId: String, bmp: Bitmap) {
        val safeBmp = if (bmp.config != Bitmap.Config.ARGB_8888) {
            bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp
        } else bmp
        if (safeBmp.isRecycled) return
        val texId = layerBitmapTexIds.getOrPut(layerId) { create2dTexture() }
        layerBitmapDims[layerId] = safeBmp.width.coerceAtLeast(1) to safeBmp.height.coerceAtLeast(1)
        runCatching {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, safeBmp, 0)
        }
        if (safeBmp !== bmp && !safeBmp.isRecycled) safeBmp.recycle()
    }

    private fun uploadLayerMaskTex(layerId: String, maskBmp: Bitmap) {
        val safeBmp = if (maskBmp.config != Bitmap.Config.ARGB_8888) {
            maskBmp.copy(Bitmap.Config.ARGB_8888, false) ?: maskBmp
        } else maskBmp
        if (safeBmp.isRecycled) return
        val texId = layerMaskTexIds.getOrPut(layerId) { create2dTexture() }
        runCatching {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, safeBmp, 0)
        }
        if (safeBmp !== maskBmp && !safeBmp.isRecycled) safeBmp.recycle()
    }

    private fun computeFboSize(surfaceW: Int, surfaceH: Int): Pair<Int, Int> {
        val maxFboDim = 960
        val w = surfaceW.coerceAtLeast(1)
        val h = surfaceH.coerceAtLeast(1)
        val maxSide = maxOf(w, h)
        if (maxSide <= maxFboDim) return w to h
        val scale = maxFboDim.toFloat() / maxSide.toFloat()
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    private fun allocateFbos(width: Int, height: Int) {
        val (targetW, targetH) = computeFboSize(width, height)
        if (fboWidth > 0) {
            GLES30.glDeleteFramebuffers(5, fboIds, 0)
            GLES30.glDeleteTextures(5, fboTexIds, 0)
        }
        GLES30.glGenFramebuffers(5, fboIds, 0)
        GLES30.glGenTextures(5, fboTexIds, 0)
        for (i in 0 until 5) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[i])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                targetW, targetH, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, fboTexIds[i], 0
            )
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        fboWidth = targetW
        fboHeight = targetH
    }

    private fun getOrCompileShader(effectId: String): Int {
        shaderProgramCache[effectId]?.takeIf { it != 0 }?.let { return it }
        val fragSrc = ShaderLibrary.fragmentShaderFor(effectId)
        var prog = buildProgram(ShaderLibrary.VERTEX_SHADER_2D, fragSrc)
        if (prog == 0) {
            prog = buildProgram(ShaderLibrary.VERTEX_SHADER_2D, ShaderLibrary.fallbackShaderFor(effectId))
        }
        if (prog != 0) shaderProgramCache[effectId] = prog
        return prog
    }

    private fun drawFullscreenQuad() {
        vertexData.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, vertexData)
        vertexData.position(2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, vertexData)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) return 0
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glBindAttribLocation(program, 0, "aPosition")
        GLES30.glBindAttribLocation(program, 1, "aTexCoord")
        GLES30.glLinkProgram(program)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (linked[0] == 0) {
            GLES30.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.w("AeCompositeGlRenderer", "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun release() {
        isReleased = true
        synchronized(bitmapLock) {
            pendingBitmaps.clear()
            pendingMasks.clear()
        }
        videoEntries.values.forEach { entry ->
            runCatching { entry.surfaceTexture.setOnFrameAvailableListener(null) }
            runCatching { entry.surfaceTexture.release() }
            if (entry.oesTexId != 0) GLES30.glDeleteTextures(1, intArrayOf(entry.oesTexId), 0)
        }
        videoEntries.clear()

        val allTex = (layerBitmapTexIds.values + layerMaskTexIds.values + layerTextTexIds.values).filter { it != 0 }.toIntArray()
        if (allTex.isNotEmpty()) GLES30.glDeleteTextures(allTex.size, allTex, 0)
        layerBitmapTexIds.clear()
        layerMaskTexIds.clear()
        layerTextTexIds.clear()
        layerTextKeys.clear()
        layerBitmapDims.clear()

        if (oesProgram != 0) GLES30.glDeleteProgram(oesProgram)
        if (screenBlitProgram != 0 && screenBlitProgram != blitProgram) GLES30.glDeleteProgram(screenBlitProgram)
        if (blitProgram != 0) GLES30.glDeleteProgram(blitProgram)
        if (samMaskProgram != 0) GLES30.glDeleteProgram(samMaskProgram)
        if (shapeMaskProgram != 0) GLES30.glDeleteProgram(shapeMaskProgram)
        if (compositeBlendProgram != 0) GLES30.glDeleteProgram(compositeBlendProgram)
        oesProgram = 0; screenBlitProgram = 0; blitProgram = 0
        samMaskProgram = 0; shapeMaskProgram = 0; compositeBlendProgram = 0

        shaderProgramCache.values.forEach { if (it != 0) GLES30.glDeleteProgram(it) }
        shaderProgramCache.clear()

        if (fboWidth > 0) {
            GLES30.glDeleteFramebuffers(5, fboIds, 0)
            GLES30.glDeleteTextures(5, fboTexIds, 0)
            fboWidth = 0; fboHeight = 0
        }
        reusableCanvasBitmap?.recycle()
        reusableCanvasBitmap = null
    }

    companion object {
        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
    }
}

private class AeCompositeGlTextureView(
    context: Context,
    private val renderer: AeCompositeGlRenderer,
    private val onBeforeSurfaceDestroy: () -> Unit = {}
) : TextureView(context), TextureView.SurfaceTextureListener {

    private var renderThread: AeGlRenderThread? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.requestExitAndWait()
        renderThread = AeGlRenderThread(surface, renderer, width.coerceAtLeast(1), height.coerceAtLeast(1)).apply {
            start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.onWindowResize(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        runCatching { onBeforeSurfaceDestroy() }
        renderThread?.requestExitAndWait()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        runCatching { onBeforeSurfaceDestroy() }
        renderThread?.requestExitAndWait()
        renderThread = null
        super.onDetachedFromWindow()
    }

    private class AeGlRenderThread(
        private val targetSurfaceTexture: SurfaceTexture,
        private val renderer: AeCompositeGlRenderer,
        initialWidth: Int,
        initialHeight: Int
    ) : Thread("AeCompositeGlThread") {
        private val lock = Object()
        private val eventQueue = ConcurrentLinkedQueue<Runnable>()

        @Volatile private var shouldExit = false
        @Volatile private var renderPending = true
        @Volatile private var sizeChanged = true
        @Volatile private var width = initialWidth
        @Volatile private var height = initialHeight

        private val isRendering = AtomicBoolean(false)
        private var lastRenderCompletedNanos = 0L

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        fun requestRender() {
            synchronized(lock) {
                renderPending = true
                if (!isRendering.get()) lock.notifyAll()
            }
        }

        fun onWindowResize(newWidth: Int, newHeight: Int) {
            synchronized(lock) {
                width = newWidth; height = newHeight
                sizeChanged = true; renderPending = true
                lock.notifyAll()
            }
        }

        fun requestExitAndWait() {
            synchronized(lock) { shouldExit = true; lock.notifyAll() }
            if (currentThread() !== this) runCatching { join(450L) }
        }

        override fun run() {
            if (!initEgl()) { releaseEgl(); return }
            try {
                StudioRenderErrorBoundary.safeEvaluate("ae_gl_init", Unit) {
                    renderer.onContextCreated()
                    renderer.onViewportChanged(width, height)
                }
                while (!shouldExit) {
                    while (true) {
                        val ev = eventQueue.poll() ?: break
                        runCatching { ev.run() }
                    }
                    val doResize: Boolean
                    val w: Int
                    val h: Int
                    val doRender: Boolean
                    synchronized(lock) {
                        doResize = sizeChanged; w = width; h = height
                        sizeChanged = false; doRender = renderPending; renderPending = false
                    }
                    if (shouldExit) break
                    if (doResize) {
                        StudioRenderErrorBoundary.safeEvaluate("ae_gl_resize", Unit) {
                            renderer.onViewportChanged(w, h)
                        }
                    }
                    if (!shouldExit && (doRender || doResize)) {
                        val elapsedNs = System.nanoTime() - lastRenderCompletedNanos
                        if (!doResize && elapsedNs in 0L until 14_000_000L) {
                            synchronized(lock) {
                                if (!shouldExit && !sizeChanged) {
                                    renderPending = true
                                    lock.wait(16L)
                                }
                            }
                            continue
                        }
                        isRendering.set(true)
                        try {
                            StudioRenderErrorBoundary.safeEvaluate("ae_gl_draw_frame", Unit) {
                                renderer.onDraw()
                                if (!shouldExit && eglDisplay !== EGL14.EGL_NO_DISPLAY && eglSurface !== EGL14.EGL_NO_SURFACE) {
                                    EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                                }
                            }
                        } finally {
                            lastRenderCompletedNanos = System.nanoTime()
                            isRendering.set(false)
                        }
                    }
                    synchronized(lock) {
                        if (!shouldExit && !renderPending && !sizeChanged && eventQueue.isEmpty()) {
                            lock.wait(100L)
                        }
                    }
                }
            } finally {
                runCatching { renderer.release() }
                releaseEgl()
            }
        }

        private fun initEgl(): Boolean {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay === EGL14.EGL_NO_DISPLAY) return false
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false
            val config = chooseEglConfig(eglDisplay) ?: return false
            val attribEs3 = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            var ctx = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, attribEs3, 0)
            if (ctx == null || ctx === EGL14.EGL_NO_CONTEXT) {
                val attribEs2 = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                ctx = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, attribEs2, 0)
            }
            if (ctx == null || ctx === EGL14.EGL_NO_CONTEXT) return false
            eglContext = ctx
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, targetSurfaceTexture, intArrayOf(EGL14.EGL_NONE), 0)
            if (eglSurface === EGL14.EGL_NO_SURFACE) return false
            return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun chooseEglConfig(display: EGLDisplay): EGLConfig? {
            val es3 = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (EGL14.eglChooseConfig(display, es3, 0, configs, 0, 1, num, 0) && num[0] > 0 && configs[0] != null) {
                return configs[0]
            }
            val es2 = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            if (EGL14.eglChooseConfig(display, es2, 0, configs, 0, 1, num, 0) && num[0] > 0 && configs[0] != null) {
                return configs[0]
            }
            return null
        }

        private fun releaseEgl() {
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface !== EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext !== EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglSurface = EGL14.EGL_NO_SURFACE; eglContext = EGL14.EGL_NO_CONTEXT; eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}

private fun normalizeAeBitmapForGl(source: Bitmap): Bitmap {
    val maxDim = 1080
    val w = source.width.coerceAtLeast(1)
    val h = source.height.coerceAtLeast(1)
    val scale = if (w > maxDim || h > maxDim) maxDim.toFloat() / maxOf(w, h).toFloat() else 1f
    val targetW = (w * scale).toInt().coerceAtLeast(1)
    val targetH = (h * scale).toInt().coerceAtLeast(1)
    val argb = if (source.config != Bitmap.Config.ARGB_8888) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: source
    } else source
    if (targetW == argb.width && targetH == argb.height) return argb
    val scaled = Bitmap.createScaledBitmap(argb, targetW, targetH, true)
    if (scaled !== argb && argb !== source && !argb.isRecycled) argb.recycle()
    return scaled
}

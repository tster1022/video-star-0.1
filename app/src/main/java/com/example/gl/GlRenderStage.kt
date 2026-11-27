package com.example.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.example.engine.render.StudioRenderErrorBoundary
import com.example.engine.sam.SamCutoutSegmenter
import com.example.model.EditorLayer
import com.example.model.MediaClip
import com.example.model.SamCutoutSpec
import com.example.model.SceneTransform
import com.example.model.TextLayerSpec
import com.example.model.TimelineScene
import com.example.model.evaluateVelocityAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Hardware OpenGL ES 3.0 preview stage backed by TextureView + EGL14.
 * TextureView integrates natively into Jetpack Compose layout clipping, rounded corners,
 * graphicsLayer transforms, masks, and overlays without SurfaceView hole-punching or detachment.
 */
@Composable
fun GlRenderStage(
    scene: TimelineScene,
    localPositionMs: Long,
    isPlaying: Boolean,
    clipVolume: Float,
    speedMultiplier: Float,
    modifier: Modifier = Modifier,
    enableExoPlayer: Boolean = true,
    samCutout: SamCutoutSpec = SamCutoutSpec(),
    layerText: String = "",
    layerTextSpec: TextLayerSpec = TextLayerSpec(),
    parentLayer: EditorLayer? = null,
    onVideoPositionChanged: (Long) -> Unit = {},
    onScenePlaybackEnded: () -> Unit = {},
    onFirstFrameRendered: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestOnPositionChanged by rememberUpdatedState(onVideoPositionChanged)
    val latestOnScenePlaybackEnded by rememberUpdatedState(onScenePlaybackEnded)
    val latestOnFirstFrameRendered by rememberUpdatedState(onFirstFrameRendered)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestLocalPosMs by rememberUpdatedState(localPositionMs)
    val latestScene by rememberUpdatedState(scene)

    val clip = scene.clip
    val isVideo = enableExoPlayer && clip is MediaClip.Video && !scene.isReversed
    val uriString = clip?.uriString ?: scene.mediaUriString

    val isDisposed = remember { AtomicBoolean(false) }
    var glTextureViewRef by remember { mutableStateOf<VideoStarGlTextureView?>(null) }
    var videoSurfaceRef by remember { mutableStateOf<Surface?>(null) }
    var hasPlayerError by remember(uriString, isVideo) { mutableStateOf(false) }
    var discoveredDurationMs by remember(uriString, isVideo) { mutableStateOf(0L) }

    val renderer = remember {
        VideoStarGlRenderer(
            onSurfaceTextureReady = { st ->
                if (isDisposed.get()) return@VideoStarGlRenderer
                val newSurface = runCatching { Surface(st) }.getOrNull() ?: return@VideoStarGlRenderer
                mainHandler.post {
                    if (isDisposed.get() || glTextureViewRef?.isAvailable != true) {
                        runCatching { newSurface.release() }
                    } else {
                        val previous = videoSurfaceRef
                        videoSurfaceRef = newSurface
                        runCatching { previous?.release() }
                    }
                }
            },
            onRequestRender = {
                if (!isDisposed.get()) {
                    glTextureViewRef?.requestRender()
                }
            },
            onFirstFrameDrawn = {
                if (!isDisposed.get()) {
                    mainHandler.post {
                        if (!isDisposed.get()) {
                            latestOnFirstFrameRendered()
                        }
                    }
                }
            }
        )
    }

    val exoPlayer = remember(uriString, isVideo) {
        if (isVideo && !uriString.isNullOrBlank()) {
            runCatching {
                val mediaAudioAttrs = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                ExoPlayer.Builder(context)
                    .setAudioAttributes(mediaAudioAttrs, false)
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
                        repeatMode = Player.REPEAT_MODE_OFF
                        prepare()
                    }
            }.getOrNull()
        } else null
    }

    LaunchedEffect(uriString, isVideo) {
        hasPlayerError = false
        discoveredDurationMs = 0L
        renderer.resetVideoSourceState()
    }

    LaunchedEffect(exoPlayer, videoSurfaceRef) {
        val player = exoPlayer ?: return@LaunchedEffect
        val surf = videoSurfaceRef
        runCatching {
            if (surf != null && surf.isValid && !isDisposed.get()) {
                player.setVideoSurface(surf)
                val vid = latestScene.clip as? MediaClip.Video
                if (vid != null) {
                    val safeStart = vid.startTrimMs.coerceAtLeast(0L)
                    val safeEnd = vid.endTrimMs.takeIf { it > safeStart + 120L }
                        ?: discoveredDurationMs.takeIf { it > safeStart + 120L }
                        ?: Long.MAX_VALUE
                    val targetAbs = (safeStart + latestLocalPosMs).coerceIn(safeStart, safeEnd)
                    player.seekTo(targetAbs)
                }
            } else {
                player.setVideoSurface(null)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0 && !isDisposed.get()) {
                    renderer.setSourceDimensions(videoSize.width, videoSize.height)
                    glTextureViewRef?.requestRender()
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
                        val vid = latestScene.clip as? MediaClip.Video
                        if (vid != null) {
                            val safeStart = vid.startTrimMs.coerceAtLeast(0L)
                            val safeEnd = vid.endTrimMs.takeIf { it > safeStart + 120L }
                                ?: dur.takeIf { it != C.TIME_UNSET && it > safeStart + 120L }
                                ?: Long.MAX_VALUE
                            val targetAbs = (safeStart + latestLocalPosMs).coerceIn(safeStart, safeEnd)
                            runCatching {
                                if (abs(player.currentPosition - targetAbs) > 45L) {
                                    player.seekTo(targetAbs)
                                }
                            }
                        }
                        glTextureViewRef?.requestRender()
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    latestOnScenePlaybackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasPlayerError = true
                renderer.resetVideoSourceState()
                glTextureViewRef?.requestRender()
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

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            runCatching { exoPlayer?.playWhenReady = false }
            runCatching { exoPlayer?.setVideoSurface(null) }
            runCatching { videoSurfaceRef?.release() }
            videoSurfaceRef = null
        }
    }

    // Load bounded poster frame for video (instant real preview while ExoPlayer prepares/seeks) or sampled photo bitmap / calibration grid
    LaunchedEffect(uriString, isVideo, scene.transform.bgColorArgb) {
        val bmp: Bitmap? = withContext(Dispatchers.IO) {
            StudioRenderErrorBoundary.safeEvaluate("gl_stage_poster_load", null) {
                val raw = if (isVideo && !uriString.isNullOrBlank()) {
                    val vid = latestScene.clip as? MediaClip.Video
                    val initialSeek = (vid?.startTrimMs ?: 0L) + latestLocalPosMs
                    MediaImportHelper.extractVideoFrame(uriString, initialSeek, context, maxDimPx = 720)
                } else if (!uriString.isNullOrBlank()) {
                    MediaImportHelper.decodeSampledBitmap(uriString, context, maxDimPx = 720)
                } else {
                    null
                }
                val resolved = raw ?: ProceduralTestGridFactory.createTestGridBitmap(
                    width = 512,
                    height = 512,
                    baseBgArgb = scene.transform.bgColorArgb.toInt()
                )
                normalizeBitmapForGl(resolved)
            }
        }
        if (bmp != null && !isDisposed.get()) {
            renderer.setSourceBitmap(bmp)
            glTextureViewRef?.requestRender()
        }
    }

    val sceneDur = scene.effectiveDurationMs.coerceAtLeast(200L)
    val localProgress = (localPositionMs.toFloat() / sceneDur.toFloat()).coerceIn(0f, 1f)

    LaunchedEffect(samCutout.maskUriString, samCutout.enabled) {
        if (samCutout.isActive) {
            val maskBmp = withContext(Dispatchers.IO) {
                SamCutoutSegmenter.loadMaskBitmap(samCutout.maskUriString)
            }
            renderer.setSamMaskBitmap(maskBmp)
        } else {
            renderer.setSamMaskBitmap(null)
        }
        glTextureViewRef?.requestRender()
    }

    val useLiveVideoForGl = isVideo && !hasPlayerError

    LaunchedEffect(scene, localProgress, useLiveVideoForGl, samCutout, layerText, layerTextSpec, parentLayer) {
        renderer.updateLayerExtras(
            samSpec = samCutout,
            baseText = layerText,
            textSpec = layerTextSpec,
            parentLayer = parentLayer
        )
        renderer.updateState(scene, localProgress, useLiveVideoForGl)
        glTextureViewRef?.requestRender()
    }

    val latestSpeedMult by rememberUpdatedState(speedMultiplier)
    val latestLocalProgress by rememberUpdatedState(localProgress)

    // Sync ExoPlayer playback & volume cleanly without restarting on every scene/frame change
    LaunchedEffect(exoPlayer, isPlaying, clipVolume, hasPlayerError, uriString) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (hasPlayerError) {
            runCatching { player.playWhenReady = false }
            return@LaunchedEffect
        }
        runCatching {
            player.volume = clipVolume.coerceIn(0f, 1f)
            val speed = (evaluateVelocityAt(latestLocalProgress, latestScene.velocityPoints, latestScene.isVelocitySmooth) * latestSpeedMult)
                .coerceIn(0.25f, 3.0f)
            player.playbackParameters = PlaybackParameters(speed)
            if (isPlaying) {
                player.setSeekParameters(SeekParameters.EXACT)
                val vid = latestScene.clip as? MediaClip.Video
                if (vid != null) {
                    val safeStart = vid.startTrimMs.coerceAtLeast(0L)
                    val safeEnd = vid.endTrimMs.takeIf { it > safeStart + 120L }
                        ?: discoveredDurationMs.takeIf { it > safeStart + 120L }
                        ?: Long.MAX_VALUE
                    val targetAbs = (safeStart + latestLocalPosMs).coerceIn(safeStart, safeEnd)
                    if (player.playbackState == Player.STATE_ENDED ||
                        player.currentPosition >= safeEnd ||
                        abs(player.currentPosition - targetAbs) > 250L
                    ) {
                        player.seekTo(targetAbs)
                    }
                }
                player.playWhenReady = true
            } else {
                player.playWhenReady = false
            }
        }
    }

    // Paused / scrub seek synchronization: 33ms throttled CLOSEST_SYNC during 120Hz scrub, EXACT on settle.
    // Never cancels/re-launches per frame during active playback.
    val pausedSeekMs = if (isPlaying) Long.MIN_VALUE else localPositionMs
    LaunchedEffect(exoPlayer, pausedSeekMs, uriString, hasPlayerError) {
        if (pausedSeekMs == Long.MIN_VALUE || hasPlayerError) return@LaunchedEffect
        val player = exoPlayer ?: return@LaunchedEffect
        val vid = latestScene.clip as? MediaClip.Video ?: return@LaunchedEffect
        val safeStart = vid.startTrimMs.coerceAtLeast(0L)
        val safeEnd = vid.endTrimMs.takeIf { it > safeStart + 120L }
            ?: discoveredDurationMs.takeIf { it > safeStart + 120L }
            ?: Long.MAX_VALUE
        val targetAbs = (safeStart + pausedSeekMs).coerceIn(safeStart, safeEnd)
        delay(33L)
        runCatching {
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            if (abs(player.currentPosition - targetAbs) > 45L) {
                player.seekTo(targetAbs)
            }
        }
        delay(140L)
        runCatching {
            player.setSeekParameters(SeekParameters.EXACT)
            if (abs(player.currentPosition - targetAbs) > 35L) {
                player.seekTo(targetAbs)
            }
        }
    }

    // Active playback position & velocity polling loop (never cancelled by localPositionMs updates)
    LaunchedEffect(exoPlayer, isPlaying, uriString, hasPlayerError) {
        val player = exoPlayer ?: return@LaunchedEffect
        val vid = clip as? MediaClip.Video ?: return@LaunchedEffect
        if (isPlaying && !hasPlayerError) {
            val safeStart = vid.startTrimMs.coerceAtLeast(0L)
            val safeEnd = vid.endTrimMs.takeIf { it > safeStart + 120L }
                ?: discoveredDurationMs.takeIf { it > safeStart + 120L }
                ?: Long.MAX_VALUE
            while (true) {
                val pos = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(safeStart)
                if (safeEnd != Long.MAX_VALUE && pos >= safeEnd) {
                    latestOnScenePlaybackEnded()
                    break
                } else {
                    latestOnPositionChanged((pos - safeStart).coerceAtLeast(0L))
                    if (latestScene.velocityPoints.isNotEmpty() || abs(latestSpeedMult - 1f) > 0.01f) {
                        val speed = (evaluateVelocityAt(latestLocalProgress, latestScene.velocityPoints, latestScene.isVelocitySmooth) * latestSpeedMult)
                            .coerceIn(0.25f, 3.0f)
                        runCatching {
                            if (abs(player.playbackParameters.speed - speed) > 0.04f) {
                                player.playbackParameters = PlaybackParameters(speed)
                            }
                        }
                    }
                }
                delay(33L)
            }
        }
    }

    val latestPlayer by rememberUpdatedState(exoPlayer)
    AndroidView(
        factory = { ctx ->
            VideoStarGlTextureView(
                context = ctx,
                renderer = renderer,
                onBeforeSurfaceDestroy = {
                    runCatching { latestPlayer?.playWhenReady = false }
                    runCatching { latestPlayer?.setVideoSurface(null) }
                    runCatching {
                        videoSurfaceRef?.release()
                        videoSurfaceRef = null
                    }
                }
            ).apply {
                glTextureViewRef = this
            }
        },
        update = { view ->
            glTextureViewRef = view
            renderer.updateLayerExtras(samCutout, layerText, layerTextSpec, parentLayer)
            renderer.updateState(scene, localProgress, useLiveVideoForGl)
        },
        modifier = modifier
    )
}

/**
 * Renders an EditorLayer's media clip or background color through the real GLSL shader pipeline
 * whenever the layer has active GPU ShaderEffectPasses or AI Subject Cutout (SAM).
 */
@Composable
fun GlLayerRenderStage(
    layer: EditorLayer,
    progress: Float,
    isPlaying: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
    parentLayer: EditorLayer? = null
) {
    val enabledPasses = remember(layer.effectPasses) {
        layer.effectPasses.filter { it.isEnabled && it.effectId != "none" }
    }
    val clip = layer.mediaClip
    val durMs = (clip?.effectiveDurationMs ?: 3_000L).coerceAtLeast(300L)
    val layerScene = remember(layer.id, clip?.id, clip?.uriString, layer.backgroundColorArgb, enabledPasses) {
        TimelineScene(
            id = "gl_layer_${layer.id}",
            title = "الطبقة ${layer.number}",
            durationMs = durMs,
            clipId = clip?.id,
            mediaUriString = clip?.uriString,
            isVideo = clip is MediaClip.Video,
            clip = clip,
            transform = SceneTransform(
                bgColorArgb = layer.backgroundColorArgb ?: 0x00000000L,
                isFitMode = clip?.isFitMode ?: false
            ),
            effectPasses = enabledPasses
        )
    }
    val localMs = (progress.coerceIn(0f, 1f) * durMs).toLong()
    GlRenderStage(
        scene = layerScene,
        localPositionMs = localMs,
        isPlaying = isPlaying,
        clipVolume = if (isMuted) 0f else 1f,
        speedMultiplier = 1f,
        samCutout = layer.samCutout,
        layerText = layer.effectiveText,
        layerTextSpec = layer.textSpec,
        parentLayer = parentLayer,
        modifier = modifier
    )
}

/**
 * TextureView-based OpenGL ES 3.0 render surface using EGL14 on a dedicated GL thread.
 */
class VideoStarGlTextureView(
    context: Context,
    private val renderer: VideoStarGlRenderer,
    private val onBeforeSurfaceDestroy: () -> Unit = {}
) : TextureView(context), TextureView.SurfaceTextureListener {

    private var renderThread: GlRenderThread? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun requestRender() {
        renderThread?.requestRender()
    }

    fun queueEvent(block: () -> Unit) {
        renderThread?.queueEvent(block)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.requestExitAndWait()
        renderThread = GlRenderThread(surface, renderer, width.coerceAtLeast(1), height.coerceAtLeast(1)).apply {
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

    private class GlRenderThread(
        private val targetSurfaceTexture: SurfaceTexture,
        private val renderer: VideoStarGlRenderer,
        initialWidth: Int,
        initialHeight: Int
    ) : Thread("VideoStarGlRenderThread") {

        private val lock = Object()
        private val eventQueue = ConcurrentLinkedQueue<Runnable>()

        @Volatile
        private var shouldExit = false

        @Volatile
        private var renderPending = true

        @Volatile
        private var sizeChanged = true

        @Volatile
        private var width = initialWidth

        @Volatile
        private var height = initialHeight

        private val isRendering = AtomicBoolean(false)
        private var lastRenderCompletedNanos = 0L

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        fun requestRender() {
            synchronized(lock) {
                renderPending = true
                // Frame-skipping lock: when a frame is already in flight, coalesce into `renderPending`
                // instead of repeatedly waking the render loop per touch sample.
                if (!isRendering.get()) {
                    lock.notifyAll()
                }
            }
        }

        fun queueEvent(block: () -> Unit) {
            eventQueue.add(Runnable { block() })
            synchronized(lock) {
                lock.notifyAll()
            }
        }

        fun onWindowResize(newWidth: Int, newHeight: Int) {
            synchronized(lock) {
                width = newWidth
                height = newHeight
                sizeChanged = true
                renderPending = true
                lock.notifyAll()
            }
        }

        fun requestExit() {
            synchronized(lock) {
                shouldExit = true
                lock.notifyAll()
            }
        }

        fun requestExitAndWait() {
            requestExit()
            if (currentThread() !== this) {
                runCatching { join(450L) }
            }
        }

        override fun run() {
            if (!initEgl()) {
                releaseEgl()
                return
            }
            try {
                StudioRenderErrorBoundary.safeEvaluate("gl_context_init", Unit) {
                    renderer.onContextCreated()
                    renderer.onViewportChanged(width, height)
                }

                while (!shouldExit) {
                    while (true) {
                        val event = eventQueue.poll() ?: break
                        runCatching { event.run() }
                    }

                    val doResize: Boolean
                    val w: Int
                    val h: Int
                    val doRender: Boolean
                    synchronized(lock) {
                        doResize = sizeChanged
                        w = width
                        h = height
                        sizeChanged = false
                        doRender = renderPending
                        renderPending = false
                    }

                    if (shouldExit) break

                    if (doResize) {
                        StudioRenderErrorBoundary.safeEvaluate("gl_viewport", Unit) {
                            renderer.onViewportChanged(w, h)
                        }
                    }
                    if (!shouldExit && (doRender || doResize)) {
                        val elapsedNs = System.nanoTime() - lastRenderCompletedNanos
                        if (!doResize && elapsedNs in 0L until 14_000_000L) {
                            // Enforce ~60fps pacing ceiling so 120Hz–240Hz scrubs coalesce smoothly
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
                            StudioRenderErrorBoundary.safeEvaluate("gl_thread_draw", Unit) {
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

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                config,
                targetSurfaceTexture,
                surfaceAttribs,
                0
            )
            if (eglSurface === EGL14.EGL_NO_SURFACE) return false

            return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun chooseEglConfig(display: EGLDisplay): EGLConfig? {
            val es3Attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (EGL14.eglChooseConfig(display, es3Attribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0 && configs[0] != null
            ) {
                return configs[0]
            }

            val es2Attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            if (EGL14.eglChooseConfig(display, es2Attribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0 && configs[0] != null
            ) {
                return configs[0]
            }
            return null
        }

        private fun releaseEgl() {
            if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                if (eglSurface !== EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext !== EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            eglSurface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}

private fun normalizeBitmapForGl(source: Bitmap): Bitmap {
    val maxDim = 1080
    val w = source.width.coerceAtLeast(1)
    val h = source.height.coerceAtLeast(1)
    val scale = if (w > maxDim || h > maxDim) {
        maxDim.toFloat() / maxOf(w, h).toFloat()
    } else 1f
    val targetW = (w * scale).toInt().coerceAtLeast(1)
    val targetH = (h * scale).toInt().coerceAtLeast(1)
    val argb = if (source.config != Bitmap.Config.ARGB_8888) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: source
    } else source
    if (targetW == argb.width && targetH == argb.height) return argb
    val scaled = Bitmap.createScaledBitmap(argb, targetW, targetH, true)
    if (scaled !== argb && argb !== source && !argb.isRecycled) {
        argb.recycle()
    }
    return scaled
}


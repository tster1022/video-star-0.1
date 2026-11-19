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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.MediaImportHelper
import com.example.model.EditorLayer
import com.example.model.MediaClip
import com.example.model.SceneTransform
import com.example.model.TimelineScene
import com.example.model.evaluateVelocityAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
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
    onVideoPositionChanged: (Long) -> Unit = {},
    onScenePlaybackEnded: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestOnPositionChanged by rememberUpdatedState(onVideoPositionChanged)
    val latestOnScenePlaybackEnded by rememberUpdatedState(onScenePlaybackEnded)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestLocalPosMs by rememberUpdatedState(localPositionMs)
    val latestScene by rememberUpdatedState(scene)

    val clip = scene.clip
    val isVideo = clip is MediaClip.Video && !scene.isReversed
    val uriString = clip?.uriString ?: scene.mediaUriString

    var glTextureViewRef by remember { mutableStateOf<VideoStarGlTextureView?>(null) }
    var videoSurfaceRef by remember { mutableStateOf<Surface?>(null) }

    val renderer = remember {
        VideoStarGlRenderer(
            onSurfaceTextureReady = { st ->
                val newSurface = Surface(st)
                mainHandler.post {
                    videoSurfaceRef?.release()
                    videoSurfaceRef = newSurface
                }
            },
            onRequestRender = {
                glTextureViewRef?.requestRender()
            }
        )
    }

    val exoPlayer = remember(uriString, isVideo) {
        if (isVideo && !uriString.isNullOrBlank()) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
                repeatMode = Player.REPEAT_MODE_OFF
                prepare()
            }
        } else null
    }

    LaunchedEffect(uriString, isVideo) {
        renderer.resetVideoSourceState()
    }

    LaunchedEffect(exoPlayer, videoSurfaceRef) {
        val player = exoPlayer ?: return@LaunchedEffect
        val surf = videoSurfaceRef
        if (surf != null && surf.isValid) {
            player.setVideoSurface(surf)
            val vid = latestScene.clip as? MediaClip.Video
            if (vid != null) {
                val targetAbs = (vid.startTrimMs + latestLocalPosMs).coerceIn(vid.startTrimMs, vid.endTrimMs)
                player.seekTo(targetAbs)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    renderer.setSourceDimensions(videoSize.width, videoSize.height)
                    glTextureViewRef?.requestRender()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val player = exoPlayer ?: return
                if (playbackState == Player.STATE_READY && !latestIsPlaying) {
                    val vid = latestScene.clip as? MediaClip.Video
                    if (vid != null) {
                        val targetAbs = (vid.startTrimMs + latestLocalPosMs).coerceIn(vid.startTrimMs, vid.endTrimMs)
                        if (abs(player.currentPosition - targetAbs) > 45L) {
                            player.seekTo(targetAbs)
                        }
                    }
                    glTextureViewRef?.requestRender()
                } else if (playbackState == Player.STATE_ENDED) {
                    latestOnScenePlaybackEnded()
                }
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
            exoPlayer?.setVideoSurface(null)
            exoPlayer?.release()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoSurfaceRef?.release()
            videoSurfaceRef = null
        }
    }

    // Load poster frame for video (instant real preview while ExoPlayer prepares/seeks) or photo bitmap / calibration grid
    LaunchedEffect(uriString, isVideo, scene.transform.bgColorArgb) {
        val bmp: Bitmap? = withContext(Dispatchers.IO) {
            if (isVideo && !uriString.isNullOrBlank()) {
                val vid = latestScene.clip as? MediaClip.Video
                val initialSeek = (vid?.startTrimMs ?: 0L) + latestLocalPosMs
                MediaImportHelper.extractVideoFrame(uriString, initialSeek, context)
            } else if (!uriString.isNullOrBlank()) {
                val path = Uri.parse(uriString).path
                if (path != null && File(path).exists()) {
                    BitmapFactory.decodeFile(path)
                } else {
                    runCatching {
                        context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    }.getOrNull()
                }
            } else {
                ProceduralTestGridFactory.createTestGridBitmap(
                    width = 512,
                    height = 512,
                    baseBgArgb = scene.transform.bgColorArgb.toInt()
                )
            }
        }
        if (bmp != null) {
            renderer.setSourceBitmap(bmp)
            glTextureViewRef?.requestRender()
        }
    }

    val sceneDur = scene.effectiveDurationMs.coerceAtLeast(200L)
    val localProgress = (localPositionMs.toFloat() / sceneDur.toFloat()).coerceIn(0f, 1f)

    LaunchedEffect(scene, localProgress, isVideo) {
        renderer.updateState(scene, localProgress, isVideo)
        glTextureViewRef?.requestRender()
    }

    // Sync ExoPlayer playback & speed
    LaunchedEffect(exoPlayer, isPlaying, clipVolume, speedMultiplier, scene) {
        val player = exoPlayer ?: return@LaunchedEffect
        player.volume = clipVolume.coerceIn(0f, 1f)
        val speed = (evaluateVelocityAt(localProgress, scene.velocityPoints, scene.isVelocitySmooth) * speedMultiplier)
            .coerceIn(0.25f, 3.0f)
        player.playbackParameters = PlaybackParameters(speed)
        if (isPlaying) {
            val vid = scene.clip as? MediaClip.Video
            if (vid != null && (player.playbackState == Player.STATE_ENDED || player.currentPosition >= vid.endTrimMs)) {
                player.seekTo(vid.startTrimMs)
            }
            player.playWhenReady = true
        } else {
            player.playWhenReady = false
        }
    }

    LaunchedEffect(exoPlayer, localPositionMs, isPlaying) {
        val player = exoPlayer ?: return@LaunchedEffect
        val vid = clip as? MediaClip.Video ?: return@LaunchedEffect
        val targetAbs = (vid.startTrimMs + localPositionMs).coerceIn(vid.startTrimMs, vid.endTrimMs)
        if (!isPlaying) {
            if (abs(player.currentPosition - targetAbs) > 45L) {
                player.seekTo(targetAbs)
            }
        } else {
            // If an external ViewModel timer looped playback or the user scrubbed forward/back, sync ExoPlayer
            if (abs(player.currentPosition - targetAbs) > 350L || player.playbackState == Player.STATE_ENDED) {
                player.seekTo(targetAbs)
                player.playWhenReady = true
            }
            while (true) {
                val pos = player.currentPosition
                if (pos >= vid.endTrimMs) {
                    latestOnScenePlaybackEnded()
                    break
                } else {
                    latestOnPositionChanged((pos - vid.startTrimMs).coerceAtLeast(0L))
                }
                delay(33L)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            VideoStarGlTextureView(ctx, renderer).apply {
                glTextureViewRef = this
            }
        },
        update = { view ->
            glTextureViewRef = view
            renderer.updateState(scene, localProgress, isVideo)
            view.requestRender()
        },
        modifier = modifier
    )
}

/**
 * Renders an EditorLayer's media clip or background color through the real GLSL shader pipeline
 * whenever the layer has active GPU ShaderEffectPasses.
 */
@Composable
fun GlLayerRenderStage(
    layer: EditorLayer,
    progress: Float,
    isPlaying: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val enabledPasses = remember(layer.effectPasses) {
        layer.effectPasses.filter { it.isEnabled && it.effectId != "none" }
    }
    val clip = layer.mediaClip
    val durMs = (clip?.effectiveDurationMs ?: 3_000L).coerceAtLeast(300L)
    val layerScene = remember(layer.id, clip, layer.backgroundColorArgb, enabledPasses) {
        TimelineScene(
            id = "gl_layer_${layer.id}",
            title = "الطبقة ${layer.number}",
            durationMs = durMs,
            clipId = clip?.id,
            mediaUriString = clip?.uriString,
            isVideo = clip is MediaClip.Video,
            clip = clip,
            transform = SceneTransform(
                bgColorArgb = layer.backgroundColorArgb ?: 0xFF000000L,
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
        modifier = modifier
    )
}

/**
 * TextureView-based OpenGL ES 3.0 render surface using EGL14 on a dedicated GL thread.
 */
class VideoStarGlTextureView(
    context: Context,
    private val renderer: VideoStarGlRenderer
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
        renderThread?.requestExitAndWait()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
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

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        fun requestRender() {
            synchronized(lock) {
                renderPending = true
                lock.notifyAll()
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

        fun requestExitAndWait() {
            synchronized(lock) {
                shouldExit = true
                lock.notifyAll()
            }
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
                renderer.onContextCreated()
                renderer.onViewportChanged(width, height)

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
                        renderer.onViewportChanged(w, h)
                    }
                    if (doRender || doResize) {
                        renderer.onDraw()
                        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
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

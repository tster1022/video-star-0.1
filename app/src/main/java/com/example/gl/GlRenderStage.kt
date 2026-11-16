package com.example.gl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLSurfaceView
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.MediaClip
import com.example.model.TimelineScene
import com.example.model.evaluateVelocityAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Hardware OpenGL ES 3.0 preview stage embedding a GLSurfaceView + VideoStarGlRenderer.
 * Feeds ExoPlayer video frames into SurfaceTexture (OES) or decoded photo bitmaps into GL_TEXTURE_2D.
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
    val clip = scene.clip
    val isVideo = clip is MediaClip.Video && !scene.isReversed
    val uriString = clip?.uriString ?: scene.mediaUriString

    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    var videoSurfaceRef by remember { mutableStateOf<Surface?>(null) }

    val renderer = remember {
        VideoStarGlRenderer(
            onSurfaceTextureReady = { st ->
                videoSurfaceRef = Surface(st)
            },
            onRequestRender = {
                glSurfaceViewRef?.requestRender()
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

    LaunchedEffect(exoPlayer, videoSurfaceRef) {
        val player = exoPlayer ?: return@LaunchedEffect
        val surf = videoSurfaceRef
        if (surf != null) {
            player.setVideoSurface(surf)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onScenePlaybackEnded()
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
            glSurfaceViewRef?.queueEvent { renderer.release() }
        }
    }

    // Load photo bitmap onto GL texture when displaying a photo scene
    LaunchedEffect(uriString, isVideo) {
        if (!isVideo && !uriString.isNullOrBlank()) {
            val bmp: Bitmap? = withContext(Dispatchers.IO) {
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
            }
            if (bmp != null) {
                renderer.setSourceBitmap(bmp)
                glSurfaceViewRef?.requestRender()
            }
        }
    }

    val sceneDur = scene.effectiveDurationMs.coerceAtLeast(200L)
    val localProgress = (localPositionMs.toFloat() / sceneDur.toFloat()).coerceIn(0f, 1f)

    LaunchedEffect(scene, localProgress, isVideo) {
        renderer.updateState(scene, localProgress, isVideo)
        glSurfaceViewRef?.requestRender()
    }

    // Sync ExoPlayer playback & speed
    LaunchedEffect(exoPlayer, isPlaying, clipVolume, speedMultiplier, scene) {
        val player = exoPlayer ?: return@LaunchedEffect
        player.volume = clipVolume.coerceIn(0f, 1f)
        val speed = (evaluateVelocityAt(localProgress, scene.velocityPoints, scene.isVelocitySmooth) * speedMultiplier)
            .coerceIn(0.25f, 3.0f)
        player.playbackParameters = PlaybackParameters(speed)
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(exoPlayer, localPositionMs, isPlaying) {
        val player = exoPlayer ?: return@LaunchedEffect
        val vid = clip as? MediaClip.Video ?: return@LaunchedEffect
        val targetAbs = (vid.startTrimMs + localPositionMs).coerceIn(vid.startTrimMs, vid.endTrimMs)
        if (!isPlaying && abs(player.currentPosition - targetAbs) > 90L) {
            player.seekTo(targetAbs)
        }
        while (isPlaying) {
            val pos = player.currentPosition
            if (pos >= vid.endTrimMs) {
                onScenePlaybackEnded()
                break
            } else {
                onVideoPositionChanged((pos - vid.startTrimMs).coerceAtLeast(0L))
            }
            delay(33L)
        }
    }

    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                glSurfaceViewRef = this
            }
        },
        update = { view ->
            renderer.updateState(scene, localProgress, isVideo)
            view.requestRender()
        },
        modifier = modifier
    )
}

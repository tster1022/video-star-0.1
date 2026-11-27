package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.MediaImportHelper
import com.example.model.MediaClip
import com.example.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

fun resolveCoilModel(uriString: String): Any {
    val parsed = Uri.parse(uriString)
    return if (parsed.scheme == "file" && parsed.path != null) {
        File(parsed.path!!)
    } else {
        parsed
    }
}

@Composable
fun MediaUriThumbnail(
    uriString: String?,
    isVideo: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    frameTimeMs: Long = 0L,
    maxDimPx: Int = 720,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    emptyLabel: String? = null
) {
    if (uriString.isNullOrBlank()) {
        Box(
            modifier = modifier.background(Color(0xFF1C1C20)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Outlined.Movie else Icons.Outlined.Image,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(28.dp)
                )
                if (!emptyLabel.isNullOrBlank()) {
                    Text(
                        text = emptyLabel,
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }
        return
    }

    val context = LocalContext.current
    if (!isVideo) {
        val safeSize = maxDimPx.coerceIn(64, 1440)
        val imageRequest = remember(uriString, safeSize) {
            ImageRequest.Builder(context)
                .data(resolveCoilModel(uriString))
                .size(safeSize)
                .allowHardware(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = modifier
        )
    } else {
        val frameBucketMs = (frameTimeMs.coerceAtLeast(0L) / 150L) * 150L
        val frameBitmap by produceState<Bitmap?>(
            initialValue = null,
            key1 = uriString,
            key2 = frameBucketMs,
            key3 = maxDimPx
        ) {
            if (frameBucketMs > 0L) {
                delay(33L)
            }
            value = MediaImportHelper.extractVideoFrame(
                uriString = uriString,
                timeMs = frameBucketMs,
                context = context,
                maxDimPx = maxDimPx.coerceIn(64, 720)
            )
        }
        val bmp = frameBitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = colorFilter,
                modifier = modifier
            )
        } else {
            Box(
                modifier = modifier.background(Color(0xFF1A1A1E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun MediaClipStageContent(
    clip: MediaClip?,
    isPlaying: Boolean,
    positionMs: Long,
    isMuted: Boolean = false,
    volume: Float = 1f,
    playbackSpeed: Float = 1f,
    isFitMode: Boolean = false,
    useLiveVideoPlayer: Boolean = true,
    contentDescription: String,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    colorMatrixArray: FloatArray? = null,
    emptyLabel: String? = null,
    onPositionChanged: (Long) -> Unit = {},
    onPlaybackEnded: () -> Unit = {},
    onDurationDiscovered: (Long) -> Unit = {}
) {
    when (clip) {
        null -> {
            MediaUriThumbnail(
                uriString = null,
                isVideo = true,
                contentDescription = contentDescription,
                modifier = modifier,
                emptyLabel = emptyLabel
            )
        }
        is MediaClip.Photo -> {
            val context = LocalContext.current
            val boundedRequest = remember(clip.uriString) {
                ImageRequest.Builder(context)
                    .data(resolveCoilModel(clip.uriString))
                    .size(1440)
                    .allowHardware(true)
                    .build()
            }
            AsyncImage(
                model = boundedRequest,
                contentDescription = contentDescription,
                contentScale = if (isFitMode) ContentScale.Fit else ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = modifier
            )
        }
        is MediaClip.Video -> {
            if (useLiveVideoPlayer && !LocalInspectionMode.current) {
                Media3TextureVideoPlayer(
                    uriString = clip.uriString,
                    startTrimMs = clip.startTrimMs,
                    endTrimMs = clip.endTrimMs,
                    isPlaying = isPlaying,
                    seekPositionMs = positionMs,
                    isMuted = isMuted,
                    volume = volume,
                    playbackSpeed = playbackSpeed,
                    isFitMode = isFitMode,
                    colorMatrixArray = colorMatrixArray,
                    modifier = modifier,
                    onPositionChanged = onPositionChanged,
                    onPlaybackEnded = onPlaybackEnded,
                    onDurationDiscovered = onDurationDiscovered
                )
            } else {
                MediaUriThumbnail(
                    uriString = clip.uriString,
                    isVideo = true,
                    contentDescription = contentDescription,
                    frameTimeMs = clip.startTrimMs + positionMs,
                    contentScale = if (isFitMode) ContentScale.Fit else ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = modifier
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun Media3TextureVideoPlayer(
    uriString: String,
    startTrimMs: Long,
    endTrimMs: Long,
    isPlaying: Boolean,
    seekPositionMs: Long,
    isMuted: Boolean,
    volume: Float = 1f,
    playbackSpeed: Float = 1f,
    isFitMode: Boolean = false,
    colorMatrixArray: FloatArray? = null,
    modifier: Modifier = Modifier,
    onPositionChanged: (Long) -> Unit = {},
    onPlaybackEnded: () -> Unit = {},
    onDurationDiscovered: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val latestOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val latestOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)
    val latestOnDurationDiscovered by rememberUpdatedState(onDurationDiscovered)
    val latestSeekPositionMs by rememberUpdatedState(seekPositionMs)

    var hasPlayerError by remember(uriString) { mutableStateOf(false) }
    var discoveredDurationMs by remember(uriString) { mutableLongStateOf(0L) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val safeStartTrimMs = startTrimMs.coerceAtLeast(0L)
    val safeEndTrimMs = when {
        endTrimMs > safeStartTrimMs + 120L -> endTrimMs
        discoveredDurationMs > safeStartTrimMs + 120L -> discoveredDurationMs
        else -> Long.MAX_VALUE
    }

    val exoPlayer = remember(uriString) {
        if (uriString.isBlank()) {
            null
        } else {
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
                        if (safeStartTrimMs > 0L) {
                            seekTo(safeStartTrimMs)
                        }
                    }
            }.getOrNull()
        }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = runCatching { player.duration }.getOrDefault(C.TIME_UNSET)
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        discoveredDurationMs = dur
                        latestOnDurationDiscovered(dur)
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    latestOnPlaybackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasPlayerError = true
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching { playerViewRef?.player = null }
            runCatching {
                player.removeListener(listener)
                player.playWhenReady = false
                player.clearVideoSurface()
                player.release()
            }
        }
    }

    LaunchedEffect(exoPlayer, isMuted, volume) {
        runCatching { exoPlayer?.volume = if (isMuted) 0f else volume.coerceIn(0f, 1f) }
    }

    LaunchedEffect(exoPlayer, playbackSpeed) {
        val safeSpeed = playbackSpeed.coerceIn(0.2f, 4.0f)
        runCatching { exoPlayer?.playbackParameters = PlaybackParameters(safeSpeed) }
    }

    LaunchedEffect(exoPlayer, isPlaying, safeStartTrimMs, safeEndTrimMs, hasPlayerError) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (hasPlayerError) {
            runCatching { player.playWhenReady = false }
            return@LaunchedEffect
        }
        if (isPlaying) {
            runCatching {
                player.setSeekParameters(SeekParameters.EXACT)
                val targetStart = latestSeekPositionMs.coerceIn(safeStartTrimMs, safeEndTrimMs)
                val pos = player.currentPosition
                if (pos < safeStartTrimMs || pos >= safeEndTrimMs || abs(pos - targetStart) > 250L) {
                    player.seekTo(targetStart)
                }
                player.playWhenReady = true
            }
            while (true) {
                val cur = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
                latestOnPositionChanged(cur)
                if (safeEndTrimMs != Long.MAX_VALUE && safeEndTrimMs > safeStartTrimMs && cur >= safeEndTrimMs) {
                    runCatching {
                        player.playWhenReady = false
                        player.seekTo(safeStartTrimMs)
                    }
                    latestOnPlaybackEnded()
                    break
                }
                delay(33L)
            }
        } else {
            runCatching { player.playWhenReady = false }
        }
    }

    val pausedSeekTargetMs = if (isPlaying) Long.MIN_VALUE else seekPositionMs
    LaunchedEffect(exoPlayer, pausedSeekTargetMs, hasPlayerError) {
        if (pausedSeekTargetMs == Long.MIN_VALUE || hasPlayerError) return@LaunchedEffect
        val player = exoPlayer ?: return@LaunchedEffect
        val clampedSeek = pausedSeekTargetMs.coerceIn(safeStartTrimMs, safeEndTrimMs)
        delay(33L)
        runCatching {
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            val current = player.currentPosition
            if (abs(current - clampedSeek) > 25L) {
                player.seekTo(clampedSeek)
            }
        }
        delay(140L)
        runCatching {
            player.setSeekParameters(SeekParameters.EXACT)
            if (abs(player.currentPosition - clampedSeek) > 25L) {
                player.seekTo(clampedSeek)
            }
        }
    }

    if (hasPlayerError || exoPlayer == null) {
        MediaUriThumbnail(
            uriString = uriString,
            isVideo = true,
            contentDescription = "Video frame fallback",
            frameTimeMs = seekPositionMs.coerceAtLeast(safeStartTrimMs),
            contentScale = if (isFitMode) ContentScale.Fit else ContentScale.Crop,
            colorFilter = colorMatrixArray?.let { ColorFilter.colorMatrix(ColorMatrix(it)) },
            modifier = modifier.fillMaxSize()
        )
    } else {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_texture_player, null) as PlayerView).apply {
                    useController = false
                    player = exoPlayer
                    resizeMode = if (isFitMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    playerViewRef = this
                }
            },
            update = { playerView ->
                playerViewRef = playerView
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
                playerView.resizeMode = if (isFitMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        val effect = colorMatrixArray?.let { arr ->
                            RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(arr))
                        }
                        playerView.videoSurfaceView?.setRenderEffect(effect)
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }
}

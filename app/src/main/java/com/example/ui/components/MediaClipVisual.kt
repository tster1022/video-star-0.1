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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
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

    if (!isVideo) {
        AsyncImage(
            model = resolveCoilModel(uriString),
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = modifier
        )
    } else {
        val context = LocalContext.current
        val frameBitmap by produceState<Bitmap?>(initialValue = null, uriString, frameTimeMs) {
            value = MediaImportHelper.extractVideoFrame(uriString, frameTimeMs, context)
        }
        val bmp = frameBitmap
        if (bmp != null) {
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
            AsyncImage(
                model = resolveCoilModel(clip.uriString),
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

    val exoPlayer = remember(uriString) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            if (startTrimMs > 0L) {
                seekTo(startTrimMs)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        latestOnDurationDiscovered(dur)
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    latestOnPlaybackEnded()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isMuted, volume) {
        exoPlayer.volume = if (isMuted) 0f else volume.coerceIn(0f, 1f)
    }

    LaunchedEffect(playbackSpeed) {
        val safeSpeed = playbackSpeed.coerceIn(0.2f, 4.0f)
        exoPlayer.playbackParameters = PlaybackParameters(safeSpeed)
    }

    LaunchedEffect(isPlaying, startTrimMs, endTrimMs) {
        if (isPlaying) {
            val pos = exoPlayer.currentPosition
            if (pos < startTrimMs || pos >= endTrimMs) {
                exoPlayer.seekTo(startTrimMs)
            }
            exoPlayer.playWhenReady = true
            while (true) {
                val cur = exoPlayer.currentPosition.coerceAtLeast(0L)
                latestOnPositionChanged(cur)
                if (endTrimMs > startTrimMs && cur >= endTrimMs) {
                    exoPlayer.playWhenReady = false
                    exoPlayer.seekTo(startTrimMs)
                    latestOnPlaybackEnded()
                    break
                }
                delay(33L)
            }
        } else {
            exoPlayer.playWhenReady = false
        }
    }

    LaunchedEffect(seekPositionMs, isPlaying) {
        if (!isPlaying) {
            val current = exoPlayer.currentPosition
            if (abs(current - seekPositionMs) > 25L) {
                exoPlayer.seekTo(seekPositionMs.coerceAtLeast(0L))
            }
        }
    }

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
            }
        },
        update = { playerView ->
            if (playerView.player !== exoPlayer) {
                playerView.player = exoPlayer
            }
            playerView.resizeMode = if (isFitMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val effect = colorMatrixArray?.let { arr ->
                    RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(arr))
                }
                playerView.videoSurfaceView?.setRenderEffect(effect)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

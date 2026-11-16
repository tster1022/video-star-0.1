package com.example.ui.screens.record

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AspectRatioMode
import com.example.ui.components.ProjectSongAudioPlayer
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.theme.AdjustVideoTitleDark
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarLightToolbar
import com.example.ui.theme.VideoStarWhite
import java.util.Locale

@Composable
fun RecordVideoScreen(
    projectId: String,
    onCancel: () -> Unit,
    onVideoRecorded: (uriString: String, durationMs: Long, sourceFps: Int) -> Unit
) {
    val viewModel: RecordVideoViewModel = viewModel(
        key = "record_$projectId",
        factory = remember(projectId) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RecordVideoViewModel(projectId) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] == true
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Sync project song playback while recording (lip-sync)
    if (uiState.syncSongPlayback && uiState.songUriString != null) {
        ProjectSongAudioPlayer(
            songUriString = uiState.songUriString,
            isPlaying = uiState.isRecording,
            timelinePositionMs = uiState.elapsedRecordingMs,
            songVolume = uiState.songVolume
        )
    }

    val previewView = remember { PreviewView(context) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    if (hasCameraPermission) {
        DisposableEffect(uiState.isFrontCamera, lifecycleOwner) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)
            var boundProvider: ProcessCameraProvider? = null
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    boundProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build()
                    val capture = VideoCapture.withOutput(recorder)
                    val selector = if (uiState.isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    videoCapture = capture
                } catch (e: Exception) {
                    viewModel.postStatusMessage("تعذر تشغيل الكاميرا: ${e.message ?: "خطأ غير معروف"}")
                }
            }, executor)

            onDispose {
                try {
                    activeRecording?.stop()
                    boundProvider?.unbindAll()
                } catch (_: Exception) {
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun beginCapture() {
        val capture = videoCapture ?: run {
            viewModel.onRecordingStoppedState()
            viewModel.postStatusMessage("الكاميرا غير جاهزة بعد")
            return
        }
        val outputFile = viewModel.createOutputFile(context)
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        var pending = capture.output.prepareRecording(context, outputOptions)
        if (hasAudioPermission) {
            pending = pending.withAudioEnabled()
        }
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    viewModel.onRecordingStoppedState()
                    if (event.hasError()) {
                        viewModel.postStatusMessage("تعذر حفظ التسجيل")
                    } else {
                        viewModel.extractVideoMetadataAndFinish(context, outputFile, onVideoRecorded)
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Surface(color = VideoStarLightToolbar, shadowElevation = 1.dp) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        activeRecording?.stop()
                                        viewModel.cancelCountdown()
                                        onCancel()
                                    }
                                    .padding(horizontal = 10.dp)
                                    .testTag("record_cancel_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Cancel", color = ClassicRed, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                text = "Record Video",
                                color = AdjustVideoTitleDark,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::toggleCameraLens,
                                modifier = Modifier.size(48.dp).testTag("record_switch_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cameraswitch,
                                    contentDescription = "تبديل الكاميرا",
                                    tint = ClassicGreen
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.8.dp, color = VideoStarIosDivider)
                    }
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .geometricTriangleBackground()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val ratioModifier = when (uiState.aspectRatio) {
                        AspectRatioMode.SQUARE -> Modifier.fillMaxWidth(0.95f).aspectRatio(1f)
                        AspectRatioMode.PORTRAIT -> Modifier.fillMaxHeight(0.95f).aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.PORTRAIT_3_4 -> Modifier.fillMaxHeight(0.92f).aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.PORTRAIT_4_5 -> Modifier.fillMaxHeight(0.92f).aspectRatio(4f / 5f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.LANDSCAPE_4_3 -> Modifier.fillMaxWidth(0.95f).aspectRatio(4f / 3f)
                        AspectRatioMode.LANDSCAPE -> Modifier.fillMaxWidth(0.96f).aspectRatio(16f / 9f)
                    }

                    Box(
                        modifier = ratioModifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.5.dp, VideoStarIosDivider, RoundedCornerShape(12.dp))
                            .testTag("record_camera_stage"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (uiState.isRecording) {
                                Surface(
                                    color = ClassicRed,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 12.dp)
                                ) {
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "REC  %02d:%02d",
                                            (uiState.elapsedRecordingMs / 1000L) / 60L,
                                            (uiState.elapsedRecordingMs / 1000L) % 60L
                                        ),
                                        color = VideoStarWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (uiState.countdownRemaining > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${uiState.countdownRemaining}",
                                        color = VideoStarWhite,
                                        fontSize = 72.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Videocam,
                                    contentDescription = "Camera Permission Needed",
                                    tint = VideoStarWhite,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "يلزم منح إذن الكاميرا والميكروفون لتسجيل الفيديو",
                                    color = VideoStarWhite,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(
                                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen),
                                    modifier = Modifier.testTag("record_request_permission_button")
                                ) {
                                    Text("منح الإذن")
                                }
                            }
                        }
                    }
                }

                // Bottom Controls Bar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VideoStarWhite,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, VideoStarIosDivider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Countdown Setting
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF3F3F7),
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = viewModel::cycleCountdownSetting)
                                .testTag("record_timer_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.Timer, contentDescription = "Timer", tint = ClassicGreen, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "${uiState.countdownSecondsSetting}s",
                                    color = VideoStarInkDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Record / Stop Trigger
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .border(4.dp, if (uiState.isRecording) ClassicRed else ClassicGreen, CircleShape)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(ClassicRed)
                                .clickable(enabled = hasCameraPermission) {
                                    if (uiState.isRecording) {
                                        activeRecording?.stop()
                                    } else if (uiState.countdownRemaining > 0) {
                                        viewModel.cancelCountdown()
                                    } else {
                                        viewModel.startRecordingSequence { beginCapture() }
                                    }
                                }
                                .testTag("record_shutter_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isRecording) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop Recording", tint = VideoStarWhite, modifier = Modifier.size(30.dp))
                            }
                        }

                        // Song Sync Toggle
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.syncSongPlayback && uiState.songUriString != null) ClassicGreen.copy(alpha = 0.14f) else Color(0xFFF3F3F7),
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = viewModel::toggleSyncSong)
                                .testTag("record_song_sync_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.MusicNote,
                                    contentDescription = "Song Sync",
                                    tint = if (uiState.songUriString != null && uiState.syncSongPlayback) ClassicGreen else VideoStarInkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (uiState.songUriString != null && uiState.syncSongPlayback) "Sync" else "Mute",
                                    color = VideoStarInkDark,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

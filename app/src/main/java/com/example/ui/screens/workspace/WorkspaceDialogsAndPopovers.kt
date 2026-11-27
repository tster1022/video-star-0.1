package com.example.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.components.PrecisionRulerDialSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.MediaClip
import com.example.model.ProjectAudioConfig
import com.example.model.TimelineScene
import com.example.model.VideoProject
import com.example.ui.components.VideoStarPopover
import com.example.ui.components.formatTimeSeconds
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarWhite
import java.util.Locale

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.iosPressable
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.VideoStarPopoverDivider
import kotlin.math.roundToInt

@Composable
fun EditActionsPopover(
    expanded: Boolean,
    anchorBounds: Rect?,
    hasNextScene: Boolean,
    hasCopiedScene: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onShareClip: () -> Unit
) {
    VideoStarPopover(
        expanded = expanded,
        onDismissRequest = onDismiss,
        anchorBounds = anchorBounds,
        width = 228.dp
    ) {
        EditPopoverRow(
            label = "نسخ",
            color = VideoStarMagenta,
            testTag = "edit_action_copy",
            onClick = onCopy
        )
        HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
        EditPopoverRow(
            label = "لصق",
            color = if (hasCopiedScene) VideoStarMagenta else VideoStarInkMuted,
            testTag = "edit_action_paste",
            onClick = onPaste
        )
        HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .iosPressable(onClick = onRedo)
                    .testTag("edit_action_redo"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "إعادة",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (canRedo) VideoStarMagenta else VideoStarInkMuted
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(VideoStarPopoverDivider)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .iosPressable(onClick = onUndo)
                    .testTag("edit_action_undo"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "إلغاء",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (canUndo) VideoStarMagenta else VideoStarInkMuted
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
        EditPopoverRow(
            label = "دمج المشاهد",
            color = if (hasNextScene) VideoStarMagenta else VideoStarInkMuted,
            testTag = "edit_action_merge",
            onClick = onMerge
        )
        HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
        EditPopoverRow(
            label = "تقسيم المشهد",
            color = VideoStarMagenta,
            testTag = "edit_action_split",
            onClick = onSplit
        )
        HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
        EditPopoverRow(
            label = "حذف هذا المشهد",
            color = VideoStarMagenta,
            testTag = "edit_action_delete",
            onClick = onDelete
        )
        HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
        EditPopoverRow(
            label = "شارك مقطع الفيديو",
            color = VideoStarMagenta,
            testTag = "edit_action_share_clip",
            onClick = onShareClip
        )
    }
}

@Composable
private fun EditPopoverRow(
    label: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .iosPressable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = color
        )
    }
}

@Composable
fun ProjectInfoPopover(
    expanded: Boolean,
    anchorBounds: Rect?,
    project: VideoProject,
    onDismiss: () -> Unit,
    onExportVideo: () -> Unit
) {
    VideoStarPopover(
        expanded = expanded,
        onDismissRequest = onDismiss,
        anchorBounds = anchorBounds,
        width = 250.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = project.title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = VideoStarInkDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider(color = VideoStarIosDivider)
            InfoMetaRow("Resolution", project.resolution)
            InfoMetaRow("FPS", "${project.frameRate} fps")
            InfoMetaRow("Aspect Ratio", project.aspectRatio.label)
            InfoMetaRow("Scenes", "${project.scenes.size}")
            InfoMetaRow("Duration", formatTimeSeconds(project.totalTimelineDurationMs))
            HorizontalDivider(color = VideoStarIosDivider)
            Button(
                onClick = onExportVideo,
                colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("info_export_video_button")
            ) {
                Text(
                    text = "Export Video",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = VideoStarWhite
                )
            }
        }
    }
}

@Composable
private fun InfoMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            color = VideoStarInkMuted
        )
        Text(
            text = value,
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = VideoStarInkDark
        )
    }
}

@Composable
fun QuickAddPopover(
    expanded: Boolean,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    onRecordVideo: () -> Unit,
    onImportVideo: () -> Unit,
    onImportPhoto: () -> Unit
) {
    VideoStarPopover(
        expanded = expanded,
        onDismissRequest = onDismiss,
        anchorBounds = anchorBounds,
        width = 196.dp
    ) {
        EditPopoverRow("تسجيل الفيديو", VideoStarMagenta, "add_action_record_video", onRecordVideo)
        HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
        EditPopoverRow("استيراد الفيديو", VideoStarMagenta, "add_action_import_video", onImportVideo)
        HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
        EditPopoverRow("استيراد الصورة", VideoStarMagenta, "add_action_import_photo", onImportPhoto)
    }
}

@Composable
fun ClipModeFixedFlexibleDialog(
    onSelectFixed: () -> Unit,
    onSelectFlexible: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VideoStarWhite,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clip_mode_fixed_flexible_dialog")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "بما أن هذا هو المقطع الوحيد في المشهد الأخير، يمكن تعديله بإحدى الطريقتين التاليتين: \"ثابت\" يحافظ على طول المقطع كما هو. \"مرن\" يعني أن المقطع يمكن أن يكون أقصر أو أطول من المقطع الأصلي.",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = VideoStarInkDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 20.dp)
                    )
                    HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .iosPressable(onClick = onCancel)
                                .testTag("clip_mode_cancel_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "إلغاء",
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = VideoStarMagenta
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(VideoStarPopoverDivider)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .iosPressable(onClick = onSelectFlexible)
                                .testTag("clip_mode_flexible_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "مرن",
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = VideoStarMagenta
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(VideoStarPopoverDivider)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .iosPressable(onClick = onSelectFixed)
                                .testTag("clip_mode_fixed_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ثابت",
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = VideoStarMagenta
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive dual-handle Timeline Trim Bar rendered directly inside the Workspace timeline area:
 * Red left Start handle + Green right End handle.
 */
@Composable
fun InteractiveTimelineTrimBar(
    startMs: Long,
    endMs: Long,
    maxMs: Long,
    onRangeChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeMax = maxMs.coerceAtLeast(500L)
    val startFraction = (startMs.toFloat() / safeMax.toFloat()).coerceIn(0f, 0.95f)
    val endFraction = (endMs.toFloat() / safeMax.toFloat()).coerceIn(startFraction + 0.03f, 1f)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = VideoStarWhite,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(0.8.dp, VideoStarIosDivider, RoundedCornerShape(10.dp))
            .testTag("interactive_timeline_trim_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Start: ${String.format(Locale.US, "%.2fs", startMs / 1000f)}",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = ClassicRed
                )
                Text(
                    text = "Duration: ${String.format(Locale.US, "%.2fs", (endMs - startMs).coerceAtLeast(200L) / 1000f)}",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = VideoStarInkDark
                )
                Text(
                    text = "End: ${String.format(Locale.US, "%.2fs", endMs / 1000f)}",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = ClassicGreen
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val density = LocalDensity.current
                val handleW = 22.dp
                val handlePx = with(density) { handleW.toPx() }
                val totalPx = with(density) { maxWidth.toPx() }.coerceAtLeast(handlePx * 3f)
                val usablePx = (totalPx - handlePx).coerceAtLeast(1f)
                val leftX = (startFraction * usablePx).roundToInt()
                val rightX = (endFraction * usablePx).roundToInt()

                // Inactive track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFFE5E5EA))
                )

                // Active trimmed region highlight
                val spanPx = (rightX - leftX + handlePx.roundToInt()).coerceAtLeast(12)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftX, 0) }
                        .width(with(density) { spanPx.toDp() })
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ClassicGreen.copy(alpha = 0.25f))
                        .border(1.5.dp, ClassicGreen, RoundedCornerShape(4.dp))
                )

                // Red Start Trim Handle
                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftX, 0) }
                        .width(handleW)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .shadow(3.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClassicRed)
                        .pointerInput(safeMax, endMs) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaMs = ((dragAmount / usablePx) * safeMax).toLong()
                                onRangeChange((startMs + deltaMs).coerceAtLeast(0L), endMs)
                            }
                        }
                        .testTag("trim_start_handle"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(VideoStarWhite, CircleShape)
                    )
                }

                // Green End Trim Handle
                Box(
                    modifier = Modifier
                        .offset { IntOffset(rightX, 0) }
                        .width(handleW)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .shadow(3.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClassicGreen)
                        .pointerInput(safeMax, startMs) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaMs = ((dragAmount / usablePx) * safeMax).toLong()
                                onRangeChange(startMs, (endMs + deltaMs).coerceAtMost(safeMax))
                            }
                        }
                        .testTag("trim_end_handle"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(VideoStarWhite, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioManagerDialog(
    audioConfig: ProjectAudioConfig,
    onUpdateConfig: (ProjectAudioConfig) -> Unit,
    onPickSongFile: () -> Unit,
    onExtractFromVideo: () -> Unit,
    onDismiss: () -> Unit
) {
    val presetTracks = remember {
        listOf(
            "إيقاع إلكتروني 128 BPM",
            "نبض سينثويف - 124 BPM",
            "باس حضري - 110 BPM",
            "لحن لو-فاي هادئ - 86 BPM"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoStarWhite,
        title = { Text("إدارة الصوت والموسيقى", color = VideoStarInkDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "الأغنية الحالية: ${audioConfig.songTitle}",
                    fontWeight = FontWeight.SemiBold,
                    color = VideoStarMagenta
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickSongFile,
                        colors = ButtonDefaults.buttonColors(containerColor = ClassicRed),
                        modifier = Modifier.weight(1f).testTag("audio_select_song_button")
                    ) {
                        Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Select Song", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onExtractFromVideo,
                        modifier = Modifier.weight(1f).testTag("audio_extract_video_button")
                    ) {
                        Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(16.dp), tint = ClassicGreen)
                        Spacer(Modifier.width(4.dp))
                        Text("From Video", fontSize = 12.sp, color = ClassicGreen)
                    }
                }

                HorizontalDivider(color = VideoStarIosDivider)

                PrecisionRulerDialSlider(
                    label = "Clip Audio",
                    valueText = "${(audioConfig.clipVolume * 100).toInt()}%",
                    value = audioConfig.clipVolume,
                    onValueChange = { onUpdateConfig(audioConfig.copy(clipVolume = it)) },
                    valueRange = 0f..1f,
                    defaultValue = 1f,
                    height = 40.dp
                )

                PrecisionRulerDialSlider(
                    label = "Song Vol",
                    valueText = "${(audioConfig.songVolume * 100).toInt()}%",
                    value = audioConfig.songVolume,
                    onValueChange = { onUpdateConfig(audioConfig.copy(songVolume = it)) },
                    valueRange = 0f..1f,
                    defaultValue = 0.8f,
                    height = 40.dp
                )

                Text("أو اختر إيقاعاً جاهزاً:", fontSize = 12.sp, color = VideoStarInkMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetTracks.forEach { track ->
                        val selected = audioConfig.songTitle == track && audioConfig.songUriString == null
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) VideoStarMagenta.copy(alpha = 0.14f) else Color(0xFFF3F3F7),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (selected) 1.dp else 0.dp,
                                    color = if (selected) VideoStarMagenta else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onUpdateConfig(audioConfig.copy(songTitle = track, songUriString = null))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = VideoStarMagenta, modifier = Modifier.size(14.dp))
                                Text(track, fontSize = 11.sp, color = VideoStarInkDark)
                                if (selected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = VideoStarMagenta, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen)
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }
    )
}

@Composable
fun TrimActiveSceneDialog(
    scene: TimelineScene,
    onSave: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val totalDurMs = when (val c = scene.clip) {
        is MediaClip.Video -> c.durationMs.coerceAtLeast(500L)
        is MediaClip.Photo -> 30_000L
        null -> 10_000L
    }
    val initialStart = (scene.clip as? MediaClip.Video)?.startTrimMs ?: 0L
    val initialEnd = (scene.clip as? MediaClip.Video)?.endTrimMs ?: scene.durationMs

    var startMs by remember(scene.id) { mutableFloatStateOf(initialStart.toFloat()) }
    var endMs by remember(scene.id) { mutableFloatStateOf(initialEnd.coerceAtLeast(initialStart + 300L).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoStarWhite,
        title = { Text("قص المشهد (${scene.title})", color = VideoStarInkDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrecisionRulerDialSlider(
                    label = "Start",
                    valueText = String.format(Locale.US, "%.2fs", startMs / 1000f),
                    value = startMs,
                    onValueChange = { startMs = it.coerceAtMost(endMs - 250f).coerceAtLeast(0f) },
                    valueRange = 0f..totalDurMs.toFloat(),
                    height = 40.dp
                )
                PrecisionRulerDialSlider(
                    label = "End",
                    valueText = String.format(Locale.US, "%.2fs", endMs / 1000f),
                    value = endMs,
                    onValueChange = { endMs = it.coerceAtLeast(startMs + 250f).coerceAtMost(totalDurMs.toFloat()) },
                    valueRange = 250f..totalDurMs.toFloat(),
                    height = 40.dp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(startMs.toLong(), endMs.toLong()) },
                colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen)
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel_ar), color = VideoStarInkMuted)
            }
        }
    )
}

@Composable
fun ExportProgressDialog(progress: Float) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = VideoStarWhite,
        title = { Text("جارٍ تصدير الفيديو (MP4)...", color = VideoStarInkDark) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = ClassicGreen,
                    trackColor = VideoStarIosDivider,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                Text(
                    text = "${(progress * 100).toInt()}% — يتم معالجة المؤثرات والتحجيم والملف النهائي",
                    color = VideoStarInkMuted,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {}
    )
}

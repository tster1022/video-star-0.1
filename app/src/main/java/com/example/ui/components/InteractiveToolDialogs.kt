package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AspectRatioMode
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarWhite

@Composable
fun ProjectEditorDialog(
    isEdit: Boolean,
    initialTitle: String,
    initialRatio: AspectRatioMode,
    onDismiss: () -> Unit,
    onConfirm: (String, AspectRatioMode) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var selectedRatio by remember(initialRatio) { mutableStateOf(initialRatio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoStarWhite,
        titleContentColor = VideoStarInkDark,
        textContentColor = VideoStarInkDark,
        title = {
            Text(
                text = stringResource(
                    if (isEdit) R.string.dialog_edit_project_title else R.string.dialog_new_project_title
                ),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.label_project_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_name_input")
                )
                Text(
                    text = stringResource(R.string.label_aspect_ratio),
                    style = MaterialTheme.typography.labelLarge,
                    color = VideoStarInkDark
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AspectRatioMode.entries.forEach { ratio ->
                        val selected = selectedRatio == ratio
                        FilterChip(
                            selected = selected,
                            onClick = { selectedRatio = ratio },
                            label = { Text(ratio.label, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VideoStarMagenta,
                                selectedLabelColor = VideoStarWhite
                            ),
                            modifier = Modifier.testTag("ratio_chip_${ratio.name.lowercase()}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, selectedRatio) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VideoStarMagenta,
                    contentColor = VideoStarWhite
                ),
                modifier = Modifier.testTag("project_dialog_confirm")
            ) {
                Text(stringResource(if (isEdit) R.string.btn_save else R.string.btn_create_ar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel_ar), color = VideoStarInkMuted)
            }
        }
    )
}

@Composable
fun DeleteProjectDialog(
    projectTitle: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoStarWhite,
        titleContentColor = VideoStarInkDark,
        textContentColor = VideoStarInkDark,
        title = {
            Text(
                text = stringResource(R.string.dialog_delete_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_delete_message, projectTitle),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VideoStarMagenta,
                    contentColor = VideoStarWhite
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(stringResource(R.string.btn_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel_ar), color = VideoStarInkMuted)
            }
        }
    )
}

@Composable
fun SettingsDialog(
    exportResolution: String,
    fpsOption: Int,
    watermarkEnabled: Boolean,
    onResolutionChange: (String) -> Unit,
    onFpsChange: (Int) -> Unit,
    onWatermarkToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoStarWhite,
        titleContentColor = VideoStarInkDark,
        textContentColor = VideoStarInkDark,
        title = { Text("إعدادات الاستوديو", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("دقة التصدير الافتراضية", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("720p", "1080p", "4K").forEach { res ->
                        FilterChip(
                            selected = exportResolution == res,
                            onClick = { onResolutionChange(res) },
                            label = { Text(res) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VideoStarMagenta,
                                selectedLabelColor = VideoStarWhite
                            )
                        )
                    }
                }
                Text("معدل الإطارات (FPS)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60).forEach { fps ->
                        FilterChip(
                            selected = fpsOption == fps,
                            onClick = { onFpsChange(fps) },
                            label = { Text("$fps fps") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VideoStarMagenta,
                                selectedLabelColor = VideoStarWhite
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("علامة Video Star المائية", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = watermarkEnabled,
                        onCheckedChange = onWatermarkToggle,
                        colors = SwitchDefaults.colors(checkedThumbColor = VideoStarWhite, checkedTrackColor = VideoStarMagenta)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
            ) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}

@Composable
fun AudioLibraryDialog(
    selectedTrack: String,
    onSelectTrack: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tracks = remember {
        listOf(
            "نبض سينثويف - 124 BPM",
            "إيقاع إلكتروني - 128 BPM",
            "باس حضري - 110 BPM",
            "لحن لو-فاي هادئ - 86 BPM"
        )
    }
    var previewingTrack by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoStarWhite,
        titleContentColor = VideoStarInkDark,
        textContentColor = VideoStarInkDark,
        title = { Text("المكتبة الصوتية", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tracks.forEach { track ->
                    val isSelected = selectedTrack == track
                    val isPreviewing = previewingTrack == track
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) VideoStarMagenta.copy(alpha = 0.1f) else VideoStarInkDark.copy(alpha = 0.04f))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) VideoStarMagenta else VideoStarWhite,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectTrack(track) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = VideoStarMagenta,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = track,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(VideoStarMagenta.copy(alpha = 0.15f))
                                .clickable {
                                    previewingTrack = if (isPreviewing) null else track
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPreviewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = VideoStarMagenta,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (isSelected) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = VideoStarMagenta,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }
    )
}

@Composable
fun QuickActionsOrHelpDialog(
    title: String,
    items: List<Pair<String, String>>,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoStarWhite,
        titleContentColor = VideoStarInkDark,
        textContentColor = VideoStarInkDark,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { (itemTitle, itemDesc) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VideoStarInkDark.copy(alpha = 0.04f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = itemTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = VideoStarMagenta
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = itemDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VideoStarInkMuted
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (actionLabel != null && onActionClick != null) {
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
                ) {
                    Text(actionLabel)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
                ) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        },
        dismissButton = {
            if (actionLabel != null && onActionClick != null) {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_close), color = VideoStarInkMuted)
                }
            }
        }
    )
}

package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AspectRatioMode
import com.example.model.CompressionQuality
import com.example.model.VideoProject
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarIosPink
import com.example.ui.theme.VideoStarIosSubtitle
import com.example.ui.theme.VideoStarIosTitle
import com.example.ui.theme.VideoStarLightSurface
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarWhite

/**
 * Arabic Bottom Sheet ("مشروع جديد") opened only when tapping the (+) button on Home.
 * Dismissing via close button, back, swipe, or outside tap returns to Home without creating a project.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectBottomSheet(
    selectedFrameRate: Int,
    selectedAspectRatio: AspectRatioMode,
    selectedResolution: String,
    selectedCompression: CompressionQuality,
    selectedPresetName: String,
    savedProjects: List<VideoProject>,
    isCompressionSheetVisible: Boolean,
    onFrameRateSelected: (Int) -> Unit,
    onAspectRatioSelected: (AspectRatioMode) -> Unit,
    onResolutionSelected: (String) -> Unit,
    onCompressionSelected: (CompressionQuality) -> Unit,
    onOpenCompressionSheet: () -> Unit,
    onCloseCompressionSheet: () -> Unit,
    onApplyPreset: (name: String, fps: Int, ratio: AspectRatioMode, res: String, compression: CompressionQuality) -> Unit,
    onStepAspectRatio: (Int) -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenSavedProject: (String) -> Unit,
    onSelectSongClick: () -> Unit,
    onEditVideoClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VideoStarWhite,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VideoStarIosDivider)
            )
        },
        contentWindowInsets = { WindowInsets(0) },
        modifier = Modifier.testTag("new_project_bottom_sheet")
    ) {
        NewProjectModalSection(
            selectedFrameRate = selectedFrameRate,
            selectedAspectRatio = selectedAspectRatio,
            selectedResolution = selectedResolution,
            selectedCompression = selectedCompression,
            selectedPresetName = selectedPresetName,
            savedProjects = savedProjects,
            isCompressionSheetVisible = isCompressionSheetVisible,
            onFrameRateSelected = onFrameRateSelected,
            onAspectRatioSelected = onAspectRatioSelected,
            onResolutionSelected = onResolutionSelected,
            onCompressionSelected = onCompressionSelected,
            onOpenCompressionSheet = onOpenCompressionSheet,
            onCloseCompressionSheet = onCloseCompressionSheet,
            onApplyPreset = onApplyPreset,
            onStepAspectRatio = onStepAspectRatio,
            onToggleOrientation = onToggleOrientation,
            onOpenSavedProject = onOpenSavedProject,
            onSelectSongClick = onSelectSongClick,
            onEditVideoClick = onEditVideoClick,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}

@Composable
fun NewProjectModalSection(
    selectedFrameRate: Int,
    selectedAspectRatio: AspectRatioMode,
    selectedResolution: String,
    selectedCompression: CompressionQuality,
    selectedPresetName: String,
    savedProjects: List<VideoProject>,
    isCompressionSheetVisible: Boolean,
    onFrameRateSelected: (Int) -> Unit,
    onAspectRatioSelected: (AspectRatioMode) -> Unit,
    onResolutionSelected: (String) -> Unit,
    onCompressionSelected: (CompressionQuality) -> Unit,
    onOpenCompressionSheet: () -> Unit,
    onCloseCompressionSheet: () -> Unit,
    onApplyPreset: (name: String, fps: Int, ratio: AspectRatioMode, res: String, compression: CompressionQuality) -> Unit,
    onStepAspectRatio: (Int) -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenSavedProject: (String) -> Unit,
    onSelectSongClick: () -> Unit,
    onEditVideoClick: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPresetsSheet by remember { mutableStateOf(false) }
    var showFormatInfo by remember { mutableStateOf(false) }

    val frameRates = remember { listOf(24, 25, 30, 50, 60) }
    val aspectRatios = remember {
        listOf(
            AspectRatioMode.PORTRAIT,      // 9:16
            AspectRatioMode.PORTRAIT_4_5,  // 4:5
            AspectRatioMode.SQUARE,        // 1:1
            AspectRatioMode.LANDSCAPE_4_3, // 4:3
            AspectRatioMode.LANDSCAPE      // 16:9
        )
    }
    val resolutions = remember { listOf("720p", "1080p", "4K") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp)
            .testTag("new_project_overlay"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Row inside Sheet: Close Button + Title ("مشروع جديد") + Preset Folder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .testTag("new_project_close_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.btn_close),
                    tint = VideoStarInkMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "مشروع جديد",
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = VideoStarIosTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("new_project_card_title")
            )

            NewProjectQuickControlsRow(
                presetLabel = selectedPresetName,
                onSavedPresetsClick = { showPresetsSheet = true },
                onDefaultClick = {
                    onApplyPreset(
                        "افتراضي",
                        30,
                        AspectRatioMode.LANDSCAPE,
                        "1080p",
                        CompressionQuality.BALANCED
                    )
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // Top Bar Controls: Quick orientation arrows & preset pill
        NewProjectTopBarAboveModal(
            presetLabel = selectedPresetName,
            onSavedPresetsClick = { showPresetsSheet = true },
            onDefaultClick = {
                onApplyPreset(
                    "افتراضي",
                    30,
                    AspectRatioMode.LANDSCAPE,
                    "1080p",
                    CompressionQuality.BALANCED
                )
            },
            onPrevRatioClick = { onStepAspectRatio(-1) },
            onOrientationClick = onToggleOrientation,
            onNextRatioClick = { onStepAspectRatio(1) }
        )

        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: معدل الإطارات (24 / 25 / 30 / 50 / 60 — default 30)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "معدل الإطارات",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = VideoStarIosSubtitle
                )
                IosSegmentedControl(
                    items = frameRates,
                    selectedItem = selectedFrameRate,
                    onItemSelected = onFrameRateSelected,
                    itemTagPrefix = "fps_segment"
                ) { fps, _ ->
                    Text(
                        text = fps.toString(),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = VideoStarIosPink
                    )
                }
            }

            // Section 2: شكل الفيديو (9:16، 4:5، 1:1، 4:3، 16:9) + info icon
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VideoStarLightSurface)
                                .border(0.8.dp, VideoStarIosPink.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .clickable { showFormatInfo = true }
                                .testTag("video_format_info_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "معلومات شكل الفيديو",
                                tint = VideoStarIosPink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "شكل الفيديو",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = VideoStarIosSubtitle,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                IosSegmentedControl(
                    items = aspectRatios,
                    selectedItem = selectedAspectRatio,
                    onItemSelected = onAspectRatioSelected,
                    itemTagPrefix = "format_segment",
                    segmentHeight = 46.dp
                ) { ratioMode, _ ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AspectRatioGlyph(
                            aspectRatioMode = ratioMode,
                            strokeColor = VideoStarIosPink
                        )
                        Text(
                            text = ratioMode.label,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = VideoStarIosPink
                        )
                    }
                }
            }

            // Section 3: الدّقة (720p / 1080p / 4K)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "الدّقة",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = VideoStarIosSubtitle
                )
                val normalizedRes = resolutions.find { it.equals(selectedResolution, ignoreCase = true) } ?: "1080p"
                IosSegmentedControl(
                    items = resolutions,
                    selectedItem = normalizedRes,
                    onItemSelected = onResolutionSelected,
                    itemTagPrefix = "resolution_segment"
                ) { res, _ ->
                    Text(
                        text = res,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = VideoStarIosPink
                    )
                }
            }

            // Section 4: إعدادات الضغط
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = VideoStarLightSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenCompressionSheet)
                    .testTag("compression_settings_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إعدادات الضغط",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = VideoStarIosPink
                    )
                    Text(
                        text = selectedCompression.arabicLabel,
                        fontFamily = CairoFontFamily,
                        fontSize = 12.sp,
                        color = VideoStarInkMuted
                    )
                }
            }
        }

        // Bottom Action Split Row: "تحرير الفيديو" و "حدد الأغنية"
        HorizontalDivider(
            thickness = 1.dp,
            color = VideoStarIosDivider
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .iosPressable(onClick = onEditVideoClick)
                        .testTag("new_project_edit_video_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "تحرير الفيديو",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = VideoStarIosPink
                    )
                }

                VerticalDivider(
                    thickness = 1.dp,
                    color = VideoStarIosDivider,
                    modifier = Modifier.fillMaxHeight()
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .iosPressable(onClick = onSelectSongClick)
                        .testTag("new_project_select_song_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "حدد الأغنية",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = VideoStarIosPink
                    )
                }
            }
        }
    }

    if (isCompressionSheetVisible) {
        AlertDialog(
            onDismissRequest = onCloseCompressionSheet,
            containerColor = VideoStarWhite,
            title = {
                Text(
                    text = "إعدادات الضغط",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = VideoStarInkDark
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompressionQuality.entries.forEach { quality ->
                        val isSelected = quality == selectedCompression
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) VideoStarIosPink.copy(alpha = 0.12f)
                                    else VideoStarLightSurface
                                )
                                .border(
                                    width = if (isSelected) 1.2.dp else 0.dp,
                                    color = if (isSelected) VideoStarIosPink else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onCompressionSelected(quality) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("compression_option_${quality.name.lowercase()}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = quality.arabicLabel,
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = VideoStarInkDark
                                )
                                Text(
                                    text = quality.subtitle,
                                    fontFamily = CairoFontFamily,
                                    fontSize = 12.sp,
                                    color = VideoStarInkMuted
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = VideoStarIosPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onCloseCompressionSheet,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
                ) {
                    Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily)
                }
            }
        )
    }

    if (showPresetsSheet) {
        val presets = listOf(
            Triple("افتراضي", 30, AspectRatioMode.LANDSCAPE to "1080p"),
            Triple("ريلز وتيك توك 60fps", 60, AspectRatioMode.PORTRAIT to "1080p"),
            Triple("سينما يوتيوب 4K", 24, AspectRatioMode.LANDSCAPE to "4K"),
            Triple("انستغرام 4:5", 30, AspectRatioMode.PORTRAIT_4_5 to "1080p"),
            Triple("مربع 1:1", 30, AspectRatioMode.SQUARE to "1080p")
        )
        AlertDialog(
            onDismissRequest = { showPresetsSheet = false },
            containerColor = VideoStarWhite,
            title = {
                Text(
                    text = "القوالب والمشاريع المحفوظة",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = VideoStarInkDark
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (name, fps, formatAndRes) ->
                        val (ratio, res) = formatAndRes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(VideoStarLightSurface)
                                .clickable {
                                    onApplyPreset(name, fps, ratio, res, CompressionQuality.BALANCED)
                                    showPresetsSheet = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontFamily = CairoFontFamily, color = VideoStarInkDark, fontWeight = FontWeight.SemiBold)
                            Text("${ratio.label} • ${fps}fps • $res", fontFamily = InterFontFamily, color = VideoStarIosPink, fontSize = 12.sp)
                        }
                    }

                    if (savedProjects.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = VideoStarIosDivider)
                        Text(
                            text = "فتح مشروع محفوظ",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = VideoStarInkMuted
                        )
                        savedProjects.take(5).forEach { proj ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VideoStarIosPink.copy(alpha = 0.08f))
                                    .clickable {
                                        showPresetsSheet = false
                                        onOpenSavedProject(proj.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(proj.title, fontFamily = CairoFontFamily, color = VideoStarInkDark, fontWeight = FontWeight.SemiBold)
                                Text("${proj.aspectRatio.label} • ${proj.frameRate}fps", fontFamily = InterFontFamily, color = VideoStarIosPink, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPresetsSheet = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
                ) {
                    Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily)
                }
            }
        )
    }

    if (showFormatInfo) {
        AlertDialog(
            onDismissRequest = { showFormatInfo = false },
            containerColor = VideoStarWhite,
            title = {
                Text(
                    text = "دليل شكل الفيديو",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = VideoStarInkDark
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• 9:16 — عمودي كامل (ريلز وتيك توك)", fontFamily = CairoFontFamily, color = VideoStarInkDark)
                    Text("• 4:5 — عمودي لمنشورات إنستغرام", fontFamily = CairoFontFamily, color = VideoStarInkDark)
                    Text("• 1:1 — مربع متساوي", fontFamily = CairoFontFamily, color = VideoStarInkDark)
                    Text("• 4:3 — أفقي كلاسيكي متوسط", fontFamily = CairoFontFamily, color = VideoStarInkDark)
                    Text("• 16:9 — فيديو أفقي عريض (الافتراضي / يوتيوب)", fontFamily = CairoFontFamily, color = VideoStarInkDark)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFormatInfo = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
                ) {
                    Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily)
                }
            }
        )
    }
}

@Composable
private fun NewProjectQuickControlsRow(
    presetLabel: String,
    onSavedPresetsClick: () -> Unit,
    onDefaultClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onSavedPresetsClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("new_project_presets_button")
        ) {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = "القوالب والمشاريع",
                    tint = VideoStarIosPink,
                    modifier = Modifier.size(24.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 1.dp)
                        .size(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(VideoStarLightSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = VideoStarIosPink,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewProjectTopBarAboveModal(
    presetLabel: String,
    onSavedPresetsClick: () -> Unit,
    onDefaultClick: () -> Unit,
    onPrevRatioClick: () -> Unit,
    onOrientationClick: () -> Unit,
    onNextRatioClick: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Rounded rectangle button: افتراضي
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VideoStarLightSurface)
                    .border(1.2.dp, VideoStarIosPink, RoundedCornerShape(8.dp))
                    .clickable(onClick = onDefaultClick)
                    .padding(horizontal = 14.dp)
                    .testTag("new_project_default_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (presetLabel == "افتراضي" || presetLabel == "Default") "افتراضي" else presetLabel,
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = VideoStarIosPink,
                    maxLines = 1
                )
            }

            // Orientation quick stepper: < [Phone] >
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .testTag("new_project_orientation_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onPrevRatioClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "النسبة السابقة",
                        tint = VideoStarIosPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onOrientationClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Smartphone,
                        contentDescription = "تبديل الاتجاه",
                        tint = VideoStarIosPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNextRatioClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "النسبة التالية",
                        tint = VideoStarIosPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AspectRatioGlyph(
    aspectRatioMode: AspectRatioMode,
    strokeColor: Color
) {
    val (wDp, hDp) = when (aspectRatioMode) {
        AspectRatioMode.PORTRAIT -> 11.dp to 19.dp
        AspectRatioMode.PORTRAIT_4_5 -> 13.dp to 17.dp
        AspectRatioMode.PORTRAIT_3_4 -> 14.dp to 18.dp
        AspectRatioMode.SQUARE -> 16.dp to 16.dp
        AspectRatioMode.LANDSCAPE_4_3 -> 19.dp to 15.dp
        AspectRatioMode.LANDSCAPE -> 22.dp to 13.dp
    }

    Canvas(modifier = Modifier.size(width = 24.dp, height = 20.dp)) {
        val wPx = wDp.toPx()
        val hPx = hDp.toPx()
        val left = (size.width - wPx) / 2f
        val top = (size.height - hPx) / 2f
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(left, top),
            size = Size(wPx, hPx),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

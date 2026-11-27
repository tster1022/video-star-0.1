package com.example.ui.screens.packs

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.packs.BundledCustomShaders
import com.example.engine.packs.CustomPackInfo
import com.example.engine.packs.CustomPackRepository
import com.example.engine.packs.ImportedFontAsset
import com.example.engine.packs.ImportedShaderEffect
import com.example.engine.packs.ImportedXmlEffectPreset
import com.example.engine.packs.PackImportStatus
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.InterFontFamily
import kotlinx.coroutines.delay

private val PackStudioBg = Color(0xFF0E0C1A)
private val PackStudioCard = Color(0xFF17132B)
private val PackStudioSurfaceAlt = Color(0xFF1F1936)
private val PackStudioBorder = Color(0xFF2F2652)
private val PackStudioPurple = Color(0xFFA855F7)
private val PackStudioNeonGreen = Color(0xFF00E676)
private val PackStudioCyan = Color(0xFF38BDF8)
private val PackStudioMuted = Color(0xFF9EA6BC)
private val PackStudioError = Color(0xFFF87171)

private enum class PackManagerTab(val label: String) {
    ALL("All"),
    XML_PRESETS("XML Presets"),
    GLSL_SHADERS("GLSL Shaders"),
    FONTS("Fonts"),
    PACKS("Packs")
}

@Composable
fun PackImportStudioSheet(
    initialTab: String = "ALL",
    onDismiss: () -> Unit,
    onApplyEffectItem: ((String) -> Unit)? = null,
    onSelectFontId: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val packs by CustomPackRepository.packs.collectAsStateWithLifecycle()
    val shaders by CustomPackRepository.importedShaders.collectAsStateWithLifecycle()
    val presets by CustomPackRepository.importedPresets.collectAsStateWithLifecycle()
    val fonts by CustomPackRepository.importedFonts.collectAsStateWithLifecycle()
    val importStatus by CustomPackRepository.importStatus.collectAsStateWithLifecycle()

    var selectedTab by remember(initialTab) {
        mutableStateOf(
            when (initialTab.uppercase()) {
                "XML" -> PackManagerTab.XML_PRESETS
                "GLSL", "SHADERS" -> PackManagerTab.GLSL_SHADERS
                "FONTS" -> PackManagerTab.FONTS
                "PACKS" -> PackManagerTab.PACKS
                else -> PackManagerTab.ALL
            }
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pendingExportPackId by remember { mutableStateOf<String?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedbackBanner) {
        if (feedbackBanner != null) {
            delay(2200L)
            feedbackBanner = null
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            CustomPackRepository.importFromSafTree(context, treeUri)
        }
    }

    val filesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            CustomPackRepository.importFromDocumentUris(context, uris)
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { outputUri: Uri? ->
        if (outputUri != null) {
            CustomPackRepository.exportToZipUri(context, outputUri, pendingExportPackId)
            feedbackBanner = "تم تصدير حزمة المؤثرات بصيغة ZIP"
        }
        pendingExportPackId = null
    }

    val handleClose: () -> Unit = {
        if (showPasteDialog) {
            showPasteDialog = false
        } else {
            CustomPackRepository.clearImportStatus()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = handleClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(enabled = true) { handleClose() }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PackStudioBg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .testTag("pack_import_studio_sheet")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClick = handleClose)
                                .testTag("pack_studio_close_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "إغلاق مدير الحزم",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "My Effects • AM Packs Studio",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${presets.size} XML • ${shaders.size} GLSL • ${fonts.size} Fonts",
                                fontFamily = InterFontFamily,
                                fontSize = 11.sp,
                                color = PackStudioNeonGreen
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    pendingExportPackId = null
                                    exportZipLauncher.launch("starstudio_my_effects.zip")
                                }
                                .testTag("pack_export_zip_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileUpload,
                                contentDescription = "تصدير الكل ZIP",
                                tint = PackStudioCyan,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    // Primary Import Action Strip (Folder SAF | ZIP/Files | Paste XML/GLSL)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImportActionTile(
                            icon = Icons.Outlined.FolderOpen,
                            title = "مجلد المؤثرات",
                            subtitle = "SAF Folder (900+)",
                            accent = PackStudioNeonGreen,
                            testTag = "pack_import_folder_btn",
                            onClick = { folderLauncher.launch(null) },
                            modifier = Modifier.weight(1f)
                        )
                        ImportActionTile(
                            icon = Icons.Rounded.Add,
                            title = "ملفات / ZIP",
                            subtitle = "XML • GLSL • TTF",
                            accent = PackStudioPurple,
                            testTag = "pack_import_files_btn",
                            onClick = { filesLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        )
                        ImportActionTile(
                            icon = Icons.Outlined.ContentPaste,
                            title = "نسخ ولصق",
                            subtitle = "Paste XML/GLSL",
                            accent = PackStudioCyan,
                            testTag = "pack_paste_code_btn",
                            onClick = { showPasteDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 1-Tap Bundled All_Shaders Installer Card (when not yet installed)
                    val hasBundledPack = packs.any { it.id == BundledCustomShaders.BUNDLED_PACK_ID }
                    if (!hasBundledPack) {
                        Surface(
                            color = Color(0xFF1F1638),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PackStudioPurple.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val count = CustomPackRepository.installBundledShaderPack(context)
                                    feedbackBanner = "تم تثبيت $count شادر من حزمة All_Shaders في My Effects"
                                }
                                .testTag("pack_install_bundled_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(PackStudioPurple.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = "تثبيت الحزمة المرفقة",
                                        tint = PackStudioNeonGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "تثبيت حزمة All_Shaders المرفقة (10 GLSL)",
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Water Ripple • Precision Grid • Checker • Linear/Radial/Sweep Grad • Invert • Mask",
                                        fontFamily = InterFontFamily,
                                        fontSize = 10.sp,
                                        color = PackStudioMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    color = PackStudioNeonGreen,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "تثبيت",
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF04140B),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Live Import Status / Progress Card
                    when (val status = importStatus) {
                        PackImportStatus.Idle -> Unit
                        is PackImportStatus.Scanning -> {
                            Surface(
                                color = PackStudioCard,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PackStudioPurple),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("pack_import_progress_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "جارٍ استيراد: ${status.packName}",
                                            fontFamily = CairoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${status.processedCount} / ${status.totalEstimate}",
                                            fontFamily = InterFontFamily,
                                            fontSize = 11.sp,
                                            color = PackStudioNeonGreen
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = {
                                            (status.processedCount.toFloat() / status.totalEstimate.coerceAtLeast(1).toFloat())
                                                .coerceIn(0f, 1f)
                                        },
                                        color = PackStudioNeonGreen,
                                        trackColor = PackStudioSurfaceAlt,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        text = status.currentFile,
                                        fontFamily = InterFontFamily,
                                        fontSize = 10.sp,
                                        color = PackStudioMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        is PackImportStatus.Completed -> {
                            val sum = status.summary
                            Surface(
                                color = PackStudioNeonGreen.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PackStudioNeonGreen.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "اكتمل استيراد ${sum.packName}",
                                            fontFamily = CairoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = PackStudioNeonGreen
                                        )
                                        Text(
                                            text = "+${sum.presetsAdded} XML • +${sum.shadersAdded} GLSL • +${sum.fontsAdded} Fonts" +
                                                if (sum.skippedUnsupported > 0) " (${sum.skippedUnsupported} skipped)" else "",
                                            fontFamily = InterFontFamily,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { CustomPackRepository.clearImportStatus() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Close, "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        is PackImportStatus.Failed -> {
                            Surface(
                                color = PackStudioError.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PackStudioError),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = status.message,
                                        fontFamily = CairoFontFamily,
                                        fontSize = 12.sp,
                                        color = PackStudioError,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { CustomPackRepository.clearImportStatus() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Close, "Dismiss", tint = PackStudioError, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = PackStudioMuted)
                        },
                        placeholder = {
                            Text(
                                text = "ابحث في XML / GLSL / الخطوط / الحزم...",
                                fontFamily = CairoFontFamily,
                                fontSize = 12.sp,
                                color = PackStudioMuted
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PackStudioPurple,
                            unfocusedBorderColor = PackStudioBorder,
                            focusedContainerColor = PackStudioCard,
                            unfocusedContainerColor = PackStudioCard
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("pack_search_input")
                    )

                    // Filter Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PackManagerTab.entries.forEach { tab ->
                            val count = when (tab) {
                                PackManagerTab.ALL -> presets.size + shaders.size + fonts.size
                                PackManagerTab.XML_PRESETS -> presets.size
                                PackManagerTab.GLSL_SHADERS -> shaders.size
                                PackManagerTab.FONTS -> fonts.size
                                PackManagerTab.PACKS -> packs.size
                            }
                            val selected = selectedTab == tab
                            Surface(
                                color = if (selected) PackStudioPurple else PackStudioCard,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, if (selected) PackStudioPurple else PackStudioBorder),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { selectedTab = tab }
                                    .testTag("pack_tab_${tab.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${tab.label} ($count)",
                                        fontFamily = InterFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    val q = searchQuery.trim().lowercase()
                    val filteredPresets = remember(presets, q) {
                        if (q.isEmpty()) presets
                        else presets.filter {
                            it.name.lowercase().contains(q) ||
                                it.fileName.lowercase().contains(q) ||
                                it.packName.lowercase().contains(q)
                        }
                    }
                    val filteredShaders = remember(shaders, q) {
                        if (q.isEmpty()) shaders
                        else shaders.filter {
                            it.name.lowercase().contains(q) ||
                                it.fileName.lowercase().contains(q) ||
                                it.packName.lowercase().contains(q)
                        }
                    }
                    val filteredFonts = remember(fonts, q) {
                        if (q.isEmpty()) fonts
                        else fonts.filter {
                            it.displayName.lowercase().contains(q) ||
                                it.fileName.lowercase().contains(q) ||
                                it.packName.lowercase().contains(q)
                        }
                    }
                    val filteredPacks = remember(packs, q) {
                        if (q.isEmpty()) packs
                        else packs.filter { it.name.lowercase().contains(q) }
                    }

                    // Main Items List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedTab == PackManagerTab.ALL || selectedTab == PackManagerTab.XML_PRESETS) {
                            items(filteredPresets, key = { "xml_${it.id}" }) { preset ->
                                XmlPresetItemCard(
                                    preset = preset,
                                    canApply = onApplyEffectItem != null,
                                    onApply = {
                                        onApplyEffectItem?.invoke(preset.id)
                                        onDismiss()
                                    },
                                    onCopyXml = {
                                        clipboardManager.setText(AnnotatedString(preset.rawXml))
                                        feedbackBanner = "تم نسخ كود XML (${preset.name}) إلى الحافظة"
                                    },
                                    onDelete = { CustomPackRepository.deleteItem(context, preset.id) }
                                )
                            }
                        }

                        if (selectedTab == PackManagerTab.ALL || selectedTab == PackManagerTab.GLSL_SHADERS) {
                            items(filteredShaders, key = { "glsl_${it.id}" }) { shader ->
                                GlslShaderItemCard(
                                    shader = shader,
                                    canApply = onApplyEffectItem != null && shader.isRunnablePostProcess,
                                    onApply = {
                                        onApplyEffectItem?.invoke(shader.id)
                                        onDismiss()
                                    },
                                    onCopySource = {
                                        clipboardManager.setText(AnnotatedString(shader.rawSource))
                                        feedbackBanner = "تم نسخ كود GLSL (${shader.fileName}) إلى الحافظة"
                                    },
                                    onDelete = { CustomPackRepository.deleteItem(context, shader.id) }
                                )
                            }
                        }

                        if (selectedTab == PackManagerTab.ALL || selectedTab == PackManagerTab.FONTS) {
                            items(filteredFonts, key = { "font_${it.id}" }) { font ->
                                FontAssetItemCard(
                                    font = font,
                                    canSelectFont = onSelectFontId != null,
                                    onSelect = {
                                        onSelectFontId?.invoke(font.id)
                                        onDismiss()
                                    },
                                    onDelete = { CustomPackRepository.deleteItem(context, font.id) }
                                )
                            }
                        }

                        if (selectedTab == PackManagerTab.PACKS) {
                            items(filteredPacks, key = { "pack_${it.id}" }) { pack ->
                                PackInfoItemCard(
                                    pack = pack,
                                    onSync = { CustomPackRepository.resyncSafPack(context, pack.id) },
                                    onExport = {
                                        pendingExportPackId = pack.id
                                        exportZipLauncher.launch("${pack.name.replace(' ', '_')}.zip")
                                    },
                                    onDelete = { CustomPackRepository.deletePack(context, pack.id) }
                                )
                            }
                        }

                        val isCurrentTabEmpty = when (selectedTab) {
                            PackManagerTab.ALL -> filteredPresets.isEmpty() && filteredShaders.isEmpty() && filteredFonts.isEmpty()
                            PackManagerTab.XML_PRESETS -> filteredPresets.isEmpty()
                            PackManagerTab.GLSL_SHADERS -> filteredShaders.isEmpty()
                            PackManagerTab.FONTS -> filteredFonts.isEmpty()
                            PackManagerTab.PACKS -> filteredPacks.isEmpty()
                        }
                        if (isCurrentTabEmpty) {
                            item {
                                Surface(
                                    color = PackStudioCard,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, PackStudioBorder),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = PackStudioPurple,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = "لا توجد عناصر مطابقة في هذا القسم بعد",
                                            fontFamily = CairoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "استخدم \"مجلد المؤثرات\" لاستيراد مجلد effects (902 عنصر) أو fonts أو ملفات ZIP أو لصق XML.",
                                            fontFamily = CairoFontFamily,
                                            fontSize = 11.5.sp,
                                            color = PackStudioMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Inline Clipboard/Export Feedback Pill
                val banner = feedbackBanner
                if (banner != null) {
                    Surface(
                        color = Color(0xFF1A2234),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, PackStudioNeonGreen),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = PackStudioNeonGreen, modifier = Modifier.size(16.dp))
                            Text(
                                text = banner,
                                fontFamily = CairoFontFamily,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                if (showPasteDialog) {
                    PasteCodeSubDialog(
                        onDismiss = { showPasteDialog = false },
                        onPasteFromClipboard = { clipboardManager.getText()?.text.orEmpty() },
                        onSaveAndApply = { code, name, applyImmediately ->
                            val created = CustomPackRepository.importPastedCode(context, code, name)
                            showPasteDialog = false
                            if (created != null) {
                                if (applyImmediately && onApplyEffectItem != null) {
                                    onApplyEffectItem.invoke(created.id)
                                    onDismiss()
                                } else {
                                    feedbackBanner = "تم حفظ ${created.name} في My Effects"
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = PackStudioCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                text = title,
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontFamily = InterFontFamily,
                fontSize = 9.sp,
                color = PackStudioMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun XmlPresetItemCard(
    preset: ImportedXmlEffectPreset,
    canApply: Boolean,
    onApply: () -> Unit,
    onCopyXml: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = PackStudioCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PackStudioPurple.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(PackStudioPurple.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = PackStudioPurple, modifier = Modifier.size(18.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${preset.passes.size} pass • ${preset.primaryMappedEffectId} • ${preset.packName}",
                    fontFamily = InterFontFamily,
                    fontSize = 10.5.sp,
                    color = PackStudioMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canApply) {
                Surface(
                    color = PackStudioNeonGreen,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onApply)
                        .testTag("pack_apply_${preset.id}")
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Apply",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF04140B)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCopyXml)
                    .testTag("pack_copy_${preset.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "نسخ XML", tint = PackStudioCyan, modifier = Modifier.size(17.dp))
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .testTag("pack_delete_${preset.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = PackStudioError, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun GlslShaderItemCard(
    shader: ImportedShaderEffect,
    canApply: Boolean,
    onApply: () -> Unit,
    onCopySource: () -> Unit,
    onDelete: () -> Unit
) {
    val isBundled = shader.packId == BundledCustomShaders.BUNDLED_PACK_ID
    Surface(
        color = PackStudioCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (shader.isRunnablePostProcess) PackStudioNeonGreen.copy(alpha = 0.35f) else PackStudioBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(PackStudioNeonGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Code, contentDescription = null, tint = PackStudioNeonGreen, modifier = Modifier.size(18.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shader.name,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${shader.fileName} • ES ${shader.originalGlslVersion} -> 300 es • ${shader.params.size} params",
                    fontFamily = InterFontFamily,
                    fontSize = 10.sp,
                    color = PackStudioMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                shader.compileWarning?.let { warn ->
                    Text(
                        text = warn,
                        fontFamily = InterFontFamily,
                        fontSize = 9.5.sp,
                        color = Color(0xFFFBBF24),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (canApply) {
                Surface(
                    color = PackStudioNeonGreen,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onApply)
                        .testTag("pack_apply_${shader.id}")
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Apply",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF04140B)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCopySource)
                    .testTag("pack_copy_${shader.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "نسخ الشيدر", tint = PackStudioCyan, modifier = Modifier.size(17.dp))
            }

            if (!isBundled) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete)
                        .testTag("pack_delete_${shader.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = PackStudioError, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun FontAssetItemCard(
    font: ImportedFontAsset,
    canSelectFont: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = PackStudioCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PackStudioCyan.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(PackStudioCyan.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.FontDownload, contentDescription = null, tint = PackStudioCyan, modifier = Modifier.size(18.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = font.displayName,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${font.fileName} • ${font.packName}",
                    fontFamily = InterFontFamily,
                    fontSize = 10.5.sp,
                    color = PackStudioMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canSelectFont) {
                Surface(
                    color = PackStudioCyan,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSelect)
                        .testTag("pack_use_font_${font.id}")
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Use Font",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF04140B)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .testTag("pack_delete_${font.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف الخط", tint = PackStudioError, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PackInfoItemCard(
    pack: CustomPackInfo,
    onSync: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val isBundled = pack.id == BundledCustomShaders.BUNDLED_PACK_ID
    Surface(
        color = PackStudioCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PackStudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.name,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${pack.presetCount} XML • ${pack.shaderCount} GLSL • ${pack.fontCount} Fonts • ${pack.sourceType.name}",
                    fontFamily = InterFontFamily,
                    fontSize = 10.5.sp,
                    color = PackStudioMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (pack.treeUriString != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSync)
                        .testTag("pack_sync_${pack.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = "تحديث المجلد", tint = PackStudioNeonGreen, modifier = Modifier.size(18.dp))
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onExport)
                    .testTag("pack_export_${pack.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = "تصدير ZIP", tint = PackStudioCyan, modifier = Modifier.size(18.dp))
            }

            if (!isBundled) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete)
                        .testTag("pack_delete_pack_${pack.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف الحزمة", tint = PackStudioError, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PasteCodeSubDialog(
    onDismiss: () -> Unit,
    onPasteFromClipboard: () -> String,
    onSaveAndApply: (code: String, name: String, applyImmediately: Boolean) -> Unit
) {
    var codeText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    val trimmed = codeText.trim()
    val detectedAsXml = trimmed.startsWith("<") && (
        trimmed.contains("<effect", true) ||
            trimmed.contains("<scene", true) ||
            trimmed.contains("<property", true)
        )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = PackStudioBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, PackStudioPurple),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("pack_paste_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "لصق كود Alight Motion XML أو GLSL",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Surface(
                        color = if (detectedAsXml) PackStudioPurple.copy(alpha = 0.25f) else PackStudioNeonGreen.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (trimmed.isEmpty()) "Auto" else if (detectedAsXml) "AM XML" else "GLSL",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (detectedAsXml) PackStudioPurple else PackStudioNeonGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    singleLine = true,
                    placeholder = { Text("اسم المؤثر (اختياري)", fontFamily = CairoFontFamily, fontSize = 12.sp, color = PackStudioMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PackStudioPurple,
                        unfocusedBorderColor = PackStudioBorder,
                        focusedContainerColor = PackStudioCard,
                        unfocusedContainerColor = PackStudioCard
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("pack_paste_name_input")
                )

                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    minLines = 6,
                    maxLines = 10,
                    placeholder = {
                        Text(
                            text = "<effect id=\"com.alightcreative.effects.tiles\">...\nأو\nvoid main() { ... }",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = PackStudioMuted
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.White
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PackStudioPurple,
                        unfocusedBorderColor = PackStudioBorder,
                        focusedContainerColor = PackStudioCard,
                        unfocusedContainerColor = PackStudioCard
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("pack_paste_code_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = onPasteFromClipboard()
                            if (clip.isNotBlank()) codeText = clip
                        },
                        border = BorderStroke(1.dp, PackStudioCyan),
                        modifier = Modifier.height(44.dp).testTag("pack_paste_clipboard_btn")
                    ) {
                        Icon(Icons.Outlined.ContentPaste, null, tint = PackStudioCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("لصق", fontFamily = CairoFontFamily, color = PackStudioCyan, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onSaveAndApply(codeText, nameText, true) },
                        enabled = trimmed.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PackStudioNeonGreen,
                            contentColor = Color(0xFF04140B)
                        ),
                        modifier = Modifier.weight(1f).height(44.dp).testTag("pack_paste_save_btn")
                    ) {
                        Text(
                            text = "حفظ وتطبيق",
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }
    }
}

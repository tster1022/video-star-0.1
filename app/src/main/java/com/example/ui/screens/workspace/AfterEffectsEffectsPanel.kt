package com.example.ui.screens.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import com.example.ui.components.PrecisionGoldAccent
import com.example.ui.components.PrecisionObsidianLabelBg
import com.example.ui.components.PrecisionReadoutBg
import com.example.ui.components.PrecisionRulerDialSlider
import com.example.ui.components.PrecisionSegmentedOptionRow
import com.example.ui.components.rememberStudioTickHaptics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VideoStarRepository
import com.example.engine.ae.AeEffectEngineCatalog
import com.example.model.EditorLayer
import com.example.model.EffectBlendMode
import com.example.model.EffectParamType
import com.example.model.PARAM_KEY_INTENSITY
import com.example.model.PARAM_KEY_OPACITY
import com.example.model.ShaderEffectPass
import com.example.ui.screens.packs.PackImportStudioSheet
import com.example.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.math.abs

// Sleek Deep Obsidian-Purple + Vivid Electric Purple Palette
private val AeEffectsBg = Color(0xFF0E0C1A)
private val AeEffectsRailBg = Color(0xFF0A0814)
private val AeEffectsDivider = Color(0xFF282042)
private val AeEffectsCardBg = Color(0xFF17132B)
private val AeEffectsButtonBorder = Color(0xFF2F2652)
private val AeEffectsVividPurple = Color(0xFFA855F7)
private val AeEffectsDeepPurple = Color(0xFF7E22CE)
private val AeEffectsTrackInactive = Color(0xFF251E40)
private val AeEffectsMuted = Color(0xFF8B84A8)

internal enum class AeEffectsSubView {
    CHAIN,
    AE_BROWSER,
    PARAM_EDITOR
}

/**
 * Two-Step Transparent-to-Highlighted Interactive Trigger Button:
 * - 0 taps (Idle): Subtle hairline border.
 * - 1st tap (Armed): Lights up in vivid accent color; tapping anywhere outside resets it.
 * - 2nd tap (Execute): Runs onConfirmedAction and returns to idle.
 */
@Composable
internal fun AeTwoStepTriggerPill(
    label: String,
    isArmed: Boolean,
    testTag: String,
    onArmChange: (Boolean) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onConfirmedAction: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = AeEffectsVividPurple
) {
    Surface(
        color = if (isArmed) accentColor else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (isArmed) 1.5.dp else 1.dp,
            color = if (isArmed) accentColor else Color.White.copy(alpha = 0.28f)
        ),
        modifier = modifier
            .height(36.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .then(
                if (isArmed) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(accentColor.copy(alpha = 0.28f), Color.Transparent)
                            ),
                            radius = size.maxDimension * 0.65f
                        )
                    }
                } else Modifier
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                onArmChange(false)
                onConfirmedAction()
            }
            .semantics {
                stateDescription = if (isArmed) "Armed" else "Idle"
            }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = if (isArmed) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (isArmed) Color.White else Color(0xFFE2DEF0),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun AeLayerEffectsPanel(
    selectedLayer: EditorLayer?,
    currentPositionMs: Long,
    openBrowserSeq: Int = 0,
    initialBrowserCategoryId: String? = null,
    isAddEffectArmed: Boolean,
    onAddEffectArmChange: (Boolean) -> Unit,
    onAddEffectBoundsChanged: (Rect) -> Unit,
    onBackClick: () -> Unit,
    onExitToIdle: () -> Unit = onBackClick,
    onPresetsClick: () -> Unit,
    onAddAeEffect: (String) -> ShaderEffectPass?,
    onUpdatePass: (ShaderEffectPass, Boolean) -> Unit,
    onRemovePass: (String) -> Unit,
    onTogglePassKeyframe: (passId: String, propertyKey: String, currentValue: Float) -> Unit
) {
    var subView by remember(selectedLayer?.id) {
        mutableStateOf(if (openBrowserSeq > 0) AeEffectsSubView.AE_BROWSER else AeEffectsSubView.CHAIN)
    }
    var editingPassId by remember(selectedLayer?.id) { mutableStateOf<String?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showPackImporter by remember { mutableStateOf(false) }
    var curvePresetIndex by remember(selectedLayer?.id) { mutableIntStateOf(0) }

    LaunchedEffect(openBrowserSeq) {
        if (openBrowserSeq > 0) {
            subView = AeEffectsSubView.AE_BROWSER
        }
    }

    val activePasses = selectedLayer?.effectPasses.orEmpty()
    val editingPass = remember(activePasses, editingPassId) {
        activePasses.firstOrNull { it.id == editingPassId } ?: activePasses.lastOrNull()
    }
    val favoriteEffectIds by VideoStarRepository.favoriteAeEffectIds.collectAsStateWithLifecycle()

    val sheetHeight = when (subView) {
        AeEffectsSubView.CHAIN, AeEffectsSubView.AE_BROWSER -> 252.dp
        AeEffectsSubView.PARAM_EDITOR -> 304.dp
    }

    val layerDur = (selectedLayer?.effectiveLayerDurationMs ?: 3_000L).toFloat().coerceAtLeast(1f)
    val startMs = selectedLayer?.startTimeMs ?: 0L
    val progress = ((currentPositionMs - startMs).toFloat() / layerDur).coerceIn(0f, 1f)

    val canKeyframeFromRail = activePasses.isNotEmpty() && editingPass != null
    val hasActiveKeyframeHere = editingPass?.keyframesForProperty(PARAM_KEY_INTENSITY)
        ?.any { abs(it.time - progress) <= 0.03f } == true
    val hasAnyKeyframes = activePasses.any { it.keyframes.isNotEmpty() || it.paramKeyframes.isNotEmpty() }

    Surface(
        color = AeEffectsBg,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ae_layer_effects_sheet")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .navigationBarsPadding()
        ) {
            // Left Vertical Action Rail matching clean AE layout: Back, Keyframe (+), Graph Curve, More (•••)
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(AeEffectsRailBg)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (subView == AeEffectsSubView.PARAM_EDITOR) {
                                    subView = AeEffectsSubView.CHAIN
                                } else {
                                    onBackClick()
                                }
                            }
                            .testTag(
                                if (subView == AeEffectsSubView.PARAM_EDITOR) "ae_param_back_button"
                                else "ae_effects_back_button"
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canKeyframeFromRail) {
                                val target = editingPass ?: return@clickable
                                onTogglePassKeyframe(
                                    target.id,
                                    PARAM_KEY_INTENSITY,
                                    target.evaluateIntensityAt(progress)
                                )
                            }
                            .alpha(if (canKeyframeFromRail) 1f else 0.28f)
                            .testTag("ae_effects_rail_keyframe"),
                        contentAlignment = Alignment.Center
                    ) {
                        AeDiamondPlusGlyph(
                            tint = if (hasActiveKeyframeHere) AeEffectsVividPurple else AeEffectsMuted,
                            filled = hasActiveKeyframeHere,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canKeyframeFromRail) {
                                curvePresetIndex = (curvePresetIndex + 1) % 3
                            }
                            .alpha(if (hasAnyKeyframes || canKeyframeFromRail) 0.92f else 0.28f)
                            .testTag("ae_effects_rail_curve"),
                        contentAlignment = Alignment.Center
                    ) {
                        AeBezierCurveGraphGlyph(
                            tint = if (hasAnyKeyframes) AeEffectsVividPurple else AeEffectsMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .clickable { showOverflowMenu = true }
                                .testTag("ae_effects_rail_more"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = "More Effect Options",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.background(AeEffectsCardBg)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Effects Browser",
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onAddEffectArmChange(false)
                                    onPresetsClick()
                                    subView = AeEffectsSubView.AE_BROWSER
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Import XML / Shaders / Fonts",
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        color = Color(0xFF00E676)
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showPackImporter = true
                                }
                            )
                            if (activePasses.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Clear All Effects",
                                            fontFamily = InterFontFamily,
                                            fontSize = 13.sp,
                                            color = Color(0xFFFF6B8B)
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        activePasses.forEach { onRemovePass(it.id) }
                                        subView = AeEffectsSubView.CHAIN
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Exit to Edit & Add",
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        color = AeEffectsVividPurple
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onExitToIdle()
                                }
                            )
                        }
                    }
                }
            }

            VerticalDivider(thickness = 1.dp, color = AeEffectsDivider)

            // Main Effects Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                when (subView) {
                    AeEffectsSubView.CHAIN, AeEffectsSubView.AE_BROWSER -> {
                        AeEffectsChainView(
                            activePasses = activePasses,
                            isAddEffectArmed = isAddEffectArmed,
                            onAddEffectArmChange = onAddEffectArmChange,
                            onAddEffectBoundsChanged = onAddEffectBoundsChanged,
                            onOpenBrowser = { subView = AeEffectsSubView.AE_BROWSER },
                            onInspectPass = { pass ->
                                editingPassId = pass.id
                                subView = AeEffectsSubView.PARAM_EDITOR
                            },
                            onTogglePassEnabled = { pass ->
                                onUpdatePass(pass.copy(isEnabled = !pass.isEnabled), true)
                            },
                            onRemovePass = onRemovePass
                        )
                    }

                    AeEffectsSubView.PARAM_EDITOR -> {
                        val pass = editingPass
                        if (pass == null) {
                            subView = AeEffectsSubView.CHAIN
                        } else {
                            AeEffectParameterEditorView(
                                pass = pass,
                                progress = progress,
                                onUpdatePass = onUpdatePass,
                                onToggleKeyframe = { propKey, curVal ->
                                    onTogglePassKeyframe(pass.id, propKey, curVal)
                                },
                                onRemovePass = {
                                    onRemovePass(pass.id)
                                    subView = AeEffectsSubView.CHAIN
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (subView == AeEffectsSubView.AE_BROWSER) {
        AeEffectsBrowserView(
            previewClip = selectedLayer?.mediaClip,
            appliedEffectIds = activePasses.map { it.effectId }.toSet(),
            favoriteEffectIds = favoriteEffectIds,
            initialCategoryId = initialBrowserCategoryId,
            onToggleFavorite = { effectId -> VideoStarRepository.toggleFavoriteAeEffect(effectId) },
            onBackToChain = { subView = AeEffectsSubView.CHAIN },
            onSelectEffect = { item ->
                val created = onAddAeEffect(item.id)
                if (created != null) {
                    editingPassId = created.id
                    subView = AeEffectsSubView.PARAM_EDITOR
                } else {
                    subView = AeEffectsSubView.CHAIN
                }
            }
        )
    }

    if (showPackImporter) {
        PackImportStudioSheet(
            initialTab = "ALL",
            onDismiss = { showPackImporter = false },
            onApplyEffectItem = { effectId ->
                val created = onAddAeEffect(effectId)
                if (created != null) {
                    editingPassId = created.id
                    subView = AeEffectsSubView.PARAM_EDITOR
                } else {
                    subView = AeEffectsSubView.CHAIN
                }
            }
        )
    }
}

@Composable
private fun AeEffectsChainView(
    activePasses: List<ShaderEffectPass>,
    isAddEffectArmed: Boolean,
    onAddEffectArmChange: (Boolean) -> Unit,
    onAddEffectBoundsChanged: (Rect) -> Unit,
    onOpenBrowser: () -> Unit,
    onInspectPass: (ShaderEffectPass) -> Unit,
    onTogglePassEnabled: (ShaderEffectPass) -> Unit,
    onRemovePass: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Full-width sleek "+ Add Effect" button matching reference layout
        Surface(
            color = if (isAddEffectArmed) AeEffectsDeepPurple else AeEffectsCardBg,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                width = if (isAddEffectArmed) 1.5.dp else 1.dp,
                color = if (isAddEffectArmed) AeEffectsVividPurple else AeEffectsButtonBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .onGloballyPositioned { onAddEffectBoundsChanged(it.boundsInRoot()) }
                .then(
                    if (isAddEffectArmed) {
                        Modifier.drawBehind {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        AeEffectsVividPurple.copy(alpha = 0.25f),
                                        AeEffectsDeepPurple.copy(alpha = 0.16f),
                                        AeEffectsVividPurple.copy(alpha = 0.25f)
                                    )
                                )
                            )
                        }
                    } else Modifier
                )
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    onAddEffectArmChange(false)
                    onOpenBrowser()
                }
                .semantics {
                    stateDescription = "Ready"
                }
                .testTag("layout_add_effect_button")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = if (isAddEffectArmed) Color.White else Color(0xFFEDE9FE),
                    modifier = Modifier.size(19.dp)
                )
                SpacerWidth6()
                Text(
                    text = "Add Effect",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }

        // Clean empty space when no effects are added; clean purple-obsidian list when effects exist
        if (activePasses.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activePasses.forEachIndexed { idx, pass ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AeEffectsCardBg)
                            .border(
                                width = 1.dp,
                                color = if (pass.isEnabled) AeEffectsVividPurple.copy(alpha = 0.55f) else AeEffectsButtonBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onInspectPass(pass) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("ae_layer_effect_pass_$idx"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (pass.isEnabled) AeEffectsVividPurple else AeEffectsMuted.copy(alpha = 0.35f))
                            )
                            Column {
                                Text(
                                    text = pass.effectName,
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (pass.isEnabled) Color.White else AeEffectsMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pass.category.shortLabel} • ${(pass.intensity * 100).toInt()}%",
                                    fontFamily = InterFontFamily,
                                    fontSize = 10.sp,
                                    color = AeEffectsMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable { onTogglePassEnabled(pass) }
                                    .testTag("ae_pass_toggle_$idx"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (pass.isEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = "Toggle Effect",
                                    tint = if (pass.isEnabled) AeEffectsVividPurple else AeEffectsMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable { onRemovePass(pass.id) }
                                    .testTag("ae_pass_remove_$idx"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove Effect",
                                    tint = AeEffectsMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AeEffectParameterEditorView(
    pass: ShaderEffectPass,
    progress: Float,
    onUpdatePass: (ShaderEffectPass, Boolean) -> Unit,
    onToggleKeyframe: (propertyKey: String, currentValue: Float) -> Unit,
    onRemovePass: () -> Unit
) {
    val catalogItem = remember(pass.effectId) {
        AeEffectEngineCatalog.findById(pass.effectId)?.toCatalogItem()
    }
    val evalIntensity = pass.evaluateIntensityAt(progress)
    val evalOpacity = pass.evaluateOpacityAt(progress)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ae_effect_param_editor"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pass.effectName,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pass.category.shortLabel,
                    fontFamily = InterFontFamily,
                    fontSize = 10.sp,
                    color = AeEffectsVividPurple
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AeEffectsCardBg)
                    .border(1.dp, AeEffectsButtonBorder, CircleShape)
                    .clickable(onClick = onRemovePass)
                    .testTag("ae_param_delete_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete Effect",
                    tint = Color(0xFFFF6B8B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        HorizontalDivider(thickness = 0.8.dp, color = AeEffectsDivider)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Intensity + Keyframe
            AeSliderParamRow(
                label = "Intensity",
                valueText = "${(evalIntensity * 100).toInt()}%",
                value = evalIntensity,
                min = 0f,
                max = 1f,
                hasKeyframeHere = pass.keyframesForProperty(PARAM_KEY_INTENSITY).any { abs(it.time - progress) <= 0.03f },
                sliderTag = "ae_param_slider_intensity",
                keyframeTag = "ae_param_keyframe_toggle_intensity",
                onValueChange = { next ->
                    onUpdatePass(pass.copy(intensity = next), false)
                },
                onToggleKeyframe = { onToggleKeyframe(PARAM_KEY_INTENSITY, evalIntensity) }
            )

            // 2. Opacity + Keyframe
            AeSliderParamRow(
                label = "Opacity",
                valueText = "${(evalOpacity * 100).toInt()}%",
                value = evalOpacity,
                min = 0f,
                max = 1f,
                hasKeyframeHere = pass.paramKeyframes[PARAM_KEY_OPACITY].orEmpty().any { abs(it.time - progress) <= 0.03f },
                sliderTag = "ae_param_slider_opacity",
                keyframeTag = "ae_param_keyframe_toggle_opacity",
                onValueChange = { next ->
                    onUpdatePass(pass.copy(passOpacity = next), false)
                },
                onToggleKeyframe = { onToggleKeyframe(PARAM_KEY_OPACITY, evalOpacity) }
            )

            // 3. Dynamic Effect Catalog Params
            catalogItem?.params?.forEach { spec ->
                when (spec.paramType) {
                    EffectParamType.FLOAT,
                    EffectParamType.POINT_2D_X,
                    EffectParamType.POINT_2D_Y -> {
                        val cur = pass.evaluateParamAt(spec.key, progress, spec.defaultValue, spec.minValue, spec.maxValue)
                        val formatted = if (spec.unitSuffix.isNotEmpty()) {
                            String.format(Locale.US, "%.2f %s", cur, spec.unitSuffix)
                        } else {
                            String.format(Locale.US, "%.2f", cur)
                        }
                        AeSliderParamRow(
                            label = spec.label,
                            valueText = formatted,
                            value = cur.coerceIn(spec.minValue, spec.maxValue),
                            min = spec.minValue,
                            max = spec.maxValue,
                            hasKeyframeHere = pass.paramKeyframes[spec.key].orEmpty().any { abs(it.time - progress) <= 0.03f },
                            sliderTag = "ae_param_slider_${spec.key}",
                            keyframeTag = "ae_param_keyframe_toggle_${spec.key}",
                            onValueChange = { next ->
                                onUpdatePass(pass.copy(params = pass.params + (spec.key to next)), false)
                            },
                            onToggleKeyframe = { onToggleKeyframe(spec.key, cur) }
                        )
                    }

                    EffectParamType.BOOLEAN -> {
                        val haptics = rememberStudioTickHaptics()
                        val checked = pass.evaluateParamAt(spec.key, progress, spec.defaultValue, 0f, 1f) >= 0.5f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AeEffectsCardBg)
                                .border(1.dp, AeEffectsButtonBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(spec.label, fontFamily = InterFontFamily, fontSize = 12.sp, color = Color.White)
                            Switch(
                                checked = checked,
                                onCheckedChange = { on ->
                                    haptics.pulseSelection()
                                    onUpdatePass(pass.copy(params = pass.params + (spec.key to if (on) 1f else 0f)), true)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AeEffectsVividPurple,
                                    uncheckedTrackColor = AeEffectsTrackInactive
                                )
                            )
                        }
                    }

                    EffectParamType.ENUM_MODE -> {
                        val selectedIdx = pass.param(spec.key, spec.defaultValue).toInt()
                            .coerceIn(0, (spec.enumOptions.size - 1).coerceAtLeast(0))
                        val indexedOptions = spec.enumOptions.mapIndexed { idx, optLabel -> idx to optLabel }
                        if (indexedOptions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AeEffectsCardBg)
                                    .border(1.dp, AeEffectsButtonBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                PrecisionSegmentedOptionRow(
                                    label = spec.label,
                                    options = indexedOptions,
                                    selectedOption = indexedOptions[selectedIdx],
                                    optionLabel = { it.second },
                                    onSelectOption = { (optIdx, _) ->
                                        onUpdatePass(pass.copy(params = pass.params + (spec.key to optIdx.toFloat())), true)
                                    },
                                    accentColor = PrecisionGoldAccent,
                                    trackColor = Color(0xFF1F1936),
                                    activeContainerColor = Color(0xFF392B63)
                                )
                            }
                        }
                    }

                    EffectParamType.COLOR -> {
                        val haptics = rememberStudioTickHaptics()
                        val swatches = listOf(0xFFA855F7L, 0xFFEC4899L, 0xFF6366F1L, 0xFFFACC15L, 0xFFFFFFFFL, 0xFF000000L)
                        val curArgb = pass.colorParams[spec.key] ?: spec.defaultColorArgb
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AeEffectsCardBg)
                                .border(1.dp, AeEffectsButtonBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(spec.label, fontFamily = InterFontFamily, fontSize = 12.sp, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                swatches.forEach { argb ->
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(argb.toInt()))
                                            .border(
                                                width = if (curArgb == argb) 2.dp else 1.dp,
                                                color = if (curArgb == argb) AeEffectsVividPurple else Color.White.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                haptics.pulseSelection()
                                                onUpdatePass(pass.copy(colorParams = pass.colorParams + (spec.key to argb)), true)
                                            }
                                    )
                                }
                            }
                        }
                    }

                    EffectParamType.TEXT_STRING -> {
                        val curTxt = pass.textParam(spec.key, spec.defaultText)
                        OutlinedTextField(
                            value = curTxt,
                            onValueChange = { next ->
                                onUpdatePass(pass.copy(textParams = pass.textParams + (spec.key to next)), false)
                            },
                            label = { Text(spec.label, fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AeEffectsVividPurple,
                                unfocusedBorderColor = AeEffectsButtonBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 4. Blend mode segmented strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AeEffectsCardBg)
                    .border(1.dp, AeEffectsButtonBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                PrecisionSegmentedOptionRow(
                    label = "Blend",
                    options = EffectBlendMode.entries,
                    selectedOption = pass.blendMode,
                    optionLabel = { it.arabicLabel },
                    onSelectOption = { mode -> onUpdatePass(pass.copy(blendMode = mode), true) },
                    accentColor = PrecisionGoldAccent,
                    labelColor = AeEffectsMuted,
                    trackColor = Color(0xFF1F1936),
                    activeContainerColor = Color(0xFF392B63)
                )
            }
        }
    }
}

@Composable
private fun AeSliderParamRow(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    hasKeyframeHere: Boolean,
    sliderTag: String,
    keyframeTag: String,
    onValueChange: (Float) -> Unit,
    onToggleKeyframe: () -> Unit
) {
    val safeMax = if (max <= min) min + 1f else max
    val haptics = rememberStudioTickHaptics()
    PrecisionRulerDialSlider(
        label = label,
        valueText = valueText,
        value = value.coerceIn(min, safeMax),
        onValueChange = onValueChange,
        valueRange = min..safeMax,
        accentColor = PrecisionGoldAccent,
        containerColor = Color(0xFF1A1530),
        labelContainerColor = PrecisionObsidianLabelBg,
        readoutContainerColor = PrecisionReadoutBg,
        height = 46.dp,
        sliderTestTag = sliderTag,
        trailingContent = {
            Surface(
                color = if (hasKeyframeHere) AeEffectsDeepPurple.copy(alpha = 0.65f) else AeEffectsTrackInactive,
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(
                    1.dp,
                    if (hasKeyframeHere) AeEffectsVividPurple else AeEffectsButtonBorder
                ),
                modifier = Modifier
                    .size(width = 32.dp, height = 34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable {
                        haptics.pulseSelection()
                        onToggleKeyframe()
                    }
                    .testTag(keyframeTag)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (hasKeyframeHere) "◆" else "◇",
                        fontSize = 14.sp,
                        color = if (hasKeyframeHere) AeEffectsVividPurple else Color(0xFFCBD5E1)
                    )
                }
            }
        }
    )
}

@Composable
private fun SpacerWidth6() {
    Box(modifier = Modifier.width(6.dp))
}

@Composable
private fun AeDiamondPlusGlyph(
    tint: Color,
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val rx = w * 0.36f
        val ry = h * 0.44f
        val path = Path().apply {
            moveTo(cx, cy - ry)
            lineTo(cx + rx, cy)
            lineTo(cx, cy + ry)
            lineTo(cx - rx, cy)
            close()
        }
        if (filled) {
            drawPath(path = path, color = tint.copy(alpha = 0.24f))
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round)
        )
        val plusArm = w * 0.14f
        drawLine(
            color = tint,
            start = Offset(cx - plusArm, cy),
            end = Offset(cx + plusArm, cy),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(cx, cy - plusArm),
            end = Offset(cx, cy + plusArm),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun AeBezierCurveGraphGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = w * 0.14f
        val boxW = w - pad * 2f
        val boxH = h - pad * 2f
        drawRect(
            color = tint.copy(alpha = 0.7f),
            topLeft = Offset(pad, pad),
            size = androidx.compose.ui.geometry.Size(boxW, boxH),
            style = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))
            )
        )
        val p0 = Offset(pad, pad + boxH)
        val p1 = Offset(pad + boxW, pad)
        val curve = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(
                pad + boxW * 0.58f, p0.y,
                pad + boxW * 0.42f, p1.y,
                p1.x, p1.y
            )
        }
        drawPath(
            path = curve,
            color = tint,
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(color = tint, radius = 2.dp.toPx(), center = p0)
        drawCircle(color = tint, radius = 2.dp.toPx(), center = p1)
    }
}

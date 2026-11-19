package com.example.ui.screens.effects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.model.EffectCatalog
import com.example.model.ShaderEffectPass
import com.example.model.arabicDisplayName
import com.example.ui.components.EffectAnimatedSampleCard
import com.example.ui.components.ReorderableLazyRow
import com.example.ui.components.ReorderableRowItem
import com.example.ui.components.SampleCardKind
import com.example.ui.components.rememberReorderableLazyRowState
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicRed

@Composable
internal fun EffectStackPanel(
    uiState: EffectsStudioUiState,
    sceneThumbnail: ImageBitmap?,
    onOpenLibrary: () -> Unit,
    onOpenPassEditor: (Int) -> Unit,
    onTogglePassEnabled: (Int) -> Unit,
    onDuplicatePass: (Int) -> Unit,
    onRemovePass: (Int) -> Unit,
    onBeginPassReorder: () -> Unit = {},
    onMovePass: (Int, Int) -> Unit,
    onEndPassReorder: () -> Unit = {},
    onClearAllPasses: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val reorderState = rememberReorderableLazyRowState(
        isDraggableKey = { it != "add_effect_tile" },
        onDragStart = { onBeginPassReorder() },
        onMove = onMovePass,
        onDragEnd = onEndPassReorder
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "قائمة الإضافات",
                    fontFamily = CairoFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "${uiState.passes.size}",
                        fontFamily = CairoFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (uiState.passes.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAllPasses,
                        modifier = Modifier.height(44.dp).testTag("effects_clear_all")
                    ) {
                        Text(
                            text = "مسح الكل",
                            fontFamily = CairoFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClassicRed
                        )
                    }
                }
                Button(
                    onClick = onOpenLibrary,
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(44.dp).testTag("effects_add_pass_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "إضافة مؤثر",
                        fontFamily = CairoFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.16f))

        if (uiState.passes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.22f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 10.dp)
                    .clickable(onClick = onOpenLibrary)
                    .testTag("effects_stack_placeholder")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "قائمة المؤثرات فارغة",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "قائمة الإضافات جاهزة لمؤثرات GPU",
                        fontFamily = CairoFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اضغط على (+ إضافة مؤثر) لتركيب فلاتر GLSL متتالية، وإعادة ترتيبها، والتحكم بشفافيتها ومفاتيح الحركة.",
                        fontFamily = CairoFontFamily,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            if (uiState.passes.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "اسحب البطاقات لإعادة ترتيب سلسلة الشادر (من 1 إلى ${uiState.passes.size}) • مطوّل للخيارات",
                        fontFamily = CairoFontFamily,
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            ReorderableLazyRow(
                state = reorderState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("effects_stack_reorderable_row"),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(
                    items = uiState.passes,
                    key = { _, pass -> pass.id }
                ) { index, pass ->
                    var menuOpen by remember { mutableStateOf(false) }

                    ReorderableRowItem(
                        state = reorderState,
                        key = pass.id,
                        enabled = !menuOpen
                    ) { isDragging ->
                        StackEffectTile(
                            index = index,
                            totalCount = uiState.passes.size,
                            pass = pass,
                            isSelected = index == uiState.selectedPassIndex,
                            isDragging = isDragging,
                            showMenu = menuOpen,
                            onShowMenuChange = { menuOpen = it },
                            sceneThumbnail = sceneThumbnail,
                            onClick = { onOpenPassEditor(index) },
                            onToggleEnabled = { onTogglePassEnabled(index) },
                            onDuplicate = { onDuplicatePass(index) },
                            onDelete = { onRemovePass(index) },
                            onMoveTo = { targetIdx ->
                                onBeginPassReorder()
                                onMovePass(index, targetIdx)
                                onEndPassReorder()
                            }
                        )
                    }
                }

                item(key = "add_effect_tile") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.primary.copy(alpha = 0.1f),
                        border = BorderStroke(1.5.dp, colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .size(width = 92.dp, height = 136.dp)
                            .clickable(onClick = onOpenLibrary)
                            .testTag("effects_stack_add_tile")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "إضافة مؤثر جديد",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "إضافة مؤثر",
                                fontFamily = CairoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StackEffectTile(
    index: Int,
    totalCount: Int,
    pass: ShaderEffectPass,
    isSelected: Boolean,
    isDragging: Boolean,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    sceneThumbnail: ImageBitmap?,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveTo: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val catalogItem = EffectCatalog.findById(pass.effectId)
    val arabicTitle = catalogItem?.arabicDisplayName() ?: pass.effectName

    val cardColor = when {
        isDragging -> colorScheme.primary.copy(alpha = 0.24f)
        isSelected -> colorScheme.primary.copy(alpha = 0.14f)
        else -> colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val borderColor = when {
        isDragging -> colorScheme.primary
        isSelected -> colorScheme.primary
        else -> colorScheme.outline.copy(alpha = 0.28f)
    }

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(136.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = cardColor,
            shadowElevation = if (isDragging) 10.dp else 1.dp,
            border = BorderStroke(
                width = if (isDragging || isSelected) 2.dp else 1.dp,
                color = borderColor
            ),
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (pass.isEnabled) 1f else 0.52f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { onShowMenuChange(true) }
                )
                .testTag("effects_pass_tile_$index")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "اسحب لإعادة ترتيب المؤثر ${index + 1}",
                        tint = if (isDragging) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .size(16.dp)
                            .testTag("effects_drag_handle_$index")
                    )

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleEnabled)
                            .testTag("effects_eye_toggle_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (pass.isEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (pass.isEnabled) "تعطيل المؤثر" else "تفعيل المؤثر",
                            tint = if (pass.isEnabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                EffectAnimatedSampleCard(
                    label = arabicTitle,
                    kind = SampleCardKind.Effect(pass.effectId),
                    isSelected = isSelected || isDragging,
                    sceneThumbnail = sceneThumbnail,
                    onClick = onClick,
                    selectedAccentColor = colorScheme.primary,
                    labelColor = colorScheme.onSurface,
                    clickable = false,
                    modifier = Modifier.height(86.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onShowMenuChange(false) }
        ) {
            if (index > 0) {
                DropdownMenuItem(
                    text = { Text("تقديم للأمام", fontFamily = CairoFontFamily) },
                    onClick = {
                        onShowMenuChange(false)
                        onMoveTo(index - 1)
                    }
                )
            }
            if (index < totalCount - 1) {
                DropdownMenuItem(
                    text = { Text("تأخير للخلف", fontFamily = CairoFontFamily) },
                    onClick = {
                        onShowMenuChange(false)
                        onMoveTo(index + 1)
                    }
                )
            }
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                text = { Text("تكرار", fontFamily = CairoFontFamily) },
                onClick = {
                    onShowMenuChange(false)
                    onDuplicate()
                }
            )
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        if (pass.isEnabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null
                    )
                },
                text = { Text(if (pass.isEnabled) "تخطي مؤقت" else "تفعيل", fontFamily = CairoFontFamily) },
                onClick = {
                    onShowMenuChange(false)
                    onToggleEnabled()
                }
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = ClassicRed) },
                text = { Text("حذف", fontFamily = CairoFontFamily, color = ClassicRed) },
                onClick = {
                    onShowMenuChange(false)
                    onDelete()
                }
            )
        }
    }
}

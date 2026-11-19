package com.example.ui.screens.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EffectCatalogItem
import com.example.model.EffectStudioTab
import com.example.model.arabicDisplayName
import com.example.ui.components.EffectAnimatedSampleCard
import com.example.ui.components.SampleCardKind
import com.example.ui.theme.CairoFontFamily

@Composable
internal fun EffectLibraryPanel(
    uiState: EffectsStudioUiState,
    sceneThumbnail: ImageBitmap?,
    onBackToStack: () -> Unit,
    onSelectEffect: (EffectCatalogItem) -> Unit,
    onToggleFavoriteEffect: (String) -> Unit,
    onSelectTab: (EffectStudioTab) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onToggleSearch: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onBackToStack,
                modifier = Modifier.height(44.dp).testTag("effects_library_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "الرجوع لقائمة الإضافات",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "القائمة",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    fontSize = 13.sp
                )
            }

            Text(
                text = "مكتبة المؤثرات (${uiState.displayedEffects.size})",
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colorScheme.onSurface
            )

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.size(44.dp).testTag("reeffect_search_toggle")
            ) {
                Icon(
                    imageVector = if (uiState.isSearchVisible) Icons.Rounded.Close else Icons.Outlined.Search,
                    contentDescription = "بحث في المؤثرات",
                    tint = colorScheme.primary
                )
            }
        }

        if (uiState.isSearchVisible) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onUpdateSearchQuery,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "ابحث بالاسم أو التصنيف...",
                        fontFamily = CairoFontFamily,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reeffect_search_input")
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "favorites_chip") {
                FilterChip(
                    selected = uiState.showFavoritesOnly,
                    onClick = onToggleFavoritesFilter,
                    leadingIcon = {
                        Icon(
                            imageVector = if (uiState.showFavoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "المفضلة",
                            fontFamily = CairoFontFamily,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary,
                        selectedLabelColor = colorScheme.onPrimary,
                        selectedLeadingIconColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("effects_tab_favorites")
                )
            }

            items(
                items = EffectStudioTab.entries,
                key = { it.name }
            ) { tab ->
                val selected = !uiState.showFavoritesOnly && uiState.selectedTab == tab
                FilterChip(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    label = {
                        Text(
                            text = tab.arabicLabel,
                            fontFamily = CairoFontFamily,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primary,
                        selectedLabelColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("reeffect_tab_${tab.name.lowercase()}")
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = uiState.displayedEffects,
                key = { it.id }
            ) { item ->
                val isFav = item.id in uiState.favoriteEffectIds
                val isSelected = uiState.activePass?.effectId == item.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            width = if (isSelected) 1.6.dp else 0.8.dp,
                            color = if (isSelected) colorScheme.primary else colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EffectAnimatedSampleCard(
                        label = item.arabicDisplayName(),
                        kind = SampleCardKind.Effect(item.id),
                        isSelected = isSelected,
                        sceneThumbnail = sceneThumbnail,
                        onClick = { onSelectEffect(item) },
                        testTag = "reeffect_item_${item.id}",
                        selectedAccentColor = colorScheme.primary,
                        labelColor = colorScheme.onSurface
                    )

                    IconButton(
                        onClick = { onToggleFavoriteEffect(item.id) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(28.dp)
                            .testTag("effects_fav_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "مفضلة",
                            tint = if (isFav) Color(0xFFFFC107) else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

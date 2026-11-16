package com.example.ui.screens.reeffect

import androidx.compose.runtime.Composable
import com.example.ui.screens.effects.EffectsStudioScreen

@Composable
fun ReEffectScreen(
    projectId: String,
    sceneIndices: List<Int>,
    onCancel: () -> Unit,
    onCreated: () -> Unit
) {
    EffectsStudioScreen(
        projectId = projectId,
        sceneIndices = sceneIndices,
        targetLayerId = null,
        onCancel = onCancel,
        onCreated = onCreated
    )
}

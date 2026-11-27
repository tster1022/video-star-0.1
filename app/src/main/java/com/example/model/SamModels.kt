package com.example.model

import kotlinx.serialization.Serializable

/**
 * Interactive tap/prompt point for SAM (Subject Segmentation) cutout selection.
 * Coordinates [x] and [y] are normalized (0f..1f) relative to the layer media frame.
 */
@Serializable
data class SamTapPoint(
    val id: String,
    val x: Float,
    val y: Float,
    val include: Boolean = true
)

/**
 * Stores AI Subject Cutout (SAM) state and GPU matte refinement settings for an EditorLayer.
 */
@Serializable
data class SamCutoutSpec(
    val enabled: Boolean = false,
    val isolateAllSubjects: Boolean = true,
    val tapPoints: List<SamTapPoint> = emptyList(),
    val feather: Float = 0.08f,
    val choke: Float = 0.0f,
    val invert: Boolean = false,
    val maskUriString: String? = null,
    val detectedSubjectCount: Int = 0
) {
    val isActive: Boolean
        get() = enabled && !maskUriString.isNullOrBlank()
}

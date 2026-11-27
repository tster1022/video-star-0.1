package com.example

import com.example.data.VideoStarRepository
import com.example.model.AeLayerKind
import com.example.model.AspectRatioMode
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogScope
import com.example.model.EffectStudioTab
import com.example.model.MediaClip
import com.example.model.StudioLayoutMode
import com.example.ui.screens.effects.EffectsStudioViewModel
import com.example.ui.screens.effects.ShaderGalleryScreen
import com.example.ui.screens.workspace.WorkspaceScreen
import com.example.ui.theme.VideoStarTheme
import com.example.viewmodel.SharedProjectViewModel
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AeMultiLayerAndGalleryTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shader gallery shows all 145 shaders filters cleanly and applies shaders to active video track`() {
        val project = VideoStarRepository.createProject("معرض الشادرات", AspectRatioMode.PORTRAIT)
        VideoStarRepository.addOrUpdatePhotoClip(
            project.id,
            MediaClip.Photo("gal_img_1", "file:///tmp/sample_photo.jpg", durationMs = 3_000L)
        )

        try {
            composeRule.setContent {
                VideoStarTheme(darkTheme = true) {
                    ShaderGalleryScreen(
                        projectId = project.id,
                        sceneIndex = 0,
                        onBack = {},
                        onOpenEffectsStudio = { _, _ -> }
                    )
                }
            }

            composeRule.onNodeWithText("معرض الشادرات (145 GPU)").assertIsDisplayed()
            composeRule.onNodeWithTag("shader_gallery_grid").assertIsDisplayed()

            // Verify Video Star shader card and Alight Motion shader card both exist & toggle on active track
            composeRule.onNodeWithTag("shader_gallery_card_motion_tile").performClick()
            composeRule.waitForIdle()
            val afterFirst = VideoStarRepository.getProjectById(project.id)!!.scenes.first().effectPasses
            assertEquals(listOf("motion_tile"), afterFirst.map { it.effectId })

            // Search for an AM shader among the 145 shaders and apply it too
            composeRule.onNodeWithTag("shader_gallery_search_toggle").performClick()
            composeRule.onNodeWithTag("shader_gallery_search_input").performTextInput("Turbulent Displace")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("shader_gallery_card_am_turbulent_displace").performClick()
            composeRule.waitForIdle()

            val afterSecond = VideoStarRepository.getProjectById(project.id)!!.scenes.first().effectPasses
            assertEquals(listOf("motion_tile", "am_turbulent_displace"), afterSecond.map { it.effectId })

            // Clear removes all passes cleanly
            composeRule.onNodeWithTag("shader_gallery_clear_button").performClick()
            composeRule.waitForIdle()
            assertTrue(VideoStarRepository.getProjectById(project.id)!!.scenes.first().effectPasses.isEmpty())
        } finally {
            VideoStarRepository.deleteProject(project.id)
        }
    }

    @Test
    fun `effect catalog scopes isolate Video Star 35 vs After Effects 110 vs Gallery 145`() {
        val project = VideoStarRepository.createProject("عزل المكتبات", AspectRatioMode.SQUARE)
        VideoStarRepository.addOrUpdatePhotoClip(
            project.id,
            MediaClip.Photo("scope_img", "file:///tmp/scope.jpg", durationMs = 3_000L)
        )
        try {
            val vsVm = EffectsStudioViewModel(
                projectId = project.id,
                sceneIndices = listOf(0),
                catalogScope = EffectCatalogScope.VIDEO_STAR_ONLY
            )
            assertEquals(35, vsVm.uiState.value.displayedEffects.size)
            assertTrue(vsVm.uiState.value.displayedEffects.none { it.category.isAlightMotion })

            val aeVm = EffectsStudioViewModel(
                projectId = project.id,
                sceneIndices = listOf(0),
                catalogScope = EffectCatalogScope.AFTER_EFFECTS_ONLY
            )
            assertEquals(110, aeVm.uiState.value.displayedEffects.size)
            assertTrue(aeVm.uiState.value.displayedEffects.all { it.category.isAlightMotion })
            assertFalse(EffectStudioTab.VIDEO_STAR in aeVm.uiState.value.availableTabs)

            val allVm = EffectsStudioViewModel(
                projectId = project.id,
                sceneIndices = listOf(0),
                catalogScope = EffectCatalogScope.ALL
            )
            assertEquals(145, allVm.uiState.value.displayedEffects.size)
        } finally {
            VideoStarRepository.deleteProject(project.id)
        }
    }

    @Test
    fun `after effects vertical multi layer stack two step trigger and live effect param editing work end to end`() {
        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.AFTER_EFFECTS)
        val project = VideoStarRepository.createProject("AE Multi Stack", AspectRatioMode.PORTRAIT)
        VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
        VideoStarRepository.addOrUpdatePhotoClip(
            project.id,
            MediaClip.Photo("ae_base_1", "file:///tmp/ae_base.jpg", durationMs = 3_000L)
        )

        try {
            composeRule.setContent {
                VideoStarTheme(darkTheme = false) {
                    WorkspaceScreen(
                        projectId = project.id,
                        sharedProjectViewModel = SharedProjectViewModel(),
                        onCloseToHome = {},
                        onOpenRecordVideo = {},
                        onOpenAdjustVideo = { _, _, _, _, _ -> },
                        onOpenAdjustPhoto = { _, _, _ -> },
                        onOpenMotion = { _, _ -> },
                        onOpenVelocity = { _, _ -> },
                        onOpenReEffect = { _, _ -> },
                        onOpenTransform = { _, _ -> },
                        onOpenMix = { _, _ -> },
                        onOpenLayers = {},
                        onOpenTiming = { _, _ -> }
                    )
                }
            }
            composeRule.waitForIdle()

            // 1. Stack additional independent layers (Text, Shape) at 00:00:00
            composeRule.onNodeWithTag("ae_add_button").performClick()
            composeRule.onNodeWithTag("ae_sidebar_text_button").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("ae_add_button").let {
                // Currently in LAYER_PROPERTIES -> go back to IDLE to open Add sheet again
                composeRule.onNodeWithTag("ae_layer_back_button").performClick()
                composeRule.waitForIdle()
                it.performClick()
            }
            composeRule.onNodeWithTag("ae_sidebar_adjustment_button").performClick()
            composeRule.waitForIdle()

            val stacked = VideoStarRepository.getProjectById(project.id)!!.aeLayers
            assertEquals(3, stacked.size)
            assertTrue(stacked.all { it.startTimeMs == 0L })
            assertEquals(AeLayerKind.ADJUSTMENT, stacked[0].layerKind)
            assertEquals(AeLayerKind.TEXT, stacked[1].layerKind)
            assertEquals(AeLayerKind.MEDIA, stacked[2].layerKind)

            composeRule.onNodeWithTag("ae_layer_row_0").assertExists()
            composeRule.onNodeWithTag("ae_layer_row_1").assertExists()
            composeRule.onNodeWithTag("ae_layer_row_2").assertExists()

            // Visibility & lock toggles on row 0 (expand floating circular bubble first)
            composeRule.onNodeWithTag("ae_layer_controls_bubble_0").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("ae_layer_eye_0").performClick()
            composeRule.waitForIdle()
            assertFalse(VideoStarRepository.getProjectById(project.id)!!.aeLayers[0].isVisible)
            composeRule.onNodeWithTag("ae_layer_eye_0").performClick()
            composeRule.waitForIdle()
            assertTrue(VideoStarRepository.getProjectById(project.id)!!.aeLayers[0].isVisible)

            // 2. Open Effects Panel -> Two-step "+ Effect" state machine
            composeRule.onNodeWithTag("ae_effects_card_button").performClick()
            composeRule.onNodeWithTag("ae_layer_effects_sheet").assertIsDisplayed()

            // 1st tap arms button (does NOT open browser yet)
            composeRule.onNodeWithTag("layout_add_effect_button").performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag("ae_effects_browser_sheet").assertCountEquals(0)

            // 2nd tap opens the dedicated AE Effects Browser (110 AE/AM only, Category Level 1)
            composeRule.onNodeWithTag("layout_add_effect_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("ae_effects_browser_sheet").assertIsDisplayed()

            // Verify VS-only effect is excluded and open Distortion category containing Tiles
            composeRule.onAllNodesWithTag("ae_effect_browser_item_vhs_tape").assertCountEquals(0)
            composeRule.onNodeWithTag("ae_category_card_distortion").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("ae_effects_browser_grid")
                .performScrollToNode(hasTestTag("ae_effect_browser_item_am_tiles"))
            composeRule.onNodeWithTag("ae_effect_browser_item_am_tiles").performClick()
            composeRule.waitForIdle()

            // Opens real-time Parameter Editor for added AM effect & toggles keyframe
            composeRule.onNodeWithTag("ae_effect_param_editor").assertIsDisplayed()
            composeRule.onNodeWithTag("ae_param_keyframe_toggle_intensity").performClick()
            composeRule.waitForIdle()

            val updatedPass = VideoStarRepository.getProjectById(project.id)!!
                .aeLayers.first().effectPasses.first()
            assertEquals("am_tiles", updatedPass.effectId)
            assertTrue(updatedPass.keyframesForProperty("intensity").isNotEmpty())
        } finally {
            VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
            VideoStarRepository.deleteProject(project.id)
        }
    }

    @Test
    fun `after effects canvas tap and finger scrub control playback without opening properties or closing workspace`() {
        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.AFTER_EFFECTS)
        val project = VideoStarRepository.createProject("AE Touch Playback", AspectRatioMode.PORTRAIT)
        VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
        VideoStarRepository.addAeStackedLayer(
            projectId = project.id,
            kind = AeLayerKind.MEDIA,
            clip = MediaClip.Video(
                id = "touch_vid_1",
                uriString = "file:///tmp/touch_vid.mp4",
                startTrimMs = 0L,
                endTrimMs = 4_000L,
                durationMs = 4_000L,
                sourceFps = 30
            )
        )
        var closeCount = 0
        var backDispatcher: androidx.activity.OnBackPressedDispatcher? = null

        try {
            composeRule.setContent {
                backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                VideoStarTheme(darkTheme = false) {
                    WorkspaceScreen(
                        projectId = project.id,
                        sharedProjectViewModel = SharedProjectViewModel(),
                        onCloseToHome = { closeCount++ },
                        onOpenRecordVideo = {},
                        onOpenAdjustVideo = { _, _, _, _, _ -> },
                        onOpenAdjustPhoto = { _, _, _ -> },
                        onOpenMotion = { _, _ -> },
                        onOpenVelocity = { _, _ -> },
                        onOpenReEffect = { _, _ -> },
                        onOpenTransform = { _, _ -> },
                        onOpenMix = { _, _ -> },
                        onOpenLayers = {},
                        onOpenTiming = { _, _ -> }
                    )
                }
            }
            composeRule.waitForIdle()

            // Tap on preview canvas toggles playback cleanly without shrinking preview into layer properties sheet
            composeRule.onNodeWithTag("workspace_preview_canvas").performClick()
            composeRule.waitForIdle()
            assertEquals(0, closeCount)
            composeRule.onAllNodesWithTag("ae_layer_properties_panel").assertCountEquals(0)

            // Horizontal finger drag across preview scrubs timeline and pauses without exiting workspace
            composeRule.onNodeWithTag("workspace_preview_canvas").performTouchInput {
                swipeRight(startX = width * 0.25f, endX = width * 0.85f)
            }
            composeRule.waitForIdle()
            assertEquals(0, closeCount)

            // Start playback via transport button, then trigger system back -> pauses playback instead of exiting
            composeRule.onNodeWithTag("ae_play_pause_button").performClick()
            composeRule.waitForIdle()
            composeRule.runOnUiThread { backDispatcher?.onBackPressed() }
            composeRule.waitForIdle()
            assertEquals(0, closeCount)
            composeRule.onNodeWithTag("studio_layout_after_effects").assertIsDisplayed()
        } finally {
            VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
            VideoStarRepository.deleteProject(project.id)
        }
    }
}

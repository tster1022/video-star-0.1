package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.JsonProjectStore
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.MediaClip
import com.example.model.StudioLayoutMode
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.workspace.WorkspaceScreen
import com.example.ui.screens.workspace.WorkspaceViewModel
import com.example.ui.theme.VideoStarTheme
import com.example.viewmodel.SharedProjectViewModel
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
class MultiLayoutSwitcherTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `switching across all 5 studio layouts preserves project clips effects and json persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = VideoStarRepository.createProject("اختبار الأنماط", AspectRatioMode.PORTRAIT)
        VideoStarRepository.addOrUpdatePhotoClip(
            project.id,
            MediaClip.Photo("p1", "file:///tmp/frame.jpg", durationMs = 2_400L)
        )
        val vm = WorkspaceViewModel(project.id)

        // Apply a shader from the unified 145-shader library while in Nova
        vm.toggleQuickShaderEffect("glow_bloom")
        assertEquals(1, VideoStarRepository.getProjectById(project.id)?.scenes?.first()?.resolvedEffectPasses()?.size)

        // Switch through all 5 layouts
        StudioLayoutMode.entries.forEach { layout ->
            vm.openLayoutModal()
            assertTrue(vm.uiState.value.isLayoutModalVisible)
            vm.selectStudioLayout(layout)
            assertFalse(vm.uiState.value.isLayoutModalVisible)
            assertEquals(layout, vm.uiState.value.studioLayout)
            // Clips and effects stay intact across every layout switch
            val currentProj = VideoStarRepository.getProjectById(project.id)!!
            assertEquals(1, currentProj.scenes.size)
            assertEquals(1, currentProj.scenes[0].resolvedEffectPasses().size)
        }

        // Persist last selected layout (After Effects) across JsonProjectStore roundtrip
        val current = VideoStarRepository.projects.value.first { it.id == project.id }
        JsonProjectStore.saveProjects(context, listOf(current))
        val reloaded = JsonProjectStore.loadProjects(context).first { it.id == project.id }
        assertEquals(StudioLayoutMode.AFTER_EFFECTS, reloaded.studioLayout)

        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `strict mode rule locks and hides layout switcher during effect trim split and selection modes`() {
        val project = VideoStarRepository.createProject("اختبار وضع التأثير", AspectRatioMode.SQUARE)
        VideoStarRepository.addOrUpdatePhotoClip(
            project.id,
            MediaClip.Photo("p1", "file:///tmp/frame.jpg", durationMs = 3_000L)
        )
        val vm = WorkspaceViewModel(project.id)

        // Home Mode -> switcher allowed
        assertFalse(vm.uiState.value.isInEditingOrEffectMode)
        vm.openLayoutModal()
        assertTrue(vm.uiState.value.isLayoutModalVisible)

        // Enter Effect Mode -> closes modal, sets editing flag, blocks opening modal
        vm.enterEffectMode()
        assertTrue(vm.uiState.value.isInlineEffectMode)
        assertTrue(vm.uiState.value.isInEditingOrEffectMode)
        assertFalse(vm.uiState.value.isLayoutModalVisible)

        vm.openLayoutModal()
        assertFalse(vm.uiState.value.isLayoutModalVisible)

        // Toggle quick effect + undo/redo
        vm.toggleQuickShaderEffect("hard_shake")
        assertEquals(1, VideoStarRepository.getProjectById(project.id)?.scenes?.first()?.resolvedEffectPasses()?.size)
        vm.undoTimelineEdit()
        assertEquals(0, VideoStarRepository.getProjectById(project.id)?.scenes?.first()?.resolvedEffectPasses()?.size)

        // Exit Effect Mode -> restores Home Mode
        vm.exitEffectMode()
        assertFalse(vm.uiState.value.isInlineEffectMode)
        assertFalse(vm.uiState.value.isInEditingOrEffectMode)

        // Split Mode also triggers editing mode gating
        vm.enterSplitMode()
        assertTrue(vm.uiState.value.isInEditingOrEffectMode)
        vm.cancelSplitMode()
        assertFalse(vm.uiState.value.isInEditingOrEffectMode)

        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `compose ui switcher pill opens 5 layout modal and strictly unmounts during effect mode`() {
        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        val project = VideoStarRepository.createProject("تجربة الواجهة", AspectRatioMode.PORTRAIT)
        VideoStarRepository.addOrUpdatePhotoClip(
            project.id,
            MediaClip.Photo("p1", "file:///tmp/frame.jpg", durationMs = 2_000L)
        )

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

        // 1. Top-left switcher starts as a compact circle in Home Mode (name hidden until 1st tap)
        composeRule.onNodeWithTag("workspace_layout_switcher_button").assertIsDisplayed()
        composeRule.onNodeWithText("Nova").assertDoesNotExist()
        composeRule.onNodeWithTag("layout_selector_modal").assertDoesNotExist()

        // Step 1 tap expands circular button to show current editor name ("Nova") without opening modal
        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Nova").assertIsDisplayed()
        composeRule.onNodeWithTag("layout_selector_modal").assertDoesNotExist()

        // 2. Step 2 tap opens Layout Modal and verify all 5 layouts + status badges exist
        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("layout_selector_modal").assertIsDisplayed()
        composeRule.onNodeWithTag("layout_option_nova").assertExists()
        composeRule.onNodeWithTag("layout_option_capcut").assertExists()
        composeRule.onNodeWithTag("layout_option_blurrr").assertExists()
        composeRule.onNodeWithTag("layout_option_node_video").assertExists()
        composeRule.onNodeWithTag("layout_option_after_effects").assertExists()
        composeRule.onNodeWithText("Unavailable for now").assertExists()
        composeRule.onNodeWithText("Needs a large screen").assertExists()

        // 3. Switch to CapCut -> completes blueprint transition, collapses switcher to circle, and 1st tap shows CapCut
        composeRule.onNodeWithTag("layout_option_capcut").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_layout_capcut").assertIsDisplayed()
        composeRule.onNodeWithText("CapCut").assertDoesNotExist()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithText("CapCut").assertIsDisplayed()

        // 4. Tap "إضافة تأثير" -> enters Effect Mode and strictly unmounts top-left Switcher pill
        composeRule.onNodeWithTag("layout_add_effect_button").performClick()
        composeRule.onNodeWithTag("workspace_effect_mode_drawer").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").assertDoesNotExist()

        // 5. Exit Effect Mode via Done ("تم") -> restores top-left Switcher circle
        composeRule.onNodeWithTag("effect_mode_done_button").performClick()
        composeRule.onNodeWithTag("workspace_effect_mode_drawer").assertDoesNotExist()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").assertIsDisplayed()

        // 6. Switch to Blurrr, Node Video, and After Effects cleanly using 2-step tap
        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("layout_option_blurrr").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_layout_blurrr").assertIsDisplayed()

        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("layout_option_node_video").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_layout_node_video").assertIsDisplayed()

        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("layout_option_after_effects").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_layout_after_effects").assertIsDisplayed()

        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `home screen switcher pill switches layout with blueprint overlay and persists to new projects`() {
        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        val project = VideoStarRepository.createProject("مشروع الواجهة الرئيسية", AspectRatioMode.SQUARE)
        VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.NOVA)

        composeRule.setContent {
            VideoStarTheme(darkTheme = false) {
                HomeScreen(
                    sharedProjectViewModel = SharedProjectViewModel(),
                    onOpenProject = {},
                    onOpenAdjustVideo = { _, _, _, _ -> }
                )
            }
        }

        // Top-left circular switcher button is visible on HomeScreen (1st tap shows Nova, 2nd tap opens modal)
        composeRule.onNodeWithTag("home_layout_switcher_button").assertIsDisplayed()
        composeRule.onNodeWithText("Nova").assertDoesNotExist()

        // Switch Home layout from Nova -> CapCut via 2-step click
        composeRule.onNodeWithTag("home_layout_switcher_button").performClick()
        composeRule.onNodeWithText("Nova").assertIsDisplayed()
        composeRule.onNodeWithTag("home_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("layout_selector_modal").assertIsDisplayed()
        composeRule.onNodeWithTag("layout_option_capcut").performClick()
        composeRule.waitForIdle()

        assertEquals(StudioLayoutMode.CAPCUT, VideoStarRepository.preferredLayout.value)
        composeRule.onNodeWithTag("home_layout_switcher_button").performClick()
        composeRule.onNodeWithText("CapCut").assertIsDisplayed()

        // Newly created project inherits the layout selected on HomeScreen
        val created = VideoStarRepository.createProject("مشروع كاب كات جديد", AspectRatioMode.PORTRAIT)
        assertEquals(StudioLayoutMode.CAPCUT, created.studioLayout)

        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        VideoStarRepository.deleteProject(created.id)
        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `blueprint wireframe transition displays neon overlay mid-switch and dismisses cleanly on completion`() {
        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        val project = VideoStarRepository.createProject("انتقال المخطط", AspectRatioMode.PORTRAIT)
        VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.NOVA)

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

        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").performClick()
        composeRule.onNodeWithTag("layout_selector_modal").assertIsDisplayed()
        composeRule.onNodeWithTag("layout_option_blurrr").performScrollTo()

        // Freeze Compose frame clock before triggering layout change to inspect mid-transition wireframe state
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("layout_option_blurrr").performClick()

        // Advance past 150ms menu exit and into the Blueprint Wireframe stage (~360ms)
        composeRule.mainClock.advanceTimeBy(360L)
        composeRule.onNodeWithTag("blueprint_transition_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("blueprint_building_subtitle").assertTextContains("Building layout...")
        composeRule.onNodeWithTag("blueprint_layout_title").assertIsDisplayed()

        // Advance past full 1000ms transition duration and settle
        composeRule.mainClock.advanceTimeBy(800L)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // Wireframe overlay is gone and Blurrr layout is active
        composeRule.onNodeWithTag("blueprint_transition_overlay").assertDoesNotExist()
        composeRule.onNodeWithTag("studio_layout_blurrr").assertIsDisplayed()

        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `layout selector modal renders uniform card heights and single line feature pills without wrapping captions`() {
        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        val project = VideoStarRepository.createProject("اختبار تناسق البطاقات", AspectRatioMode.PORTRAIT)
        VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.NOVA)

        try {
            composeRule.setContent {
                VideoStarTheme(darkTheme = false) {
                    HomeScreen(
                        sharedProjectViewModel = SharedProjectViewModel(),
                        onOpenProject = {},
                        onOpenAdjustVideo = { _, _, _, _ -> }
                    )
                }
            }

            composeRule.onNodeWithTag("home_layout_switcher_button").performClick()
            composeRule.onNodeWithTag("home_layout_switcher_button").performClick()
            composeRule.onNodeWithTag("layout_selector_modal").assertIsDisplayed()

            // Header subtitle & close button co-exist cleanly
            composeRule.onNodeWithText("Switch workspace interface — project & effects stay intact").assertIsDisplayed()
            composeRule.onNodeWithTag("layout_modal_close_button").assertIsDisplayed()

            // Active checkmark appears only on Nova initially (unmerged tree because card root is clickable)
            composeRule.onNodeWithTag("layout_active_checkmark_nova", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithTag("layout_active_checkmark_capcut", useUnmergedTree = true).assertDoesNotExist()

            // Measure each card & feature pill strip's true unclipped layout height (semanticsNode.size.height)
            composeRule.onNodeWithTag("layout_option_nova").performScrollTo()
            val novaCardNode = composeRule.onNodeWithTag("layout_option_nova").fetchSemanticsNode()
            val novaPillNode = composeRule.onNodeWithTag("layout_feature_pill_nova", useUnmergedTree = true).fetchSemanticsNode()

            composeRule.onNodeWithTag("layout_option_capcut").performScrollTo()
            val capCutCardNode = composeRule.onNodeWithTag("layout_option_capcut").fetchSemanticsNode()
            val capCutPillNode = composeRule.onNodeWithTag("layout_feature_pill_capcut", useUnmergedTree = true).fetchSemanticsNode()

            composeRule.onNodeWithTag("layout_option_blurrr").performScrollTo()
            val blurrrCardNode = composeRule.onNodeWithTag("layout_option_blurrr").fetchSemanticsNode()
            val blurrrPillNode = composeRule.onNodeWithTag("layout_feature_pill_blurrr", useUnmergedTree = true).fetchSemanticsNode()

            composeRule.onNodeWithTag("layout_option_node_video").performScrollTo()
            val nodeCardNode = composeRule.onNodeWithTag("layout_option_node_video").fetchSemanticsNode()
            val nodePillNode = composeRule.onNodeWithTag("layout_feature_pill_node_video", useUnmergedTree = true).fetchSemanticsNode()

            composeRule.onNodeWithTag("layout_option_after_effects").performScrollTo()
            val aeCardNode = composeRule.onNodeWithTag("layout_option_after_effects").fetchSemanticsNode()
            val aePillNode = composeRule.onNodeWithTag("layout_feature_pill_after_effects", useUnmergedTree = true).fetchSemanticsNode()

            assertEquals(novaPillNode.size.height, capCutPillNode.size.height)
            assertEquals(novaPillNode.size.height, blurrrPillNode.size.height)
            assertEquals(novaPillNode.size.height, nodePillNode.size.height)
            assertEquals(novaPillNode.size.height, aePillNode.size.height)

            assertEquals(novaCardNode.size.height, capCutCardNode.size.height)
            assertEquals(novaCardNode.size.height, blurrrCardNode.size.height)
            assertEquals(novaCardNode.size.height, nodeCardNode.size.height)
            assertEquals(novaCardNode.size.height, aeCardNode.size.height)

            composeRule.onNodeWithTag("layout_modal_close_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("layout_selector_modal").assertDoesNotExist()
        } finally {
            VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
            VideoStarRepository.deleteProject(project.id)
        }
    }

    @Test
    fun `after effects mode renders all 5 states from idle to add sheet layer properties and effects panel`() {
        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.AFTER_EFFECTS)
        val project = VideoStarRepository.createProject("New project 3", AspectRatioMode.PORTRAIT)
        VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
        VideoStarRepository.addOrUpdatePhotoClip(
            project.id,
            MediaClip.Photo("ae_img_1", "file:///tmp/ae_layer.jpg", durationMs = 3_000L)
        )

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

        // State A & B: Idle Main Toolbar + Scrollable Tools + Auto Quality + Switcher Pill
        composeRule.onNodeWithTag("studio_layout_after_effects").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").assertIsDisplayed()
        composeRule.onNodeWithTag("ae_add_button").assertIsDisplayed()
        composeRule.onNodeWithTag("ae_tool_ai").assertIsDisplayed()
        composeRule.onNodeWithText("Auto").assertIsDisplayed()
        composeRule.onNodeWithText("00:00:00").assertIsDisplayed()
        composeRule.onNodeWithTag("layout_add_effect_button").performScrollTo().assertExists()

        // State C: Tap "+ Add" -> opens dark-teal Add Media Sheet with sidebar & Open gallery CTA
        composeRule.onNodeWithTag("ae_add_button").performClick()
        composeRule.onNodeWithTag("ae_add_bottom_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Media").assertIsDisplayed()
        composeRule.onNodeWithText("Audio").assertIsDisplayed()
        composeRule.onNodeWithText("Shape").assertIsDisplayed()
        composeRule.onNodeWithText("Element").assertIsDisplayed()
        composeRule.onNodeWithTag("ae_open_gallery_button").assertIsDisplayed()
        composeRule.onNodeWithText("Open gallery").assertIsDisplayed()
        composeRule.onNodeWithText("Text").assertIsDisplayed()
        composeRule.onNodeWithText("Adjustment").assertIsDisplayed()
        composeRule.onNodeWithText("Null").assertIsDisplayed()

        // State D: Tap clip on timeline -> selects layer, transforms header & shows Layer Properties grid
        composeRule.onNodeWithTag("ae_timeline_clip_0").performClick()
        composeRule.onNodeWithTag("ae_duplicate_layer_button").assertIsDisplayed()
        composeRule.onNodeWithTag("ae_delete_layer_button").assertIsDisplayed()
        composeRule.onNodeWithTag("ae_layer_properties_panel").assertIsDisplayed()
        composeRule.onNodeWithText("Border & Shadow").assertIsDisplayed()
        composeRule.onNodeWithText("Opacity & Blending").assertIsDisplayed()
        composeRule.onNodeWithText("Animate").assertIsDisplayed()
        composeRule.onNodeWithTag("ae_effects_card_button").assertIsDisplayed()

        // State E: Tap "Effects" card -> opens dark-teal Layer Effects sheet & enforces Strict Mode Rule
        composeRule.onNodeWithTag("ae_effects_card_button").performClick()
        composeRule.onNodeWithTag("ae_layer_effects_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("ae_effects_presets_button").assertIsDisplayed()
        composeRule.onNodeWithTag("layout_add_effect_button").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Effects run as a chain, top to bottom, on what the layer drew. Tap '+ Effect' to start."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").assertDoesNotExist()

        // Back out of Effects panel -> returns to Layer Properties and restores Layout Switcher
        composeRule.onNodeWithTag("ae_effects_back_button").performClick()
        composeRule.onNodeWithTag("ae_layer_properties_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_layout_switcher_button").assertIsDisplayed()

        VideoStarRepository.updatePreferredLayout(StudioLayoutMode.NOVA)
        VideoStarRepository.deleteProject(project.id)
    }
}

package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.data.VideoStarRepository
import com.example.engine.ae.AeEffectEngine
import com.example.engine.ae.AeEffectEngineCatalog
import com.example.engine.ae.AeEngineCategory
import com.example.model.AeLayerKind
import com.example.model.AspectRatioMode
import com.example.model.BezierCurveSpec
import com.example.model.EffectBlendMode
import com.example.model.EditorLayer
import com.example.model.LayerKeyframe
import com.example.model.MediaClip
import com.example.model.StudioLayoutMode
import com.example.ui.screens.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AeStandaloneEngineUnitTest {

    @Test
    fun `ae effect engine catalog strictly excludes video star effects and includes navigable categories`() {
        val aeIds = AeEffectEngineCatalog.allEffects.map { it.id }.toSet()
        assertTrue(aeIds.isNotEmpty())
        assertTrue("am_tiles" in aeIds)
        assertTrue("am_turbulent_displace" in aeIds)

        // Video Star specific effects must NEVER leak into standalone AE engine
        val vsIds = listOf("vhs_tape", "motion_tile", "hard_shake", "gaussian_blur", "rgb_split", "lens_bokeh")
        vsIds.forEach { vsId ->
            assertFalse("Video Star effect $vsId leaked into AE catalog", vsId in aeIds)
            assertFalse(AeEffectEngine.isAeSupportedEffect(vsId))
            assertNull(AeEffectEngine.createPassForLayer(vsId))
        }

        val navigable = AeEffectEngineCatalog.navigableCategories()
        assertEquals(AeEngineCategory.MY_EFFECTS, navigable.first())
        assertTrue(AeEngineCategory.TRANSFORM in navigable)
        assertTrue(AeEngineCategory.DISTORTION in navigable)
        assertTrue(AeEngineCategory.OTHER in navigable)
        assertTrue(navigable.size >= 10)
    }

    @Test
    fun `composeAeLayerTransformAt interpolates keyframes with bezier easing and opacity`() {
        val layer = EditorLayer(
            id = "ae_kf_layer",
            number = 1,
            startTimeMs = 1_000L,
            durationMs = 2_000L,
            isMirrored = true,
            keyframes = listOf(
                LayerKeyframe(
                    id = "kf_start",
                    timeProgress = 0f,
                    offsetX = 0f,
                    offsetY = 0f,
                    scale = 1f,
                    rotation = 0f,
                    opacity = 0.2f,
                    curve = BezierCurveSpec(0f, 0f, 1f, 1f) // Linear for deterministic midpoint
                ),
                LayerKeyframe(
                    id = "kf_end",
                    timeProgress = 1f,
                    offsetX = 100f,
                    offsetY = -50f,
                    scale = 2f,
                    rotation = 90f,
                    opacity = 1f
                )
            )
        )

        val atStart = AeEffectEngine.composeAeLayerTransformAt(layer, 500L) // clamped before startTimeMs
        assertEquals(0f, atStart.offsetX, 0.01f)
        assertEquals(1f, atStart.scale, 0.01f)
        assertEquals(0.2f, atStart.opacity, 0.01f)
        assertEquals(-1f, atStart.scaleXSign, 0.01f)
        assertEquals(1f, atStart.scaleYSign, 0.01f)

        val atMid = AeEffectEngine.composeAeLayerTransformAt(layer, 2_000L) // t = 0.5
        assertEquals(50f, atMid.offsetX, 0.01f)
        assertEquals(-25f, atMid.offsetY, 0.01f)
        assertEquals(1.5f, atMid.scale, 0.01f)
        assertEquals(45f, atMid.rotation, 0.01f)
        assertEquals(0.6f, atMid.opacity, 0.01f)

        val atEnd = AeEffectEngine.composeAeLayerTransformAt(layer, 4_000L) // clamped past end
        assertEquals(100f, atEnd.offsetX, 0.01f)
        assertEquals(2f, atEnd.scale, 0.01f)
        assertEquals(90f, atEnd.rotation, 0.01f)
        assertEquals(1f, atEnd.opacity, 0.01f)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `workspace viewModel adds only standalone AE effects and edits inline layer props split and trim without affecting scenes`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val project = VideoStarRepository.createProject("AE Standalone Ops", AspectRatioMode.PORTRAIT)
        val store = ViewModelStore()
        try {
            VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
            val created = VideoStarRepository.addAeStackedLayer(
                projectId = project.id,
                kind = AeLayerKind.MEDIA,
                clip = MediaClip.Video(
                    id = "ae_vid_1",
                    uriString = "file:///tmp/ae_vid.mp4",
                    startTrimMs = 0L,
                    endTrimMs = 4_000L,
                    durationMs = 4_000L
                ),
                startTimeMs = 0L,
                durationMs = 4_000L
            )!!

            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkspaceViewModel(project.id) as T
            }
            val vm = ViewModelProvider(store, factory)[WorkspaceViewModel::class.java]

            // Rejects Video Star effect IDs, accepts standalone AE effect IDs
            assertNull(vm.addAeLayerEffect(created.id, "vhs_tape"))
            val applied = vm.addAeLayerEffect(created.id, "am_turbulent_displace")
            assertNotNull(applied)
            assertEquals("am_turbulent_displace", applied!!.effectId)

            // Update inline AE properties cleanly
            val curLayer = vm.uiState.value.aeLayers.first { it.id == created.id }
            vm.updateAeLayerProperties(
                created.id,
                curLayer.copy(
                    speedMultiplier = 1.5f,
                    blendMode = EffectBlendMode.SCREEN,
                    borderWidthDp = 4f,
                    shadowOpacity = 0.7f,
                    audioVolume = 0.4f
                ),
                recordUndo = true
            )
            val updatedLayer = vm.uiState.value.aeLayers.first { it.id == created.id }
            assertEquals(1.5f, updatedLayer.speedMultiplier, 0.01f)
            assertEquals(EffectBlendMode.SCREEN, updatedLayer.blendMode)
            assertEquals(4f, updatedLayer.borderWidthDp, 0.01f)
            assertEquals(0.7f, updatedLayer.shadowOpacity, 0.01f)
            assertEquals(0.4f, updatedLayer.audioVolume, 0.01f)

            // Keyframe toggle at playhead creates and then removes transform keyframe
            vm.seekToPositionMs(2_000L)
            vm.toggleAeLayerTransformKeyframe(created.id)
            assertEquals(1, vm.uiState.value.aeLayers.first { it.id == created.id }.keyframes.size)
            vm.toggleAeLayerTransformKeyframe(created.id)
            assertEquals(0, vm.uiState.value.aeLayers.first { it.id == created.id }.keyframes.size)

            // Split layer at 2000ms splits cleanly into 2 AE layers without touching scenes
            val sceneCountBeforeSplit = VideoStarRepository.getProjectById(project.id)!!.scenes.size
            val secondId = vm.splitAeLayerAtPlayhead(created.id)
            assertNotNull(secondId)
            val layersAfterSplit = vm.uiState.value.aeLayers
            assertEquals(2, layersAfterSplit.size)
            assertEquals(sceneCountBeforeSplit, VideoStarRepository.getProjectById(project.id)!!.scenes.size)

            // Trim start & end on the second half
            vm.seekToPositionMs(2_500L)
            vm.trimAeLayerStartToPlayhead(secondId!!)
            val trimmedStart = vm.uiState.value.aeLayers.first { it.id == secondId }
            assertEquals(2_500L, trimmedStart.startTimeMs)

            vm.seekToPositionMs(3_200L)
            vm.trimAeLayerEndToPlayhead(secondId)
            val trimmedEnd = vm.uiState.value.aeLayers.first { it.id == secondId }
            assertEquals(700L, trimmedEnd.effectiveLayerDurationMs)
        } finally {
            store.clear()
            VideoStarRepository.deleteProject(project.id)
            Dispatchers.resetMain()
        }
    }
}

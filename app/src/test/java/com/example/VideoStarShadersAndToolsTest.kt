package com.example

import com.example.data.VideoStarRepository
import com.example.gl.ShaderLibrary
import com.example.model.AspectRatioMode
import com.example.model.EffectCatalog
import com.example.model.EffectCategory
import com.example.model.EffectKeyframe
import com.example.model.EffectStudioTab
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.MediaClip
import com.example.model.MotionPreset
import com.example.model.ShaderEffectPass
import com.example.model.TextLayerSpec
import com.example.model.TransitionCatalog
import com.example.model.TransitionCategory
import com.example.ui.screens.effects.EffectsStudioViewModel
import com.example.ui.screens.keyframe.KeyframeViewModel
import com.example.ui.screens.layers.LayersViewModel
import com.example.ui.screens.multilayer.MultiLayerViewModel
import com.example.ui.screens.transform.TransformAnimationMode
import com.example.ui.screens.transform.TransformEndpoint
import com.example.ui.screens.transform.TransformViewModel
import com.example.ui.screens.workspace.WorkspaceViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoStarShadersAndToolsTest {

    @Test
    fun `effect catalog provides 35 shaders across 6 categories and matches ShaderLibrary`() {
        assertEquals(35, EffectCatalog.shaderEffects.size)
        assertEquals(6, EffectCategory.entries.size)

        EffectCategory.entries.forEach { category ->
            val count = EffectCatalog.shaderEffects.count { it.category == category }
            assertTrue("Expected effects in $category", count > 0)
        }

        EffectCatalog.shaderEffects.forEach { effect ->
            assertTrue("ShaderLibrary missing GLSL body for ${effect.id}", ShaderLibrary.hasShader(effect.id))
            val glsl = ShaderLibrary.fragmentShaderFor(effect.id)
            assertTrue(glsl.contains("#version 300 es"))
            assertTrue(glsl.contains("outColor"))
        }

        // Legacy ID aliases resolve cleanly
        assertEquals("tile_grid", EffectCatalog.resolveCanonicalId("tile_2x2"))
        assertEquals("spin_vortex", EffectCatalog.resolveCanonicalId("spin_blur"))
        assertNotNull(EffectCatalog.findById("tile_2x2"))
        assertNotNull(EffectCatalog.findById("spin_blur"))
    }

    @Test
    fun `shader effect pass evaluates static intensity and interpolates intensity keyframes`() {
        val staticPass = ShaderEffectPass(
            id = "p_static",
            effectId = "rgb_split",
            effectName = "RGB Split",
            category = EffectCategory.COLOR_GLOW,
            intensity = 0.64f
        )
        assertEquals(0.64f, staticPass.evaluateIntensityAt(0.0f), 0.001f)
        assertEquals(0.64f, staticPass.evaluateIntensityAt(0.75f), 0.001f)

        val animatedPass = staticPass.copy(
            keyframes = listOf(
                EffectKeyframe("k1", 0.2f, 0.2f),
                EffectKeyframe("k2", 0.8f, 0.8f)
            )
        )
        assertEquals(0.2f, animatedPass.evaluateIntensityAt(0.0f), 0.001f)
        assertEquals(0.5f, animatedPass.evaluateIntensityAt(0.5f), 0.001f)
        assertEquals(0.8f, animatedPass.evaluateIntensityAt(1.0f), 0.001f)
    }

    @Test
    fun `workspace clearActiveSceneEffects and interactive trim mode modify and persist scene`() {
        val project = VideoStarRepository.createProject("اختبار التحرير", AspectRatioMode.PORTRAIT)
        VideoStarRepository.addOrUpdateVideoClip(
            project.id,
            MediaClip.Video(
                id = "vid_1",
                uriString = "file:///tmp/sample.mp4",
                startTrimMs = 0L,
                endTrimMs = 5_000L,
                durationMs = 5_000L
            )
        )
        val vhs = EffectCatalog.findById("vhs_tape")!!
        VideoStarRepository.applySceneEffectPasses(
            project.id,
            setOf(0),
            listOf(
                ShaderEffectPass(
                    id = "p1",
                    effectId = vhs.id,
                    effectName = vhs.name,
                    category = vhs.category,
                    intensity = 0.8f
                )
            )
        )

        val ws = WorkspaceViewModel(project.id)
        assertEquals(1, VideoStarRepository.getProjectById(project.id)?.scenes?.first()?.resolvedEffectPasses()?.size)

        ws.clearActiveSceneEffects()
        val afterClear = VideoStarRepository.getProjectById(project.id)?.scenes?.first()
        assertNotNull(afterClear)
        assertEquals("none", afterClear?.effect?.effectId)
        assertTrue(afterClear?.resolvedEffectPasses()?.isEmpty() == true)

        ws.openTrimDialog()
        assertTrue(ws.uiState.value.isTrimMode)
        ws.updateTrimRange(500L, 2_750L)
        assertEquals(500L, ws.uiState.value.trimStartMs)
        assertEquals(2_750L, ws.uiState.value.trimEndMs)

        ws.confirmTrimMode()
        assertFalse(ws.uiState.value.isTrimMode)
        val afterTrim = VideoStarRepository.getProjectById(project.id)?.scenes?.first()
        assertEquals(2_250L, afterTrim?.effectiveDurationMs)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `effects studio supports multi-pass shader stacking keyframes params and layer saving`() {
        val project = VideoStarRepository.createProject("استوديو المؤثرات", AspectRatioMode.SQUARE)
        VideoStarRepository.addOrUpdatePhotoClip(
            project.id,
            MediaClip.Photo("p_fx", "file:///tmp/fx.jpg", durationMs = 4_000L)
        )

        val studioVm = EffectsStudioViewModel(project.id, listOf(0))
        val rgb = EffectCatalog.findById("rgb_split")!!
        val vhs = EffectCatalog.findById("vhs_tape")!!

        studioVm.selectEffect(rgb)
        studioVm.updatePassParam("distance", 0.9f)
        studioVm.seekToProgress(0.25f)
        studioVm.updateIntensity(0.2f)
        studioVm.toggleKeyframeAtCurrentTime()
        studioVm.seekToProgress(0.75f)
        studioVm.toggleKeyframeAtCurrentTime()
        studioVm.updateIntensity(0.95f)

        studioVm.addPass()
        studioVm.selectEffect(vhs)
        studioVm.updatePassParam("tracking", 0.77f)

        assertEquals(2, studioVm.uiState.value.passes.size)

        var savedScene = false
        studioVm.createAndSave { savedScene = true }
        assertTrue(savedScene)

        val scenePasses = VideoStarRepository.getProjectById(project.id)!!
            .scenes
            .first()
            .resolvedEffectPasses()
        assertEquals(2, scenePasses.size)
        assertEquals("rgb_split", scenePasses[0].effectId)
        assertEquals(0.9f, scenePasses[0].param("distance"), 0.01f)
        assertEquals(2, scenePasses[0].keyframes.size)
        assertEquals(0.575f, scenePasses[0].evaluateIntensityAt(0.5f), 0.01f)
        assertEquals("vhs_tape", scenePasses[1].effectId)
        assertEquals(0.77f, scenePasses[1].param("tracking"), 0.01f)

        // Now verify saving shader stack onto a Multi-Layer EditorLayer
        val layerStudio = EffectsStudioViewModel(project.id, listOf(0), targetLayerId = "layer_1")
        val kaleido = EffectCatalog.findById("kaleidoscope")!!
        layerStudio.selectEffect(kaleido)
        layerStudio.updatePassParam("sides", 8f)
        var savedLayer = false
        layerStudio.createAndSave { savedLayer = true }
        assertTrue(savedLayer)

        val updatedLayer = VideoStarRepository.getProjectById(project.id)!!.layers.first { it.id == "layer_1" }
        assertEquals(1, updatedLayer.effectPasses.size)
        assertEquals("kaleidoscope", updatedLayer.effectPasses[0].effectId)
        assertEquals(8f, updatedLayer.effectPasses[0].param("sides"), 0.01f)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `keyframe viewmodel interpolates 3d rotationX rotationY and perspectiveFov across keyframes`() {
        val project = VideoStarRepository.createProject("اختبار 3D", AspectRatioMode.PORTRAIT)
        val vm = KeyframeViewModel(project.id, "layer_1")

        vm.toggle3DEnabled()
        assertTrue(vm.uiState.value.is3DEnabled)

        vm.scrubTo(0.0f)
        vm.clearAllKeyframesExceptCurrent()
        vm.updateRotationX(-30f)
        vm.updateRotationY(20f)
        vm.updatePerspectiveFov(35f)

        vm.scrubTo(1.0f)
        vm.addOrUpdateKeyframeAtCurrentTime()
        vm.updateRotationX(30f)
        vm.updateRotationY(-20f)
        vm.updatePerspectiveFov(75f)

        vm.scrubTo(0.5f)
        assertTrue(vm.uiState.value.displayRotationX > -15f && vm.uiState.value.displayRotationX < 30f)
        assertTrue(vm.uiState.value.displayRotationY < 10f && vm.uiState.value.displayRotationY > -20f)
        assertTrue(vm.uiState.value.displayPerspectiveFov > 45f && vm.uiState.value.displayPerspectiveFov <= 75f)

        var saved = false
        vm.saveAndReturn { saved = true }
        assertTrue(saved)

        val layer = VideoStarRepository.getProjectById(project.id)!!.layers.first { it.id == "layer_1" }
        assertTrue(layer.is3DEnabled)
        assertEquals(2, layer.keyframes.size)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `layers and multilayer viewmodels update mask modes shapes feather invert and rich text spec`() {
        val project = VideoStarRepository.createProject("اختبار القناع والنص", AspectRatioMode.PORTRAIT)

        val layersVm = LayersViewModel(project.id)
        layersVm.selectLayer(0)
        layersVm.setLayerMask(
            mode = MaskMode.SHAPE,
            shape = MaskShape.HEART,
            feather = 0.45f,
            invert = true,
            linearAngle = 90f
        )
        val layerAfterMask = layersVm.uiState.value.activeLayer!!
        assertEquals(MaskMode.SHAPE, layerAfterMask.maskMode)
        assertEquals(MaskShape.HEART, layerAfterMask.maskShape)
        assertEquals(0.45f, layerAfterMask.maskFeather, 0.01f)
        assertTrue(layerAfterMask.maskInvert)

        val richText = TextLayerSpec(
            text = "Video Star Pro",
            textColorArgb = 0xFFFF2A85L,
            strokeColorArgb = 0xFFFFFFFFL,
            strokeWidthPx = 4f,
            shadowRadius = 10f,
            glowIntensity = 0.8f,
            fontSizeSp = 36f,
            fontStyleId = "mono_arcade"
        )
        layersVm.setLayerTextSpec(richText)
        assertEquals("Video Star Pro", layersVm.uiState.value.activeLayer?.effectiveText)
        assertEquals(0.8f, layersVm.uiState.value.activeLayer?.textSpec?.glowIntensity ?: 0f, 0.01f)
        layersVm.syncToRepository()

        val mlVm = MultiLayerViewModel(project.id)
        mlVm.selectLayer(1)
        mlVm.updateLayerMask(
            mode = MaskMode.LINEAR,
            shape = MaskShape.TRIANGLE,
            feather = 0.6f,
            invert = false,
            linearAngle = 135f
        )
        mlVm.setLayerTextSpec(richText.copy(text = "Multi Layer Text", fontStyleId = "sf_display"))

        val mlLayer = mlVm.uiState.value.activeLayer!!
        assertEquals(MaskMode.LINEAR, mlLayer.maskMode)
        assertEquals(MaskShape.TRIANGLE, mlLayer.maskShape)
        assertEquals(135f, mlLayer.maskLinearAngle, 0.01f)
        assertEquals("Multi Layer Text", mlLayer.effectiveText)
        assertEquals("sf_display", mlLayer.textSpec.fontStyleId)

        var done = false
        mlVm.commitAndCreate { done = true }
        assertTrue(done)

        val savedProject = VideoStarRepository.getProjectById(project.id)!!
        assertEquals(MaskMode.LINEAR, savedProject.layers[1].maskMode)
        assertEquals("Multi Layer Text", savedProject.layers[1].effectiveText)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `effects studio 5 tabs cover all 35 shaders cleanly`() {
        val project = VideoStarRepository.createProject("اختبار التبويبات", AspectRatioMode.PORTRAIT)
        val vm = EffectsStudioViewModel(project.id, listOf(0))

        vm.selectTab(EffectStudioTab.ALL)
        assertEquals(35, vm.uiState.value.displayedEffects.size)

        val nonAllTabs = EffectStudioTab.entries.filter { it != EffectStudioTab.ALL }
        var sum = 0
        nonAllTabs.forEach { tab ->
            vm.selectTab(tab)
            val items = vm.uiState.value.displayedEffects
            assertTrue("Expected effects in $tab", items.isNotEmpty())
            sum += items.size
        }
        assertEquals(35, sum)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `transform viewmodel supports Static vs Start to End animation and interpolates midpoint`() {
        val project = VideoStarRepository.createProject("اختبار التحويل المتحرك", AspectRatioMode.PORTRAIT)
        VideoStarRepository.addOrUpdatePhotoClip(
            project.id,
            MediaClip.Photo("p_tf", "file:///tmp/tf.jpg", durationMs = 3_000L)
        )

        val vm = TransformViewModel(project.id, sceneIndex = 0)
        assertFalse(vm.uiState.value.isAnimatedMode)

        vm.setAnimationMode(TransformAnimationMode.ANIMATED)
        assertTrue(vm.uiState.value.isAnimatedMode)
        vm.selectEndpoint(TransformEndpoint.START)
        vm.setZoom(1.0f)
        vm.setAngle(0f)
        vm.selectEndpoint(TransformEndpoint.END)
        vm.setZoom(2.0f)
        vm.setAngle(90f)
        vm.nudge(20f, -10f)
        vm.selectEasing("Linear")
        vm.toggleMirrorEdges()

        var saved = false
        vm.createAndSave { saved = true }
        assertTrue(saved)

        val savedTransform = VideoStarRepository.getProjectById(project.id)!!.scenes.first().transform
        assertTrue(savedTransform.isAnimated)
        assertTrue(savedTransform.mirrorEdges)
        assertEquals("Linear", savedTransform.easing)

        val mid = savedTransform.evaluatedAt(0.5f)
        assertEquals(1.5f, mid.scale, 0.01f)
        assertEquals(45f, mid.rotation, 0.05f)
        assertEquals(10f, mid.offsetX, 0.05f)
        assertEquals(-5f, mid.offsetY, 0.05f)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `transition catalog and motion presets expose redesigned ios options`() {
        assertEquals(4, TransitionCategory.entries.size)
        val ids = TransitionCatalog.allTransitions.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("zoom_blur", "zoom_spin", "cube_left", "cube_right", "back_flip", "white_flash", "slide_left", "radial_wipe")))
        assertEquals(
            listOf("None", "Shake X", "Shake Y", "Pendulum", "Zoom Bounce", "Heartbeat", "Floating"),
            MotionPreset.entries.map { it.label }
        )
    }
}

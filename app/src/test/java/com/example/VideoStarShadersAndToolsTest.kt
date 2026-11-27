package com.example

import com.example.data.VideoStarRepository
import com.example.engine.effects.AlightMotionEffectRegistry
import com.example.engine.effects.shaders.ShaderUniformEvaluator
import com.example.engine.sam.SamCutoutSegmenter
import com.example.gl.ShaderLibrary
import com.example.model.AspectRatioMode
import com.example.model.BezierCurveSpec
import com.example.model.EffectCatalog
import com.example.model.EffectCategory
import com.example.model.EffectKeyframe
import com.example.model.EffectStudioTab
import com.example.model.KeyframeEasing
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.MediaClip
import com.example.model.MotionPreset
import com.example.model.ParameterKeyframe
import com.example.model.SamCutoutSpec
import com.example.model.SamTapPoint
import com.example.model.ShaderEffectPass
import com.example.model.TextLayerSpec
import com.example.model.TransitionCatalog
import com.example.model.TransitionCategory
import com.example.model.interpolateColorKeyframes
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
    fun `effect catalog provides 35 Video Star plus 110 AM shaders across 15 categories and matches ShaderLibrary`() {
        assertEquals(35, EffectCatalog.shaderEffects.size)
        assertEquals(110, AlightMotionEffectRegistry.effects.size)
        assertEquals(145, EffectCatalog.combinedShaderEffects.size)
        assertEquals(15, EffectCategory.entries.size)

        EffectCategory.entries.forEach { category ->
            val count = EffectCatalog.combinedShaderEffects.count { it.category == category }
            assertTrue("Expected effects in $category", count > 0)
        }

        EffectCatalog.combinedShaderEffects.forEach { effect ->
            assertTrue("ShaderLibrary missing GLSL body for ${effect.id}", ShaderLibrary.hasShader(effect.id))
            val glsl = ShaderLibrary.fragmentShaderFor(effect.id)
            assertTrue("Missing #version 300 es for ${effect.id}", glsl.contains("#version 300 es"))
            assertTrue("Missing outColor for ${effect.id}", glsl.contains("outColor"))
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
    fun `effects studio 12 tabs cover all 145 shaders cleanly`() {
        val project = VideoStarRepository.createProject("اختبار التبويبات", AspectRatioMode.PORTRAIT)
        val vm = EffectsStudioViewModel(project.id, listOf(0))

        assertEquals(12, EffectStudioTab.entries.size)
        vm.selectTab(EffectStudioTab.ALL)
        assertEquals(145, vm.uiState.value.displayedEffects.size)

        val nonAllTabs = EffectStudioTab.entries.filter { it != EffectStudioTab.ALL }
        var sum = 0
        nonAllTabs.forEach { tab ->
            vm.selectTab(tab)
            val items = vm.uiState.value.displayedEffects
            if (tab != EffectStudioTab.MY_EFFECTS) {
                assertTrue("Expected effects in $tab", items.isNotEmpty())
            }
            sum += items.size
        }
        assertEquals(145, sum)

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

    @Test
    fun `effects studio 3-level stack manages visibility duplicate reorder blend mode favorites and undo redo`() {
        val project = VideoStarRepository.createProject("استوديو الطبقات", AspectRatioMode.PORTRAIT)
        val vm = com.example.ui.screens.effects.EffectsStudioViewModel(
            projectId = project.id,
            sceneIndices = listOf(0),
            targetLayerId = "layer_2"
        )

        assertEquals("الطبقة 2", vm.uiState.value.layerDisplayTitle)
        assertEquals(com.example.ui.screens.effects.StudioPanelLevel.STACK, vm.uiState.value.panelLevel)

        vm.openEffectLibrary()
        assertEquals(com.example.ui.screens.effects.StudioPanelLevel.LIBRARY, vm.uiState.value.panelLevel)

        val rgb = EffectCatalog.findById("rgb_split")!!
        val blur = EffectCatalog.findById("gaussian_blur")!!
        vm.addEffectFromLibrary(rgb)
        assertEquals(com.example.ui.screens.effects.StudioPanelLevel.EDITOR, vm.uiState.value.panelLevel)
        assertEquals(1, vm.uiState.value.passes.size)
        assertTrue(vm.uiState.value.canUndo)

        vm.updatePassBlendMode(com.example.model.EffectBlendMode.ADD)
        vm.updatePassOpacity(0.8f)
        assertEquals(com.example.model.EffectBlendMode.ADD, vm.uiState.value.activePass?.blendMode)
        assertEquals(0.8f, vm.uiState.value.activePass?.passOpacity ?: 0f, 0.01f)

        vm.backToEffectStack()
        assertEquals(com.example.ui.screens.effects.StudioPanelLevel.STACK, vm.uiState.value.panelLevel)

        vm.addEffectFromLibrary(blur)
        assertEquals(2, vm.uiState.value.passes.size)

        // Move second pass to index 0
        vm.movePass(1, 0)
        assertEquals("gaussian_blur", vm.uiState.value.passes[0].effectId)
        assertEquals("rgb_split", vm.uiState.value.passes[1].effectId)

        // Eye toggle disables pass in preview resolvedEffectPasses() while keeping it in stack
        vm.togglePassEnabled(0)
        assertFalse(vm.uiState.value.passes[0].isEnabled)
        assertEquals(1, vm.uiState.value.previewScene.resolvedEffectPasses().size)

        // Duplicate pass increases stack to 3
        vm.duplicatePass(1)
        assertEquals(3, vm.uiState.value.passes.size)

        // Undo restores back to 2 passes
        vm.undo()
        assertEquals(2, vm.uiState.value.passes.size)
        assertTrue(vm.uiState.value.canRedo)

        vm.redo()
        assertEquals(3, vm.uiState.value.passes.size)

        // Favorites filter
        vm.toggleFavoritesFilter()
        assertTrue(vm.uiState.value.displayedEffects.size < 145)
        vm.toggleFavoritesFilter()
        assertEquals(145, vm.uiState.value.displayedEffects.size)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `per-parameter keyframe system stores time value easing animates shader uniforms and supports navigation`() {
        val project = VideoStarRepository.createProject("اختبار نقاط الحركة", AspectRatioMode.PORTRAIT)
        val vm = EffectsStudioViewModel(project.id, listOf(0), targetLayerId = "layer_1")
        val rgb = EffectCatalog.findById("rgb_split")!!

        vm.addEffectFromLibrary(rgb)

        // 1. Animate intensity with EASE_IN easing
        vm.seekToProgress(0.0f)
        vm.updateIntensity(0.0f)
        vm.togglePropertyKeyframe(com.example.model.PARAM_KEY_INTENSITY)
        vm.updatePropertyKeyframeEasing(
            com.example.model.PARAM_KEY_INTENSITY,
            com.example.model.KeyframeEasing.EASE_IN
        )
        vm.seekToProgress(1.0f)
        // Auto-keyframes at 1.0f because intensity already has a keyframe
        vm.updateIntensity(1.0f)

        // Ease-In at t = 0.5 should be well below linear 0.5
        val easedMidIntensity = vm.uiState.value.activePass!!.evaluateIntensityAt(0.5f)
        assertTrue("Expected Ease-In midpoint < 0.4, was $easedMidIntensity", easedMidIntensity < 0.4f)

        // 2. Animate shader param "distance" with STEP easing and verify ShaderLibrary uniform interpolation
        vm.seekToProgress(0.2f)
        vm.updatePassParam("distance", 0.2f)
        vm.togglePropertyKeyframe("distance")
        vm.updatePropertyKeyframeEasing("distance", com.example.model.KeyframeEasing.STEP)

        vm.seekToProgress(0.8f)
        vm.updatePassParam("distance", 0.9f) // auto-keyframed at 0.8

        val pass = vm.uiState.value.activePass!!
        val distanceKfs = pass.keyframesForProperty("distance")
        assertEquals(2, distanceKfs.size)
        assertEquals(com.example.model.KeyframeEasing.STEP, distanceKfs[0].easing)

        // At 0.5f, STEP holds first keyframe's value (0.2f); at 0.8f it steps to 0.9f
        assertEquals(0.2f, ShaderLibrary.extractUniformParams(pass, 0.5f)[0], 0.01f)
        assertEquals(0.9f, ShaderLibrary.extractUniformParams(pass, 0.8f)[0], 0.01f)

        // Switch first keyframe to LINEAR and check linear midpoint
        vm.seekToProgress(0.2f)
        vm.updatePropertyKeyframeEasing("distance", com.example.model.KeyframeEasing.LINEAR)
        val linearPass = vm.uiState.value.activePass!!
        assertEquals(0.55f, ShaderLibrary.extractUniformParams(linearPass, 0.5f)[0], 0.02f)

        // 3. Animate pass opacity with keyframe toggles + prev/next jump
        vm.seekToProgress(0.0f)
        vm.updatePassOpacity(0.1f)
        vm.togglePropertyKeyframe(com.example.model.PARAM_KEY_OPACITY)
        vm.seekToProgress(1.0f)
        vm.updatePassOpacity(0.9f)
        assertEquals(0.5f, vm.uiState.value.activePass!!.evaluateOpacityAt(0.5f), 0.02f)

        vm.jumpToPrevKeyframe(com.example.model.PARAM_KEY_OPACITY)
        assertEquals(0.0f, vm.uiState.value.previewProgress, 0.02f)
        vm.jumpToNextKeyframe(com.example.model.PARAM_KEY_OPACITY)
        assertEquals(1.0f, vm.uiState.value.previewProgress, 0.02f)

        // Save to repository and verify persistence
        var saved = false
        vm.createAndSave { saved = true }
        assertTrue(saved)
        val savedLayer = VideoStarRepository.getProjectById(project.id)!!.layers.first { it.id == "layer_1" }
        assertEquals(2, savedLayer.effectPasses.first().keyframesForProperty("distance").size)
        assertEquals(2, savedLayer.effectPasses.first().keyframesForProperty(com.example.model.PARAM_KEY_OPACITY).size)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `mini keyframe timeline exposes markers supports drag to retime long press delete and undo`() {
        val project = VideoStarRepository.createProject("مسار النقاط المصغر", AspectRatioMode.PORTRAIT)
        val vm = EffectsStudioViewModel(project.id, listOf(0), targetLayerId = "layer_1")
        val blur = EffectCatalog.findById("gaussian_blur")!!

        vm.addEffectFromLibrary(blur)
        vm.seekToProgress(0.2f)
        vm.updateIntensity(0.2f)
        vm.togglePropertyKeyframe(com.example.model.PARAM_KEY_INTENSITY)

        vm.seekToProgress(0.8f)
        vm.updateIntensity(0.9f)

        val initialMarkers = vm.uiState.value.activePassKeyframeMarkers
        assertEquals(2, initialMarkers.size)
        val firstId = initialMarkers.first().keyframeId

        // Drag first keyframe from 0.2f -> 0.5f
        vm.beginKeyframeDrag()
        vm.moveKeyframeTime(com.example.model.PARAM_KEY_INTENSITY, firstId, 0.5f)

        val afterDrag = vm.uiState.value.activePassKeyframeMarkers
        val moved = afterDrag.first { it.keyframeId == firstId }
        assertEquals(0.5f, moved.time, 0.01f)
        assertEquals(0.5f, vm.uiState.value.previewProgress, 0.01f)

        // Tap/select keyframe jumps and focuses
        vm.selectAndSeekKeyframe(com.example.model.PARAM_KEY_INTENSITY, afterDrag.last().time)
        assertEquals(0.8f, vm.uiState.value.previewProgress, 0.02f)

        // Long-press deletes keyframe and undo restores it
        vm.deleteKeyframe(com.example.model.PARAM_KEY_INTENSITY, firstId)
        assertEquals(1, vm.uiState.value.activePassKeyframeMarkers.size)

        vm.undo()
        assertEquals(2, vm.uiState.value.activePassKeyframeMarkers.size)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `effect stack drag reorder updates shader render pass chain in single undo step`() {
        val project = VideoStarRepository.createProject("اختبار ترتيب الشادر", AspectRatioMode.PORTRAIT)
        val vm = EffectsStudioViewModel(project.id, listOf(0), targetLayerId = "layer_1")

        val rgb = EffectCatalog.findById("rgb_split")!!
        val blur = EffectCatalog.findById("gaussian_blur")!!
        val vhs = EffectCatalog.findById("vhs_tape")!!

        vm.addEffectFromLibrary(rgb)
        vm.backToEffectStack()
        vm.addEffectFromLibrary(blur)
        vm.backToEffectStack()
        vm.addEffectFromLibrary(vhs)
        vm.backToEffectStack()

        // Initial render chain: [rgb_split, gaussian_blur, vhs_tape]; select middle pass (gaussian_blur at index 1)
        vm.selectPass(1)
        assertEquals(listOf("rgb_split", "gaussian_blur", "vhs_tape"), vm.uiState.value.passes.map { it.effectId })
        assertEquals(1, vm.uiState.value.selectedPassIndex)
        assertEquals(
            listOf("rgb_split", "gaussian_blur", "vhs_tape"),
            vm.uiState.value.previewScene.resolvedEffectPasses().map { it.effectId }
        )

        // Simulate continuous drag of index 0 (rgb_split) across index 1 and 2 to the end
        vm.beginPassReorderDrag()
        vm.movePass(0, 1, recordUndo = false)
        vm.movePass(1, 2, recordUndo = false)

        // Render chain & primary effect updated immediately; selectedPassIndex tracked gaussian_blur to index 0
        assertEquals(listOf("gaussian_blur", "vhs_tape", "rgb_split"), vm.uiState.value.passes.map { it.effectId })
        assertEquals(
            listOf("gaussian_blur", "vhs_tape", "rgb_split"),
            vm.uiState.value.previewScene.resolvedEffectPasses().map { it.effectId }
        )
        assertEquals("gaussian_blur", vm.uiState.value.previewScene.effect.effectId)
        assertEquals(0, vm.uiState.value.selectedPassIndex)

        // Persist and verify saved layer preserves new pass execution order
        var saved = false
        vm.createAndSave { saved = true }
        assertTrue(saved)
        val persistedIds = VideoStarRepository.getProjectById(project.id)!!
            .layers.first { it.id == "layer_1" }
            .effectPasses.map { it.effectId }
        assertEquals(listOf("gaussian_blur", "vhs_tape", "rgb_split"), persistedIds)

        // Single undo restores the pre-drag chain order in one step
        vm.undo()
        assertEquals(listOf("rgb_split", "gaussian_blur", "vhs_tape"), vm.uiState.value.passes.map { it.effectId })
        assertEquals("rgb_split", vm.uiState.value.previewScene.resolvedEffectPasses().first().effectId)

        // Redo re-applies the dragged order
        vm.redo()
        assertEquals(listOf("gaussian_blur", "vhs_tape", "rgb_split"), vm.uiState.value.passes.map { it.effectId })

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `history helper coalesces continuous parameter drags tracks layer order adjustments and discards noop gestures`() {
        var fakeClockMs = 1_000L
        val historyHelper = com.example.ui.screens.effects.EffectsStudioHistoryHelper(
            maxHistorySize = 40,
            coalesceWindowMs = 600L,
            clockMs = { fakeClockMs }
        )
        val project = VideoStarRepository.createProject("اختبار سجل التعديلات", AspectRatioMode.PORTRAIT)
        val vm = EffectsStudioViewModel(
            projectId = project.id,
            sceneIndices = listOf(0),
            targetLayerId = "layer_1",
            historyHelper = historyHelper
        )

        val rgb = EffectCatalog.findById("rgb_split")!!
        val blur = EffectCatalog.findById("gaussian_blur")!!
        vm.addEffectFromLibrary(rgb)
        vm.addEffectFromLibrary(blur)
        assertEquals(2, historyHelper.undoCount)

        // 1. Continuous slider drag on intensity collapses into 1 undo step
        val startIntensity = vm.uiState.value.activePass!!.intensity
        vm.updateIntensity(0.25f)
        fakeClockMs += 50L
        vm.updateIntensity(0.55f)
        fakeClockMs += 50L
        vm.updateIntensity(0.85f)
        vm.commitParameterEdit()
        assertEquals(3, historyHelper.undoCount)
        assertEquals(0.85f, vm.uiState.value.activePass!!.intensity, 0.01f)
        assertEquals("تعديل الشدة", vm.uiState.value.undoActionLabel)

        // Second separate drag on intensity after commit creates a new undo step even quickly
        fakeClockMs += 20L
        vm.updateIntensity(0.40f)
        vm.commitParameterEdit()
        assertEquals(4, historyHelper.undoCount)

        // Adjusting a shader param & opacity also tracks Undo/Redo cleanly
        vm.updatePassParam("blur_radius", 0.3f)
        vm.commitParameterEdit()
        vm.updatePassOpacity(0.6f)
        vm.commitParameterEdit()
        assertEquals(6, historyHelper.undoCount)

        // Undo opacity -> undo param -> undo 2nd intensity -> undo 1st intensity back to start
        vm.undo()
        assertEquals(1.0f, vm.uiState.value.activePass!!.passOpacity, 0.01f)
        vm.undo()
        vm.undo()
        assertEquals(0.85f, vm.uiState.value.activePass!!.intensity, 0.01f)
        vm.undo()
        assertEquals(startIntensity, vm.uiState.value.activePass!!.intensity, 0.01f)
        assertTrue(vm.uiState.value.canRedo)

        // Redo brings intensity right back
        vm.redo()
        assertEquals(0.85f, vm.uiState.value.activePass!!.intensity, 0.01f)

        // 2. No-op drag gesture (dragged and dropped back in original place) leaves no ghost undo entry
        val undoBeforeNoop = historyHelper.undoCount
        vm.beginPassReorderDrag()
        vm.movePass(0, 1, recordUndo = false)
        vm.movePass(1, 0, recordUndo = false)
        vm.endPassReorderDrag()
        assertEquals(undoBeforeNoop, historyHelper.undoCount)

        // 3. Layer order adjustment tracked and reversible via Undo/Redo and persisted on save
        val initialLayers = vm.uiState.value.layerOrderIds
        assertTrue(initialLayers.size >= 2)
        val firstLayerId = initialLayers[0]
        val secondLayerId = initialLayers[1]

        vm.beginLayerOrderAdjust()
        vm.moveLayerOrder(0, 1, recordUndo = false)
        vm.endLayerOrderAdjust()

        assertEquals(secondLayerId, vm.uiState.value.layerOrderIds[0])
        assertEquals(firstLayerId, vm.uiState.value.layerOrderIds[1])
        assertEquals("إعادة ترتيب الطبقات", vm.uiState.value.undoActionLabel)

        vm.undo()
        assertEquals(initialLayers, vm.uiState.value.layerOrderIds)

        vm.redo()
        assertEquals(secondLayerId, vm.uiState.value.layerOrderIds[0])

        var saved = false
        vm.createAndSave { saved = true }
        assertTrue(saved)
        val persistedLayerIds = VideoStarRepository.getProjectById(project.id)!!.layers.map { it.id }
        assertEquals(secondLayerId, persistedLayerIds[0])
        assertEquals(firstLayerId, persistedLayerIds[1])

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `adding shader effect preserves source video clip in EffectsStudio and target layer preview`() {
        val project = VideoStarRepository.createProject("اختبار بقاء الفيديو", AspectRatioMode.PORTRAIT)
        val sceneVideo = MediaClip.Video(
            id = "vid_scene_1",
            uriString = "file:///tmp/scene_video.mp4",
            startTrimMs = 0L,
            endTrimMs = 4_000L,
            durationMs = 4_000L
        )
        val layer2Video = MediaClip.Video(
            id = "vid_layer_2",
            uriString = "file:///tmp/layer2_video.mp4",
            startTrimMs = 0L,
            endTrimMs = 3_500L,
            durationMs = 3_500L
        )
        VideoStarRepository.addOrUpdateVideoClip(project.id, sceneVideo)
        val existingLayers = VideoStarRepository.getProjectById(project.id)!!.layers.toMutableList()
        val l2Idx = existingLayers.indexOfFirst { it.id == "layer_2" }
        existingLayers[l2Idx] = existingLayers[l2Idx].copy(mediaClip = layer2Video)
        VideoStarRepository.syncProjectLayers(project.id, existingLayers)

        val studioSceneVm = EffectsStudioViewModel(project.id, listOf(0))
        val rgb = EffectCatalog.findById("rgb_split")!!
        studioSceneVm.addEffectFromLibrary(rgb)
        assertEquals("file:///tmp/scene_video.mp4", studioSceneVm.uiState.value.previewScene.clip?.uriString)
        assertTrue(studioSceneVm.uiState.value.previewScene.resolvedEffectPasses().any { it.effectId == "rgb_split" })

        val studioLayer2Vm = EffectsStudioViewModel(project.id, listOf(0), targetLayerId = "layer_2")
        studioLayer2Vm.addEffectFromLibrary(rgb)
        assertEquals("file:///tmp/layer2_video.mp4", studioLayer2Vm.uiState.value.previewScene.clip?.uriString)
        assertTrue(studioLayer2Vm.uiState.value.previewScene.resolvedEffectPasses().any { it.effectId == "rgb_split" })

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `alight motion 9 categories contain exact 110 unique effects with valid shaders`() {
        val expectedCounts = mapOf(
            EffectCategory.AM_MOVE_TRANSFORM to 12,
            EffectCategory.AM_DISTORTION_WARP to 18,
            EffectCategory.AM_MATTE_MASK_KEY to 11,
            EffectCategory.AM_OPACITY_VISIBILITY to 7,
            EffectCategory.AM_PROCEDURAL to 23,
            EffectCategory.AM_REPEAT to 7,
            EffectCategory.AM_THREE_D to 18,
            EffectCategory.AM_TEXT to 6,
            EffectCategory.AM_OTHER to 8
        )
        assertEquals(110, expectedCounts.values.sum())
        assertEquals(110, AlightMotionEffectRegistry.effects.map { it.id }.toSet().size)

        expectedCounts.forEach { (category, expected) ->
            val actual = AlightMotionEffectRegistry.effects.filter { it.category == category }
            assertEquals("Category count mismatch for $category", expected, actual.size)
            actual.forEach { item ->
                assertTrue("Missing GLSL shader for ${item.id}", ShaderLibrary.hasShader(item.id))
                val shader = ShaderLibrary.fragmentShaderFor(item.id)
                assertTrue("Shader missing uParams/main for ${item.id}", shader.contains("void main()"))
            }
        }
    }

    @Test
    fun `shader uniform evaluator packs typed parameters quantizes time and formats dynamic text`() {
        // 1. Color + Point2D + Float packing check (am_inner_pinch_bulge + am_adv_chroma_key)
        val pinchCatalog = EffectCatalog.findById("am_inner_pinch_bulge")!!
        val pinchPass = ShaderEffectPass(
            id = "p_pinch",
            effectId = pinchCatalog.id,
            effectName = pinchCatalog.name,
            category = pinchCatalog.category,
            intensity = 0.8f,
            params = mapOf("center_x" to 0.25f, "center_y" to 0.75f, "strength" to 0.6f, "radius" to 0.4f, "feather" to 0.2f)
        )
        val pinchUniforms = ShaderUniformEvaluator.evaluate(pinchPass, 0.5f, 4.0f)
        assertEquals(0.25f, pinchUniforms.params[0], 0.001f)
        assertEquals(0.75f, pinchUniforms.params[1], 0.001f)
        assertEquals(0.6f, pinchUniforms.params[2], 0.001f)
        assertEquals(2.0f, pinchUniforms.effectiveTimeSec, 0.001f)

        val chromaCatalog = EffectCatalog.findById("am_adv_chroma_key")!!
        val chromaPass = ShaderEffectPass(
            id = "p_chroma",
            effectId = chromaCatalog.id,
            effectName = chromaCatalog.name,
            category = chromaCatalog.category,
            colorParams = mapOf("key_color" to 0xFF00FF80L)
        )
        val chromaUniforms = ShaderUniformEvaluator.evaluate(chromaPass, 0.5f, 2.0f)
        assertEquals(0f, chromaUniforms.colors[0], 0.01f)
        assertEquals(1f, chromaUniforms.colors[1], 0.01f)
        assertEquals(128f / 255f, chromaUniforms.colors[2], 0.02f)

        // 2. Time Quantization
        val quantPass = ShaderEffectPass(
            id = "p_q",
            effectId = "am_time_quantization",
            effectName = "Time Quantization",
            category = EffectCategory.AM_OTHER,
            params = mapOf("target_fps" to 4f, "phase_offset" to 0f)
        )
        val (qProg, qSec) = ShaderUniformEvaluator.computeQuantizedProgress(quantPass, 0.37f, 2.0f)
        // 2.0s * 4fps = 8 steps; floor(0.37 * 8) / 8 = 2 / 8 = 0.25
        assertEquals(0.25f, qProg, 0.01f)
        assertEquals(0.5f, qSec, 0.02f)

        // 3. Dynamic Text formatting (Counter, Progress, Randomizer, Timecode)
        val countPass = ShaderEffectPass(
            id = "p_cnt",
            effectId = "am_text_count",
            effectName = "Count Up/Down",
            category = EffectCategory.AM_TEXT,
            params = mapOf("start_val" to 0f, "end_val" to 100f, "decimals" to 0f, "comma_sep" to 0f),
            textParams = mapOf("prefix" to "$", "suffix" to "pts")
        )
        val counted = ShaderUniformEvaluator.formatTextForEffects("Hello", listOf(countPass), 0.5f, 4f)
        assertEquals("$50pts", counted)

        val tcPass = ShaderEffectPass(
            id = "p_tc",
            effectId = "am_text_timecode",
            effectName = "Timecode",
            category = EffectCategory.AM_TEXT,
            params = mapOf("tc_format" to 1f, "offset_sec" to 0f, "frame_rate" to 30f)
        )
        val formattedTc = ShaderUniformEvaluator.formatTextForEffects("Hello", listOf(tcPass), 0.5f, 10f)
        assertEquals("00:05.00", formattedTc)
    }

    @Test
    fun `custom cubic bezier curve color keyframes and 2D studio inspector updates work seamlessly`() {
        // Direct curve + color interpolation checks
        val kfs = listOf(
            ParameterKeyframe(
                id = "c1",
                time = 0f,
                value = 0f,
                easing = KeyframeEasing.CUBIC_BEZIER,
                customBezier = BezierCurveSpec(0.1f, 0.9f, 0.2f, 1.0f),
                colorArgb = 0xFFFF0000L
            ),
            ParameterKeyframe(
                id = "c2",
                time = 1f,
                value = 1f,
                easing = KeyframeEasing.LINEAR,
                colorArgb = 0xFF0000FFL
            )
        )
        val midColor = interpolateColorKeyframes(kfs, 0.5f, 0xFFFFFFFFL).toInt()
        // Custom steep Ease-Out Bezier jumps red down & blue high by t=0.5
        assertTrue(android.graphics.Color.blue(midColor) > android.graphics.Color.red(midColor))

        // Studio VM Color + Point2D + Text + CustomBezier editing
        val project = VideoStarRepository.createProject("AM Inspector Test", AspectRatioMode.SQUARE)
        val vm = EffectsStudioViewModel(project.id, listOf(0), targetLayerId = "layer_1")
        val swipe = EffectCatalog.findById("am_radial_wipe")!!
        vm.addEffectFromLibrary(swipe)

        val pinch = EffectCatalog.findById("am_inner_pinch_bulge")!!
        vm.selectEffect(pinch)
        val cxSpec = pinch.params.first { it.key == "center_x" }
        val cySpec = pinch.params.first { it.key == "center_y" }
        vm.updatePassPoint2D("center_x", "center_y", 0.35f, 0.65f)
        assertEquals(0.35f, vm.uiState.value.evaluatedParamValue(cxSpec), 0.01f)
        assertEquals(0.65f, vm.uiState.value.evaluatedParamValue(cySpec), 0.01f)

        val matteFringe = EffectCatalog.findById("am_matte_fringe")!!
        val colorSpec = matteFringe.params.first { it.key == "fringe_color" }
        vm.selectEffect(matteFringe)
        vm.seekToProgress(0f)
        vm.updatePassColor("fringe_color", 0xFFFF0000L)
        vm.togglePropertyKeyframe("fringe_color")
        vm.updatePropertyCustomBezier("fringe_color", BezierCurveSpec(0.25f, 0.8f, 0.25f, 1.0f))
        vm.seekToProgress(1f)
        vm.updatePassColor("fringe_color", 0xFF00FF00L)
        vm.seekToProgress(0.5f)

        val evaluatedMid = vm.uiState.value.evaluatedColorValue(colorSpec).toInt()
        assertTrue(android.graphics.Color.green(evaluatedMid) > android.graphics.Color.red(evaluatedMid))

        val counter = EffectCatalog.findById("am_text_count")!!
        val prefixSpec = counter.params.first { it.key == "prefix" }
        vm.selectEffect(counter)
        vm.updatePassText("prefix", "SCORE:")
        assertEquals("SCORE:", vm.uiState.value.evaluatedTextValue(prefixSpec))

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `sam cutout segmenter isolates center foreground subject from uniform background and respects tap points and invert`() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h)
        // Bright saturated magenta subject in center [10..22, 10..22], flat dark background elsewhere
        for (y in 0 until h) {
            for (x in 0 until w) {
                val isSubject = x in 10..21 && y in 10..21
                pixels[y * w + x] = if (isSubject) {
                    android.graphics.Color.rgb(250, 40, 180)
                } else {
                    android.graphics.Color.rgb(18, 22, 28)
                }
            }
        }

        val autoMask = SamCutoutSegmenter.computeRefinedConfidenceMask(
            width = w,
            height = h,
            pixels = pixels,
            spec = SamCutoutSpec(enabled = true, isolateAllSubjects = true, feather = 0.0f, choke = 0.0f, invert = false)
        )
        val centerAlpha = autoMask[16 * w + 16]
        val cornerAlpha = autoMask[1 * w + 1]
        assertTrue("Expected foreground center alpha ($centerAlpha) >> corner ($cornerAlpha)", centerAlpha > 0.7f && cornerAlpha < 0.25f)

        // Invert flips foreground vs background cleanly
        val invertedMask = SamCutoutSegmenter.computeRefinedConfidenceMask(
            width = w,
            height = h,
            pixels = pixels,
            spec = SamCutoutSpec(enabled = true, isolateAllSubjects = true, feather = 0.0f, choke = 0.0f, invert = true)
        )
        assertTrue(invertedMask[1 * w + 1] > 0.7f)
        assertTrue(invertedMask[16 * w + 16] < 0.3f)

        // Exclude tap near center removes the tapped region confidence
        val tapExcludedMask = SamCutoutSegmenter.computeRefinedConfidenceMask(
            width = w,
            height = h,
            pixels = pixels,
            spec = SamCutoutSpec(
                enabled = true,
                isolateAllSubjects = false,
                tapPoints = listOf(SamTapPoint("p1", 0.5f, 0.5f, include = false)),
                feather = 0f
            )
        )
        assertTrue(tapExcludedMask[16 * w + 16] < centerAlpha)
    }
}







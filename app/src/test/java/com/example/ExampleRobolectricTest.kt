package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.MediaClip
import com.example.ui.screens.adjust.AdjustPhotoViewModel
import com.example.ui.screens.adjust.AdjustVideoViewModel
import com.example.ui.screens.home.HomeViewModel
import androidx.compose.ui.graphics.Color
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
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StarStudio", appName)
    }

    @Test
    fun `repository starts empty without sample projects or dummy clips`() {
        assertTrue(VideoStarRepository.projects.value.isEmpty())
    }

    @Test
    fun `repository creates updates and deletes projects in memory`() {
        val initialCount = VideoStarRepository.projects.value.size
        val created = VideoStarRepository.createProject(
            title = "مشروع اختبار",
            aspectRatio = AspectRatioMode.PORTRAIT,
            frameRate = 60,
            resolution = "4k"
        )
        assertEquals(initialCount + 1, VideoStarRepository.projects.value.size)
        assertTrue(created.clips.isEmpty())
        assertTrue(created.layers.all { it.mediaClip == null })
        assertEquals(60, created.frameRate)
        assertEquals("4k", created.resolution)

        VideoStarRepository.updateProject(created.id, "مشروع معدل", AspectRatioMode.LANDSCAPE)
        val updated = VideoStarRepository.getProjectById(created.id)
        assertNotNull(updated)
        assertEquals("مشروع معدل", updated?.title)
        assertEquals(AspectRatioMode.LANDSCAPE, updated?.aspectRatio)

        VideoStarRepository.deleteProject(created.id)
        assertTrue(VideoStarRepository.getProjectById(created.id) == null)
    }

    @Test
    fun `home viewmodel launches without create modal, dismisses without creating, and persists 4_5 and 4K settings`() {
        val initialCount = VideoStarRepository.projects.value.size
        val viewModel = HomeViewModel()
        assertEquals(null, viewModel.uiState.value.activeDialog)

        viewModel.openCreateProjectDialog()
        assertEquals(com.example.ui.screens.home.HomeDialogState.CreateProject, viewModel.uiState.value.activeDialog)
        assertEquals(30, viewModel.uiState.value.newProjectFrameRate)
        assertEquals(AspectRatioMode.LANDSCAPE, viewModel.uiState.value.newProjectAspectRatio)
        assertEquals("1080p", viewModel.uiState.value.newProjectResolution)
        assertEquals(com.example.model.CompressionQuality.BALANCED, viewModel.uiState.value.newProjectCompression)

        // Closing or tapping outside dismisses cleanly without creating a project
        viewModel.dismissDialog()
        assertEquals(null, viewModel.uiState.value.activeDialog)
        assertEquals(initialCount, VideoStarRepository.projects.value.size)

        viewModel.openCreateProjectDialog()
        viewModel.updateNewProjectFrameRate(60)
        viewModel.updateNewProjectAspectRatio(AspectRatioMode.PORTRAIT_4_5)
        viewModel.updateNewProjectResolution("4K")
        viewModel.updateNewProjectCompression(com.example.model.CompressionQuality.HIGH)

        var createdId = ""
        viewModel.createProjectFromDraft { createdId = it }

        assertEquals(null, viewModel.uiState.value.activeDialog)
        val created = VideoStarRepository.getProjectById(createdId)
        assertNotNull(created)
        assertEquals(60, created?.frameRate)
        assertEquals(AspectRatioMode.PORTRAIT_4_5, created?.aspectRatio)
        assertEquals("4K", created?.resolution)
        assertEquals(com.example.model.CompressionQuality.HIGH, created?.compressionQuality)

        if (createdId.isNotEmpty()) {
            VideoStarRepository.deleteProject(createdId)
        }
    }

    @Test
    fun `workspace viewmodel steps exact single frames at project fps and defaults duration to 10_02s`() {
        val project = VideoStarRepository.createProject(
            title = "اختبار الإطارات",
            aspectRatio = AspectRatioMode.LANDSCAPE,
            frameRate = 25,
            resolution = "1080p"
        )
        val vm = com.example.ui.screens.workspace.WorkspaceViewModel(project.id)
        assertEquals(10_020L, vm.uiState.value.totalDurationMs)
        assertEquals(0L, vm.uiState.value.currentPositionMs)

        // 1 frame at 25 fps = 40 ms
        vm.stepForward()
        assertEquals(40L, vm.uiState.value.currentPositionMs)
        vm.stepBackward()
        assertEquals(0L, vm.uiState.value.currentPositionMs)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `adjust video viewmodel trims duration and commits real MediaClip Video to project`() {
        val project = VideoStarRepository.createProject("اختبار القص", AspectRatioMode.SQUARE)
        val testUri = "file:///tmp/user_video.mp4"
        val vm = AdjustVideoViewModel(project.id, testUri, 10_270L, 30)

        assertEquals(10_270L, vm.uiState.value.trimmedDurationMs)
        assertTrue(vm.uiState.value.isGridVisible)

        vm.toggleGrid()
        assertFalse(vm.uiState.value.isGridVisible)

        vm.seekProgress(0.2f)
        vm.setStartToCurrent()
        vm.seekProgress(0.7f)
        vm.setEndToCurrent()
        vm.selectBackgroundColor(Color.White)
        vm.onTransformGesture(12f, -8f, 1.25f, 15f)

        var completed = false
        vm.commitMakeVideo { completed = true }
        assertTrue(completed)

        val updated = VideoStarRepository.getProjectById(project.id)
        assertNotNull(updated?.trimAdjustment)
        assertEquals(1.25f, updated?.trimAdjustment?.scale ?: 0f, 0.01f)
        assertEquals(vm.uiState.value.trimmedDurationMs, updated?.durationMs)
        val videoClip = updated?.primaryClip as? MediaClip.Video
        assertNotNull(videoClip)
        assertEquals(testUri, videoClip?.uriString)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `adjust photo viewmodel configures duration flips and commits MediaClip Photo to timeline and layer`() {
        val project = VideoStarRepository.createProject("اختبار الصور", AspectRatioMode.PORTRAIT)
        val photoUri = "file:///tmp/user_photo.jpg"
        val timelineVm = AdjustPhotoViewModel(project.id, photoUri)

        timelineVm.setDurationSeconds(5.0f)
        timelineVm.toggleFlipHorizontal()
        timelineVm.onTransformGesture(10f, 15f, 1.4f, 12f)
        timelineVm.selectBackgroundColor(Color.Black)

        var timelineDone = false
        timelineVm.commitMakePhoto { timelineDone = true }
        assertTrue(timelineDone)

        val afterTimeline = VideoStarRepository.getProjectById(project.id)
        val photoClip = afterTimeline?.primaryClip as? MediaClip.Photo
        assertNotNull(photoClip)
        assertEquals(photoUri, photoClip?.uriString)
        assertEquals(5_000L, photoClip?.durationMs)
        assertTrue(photoClip?.isFlippedHorizontal == true)

        val layerPhotoUri = "file:///tmp/layer_photo.png"
        val layerVm = AdjustPhotoViewModel(project.id, layerPhotoUri, targetLayerIndex = 1)
        layerVm.setDurationSeconds(2.5f)
        var layerDone = false
        layerVm.commitMakePhoto { layerDone = true }
        assertTrue(layerDone)

        val afterLayer = VideoStarRepository.getProjectById(project.id)
        val layer2Clip = afterLayer?.layers?.getOrNull(1)?.mediaClip as? MediaClip.Photo
        assertNotNull(layer2Clip)
        assertEquals(layerPhotoUri, layer2Clip?.uriString)
        assertEquals(2_500L, layer2Clip?.durationMs)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `workspace split scene creates Scene 1 and Scene 2 and displays thumbnail strip`() {
        val project = VideoStarRepository.createProject("اختبار التقسيم", AspectRatioMode.SQUARE)
        val vm = com.example.ui.screens.workspace.WorkspaceViewModel(project.id)

        vm.seekToProgress(0.4f)
        vm.enterSplitMode()
        assertTrue(vm.uiState.value.isSplitMode)

        vm.confirmSplitScene()
        assertFalse(vm.uiState.value.isSplitMode)
        assertTrue(vm.uiState.value.showSceneStrip)

        val updated = VideoStarRepository.getProjectById(project.id)
        assertNotNull(updated)
        assertEquals(2, updated?.scenes?.size)
        assertEquals("Scene 1", updated?.scenes?.get(0)?.title)
        assertEquals("Scene 2", updated?.scenes?.get(1)?.title)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `layers and multilayer viewmodels support slot tap detection color assignment clearing and 3d toggle`() {
        val project = VideoStarRepository.createProject("اختبار الطبقات", AspectRatioMode.PORTRAIT)
        val vm = com.example.ui.screens.layers.LayersViewModel(project.id)

        assertEquals(0, vm.uiState.value.selectedLayerIndex)
        assertEquals(5, vm.uiState.value.layerSlots.size)
        assertTrue(vm.uiState.value.layerSlots.all { it.mediaType == com.example.model.LayerMediaType.NONE })

        // Tapping Layer 1 selects it cleanly (never deselects to null) and signals empty slot -> Photo Picker
        val layer1WasEmpty = vm.onLayerSlotClicked(0)
        assertTrue(layer1WasEmpty)
        assertEquals(0, vm.uiState.value.selectedLayerIndex)

        // Assigning color sets COLOR LayerSlot and marks slot non-empty
        vm.assignColorToLayer(1, Color(0xFFD91E18))
        assertEquals(1, vm.uiState.value.selectedLayerIndex)
        assertEquals(com.example.model.LayerMediaType.COLOR, vm.uiState.value.layerSlots[1].mediaType)
        assertFalse(vm.onLayerSlotClicked(1))

        // Clearing resets slot back to NONE
        vm.clearLayerSlot(1)
        assertEquals(com.example.model.LayerMediaType.NONE, vm.uiState.value.layerSlots[1].mediaType)
        assertTrue(vm.onLayerSlotClicked(1))

        vm.selectLayer(2)
        assertEquals(2, vm.uiState.value.selectedLayerIndex)
        vm.toggle3D()
        assertTrue(vm.uiState.value.activeLayer?.is3DEnabled == true)

        // Verify MultiLayerViewModel parity (Dark theme)
        val mlVm = com.example.ui.screens.multilayer.MultiLayerViewModel(project.id)
        assertEquals(AspectRatioMode.PORTRAIT.ratio, mlVm.uiState.value.projectAspectRatio, 0.01f)
        assertTrue(mlVm.onLayerSlotClicked(0))
        mlVm.assignColorToLayer(3, Color(0xFFFF2A85))
        assertEquals(com.example.model.LayerMediaType.COLOR, mlVm.uiState.value.layerSlots[3].mediaType)
        assertFalse(mlVm.onLayerSlotClicked(3))
        mlVm.clearLayerSlot(3)
        assertEquals(com.example.model.LayerMediaType.NONE, mlVm.uiState.value.layerSlots[3].mediaType)

        // Stage gesture hint dismissal, addNewLayer, and transform reset on both ViewModels
        assertFalse(vm.uiState.value.hasTouchedStage)
        vm.onLayerTransformGesture(24f, -12f, 1.5f, 15f)
        assertTrue(vm.uiState.value.hasTouchedStage)
        vm.resetActiveLayerTransform()
        assertEquals(0f, vm.uiState.value.activeLayer?.offsetX ?: -1f, 0.01f)
        assertEquals(1f, vm.uiState.value.activeLayer?.scale ?: 0f, 0.01f)
        vm.addNewLayer()
        assertEquals(6, vm.uiState.value.layers.size)
        assertEquals(5, vm.uiState.value.selectedLayerIndex)

        assertFalse(mlVm.uiState.value.hasTouchedStage)
        mlVm.markStageTouched()
        assertTrue(mlVm.uiState.value.hasTouchedStage)
        mlVm.onLayerTransformGesture(15f, 20f, 1.3f, 10f)
        mlVm.resetActiveLayerTransform()
        assertEquals(0f, mlVm.uiState.value.activeLayer?.offsetX ?: -1f, 0.01f)
        assertEquals(1f, mlVm.uiState.value.activeLayer?.scale ?: 0f, 0.01f)
        mlVm.addNewLayer()
        assertEquals(6, mlVm.uiState.value.layers.size)
        assertEquals(5, mlVm.uiState.value.selectedLayerIndex)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `keyframe viewmodel smoothly interpolates across keyframes and supports jog dial and size reset`() {
        val project = VideoStarRepository.createProject("اختبار Keyframe", AspectRatioMode.PORTRAIT)
        val vm = com.example.ui.screens.keyframe.KeyframeViewModel(project.id, "layer_1")

        // Clear to deterministic 2 keyframes: 0.0 -> (scale=1f, rot=0f), 1.0 -> (scale=2f, rot=90f)
        vm.scrubTo(0f)
        vm.clearAllKeyframesExceptCurrent()
        vm.updateScale(1f)
        vm.resetRotationToZero()

        vm.scrubTo(1f)
        vm.addOrUpdateKeyframeAtCurrentTime()
        vm.updateScale(2f)
        vm.jogRotate(90f)

        // Midpoint interpolates smoothly along default Smooth Cubic Bezier curve (~0.80 progress at x=0.5)
        vm.scrubTo(0.5f)
        assertTrue(vm.uiState.value.displayScale > 1.5f && vm.uiState.value.displayScale < 2.0f)
        assertTrue(vm.uiState.value.displayRotation > 45f && vm.uiState.value.displayRotation < 90f)

        vm.resetScaleTo100()
        assertEquals(1f, vm.uiState.value.displayScale, 0.01f)

        var saved = false
        vm.saveAndReturn { saved = true }
        assertTrue(saved)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `graph viewmodel updates cubic bezier handles inverts copies pastes and eases keyframe playback`() {
        val project = VideoStarRepository.createProject("اختبار المنحنيات", AspectRatioMode.SQUARE)
        val kfVm = com.example.ui.screens.keyframe.KeyframeViewModel(project.id, "layer_1")
        kfVm.scrubTo(0f)
        kfVm.clearAllKeyframesExceptCurrent()
        kfVm.updateScale(1f)
        kfVm.scrubTo(1f)
        kfVm.addOrUpdateKeyframeAtCurrentTime()
        kfVm.updateScale(2f)
        var saved = false
        kfVm.saveAndReturn { saved = true }
        assertTrue(saved)

        val graphVm = com.example.ui.screens.graphs.GraphViewModel(project.id, "layer_1", 0)
        graphVm.updateP1(0.85f, 0.0f)
        graphVm.updateP2(0.85f, 0.1f)
        graphVm.copyCurve()
        assertTrue(graphVm.uiState.value.hasCopiedCurve)

        graphVm.invertCurve()
        graphVm.pasteCurve()
        assertEquals(0.85f, graphVm.uiState.value.curve.p1x, 0.01f)

        var applied = false
        graphVm.applyAndReturn { applied = true }
        assertTrue(applied)

        // Refresh KeyframeViewModel and verify midpoint eased scale is slower (< 1.5x) due to Ease-In curve
        kfVm.refreshFromRepository()
        kfVm.scrubTo(0.5f)
        assertTrue(kfVm.uiState.value.displayScale < 1.45f)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `velocity viewmodel supports presets smooth toggle point dragging and repository save`() {
        val project = VideoStarRepository.createProject("اختبار السرعة", AspectRatioMode.PORTRAIT)
        val vm = com.example.ui.screens.velocity.VelocityViewModel(project.id)

        vm.applyPreset(com.example.ui.screens.velocity.VelocityPreset.RAMP_UP)
        vm.scrubTo(0f)
        assertEquals(0.35f, vm.uiState.value.instantSpeed, 0.05f)
        vm.scrubTo(1f)
        assertEquals(3.20f, vm.uiState.value.instantSpeed, 0.05f)

        vm.scrubTo(0.35f)
        vm.addPointAtCurrentTime()
        assertEquals(4, vm.uiState.value.points.size)

        vm.toggleSmooth()
        assertFalse(vm.uiState.value.isSmooth)

        var created = false
        vm.createAndSave { created = true }
        assertTrue(created)

        val updated = VideoStarRepository.getProjectById(project.id)
        assertEquals(4, updated?.velocityPoints?.size)
        assertFalse(updated?.isVelocitySmooth ?: true)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `transform viewmodel applies flips 90 rotation zoom and resets then saves to project`() {
        val project = VideoStarRepository.createProject("اختبار التحويل", AspectRatioMode.SQUARE)
        val vm = com.example.ui.screens.transform.TransformViewModel(project.id)

        vm.toggleFlipHorizontal()
        vm.toggleFlipVertical()
        vm.rotateBy90()
        vm.setZoom(1.75f)
        vm.nudge(12f, -18f)

        assertTrue(vm.uiState.value.isFlippedHorizontal)
        assertTrue(vm.uiState.value.isFlippedVertical)
        assertEquals(90f, vm.uiState.value.rotation, 0.1f)
        assertEquals(1.75f, vm.uiState.value.scale, 0.01f)

        var created = false
        vm.createAndSave { created = true }
        assertTrue(created)

        val updated = VideoStarRepository.getProjectById(project.id)
        assertTrue(updated?.trimAdjustment?.isFlippedHorizontal == true)
        assertTrue(updated?.trimAdjustment?.isFlippedVertical == true)
        assertEquals(90f, updated?.trimAdjustment?.rotation ?: 0f, 0.1f)
        assertEquals(1.75f, updated?.trimAdjustment?.scale ?: 0f, 0.01f)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `workspace viewmodel supports copy paste undo redo and fixed flexible clip mode dialog on last scene`() {
        val project = VideoStarRepository.createProject("اختبار الحافظة والوضع", AspectRatioMode.PORTRAIT)
        val clip = MediaClip.Video(
            id = "clip_stage2",
            uriString = "file:///tmp/stage2.mp4",
            startTrimMs = 0L,
            endTrimMs = 6_000L,
            durationMs = 6_000L,
            sourceFps = 30
        )
        VideoStarRepository.addOrUpdateVideoClip(project.id, clip)

        val vm = com.example.ui.screens.workspace.WorkspaceViewModel(project.id)
        assertEquals(1, VideoStarRepository.getProjectById(project.id)?.scenes?.size)

        // Copy & Paste + Undo & Redo
        vm.copyActiveScene()
        assertNotNull(vm.uiState.value.copiedScene)
        vm.pasteCopiedScene()
        assertEquals(2, VideoStarRepository.getProjectById(project.id)?.scenes?.size)
        assertTrue(vm.uiState.value.canUndo)

        vm.undoTimelineEdit()
        assertEquals(1, VideoStarRepository.getProjectById(project.id)?.scenes?.size)
        assertTrue(vm.uiState.value.canRedo)

        vm.redoTimelineEdit()
        assertEquals(2, VideoStarRepository.getProjectById(project.id)?.scenes?.size)

        // Select last scene -> Trim opens Fixed/Flexible dialog
        vm.selectScene(1)
        vm.openTrimWithClipModeCheck()
        val trimDialog = vm.uiState.value.activeDialog as? com.example.ui.screens.workspace.WorkspaceDialogState.ClipModeFixedFlexible
        assertNotNull(trimDialog)
        assertEquals(com.example.ui.screens.workspace.ClipModeTrigger.TRIM, trimDialog?.trigger)

        vm.applyFixedClipMode()
        assertTrue(vm.uiState.value.isTrimMode)
        assertTrue(vm.uiState.value.trimIsFixedDuration)

        // Timing on last scene opens Fixed/Flexible dialog as well
        vm.cancelTrimMode()
        vm.selectScene(1)
        var navigatedSceneIdx = -1
        vm.openTimingWithClipModeCheck { navigatedSceneIdx = it }
        val timingDialog = vm.uiState.value.activeDialog as? com.example.ui.screens.workspace.WorkspaceDialogState.ClipModeFixedFlexible
        assertNotNull(timingDialog)
        assertEquals(com.example.ui.screens.workspace.ClipModeTrigger.TIMING, timingDialog?.trigger)

        vm.applyFlexibleClipMode()
        assertEquals(1, navigatedSceneIdx)

        VideoStarRepository.deleteProject(project.id)
    }

    @Test
    fun `adjust video viewmodel supports 1px nudges rotate90 crop aspect cycle and color grading propagation`() {
        val project = VideoStarRepository.createProject("اختبار الألوان والقص", AspectRatioMode.LANDSCAPE)
        val vm = AdjustVideoViewModel(project.id, "file:///tmp/color_test.mp4", 4_000L, 30)

        vm.nudgeUp()
        vm.nudgeRight()
        assertEquals(-1f, vm.uiState.value.offsetY, 0.01f)
        assertEquals(1f, vm.uiState.value.offsetX, 0.01f)
        vm.nudgeDown()
        vm.nudgeLeft()
        assertEquals(0f, vm.uiState.value.offsetY, 0.01f)
        assertEquals(0f, vm.uiState.value.offsetX, 0.01f)

        vm.rotateBy90()
        assertEquals(90f, vm.uiState.value.rotation, 0.01f)

        val initialCrop = vm.uiState.value.effectiveAspectRatio
        vm.cycleCropAspectRatio()
        assertTrue(vm.uiState.value.effectiveAspectRatio != initialCrop)

        vm.updateBrightness(0.25f)
        vm.updateContrast(1.3f)
        vm.updateSaturation(1.6f)
        vm.updateHue(45f)

        var done = false
        vm.commitMakeVideo { done = true }
        assertTrue(done)

        val savedScene = VideoStarRepository.getProjectById(project.id)?.scenes?.firstOrNull()
        assertNotNull(savedScene)
        assertEquals(0.25f, savedScene?.colorGrading?.brightness ?: 0f, 0.01f)
        assertEquals(1.3f, savedScene?.colorGrading?.contrast ?: 0f, 0.01f)
        assertEquals(1.6f, savedScene?.colorGrading?.saturation ?: 0f, 0.01f)
        assertEquals(45f, savedScene?.colorGrading?.hue ?: 0f, 0.01f)

        VideoStarRepository.deleteProject(project.id)
    }
}



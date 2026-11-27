package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.data.MediaImportHelper
import com.example.data.VideoStarRepository
import com.example.model.AeLayerKind
import com.example.model.AspectRatioMode
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogScope
import com.example.model.EffectCategory
import com.example.model.EffectStudioTab
import com.example.model.MediaClip
import com.example.model.ParameterKeyframe
import com.example.model.ShaderEffectPass
import com.example.model.StudioLayoutMode
import com.example.model.TimelineScene
import com.example.ui.screens.workspace.AeBrowserCategory
import com.example.ui.screens.workspace.AeEffectsBrowserCatalog
import com.example.ui.screens.workspace.WorkspaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShaderCatalogAndAeStackUnitTest {

    @Test
    fun `effect catalog exposes 145 total shaders and separates 35 VS vs 110 AE scopes`() {
        val all = EffectCatalog.combinedShaderEffects
        assertEquals(145, all.size)

        val vsOnly = all.filter { !it.category.isAlightMotion }
        val aeOnly = all.filter { it.category.isAlightMotion }
        assertEquals(35, vsOnly.size)
        assertEquals(110, aeOnly.size)

        val vsTabs = EffectStudioTab.tabsForScope(EffectCatalogScope.VIDEO_STAR_ONLY)
        val aeTabs = EffectStudioTab.tabsForScope(EffectCatalogScope.AFTER_EFFECTS_ONLY)
        val allTabs = EffectStudioTab.tabsForScope(EffectCatalogScope.ALL)

        assertTrue(EffectStudioTab.VIDEO_STAR in vsTabs)
        assertFalse(EffectStudioTab.VIDEO_STAR in aeTabs)
        assertTrue(EffectStudioTab.ALL in allTabs)
        assertTrue(aeTabs.size >= 9)
    }

    @Test
    fun `ae vertical multi layer stack keeps simultaneous timestamps and live effect keyframes`() {
        val project = VideoStarRepository.createProject("AE Unit Stack", AspectRatioMode.PORTRAIT)
        try {
            VideoStarRepository.addOrUpdatePhotoClip(
                project.id,
                MediaClip.Photo("unit_img_1", "file:///tmp/unit_1.jpg", durationMs = 3_000L)
            )
            VideoStarRepository.ensureAeLayersHydrated(project.id)

            VideoStarRepository.addAeStackedLayer(
                projectId = project.id,
                kind = AeLayerKind.TEXT,
                title = "Title"
            )
            VideoStarRepository.addAeStackedLayer(
                projectId = project.id,
                kind = AeLayerKind.ADJUSTMENT,
                title = "Adjustment"
            )

            val stacked = VideoStarRepository.getProjectById(project.id)!!.aeLayers
            assertEquals(3, stacked.size)
            assertTrue(stacked.all { it.startTimeMs == 0L })
            assertEquals(AeLayerKind.ADJUSTMENT, stacked[0].layerKind)
            assertEquals(AeLayerKind.TEXT, stacked[1].layerKind)
            assertEquals(AeLayerKind.MEDIA, stacked[2].layerKind)

            val topLayerId = stacked[0].id
            VideoStarRepository.toggleAeLayerVisibility(project.id, topLayerId)
            assertFalse(VideoStarRepository.getProjectById(project.id)!!.aeLayers[0].isVisible)
            VideoStarRepository.toggleAeLayerVisibility(project.id, topLayerId)
            assertTrue(VideoStarRepository.getProjectById(project.id)!!.aeLayers[0].isVisible)

            val item = EffectCatalog.findById("am_tiles")!!
            val pass = ShaderEffectPass(
                id = "pass_unit_1",
                effectId = item.id,
                effectName = item.name,
                category = item.category,
                intensity = 0.8f,
                params = item.defaultParamMap(),
                paramKeyframes = mapOf(
                    "intensity" to listOf(ParameterKeyframe(id = "kf_1", time = 0.5f, value = 0.8f))
                )
            )
            VideoStarRepository.updateAeLayer(project.id, topLayerId) { cur ->
                cur.copy(effectPasses = cur.effectPasses + pass)
            }
            val savedPasses = VideoStarRepository.getProjectById(project.id)!!.aeLayers[0].effectPasses
            assertEquals(listOf("am_tiles"), savedPasses.map { it.effectId })
            assertEquals(1, savedPasses.first().keyframesForProperty("intensity").size)
        } finally {
            VideoStarRepository.deleteProject(project.id)
        }
    }

    @Test
    fun `moveAeLayerTiming clamps pre-zero start times and respects locked layers`() {
        val project = VideoStarRepository.createProject("AE Timing Move", AspectRatioMode.PORTRAIT)
        try {
            VideoStarRepository.addAeStackedLayer(
                projectId = project.id,
                kind = AeLayerKind.SHAPE,
                title = "Shape 1"
            )
            val layer = VideoStarRepository.getProjectById(project.id)!!.aeLayers.first()

            // Negative start time must clamp strictly to 0L
            VideoStarRepository.moveAeLayerTiming(project.id, layer.id, -850L)
            assertEquals(0L, VideoStarRepository.getProjectById(project.id)!!.aeLayers.first().startTimeMs)

            // Positive move shifts startTimeMs cleanly
            VideoStarRepository.moveAeLayerTiming(project.id, layer.id, 1_750L)
            assertEquals(1_750L, VideoStarRepository.getProjectById(project.id)!!.aeLayers.first().startTimeMs)

            // Locked layers cannot be repositioned
            VideoStarRepository.toggleAeLayerLock(project.id, layer.id)
            VideoStarRepository.moveAeLayerTiming(project.id, layer.id, 4_200L)
            assertEquals(1_750L, VideoStarRepository.getProjectById(project.id)!!.aeLayers.first().startTimeMs)
        } finally {
            VideoStarRepository.deleteProject(project.id)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `workspace viewModel computes staggered ae duration, clamps seek to zero, and guards local video sync`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val project = VideoStarRepository.createProject("AE ViewModel Timeline", AspectRatioMode.PORTRAIT)
        val store = ViewModelStore()
        try {
            VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
            VideoStarRepository.addAeStackedLayer(
                projectId = project.id,
                kind = AeLayerKind.MEDIA,
                clip = MediaClip.Video(
                    id = "vid_1",
                    uriString = "file:///tmp/v1.mp4",
                    startTrimMs = 0L,
                    endTrimMs = 4_000L,
                    durationMs = 4_000L,
                    sourceFps = 30
                )
            )
            VideoStarRepository.addAeStackedLayer(
                projectId = project.id,
                kind = AeLayerKind.MEDIA,
                clip = MediaClip.Photo(
                    id = "img_2",
                    uriString = "file:///tmp/i2.jpg",
                    durationMs = 3_000L
                )
            )

            val layers = VideoStarRepository.getProjectById(project.id)!!.aeLayers
            assertEquals(2, layers.size)
            val secondLayer = layers.first() // newest on top
            VideoStarRepository.moveAeLayerTiming(project.id, secondLayer.id, 2_500L)

            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkspaceViewModel(project.id) as T
            }
            val vm = ViewModelProvider(store, factory)[WorkspaceViewModel::class.java]
            val ui = vm.uiState.value
            // Total duration in AE mode reflects max(startTimeMs + durationMs) = 2500 + 3000 = 5500ms
            assertEquals(5_500L, ui.totalDurationMs)

            // Seek clamps strictly >= 0L (never negative / pre-zero)
            vm.seekToPositionMs(-1_200L)
            assertEquals(0L, vm.uiState.value.currentPositionMs)

            vm.seekToPositionMs(2_100L)
            assertEquals(2_100L, vm.uiState.value.currentPositionMs)

            // ExoPlayer local video sync must NOT overwrite AE multi-layer playhead position
            vm.syncPositionFromLocalVideo(350L)
            assertEquals(2_100L, vm.uiState.value.currentPositionMs)

            // Moving layer via ViewModel updates totalDurationMs live
            vm.moveAeLayerTiming(secondLayer.id, 3_500L)
            assertEquals(6_500L, vm.uiState.value.totalDurationMs)
        } finally {
            store.clear()
            VideoStarRepository.deleteProject(project.id)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `media import helper computes bounded sample sizes and ae browser maps all 110 effects with favorites`() {
        assertEquals(2, MediaImportHelper.calculateInSampleSize(4032, 3024, 1080))
        assertEquals(16, MediaImportHelper.calculateInSampleSize(4032, 3024, 144))
        assertEquals(1, MediaImportHelper.calculateInSampleSize(720, 720, 1080))

        val aeOnly = EffectCatalog.combinedShaderEffects.filter { it.category.isAlightMotion }
        assertEquals(110, aeOnly.size)
        aeOnly.forEach { item ->
            val meta = AeEffectsBrowserCatalog.metaFor(item)
            assertTrue("Missing description for ${item.id}", meta.description.isNotBlank())
            assertTrue(meta.browserCategory != AeBrowserCategory.MY_EFFECTS)
        }
        val categories = AeEffectsBrowserCatalog.visibleCategories(aeOnly)
        assertTrue(categories.first() == AeBrowserCategory.MY_EFFECTS)
        assertTrue(categories.size >= 10)

        val wasAdded = VideoStarRepository.toggleFavoriteAeEffect("am_tiles")
        assertTrue(wasAdded)
        assertTrue("am_tiles" in VideoStarRepository.favoriteAeEffectIds.value)
        val wasRemoved = VideoStarRepository.toggleFavoriteAeEffect("am_tiles")
        assertFalse(wasRemoved)
        assertFalse("am_tiles" in VideoStarRepository.favoriteAeEffectIds.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `new empty ae project safely handles seeking stepping and playback toggle without crashing`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val project = VideoStarRepository.createProject("مشروع جديد فارغ", AspectRatioMode.PORTRAIT)
        val store = ViewModelStore()
        try {
            VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkspaceViewModel(project.id) as T
            }
            val vm = ViewModelProvider(store, factory)[WorkspaceViewModel::class.java]
            assertEquals(0, vm.uiState.value.aeLayers.size)

            vm.seekToPositionMs(1_500L)
            assertEquals(1_500L, vm.uiState.value.currentPositionMs)
            vm.stepBackward()
            vm.stepForward()
            vm.togglePlayback()
            assertTrue(vm.uiState.value.isPlaying)
            vm.togglePlayback()
            assertFalse(vm.uiState.value.isPlaying)
        } finally {
            store.clear()
            VideoStarRepository.deleteProject(project.id)
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `commitAeLayerTimingDrag records single undoable move, clamps zero, and ignores locked layers`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val project = VideoStarRepository.createProject("AE Drag Commit", AspectRatioMode.PORTRAIT)
        val store = ViewModelStore()
        try {
            VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
            val created = VideoStarRepository.addAeStackedLayer(
                projectId = project.id,
                kind = AeLayerKind.SHAPE,
                title = "Drag Target",
                startTimeMs = 1_000L,
                durationMs = 3_000L
            )!!
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkspaceViewModel(project.id) as T
            }
            val vm = ViewModelProvider(store, factory)[WorkspaceViewModel::class.java]
            assertFalse(vm.uiState.value.canUndo)

            // Commit a drag from 1000ms -> 2400ms
            vm.commitAeLayerTimingDrag(created.id, initialStartMs = 1_000L, finalStartMs = 2_400L)
            assertEquals(2_400L, VideoStarRepository.getProjectById(project.id)!!.aeLayers.first().startTimeMs)
            assertTrue(vm.uiState.value.canUndo)

            // Single undo must restore 1000ms directly
            vm.undoTimelineEdit()
            assertEquals(1_000L, VideoStarRepository.getProjectById(project.id)!!.aeLayers.first().startTimeMs)

            // Negative drag clamps strictly to 0L
            vm.commitAeLayerTimingDrag(created.id, initialStartMs = 1_000L, finalStartMs = -900L)
            assertEquals(0L, VideoStarRepository.getProjectById(project.id)!!.aeLayers.first().startTimeMs)

            // Locked layers reject timing drag commits cleanly
            VideoStarRepository.toggleAeLayerLock(project.id, created.id)
            vm.commitAeLayerTimingDrag(created.id, initialStartMs = 0L, finalStartMs = 3_200L)
            assertEquals(0L, VideoStarRepository.getProjectById(project.id)!!.aeLayers.first().startTimeMs)
        } finally {
            store.clear()
            VideoStarRepository.deleteProject(project.id)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `saveProjectLayers and LayersViewModel edit targeted Scene 1 strictly in-place without creating extra Scene 4`() {
        val project = VideoStarRepository.createProject("Three Scene Nova Test", AspectRatioMode.PORTRAIT)
        try {
            val clip1 = MediaClip.Photo(id = "c1", uriString = "content://test/1.jpg", durationMs = 2_000L)
            val clip2 = MediaClip.Photo(id = "c2", uriString = "content://test/2.jpg", durationMs = 2_000L)
            val clip3 = MediaClip.Photo(id = "c3", uriString = "content://test/3.jpg", durationMs = 2_000L)
            val seededScenes = listOf(
                TimelineScene(id = "sc_1", title = "Scene 1", durationMs = 2_000L, clipId = "c1", mediaUriString = clip1.uriString, isVideo = false, clip = clip1),
                TimelineScene(id = "sc_2", title = "Scene 2", durationMs = 2_000L, clipId = "c2", mediaUriString = clip2.uriString, isVideo = false, clip = clip2),
                TimelineScene(id = "sc_3", title = "Scene 3", durationMs = 2_000L, clipId = "c3", mediaUriString = clip3.uriString, isVideo = false, clip = clip3)
            )
            VideoStarRepository.restoreScenes(project.id, seededScenes)

            // Targeted edit on Scene 1 (index 0) via LayersViewModel
            val vm = com.example.ui.screens.layers.LayersViewModel(project.id, targetSceneIndex = 0)
            val fxPass = ShaderEffectPass(
                id = "pass_1",
                effectId = "am_turbulent_displace",
                effectName = "Turbulent Displace",
                category = EffectCategory.AM_DISTORTION_WARP,
                intensity = 0.75f
            )
            val editedLayers = vm.uiState.value.layers.mapIndexed { idx, layer ->
                if (idx == 0) layer.copy(scale = 1.4f, effectPasses = listOf(fxPass)) else layer
            }
            VideoStarRepository.saveProjectLayers(
                projectId = project.id,
                layers = editedLayers,
                targetSceneIndex = 0,
                createNewSceneIfEmpty = false
            )

            val afterFirstSave = VideoStarRepository.getProjectById(project.id)!!
            assertEquals(3, afterFirstSave.scenes.size)
            assertEquals("sc_1", afterFirstSave.scenes[0].id)
            assertTrue(afterFirstSave.scenes[0].isMultiLayerComposite)
            assertEquals(1.4f, afterFirstSave.scenes[0].layers.first().scale)
            assertEquals("am_turbulent_displace", afterFirstSave.scenes[0].resolvedEffectPasses().first().effectId)
            assertFalse(afterFirstSave.scenes[2].isMultiLayerComposite)

            // Even calling saveProjectLayers without targetSceneIndex must NEVER append Scene 4 when scenes exist
            VideoStarRepository.saveProjectLayers(
                projectId = project.id,
                layers = editedLayers.mapIndexed { i, l -> if (i == 0) l.copy(scale = 1.8f) else l },
                targetSceneIndex = null,
                createNewSceneIfEmpty = true
            )
            val afterSecondSave = VideoStarRepository.getProjectById(project.id)!!
            assertEquals(3, afterSecondSave.scenes.size)
            assertEquals(1.8f, afterSecondSave.scenes[0].layers.first().scale)
        } finally {
            VideoStarRepository.deleteProject(project.id)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `ae mode resolves Scene 1 at t=0ms instead of Scene 3 and syncs AE effect back to Scene 1 in-place`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val project = VideoStarRepository.createProject("AE Scene Sync Test", AspectRatioMode.PORTRAIT)
        val store = ViewModelStore()
        try {
            val clip1 = MediaClip.Photo(id = "c1", uriString = "content://ae/1.jpg", durationMs = 2_000L)
            val clip2 = MediaClip.Photo(id = "c2", uriString = "content://ae/2.jpg", durationMs = 2_000L)
            val clip3 = MediaClip.Photo(id = "c3", uriString = "content://ae/3.jpg", durationMs = 2_000L)
            VideoStarRepository.restoreScenes(
                project.id,
                listOf(
                    TimelineScene(id = "sc_1", title = "Scene 1", durationMs = 2_000L, clipId = "c1", mediaUriString = clip1.uriString, isVideo = false, clip = clip1),
                    TimelineScene(id = "sc_2", title = "Scene 2", durationMs = 2_000L, clipId = "c2", mediaUriString = clip2.uriString, isVideo = false, clip = clip2),
                    TimelineScene(id = "sc_3", title = "Scene 3", durationMs = 2_000L, clipId = "c3", mediaUriString = clip3.uriString, isVideo = false, clip = clip3)
                )
            )
            VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
            VideoStarRepository.ensureAeLayersHydrated(project.id)

            val hydrated = VideoStarRepository.getProjectById(project.id)!!
            // Playhead at t=0ms MUST resolve Scene 1's layer, NOT Scene 3 (even though display stack reverses Z-order)
            val layerAtStart = hydrated.resolveActiveAeLayer(0L)!!
            assertEquals("sc_1", layerAtStart.sourceSceneId)
            assertEquals(0, hydrated.resolveSceneIndexForAeLayer(layerAtStart))

            // Playhead at t=2500ms resolves Scene 2's layer
            val layerAtMid = hydrated.resolveActiveAeLayer(2_500L)!!
            assertEquals("sc_2", layerAtMid.sourceSceneId)
            assertEquals(1, hydrated.resolveSceneIndexForAeLayer(layerAtMid))

            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkspaceViewModel(project.id) as T
            }
            val vm = ViewModelProvider(store, factory)[WorkspaceViewModel::class.java]
            vm.selectAeLayer(layerAtStart.id)
            assertEquals(0, vm.uiState.value.activeSceneIndex)

            vm.addAeLayerEffect(layerAtStart.id, "am_turbulent_displace")
            val updatedProj = VideoStarRepository.getProjectById(project.id)!!
            assertEquals(3, updatedProj.scenes.size)
            assertEquals("am_turbulent_displace", updatedProj.scenes[0].resolvedEffectPasses().firstOrNull()?.effectId)
            assertTrue(updatedProj.scenes[2].resolvedEffectPasses().isEmpty())
        } finally {
            store.clear()
            VideoStarRepository.deleteProject(project.id)
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `ae single video playback wraps from end on play and pausePlayback is idempotent without resetting position`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val project = VideoStarRepository.createProject("AE Single Video Playback", AspectRatioMode.PORTRAIT)
        val store = ViewModelStore()
        try {
            VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
            VideoStarRepository.addAeStackedLayer(
                projectId = project.id,
                kind = AeLayerKind.MEDIA,
                clip = MediaClip.Video(
                    id = "single_vid_1",
                    uriString = "file:///tmp/single_vid.mp4",
                    startTrimMs = 0L,
                    endTrimMs = 3_000L,
                    durationMs = 3_000L,
                    sourceFps = 30
                )
            )
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkspaceViewModel(project.id) as T
            }
            val vm = ViewModelProvider(store, factory)[WorkspaceViewModel::class.java]

            // Seek to end of single-video timeline, then press Play -> wraps to 0 and keeps playing
            vm.seekToPositionMs(3_000L)
            assertEquals(3_000L, vm.uiState.value.currentPositionMs)
            vm.togglePlayback()
            assertTrue(vm.uiState.value.isPlaying)
            assertEquals(0L, vm.uiState.value.currentPositionMs)

            // Idempotent pause preserves current position
            vm.seekToPositionMs(1_250L)
            vm.pausePlayback()
            assertFalse(vm.uiState.value.isPlaying)
            assertEquals(1_250L, vm.uiState.value.currentPositionMs)
            vm.pausePlayback()
            assertFalse(vm.uiState.value.isPlaying)
            assertEquals(1_250L, vm.uiState.value.currentPositionMs)
        } finally {
            store.clear()
            VideoStarRepository.deleteProject(project.id)
            Dispatchers.resetMain()
        }
    }
}


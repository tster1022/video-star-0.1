package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.data.VideoStarRepository
import com.example.engine.render.ScrubDispatchDecision
import com.example.engine.render.StudioRenderErrorBoundary
import com.example.engine.render.StudioRenderMemoryCoordinator
import com.example.engine.render.StudioScrubThrottleController
import com.example.model.AeLayerKind
import com.example.model.AspectRatioMode
import com.example.model.EffectCatalog
import com.example.model.MediaClip
import com.example.model.ShaderEffectPass
import com.example.model.StudioLayoutMode
import com.example.ui.screens.workspace.WorkspaceViewModel
import com.example.ui.screens.workspace.computeAeTimelineZoomScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOverhaulAndCacheUnitTest {

    @Test
    fun `memory coordinator stays strictly under 64MB and invokes trim listeners on release`() {
        val combinedKb = StudioRenderMemoryCoordinator.MAX_VIDEO_FRAME_CACHE_KB +
            StudioRenderMemoryCoordinator.MAX_SHADER_THUMB_CACHE_KB +
            StudioRenderMemoryCoordinator.MAX_SHADER_SOURCE_CACHE_KB

        assertTrue(
            "Combined cache cap ($combinedKb KB) must be strictly under 64 MB (65536 KB)",
            combinedKb <= StudioRenderMemoryCoordinator.MAX_TOTAL_MEMORY_BUDGET_KB &&
                combinedKb < 64 * 1024
        )
        assertEquals(56 * 1024, combinedKb)
        assertEquals(1, StudioRenderMemoryCoordinator.bitmapSizeKb(128))
        assertEquals(4096, StudioRenderMemoryCoordinator.bitmapSizeKb(4 * 1024 * 1024))

        val key = "unit_test_trim_listener"
        var nonAggressiveCalls = 0
        var aggressiveCalls = 0
        StudioRenderMemoryCoordinator.registerTrimListener(key) { aggressive ->
            if (aggressive) aggressiveCalls++ else nonAggressiveCalls++
        }
        try {
            val before = StudioRenderMemoryCoordinator.trimCount()
            StudioRenderMemoryCoordinator.trimTemporaryBuffers(aggressive = false)
            StudioRenderMemoryCoordinator.trimTemporaryBuffers(aggressive = true)
            assertEquals(before + 2L, StudioRenderMemoryCoordinator.trimCount())
            assertEquals(1, nonAggressiveCalls)
            assertEquals(1, aggressiveCalls)
        } finally {
            StudioRenderMemoryCoordinator.unregisterTrimListener(key)
        }
    }

    @Test
    fun `scrub throttle drops busy and high-frequency frames while coalescing latest timestamp`() {
        val controller = StudioScrubThrottleController(minFrameIntervalMs = 16L)
        controller.beginScrub(initialPositionMs = 100L)
        assertTrue(controller.isScrubbing)

        // Frame at t=0ms dispatches immediately
        val d1 = controller.requestFrame(targetTimeMs = 250L, nowNanos = 1_000_000_000L)
        assertEquals(ScrubDispatchDecision.Dispatch(250L), d1)

        // Rapid 120Hz / 240Hz drag bursts while render is busy are dropped, retaining latest timestamp
        assertEquals(
            ScrubDispatchDecision.DroppedWhileBusy,
            controller.requestFrame(targetTimeMs = 400L, nowNanos = 1_004_000_000L)
        )
        assertEquals(
            ScrubDispatchDecision.DroppedWhileBusy,
            controller.requestFrame(targetTimeMs = 780L, nowNanos = 1_008_000_000L)
        )

        // Completing the in-flight frame surfaces only the latest coalesced timestamp (780ms)
        val pending = controller.completeFrameRender()
        assertEquals(780L, pending)

        // Requesting within < 16ms throttle window while still scrubbing is throttled
        val d2 = controller.requestFrame(targetTimeMs = 910L, nowNanos = 1_010_000_000L)
        assertEquals(ScrubDispatchDecision.Throttled, d2)

        // After 16ms passes, new frame dispatches cleanly
        val d3 = controller.requestFrame(targetTimeMs = 1_120L, nowNanos = 1_020_000_000L)
        assertEquals(ScrubDispatchDecision.Dispatch(1_120L), d3)
        assertNull(controller.completeFrameRender())

        controller.endScrub()
        assertFalse(controller.isScrubbing)
        val (dispatched, dropped) = controller.stats()
        assertEquals(2L, dispatched)
        assertEquals(3L, dropped)
    }

    @Test
    fun `render error boundary isolates runtime faults and preserves fallback output`() {
        StudioRenderErrorBoundary.resetStatsForTest()
        val safeValue = StudioRenderErrorBoundary.safeEvaluate("unit_fault_tag", fallback = "last_valid_frame") {
            throw IllegalStateException("Simulated shader uniform fault")
        }
        assertEquals("last_valid_frame", safeValue)
        assertEquals(1L, StudioRenderErrorBoundary.errorCount())
        assertEquals("unit_fault_tag", StudioRenderErrorBoundary.lastErrorTag)

        val normalValue = StudioRenderErrorBoundary.safeEvaluate("ok_tag", fallback = 0) { 42 }
        assertEquals(42, normalValue)
        assertEquals(1L, StudioRenderErrorBoundary.errorCount())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `workspace playback toggles never stack loops and scrub lifecycle halts playback cleanly`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val project = VideoStarRepository.createProject("Playback Safety Unit", AspectRatioMode.PORTRAIT)
        val store = ViewModelStore()
        try {
            VideoStarRepository.addOrUpdatePhotoClip(
                project.id,
                MediaClip.Photo("pb_img_1", "file:///tmp/pb_1.jpg", durationMs = 4_000L)
            )
            VideoStarRepository.updateStudioLayout(project.id, StudioLayoutMode.AFTER_EFFECTS)
            VideoStarRepository.ensureAeLayersHydrated(project.id)

            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkspaceViewModel(project.id) as T
            }
            val vm = ViewModelProvider(store, factory)[WorkspaceViewModel::class.java]

            val initialToken = vm.currentPlaybackSessionId()
            vm.togglePlayback() // start
            val playToken1 = vm.currentPlaybackSessionId()
            assertTrue(playToken1 > initialToken)
            assertTrue(vm.uiState.value.isPlaying)

            vm.togglePlayback() // pause
            val pauseToken = vm.currentPlaybackSessionId()
            assertTrue(pauseToken > playToken1)
            assertFalse(vm.uiState.value.isPlaying)

            vm.togglePlayback() // resume
            val playToken2 = vm.currentPlaybackSessionId()
            assertTrue(playToken2 > pauseToken)
            assertTrue(vm.uiState.value.isPlaying)

            // Begin scrubbing immediately cancels active playback and locks against stale video sync
            vm.beginScrubbing()
            assertFalse(vm.uiState.value.isPlaying)
            assertTrue(vm.uiState.value.isScrubbing)
            assertTrue(vm.currentPlaybackSessionId() > playToken2)

            vm.seekToProgress(0.5f)
            val midpointMs = vm.uiState.value.currentPositionMs
            assertTrue(midpointMs in 1_800L..2_200L)

            // Local ExoPlayer callback during scrub/pause must NOT hijack playhead position
            vm.syncPositionFromLocalVideo(120L)
            assertEquals(midpointMs, vm.uiState.value.currentPositionMs)

            vm.endScrubbing()
            assertFalse(vm.uiState.value.isScrubbing)
        } finally {
            store.clear()
            VideoStarRepository.deleteProject(project.id)
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `switching across all 5 studio layouts trims buffers resets playback and preserves project edits`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val project = VideoStarRepository.createProject("All Layouts Switch Unit", AspectRatioMode.PORTRAIT)
        val store = ViewModelStore()
        try {
            VideoStarRepository.addOrUpdatePhotoClip(
                project.id,
                MediaClip.Photo("layout_img_1", "file:///tmp/layout_1.jpg", durationMs = 3_000L)
            )
            val fx = EffectCatalog.findById("am_tiles")!!
            val scenePass = ShaderEffectPass(
                id = "scene_fx_keep",
                effectId = fx.id,
                effectName = fx.name,
                category = fx.category,
                intensity = 0.75f
            )
            VideoStarRepository.applySceneEffectPasses(project.id, setOf(0), listOf(scenePass))

            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkspaceViewModel(project.id) as T
            }
            val vm = ViewModelProvider(store, factory)[WorkspaceViewModel::class.java]

            val trimStart = StudioRenderMemoryCoordinator.trimCount()
            val cycleOrder = listOf(
                StudioLayoutMode.CAPCUT,
                StudioLayoutMode.BLURRR,
                StudioLayoutMode.NODE_VIDEO,
                StudioLayoutMode.AFTER_EFFECTS,
                StudioLayoutMode.NOVA
            )

            cycleOrder.forEach { targetLayout ->
                vm.togglePlayback()
                assertTrue(vm.uiState.value.isPlaying)
                vm.selectStudioLayout(targetLayout)
                assertEquals(targetLayout, vm.uiState.value.studioLayout)
                assertFalse(vm.uiState.value.isPlaying)
                assertFalse(vm.uiState.value.isScrubbing)
                assertEquals(1, vm.uiState.value.project?.scenes?.size)
                assertEquals(
                    listOf("am_tiles"),
                    vm.uiState.value.project?.scenes?.first()?.resolvedEffectPasses()?.map { it.effectId }
                )
            }

            assertTrue(StudioRenderMemoryCoordinator.trimCount() >= trimStart + cycleOrder.size)
            assertTrue(vm.uiState.value.project?.aeLayers?.any { it.layerKind == AeLayerKind.MEDIA } == true)
        } finally {
            store.clear()
            VideoStarRepository.deleteProject(project.id)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `after effects timeline pinch zoom clamps bounds and adapts ruler step cadence`() {
        val defaultScale = computeAeTimelineZoomScale(1.0f)
        assertEquals(1.0f, defaultScale.zoomFactor, 0.001f)
        assertEquals(88f, defaultScale.dpPerSecond, 0.01f)
        assertEquals(1_000L, defaultScale.majorStepMs)
        assertEquals(200L, defaultScale.minorStepMs)

        // Pinching in zooms out so seconds compress and sweep faster with non-crowded 5s ticks
        val zoomedOutExtreme = computeAeTimelineZoomScale(0.05f)
        assertEquals(0.22f, zoomedOutExtreme.zoomFactor, 0.001f)
        assertTrue(zoomedOutExtreme.dpPerSecond < 30f)
        assertEquals(5_000L, zoomedOutExtreme.majorStepMs)
        assertEquals(1_000L, zoomedOutExtreme.minorStepMs)

        val zoomedOutMedium = computeAeTimelineZoomScale(0.5f)
        assertEquals(44f, zoomedOutMedium.dpPerSecond, 0.01f)
        assertEquals(2_000L, zoomedOutMedium.majorStepMs)

        // Pinching out zooms in for frame-level precision
        val zoomedInMax = computeAeTimelineZoomScale(10f)
        assertEquals(3.6f, zoomedInMax.zoomFactor, 0.001f)
        assertTrue(zoomedInMax.dpPerSecond > 190f)
        assertEquals(500L, zoomedInMax.majorStepMs)
        assertEquals(100L, zoomedInMax.minorStepMs)
    }
}

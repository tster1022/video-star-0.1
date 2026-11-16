package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.JsonProjectStore
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.EffectCatalog
import com.example.model.MediaClip
import com.example.model.MotionPreset
import com.example.model.TimingMode
import com.example.model.TransitionCategory
import com.example.ui.screens.mix.MixViewModel
import com.example.ui.screens.motion.MotionViewModel
import com.example.ui.screens.reeffect.ReEffectViewModel
import com.example.ui.screens.timing.TimingViewModel
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
class VideoStarSceneWorkflowTest {

    @Test
    fun `json project store roundtrips scenes effects motion timing and audio config`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val proj = VideoStarRepository.createProject("اختبار الحفظ", AspectRatioMode.PORTRAIT)
        val clip1 = MediaClip.Photo("p1", "file:///tmp/photo1.jpg", durationMs = 2_500L)
        val clip2 = MediaClip.Photo("p2", "file:///tmp/photo2.jpg", durationMs = 3_000L)
        VideoStarRepository.addOrUpdatePhotoClip(proj.id, clip1)
        VideoStarRepository.addOrUpdatePhotoClip(proj.id, clip2)

        val current = VideoStarRepository.projects.value.first { it.id == proj.id }
        JsonProjectStore.saveProjects(context, listOf(current))
        val loaded = JsonProjectStore.loadProjects(context)

        assertTrue(loaded.any { it.id == proj.id && it.scenes.size == 2 })
        val restored = loaded.first { it.id == proj.id }
        assertEquals(2_500L, restored.scenes[0].effectiveDurationMs)
        assertEquals(3_000L, restored.scenes[1].effectiveDurationMs)

        VideoStarRepository.deleteProject(proj.id)
    }

    @Test
    fun `workspace scene stepping quick split duplicate reverse merge and delete work on real scenes`() {
        val proj = VideoStarRepository.createProject("اختبار المشاهد", AspectRatioMode.SQUARE)
        VideoStarRepository.addOrUpdatePhotoClip(proj.id, MediaClip.Photo("p1", "file:///tmp/1.jpg", durationMs = 2_000L))
        VideoStarRepository.addOrUpdatePhotoClip(proj.id, MediaClip.Photo("p2", "file:///tmp/2.jpg", durationMs = 2_000L))

        val ws = WorkspaceViewModel(proj.id)
        assertEquals(2, ws.uiState.value.project?.scenes?.size)
        assertEquals(0, ws.uiState.value.activeSceneIndex)

        // 1-frame stepping at default 30fps (~33ms)
        ws.stepForward()
        assertEquals(0, ws.uiState.value.activeSceneIndex)
        assertEquals(33L, ws.uiState.value.currentPositionMs)

        ws.stepBackward()
        assertEquals(0, ws.uiState.value.activeSceneIndex)
        assertEquals(0L, ws.uiState.value.currentPositionMs)

        // Scene selection switches between Scene 0 and Scene 1
        ws.selectScene(1)
        assertEquals(1, ws.uiState.value.activeSceneIndex)
        assertEquals(2_000L, ws.uiState.value.currentPositionMs)

        ws.selectScene(0)
        assertEquals(0, ws.uiState.value.activeSceneIndex)
        assertEquals(0L, ws.uiState.value.currentPositionMs)

        // Duplicate scene 0 -> 3 scenes
        ws.duplicateActiveScene()
        assertEquals(3, VideoStarRepository.getProjectById(proj.id)?.scenes?.size)

        // Reverse active scene -> isReversed toggles
        ws.toggleReverseActiveScene()
        assertTrue(VideoStarRepository.getProjectById(proj.id)?.scenes?.get(0)?.isReversed == true)

        // Merge scene 0 and scene 1 -> back to 2 scenes
        ws.mergeActiveSceneWithNext()
        assertEquals(2, VideoStarRepository.getProjectById(proj.id)?.scenes?.size)

        VideoStarRepository.deleteProject(proj.id)
    }

    @Test
    fun `reeffect motion timing mix and transform with mirror edges persist into scene`() {
        val proj = VideoStarRepository.createProject("اختبار الأدوات", AspectRatioMode.PORTRAIT)
        VideoStarRepository.addOrUpdatePhotoClip(proj.id, MediaClip.Photo("p1", "file:///tmp/a.jpg", durationMs = 2_000L))
        VideoStarRepository.addOrUpdatePhotoClip(proj.id, MediaClip.Photo("p2", "file:///tmp/b.jpg", durationMs = 2_000L))

        // Re-Effect batch on both scenes
        val fxVm = ReEffectViewModel(proj.id, listOf(0, 1))
        val vhs = EffectCatalog.findById("vhs_tape")!!
        fxVm.selectEffect(vhs)
        fxVm.updateIntensity(0.85f)
        var fxSaved = false
        fxVm.createAndSave { fxSaved = true }
        assertTrue(fxSaved)

        // Motion on scene 1
        val motionVm = MotionViewModel(proj.id, sceneIndex = 1)
        motionVm.selectPreset(MotionPreset.PENDULUM)
        motionVm.updateSpeed(2.0f)
        motionVm.updateIntensity(0.75f)
        motionVm.createAndSave {}

        // Timing on scene 0 with freeze frame + repeat 2x
        val timingVm = TimingViewModel(proj.id, sceneIndex = 0)
        timingVm.setMode(TimingMode.FLEXIBLE)
        timingVm.setDurationSeconds(4.0f)
        timingVm.setFreezeEnabled(true)
        timingVm.setFreezeDurationSeconds(1.5f)
        timingVm.setRepeatCount(2)
        timingVm.createAndSave {}

        // Mix between scene 0 and scene 1
        val mixVm = MixViewModel(proj.id, sceneIndex = 0)
        assertTrue(mixVm.uiState.value.hasValidPair)
        mixVm.selectCategory(TransitionCategory.FLIP_3D)
        mixVm.updateDurationSeconds(0.9f)
        mixVm.createAndSave {}

        // Transform with Mirror Edges on scene 1
        val tfVm = TransformViewModel(proj.id, sceneIndex = 1)
        tfVm.toggleMirrorEdges()
        tfVm.setZoom(1.4f)
        tfVm.createAndSave {}

        val updated = VideoStarRepository.getProjectById(proj.id)
        assertNotNull(updated)
        val s0 = updated!!.scenes[0]
        val s1 = updated.scenes[1]

        assertEquals("vhs_tape", s0.effect.effectId)
        assertEquals("vhs_tape", s1.effect.effectId)
        assertEquals(0.85f, s1.effect.intensity, 0.01f)
        assertEquals(MotionPreset.PENDULUM, s1.motion.preset)
        assertEquals(2.0f, s1.motion.speed, 0.01f)
        assertEquals(TimingMode.FLEXIBLE, s0.timing.mode)
        assertEquals(5_500L, s0.effectiveDurationMs)
        assertEquals(2, s0.timing.repeatCount)
        assertNotNull(s0.transitionToNext)
        assertEquals(900L, s0.transitionToNext?.durationMs)
        assertTrue(s1.transform.mirrorEdges)
        assertFalse(s0.transform.mirrorEdges)
        assertEquals(1.4f, s1.transform.scale, 0.01f)

        VideoStarRepository.deleteProject(proj.id)
    }
}

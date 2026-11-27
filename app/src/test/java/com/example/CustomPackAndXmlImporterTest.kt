package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.VideoStarRepository
import com.example.engine.ae.AeEffectEngine
import com.example.engine.ae.AeEffectEngineCatalog
import com.example.engine.ae.AeEngineCategory
import com.example.engine.effects.shaders.ShaderCommonGlsl
import com.example.engine.packs.AmXmlEffectParser
import com.example.engine.packs.BundledCustomShaders
import com.example.engine.packs.CustomPackRepository
import com.example.engine.packs.GlslCompatTranspiler
import com.example.gl.ShaderLibrary
import com.example.model.AspectRatioMode
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogScope
import com.example.model.EffectParamType
import com.example.model.EffectStudioTab
import com.example.model.MediaClip
import com.example.model.PARAM_KEY_INTENSITY
import com.example.ui.screens.effects.EffectsStudioViewModel
import com.example.ui.screens.packs.PackImportStudioSheet
import com.example.ui.theme.VideoStarTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomPackAndXmlImporterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CustomPackRepository.resetAllForTesting(context)
    }

    @After
    fun tearDown() {
        CustomPackRepository.resetAllForTesting(context)
    }

    @Test
    fun `glsl compat transpiler converts ES 100 shaders to ES 300 and flags UBO or vertex shaders`() {
        val rippleGlsl = """
            #version 100
            precision mediump float;
            uniform sampler2D texture;
            uniform float alpha;
            varying vec2 st;
            uniform float time;
            uniform float amplitude;
            uniform float frequency;
            uniform vec2 center;
            void main() {
                vec2 p = st - center;
                float len = length(p);
                float ripple = sin(len * frequency - time * 6.0) * amplitude;
                vec2 uv = st + (p / max(len, 1e-4)) * ripple;
                vec4 dst = texture2D(texture, clamp(uv, 0.0, 1.0));
                gl_FragColor = dst * alpha;
            }
        """.trimIndent()

        val result = GlslCompatTranspiler.transpile(rippleGlsl, "tex2d_ripple.frag")
        assertTrue(result.isRunnablePostProcess)
        assertEquals("100", result.originalVersion)
        assertEquals("ripple", result.fallbackEffectId)
        assertTrue(result.params.any { it.key == "amplitude" })
        assertTrue(result.params.any { it.key == "frequency" })
        assertTrue(result.params.any { it.key == "center_x" && it.paramType == EffectParamType.POINT_2D_X })
        assertTrue(result.params.any { it.key == "center_y" && it.paramType == EffectParamType.POINT_2D_Y })

        val fullEs300 = ShaderCommonGlsl.buildCustomFragmentShader(result.preambleDecls, result.mainBody)
        assertTrue(fullEs300.startsWith("#version 300 es"))
        assertTrue(fullEs300.contains("texture(uTexture"))
        assertTrue(fullEs300.contains("runCustomUserMain(vec2 _starUv, vec4 _starOrig)"))
        assertFalse(fullEs300.contains("gl_FragColor"))
        assertFalse(fullEs300.contains("texture2D"))

        // NanoVG multi-UBO shader and .vert files should safely classify as non-runnable with valid fallback
        val nanoVgUbo = """
            #version 100
            #ifdef USE_UNIFORMBUFFER
            layout(std140) uniform frag { mat3 scissorMat; };
            #endif
            void main() { gl_FragColor = vec4(1.0); }
        """.trimIndent()
        val uboRes = GlslCompatTranspiler.transpile(nanoVgUbo, "nanovg_base.frag")
        assertFalse(uboRes.isRunnablePostProcess)
        assertTrue(ShaderLibrary.hasShader(uboRes.fallbackEffectId))

        val vertRes = GlslCompatTranspiler.transpile("void main() { gl_Position = vec4(0.0); }", "quad.vert")
        assertFalse(vertRes.isRunnablePostProcess)
    }

    @Test
    fun `am xml parser extracts stacked effects parameters and keyframes across engines`() {
        val sampleAmXml = """
            <scene title="Velocity Shake Preset" duration="2000">
              <media startTime="0" endTime="2000">
                <effect id="com.alightcreative.effects.tiles" label="Mirror Tiles" locallyApplied="true" blendMode="normal">
                  <property name="crop" type="float" value="1.25" />
                  <property name="mirror" type="bool" value="true" />
                </effect>
                <effect id="com.alightcreative.effects.motionblur2" label="Motion Blur" locallyApplied="true" blendMode="add">
                  <property name="intensity" type="float" value="80">
                    <kf t="0" v="0" e="linear" />
                    <kf t="2000" v="100" e="easeInOut" />
                  </property>
                </effect>
              </media>
            </scene>
        """.trimIndent()

        val parsed = AmXmlEffectParser.parseXml(
            rawXml = sampleAmXml,
            fileName = "velocity_shake.xml",
            packId = "pack_test_xml",
            packName = "Test AM Pack"
        )
        assertEquals(1, parsed.presets.size)
        val preset = parsed.presets.first()
        assertEquals("Velocity Shake Preset", preset.name)
        assertEquals(2, preset.passes.size)
        assertEquals("am_tiles", preset.passes[0].effectId)
        assertEquals("dir_motion_blur", preset.passes[1].effectId)
        assertEquals(2, preset.passes[1].keyframesForProperty(PARAM_KEY_INTENSITY).size)
        assertEquals(0f, preset.passes[1].keyframesForProperty(PARAM_KEY_INTENSITY).first().time, 0.01f)
        assertEquals(1f, preset.passes[1].keyframesForProperty(PARAM_KEY_INTENSITY).last().time, 0.01f)
    }

    @Test
    fun `installing bundled shaders and pasting xml updates all engines and cleans up to 145 baseline`() {
        // Cold baseline must start cleanly at 145
        assertEquals(145, EffectCatalog.combinedShaderEffects.size)

        val installedCount = CustomPackRepository.installBundledShaderPack(context)
        assertEquals(BundledCustomShaders.shaders.size, installedCount)
        assertEquals(145 + installedCount, EffectCatalog.combinedShaderEffects.size)
        assertTrue(ShaderLibrary.hasShader("custom_glsl_tex2d_ripple"))
        assertTrue(ShaderLibrary.fragmentShaderFor("custom_glsl_tex2d_ripple").contains("#version 300 es"))

        // Pasting XML preset adds to catalog + AE catalog + EffectsStudioViewModel MY_EFFECTS tab
        val xmlItem = CustomPackRepository.importPastedCode(
            context = context,
            rawCode = """
                <scene title="XML Swirl Glow">
                  <effect id="com.alightcreative.effects.swirl" label="Swirl">
                    <property name="strength" type="float" value="0.65" />
                  </effect>
                  <effect id="com.alightcreative.effects.glow" label="Glow">
                    <property name="intensity" type="float" value="75" />
                  </effect>
                </scene>
            """.trimIndent(),
            customName = "XML Swirl Glow"
        )
        assertNotNull(xmlItem)
        val xmlId = xmlItem!!.id
        assertTrue(AeEffectEngineCatalog.allEffects.any { it.id == xmlId && it.category == AeEngineCategory.MY_EFFECTS })

        val expandedAe = AeEffectEngine.createPassesForSelection(xmlId)
        assertEquals(listOf("am_swirl", "glow_bloom"), expandedAe.map { it.effectId })

        val project = VideoStarRepository.createProject("اختبار حزم XML", AspectRatioMode.PORTRAIT)
        try {
            VideoStarRepository.addOrUpdatePhotoClip(
                project.id,
                MediaClip.Photo("pack_test_photo", "file:///tmp/pack.jpg", durationMs = 2_000L)
            )
            val vsVm = EffectsStudioViewModel(
                projectId = project.id,
                sceneIndices = listOf(0),
                catalogScope = EffectCatalogScope.VIDEO_STAR_ONLY
            )
            vsVm.selectTab(EffectStudioTab.MY_EFFECTS)
            val myEffectIds = vsVm.uiState.value.displayedEffects.map { it.id }.toSet()
            assertTrue(xmlId in myEffectIds)
            assertTrue("custom_glsl_tex2d_ripple" in myEffectIds)

            vsVm.addEffectFromLibrary(xmlItem)
            assertEquals(listOf("am_swirl", "glow_bloom"), vsVm.uiState.value.passes.map { it.effectId })
        } finally {
            VideoStarRepository.deleteProject(project.id)
        }

        CustomPackRepository.resetAllForTesting(context)
        assertEquals(145, EffectCatalog.combinedShaderEffects.size)
    }

    @Test
    fun `pack import studio sheet installs bundled pack and imports pasted xml preset interactively`() {
        var appliedId: String? = null
        composeRule.setContent {
            VideoStarTheme(darkTheme = true) {
                PackImportStudioSheet(
                    initialTab = "ALL",
                    onDismiss = {},
                    onApplyEffectItem = { appliedId = it }
                )
            }
        }

        composeRule.onNodeWithTag("pack_import_studio_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("pack_install_bundled_btn").performClick()
        composeRule.waitForIdle()

        assertTrue(CustomPackRepository.importedShaders.value.size == BundledCustomShaders.shaders.size)
        composeRule.onNodeWithText("AM Water Ripple").assertIsDisplayed()

        // Open Copy/Paste XML/GLSL dialog and import XML directly
        composeRule.onNodeWithTag("pack_paste_code_btn").performClick()
        composeRule.onNodeWithTag("pack_paste_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("pack_paste_name_input").performTextInput("My Custom Ripple")
        composeRule.onNodeWithTag("pack_paste_code_input").performTextInput(
            "<effect id=\"com.alightcreative.effects.ripple\"><property name=\"intensity\" type=\"float\" value=\"70\"/></effect>"
        )
        composeRule.onNodeWithTag("pack_paste_save_btn").performClick()
        composeRule.waitForIdle()

        assertNotNull(appliedId)
        assertTrue(appliedId!!.startsWith("custom_xml_"))
    }
}

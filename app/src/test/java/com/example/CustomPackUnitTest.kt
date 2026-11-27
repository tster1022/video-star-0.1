package com.example

import com.example.engine.effects.shaders.ShaderCommonGlsl
import com.example.engine.packs.AmXmlEffectParser
import com.example.engine.packs.BundledCustomShaders
import com.example.engine.packs.CustomPackRepository
import com.example.engine.packs.GlslCompatTranspiler
import com.example.gl.ShaderLibrary
import com.example.model.EffectCatalog
import com.example.model.EffectParamType
import com.example.model.PARAM_KEY_INTENSITY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPackUnitTest {

    @Test
    fun `cold baseline stays exact 145 shaders until user imports packs`() {
        assertEquals(0, CustomPackRepository.packs.value.size)
        assertEquals(0, CustomPackRepository.importedShaders.value.size)
        assertEquals(0, CustomPackRepository.importedPresets.value.size)
        assertEquals(145, EffectCatalog.combinedShaderEffects.size)
    }

    @Test
    fun `bundled All_Shaders pack transpiles cleanly to ES 300 without uv collision`() {
        val shaders = BundledCustomShaders.shaders
        assertEquals(10, shaders.size)
        shaders.forEach { sh ->
            assertTrue("${sh.fileName} should be runnable", sh.isRunnablePostProcess)
            val fullGlsl = ShaderCommonGlsl.buildCustomFragmentShader(sh.preambleDecls, sh.mainBody)
            assertTrue("${sh.fileName} must declare #version 300 es", fullGlsl.startsWith("#version 300 es"))
            assertFalse("${sh.fileName} must not leak gl_FragColor", fullGlsl.contains("gl_FragColor"))
            assertFalse("${sh.fileName} must not leak texture2D", fullGlsl.contains("texture2D"))
            assertTrue("${sh.fileName} must call runCustomUserMain", fullGlsl.contains("runCustomUserMain(uv, orig)"))
            assertTrue("${sh.fileName} must map valid fallback", ShaderLibrary.hasShader(sh.fallbackEffectId))
        }

        val ripple = shaders.first { it.id == "custom_glsl_tex2d_ripple" }
        assertTrue(ripple.params.any { it.key == "amplitude" })
        assertTrue(ripple.params.any { it.key == "frequency" })
        assertTrue(ripple.params.any { it.key == "center_x" && it.paramType == EffectParamType.POINT_2D_X })
    }

    @Test
    fun `glsl transpiler flags NanoVG UBO templates and vertex shaders safely`() {
        val uboSource = """
            #version 100
            #ifdef USE_UNIFORMBUFFER
            layout(std140) uniform frag { mat3 scissorMat; };
            #endif
            void main() { gl_FragColor = vec4(1.0); }
        """.trimIndent()
        val uboRes = GlslCompatTranspiler.transpile(uboSource, "nanovg_base.frag")
        assertFalse(uboRes.isRunnablePostProcess)
        assertTrue(ShaderLibrary.hasShader(uboRes.fallbackEffectId))

        val vertRes = GlslCompatTranspiler.transpile("void main() { gl_Position = vec4(0.0); }", "sprite.vert")
        assertFalse(vertRes.isRunnablePostProcess)
    }

    @Test
    fun `am xml parser binds effects parameters keyframes and custom pack shaders`() {
        val xml = """
            <scene title="Turbulent Velocity" duration="2000">
              <media startTime="0" endTime="2000">
                <effect id="com.alightcreative.effects.tiles" label="Mirror Tiles" locallyApplied="true">
                  <property name="crop" type="float" value="1.2" />
                </effect>
                <effect id="com.alightcreative.effects.motionblur2" label="Motion Blur" blendMode="add">
                  <property name="intensity" type="float" value="80">
                    <kf t="0" v="0" e="linear" />
                    <kf t="2000" v="100" e="easeInOut" />
                  </property>
                </effect>
                <effect id="custom_ripple" label="AM Water Ripple">
                  <property name="amplitude" type="float" value="0.06" />
                </effect>
              </media>
            </scene>
        """.trimIndent()

        val parsed = AmXmlEffectParser.parseXml(
            rawXml = xml,
            fileName = "turbulent_velocity.xml",
            packId = "pack_xml_unit",
            packName = "Unit Pack",
            availableCustomShaders = BundledCustomShaders.shaders
        )
        assertEquals(1, parsed.presets.size)
        val preset = parsed.presets.first()
        assertEquals("Turbulent Velocity", preset.name)
        assertEquals(3, preset.passes.size)
        assertEquals("am_tiles", preset.passes[0].effectId)
        assertEquals("dir_motion_blur", preset.passes[1].effectId)
        assertEquals("custom_glsl_tex2d_ripple", preset.passes[2].effectId)
        assertEquals(2, preset.passes[1].keyframesForProperty(PARAM_KEY_INTENSITY).size)
        assertEquals(0f, preset.passes[1].keyframesForProperty(PARAM_KEY_INTENSITY).first().time, 0.01f)
        assertEquals(1f, preset.passes[1].keyframesForProperty(PARAM_KEY_INTENSITY).last().time, 0.01f)
    }
}

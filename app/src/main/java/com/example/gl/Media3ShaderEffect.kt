package com.example.gl

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.model.ShaderEffectPass

/**
 * Media3 GlEffect bridge that executes any Video Star GLSL ES 3.00 ShaderEffectPass
 * inside Media3's Transformer hardware export pipeline, including animated EffectKeyframe evaluation.
 */
@UnstableApi
class ShaderPassGlEffect(
    private val pass: ShaderEffectPass,
    private val sceneDurationUs: Long
) : GlEffect {

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean
    ): GlShaderProgram {
        return ShaderPassGlShaderProgram(
            useHdr = useHdr,
            pass = pass,
            sceneDurationUs = sceneDurationUs.coerceAtLeast(200_000L)
        )
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        if (!pass.isEnabled || pass.effectId == "none") return true
        val intensityKfs = pass.keyframesForProperty(com.example.model.PARAM_KEY_INTENSITY)
        val maxIntensity = if (intensityKfs.isEmpty()) pass.intensity else intensityKfs.maxOf { it.value }
        val opacityKfs = pass.paramKeyframes[com.example.model.PARAM_KEY_OPACITY].orEmpty()
        val maxOpacity = if (opacityKfs.isEmpty()) pass.passOpacity else opacityKfs.maxOf { it.value }
        return (maxIntensity * maxOpacity) <= 0.005f
    }
}

@UnstableApi
private class ShaderPassGlShaderProgram(
    useHdr: Boolean,
    private val pass: ShaderEffectPass,
    private val sceneDurationUs: Long
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private var width = 0
    private var height = 0
    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private val glProgram: GlProgram by lazy {
        try {
            val program = GlProgram(
                ShaderLibrary.VERTEX_SHADER_2D,
                ShaderLibrary.fragmentShaderFor(pass.effectId)
            )
            program.setBufferAttribute(
                "aPosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            program.setBufferAttribute(
                "aTexCoord",
                GlUtil.getTextureCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            program
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val localProgress = (presentationTimeUs.toFloat() / sceneDurationUs.toFloat()).coerceIn(0f, 1f)
            val opacity = pass.evaluateOpacityAt(localProgress)
            val intensity = (pass.evaluateIntensityAt(localProgress) * opacity).coerceIn(0f, 1f)
            val timeSec = presentationTimeUs / 1_000_000f
            val uniformParams = ShaderLibrary.extractUniformParams(pass, localProgress)

            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexture", inputTexId, 0)
            glProgram.setFloatsUniform("uTexMatrix", identityMatrix)
            glProgram.setFloatsUniform("uTexSize", floatArrayOf(width.toFloat(), height.toFloat()))
            glProgram.setFloatUniform("uTime", timeSec)
            glProgram.setFloatUniform("uIntensity", intensity)
            glProgram.setFloatUniform("uParam1", uniformParams[0])
            glProgram.setFloatUniform("uParam2", uniformParams[1])
            glProgram.setFloatUniform("uParam3", uniformParams[2])
            glProgram.setFloatsUniform("uTintColor", floatArrayOf(1f, 0.16f, 0.52f, 1f))
            glProgram.setIntUniform("uBlendMode", pass.blendMode.glIndex)
            glProgram.bindAttributesAndUniforms()

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}

package com.example.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.model.ShaderEffectPass
import com.example.model.TimelineScene
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Multi-pass OpenGL ES 3.0 renderer for live preview stages.
 * - Supports external OES video frames via SurfaceTexture
 * - Supports Bitmap frames (photos / rendered fallback bitmaps) via GL_TEXTURE_2D
 * - Renders SceneTransform + multi-pass ShaderEffectPass pipeline via ping-pong FBOs
 */
class VideoStarGlRenderer(
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit = {},
    private val onRequestRender: () -> Unit = {}
) : GLSurfaceView.Renderer {

    @Volatile
    private var activeScene: TimelineScene? = null

    @Volatile
    private var localProgress: Float = 0f

    @Volatile
    private var isVideoSource: Boolean = false

    @Volatile
    private var pendingBitmap: Bitmap? = null

    @Volatile
    private var hasNewBitmap: Boolean = false

    @Volatile
    private var frameAvailable: Boolean = false

    private var surfaceWidth: Int = 720
    private var surfaceHeight: Int = 720

    private var oesTextureId: Int = 0
    private var bitmapTextureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val oesTexMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val transformTexMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private var oesProgram: Int = 0
    private var blitProgram: Int = 0
    private val shaderProgramCache = HashMap<String, Int>()

    // Ping-pong FBOs (2 targets so N passes chain cleanly without texture feedback)
    private val fboIds = IntArray(2)
    private val fboTexIds = IntArray(2)
    private var fboWidth = 0
    private var fboHeight = 0

    private val vertexData: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_VERTICES)
            position(0)
        }

    fun updateState(scene: TimelineScene?, progress: Float, isVideo: Boolean) {
        activeScene = scene
        localProgress = progress.coerceIn(0f, 1f)
        isVideoSource = isVideo
    }

    fun setSourceBitmap(bitmap: Bitmap?) {
        pendingBitmap = bitmap
        hasNewBitmap = bitmap != null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)

        oesProgram = buildProgram(ShaderLibrary.VERTEX_SHADER_2D, ShaderLibrary.PASSTHROUGH_OES_FRAGMENT_SHADER)
        blitProgram = buildProgram(ShaderLibrary.VERTEX_SHADER_2D, ShaderLibrary.PASSTHROUGH_2D_FRAGMENT_SHADER)

        val textures = IntArray(2)
        GLES30.glGenTextures(2, textures, 0)
        oesTextureId = textures[0]
        bitmapTextureId = textures[1]

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bitmapTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener {
                frameAvailable = true
                onRequestRender()
            }
        }
        surfaceTexture = st
        onSurfaceTextureReady(st)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        allocatePingPongFbos(surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val scene = activeScene
        val bgArgb = scene?.transform?.bgColorArgb?.toInt() ?: 0xFF000000.toInt()
        val bgR = android.graphics.Color.red(bgArgb) / 255f
        val bgG = android.graphics.Color.green(bgArgb) / 255f
        val bgB = android.graphics.Color.blue(bgArgb) / 255f
        GLES30.glClearColor(bgR, bgG, bgB, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (frameAvailable) {
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(oesTexMatrix)
            frameAvailable = false
        }

        if (hasNewBitmap) {
            val bmp = pendingBitmap
            if (bmp != null && !bmp.isRecycled) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bitmapTextureId)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            }
            hasNewBitmap = false
        }

        if (fboWidth != surfaceWidth || fboHeight != surfaceHeight) {
            allocatePingPongFbos(surfaceWidth, surfaceHeight)
        }

        // Step 1: Blit input source (OES video or 2D Bitmap) + SceneTransform into FBO[0]
        buildSceneTransformMatrix(scene, transformTexMatrix)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[0])
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (isVideoSource) {
            GLES30.glUseProgram(oesProgram)
            val m = FloatArray(16)
            Matrix.multiplyMM(m, 0, oesTexMatrix, 0, transformTexMatrix, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(oesProgram, "uTexMatrix"), 1, false, m, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(oesProgram, "uOesTexture"), 0)
            drawFullscreenQuad()
        } else {
            GLES30.glUseProgram(blitProgram)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uTexMatrix"), 1, false, transformTexMatrix, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bitmapTextureId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(blitProgram, "uTexture"), 0)
            drawFullscreenQuad()
        }

        // Step 2: Run ping-pong shader passes for each active ShaderEffectPass
        var currentInputIdx = 0
        val passes: List<ShaderEffectPass> = scene?.resolvedEffectPasses() ?: emptyList()
        val timeSec = localProgress * ((scene?.effectiveDurationMs ?: 3_000L) / 1000f)

        for (pass in passes) {
            val intensity = pass.evaluateIntensityAt(localProgress)
            if (intensity <= 0.005f || pass.effectId == "none") continue
            val prog = getOrCompileShader(pass.effectId)
            if (prog == 0) continue

            val nextTargetIdx = 1 - currentInputIdx
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[nextTargetIdx])
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            GLES30.glUseProgram(prog)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(prog, "uTexMatrix"), 1, false, identityMatrix, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[currentInputIdx])
            GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uTexture"), 0)
            GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, "uTexSize"), surfaceWidth.toFloat(), surfaceHeight.toFloat())
            GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uTime"), timeSec)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uIntensity"), intensity)

            val uniformParams = ShaderLibrary.extractUniformParams(pass)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uParam1"), uniformParams[0])
            GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uParam2"), uniformParams[1])
            GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uParam3"), uniformParams[2])
            GLES30.glUniform4f(GLES30.glGetUniformLocation(prog, "uTintColor"), 1f, 0.16f, 0.52f, 1f)

            drawFullscreenQuad()
            currentInputIdx = nextTargetIdx
        }

        // Step 3: Blit final texture to default screen framebuffer (0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glUseProgram(blitProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[currentInputIdx])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(blitProgram, "uTexture"), 0)
        drawFullscreenQuad()
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
        if (oesProgram != 0) GLES30.glDeleteProgram(oesProgram)
        if (blitProgram != 0) GLES30.glDeleteProgram(blitProgram)
        shaderProgramCache.values.forEach { if (it != 0) GLES30.glDeleteProgram(it) }
        shaderProgramCache.clear()
        GLES30.glDeleteFramebuffers(2, fboIds, 0)
        GLES30.glDeleteTextures(2, fboTexIds, 0)
    }

    private fun buildSceneTransformMatrix(scene: TimelineScene?, out: FloatArray) {
        Matrix.setIdentityM(out, 0)
        if (scene == null) return
        val t = scene.transform.evaluatedAt(localProgress)
        Matrix.translateM(out, 0, 0.5f, 0.5f, 0f)
        val sx = (if (t.flipH) -1f else 1f) / t.scale.coerceAtLeast(0.15f)
        val sy = (if (t.flipV) -1f else 1f) / t.scale.coerceAtLeast(0.15f)
        Matrix.scaleM(out, 0, sx, sy, 1f)
        if (kotlin.math.abs(t.rotation) > 0.01f) {
            Matrix.rotateM(out, 0, -t.rotation, 0f, 0f, 1f)
        }
        val normOffX = -(t.offsetX / surfaceWidth.coerceAtLeast(1))
        val normOffY = (t.offsetY / surfaceHeight.coerceAtLeast(1))
        Matrix.translateM(out, 0, -0.5f + normOffX, -0.5f + normOffY, 0f)
    }

    private fun allocatePingPongFbos(width: Int, height: Int) {
        if (fboWidth > 0) {
            GLES30.glDeleteFramebuffers(2, fboIds, 0)
            GLES30.glDeleteTextures(2, fboTexIds, 0)
        }
        GLES30.glGenFramebuffers(2, fboIds, 0)
        GLES30.glGenTextures(2, fboTexIds, 0)
        for (i in 0..1) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[i])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, fboTexIds[i], 0
            )
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        fboWidth = width
        fboHeight = height
    }

    private fun getOrCompileShader(effectId: String): Int {
        shaderProgramCache[effectId]?.let { return it }
        val fragSrc = ShaderLibrary.fragmentShaderFor(effectId)
        val prog = buildProgram(ShaderLibrary.VERTEX_SHADER_2D, fragSrc)
        shaderProgramCache[effectId] = prog
        return prog
    }

    private fun drawFullscreenQuad() {
        vertexData.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, vertexData)
        vertexData.position(2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, vertexData)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) return 0
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (linked[0] == 0) {
            GLES30.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
    }
}

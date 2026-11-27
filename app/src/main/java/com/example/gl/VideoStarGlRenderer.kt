package com.example.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.engine.effects.shaders.ShaderUniformEvaluator
import com.example.engine.render.StudioRenderErrorBoundary
import com.example.model.EditorLayer
import com.example.model.SamCutoutSpec
import com.example.model.ShaderEffectPass
import com.example.model.TextLayerSpec
import com.example.model.TimelineScene
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

import android.util.Log

/**
 * Multi-pass OpenGL ES 3.0 renderer for live preview stages.
 * - Supports external OES video frames via SurfaceTexture
 * - Supports Bitmap frames (photos / extracted video poster / fallback calibration bitmaps) via GL_TEXTURE_2D
 * - Supports Dynamic Text Canvas rendering (including AM Text Count/Progress/Randomizer/Timecode)
 * - Supports AI Subject Cutout (SAM) GPU alpha matte masking with Feather/Invert
 * - Maintains pooled ping-pong FBOs, Background Copy FBO, and Temporal History FBOs (zero per-frame allocations)
 */
class VideoStarGlRenderer(
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit = {},
    private val onRequestRender: () -> Unit = {},
    private val onFirstFrameDrawn: () -> Unit = {}
) : GLSurfaceView.Renderer {

    @Volatile
    private var activeScene: TimelineScene? = null

    @Volatile
    private var localProgress: Float = 0f

    @Volatile
    private var isVideoSource: Boolean = false

    private val bitmapLock = Any()

    @Volatile
    private var pendingBitmap: Bitmap? = null

    @Volatile
    private var hasNewBitmap: Boolean = false

    @Volatile
    private var pendingSamMask: Bitmap? = null

    @Volatile
    private var hasNewSamMask: Boolean = false

    @Volatile
    private var samCutoutSpec: SamCutoutSpec = SamCutoutSpec()

    @Volatile
    private var layerText: String = ""

    @Volatile
    private var layerTextSpec: TextLayerSpec = TextLayerSpec()

    @Volatile
    private var lastRenderedTextKey: String = ""

    @Volatile
    private var parentLayerRef: EditorLayer? = null

    @Volatile
    private var frameAvailable: Boolean = false

    @Volatile
    private var isReleased: Boolean = false

    @Volatile
    private var hasReceivedVideoFrame: Boolean = false

    @Volatile
    private var videoWidth: Int = 0

    @Volatile
    private var videoHeight: Int = 0

    @Volatile
    private var bitmapWidth: Int = 0

    @Volatile
    private var bitmapHeight: Int = 0

    private var surfaceWidth: Int = 720
    private var surfaceHeight: Int = 720

    private var oesTextureId: Int = 0
    private var bitmapTextureId: Int = 0
    private var samMaskTextureId: Int = 0
    private var textTextureId: Int = 0
    private var hasUploadedSamMask: Boolean = false

    private var surfaceTexture: SurfaceTexture? = null
    private val oesTexMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val bitmapFlipYMatrix = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
        Matrix.translateM(this, 0, 0f, 1f, 0f)
        Matrix.scaleM(this, 0, 1f, -1f, 1f)
    }
    private val transformTexMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private var oesProgram: Int = 0
    private var blitProgram: Int = 0
    private var screenBlitProgram: Int = 0
    private var samMaskProgram: Int = 0
    private var hasNotifiedFirstFrame: Boolean = false
    private val shaderProgramCache = HashMap<String, Int>()

    // Pooled Ping-Pong FBOs (0..1), Background Snapshot FBO (2), and History FBOs (3..4)
    private val fboIds = IntArray(5)
    private val fboTexIds = IntArray(5)
    private var fboWidth = 0
    private var fboHeight = 0
    private var historyWriteIdx = 3
    @Volatile
    private var lastValidHistoryIdx = 3
    @Volatile
    private var hasValidHistoryFrame = false

    private var reusableTextBitmap: Bitmap? = null

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

    fun updateLayerExtras(
        samSpec: SamCutoutSpec,
        baseText: String,
        textSpec: TextLayerSpec,
        parentLayer: EditorLayer?
    ) {
        samCutoutSpec = samSpec
        layerText = baseText
        layerTextSpec = textSpec
        parentLayerRef = parentLayer
    }

    fun setSamMaskBitmap(maskBitmap: Bitmap?) {
        synchronized(bitmapLock) {
            if (maskBitmap != null && !maskBitmap.isRecycled) {
                pendingSamMask = maskBitmap
                hasNewSamMask = true
            } else {
                pendingSamMask = null
                hasNewSamMask = true
            }
        }
    }

    fun setSourceDimensions(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            videoWidth = width
            videoHeight = height
        }
    }

    fun resetVideoSourceState() {
        hasReceivedVideoFrame = false
        frameAvailable = false
        videoWidth = 0
        videoHeight = 0
    }

    fun setSourceBitmap(bitmap: Bitmap?) {
        synchronized(bitmapLock) {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmapWidth = bitmap.width
                bitmapHeight = bitmap.height
                pendingBitmap = bitmap
                hasNewBitmap = true
            }
        }
    }

    fun onContextCreated() {
        onSurfaceCreated(null, null)
    }

    fun onViewportChanged(width: Int, height: Int) {
        onSurfaceChanged(null, width, height)
    }

    fun onDraw() {
        onDrawFrame(null)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        isReleased = false
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        hasNotifiedFirstFrame = false
        hasValidHistoryFrame = false

        shaderProgramCache.clear()
        oesProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.PASSTHROUGH_OES_FRAGMENT_SHADER)
        if (oesProgram == 0) {
            oesProgram = buildProgram(
                ShaderLibrary.SOURCE_VERTEX_SHADER_COMPAT,
                ShaderLibrary.PASSTHROUGH_OES_FRAGMENT_SHADER_COMPAT
            )
        }
        blitProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.PASSTHROUGH_2D_FRAGMENT_SHADER)
        if (blitProgram == 0) {
            blitProgram = buildProgram(
                ShaderLibrary.SOURCE_VERTEX_SHADER_COMPAT,
                ShaderLibrary.PASSTHROUGH_2D_FRAGMENT_SHADER_COMPAT
            )
        }
        screenBlitProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.PASSTHROUGH_PREMULT_2D_FRAGMENT_SHADER)
        if (screenBlitProgram == 0) {
            screenBlitProgram = buildProgram(
                ShaderLibrary.SOURCE_VERTEX_SHADER_COMPAT,
                ShaderLibrary.PASSTHROUGH_PREMULT_2D_FRAGMENT_SHADER_COMPAT
            )
        }
        if (screenBlitProgram == 0) screenBlitProgram = blitProgram
        samMaskProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.SAM_MASK_APPLY_FRAGMENT_SHADER)

        val textures = IntArray(4)
        GLES30.glGenTextures(4, textures, 0)
        oesTextureId = textures[0]
        bitmapTextureId = textures[1]
        samMaskTextureId = textures[2]
        textTextureId = textures[3]

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        for (texId in intArrayOf(bitmapTextureId, samMaskTextureId, textTextureId)) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }

        surfaceTexture?.let { old ->
            runCatching { old.setOnFrameAvailableListener(null) }
            runCatching { old.release() }
        }
        hasReceivedVideoFrame = false
        frameAvailable = false

        val st = SurfaceTexture(oesTextureId).apply {
            setOnFrameAvailableListener {
                if (!isReleased) {
                    frameAvailable = true
                    hasReceivedVideoFrame = true
                    onRequestRender()
                }
            }
        }
        surfaceTexture = st

        val initialBmp = synchronized(bitmapLock) {
            val b = pendingBitmap
            if (b != null && !b.isRecycled) {
                hasNewBitmap = false
                b
            } else null
        }
        if (initialBmp != null) {
            uploadBitmapTexture(bitmapTextureId, initialBmp)
        } else {
            val fallbackGrid = ProceduralTestGridFactory.createTestGridBitmap(256, 256)
            uploadBitmapTexture(bitmapTextureId, fallbackGrid)
            fallbackGrid.recycle()
        }

        onSurfaceTextureReady(st)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        allocatePooledFbos(surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (isReleased || blitProgram == 0) return
        try {
            drawFrameInternal()
        } catch (t: Throwable) {
            StudioRenderErrorBoundary.recordSuppressedError("gl_onDrawFrame", t)
            runCatching { blitLastValidFrameFallback() }
        }
    }

    private fun drawFrameInternal() {
        val scene = activeScene
        val bgArgb = scene?.transform?.bgColorArgb?.toInt() ?: 0xFF000000.toInt()
        val bgR = Color.red(bgArgb) / 255f
        val bgG = Color.green(bgArgb) / 255f
        val bgB = Color.blue(bgArgb) / 255f
        val bgA = Color.alpha(bgArgb) / 255f

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(bgR, bgG, bgB, bgA)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (frameAvailable) {
            surfaceTexture?.let { st ->
                runCatching {
                    st.updateTexImage()
                    st.getTransformMatrix(oesTexMatrix)
                }
            }
            frameAvailable = false
        }

        val nextBmpToUpload: Bitmap? = synchronized(bitmapLock) {
            if (hasNewBitmap) {
                hasNewBitmap = false
                pendingBitmap?.takeIf { !it.isRecycled }
            } else null
        }
        if (nextBmpToUpload != null) {
            uploadBitmapTexture(bitmapTextureId, nextBmpToUpload)
        }

        val (maskUpdatePending, nextMaskToUpload) = synchronized(bitmapLock) {
            if (hasNewSamMask) {
                hasNewSamMask = false
                true to pendingSamMask?.takeIf { !it.isRecycled }
            } else {
                false to null
            }
        }
        if (maskUpdatePending) {
            if (nextMaskToUpload != null) {
                runCatching {
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, samMaskTextureId)
                    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, nextMaskToUpload, 0)
                    hasUploadedSamMask = true
                }
            } else {
                hasUploadedSamMask = false
            }
        }

        val (targetFboW, targetFboH) = computeFboSize(surfaceWidth, surfaceHeight)
        if (fboWidth != targetFboW || fboHeight != targetFboH) {
            allocatePooledFbos(surfaceWidth, surfaceHeight)
        }

        val passes: List<ShaderEffectPass> = scene?.resolvedEffectPasses() ?: emptyList()
        val durationSec = ((scene?.effectiveDurationMs ?: 3_000L) / 1000f).coerceAtLeast(0.2f)

        // Check if Time Quantization is active
        val quantPass = passes.firstOrNull { it.isEnabled && it.effectId == "am_time_quantization" }
        val (evalProgress, evalTimeSec) = if (quantPass != null) {
            ShaderUniformEvaluator.computeQuantizedProgress(quantPass, localProgress, durationSec)
        } else {
            localProgress to (localProgress * durationSec)
        }

        // Check if AM Text generator effect overrides the texture source
        val hasTextEffect = passes.any {
            it.isEnabled && it.effectId in setOf("am_text_count", "am_text_progress", "am_text_randomizer", "am_text_timecode")
        }
        val useDynamicTextBitmap = hasTextEffect || (layerText.isNotBlank() && scene?.clip == null && !isVideoSource)
        if (useDynamicTextBitmap) {
            val renderedText = ShaderUniformEvaluator.formatTextForEffects(
                baseText = layerText,
                passes = passes,
                progress = evalProgress,
                durationSec = durationSec
            )
            val cacheKey = "${renderedText}_${layerTextSpec.textColorArgb}_${layerTextSpec.fontSizeSp}_${surfaceWidth}x${surfaceHeight}"
            if (cacheKey != lastRenderedTextKey) {
                uploadDynamicTextTexture(renderedText, layerTextSpec, bgArgb)
                lastRenderedTextKey = cacheKey
            }
        }

        val useLiveOes = isVideoSource &&
            hasReceivedVideoFrame &&
            oesProgram != 0 &&
            !useDynamicTextBitmap
        val srcW = when {
            useDynamicTextBitmap -> surfaceWidth
            useLiveOes -> videoWidth.takeIf { it > 0 } ?: bitmapWidth.coerceAtLeast(surfaceWidth)
            else -> bitmapWidth.coerceAtLeast(1)
        }
        val srcH = when {
            useDynamicTextBitmap -> surfaceHeight
            useLiveOes -> videoHeight.takeIf { it > 0 } ?: bitmapHeight.coerceAtLeast(surfaceHeight)
            else -> bitmapHeight.coerceAtLeast(1)
        }

        // Step 1: Blit input source + AspectFit/Crop + SceneTransform (+ optional Parenting Helper) into FBO[0]
        buildSceneTransformMatrix(scene, passes, evalProgress, transformTexMatrix, srcW, srcH)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[0])
        GLES30.glViewport(0, 0, fboWidth, fboHeight)
        GLES30.glClearColor(bgR, bgG, bgB, bgA)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (useLiveOes) {
            GLES30.glUseProgram(oesProgram)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(oesProgram, "uTexMatrix"), 1, false, transformTexMatrix, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(oesProgram, "uSourceTexMatrix"), 1, false, oesTexMatrix, 0)
            GLES30.glUniform4f(GLES30.glGetUniformLocation(oesProgram, "uBgColor"), bgR, bgG, bgB, bgA)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(oesProgram, "uOesTexture"), 0)
            drawFullscreenQuad()
        } else {
            GLES30.glUseProgram(blitProgram)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uTexMatrix"), 1, false, transformTexMatrix, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uSourceTexMatrix"), 1, false, bitmapFlipYMatrix, 0)
            GLES30.glUniform4f(GLES30.glGetUniformLocation(blitProgram, "uBgColor"), bgR, bgG, bgB, bgA)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (useDynamicTextBitmap) textTextureId else bitmapTextureId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(blitProgram, "uTexture"), 0)
            drawFullscreenQuad()
        }

        var currentInputIdx = 0

        // Optional Step 1.5: Apply AI Subject Cutout (SAM) GPU alpha matte before effects
        val samSpec = samCutoutSpec
        if (samSpec.enabled && hasUploadedSamMask && samMaskProgram != 0) {
            val nextTarget = 1 - currentInputIdx
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[nextTarget])
            GLES30.glViewport(0, 0, fboWidth, fboHeight)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            GLES30.glUseProgram(samMaskProgram)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(samMaskProgram, "uTexMatrix"), 1, false, identityMatrix, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(samMaskProgram, "uSourceTexMatrix"), 1, false, identityMatrix, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[currentInputIdx])
            GLES30.glUniform1i(GLES30.glGetUniformLocation(samMaskProgram, "uTexture"), 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, samMaskTextureId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(samMaskProgram, "uMaskTex"), 1)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(samMaskProgram, "uFeather"), samSpec.feather.coerceIn(0f, 1f))
            GLES30.glUniform1i(GLES30.glGetUniformLocation(samMaskProgram, "uInvertMask"), if (samSpec.invert) 1 else 0)
            drawFullscreenQuad()
            currentInputIdx = nextTarget
        }

        // Snapshot source into Background Copy FBO[2] for Copy/Magnify Background shaders
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[2])
        GLES30.glViewport(0, 0, fboWidth, fboHeight)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(blitProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uSourceTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(blitProgram, "uBgColor"), 0f, 0f, 0f, 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[currentInputIdx])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(blitProgram, "uTexture"), 0)
        drawFullscreenQuad()

        val historyReadIdx = if (historyWriteIdx == 3) 4 else 3

        // Step 2: Ping-pong shader passes for each active ShaderEffectPass (isolated per pass)
        for (pass in passes) {
            if (!pass.isEnabled || pass.effectId == "none") continue
            try {
                val evaluated = ShaderUniformEvaluator.evaluate(
                    pass = pass,
                    progress = localProgress,
                    sceneDurationSec = durationSec,
                    quantizedProgressOverride = evalProgress,
                    quantizedTimeOverrideSec = evalTimeSec
                )
                if (evaluated.intensity <= 0.005f) continue
                val prog = getOrCompileShader(pass.effectId)
                if (prog == 0) continue

                val nextTargetIdx = 1 - currentInputIdx
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[nextTargetIdx])
                GLES30.glViewport(0, 0, fboWidth, fboHeight)
                GLES30.glClearColor(0f, 0f, 0f, 0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

                GLES30.glUseProgram(prog)
                GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(prog, "uTexMatrix"), 1, false, identityMatrix, 0)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[currentInputIdx])
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uTexture"), 0)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[2])
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uBackgroundTex"), 1)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[historyReadIdx])
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uHistoryTex"), 2)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (hasUploadedSamMask) samMaskTextureId else fboTexIds[currentInputIdx])
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uMaskTex"), 3)

                GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, "uTexSize"), fboWidth.toFloat(), fboHeight.toFloat())
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uTime"), evaluated.effectiveTimeSec)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uDuration"), durationSec)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uIntensity"), evaluated.intensity)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uProgress"), evaluated.effectiveProgress)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uParam1"), evaluated.legacyParam1)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uParam2"), evaluated.legacyParam2)
                GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uParam3"), evaluated.legacyParam3)

                val paramsLoc = GLES30.glGetUniformLocation(prog, "uParams")
                if (paramsLoc >= 0) GLES30.glUniform1fv(paramsLoc, 12, evaluated.params, 0)
                val colorsLoc = GLES30.glGetUniformLocation(prog, "uColors")
                if (colorsLoc >= 0) GLES30.glUniform4fv(colorsLoc, 3, evaluated.colors, 0)
                val modesLoc = GLES30.glGetUniformLocation(prog, "uModes")
                if (modesLoc >= 0) GLES30.glUniform1iv(modesLoc, 4, evaluated.modes, 0)

                GLES30.glUniform4f(GLES30.glGetUniformLocation(prog, "uTintColor"), 1f, 0.16f, 0.52f, 1f)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uBlendMode"), pass.blendMode.glIndex)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uHasMask"), if (hasUploadedSamMask) 1 else 0)
                GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uHasBg"), 1)

                drawFullscreenQuad()
                currentInputIdx = nextTargetIdx
            } catch (t: Throwable) {
                StudioRenderErrorBoundary.recordSuppressedError("gl_pass_${pass.effectId}", t)
                // Preserve currentInputIdx cleanly so subsequent passes and final blit succeed
            }
        }

        // Save result into temporal history buffer for Echo Keyframes & Luma Stamper
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[historyWriteIdx])
        GLES30.glViewport(0, 0, fboWidth, fboHeight)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(blitProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(blitProgram, "uSourceTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(blitProgram, "uBgColor"), 0f, 0f, 0f, 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[currentInputIdx])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(blitProgram, "uTexture"), 0)
        drawFullscreenQuad()
        lastValidHistoryIdx = historyWriteIdx
        hasValidHistoryFrame = true
        historyWriteIdx = historyReadIdx

        // Step 3: Blit final texture to default screen framebuffer (0) with premultiplied alpha for TextureView
        val outProg = screenBlitProgram.takeIf { it != 0 } ?: blitProgram
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        if (bgA < 0.99f) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        GLES30.glUseProgram(outProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(outProg, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(outProg, "uSourceTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(outProg, "uBgColor"), 0f, 0f, 0f, 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[currentInputIdx])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(outProg, "uTexture"), 0)
        drawFullscreenQuad()
        GLES30.glDisable(GLES30.GL_BLEND)
        if (!hasNotifiedFirstFrame) {
            hasNotifiedFirstFrame = true
            onFirstFrameDrawn()
        }
    }

    private fun blitLastValidFrameFallback() {
        val outProg = screenBlitProgram.takeIf { it != 0 } ?: blitProgram
        if (isReleased || outProg == 0) return
        val useHistoryTex = hasValidHistoryFrame && fboWidth > 0 && fboTexIds[lastValidHistoryIdx] != 0
        val texId = if (useHistoryTex) fboTexIds[lastValidHistoryIdx] else bitmapTextureId
        if (texId == 0) return

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth.coerceAtLeast(1), surfaceHeight.coerceAtLeast(1))
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(outProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(outProg, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(outProg, "uSourceTexMatrix"),
            1,
            false,
            if (useHistoryTex) identityMatrix else bitmapFlipYMatrix,
            0
        )
        GLES30.glUniform4f(GLES30.glGetUniformLocation(outProg, "uBgColor"), 0f, 0f, 0f, 1f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(outProg, "uTexture"), 0)
        drawFullscreenQuad()
    }

    fun release() {
        isReleased = true
        frameAvailable = false
        hasValidHistoryFrame = false
        synchronized(bitmapLock) {
            pendingBitmap = null
            pendingSamMask = null
            hasNewBitmap = false
            hasNewSamMask = false
        }
        surfaceTexture?.let { st ->
            runCatching { st.setOnFrameAvailableListener(null) }
            runCatching { st.release() }
        }
        surfaceTexture = null
        val texs = intArrayOf(oesTextureId, bitmapTextureId, samMaskTextureId, textTextureId)
        if (texs.any { it != 0 }) {
            GLES30.glDeleteTextures(4, texs, 0)
            oesTextureId = 0
            bitmapTextureId = 0
            samMaskTextureId = 0
            textTextureId = 0
        }
        if (oesProgram != 0) { GLES30.glDeleteProgram(oesProgram); oesProgram = 0 }
        if (screenBlitProgram != 0 && screenBlitProgram != blitProgram) { GLES30.glDeleteProgram(screenBlitProgram) }
        screenBlitProgram = 0
        if (blitProgram != 0) { GLES30.glDeleteProgram(blitProgram); blitProgram = 0 }
        if (samMaskProgram != 0) { GLES30.glDeleteProgram(samMaskProgram); samMaskProgram = 0 }
        shaderProgramCache.values.forEach { if (it != 0) GLES30.glDeleteProgram(it) }
        shaderProgramCache.clear()
        if (fboWidth > 0) {
            GLES30.glDeleteFramebuffers(5, fboIds, 0)
            GLES30.glDeleteTextures(5, fboTexIds, 0)
            fboWidth = 0
            fboHeight = 0
        }
        reusableTextBitmap?.recycle()
        reusableTextBitmap = null
    }

    private fun uploadBitmapTexture(texId: Int, bmp: Bitmap) {
        if (bmp.isRecycled) return
        val safeBmp = if (bmp.config != Bitmap.Config.ARGB_8888) {
            bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp
        } else {
            bmp
        }
        if (safeBmp.isRecycled) return
        bitmapWidth = safeBmp.width.coerceAtLeast(1)
        bitmapHeight = safeBmp.height.coerceAtLeast(1)
        runCatching {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, safeBmp, 0)
        }
        if (safeBmp !== bmp && !safeBmp.isRecycled) {
            safeBmp.recycle()
        }
    }

    private fun uploadDynamicTextTexture(text: String, spec: TextLayerSpec, bgArgb: Int) {
        val w = surfaceWidth.coerceIn(256, 1024)
        val h = surfaceHeight.coerceIn(256, 1024)
        val bmp = reusableTextBitmap?.takeIf { !it.isRecycled && it.width == w && it.height == h }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                reusableTextBitmap?.recycle()
                reusableTextBitmap = it
            }
        bmp.eraseColor(if (Color.alpha(bgArgb) == 0) Color.TRANSPARENT else bgArgb)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.textColorArgb.toInt()
            textSize = (spec.fontSizeSp * (w / 240f)).coerceIn(28f, 160f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            com.example.engine.packs.CustomFontRegistry.resolveAndroidTypeface(spec.fontStyleId)?.let { tf ->
                typeface = tf
            }
            setShadowLayer(spec.shadowRadius.coerceAtLeast(4f), 0f, 4f, Color.BLACK)
        }
        val fm = paint.fontMetrics
        val baseline = h / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, w / 2f, baseline, paint)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textTextureId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
    }

    private fun buildSceneTransformMatrix(
        scene: TimelineScene?,
        passes: List<ShaderEffectPass>,
        progress: Float,
        out: FloatArray,
        srcW: Int,
        srcH: Int
    ) {
        Matrix.setIdentityM(out, 0)
        if (scene == null) return
        val t = scene.transform.evaluatedAt(progress)

        var parentOffX = 0f
        var parentOffY = 0f
        var parentScale = 1f
        var parentRot = 0f
        val parent = parentLayerRef
        val parentHelper = passes.firstOrNull { it.isEnabled && it.effectId == "am_parenting_helper" }
        if (parent != null && parentHelper != null) {
            val fPos = parentHelper.evaluateParamAt("follow_pos", progress, 1f, 0f, 2f)
            val fScale = parentHelper.evaluateParamAt("follow_scale", progress, 1f, 0f, 2f)
            val fRot = parentHelper.evaluateParamAt("follow_rot", progress, 1f, 0f, 2f)
            parentOffX = parent.offsetX * fPos
            parentOffY = parent.offsetY * fPos
            parentScale = 1f + (parent.scale - 1f) * fScale
            parentRot = parent.rotation * fRot
        }

        var aspectScaleX = 1f
        var aspectScaleY = 1f
        if (srcW > 0 && srcH > 0 && surfaceWidth > 0 && surfaceHeight > 0) {
            val srcAspect = srcW.toFloat() / srcH.toFloat()
            val dstAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
            if (abs(srcAspect - dstAspect) > 0.01f) {
                if (t.isFitMode) {
                    if (srcAspect > dstAspect) aspectScaleY = srcAspect / dstAspect
                    else aspectScaleX = dstAspect / srcAspect
                } else {
                    if (srcAspect > dstAspect) aspectScaleX = dstAspect / srcAspect
                    else aspectScaleY = srcAspect / dstAspect
                }
            }
        }

        val totalScale = (t.scale * parentScale).coerceAtLeast(0.15f)
        val totalRot = t.rotation + parentRot
        Matrix.translateM(out, 0, 0.5f, 0.5f, 0f)
        val sx = ((if (t.flipH) -1f else 1f) * aspectScaleX) / totalScale
        val sy = ((if (t.flipV) -1f else 1f) * aspectScaleY) / totalScale
        Matrix.scaleM(out, 0, sx, sy, 1f)
        if (abs(totalRot) > 0.01f) {
            Matrix.rotateM(out, 0, -totalRot, 0f, 0f, 1f)
        }
        val normOffX = -((t.offsetX + parentOffX) / surfaceWidth.coerceAtLeast(1))
        val normOffY = ((t.offsetY + parentOffY) / surfaceHeight.coerceAtLeast(1))
        Matrix.translateM(out, 0, -0.5f + normOffX, -0.5f + normOffY, 0f)
    }

    private fun computeFboSize(surfaceW: Int, surfaceH: Int): Pair<Int, Int> {
        val maxFboDim = 960
        val w = surfaceW.coerceAtLeast(1)
        val h = surfaceH.coerceAtLeast(1)
        val maxSide = maxOf(w, h)
        if (maxSide <= maxFboDim) return w to h
        val scale = maxFboDim.toFloat() / maxSide.toFloat()
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    private fun allocatePooledFbos(width: Int, height: Int) {
        val (targetW, targetH) = computeFboSize(width, height)
        if (fboWidth > 0) {
            GLES30.glDeleteFramebuffers(5, fboIds, 0)
            GLES30.glDeleteTextures(5, fboTexIds, 0)
        }
        GLES30.glGenFramebuffers(5, fboIds, 0)
        GLES30.glGenTextures(5, fboTexIds, 0)
        for (i in 0 until 5) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexIds[i])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                targetW, targetH, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
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
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        fboWidth = targetW
        fboHeight = targetH
    }

    private fun getOrCompileShader(effectId: String): Int {
        shaderProgramCache[effectId]?.takeIf { it != 0 }?.let { return it }
        val fragSrc = ShaderLibrary.fragmentShaderFor(effectId)
        var prog = buildProgram(ShaderLibrary.VERTEX_SHADER_2D, fragSrc)
        if (prog == 0 && com.example.engine.packs.CustomPackRepository.hasCustomEffect(effectId)) {
            prog = buildProgram(ShaderLibrary.VERTEX_SHADER_2D, ShaderLibrary.fallbackShaderFor(effectId))
        }
        if (prog != 0) {
            shaderProgramCache[effectId] = prog
        }
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
        GLES30.glBindAttribLocation(program, 0, "aPosition")
        GLES30.glBindAttribLocation(program, 1, "aTexCoord")
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
            val info = GLES30.glGetShaderInfoLog(shader)
            Log.w("VideoStarGlRenderer", "Shader compile failed ($type): $info")
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

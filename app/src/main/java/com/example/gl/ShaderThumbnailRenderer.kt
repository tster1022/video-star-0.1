package com.example.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.data.MediaImportHelper
import com.example.engine.effects.shaders.ShaderUniformEvaluator
import com.example.engine.render.StudioRenderErrorBoundary
import com.example.engine.render.StudioRenderMemoryCoordinator
import com.example.model.EffectCatalog
import com.example.model.MediaClip
import com.example.model.ShaderEffectPass
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors

/**
 * Offscreen EGL14 Pbuffer thumbnail renderer for all 145 GPU shaders.
 * Renders real GLSL previews over the active video/photo track frame (or procedural grid),
 * with byte-bounded LRU caching and automatic fallback under Robolectric / Compose Inspection.
 */
object ShaderThumbnailRenderer {

    private const val THUMB_SIZE = 160

    private val glDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ShaderThumbGL").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    private val renderMutex = Mutex()
    private val thumbCache = object : LruCache<String, ImageBitmap>(
        StudioRenderMemoryCoordinator.MAX_SHADER_THUMB_CACHE_KB
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return StudioRenderMemoryCoordinator.bitmapSizeKb(value.width * value.height * 4)
        }
    }
    private val sourceBmpCache = object : LruCache<String, Bitmap>(
        StudioRenderMemoryCoordinator.MAX_SHADER_SOURCE_CACHE_KB
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            if (value.isRecycled) return 1
            return StudioRenderMemoryCoordinator.bitmapSizeKb(value.allocationByteCount)
        }
    }

    init {
        StudioRenderMemoryCoordinator.registerTrimListener("shader_thumbnail_caches") { aggressive ->
            trimCaches(aggressive)
        }
    }

    fun trimCaches(aggressive: Boolean = false) {
        if (aggressive) {
            thumbCache.evictAll()
            sourceBmpCache.evictAll()
            currentUploadedSourceKey = null
        } else {
            thumbCache.trimToSize(StudioRenderMemoryCoordinator.MAX_SHADER_THUMB_CACHE_KB / 4)
            sourceBmpCache.trimToSize(StudioRenderMemoryCoordinator.MAX_SHADER_SOURCE_CACHE_KB / 2)
        }
    }

    // Owned exclusively by glDispatcher thread
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglInitFailed = false

    private var srcTexId = 0
    private var preparedFboId = 0
    private var preparedTexId = 0
    private var currentUploadedSourceKey: String? = null

    private var flipProgram = 0
    private val shaderPrograms = HashMap<String, Int>()
    private val QUAD_VERTICES = floatArrayOf(
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f
    )
    private val quadBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_VERTICES)
            position(0)
        }

    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val flipYMatrix = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
        Matrix.translateM(this, 0, 0f, 1f, 0f)
        Matrix.scaleM(this, 0, 1f, -1f, 1f)
    }

    private val readBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(THUMB_SIZE * THUMB_SIZE * 4).order(ByteOrder.nativeOrder())
    private val rowTemp = IntArray(THUMB_SIZE)

    suspend fun renderThumbnail(
        context: Context,
        effectId: String,
        clip: MediaClip?
    ): ImageBitmap? {
        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true) || eglInitFailed) {
            return null
        }
        val canonicalId = EffectCatalog.resolveCanonicalId(effectId)
        val trimKey = (clip as? MediaClip.Video)?.startTrimMs ?: 0L
        val sourceKey = clip?.let { "${it.id}_${it.uriString}_$trimKey" } ?: "procedural_grid"
        val cacheKey = "$sourceKey|$canonicalId"

        thumbCache.get(cacheKey)?.let { return it }

        return renderMutex.withLock {
            thumbCache.get(cacheKey)?.let { return@withLock it }
            val sourceBmp = getOrLoadSourceBitmap(context, clip, sourceKey)

            withContext(glDispatcher) {
                thumbCache.get(cacheKey)?.let { return@withContext it }
                StudioRenderErrorBoundary.safeEvaluate("shader_thumb_$canonicalId", null) {
                    if (!ensureEglInitialized()) return@safeEvaluate null
                    uploadSourceAndPrepareFbo(sourceBmp, sourceKey)
                    val rendered = renderPassToImageBitmap(canonicalId)
                    if (rendered != null) {
                        thumbCache.put(cacheKey, rendered)
                    }
                    rendered
                }
            }
        }
    }

    private suspend fun getOrLoadSourceBitmap(
        context: Context,
        clip: MediaClip?,
        sourceKey: String
    ): Bitmap {
        sourceBmpCache.get(sourceKey)?.takeIf { !it.isRecycled }?.let { return it }

        val loaded = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val uriStr = clip?.uriString
                val raw = if (!uriStr.isNullOrBlank()) {
                    if (clip is MediaClip.Video) {
                        MediaImportHelper.extractVideoFrame(
                            uriString = uriStr,
                            timeMs = clip.startTrimMs,
                            context = context,
                            maxDimPx = THUMB_SIZE
                        )
                    } else {
                        MediaImportHelper.decodeSampledBitmap(
                            uriString = uriStr,
                            context = context,
                            maxDimPx = THUMB_SIZE
                        )
                    }
                } else null

                if (raw != null && !raw.isRecycled) {
                    val scaled = Bitmap.createScaledBitmap(raw, THUMB_SIZE, THUMB_SIZE, true)
                    val safe = if (scaled.config != Bitmap.Config.ARGB_8888) {
                        scaled.copy(Bitmap.Config.ARGB_8888, false) ?: scaled
                    } else scaled
                    if (scaled !== raw && !raw.isRecycled && clip !is MediaClip.Video) {
                        raw.recycle()
                    }
                    if (safe !== scaled && !scaled.isRecycled) {
                        scaled.recycle()
                    }
                    safe
                } else {
                    ProceduralTestGridFactory.createTestGridBitmap(THUMB_SIZE, THUMB_SIZE)
                }
            }.getOrElse {
                ProceduralTestGridFactory.createTestGridBitmap(THUMB_SIZE, THUMB_SIZE)
            }
        }
        sourceBmpCache.put(sourceKey, loaded)
        return loaded
    }

    private fun ensureEglInitialized(): Boolean {
        if (eglContext != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            return true
        }
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            eglInitFailed = true
            return false
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            eglInitFailed = true
            return false
        }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT_KHR
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        var ok = EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0)
        if (!ok || numConfigs[0] == 0) {
            val es2Attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            ok = EGL14.eglChooseConfig(display, es2Attribs, 0, configs, 0, 1, numConfigs, 0)
        }
        val config = configs.firstOrNull()
        if (!ok || config == null) {
            eglInitFailed = true
            return false
        }

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        var ctx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) {
            val ctx2 = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            ctx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctx2, 0)
        }
        if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) {
            eglInitFailed = true
            return false
        }

        val pbAttribs = intArrayOf(
            EGL14.EGL_WIDTH, THUMB_SIZE,
            EGL14.EGL_HEIGHT, THUMB_SIZE,
            EGL14.EGL_NONE
        )
        val surf = EGL14.eglCreatePbufferSurface(display, config, pbAttribs, 0)
        if (surf == null || surf == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(display, ctx)
            eglInitFailed = true
            return false
        }

        if (!EGL14.eglMakeCurrent(display, surf, surf, ctx)) {
            EGL14.eglDestroySurface(display, surf)
            EGL14.eglDestroyContext(display, ctx)
            eglInitFailed = true
            return false
        }

        eglDisplay = display
        eglContext = ctx
        eglSurface = surf

        val texs = IntArray(2)
        GLES30.glGenTextures(2, texs, 0)
        srcTexId = texs[0]
        preparedTexId = texs[1]

        for (t in texs) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, preparedTexId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            THUMB_SIZE, THUMB_SIZE, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )

        val fbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        preparedFboId = fbos[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, preparedFboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, preparedTexId, 0
        )
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        flipProgram = buildProgram(ShaderLibrary.SOURCE_VERTEX_SHADER_2D, ShaderLibrary.PASSTHROUGH_2D_FRAGMENT_SHADER)
        if (flipProgram == 0) {
            flipProgram = buildProgram(
                ShaderLibrary.SOURCE_VERTEX_SHADER_COMPAT,
                ShaderLibrary.PASSTHROUGH_2D_FRAGMENT_SHADER_COMPAT
            )
        }
        return flipProgram != 0
    }

    private fun uploadSourceAndPrepareFbo(sourceBmp: Bitmap, sourceKey: String) {
        if (currentUploadedSourceKey == sourceKey) return
        val safe = if (sourceBmp.config != Bitmap.Config.ARGB_8888) {
            sourceBmp.copy(Bitmap.Config.ARGB_8888, false) ?: sourceBmp
        } else sourceBmp

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTexId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, safe, 0)
        if (safe !== sourceBmp && !safe.isRecycled) safe.recycle()

        // Flip source bitmap into preparedTexId so shader effects match VideoStarGlRenderer UV orientation
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, preparedFboId)
        GLES30.glViewport(0, 0, THUMB_SIZE, THUMB_SIZE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(flipProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(flipProgram, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(flipProgram, "uSourceTexMatrix"), 1, false, flipYMatrix, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(flipProgram, "uBgColor"), 0f, 0f, 0f, 1f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTexId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(flipProgram, "uTexture"), 0)
        drawQuad()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        currentUploadedSourceKey = sourceKey
    }

    private fun renderPassToImageBitmap(effectId: String): ImageBitmap? {
        val item = EffectCatalog.findById(effectId) ?: return null
        val existingProg = shaderPrograms[effectId]
        val prog = if (existingProg != null && existingProg != 0) {
            existingProg
        } else {
            val primary = buildProgram(ShaderLibrary.VERTEX_SHADER_2D, ShaderLibrary.fragmentShaderFor(effectId))
            val resolved = if (primary != 0) primary else {
                buildProgram(ShaderLibrary.VERTEX_SHADER_2D, ShaderLibrary.fallbackShaderFor(effectId))
            }
            if (resolved != 0) {
                shaderPrograms[effectId] = resolved
            }
            resolved
        }
        if (prog == 0) return null

        val previewPass = ShaderEffectPass(
            id = "thumb_$effectId",
            effectId = item.id,
            effectName = item.name,
            category = item.category,
            intensity = item.defaultIntensity.coerceAtLeast(0.55f),
            params = item.defaultParamMap(),
            colorParams = item.defaultColorMap(),
            textParams = item.defaultTextMap()
        )
        val evaluated = ShaderUniformEvaluator.evaluate(
            pass = previewPass,
            progress = 0.42f,
            sceneDurationSec = 3.0f
        )

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, THUMB_SIZE, THUMB_SIZE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0.05f, 0.06f, 0.09f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(prog, "uTexMatrix"), 1, false, identityMatrix, 0)

        for (unit in 0..3) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, preparedTexId)
        }
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uTexture"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uBackgroundTex"), 1)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uHistoryTex"), 2)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uMaskTex"), 3)

        GLES30.glUniform2f(GLES30.glGetUniformLocation(prog, "uTexSize"), THUMB_SIZE.toFloat(), THUMB_SIZE.toFloat())
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uTime"), evaluated.effectiveTimeSec)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(prog, "uDuration"), 3.0f)
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
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uBlendMode"), previewPass.blendMode.glIndex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uHasMask"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(prog, "uHasBg"), 1)

        drawQuad()
        GLES30.glFinish()

        readBuffer.rewind()
        GLES30.glReadPixels(0, 0, THUMB_SIZE, THUMB_SIZE, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readBuffer)
        readBuffer.rewind()

        val bmp = Bitmap.createBitmap(THUMB_SIZE, THUMB_SIZE, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(readBuffer)

        // Flip GL bottom-up pixel rows into Android top-down orientation
        val pixels = IntArray(THUMB_SIZE * THUMB_SIZE)
        bmp.getPixels(pixels, 0, THUMB_SIZE, 0, 0, THUMB_SIZE, THUMB_SIZE)
        for (y in 0 until THUMB_SIZE / 2) {
            val topOff = y * THUMB_SIZE
            val botOff = (THUMB_SIZE - 1 - y) * THUMB_SIZE
            System.arraycopy(pixels, topOff, rowTemp, 0, THUMB_SIZE)
            System.arraycopy(pixels, botOff, pixels, topOff, THUMB_SIZE)
            System.arraycopy(rowTemp, 0, pixels, botOff, THUMB_SIZE)
        }
        bmp.setPixels(pixels, 0, THUMB_SIZE, 0, 0, THUMB_SIZE, THUMB_SIZE)
        return bmp.asImageBitmap()
    }

    private fun drawQuad() {
        quadBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, quadBuffer)
        quadBuffer.position(2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, quadBuffer)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) return 0
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glBindAttribLocation(prog, 0, "aPosition")
        GLES30.glBindAttribLocation(prog, 1, "aTexCoord")
        GLES30.glLinkProgram(prog)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linked, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (linked[0] == 0) {
            GLES30.glDeleteProgram(prog)
            return 0
        }
        return prog
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
}

@Composable
fun rememberShaderThumbnail(
    effectId: String,
    clip: MediaClip?
): ImageBitmap? {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val trimKey = (clip as? MediaClip.Video)?.startTrimMs ?: 0L
    val clipKey = clip?.let { "${it.id}_${it.uriString}_$trimKey" } ?: "procedural"
    val state by produceState<ImageBitmap?>(initialValue = null, key1 = effectId, key2 = clipKey) {
        if (isInspection) {
            value = null
            return@produceState
        }
        value = ShaderThumbnailRenderer.renderThumbnail(context, effectId, clip)
    }
    return state
}

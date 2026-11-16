package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

data class ImportedMediaInfo(
    val localUriString: String,
    val durationMs: Long,
    val sourceFps: Int,
    val isVideo: Boolean
)

data class ImportedAudioInfo(
    val localUriString: String,
    val title: String,
    val durationMs: Long
)

object MediaImportHelper {

    fun isVideoUri(context: Context, uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri)?.lowercase()
        if (mime != null) {
            if (mime.startsWith("video/")) return true
            if (mime.startsWith("image/")) return false
        }
        val pathExt = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        if (pathExt in setOf("mp4", "mov", "mkv", "webm", "3gp", "avi", "m4v")) {
            return true
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun copyUriToProjectMediaDir(
        context: Context,
        projectId: String,
        sourceUri: Uri,
        isVideo: Boolean
    ): ImportedMediaInfo = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, "projects/$projectId/media").apply {
            if (!exists()) mkdirs()
        }

        val mimeType = context.contentResolver.getType(sourceUri)
        val extFromMime = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val extension = extFromMime?.takeIf { it.isNotBlank() } ?: if (isVideo) "mp4" else "jpg"
        val fileName = "${if (isVideo) "vid" else "img"}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}.$extension"
        val destFile = File(mediaDir, fileName)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("تعذر قراءة الملف المختار من الجهاز")

        if (!destFile.exists() || destFile.length() == 0L) {
            error("فشل نسخ الملف إلى مساحة المشروع")
        }

        val localUri = Uri.fromFile(destFile)
        var durationMs = if (isVideo) 5_000L else 3_000L
        var sourceFps = 30

        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(destFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val parsedDur = durStr?.toLongOrNull()
                if (parsedDur != null && parsedDur > 0L) {
                    durationMs = parsedDur
                }

                val captureFpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                val captureFps = captureFpsStr?.toFloatOrNull()?.toInt()
                if (captureFps != null && captureFps in 12..120) {
                    sourceFps = captureFps
                } else {
                    val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
                    if (frameCount != null && frameCount > 0L && durationMs > 0L) {
                        val computed = ((frameCount * 1000.0) / durationMs.toDouble()).toInt()
                        if (computed in 12..120) {
                            sourceFps = computed
                        }
                    }
                }
            } catch (_: Throwable) {
                // Keep safe fallback duration & fps if retriever cannot decode on current environment
            } finally {
                runCatching { retriever.release() }
            }
        }

        ImportedMediaInfo(
            localUriString = localUri.toString(),
            durationMs = durationMs.coerceAtLeast(500L),
            sourceFps = sourceFps,
            isVideo = isVideo
        )
    }

    suspend fun copyAudioUriToProjectDir(
        context: Context,
        projectId: String,
        sourceUri: Uri
    ): ImportedAudioInfo = withContext(Dispatchers.IO) {
        val audioDir = File(context.filesDir, "projects/$projectId/audio").apply {
            if (!exists()) mkdirs()
        }
        val mimeType = context.contentResolver.getType(sourceUri)
        val extFromMime = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val extension = extFromMime?.takeIf { it.isNotBlank() } ?: "m4a"
        val destFile = File(audioDir, "song_${System.currentTimeMillis()}.$extension")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("تعذر استيراد الملف الصوتي")

        var title = sourceUri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() } ?: "أغنية مستوردة"
        var durationMs = 0L

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(destFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let {
                title = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                if (it > 0L) durationMs = it
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { retriever.release() }
        }

        if (durationMs <= 0L) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(destFile.absolutePath)
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/") && fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        val us = fmt.getLong(MediaFormat.KEY_DURATION)
                        if (us > 0L) {
                            durationMs = us / 1000L
                            break
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { extractor.release() }
            }
        }

        ImportedAudioInfo(
            localUriString = Uri.fromFile(destFile).toString(),
            title = title,
            durationMs = durationMs.coerceAtLeast(10_020L)
        )
    }

    suspend fun extractAudioFromVideoUri(
        context: Context,
        projectId: String,
        sourceVideoUri: Uri
    ): ImportedAudioInfo = withContext(Dispatchers.IO) {
        val importedVideo = copyUriToProjectMediaDir(context, projectId, sourceVideoUri, isVideo = true)
        val videoPath = Uri.parse(importedVideo.localUriString).path
            ?: error("تعذر الوصول لملف الفيديو")

        val audioDir = File(context.filesDir, "projects/$projectId/audio").apply {
            if (!exists()) mkdirs()
        }
        val outFile = File(audioDir, "extracted_${System.currentTimeMillis()}.m4a")

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var extractedSuccess = false
        try {
            extractor.setDataSource(videoPath)
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex >= 0) {
                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)
                muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxTrack = muxer.addTrack(format)
                muxer.start()

                val buffer = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrack, buffer, info)
                    extractor.advance()
                }
                extractedSuccess = true
            }
        } catch (_: Throwable) {
            extractedSuccess = false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }

        if (!extractedSuccess || !outFile.exists() || outFile.length() == 0L) {
            error("الفيديو المختار لا يحتوي على مسار صوتي قابل للاستخراج")
        }

        ImportedAudioInfo(
            localUriString = Uri.fromFile(outFile).toString(),
            title = "Extracted Video Audio",
            durationMs = importedVideo.durationMs
        )
    }

    suspend fun extractVideoFrame(uriString: String, timeMs: Long = 0L): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            val parsed = Uri.parse(uriString)
            val path = parsed.path
            if (parsed.scheme == "file" && path != null && File(path).exists()) {
                retriever.setDataSource(path)
            } else {
                return@withContext null
            }
            retriever.getFrameAtTime(timeMs.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun extractAudioWaveformSamples(
        uriString: String,
        barCount: Int = 56
    ): List<Float> = withContext(Dispatchers.IO) {
        val safeBars = barCount.coerceIn(16, 128)
        val parsed = Uri.parse(uriString)
        val path = parsed.path
        if (uriString.isBlank() || parsed.scheme != "file" || path == null || !File(path).exists()) {
            return@withContext emptyList()
        }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrack < 0 || audioFormat == null) return@withContext emptyList()
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            val durUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(200_000L)
            } else 5_000_000L

            extractor.selectTrack(audioTrack)
            val bins = FloatArray(safeBars)
            val counts = IntArray(safeBars)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(audioFormat, null, null, 0)
                start()
            }
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var decodedFrames = 0

            while (!outputDone && decodedFrames < 420) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(8_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 8_000L)
                if (outIdx >= 0) {
                    decodedFrames++
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val binIndex = ((bufferInfo.presentationTimeUs.toDouble() / durUs.toDouble()) * safeBars)
                            .toInt().coerceIn(0, safeBars - 1)
                        val shortBuf = outBuf.asShortBuffer()
                        var peak = 0f
                        var samplesRead = 0
                        while (shortBuf.hasRemaining() && samplesRead < 512) {
                            val amp = kotlin.math.abs(shortBuf.get().toInt()) / 32768f
                            if (amp > peak) peak = amp
                            samplesRead++
                        }
                        bins[binIndex] = maxOf(bins[binIndex], peak)
                        counts[binIndex]++
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    outputDone = true
                }
            }

            val maxAmp = bins.maxOrNull()?.takeIf { it > 0.01f } ?: return@withContext emptyList()
            bins.mapIndexed { idx, v ->
                if (counts[idx] == 0) 0.18f else ((v / maxAmp) * 0.85f + 0.15f).coerceIn(0.15f, 1.0f)
            }
        } catch (_: Throwable) {
            emptyList()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}

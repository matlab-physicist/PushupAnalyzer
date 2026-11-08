package com.pushupanalyzer.app

import android.content.Context
import android.media.*
import android.net.Uri
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil

class VideoExporter(private val context: Context) {
    fun export(uri: Uri, analysis: Analysis, output: File, cancelled: () -> Unit, progress: (String) -> Unit): Boolean {
        val reader = MediaMetadataRetriever()
        val audio = MediaExtractor()
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var surface: android.view.Surface? = null
        var egl: EncoderSurface? = null
        var started = false
        var codecStarted = false
        try {
            reader.setDataSource(context,uri)
            audio.setDataSource(context,uri,null)
            val audioIndex = (0 until audio.trackCount).firstOrNull {
                audio.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC
            }
            val hasAudio = (0 until audio.trackCount).any {
                audio.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,FrameComposer.WIDTH,FrameComposer.HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE,5_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE,FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
                setInteger(MediaFormat.KEY_MAX_B_FRAMES,0)
            }
            val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter {
                it.isEncoder && it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_AVC) &&
                    it.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).isFormatSupported(format)
            }.sortedByDescending { it.isHardwareAccelerated }
            require(encoders.isNotEmpty()) { "No compatible H.264 encoder available" }
            var configured: MediaCodec? = null
            for (candidate in encoders) {
                val attempt = MediaCodec.createByCodecName(candidate.name)
                try { attempt.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE); configured = attempt; break }
                catch (_: Exception) { attempt.release() }
            }
            val encoder = configured ?: error("Could not configure an H.264 encoder")
            codec = encoder
            val writer = MediaMuxer(output.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val audioTrack = audioIndex?.let { audio.selectTrack(it); writer.addTrack(audio.getTrackFormat(it)) }
            surface = encoder.createInputSurface()
            egl = EncoderSurface(surface)
            encoder.start(); codecStarted = true
            val info = MediaCodec.BufferInfo()
            var videoTrack = -1
            var eos = false
            var lastProgress = SystemClock.elapsedRealtime()
            fun drain(wait: Boolean) {
                do {
                    cancelled()
                    val index = encoder.dequeueOutputBuffer(info,if (wait) 10_000 else 0)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!started) { "Encoder changed format twice" }
                            videoTrack = writer.addTrack(encoder.outputFormat)
                            writer.start(); started = true
                            lastProgress = SystemClock.elapsedRealtime()
                        }
                        index >= 0 -> {
                            try {
                                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                                if (info.size > 0) {
                                    check(started)
                                    val buffer = checkNotNull(encoder.getOutputBuffer(index))
                                    buffer.position(info.offset); buffer.limit(info.offset+info.size)
                                    writer.writeSampleData(videoTrack,buffer,info)
                                }
                                eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                lastProgress = SystemClock.elapsedRealtime()
                            } finally { encoder.releaseOutputBuffer(index,false) }
                        }
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            check(SystemClock.elapsedRealtime()-lastProgress < 30_000) { "Encoder timed out" }
                            return
                        }
                    }
                } while (!eos)
            }
            FrameComposer(analysis).use { composer ->
                val frames = ceil(analysis.duration * FPS).toInt()
                for (i in 0 until frames) {
                    cancelled(); drain(false)
                    val pts = i * 1_000_000L / FPS
                    val frame = reader.getScaledFrameAtTime(pts,MediaMetadataRetriever.OPTION_CLOSEST,
                        FrameComposer.WIDTH,FrameComposer.VIDEO_HEIGHT) ?: error("Cannot decode frame at $pts us")
                    try { egl.draw(composer.compose(frame,pts/1_000_000.0),pts) }
                    finally { frame.recycle() }
                    drain(false)
                    if (i % FPS == 0) progress("Rendering MP4: ${100*i/frames}%")
                }
            }
            encoder.signalEndOfInputStream()
            lastProgress = SystemClock.elapsedRealtime()
            while (!eos) drain(true)
            check(started) { "Encoder produced no output" }
            if (audioTrack != null) {
                val buffer = ByteBuffer.allocateDirect(1024*1024)
                while (true) {
                    cancelled()
                    // AAC priming packets can have valid negative timestamps. Only the
                    // track index identifies EOS; skip preroll before the video timeline.
                    if (audio.sampleTrackIndex < 0) break
                    val pts = audio.sampleTime
                    if (pts < 0) { audio.advance(); continue }
                    if (pts >= (analysis.duration*1_000_000).toLong()) break
                    buffer.clear()
                    val size = audio.readSampleData(buffer,0)
                    if (size < 0) break
                    require(size <= buffer.capacity()) { "Audio sample too large" }
                    info.set(0,size,pts,if (audio.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    writer.writeSampleData(audioTrack,buffer,info)
                    audio.advance()
                }
            }
            writer.stop(); started = false
            return hasAudio && audioTrack == null
        } finally {
            runCatching { egl?.close() }
            runCatching { surface?.release() }
            if (codecStarted) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            reader.release(); audio.release()
        }
    }
    companion object { const val FPS = 30 }
}

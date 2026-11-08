package com.pushupanalyzer.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.*
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class VideoAnalyzer(private val context: Context) {
    fun analyze(uri: Uri, cancelled: () -> Unit, progress: (String) -> Unit): Analysis {
        val reader = MediaMetadataRetriever()
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
        try {
            reader.setDataSource(context, uri)
            val duration = (reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) / 1000.0
            require(duration in 0.5..600.0) { "Choose a video between 0.5 seconds and 10 minutes." }
            val channels = listOf("left-3d","right-3d","left-2d","right-2d","left-reach","right-reach").associateWith { mutableListOf<AngleSample>() }
            val count = ceil(duration * 10).toInt()
            for (i in 0 until count) {
                cancelled()
                val time = i / 10.0
                val frame = reader.getScaledFrameAtTime((time * 1_000_000).toLong(),
                    MediaMetadataRetriever.OPTION_CLOSEST, 640, 640)
                try {
                    val pose = frame?.let {
                        Tasks.await(detector.process(InputImage.fromBitmap(it, 0)), 30, TimeUnit.SECONDS)
                    }
                    for ((name, samples) in channels) samples += AngleSample(time,pose?.let {
                        signal(it,name.startsWith("left"),name.substringAfter('-'))
                    })
                } finally { frame?.recycle() }
                if (i % 10 == 0) progress("Analyzing poses: ${100 * i / count}%")
            }
            val analysis = MultiViewCounter.detect(channels, duration)
            require(analysis.coverage >= 0.25) {
                "Too few visible poses. Keep one person and their arms visible. Try better lighting or less occlusion."
            }
            return analysis
        } finally { detector.close(); reader.release() }
    }

    private fun signal(pose: Pose, left: Boolean, kind: String): Double? {
        val types = if (left) intArrayOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_HIP) else intArrayOf(PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_HIP)
        val points = types.map { type -> pose.getPoseLandmark(type)?.let {
            val p=it.position3D
            Joint(p.x.toDouble(),p.y.toDouble(),p.z.toDouble(),it.inFrameLikelihood.toDouble())
        } }
        return if(kind=="reach") MultiViewCounter.reach(points[0],points[2],points[3])
        else MultiViewCounter.angle(points[0],points[1],points[2],kind=="3d")
    }
}

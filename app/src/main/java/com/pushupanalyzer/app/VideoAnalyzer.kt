package com.pushupanalyzer.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.*
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil

class VideoAnalyzer(private val context: Context) {
    fun analyze(uri: Uri, cancelled: () -> Unit, progress: (String) -> Unit): Analysis {
        val reader = MediaMetadataRetriever()
        val detector = PoseDetection.getClient(PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build())
        try {
            reader.setDataSource(context, uri)
            val duration = (reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) / 1000.0
            require(duration in 0.5..600.0) { "Choose a video between 0.5 seconds and 10 minutes." }
            val left = mutableListOf<AngleSample>(); val right = mutableListOf<AngleSample>()
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
                    left += AngleSample(time, pose?.let { angle(it, true) })
                    right += AngleSample(time, pose?.let { angle(it, false) })
                } finally { frame?.recycle() }
                if (i % 10 == 0) progress("Analyzing poses: ${100 * i / count}%")
            }
            // Use one side for the entire clip, avoiding artificial angle jumps.
            val selected = if (left.count { it.angle != null } >= right.count { it.angle != null }) left else right
            val analysis = RepDetector.detect(selected, duration)
            require(analysis.coverage >= 0.25) {
                "Too few visible poses. Film one person side-on, with shoulder, elbow, wrist and hip visible."
            }
            return analysis
        } finally { detector.close(); reader.release() }
    }

    private fun angle(pose: Pose, left: Boolean): Double? {
        val types = if (left) intArrayOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_HIP) else intArrayOf(PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_HIP)
        val points = types.map { pose.getPoseLandmark(it) ?: return null }
        if (points.any { it.inFrameLikelihood < 0.65f }) return null
        val (s, e, w, h) = points.map { it.position }
        // Exclude standing arm bends: the torso must be approximately horizontal.
        if (abs(s.x - h.x) < abs(s.y - h.y) * 0.7f) return null
        return RepDetector.elbowAngle(s.x.toDouble(), s.y.toDouble(), e.x.toDouble(), e.y.toDouble(),
            w.x.toDouble(), w.y.toDouble())
    }
}

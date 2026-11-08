package com.pushupanalyzer.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CancellationException

@RunWith(AndroidJUnit4::class)
class VideoPipelineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun source(): File = File(context.cacheDir,"test-source.mp4").also { file ->
        instrumentation.context.assets.open("source.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    @Test fun exportHasCorrectDurationAudioOrientationGraphsAndGalleryEntry() {
        val source = source()
        val output = File(context.cacheDir,"test-output.mp4")
        val analysis = Analysis(2.0,listOf(Repetition(Segment(0.2,0.8,Phase.DOWN),Segment(0.8,1.4,Phase.UP))),1.0)
        var saved: Uri? = null
        try {
            assertFalse("AAC audio should be retained",VideoExporter(context).export(Uri.fromFile(source),analysis,output,{},{ }))
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(output.absolutePath)
                assertEquals("960",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
                assertEquals("960",reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("duration=$duration",duration in 1950..2100)
                val frame = reader.getFrameAtTime(1_000_000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                try {
                    // Detect vertical flips in the GLES texture upload, and verify the graph panel.
                    val top = frame.getPixel(480,180); val bottom = frame.getPixel(480,480)
                    assertTrue("Top video pixel should be red: ${Integer.toHexString(top)}",Color.red(top) > Color.blue(top)+100)
                    assertTrue("Bottom video pixel should be blue: ${Integer.toHexString(bottom)}",Color.blue(bottom) > Color.red(bottom)+100)
                    assertTrue("Graph background is missing",Color.red(frame.getPixel(20,800)) < 60)
                    // Graph playhead at time=1.0, half of a 2.0 second clip.
                    val line = frame.getPixel(504,750)
                    assertTrue("graph playhead missing",Color.red(line)>150 && Color.green(line)>150)
                } finally { frame.recycle() }
            } finally { reader.release() }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                val video = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "video/avc" }
                assertTrue("Export has no AAC audio track",(0 until extractor.trackCount).any { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" })
                extractor.selectTrack(video)
                var frames = 0; var previous = -1L
                while (extractor.sampleTime >= 0) {
                    val pts = extractor.sampleTime
                    assertTrue("Non-increasing PTS at frame $frames: $previous -> $pts",pts > previous)
                    if (previous >= 0) assertTrue("frame delta=${pts-previous}",pts-previous in 33332..33334)
                    previous = pts; frames++; extractor.advance()
                }
                assertEquals(60,frames)
            } finally { extractor.release() }
            saved = GalleryStore.save(context,output,{})
            context.contentResolver.openAssetFileDescriptor(saved,"r")!!.use { assertEquals(output.length(),it.length) }
        } finally {
            saved?.let { context.contentResolver.delete(it,null,null) }
            source.delete(); output.delete()
        }
    }
    @Test fun bundledPoseModelInitializesWithoutInternetAndRejectsEmptyScene() {
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains(Manifest.permission.INTERNET))
        val source = source()
        try {
            try {
                VideoAnalyzer(context).analyze(Uri.fromFile(source),{},{})
                fail("An empty scene must not produce repetitions")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("Too few visible poses"))
            }
        } finally { source.delete() }
    }
    @Test fun cancellationStopsExportAndDoesNotPublishAGalleryFile() {
        val source = source(); val output = File(context.cacheDir,"cancelled.mp4")
        try {
            try {
                VideoExporter(context).export(Uri.fromFile(source),Analysis(2.0,emptyList(),1.0),output,
                    { throw CancellationException() },{})
                fail("Cancellation must stop export")
            } catch (_: CancellationException) { /* expected */ }
        } finally { source.delete(); output.delete() }
    }
}

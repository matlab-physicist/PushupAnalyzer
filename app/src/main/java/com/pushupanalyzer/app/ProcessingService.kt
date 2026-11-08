package com.pushupanalyzer.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** State survives Activity recreation; the foreground service owns the job. */
object ProcessingState {
    data class Snapshot(val running: Boolean = false, val message: String = "Ready. All analysis stays on this phone.", val output: Uri? = null)
    @Volatile var current = Snapshot()
        private set
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(Snapshot) -> Unit>()
    fun observe(listener: (Snapshot) -> Unit) { listeners += listener; listener(current) }
    fun remove(listener: (Snapshot) -> Unit) { listeners -= listener }
    fun publish(state: Snapshot) { main.post { current = state; listeners.toList().forEach { it(state) } } }
}

class ProcessingService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private var running = false
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Video processing",NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this,1,Intent(this,ProcessingService::class.java).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Pushup Analyzer").setContentText(message).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).addAction(Notification.Action.Builder(null,"Cancel",cancel).build()).build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) { cancelled.set(true); if (!running) stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        val uri = intent?.data ?: run { stopSelf(); return START_NOT_STICKY }
        running = true; cancelled.set(false)
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        startForeground(1,notification("Starting offline analysis…"),type)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PushupAnalyzer:Export").apply {
            acquire(2*60*60*1000L)
        }
        ProcessingState.publish(ProcessingState.Snapshot(true,"Starting offline analysis…"))
        worker.execute {
            val file = File(cacheDir,"PushupAnalyzer_${System.currentTimeMillis()}.mp4")
            fun checkCancel() { if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException() }
            fun progress(message: String) {
                ProcessingState.publish(ProcessingState.Snapshot(true,message))
                main.post { if (running) getSystemService(NotificationManager::class.java).notify(1,notification(message)) }
            }
            try {
                // Remove abandoned cache exports from a process death, never gallery files.
                cacheDir.listFiles()?.filter { it.name.startsWith("PushupAnalyzer_") && it.extension == "mp4" }?.forEach { it.delete() }
                val analysis = VideoAnalyzer(this).analyze(uri,::checkCancel,::progress)
                progress("${analysis.reps.size} full reps detected. Rendering…")
                // Keep enough space for both temporary output and the gallery copy.
                val estimatedBytes = (analysis.duration * 5_000_000 / 8 * 2.5).toLong()
                require(cacheDir.usableSpace > estimatedBytes + 50_000_000) { "Not enough free storage for export." }
                val audioOmitted = VideoExporter(this).export(uri,analysis,file,::checkCancel,::progress)
                checkCancel(); progress("Saving to gallery…")
                val saved = GalleryStore.save(this,file,::checkCancel)
                val warning = if (audioOmitted) "\nNon-AAC source audio was omitted." else ""
                val tracking = if (analysis.coverage < 0.8) "\nTracking was intermittent; review the estimated count." else ""
                ProcessingState.publish(ProcessingState.Snapshot(false,
                    "Saved to Movies/PushupAnalyzer.\n${analysis.reps.size} estimated full reps; ${"%.0f".format(analysis.coverage*100)}% pose coverage.$tracking$warning",saved))
            } catch (_: CancellationException) {
                ProcessingState.publish(ProcessingState.Snapshot(false,"Cancelled. No output was saved."))
            } catch (e: Exception) {
                ProcessingState.publish(ProcessingState.Snapshot(false,"Processing failed: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                file.delete()
                main.post {
                    running = false
                    wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) { cancelled.set(true); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() {
        cancelled.set(true); worker.shutdownNow()
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        super.onDestroy()
    }
    companion object {
        const val CANCEL = "com.pushupanalyzer.app.CANCEL"
        private const val CHANNEL = "processing"
    }
}

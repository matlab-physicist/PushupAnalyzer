package com.pushupanalyzer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var select: Button
    private lateinit var cancel: Button
    private lateinit var open: Button
    private val listener: (ProcessingState.Snapshot) -> Unit = { state ->
        status.text = state.message
        select.isEnabled = !state.running
        cancel.visibility = if (state.running) View.VISIBLE else View.GONE
        open.visibility = if (state.output != null) View.VISIBLE else View.GONE
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n*density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24),dp(16),dp(24),dp(24))
        }
        val scroll = ScrollView(this).apply { addView(layout) }
        if (Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        // getInsets(Type) is API 30; API 29 uses the legacy inset values.
        if (Build.VERSION.SDK_INT == 29) scroll.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        layout.addView(TextView(this).apply {
            text = "Pushup Analyzer"; textSize = 28f; setTextColor(Color.rgb(16,35,60))
        })
        layout.addView(TextView(this).apply {
            text = "Offline video analysis\n\nFilm one person from the side with the whole body visible and a steady camera. Use a local MP4, up to 10 minutes.\n\nCounts full extended–bent–extended arm cycles. Timings are estimates at 0.1s resolution. Output: 960 × 960 MP4 at 30 fps, with video above synchronized timing graphs. AAC audio is preserved."
            textSize = 16f; setPadding(0,dp(20),0,dp(24))
        })
        select = Button(this).apply {
            text = "Select MP4 from gallery"
            setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "video/mp4"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                },101)
            }
        }
        layout.addView(select)
        cancel = Button(this).apply {
            text = "Cancel processing"
            setOnClickListener { startService(Intent(this@MainActivity,ProcessingService::class.java).setAction(ProcessingService.CANCEL)) }
        }
        layout.addView(cancel)
        open = Button(this).apply {
            text = "Open saved video"
            setOnClickListener {
                ProcessingState.current.output?.let { uri ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        .onFailure { Toast.makeText(this@MainActivity,"Open Movies/PushupAnalyzer in your gallery.",Toast.LENGTH_LONG).show() }
                }
            }
        }
        layout.addView(open)
        status = TextView(this).apply { textSize = 16f; setPadding(0,dp(24),0,0) }
        layout.addView(status)
        setContentView(scroll)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),102)
        }
    }
    override fun onStart() { super.onStart(); ProcessingState.observe(listener) }
    override fun onStop() { ProcessingState.remove(listener); super.onStop() }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode != 101 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        try { startForegroundService(Intent(this,ProcessingService::class.java).setData(uri)) }
        catch (e: Exception) { status.text = "Could not start processing: ${e.message}" }
    }
}

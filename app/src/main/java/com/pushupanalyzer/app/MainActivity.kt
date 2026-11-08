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
    private lateinit var sensitivity: Spinner
    private lateinit var select: Button
    private lateinit var cancel: Button
    private lateinit var open: Button
    private val listener: (ProcessingState.Snapshot) -> Unit = { state ->
        status.text = state.message
        select.isEnabled = !state.running
        sensitivity.isEnabled = !state.running
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
            setText(R.string.app_name); textSize = 28f; setTextColor(Color.rgb(16,35,60))
        })
        layout.addView(TextView(this).apply {
            setText(R.string.instructions)
            textSize = 16f; setPadding(0,dp(20),0,dp(24))
        })
        layout.addView(TextView(this).apply { setText(R.string.required_movement); textSize=18f })
        val preferences=getSharedPreferences("detection",MODE_PRIVATE)
        sensitivity=Spinner(this).apply {
            id=R.id.sensitivity_selector
            adapter=ArrayAdapter.createFromResource(this@MainActivity,R.array.sensitivity_labels,
                android.R.layout.simple_spinner_item).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(DetectionSensitivity.fromName(preferences.getString("sensitivity",null)).ordinal)
            onItemSelectedListener=object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    preferences.edit().putString("sensitivity",DetectionSensitivity.entries[position].name).apply()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        layout.addView(sensitivity)
        layout.addView(TextView(this).apply { setText(R.string.sensitivity_help); setPadding(0,0,0,dp(16)) })
        select = Button(this).apply {
            setText(R.string.select_video)
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
            setText(R.string.cancel_processing)
            setOnClickListener { startService(Intent(this@MainActivity,ProcessingService::class.java).setAction(ProcessingService.CANCEL)) }
        }
        layout.addView(cancel)
        open = Button(this).apply {
            setText(R.string.open_video)
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
        try { startForegroundService(Intent(this,ProcessingService::class.java).setData(uri)
            .putExtra(ProcessingService.SENSITIVITY,DetectionSensitivity.entries[sensitivity.selectedItemPosition].name)) }
        catch (e: Exception) { status.text = getString(R.string.start_failed,e.message) }
    }
}

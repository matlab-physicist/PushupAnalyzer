package com.pushupanalyzer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.widget.Spinner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitivityUiTest {
    @Test fun smallerMovementSelectionSurvivesActivityRelaunch() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val preferences=context.getSharedPreferences("detection",0)
        val original=preferences.getString("sensitivity",null)
        var activity: Activity?=null
        fun launch(): Activity = instrumentation.startActivitySync(
            Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            if(Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,Manifest.permission.POST_NOTIFICATIONS)
            preferences.edit().remove("sensitivity").commit()
            activity=launch()
            instrumentation.runOnMainSync {
                val selector=activity!!.findViewById<Spinner>(R.id.sensitivity_selector)
                assertEquals(DetectionSensitivity.SENSITIVE.ordinal,selector.selectedItemPosition)
                selector.setSelection(DetectionSensitivity.VERY_SENSITIVE.ordinal)
            }
            instrumentation.waitForIdleSync()
            assertEquals("VERY_SENSITIVE",preferences.getString("sensitivity",null))
            instrumentation.runOnMainSync { activity!!.finish() }
            instrumentation.waitForIdleSync()
            activity=launch()
            instrumentation.runOnMainSync {
                assertEquals(DetectionSensitivity.VERY_SENSITIVE.ordinal,
                    activity!!.findViewById<Spinner>(R.id.sensitivity_selector).selectedItemPosition)
            }
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            preferences.edit().putString("sensitivity",original).commit()
        }
    }
}

package com.olerast.suflyor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.olerast.suflyor.R

/** Public system settings only. OEMs may omit individual pages, so always offer a fallback. */
internal object AccessibilitySettings {
    fun open(context: Context) = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun battery(context: Context) = open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    fun appInfo(context: Context) = open(context, appIntent(context))

    private fun appIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun open(context: Context, first: Intent) {
        for (intent in listOf(first, appIntent(context), Intent(Settings.ACTION_SETTINGS))) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
        Toast.makeText(context, R.string.a11y_settings_failed, Toast.LENGTH_LONG).show()
    }
}

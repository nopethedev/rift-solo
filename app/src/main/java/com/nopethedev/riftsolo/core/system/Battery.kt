package com.nopethedev.riftsolo.core.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Background playback helpers. Many phones (Xiaomi, Huawei, Oppo, Samsung, older Android versions in general) kill
 * apps in the background to save battery, even while they play music. Letting Rift Solo ignore battery optimisation
 * stops that; some brands also need "No restrictions" / "Autostart" in their own settings.
 */
object Battery {
    fun isUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The system "Allow Rift Solo to always run in the background?" dialog. */
    @SuppressLint("BatteryLife")
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Fallbacks when the dialog isn't available: the full list, or the app's own settings page. */
    fun listIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    fun appDetailsIntent(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Brands known to kill background apps beyond what Android itself does (see dontkillmyapp.com). */
    val aggressiveOem: Boolean get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "oneplus", "vivo", "samsung", "meizu", "asus", "tecno", "infinix")

    fun open(context: Context, vararg intents: Intent) {
        for (i in intents) {
            try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
        }
    }
}

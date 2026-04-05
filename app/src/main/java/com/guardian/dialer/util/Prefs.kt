package com.guardian.dialer.util

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.guardian.dialer.BuildConfig

/** Central access to app configuration stored in SharedPreferences. */
object Prefs {
    private const val NAME = "guardian_prefs"

    const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
    const val KEY_ELEVENLABS_AGENT_ID = "elevenlabs_agent_id"
    const val KEY_TEXTBEE_API_KEY = "textbee_api_key"
    const val KEY_TEXTBEE_DEVICE_ID = "textbee_device_id"
    const val KEY_OWNER_PHONE = "owner_phone"
    const val KEY_ENABLED = "guardian_enabled"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getString(ctx: Context, key: String, default: String = ""): String =
        prefs(ctx).getString(key, default) ?: default

    fun getBoolean(ctx: Context, key: String, default: Boolean = false): Boolean =
        prefs(ctx).getBoolean(key, default)

    fun put(ctx: Context, key: String, value: String) =
        prefs(ctx).edit().putString(key, value).apply()

    fun put(ctx: Context, key: String, value: Boolean) =
        prefs(ctx).edit().putBoolean(key, value).apply()

    /**
     * Seeds SharedPreferences from BuildConfig values (set via local.properties).
     * Only writes if the pref is currently blank so manual edits are not overwritten.
     * Also attempts to read the device's own phone number for KEY_OWNER_PHONE.
     */
    fun seedFromBuildConfig(ctx: Context) {
        fun seed(key: String, value: String) {
            if (value.isNotBlank() && getString(ctx, key).isBlank()) put(ctx, key, value)
        }
        seed(KEY_ELEVENLABS_API_KEY, BuildConfig.ELEVENLABS_API_KEY)
        seed(KEY_ELEVENLABS_AGENT_ID, BuildConfig.ELEVENLABS_AGENT_ID)
        seed(KEY_TEXTBEE_API_KEY, BuildConfig.TEXTBEE_API_KEY)
        seed(KEY_TEXTBEE_DEVICE_ID, BuildConfig.TEXTBEE_DEVICE_ID)

        if (getString(ctx, KEY_OWNER_PHONE).isBlank() &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
        ) {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val line1 = try {
                @Suppress("DEPRECATION")
                tm.line1Number
            } catch (e: SecurityException) {
                null
            }
            if (!line1.isNullOrBlank()) put(ctx, KEY_OWNER_PHONE, line1)
        }
    }

    /** True if all required fields are configured. */
    fun isConfigured(ctx: Context): Boolean {
        val p = prefs(ctx)
        return !p.getString(KEY_ELEVENLABS_API_KEY, "").isNullOrBlank()
            && !p.getString(KEY_ELEVENLABS_AGENT_ID, "").isNullOrBlank()
            && !p.getString(KEY_TEXTBEE_API_KEY, "").isNullOrBlank()
            && !p.getString(KEY_TEXTBEE_DEVICE_ID, "").isNullOrBlank()
            && !p.getString(KEY_OWNER_PHONE, "").isNullOrBlank()
    }
}

package com.guardian.dialer.util

import android.content.Context
import android.content.SharedPreferences

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

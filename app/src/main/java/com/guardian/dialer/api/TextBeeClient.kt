package com.guardian.dialer.api

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Sends SMS via TextBee API (https://textbee.dev).
 * TextBee uses your own Android phone as an SMS gateway.
 */
class TextBeeClient(
    private val apiKey: String,
    private val deviceId: String
) {
    companion object {
        private const val TAG = "TextBeeClient"
        private const val BASE_URL = "https://api.textbee.dev/api/v1"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Send an SMS to [recipient] with [message] body.
     * Returns true on success.
     */
    suspend fun sendSms(recipient: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(mapOf(
                "recipients" to listOf(recipient),
                "message" to message
            ))

            val request = Request.Builder()
                .url("$BASE_URL/gateway/devices/$deviceId/sendSMS")
                .header("x-api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            if (!success) {
                Log.e(TAG, "TextBee SMS failed: ${response.code} ${response.body?.string()}")
            } else {
                Log.i(TAG, "SMS sent to $recipient")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "TextBee SMS error: ${e.message}", e)
            false
        }
    }
}

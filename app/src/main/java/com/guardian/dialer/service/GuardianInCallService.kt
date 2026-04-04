package com.guardian.dialer.service

import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.guardian.dialer.InCallActivity
import com.guardian.dialer.api.ElevenLabsClient
import com.guardian.dialer.api.TextBeeClient
import com.guardian.dialer.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Core InCallService — required for Default Dialer.
 * For known contacts: shows normal InCallActivity.
 * For unknown numbers flagged by CallScreeningService: auto-answers and bridges to ElevenLabs.
 */
class GuardianInCallService : InCallService() {
    companion object {
        private const val TAG = "GuardianInCall"
        private const val AI_ANSWER_DELAY_MS = 500L // brief delay before auto-answer

        // Singleton reference for InCallActivity to access
        var currentCall: Call? = null
        var instance: GuardianInCallService? = null
        var activeBridge: ElevenLabsClient? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Log.d(TAG, "Call state changed: $state")
            when (state) {
                Call.STATE_DISCONNECTED -> {
                    onCallEnded(call)
                }
                Call.STATE_ACTIVE -> {
                    // Call is now connected
                }
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        instance = this
        call.registerCallback(callCallback)

        val number = call.details?.handle?.schemeSpecificPart ?: ""
        Log.i(TAG, "Call added: $number state=${call.state}")

        if (GuardianCallScreeningService.pendingAiCalls.remove(number)) {
            // Unknown caller — auto-answer after brief delay and bridge to AI
            Log.i(TAG, "AI pickup for $number")
            handler.postDelayed({
                answerForAi(call, number)
            }, AI_ANSWER_DELAY_MS)
        } else {
            // Known contact or Guardian disabled — show normal in-call UI
            launchInCallUi(number, isAiHandled = false)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (currentCall == call) {
            currentCall = null
        }
        onCallEnded(call)
    }

    private fun answerForAi(call: Call, number: String) {
        if (call.state == Call.STATE_RINGING) {
            call.answer(/* videoState = */ 0) // audio-only

            // Route to speaker so AudioRecord can capture remote audio
            handler.postDelayed({
                setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                startElevenLabsBridge(number)
                launchInCallUi(number, isAiHandled = true)
            }, 300)
        }
    }

    private fun startElevenLabsBridge(callerNumber: String) {
        val apiKey = Prefs.getString(this, Prefs.KEY_ELEVENLABS_API_KEY)
        val agentId = Prefs.getString(this, Prefs.KEY_ELEVENLABS_AGENT_ID)

        if (apiKey.isBlank() || agentId.isBlank()) {
            Log.e(TAG, "ElevenLabs not configured")
            return
        }

        val bridge = ElevenLabsClient(apiKey, agentId, callerNumber)
        activeBridge = bridge

        bridge.onSessionEnded = { summary ->
            Log.i(TAG, "AI session ended, sending summary SMS")
            sendSummaryToOwner(summary)
            activeBridge = null
        }

        bridge.start()
        Log.i(TAG, "ElevenLabs bridge started for $callerNumber")
    }

    private fun onCallEnded(call: Call) {
        activeBridge?.stop()
        activeBridge = null
    }

    private fun sendSummaryToOwner(summary: String) {
        val textBeeApiKey = Prefs.getString(this, Prefs.KEY_TEXTBEE_API_KEY)
        val textBeeDeviceId = Prefs.getString(this, Prefs.KEY_TEXTBEE_DEVICE_ID)
        val ownerPhone = Prefs.getString(this, Prefs.KEY_OWNER_PHONE)

        if (textBeeApiKey.isBlank() || textBeeDeviceId.isBlank() || ownerPhone.isBlank()) {
            Log.e(TAG, "TextBee not configured, can't send summary")
            return
        }

        val client = TextBeeClient(textBeeApiKey, textBeeDeviceId)
        scope.launch {
            // Truncate if too long for SMS
            val msg = if (summary.length > 1500) {
                summary.take(1497) + "..."
            } else {
                summary
            }
            client.sendSms(ownerPhone, msg)
        }
    }

    private fun launchInCallUi(number: String, isAiHandled: Boolean) {
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("number", number)
            putExtra("ai_handled", isAiHandled)
        }
        startActivity(intent)
    }
}

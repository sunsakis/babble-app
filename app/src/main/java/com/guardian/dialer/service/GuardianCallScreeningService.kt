package com.guardian.dialer.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.guardian.dialer.util.ContactUtils
import com.guardian.dialer.util.Prefs

/**
 * Screens incoming calls. If the number is NOT in contacts and Guardian is enabled,
 * the call is marked for AI handling — it will ring silently and GuardianInCallService
 * will auto-answer and bridge to ElevenLabs.
 */
class GuardianCallScreeningService : CallScreeningService() {
    companion object {
        private const val TAG = "GuardianScreening"

        /** Set of numbers currently flagged for AI pickup. Checked by InCallService. */
        val pendingAiCalls = mutableSetOf<String>()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        val direction = callDetails.callDirection

        Log.d(TAG, "Screening call: $number direction=$direction")

        // Only screen incoming calls
        if (direction != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // Check if Guardian is enabled and configured
        if (!Prefs.getBoolean(this, Prefs.KEY_ENABLED, false) || !Prefs.isConfigured(this)) {
            Log.d(TAG, "Guardian disabled or not configured, passing through")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // Check contacts
        if (number.isNotBlank() && ContactUtils.isUnknownNumber(this, number)) {
            Log.i(TAG, "Unknown number $number — flagging for AI pickup")
            pendingAiCalls.add(number)

            // Allow the call through but silence the ring —
            // InCallService will auto-answer and bridge to ElevenLabs
            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(true)  // don't ring — AI will pick up
                .setSkipNotification(false)
                .build()
            respondToCall(callDetails, response)
        } else {
            Log.d(TAG, "Known contact $number — normal ring")
            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }
}

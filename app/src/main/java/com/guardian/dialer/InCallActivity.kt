package com.guardian.dialer

import android.os.Bundle
import android.telecom.Call
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.guardian.dialer.databinding.ActivityIncallBinding
import com.guardian.dialer.service.GuardianInCallService

/**
 * Minimal in-call UI.
 * - For AI-handled calls: shows "AI is handling this call" with live status
 * - For normal calls: shows standard answer/hang-up controls
 */
class InCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        binding = ActivityIncallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val number = intent.getStringExtra("number") ?: "Unknown"
        val aiHandled = intent.getBooleanExtra("ai_handled", false)

        binding.txtCallerNumber.text = number

        if (aiHandled) {
            binding.txtCallStatus.text = "AI Agent is handling this call"
            binding.btnAnswer.visibility = View.GONE
            binding.btnHangup.text = "End AI Call"
        } else {
            updateCallStateUi()
            binding.btnAnswer.visibility = if (
                GuardianInCallService.currentCall?.state == Call.STATE_RINGING
            ) View.VISIBLE else View.GONE
        }

        binding.btnAnswer.setOnClickListener {
            GuardianInCallService.currentCall?.answer(0)
            binding.btnAnswer.visibility = View.GONE
            binding.txtCallStatus.text = "Connected"
        }

        binding.btnHangup.setOnClickListener {
            GuardianInCallService.currentCall?.disconnect()
            finish()
        }

        binding.btnSpeaker.setOnClickListener {
            // Toggle speaker (simplified — real impl would use CallAudioState)
            binding.btnSpeaker.isSelected = !binding.btnSpeaker.isSelected
        }

        binding.btnMute.setOnClickListener {
            binding.btnMute.isSelected = !binding.btnMute.isSelected
        }
    }

    private fun updateCallStateUi() {
        val call = GuardianInCallService.currentCall
        binding.txtCallStatus.text = when (call?.state) {
            Call.STATE_RINGING -> "Incoming call..."
            Call.STATE_ACTIVE -> "Connected"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DIALING -> "Dialing..."
            Call.STATE_CONNECTING -> "Connecting..."
            Call.STATE_DISCONNECTED -> "Call ended"
            else -> "Unknown"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't disconnect the call when leaving UI — it continues in background
    }
}

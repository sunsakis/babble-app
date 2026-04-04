package com.guardian.dialer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.guardian.dialer.databinding.ActivitySettingsBinding
import com.guardian.dialer.util.Prefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Guardian Settings"

        loadSettings()

        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        binding.editElevenLabsApiKey.setText(Prefs.getString(this, Prefs.KEY_ELEVENLABS_API_KEY))
        binding.editElevenLabsAgentId.setText(Prefs.getString(this, Prefs.KEY_ELEVENLABS_AGENT_ID))
        binding.editTextBeeApiKey.setText(Prefs.getString(this, Prefs.KEY_TEXTBEE_API_KEY))
        binding.editTextBeeDeviceId.setText(Prefs.getString(this, Prefs.KEY_TEXTBEE_DEVICE_ID))
        binding.editOwnerPhone.setText(Prefs.getString(this, Prefs.KEY_OWNER_PHONE))
    }

    private fun saveSettings() {
        Prefs.put(this, Prefs.KEY_ELEVENLABS_API_KEY,
            binding.editElevenLabsApiKey.text.toString().trim())
        Prefs.put(this, Prefs.KEY_ELEVENLABS_AGENT_ID,
            binding.editElevenLabsAgentId.text.toString().trim())
        Prefs.put(this, Prefs.KEY_TEXTBEE_API_KEY,
            binding.editTextBeeApiKey.text.toString().trim())
        Prefs.put(this, Prefs.KEY_TEXTBEE_DEVICE_ID,
            binding.editTextBeeDeviceId.text.toString().trim())
        Prefs.put(this, Prefs.KEY_OWNER_PHONE,
            binding.editOwnerPhone.text.toString().trim())

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()

        if (Prefs.isConfigured(this)) {
            Toast.makeText(this, "Guardian ready to activate", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Fill in all fields to enable Guardian", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

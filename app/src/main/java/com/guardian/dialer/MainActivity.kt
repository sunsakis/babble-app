package com.guardian.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.guardian.dialer.databinding.ActivityMainBinding
import com.guardian.dialer.util.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requiredPermissions = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO,
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            requestDefaultDialer()
        } else {
            Toast.makeText(this, "Permissions required for Guardian Dialer", Toast.LENGTH_LONG).show()
        }
        updateUi()
    }

    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle tel: intents (required for default dialer)
        handleDialIntent(intent)

        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            Prefs.put(this, Prefs.KEY_ENABLED, isChecked)
            updateUi()
        }

        binding.btnSetDefault.setOnClickListener {
            checkPermissionsAndSetDefault()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnDial.setOnClickListener {
            val number = binding.editPhone.text.toString().trim()
            if (number.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startActivity(intent)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
    }

    private fun handleDialIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_DIAL || intent?.action == Intent.ACTION_VIEW) {
            val number = intent.data?.schemeSpecificPart ?: return
            binding.editPhone.setText(number)
        }
    }

    private fun checkPermissionsAndSetDefault() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            requestDefaultDialer()
        }
    }

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                defaultDialerLauncher.launch(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                )
            }
        } else {
            val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
            if (tm.defaultDialerPackage != packageName) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                }
                startActivity(intent)
            }
        }
    }

    private fun isDefaultDialer(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
            tm.defaultDialerPackage == packageName
        }
    }

    private fun updateUi() {
        val isDefault = isDefaultDialer()
        val isConfigured = Prefs.isConfigured(this)
        val isEnabled = Prefs.getBoolean(this, Prefs.KEY_ENABLED, false)

        binding.switchEnabled.isChecked = isEnabled

        binding.txtStatus.text = buildString {
            if (isDefault) append("Default Dialer") else append("Not default dialer")
            append(" \u2022 ")
            if (isConfigured) append("Configured") else append("Not configured")
            append(" \u2022 ")
            if (isEnabled) append("Guardian ON") else append("Guardian OFF")
        }

        binding.btnSetDefault.isEnabled = !isDefault
        binding.switchEnabled.isEnabled = isConfigured

        binding.txtDescription.text = if (isEnabled && isConfigured && isDefault) {
            "Guardian is active. Unknown callers will be answered by the AI agent."
        } else if (!isDefault) {
            "Set as default dialer to enable call screening."
        } else if (!isConfigured) {
            "Open Settings to configure API keys."
        } else {
            "Toggle the switch to activate Guardian."
        }
    }
}

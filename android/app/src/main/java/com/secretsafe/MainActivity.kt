package com.secretsafe

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.secretsafe.service.WSBackgroundService
import com.secretsafe.ui.screens.ApprovalDialog
import com.secretsafe.ui.screens.DashboardScreen
import com.secretsafe.ui.screens.LoginScreen
import com.secretsafe.ui.screens.CreatePinScreen
import com.secretsafe.ui.screens.PinLockScreen
import com.secretsafe.ui.theme.SecretSafeTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permissions (required for Android 13+)
        requestNotificationPermission()

        // Read session credentials persistence
        val sharedPref = getSharedPreferences("SecretSafePref", Context.MODE_PRIVATE)
        val savedToken = sharedPref.getString("token", "") ?: ""
        val savedServerUrl = sharedPref.getString("server_url", "") ?: ""

        // If saved connection profile is detected, startup websocket daemon
        if (savedToken.isNotEmpty() && savedServerUrl.isNotEmpty()) {
            startWSService(savedServerUrl, savedToken)
        }

        setContent {
            SecretSafeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var token by remember { mutableStateOf(savedToken) }
                    var serverUrl by remember { mutableStateOf(savedServerUrl) }
                    var appPin by remember { mutableStateOf(sharedPref.getString("app_pin", "") ?: "") }
                    var isAppUnlocked by remember { mutableStateOf(false) }

                    val activeRequest = WSBackgroundService.currentRequest.value

                    if (token.isEmpty()) {
                        LoginScreen(onLoginSuccess = { tkn, url ->
                            sharedPref.edit().apply {
                                putString("token", tkn)
                                putString("server_url", url)
                                apply()
                            }
                            token = tkn
                            serverUrl = url
                            startWSService(url, tkn)
                        })
                    } else if (appPin.isEmpty()) {
                        // User logged in but hasn't created a PIN code yet
                        CreatePinScreen(onPinCreated = { pin ->
                            sharedPref.edit().apply {
                                putString("app_pin", pin)
                                apply()
                            }
                            appPin = pin
                            isAppUnlocked = true
                        })
                    } else if (!isAppUnlocked) {
                        // App is locked by security PIN
                        PinLockScreen(
                            savedPin = appPin,
                            onSuccess = { isAppUnlocked = true },
                            onSignOut = {
                                sharedPref.edit().apply {
                                    putString("token", "")
                                    putString("server_url", "")
                                    putString("app_pin", "")
                                    apply()
                                }
                                token = ""
                                serverUrl = ""
                                appPin = ""
                                isAppUnlocked = false
                                stopWSService()
                            }
                        )
                    } else {
                        // App is unlocked and active
                        DashboardScreen(
                            token = token,
                            serverUrl = serverUrl,
                            onSignOut = {
                                sharedPref.edit().apply {
                                    putString("token", "")
                                    putString("server_url", "")
                                    apply()
                                }
                                token = ""
                                serverUrl = ""
                                isAppUnlocked = false
                                stopWSService()
                            }
                        )
                    }

                    // Render dialog layer overlay when approval events are received
                    if (isAppUnlocked && activeRequest != null) {
                        ApprovalDialog(request = activeRequest)
                    }
                }
            }
        }
    }

    private fun startWSService(url: String, token: String) {
        val intent = Intent(this, WSBackgroundService::class.java).apply {
            putExtra("server_url", url)
            putExtra("token", token)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopWSService() {
        val intent = Intent(this, WSBackgroundService::class.java)
        stopService(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}

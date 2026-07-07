package com.secretsafe.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import com.secretsafe.ui.screens.AppPinVerifyDialog
import com.secretsafe.ui.theme.SecretSafeTheme
import com.secretsafe.utils.BiometricHelper

class NotificationActionActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestId = intent.getStringExtra("request_id")
        if (requestId.isNullOrEmpty()) {
            finish()
            return
        }

        // Trigger biometrics or PIN verification immediately
        authenticateAndApprove(requestId)
    }

    private fun authenticateAndApprove(requestId: String) {
        BiometricHelper.authenticate(
            activity = this,
            title = "Approve Credential Request",
            subtitle = "Authenticate to release password to the requester",
            onSuccess = {
                approveRequest(requestId)
            },
            onFailure = { err ->
                // Fall back to Custom PIN Verification layout
                val sharedPref = getSharedPreferences("SecretSafePref", Context.MODE_PRIVATE)
                val appPin = sharedPref.getString("app_pin", "") ?: ""
                showPinFallback(requestId, appPin)
            }
        )
    }

    private fun approveRequest(requestId: String) {
        WSBackgroundService.sendResponse(requestId, true)

        // Cancel notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(WSBackgroundService.ALERT_NOTIFICATION_ID)

        // Show feedback notification if enabled in preferences
        val sharedPref = getSharedPreferences("SecretSafePref", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
        val notifyApproveDeny = sharedPref.getBoolean("notify_approve_deny", true)

        if (notificationsEnabled && notifyApproveDeny) {
            val responseNotification = androidx.core.app.NotificationCompat.Builder(this, WSBackgroundService.CHANNEL_ID)
                .setContentTitle("Request Handled")
                .setContentText("Approved credential request")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(3, responseNotification)
        }

        Toast.makeText(this, "Request approved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showPinFallback(requestId: String, appPin: String) {
        setContent {
            SecretSafeTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    AppPinVerifyDialog(
                        savedPin = appPin,
                        onDismiss = {
                            showDialog = false
                            finish()
                        },
                        onSuccess = {
                            showDialog = false
                            approveRequest(requestId)
                        }
                    )
                } else {
                    finish()
                }
            }
        }
    }
}

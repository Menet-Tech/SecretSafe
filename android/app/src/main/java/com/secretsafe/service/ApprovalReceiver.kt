package com.secretsafe.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra("request_id") ?: return
        val action = intent.action ?: return

        val approved = when (action) {
            "com.secretsafe.APPROVE" -> true
            "com.secretsafe.DENY" -> false
            else -> return
        }

        // Send response via running WebSocket background service
        WSBackgroundService.sendResponse(requestId, approved)

        // Cancel the alert notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(WSBackgroundService.ALERT_NOTIFICATION_ID)

        // Check if notify_approve_deny is enabled in preferences to send feedback notification
        val sharedPref = context.getSharedPreferences("SecretSafePref", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
        val notifyApproveDeny = sharedPref.getBoolean("notify_approve_deny", true)

        if (notificationsEnabled && notifyApproveDeny) {
            val statusText = if (approved) "Approved credential request" else "Denied credential request"
            val responseNotification = NotificationCompat.Builder(context, WSBackgroundService.CHANNEL_ID)
                .setContentTitle("Request Handled")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(3, responseNotification)
        }
    }
}

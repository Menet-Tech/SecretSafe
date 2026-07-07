package com.secretsafe.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.secretsafe.MainActivity
import com.secretsafe.api.WSMessage
import com.secretsafe.api.NetworkClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.util.concurrent.TimeUnit

class WSBackgroundService : Service() {
    private val client = NetworkClient.getUnsafeOkHttpClientBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isRunning = false
    private var serverUrl = ""
    private var token = ""

    companion object {
        const val CHANNEL_ID = "SecretSafeServiceChannel"
        const val ALERT_CHANNEL_ID = "SecretSafeAlertChannel"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2

        // Live Compose States for UI bindings
        val isConnected = mutableStateOf(false)
        val currentRequest = mutableStateOf<WSMessage?>(null)
        
        var serviceInstance: WSBackgroundService? = null

        fun sendResponse(requestId: String, approved: Boolean) {
            serviceInstance?.respondToRequest(requestId, approved)
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverUrl = intent?.getStringExtra("server_url") ?: ""
        token = intent?.getStringExtra("token") ?: ""

        if (serverUrl.isNotEmpty() && token.isNotEmpty() && !isRunning) {
            isRunning = true
            startForegroundServiceNotification()
            connectWebSocket()
        }

        return START_REDELIVER_INTENT
    }

    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = if (isConnected.value) "Connected to Security Server" else "Connecting..."

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SecretSafe Shield Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateServiceNotification(status: String) {
        val sharedPref = getSharedPreferences("SecretSafePref", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
        val notifyConnected = sharedPref.getBoolean("notify_connected", true)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Show status only if notifications and connection alerts are enabled, otherwise show generic title
        val textToShow = if (notificationsEnabled && notifyConnected) {
            status
        } else {
            "SecretSafe protection active"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SecretSafe Shield Active")
            .setContentText(textToShow)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun connectWebSocket() {
        if (!isRunning) return

        // Translate http/https scheme into websocket ws/wss protocols
        val wsUrl = "${serverUrl.replace("http://", "ws://").replace("https://", "wss://").trimEnd('/')}/api/ws?token=$token"
        val request = Request.Builder().url(wsUrl).build()

        val listener = SecureWSListener(
            onConnected = {
                isConnected.value = true
                updateServiceNotification("Connected to Security Server")
            },
            onDisconnected = {
                isConnected.value = false
                updateServiceNotification("Disconnected from Server. Retrying...")
                // Retry connection loop in separate thread
                Thread {
                    Thread.sleep(5000)
                    if (isRunning) connectWebSocket()
                }.start()
            },
            onApprovalRequestReceived = { requestMsg ->
                currentRequest.value = requestMsg
                showApprovalAlertNotification(requestMsg)
            }
        )

        webSocket = client.newWebSocket(request, listener)
    }

    private fun showApprovalAlertNotification(msg: WSMessage) {
        val sharedPref = getSharedPreferences("SecretSafePref", Context.MODE_PRIVATE)
        val notificationsEnabled = sharedPref.getBoolean("notifications_enabled", true)
        val notifyRequest = sharedPref.getBoolean("notify_request", true)

        // Do not display if user has disabled notifications or request notifications in settings
        if (!notificationsEnabled || !notifyRequest) {
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Approve Intent
        val approveIntent = Intent(this, ApprovalReceiver::class.java).apply {
            action = "com.secretsafe.APPROVE"
            putExtra("request_id", msg.request_id)
        }
        val approvePendingIntent = PendingIntent.getBroadcast(
            this, 100, approveIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Deny Intent
        val denyIntent = Intent(this, ApprovalReceiver::class.java).apply {
            action = "com.secretsafe.DENY"
            putExtra("request_id", msg.request_id)
        }
        val denyPendingIntent = PendingIntent.getBroadcast(
            this, 101, denyIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alertText = "Host: ${msg.host} wants to retrieve credentials for '${msg.credential_name}'"

        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Credential Approval Requested")
            .setContentText(alertText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_ok, "Approve", approvePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Deny", denyPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification)
    }

    fun respondToRequest(requestId: String, approved: Boolean) {
        val responseMsg = WSMessage(
            type = "approval_response",
            request_id = requestId,
            approved = approved
        )
        val json = Gson().toJson(responseMsg)
        
        Thread {
            webSocket?.send(json)
        }.start()
        
        currentRequest.value = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Shield Status Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Approval Alerts Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        webSocket?.close(1000, "Service destroyed")
        isConnected.value = false
        serviceInstance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

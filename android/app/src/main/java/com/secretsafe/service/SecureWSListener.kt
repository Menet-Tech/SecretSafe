package com.secretsafe.service

import com.google.gson.Gson
import com.secretsafe.api.WSMessage
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class SecureWSListener(
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onApprovalRequestReceived: (WSMessage) -> Unit
) : WebSocketListener() {
    private val gson = Gson()

    override fun onOpen(webSocket: WebSocket, response: Response) {
        onConnected()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val msg = gson.fromJson(text, WSMessage::class.java)
            if (msg.type == "approval_request") {
                onApprovalRequestReceived(msg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        onDisconnected()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        onDisconnected()
    }
}

package com.secretsafe.api

data class LoginResponse(
    val token: String,
    val username: String,
    val role: String
)

data class CredentialItem(
    val id: Int,
    val name: String,
    val username: String,
    val address: String? = null,
    val created_at: String
)

data class WSMessage(
    val type: String, // "approval_request", "approval_response", "ping", "pong", "error"
    val request_id: String? = null,
    val credential_name: String? = null,
    val username: String? = null,
    val host: String? = null,
    val approved: Boolean? = null
)

data class DecryptedCredentialResponse(
    val id: Int,
    val name: String,
    val username: String,
    val password: String,
    val address: String? = null
)

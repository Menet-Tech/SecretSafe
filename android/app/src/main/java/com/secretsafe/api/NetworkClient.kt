package com.secretsafe.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkClient {
    fun getUnsafeOkHttpClientBuilder(): OkHttpClient.Builder {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            val builder = OkHttpClient.Builder()
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
            return builder
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private val client = getUnsafeOkHttpClientBuilder().build()
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun login(username: String, password: String, serverUrl: String, callback: (Result<LoginResponse>) -> Unit) {
        val url = "${serverUrl.trimEnd('/')}/api/auth/login"
        val jsonPayload = gson.toJson(mapOf("username" to username, "password" to password))
        val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(Result.failure(Exception("Login failed: ${response.code}")))
                        return
                    }
                    val bodyStr = response.body?.string()
                    try {
                        val loginResp = gson.fromJson(bodyStr, LoginResponse::class.java)
                        callback(Result.success(loginResp))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun fetchCredentials(token: String, serverUrl: String, callback: (Result<List<CredentialItem>>) -> Unit) {
        val url = "${serverUrl.trimEnd('/')}/api/credentials"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(Result.failure(Exception("Fetch failed: ${response.code}")))
                        return
                    }
                    val bodyStr = response.body?.string()
                    try {
                        val type = object : TypeToken<List<CredentialItem>>() {}.type
                        val items = gson.fromJson<List<CredentialItem>>(bodyStr, type)
                        callback(Result.success(items))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun addCredential(
        token: String,
        serverUrl: String,
        name: String,
        username: String,
        secret: String,
        address: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val url = "${serverUrl.trimEnd('/')}/api/credentials"
        val payload = mapOf(
            "name" to name,
            "username" to username,
            "password" to secret,
            "address" to address
        )
        val jsonPayload = gson.toJson(payload)
        val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(Result.failure(Exception("Failed to add: ${response.code}")))
                        return
                    }
                    callback(Result.success(Unit))
                }
            }
        })
    }

    fun retrieveCredential(
        token: String,
        serverUrl: String,
        id: Int,
        callback: (Result<DecryptedCredentialResponse>) -> Unit
    ) {
        val url = "${serverUrl.trimEnd('/')}/api/credentials/$id/retrieve?host=Android-App"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(Result.failure(Exception("Failed to retrieve: ${response.code}")))
                        return
                    }
                    val bodyStr = response.body?.string()
                    try {
                        val resp = gson.fromJson(bodyStr, DecryptedCredentialResponse::class.java)
                        callback(Result.success(resp))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }
}

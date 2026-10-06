package com.example.haritalar.data.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LiveSharingClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    data class Session(val id: String, val token: String, val viewerUrl: String, val expiresAt: String)

    suspend fun createSession(latitude: Double, longitude: Double, bearing: Float?, speedKmh: Float?, etaSeconds: Long?): Session =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("latitude", latitude)
                put("longitude", longitude)
                bearing?.let { put("bearing", it) }
                speedKmh?.let { put("speedKmh", it) }
                etaSeconds?.let { put("etaSeconds", it) }
            }
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/sessions")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            // Session creation is intentionally not retried because POST is not assumed idempotent.
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("live-share-create-${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                Session(
                    id = json.getString("sessionId"),
                    token = json.getString("token"),
                    viewerUrl = json.getString("viewerUrl"),
                    expiresAt = json.getString("expiresAt")
                )
            }
        }

    suspend fun updateLocation(sessionId: String, token: String, latitude: Double, longitude: Double, bearing: Float?, speedKmh: Float?, etaSeconds: Long?) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("latitude", latitude)
                put("longitude", longitude)
                bearing?.let { put("bearing", it) }
                speedKmh?.let { put("speedKmh", it) }
                etaSeconds?.let { put("etaSeconds", it) }
            }
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/v1/sessions/$sessionId")
                .header("Authorization", "Bearer $token")
                .put(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            executeWithRetry(request, "live-share-update") { response ->
                if (!response.isSuccessful) error("live-share-update-${response.code}")
            }
        }

    suspend fun revoke(sessionId: String, token: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/sessions/$sessionId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        executeWithRetry(request, "live-share-revoke") { response ->
            if (!response.isSuccessful) error("live-share-revoke-${response.code}")
        }
    }

    private suspend fun <T> executeWithRetry(
        request: Request,
        operation: String,
        block: (okhttp3.Response) -> T
    ): T {
        var attempt = 1
        var last: Throwable? = null
        while (attempt <= LiveSharingRetryPolicy.MAX_ATTEMPTS) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return block(response)
                    if (!LiveSharingRetryPolicy.shouldRetryHttp(response.code) || attempt == LiveSharingRetryPolicy.MAX_ATTEMPTS) {
                        return block(response)
                    }
                    last = IllegalStateException("$operation-${response.code}")
                }
            } catch (e: IOException) {
                last = e
                if (attempt == LiveSharingRetryPolicy.MAX_ATTEMPTS) throw e
            }
            delay(LiveSharingRetryPolicy.backoffMs(attempt))
            attempt++
        }
        throw last ?: IllegalStateException(operation)
    }
}

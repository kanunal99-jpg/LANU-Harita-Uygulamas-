package com.example.haritalar.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LiveSharingClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient()
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
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("live-share-update-${response.code}")
            }
        }

    suspend fun revoke(sessionId: String, token: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/sessions/$sessionId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("live-share-revoke-${response.code}")
        }
    }
}

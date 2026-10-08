package com.example.haritalar.data.network

import com.example.haritalar.model.SafetyCameraBoundingBox
import com.example.haritalar.model.SafetyCameraFetchResult
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real SafetyCameraService request pipeline with an in-memory HTTP interceptor.
 * No Overpass network, rate limits, API keys or paid mocking dependencies.
 */
class SafetyCameraMirrorFallbackTest {
    private val bbox = SafetyCameraBoundingBox(40.9, 28.9, 41.1, 29.1)
    private val hosts = listOf(
        "https://primary.lanu-test.invalid/api/interpreter",
        "https://mirror.lanu-test.invalid/api/interpreter",
        "https://last.lanu-test.invalid/api/interpreter"
    )

    private data class FakeReply(val status: Int, val body: String)

    private fun service(
        seen: MutableList<String>,
        responses: Map<String, FakeReply>
    ): SafetyCameraService {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            seen += host
            val reply = responses[host] ?: FakeReply(503, "missing mock")
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(reply.status)
                .message("mock")
                .body(reply.body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return SafetyCameraService(client = client, endpoints = hosts)
    }

    @Test
    fun badHttp200RemarkFailsOverToSecondMirrorAndKeepsSourceIdentity() = runBlocking {
        val seen = mutableListOf<String>()
        val client = service(
            seen,
            mapOf(
                "primary.lanu-test.invalid" to FakeReply(
                    200, """{"remark":"runtime error: Query timed out","elements":[]}"""
                ),
                "mirror.lanu-test.invalid" to FakeReply(
                    200,
                    """{"elements":[{"type":"node","id":837,"lat":41.0,"lon":29.0,"tags":{"highway":"speed_camera"}}]}"""
                ),
                "last.lanu-test.invalid" to FakeReply(500, "unused")
            )
        )
        val result = client.fetchSpeedCamerasInBoundingBox(bbox)
        assertTrue(result is SafetyCameraFetchResult.Success)
        val success = result as SafetyCameraFetchResult.Success
        assertEquals(listOf("primary.lanu-test.invalid", "mirror.lanu-test.invalid"), seen)
        assertEquals(hosts[1], success.endpointUsed)
        assertEquals(listOf(837L), success.cameras.map { it.id })
        assertFalse(success.fromCache)
    }

    @Test
    fun http503FallsBackToThirdMirrorWithLegitimateZeroResults() = runBlocking {
        val seen = mutableListOf<String>()
        val client = service(
            seen,
            mapOf(
                "primary.lanu-test.invalid" to FakeReply(503, "unavailable"),
                "mirror.lanu-test.invalid" to FakeReply(200, """{"elements":"invalid"}"""),
                "last.lanu-test.invalid" to FakeReply(200, """{"elements":[]}""")
            )
        )
        val result = client.fetchSpeedCamerasInBoundingBox(bbox)
        assertTrue(result is SafetyCameraFetchResult.Success)
        val success = result as SafetyCameraFetchResult.Success
        assertEquals(hosts[2], success.endpointUsed)
        assertTrue(success.cameras.isEmpty())
        assertEquals(3, seen.size)
    }

    @Test
    fun everyMirrorReturningMalformedDataIsExplicitErrorNotVerifiedEmpty() = runBlocking {
        val seen = mutableListOf<String>()
        val client = service(
            seen,
            mapOf(
                "primary.lanu-test.invalid" to FakeReply(200, "<html>error</html>"),
                "mirror.lanu-test.invalid" to FakeReply(200, """{"elements":null}"""),
                "last.lanu-test.invalid" to FakeReply(200, """{"remark":"out of memory","elements":[]}""")
            )
        )
        val result = client.fetchSpeedCamerasInBoundingBox(bbox)
        assertTrue(result is SafetyCameraFetchResult.Error)
        val failure = result as SafetyCameraFetchResult.Error
        assertTrue(failure.isNetworkError)
        assertEquals(3, seen.size)
        assertTrue(failure.fallbackCameras.isEmpty())
    }

    @Test
    fun invalidBoundingBoxNeverContactsAnyProvider() = runBlocking {
        val seen = mutableListOf<String>()
        val client = service(seen, emptyMap())
        val result = client.fetchSpeedCamerasInBoundingBox(
            SafetyCameraBoundingBox(50.0, 29.0, 40.0, 30.0)
        )
        assertTrue(result is SafetyCameraFetchResult.Error)
        assertTrue(seen.isEmpty())
    }
}

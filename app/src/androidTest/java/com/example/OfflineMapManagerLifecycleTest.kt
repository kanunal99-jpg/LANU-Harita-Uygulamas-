package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.haritalar.data.offline.OfflineMapManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.geometry.LatLngBounds

class OfflineMapManagerLifecycleTest {
    @Test
    fun closePreventsNewDownloadRequestsAndStateMutation() = runBlocking {
        val manager = OfflineMapManager(ApplicationProvider.getApplicationContext<Context>())
        manager.close()

        val bounds = LatLngBounds.Builder()
            .include(org.maplibre.android.geometry.LatLng(41.0082, 28.9784))
            .include(org.maplibre.android.geometry.LatLng(41.0102, 28.9824))
            .build()

        manager.downloadRegion(
            styleUrl = "https://example.invalid/style.json",
            bounds = bounds,
            minZoom = 10.0,
            maxZoom = 12.0,
            pixelRatio = 1.0f
        )

        assertNull(manager.downloadProgress.first())
        assertNull(manager.downloadMessage.first())
    }
}

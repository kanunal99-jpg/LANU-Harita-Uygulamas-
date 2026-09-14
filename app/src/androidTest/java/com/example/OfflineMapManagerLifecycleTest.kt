package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.haritalar.data.offline.OfflineMapManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Test

class OfflineMapManagerLifecycleTest {
    @Test
    fun closeCancelsCallbackScope() {
        val manager = OfflineMapManager(ApplicationProvider.getApplicationContext<Context>())
        val field = OfflineMapManager::class.java.getDeclaredField("callbackScope").apply { isAccessible = true }
        val scope = field.get(manager) as CoroutineScope
        manager.close()
        assertFalse((scope.coroutineContext[Job] ?: error("callback scope job missing")).isActive)
    }
}

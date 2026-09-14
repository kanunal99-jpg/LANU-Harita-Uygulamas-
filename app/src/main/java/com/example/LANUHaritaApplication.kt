package com.example

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class LANUHaritaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (isRobolectricRuntime()) return
        MapLibre.getInstance(this, "", WellKnownTileServer.MapLibre)
    }

    private fun isRobolectricRuntime(): Boolean = try {
        Class.forName("org.robolectric.Robolectric")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
}

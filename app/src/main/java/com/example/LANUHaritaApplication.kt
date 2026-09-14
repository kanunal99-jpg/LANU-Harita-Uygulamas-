package com.example

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class LANUHaritaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this, "", WellKnownTileServer.MapLibre)
    }
}

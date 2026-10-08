package com.example.haritalar.navigation

/** Publish partial route camera data early, without marking the whole route complete. */
object SafetyCameraRouteProgressPolicy {
    private const val CENTERS_PER_BATCH = 6

    fun shouldPublishPartial(index: Int, totalCenters: Int): Boolean =
        index >= 0 &&
            totalCenters > 1 &&
            index < totalCenters - 1 &&
            (index == 0 || (index + 1) % CENTERS_PER_BATCH == 0)
}

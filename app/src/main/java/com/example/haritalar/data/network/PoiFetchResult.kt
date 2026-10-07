package com.example.haritalar.data.network

import com.example.haritalar.model.PoiItem

sealed class PoiFetchResult {
    data class Success(
        val pois: List<PoiItem>,
        val endpointUsed: String
    ) : PoiFetchResult()

    data class Error(
        val message: String,
        val isNetworkError: Boolean
    ) : PoiFetchResult()
}

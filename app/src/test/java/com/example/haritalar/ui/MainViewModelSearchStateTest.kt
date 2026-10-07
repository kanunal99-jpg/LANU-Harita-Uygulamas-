package com.example.haritalar.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.haritalar.model.AddressResultType
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.NavigationState
import com.example.haritalar.model.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainViewModelSearchStateTest {
    @Test
    fun selectingResultClosesSearchAndDoesNotLeakAnOldRouteState() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(application)
        val result = SearchResult(
            id = "aydinli",
            name = "Aydınlı Mahallesi",
            displayName = "Aydınlı Mahallesi, Tuzla, İstanbul, 34953, Türkiye",
            point = GeoPoint(40.88, 29.32),
            resultType = AddressResultType.NEIGHBORHOOD
        )

        viewModel.selectSearchResult(result)
        val selectedState = viewModel.uiState.value

        assertFalse(selectedState.isSearchFocused)
        assertTrue(selectedState.isDestinationCardVisible)
        assertEquals(result, selectedState.selectedDestination)
        assertTrue(selectedState.routeOptions.isEmpty())
        assertNull(selectedState.selectedRoute)
        assertEquals(NavigationState.IDLE, selectedState.navigationState)

        viewModel.onSearchFocusChanged(true)
        val searchState = viewModel.uiState.value

        assertTrue(searchState.isSearchFocused)
        assertNull(searchState.selectedDestination)
        assertFalse(searchState.isDestinationCardVisible)
        assertTrue(searchState.routeOptions.isEmpty())
        assertNull(searchState.selectedRoute)
        assertEquals(NavigationState.IDLE, searchState.navigationState)
    }
}

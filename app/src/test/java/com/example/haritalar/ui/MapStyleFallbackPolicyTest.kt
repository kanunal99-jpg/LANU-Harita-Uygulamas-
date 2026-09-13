package com.example.haritalar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleFallbackPolicyTest {
    @Test
    fun candidates_keep_primary_style_first() {
        assertEquals(MapStyleFallbackPolicy.PRIMARY_STYLE_URL, MapStyleFallbackPolicy.candidates.first())
    }

    @Test
    fun candidates_include_backup_style_after_primary() {
        assertEquals(
            MapStyleFallbackPolicy.BACKUP_STYLE_URL,
            MapStyleFallbackPolicy.candidates[1]
        )
    }

    @Test
    fun candidates_end_with_bundled_safe_default() {
        assertEquals(
            MapStyleFallbackPolicy.SAFE_DEFAULT_STYLE_URI,
            MapStyleFallbackPolicy.candidates.last()
        )
        assertTrue(MapStyleFallbackPolicy.candidates.size >= 3)
    }

    @Test
    fun candidates_form_an_ordered_three_step_chain() {
        assertEquals(3, MapStyleFallbackPolicy.candidates.size)
        assertEquals(
            listOf(
                MapStyleFallbackPolicy.PRIMARY_STYLE_URL,
                MapStyleFallbackPolicy.BACKUP_STYLE_URL,
                MapStyleFallbackPolicy.SAFE_DEFAULT_STYLE_URI
            ),
            MapStyleFallbackPolicy.candidates
        )
    }
}

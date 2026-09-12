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
        assertTrue(MapStyleFallbackPolicy.candidates.size >= 2)
    }
}

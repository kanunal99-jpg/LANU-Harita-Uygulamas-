package com.example.haritalar.navigation

import com.example.haritalar.model.LaneDirection
import com.example.haritalar.model.LaneInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneGuidanceHelperTest {
    @Test
    fun emptyLanes_haveNoVoiceHint() {
        assertNull(LaneGuidanceHelper.buildLaneVoiceHint(emptyList()))
    }

    @Test
    fun allActiveLanes_haveNoVoiceHint() {
        val lanes = listOf(
            LaneInfo(listOf(LaneDirection.STRAIGHT), true),
            LaneInfo(listOf(LaneDirection.STRAIGHT), true)
        )
        assertNull(LaneGuidanceHelper.buildLaneVoiceHint(lanes))
    }

    @Test
    fun rightSideActiveLanes_produceRightHint() {
        val lanes = listOf(
            LaneInfo(listOf(LaneDirection.STRAIGHT), false),
            LaneInfo(listOf(LaneDirection.RIGHT), true),
            LaneInfo(listOf(LaneDirection.RIGHT), true)
        )
        val hint = LaneGuidanceHelper.buildLaneVoiceHint(lanes)
        assertTrue(hint?.contains("Sağ") == true)
    }

    @Test
    fun leftSideActiveLanes_produceLeftHint() {
        val lanes = listOf(
            LaneInfo(listOf(LaneDirection.LEFT), true),
            LaneInfo(listOf(LaneDirection.LEFT), true),
            LaneInfo(listOf(LaneDirection.STRAIGHT), false)
        )
        assertEquals("Sol şeritleri kullanın", LaneGuidanceHelper.buildLaneVoiceHint(lanes))
    }
}

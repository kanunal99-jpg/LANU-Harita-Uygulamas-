package com.example.haritalar.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedPlacePolicyTest {
    @Test
    fun supportsHomeWorkAndCustomOnly() {
        assertEquals("HOME", SavedPlacePolicy.normalizeCategory("home"))
        assertEquals("WORK", SavedPlacePolicy.normalizeCategory(" WORK "))
        assertEquals("CUSTOM", SavedPlacePolicy.normalizeCategory("custom"))
        assertNull(SavedPlacePolicy.normalizeCategory("OTHER"))
    }

    @Test
    fun blocksTheFiveHundredAndFirstCustomPlace() {
        assertEquals(
            SavedPlaceResult.CAPACITY_REACHED,
            SavedPlacePolicy.decision(
                currentCount = 500,
                category = "CUSTOM",
                hasExistingSingleCategory = false,
                exactDuplicate = false
            )
        )
    }

    @Test
    fun homeAndWorkCanReplaceAtCapacityWithoutGrowingTheDatabase() {
        assertEquals(
            SavedPlaceResult.REPLACED,
            SavedPlacePolicy.decision(
                currentCount = 500,
                category = "HOME",
                hasExistingSingleCategory = true,
                exactDuplicate = false
            )
        )
    }

    @Test
    fun exactDuplicatesAreRejected() {
        assertEquals(
            SavedPlaceResult.DUPLICATE,
            SavedPlacePolicy.decision(
                currentCount = 12,
                category = "CUSTOM",
                hasExistingSingleCategory = false,
                exactDuplicate = true
            )
        )
    }
}

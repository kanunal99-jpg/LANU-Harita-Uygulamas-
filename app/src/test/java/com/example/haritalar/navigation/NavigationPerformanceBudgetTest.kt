package com.example.haritalar.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPerformanceBudgetTest {
    @Test
    fun navigationUpdateBudgetRejectsSlowWork() {
        assertTrue(NavigationPerformanceBudget.isNavigationUpdateWithinBudget(50))
        assertFalse(NavigationPerformanceBudget.isNavigationUpdateWithinBudget(51))
    }

    @Test
    fun routeGenerationBudgetIsExplicit() {
        assertTrue(NavigationPerformanceBudget.isRouteGenerationWithinBudget(15_000))
        assertFalse(NavigationPerformanceBudget.isRouteGenerationWithinBudget(15_001))
    }
}

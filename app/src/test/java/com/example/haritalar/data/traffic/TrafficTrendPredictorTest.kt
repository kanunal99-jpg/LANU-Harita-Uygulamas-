package com.example.haritalar.data.traffic

import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficTrendPredictorTest {

    @Test
    fun testContinuousTrendFactors() {
        val midnightFactor = TrafficTrendPredictor.calculateContinuousTrendFactor(2)
        val morningFactor = TrafficTrendPredictor.calculateContinuousTrendFactor(8)
        val afternoonFactor = TrafficTrendPredictor.calculateContinuousTrendFactor(13)
        val eveningFactor = TrafficTrendPredictor.calculateContinuousTrendFactor(18)

        // Peak hours must produce higher congestion factors than off-peak hours
        assertTrue("Morning peak should be higher than midnight", morningFactor > midnightFactor)
        assertTrue("Evening peak should be higher than midday/afternoon", eveningFactor > afternoonFactor)
        assertTrue("Evening peak is typically the highest congestion period", eveningFactor > morningFactor)
        assertTrue("Midnight should be very close to free flow factor", midnightFactor < 1.1)
    }

    @Test
    fun testPredictTrafficForHourNoLiveTraffic() {
        // Test predictions for different hours when live traffic is null
        val morningPrediction = TrafficTrendPredictor.predictTrafficForHour(8, null)
        val nightPrediction = TrafficTrendPredictor.predictTrafficForHour(3, null)

        assertEquals(8, morningPrediction.hour)
        assertEquals(3, nightPrediction.hour)

        assertTrue(morningPrediction.congestionMultiplier > 1.8)
        assertTrue(nightPrediction.congestionMultiplier < 1.1)

        assertEquals(TrafficLevel.HEAVY, morningPrediction.predictedTrafficLevel)
        assertEquals(TrafficLevel.LOW, nightPrediction.predictedTrafficLevel)

        assertNotNull(morningPrediction.advice)
        assertNotNull(nightPrediction.advice)
    }

    @Test
    fun testPredictTrafficForHourWithLiveTrafficCalibration() {
        // Create an active live traffic status indicating severe congestion (+10 minutes of delay)
        val liveStatus = TrafficStatus(
            verified = true,
            message = "TomTom Canlı Trafik • +10 dk gecikme",
            delaySeconds = 600, // 10 minutes delay
            trafficLevel = TrafficLevel.HEAVY,
            segmentCount = 6, // 6 segments x 100s = 600s baseline free-flow
            isLiveApi = true
        )

        // Calculate prediction for peak hour with live traffic calibration
        val morningPredictionWithLive = TrafficTrendPredictor.predictTrafficForHour(8, liveStatus)

        // The calibrated congestion multiplier should be significantly higher due to severe live traffic feedback
        assertTrue("Congestion multiplier should reflect the severe live delay", morningPredictionWithLive.congestionMultiplier > 1.9)
        assertEquals(TrafficLevel.SEVERE, morningPredictionWithLive.predictedTrafficLevel)
        assertTrue("Delay estimation should be calculated", morningPredictionWithLive.predictedDelaySeconds > 0)
    }
}

package com.example.haritalar.data.traffic

import com.example.haritalar.model.TrafficLevel
import com.example.haritalar.model.TrafficStatus
import kotlin.math.abs
import kotlin.math.exp

/**
 * Advanced Traffic Analysis & Prediction Engine.
 * Combines continuous hourly peaks (Gaussian density modeling of morning and evening rush hours)
 * with real-time feedback calibration from live TomTom flow data.
 */
object TrafficTrendPredictor {

    data class TrendPeriod(
        val name: String,
        val timeRange: String,
        val startHour: Int,
        val endHour: Int,
        val baseDelayFactor: Double,
        val trafficLevel: TrafficLevel
    )

    val periods = listOf(
        TrendPeriod("Gece / Sakin Yol", "22:00 - 07:30", 22, 7, 0.9, TrafficLevel.LOW),
        TrendPeriod("Sabah Yoğunluğu", "07:30 - 09:30", 7, 9, 2.1, TrafficLevel.HEAVY),
        TrendPeriod("Gün Ortası Akışı", "09:30 - 17:00", 9, 17, 1.3, TrafficLevel.MODERATE),
        TrendPeriod("Akşam Yoğunluğu", "17:00 - 19:30", 17, 19, 2.5, TrafficLevel.SEVERE),
        TrendPeriod("Akşamüstü Geçişi", "19:30 - 22:00", 19, 22, 1.1, TrafficLevel.LOW)
    )

    data class PredictionResult(
        val hour: Int,
        val periodName: String,
        val timeRange: String,
        val predictedDelaySeconds: Long,
        val predictedTrafficLevel: TrafficLevel,
        val congestionMultiplier: Double,
        val advice: String
    )

    /**
     * Estimates the continuous trend coefficient for a specific hour (0-23) using Gaussian density spikes
     * around typical peak hours (08:30 and 18:00) with a baseline off-peak level.
     */
    fun calculateContinuousTrendFactor(hour: Int): Double {
        // Morning rush peak at 08:30 (8.5) with spread (standard deviation) of 1.25 hours
        val distToMorningPeak = abs(hour - 8.5)
        val morningInfluence = exp(-distToMorningPeak * distToMorningPeak / (2 * 1.25 * 1.25))

        // Evening rush peak at 18:00 (18.0) with spread of 1.5 hours
        val distToEveningPeak = abs(hour - 18.0)
        val eveningInfluence = exp(-distToEveningPeak * distToEveningPeak / (2 * 1.5 * 1.5))

        // Midday constant slight elevation
        val middayInfluence = if (hour in 10..16) 0.25 else 0.0

        // Base free flow factor is 1.0. Rush hours scale up the travel times.
        return 1.0 + (1.1 * morningInfluence) + (1.5 * eveningInfluence) + middayInfluence
    }

    /**
     * Predicts traffic conditions and potential delays for a specific time of day.
     * Incorporates current real-time traffic feedback to dynamically scale predictions.
     */
    fun predictTrafficForHour(hour: Int, currentStatus: TrafficStatus?): PredictionResult {
        val period = findPeriodForHour(hour)
        val trendFactor = calculateContinuousTrendFactor(hour)

        // Find reference travel time of active route (e.g., from verified segments or a 20-min fallback)
        val baseTravelTimeSec = if (currentStatus != null && currentStatus.segmentCount > 0) {
            // Assume 100 seconds per segment as free flow if not measurable
            val defaultDuration = currentStatus.segmentCount * 100L
            if (currentStatus.delaySeconds > 0) {
                // If there's live delay, subtract it to isolate the baseline free-flow travel time
                (defaultDuration - currentStatus.delaySeconds).coerceAtLeast(300L)
            } else {
                defaultDuration
            }
        } else {
            1200L // 20 minutes default reference
        }

        // Calibrate historical prediction using active real-time traffic status
        val calendar = java.util.Calendar.getInstance()
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentLiveDelay = currentStatus?.delaySeconds ?: 0L

        val liveAdjustment = if (currentLiveDelay > 0 && currentHour == hour) {
            // Direct alignment with the active real-time delay
            (baseTravelTimeSec + currentLiveDelay).toDouble() / baseTravelTimeSec
        } else if (currentLiveDelay > 0) {
            // Dynamically scale prediction factors by comparing live delays at current hour
            // against current hour's normal trend
            val liveFactor = (baseTravelTimeSec + currentLiveDelay).toDouble() / baseTravelTimeSec
            val expectedTrendAtCurrentHour = calculateContinuousTrendFactor(currentHour)
            val realTimeCalibrationScale = liveFactor / expectedTrendAtCurrentHour
            
            // Adjust the selected hour's trend factor proportionally
            (trendFactor * realTimeCalibrationScale).coerceIn(0.8, 4.0)
        } else {
            trendFactor
        }

        val predictedDelaySec = if (liveAdjustment > 1.0) {
            ((liveAdjustment - 1.0) * baseTravelTimeSec).toLong()
        } else {
            0L
        }

        val predictedTrafficLevel = when {
            liveAdjustment < 1.20 -> TrafficLevel.LOW
            liveAdjustment < 1.55 -> TrafficLevel.MODERATE
            liveAdjustment < 2.15 -> TrafficLevel.HEAVY
            else -> TrafficLevel.SEVERE
        }

        val adviceText = getAdviceForTrend(hour, predictedTrafficLevel)

        return PredictionResult(
            hour = hour,
            periodName = period.name,
            timeRange = period.timeRange,
            predictedDelaySeconds = predictedDelaySec,
            predictedTrafficLevel = predictedTrafficLevel,
            congestionMultiplier = liveAdjustment,
            advice = adviceText
        )
    }

    private fun findPeriodForHour(hour: Int): TrendPeriod {
        return periods.firstOrNull { p ->
            if (p.startHour > p.endHour) {
                // Period spans across midnight, e.g. 22:00 to 07:30
                hour >= p.startHour || hour <= p.endHour
            } else {
                hour in p.startHour..p.endHour
            }
        } ?: periods.first()
    }

    private fun getAdviceForTrend(hour: Int, level: TrafficLevel): String {
        return when (level) {
            TrafficLevel.LOW -> "Yollar tamamen akıcı! Seyahat için ideal ve gecikmesiz bir zaman dilimi."
            TrafficLevel.MODERATE -> "Kısmi yavaşlamalar olabilir, ancak genel trafik akışı rahat ve sorunsuz."
            TrafficLevel.HEAVY -> "Belirgin yoğunluk artışı. Ana güzergahlar yerine alternatif ara sokakları tercih edebilirsiniz."
            TrafficLevel.SEVERE -> "Ciddi dur-kalk ve uzun kuyruklar! Seyahatinizi ertelemeniz veya metro/raylı sistem kullanmanız önerilir."
            TrafficLevel.UNKNOWN -> "Yol koşulları hakkında güncel tahmin bulunmamaktadır."
        }
    }
}

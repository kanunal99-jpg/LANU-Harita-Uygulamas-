package com.example.haritalar.navigation

import com.example.haritalar.model.DepartureTurn
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.RouteOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests covering all vehicle heading calculation, fallback, U-turn, departure guidance,
 * and wrong-way detection requirements.
 */
class VehicleHeadingManagerTest {

    private lateinit var fakeCompassSensor: FakeCompassSensor
    private lateinit var headingManager: VehicleHeadingManager

    class FakeCompassSensor : HeadingSensorProvider {
        private val _flow = MutableStateFlow(0f)
        override val sensorHeading: StateFlow<Float> = _flow
        private var fresh: Boolean = true

        fun emit(heading: Float?, isFresh: Boolean = true) {
            if (heading != null) {
                _flow.value = heading
            }
            this.fresh = (heading != null) && isFresh
        }

        override fun isFresh(maxAgeMs: Long): Boolean = fresh
        override fun start() {}
        override fun stop() {}
    }

    @Before
    fun setUp() {
        fakeCompassSensor = FakeCompassSensor()
        headingManager = VehicleHeadingManager(fakeCompassSensor)
    }

    // 1. Shortest Angle Delta & Wrap-around tests (359 -> 1 and 1 -> 359)
    @Test
    fun testShortestAngleDelta_wraparound() {
        // 359 to 1 should be +2 degrees (clockwise)
        val delta1 = VehicleHeadingManager.shortestAngleDelta(359f, 1f)
        assertEquals(2f, delta1, 0.001f)

        // 1 to 359 should be -2 degrees (counter-clockwise)
        val delta2 = VehicleHeadingManager.shortestAngleDelta(1f, 359f)
        assertEquals(-2f, delta2, 0.001f)

        // Exact 180 delta
        val delta180 = Math.abs(VehicleHeadingManager.shortestAngleDelta(0f, 180f))
        assertEquals(180f, delta180, 0.001f)

        // Normalize angle helper
        assertEquals(10f, VehicleHeadingManager.normalizeAngle(370f), 0.001f)
        assertEquals(350f, VehicleHeadingManager.normalizeAngle(-10f), 0.001f)
    }

    // 2. Relative Departure Angle Classification Tests
    @Test
    fun testRelativeDepartureAngle() {
        // vehicle 0° route 0° -> STRAIGHT
        assertEquals(DepartureTurn.STRAIGHT, VehicleHeadingManager.classifyDepartureTurn(0f, 0f))
        assertEquals(DepartureTurn.STRAIGHT, VehicleHeadingManager.classifyDepartureTurn(0f, 10f))

        // vehicle 0° route 90° -> RIGHT
        assertEquals(DepartureTurn.RIGHT, VehicleHeadingManager.classifyDepartureTurn(0f, 90f))

        // vehicle 0° route 45° -> SLIGHT_RIGHT
        assertEquals(DepartureTurn.SLIGHT_RIGHT, VehicleHeadingManager.classifyDepartureTurn(0f, 45f))

        // vehicle 0° route 130° -> SHARP_RIGHT
        assertEquals(DepartureTurn.SHARP_RIGHT, VehicleHeadingManager.classifyDepartureTurn(0f, 130f))

        // vehicle 0° route 270° -> LEFT
        assertEquals(DepartureTurn.LEFT, VehicleHeadingManager.classifyDepartureTurn(0f, 270f))

        // vehicle 0° route 315° -> SLIGHT_LEFT
        assertEquals(DepartureTurn.SLIGHT_LEFT, VehicleHeadingManager.classifyDepartureTurn(0f, 315f))

        // vehicle 0° route 230° -> SHARP_LEFT
        assertEquals(DepartureTurn.SHARP_LEFT, VehicleHeadingManager.classifyDepartureTurn(0f, 230f))

        // vehicle 0° route 180° -> UTURN
        assertEquals(DepartureTurn.UTURN, VehicleHeadingManager.classifyDepartureTurn(0f, 180f))
        assertEquals(DepartureTurn.UTURN, VehicleHeadingManager.classifyDepartureTurn(0f, 175f))
        assertEquals(DepartureTurn.UTURN, VehicleHeadingManager.classifyDepartureTurn(0f, 185f))
    }

    // 3. Departure Guidance Object Generation
    @Test
    fun testBuildDepartureGuidance() {
        val route = RouteOption(
            routeId = "test_route",
            title = "Hızlı Rota",
            summary = "Ana Yol",
            durationSeconds = 900L,
            distanceMeters = 8500.0,
            geometry = listOf(
                GeoPoint(41.000, 28.000),
                GeoPoint(41.000, 28.010) // Heading East ~90°
            ),
            maneuvers = emptyList()
        )

        // Vehicle pointing West (270°), route starts East (90°) -> Opposite direction -> U-turn / Wrong way
        val guidance = VehicleHeadingManager.buildDepartureGuidance(
            vehicleHeading = 270f,
            route = route,
            userPoint = GeoPoint(41.000, 28.000)
        )

        assertNotNull(guidance)
        assertEquals(DepartureTurn.UTURN, guidance.turnType)
        assertTrue(guidance.isWrongWay)
        assertTrue(guidance.instruction.contains("U dönüşü") || guidance.instruction.contains("ters"))
    }

    // 4. Wrong-way detection: 0° vs 180°
    @Test
    fun testWrongWayEvaluation() {
        // Vehicle traveling at speed (25 km/h) in opposite direction of route (requires 2 consecutive ticks for safety hysteresis)
        headingManager.evaluateWrongWay(0f, 180f, 25f) // Tick 1
        val isWrongWay = headingManager.evaluateWrongWay(
            currentHeading = 0f,
            routeSegmentBearing = 180f,
            speedKmh = 25f
        ) // Tick 2
        assertTrue("Heading 0° against route segment 180° must be flagged as wrong way", isWrongWay)

        // Vehicle traveling in same direction (0° vs 15°)
        val isCorrectWay = headingManager.evaluateWrongWay(
            currentHeading = 0f,
            routeSegmentBearing = 15f,
            speedKmh = 25f
        )
        assertFalse("Heading 0° along route segment 15° is valid", isCorrectWay)

        // At stop (speed < 7 km/h), do not falsely trigger wrong way alarm
        val stoppedOpposite = headingManager.evaluateWrongWay(
            currentHeading = 0f,
            routeSegmentBearing = 180f,
            speedKmh = 3f
        )
        assertFalse("Low speed parking/reversing should not trigger wrong way alarm", stoppedOpposite)
    }

    // 5. GPS -> Sensor Fallback Logic
    @Test
    fun testGpsToSensorFallback() {
        // Case A: Vehicle moving with accurate GPS heading (speed 30 km/h)
        val movingLoc = UserLocationData(
            point = GeoPoint(41.01, 28.97),
            accuracyMeters = 4.0f,
            speedKmh = 30.0f,
            bearing = 120.0f,
            isGpsWeak = false
        )
        val stateA = headingManager.processLocation(movingLoc)
        assertEquals(HeadingSource.GPS_BEARING, stateA.source)
        assertEquals(120.0f, stateA.heading, 1.0f)

        // Case B: Vehicle stops at traffic light (speed 0.5 km/h), GPS bearing becomes erratic.
        // Sensor supplies device heading (e.g. 135°)
        fakeCompassSensor.emit(135.0f, isFresh = true)
        val stoppedLoc = UserLocationData(
            point = GeoPoint(41.01, 28.97),
            accuracyMeters = 4.0f,
            speedKmh = 0.5f,
            bearing = 340.0f, // noisy stopped GPS bearing
            isGpsWeak = false
        )
        var stateB = headingManager.processLocation(stoppedLoc)
        assertEquals(HeadingSource.SENSOR, stateB.source)
        // With exponential moving average smoothing, verify heading converges towards 135°
        repeat(15) {
            stateB = headingManager.processLocation(stoppedLoc)
        }
        assertEquals(135.0f, stateB.heading, 2.0f)
    }

    // 6. Stale Heading fallback test
    @Test
    fun testStaleHeadingHandling() {
        // Set an initial heading
        val loc = UserLocationData(
            point = GeoPoint(41.01, 28.97),
            accuracyMeters = 5.0f,
            speedKmh = 40.0f,
            bearing = 85.0f,
            isGpsWeak = false
        )
        headingManager.processLocation(loc)

        // GPS becomes completely weak with no speed, sensor is stale
        fakeCompassSensor.emit(null, isFresh = false)
        val weakLoc = UserLocationData(
            point = GeoPoint(41.01, 28.97),
            accuracyMeters = 120.0f,
            speedKmh = 0.0f,
            bearing = 0.0f,
            isGpsWeak = true
        )
        val state = headingManager.processLocation(weakLoc)
        // Must fallback to LAST_KNOWN instead of jumping randomly to 0
        assertEquals(85.0f, state.heading, 2.0f)
    }

    // 7. Navigation session reset and reroute test
    @Test
    fun testResetAndReroute() {
        // Set state
        val loc = UserLocationData(
            point = GeoPoint(41.01, 28.97),
            accuracyMeters = 4.0f,
            speedKmh = 50.0f,
            bearing = 210.0f,
            isGpsWeak = false
        )
        headingManager.processLocation(loc)
        assertEquals(210.0f, headingManager.headingState.value.heading, 2.0f)

        // On reroute, buffer is cleared
        headingManager.onReroute()
        // On stop / reset session
        headingManager.resetSession()
        assertEquals(0.0f, headingManager.headingState.value.heading, 0.001f)
        assertEquals(HeadingSource.UNAVAILABLE, headingManager.headingState.value.source)
    }
}

package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.haritalar.model.GeoPoint
import com.example.haritalar.model.ManeuverType
import com.example.haritalar.model.TurnManeuver
import com.example.haritalar.navigation.NavigationProgress
import com.example.haritalar.ui.DrivingTopInstructionBanner
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [35])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleProgress = NavigationProgress(
      currentManeuver = TurnManeuver(
        instruction = "Atatürk Bulvarı yönünde sağa dönün",
        distanceMeters = 350.0,
        type = ManeuverType.RIGHT,
        point = GeoPoint(41.0082, 28.9784),
        roadName = "Atatürk Bulvarı"
      ),
      nextManeuver = null,
      distanceToManeuverMeters = 350.0,
      totalRemainingDistanceMeters = 12400.0,
      totalRemainingSeconds = 1080L,
      currentRoadName = "Atatürk Bulvarı"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        DrivingTopInstructionBanner(
          progress = sampleProgress,
          isOffRoute = false
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

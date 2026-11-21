package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.GpxExporter
import com.example.domain.model.GeoPoint
import com.example.domain.model.RecordedRide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Singletrack MTB", appName)
  }

  @Test
  fun `gpx exporter creates FileProvider share intent`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ride = RecordedRide(
      id = "r1",
      title = "Morning Trail Loop",
      trailName = "Ridge Trail",
      startTimeMillis = 1700000000000L,
      endTimeMillis = 1700000600000L,
      durationSeconds = 600L,
      distanceMiles = 3.4,
      avgSpeedMph = 11.2,
      maxSpeedMph = 18.5,
      elevationGainFeet = 420.0,
      routePoints = listOf(GeoPoint(38.57, -109.55), GeoPoint(38.58, -109.54))
    )

    val chooser = GpxExporter.buildShareGpxIntent(context, ride)
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    @Suppress("DEPRECATION")
    val inner = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
    assertNotNull(inner)
    assertEquals("application/gpx+xml", inner?.type)
    assertTrue((inner?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  }
}

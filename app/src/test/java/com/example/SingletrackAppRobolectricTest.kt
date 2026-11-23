package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Robolectric
import com.example.data.export.GpxExporter
import com.example.data.remote.SingletrackImageLoader
import com.example.domain.model.GeoPoint
import com.example.domain.model.RecordedRide
import com.example.domain.model.Trail
import com.example.domain.model.TrailDifficulty
import com.example.ui.components.TrailNavigationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SingletrackAppRobolectricTest {

    @Test
    fun `app name matches Singletrack MTB`() {
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

    @Test
    fun `activities restrict orientation to portrait in manifest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
        }
        val mainActivity = pkgInfo.activities?.firstOrNull { it.name == MainActivity::class.java.name }
        assertNotNull(mainActivity)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, mainActivity?.screenOrientation)
        @Suppress("DEPRECATION")
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            (mainActivity?.softInputMode ?: 0) and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        )
    }

    @Test
    fun `activities enable edge-to-edge display cutout mode`() {
        val controller = Robolectric.buildActivity(PermissionsRationaleActivity::class.java).setup()
        val activity = controller.get()
        val cutoutMode = activity.window.attributes.layoutInDisplayCutoutMode
        assertTrue(
            "Expected shortEdges or always cutout mode, got $cutoutMode",
            cutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES ||
                cutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        )
    }

    @Test
    fun `trail navigation helper builds geo navigation and share links`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val trail = Trail(
            osmKey = "way/42",
            osmType = "way",
            osmId = 42L,
            name = "Slickrock Practice Loop",
            difficulty = TrailDifficulty.INTERMEDIATE,
            rawDifficultyTag = "mtb:scale=2",
            surface = "Rock",
            centerLat = 38.57331,
            centerLon = -109.51944,
            distanceFromUserMiles = 1.8,
            bearingDegrees = 45.0,
            bearingCardinal = "NE",
            lengthMiles = 2.1,
            operatorName = null,
            networkName = null,
            description = null,
            oneway = null,
            uphillScale = null,
            isDesignatedMtb = true
        )

        val uris = TrailNavigationHelper.buildDirectionsUris(trail)
        assertEquals("geo", uris[0].scheme)
        assertEquals("google.navigation", uris[1].scheme)
        assertEquals("https", uris[2].scheme)
        val intent = TrailNavigationHelper.buildDirectionsIntent(context, trail)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(TrailNavigationHelper.buildShareDirectionsUrl(trail).contains("destination=38.57331,-109.51944"))
    }

    @Test
    fun `manifest does not declare foreground service or notification permissions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES).toLong()
                )
            )
        } else {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
            )
        }
        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse(requested.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertFalse(requested.contains("android.permission.FOREGROUND_SERVICE"))
        assertFalse(requested.contains("android.permission.FOREGROUND_SERVICE_LOCATION"))
        val services = pkgInfo.services.orEmpty()
        assertFalse(services.any { it.name.contains("RideRecordingService") })
        assertFalse(services.any { it.foregroundServiceType != 0 })
    }

    @Test
    fun `shared coil image loader initializes bounded memory and disk caches`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = SingletrackImageLoader.get(context)
        assertNotNull(loader.memoryCache)
        assertNotNull(loader.diskCache)
    }
}

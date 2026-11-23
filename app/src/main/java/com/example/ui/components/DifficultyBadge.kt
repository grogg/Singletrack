package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.TrailDifficulty
import com.example.ui.theme.DifficultyAdvancedColor
import com.example.ui.theme.DifficultyAdvancedDarkColor
import com.example.ui.theme.DifficultyEasyColor
import com.example.ui.theme.DifficultyExpertColor
import com.example.ui.theme.DifficultyIntermediateColor
import com.example.ui.theme.DifficultyUnratedColor
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun difficultyAccentColor(difficulty: TrailDifficulty): Color {
    val isDark = isSystemInDarkTheme()
    return when (difficulty) {
        TrailDifficulty.EASY -> DifficultyEasyColor
        TrailDifficulty.INTERMEDIATE -> DifficultyIntermediateColor
        TrailDifficulty.ADVANCED -> if (isDark) DifficultyAdvancedDarkColor else DifficultyAdvancedColor
        TrailDifficulty.EXPERT -> DifficultyExpertColor
        TrailDifficulty.UNRATED -> DifficultyUnratedColor
    }
}

@Composable
fun DifficultySymbol(
    difficulty: TrailDifficulty,
    modifier: Modifier = Modifier,
    symbolSize: Dp = 14.dp
) {
    val color = difficultyAccentColor(difficulty)
    val primaryPath = remember { Path() }
    val secondaryPath = remember { Path() }
    Canvas(
        modifier = modifier
            .size(symbolSize)
            .semantics { contentDescription = "${difficulty.label} difficulty symbol" }
    ) {
        val w = size.width
        val h = size.height
        when (difficulty) {
            TrailDifficulty.EASY -> {
                drawCircle(color = color, radius = w * 0.44f)
            }
            TrailDifficulty.INTERMEDIATE -> {
                val side = w * 0.78f
                val inset = (w - side) / 2f
                drawRect(
                    color = color,
                    topLeft = Offset(inset, inset),
                    size = Size(side, side)
                )
            }
            TrailDifficulty.ADVANCED -> {
                primaryPath.reset()
                primaryPath.moveTo(w / 2f, h * 0.06f)
                primaryPath.lineTo(w * 0.94f, h / 2f)
                primaryPath.lineTo(w / 2f, h * 0.94f)
                primaryPath.lineTo(w * 0.06f, h / 2f)
                primaryPath.close()
                drawPath(primaryPath, color = color)
            }
            TrailDifficulty.EXPERT -> {
                primaryPath.reset()
                primaryPath.moveTo(w * 0.30f, h * 0.14f)
                primaryPath.lineTo(w * 0.54f, h / 2f)
                primaryPath.lineTo(w * 0.30f, h * 0.86f)
                primaryPath.lineTo(w * 0.06f, h / 2f)
                primaryPath.close()

                secondaryPath.reset()
                secondaryPath.moveTo(w * 0.70f, h * 0.14f)
                secondaryPath.lineTo(w * 0.94f, h / 2f)
                secondaryPath.lineTo(w * 0.70f, h * 0.86f)
                secondaryPath.lineTo(w * 0.46f, h / 2f)
                secondaryPath.close()

                drawPath(primaryPath, color = color)
                drawPath(secondaryPath, color = color)
            }
            TrailDifficulty.UNRATED -> {
                drawCircle(color = color.copy(alpha = 0.25f), radius = w * 0.44f)
                drawCircle(color = color, radius = w * 0.22f)
            }
        }
    }
}

@Composable
fun DifficultyBadge(
    difficulty: TrailDifficulty,
    modifier: Modifier = Modifier,
    showFullLabel: Boolean = true
) {
    val accent = difficultyAccentColor(difficulty)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DifficultySymbol(difficulty = difficulty, symbolSize = 12.dp)
        Text(
            text = if (showFullLabel) difficulty.label else difficulty.shortLabel,
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
    }
}

object FormatUtils {
    const val MILES_TO_KM = 1.609344
    const val FEET_TO_METERS = 0.3048

    private val integerFormatter = ThreadLocal.withInitial {
        NumberFormat.getIntegerInstance(Locale.US)
    }

    @Volatile
    private var cachedLocale: Locale? = null

    @Volatile
    private var cachedTimestampFormatter: DateTimeFormatter? = null

    private fun timestampFormatter(): DateTimeFormatter {
        val current = Locale.getDefault()
        val existing = cachedTimestampFormatter
        if (existing != null && cachedLocale == current) {
            return existing
        }
        return DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", current).also {
            cachedLocale = current
            cachedTimestampFormatter = it
        }
    }

    fun distanceValue(miles: Double, unit: MeasurementUnit): Double {
        return when (unit) {
            MeasurementUnit.IMPERIAL -> miles
            MeasurementUnit.METRIC -> miles * MILES_TO_KM
        }
    }

    fun elevationValue(feet: Int, unit: MeasurementUnit): Int {
        return when (unit) {
            MeasurementUnit.IMPERIAL -> feet
            MeasurementUnit.METRIC -> (feet * FEET_TO_METERS).roundToInt()
        }
    }

    fun formatDistance(
        miles: Double,
        unit: MeasurementUnit,
        decimals: Int = 1
    ): String {
        val converted = distanceValue(miles, unit)
        val suffix = if (unit == MeasurementUnit.METRIC) "km" else "mi"
        return String.format(Locale.US, "%.${decimals}f %s", converted, suffix)
    }

    fun formatSpeed(mph: Double, unit: MeasurementUnit): String {
        val converted = distanceValue(mph, unit)
        val suffix = if (unit == MeasurementUnit.METRIC) "km/h" else "mph"
        return String.format(Locale.US, "%.1f %s", converted, suffix)
    }

    fun formatElevation(
        feet: Int,
        unit: MeasurementUnit,
        prefix: String = ""
    ): String {
        val converted = elevationValue(feet, unit)
        val suffix = if (unit == MeasurementUnit.METRIC) "m" else "ft"
        val formatted = integerFormatter.get()?.format(converted) ?: converted.toString()
        return "$prefix$formatted $suffix"
    }

    fun formatElevation(
        feet: Double,
        unit: MeasurementUnit,
        prefix: String = ""
    ): String = formatElevation(feet.roundToInt(), unit, prefix)

    fun formatRadiusShort(radiusMiles: Double = 20.0, unit: MeasurementUnit): String {
        return when (unit) {
            MeasurementUnit.IMPERIAL -> "${radiusMiles.roundToInt()} mi"
            MeasurementUnit.METRIC -> "${(radiusMiles * MILES_TO_KM).roundToInt()} km"
        }
    }

    fun formatRadiusAdjective(radiusMiles: Double = 20.0, unit: MeasurementUnit): String {
        return when (unit) {
            MeasurementUnit.IMPERIAL -> "${radiusMiles.roundToInt()}-Mile"
            MeasurementUnit.METRIC -> "${(radiusMiles * MILES_TO_KM).roundToInt()}-km"
        }
    }

    fun formatRadiusLong(radiusMiles: Double = 20.0, unit: MeasurementUnit): String {
        return when (unit) {
            MeasurementUnit.IMPERIAL -> "${radiusMiles.roundToInt()} miles"
            MeasurementUnit.METRIC -> "${(radiusMiles * MILES_TO_KM).roundToInt()} km"
        }
    }

    fun formatDuration(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600L
        val minutes = (safeSeconds % 3600L) / 60L
        val seconds = safeSeconds % 60L
        return buildString(8) {
            if (hours < 10L) append('0')
            append(hours)
            append(':')
            if (minutes < 10L) append('0')
            append(minutes)
            append(':')
            if (seconds < 10L) append('0')
            append(seconds)
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun formatTimestamp(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(timestampFormatter())
    }
}

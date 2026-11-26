package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.domain.model.GeoPoint
import com.example.ui.theme.DifficultyEasyColor
import com.example.ui.theme.PineDeep
import com.example.ui.theme.Terracotta

private data class TrailShapeBounds(
    val segments: List<List<GeoPoint>>,
    val startPoint: GeoPoint?,
    val endPoint: GeoPoint?,
    val totalPoints: Int,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

@Composable
fun TrailShapeCanvas(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    segments: List<List<GeoPoint>> = emptyList(),
    emptyText: String = "Trail geometry preview unavailable",
    lineColor: Color = Terracotta
) {
    val shapeData = remember(points, segments) {
        val effectiveSegments = if (segments.any { it.size >= 2 }) {
            segments.filter { it.size >= 2 }
        } else if (points.size >= 2) {
            listOf(points)
        } else {
            emptyList()
        }
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var totalCount = 0
        var firstPt: GeoPoint? = null
        var lastPt: GeoPoint? = null
        for (seg in effectiveSegments) {
            for (pt in seg) {
                if (firstPt == null) firstPt = pt
                lastPt = pt
                if (pt.lat < minLat) minLat = pt.lat
                if (pt.lat > maxLat) maxLat = pt.lat
                if (pt.lon < minLon) minLon = pt.lon
                if (pt.lon > maxLon) maxLon = pt.lon
                totalCount++
            }
        }
        TrailShapeBounds(
            segments = effectiveSegments,
            startPoint = firstPt,
            endPoint = lastPt,
            totalPoints = totalCount,
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon
        )
    }

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val surfaceBrush = remember(surfaceBg, containerColor) {
        Brush.linearGradient(colors = listOf(surfaceBg, containerColor))
    }
    val startColor = DifficultyEasyColor
    val endColor = lineColor
    val glowStroke = remember {
        Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val lineStroke = remember {
        Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val reusablePath = remember { Path() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(surfaceBrush)
            .semantics { contentDescription = "Topographic route shape preview" }
    ) {
        if (shapeData.totalPoints < 2) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTopoGrid(gridColor)
            }
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTopoGrid(gridColor)

                val latSpan = (shapeData.maxLat - shapeData.minLat).coerceAtLeast(0.0005)
                val lonSpan = (shapeData.maxLon - shapeData.minLon).coerceAtLeast(0.0005)

                val pad = size.minDimension * 0.14f
                val drawW = (size.width - pad * 2f).coerceAtLeast(1f)
                val drawH = (size.height - pad * 2f).coerceAtLeast(1f)
                val scale = minOf(drawW / lonSpan.toFloat(), drawH / latSpan.toFloat())

                val renderedW = lonSpan.toFloat() * scale
                val renderedH = latSpan.toFloat() * scale
                val offsetX = (size.width - renderedW) / 2f
                val offsetY = (size.height - renderedH) / 2f

                fun toX(lon: Double): Float = offsetX + ((lon - shapeData.minLon).toFloat() * scale)
                fun toY(lat: Double): Float = offsetY + ((shapeData.maxLat - lat).toFloat() * scale)

                for (seg in shapeData.segments) {
                    reusablePath.reset()
                    val first = seg.first()
                    reusablePath.moveTo(toX(first.lon), toY(first.lat))
                    for (i in 1 until seg.size) {
                        val pt = seg[i]
                        reusablePath.lineTo(toX(pt.lon), toY(pt.lat))
                    }

                    drawPath(
                        path = reusablePath,
                        color = lineColor.copy(alpha = 0.25f),
                        style = glowStroke
                    )
                    drawPath(
                        path = reusablePath,
                        color = lineColor,
                        style = lineStroke
                    )
                }

                shapeData.startPoint?.let { sp ->
                    val startOffset = Offset(toX(sp.lon), toY(sp.lat))
                    drawCircle(color = Color.White, radius = 11f, center = startOffset)
                    drawCircle(color = startColor, radius = 7.5f, center = startOffset)
                }

                shapeData.endPoint?.let { ep ->
                    val endOffset = Offset(toX(ep.lon), toY(ep.lat))
                    drawCircle(color = Color.White, radius = 11f, center = endOffset)
                    drawCircle(color = endColor, radius = 7.5f, center = endOffset)
                }
            }

            Text(
                text = "N ↑",
                style = MaterialTheme.typography.labelSmall,
                color = PineDeep,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun DrawScope.drawTopoGrid(gridColor: Color) {
    val cols = 6
    val rows = 4
    for (i in 1 until cols) {
        val x = size.width * i / cols
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
    for (j in 1 until rows) {
        val y = size.height * j / rows
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

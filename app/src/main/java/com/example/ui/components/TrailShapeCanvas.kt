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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.domain.model.GeoPoint
import com.example.ui.theme.PineDeep
import com.example.ui.theme.Terracotta

@Composable
fun TrailShapeCanvas(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    segments: List<List<GeoPoint>> = emptyList(),
    emptyText: String = "Trail geometry preview unavailable",
    lineColor: Color = Terracotta
) {
    val effectiveSegments = if (segments.any { it.size >= 2 }) {
        segments.filter { it.size >= 2 }
    } else if (points.size >= 2) {
        listOf(points)
    } else {
        emptyList()
    }
    val allPoints = effectiveSegments.flatten()

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val startColor = Color(0xFF2E7D32)
    val endColor = lineColor

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(surfaceBg, MaterialTheme.colorScheme.surfaceContainer)
                )
            )
            .semantics { contentDescription = "Topographic route shape preview" }
    ) {
        if (allPoints.size < 2) {
            Canvas(modifier = Modifier.fillMaxSize()) {
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

                val minLat = allPoints.minOf { it.lat }
                val maxLat = allPoints.maxOf { it.lat }
                val minLon = allPoints.minOf { it.lon }
                val maxLon = allPoints.maxOf { it.lon }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
                val lonSpan = (maxLon - minLon).coerceAtLeast(0.0005)

                val pad = size.minDimension * 0.14f
                val drawW = (size.width - pad * 2f).coerceAtLeast(1f)
                val drawH = (size.height - pad * 2f).coerceAtLeast(1f)
                val scale = minOf(drawW / lonSpan.toFloat(), drawH / latSpan.toFloat())

                val renderedW = lonSpan.toFloat() * scale
                val renderedH = latSpan.toFloat() * scale
                val offsetX = (size.width - renderedW) / 2f
                val offsetY = (size.height - renderedH) / 2f

                fun toOffset(pt: GeoPoint): Offset {
                    val x = offsetX + ((pt.lon - minLon).toFloat() * scale)
                    val y = offsetY + ((maxLat - pt.lat).toFloat() * scale)
                    return Offset(x, y)
                }

                for (seg in effectiveSegments) {
                    val path = Path()
                    val first = toOffset(seg.first())
                    path.moveTo(first.x, first.y)
                    for (i in 1 until seg.size) {
                        val pt = toOffset(seg[i])
                        path.lineTo(pt.x, pt.y)
                    }

                    drawPath(
                        path = path,
                        color = lineColor.copy(alpha = 0.25f),
                        style = Stroke(
                            width = 14f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(
                            width = 6f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                val startOffset = toOffset(allPoints.first())
                val endOffset = toOffset(allPoints.last())

                drawCircle(color = Color.White, radius = 11f, center = startOffset)
                drawCircle(color = startColor, radius = 7.5f, center = startOffset)

                drawCircle(color = Color.White, radius = 11f, center = endOffset)
                drawCircle(color = endColor, radius = 7.5f, center = endOffset)
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

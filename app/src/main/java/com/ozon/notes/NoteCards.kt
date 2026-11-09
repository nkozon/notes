package com.ozon.notes

import androidx.compose.ui.layout.layout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    isSelected: Boolean = false
) {
    val isDrawing = note.type == NoteType.DRAWING

    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val displayContent = note.previewText?.takeIf { it.isNotBlank() } ?: note.content
    val hasSupportingContent = isDrawing || (!note.isContentHidden && displayContent.isNotBlank())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = null
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDrawing) Icons.Rounded.Brush else Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title.ifBlank { "New Note" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (note.isPinned) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            supportingContent = if (hasSupportingContent) {
                {
                    if (isDrawing) {
                        Text(
                            text = "Drawing",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else if (!note.isContentHidden && displayContent.isNotBlank()) {
                        val firstSentence = remember(displayContent) {
                            displayContent.split(Regex("(?<=[.!?])\\s+")).firstOrNull() ?: displayContent
                        }
                        Text(
                            text = firstSentence,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else null,
            trailingContent = if (isDrawing && (note.previewImage != null || note.drawingData?.strokes?.isNotEmpty() == true)) {
                {
                    Box(
                        modifier = Modifier
                            .size(60.dp, 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        if (note.previewImage != null) {
                            AsyncImage(
                                model = note.previewImage,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            DrawingPreview(strokes = note.drawingData?.strokes ?: emptyList())
                        }
                    }
                }
            } else if (!isDrawing && note.isContentHidden) {
                {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = "Hidden",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else null
        )
    }
}

private data class StrokeBoundsData(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float
)

@Composable
fun DrawingPreview(strokes: List<com.ozon.notes.Stroke>) {
    if (strokes.isEmpty()) return

    val strokeData = remember(strokes) {
        if (strokes.isEmpty()) return@remember null
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        
        strokes.forEach { stroke ->
            stroke.points.forEach { pt ->
                minX = minOf(minX, pt.x)
                minY = minOf(minY, pt.y)
                maxX = maxOf(maxX, pt.x)
                maxY = maxOf(maxY, pt.y)
            }
        }
        
        val drawingWidth = maxX - minX
        val drawingHeight = maxY - minY
        
        if (drawingWidth <= 0 || drawingHeight <= 0) return@remember null

        StrokeBoundsData(minX, minY, drawingWidth, drawingHeight)
    }

    val path = remember { androidx.compose.ui.graphics.Path() }

    if (strokeData != null) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize().padding(4.dp)
        ) {
            val scale = minOf(size.width / strokeData.width, size.height / strokeData.height) * 0.8f
            val offsetX = (size.width - strokeData.width * scale) / 2f - strokeData.minX * scale
            val offsetY = (size.height - strokeData.height * scale) / 2f - strokeData.minY * scale

            strokes.forEach { stroke ->
                val points = stroke.points
                if (points.isNotEmpty()) {
                    path.reset()
                    path.moveTo(points[0].x * scale + offsetX, points[0].y * scale + offsetY)
                    for (i in 1 until points.size step 3) {
                        path.lineTo(points[i].x * scale + offsetX, points[i].y * scale + offsetY)
                    }
                    if ((points.size - 1) % 3 != 0) {
                        path.lineTo(points.last().x * scale + offsetX, points.last().y * scale + offsetY)
                    }
                    drawPath(
                        path = path,
                        color = Color(stroke.colorArgb),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = stroke.width * scale,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}


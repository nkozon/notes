package com.ozon.notes.drawing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ozon.notes.DrawingImage
import com.ozon.notes.LocalAdvancedUiEnabled
import com.ozon.notes.LocalHazeState
import com.ozon.notes.Stroke as NoteStroke
import com.ozon.notes.advancedUiBlur
import com.ozon.notes.advancedUiSurfaceColor
import com.ozon.notes.drawing.controller.DrawingCanvasController
import com.ozon.notes.drawing.geometry.DrawingGeometry
import kotlin.math.roundToInt

@Composable
fun DrawingSelectionOverlay(
    controller: DrawingCanvasController,
    onExportSelection: (List<NoteStroke>, List<DrawingImage>) -> Unit
) {
    val selectionBounds = controller.selectionBounds ?: return
    val canvasScale = controller.canvasScale
    val canvasOffset = controller.canvasOffset
    val liveXform = controller.activeTransformation

    val density = LocalDensity.current
    val px16 = with(density) { 16.dp.toPx() }
    val px64 = with(density) { 64.dp.toPx() }

    var showSelectionColorPopup by remember { mutableStateOf(false) }
    var showSelectionThicknessPopup by remember { mutableStateOf(false) }

    LaunchedEffect(selectionBounds == Rect.Zero) {
        if (selectionBounds == Rect.Zero) {
            showSelectionColorPopup = false
            showSelectionThicknessPopup = false
        }
    }

    val advancedUi = LocalAdvancedUiEnabled.current
    val hazeState = LocalHazeState.current

    Surface(
        modifier = Modifier
            .offset {
                var x = selectionBounds.center.x
                var y = selectionBounds.top
                var bBottom = selectionBounds.bottom

                if (liveXform != null) {
                    liveXform.offset?.let { x += it.x; y += it.y; bBottom += it.y }
                    if (liveXform.scale != null && liveXform.pivot != null) {
                        val sx = liveXform.scale.x; val sy = liveXform.scale.y
                        val px = liveXform.pivot.x; val py = liveXform.pivot.y
                        x = px + (x - px) * sx
                        y = py + (y - py) * sy
                        bBottom = py + (bBottom - py) * sy
                    }
                }

                val screenX = x * canvasScale + canvasOffset.x
                val screenY = y * canvasScale + canvasOffset.y
                val isTooHigh = screenY < 200f
                val yOffset = if (isTooHigh) (bBottom * canvasScale + canvasOffset.y + px16) else (screenY - px64)
                IntOffset((screenX - 72.dp.toPx().toInt()).roundToInt(), yOffset.roundToInt())
            }
            .shadow(if (advancedUi) 0.dp else 4.dp, CircleShape)
            .advancedUiBlur(
                hazeState = hazeState,
                shape = CircleShape,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                backgroundColor = MaterialTheme.colorScheme.surface,
                enabled = advancedUi
            )
            .clip(CircleShape)
            .zIndex(15f),
        color = advancedUiSurfaceColor(
            originalColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
            tint = MaterialTheme.colorScheme.primary,
            enabled = advancedUi
        ),
        tonalElevation = if (advancedUi) 0.dp else 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val s = controller.selectedStrokeIds.mapNotNull { controller.strokeMap[it] }
                val i = controller.selectedImageIds.mapNotNull { controller.imageMap[it] }
                onExportSelection(s, i)
            }) {
                Icon(Icons.Rounded.FileDownload, contentDescription = "Export Selection")
            }
            IconButton(onClick = {
                controller.duplicateSelection()
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate")
            }
            IconButton(onClick = {
                controller.deleteSelection()
                showSelectionThicknessPopup = false
                showSelectionColorPopup = false
            }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
            if (controller.selectedStrokeIds.isNotEmpty()) {
                VerticalDivider(
                    modifier = Modifier.height(24.dp).padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                IconButton(onClick = {
                    showSelectionColorPopup = !showSelectionColorPopup
                    showSelectionThicknessPopup = false
                }) {
                    val firstColor = controller.strokeMap[controller.selectedStrokeIds.first()]?.colorArgb ?: Color.Black.value.toInt()
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(firstColor))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                    )
                }
                IconButton(onClick = {
                    showSelectionThicknessPopup = !showSelectionThicknessPopup
                    showSelectionColorPopup = false
                }) {
                    Icon(Icons.Rounded.LineWeight, contentDescription = "Thickness")
                }
            }
        }
    }

    if (showSelectionColorPopup || showSelectionThicknessPopup) {
        Column(
            modifier = Modifier
                .offset {
                    var x = selectionBounds.center.x
                    var y = selectionBounds.top
                    var bBottom = selectionBounds.bottom

                    if (liveXform != null) {
                        liveXform.offset?.let { x += it.x; y += it.y; bBottom += it.y }
                        if (liveXform.scale != null && liveXform.pivot != null) {
                            val sx = liveXform.scale.x; val sy = liveXform.scale.y
                            val px = liveXform.pivot.x; val py = liveXform.pivot.y
                            x = px + (x - px) * sx
                            y = py + (y - py) * sy
                            bBottom = py + (bBottom - py) * sy
                        }
                    }

                    val screenX = x * canvasScale + canvasOffset.x
                    val screenY = y * canvasScale + canvasOffset.y
                    val isTooHigh = screenY < 200f
                    val yOffset = if (isTooHigh) (bBottom * canvasScale + canvasOffset.y + px16) else (screenY - px64)
                    IntOffset((screenX - 100.dp.toPx()).roundToInt(), (yOffset + (if (isTooHigh) 60.dp.toPx() else -200.dp.toPx())).roundToInt())
                }
                .zIndex(16f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showSelectionColorPopup && controller.selectedStrokeIds.isNotEmpty()) {
                ColorPopup(
                    selectedColor = Color(controller.strokeMap[controller.selectedStrokeIds.first()]?.colorArgb ?: Color.Black.value.toInt()),
                    onColorChange = { newColor ->
                        controller.changeSelectedColor(newColor)
                        showSelectionColorPopup = false
                    },
                    onOpenPicker = {}
                )
            }
            if (showSelectionThicknessPopup && controller.selectedStrokeIds.isNotEmpty()) {
                ThicknessPopup(
                    thickness = controller.strokeMap[controller.selectedStrokeIds.first()]?.width ?: 2.5f,
                    onThicknessChange = { newWidth ->
                        controller.changeSelectedThickness(newWidth)
                    },
                    color = Color(controller.strokeMap[controller.selectedStrokeIds.first()]?.colorArgb ?: Color.Black.value.toInt()),
                    min = 0.5f,
                    max = 50f
                )
            }
        }
    }
}

@Composable
fun SelectionExportDialog(
    strokes: List<NoteStroke>,
    images: List<DrawingImage>,
    onDismiss: () -> Unit,
    onExportPng: () -> Unit,
    onExportPdfBitmap: () -> Unit,
    onExportPdfVector: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Selection") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val bounds = remember(strokes, images) { DrawingGeometry.getBounds(strokes, images) }
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (bounds.width > 0 && bounds.height > 0) {
                            val padding = 20f
                            val scale = minOf(size.width / (bounds.width + padding * 2), size.height / (bounds.height + padding * 2))

                            withTransform({
                                translate(size.width / 2f, size.height / 2f)
                                scale(scale, scale, Offset.Zero)
                                translate(-bounds.center.x, -bounds.center.y)
                            }) {
                                strokes.forEach { stroke ->
                                    val path = Path().apply {
                                        stroke.points.forEachIndexed { i, p ->
                                            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(stroke.colorArgb),
                                        style = Stroke(
                                            width = stroke.width,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Select format:", style = MaterialTheme.typography.titleMedium)
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onExportPng, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(8.dp)); Text("PNG Image")
                }
                Button(onClick = onExportPdfBitmap, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("PDF (Bitmap)")
                }
                Button(onClick = onExportPdfVector, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("PDF (Vector)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

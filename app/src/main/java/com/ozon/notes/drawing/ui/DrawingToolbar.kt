package com.ozon.notes.drawing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozon.notes.DrawingTool
import com.ozon.notes.R
import com.ozon.notes.ToolbarAnchor
import kotlin.math.roundToInt

private val HorizontalToolbarWidth = 340.dp
private val ToolbarHeight = 46.dp

@Composable
fun DrawingToolbar(
    currentTool: DrawingTool,
    onToolChange: (DrawingTool) -> Unit,
    anchor: ToolbarAnchor,
    onAnchorChange: (ToolbarAnchor) -> Unit,
    isCollapsed: Boolean,
    onToggleCollapse: (Boolean) -> Unit,
    penThickness: Float,
    onPenThicknessChange: (Float) -> Unit,
    eraserThickness: Float,
    onEraserThicknessChange: (Float) -> Unit,
    showThicknessPopup: Boolean,
    selectedPenColor: Color,
    onPenColorChange: (Color) -> Unit,
    showColorPopup: Boolean,
    onToggleColorPopup: (Boolean) -> Unit,
    undoEnabled: Boolean,
    onUndo: () -> Unit,
    redoEnabled: Boolean,
    onRedo: () -> Unit,
    canvasScale: Float,
    onResetZoom: () -> Unit,
    thicknessPresets: List<Float> = emptyList(),
    onThicknessPresetsChange: (List<Float>) -> Unit = {}
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var predictedAnchor by remember { mutableStateOf<ToolbarAnchor?>(null) }
    var showFullColorPicker by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val toolbarWidth = minOf(HorizontalToolbarWidth, maxWidth - 24.dp)

        val alignment = when (anchor) {
            ToolbarAnchor.TOP -> Alignment.TopCenter
            ToolbarAnchor.BOTTOM -> Alignment.BottomCenter
            ToolbarAnchor.LEFT -> Alignment.CenterStart
            ToolbarAnchor.RIGHT -> Alignment.CenterEnd
            ToolbarAnchor.TOP_LEFT -> Alignment.TopStart
            ToolbarAnchor.TOP_RIGHT -> Alignment.TopEnd
            ToolbarAnchor.BOTTOM_LEFT -> Alignment.BottomStart
            ToolbarAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
        }

        val isTop = anchor == ToolbarAnchor.TOP || anchor == ToolbarAnchor.TOP_LEFT || anchor == ToolbarAnchor.TOP_RIGHT
        val isBottom = anchor == ToolbarAnchor.BOTTOM || anchor == ToolbarAnchor.BOTTOM_LEFT || anchor == ToolbarAnchor.BOTTOM_RIGHT
        val isHorizontal = anchor == ToolbarAnchor.TOP || anchor == ToolbarAnchor.BOTTOM ||
                           anchor == ToolbarAnchor.TOP_LEFT || anchor == ToolbarAnchor.TOP_RIGHT ||
                           anchor == ToolbarAnchor.BOTTOM_LEFT || anchor == ToolbarAnchor.BOTTOM_RIGHT

        fun getAlignment(a: ToolbarAnchor) = when (a) {
            ToolbarAnchor.TOP -> Alignment.TopCenter
            ToolbarAnchor.BOTTOM -> Alignment.BottomCenter
            ToolbarAnchor.LEFT -> Alignment.CenterStart
            ToolbarAnchor.RIGHT -> Alignment.CenterEnd
            ToolbarAnchor.TOP_LEFT -> Alignment.TopStart
            ToolbarAnchor.TOP_RIGHT -> Alignment.TopEnd
            ToolbarAnchor.BOTTOM_LEFT -> Alignment.BottomStart
            ToolbarAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
        }

        // --- Drag Preview Target ---
        predictedAnchor?.let { pred ->
            val pTop = pred == ToolbarAnchor.TOP || pred == ToolbarAnchor.TOP_LEFT || pred == ToolbarAnchor.TOP_RIGHT
            val pBottom = pred == ToolbarAnchor.BOTTOM || pred == ToolbarAnchor.BOTTOM_LEFT || pred == ToolbarAnchor.BOTTOM_RIGHT
            val pIsHorizontal = pred == ToolbarAnchor.TOP || pred == ToolbarAnchor.BOTTOM ||
                                pred == ToolbarAnchor.TOP_LEFT || pred == ToolbarAnchor.TOP_RIGHT ||
                                pred == ToolbarAnchor.BOTTOM_LEFT || pred == ToolbarAnchor.BOTTOM_RIGHT

            Box(
                modifier = Modifier
                    .align(getAlignment(pred))
                    .then(
                        if (pTop) Modifier.statusBarsPadding().padding(top = 60.dp, start = 12.dp, end = 12.dp)
                        else if (pBottom) Modifier.navigationBarsPadding().padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                        else Modifier.padding(12.dp)
                    )
            ) {
                Surface(
                    modifier = Modifier.size(
                        width = if (isCollapsed) (if (pIsHorizontal) 144.dp else ToolbarHeight) else if (pIsHorizontal) toolbarWidth else ToolbarHeight,
                        height = if (isCollapsed) (if (pIsHorizontal) ToolbarHeight else 144.dp) else if (pIsHorizontal) ToolbarHeight else 220.dp
                    ),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {}
            }
        }

        Column(
            modifier = Modifier
                .align(alignment)
                .wrapContentSize()
                .then(
                    if (isTop) Modifier.statusBarsPadding().padding(top = 60.dp, start = 12.dp, end = 12.dp)
                    else if (isBottom) Modifier.navigationBarsPadding().padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                    else Modifier.padding(12.dp)
                ),
            horizontalAlignment = when (anchor) {
                ToolbarAnchor.TOP_LEFT, ToolbarAnchor.BOTTOM_LEFT, ToolbarAnchor.LEFT -> Alignment.Start
                ToolbarAnchor.TOP_RIGHT, ToolbarAnchor.BOTTOM_RIGHT, ToolbarAnchor.RIGHT -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
        ) {
            if (!isCollapsed && showThicknessPopup) {
                ThicknessPopup(
                    thickness = if (currentTool == DrawingTool.PEN) penThickness else eraserThickness,
                    onThicknessChange = if (currentTool == DrawingTool.PEN) onPenThicknessChange else onEraserThicknessChange,
                    color = if (currentTool == DrawingTool.PEN) selectedPenColor else Color.LightGray,
                    presets = thicknessPresets,
                    onPresetLongClick = { index ->
                        val current = if (currentTool == DrawingTool.PEN) penThickness else eraserThickness
                        val newPresets = thicknessPresets.toMutableList().apply { set(index, current) }
                        onThicknessPresetsChange(newPresets)
                    },
                    min = 0.5f,
                    max = 50f
                )
                Spacer(Modifier.height(6.dp))
            }
            if (!isCollapsed && showColorPopup) {
                ColorPopup(
                    selectedColor = selectedPenColor,
                    onColorChange = {
                        onPenColorChange(it)
                        onToggleColorPopup(false)
                    },
                    onOpenPicker = {
                        showFullColorPicker = true
                        onToggleColorPopup(false)
                    }
                )
                Spacer(Modifier.height(6.dp))
            }

            Surface(
                modifier = Modifier
                    .then(
                        if (!isCollapsed && isHorizontal) Modifier.width(toolbarWidth)
                        else Modifier.wrapContentSize()
                    )
                    .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                    .shadow(if (isCollapsed) 3.dp else 6.dp, CircleShape)
                    .clip(CircleShape)
                    .pointerInput(anchor) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragOffset = Offset.Zero
                                predictedAnchor = anchor
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount

                                val currentBasePos = when (anchor) {
                                    ToolbarAnchor.TOP -> Offset(screenWidth / 2, 0f)
                                    ToolbarAnchor.BOTTOM -> Offset(screenWidth / 2, screenHeight)
                                    ToolbarAnchor.LEFT -> Offset(0f, screenHeight / 2)
                                    ToolbarAnchor.RIGHT -> Offset(screenWidth, screenHeight / 2)
                                    ToolbarAnchor.TOP_LEFT -> Offset(0f, 0f)
                                    ToolbarAnchor.TOP_RIGHT -> Offset(screenWidth, 0f)
                                    ToolbarAnchor.BOTTOM_LEFT -> Offset(0f, screenHeight)
                                    ToolbarAnchor.BOTTOM_RIGHT -> Offset(screenWidth, screenHeight)
                                }

                                val virtualPos = currentBasePos + dragOffset

                                val anchorPoints = mapOf(
                                    ToolbarAnchor.TOP to Offset(screenWidth / 2, 0f),
                                    ToolbarAnchor.BOTTOM to Offset(screenWidth / 2, screenHeight),
                                    ToolbarAnchor.LEFT to Offset(0f, screenHeight / 2),
                                    ToolbarAnchor.RIGHT to Offset(screenWidth, screenHeight / 2),
                                    ToolbarAnchor.TOP_LEFT to Offset(0f, 0f),
                                    ToolbarAnchor.TOP_RIGHT to Offset(screenWidth, 0f),
                                    ToolbarAnchor.BOTTOM_LEFT to Offset(0f, screenHeight),
                                    ToolbarAnchor.BOTTOM_RIGHT to Offset(screenWidth, screenHeight)
                                )

                                predictedAnchor = anchorPoints.minByOrNull { (_, point) ->
                                    (point - virtualPos).getDistance()
                                }?.key ?: anchor
                            },
                            onDragEnd = {
                                predictedAnchor?.let { onAnchorChange(it) }
                                dragOffset = Offset.Zero
                                predictedAnchor = null
                            },
                            onDragCancel = {
                                dragOffset = Offset.Zero
                                predictedAnchor = null
                            }
                        )
                    },
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(if (isCollapsed) 2.dp else 4.dp),
                tonalElevation = if (isCollapsed) 2.dp else 4.dp
            ) {
                val toolbarPadding = 8.dp
                if (isHorizontal) {
                    Row(
                        modifier = Modifier
                            .then(
                                if (!isCollapsed) Modifier.width(toolbarWidth)
                                else Modifier.wrapContentSize()
                            )
                            .clip(CircleShape)
                            .horizontalScroll(rememberScrollState())
                            .padding(toolbarPadding),
                        horizontalArrangement = if (!isCollapsed) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            ToolbarContent(
                                isHorizontal = isHorizontal,
                                isCollapsed = isCollapsed,
                                currentTool = currentTool,
                                onToolChange = onToolChange,
                                selectedPenColor = selectedPenColor,
                                showColorPopup = showColorPopup,
                                onToggleColorPopup = onToggleColorPopup,
                                onToggleCollapse = onToggleCollapse,
                                undoEnabled = undoEnabled,
                                onUndo = onUndo,
                                redoEnabled = redoEnabled,
                                onRedo = onRedo,
                                canvasScale = canvasScale,
                                onResetZoom = onResetZoom
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = (screenHeight / LocalDensity.current.density).dp - 120.dp)
                            .clip(CircleShape)
                            .verticalScroll(rememberScrollState())
                            .padding(toolbarPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            ToolbarContent(
                                isHorizontal = isHorizontal,
                                isCollapsed = isCollapsed,
                                currentTool = currentTool,
                                onToolChange = onToolChange,
                                selectedPenColor = selectedPenColor,
                                showColorPopup = showColorPopup,
                                onToggleColorPopup = onToggleColorPopup,
                                onToggleCollapse = onToggleCollapse,
                                undoEnabled = undoEnabled,
                                onUndo = onUndo,
                                redoEnabled = redoEnabled,
                                onRedo = onRedo,
                                canvasScale = canvasScale,
                                onResetZoom = onResetZoom
                            )
                        }
                    }
                }
            }
        }
    }
    if (showFullColorPicker) {
        FullColorPickerDialog(
            initialColor = selectedPenColor,
            onColorChange = {
                onPenColorChange(it)
                showFullColorPicker = false
            },
            onDismiss = { showFullColorPicker = false }
        )
    }
}

@Composable
private fun ToolbarContent(
    isHorizontal: Boolean,
    isCollapsed: Boolean,
    currentTool: DrawingTool,
    onToolChange: (DrawingTool) -> Unit,
    selectedPenColor: Color,
    showColorPopup: Boolean,
    onToggleColorPopup: (Boolean) -> Unit,
    onToggleCollapse: (Boolean) -> Unit,
    undoEnabled: Boolean,
    onUndo: () -> Unit,
    redoEnabled: Boolean,
    onRedo: () -> Unit,
    canvasScale: Float,
    onResetZoom: () -> Unit
) {
    if (!isCollapsed) {
        ToolbarItem(DrawingTool.PEN, rememberVectorPainter(Icons.Rounded.Edit), currentTool == DrawingTool.PEN) { onToolChange(DrawingTool.PEN) }
        IconButton(
            onClick = { onToggleColorPopup(!showColorPopup) },
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (showColorPopup) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(selectedPenColor)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
            )
        }
        ToolbarItem(DrawingTool.ERASER, painterResource(R.drawable.ic_ink_eraser), currentTool == DrawingTool.ERASER) { onToolChange(DrawingTool.ERASER) }
        ToolbarItem(DrawingTool.LASSO, rememberVectorPainter(Icons.Rounded.Gesture), currentTool == DrawingTool.LASSO) { onToolChange(DrawingTool.LASSO) }
        ToolbarItem(DrawingTool.HAND, rememberVectorPainter(Icons.Rounded.PanTool), currentTool == DrawingTool.HAND) { onToolChange(DrawingTool.HAND) }
        ToolbarSeparator(isHorizontal)
    } else {
        IconButton(onClick = { onToggleCollapse(false) }, modifier = Modifier.size(30.dp)) {
            Icon(
                painter = when (currentTool) {
                    DrawingTool.PEN -> rememberVectorPainter(Icons.Rounded.Edit)
                    DrawingTool.ERASER -> painterResource(R.drawable.ic_ink_eraser)
                    DrawingTool.LASSO -> rememberVectorPainter(Icons.Rounded.Gesture)
                    else -> rememberVectorPainter(Icons.Rounded.PanTool)
                },
                contentDescription = "Expand",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        ToolbarSeparator(isHorizontal)
    }
    IconButton(onClick = onUndo, enabled = undoEnabled, modifier = Modifier.size(30.dp)) {
        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
    }
    IconButton(onClick = onRedo, enabled = redoEnabled, modifier = Modifier.size(30.dp)) {
        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo", modifier = Modifier.size(16.dp))
    }
    ToolbarSeparator(isHorizontal)
    if (isHorizontal) {
        TextButton(
            onClick = onResetZoom,
            modifier = Modifier.size(width = 38.dp, height = 30.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "${(canvasScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = if (canvasScale != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        IconButton(onClick = onResetZoom, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Rounded.ZoomIn,
                contentDescription = "Reset Zoom",
                modifier = Modifier.size(18.dp),
                tint = if (canvasScale != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (!isCollapsed) {
        ToolbarSeparator(isHorizontal)
        IconButton(onClick = { onToggleCollapse(true) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Rounded.UnfoldLess, contentDescription = "Collapse", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ToolbarSeparator(isHorizontal: Boolean) {
    if (isHorizontal) VerticalDivider(modifier = Modifier.height(18.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    else HorizontalDivider(modifier = Modifier.width(18.dp).height(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
fun ToolbarItem(tool: DrawingTool, painter: Painter, isSelected: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        colors = if (isSelected) IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) else IconButtonDefaults.iconButtonColors()
    ) {
        Icon(painter, tool.name, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ColorPopup(selectedColor: Color, onColorChange: (Color) -> Unit, onOpenPicker: () -> Unit) {
    val presetColors = listOf(
        Color.Black, Color(0xFFF44336), Color(0xFF2196F3), Color(0xFF4CAF50),
        Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF795548)
    )
    Surface(
        modifier = Modifier.width(240.dp).shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Colors",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                presetColors.take(4).forEach { color ->
                    ColorCircle(color = color, isSelected = color == selectedColor, onClick = { onColorChange(color) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                presetColors.drop(4).forEach { color ->
                    ColorCircle(color = color, isSelected = color == selectedColor, onClick = { onColorChange(color) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenPicker,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Custom Picker")
            }
        }
    }
}

@Composable
fun ColorCircle(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThicknessPopup(
    thickness: Float,
    onThicknessChange: (Float) -> Unit,
    color: Color,
    presets: List<Float> = listOf(2f, 5f, 10f, 20f, 40f),
    onPresetLongClick: (Int) -> Unit = {},
    min: Float = 0.5f,
    max: Float = 50f
) {
    Surface(
        modifier = Modifier.width(300.dp).shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp, 60.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .height((thickness / 2.5f).coerceIn(1f, 44f).dp)
                            .fillMaxWidth(0.7f)
                            .background(color, CircleShape)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "%.1f".format(thickness),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { onThicknessChange((thickness - 0.1f).coerceIn(min, max)) }) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.primary)
                }
                Slider(value = thickness, onValueChange = onThicknessChange, valueRange = min..max, modifier = Modifier.weight(1f))
                IconButton(onClick = { onThicknessChange((thickness + 0.1f).coerceIn(min, max)) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                presets.forEachIndexed { index, preset ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (kotlin.math.abs(thickness - preset) < 0.01f) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .combinedClickable(
                                    onClick = { onThicknessChange(preset) },
                                    onLongClick = { onPresetLongClick(index) }
                                )
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size((preset / 4f).coerceIn(2f, 24f).dp).background(color, CircleShape))
                        }
                        Text(
                            text = if (preset % 1f == 0f) preset.toInt().toString() else "%.1f".format(preset),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullColorPickerDialog(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }

    val currentColor = remember(red, green, blue, alpha) {
        Color(red, green, blue, alpha)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Color") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentColor)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                )

                Text("Red: ${(red * 255).roundToInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(value = red, onValueChange = { red = it }, valueRange = 0f..1f)

                Text("Green: ${(green * 255).roundToInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(value = green, onValueChange = { green = it }, valueRange = 0f..1f)

                Text("Blue: ${(blue * 255).roundToInt()}", style = MaterialTheme.typography.bodySmall)
                Slider(value = blue, onValueChange = { blue = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            Button(onClick = { onColorChange(currentColor) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

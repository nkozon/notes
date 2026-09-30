package com.ozon.notes.drawing.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ozon.notes.CanvasType
import com.ozon.notes.drawing.controller.DrawingCanvasController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawingTopBar(
    controller: DrawingCanvasController,
    isSplitScreen: Boolean,
    isSidePanelVisible: Boolean,
    onToggleSidePanel: () -> Unit,
    showGuidelines: Boolean,
    onToggleGuidelines: () -> Unit,
    showPageOverview: Boolean,
    onTogglePageOverview: () -> Unit,
    onAddPageAtEnd: () -> Unit,
    onInsertImage: () -> Unit,
    onExportPng: () -> Unit,
    onExportPdfBitmap: () -> Unit,
    onExportPdfVector: () -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showTitleDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .zIndex(25f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left pill: Back button and Title
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(
                    visible = isSplitScreen,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            expandHorizontally(
                                expandFrom = Alignment.Start,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                            ),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                           scaleOut(targetScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                           shrinkHorizontally(
                               shrinkTowards = Alignment.Start,
                               animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                           )
                ) {
                    IconButton(
                        onClick = onToggleSidePanel,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isSidePanelVisible) Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit,
                            contentDescription = "Toggle Fullscreen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            alignment = Alignment.CenterStart
                        )
                        .padding(start = 4.dp, end = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showTitleDialog = true },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = controller.title.ifBlank { "Drawing" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (controller.title.isBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right pill: Action buttons (Save/Checkmark, Page Overview, More Menu)
        Surface(
            modifier = Modifier
                .shadow(8.dp, CircleShape)
                .clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = controller.isDirty || controller.showSavedCheckmark,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            expandHorizontally(
                                expandFrom = Alignment.End,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                           scaleOut(targetScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                           shrinkHorizontally(
                               shrinkTowards = Alignment.End,
                               animationSpec = spring(
                                   dampingRatio = Spring.DampingRatioNoBouncy,
                                   stiffness = Spring.StiffnessMediumLow
                               )
                           )
                ) {
                    AnimatedContent(
                        targetState = controller.isDirty,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(160)) + scaleIn(initialScale = 0.85f, animationSpec = tween(160)))
                                .togetherWith(fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120)))
                        },
                        label = "SaveCheckTransition"
                    ) { isDirty ->
                        if (isDirty) {
                            IconButton(
                                onClick = onSave,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = "Save Note",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Saved",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = controller.canvasType != CanvasType.INFINITE,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            expandHorizontally(
                                expandFrom = Alignment.End,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                           scaleOut(targetScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                           shrinkHorizontally(
                               shrinkTowards = Alignment.End,
                               animationSpec = spring(
                                   dampingRatio = Spring.DampingRatioNoBouncy,
                                   stiffness = Spring.StiffnessMediumLow
                               )
                           )
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (showPageOverview) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = onTogglePageOverview,
                                onLongClick = onAddPageAtEnd
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = "Page Overview",
                            tint = if (showPageOverview) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Insert Image") },
                            onClick = {
                                showMoreMenu = false
                                onInsertImage()
                            },
                            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (showGuidelines) "Hide Guidelines" else "Show Guidelines") },
                            onClick = {
                                showMoreMenu = false
                                onToggleGuidelines()
                            },
                            leadingIcon = {
                                Icon(
                                    if (showGuidelines) Icons.Rounded.GridOff else Icons.Rounded.GridOn,
                                    contentDescription = null
                                )
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Export as PNG") },
                            onClick = {
                                showMoreMenu = false
                                onExportPng()
                            },
                            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as Bitmap PDF") },
                            onClick = {
                                showMoreMenu = false
                                onExportPdfBitmap()
                            },
                            leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as Vector PDF") },
                            onClick = {
                                showMoreMenu = false
                                onExportPdfVector()
                            },
                            leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }

    if (showTitleDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(21f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showTitleDialog = false }
        )
    }

    AnimatedVisibility(
        visible = showTitleDialog,
        enter = scaleIn(
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            initialScale = 0.88f,
            transformOrigin = TransformOrigin(0.15f, 0f)
        ) + fadeIn(),
        exit = scaleOut(
            targetScale = 0.88f,
            transformOrigin = TransformOrigin(0.15f, 0f)
        ) + fadeOut(),
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 68.dp, start = 16.dp, end = 16.dp)
            .zIndex(22f)
    ) {
        DrawingTitleDetailsPopup(
            title = controller.title,
            onTitleChange = {
                controller.title = it
                controller.isDirty = true
            },
            lastSavedTime = controller.lastSavedTime,
            onClose = { showTitleDialog = false }
        )
    }
}

@Composable
fun DrawingTitleDetailsPopup(
    title: String,
    onTitleChange: (String) -> Unit,
    lastSavedTime: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var titleState by remember {
        mutableStateOf(
            TextFieldValue(
                text = title,
                selection = TextRange(title.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Note Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = titleState,
                onValueChange = { newValue ->
                    titleState = newValue
                    onTitleChange(newValue.text)
                },
                label = { Text("Note Title") },
                placeholder = { Text("Drawing") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            val sdf = remember { SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }
            val formattedDate = remember(lastSavedTime) { sdf.format(Date(lastSavedTime)) }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Last Saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Done", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}

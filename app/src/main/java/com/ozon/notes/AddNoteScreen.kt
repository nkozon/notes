package com.ozon.notes

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AddNoteScreen(
    noteId: String?,
    viewModel: NotesViewModel,
    onNavigateUp: () -> Unit
) {
    key(noteId) {
        AddNoteScreenContent(noteId, viewModel, onNavigateUp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNoteScreenContent(
    noteId: String?,
    viewModel: NotesViewModel,
    onNavigateUp: () -> Unit
) {
    var title by remember { 
        mutableStateOf(
            TextFieldValue(
                text = "",
                selection = TextRange(0)
            ) 
        ) 
    }
    val richTextState = rememberRichTextState()
    var isPinned by remember { mutableStateOf(false) }
    var isDeleted by remember { mutableStateOf(false) }
    
    // We only want to track if the note was saved (to avoid double saving on back)
    var wasSavedManually by remember { mutableStateOf(false) }

    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(noteId) {
        if (noteId != null) {
            val note = viewModel.getNoteById(noteId)
            if (note != null) {
                title = TextFieldValue(text = note.title, selection = TextRange(note.title.length))
                richTextState.setHtml(note.contentHtml ?: "")
                isPinned = note.isPinned
                timestamp = note.timestamp
            }
        } else {
            focusRequester.requestFocus()
        }
    }

    fun saveNote() {
        val noteStillExists = noteId == null || viewModel.notesState.value.any { it.id == noteId }
        if (noteStillExists && !isDeleted && (title.text.isNotBlank() || richTextState.annotatedString.text.isNotBlank())) {
            viewModel.onEvent(
                NoteEvent.SaveNote(
                    Note(
                        id = noteId ?: UUID.randomUUID().toString(),
                        title = title.text,
                        content = richTextState.annotatedString.text,
                        contentHtml = richTextState.toHtml(),
                        timestamp = if (noteId == null) System.currentTimeMillis() else timestamp,
                        isPinned = isPinned
                    )
                )
            )
        }
    }

    androidx.activity.compose.BackHandler {
        saveNote()
        onNavigateUp()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!wasSavedManually) {
                saveNote()
            }
        }
    }

    var toolbarAnchor by remember { mutableStateOf(ToolbarAnchor.BOTTOM) }
    var isToolbarCollapsed by remember { mutableStateOf(false) }

    val wordCount = remember(richTextState.annotatedString.text) {
        richTextState.annotatedString.text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }

    val scrollState = rememberScrollState()
    val topAlpha by remember {
        derivedStateOf {
            (scrollState.value / 50f).coerceIn(0f, 1f)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (noteId != null) {
                        val sdf = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                        Text(
                            text = "Edited ${sdf.format(Date(timestamp))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        CircleIconButton(
                            onClick = {
                                saveNote()
                                wasSavedManually = true
                                onNavigateUp()
                            },
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            CircleIconButton(
                                onClick = { showMoreMenu = true },
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = "More"
                            )
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                val charCount = richTextState.annotatedString.text.length
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text("Statistics")
                                            Text(
                                                text = "$wordCount words, $charCount chars",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    },
                                    onClick = { showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Rounded.BarChart, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = { showMoreMenu = false },
                                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showMoreMenu = false
                                        isDeleted = true
                                        noteId?.let { viewModel.onEvent(NoteEvent.DeleteNote(it)) }
                                        wasSavedManually = true
                                        onNavigateUp()
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .imePadding()
            ) {
                // Initial padding so content starts below the header but can scroll behind it
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(modifier = Modifier.height(64.dp))

                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (title.text.isEmpty()) {
                            Text(
                                "Title",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                        innerTextField()
                    }
                )

                RichTextEditor(
                    state = richTextState,
                    placeholder = { 
                        Text(
                            "Type your note here...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp),
                    colors = RichTextEditorDefaults.richTextEditorColors(
                        containerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                
                // Bottom padding to allow scrolling past the floating toolbar
                Spacer(modifier = Modifier.height(200.dp))
            }

            SystemBarGradients(
                modifier = Modifier.zIndex(5f),
                topAlpha = topAlpha
            )

            TextFormattingToolbar(
                modifier = Modifier.zIndex(10f),
                richTextState = richTextState,
                anchor = toolbarAnchor,
                onAnchorChange = { toolbarAnchor = it },
                isCollapsed = isToolbarCollapsed,
                onToggleCollapse = { isToolbarCollapsed = it }
            )
        }
    }
}

@Composable
fun TextFormattingToolbar(
    modifier: Modifier = Modifier,
    richTextState: RichTextState,
    anchor: ToolbarAnchor,
    onAnchorChange: (ToolbarAnchor) -> Unit,
    isCollapsed: Boolean,
    onToggleCollapse: (Boolean) -> Unit
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var predictedAnchor by remember { mutableStateOf<ToolbarAnchor?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

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
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val headerHeight = 64.dp
        
        val isTop = anchor == ToolbarAnchor.TOP || anchor == ToolbarAnchor.TOP_LEFT || anchor == ToolbarAnchor.TOP_RIGHT
        val isBottom = anchor == ToolbarAnchor.BOTTOM || anchor == ToolbarAnchor.BOTTOM_LEFT || anchor == ToolbarAnchor.BOTTOM_RIGHT

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

        // Drag Preview
        predictedAnchor?.let { pred ->
            val pIsHorizontal = pred == ToolbarAnchor.TOP || pred == ToolbarAnchor.BOTTOM || 
                              pred == ToolbarAnchor.TOP_LEFT || pred == ToolbarAnchor.TOP_RIGHT ||
                              pred == ToolbarAnchor.BOTTOM_LEFT || pred == ToolbarAnchor.BOTTOM_RIGHT

            Box(
                modifier = Modifier
                    .align(getAlignment(pred))
                    .padding(12.dp)
                    .padding(
                        bottom = if (pred == ToolbarAnchor.BOTTOM || pred == ToolbarAnchor.BOTTOM_LEFT || pred == ToolbarAnchor.BOTTOM_RIGHT) 
                                 maxOf(navBarPadding, imePadding) + 12.dp else 0.dp,
                        top = if (pred == ToolbarAnchor.TOP || pred == ToolbarAnchor.TOP_LEFT || pred == ToolbarAnchor.TOP_RIGHT) 
                              statusBarPadding + headerHeight else 0.dp
                    )
            ) {
                Surface(
                    modifier = Modifier.then(if (pIsHorizontal) Modifier.size(240.dp, 48.dp) else Modifier.size(48.dp, 240.dp)),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {}
            }
        }

        Column(
            modifier = Modifier
                .align(alignment)
                .padding(12.dp)
                .padding(
                    bottom = if (isBottom) maxOf(navBarPadding, imePadding) + 12.dp else 0.dp, 
                    top = if (isTop) statusBarPadding + headerHeight else 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentSize()
                    .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                    .shadow(if (isCollapsed) 4.dp else 8.dp, CircleShape)
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
                                val currentBasePos = when(anchor) {
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
                                predictedAnchor = anchorPoints.minByOrNull { (_, point) -> (point - virtualPos).getDistance() }?.key ?: anchor
                            },
                            onDragEnd = {
                                predictedAnchor?.let { onAnchorChange(it) }
                                dragOffset = Offset.Zero
                                predictedAnchor = null
                            },
                            onDragCancel = { dragOffset = Offset.Zero; predictedAnchor = null }
                        )
                    },
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(if (isCollapsed) 2.dp else 6.dp),
                tonalElevation = if (isCollapsed) 2.dp else 6.dp
            ) {
                val isHorizontal = anchor == ToolbarAnchor.TOP || anchor == ToolbarAnchor.BOTTOM || 
                               anchor == ToolbarAnchor.TOP_LEFT || anchor == ToolbarAnchor.TOP_RIGHT ||
                               anchor == ToolbarAnchor.BOTTOM_LEFT || anchor == ToolbarAnchor.BOTTOM_RIGHT
                val padding = if (isCollapsed) 6.dp else 10.dp
                
                if (isHorizontal) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = (screenWidth / LocalDensity.current.density).dp - 48.dp)
                            .clip(CircleShape)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = padding, vertical = padding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormattingToolbarContent(isHorizontal, isCollapsed, richTextState, onToggleCollapse)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = (screenHeight / LocalDensity.current.density).dp - 120.dp)
                            .clip(CircleShape)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = padding, vertical = padding),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FormattingToolbarContent(isHorizontal, isCollapsed, richTextState, onToggleCollapse)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormattingToolbarContent(
    isHorizontal: Boolean,
    isCollapsed: Boolean,
    richTextState: RichTextState,
    onToggleCollapse: (Boolean) -> Unit
) {
    if (!isCollapsed) {
        CircleIconButton(
            onClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
            icon = Icons.Rounded.FormatBold,
            contentDescription = "Bold",
            containerColor = if (richTextState.currentSpanStyle.fontWeight == FontWeight.Bold)
                MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (richTextState.currentSpanStyle.fontWeight == FontWeight.Bold)
                MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconSize = 18.dp,
            modifier = Modifier.size(34.dp)
        )
        CircleIconButton(
            onClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
            icon = Icons.Rounded.FormatItalic,
            contentDescription = "Italic",
            containerColor = if (richTextState.currentSpanStyle.fontStyle == FontStyle.Italic)
                MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (richTextState.currentSpanStyle.fontStyle == FontStyle.Italic)
                MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconSize = 18.dp,
            modifier = Modifier.size(34.dp)
        )
        CircleIconButton(
            onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
            icon = Icons.Rounded.FormatUnderlined,
            contentDescription = "Underline",
            containerColor = if (richTextState.currentSpanStyle.textDecoration == TextDecoration.Underline)
                MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (richTextState.currentSpanStyle.textDecoration == TextDecoration.Underline)
                MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconSize = 18.dp,
            modifier = Modifier.size(34.dp)
        )
        CircleIconButton(
            onClick = { richTextState.toggleUnorderedList() },
            icon = Icons.AutoMirrored.Rounded.List,
            contentDescription = "Bullets",
            containerColor = if (richTextState.isUnorderedList)
                MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (richTextState.isUnorderedList)
                MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconSize = 18.dp,
            modifier = Modifier.size(34.dp)
        )

        var showTypeMenu by remember { mutableStateOf(false) }
        val types = listOf(
            "Heading" to (MaterialTheme.typography.headlineMedium.toSpanStyle().copy(fontSize = 24.sp)),
            "Subtitle 1" to (MaterialTheme.typography.titleLarge.toSpanStyle().copy(fontSize = 20.sp)),
            "Subtitle 2" to (MaterialTheme.typography.titleMedium.toSpanStyle().copy(fontSize = 18.sp)),
            "Body" to (MaterialTheme.typography.bodyLarge.toSpanStyle().copy(fontSize = 16.sp))
        )

        Box {
            Surface(
                onClick = { showTypeMenu = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = if (isHorizontal) Modifier.height(34.dp) else Modifier.width(34.dp)
            ) {
                if (isHorizontal) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TextFields,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TextFields,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = showTypeMenu,
                onDismissRequest = { showTypeMenu = false }
            ) {
                types.forEach { (label, spanStyle) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            richTextState.toggleSpanStyle(spanStyle)
                            showTypeMenu = false
                        }
                    )
                }
            }
        }
        
        ToolbarSeparator(isHorizontal)
        CircleIconButton(
            onClick = { onToggleCollapse(true) },
            icon = Icons.Rounded.UnfoldLess,
            contentDescription = "Collapse",
            containerColor = Color.Transparent,
            iconSize = 18.dp,
            modifier = Modifier.size(34.dp)
        )
    } else {
        CircleIconButton(
            onClick = { onToggleCollapse(false) },
            icon = Icons.Rounded.FormatSize,
            contentDescription = "Expand",
            contentColor = MaterialTheme.colorScheme.primary,
            iconSize = 20.dp,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun ToolbarSeparator(isHorizontal: Boolean) {
    if (isHorizontal) VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    else HorizontalDivider(modifier = Modifier.width(24.dp).height(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}


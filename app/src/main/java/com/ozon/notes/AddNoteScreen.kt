package com.ozon.notes

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.ozon.notes.ui.theme.NoteColors
import com.ozon.notes.ui.theme.adaptNoteColor
import java.util.UUID

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
    var selectedColorArgb by remember { mutableIntStateOf(Color.Transparent.toArgb()) }
    var isPinned by remember { mutableStateOf(false) }
    var isDeleted by remember { mutableStateOf(false) }
    var isColorMenuExpanded by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    
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
                selectedColorArgb = note.colorArgb
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
                        colorArgb = selectedColorArgb,
                        isPinned = isPinned
                    )
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!wasSavedManually) {
                saveNote()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        CircleIconButton(
                            onClick = {
                                if (noteId != null) {
                                    saveNote()
                                }
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
                        // --- RICH TEXT ACTIONS (PERSISTENT IN HEADER) ---
                        CircleIconButton(
                            onClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                            icon = Icons.Rounded.FormatBold,
                            contentDescription = "Bold",
                            shape = if (richTextState.currentSpanStyle.fontWeight == FontWeight.Bold) RoundedCornerShape(12.dp) else CircleShape,
                            containerColor = if (richTextState.currentSpanStyle.fontWeight == FontWeight.Bold)
                                MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            contentColor = if (richTextState.currentSpanStyle.fontWeight == FontWeight.Bold)
                                MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        CircleIconButton(
                            onClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                            icon = Icons.Rounded.FormatItalic,
                            contentDescription = "Italic",
                            shape = if (richTextState.currentSpanStyle.fontStyle == FontStyle.Italic) RoundedCornerShape(12.dp) else CircleShape,
                            containerColor = if (richTextState.currentSpanStyle.fontStyle == FontStyle.Italic)
                                MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            contentColor = if (richTextState.currentSpanStyle.fontStyle == FontStyle.Italic)
                                MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        CircleIconButton(
                            onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
                            icon = Icons.Rounded.FormatUnderlined,
                            contentDescription = "Underline",
                            shape = if (richTextState.currentSpanStyle.textDecoration == TextDecoration.Underline) RoundedCornerShape(12.dp) else CircleShape,
                            containerColor = if (richTextState.currentSpanStyle.textDecoration == TextDecoration.Underline)
                                MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            contentColor = if (richTextState.currentSpanStyle.textDecoration == TextDecoration.Underline)
                                MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        CircleIconButton(
                            onClick = { richTextState.toggleUnorderedList() },
                            icon = Icons.AutoMirrored.Rounded.List,
                            contentDescription = "Bullet Points",
                            shape = if (richTextState.isUnorderedList) RoundedCornerShape(12.dp) else CircleShape,
                            containerColor = if (richTextState.isUnorderedList)
                                MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            contentColor = if (richTextState.isUnorderedList)
                                MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))

                        val types = listOf(
                            "Heading" to (MaterialTheme.typography.headlineMedium.toSpanStyle().copy(fontSize = 24.sp)),
                            "Subtitle 1" to (MaterialTheme.typography.titleLarge.toSpanStyle().copy(fontSize = 20.sp)),
                            "Subtitle 2" to (MaterialTheme.typography.titleMedium.toSpanStyle().copy(fontSize = 18.sp)),
                            "Body" to (MaterialTheme.typography.bodyLarge.toSpanStyle().copy(fontSize = 16.sp))
                        )

                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                                    .clickable { showTypeMenu = true }
                                    .padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = "Type",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Text Type",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
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

                        Spacer(modifier = Modifier.width(12.dp))
                        VerticalDivider(modifier = Modifier.height(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))

                        if (noteId != null) {
                            CircleIconButton(
                                onClick = {
                                    isDeleted = true
                                    viewModel.onEvent(NoteEvent.DeleteNote(noteId))
                                    wasSavedManually = true
                                    onNavigateUp()
                                },
                                icon = Icons.Rounded.Delete,
                                contentDescription = "Delete Note",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                                Button(
                                onClick = {
                                    saveNote()
                                    wasSavedManually = true
                                    onNavigateUp()
                                },
                                modifier = Modifier
                                    .height(38.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Text("Save", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color.Transparent,
                actions = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        IconButton(onClick = { isColorMenuExpanded = true }) {
                            val displayColor = if (selectedColorArgb == Color.Transparent.toArgb()) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                adaptNoteColor(selectedColorArgb)
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(displayColor)
                                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                            )
                        }

                        DropdownMenu(
                            expanded = isColorMenuExpanded,
                            onDismissRequest = { isColorMenuExpanded = false }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ColorOptionCircle(
                                    colorToApply = Color.Transparent,
                                    displayColor = MaterialTheme.colorScheme.surfaceVariant,
                                    isSelected = selectedColorArgb == Color.Transparent.toArgb(),
                                    onClick = {
                                        selectedColorArgb = Color.Transparent.toArgb()
                                        isColorMenuExpanded = false
                                    }
                                )
                                NoteColors.forEach { color ->
                                    ColorOptionCircle(
                                        colorToApply = color,
                                        displayColor = color,
                                        isSelected = adaptNoteColor(selectedColorArgb) == color,
                                        onClick = {
                                            selectedColorArgb = color.toArgb()
                                            isColorMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding + 64.dp, bottom = bottomPadding + 80.dp)
                    .zIndex(2f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title", style = MaterialTheme.typography.headlineMedium) },
                        textStyle = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    RichTextEditor(
                        state = richTextState,
                        placeholder = { Text("Type your note here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = RichTextEditorDefaults.richTextEditorColors(
                            containerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }

            SystemBarGradients(modifier = Modifier.zIndex(1f))
            
            // To ensure title/editor are above gradient, we can give the padded Box a higher zIndex
            // Or just put SystemBarGradients BEFORE the padded Box in the rendering order.
            // Let's use zIndex(0f) for SystemBarGradients and zIndex(1f) for the padded Box.
        }
    }
}

@Composable
fun ColorOptionCircle(
    colorToApply: Color,
    displayColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(displayColor)
            .clickable { onClick() }
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                shape = CircleShape
            )
    )
}

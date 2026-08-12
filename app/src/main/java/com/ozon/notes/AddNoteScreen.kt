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
    viewModel: NoteViewModel,
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
    viewModel: NoteViewModel,
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
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        if (noteId != null) {
                            saveNote()
                        }
                        wasSavedManually = true
                        onNavigateUp()
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // --- RICH TEXT ACTIONS (PERSISTENT IN HEADER) ---
                    IconButton(
                        onClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }
                    ) {
                        Icon(
                            Icons.Rounded.FormatBold,
                            contentDescription = "Bold",
                            tint = if (richTextState.currentSpanStyle.fontWeight == FontWeight.Bold) 
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
                    ) {
                        Icon(
                            Icons.Rounded.FormatItalic,
                            contentDescription = "Italic",
                            tint = if (richTextState.currentSpanStyle.fontStyle == FontStyle.Italic) 
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
                    ) {
                        Icon(
                            Icons.Rounded.FormatUnderlined,
                            contentDescription = "Underline",
                            tint = if (richTextState.currentSpanStyle.textDecoration == TextDecoration.Underline) 
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { richTextState.toggleUnorderedList() }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.List,
                            contentDescription = "Bullet Points",
                            tint = if (richTextState.isUnorderedList) 
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(onClick = { showTypeMenu = true }) {
                            Icon(Icons.Rounded.TextFields, contentDescription = "Text Type")
                        }
                        DropdownMenu(
                            expanded = showTypeMenu,
                            onDismissRequest = { showTypeMenu = false }
                        ) {
                            val types = listOf(
                                "Heading" to (MaterialTheme.typography.headlineMedium.toSpanStyle().copy(fontSize = 24.sp)),
                                "Subtitle 1" to (MaterialTheme.typography.titleLarge.toSpanStyle().copy(fontSize = 20.sp)),
                                "Subtitle 2" to (MaterialTheme.typography.titleMedium.toSpanStyle().copy(fontSize = 18.sp)),
                                "Body" to (MaterialTheme.typography.bodyLarge.toSpanStyle().copy(fontSize = 16.sp))
                            )
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

                    Spacer(modifier = Modifier.width(8.dp))
                    VerticalDivider(modifier = Modifier.height(24.dp).align(Alignment.CenterVertically))
                    Spacer(modifier = Modifier.width(8.dp))

                    if (noteId != null) {
                        IconButton(
                            onClick = {
                                isDeleted = true
                                viewModel.onEvent(NoteEvent.DeleteNote(noteId))
                                wasSavedManually = true
                                onNavigateUp()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete Note",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                saveNote()
                                wasSavedManually = true
                                onNavigateUp()
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(36.dp),
                            shape = RoundedCornerShape(percent = 50),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text("Save", style = MaterialTheme.typography.labelLarge)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

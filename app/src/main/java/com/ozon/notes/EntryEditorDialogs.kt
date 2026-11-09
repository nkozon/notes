package com.ozon.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.absoluteValue
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

@Composable
fun DescriptionDialog(
    entry: ListEntry,
    viewModel: ChecklistViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var description by remember { mutableStateOf(entry.description ?: "") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(entry.id) {
        if (entry.description == null) {
            val fullDesc = viewModel.getEntryDescription(entry.id)
            if (fullDesc != null) description = fullDesc
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title) },
        text = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(description.takeIf { it.isNotBlank() })
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ScoreWheelPicker(
    score: Float,
    onScoreChange: (Float) -> Unit,
    onScoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val itemHeight = 44.dp
    val itemHeightPx = with(density) { itemHeight.toPx() }

    val initialIndex = (score * 10).roundToInt().coerceIn(0, 100).toFloat()
    val scrollOffset = remember { Animatable(initialIndex) }
    var lastHapticIndex by remember { mutableIntStateOf((score * 10).roundToInt().coerceIn(0, 100)) }

    LaunchedEffect(score) {
        val targetIndex = (score * 10).roundToInt().coerceIn(0, 100).toFloat()
        if ((scrollOffset.value - targetIndex).absoluteValue > 0.05f && !scrollOffset.isRunning) {
            scrollOffset.animateTo(
                targetValue = targetIndex,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(152.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                RoundedCornerShape(20.dp)
            )
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        val deltaIndex = -delta / itemHeightPx
                        val newValue = (scrollOffset.value + deltaIndex).coerceIn(-0.3f, 100.3f)
                        scrollOffset.snapTo(newValue)

                        val currentIntIndex = newValue.roundToInt().coerceIn(0, 100)
                        if (currentIntIndex != lastHapticIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticIndex = currentIntIndex
                            onScoreChange(currentIntIndex / 10f)
                        }
                    }
                },
                onDragStopped = { velocity ->
                    coroutineScope.launch {
                        val velocityInIndices = -velocity / itemHeightPx
                        val target = (scrollOffset.value + velocityInIndices * 0.12f)
                            .roundToInt()
                            .coerceIn(0, 100)
                            .toFloat()

                        scrollOffset.animateTo(
                            targetValue = target,
                            initialVelocity = velocityInIndices,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        onScoreChange(target / 10f)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Center click area (no background or border)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onScoreClick
                )
        )

        // Wheel drum items
        val currentScroll = scrollOffset.value
        val baseIndex = currentScroll.roundToInt()
        val minIndex = (baseIndex - 3).coerceAtLeast(0)
        val maxIndex = (baseIndex + 3).coerceAtMost(100)

        for (i in minIndex..maxIndex) {
            val offsetFromCenter = i - currentScroll
            val absOffset = offsetFromCenter.absoluteValue
            val scale = (1f - 0.18f * absOffset).coerceIn(0.6f, 1f)
            val alpha = (1f - 0.48f * absOffset).coerceIn(0.12f, 1f)
            val rotationX = (offsetFromCenter * 18f).coerceIn(-55f, 55f)
            val itemScore = i / 10f

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationY = offsetFromCenter * itemHeightPx
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        this.rotationX = rotationX
                        cameraDistance = 8 * density.density
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (absOffset < 0.5f) {
                            onScoreClick()
                        } else {
                            coroutineScope.launch {
                                scrollOffset.animateTo(
                                    targetValue = i.toFloat(),
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                                onScoreChange(i / 10f)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatRating(itemScore),
                    style = if (absOffset < 0.5f) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = if (absOffset < 0.5f) FontWeight.Bold else FontWeight.Medium,
                    color = if (absOffset < 0.5f) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
        val topGradient = remember(surfaceColor) {
            Brush.verticalGradient(listOf(surfaceColor, Color.Transparent))
        }
        val bottomGradient = remember(surfaceColor) {
            Brush.verticalGradient(listOf(Color.Transparent, surfaceColor))
        }

        // Top fade gradient
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(topGradient)
        )

        // Bottom fade gradient
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(bottomGradient)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDialog(
    listId: String,
    listType: ListType,
    currentSectionName: String = "Currently Watching",
    entry: ListEntry? = null,
    allTags: List<Tag> = emptyList(),
    allEntries: List<ListEntry> = emptyList(),
    titlePrefix: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        rating: Float,
        tagIds: List<String>,
        linkedId: String?,
        dueDate: Long?,
        remindMe: Boolean,
        isCurrentlyWatching: Boolean,
        isPaused: Boolean,
        currentProgress: Int?,
        totalProgress: Int?,
        progressUnit: String?
    ) -> Unit,
    onSaveTag: (Tag) -> Unit
) {
    var title by remember { 
        mutableStateOf(
            TextFieldValue(
                text = entry?.title ?: "",
                selection = TextRange(entry?.title?.length ?: 0)
            )
        ) 
    }
    var rating by remember { mutableFloatStateOf(entry?.rating ?: 0f) }
    var selectedTagIds by remember { mutableStateOf(entry?.tagIds?.toSet() ?: emptySet()) }
    var linkedEntryId by remember { mutableStateOf(entry?.linkedEntryId) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    
    var dueDate by remember { mutableStateOf(entry?.dueDate) }
    var remindMe by remember { mutableStateOf(entry?.remindMe ?: false) }

    var progress by remember {
        mutableStateOf(EntryProgressDraft(
            status = when {
                entry?.isPaused == true -> EntryProgressStatus.PAUSED
                entry?.isCurrentlyWatching == true -> EntryProgressStatus.WATCHING
                else -> EntryProgressStatus.NONE
            },
            current = entry?.currentProgress?.toString().orEmpty(),
            total = entry?.totalProgress?.toString().orEmpty(),
            unit = entry?.progressUnit ?: "episodes"
        ))
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            remindMe = isGranted
        }
    )

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var showWheelPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = titlePrefix ?: if (entry == null) "Add Entry" else "Edit Entry",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close"
                            )
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                if (title.text.isNotBlank()) {
                                    val finalRating = ((rating * 10).roundToInt() / 10f)
                                    onConfirm(
                                        title.text,
                                        finalRating,
                                        selectedTagIds.toList(),
                                        linkedEntryId,
                                        dueDate,
                                        remindMe,
                                        progress.status == EntryProgressStatus.WATCHING,
                                        progress.status == EntryProgressStatus.PAUSED,
                                        progress.current.toIntOrNull(),
                                        progress.total.toIntOrNull(),
                                        progress.unit.takeIf { progress.status != EntryProgressStatus.NONE && it.isNotBlank() }
                                    )
                                }
                            },
                            enabled = title.text.isNotBlank(),
                            shape = CircleShape,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (entry == null) "Add" else "Save",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                    if (listType == ListType.RATING) {
                        FilledTonalButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                showWheelPicker = true
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .height(56.dp)
                                .semantics { contentDescription = "Edit rating" }
                        ) {
                            Text(
                                text = "${formatRating(rating)} / 10",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                
                if (listType == ListType.UPCOMING) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Due Date", style = MaterialTheme.typography.labelLarge)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = { showDatePicker = true },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = dueDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it)) } ?: "Select Date",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            if (dueDate != null) {
                                IconButton(onClick = { 
                                    dueDate = null
                                    remindMe = false
                                }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear Date")
                                }
                            }
                        }

                        if (dueDate != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Notify me", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = remindMe,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                                    remindMe = true
                                                } else {
                                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                    // Note: remindMe will be set in the launcher callback
                                                }
                                            } else {
                                                remindMe = true
                                            }
                                        } else {
                                            remindMe = false
                                        }
                                    }
                                )
                            }
                            
                            if (remindMe) {
                                Surface(
                                    onClick = { showTimePicker = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = dueDate?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "Select Time",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                if (listType == ListType.RATING) {
                    EntryProgressSettings(
                        currentSectionName = currentSectionName,
                        value = progress,
                        onValueChange = { progress = it }
                    )

                    // Linked Entry Selection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Part of", style = MaterialTheme.typography.labelLarge)
                        val selectedLinkedEntry = allEntries.find { it.id == linkedEntryId }
                        var showSelectionPopup by remember { mutableStateOf(false) }

                        Surface(
                            onClick = {
                                focusManager.clearFocus()
                                showSelectionPopup = true 
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedLinkedEntry?.title ?: "None",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selectedLinkedEntry != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                            }
                        }

                        if (showSelectionPopup) {
                            var linkedSearchQuery by remember { mutableStateOf("") }
                            val filteredEntries = remember(linkedSearchQuery, allEntries, entry) {
                                allEntries.filter { 
                                    it.id != entry?.id && 
                                    it.parentId == null && 
                                    it.linkedEntryId == null &&
                                    (linkedSearchQuery.isBlank() || it.title.contains(linkedSearchQuery, ignoreCase = true))
                                }
                            }
                            val scrollState = rememberScrollState()
                            val topAlpha by remember { derivedStateOf { (scrollState.value / 100f).coerceIn(0f, 1f) } }

                            Dialog(
                                onDismissRequest = { showSelectionPopup = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                var isVisible by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) { isVisible = true }

                                AnimatedVisibility(
                                    visible = isVisible,
                                    enter = fadeIn(animationSpec = tween(durationMillis = 400)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 300))
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                            .heightIn(max = 600.dp),
                                        shape = RoundedCornerShape(32.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        tonalElevation = 6.dp
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            val headerHeight = 80.dp
                                            
                                            // 1. Scrollable List
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(scrollState)
                                                    .padding(horizontal = 20.dp)
                                            ) {
                                                Spacer(modifier = Modifier.height(headerHeight + 8.dp))
                                                
                                                // "None" option
                                                LinkedSelectionItem(
                                                    title = "None",
                                                    isSelected = linkedEntryId == null,
                                                    index = 0,
                                                    total = filteredEntries.size + 1,
                                                    onClick = {
                                                        linkedEntryId = null
                                                        showSelectionPopup = false
                                                    }
                                                )
                                                
                                                filteredEntries.forEachIndexed { index, item ->
                                                    LinkedSelectionItem(
                                                        title = item.title,
                                                        isSelected = linkedEntryId == item.id,
                                                        index = index + 1,
                                                        total = filteredEntries.size + 1,
                                                        onClick = {
                                                            linkedEntryId = item.id
                                                            showSelectionPopup = false
                                                        }
                                                    )
                                                }
                                                
                                                if (filteredEntries.isEmpty() && linkedSearchQuery.isNotBlank()) {
                                                    Box(
                                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("No entries found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(24.dp))
                                            }

                                            // 2. Fixed Header Background Gradient
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(headerHeight + 24.dp)
                                                    .graphicsLayer(alpha = topAlpha)
                                                    .background(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                MaterialTheme.colorScheme.surfaceContainerLow,
                                                                MaterialTheme.colorScheme.surfaceContainerLow,
                                                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
                                                                Color.Transparent
                                                            )
                                                        )
                                                    )
                                            )

                                            // 3. Header Buttons (Close + Search)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp)
                                                    .height(56.dp)
                                                    .align(Alignment.TopCenter)
                                                    .offset(y = 20.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Surface(
                                                    onClick = { showSelectionPopup = false },
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    modifier = Modifier.size(56.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Rounded.Clear, contentDescription = "Close", modifier = Modifier.size(24.dp))
                                                    }
                                                }
                                                
                                                OutlinedTextField(
                                                    value = linkedSearchQuery,
                                                    onValueChange = { linkedSearchQuery = it },
                                                    placeholder = { Text("Search") },
                                                    leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                                    singleLine = true,
                                                    shape = CircleShape,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                        unfocusedBorderColor = Color.Transparent,
                                                        focusedBorderColor = Color.Transparent
                                                    )
                                                )
                                            }

                                            // 4. Bottom Gradient Fade
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(32.dp)
                                                    .align(Alignment.BottomCenter)
                                                    .background(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerLow)
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tag Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tags", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { showAddTagDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add New Tag")
                        }
                    }
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Selected tags in their specific order
                        selectedTagIds.forEach { tagId ->
                            allTags.find { it.id == tagId }?.let { tag ->
                                FilterChip(
                                    selected = true,
                                    onClick = { selectedTagIds = selectedTagIds - tagId },
                                    label = { Text(tag.name) }
                                )
                            }
                        }
                        // Unselected tags in global order
                        allTags.filter { it.id !in selectedTagIds }.forEach { tag ->
                            FilterChip(
                                selected = false,
                                onClick = { selectedTagIds = selectedTagIds + tag.id },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

    if (showWheelPicker && listType == ListType.RATING) {
        RatingPickerDialog(
            rating = rating,
            onRatingChange = { rating = it },
            onDismiss = { showWheelPicker = false }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDate = datePickerState.selectedDateMillis
                        if (selectedDate != null) {
                            val calendar = Calendar.getInstance()
                            dueDate?.let { calendar.timeInMillis = it }
                            val timePartCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                            calendar.set(Calendar.YEAR, timePartCal.get(Calendar.YEAR))
                            calendar.set(Calendar.MONTH, timePartCal.get(Calendar.MONTH))
                            calendar.set(Calendar.DAY_OF_MONTH, timePartCal.get(Calendar.DAY_OF_MONTH))
                            dueDate = calendar.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val calendar = Calendar.getInstance()
        dueDate?.let { calendar.timeInMillis = it }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true
        )
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newCal = Calendar.getInstance()
                        dueDate?.let { newCal.timeInMillis = it }
                        newCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        newCal.set(Calendar.MINUTE, timePickerState.minute)
                        newCal.set(Calendar.SECOND, 0)
                        dueDate = newCal.timeInMillis
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    if (showAddTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("New Tag") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            val newTag = Tag(name = newTagName, listId = listId)
                            onSaveTag(newTag)
                            selectedTagIds = selectedTagIds + newTag.id
                            showAddTagDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel") }
            }
        )
    }
}


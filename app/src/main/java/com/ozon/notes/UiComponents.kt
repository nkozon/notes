package com.ozon.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.3f))
            .clickable(
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (enabled) contentColor else contentColor.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun SortDropdown(
    selectedOrder: ListSortOrder,
    onOrderSelected: (ListSortOrder) -> Unit,
    availableOrders: List<ListSortOrder> = listOf(ListSortOrder.ALPHABETICAL, ListSortOrder.REVERSE_ALPHABETICAL),
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(34.dp)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = selectedOrder.toShortLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Sort",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(280.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            var selectedGroup by remember { 
                mutableStateOf(
                    if (selectedOrder == ListSortOrder.TAG_ALPHABETICAL || selectedOrder == ListSortOrder.TAG_REVERSE_ALPHABETICAL) 1 else 0
                ) 
            }

            val titleOptions = availableOrders.filter { it != ListSortOrder.TAG_ALPHABETICAL && it != ListSortOrder.TAG_REVERSE_ALPHABETICAL }
            val tagOptions = availableOrders.filter { it == ListSortOrder.TAG_ALPHABETICAL || it == ListSortOrder.TAG_REVERSE_ALPHABETICAL }
            val hasTagsGroup = tagOptions.isNotEmpty()

            // DropdownMenu adds 8.dp vertical padding by default. 
            // We add horizontal padding only to achieve a perfectly symmetrical 8.dp border.
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                // Header Selector (only if both groups exist)
                if (hasTagsGroup) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
                            .padding(4.dp)
                    ) {
                        listOf("Title", "Tags").forEachIndexed { index, title ->
                            val isSelected = selectedGroup == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { selectedGroup = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = if (isSelected) MaterialTheme.typography.titleMedium.fontFamily else MaterialTheme.typography.labelLarge.fontFamily
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Options with animation
                AnimatedContent(
                    targetState = selectedGroup,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                        } else {
                            slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        }
                    },
                    label = "SortOptions"
                ) { groupIndex ->
                    val filteredOptions = if (groupIndex == 0) titleOptions else tagOptions

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filteredOptions.forEachIndexed { index, order ->
                            val isSelected = selectedOrder == order
                            val shape = if (isSelected) {
                                CircleShape
                            } else {
                                when {
                                    filteredOptions.size == 1 -> RoundedCornerShape(16.dp)
                                    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                    index == filteredOptions.size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                    else -> RoundedCornerShape(4.dp)
                                }
                            }

                            Surface(
                                onClick = {
                                    onOrderSelected(order)
                                    expanded = false
                                },
                                shape = shape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when(order) {
                                            ListSortOrder.ALPHABETICAL -> "Alphabetical"
                                            ListSortOrder.REVERSE_ALPHABETICAL -> "Reverse alphabetical"
                                            ListSortOrder.TAG_ALPHABETICAL -> "Alphabetical"
                                            ListSortOrder.TAG_REVERSE_ALPHABETICAL -> "Reverse alphabetical"
                                            ListSortOrder.RATING_LOW_TO_HIGH -> "Increasing score"
                                            ListSortOrder.RATING_HIGH_TO_LOW -> "Decreasing score"
                                            ListSortOrder.NEWEST -> "Newest first"
                                            ListSortOrder.OLDEST -> "Oldest first"
                                        },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = if (isSelected) MaterialTheme.typography.titleMedium.fontFamily else MaterialTheme.typography.bodyLarge.fontFamily
                                        ),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = order.toShortLabel(),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = if (isSelected) MaterialTheme.typography.titleMedium.fontFamily else MaterialTheme.typography.labelLarge.fontFamily
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionWrapper(
    onDelete: () -> Unit,
    onPin: () -> Unit,
    isPinned: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val currentOnDelete by androidx.compose.runtime.rememberUpdatedState(onDelete)
    val currentOnPin by androidx.compose.runtime.rememberUpdatedState(onPin)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    currentOnDelete()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnPin()
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isSettled = direction == SwipeToDismissBoxValue.Settled

            val color = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }

            val scale by animateFloatAsState(
                targetValue = if (isSettled) 0.5f else 1.2f,
                label = "iconScale"
            )

            val alpha by animateFloatAsState(
                targetValue = if (isSettled) 0f else 1f,
                label = "iconAlpha"
            )

            val alignment = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.Center
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .scale(scale)
                            .graphicsLayer(alpha = alpha)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .scale(scale)
                            .graphicsLayer(alpha = alpha)
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Outlined.PushPin else Icons.Rounded.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isPinned) "Unpin" else "Pin",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListCard(
    list: NoteList,
    entryCount: Int,
    subEntryCount: Int,
    checkedCount: Int,
    showCounts: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    isSelected: Boolean = false,
    watchingCount: Int = 0
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = list.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (list.isPinned) {
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
            supportingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when(list.type) {
                            ListType.CHECKLIST -> "Checklist"
                            ListType.RATING -> "Rating List"
                            ListType.UPCOMING -> "Upcoming List"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (showCounts) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (list.type == ListType.CHECKLIST || list.type == ListType.UPCOMING) {
                                val total = entryCount + subEntryCount
                                val unchecked = total - checkedCount
                                "$unchecked entries, $checkedCount checked"
                            } else {
                                val sectionName = list.getEffectiveCurrentSectionName()
                                val watchingShort = when {
                                    sectionName.contains("read", ignoreCase = true) -> "reading"
                                    sectionName.contains("play", ignoreCase = true) -> "playing"
                                    sectionName.contains("listen", ignoreCase = true) -> "listening"
                                    else -> "watching"
                                }
                                "$entryCount entries${if (watchingCount > 0) ", $watchingCount $watchingShort" else ""}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(list.type) {
                            ListType.CHECKLIST -> Icons.AutoMirrored.Rounded.List
                            ListType.RATING -> Icons.Rounded.Star
                            ListType.UPCOMING -> Icons.Rounded.Event
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingTitleLayout(
    title: String,
    onNavigateUp: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    
    val expandedHeightPx = with(density) { (152.dp + topPadding).toPx() }
    val collapsedHeightPx = with(density) { (64.dp + topPadding).toPx() }
    val scrollLimitPx = with(density) { (64.dp - 152.dp).toPx() }
    
    LaunchedEffect(scrollLimitPx) {
        if (scrollBehavior.state.heightOffsetLimit != scrollLimitPx) {
            scrollBehavior.state.heightOffsetLimit = scrollLimitPx
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val fraction = scrollBehavior.state.collapsedFraction
                val easedFraction = FastOutSlowInEasing.transform(fraction)
                val heightPx = lerp(expandedHeightPx, collapsedHeightPx, easedFraction).roundToInt()
                val clampedHeight = heightPx.coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = clampedHeight,
                        maxHeight = clampedHeight
                    )
                )
                layout(placeable.width, clampedHeight) {
                    placeable.placeRelative(0, 0)
                }
            }
    ) {
        // Back Button
        Box(
            modifier = Modifier
                .padding(top = topPadding + 10.dp, start = 16.dp)
                .size(44.dp)
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            CircleIconButton(
                onClick = onNavigateUp,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back"
            )
        }
        
        // Actions
        Row(
            modifier = Modifier
                .padding(top = topPadding + 10.dp)
                .height(44.dp)
                .align(Alignment.TopEnd)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )

        // Title
        val displayStyle = MaterialTheme.typography.displaySmall
        val titleStyle = MaterialTheme.typography.titleLarge
        
        Text(
            text = title,
            style = displayStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer {
                    val fraction = scrollBehavior.state.collapsedFraction
                    val easedFraction = FastOutSlowInEasing.transform(fraction)
                    val targetScale = titleStyle.fontSize.toPx() / displayStyle.fontSize.toPx()
                    val scale = lerp(1f, targetScale, easedFraction)
                    scaleX = scale
                    scaleY = scale
                    
                    // Horizontal Position
                    // Expanded: 16dp (align with card edges)
                    // Collapsed: 76dp (16dp edge + 44dp button + 16dp spacing)
                    translationX = lerp(16.dp.toPx(), 76.dp.toPx(), easedFraction)
                    
                    // Vertical Alignment (Relative to topPadding)
                    // Header row is 64dp high. Center is at 32dp.
                    // We must subtract half the text height to align centers.
                    val textHeight = size.height
                    val collapsedY = 32.dp.toPx() - (textHeight / 2f)
                    val expandedY = 100.dp.toPx() - (textHeight / 2f)
                    
                    translationY = topPadding.toPx() + lerp(expandedY, collapsedY, easedFraction)
                    
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
        )
    }
}

@Composable
fun SystemBarGradients(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
    showTop: Boolean = true,
    showBottom: Boolean = true,
    topAlpha: () -> Float = { 1f },
    bottomAlpha: () -> Float = { 1f },
    bottomHeight: Dp? = null
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        // Status Bar Gradient
        if (showTop) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight * 3f)
                    .graphicsLayer { alpha = topAlpha() }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                color,
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )
        }

        // Navigation Bar Gradient
        if (showBottom) {
            val effBottomHeight = bottomHeight ?: maxOf(navigationBarHeight * 5f, 160.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(effBottomHeight)
                    .graphicsLayer { alpha = bottomAlpha() }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                color
                            )
                        )
                    )
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun SystemBarGradients(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
    showTop: Boolean = true,
    showBottom: Boolean = true,
    topAlpha: Float,
    bottomAlpha: Float = 1f,
    bottomHeight: Dp? = null
) {
    SystemBarGradients(
        modifier = modifier,
        color = color,
        showTop = showTop,
        showBottom = showBottom,
        topAlpha = { topAlpha },
        bottomAlpha = { bottomAlpha },
        bottomHeight = bottomHeight
    )
}

@Composable
fun FullColorPickerDialog(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var hsv by remember(initialColor) { 
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        mutableStateOf(hsv)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(android.graphics.Color.HSVToColor(hsv)))
                        .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                )
                
                Text("Hue: ${hsv[0].toInt()}°", style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[0], onValueChange = { hsv = hsv.copyOf().apply { set(0, it) } }, valueRange = 0f..360f)
                
                Text("Saturation: ${(hsv[1] * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[1], onValueChange = { hsv = hsv.copyOf().apply { set(1, it) } }, valueRange = 0f..1f)
                
                Text("Value: ${(hsv[2] * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[2], onValueChange = { hsv = hsv.copyOf().apply { set(2, it) } }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorChange(Color(android.graphics.Color.HSVToColor(hsv))) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SectionHeader(
    title: String,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            onClick = onAddClick,
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "Add $title",
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .size(24.dp)
            )
        }
    }
}

@Composable
fun SettingsItemContainer(
    index: Int,
    total: Int,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val topRadius = if (index == 0) 28.dp else 4.dp
    val bottomRadius = if (index == total - 1) 28.dp else 4.dp
    val shape = RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
fun BackupLoadingDialog(
    title: String,
    subTitle: String = "Please wait while your data is being processed..."
) {
    Dialog(
        onDismissRequest = { /* Modal non-dismissible during active file I/O */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

fun MainTab.getTitle(): String = when (this) {
    MainTab.TEXT -> "Text"
    MainTab.DRAWINGS -> "Drawings"
    MainTab.CHECKLISTS -> "Checklists"
    MainTab.RATINGS -> "Ratings"
    MainTab.UPCOMING -> "Upcoming"
}

fun MainTab.getIcon(): ImageVector = when (this) {
    MainTab.TEXT -> Icons.Rounded.Description
    MainTab.DRAWINGS -> Icons.Rounded.Brush
    MainTab.CHECKLISTS -> Icons.Rounded.CheckBox
    MainTab.RATINGS -> Icons.Rounded.Star
    MainTab.UPCOMING -> Icons.Rounded.Event
}

@Composable
fun RatingListActiveSectionSelector(
    sectionName: String,
    onSectionNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val presetSuggestions = remember {
        listOf(
            "Currently Watching",
            "Currently Reading",
            "Currently Playing",
            "Currently Listening"
        )
    }
    val isPreset = presetSuggestions.contains(sectionName)
    var isCustom by remember(sectionName) { mutableStateOf(!isPreset && sectionName.isNotBlank()) }
    var selectedPreset by remember(sectionName) {
        mutableStateOf(if (isPreset) sectionName else presetSuggestions[0])
    }
    var customText by remember(sectionName) {
        mutableStateOf(if (!isPreset) sectionName else "")
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Active Section Title", style = MaterialTheme.typography.labelMedium)

        if (!isCustom) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val leadingIcon = when (selectedPreset) {
                    "Currently Watching" -> Icons.Rounded.Tv
                    "Currently Reading" -> Icons.AutoMirrored.Rounded.MenuBook
                    "Currently Playing" -> Icons.Rounded.SportsEsports
                    "Currently Listening" -> Icons.Rounded.Headphones
                    else -> Icons.Rounded.Bookmark
                }
                OutlinedTextField(
                    value = selectedPreset,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = if (dropdownExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Select Preset",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { dropdownExpanded = true }
                )
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    presetSuggestions.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = preset,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                val icon = when (preset) {
                                    "Currently Watching" -> Icons.Rounded.Tv
                                    "Currently Reading" -> Icons.AutoMirrored.Rounded.MenuBook
                                    "Currently Playing" -> Icons.Rounded.SportsEsports
                                    "Currently Listening" -> Icons.Rounded.Headphones
                                    else -> Icons.Rounded.Bookmark
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            onClick = {
                                selectedPreset = preset
                                onSectionNameChange(preset)
                                dropdownExpanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Custom...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            dropdownExpanded = false
                            isCustom = true
                            onSectionNameChange(customText.ifBlank { selectedPreset })
                        }
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = customText,
                onValueChange = {
                    customText = it
                    onSectionNameChange(it)
                },
                placeholder = { Text(selectedPreset) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            OutlinedButton(
                onClick = {
                    isCustom = false
                    onSectionNameChange(selectedPreset)
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.List,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Presets")
            }
        }
    }
}

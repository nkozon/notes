package com.ozon.notes

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterDropdown(
    selectedTagIds: Set<String>,
    allTags: List<Tag>,
    filterMode: TagFilterMode,
    onTagToggle: (String) -> Unit,
    onModeToggle: (TagFilterMode) -> Unit,
    onClearAll: () -> Unit,
    onReorderTags: (List<String>) -> Unit = {},
    onDeleteTag: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    val hazeState = LocalHazeState.current
    val useBlur = LocalAdvancedUiEnabled.current && hazeState != null
    LiveBlurSourceUpdates(hazeState, enabled = expanded && useBlur)
    val menuShape = RoundedCornerShape(28.dp)
    val menuColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.FilterAlt,
                contentDescription = "Filter by Tag",
                tint = if (selectedTagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(280.dp)
                .advancedUiBlur(
                    hazeState = hazeState,
                    shape = menuShape,
                    backgroundColor = MaterialTheme.colorScheme.background,
                    enabled = useBlur
                )
                .background(
                    advancedUiSurfaceColor(originalColor = menuColor, enabled = useBlur),
                    menuShape
                ),
            shape = menuShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                listOf(TagFilterMode.OR to "OR", TagFilterMode.AND to "AND").forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = filterMode == mode,
                        onClick = { onModeToggle(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(label)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            allTags.forEachIndexed { index, tag ->
                val isSelected = tag.id in selectedTagIds
                val itemShape = when {
                    isSelected -> CircleShape
                    allTags.size == 1 -> RoundedCornerShape(16.dp)
                    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    index == allTags.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    else -> RoundedCornerShape(4.dp)
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    },
                    onClick = { onTagToggle(tag.id) },
                    leadingIcon = {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkmarkColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .fillMaxWidth()
                        .clip(itemShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                            itemShape
                        )
                        .semantics {
                            role = Role.Checkbox
                            toggleableState = if (isSelected) ToggleableState.On else ToggleableState.Off
                        },
                    colors = MenuDefaults.itemColors(
                        textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            if (allTags.isEmpty()) {
                Text(
                    "No tags available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (selectedTagIds.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Clear all filters") },
                    onClick = onClearAll,
                    leadingIcon = { Icon(Icons.Rounded.Clear, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp).clip(MaterialTheme.shapes.small)
                )
            }
            if (allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            expanded = false
                            showManageTagsDialog = true
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Manage tags")
                    }
                }
            }
        }
    }

    if (showManageTagsDialog) {
        ManageTagsDialog(
            tags = allTags,
            onDismiss = { showManageTagsDialog = false },
            onReorder = onReorderTags,
            onDelete = onDeleteTag
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTagsDialog(
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (String) -> Unit
) {
    var currentTags by remember { mutableStateOf(tags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Tags") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                currentTags.forEachIndexed { index, tag ->
                    val isFirst = index == 0
                    val isLast = index == currentTags.size - 1
                    
                    val shape = when {
                        currentTags.size == 1 -> RoundedCornerShape(24.dp)
                        isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                        isLast -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(4.dp)
                    }

                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tag.name, style = MaterialTheme.typography.titleMedium)
                            }
                            
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val newList = currentTags.toMutableList()
                                        val temp = newList[index]
                                        newList[index] = newList[index - 1]
                                        newList[index - 1] = temp
                                        currentTags = newList
                                        onReorder(newList.map { it.id })
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Rounded.ExpandLess, contentDescription = "Move Up")
                            }
                            
                            IconButton(
                                onClick = {
                                    if (index < currentTags.size - 1) {
                                        val newList = currentTags.toMutableList()
                                        val temp = newList[index]
                                        newList[index] = newList[index + 1]
                                        newList[index + 1] = temp
                                        currentTags = newList
                                        onReorder(newList.map { it.id })
                                    }
                                },
                                enabled = index < currentTags.size - 1
                            ) {
                                Icon(Icons.Rounded.ExpandMore, contentDescription = "Move Down")
                            }

                            IconButton(
                                onClick = {
                                    onDelete(tag.id)
                                    currentTags = currentTags.filter { it.id != tag.id }
                                },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete Tag")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}


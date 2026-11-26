package com.ozon.notes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun BackupSelectionDialog(
    picker: LocalBackupState.Picking,
    onSelectionChange: (Set<BackupItem>) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val groups = remember(picker.items) { picker.items.groupBy { it.type }.toSortedMap() }
    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val colors = MaterialTheme.colorScheme
    val restoring = picker.restoring
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 680.dp),
            shape = RoundedCornerShape(32.dp),
            color = colors.surfaceContainerHigh
        ) {
            Column {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    item {
                        Column(Modifier.padding(horizontal = 8.dp)) {
                            Surface(shape = RoundedCornerShape(18.dp), color = colors.primaryContainer) {
                                Icon(
                                    if (restoring) Icons.Rounded.Restore else Icons.Rounded.Backup,
                                    contentDescription = null, modifier = Modifier.padding(12.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(if (restoring) "Restore from backup" else "Create backup", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    picker.items.isEmpty() && restoring -> "This backup contains no notes or lists."
                                    picker.items.isEmpty() -> "Your library has no notes or lists to back up."
                                    restoring -> "Choose what to restore. Each selected item will be added as a new copy."
                                    else -> "Choose entire types or individual notes and lists."
                                },
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                            )
                        }
                    }
                    if (picker.items.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${picker.selected.size} of ${picker.items.size} selected", style = MaterialTheme.typography.labelLarge)
                                val allSelected = picker.selected.size == picker.items.size
                                TextButton(onClick = {
                                    onSelectionChange(if (allSelected) emptySet() else picker.items.toSet())
                                }) { Text(if (allSelected) "Clear all" else "Select all") }
                            }
                        }
                    }
                    groups.forEach { (type, members) ->
                        val expanded = type.name !in collapsed
                        val selectedCount = members.count { it in picker.selected }
                        item(key = type.name) {
                            val state = when (selectedCount) {
                                0 -> ToggleableState.Off
                                members.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            }
                            val corners = if (expanded) 5.dp else 20.dp
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                shape = RoundedCornerShape(20.dp, 20.dp, corners, corners),
                                color = colors.secondaryContainer
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        Modifier.weight(1f).triStateToggleable(state, role = Role.Checkbox) {
                                            onSelectionChange(if (state == ToggleableState.On) picker.selected - members.toSet() else picker.selected + members)
                                        }.heightIn(min = 52.dp).padding(start = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        TriStateCheckbox(state, onClick = null)
                                        Icon(type.getIcon(), contentDescription = null, modifier = Modifier.size(20.dp))
                                        Text(type.getTitle(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                        Text("$selectedCount/${members.size}", style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(onClick = {
                                        collapsed = if (expanded) collapsed + type.name else collapsed - type.name
                                    }) {
                                        val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "Expand group")
                                        Icon(
                                            Icons.Rounded.ExpandMore,
                                            contentDescription = "${if (expanded) "Collapse" else "Expand"} ${type.getTitle()}",
                                            modifier = Modifier.rotate(rotation)
                                        )
                                    }
                                }
                            }
                        }
                        if (expanded) {
                            items(members, key = { "${type.name}/${it.id}" }) { item ->
                                val checked = item in picker.selected
                                val bottom = if (item == members.last()) 20.dp else 5.dp
                                Surface(
                                    shape = RoundedCornerShape(5.dp, 5.dp, bottom, bottom),
                                    color = colors.surfaceContainerLowest
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox) {
                                            onSelectionChange(if (it) picker.selected + item else picker.selected - item)
                                        }.heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Checkbox(checked, onCheckedChange = null)
                                        Column {
                                            Text(item.displayTitle, style = MaterialTheme.typography.bodyLarge)
                                            if (item.repeatedInBackup) {
                                                Text("Name appears more than once in this backup", style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                                            }
                                            if (item.sameName) {
                                                Text("Name already in your library", style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = onConfirm, enabled = picker.selected.isNotEmpty()) {
                        Text(if (restoring) "Restore" else "Choose location")
                    }
                }
            }
        }
    }
}

package com.ozon.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Keeps popup contents sharp while applying the selected glass material to their backdrop. */
@Composable
fun AdvancedUiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current
    val useBlur = LocalAdvancedUiEnabled.current && hazeState != null
    LiveBlurSourceUpdates(hazeState, enabled = expanded && useBlur)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = if (useBlur) {
            modifier.advancedUiBlur(
                hazeState = hazeState,
                shape = shape,
                backgroundColor = MaterialTheme.colorScheme.background,
                liquidGlass = true,
            ).background(
                advancedUiSurfaceColor(originalColor = containerColor, liquidGlass = true),
                shape,
            )
        } else modifier,
        shape = shape,
        containerColor = if (useBlur) Color.Transparent else containerColor,
        tonalElevation = if (useBlur) 0.dp else 3.dp,
        content = content,
    )
}

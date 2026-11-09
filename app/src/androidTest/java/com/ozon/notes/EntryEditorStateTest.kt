package com.ozon.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test

class EntryEditorStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun extractedDescriptionEditorKeepsUnsavedTextAcrossParentRecomposition() {
        RepositoryTestEnvironment().use { environment ->
            val title = mutableStateOf("Original title")
            lateinit var viewModel: ChecklistViewModel
            compose.runOnUiThread { viewModel = ChecklistViewModel(environment.repository) }
            compose.setContent {
                MaterialTheme {
                    DescriptionDialog(
                        entry = ListEntry(id = "entry", listId = "list", title = title.value, description = "original"),
                        viewModel = viewModel,
                        onDismiss = {}, onConfirm = {}
                    )
                }
            }
            compose.onNodeWithText("original").performTextReplacement("unsaved edit")
            compose.runOnIdle { title.value = "Updated title" }
            compose.onNodeWithText("Updated title").assertExists()
            compose.onNodeWithText("unsaved edit").assertTextContains("unsaved edit")
        }
    }
}

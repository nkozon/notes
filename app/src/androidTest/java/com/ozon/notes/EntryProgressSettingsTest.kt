package com.ozon.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EntryProgressSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun statusButtonsAreExclusiveAndCanBothBeOffWithoutLosingProgress() {
        val draft = mutableStateOf(EntryProgressDraft(EntryProgressStatus.NONE, "3", "12", "episodes"))
        compose.setContent {
            MaterialTheme {
                EntryProgressSettings("Currently watching", draft.value) { draft.value = it }
            }
        }
        compose.onNodeWithText("Currently watching").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Current progress").assertExists()
        compose.onNodeWithText("Paused").performClick().assertIsOn()
        compose.onNodeWithText("Currently watching").assertIsOff()
        compose.onNodeWithText("Paused").performClick().assertIsOff()
        compose.onNodeWithText("Currently watching").assertIsOff()
        compose.onNodeWithContentDescription("Current progress").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(EntryProgressStatus.NONE, draft.value.status)
            assertEquals("3", draft.value.current)
            assertEquals("12", draft.value.total)
        }
    }

    @Test fun customUnitsCanBeNamedAndReplacedWithAPreset() {
        val draft = mutableStateOf(EntryProgressDraft(EntryProgressStatus.WATCHING, "", "", "hours"))
        compose.setContent {
            MaterialTheme {
                EntryProgressSettings("Currently watching", draft.value) { draft.value = it }
            }
        }
        compose.onNodeWithText("Unit · hours").assertExists()
        compose.onNodeWithContentDescription("Custom progress unit").performClick()
        compose.onNodeWithText("Unit name").performTextReplacement("volumes")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Unit · volumes").assertExists()
        compose.runOnIdle { assertEquals("volumes", draft.value.unit) }
        compose.onNodeWithText("Pages").performClick().assertIsOn()
        compose.runOnIdle { assertEquals("pages", draft.value.unit) }
    }
}

package com.ozon.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProgressSectionSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun headerAndRowsHaveNoCutsAcrossLazyItemSpacing() {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primaryContainer = Color.Blue)) {
                LazyColumn(
                    modifier = Modifier.size(240.dp, 240.dp).background(Color.Black).testTag("section"),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ListDetailItemSpacing)
                ) {
                    items(3) { index ->
                        ProgressSectionSurface(isHeader = index == 0, connectToNext = index < 2) {
                            Box(Modifier.height(60.dp))
                        }
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("section").captureToImage().toPixelMap()
        val density = pixels.width / 240f
        val x = (2 * density).toInt() // Side rail: well outside the inner cards.
        val sectionColor = pixels[x, (40 * density).toInt()]
        assertNotEquals(Color.Black, sectionColor)
        for (yDp in listOf(59f, 60f, 62f, 64f, 123f, 124f, 126f, 128f)) {
            assertEquals("Cut at $yDp dp", sectionColor, pixels[x, (yDp * density).toInt()])
        }
        assertEquals(Color.Black, pixels[0, 0])
        assertEquals(Color.Black, pixels[0, (187 * density).toInt()])
    }

    @Test
    fun fractionalDisplayDensityDoesNotLeavePixelSeams() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(2.625f)
            ) {
                MaterialTheme(colorScheme = darkColorScheme(primaryContainer = Color.Blue)) {
                    LazyColumn(
                        modifier = Modifier.size(240.dp, 240.dp).background(Color.Black).testTag("section"),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ListDetailItemSpacing)
                    ) {
                        items(3) { index ->
                            ProgressSectionSurface(isHeader = index == 0, connectToNext = index < 2) {
                                Box(Modifier.height(60.dp))
                            }
                        }
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("section").captureToImage().toPixelMap()
        val density = pixels.width / 240f
        val x = (2 * density).toInt()
        val color = pixels[x, (40 * density).toInt()]
        assertNotEquals(Color.Black, color)
        // Check every scanline, including the final pixel of each rounded spacing interval.
        for (y in (40 * density).toInt() until (150 * density).toInt()) {
            assertEquals("Seam at pixel $y", color, pixels[x, y])
        }
    }

    @Test
    fun expansionAndCollapseAnimateHeightWithoutBackgroundCuts() {
        val expanded = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primaryContainer = Color.Blue)) {
                val reveal = progressSectionReveal(expanded.value)
                LazyColumn(
                    modifier = Modifier.size(240.dp, 240.dp).background(Color.Black).testTag("section"),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ListDetailItemSpacing)
                ) {
                    item(key = "header") {
                        ProgressSectionSurface(isHeader = true, connectToNext = reveal > 0f,
                            modifier = Modifier.testTag("header")) {
                            Box(Modifier.height(60.dp))
                        }
                    }
                    if (reveal > 0f) {
                        items(2, key = { "row_$it" }) { index ->
                            ProgressSectionSurface(
                                isHeader = false,
                                connectToNext = index == 0,
                                revealFraction = reveal,
                                modifier = Modifier.testTag("row_$index")
                            ) {
                                Box(Modifier.height(60.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            repeat(2) {
                for (opening in listOf(true, false)) {
                    compose.runOnIdle { expanded.value = opening }
                    // Start the transition, then inspect a frame well before it completes.
                    compose.mainClock.advanceTimeByFrame()
                    compose.mainClock.advanceTimeBy(96)
                    val rootBounds = compose.onNodeWithTag("section").fetchSemanticsNode().boundsInRoot
                    val rowBounds = compose.onNodeWithTag("row_0").fetchSemanticsNode().boundsInRoot
                    val pixels = compose.onNodeWithTag("section").captureToImage().toPixelMap()
                    val density = pixels.width / 240f
                    assertTrue("Row must have intermediate height while opening=$opening",
                        rowBounds.height > 0 && rowBounds.height < 60 * density)
                    val x = (2 * density).toInt()
                    val color = pixels[x, (40 * density).toInt()]
                    for (tag in listOf("header", "row_0")) {
                        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                        val gapY = (bounds.bottom - rootBounds.top + 2 * density).toInt()
                        assertEquals("Gap below $tag while opening=$opening", color, pixels[x, gapY])
                    }
                    compose.mainClock.advanceTimeBy(400)
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun sectionStillComposesOnlyVisibleRows() {
        val composed = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        compose.setContent {
            MaterialTheme {
                LazyColumn(
                    modifier = Modifier.size(240.dp, 240.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ListDetailItemSpacing)
                ) {
                    items((0 until 500).toList(), key = { it }) { index ->
                        androidx.compose.runtime.SideEffect { composed.add(index) }
                        ProgressSectionSurface(isHeader = index == 0, connectToNext = index < 499) {
                            Box(Modifier.height(60.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertTrue("Composed ${composed.size} rows", composed.size < 20)
        assertFalse(composed.contains(499))
    }
}

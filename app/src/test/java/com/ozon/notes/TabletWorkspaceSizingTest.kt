package com.ozon.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletWorkspaceSizingTest {
    @Test
    fun resizingOnTabletsLeavesRoomForTheEditor() {
        for (window in listOf(680f, 840f, 1200f, 1600f)) {
            for (requested in listOf(-100f, 280f, 360f, 2000f)) {
                val sidebar = tabletSidebarWidth(window, requested)
                assertTrue("Sidebar must remain usable", sidebar >= 280f)
                assertTrue("Editor must retain at least 360 dp", window - sidebar - TabletResizeHandleWidth >= 360f)
            }
        }
    }

    @Test
    fun forcedTabletModeStillFitsSmallWindows() {
        for (window in listOf(320f, 400f, 600f)) {
            val sidebar = tabletSidebarWidth(window, 2000f)
            assertTrue(sidebar > 0f)
            assertTrue("Editor cannot disappear behind the sidebar", sidebar + TabletResizeHandleWidth < window)
        }
    }

    @Test
    fun windowResizeDoesNotChangeThePreferredWidth() {
        val preferred = 400f
        assertTrue(tabletSidebarWidth(700f, preferred) < preferred)
        assertEquals(preferred, tabletSidebarWidth(1200f, preferred), 0f)
        assertEquals(DefaultTabletSidebarWidth, tabletSidebarWidth(1200f, Float.NaN), 0f)
    }
}

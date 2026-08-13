# UI Bug Fixes - Drawing and Text Screens

This plan addresses several UI bugs reported in the drawing and text note screens, specifically related to component rendering order (gradients) and toolbar organization.

## User Review Required

> [!NOTE]
> The "dark status bar" requirement for the drawing screen is interpreted as a dark background with light icons (Dark Mode style), which is common for drawing applications to minimize distractions. If the user meant dark icons on a light background, this can be easily adjusted.

## Proposed Changes

### [Drawing Screen]

Summary: Fix toolbar rendering order, move undo/redo/paste to a new sub-header, and set status bar style.

#### [MODIFY] [DrawingNoteScreen.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/DrawingNoteScreen.kt)
- Move `IconButton`s for Undo, Redo, and Paste from `TopAppBar` actions.
- Implement a new `DrawingUtilityToolbar` component to house these actions, positioned below the `TopAppBar`.
- Adjust `zIndex` of `SystemBarGradients` and other components to ensure toolbars are rendered above gradients.
- Add a `LaunchedEffect` to force a dark status bar (light icons) while on this screen.

---

### [Text Note Screen]

Summary: Fix title and body rendering order relative to gradients.

#### [MODIFY] [AddNoteScreen.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/AddNoteScreen.kt)
- Increase `zIndex` of the main `Column` (containing title and editor) to ensure it renders above the `SystemBarGradients`.

## Verification Plan

### Automated Tests
- N/A (UI layout changes are best verified manually in this context)

### Manual Verification
1. **Drawing Screen:**
   - Open a drawing note.
   - Verify the status bar is dark.
   - Verify the main drawing toolbar at the bottom is fully visible and not partially obscured by the gradient.
   - Verify the new utility toolbar (Undo/Redo/Paste) appears below the header.
2. **Text Note Screen:**
   - Open a text note.
   - Verify the title and body text are clearly visible and rendered above the status/navigation bar gradients.

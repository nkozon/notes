package com.ozon.notes

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SelectiveBackupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pickerSupportsWholeTypesAndIndividualItems() {
        val first = BackupItem("a", "Journal", MainTab.TEXT, sameName = true)
        val second = BackupItem("b", "Draft", MainTab.TEXT)
        val state = mutableStateOf(LocalBackupState.Picking(listOf(first, second), restoring = true))
        var confirmed = emptySet<BackupItem>()
        compose.setContent {
            MaterialTheme {
                BackupSelectionDialog(
                    state.value, { state.value = state.value.copy(selected = it) },
                    onConfirm = { confirmed = state.value.selected }, onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Name already in your library").assertExists()
        compose.onNodeWithText("Text").performClick()
        compose.onNodeWithText("Restore").assertIsNotEnabled()
        compose.onNodeWithText("Draft").performClick().assertIsOn()
        compose.onNodeWithText("1 of 2 selected").assertExists()
        compose.onNodeWithText("Restore").performClick()
        compose.runOnIdle { assertEquals(setOf(second), confirmed) }
    }

    @Test fun partialBackupAndRestoreKeepTheOriginalsAndListRelationships() = runBlocking {
        RepositoryTestEnvironment().use { env ->
            val repo = env.repository
            val list = NoteList(id = "shared", title = "Shopping", type = ListType.CHECKLIST)
            val note = Note(id = "shared", title = "Journal", content = "private")
            val other = list.copy(id = "other", title = "Movies", type = ListType.RATING)
            val parent = ListEntry(id = "p", listId = list.id, title = "Groceries")
            val child = ListEntry(id = "c", listId = list.id, title = "Fruit", parentId = parent.id,
                linkedEntryId = parent.id, description = "Apples", tagIds = listOf("tag"))
            val tag = Tag(id = "tag", listId = list.id, name = "Weekly")
            repo.importBackupData(BackupData(listOf(note), listOf(list, other), listOf(parent, child), listOf(tag)))
            val vm = SettingsViewModel(repo)
            try {
                withContext(Dispatchers.Main) { vm.chooseLocalBackupItems() }
                val picker = vm.awaitPicker()
                val selected = picker.items.single { it.title == "Shopping" }
                val archive = File(env.context.cacheDir, "selected.notesbackup")
                withContext(Dispatchers.Main) {
                    vm.updateBackupSelection(setOf(selected))
                    vm.confirmBackupSelection()
                    vm.saveLocalBackup(Uri.fromFile(archive))
                }
                assertEquals("Backup saved", vm.awaitMessage())
                assertTrue(repo.getHasPendingChanges().first())

                withContext(Dispatchers.Main) { vm.previewLocalRestore(Uri.fromFile(archive)) }
                val preview = vm.awaitPicker()
                assertEquals(listOf("Shopping"), preview.items.map { it.title })
                assertTrue(preview.items.single().sameName)
                withContext(Dispatchers.Main) { vm.dismissLocalBackup() }
                assertEquals(2, repo.getAllLists().first().size)

                withContext(Dispatchers.Main) { vm.previewLocalRestore(Uri.fromFile(archive)) }
                vm.awaitPicker()
                withContext(Dispatchers.Main) { vm.confirmBackupSelection() }
                assertEquals("Selected items restored", vm.awaitMessage())
                val data = repo.getBackupData()
                assertEquals(listOf("private"), data.notes.map { it.content })
                assertEquals(3, data.lists.size)
                val copy = data.lists.single { it.title == list.title && it.id != list.id }
                val copyParent = data.entries.single { it.listId == copy.id && it.title == parent.title }
                val copyChild = data.entries.single { it.listId == copy.id && it.title == child.title }
                assertEquals(copyParent.id, copyChild.parentId)
                assertEquals(copyParent.id, copyChild.linkedEntryId)
                assertEquals("Apples", copyChild.description)
                val copyTag = data.tags.single { it.listId == copy.id }
                assertEquals(listOf(copyTag.id), copyChild.tagIds)
                assertEquals("Weekly", copyTag.name)
                assertEquals("Apples", repo.getEntryDescription(child.id))
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    @Test fun drawingAttachmentsTravelWithTheirSelectedNote() = runBlocking {
        RepositoryTestEnvironment().use { env ->
            val repo = env.repository
            val pdf = File(env.context.filesDir, "pages.pdf").apply { writeText("PDF attachment") }
            val image = File(env.context.filesDir, "sketch.png").apply { writeText("image attachment") }
            val drawing = Note(id = "drawing", title = "Sketch", type = NoteType.DRAWING, content = "",
                drawingData = DrawingData(
                    pdfInfo = PdfInfo(pdf.absolutePath, "pages.pdf", 1),
                    images = listOf(DrawingImage(path = image.absolutePath, offset = DrawingPoint(0f, 0f), scale = DrawingPoint(1f, 1f)))
                ))
            val text = Note(id = "n", title = "Draft", content = "draft")
            repo.saveNote(drawing)
            repo.saveNote(text)
            val items = backupItems(listOf(drawing, text), emptyList())
            val pick = BackupSelection(items.filter { it.type == MainTab.DRAWINGS }.toSet())
            val output = ByteArrayOutputStream()
            repo.writeLocalBackup(output, pick)
            repo.getBackupEngine().withBackupData(output.toByteArray().inputStream()) { data ->
                assertEquals(listOf("Sketch"), data.notes.map { it.title })
                repo.importBackupData(data.restoreCopies(pick))
            }
            val copy = repo.getBackupData().notes.single { it.title == drawing.title && it.id != drawing.id }
            val media = requireNotNull(copy.drawingData)
            assertEquals("PDF attachment", File(requireNotNull(media.pdfInfo).localPath).readText())
            assertEquals("image attachment", File(media.images.single().path).readText())
            assertFalse(env.context.cacheDir.listFiles().orEmpty().any { it.name.startsWith("restore-media-") })

            val full = BackupSelection(backupItems(repo.getAllNotes().first(), emptyList()).toSet())
            repo.writeLocalBackup(ByteArrayOutputStream(), full)
            assertFalse(repo.getHasPendingChanges().first())
        }
    }

    @Test fun legacyJsonAndVersionOneArchiveBothPreviewBeforeRestoring() = runBlocking {
        RepositoryTestEnvironment().use { env ->
            val repo = env.repository
            repo.saveNote(Note(id = "n", title = " Journal ", content = "current"))
            val noteJson = """{"id":"n","title":"journal","content":"archived","colorArgb":0}"""
            val legacy = File(env.context.cacheDir, "legacy.json").apply {
                writeText("""{"notes":[$noteJson],"lists":[{"id":"list","title":"Skip me","type":"CHECKLIST"}],"entries":[]}""")
            }
            val granular = File(env.context.cacheDir, "granular.notesbackup")
            ZipOutputStream(granular.outputStream()).use { zip ->
                listOf("manifest.json" to """{"version":1,"noteCount":1}""", "notes/n.json" to noteJson).forEach { (path, text) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
            val vm = SettingsViewModel(repo)
            try {
                for (file in listOf(legacy, granular)) {
                    withContext(Dispatchers.Main) { vm.previewLocalRestore(Uri.fromFile(file)) }
                    val picker = vm.awaitPicker()
                    val item = picker.items.single { it.isNote }
                    assertTrue(item.sameName)
                    assertEquals("current", repo.getNoteContent("n"))
                    withContext(Dispatchers.Main) {
                        vm.updateBackupSelection(setOf(item))
                        vm.confirmBackupSelection()
                    }
                    assertEquals("Selected items restored", vm.awaitMessage())
                }
                assertTrue(repo.getAllLists().first().isEmpty())
                assertEquals(listOf("archived", "archived", "current"), repo.getBackupData().notes.map { it.content }.sorted())
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    private suspend fun SettingsViewModel.awaitPicker() = withTimeout(10_000) {
        localBackup.first { it is LocalBackupState.Picking || it is LocalBackupState.Message }.let {
            check(it is LocalBackupState.Picking) { it.toString() }
            it
        }
    }

    private suspend fun SettingsViewModel.awaitMessage() = withTimeout(10_000) {
        (localBackup.first { it is LocalBackupState.Message } as LocalBackupState.Message).text
    }
}

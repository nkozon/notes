package com.ozon.notes

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testDrawingBackupSerialization() {
        val json = kotlinx.serialization.json.Json { 
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        val stroke = Stroke(
            id = "s1",
            points = listOf(DrawingPoint(10f, 20f), DrawingPoint(30f, 40f)),
            colorArgb = 0xFF0000,
            width = 5f,
            tool = DrawingTool.PEN
        )
        val drawingData = DrawingData(
            strokes = listOf(stroke),
            images = emptyList(),
            canvasType = CanvasType.PAGED,
            pageLayout = PageLayout(width = 800f, height = 1200f),
            pageCount = 2,
            viewportScale = 1.5f
        )
        val note = Note(
            id = "n1",
            title = "My Drawing",
            content = "Drawing Note",
            type = NoteType.DRAWING,
            drawingData = drawingData
        )
        val backupData = BackupData(
            notes = listOf(note),
            lists = emptyList(),
            entries = emptyList()
        )

        val serialized = json.encodeToString(backupData)
        println("Serialized JSON: $serialized")
        assertTrue(serialized.contains("My Drawing"))
        assertTrue(serialized.contains("strokes"))
        assertTrue(serialized.contains("PAGED"))

        val deserialized = json.decodeFromString<BackupData>(serialized)
        assertEquals(1, deserialized.notes.size)
        val deserializedNote = deserialized.notes[0]
        assertEquals(NoteType.DRAWING, deserializedNote.type)
        assertNotNull(deserializedNote.drawingData)
        assertEquals(1, deserializedNote.drawingData!!.strokes.size)
        assertEquals(CanvasType.PAGED, deserializedNote.drawingData!!.canvasType)
    }

    @Test
    fun testDrawingWithImagesAndPdfBackupSerialization() {
        val json = kotlinx.serialization.json.Json { 
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        val stroke = Stroke(
            id = "s1",
            points = listOf(DrawingPoint(10f, 20f), DrawingPoint(30f, 40f)),
            colorArgb = 0x00FF00,
            width = 3.5f,
            tool = DrawingTool.PEN
        )
        val image = DrawingImage(
            id = "img1",
            path = "/fake/path/img.png",
            offset = DrawingPoint(50f, 60f),
            scale = DrawingPoint(200f, 300f),
            rotation = 45f,
            base64Data = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        )
        val pdfInfo = PdfInfo(
            localPath = "/fake/path/doc.pdf",
            originalName = "test.pdf",
            pageCount = 3,
            pageSizes = listOf(PdfPageSize(600f, 800f)),
            base64Data = "JVBERi0xLjQKJcOkw7zDtsOf..."
        )
        val drawingData = DrawingData(
            strokes = listOf(stroke),
            images = listOf(image),
            canvasType = CanvasType.PDF,
            pageLayout = PageLayout(width = 600f, height = 800f),
            pdfInfo = pdfInfo,
            pageCount = 3,
            viewportX = 10f,
            viewportY = 20f,
            viewportScale = 1.0f
        )
        val note = Note(
            id = "n2",
            title = "PDF Drawing Note",
            content = "Drawing Note",
            type = NoteType.DRAWING,
            drawingData = drawingData
        )
        val backupData = BackupData(
            notes = listOf(note),
            lists = emptyList(),
            entries = emptyList()
        )

        val serialized = json.encodeToString(backupData)
        val deserialized = json.decodeFromString<BackupData>(serialized)
        assertEquals(1, deserialized.notes.size)
        val deserializedNote = deserialized.notes[0]
        assertEquals(NoteType.DRAWING, deserializedNote.type)
        val deserializedDrawing = deserializedNote.drawingData
        assertNotNull(deserializedDrawing)
        assertEquals(1, deserializedDrawing!!.strokes.size)
        assertEquals(1, deserializedDrawing.images.size)
        assertEquals("img1", deserializedDrawing.images[0].id)
        assertEquals(image.base64Data, deserializedDrawing.images[0].base64Data)
        assertNotNull(deserializedDrawing.pdfInfo)
        assertEquals("test.pdf", deserializedDrawing.pdfInfo?.originalName)
        assertEquals(pdfInfo.base64Data, deserializedDrawing.pdfInfo?.base64Data)
    }

    @Test
    fun testNoteListEntityWithCountsToDomainMapping() {
        val entity = NoteListEntityWithCounts(
            list = NoteListEntity(
                id = "list1",
                title = "Movies",
                type = ListType.RATING.name,
                timestamp = 1000L,
                isPinned = false,
                sortOrder = ListSortOrder.ALPHABETICAL.name,
                currentSectionName = "Currently Watching"
            ),
            entryCount = 5,
            subEntryCount = 3,
            checkedCount = 0,
            watchingCount = 2
        )

        val domain = entity.toDomain()
        assertEquals("list1", domain.list.id)
        assertEquals("Movies", domain.list.title)
        assertEquals(ListType.RATING, domain.list.type)
        assertEquals(5, domain.entryCount)
        assertEquals(3, domain.subEntryCount)
        assertEquals(0, domain.checkedCount)
        assertEquals(2, domain.watchingCount)
    }

    @Test
    fun testSyncListPackageChecklistSerializationRoundtrip() {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        val checklist = NoteList(
            id = "cl_1",
            title = "Groceries",
            type = ListType.CHECKLIST,
            isPinned = true,
            sortOrder = ListSortOrder.ALPHABETICAL
        )

        val tag = Tag(
            id = "tag_1",
            listId = "cl_1",
            name = "Urgent",
            colorArgb = 0xFFFF0000.toInt(),
            position = 0
        )

        val entry1 = ListEntry(
            id = "e1",
            listId = "cl_1",
            title = "Apples",
            isChecked = true,
            isPinned = false,
            tagIds = listOf("tag_1"),
            description = "Honeycrisp preferred"
        )
        val entry2 = ListEntry(
            id = "e2",
            listId = "cl_1",
            title = "Milk",
            isChecked = false,
            isPinned = true,
            tagIds = emptyList()
        )

        val pkg = SyncListPackage(
            list = checklist,
            entries = listOf(entry1, entry2),
            tags = listOf(tag)
        )

        val serialized = json.encodeToString(pkg)
        assertTrue(serialized.contains("Groceries"))
        assertTrue(serialized.contains("CHECKLIST"))
        assertTrue(serialized.contains("Apples"))
        assertTrue(serialized.contains("Milk"))
        assertTrue(serialized.contains("Urgent"))

        val deserialized = json.decodeFromString<SyncListPackage>(serialized)
        assertEquals("cl_1", deserialized.list.id)
        assertEquals("Groceries", deserialized.list.title)
        assertEquals(ListType.CHECKLIST, deserialized.list.type)
        assertEquals(2, deserialized.entries.size)
        assertEquals("Apples", deserialized.entries[0].title)
        assertTrue(deserialized.entries[0].isChecked)
        assertEquals("Honeycrisp preferred", deserialized.entries[0].description)
        assertEquals("Milk", deserialized.entries[1].title)
        assertFalse(deserialized.entries[1].isChecked)
        assertEquals(1, deserialized.tags.size)
        assertEquals("Urgent", deserialized.tags[0].name)
    }

    @Test
    fun testSyncListPackageEmptyEntriesRoundtrip() {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        val emptyChecklist = NoteList(
            id = "cl_empty",
            title = "Empty Checklist",
            type = ListType.CHECKLIST
        )

        val pkg = SyncListPackage(
            list = emptyChecklist,
            entries = emptyList(),
            tags = emptyList()
        )

        val serialized = json.encodeToString(pkg)
        val deserialized = json.decodeFromString<SyncListPackage>(serialized)
        assertEquals("cl_empty", deserialized.list.id)
        assertEquals(ListType.CHECKLIST, deserialized.list.type)
        assertTrue(deserialized.entries.isEmpty())
        assertTrue(deserialized.tags.isEmpty())
    }
}
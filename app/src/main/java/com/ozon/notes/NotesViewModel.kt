package com.ozon.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ozon.notes.domain.GetFilteredListsUseCase
import com.ozon.notes.domain.GetFilteredNotesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File

/**
 * ViewModel responsible for the main dashboard state.
 * Handles the collection and processing of all Notes and Lists, including search and global sorting.
 * 
 * Note: Expensive sorting/filtering operations are offloaded to [Dispatchers.Default] 
 * via UseCases to keep the UI responsive.
 */
class NotesViewModel(private val repository: NoteRepository) : ViewModel() {

    private val getFilteredNotesUseCase = GetFilteredNotesUseCase()
    private val getFilteredListsUseCase = GetFilteredListsUseCase()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val noteSortOrder: StateFlow<ListSortOrder> = repository.getNoteSortOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListSortOrder.NEWEST)

    val notesState: StateFlow<List<Note>> = combine(
        repository.getAllNotes(),
        _searchQuery,
        noteSortOrder
    ) { notes, query, sortOrder ->
        getFilteredNotesUseCase(notes, query, sortOrder)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listsSortOrder: StateFlow<ListSortOrder> = repository.getListsSortOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListSortOrder.NEWEST)

    val listsState: StateFlow<List<NoteList>> = combine(
        repository.getAllLists(),
        _searchQuery,
        listsSortOrder
    ) { lists, query, sortOrder ->
        getFilteredListsUseCase(lists, query, sortOrder)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listsWithCountsState: StateFlow<List<NoteListWithCounts>> = combine(
        searchQuery,
        listsSortOrder,
        repository.getAllListsWithCounts()
    ) { query, sortOrder, allListsWithCounts ->
        val filtered = if (query.isBlank()) {
            allListsWithCounts
        } else {
            allListsWithCounts.filter { 
                it.list.title.contains(query, ignoreCase = true) 
            }
        }

        filtered.sortedWith { a, b ->
            val la = a.list; val lb = b.list
            if (la.isPinned != lb.isPinned) {
                return@sortedWith lb.isPinned.compareTo(la.isPinned)
            }
            when (sortOrder) {
                ListSortOrder.ALPHABETICAL -> la.title.compareTo(lb.title, ignoreCase = true)
                ListSortOrder.REVERSE_ALPHABETICAL -> lb.title.compareTo(la.title, ignoreCase = true)
                ListSortOrder.NEWEST -> lb.timestamp.compareTo(la.timestamp)
                ListSortOrder.OLDEST -> la.timestamp.compareTo(lb.timestamp)
                else -> lb.timestamp.compareTo(la.timestamp)
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val splitFractionState: StateFlow<Float> = repository.getSplitFraction()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.35f)

    val forceStylusOnly: StateFlow<Boolean> = repository.getForceStylusOnly()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastDrawingColor: StateFlow<Int> = repository.getLastDrawingColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), android.graphics.Color.BLACK)

    val toolbarAnchor: StateFlow<ToolbarAnchor> = repository.getToolbarAnchor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ToolbarAnchor.BOTTOM)

    private val _isSidePanelVisible = MutableStateFlow(true)
    val isSidePanelVisible = _isSidePanelVisible.asStateFlow()

    private val _deletingIds = MutableStateFlow<Set<String>>(emptySet())
    val deletingIds = _deletingIds.asStateFlow()

    private var _pendingDrawingConfig: DrawingData? = null
    val pendingDrawingConfig: DrawingData? get() = _pendingDrawingConfig

    fun setPendingDrawingConfig(config: DrawingData?) {
        _pendingDrawingConfig = config
    }

    private val _importProgress = MutableStateFlow<String?>(null)
    val importProgress = _importProgress.asStateFlow()

    fun createNewNote(): String {
        val id = UUID.randomUUID().toString()
        val newNote = Note(id = id, title = "New Note", content = "", type = NoteType.TEXT)
        viewModelScope.launch { repository.saveNote(newNote) }
        return id
    }

    fun createNewDrawing(
        config: DrawingData? = null, 
        pdfUri: android.net.Uri? = null, 
        context: android.content.Context? = null,
        onImportComplete: (String) -> Unit = {}
    ) {
        val id = UUID.randomUUID().toString()
        val initialConfig = config ?: DrawingData(canvasType = CanvasType.INFINITE)
        
        val newNote = Note(
            id = id, 
            title = "New Drawing", 
            content = "Drawing Note", 
            type = NoteType.DRAWING,
            drawingData = initialConfig
        )
        
        viewModelScope.launch { 
            _importProgress.value = "Creating note..."
            repository.saveNote(newNote)
            
            if (initialConfig.canvasType == CanvasType.PDF && pdfUri != null && context != null) {
                try {
                    _importProgress.value = "Copying PDF..."
                    val localPath = withContext(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(pdfUri) 
                            ?: throw Exception("Could not open PDF file")
                        val fileName = "note_pdf_${UUID.randomUUID()}.pdf"
                        val file = File(context.filesDir, fileName)
                        file.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                        file.absolutePath
                    }
                    
                    _importProgress.value = "Analyzing pages..."
                    val pdfInfo = withContext(Dispatchers.IO) {
                        val pfd = android.os.ParcelFileDescriptor.open(File(localPath), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                        val count = renderer.pageCount
                        val sizes = if (count > 0) {
                            val firstPage = renderer.openPage(0)
                            val firstSize = PdfPageSize(firstPage.width.toFloat(), firstPage.height.toFloat())
                            firstPage.close()
                            // Optimization: Assume all pages have the same size as the first page for fast importing
                            List(count) { firstSize }
                        } else {
                            emptyList()
                        }
                        renderer.close()
                        pfd.close()
                        
                        PdfInfo(
                            localPath = localPath,
                            originalName = "Imported PDF",
                            pageCount = count,
                            pageSizes = sizes
                        )
                    }
                    
                    val updatedNote = newNote.copy(
                        drawingData = initialConfig.copy(pdfInfo = pdfInfo, backgroundPdfPath = null, pageCount = pdfInfo.pageCount)
                    )
                    repository.saveNote(updatedNote)
                    _importProgress.value = null
                    onImportComplete(id)
                } catch (e: Exception) {
                    e.printStackTrace()
                    _importProgress.value = null
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "PDF Import Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                _importProgress.value = null
                onImportComplete(id)
            }
        }
    }
    fun onEvent(event: NoteEvent) {
        when (event) {
            is NoteEvent.UpdateSearchQuery -> _searchQuery.value = event.query
            is NoteEvent.SaveNote -> viewModelScope.launch { 
                repository.saveNote(event.note) 
            }
            is NoteEvent.TogglePinNote -> viewModelScope.launch { repository.togglePinNote(event.noteId) }
            is NoteEvent.DeleteNote -> {
                _deletingIds.update { it + event.noteId }
                viewModelScope.launch { 
                    repository.deleteNote(event.noteId)
                    _deletingIds.update { it - event.noteId }
                }
            }
            is NoteEvent.SaveList -> viewModelScope.launch { repository.saveList(event.list) }
            is NoteEvent.TogglePinList -> viewModelScope.launch { repository.togglePinList(event.listId) }
            is NoteEvent.DeleteList -> viewModelScope.launch { repository.deleteList(event.listId) }
            is NoteEvent.UpdateNoteSortOrder -> viewModelScope.launch { 
                repository.setNoteSortOrder(event.sortOrder)
                repository.setListsSortOrder(event.sortOrder)
            }
            is NoteEvent.UpdateListsSortOrder -> viewModelScope.launch { 
                repository.setListsSortOrder(event.sortOrder)
            }
            is NoteEvent.UpdateSplitFraction -> viewModelScope.launch { repository.setSplitFraction(event.fraction) }
            is NoteEvent.UpdateForceStylusOnly -> viewModelScope.launch { repository.setForceStylusOnly(event.enabled) }
            is NoteEvent.UpdateLastDrawingColor -> viewModelScope.launch { repository.setLastDrawingColor(event.color) }
            is NoteEvent.UpdateToolbarAnchor -> viewModelScope.launch { repository.setToolbarAnchor(event.anchor) }
            is NoteEvent.UpdateSmoothingStrength -> viewModelScope.launch { repository.setSmoothingStrength(event.strength) }
            is NoteEvent.ToggleSidePanel -> {
                _isSidePanelVisible.value = !_isSidePanelVisible.value
            }
            is NoteEvent.SetSidePanelVisible -> {
                _isSidePanelVisible.value = event.visible
            }
            else -> { /* Handled elsewhere */ }
        }
    }

    suspend fun getNoteById(id: String): Note? = repository.getNoteById(id)
    fun getListById(id: String): NoteList? = listsState.value.find { it.id == id }

    companion object {
        fun provideFactory(repository: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(repository) as T
            }
    }
}

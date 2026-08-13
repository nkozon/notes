package com.ozon.notes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch

class NoteViewModel(private val repository: NoteRepository) : ViewModel() {

    fun getRepository() = repository

    // 1. Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // 2. Sorting State
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val noteSortOrder: StateFlow<ListSortOrder> = repository.getNoteSortOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListSortOrder.NEWEST)

    // 3. Combined Notes State (Filters by Search AND Sort)
    val notesState: StateFlow<List<Note>> = combine(
        repository.getAllNotes(),
        _searchQuery,
        noteSortOrder
    ) { notes, query, sortOrder ->
        notes.filter { note ->
            val matchesSearch = if (query.isBlank()) true else {
                note.title.contains(query, ignoreCase = true) ||
                        note.content.contains(query, ignoreCase = true)
            }
            matchesSearch
        }.sortedWith { a, b ->
            if (a.isPinned != b.isPinned) {
                return@sortedWith b.isPinned.compareTo(a.isPinned)
            }
            when (sortOrder) {
                ListSortOrder.ALPHABETICAL -> {
                    val res = a.title.compareTo(b.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.REVERSE_ALPHABETICAL -> {
                    val res = b.title.compareTo(a.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.NEWEST -> {
                    val res = b.timestamp.compareTo(a.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.OLDEST -> {
                    val res = a.timestamp.compareTo(b.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                else -> a.title.compareTo(b.title, ignoreCase = true)
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- LISTS ---
    val listsSortOrder: StateFlow<ListSortOrder> = repository.getListsSortOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListSortOrder.NEWEST)

    val listsState: StateFlow<List<NoteList>> = combine(
        repository.getAllLists(),
        listsSortOrder,
        _searchQuery
    ) { lists, sortOrder, query ->
        lists.filter { 
            if (query.isBlank()) true else it.title.contains(query, ignoreCase = true)
        }.sortedWith { a, b ->
            if (a.isPinned != b.isPinned) {
                return@sortedWith b.isPinned.compareTo(a.isPinned)
            }
            when (sortOrder) {
                ListSortOrder.ALPHABETICAL -> {
                    val res = a.title.compareTo(b.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.REVERSE_ALPHABETICAL -> {
                    val res = b.title.compareTo(a.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.NEWEST -> {
                    val res = b.timestamp.compareTo(a.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.OLDEST -> {
                    val res = a.timestamp.compareTo(b.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                else -> a.title.compareTo(b.title, ignoreCase = true)
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val listsWithCountsState: StateFlow<List<NoteListWithCounts>> = combine(
        listsState,
        repository.getAllEntries()
    ) { lists, allEntries ->
        val entriesByList = allEntries.groupBy { it.listId }
        lists.map { list ->
            val listEntries = entriesByList[list.id] ?: emptyList()
            val entries = listEntries.count { it.parentId.isNullOrBlank() }
            val subEntries = listEntries.count { !it.parentId.isNullOrBlank() }
            val checkedCount = listEntries.count { it.isChecked }
            NoteListWithCounts(list, entries, subEntries, checkedCount)
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentListId = MutableStateFlow<String?>(null)
    private val _listSortOrder = MutableStateFlow(ListSortOrder.ALPHABETICAL)
    val listSortOrder = _listSortOrder.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entriesState: StateFlow<List<ListEntry>> = combine(
        _currentListId,
        _listSortOrder,
        _searchQuery,
        repository.getChecklistBehavior(),
        repository.getAllLists()
    ) { listId, sortOrder, query, behavior, allLists ->
        val currentList = allLists.find { it.id == listId }
        val isChecklist = currentList?.type == ListType.CHECKLIST
        
        ChecklistFilterParams(listId, sortOrder, query, behavior, isChecklist)
    }.flatMapLatest { params ->
        val listId = params.listId
        if (listId == null) return@flatMapLatest flowOf(emptyList<ListEntry>())

        repository.getEntriesForList(listId).map { entries ->
            val filteredEntries = if (params.query.isBlank()) {
                entries
            } else {
                val matchingIds = entries.filter {
                    it.title.contains(params.query, ignoreCase = true)
                }.map { it.id }.toSet()

                val resultIds = mutableSetOf<String>()
                
                val rootEntries = entries.filter { it.parentId.isNullOrBlank() }
                
                rootEntries.forEach { root ->
                    fun hasMatchingDescendant(parentId: String): Boolean {
                        val children = entries.filter { it.parentId == parentId }
                        return children.any { it.id in matchingIds || hasMatchingDescendant(it.id) }
                    }

                    if (root.id in matchingIds || hasMatchingDescendant(root.id)) {
                        resultIds.add(root.id)
                        fun addAllDescendants(parentId: String) {
                            entries.filter { it.parentId == parentId }.forEach { child ->
                                resultIds.add(child.id)
                                addAllDescendants(child.id)
                            }
                        }
                        addAllDescendants(root.id)
                    }
                }
                entries.filter { it.id in resultIds }
            }

            filteredEntries.filter {
                if (params.isChecklist && params.behavior == ChecklistBehavior.HIDE) !it.isChecked else true
            }.sortedWith { a, b ->
                if (params.isChecklist && params.behavior == ChecklistBehavior.MOVE_TO_BOTTOM) {
                }
                
                when (params.sortOrder) {
                    ListSortOrder.ALPHABETICAL -> {
                        val res = a.title.compareTo(b.title, ignoreCase = true)
                        if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                    }
                    ListSortOrder.REVERSE_ALPHABETICAL -> {
                        val res = b.title.compareTo(a.title, ignoreCase = true)
                        if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                    }
                    ListSortOrder.RATING_LOW_TO_HIGH -> {
                        val res = a.rating.compareTo(b.rating)
                        if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                    }
                    ListSortOrder.RATING_HIGH_TO_LOW -> {
                        val res = b.rating.compareTo(a.rating)
                        if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                    }
                    ListSortOrder.NEWEST -> {
                        val res = b.timestamp.compareTo(a.timestamp)
                        if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                    }
                    ListSortOrder.OLDEST -> {
                        val res = a.timestamp.compareTo(b.timestamp)
                        if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                    }
                }
            }
        }.flowOn(Dispatchers.Default)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private data class ChecklistFilterParams(
        val listId: String?,
        val sortOrder: ListSortOrder,
        val query: String,
        val behavior: ChecklistBehavior,
        val isChecklist: Boolean
    )

    // --- SETTINGS ---
    val startupViewState: StateFlow<AppView> = repository.getStartupView()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppView.MAIN
        )

    val themeState: StateFlow<AppTheme> = repository.getTheme()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.SYSTEM
        )

    val tabletModeState: StateFlow<TabletMode> = repository.getTabletMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TabletMode.AUTOMATIC
        )

    val checklistBehaviorState: StateFlow<ChecklistBehavior> = repository.getChecklistBehavior()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChecklistBehavior.GREY_OUT
        )

    val showEntryCountState: StateFlow<Boolean> = repository.getShowEntryCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val lastBackupTimeState: StateFlow<Long> = repository.getLastBackupTime()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    val autoBackupEnabled: StateFlow<Boolean> = repository.getAutoBackupEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val backupUri: StateFlow<String?> = repository.getBackupUri()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasPendingChanges: StateFlow<Boolean> = repository.getHasPendingChanges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val ratingIndicatorsEnabled: StateFlow<Boolean> = repository.getRatingIndicatorsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val highScoreEnabled: StateFlow<Boolean> = repository.getHighScoreEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val highScoreThreshold: StateFlow<Float> = repository.getHighScoreThreshold()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 9.0f)

    val lowScoreEnabled: StateFlow<Boolean> = repository.getLowScoreEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lowScoreThreshold: StateFlow<Float> = repository.getLowScoreThreshold()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4.0f)

    val splitFractionState: StateFlow<Float> = repository.getSplitFraction()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.35f)

    val forceStylusOnly: StateFlow<Boolean> = repository.getForceStylusOnly()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastDrawingColor: StateFlow<Int> = repository.getLastDrawingColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), android.graphics.Color.BLACK)

    private val _isSidePanelVisible = MutableStateFlow(true)
    val isSidePanelVisible = _isSidePanelVisible.asStateFlow()

    fun onEvent(event: NoteEvent) {
        when (event) {
            is NoteEvent.SaveNote -> viewModelScope.launch { repository.saveNote(event.note) }
            is NoteEvent.TogglePinNote -> viewModelScope.launch { repository.togglePinNote(event.noteId) }
            is NoteEvent.DeleteNote -> viewModelScope.launch { repository.deleteNote(event.noteId) }
            is NoteEvent.UpdateSearchQuery -> _searchQuery.value = event.query
            is NoteEvent.SaveList -> viewModelScope.launch { repository.saveList(event.list) }
            is NoteEvent.TogglePinList -> viewModelScope.launch { repository.togglePinList(event.listId) }
            is NoteEvent.DeleteList -> viewModelScope.launch { repository.deleteList(event.listId) }
            is NoteEvent.SetCurrentList -> _currentListId.value = event.listId
            is NoteEvent.SaveEntry -> viewModelScope.launch { repository.saveEntry(event.entry) }
            is NoteEvent.DeleteEntry -> viewModelScope.launch { repository.deleteEntry(event.entryId) }
            is NoteEvent.UpdateListSortOrder -> _listSortOrder.value = event.sortOrder
            is NoteEvent.UpdateStartupView -> viewModelScope.launch { repository.setStartupView(event.view) }
            is NoteEvent.UpdateTheme -> viewModelScope.launch { repository.setTheme(event.theme) }
            is NoteEvent.UpdateTabletMode -> viewModelScope.launch { repository.setTabletMode(event.mode) }
            is NoteEvent.UpdateNoteSortOrder -> viewModelScope.launch { 
                repository.setNoteSortOrder(event.sortOrder)
                repository.setListsSortOrder(event.sortOrder)
            }
            is NoteEvent.UpdateListsSortOrder -> viewModelScope.launch { 
                repository.setListsSortOrder(event.sortOrder)
            }
            is NoteEvent.ClearAllData -> viewModelScope.launch { repository.clearAllData() }
            is NoteEvent.BackupData -> viewModelScope.launch { 
                val data = repository.getBackupData()
                event.onDataReady(data) 
            }
            is NoteEvent.RestoreData -> viewModelScope.launch { repository.restoreBackupData(event.data) }
            is NoteEvent.UpdateChecklistBehavior -> viewModelScope.launch { repository.setChecklistBehavior(event.behavior) }
            is NoteEvent.UpdateShowEntryCount -> viewModelScope.launch { repository.setShowEntryCount(event.show) }
            is NoteEvent.UpdateLastBackupTime -> viewModelScope.launch { repository.setLastBackupTime(event.time) }
            is NoteEvent.UpdateAutoBackupEnabled -> viewModelScope.launch { repository.setAutoBackupEnabled(event.enabled) }
            is NoteEvent.UpdateBackupUri -> viewModelScope.launch { repository.setBackupUri(event.uri) }
            is NoteEvent.UpdateHasPendingChanges -> viewModelScope.launch { repository.setHasPendingChanges(event.hasChanges) }
            is NoteEvent.UpdateRatingIndicatorsEnabled -> viewModelScope.launch { repository.setRatingIndicatorsEnabled(event.enabled) }
            is NoteEvent.UpdateHighScoreEnabled -> viewModelScope.launch { repository.setHighScoreEnabled(event.enabled) }
            is NoteEvent.UpdateHighScoreThreshold -> viewModelScope.launch { repository.setHighScoreThreshold(event.threshold) }
            is NoteEvent.UpdateLowScoreEnabled -> viewModelScope.launch { repository.setLowScoreEnabled(event.enabled) }
            is NoteEvent.UpdateLowScoreThreshold -> viewModelScope.launch { repository.setLowScoreThreshold(event.threshold) }
            is NoteEvent.UpdateSplitFraction -> viewModelScope.launch { repository.setSplitFraction(event.fraction) }
            is NoteEvent.UpdateForceStylusOnly -> viewModelScope.launch { repository.setForceStylusOnly(event.enabled) }
            is NoteEvent.UpdateLastDrawingColor -> viewModelScope.launch { repository.setLastDrawingColor(event.color) }
            is NoteEvent.ToggleSidePanel -> {
                _isSidePanelVisible.value = !_isSidePanelVisible.value
            }
            is NoteEvent.SetSidePanelVisible -> {
                _isSidePanelVisible.value = event.visible
            }
            is NoteEvent.TriggerAutoBackup -> triggerAutoBackup()
        }
    }

    private fun triggerAutoBackup() {
        val enabled = autoBackupEnabled.value
        val uriString = backupUri.value
        val hasChanges = hasPendingChanges.value

        if (!enabled || uriString == null || !hasChanges) return

        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    val treeUri = android.net.Uri.parse(uriString)
                    val context = repository.getContext()
                    val pickedDir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
                    
                    val fileName = "auto_backup_${System.currentTimeMillis()}.json"
                    val file = pickedDir.createFile("application/json", fileName) ?: return@withContext
                    
                    val data = repository.getBackupData()
                    val jsonString = Json.encodeToString(data)
                    
                    context.contentResolver.openOutputStream(file.uri)?.use { 
                        it.write(jsonString.toByteArray())
                    }
                    
                    repository.setLastBackupTime(System.currentTimeMillis())
                    repository.setHasPendingChanges(false)
                    Log.d("NoteViewModel", "Auto backup successful: $fileName")
                } catch (e: Exception) {
                    Log.e("NoteViewModel", "Auto backup failed", e)
                }
            }
        }
    }

    fun getNoteById(id: String): Note? = notesState.value.find { it.id == id }
    fun getListById(id: String): NoteList? = listsState.value.find { it.id == id }

    companion object {
        fun provideFactory(repository: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteViewModel(repository) as T
            }
    }
}

sealed interface NoteEvent {
    data class SaveNote(val note: Note) : NoteEvent
    data class TogglePinNote(val noteId: String) : NoteEvent
    data class DeleteNote(val noteId: String) : NoteEvent
    data class UpdateSearchQuery(val query: String) : NoteEvent
    data class SaveList(val list: NoteList) : NoteEvent
    data class TogglePinList(val listId: String) : NoteEvent
    data class DeleteList(val listId: String) : NoteEvent
    data class SetCurrentList(val listId: String?) : NoteEvent
    data class SaveEntry(val entry: ListEntry) : NoteEvent
    data class DeleteEntry(val entryId: String) : NoteEvent
    data class UpdateListSortOrder(val sortOrder: ListSortOrder) : NoteEvent
    data class UpdateStartupView(val view: AppView) : NoteEvent
    data class UpdateTheme(val theme: AppTheme) : NoteEvent
    data class UpdateTabletMode(val mode: TabletMode) : NoteEvent
    data class UpdateNoteSortOrder(val sortOrder: ListSortOrder) : NoteEvent
    data class UpdateListsSortOrder(val sortOrder: ListSortOrder) : NoteEvent
    data object ClearAllData : NoteEvent
    data class BackupData(val onDataReady: (com.ozon.notes.BackupData) -> Unit) : NoteEvent
    data class RestoreData(val data: com.ozon.notes.BackupData) : NoteEvent
    data class UpdateChecklistBehavior(val behavior: ChecklistBehavior) : NoteEvent
    data class UpdateShowEntryCount(val show: Boolean) : NoteEvent
    data class UpdateLastBackupTime(val time: Long) : NoteEvent
    data class UpdateAutoBackupEnabled(val enabled: Boolean) : NoteEvent
    data class UpdateBackupUri(val uri: String?) : NoteEvent
    data class UpdateHasPendingChanges(val hasChanges: Boolean) : NoteEvent
    data class UpdateRatingIndicatorsEnabled(val enabled: Boolean) : NoteEvent
    data class UpdateHighScoreEnabled(val enabled: Boolean) : NoteEvent
    data class UpdateHighScoreThreshold(val threshold: Float) : NoteEvent
    data class UpdateLowScoreEnabled(val enabled: Boolean) : NoteEvent
    data class UpdateLowScoreThreshold(val threshold: Float) : NoteEvent
    data class UpdateSplitFraction(val fraction: Float) : NoteEvent
    data object ToggleSidePanel : NoteEvent
    data class SetSidePanelVisible(val visible: Boolean) : NoteEvent
    data class UpdateForceStylusOnly(val enabled: Boolean) : NoteEvent
    data class UpdateLastDrawingColor(val color: Int) : NoteEvent
    data object TriggerAutoBackup : NoteEvent
}

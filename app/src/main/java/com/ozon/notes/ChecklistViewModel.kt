package com.ozon.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ozon.notes.domain.GetFilteredEntriesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the state of a single Checklist or Rating list.
 * 
 * Features:
 * - Reactive tracking of the 'current' list being viewed.
 * - Hierarchical entry management (CRUD for entries and sub-entries).
 * - Real-time filtering and sorting of entries.
 */
class ChecklistViewModel(private val repository: NoteRepository) : ViewModel() {

    private val getFilteredEntriesUseCase = GetFilteredEntriesUseCase()

    private val _currentListId = MutableStateFlow<String?>(null)
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentList: StateFlow<NoteList?> = _currentListId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repository.getAllLists().map { lists -> lists.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _listSortOrder = MutableStateFlow(ListSortOrder.ALPHABETICAL)
    val listSortOrder = _listSortOrder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

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
            getFilteredEntriesUseCase(
                entries,
                params.query,
                params.sortOrder,
                params.behavior,
                params.isChecklist
            )
        }.flowOn(Dispatchers.Default)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onEvent(event: NoteEvent) {
        when (event) {
            is NoteEvent.SetCurrentList -> _currentListId.value = event.listId
            is NoteEvent.UpdateListSortOrder -> _listSortOrder.value = event.sortOrder
            is NoteEvent.UpdateSearchQuery -> _searchQuery.value = event.query
            is NoteEvent.SaveEntry -> viewModelScope.launch { repository.saveEntry(event.entry) }
            is NoteEvent.DeleteEntry -> viewModelScope.launch { repository.deleteEntry(event.entryId) }
            else -> { /* Handled elsewhere */ }
        }
    }

    private data class ChecklistFilterParams(
        val listId: String?,
        val sortOrder: ListSortOrder,
        val query: String,
        val behavior: ChecklistBehavior,
        val isChecklist: Boolean
    )

    companion object {
        fun provideFactory(repository: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ChecklistViewModel(repository) as T
            }
    }
}

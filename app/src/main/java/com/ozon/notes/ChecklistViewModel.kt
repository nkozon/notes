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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val listSortOrder: StateFlow<ListSortOrder> = _currentListId.flatMapLatest { id ->
        if (id == null) flowOf(ListSortOrder.ALPHABETICAL)
        else repository.getListSortOrder(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListSortOrder.ALPHABETICAL)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilterTagIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFilterTagIds = _selectedFilterTagIds.asStateFlow()

    private val _tagFilterMode = MutableStateFlow(TagFilterMode.OR)
    val tagFilterMode = _tagFilterMode.asStateFlow()

    private val _pendingEntries = MutableStateFlow<List<ListEntry>>(emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allTags: StateFlow<List<Tag>> = _currentListId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getTagsForList(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entriesState: StateFlow<List<ListEntry>> = combine(
        _currentListId,
        listSortOrder,
        _searchQuery,
        _selectedFilterTagIds,
        _tagFilterMode,
        repository.getChecklistBehavior(),
        repository.getAllLists(),
        allTags
    ) { args ->
        val listId = args[0] as String?
        val sortOrder = args[1] as ListSortOrder
        val query = args[2] as String
        val tagIds = args[3] as Set<String>
        val filterMode = args[4] as TagFilterMode
        val behavior = args[5] as ChecklistBehavior
        val allLists = args[6] as List<NoteList>
        val tags = args[7] as List<Tag>

        val currentList = allLists.find { it.id == listId }
        val isChecklist = currentList?.type == ListType.CHECKLIST
        
        ChecklistFilterParams(listId, sortOrder, query, tagIds, filterMode, behavior, isChecklist, tags)
    }.flatMapLatest { params ->
        val listId = params.listId
        if (listId == null) return@flatMapLatest flowOf(emptyList<ListEntry>())

        combine(repository.getEntriesForList(listId), _pendingEntries) { entries, pending ->
            val pendingIds = pending.map { it.id }.toSet()
            val filteredDb = entries.filter { it.id !in pendingIds }
            val relevantPending = pending.filter { it.listId == listId }
            filteredDb + relevantPending
        }.map { allEntries ->
            getFilteredEntriesUseCase(
                allEntries,
                params.query,
                params.tagIds,
                params.filterMode,
                params.sortOrder,
                params.behavior,
                params.isChecklist,
                params.tags
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
            is NoteEvent.UpdateListSortOrder -> {
                _currentListId.value?.let { listId ->
                    viewModelScope.launch {
                        repository.setListSortOrder(listId, event.sortOrder)
                    }
                }
            }
            is NoteEvent.UpdateSearchQuery -> _searchQuery.value = event.query
            is NoteEvent.ToggleFilterTag -> {
                _selectedFilterTagIds.update { current ->
                    if (current.contains(event.tagId)) current - event.tagId else current + event.tagId
                }
            }
            is NoteEvent.ClearFilterTags -> _selectedFilterTagIds.value = emptySet()
            is NoteEvent.UpdateTagFilterMode -> _tagFilterMode.value = event.mode
            is NoteEvent.SaveList -> viewModelScope.launch { repository.saveList(event.list) }
            is NoteEvent.SaveTag -> viewModelScope.launch { repository.saveTag(event.tag) }
            is NoteEvent.DeleteTag -> viewModelScope.launch { repository.deleteTag(event.tagId) }
            is NoteEvent.SaveEntry -> {
                _pendingEntries.update { current -> 
                    current.filter { it.id != event.entry.id } + event.entry 
                }
                viewModelScope.launch { 
                    repository.saveEntry(event.entry)
                    // Keep in pending for a short while to ensure Room has emitted the change
                    _pendingEntries.update { current -> current.filter { it.id != event.entry.id } }
                }
            }
            is NoteEvent.DeleteEntry -> viewModelScope.launch { repository.deleteEntry(event.entryId) }
            else -> { /* Handled elsewhere */ }
        }
    }

    private data class ChecklistFilterParams(
        val listId: String?,
        val sortOrder: ListSortOrder,
        val query: String,
        val tagIds: Set<String>,
        val filterMode: TagFilterMode,
        val behavior: ChecklistBehavior,
        val isChecklist: Boolean,
        val tags: List<Tag>
    )

    suspend fun getEntryDescription(entryId: String): String? = repository.getEntryDescription(entryId)

    companion object {
        fun provideFactory(repository: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ChecklistViewModel(repository) as T
            }
    }
}

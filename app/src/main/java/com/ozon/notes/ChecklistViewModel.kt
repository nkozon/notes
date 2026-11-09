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
class ChecklistViewModel(private val repository: ChecklistRepository) : ViewModel() {

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

    private data class EntryFilterParams(
        val sortOrder: ListSortOrder = ListSortOrder.ALPHABETICAL,
        val query: String = "",
        val tagIds: Set<String> = emptySet(),
        val filterMode: TagFilterMode = TagFilterMode.OR,
        val behavior: ChecklistBehavior = ChecklistBehavior.HIDE,
        val isChecklist: Boolean = false,
        val tags: List<Tag> = emptyList()
    )

    private data class FilterSelection(
        val sortOrder: ListSortOrder,
        val query: String,
        val tagIds: Set<String>,
        val filterMode: TagFilterMode
    )

    /**
     * In-memory filter parameters. Changing search/sort/tags no longer re-subscribes to the Room
     * query for the current list; only [entriesState]'s mapper re-runs.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private val filterParams: Flow<EntryFilterParams> = combine(
        listSortOrder,
        _searchQuery.debounce { if (it.isBlank()) 0L else 120L }.distinctUntilChanged(),
        _selectedFilterTagIds,
        _tagFilterMode
    ) { sortOrder, query, tagIds, filterMode ->
        FilterSelection(sortOrder, query, tagIds, filterMode)
    }.combine(repository.getChecklistBehavior()) { selection, behavior ->
        EntryFilterParams(
            sortOrder = selection.sortOrder,
            query = selection.query,
            tagIds = selection.tagIds,
            filterMode = selection.filterMode,
            behavior = behavior
        )
    }.combine(currentList) { params, list ->
        params.copy(
            isChecklist = list?.type == ListType.CHECKLIST || list?.type == ListType.UPCOMING
        )
    }.combine(allTags) { params, tags ->
        params.copy(tags = tags)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entriesState: StateFlow<List<ListEntry>> = _currentListId.flatMapLatest { listId ->
        if (listId == null) {
            flowOf(emptyList())
        } else {
            combine(
                repository.getEntriesForList(listId),
                _pendingEntries,
                filterParams
            ) { entries, pending, params ->
                val pendingIds = pending.mapTo(HashSet()) { it.id }
                val filteredDb = entries.filter { it.id !in pendingIds }
                val relevantPending = pending.filter { it.listId == listId }
                getFilteredEntriesUseCase(
                    filteredDb + relevantPending,
                    params.query,
                    params.tagIds,
                    params.filterMode,
                    params.sortOrder,
                    params.behavior,
                    params.isChecklist,
                    params.tags
                )
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(
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
            is NoteEvent.SaveTag -> {
                viewModelScope.launch {
                    val tagToSave = if (event.tag.position == 0) {
                        val currentTags = allTags.value
                        val maxPosition = currentTags.maxByOrNull { it.position }?.position ?: 0
                        event.tag.copy(position = maxPosition + 1)
                    } else event.tag
                    repository.saveTag(tagToSave)
                }
            }
            is NoteEvent.DeleteTag -> viewModelScope.launch { repository.deleteTag(event.tagId) }
            is NoteEvent.ReorderTags -> {
                viewModelScope.launch {
                    val currentTags = allTags.value
                    val updatedTags = currentTags.map { tag ->
                        val newPosition = event.orderedTagIds.indexOf(tag.id)
                        if (newPosition != -1) tag.copy(position = newPosition) else tag
                    }
                    repository.saveTags(updatedTags)
                }
            }
            is NoteEvent.SaveEntry -> {
                _pendingEntries.update { current -> 
                    current.filter { it.id != event.entry.id } + event.entry 
                }
                viewModelScope.launch { 
                    repository.saveEntry(event.entry)
                    
                    // Handle notifications
                    if (event.entry.remindMe) {
                        NotificationHelper.scheduleNotification(repository.getContext(), event.entry)
                    } else {
                        NotificationHelper.cancelNotification(repository.getContext(), event.entry.id)
                    }

                    // Keep in pending for a short while to ensure Room has emitted the change
                    _pendingEntries.update { current -> current.filter { it.id != event.entry.id } }
                }
            }
            is NoteEvent.SaveEntries -> {
                val entries = event.entries
                if (entries.isEmpty()) return
                val entryIds = entries.mapTo(HashSet()) { it.id }
                _pendingEntries.update { current ->
                    current.filter { it.id !in entryIds } + entries
                }
                viewModelScope.launch {
                    repository.saveEntries(entries)

                    entries.forEach { entry ->
                        if (entry.remindMe) {
                            NotificationHelper.scheduleNotification(repository.getContext(), entry)
                        } else {
                            NotificationHelper.cancelNotification(repository.getContext(), entry.id)
                        }
                    }

                    _pendingEntries.update { current -> current.filter { it.id !in entryIds } }
                }
            }
            is NoteEvent.DeleteEntry -> {
                viewModelScope.launch { 
                    repository.deleteEntry(event.entryId)
                    NotificationHelper.cancelNotification(repository.getContext(), event.entryId)
                }
            }
            is NoteEvent.DeleteCompletedEntries -> viewModelScope.launch { repository.deleteCompletedEntries(event.listId) }
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

    suspend fun searchTmdb(query: String): List<TmdbMovie> = repository.searchTmdb(query)

    companion object {
        fun provideFactory(repository: ChecklistRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ChecklistViewModel(repository) as T
            }
    }
}

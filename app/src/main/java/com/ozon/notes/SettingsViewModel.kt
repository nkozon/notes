package com.ozon.notes

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ViewModel responsible for app-wide settings and data management.
 * 
 * Duties:
 * - Persisting user preferences (Theme, Tablet Mode, etc.).
 * - Managing the App Update lifecycle via [UpdateManager].
 * - Handling data backup, restore, and manual cleanup.
 */
class SettingsViewModel(private val repository: NoteRepository) : ViewModel() {

    private val updateManager = UpdateManager(repository.getContext())

    val themeState: StateFlow<AppTheme> = repository.getTheme()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    val useDynamicColorState: StateFlow<Boolean> = repository.getUseDynamicColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val customPrimaryColorState: StateFlow<Int?> = repository.getCustomPrimaryColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customSecondaryColorState: StateFlow<Int?> = repository.getCustomSecondaryColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customAccentColorState: StateFlow<Int?> = repository.getCustomAccentColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isOledModeState: StateFlow<Boolean> = repository.getIsOledMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val tabletModeState: StateFlow<TabletMode> = repository.getTabletMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TabletMode.AUTOMATIC)

    val checklistBehaviorState: StateFlow<ChecklistBehavior> = repository.getChecklistBehavior()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChecklistBehavior.GREY_OUT)

    val showEntryCountState: StateFlow<Boolean> = repository.getShowEntryCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastBackupTimeState: StateFlow<Long> = repository.getLastBackupTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

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

    val forceStylusOnly: StateFlow<Boolean> = repository.getForceStylusOnly()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val smoothingStrength: StateFlow<SmoothingStrength> = repository.getSmoothingStrength()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmoothingStrength.MODERATE)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    fun onEvent(event: NoteEvent) {
        when (event) {
            is NoteEvent.UpdateTheme -> viewModelScope.launch { repository.setTheme(event.theme) }
            is NoteEvent.UpdateUseDynamicColor -> viewModelScope.launch { repository.setUseDynamicColor(event.enabled) }
            is NoteEvent.UpdateCustomPrimaryColor -> viewModelScope.launch { repository.setCustomPrimaryColor(event.color) }
            is NoteEvent.UpdateCustomSecondaryColor -> viewModelScope.launch { repository.setCustomSecondaryColor(event.color) }
            is NoteEvent.UpdateCustomAccentColor -> viewModelScope.launch { repository.setCustomAccentColor(event.color) }
            is NoteEvent.UpdateIsOledMode -> viewModelScope.launch { repository.setIsOledMode(event.enabled) }
            is NoteEvent.UpdateTabletMode -> viewModelScope.launch { repository.setTabletMode(event.mode) }
            is NoteEvent.UpdateChecklistBehavior -> viewModelScope.launch { repository.setChecklistBehavior(event.behavior) }
            is NoteEvent.UpdateShowEntryCount -> viewModelScope.launch { repository.setShowEntryCount(event.show) }
            is NoteEvent.UpdateAutoBackupEnabled -> viewModelScope.launch { repository.setAutoBackupEnabled(event.enabled) }
            is NoteEvent.UpdateBackupUri -> viewModelScope.launch { repository.setBackupUri(event.uri) }
            is NoteEvent.UpdateRatingIndicatorsEnabled -> viewModelScope.launch { repository.setRatingIndicatorsEnabled(event.enabled) }
            is NoteEvent.UpdateHighScoreEnabled -> viewModelScope.launch { repository.setHighScoreEnabled(event.enabled) }
            is NoteEvent.UpdateHighScoreThreshold -> viewModelScope.launch { repository.setHighScoreThreshold(event.threshold) }
            is NoteEvent.UpdateLowScoreEnabled -> viewModelScope.launch { repository.setLowScoreEnabled(event.enabled) }
            is NoteEvent.UpdateLowScoreThreshold -> viewModelScope.launch { repository.setLowScoreThreshold(event.threshold) }
            is NoteEvent.UpdateForceStylusOnly -> viewModelScope.launch { repository.setForceStylusOnly(event.enabled) }
            is NoteEvent.UpdateSmoothingStrength -> viewModelScope.launch { repository.setSmoothingStrength(event.strength) }
            is NoteEvent.ClearAllData -> viewModelScope.launch { repository.clearAllData() }
            is NoteEvent.BackupData -> viewModelScope.launch { 
                val data = repository.getBackupData()
                event.onDataReady(data) 
            }
            is NoteEvent.RestoreData -> viewModelScope.launch { repository.restoreBackupData(event.data) }
            is NoteEvent.TriggerAutoBackup -> triggerAutoBackup()
            is NoteEvent.CheckForUpdate -> checkForUpdate()
            is NoteEvent.InstallUpdate -> {
                val id = updateManager.downloadAndInstallUpdate(event.url, event.version)
                if (id != null) {
                    startDownloadProgressPolling(id)
                }
            }
            else -> { /* Other events handled by other viewmodels */ }
        }
    }

    private fun startDownloadProgressPolling(id: Long) {
        viewModelScope.launch {
            while (true) {
                val progress = updateManager.getDownloadProgress(id)
                _updateState.value = UpdateState.Downloading(progress)
                if (progress >= 1f) break
                delay(500)
            }
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            _updateState.value = updateManager.checkForUpdate()
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
                    Log.d("SettingsViewModel", "Auto backup successful: $fileName")
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Auto backup failed", e)
                }
            }
        }
    }

    companion object {
        fun provideFactory(repository: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
            }
    }
}

package com.ozon.notes

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * ViewModel responsible for app-wide settings, data management, and cloud backup.
 * 
 * Duties:
 * - Persisting user preferences (Theme, Tablet Mode, etc.).
 * - Managing the App Update lifecycle via [UpdateManager].
 * - Handling modular compressed backups, restores, and Dropbox cloud sync.
 */
@OptIn(ExperimentalSerializationApi::class)
class SettingsViewModel(private val repository: NoteRepository) : ViewModel() {

    private val updateManager = UpdateManager(repository.getContext())
    val backupEngine = repository.getBackupEngine()
    val dropboxAuthManager = repository.getDropboxAuthManager()
    val dropboxClient = repository.getDropboxClient()

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

    val dropboxAutoBackupEnabled: StateFlow<Boolean> = repository.getDropboxAutoBackupEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastDropboxBackupTimeState: StateFlow<Long> = repository.getLastDropboxBackupTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

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

    val moviePostersEnabled: StateFlow<Boolean> = repository.getMoviePostersEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _posterCacheSize = MutableStateFlow(0L)
    val posterCacheSize = _posterCacheSize.asStateFlow()

    fun updatePosterCacheSize() {
        _posterCacheSize.value = repository.getPosterCacheSize()
    }

    val forceStylusOnly: StateFlow<Boolean> = repository.getForceStylusOnly()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastDrawingThickness: StateFlow<Float> = repository.getLastDrawingThickness()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5.0f)

    val smoothingStrength: StateFlow<SmoothingStrength> = repository.getSmoothingStrength()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmoothingStrength.MODERATE)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    // --- Dropbox State ---
    private val _dropboxAuthState = MutableStateFlow(
        DropboxAuthState(
            isConnected = dropboxAuthManager.isLoggedIn(),
            isConfigured = dropboxAuthManager.isConfigured()
        )
    )
    val dropboxAuthState = _dropboxAuthState.asStateFlow()

    private val _dropboxSyncStatus = MutableStateFlow<DropboxSyncStatus>(DropboxSyncStatus.Idle)
    val dropboxSyncStatus = _dropboxSyncStatus.asStateFlow()

    private val _estimatedBackupSize = MutableStateFlow(0L)
    val estimatedBackupSize = _estimatedBackupSize.asStateFlow()

    init {
        updateEstimatedBackupSize()
        refreshDropboxState()
    }

    fun updateEstimatedBackupSize() {
        viewModelScope.launch {
            _estimatedBackupSize.value = backupEngine.estimateBackupSize()
        }
    }

    fun refreshDropboxState() {
        val configured = dropboxAuthManager.isConfigured()
        if (!dropboxAuthManager.isLoggedIn()) {
            _dropboxAuthState.value = DropboxAuthState(
                isConnected = false,
                isConfigured = configured
            )
            return
        }

        viewModelScope.launch {
            val accountResult = dropboxClient.getAccountInfo()
            val spaceResult = dropboxClient.getSpaceUsage()
            val metadataResult = dropboxClient.getBackupMetadata()

            val account = accountResult.getOrNull()
            val space = spaceResult.getOrNull()
            val metadata = metadataResult.getOrNull()

            val latestModified = dropboxClient.parseServerModified(metadata?.server_modified)

            _dropboxAuthState.value = DropboxAuthState(
                isConnected = true,
                accountName = account?.name?.display_name,
                accountEmail = account?.email,
                usedSpace = space?.used ?: 0L,
                totalSpace = space?.allocation?.allocated ?: 0L,
                latestBackupSize = metadata?.size,
                latestBackupTime = latestModified,
                isConfigured = configured
            )
        }
    }

    fun startDropboxAuth(context: Context) {
        dropboxAuthManager.startOAuth(context)
    }

    fun handleDropboxAuthRedirect(uri: Uri, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = dropboxAuthManager.handleRedirectUri(uri)
            if (result.isSuccess) {
                refreshDropboxState()
                onResult(true, null)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Authentication failed"
                onResult(false, err)
            }
        }
    }

    fun disconnectDropbox() {
        dropboxAuthManager.logout()
        _dropboxAuthState.value = DropboxAuthState(
            isConnected = false,
            isConfigured = dropboxAuthManager.isConfigured()
        )
    }

    fun backupToDropbox(onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _dropboxSyncStatus.value = DropboxSyncStatus.Syncing("Creating backup archive...")
            val tempFile = File(repository.getContext().cacheDir, "dropbox_backup_temp.notesbackup")
            try {
                tempFile.outputStream().use { out ->
                    backupEngine.createBackup(out)
                }
                _dropboxSyncStatus.value = DropboxSyncStatus.Syncing("Uploading to Dropbox...")
                val uploadResult = dropboxClient.uploadBackup(tempFile)
                if (uploadResult.isSuccess) {
                    val currentTime = System.currentTimeMillis()
                    repository.setLastDropboxBackupTime(currentTime)
                    repository.setHasPendingChanges(false)
                    _dropboxSyncStatus.value = DropboxSyncStatus.Success("Backup uploaded successfully")
                    refreshDropboxState()
                    onComplete(true, null)
                } else {
                    val err = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                    _dropboxSyncStatus.value = DropboxSyncStatus.Error(err)
                    onComplete(false, err)
                }
            } catch (e: Exception) {
                val err = e.message ?: "Backup failed"
                _dropboxSyncStatus.value = DropboxSyncStatus.Error(err)
                onComplete(false, err)
            } finally {
                tempFile.delete()
            }
        }
    }

    fun restoreFromDropbox(onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _dropboxSyncStatus.value = DropboxSyncStatus.Syncing("Downloading backup from Dropbox...")
            val tempFile = File(repository.getContext().cacheDir, "dropbox_restore_temp.notesbackup")
            try {
                val downloadResult = dropboxClient.downloadBackup(tempFile)
                if (downloadResult.isSuccess) {
                    _dropboxSyncStatus.value = DropboxSyncStatus.Syncing("Restoring data...")
                    val success = tempFile.inputStream().use { input ->
                        backupEngine.restoreBackup(input, isMerge = false)
                    }
                    if (success) {
                        _dropboxSyncStatus.value = DropboxSyncStatus.Success("Restored successfully")
                        updateEstimatedBackupSize()
                        onComplete(true, null)
                    } else {
                        _dropboxSyncStatus.value = DropboxSyncStatus.Error("Failed to unpack backup")
                        onComplete(false, "Failed to unpack backup")
                    }
                } else {
                    val err = downloadResult.exceptionOrNull()?.message ?: "Download failed"
                    _dropboxSyncStatus.value = DropboxSyncStatus.Error(err)
                    onComplete(false, err)
                }
            } catch (e: Exception) {
                val err = e.message ?: "Restore failed"
                _dropboxSyncStatus.value = DropboxSyncStatus.Error(err)
                onComplete(false, err)
            } finally {
                tempFile.delete()
            }
        }
    }

    fun createLocalBackup(outputStream: OutputStream, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                backupEngine.createBackup(outputStream)
                repository.setLastBackupTime(System.currentTimeMillis())
                repository.setHasPendingChanges(false)
                updateEstimatedBackupSize()
                onComplete(true)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Local backup failed", e)
                onComplete(false)
            }
        }
    }

    fun restoreLocalBackup(inputStream: InputStream, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = backupEngine.restoreBackup(inputStream, isMerge = false)
                if (success) {
                    updateEstimatedBackupSize()
                }
                onComplete(success)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Local restore failed", e)
                onComplete(false)
            }
        }
    }

    fun exportGranularNote(noteId: String, outputStream: OutputStream, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupEngine.exportNote(noteId, outputStream)
            onComplete(success)
        }
    }

    fun exportGranularList(listId: String, outputStream: OutputStream, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupEngine.exportList(listId, outputStream)
            onComplete(success)
        }
    }

    fun importGranularBackup(inputStream: InputStream, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupEngine.restoreBackup(inputStream, isMerge = true)
            if (success) {
                updateEstimatedBackupSize()
            }
            onComplete(success)
        }
    }

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
            is NoteEvent.UpdateDropboxAutoBackupEnabled -> viewModelScope.launch { repository.setDropboxAutoBackupEnabled(event.enabled) }
            is NoteEvent.UpdateLastDropboxBackupTime -> viewModelScope.launch { repository.setLastDropboxBackupTime(event.time) }
            is NoteEvent.UpdateBackupUri -> viewModelScope.launch { repository.setBackupUri(event.uri) }
            is NoteEvent.UpdateRatingIndicatorsEnabled -> viewModelScope.launch { repository.setRatingIndicatorsEnabled(event.enabled) }
            is NoteEvent.UpdateHighScoreEnabled -> viewModelScope.launch { repository.setHighScoreEnabled(event.enabled) }
            is NoteEvent.UpdateHighScoreThreshold -> viewModelScope.launch { repository.setHighScoreThreshold(event.threshold) }
            is NoteEvent.UpdateLowScoreEnabled -> viewModelScope.launch { repository.setLowScoreEnabled(event.enabled) }
            is NoteEvent.UpdateLowScoreThreshold -> viewModelScope.launch { repository.setLowScoreThreshold(event.threshold) }
            is NoteEvent.UpdateMoviePostersEnabled -> viewModelScope.launch { repository.setMoviePostersEnabled(event.enabled) }
            is NoteEvent.ClearPosterCache -> viewModelScope.launch { 
                repository.clearPosterCache()
                updatePosterCacheSize()
            }
            is NoteEvent.UpdateForceStylusOnly -> viewModelScope.launch { repository.setForceStylusOnly(event.enabled) }
            is NoteEvent.UpdateLastDrawingThickness -> viewModelScope.launch { repository.setLastDrawingThickness(event.thickness) }
            is NoteEvent.UpdateSmoothingStrength -> viewModelScope.launch { repository.setSmoothingStrength(event.strength) }
            is NoteEvent.ClearAllData -> viewModelScope.launch { 
                repository.clearAllData()
                updateEstimatedBackupSize()
            }
            is NoteEvent.BackupData -> viewModelScope.launch { 
                val data = repository.getBackupData()
                event.onDataReady(data) 
            }
            is NoteEvent.RestoreData -> viewModelScope.launch { 
                repository.restoreBackupData(event.data)
                updateEstimatedBackupSize()
            }
            is NoteEvent.TriggerAutoBackup -> triggerAutoBackup()
            is NoteEvent.CheckForUpdate -> checkForUpdate()
            is NoteEvent.InstallUpdate -> {
                val id = updateManager.downloadAndInstallUpdate(event.url, event.version)
                if (id != null) {
                    startDownloadProgressPolling(id)
                }
            }
            is NoteEvent.ExportNote -> viewModelScope.launch {
                val data = repository.getNoteBackup(event.noteId)
                if (data != null) event.onDataReady(data)
            }
            is NoteEvent.ExportList -> viewModelScope.launch {
                val data = repository.getListBackup(event.listId)
                if (data != null) event.onDataReady(data)
            }
            is NoteEvent.ImportGranular -> viewModelScope.launch {
                repository.importBackupData(event.data)
                updateEstimatedBackupSize()
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
                    
                    val fileName = "auto_backup_${System.currentTimeMillis()}.notesbackup"
                    val file = pickedDir.createFile("application/zip", fileName) ?: return@withContext
                    
                    context.contentResolver.openOutputStream(file.uri)?.use { out ->
                        backupEngine.createBackup(out)
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

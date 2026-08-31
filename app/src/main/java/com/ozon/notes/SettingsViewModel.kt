package com.ozon.notes

import android.content.ContentResolver
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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

enum class InitialSyncDialogType {
    NONE,
    NEW_DEVICE_CLOUD_FOUND,
    CONFLICTING_DATA_MERGE,
    MANUAL_OPTIONS
}

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

    val showNotesTabState: StateFlow<Boolean> = repository.getShowNotesTab()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showListsTabState: StateFlow<Boolean> = repository.getShowListsTab()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lastBackupTimeState: StateFlow<Long> = repository.getLastBackupTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val autoBackupEnabled: StateFlow<Boolean> = repository.getAutoBackupEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dropboxAutoBackupEnabled: StateFlow<Boolean> = repository.getDropboxAutoBackupEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dropboxSyncWifiOnly: StateFlow<Boolean> = repository.getDropboxSyncWifiOnly()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _mobileDataDownloadPrompt = MutableStateFlow<Long?>(null)
    val mobileDataDownloadPrompt = _mobileDataDownloadPrompt.asStateFlow()

    fun dismissMobileDataPrompt() {
        _mobileDataDownloadPrompt.value = null
        _dropboxSyncStatus.value = DropboxSyncStatus.Idle
    }

    fun confirmMobileDataSync() {
        _mobileDataDownloadPrompt.value = null
        syncWithDropbox(silent = false, forceMobileData = true)
    }

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

    val lastDropboxBackupTime: StateFlow<Long> = repository.getLastDropboxBackupTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lastDropboxSyncTime: StateFlow<Long> = repository.getLastDropboxSyncTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val syncingItems: StateFlow<List<SyncItemInfo>> = repository.getDropboxSyncingItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _initialSyncDialogType = MutableStateFlow(InitialSyncDialogType.NONE)
    val initialSyncDialogType = _initialSyncDialogType.asStateFlow()

    val showInitialSyncDialog = _initialSyncDialogType.map { it != InitialSyncDialogType.NONE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setShowInitialSyncDialog(show: Boolean) {
        _initialSyncDialogType.value = if (show) InitialSyncDialogType.MANUAL_OPTIONS else InitialSyncDialogType.NONE
    }

    fun dismissInitialSyncDialog() {
        _initialSyncDialogType.value = InitialSyncDialogType.NONE
    }

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

    fun checkPendingInitialSync() {
        viewModelScope.launch {
            if (dropboxAuthManager.isLoggedIn()) {
                val lastSyncTime = repository.getLastDropboxSyncTime().first()
                if (lastSyncTime == 0L && _initialSyncDialogType.value == InitialSyncDialogType.NONE) {
                    val syncCheck = repository.checkDropboxSyncState()
                    if (syncCheck.remoteExists && !syncCheck.localHasData) {
                        _initialSyncDialogType.value = InitialSyncDialogType.NEW_DEVICE_CLOUD_FOUND
                    } else if (syncCheck.remoteExists && syncCheck.localHasData) {
                        _initialSyncDialogType.value = InitialSyncDialogType.CONFLICTING_DATA_MERGE
                    }
                }
            }
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
            val syncTime = repository.getLastDropboxSyncTime().first()

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
                autoBackupEnabled = repository.getDropboxAutoBackupEnabled().first(),
                isConfigured = configured,
                lastSyncTime = syncTime
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
                val syncCheck = repository.checkDropboxSyncState()
                if (syncCheck.remoteExists && !syncCheck.localHasData) {
                    // Scenario A: New/empty device, existing Dropbox data found -> Ask user to Download vs Clear Cloud
                    _initialSyncDialogType.value = InitialSyncDialogType.NEW_DEVICE_CLOUD_FOUND
                    onResult(true, null)
                } else if (syncCheck.remoteExists && syncCheck.localHasData) {
                    // Scenario B: Device has notes AND Dropbox has data -> Ask user to Merge vs Overwrite
                    _initialSyncDialogType.value = InitialSyncDialogType.CONFLICTING_DATA_MERGE
                    onResult(true, null)
                } else {
                    // Dropbox is empty: safe to initialize and upload local notes (if any)
                    resolveInitialSync(InitialSyncMode.OVERWRITE_REMOTE) { success, err ->
                        onResult(success, err)
                    }
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Authentication failed"
                onResult(false, err)
            }
        }
    }

    fun resolveInitialSync(mode: InitialSyncMode, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _initialSyncDialogType.value = InitialSyncDialogType.NONE
            val statusMsg = when (mode) {
                InitialSyncMode.MERGE -> "Merging notes & lists with Dropbox..."
                InitialSyncMode.OVERWRITE_LOCAL -> "Downloading notes & lists from Dropbox..."
                InitialSyncMode.OVERWRITE_REMOTE -> "Uploading notes & lists to Dropbox..."
            }
            _dropboxSyncStatus.value = DropboxSyncStatus.Syncing(statusMsg)
            val result = repository.resolveInitialSync(mode)
            when (result) {
                is SyncResult.Success -> {
                    _dropboxSyncStatus.value = DropboxSyncStatus.Success(result.message)
                    refreshDropboxState()
                    updateEstimatedBackupSize()
                    onComplete(true, null)
                }
                is SyncResult.ConfirmationRequired -> {
                    _dropboxSyncStatus.value = DropboxSyncStatus.Idle
                    _mobileDataDownloadPrompt.value = result.downloadBytes
                    onComplete(false, null)
                }
                is SyncResult.NoOp -> {
                    _dropboxSyncStatus.value = DropboxSyncStatus.Idle
                    onComplete(true, null)
                }
                is SyncResult.Error -> {
                    _dropboxSyncStatus.value = DropboxSyncStatus.Error(result.message)
                    onComplete(false, result.message)
                }
            }
        }
    }

    fun disconnectDropbox() {
        dropboxAuthManager.logout()
        BackupWorker.cancelPeriodic(repository.getContext())
        _dropboxAuthState.value = DropboxAuthState(
            isConnected = false,
            isConfigured = dropboxAuthManager.isConfigured()
        )
    }

    fun syncWithDropbox(
        silent: Boolean = false,
        forceMobileData: Boolean = false,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            if (!silent) {
                _dropboxSyncStatus.value = DropboxSyncStatus.Syncing("Syncing with Dropbox...")
            }
            val result = repository.syncWithDropbox(forceMobileData)
            when (result) {
                is SyncResult.Success -> {
                    _dropboxSyncStatus.value = DropboxSyncStatus.Success(result.message)
                    refreshDropboxState()
                    updateEstimatedBackupSize()
                    onComplete(true, null)
                }
                is SyncResult.ConfirmationRequired -> {
                    _dropboxSyncStatus.value = DropboxSyncStatus.Idle
                    if (!silent) {
                        _mobileDataDownloadPrompt.value = result.downloadBytes
                    }
                    onComplete(false, null)
                }
                is SyncResult.NoOp -> {
                    if (!silent) {
                        _dropboxSyncStatus.value = DropboxSyncStatus.Idle
                    }
                    onComplete(true, null)
                }
                is SyncResult.Error -> {
                    _dropboxSyncStatus.value = DropboxSyncStatus.Error(result.message)
                    onComplete(false, result.message)
                }
            }
        }
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
                    repository.setLastDropboxSyncTime(currentTime)
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

    fun createLocalBackup(uri: Uri, contentResolver: ContentResolver, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = contentResolver.openOutputStream(uri)
                    ?: throw IOException("Failed to open destination file for writing")
                stream.use { out ->
                    backupEngine.createBackup(out)
                }
                repository.setLastBackupTime(System.currentTimeMillis())
                repository.setHasPendingChanges(false)
                updateEstimatedBackupSize()
                withContext(Dispatchers.Main) {
                    onComplete(true, null)
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Local backup failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun restoreLocalBackup(uri: Uri, contentResolver: ContentResolver, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IOException("Failed to open backup file for reading")
                val success = stream.use { input ->
                    backupEngine.restoreBackup(input, isMerge = false)
                }
                if (success) {
                    updateEstimatedBackupSize()
                    withContext(Dispatchers.Main) {
                        onComplete(true, null)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Invalid or unreadable backup file")
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Local restore failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun exportGranularNote(noteId: String, uri: Uri, contentResolver: ContentResolver, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = contentResolver.openOutputStream(uri)
                    ?: throw IOException("Failed to open destination file for writing")
                val success = stream.use { out ->
                    backupEngine.exportNote(noteId, out)
                }
                withContext(Dispatchers.Main) {
                    onComplete(success, if (success) null else "Note export failed")
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Note export failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.localizedMessage ?: "Export failed")
                }
            }
        }
    }

    fun exportGranularList(listId: String, uri: Uri, contentResolver: ContentResolver, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = contentResolver.openOutputStream(uri)
                    ?: throw IOException("Failed to open destination file for writing")
                val success = stream.use { out ->
                    backupEngine.exportList(listId, out)
                }
                withContext(Dispatchers.Main) {
                    onComplete(success, if (success) null else "List export failed")
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "List export failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.localizedMessage ?: "Export failed")
                }
            }
        }
    }

    fun importGranularBackup(uri: Uri, contentResolver: ContentResolver, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IOException("Failed to open backup file for reading")
                val success = stream.use { input ->
                    backupEngine.restoreBackup(input, isMerge = true)
                }
                if (success) {
                    updateEstimatedBackupSize()
                    withContext(Dispatchers.Main) {
                        onComplete(true, null)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Could not import backup data")
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Granular import failed", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.localizedMessage ?: "Import failed")
                }
            }
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
            is NoteEvent.UpdateShowNotesTab -> viewModelScope.launch { repository.setShowNotesTab(event.show) }
            is NoteEvent.UpdateShowListsTab -> viewModelScope.launch { repository.setShowListsTab(event.show) }
            is NoteEvent.UpdateAutoBackupEnabled -> viewModelScope.launch { repository.setAutoBackupEnabled(event.enabled) }
            is NoteEvent.UpdateDropboxAutoBackupEnabled -> viewModelScope.launch { 
                repository.setDropboxAutoBackupEnabled(event.enabled)
                if (event.enabled) {
                    BackupWorker.schedulePeriodic(repository.getContext())
                    val syncCheck = repository.checkDropboxSyncState()
                    if (repository.getLastDropboxSyncTime().first() == 0L) {
                        if (syncCheck.remoteExists && !syncCheck.localHasData) {
                            _initialSyncDialogType.value = InitialSyncDialogType.NEW_DEVICE_CLOUD_FOUND
                        } else if (syncCheck.remoteExists && syncCheck.localHasData) {
                            _initialSyncDialogType.value = InitialSyncDialogType.CONFLICTING_DATA_MERGE
                        } else {
                            syncWithDropbox(silent = false)
                        }
                    } else {
                        syncWithDropbox(silent = false)
                    }
                } else {
                    BackupWorker.cancelPeriodic(repository.getContext())
                }
            }
            is NoteEvent.UpdateDropboxSyncWifiOnly -> viewModelScope.launch { repository.setDropboxSyncWifiOnly(event.enabled) }
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

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
import java.io.IOException

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

    val fontState: StateFlow<AppFont> = repository.getAppFont()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFont.DEFAULT)

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

    val solidHeaders = repository.getSolidHeaders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSolidHeaders(enabled: Boolean) {
        viewModelScope.launch { repository.setSolidHeaders(enabled) }
    }

    val advancedUiState: StateFlow<Boolean> = repository.getAdvancedUi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val advancedUiStyleState: StateFlow<AdvancedUiStyle> = repository.getAdvancedUiStyle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdvancedUiStyle.SIMPLE_BLUR)

    val liquidGlassFrostingState: StateFlow<Float> = repository.getLiquidGlassFrosting()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultLiquidGlassFrosting)

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

    val lastSelectedTabState: StateFlow<MainTab> = repository.getLastSelectedTab()

    val hideUncreatedTabsState: StateFlow<Boolean> = repository.getHideUncreatedTabs()

    val mobileCombinedViewState: StateFlow<Boolean> = repository.getMobileCombinedView()

    val showTabLabelsState: StateFlow<Boolean> = repository.getShowTabLabels()

    val tabOrderState: StateFlow<List<MainTab>> = repository.getTabOrder()

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

    val smoothingStrength: StateFlow<Float> = repository.getSmoothingStrength()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)

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

    private val _localBackup = MutableStateFlow<LocalBackupState>(LocalBackupState.Idle)
    internal val localBackup = _localBackup.asStateFlow()
    private var restoreChoice: CompletableDeferred<BackupSelection?>? = null

    private suspend fun libraryItems() = withContext(Dispatchers.IO) {
        backupItems(repository.getAllNotes().first(), repository.getAllLists().first())
    }

    private fun launchBackupOperation(title: String, action: suspend () -> Unit) {
        _localBackup.value = LocalBackupState.Busy(title)
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SettingsViewModel", title, e)
                val reason = when (e) {
                    is kotlinx.serialization.SerializationException -> "This file is not a readable Notes backup."
                    is SecurityException -> "File access was denied. Choose the file again."
                    else -> e.localizedMessage ?: "Please try again."
                }
                _localBackup.value = LocalBackupState.Message("Couldn’t finish. $reason")
            }
        }
    }

    internal fun chooseLocalBackupItems() = launchBackupOperation("Reading your library") {
        _localBackup.value = LocalBackupState.Picking(libraryItems(), restoring = false)
    }

    internal fun previewLocalRestore(uri: Uri) = launchBackupOperation("Reading backup") {
        val restored = withContext(Dispatchers.IO) {
            val resolver = repository.getContext().contentResolver
            val input = resolver.openInputStream(uri) ?: throw IOException("Couldn’t open this backup.")
            input.use {
                backupEngine.withBackupData(it) { data ->
                    data.validateIdentifiers()
                    val items = backupItems(data.notes, data.lists).markMatchingTitles(libraryItems())
                    // Keep validated data and its staged media alive until this exact preview is confirmed.
                    val selection = withContext(Dispatchers.Main) {
                        val choice = CompletableDeferred<BackupSelection?>()
                        restoreChoice = choice
                        try {
                            _localBackup.value = LocalBackupState.Picking(items, restoring = true)
                            choice.await()
                        } finally {
                            if (restoreChoice === choice) restoreChoice = null
                        }
                    }
                    if (selection == null) return@withBackupData false
                    repository.importBackupData(data.restoreCopies(selection))
                    true
                }
            }
        }
        if (restored) {
            updateEstimatedBackupSize()
            _localBackup.value = LocalBackupState.Message("Selected items restored")
        }
    }

    internal fun updateBackupSelection(selected: Set<BackupItem>) {
        val picker = _localBackup.value as? LocalBackupState.Picking ?: return
        _localBackup.value = picker.copy(selected = selected)
    }

    internal fun confirmBackupSelection() {
        val picker = _localBackup.value as? LocalBackupState.Picking ?: return
        if (picker.selected.isEmpty()) return
        val selection = BackupSelection(picker.selected)
        if (picker.restoring) {
            _localBackup.value = LocalBackupState.Busy("Restoring selected items")
            restoreChoice?.complete(selection)
        } else {
            _localBackup.value = LocalBackupState.Destination(selection)
        }
    }

    internal fun saveLocalBackup(uri: Uri?) {
        val destination = _localBackup.value as? LocalBackupState.Destination
        if (uri == null) {
            dismissLocalBackup()
            return
        }
        if (destination == null) {
            _localBackup.value = LocalBackupState.Message("Choose the items to back up again.")
            return
        }
        launchBackupOperation("Creating backup") {
            withContext(Dispatchers.IO) {
                val resolver = repository.getContext().contentResolver
                val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Couldn’t open this file for writing.")
                output.use { repository.writeLocalBackup(it, destination.selection) }
            }
            _localBackup.value = LocalBackupState.Message("Backup saved")
        }
    }

    internal fun dismissLocalBackup() {
        restoreChoice?.complete(null)
        _localBackup.value = LocalBackupState.Idle
    }

    fun onEvent(event: NoteEvent) {
        when (event) {
            is NoteEvent.UpdateTheme -> viewModelScope.launch { repository.setTheme(event.theme) }
            is NoteEvent.UpdateAppFont -> viewModelScope.launch { repository.setAppFont(event.font) }
            is NoteEvent.UpdateUseDynamicColor -> viewModelScope.launch { repository.setUseDynamicColor(event.enabled) }
            is NoteEvent.UpdateCustomPrimaryColor -> viewModelScope.launch { repository.setCustomPrimaryColor(event.color) }
            is NoteEvent.UpdateCustomSecondaryColor -> viewModelScope.launch { repository.setCustomSecondaryColor(event.color) }
            is NoteEvent.UpdateCustomAccentColor -> viewModelScope.launch { repository.setCustomAccentColor(event.color) }
            is NoteEvent.UpdateIsOledMode -> viewModelScope.launch { repository.setIsOledMode(event.enabled) }
            is NoteEvent.UpdateAdvancedUi -> viewModelScope.launch { repository.setAdvancedUi(event.enabled) }
            is NoteEvent.UpdateAdvancedUiStyle -> viewModelScope.launch { repository.setAdvancedUiStyle(event.style) }
            is NoteEvent.UpdateLiquidGlassFrosting -> viewModelScope.launch { repository.setLiquidGlassFrosting(event.frosting) }
            is NoteEvent.UpdateTabletMode -> viewModelScope.launch { repository.setTabletMode(event.mode) }
            is NoteEvent.UpdateChecklistBehavior -> viewModelScope.launch { repository.setChecklistBehavior(event.behavior) }
            is NoteEvent.UpdateShowEntryCount -> viewModelScope.launch { repository.setShowEntryCount(event.show) }
            is NoteEvent.UpdateShowNotesTab -> viewModelScope.launch { repository.setShowNotesTab(event.show) }
            is NoteEvent.UpdateShowListsTab -> viewModelScope.launch { repository.setShowListsTab(event.show) }
            is NoteEvent.UpdateLastSelectedTab -> viewModelScope.launch { repository.setLastSelectedTab(event.tab) }
            is NoteEvent.UpdateHideUncreatedTabs -> viewModelScope.launch { repository.setHideUncreatedTabs(event.hide) }
            is NoteEvent.UpdateMobileCombinedView -> viewModelScope.launch { repository.setMobileCombinedView(event.enabled) }
            is NoteEvent.UpdateShowTabLabels -> viewModelScope.launch { repository.setShowTabLabels(event.show) }
            is NoteEvent.UpdateTabOrder -> viewModelScope.launch { repository.setTabOrder(event.order) }
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

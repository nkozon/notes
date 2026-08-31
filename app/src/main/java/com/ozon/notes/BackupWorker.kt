package com.ozon.notes

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Background worker responsible for performing automatic backups (local and Dropbox cloud).
 * 
 * Why WorkManager?
 * Manual backup triggers in `onPause` can be unreliable if the system kills the app process 
 * immediately. WorkManager ensures that the backup job survives process death and executes 
 * under proper conditions (e.g., storage not low).
 */
class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val repository = AppContainer.provideRepository(applicationContext)
        val backupEngine = repository.getBackupEngine()
        val dropboxAuthManager = repository.getDropboxAuthManager()

        val localEnabled = repository.getAutoBackupEnabled().first()
        val dropboxEnabled = repository.getDropboxAutoBackupEnabled().first()
        val uriString = repository.getBackupUri().first()
        val hasChanges = repository.getHasPendingChanges().first()

        if (!localEnabled && !dropboxEnabled) {
            return@withContext ListenableWorker.Result.success()
        }

        var localSuccess = false
        var dropboxSuccess = false

        // 1. Local Auto Backup (only needed when there are local pending changes)
        if (localEnabled && hasChanges && uriString != null) {
            try {
                val treeUri = android.net.Uri.parse(uriString)
                val pickedDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
                if (pickedDir != null) {
                    val fileName = "auto_backup_${System.currentTimeMillis()}.notesbackup"
                    val file = pickedDir.createFile("application/zip", fileName)
                    if (file != null) {
                        applicationContext.contentResolver.openOutputStream(file.uri)?.use { stream ->
                            backupEngine.createBackup(stream)
                        }
                        repository.setLastBackupTime(System.currentTimeMillis())
                        localSuccess = true
                        Log.d("BackupWorker", "Local auto backup successful: $fileName")

                        // Keep rolling window of last 10 auto-backups to prevent storage exhaustion
                        try {
                            val autoBackups = pickedDir.listFiles()
                                .filter { it.name?.startsWith("auto_backup_") == true && it.name?.endsWith(".notesbackup") == true }
                                .sortedByDescending { it.lastModified() }
                            if (autoBackups.size > 10) {
                                autoBackups.drop(10).forEach { oldBackup ->
                                    oldBackup.delete()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("BackupWorker", "Error pruning old auto backups", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BackupWorker", "Local auto backup failed", e)
            }
        }

        // 2. Dropbox Cloud Auto-Sync (bidirectional sync across devices)
        if (dropboxEnabled && dropboxAuthManager.isLoggedIn()) {
            try {
                Log.d("BackupWorker", "Triggering Dropbox auto-sync...")
                val syncResult = repository.syncWithDropbox()
                when (syncResult) {
                    is SyncResult.Success -> {
                        dropboxSuccess = true
                        Log.d("BackupWorker", "Dropbox auto-sync completed: ${syncResult.message}")
                    }
                    is SyncResult.ConfirmationRequired -> {
                        Log.d("BackupWorker", "Dropbox auto-sync skipped: Wi-Fi only or cellular data confirmation required")
                    }
                    is SyncResult.NoOp -> {
                        Log.d("BackupWorker", "Dropbox auto-sync skipped (already in progress)")
                    }
                    is SyncResult.Error -> {
                        Log.e("BackupWorker", "Dropbox auto-sync error: ${syncResult.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("BackupWorker", "Dropbox auto-sync exception", e)
            }
        }

        if (localSuccess) {
            repository.setHasPendingChanges(false)
        }

        ListenableWorker.Result.success()
    }

    companion object {
        private const val WORK_NAME = "auto_backup_work"
        private const val PERIODIC_WORK_NAME = "periodic_dropbox_sync_work"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresStorageNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}

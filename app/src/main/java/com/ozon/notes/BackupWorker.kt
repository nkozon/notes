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
        val dropboxClient = repository.getDropboxClient()

        val localEnabled = repository.getAutoBackupEnabled().first()
        val dropboxEnabled = repository.getDropboxAutoBackupEnabled().first()
        val uriString = repository.getBackupUri().first()
        val hasChanges = repository.getHasPendingChanges().first()

        if (!hasChanges || (!localEnabled && !dropboxEnabled)) {
            return@withContext ListenableWorker.Result.success()
        }

        var localSuccess = false
        var dropboxSuccess = false

        // 1. Local Auto Backup
        if (localEnabled && uriString != null) {
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
                    }
                }
            } catch (e: Exception) {
                Log.e("BackupWorker", "Local auto backup failed", e)
            }
        }

        // 2. Dropbox Cloud Auto Backup
        if (dropboxEnabled && dropboxAuthManager.isLoggedIn()) {
            val tempFile = File(applicationContext.cacheDir, "auto_dropbox_backup.notesbackup")
            try {
                tempFile.outputStream().use { stream ->
                    backupEngine.createBackup(stream)
                }
                val uploadResult = dropboxClient.uploadBackup(tempFile)
                if (uploadResult.isSuccess) {
                    repository.setLastDropboxBackupTime(System.currentTimeMillis())
                    dropboxSuccess = true
                    Log.d("BackupWorker", "Dropbox auto backup successful")
                } else {
                    Log.e("BackupWorker", "Dropbox auto backup failed: ${uploadResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("BackupWorker", "Dropbox auto backup exception", e)
            } finally {
                tempFile.delete()
            }
        }

        if (localSuccess || dropboxSuccess) {
            repository.setHasPendingChanges(false)
        }

        ListenableWorker.Result.success()
    }

    companion object {
        private const val WORK_NAME = "auto_backup_work"

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
    }
}

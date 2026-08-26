package com.ozon.notes

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import java.util.concurrent.TimeUnit

/**
 * Background worker responsible for performing automatic JSON backups.
 * 
 * Why WorkManager?
 * Manual backup triggers in `onPause` can be unreliable if the system kills the app process 
 * immediately. WorkManager ensures that the backup job survives process death and executes 
 * under proper conditions (e.g., storage not low).
 */
@OptIn(ExperimentalSerializationApi::class)
class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val repository = AppContainer.provideRepository(applicationContext)
        
        val enabled = repository.getAutoBackupEnabled().first()
        val uriString = repository.getBackupUri().first()
        val hasChanges = repository.getHasPendingChanges().first()

        if (!enabled || uriString == null || !hasChanges) {
            return@withContext ListenableWorker.Result.success()
        }

        try {
            val treeUri = android.net.Uri.parse(uriString)
            val pickedDir = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: return@withContext ListenableWorker.Result.failure()
            
            val fileName = "auto_backup_${System.currentTimeMillis()}.json"
            val file = pickedDir.createFile("application/json", fileName) ?: return@withContext ListenableWorker.Result.failure()
            
            val data = repository.getBackupData()
            
            applicationContext.contentResolver.openOutputStream(file.uri)?.use { stream ->
                kotlinx.serialization.json.Json.encodeToStream(data, stream)
            }
            
            repository.setLastBackupTime(System.currentTimeMillis())
            repository.setHasPendingChanges(false)
            
            Log.d("BackupWorker", "Auto backup successful: $fileName")
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Auto backup failed", e)
            ListenableWorker.Result.retry()
        }
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

package com.ozon.notes

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@Serializable
data class DropboxAccountName(
    val display_name: String = ""
)

@Serializable
data class DropboxAccountResponse(
    val name: DropboxAccountName = DropboxAccountName(),
    val email: String = ""
)

@Serializable
data class DropboxSpaceAllocation(
    val allocated: Long = 0L
)

@Serializable
data class DropboxSpaceResponse(
    val used: Long = 0L,
    val allocation: DropboxSpaceAllocation? = null
)

@Serializable
data class DropboxFileMetadata(
    val name: String = "",
    val size: Long = 0L,
    val server_modified: String? = null
)

class DropboxClient(
    private val authManager: DropboxAuthManager
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAccountInfo(): Result<DropboxAccountResponse> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/users/get_current_account")
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get account info: $body"))
            }
            val account = json.decodeFromString<DropboxAccountResponse>(body)
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSpaceUsage(): Result<DropboxSpaceResponse> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/users/get_space_usage")
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get space usage: $body"))
            }

            // Extract used and allocated cleanly (supports individual or team allocation formats)
            val jsonObject = json.decodeFromString<JsonObject>(body)
            val used = jsonObject["used"]?.jsonPrimitive?.longOrNull ?: 0L
            val allocationObj = jsonObject["allocation"] as? JsonObject
            val allocated = allocationObj?.get("allocated")?.jsonPrimitive?.longOrNull ?: (2L * 1024 * 1024 * 1024)

            Result.success(DropboxSpaceResponse(used = used, allocation = DropboxSpaceAllocation(allocated = allocated)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBackupMetadata(fileName: String = "backup_latest.notesbackup"): Result<DropboxFileMetadata?> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val argJson = "{\"path\": \"/$fileName\"}"
        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/get_metadata")
            .header("Authorization", "Bearer $token")
            .header("Dropbox-API-Arg", argJson)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (response.code == 409) {
                // File does not exist
                return@withContext Result.success(null)
            }
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get metadata: $body"))
            }
            val metadata = json.decodeFromString<DropboxFileMetadata>(body)
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadBackup(
        file: File,
        fileName: String = "backup_latest.notesbackup"
    ): Result<DropboxFileMetadata> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val argJson = "{\"path\": \"/$fileName\", \"mode\": \"overwrite\", \"autorename\": false, \"mute\": false}"
        val requestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/upload")
            .header("Authorization", "Bearer $token")
            .header("Dropbox-API-Arg", argJson)
            .header("Content-Type", "application/octet-stream")
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Upload failed: $body"))
            }
            val metadata = json.decodeFromString<DropboxFileMetadata>(body)
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadBackup(
        targetFile: File,
        fileName: String = "backup_latest.notesbackup"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val argJson = "{\"path\": \"/$fileName\"}"
        val request = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/download")
            .header("Authorization", "Bearer $token")
            .header("Dropbox-API-Arg", argJson)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("Download failed: $body"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            targetFile.outputStream().use { fileOut ->
                body.byteStream().use { input ->
                    input.copyTo(fileOut)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseServerModified(timestamp: String?): Long? {
        if (timestamp == null) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(timestamp)?.time
        } catch (e: Exception) {
            null
        }
    }
}

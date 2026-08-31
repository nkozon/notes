package com.ozon.notes

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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

enum class DropboxEntryType {
    FILE,
    FOLDER,
    DELETED
}

data class DropboxDeltaEntry(
    val type: DropboxEntryType,
    val name: String,
    val pathLower: String,
    val pathDisplay: String,
    val size: Long,
    val serverModified: String?
)

data class DropboxFolderListing(
    val entries: List<DropboxDeltaEntry>,
    val cursor: String,
    val hasMore: Boolean
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
            .header("Content-Type", "application/json")
            .post("null".toRequestBody("application/json".toMediaTypeOrNull()))
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
            .header("Content-Type", "application/json")
            .post("null".toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get space usage: $body"))
            }

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

        val path = if (fileName.startsWith("/")) fileName else "/$fileName"
        val jsonBody = "{\"path\": \"$path\", \"include_media_info\": false, \"include_deleted\": false, \"include_has_explicit_shared_members\": false}"
        val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/get_metadata")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.code == 409 || body.contains("path/not_found")) {
                return@withContext Result.success(null)
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to get metadata: $body"))
            }
            val metadata = json.decodeFromString<DropboxFileMetadata>(body)
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFolder(path: String = "/sync", recursive: Boolean = true): Result<DropboxFolderListing> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val normalizedPath = if (path == "/" || path.isEmpty()) "" else if (path.startsWith("/")) path else "/$path"
        val jsonBody = "{\"path\": \"$normalizedPath\", \"recursive\": $recursive, \"include_deleted\": true, \"include_has_explicit_shared_members\": false}"
        val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.code == 409 || body.contains("path/not_found")) {
                return@withContext Result.success(DropboxFolderListing(emptyList(), "", false))
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("List folder failed: $body"))
            }

            val jsonObject = json.decodeFromString<JsonObject>(body)
            val cursor = jsonObject["cursor"]?.jsonPrimitive?.content ?: ""
            val hasMore = jsonObject["has_more"]?.jsonPrimitive?.booleanOrNull ?: false
            val rawEntries = jsonObject["entries"]?.jsonArray ?: JsonArray(emptyList())

            val entries = rawEntries.mapNotNull { element ->
                val obj = element.jsonObject
                val tag = obj[".tag"]?.jsonPrimitive?.content ?: ""
                val type = when (tag) {
                    "file" -> DropboxEntryType.FILE
                    "folder" -> DropboxEntryType.FOLDER
                    "deleted" -> DropboxEntryType.DELETED
                    else -> return@mapNotNull null
                }
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val pathLower = obj["path_lower"]?.jsonPrimitive?.content ?: ""
                val pathDisplay = obj["path_display"]?.jsonPrimitive?.content ?: pathLower
                val size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
                val serverModified = obj["server_modified"]?.jsonPrimitive?.content

                DropboxDeltaEntry(
                    type = type,
                    name = name,
                    pathLower = pathLower,
                    pathDisplay = pathDisplay,
                    size = size,
                    serverModified = serverModified
                )
            }

            Result.success(DropboxFolderListing(entries, cursor, hasMore))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFolderContinue(cursor: String): Result<DropboxFolderListing> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        if (cursor.isBlank()) {
            return@withContext listFolder("/sync", true)
        }

        val jsonBody = "{\"cursor\": \"$cursor\"}"
        val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/list_folder/continue")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.code == 409 && (body.contains("reset") || body.contains("path/not_found"))) {
                // Cursor expired or reset required -> do a fresh listFolder
                return@withContext listFolder("/sync", true)
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("List folder continue failed: $body"))
            }

            val jsonObject = json.decodeFromString<JsonObject>(body)
            val newCursor = jsonObject["cursor"]?.jsonPrimitive?.content ?: cursor
            val hasMore = jsonObject["has_more"]?.jsonPrimitive?.booleanOrNull ?: false
            val rawEntries = jsonObject["entries"]?.jsonArray ?: JsonArray(emptyList())

            val entries = rawEntries.mapNotNull { element ->
                val obj = element.jsonObject
                val tag = obj[".tag"]?.jsonPrimitive?.content ?: ""
                val type = when (tag) {
                    "file" -> DropboxEntryType.FILE
                    "folder" -> DropboxEntryType.FOLDER
                    "deleted" -> DropboxEntryType.DELETED
                    else -> return@mapNotNull null
                }
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val pathLower = obj["path_lower"]?.jsonPrimitive?.content ?: ""
                val pathDisplay = obj["path_display"]?.jsonPrimitive?.content ?: pathLower
                val size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
                val serverModified = obj["server_modified"]?.jsonPrimitive?.content

                DropboxDeltaEntry(
                    type = type,
                    name = name,
                    pathLower = pathLower,
                    pathDisplay = pathDisplay,
                    size = size,
                    serverModified = serverModified
                )
            }

            Result.success(DropboxFolderListing(entries, newCursor, hasMore))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        path: String,
        bytes: ByteArray
    ): Result<DropboxFileMetadata> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val argJson = "{\"path\": \"$normalizedPath\", \"mode\": \"overwrite\", \"autorename\": false, \"mute\": true}"
        val requestBody = bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())

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

    suspend fun uploadFile(
        path: String,
        file: File
    ): Result<DropboxFileMetadata> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val argJson = "{\"path\": \"$normalizedPath\", \"mode\": \"overwrite\", \"autorename\": false, \"mute\": true}"
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

    suspend fun downloadFileBytes(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val argJson = "{\"path\": \"$normalizedPath\"}"
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
            val bytes = response.body?.bytes() ?: ByteArray(0)
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFileTo(
        path: String,
        targetFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val argJson = "{\"path\": \"$normalizedPath\"}"
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

    suspend fun deletePath(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authManager.getValidAccessToken()
            ?: return@withContext Result.failure(Exception("Not authenticated with Dropbox"))

        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val jsonBody = "{\"path\": \"$normalizedPath\"}"
        val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/delete_v2")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.code == 409 && body.contains("path_lookup/not_found")) {
                return@withContext Result.success(Unit)
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Delete failed: $body"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadBackup(
        file: File,
        fileName: String = "backup_latest.notesbackup"
    ): Result<DropboxFileMetadata> = uploadFile(fileName, file)

    suspend fun downloadBackup(
        targetFile: File,
        fileName: String = "backup_latest.notesbackup"
    ): Result<Unit> = downloadFileTo(fileName, targetFile)

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

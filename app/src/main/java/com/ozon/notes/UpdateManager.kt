package com.ozon.notes

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val githubReleasesApiUrl = "https://api.github.com/repos/nkozon/notes/releases?per_page=50"
    private val githubLatestApiUrl = "https://api.github.com/repos/nkozon/notes/releases/latest"

    suspend fun checkForUpdate(): UpdateState = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion()

            val url = URL(githubReleasesApiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "Notes-App")
            connection.connect()

            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val allReleases = json.decodeFromString<List<GitHubRelease>>(responseBody)

                val newerReleases = allReleases.filter { isNewerVersion(it.tagName, currentVersion) }

                if (newerReleases.isNotEmpty()) {
                    val sortedReleases = newerReleases.sortedWith { r1, r2 ->
                        if (isNewerVersion(r1.tagName, r2.tagName)) -1
                        else if (isNewerVersion(r2.tagName, r1.tagName)) 1
                        else 0
                    }

                    val releaseWithApk = sortedReleases.find { rel -> rel.assets.any { it.name.endsWith(".apk") } }
                    val apkAsset = releaseWithApk?.assets?.find { it.name.endsWith(".apk") }

                    if (apkAsset != null) {
                        val versionChangelogs = sortedReleases.map { rel ->
                            val rawTag = rel.tagName.trim()
                            val formattedVersion = if (!rawTag.startsWith("v", ignoreCase = true)) "v$rawTag" else rawTag
                            VersionChangelog(
                                version = formattedVersion,
                                changelogs = parseChangelogBody(rel.body)
                            )
                        }

                        return@withContext UpdateState.UpdateAvailable(
                            version = sortedReleases.first().tagName,
                            body = sortedReleases.first().body,
                            downloadUrl = apkAsset.downloadUrl,
                            releases = versionChangelogs
                        )
                    }
                }
                return@withContext UpdateState.UpToDate
            } else {
                return@withContext checkLatestFallback(currentVersion)
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to check for update", e)
            return@withContext UpdateState.Error(e.localizedMessage ?: "Unknown error")
        }
    }

    private suspend fun checkLatestFallback(currentVersion: String): UpdateState = withContext(Dispatchers.IO) {
        try {
            val url = URL(githubLatestApiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "Notes-App")
            connection.connect()

            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<GitHubRelease>(responseBody)

                if (isNewerVersion(release.tagName, currentVersion)) {
                    val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                    if (apkAsset != null) {
                        val rawTag = release.tagName.trim()
                        val formattedVersion = if (!rawTag.startsWith("v", ignoreCase = true)) "v$rawTag" else rawTag
                        val changelog = VersionChangelog(
                            version = formattedVersion,
                            changelogs = parseChangelogBody(release.body)
                        )
                        return@withContext UpdateState.UpdateAvailable(
                            version = release.tagName,
                            body = release.body,
                            downloadUrl = apkAsset.downloadUrl,
                            releases = listOf(changelog)
                        )
                    }
                }
                UpdateState.UpToDate
            } else {
                UpdateState.Error("Server returned code ${connection.responseCode}")
            }
        } catch (e: Exception) {
            UpdateState.Error(e.localizedMessage ?: "Unknown error")
        }
    }

    fun parseChangelogBody(body: String?): List<String> {
        if (body.isNullOrBlank()) return listOf("Bug fixes and performance improvements")

        val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
        val changelogItems = mutableListOf<String>()

        for (line in lines) {
            if (line.startsWith("#")) continue
            if (line.contains("Full Changelog", ignoreCase = true) && line.contains("http")) continue
            if (line.startsWith("http://") || line.startsWith("https://")) continue

            var cleanLine = line
            cleanLine = cleanLine.replace(Regex("^[\\*\\-\\+•]\\s*"), "")
            cleanLine = cleanLine.replace(Regex("^\\d+\\.\\s*"), "")
            cleanLine = cleanLine.replace(Regex("\\s+by\\s+@[^\\s]+\\s+in\\s+https?://[^\\s]+"), "")
            cleanLine = cleanLine.replace(Regex("\\s+in\\s+https?://[^\\s]+"), "")

            cleanLine = cleanLine.trim()

            if (cleanLine.isNotBlank() && !cleanLine.startsWith("**") && cleanLine != "---") {
                changelogItems.add(cleanLine)
            }
        }

        return if (changelogItems.isNotEmpty()) changelogItems else listOf(body.trim())
    }

    fun downloadAndInstallUpdate(url: String, versionName: String): Long? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Please allow 'Install unknown apps' and try again", Toast.LENGTH_LONG).show()
                return null
            }
        }

        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "notes-$versionName.apk")
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Notes Update")
            .setDescription("Version $versionName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destination))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    installApk(destination)
                    context.unregisterReceiver(this)
                }
            }
        }
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_EXPORTED
        } else {
            0
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), flags)
        return downloadId
    }

    fun getDownloadProgress(downloadId: Long): Float {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (bytesTotal > 0) {
                return bytesDownloaded.toFloat() / bytesTotal.toFloat()
            }
        }
        cursor.close()
        return 0f
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestClean = latest.removePrefix("v").removePrefix("V").trim()
        val currentClean = current.removePrefix("v").removePrefix("V").trim()
        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

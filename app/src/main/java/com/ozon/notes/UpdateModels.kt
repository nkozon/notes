package com.ozon.notes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val version: String, val body: String?, val downloadUrl: String) : UpdateState
    data object UpToDate : UpdateState
    data class Error(val message: String) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
}

package com.ozon.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
data class DropboxTokenResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val expires_in: Long = 14400,
    val refresh_token: String? = null,
    val account_id: String? = null,
    val uid: String? = null,
    val error: String? = null,
    val error_description: String? = null
)

class DropboxAuthManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("dropbox_auth_prefs", Context.MODE_PRIVATE)
    private val okHttpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    val appKey: String = BuildConfig.DROPBOX_APP_KEY
    val redirectUri: String = "notesapp://dropbox-auth"

    fun isConfigured(): Boolean = appKey.isNotBlank() && appKey != "YOUR_DROPBOX_APP_KEY"

    fun isLoggedIn(): Boolean = prefs.getString("refresh_token", null) != null || prefs.getString("access_token", null) != null

    /**
     * Initiates OAuth 2.0 PKCE flow by opening the browser / CustomTab.
     */
    fun startOAuth(context: Context) {
        if (!isConfigured()) {
            Log.e("DropboxAuth", "Dropbox App Key is not configured in local.properties")
            return
        }

        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        prefs.edit().putString("pkce_verifier", codeVerifier).apply()

        val authUrl = "https://www.dropbox.com/oauth2/authorize" +
                "?client_id=$appKey" +
                "&response_type=code" +
                "&code_challenge=$codeChallenge" +
                "&code_challenge_method=S256" +
                "&redirect_uri=$redirectUri" +
                "&token_access_type=offline"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Handles redirect from browser (notesapp://dropbox-auth?code=...).
     */
    suspend fun handleRedirectUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")

        if (error != null) {
            return@withContext Result.failure(Exception("Dropbox authentication error: $error"))
        }

        if (code == null) {
            return@withContext Result.failure(Exception("No authorization code received from Dropbox"))
        }

        val codeVerifier = prefs.getString("pkce_verifier", null)
            ?: return@withContext Result.failure(Exception("Missing PKCE code verifier"))

        val formBody = FormBody.Builder()
            .add("code", code)
            .add("grant_type", "authorization_code")
            .add("client_id", appKey)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/oauth2/token")
            .post(formBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Token exchange failed ($responseBody)"))
            }

            val tokenResponse = json.decodeFromString<DropboxTokenResponse>(responseBody)
            val accessToken = tokenResponse.access_token
                ?: return@withContext Result.failure(Exception("Missing access_token in response"))
            val refreshToken = tokenResponse.refresh_token
            val expiresIn = tokenResponse.expires_in

            val expiryTimestamp = System.currentTimeMillis() + (expiresIn - 300) * 1000L

            prefs.edit()
                .putString("access_token", accessToken)
                .apply {
                    if (refreshToken != null) {
                        putString("refresh_token", refreshToken)
                    }
                }
                .putLong("token_expires_at", expiryTimestamp)
                .putString("account_id", tokenResponse.account_id)
                .remove("pkce_verifier")
                .apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DropboxAuth", "Error exchanging code for token", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves a valid access token, automatically refreshing it if needed.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val accessToken = prefs.getString("access_token", null)
        val refreshToken = prefs.getString("refresh_token", null)
        val expiresAt = prefs.getLong("token_expires_at", 0L)

        // If access token is valid and not expired, return it
        if (accessToken != null && System.currentTimeMillis() < expiresAt) {
            return@withContext accessToken
        }

        // If expired but refresh token exists, refresh it
        if (refreshToken != null) {
            return@withContext refreshAccessToken(refreshToken)
        }

        accessToken
    }

    private suspend fun refreshAccessToken(refreshToken: String): String? = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", appKey)
            .build()

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/oauth2/token")
            .post(formBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("DropboxAuth", "Failed to refresh token: $responseBody")
                return@withContext null
            }

            val tokenResponse = json.decodeFromString<DropboxTokenResponse>(responseBody)
            val newAccessToken = tokenResponse.access_token ?: return@withContext null
            val expiresIn = tokenResponse.expires_in
            val expiryTimestamp = System.currentTimeMillis() + (expiresIn - 300) * 1000L

            prefs.edit()
                .putString("access_token", newAccessToken)
                .putLong("token_expires_at", expiryTimestamp)
                .apply()

            newAccessToken
        } catch (e: Exception) {
            Log.e("DropboxAuth", "Error during token refresh", e)
            null
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(64)
        secureRandom.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

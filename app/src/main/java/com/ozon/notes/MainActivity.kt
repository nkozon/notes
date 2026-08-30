package com.ozon.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ozon.notes.ui.theme.NotesTheme

class MainActivity : ComponentActivity() {
    private val initialListId = mutableStateOf<String?>(null)
    private val rescheduleEntryId = mutableStateOf<String?>(null)
    private val pendingAuthUri = mutableStateOf<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        createNotificationChannel()

        handleIntent(intent)

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            val repository = AppContainer.provideRepository(applicationContext)
            
            val notesViewModel: NotesViewModel = viewModel(
                factory = NotesViewModel.provideFactory(repository)
            )
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(repository)
            )
            val checklistViewModel: ChecklistViewModel = viewModel(
                factory = ChecklistViewModel.provideFactory(repository)
            )

            val authUri by pendingAuthUri
            LaunchedEffect(authUri) {
                authUri?.let { uri ->
                    settingsViewModel.handleDropboxAuthRedirect(uri) { success, error ->
                        val msg = if (success) "Dropbox connected successfully" else "Dropbox login failed: ${error ?: "Unknown error"}"
                        android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    pendingAuthUri.value = null
                }
            }

            val appTheme by settingsViewModel.themeState.collectAsStateWithLifecycle()
            val useDynamicColor by settingsViewModel.useDynamicColorState.collectAsStateWithLifecycle()
            val customPrimaryColor by settingsViewModel.customPrimaryColorState.collectAsStateWithLifecycle()
            val customSecondaryColor by settingsViewModel.customSecondaryColorState.collectAsStateWithLifecycle()
            val customAccentColor by settingsViewModel.customAccentColorState.collectAsStateWithLifecycle()
            val isOledMode by settingsViewModel.isOledModeState.collectAsStateWithLifecycle()

            val listId by initialListId
            val entryId by rescheduleEntryId

            val isDarkTheme = when (appTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            // Update edge-to-edge style when theme changes
            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { isDarkTheme }
                )
            }

            // Auto-backup trigger using WorkManager
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE) {
                        BackupWorker.enqueue(applicationContext)
                    }
                }
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            NotesTheme(
                darkTheme = isDarkTheme,
                dynamicColor = useDynamicColor,
                customPrimaryColor = customPrimaryColor,
                customSecondaryColor = customSecondaryColor,
                customAccentColor = customAccentColor,
                isOledMode = isOledMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAdaptiveScreen(
                        notesViewModel = notesViewModel,
                        settingsViewModel = settingsViewModel,
                        checklistViewModel = checklistViewModel,
                        initialListId = listId,
                        rescheduleEntryId = entryId
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        initialListId.value = intent?.getStringExtra("listId")
        rescheduleEntryId.value = intent?.getStringExtra("rescheduleEntryId")
        val data = intent?.data
        if (data?.scheme == "notesapp" && data.host == "dropbox-auth") {
            pendingAuthUri.value = data
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Upcoming Notes"
            val descriptionText = "Notifications for upcoming note entries"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel("upcoming_notes_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: android.app.NotificationManager =
                getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

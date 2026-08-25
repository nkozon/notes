package com.ozon.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviePostersScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit,
) {
    val moviePostersEnabled by viewModel.moviePostersEnabled.collectAsStateWithLifecycle()
    val posterCacheSize by viewModel.posterCacheSize.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.updatePosterCacheSize()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Movie Posters",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        CircleIconButton(
                            onClick = onNavigateUp,
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { _ ->
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val scrollState = rememberScrollState()

        val topAlpha by remember {
            derivedStateOf {
                (scrollState.value / 100f).coerceIn(0f, 1f)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(
                        top = topPadding + 64.dp,
                        bottom = bottomPadding + 16.dp
                    )
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = LinearOutSlowInEasing
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SettingsSection(title = "General") {
                    SettingsItemContainer(index = 0, total = 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Grab Movie Posters",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = "Auto-fetch posters for rating lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = moviePostersEnabled,
                                onCheckedChange = {
                                    viewModel.onEvent(
                                        NoteEvent.UpdateMoviePostersEnabled(it)
                                    )
                                }
                            )
                        }
                    }

                    SettingsItemContainer(
                        index = 1,
                        total = 2,
                        onClick = {
                            viewModel.onEvent(NoteEvent.ClearPosterCache)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Clear Poster Cache",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                val sizeMb = posterCacheSize / (1024f * 1024f)

                                Text(
                                    text = "Current usage: ${"%.2f".format(sizeMb)} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // TMDb Attribution Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.tmdb_logo),
                        contentDescription = "TMDb Logo",
                        modifier = Modifier.height(32.dp)
                    )

                    Text(
                        text = "The movie posters are fetched with the TMDb API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Text(
                        text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    TextButton(
                        onClick = {
                            uriHandler.openUri("https://www.themoviedb.org")
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Visit themoviedb.org")

                            Spacer(Modifier.width(8.dp))

                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            SystemBarGradients(
                modifier = Modifier
                    .zIndex(1f)
                    .align(Alignment.TopCenter),
                topAlpha = topAlpha
            )
        }
    }
}
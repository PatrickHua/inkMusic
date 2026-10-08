package com.calmapps.calmmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmapps.calmmusic.MonoMusicViewModel
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.text.TextMMD

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailsScreen(
    artistId: String?,
    viewModel: MonoMusicViewModel,
    onPlaySongClick: (SongUiModel, List<SongUiModel>) -> Unit,
    onAlbumClick: (AlbumUiModel) -> Unit,
    onShuffleSongsClick: (List<SongUiModel>) -> Unit,
    onAddToPlaylistClick: (SongUiModel) -> Unit,
    onRemoveFromLibraryClick: (SongUiModel) -> Unit,
    onDeleteClick: (SongUiModel) -> Unit,
) {
    var songs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumUiModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Songs first: it is the tab people actually use.
    val tabOptions = listOf("Songs", "Albums")

    val playbackState by viewModel.playbackState.collectAsState()
    val currentSongId = playbackState.currentSongId

    val refreshTrigger by viewModel.libraryRefreshTrigger.collectAsState()

    LaunchedEffect(artistId, refreshTrigger) {
        if (artistId == null) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        try {
            val content = viewModel.getArtistContent(artistId)
            songs = content.songs
            albums = content.albums
            if (songs.isEmpty() && albums.isNotEmpty()) selectedTab = 1
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load artist"
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "Loading artist...")
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextMMD(text = "Error loading artist")
                        TextMMD(text = errorMessage!!)
                    }
                }
            }

            songs.isEmpty() && albums.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "No content for this artist yet")
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    PrimaryTabRowMMD(selectedTabIndex = selectedTab) {
                        tabOptions.forEachIndexed { index, title ->
                            TabMMD(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    TextMMD(
                                        text = title,
                                        fontSize = 16.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }

                    if (selectedTab == 1) {
                        // Albums tab
                        InkLazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.Top,
                        ) {
                            if (albums.isNotEmpty()) {
                                items(albums) { album ->
                                    AlbumItem(
                                        album = album,
                                        onClick = { onAlbumClick(album) },
                                        showDivider = album != albums.lastOrNull(),
                                    )
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TextMMD(text = "No albums for this artist")
                                    }
                                }
                            }
                        }
                    } else {
                        // Songs tab
                        val songsListState = rememberLazyListState()
                        val lastSongId = songs.lastOrNull()?.id
                        if (songs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextMMD(text = "No songs for this artist")
                            }
                        } else {
                            AlphabetIndexed(items = songs, label = { it.title }, state = songsListState) { listModifier ->
                                InkLazyColumn(
                                    modifier = listModifier,
                                    state = songsListState,
                                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                                ) {
                                    items(songs, key = { it.id }) { song ->
                                        SongItem(
                                            song = song,
                                            isCurrentlyPlaying = song.id == currentSongId,
                                            onClick = { onPlaySongClick(song, songs) },
                                            onAddToPlaylist = { onAddToPlaylistClick(song) },
                                            onRemoveFromLibrary = { onRemoveFromLibraryClick(song) },
                                            onDelete = { onDeleteClick(song) },
                                            showDivider = song.id != lastSongId,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isLoading && errorMessage == null && songs.isNotEmpty()) {
            FloatingActionButtonMMD(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                onClick = { onShuffleSongsClick(songs) },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle artist songs",
                )
            }
        }
    }
}
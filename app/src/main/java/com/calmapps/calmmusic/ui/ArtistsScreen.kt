package com.calmapps.calmmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

/** Simple UI model for distinct artists in the library. */
data class ArtistUiModel(
    val id: String,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

@Composable
fun ArtistsScreen(
    artists: List<ArtistUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    isSyncInProgress: Boolean,
    hasAnySongs: Boolean,
    onOpenStreamingSettingsClick: () -> Unit,
    onOpenLocalSettingsClick: () -> Unit,
    onArtistClick: (ArtistUiModel) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "Loading artists...")
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextMMD(text = "Error loading artists")
                        TextMMD(text = errorMessage)
                    }
                }
            }

            artists.isEmpty() && isSyncInProgress -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "Music sync is in progress…")
                }
            }

            artists.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!hasAnySongs) {
                        LibraryOnboardingEmptyState(
                            title = "No artists yet",
                            body = "Download songs from YouTube Music or choose local folders in Settings to start building your library.",
                            onOpenStreamingSettingsClick = onOpenStreamingSettingsClick,
                            onOpenLocalSettingsClick = onOpenLocalSettingsClick,
                        )
                    } else {
                        TextMMD(text = "No artists to show yet. Once your songs have artist info, they'll appear here.")
                    }
                }
            }

            else -> {
                val lastArtistId = artists.lastOrNull()?.id
                val listState = rememberLazyListState()
                val (letters, firstIndexOf) = rememberLetterIndex(artists) { it.name }
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumnMMD(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    ) {
                        items(
                            items = artists,
                            key = { it.id },
                        ) { artist ->
                            val isLast = artist.id == lastArtistId
                            ArtistItem(
                                artist = artist,
                                onClick = { onArtistClick(artist) },
                                showDivider = !isLast,
                            )
                        }
                    }
                    AlphabetIndex(letters = letters, firstIndexOf = firstIndexOf, listState = listState)
                }
            }
        }
    }
}

/** One line per artist (name and song count), so a screen shows about twice as many. */
@Composable
fun ArtistItem(
    artist: ArtistUiModel,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextMMD(
                text = artist.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextMMD(
                text = artist.songCount.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
        }

        if (showDivider) {
            DashedDivider(thickness = 1.dp)
        }
    }
}

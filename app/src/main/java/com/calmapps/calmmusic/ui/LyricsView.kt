package com.calmapps.calmmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmapps.calmmusic.lyrics.Lyrics

/**
 * Lyrics for the Now Playing screen. The line being sung is bold. To spare the
 * E-ink panel, the list never animates: it jumps a page only when the current
 * line leaves the screen. Tapping a synced line seeks to it.
 */
@Composable
fun LyricsView(
    lyrics: Lyrics?,
    isLoading: Boolean,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        lyrics == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (isLoading) "Looking for lyrics…" else "No lyrics found",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        lyrics.instrumental && lyrics.lines.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Instrumental", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        else -> {
            // Blank timed lines mark pauses; on a small screen they only waste rows.
            val shown = remember(lyrics) {
                if (lyrics.synced) lyrics.copy(lines = lyrics.lines.filter { it.text.isNotBlank() }) else lyrics
            }
            val listState = rememberLazyListState()
            val current = shown.lineIndexAt(positionMs)

            LaunchedEffect(current) {
                if (current < 0) return@LaunchedEffect
                val visible = listState.layoutInfo.visibleItemsInfo
                val first = visible.firstOrNull()?.index ?: 0
                // Keep one line of look-ahead below the current line.
                val lastFullyVisible = (visible.lastOrNull()?.index ?: 0) - 1
                if (current < first || current > lastFullyVisible) {
                    listState.scrollToItem((current - 1).coerceAtLeast(0))
                }
            }

            LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
                itemsIndexed(shown.lines) { index, line ->
                    val time = line.timeMs
                    Text(
                        text = line.text.ifBlank { " " },
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = if (index == current) FontWeight.Black else FontWeight.Normal,
                        color = if (index == current || current < 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (time != null) Modifier.clickable { onSeek(time) } else Modifier)
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

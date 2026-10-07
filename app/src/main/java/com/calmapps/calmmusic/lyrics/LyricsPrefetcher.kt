package com.calmapps.calmmusic.lyrics

import android.content.Context
import androidx.media3.common.Player
import com.calmapps.calmmusic.data.MonoMusicDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Loads (and if needed downloads and saves) lyrics for each song as soon as the
 * player knows its duration, so every played song ends up with lyrics on disk
 * whether or not the Now Playing screen is open.
 */
class LyricsPrefetcher(private val context: Context) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSongId: String? = null

    override fun onEvents(player: Player, events: Player.Events) {
        val item = player.currentMediaItem ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        if (item.mediaId == lastSongId) return
        lastSongId = item.mediaId

        val meta = item.mediaMetadata
        val itemUri = item.localConfiguration?.uri?.toString()
        scope.launch {
            // Items can arrive without their uri; the library knows the song's file.
            val librarySong = MonoMusicDatabase.getDatabase(context).songDao().getById(item.mediaId)
            val query = LyricsQuery(
                songId = item.mediaId,
                title = meta.title?.toString() ?: librarySong?.title.orEmpty(),
                artist = meta.artist?.toString() ?: librarySong?.artist.orEmpty(),
                album = meta.albumTitle?.toString() ?: librarySong?.album,
                durationMs = duration,
                audioFile = LyricsQuery.fileOf(itemUri) ?: LyricsQuery.fileOf(librarySong?.localUri),
            )
            if (query.title.isNotBlank()) LyricsStore.load(context, query, fetch = true)
        }
    }

    fun release() {
        scope.cancel()
    }
}

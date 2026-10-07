package com.calmapps.calmmusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.calmapps.calmmusic.data.LibraryRepository
import com.calmapps.calmmusic.data.MonoMusicDatabase
import com.calmapps.calmmusic.lyrics.LyricsQuery
import com.calmapps.calmmusic.lyrics.LyricsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Commands a computer can send over adb. The manifest guards this receiver with
 * the DUMP permission, which adb's shell holds and ordinary apps cannot get.
 *
 *     adb shell am broadcast -a io.github.patrickhua.inkmusic.RESCAN \
 *         -n io.github.patrickhua.inkmusic/com.calmapps.calmmusic.AgentCommandReceiver
 *
 * RESCAN re-reads `inkMusic/` (songs and playlist files) and rewrites
 * `library.json`; open screens reload when it finishes.
 *
 * FETCH_LYRICS looks up lyrics for every library song that has none yet and saves
 * them as `.lrc` files. It runs in the background (progress in logcat under
 * "inkMusicAgent"); send it again to resume after an interruption.
 */
class AgentCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MonoMusic
        when (intent.action?.substringAfterLast('.')) {
            "RESCAN" -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        val stats = LibraryRepository(app).sync()
                        Log.i(TAG, "Rescan done: ${stats.totalFiles} files, ${stats.addedOrUpdated} updated, ${stats.removed} removed")
                        pending.resultData = "files=${stats.totalFiles} updated=${stats.addedOrUpdated} removed=${stats.removed}"
                    } catch (e: Exception) {
                        Log.e(TAG, "Rescan failed", e)
                        pending.resultData = "error=${e.message}"
                    } finally {
                        pending.finish()
                    }
                }
            }
            "FETCH_LYRICS" -> {
                if (lyricsJob?.isActive == true) {
                    resultData = "already running"
                    return
                }
                lyricsJob = scope.launch { fetchAllLyrics(app) }
                resultData = "started"
            }
            else -> Log.w(TAG, "Unknown command: ${intent.action}")
        }
    }

    private suspend fun fetchAllLyrics(app: MonoMusic) {
        val songs = MonoMusicDatabase.getDatabase(app).songDao().getAll()
        var found = 0
        var missing = 0
        songs.forEachIndexed { index, song ->
            val lyrics = LyricsStore.load(
                app,
                LyricsQuery(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    durationMs = song.durationMillis,
                    audioFile = LyricsQuery.fileOf(song.localUri),
                ),
                fetch = true,
            )
            if (lyrics != null) found++ else missing++
            if ((index + 1) % 25 == 0) Log.i(TAG, "Lyrics: ${index + 1}/${songs.size} ($found found, $missing without)")
        }
        Log.i(TAG, "Lyrics done: $found found, $missing without, ${songs.size} songs")
    }

    private companion object {
        const val TAG = "inkMusicAgent"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var lyricsJob: Job? = null
    }
}

package com.calmapps.calmmusic

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
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
import kotlinx.coroutines.runBlocking

/**
 * Commands a computer can send over adb. Each call returns its result directly:
 *
 *     adb shell content call --uri content://io.github.patrickhua.inkmusic.agent --method RESCAN
 *
 *  - RESCAN: re-read `inkMusic/` (songs and playlist files), rewrite
 *    `library.json`, and reload open screens. Returns file counts when done.
 *  - FETCH_LYRICS: start fetching lyrics for every song without them, in the
 *    background. Returns at once; call again (or STATUS) to see progress.
 *  - STATUS: progress of the lyrics fetch.
 *
 * A provider is used rather than a broadcast receiver because some devices
 * (MediaTek builds) silently drop broadcasts to apps. The manifest requires the
 * DUMP permission, which adb's shell holds and ordinary apps cannot get.
 */
class AgentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = context!!.applicationContext as MonoMusic
        val result = when (method.uppercase()) {
            "RESCAN" -> runBlocking {
                val stats = LibraryRepository(app).sync()
                "files=${stats.totalFiles} updated=${stats.addedOrUpdated} removed=${stats.removed}"
            }
            "FETCH_LYRICS" -> {
                if (lyricsJob?.isActive != true) lyricsJob = scope.launch { fetchAllLyrics(app) }
                "lyrics: $lyricsProgress"
            }
            "STATUS" -> "lyrics: $lyricsProgress"
            else -> "unknown method $method (RESCAN, FETCH_LYRICS, STATUS)"
        }
        return Bundle().apply { putString("result", result) }
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
            lyricsProgress = "running ${index + 1}/${songs.size}, $found with lyrics, $missing without"
            if ((index + 1) % 25 == 0) Log.i(TAG, lyricsProgress)
        }
        lyricsProgress = "done: $found with lyrics, $missing without, ${songs.size} songs"
        Log.i(TAG, lyricsProgress)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val TAG = "inkMusicAgent"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var lyricsJob: Job? = null

        @Volatile
        var lyricsProgress = "not started"
    }
}

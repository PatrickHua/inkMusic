package com.calmapps.calmmusic.lyrics

import android.content.Context
import android.net.Uri
import android.util.Log
import com.calmapps.calmmusic.data.InkStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** What the store needs to know about a song to find its lyrics. */
data class LyricsQuery(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    /** The song's audio file, when it has one (`file://` uri or path). */
    val audioFile: File?,
) {
    companion object {
        fun fileOf(uri: String?): File? =
            uri?.let { Uri.parse(it) }?.takeIf { it.scheme == "file" }?.path?.let(::File)
    }
}

/**
 * Finds lyrics and keeps them as files the user owns:
 *
 *  1. `<song>.lrc` next to the audio file (e.g. copied from a computer)
 *  2. lyrics embedded in the audio file's tags
 *  3. `inkMusic/lyrics/<songId>.lrc`, the cache for songs without a file (streams)
 *  4. LRCLIB, saved to 1 when the song has a file, else to 3
 *
 * Songs LRCLIB has nothing for are remembered in `inkMusic/lyrics/.not-found` and
 * retried after [RETRY_MISSING_AFTER_MS]. Concurrent requests for one song share
 * a single lookup, so the player and the Now Playing screen never fetch twice.
 */
object LyricsStore {

    private const val TAG = "inkMusicLyrics"
    private const val LYRICS_DIR = "lyrics"
    private const val NOT_FOUND_FILE = ".not-found"
    private const val RETRY_MISSING_AFTER_MS = 14L * 24 * 60 * 60 * 1000

    private val client by lazy { LrcLibClient() }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Lyrics?>>()
    private val memory = object : LinkedHashMap<String, Lyrics>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?) = size > 20
    }

    fun sidecarFor(audio: File): File = File(audio.parentFile, "${audio.nameWithoutExtension}.lrc")

    private fun lyricsDir(context: Context) = File(InkStorage.primaryRoot(context), LYRICS_DIR)

    private fun cacheFor(context: Context, songId: String) =
        File(lyricsDir(context), songId.replace(Regex("""[\\/:*?"<>|]"""), "_") + ".lrc")

    /**
     * Lyrics for [query] from files only, or from LRCLIB too when [fetch] is set.
     * Returns null when the song has no lyrics (or they could not be fetched).
     */
    suspend fun load(context: Context, query: LyricsQuery, fetch: Boolean): Lyrics? {
        synchronized(memory) { memory[query.songId] }?.let { return it }

        val mine = CompletableDeferred<Lyrics?>()
        val existing = inFlight.putIfAbsent(query.songId, mine)
        if (existing != null) return existing.await()

        return try {
            val result = withContext(Dispatchers.IO) { resolve(context, query, fetch) }
            if (result != null) synchronized(memory) { memory[query.songId] = result }
            mine.complete(result)
            result
        } catch (e: Exception) {
            mine.complete(null)
            null
        } finally {
            inFlight.remove(query.songId)
        }
    }

    private fun resolve(context: Context, query: LyricsQuery, fetch: Boolean): Lyrics? {
        val audio = query.audioFile?.takeIf { it.isFile }

        audio?.let(::sidecarFor)?.takeIf { it.isFile }?.let { return readLrc(it) }
        audio?.let(::embeddedLyrics)?.let { return it }

        val cache = cacheFor(context, query.songId)
        if (cache.isFile) {
            val cached = readLrc(cache)
            // The song now has a file (e.g. it was downloaded): keep its lyrics beside it.
            if (audio != null && cached != null) save(sidecarFor(audio), cached, query)
            return cached
        }

        if (!fetch || isKnownMissing(context, query.songId)) return null

        val fetched = try {
            client.find(query.title, query.artist, query.durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB lookup failed for ${query.title}", e)
            return null // Offline or server trouble: try again next time.
        }
        if (fetched == null) {
            markMissing(context, query.songId)
            return null
        }
        save(audio?.let(::sidecarFor) ?: cache, fetched, query)
        return fetched
    }

    private fun readLrc(file: File): Lyrics? =
        try {
            Lyrics.parseLrc(file.readText()).takeIf { it.lines.isNotEmpty() || it.instrumental }
        } catch (_: Exception) {
            null
        }

    private fun embeddedLyrics(audio: File): Lyrics? =
        try {
            TagOptionSingleton.getInstance().isAndroid = true
            val text = AudioFileIO.read(audio).tag?.getFirst(FieldKey.LYRICS)
            text?.takeIf { it.isNotBlank() }?.let { Lyrics.parseLrc(it).copy(source = "embedded") }
                ?.takeIf { it.lines.isNotEmpty() }
        } catch (_: Exception) {
            null
        }

    private fun save(target: File, lyrics: Lyrics, query: LyricsQuery) {
        try {
            InkStorage.writeAtomically(target, lyrics.toLrc(query.title, query.artist, query.album, query.durationMs))
        } catch (e: Exception) {
            Log.w(TAG, "Could not save lyrics to $target", e)
        }
    }

    private fun notFoundFile(context: Context) = File(lyricsDir(context), NOT_FOUND_FILE)

    @Synchronized
    private fun isKnownMissing(context: Context, songId: String): Boolean {
        val file = notFoundFile(context)
        if (!file.isFile) return false
        val now = System.currentTimeMillis()
        return file.readLines().any { line ->
            val (id, time) = line.split('\t').let { it.getOrNull(0) to it.getOrNull(1)?.toLongOrNull() }
            id == songId && time != null && now - time < RETRY_MISSING_AFTER_MS
        }
    }

    @Synchronized
    private fun markMissing(context: Context, songId: String) {
        try {
            val file = notFoundFile(context)
            file.parentFile?.mkdirs()
            file.appendText("$songId\t${System.currentTimeMillis()}\n")
        } catch (_: Exception) {
        }
    }
}

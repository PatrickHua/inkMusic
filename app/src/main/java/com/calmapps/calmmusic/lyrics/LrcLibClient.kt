package com.calmapps.calmmusic.lyrics

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Looks up lyrics on LRCLIB (https://lrclib.net), a free database of synced
 * lyrics that needs no account. Returns null when nothing matches well enough;
 * throws on network errors so callers can retry later.
 */
class LrcLibClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) {

    fun find(title: String, artist: String, durationMs: Long?): Lyrics? {
        val cleanTitle = cleanTitle(title)
        if (cleanTitle.isBlank()) return null

        // Untagged files often carry "Artist - Title" (or "Title - Artist") as the title.
        val guesses = buildList {
            add(cleanTitle to artist)
            if (artist.isBlank() && " - " in cleanTitle) {
                val left = cleanTitle.substringBefore(" - ").trim()
                val right = cleanTitle.substringAfter(" - ").trim()
                add(right to left)
                add(left to right)
            }
        }

        var match: JSONObject? = null
        for ((guessTitle, guessArtist) in guesses) {
            if (guessArtist.isBlank()) continue
            match = best(search("track_name" to guessTitle, "artist_name" to guessArtist), guessTitle, guessArtist, durationMs)
            if (match != null) break
        }
        if (match == null && artist.isNotBlank()) {
            match = best(search("q" to "$cleanTitle $artist"), cleanTitle, artist, durationMs)
        }
        // Title alone finds songs filed under another spelling of the artist
        // (鄧麗君 / 邓丽君 / Teresa Teng); only a matching duration makes that safe.
        if (match == null && durationMs != null && durationMs > 0) {
            match = best(search("track_name" to cleanTitle), cleanTitle, "", durationMs)
        }
        match ?: return null

        if (match.optBoolean("instrumental")) {
            return Lyrics(emptyList(), synced = false, instrumental = true, source = SOURCE)
        }
        val synced = match.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = match.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val text = synced ?: plain ?: return null
        return Lyrics.parseLrc(text).copy(source = SOURCE)
    }

    private fun best(candidates: List<JSONObject>, title: String, artist: String, durationMs: Long?): JSONObject? =
        candidates
            .filter { isGoodMatch(it, title, artist, durationMs) }
            // Prefer synced lyrics, then the closest duration.
            .sortedWith(
                compareBy<JSONObject>(
                    { it.optString("syncedLyrics").isBlank() || it.optString("syncedLyrics") == "null" },
                    { durationGap(it, durationMs) },
                ),
            )
            .firstOrNull()

    private fun search(vararg params: Pair<String, String>): List<JSONObject> {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return emptyList()
            if (!response.isSuccessful) error("LRCLIB returned ${response.code}")
            val array = JSONArray(response.body?.string().orEmpty())
            return (0 until array.length()).map { array.getJSONObject(it) }
        }
    }

    private fun isGoodMatch(candidate: JSONObject, title: String, artist: String, durationMs: Long?): Boolean {
        if (durationMs != null && durationMs > 0) {
            // Same recording: durations within a few seconds.
            return durationGap(candidate, durationMs) <= 3_000
        }
        // No duration to compare: require the title and artist to agree.
        val titleOk = normalize(candidate.optString("trackName")) == normalize(title)
        val artistOk = artist.isBlank() || normalize(candidate.optString("artistName")).let {
            it.contains(normalize(artist)) || normalize(artist).contains(it)
        }
        return titleOk && artistOk
    }

    private fun durationGap(candidate: JSONObject, durationMs: Long?): Long {
        if (durationMs == null) return 0
        val seconds = candidate.optDouble("duration", Double.NaN)
        if (seconds.isNaN()) return Long.MAX_VALUE
        return abs((seconds * 1000).toLong() - durationMs)
    }

    companion object {
        const val SOURCE = "LRCLIB"
        private const val USER_AGENT = "inkMusic (https://github.com/PatrickHua/inkMusic)"

        private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

        /** Drops track numbers and "(Official Video)"-style suffixes that LRCLIB never has. */
        fun cleanTitle(title: String): String = title
            .replace(Regex("""^\d{1,3}\s*[-.]?\s+"""), "")
            .replace(Regex("""\s*[(\[](official|lyric|audio|video|mv|hd|hq|remaster)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}

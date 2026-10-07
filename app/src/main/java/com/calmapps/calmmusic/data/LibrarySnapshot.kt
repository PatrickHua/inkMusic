package com.calmapps.calmmusic.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Writes `inkMusic/library.json`: every indexed song with its metadata and its
 * path relative to the inkMusic root, plus every playlist as the app resolved it
 * (song ids in order), so tools on a computer can read the library without the
 * app's database. Read-only for consumers; rewritten after each sync.
 */
object LibrarySnapshot {

    suspend fun write(context: Context, songs: List<Song>, playlistDao: PlaylistDao) {
        val root = InkStorage.primaryRoot(context)
        val roots = InkStorage.candidateRoots(context).map { it.absolutePath + "/" }
        val array = JSONArray()
        for (song in songs) {
            val path = song.localUri?.let { Uri.parse(it).path }
            val songRoot = path?.let { p -> roots.firstOrNull { p.startsWith(it) } }
            array.put(
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .putOpt("albumArtist", song.albumArtist)
                    .putOpt("album", song.album)
                    .putOpt("track", song.trackNumber)
                    .putOpt("disc", song.discNumber)
                    .putOpt("durationMs", song.durationMillis)
                    .putOpt("year", song.releaseYear)
                    .putOpt("path", path?.let { if (songRoot != null) it.removePrefix(songRoot) else it })
                    .put("youtube", song.isYouTube),
            )
        }
        val playlists = JSONArray()
        for (playlist in playlistDao.getAllPlaylists()) {
            val songIds = JSONArray()
            playlistDao.getSongsForPlaylist(playlist.id).forEach { songIds.put(it.id) }
            playlists.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .putOpt("description", playlist.description)
                    .put("songIds", songIds),
            )
        }
        val json = JSONObject()
            .put("version", 1)
            .put("generatedAt", System.currentTimeMillis())
            .put("songs", array)
            .put("playlists", playlists)
        InkStorage.writeAtomically(File(root, InkStorage.LIBRARY_SNAPSHOT), json.toString(1))
    }
}

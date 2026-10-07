package com.calmapps.calmmusic.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Playlists are `.m3u8` files in `inkMusic/playlists/` and the files are the source
 * of truth: every library sync mirrors them into the database, and every change made
 * in the app is written straight back to its file.
 *
 * A file anyone (or any agent) can write is just paths, one per line:
 *
 *     #EXTM3U
 *     #PLAYLIST:Focus
 *     ../songs/Classical/Bach - Air.mp3
 *     https://music.youtube.com/watch?v=dQw4w9WgXcQ
 *
 * Paths may be absolute or relative to the playlist file, the inkMusic root, or its
 * `songs/` folder. When the app writes a file it adds `#INKMUSIC-*` lines carrying
 * ids, so playlists keep working after songs are renamed or moved.
 */
object PlaylistFiles {

    private const val EXTENSION = "m3u8"
    private const val TAG_NAME = "#PLAYLIST:"
    private const val TAG_ID = "#INKMUSIC-PLAYLIST-ID:"
    private const val TAG_DESCRIPTION = "#INKMUSIC-DESCRIPTION:"
    private const val TAG_CREATED = "#INKMUSIC-CREATED:"
    private const val TAG_SONG_ID = "#INKMUSIC-SONG-ID:"
    private const val TAG_INFO = "#EXTINF:"
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    private data class Entry(
        val location: String,
        val songId: String?,
        val info: String?,
    )

    private data class ParsedPlaylist(
        val file: File,
        val id: String,
        val name: String,
        val description: String?,
        val createdAt: Long?,
        val entries: List<Entry>,
    )

    private fun playlistFiles(context: Context): List<File> =
        InkStorage.existingRoots(context)
            .map { File(it, InkStorage.PLAYLISTS_DIR) }
            .flatMap { dir -> dir.listFiles().orEmpty().toList() }
            .filter { it.isFile && it.extension.lowercase() in setOf("m3u8", "m3u") }

    private fun parse(file: File): ParsedPlaylist {
        var id: String? = null
        var name: String? = null
        var description: String? = null
        var createdAt: Long? = null
        var pendingSongId: String? = null
        var pendingInfo: String? = null
        val entries = mutableListOf<Entry>()

        for (raw in file.readLines()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> {}
                line.startsWith(TAG_ID) -> id = line.removePrefix(TAG_ID).trim()
                line.startsWith(TAG_NAME) -> name = line.removePrefix(TAG_NAME).trim()
                line.startsWith(TAG_DESCRIPTION) -> description = line.removePrefix(TAG_DESCRIPTION).trim()
                line.startsWith(TAG_CREATED) -> createdAt = line.removePrefix(TAG_CREATED).trim().toLongOrNull()
                line.startsWith(TAG_SONG_ID) -> pendingSongId = line.removePrefix(TAG_SONG_ID).trim()
                line.startsWith(TAG_INFO) -> pendingInfo = line.removePrefix(TAG_INFO).substringAfter(',', "").trim()
                line.startsWith("#") -> {}
                else -> {
                    entries += Entry(line, pendingSongId, pendingInfo)
                    pendingSongId = null
                    pendingInfo = null
                }
            }
        }

        return ParsedPlaylist(
            file = file,
            // Files written by hand have no id; derive a stable one from the file name.
            id = id?.takeIf { it.isNotBlank() } ?: "file:${file.nameWithoutExtension}",
            name = name?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            description = description?.takeIf { it.isNotBlank() },
            createdAt = createdAt,
            entries = entries,
        )
    }

    private fun resolveFile(context: Context, playlistFile: File, location: String): File? {
        val path = if (location.startsWith("file://")) Uri.parse(location).path ?: return null else location
        val direct = File(path)
        if (direct.isAbsolute) return direct.takeIf { it.isFile }
        val root = playlistFile.parentFile?.parentFile
        return listOfNotNull(
            playlistFile.parentFile?.let { File(it, path) },
            root?.let { File(it, path) },
            root?.let { File(File(it, InkStorage.SONGS_DIR), path) },
        ).map { it.canonicalFile }.firstOrNull { it.isFile }
    }

    private fun videoIdOf(location: String): String? {
        if (!location.startsWith("http")) return null
        val uri = Uri.parse(location)
        val id = uri.getQueryParameter("v") ?: uri.lastPathSegment.takeIf { uri.host == "youtu.be" }
        return id?.takeIf { VIDEO_ID.matches(it) }
    }

    /**
     * Makes the database's playlists match the files: creates and updates playlists
     * found on disk and deletes playlists whose file is gone. Entries that point at
     * nothing in the library are skipped (the file keeps them).
     */
    suspend fun importAll(context: Context, songDao: SongDao, playlistDao: PlaylistDao) {
        val parsed = playlistFiles(context).map(::parse).distinctBy { it.id }
        val existing = playlistDao.getAllPlaylists().associateBy { it.id }
        if (parsed.isEmpty() && existing.isEmpty()) return

        val songs = songDao.getAll()
        val byLocalPath = songs.mapNotNull { song ->
            song.localUri?.let { Uri.parse(it).path }?.let { it to song }
        }.toMap()
        val byId = songs.associateBy { it.id }

        for (playlist in parsed) {
            val songIds = LinkedHashSet<String>()
            for (entry in playlist.entries) {
                val fromPath = resolveFile(context, playlist.file, entry.location)
                    ?.let { byLocalPath[it.absolutePath] }
                val songId = fromPath?.id
                    ?: entry.songId?.takeIf { it in byId }
                    ?: videoIdOf(entry.location)?.also { videoId ->
                        if (videoId !in byId) songDao.upsertAll(listOf(streamSkeleton(videoId, entry.info)))
                    }
                if (songId != null) songIds += songId
            }

            val old = existing[playlist.id]
            if (old == null) {
                playlistDao.upsertPlaylist(
                    PlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        description = playlist.description,
                        createdAt = playlist.createdAt ?: playlist.file.lastModified(),
                    ),
                )
            } else if (old.name != playlist.name || old.description != playlist.description) {
                playlistDao.updatePlaylistMetadata(playlist.id, playlist.name, playlist.description)
            }
            playlistDao.deleteTracksForPlaylist(playlist.id)
            playlistDao.upsertTracks(
                songIds.mapIndexed { index, songId -> PlaylistTrackEntity(playlist.id, songId, index) },
            )
        }

        val onDisk = parsed.mapTo(mutableSetOf()) { it.id }
        for (stale in existing.values.filter { it.id !in onDisk }) {
            playlistDao.deleteTracksForPlaylist(stale.id)
            playlistDao.deletePlaylist(stale)
        }
    }

    /** Writes one playlist from the database to its file, creating the file if needed. */
    suspend fun export(context: Context, playlistDao: PlaylistDao, playlistId: String) {
        val playlist = playlistDao.getAllPlaylists().firstOrNull { it.id == playlistId } ?: return
        val songs = playlistDao.getSongsForPlaylist(playlistId)
        val file = fileFor(context, playlist)

        val text = buildString {
            appendLine("#EXTM3U")
            appendLine("$TAG_NAME${playlist.name}")
            appendLine("$TAG_ID${playlist.id}")
            playlist.description?.takeIf { it.isNotBlank() }?.let { appendLine("$TAG_DESCRIPTION$it") }
            appendLine("$TAG_CREATED${playlist.createdAt}")
            for (song in songs) {
                val seconds = song.durationMillis?.div(1000) ?: -1
                val label = listOf(song.artist, song.title).filter { it.isNotBlank() }.joinToString(" - ")
                appendLine()
                appendLine("$TAG_INFO$seconds,$label")
                appendLine("$TAG_SONG_ID${song.id}")
                val localPath = song.localUri?.let { Uri.parse(it).path }
                when {
                    localPath != null -> appendLine(File(localPath).relativeToOrSelf(file.parentFile!!).path)
                    song.isYouTube -> appendLine("https://music.youtube.com/watch?v=${song.id}")
                    else -> appendLine("# missing file")
                }
            }
        }
        InkStorage.writeAtomically(file, text)
    }

    /** Deletes the file backing [playlistId], if any. */
    fun delete(context: Context, playlistId: String) {
        playlistFiles(context).firstOrNull { parse(it).id == playlistId }?.delete()
    }

    private fun fileFor(context: Context, playlist: PlaylistEntity): File {
        playlistFiles(context).firstOrNull { parse(it).id == playlist.id }?.let { return it }
        val dir = InkStorage.playlistsDir(context)
        val base = playlist.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Playlist" }
        var candidate = File(dir, "$base.$EXTENSION")
        var n = 2
        while (candidate.exists()) candidate = File(dir, "$base ($n).$EXTENSION").also { n++ }
        return candidate
    }

    private fun streamSkeleton(videoId: String, info: String?): Song {
        val artist = info?.substringBefore(" - ", "")?.trim().orEmpty()
        val title = info?.substringAfter(" - ", info)?.trim()?.ifEmpty { null } ?: videoId
        val artistKey = Song.artistKeyOf(artist, null)
        return Song(
            id = videoId,
            title = title,
            artist = artist,
            albumArtist = null,
            album = null,
            trackNumber = null,
            discNumber = null,
            durationMillis = null,
            releaseYear = null,
            artistKey = artistKey,
            albumKey = null,
            localUri = null,
            localLastModified = null,
            localSizeBytes = null,
        )
    }
}

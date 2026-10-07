package com.calmapps.calmmusic.data

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Everything the user owns lives as plain files under `<volume>/Music/inkMusic/`,
 * so it survives reinstalls and can be read and written from a computer over adb:
 *
 *   songs/        audio files in any folder layout; `.lrc` lyrics sit next to songs
 *   playlists/    one `.m3u8` file per playlist
 *   history/      listening log, one `.jsonl` file per month
 *   library.json  snapshot of the indexed library, rewritten after every sync
 *
 * The app's database is only an index rebuilt from these files. Reading files other
 * apps (or adb) wrote needs "All files access" (MANAGE_EXTERNAL_STORAGE).
 */
object InkStorage {

    const val ROOT_NAME = "inkMusic"
    const val SONGS_DIR = "songs"
    const val PLAYLISTS_DIR = "playlists"
    const val HISTORY_DIR = "history"
    const val DOWNLOADS_DIR = "Downloads"
    const val LIBRARY_SNAPSHOT = "library.json"

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    /** Mount point of every external volume, removable (SD card) first. */
    private fun volumeDirs(context: Context): List<File> =
        context.getExternalFilesDirs(null)
            .filterNotNull()
            .mapNotNull { appDir ->
                val path = appDir.absolutePath
                val index = path.indexOf("/Android/data/")
                if (index < 0) null else File(path.substring(0, index))
            }
            .distinct()
            .sortedByDescending { isRemovable(it) }

    private fun isRemovable(dir: File): Boolean =
        try {
            Environment.isExternalStorageRemovable(dir)
        } catch (_: IllegalArgumentException) {
            false
        }

    /** `Music/inkMusic` on every mounted volume, whether or not it exists yet. */
    fun candidateRoots(context: Context): List<File> =
        volumeDirs(context).map { File(it, "${Environment.DIRECTORY_MUSIC}/$ROOT_NAME") }

    /** Roots that exist; their `songs/` folders make up the library. */
    fun existingRoots(context: Context): List<File> =
        candidateRoots(context).filter { it.isDirectory }

    /**
     * Where new files go: the first existing root (SD card preferred), else a new
     * root on the SD card or primary storage.
     */
    fun primaryRoot(context: Context): File =
        existingRoots(context).firstOrNull()
            ?: candidateRoots(context).firstOrNull()
            ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), ROOT_NAME)

    fun songsDirs(context: Context): List<File> =
        existingRoots(context).map { File(it, SONGS_DIR) }.filter { it.isDirectory }

    fun playlistsDir(context: Context): File = File(primaryRoot(context), PLAYLISTS_DIR)

    fun historyDir(context: Context): File = File(primaryRoot(context), HISTORY_DIR)

    fun downloadsDir(context: Context): File =
        File(File(primaryRoot(context), SONGS_DIR), DOWNLOADS_DIR)

    /** Writes [text] to [target] through a temp file so readers never see half a file. */
    fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
    }
}

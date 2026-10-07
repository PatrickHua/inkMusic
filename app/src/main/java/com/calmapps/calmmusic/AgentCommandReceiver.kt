package com.calmapps.calmmusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.calmapps.calmmusic.data.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
            else -> Log.w(TAG, "Unknown command: ${intent.action}")
        }
    }

    private companion object {
        const val TAG = "inkMusicAgent"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

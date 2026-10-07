package com.calmapps.calmmusic.playback

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.calmapps.calmmusic.data.InkStorage
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Appends one JSON line per listening event to `inkMusic/history/YYYY-MM.jsonl`:
 *
 *     {"time":"2026-10-07T14:55:01Z","event":"start","songId":"…","title":"…","artist":"…"}
 *     {"time":"…","event":"end","songId":"…","result":"completed","listenedMs":201000,"positionMs":203500,"durationMs":204000}
 *
 * `result` is `completed` (played to the end), `skipped` (moved to another song),
 * or `stopped` (playback ended or the queue was replaced). `listenedMs` counts only
 * time actually playing, so seeking does not inflate it; `positionMs` is omitted
 * when the song was left mid-way for another one.
 */
class ListeningLog(private val context: Context) : Player.Listener {

    private val writer = Executors.newSingleThreadExecutor()
    private var player: Player? = null

    private var currentItem: MediaItem? = null
    private var listenedMs = 0L
    private var playingSince: Long? = null

    fun attach(player: Player) {
        this.player = player
        player.addListener(this)
        player.currentMediaItem?.let { start(it) }
        if (player.isPlaying) playingSince = SystemClock.elapsedRealtime()
    }

    /** Closes the current song; call when the player is released. */
    fun detach() {
        finish("stopped")
        player?.removeListener(this)
        player = null
        writer.shutdown()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (isPlaying) {
            if (currentItem == null) player?.currentMediaItem?.let { start(it) }
            playingSince = now
        } else {
            playingSince?.let { listenedMs += now - it }
            playingSince = null
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val result = when {
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "completed"
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "completed"
            // Left mid-way for another song: next/previous, or tapping a song,
            // which replaces the queue.
            mediaItem != null -> "skipped"
            else -> "stopped"
        }
        // By now the player reports the new item, so the old position is only known
        // when the song played to its end.
        finish(result, positionMs = if (result == "completed") currentDurationMs else null, positionKnown = false)
        mediaItem?.let { start(it) }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) finish("completed", positionMs = currentDurationMs)
    }

    private var currentDurationMs: Long? = null

    private fun start(item: MediaItem) {
        currentItem = item
        listenedMs = 0
        playingSince = if (player?.isPlaying == true) SystemClock.elapsedRealtime() else null
        currentDurationMs = null
        val meta = item.mediaMetadata
        append(
            JSONObject()
                .put("event", "start")
                .put("songId", item.mediaId)
                .putOpt("title", meta.title?.toString())
                .putOpt("artist", meta.artist?.toString())
                .putOpt("album", meta.albumTitle?.toString()),
        )
    }

    private fun finish(result: String, positionMs: Long? = null, positionKnown: Boolean = true) {
        val item = currentItem ?: return
        val now = SystemClock.elapsedRealtime()
        playingSince?.let { listenedMs += now - it }
        val p = player
        val duration = currentDurationMs ?: p?.duration?.takeIf { it > 0 && positionKnown }
        append(
            JSONObject()
                .put("event", "end")
                .put("songId", item.mediaId)
                .put("result", result)
                .put("listenedMs", listenedMs)
                .putOpt("positionMs", positionMs ?: p?.currentPosition?.takeIf { positionKnown })
                .putOpt("durationMs", duration),
        )
        currentItem = null
        playingSince = null
        listenedMs = 0
    }

    override fun onEvents(player: Player, events: Player.Events) {
        player.duration.takeIf { it > 0 }?.let { currentDurationMs = it }
    }

    private fun append(event: JSONObject) {
        val time = Date()
        val line = JSONObject().put("time", isoFormat().format(time)).apply {
            event.keys().forEach { put(it, event.get(it)) }
        }.toString()
        writer.execute {
            try {
                val dir = InkStorage.historyDir(context)
                dir.mkdirs()
                File(dir, "${monthFormat().format(time)}.jsonl").appendText(line + "\n")
            } catch (_: Exception) {
                // Never let logging interrupt playback.
            }
        }
    }

    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun monthFormat() = SimpleDateFormat("yyyy-MM", Locale.US)
}

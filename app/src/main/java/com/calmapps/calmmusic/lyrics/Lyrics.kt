package com.calmapps.calmmusic.lyrics

/**
 * Lyrics for one song. [synced] lyrics have a time on every line; plain lyrics
 * have none. [instrumental] marks songs known to have no words.
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val instrumental: Boolean = false,
    val source: String? = null,
) {
    /** Index of the line being sung at [positionMs], or -1 before the first line. */
    fun lineIndexAt(positionMs: Long): Int {
        if (!synced) return -1
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if ((lines[mid].timeMs ?: 0) <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    fun toLrc(title: String?, artist: String?, album: String?, durationMs: Long?): String = buildString {
        title?.takeIf { it.isNotBlank() }?.let { appendLine("[ti:$it]") }
        artist?.takeIf { it.isNotBlank() }?.let { appendLine("[ar:$it]") }
        album?.takeIf { it.isNotBlank() }?.let { appendLine("[al:$it]") }
        durationMs?.let { appendLine("[length:${formatTime(it).substringBefore('.')}]") }
        source?.let { appendLine("[re:$it]") }
        if (instrumental) appendLine("[$INSTRUMENTAL_TAG]")
        for (line in lines) {
            val time = line.timeMs
            if (time != null) appendLine("[${formatTime(time)}]${line.text}") else appendLine(line.text)
        }
    }

    companion object {
        private const val INSTRUMENTAL_TAG = "inkmusic:instrumental"
        private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val META_TAG = Regex("""^\[([a-zA-Z]+):(.*)]$""")

        /** Parses LRC text; lines without time tags make plain (unsynced) lyrics. */
        fun parseLrc(text: String): Lyrics {
            val timed = mutableListOf<LyricLine>()
            val plain = mutableListOf<LyricLine>()
            var instrumental = false
            var source: String? = null

            for (raw in text.lines()) {
                val line = raw.trim().removePrefix("﻿")
                if (line == "[$INSTRUMENTAL_TAG]") {
                    instrumental = true
                    continue
                }
                val times = TIME_TAG.findAll(line).toList()
                if (times.isNotEmpty() && line.startsWith("[")) {
                    // "[00:12.00][01:30.00]chorus" repeats one text at several times.
                    val textPart = line.substring(times.last().range.last + 1).trim()
                    for (match in times) {
                        val (min, sec, frac) = match.destructured
                        val fracMs = when (frac.length) {
                            0 -> 0
                            1 -> frac.toInt() * 100
                            2 -> frac.toInt() * 10
                            else -> frac.take(3).toInt()
                        }
                        timed += LyricLine((min.toLong() * 60 + sec.toLong()) * 1000 + fracMs, textPart)
                    }
                    continue
                }
                val meta = META_TAG.find(line)
                if (meta != null) {
                    if (meta.groupValues[1] == "re") source = meta.groupValues[2].trim()
                    continue
                }
                plain += LyricLine(null, raw.trimEnd())
            }

            return if (timed.isNotEmpty()) {
                Lyrics(timed.sortedBy { it.timeMs }, synced = true, instrumental = instrumental, source = source)
            } else {
                Lyrics(plain.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }, synced = false, instrumental = instrumental, source = source)
            }
        }

        private fun formatTime(ms: Long): String {
            val totalSeconds = ms / 1000
            return "%02d:%02d.%02d".format(totalSeconds / 60, totalSeconds % 60, (ms % 1000) / 10)
        }
    }
}

data class LyricLine(val timeMs: Long?, val text: String)

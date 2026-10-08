package com.calmapps.calmmusic.ui

import android.icu.text.Transliterator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Sort keys that file names by how they sound in Latin letters: 周杰伦 sorts as
 * "zhou jie lun" (under Z) and テレサ as "teresa", so mixed Chinese, Japanese,
 * and English libraries read as one A–Z list.
 */
object SortKeys {
    private val transliterator by lazy { Transliterator.getInstance("Any-Latin; Latin-ASCII; Lower") }
    private val cache = ConcurrentHashMap<String, String>()

    fun of(text: String): String = cache.getOrPut(text) {
        val trimmed = text.trim().removePrefix("The ").removePrefix("the ")
        // Leading quotes and symbols don't count: "Weird Al" files under W.
        synchronized(transliterator) { transliterator.transliterate(trimmed) }
            .trimStart { !it.isLetterOrDigit() }
            .trim()
    }

    /** "A".."Z", or "#" for digits and symbols. */
    fun letterOf(text: String): String {
        val first = of(text).firstOrNull { it.isLetterOrDigit() } ?: return "#"
        return if (first in 'a'..'z') first.uppercase() else "#"
    }
}

/**
 * A strip of the letters present in a list; tapping one jumps the list to its
 * first item, and the letter of the top visible item is shown inverted. Jumps
 * never animate, and the highlight only changes when the letter does, which
 * keeps E-ink refreshes down.
 */
@Composable
fun AlphabetIndex(
    letters: List<String>,
    firstIndexOf: Map<String, Int>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val current by remember(firstIndexOf) {
        derivedStateOf {
            val top = listState.firstVisibleItemIndex
            firstIndexOf.entries.lastOrNull { it.value <= top }?.key
        }
    }
    Column(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight(),
    ) {
        letters.forEach { letter ->
            val selected = letter == current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                    .clickable {
                        firstIndexOf[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Letters present in [items] (in list order) and the first index of each. */
@Composable
fun <T> rememberLetterIndex(items: List<T>, label: (T) -> String): Pair<List<String>, Map<String, Int>> =
    remember(items) {
        val first = LinkedHashMap<String, Int>()
        items.forEachIndexed { index, item -> first.putIfAbsent(SortKeys.letterOf(label(item)), index) }
        first.keys.toList() to first
    }

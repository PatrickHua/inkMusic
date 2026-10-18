package com.calmapps.calmmusic.ui

import android.icu.text.Transliterator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.lazy.LazyDefaultsMMD
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's vertical list, scrolled the way Mudita's LazyColumnMMD does it but
 * without its scrollbar (long lists get [AlphabetIndexed] instead).
 *
 * Normal scrolling is off. Each swipe, however long, jumps the list [scrollStep]
 * rows at once with no animation, so rows are replaced in place and the E-ink
 * panel never draws the in-between frames that leave ghosting behind.
 */
@Composable
fun InkLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    scrollStep: Int = LazyDefaultsMMD.SCROLL_STEP,
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = modifier.pointerInput(state, scrollStep) {
            var jumped = false
            detectVerticalDragGestures(
                onDragEnd = { jumped = false },
                onDragCancel = { jumped = false },
            ) { _, dragAmount ->
                if (jumped) return@detectVerticalDragGestures
                jumped = true
                val total = state.layoutInfo.totalItemsCount
                if (total == 0) return@detectVerticalDragGestures
                // Swiping up moves forward through the list.
                val direction = if (dragAmount > 0) -1 else 1
                val target = (state.firstVisibleItemIndex + direction * scrollStep).coerceIn(0, total - 1)
                scope.launch { state.scrollToItem(target) }
            }
        },
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        userScrollEnabled = false,
        content = content,
    )
}

/**
 * Lays out [list] with an A–Z strip on its right when [items] is long enough to
 * need one. [items] must already be sorted by [SortKeys.of] of [label], and
 * [list] must use [state] and show one row per item, in order.
 */
@Composable
fun <T> AlphabetIndexed(
    items: List<T>,
    label: (T) -> String,
    state: LazyListState,
    modifier: Modifier = Modifier,
    list: @Composable (Modifier) -> Unit,
) {
    if (items.size < MIN_ITEMS_FOR_INDEX) {
        list(modifier.fillMaxSize())
        return
    }
    val firstIndexOf = remember(items) {
        LinkedHashMap<String, Int>().apply {
            items.forEachIndexed { index, item -> putIfAbsent(SortKeys.letterOf(label(item)), index) }
        }
    }
    Row(modifier = modifier.fillMaxSize()) {
        list(Modifier.weight(1f))
        AlphabetStrip(firstIndexOf, state)
    }
}

/** Below this many rows a list fits in a few pages and the strip is just noise. */
private const val MIN_ITEMS_FOR_INDEX = 30

/**
 * Letters present in a list; tapping one jumps the list to its first item, and
 * the letter of the top visible row is shown inverted. Jumps never animate, and
 * the highlight only redraws when the letter changes, sparing the E-ink panel.
 */
@Composable
private fun AlphabetStrip(firstIndexOf: Map<String, Int>, state: LazyListState) {
    val scope = rememberCoroutineScope()
    val current by remember(firstIndexOf) {
        derivedStateOf {
            val top = state.firstVisibleItemIndex
            firstIndexOf.entries.lastOrNull { it.value <= top }?.key
        }
    }
    Column(
        modifier = Modifier
            .width(28.dp)
            .fillMaxHeight(),
    ) {
        firstIndexOf.forEach { (letter, index) ->
            val selected = letter == current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                    .clickable { scope.launch { state.scrollToItem(index) } },
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

/**
 * Sort keys that file names by how they sound in Latin letters: 周杰伦 sorts as
 * "zhou jie lun" (under Z) and テレサ as "teresa", so mixed Chinese, Japanese,
 * and English libraries read as one A–Z list. A leading "The" and leading
 * punctuation are ignored.
 */
object SortKeys {
    private val transliterator by lazy { Transliterator.getInstance("Any-Latin; Latin-ASCII; Lower") }
    private val cache = ConcurrentHashMap<String, String>()

    fun of(text: String): String = cache.getOrPut(text) {
        val trimmed = text.trim().removePrefix("The ").removePrefix("the ")
        synchronized(transliterator) { transliterator.transliterate(trimmed) }
            .trimStart { !it.isLetterOrDigit() }
            .trim()
    }

    /** "A".."Z", or "#" for digits and symbols. */
    fun letterOf(text: String): String {
        val first = of(text).firstOrNull() ?: return "#"
        return if (first in 'a'..'z') first.uppercase() else "#"
    }
}

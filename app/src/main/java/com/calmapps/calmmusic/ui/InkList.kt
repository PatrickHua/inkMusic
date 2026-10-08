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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.lazy.LazyDefaultsMMD
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * How lists respond to a swipe. Both avoid smooth scrolling, whose in-between
 * frames smear an E-ink panel:
 *  - [ByRow]: the list follows the finger one whole row at a time, like a
 *    terminal printing lines; lifting the finger stops it (no momentum).
 *  - [ByPage]: each swipe jumps [LazyDefaultsMMD.SCROLL_STEP] rows at once,
 *    the way Mudita's LazyColumnMMD scrolls.
 */
enum class InkScroll { ByRow, ByPage }

/** The scrolling style for every list in the app. */
val InkScrollMode = InkScroll.ByRow

/**
 * The app's vertical list: no scrollbar (long lists get [AlphabetIndexed]),
 * normal scrolling off, and swipes handled per [InkScrollMode].
 */
@Composable
fun InkLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = modifier.pointerInput(state) {
            when (InkScrollMode) {
                InkScroll.ByRow -> scrollByRow(state) { target -> scope.launch { state.scrollToItem(target) } }
                InkScroll.ByPage -> scrollByPage(state) { target -> scope.launch { state.scrollToItem(target) } }
            }
        },
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        userScrollEnabled = false,
        content = content,
    )
}

/** Moves one row each time the finger travels the height of the top row. */
private suspend fun PointerInputScope.scrollByRow(state: LazyListState, jumpTo: (Int) -> Unit) {
    var travelled = 0f
    var target = 0
    detectVerticalDragGestures(
        onDragStart = {
            travelled = 0f
            target = state.firstVisibleItemIndex
        },
    ) { _, dragAmount ->
        val info = state.layoutInfo
        val rowHeight = info.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: return@detectVerticalDragGestures
        // The last rows can't reach the top; stop where the list end is in view.
        val lastTop = (info.totalItemsCount - info.visibleItemsInfo.size + 1).coerceAtLeast(0)
        // Dragging up (negative) moves forward through the list.
        travelled -= dragAmount
        while (kotlin.math.abs(travelled) >= rowHeight) {
            val step = if (travelled > 0) 1 else -1
            travelled -= step * rowHeight
            val next = (target + step).coerceIn(0, lastTop)
            if (next != target) {
                target = next
                jumpTo(target)
            }
        }
    }
}

/** Jumps [LazyDefaultsMMD.SCROLL_STEP] rows once per swipe. */
private suspend fun PointerInputScope.scrollByPage(state: LazyListState, jumpTo: (Int) -> Unit) {
    var jumped = false
    detectVerticalDragGestures(
        onDragEnd = { jumped = false },
        onDragCancel = { jumped = false },
    ) { _, dragAmount ->
        if (jumped) return@detectVerticalDragGestures
        jumped = true
        val total = state.layoutInfo.totalItemsCount
        if (total == 0) return@detectVerticalDragGestures
        val direction = if (dragAmount > 0) -1 else 1
        jumpTo((state.firstVisibleItemIndex + direction * LazyDefaultsMMD.SCROLL_STEP).coerceIn(0, total - 1))
    }
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

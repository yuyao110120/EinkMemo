package com.einkmemo.app

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MainScreen(store: MemoStore) {
    Column(Modifier.fillMaxSize().background(InkWhite)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                // 行高保持在可读范围（≥56dp），行数随分辨率/横竖屏自适应（3–10 行/页）
                val desiredRows = (maxHeight.value / 56f).toInt().coerceIn(3, PAGE_SIZE)
                LaunchedEffect(desiredRows) { store.updateRowsPerPage(desiredRows) }
                val rows = store.rowsPerPage
                val rowHeight = maxHeight / rows.toFloat()
                val rowPx = with(density) { rowHeight.toPx() }
                val fontPx = rowPx * 0.40f
                val fontSp = with(density) {
                    (fontPx / (density.density * density.fontScale)).sp
                }
                val all = store.activeItems
                val start = store.page * rows
                val pageItems = all.drop(start).take(rows)
                Column(Modifier.fillMaxSize()) {
                    pageItems.forEachIndexed { index, item ->
                        NoteRow(
                            item = item,
                            rowHeight = rowHeight,
                            rowPx = rowPx,
                            fontSp = fontSp,
                            showDivider = index < pageItems.lastIndex,
                            selected = store.swapId == item.id,
                            onTap = {
                                store.dismissHint()
                                store.tapRow(item.id)
                            },
                            onIconTap = {
                                store.dismissHint()
                                store.tapDoodle(item.id)
                            },
                            onSwipeRight = {
                                store.dismissHint()
                                store.toggleDone(item.id)
                            },
                            onSwipeLeft = {
                                store.dismissHint()
                                store.openEdit(item.id)
                            },
                            onVerticalSwipe = { dy ->
                                store.dismissHint()
                                store.turnPage(if (dy < 0f) 1 else -1)
                            },
                        )
                    }
                }
            }
        }
        if (store.failVisible) {
            FailLine {
                store.dismissHint()
                store.screen = Screen.Settings
            }
        }
        if (store.editing != null) InputBar(store) else BottomBar(store, 40.dp)
    }
}

@Composable
private fun NoteRow(
    item: NoteItem,
    rowHeight: Dp,
    rowPx: Float,
    fontSp: TextUnit,
    showDivider: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onIconTap: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    onVerticalSwipe: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val typeface = LocalAppTypeface.current
    val paint = remember(fontSp, typeface) {
        Paint().apply {
            this.typeface = typeface
            textSize = with(density) { fontSp.toPx() }
            isAntiAlias = true
        }
    }
    val inkBounds = remember(paint) {
        Rect().also { paint.getTextBounds("国", 0, 1, it) }
    }
    var opticalOffset by remember(fontSp, rowPx) { mutableStateOf(0f) }
    val iconScale = maxOf(1, (rowPx * 0.5f / 46f).roundToInt())
    val iconSizeDp = with(density) { (46f * iconScale / density.density).dp }
    // SVG 内左/右留白（3/46、2/46）；让「图标到屏幕」与「图标到文字」的视觉间距相同
    val visualGap = 12.dp
    val iconStartDp = (visualGap - iconSizeDp * (3f / 46f)).coerceAtLeast(0.dp)
    val textGapDp = (visualGap - iconSizeDp * (2f / 46f)).coerceAtLeast(0.dp)
    val textStart = iconStartDp + iconSizeDp + textGapDp

    val iconHitPx = with(density) { (iconStartDp + iconSizeDp).toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (selected) InkBlack else InkWhite)
            .pointerInput(item.id, selected, iconHitPx) {
                detectRowGestures(
                    onTap = { pos ->
                        if (pos.x <= iconHitPx) onIconTap() else onTap()
                    },
                    onSwipeRight = { if (!selected) onSwipeRight() },
                    onSwipeLeft = { if (!selected) onSwipeLeft() },
                    onVerticalSwipe = { dy -> if (!selected) onVerticalSwipe(dy) },
                )
            }
    ) {
        NoteDoodle(
            color = when {
                selected -> InkWhite
                item.done -> InkGray
                else -> InkBlack
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = iconStartDp)
                .size(iconSizeDp),
        )
        InkText(
            text = item.text,
            fontSize = fontSp,
            lineHeight = fontSp,
            color = when {
                selected -> InkWhite
                item.done -> InkGray
                else -> InkBlack
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            onTextLayout = { layout ->
                val baseline = layout.firstBaseline
                val layoutTop = (rowPx - layout.size.height) / 2f
                val inkCenter = layoutTop + baseline + ((inkBounds.top + inkBounds.bottom) / 2f)
                val offset = rowPx / 2f - inkCenter
                if (abs(offset - opticalOffset) > 0.5f) opticalOffset = offset
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(start = textStart, end = 22.dp)
                .offset { IntOffset(0, opticalOffset.roundToInt()) },
        )
        if (showDivider) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(InkBlack)
            )
        }
    }
}

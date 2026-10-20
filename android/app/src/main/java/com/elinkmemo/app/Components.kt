package com.elinkmemo.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Typeface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

val InkBlack = Color(0xFF000000)
val InkWhite = Color(0xFFFFFFFF)
val InkGray = Color(0xFF808080)

val PixelFontFamily: FontFamily = FontFamily(Font(R.font.fusion_pixel_12px_proportional))
val LocalAppFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }
val LocalAppTypeface = staticCompositionLocalOf<Typeface> { Typeface.create("sans-serif", Typeface.NORMAL) }

@Composable
fun systemStatusBarHeight(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val px = if (id > 0) {
            context.resources.getDimensionPixelSize(id)
        } else {
            with(density) { 24.dp.toPx().toInt() }
        }
        with(density) { px.toDp() }
    }
}

fun fmtTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

fun dayKey(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

fun fmtDay(key: String): String {
    val date = LocalDate.parse(key)
    return date.monthValue.toString() + "月" + date.dayOfMonth.toString() + "日"
}

@Composable
fun InkText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = InkBlack,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textDecoration: TextDecoration? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            textDecoration = textDecoration,
            fontFamily = LocalAppFontFamily.current,
        ),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = onTextLayout,
    )
}

@Composable
fun RowSeparator() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(InkBlack)
    )
}

@Composable
fun ClickRow(
    height: Dp = 64.dp,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height - 1.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        RowSeparator()
    }
}

@Composable
fun NavRow(onClick: () -> Unit) {
    ClickRow(onClick = onClick) {
        InkText("← 返回", 20.sp, Modifier.padding(horizontal = 22.dp))
    }
}

@Composable
fun MenuRow(title: String, chevron: Boolean = false, onClick: () -> Unit) {
    ClickRow(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkText(title, 22.sp)
            if (chevron) InkText("›", 22.sp)
        }
    }
}

@Composable
fun MenuRowTall(title: String, sub: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(63.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            InkText(title, 22.sp)
            InkText(sub, 13.sp)
        }
        RowSeparator()
    }
}

@Composable
fun ButtonRow(title: String, sub: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(63.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            InkText(title, 20.sp)
            if (sub != null) InkText(sub, 13.sp)
        }
        RowSeparator()
    }
}

@Composable
fun FieldRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(63.dp)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkText(label, 16.sp, Modifier.width(72.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .border(1.dp, InkBlack),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    InkText(
                        placeholder,
                        17.sp,
                        color = InkGray,
                        modifier = Modifier.padding(horizontal = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 17.sp,
                        color = InkBlack,
                        fontFamily = LocalAppFontFamily.current,
                    ),
                    cursorBrush = SolidColor(InkBlack),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                )
            }
        }
        RowSeparator()
    }
}

@Composable
fun GroupHeader(title: String) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(35.dp)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            InkText(title, 14.sp)
        }
        RowSeparator()
    }
}

@Composable
fun PlusIcon(iconSize: Dp = 28.dp) {
    Canvas(Modifier.size(iconSize)) {
        val extent = size.width
        val arm = extent * 0.14f
        val pad = extent * 0.16f
        val cx = extent / 2f
        val cy = size.height / 2f
        drawRect(InkBlack, Offset(cx - arm / 2f, pad), Size(arm, size.height - pad * 2f))
        drawRect(InkBlack, Offset(pad, cy - arm / 2f), Size(extent - pad * 2f, arm))
    }
}

@Composable
fun MoreIcon(iconSize: Dp = 28.dp) {
    Canvas(Modifier.size(iconSize)) {
        val extent = size.width
        val arm = extent * 0.14f
        val pad = extent * 0.16f
        val top = (size.height - arm) / 2f
        val xs = floatArrayOf(pad, (extent - arm) / 2f, extent - pad - arm)
        for (x in xs) drawRect(InkBlack, Offset(x, top), Size(arm, arm))
    }
}

@Composable
fun BottomBar(store: MemoStore, barHeight: Dp) {
    val moreInteraction = remember { MutableInteractionSource() }
    val addInteraction = remember { MutableInteractionSource() }
    val buttonWidth = maxOf(barHeight, 32.dp)
    val iconSize = (barHeight * 0.6f).coerceIn(12.dp, 26.dp)
    Column {
        RowSeparator()
        Box(Modifier.fillMaxWidth().height(barHeight - 1.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(buttonWidth)
                    .fillMaxHeight()
                    .clickable(interactionSource = moreInteraction, indication = null) {
                        store.dismissHint()
                        store.editing = null
                        store.clearSwapSelection()
                        store.screen = Screen.Settings
                    },
                contentAlignment = Alignment.Center,
            ) { MoreIcon(iconSize) }
            if (store.swapId != null) {
                InkText(
                    "点另一条交换",
                    (barHeight.value * 0.45f).coerceIn(9f, 13f).sp,
                    Modifier.align(Alignment.Center),
                )
            } else if (store.pageCount > 1) {
                InkText(
                    (store.page + 1).toString() + "/" + store.pageCount,
                    (barHeight.value * 0.45f).coerceIn(9f, 13f).sp,
                    Modifier.align(Alignment.Center),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(buttonWidth)
                    .fillMaxHeight()
                    .clickable(interactionSource = addInteraction, indication = null) {
                        store.dismissHint()
                        store.openAdd()
                    },
                contentAlignment = Alignment.Center,
            ) { PlusIcon(iconSize) }
        }
    }
}

@Composable
fun FailLine(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column {
        RowSeparator()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(27.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            InkText("同步失败 · 点此查看", 13.sp)
        }
    }
}

@Composable
fun InputBar(store: MemoStore) {
    val editing = store.editing ?: return
    val density = LocalDensity.current
    val initial = if (editing.mode == EditMode.Edit) {
        store.items.firstOrNull { it.id == editing.id }?.text ?: ""
    } else ""
    var text by remember(editing) { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val cancelInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(editing) { focusRequester.requestFocus() }

    fun submit() {
        val mode = editing.mode
        store.commitText(text)
        text = ""
        if (mode == EditMode.Add && store.editing != null) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    Column {
        RowSeparator()
        Row(
            modifier = Modifier.fillMaxWidth().height(43.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(interactionSource = cancelInteraction, indication = null) {
                        store.cancelEdit()
                    },
                contentAlignment = Alignment.Center,
            ) {
                InkText("×", 26.sp)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    InkText(
                        if (editing.mode == EditMode.Add) "添加一条…" else "修改…",
                        20.sp,
                        color = InkGray,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        color = InkBlack,
                        lineHeight = 20.sp,
                        fontFamily = LocalAppFontFamily.current,
                    ),
                    cursorBrush = SolidColor(InkBlack),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                            if (event.type == KeyEventType.KeyDown && enter) {
                                submit()
                                true
                            } else false
                        },
                )
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
fun HintOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .background(InkWhite)
                .border(1.dp, InkBlack)
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InkText("右滑划掉", 20.sp)
            InkText("点击编辑", 20.sp)
            InkText("点小图选中 · 再点一条换位", 20.sp)
            InkText("上下滑或翻页键翻页", 20.sp)
        }
    }
}

@Composable
fun ConflictOverlay(store: MemoStore) {
    Column(Modifier.fillMaxSize().background(InkWhite)) {
        Spacer(Modifier.height(72.dp))
        InkText("同步冲突", 28.sp, Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(12.dp))
        InkText("远端和本机都有修改，选择保留哪一份：", 16.sp, Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.weight(1f))
        ButtonRow("保留本机（覆盖远端）") { store.resolveConflictKeepLocal() }
        ButtonRow("保留远端（覆盖本机）") { store.resolveConflictKeepRemote() }
        ButtonRow("取消") { store.cancelConflict() }
    }
}

suspend fun PointerInputScope.detectRowGestures(
    onTap: (Offset) -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    onVerticalSwipe: (Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dx = 0f
        var dy = 0f
        var released = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.pressed) {
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                change.consume()
            } else {
                change.consume()
                released = true
                break
            }
        }
        if (!released) return@awaitEachGesture
        val tapSlop = 10.dp.toPx()
        val horizontalSlop = 36.dp.toPx()
        val verticalSlop = 48.dp.toPx()
        val horizontal = abs(dx) > abs(dy) * 1.4f
        val vertical = abs(dy) > abs(dx) * 1.4f
        when {
            abs(dx) < tapSlop && abs(dy) < tapSlop -> onTap(down.position)
            horizontal && abs(dx) > horizontalSlop -> if (dx > 0) onSwipeRight() else onSwipeLeft()
            vertical && abs(dy) > verticalSlop -> onVerticalSwipe(dy)
        }
    }
}

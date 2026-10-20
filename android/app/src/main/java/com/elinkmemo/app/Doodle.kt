package com.elinkmemo.app

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser

private const val DOODLE_VIEWPORT = 46f

// makebead-46x46.svg：46×46 像素画（1-bit，边缘对齐整数像素）
private const val DOODLE_PATH_DATA =
    "M24 9h10v1h-10zM22 10h13v1h-13zM22 11h3v1h-3zM26 11h9v1h-9zM22 12h13v4h-13z" +
    "M22 16h7v1h-7zM22 17h11v2h-11zM10 19h1v2h-1zM21 19h6v1h-6zM19 20h8v1h-8z" +
    "M10 21h2v1h-2zM17 21h13v1h-13zM10 22h3v2h-3zM16 22h11v2h-11zM29 22h1v2h-1z" +
    "M10 24h17v2h-17zM11 26h16v1h-16zM11 27h15v1h-15zM12 28h14v1h-14zM13 29h12v1h-12z" +
    "M15 30h9v1h-9zM3 31h10v1h-10zM16 31h4v2h-4zM21 31h3v2h-3zM26 31h18v1h-18z" +
    "M16 33h3v1h-3zM22 33h2v2h-2zM38 33h2v1h-2zM7 34h3v1h-3zM16 34h1v1h-1z" +
    "M16 35h3v2h-3zM22 35h3v2h-3zM29 35h1v1h-1zM3 36h3v1h-3zM12 36h1v1h-1zM34 36h2v1h-2z"

@Composable
fun NoteDoodle(color: Color, modifier: Modifier = Modifier) {
    val paint = remember {
        Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
        }
    }
    val path: Path = remember { PathParser().parsePathString(DOODLE_PATH_DATA).toPath() }
    Canvas(modifier) {
        val scale = size.width / DOODLE_VIEWPORT
        val native = drawContext.canvas.nativeCanvas
        paint.color = color.toArgb()
        native.save()
        native.scale(scale, scale)
        native.drawPath(path.asAndroidPath(), paint)
        native.restore()
    }
}

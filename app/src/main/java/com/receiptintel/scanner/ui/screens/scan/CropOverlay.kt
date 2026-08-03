package com.receiptintel.scanner.ui.screens.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val HANDLE_TOUCH_RADIUS = 60f

/**
 * Draws a four-corner draggable crop rectangle over the receipt preview.
 * [onRectChanged] reports the rectangle in the overlay's own pixel space;
 * the caller is responsible for mapping that to bitmap coordinates when the
 * user confirms, since the overlay doesn't know the underlying bitmap's
 * intrinsic size vs. its displayed size.
 */
@Composable
fun CropOverlay(
    modifier: Modifier = Modifier,
    initialRect: Rect,
    onRectChanged: (Rect) -> Unit
) {
    var rect by remember { mutableStateOf(initialRect) }
    var activeHandle by remember { mutableStateOf(-1) } // 0=TL,1=TR,2=BR,3=BL, -1=none/whole rect

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        activeHandle = nearestHandle(rect, offset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        rect = when (activeHandle) {
                            0 -> rect.copy(left = rect.left + dragAmount.x, top = rect.top + dragAmount.y)
                            1 -> rect.copy(right = rect.right + dragAmount.x, top = rect.top + dragAmount.y)
                            2 -> rect.copy(right = rect.right + dragAmount.x, bottom = rect.bottom + dragAmount.y)
                            3 -> rect.copy(left = rect.left + dragAmount.x, bottom = rect.bottom + dragAmount.y)
                            else -> rect.translate(dragAmount)
                        }
                        onRectChanged(rect)
                    }
                )
            }
    ) {
        // Dim everything outside the crop rect.
        drawRect(color = Color.Black.copy(alpha = 0.5f))
        drawRect(
            color = Color.Transparent,
            topLeft = rect.topLeft,
            size = Size(rect.width, rect.height),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )
        drawRect(
            color = Color(0xFF5FCBB4),
            topLeft = rect.topLeft,
            size = Size(rect.width, rect.height),
            style = Stroke(width = 4f)
        )
        val corners = listOf(rect.topLeft, Offset(rect.right, rect.top), rect.bottomRight, Offset(rect.left, rect.bottom))
        corners.forEach { corner ->
            drawCircle(color = Color(0xFF5FCBB4), radius = 16f, center = corner)
            drawCircle(color = Color.White, radius = 8f, center = corner)
        }
    }
}

private fun nearestHandle(rect: Rect, point: Offset): Int {
    val corners = listOf(
        rect.topLeft,
        Offset(rect.right, rect.top),
        rect.bottomRight,
        Offset(rect.left, rect.bottom)
    )
    corners.forEachIndexed { index, corner ->
        if (abs(corner.x - point.x) < HANDLE_TOUCH_RADIUS && abs(corner.y - point.y) < HANDLE_TOUCH_RADIUS) {
            return index
        }
    }
    return -1 // drag whole rect
}

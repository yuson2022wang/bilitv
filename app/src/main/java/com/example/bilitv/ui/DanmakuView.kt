package com.example.bilitv.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.bilitv.data.Danmaku

private const val LANES = 9
private const val DURATION_MS = 8000L

private class Active(val text: String, val color: Int, val startMs: Long, val lane: Int, val width: Float)

private class Engine(private val list: List<Danmaku>) {
    private var idx = 0
    private var lastPos = 0L
    private val active = ArrayList<Active>()
    private val laneFree = LongArray(LANES)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xCC000000.toInt()
    }

    private fun seekTo(pos: Long) {
        active.clear()
        laneFree.fill(0)
        var lo = 0
        var hi = list.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].timeMs < pos) lo = mid + 1 else hi = mid
        }
        idx = lo
    }

    fun draw(canvas: android.graphics.Canvas, pos: Long, w: Float, h: Float, textSize: Float, alpha: Int) {
        if (pos < lastPos || pos - lastPos > 3000) seekTo(pos)
        lastPos = pos
        paint.textSize = textSize
        outline.textSize = textSize
        val laneH = textSize * 1.35f
        val lanes = minOf(LANES, (h * 0.6f / laneH).toInt().coerceAtLeast(1))

        while (idx < list.size && list[idx].timeMs <= pos) {
            val d = list[idx++]
            if (pos - d.timeMs > 1000) continue
            val width = paint.measureText(d.text)
            val lane = (0 until lanes).firstOrNull { laneFree[it] <= pos } ?: continue
            // 释放该行：弹幕尾部完全进入屏幕后，后一条才可进入
            laneFree[lane] = pos + (width / (w + width) * DURATION_MS).toLong() + 400
            active.add(Active(d.text, d.color or 0xFF000000.toInt(), pos, lane, width))
        }

        val it = active.iterator()
        while (it.hasNext()) {
            val a = it.next()
            val progress = (pos - a.startMs).toFloat() / DURATION_MS
            if (progress >= 1f) { it.remove(); continue }
            val x = w - progress * (w + a.width)
            val y = laneH * (a.lane + 1)
            outline.alpha = alpha
            canvas.drawText(a.text, x, y, outline)
            paint.color = a.color
            paint.alpha = alpha
            canvas.drawText(a.text, x, y, paint)
        }
    }
}

/** 简易滚动弹幕层。positionMs 每帧读取，playing 为 false 时不推进（保持画面）。 */
@Composable
fun DanmakuLayer(
    danmaku: List<Danmaku>,
    positionMs: () -> Long,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val engine = remember(danmaku) { Engine(danmaku) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, engine) {
        if (!playing) return@LaunchedEffect
        while (true) {
            androidx.compose.runtime.withFrameNanos { frame = it }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") frame // 读取以便每帧重绘
        drawIntoCanvas {
            engine.draw(it.nativeCanvas, positionMs(), size.width, size.height, size.height / 22f, 235)
        }
    }
}

package com.example.bilitv.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bilitv.data.VideoItem
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Error(val msg: String) : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
}

/** 通用加载容器：加载中 / 错误重试 / 内容。 */
@Composable
fun <T> LoadState(key: Any?, load: suspend () -> T, content: @Composable (T) -> Unit) {
    var state by remember(key) { mutableStateOf<UiState<T>>(UiState.Loading) }
    var tick by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, tick) {
        state = UiState.Loading
        state = try {
            UiState.Success(load())
        } catch (e: Exception) {
            UiState.Error(e.message?.takeIf { it.isNotBlank() } ?: "加载失败")
        }
    }
    when (val s = state) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = GlassColors.Accent)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.msg, color = GlassColors.TextSecondary, fontSize = 18.sp)
                Box(Modifier.padding(top = 16.dp)) { GlassButton("重试", { tick++ }) }
            }
        }
        is UiState.Success -> content(s.data)
    }
}

private fun thumb(url: String, w: Int, h: Int): String =
    if (url.contains("hdslb.com") && !url.contains("@")) "$url@${w}w_${h}h_1c.webp" else url

@Composable
fun VideoCard(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassClickable(onClick, modifier.fillMaxWidth()) { focused ->
        Column(Modifier.padding(10.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
            ) {
                AsyncImage(
                    model = thumb(item.cover, 480, 270), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
                if (item.duration.isNotEmpty()) {
                    Text(
                        item.duration, color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (item.progressSec > 0) {
                    Text(
                        "已看", color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassColors.Blue.copy(0.8f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                item.title, color = if (focused) Color.White else GlassColors.TextPrimary,
                fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
            )
            Row(Modifier.padding(top = 4.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.author, color = GlassColors.TextSecondary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.play.isNotEmpty()) {
                    Text(
                        "  ▶ ${item.play}", color = GlassColors.TextSecondary, fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun VideoGrid(
    items: List<VideoItem>,
    onOpen: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    onEnd: (() -> Unit)? = null,
) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize(), Alignment.Center) {
            Text("这里还没有内容", color = GlassColors.TextSecondary, fontSize = 18.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(items, key = { index, it -> "${it.bvid}-$index" }) { index, item ->
            VideoCard(item, { onOpen(item) })
            if (onEnd != null && index == items.lastIndex) LaunchedEffect(items.size) { onEnd() }
        }
    }
}

@Composable
fun Avatar(url: String, size: Int = 48, modifier: Modifier = Modifier) {
    AsyncImage(
        model = if (url.contains("hdslb.com") && !url.contains("@")) "$url@${size * 2}w_${size * 2}h.webp" else url,
        contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.size(size.dp).clip(CircleShape)
    )
}

fun qrBitmap(content: String, px: Int = 560): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
    val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px, hints)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    for (x in 0 until px) for (y in 0 until px) {
        bmp.setPixel(x, y, if (m[x, y]) AColor.BLACK else AColor.WHITE)
    }
    return bmp
}

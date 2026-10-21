package com.example.bilitv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.bilitv.data.BiliApi
import com.example.bilitv.data.VideoDetail
import com.example.bilitv.data.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    bvid: String,
    onPlay: (Route.Player) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenUp: (Long, String) -> Unit,
    onLogin: () -> Unit,
) {
    LoadState(key = bvid, load = { BiliApi.detail(bvid) }) { d ->
        DetailContent(d, onPlay, onOpenVideo, onOpenUp, onLogin)
    }
}

@Composable
private fun DetailContent(
    d: VideoDetail,
    onPlay: (Route.Player) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenUp: (Long, String) -> Unit,
    onLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playFocus = remember { FocusRequester() }
    var part by remember { mutableIntStateOf(0) }
    var rightTab by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    fun action(label: String, block: suspend () -> Unit) {
        if (AppState.user == null) { onLogin(); return }
        scope.launch {
            toast = try { block(); "$label 成功" } catch (e: Exception) { e.message ?: "$label 失败" }
        }
    }

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // 左：信息与操作
        Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassPanel(Modifier.fillMaxWidth().aspectRatio(16f / 7f)) {
                AsyncImage(
                    model = d.cover, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                d.title, color = GlassColors.TextPrimary, fontSize = 24.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassClickable({ onOpenUp(d.ownerMid, d.ownerName) }, shape = RoundedCornerShape(50)) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Avatar(d.ownerFace, 32)
                        Text(d.ownerName, color = GlassColors.TextPrimary, fontSize = 16.sp)
                    }
                }
                GlassButton("＋ 关注", { action("关注") { BiliApi.follow(d.ownerMid) } }, fontSize = 16)
            }
            Text(
                "▶ ${d.view}   弹幕 ${d.danmaku}   👍 ${d.like}   🪙 ${d.coin}   ⭐ ${d.favorite}",
                color = GlassColors.TextSecondary, fontSize = 15.sp
            )
            Text(
                d.desc, color = GlassColors.TextSecondary, fontSize = 14.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassButton("▶ 播放", {
                    val cid = d.pages.getOrNull(part)?.first ?: d.cid
                    onPlay(Route.Player(d.bvid, d.aid, cid, d.title))
                }, Modifier.focusRequester(playFocus))
                GlassButton("👍 点赞", { action("点赞") { BiliApi.like(d.aid) } })
                GlassButton("🪙 投币", { action("投币") { BiliApi.coin(d.aid) } })
                GlassButton("⭐ 收藏", {
                    action("收藏") {
                        val folder = BiliApi.favFolders(AppState.user!!.mid).firstOrNull()
                            ?: throw Exception("没有可用的收藏夹")
                        BiliApi.addFavorite(d.aid, folder.id)
                    }
                })
            }
            if (d.pages.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(d.pages.size) { i ->
                        TabChip("P${i + 1} ${d.pages[i].second}".take(18), part == i) { part = i }
                    }
                }
            }
            toast?.let {
                Text(it, color = GlassColors.Accent, fontSize = 16.sp)
            }
        }

        // 右：相关推荐 / 评论
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                TabChip("相关推荐", rightTab == 0) { rightTab = 0 }
                TabChip("评论", rightTab == 1) { rightTab = 1 }
            }
            if (rightTab == 0) {
                LoadState(key = "rel-${d.bvid}", load = { BiliApi.related(d.bvid) }) { list ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
                        items(list.size) { i -> RelatedRow(list[i]) { onOpenVideo(list[i].bvid) } }
                    }
                }
            } else {
                LoadState(key = "cm-${d.aid}", load = { BiliApi.comments(d.aid) }) { list ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                        items(list.size) { i ->
                            val c = list[i]
                            GlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
                                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Avatar(c.face, 36)
                                    Column {
                                        Text(c.user, color = GlassColors.Accent, fontSize = 13.sp)
                                        Text(
                                            c.text, color = GlassColors.TextPrimary, fontSize = 14.sp,
                                            maxLines = 4, overflow = TextOverflow.Ellipsis
                                        )
                                        Text("👍 ${c.like}", color = GlassColors.TextSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedRow(v: VideoItem, onClick: () -> Unit) {
    GlassClickable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), 1.04f) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = v.cover.let { if (it.contains("hdslb.com") && !it.contains("@")) "$it@320w_180h_1c.webp" else it },
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
            )
            Column(Modifier.weight(1f)) {
                Text(
                    v.title, color = GlassColors.TextPrimary, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${v.author}  ▶ ${v.play}", color = GlassColors.TextSecondary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

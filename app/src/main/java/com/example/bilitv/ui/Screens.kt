package com.example.bilitv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilitv.data.BiliApi
import com.example.bilitv.data.Session
import com.example.bilitv.data.VideoItem
import kotlinx.coroutines.launch

@Composable
fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    GlassClickable(onClick, shape = RoundedCornerShape(50), focusScale = 1.08f) { focused ->
        Text(
            text,
            color = if (selected || focused) Color.White else GlassColors.TextSecondary,
            fontSize = 18.sp,
            modifier = Modifier
                .background(if (selected && !focused) GlassColors.Blue.copy(0.55f) else Color.Transparent)
                .padding(horizontal = 24.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun LoginPrompt(message: String, onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = GlassColors.TextSecondary, fontSize = 20.sp)
            Box(Modifier.padding(top = 20.dp)) { GlassButton("扫码登录", onLogin) }
        }
    }
}

// ---------------- 首页 ----------------

@Composable
fun HomeScreen(onOpen: (VideoItem) -> Unit) {
    var sub by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 20.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf("推荐", "热门", "排行").forEachIndexed { i, t ->
                TabChip(t, sub == i) { if (sub == i) refresh++ else sub = i }
            }
        }
        LoadState(key = sub to refresh, load = {
            when (sub) {
                0 -> BiliApi.recommend()
                1 -> BiliApi.popular()
                else -> BiliApi.rank()
            }
        }) { VideoGrid(it, onOpen) }
    }
}

// ---------------- 搜索 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onOpen: (VideoItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    val doSearch = { if (query.isNotBlank()) submitted = query.trim() }

    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPanel(Modifier.weight(1f), RoundedCornerShape(50)) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = GlassColors.TextPrimary, fontSize = 20.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("点击输入关键词…", color = GlassColors.TextSecondary, fontSize = 20.sp)
                            }
                            inner()
                        }
                    }
                )
            }
            GlassButton("搜索", { doSearch() })
        }

        if (submitted.isEmpty()) {
            LoadState(key = "hot", load = { BiliApi.hotSearch() }) { words ->
                Column(Modifier.padding(20.dp)) {
                    SectionTitle("热搜")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        words.forEach { w ->
                            GlassButton(w, { query = w; submitted = w }, fontSize = 16)
                        }
                    }
                }
            }
        } else {
            LoadState(key = submitted, load = { BiliApi.search(submitted) }) { VideoGrid(it, onOpen) }
        }
    }
}

// ---------------- 动态 ----------------

@Composable
fun DynamicScreen(onOpen: (VideoItem) -> Unit, onLogin: () -> Unit) {
    if (AppState.user == null) {
        LoginPrompt("登录后查看关注的 UP 主的最新视频", onLogin)
    } else {
        LoadState(key = AppState.user?.mid, load = { BiliApi.dynamicVideos() }) {
            VideoGrid(it, onOpen)
        }
    }
}

// ---------------- 我的 ----------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeScreen(
    onOpen: (VideoItem) -> Unit,
    onOpenUp: (Long, String) -> Unit,
    onLogin: () -> Unit,
) {
    val user = AppState.user
    if (user == null) {
        LoginPrompt("登录后查看历史记录、收藏夹和关注", onLogin)
        return
    }
    var sub by remember { mutableIntStateOf(0) }
    var folder by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Avatar(user.face, 56)
            Column(Modifier.weight(1f)) {
                Text(user.name, color = GlassColors.TextPrimary, fontSize = 22.sp)
                Text(
                    "LV${user.level}  ·  硬币 ${"%.1f".format(user.coins)}",
                    color = GlassColors.TextSecondary, fontSize = 14.sp
                )
            }
            listOf("历史记录", "收藏夹", "关注").forEachIndexed { i, t ->
                TabChip(t, sub == i) { sub = i }
            }
            GlassButton("退出登录", {
                scope.launch { Session.logout(); AppState.user = null }
            }, fontSize = 16)
        }

        when (sub) {
            0 -> LoadState(key = "history", load = { BiliApi.history() }) { VideoGrid(it, onOpen) }
            1 -> LoadState(key = "folders", load = { BiliApi.favFolders(user.mid) }) { folders ->
                if (folders.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("没有收藏夹", color = GlassColors.TextSecondary, fontSize = 18.sp)
                    }
                } else {
                    val current = if (folder == 0L) folders.first().id else folder
                    Column(Modifier.fillMaxSize()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(folders, key = { it.id }) { f ->
                                TabChip("${f.title} (${f.count})", current == f.id) { folder = f.id }
                            }
                        }
                        LoadState(key = current, load = { BiliApi.favItems(current) }) {
                            VideoGrid(it, onOpen)
                        }
                    }
                }
            }
            else -> LoadState(key = "following", load = { BiliApi.followings(user.mid) }) { ups ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(ups, key = { it.mid }) { up ->
                        GlassClickable({ onOpenUp(up.mid, up.name) }) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Avatar(up.face, 52)
                                Column {
                                    Text(
                                        up.name, color = GlassColors.TextPrimary, fontSize = 16.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        up.sign, color = GlassColors.TextSecondary, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- UP 主空间 ----------------

@Composable
fun UpScreen(mid: Long, name: String, onOpen: (VideoItem) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            name, color = GlassColors.TextPrimary, fontSize = 26.sp,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp)
        )
        LoadState(key = mid, load = { BiliApi.upVideos(mid) }) { VideoGrid(it, onOpen) }
    }
}

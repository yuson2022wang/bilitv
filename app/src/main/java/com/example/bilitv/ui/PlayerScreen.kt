package com.example.bilitv.ui

import android.view.KeyEvent as AKeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.bilitv.data.BiliApi
import com.example.bilitv.data.DashStream
import com.example.bilitv.data.Danmaku
import com.example.bilitv.data.PlayInfo
import com.example.bilitv.data.Session
import com.example.bilitv.data.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPEEDS = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f)

private fun pickVideo(info: PlayInfo, quality: Int?): DashStream {
    val target = quality ?: info.videos.maxOf { it.quality }
    val sameQ = info.videos.filter { it.quality == target }.ifEmpty { info.videos }
    return sameQ.firstOrNull { it.codec.startsWith("avc") } ?: sameQ.first()
}

@OptIn(UnstableApi::class)
private fun buildSource(video: DashStream, audio: DashStream?): MediaSource {
    val factory = ProgressiveMediaSource.Factory(OkHttpDataSource.Factory(Session.client))
    val v = factory.createMediaSource(MediaItem.fromUri(video.url))
    return if (audio != null) {
        MergingMediaSource(v, factory.createMediaSource(MediaItem.fromUri(audio.url)))
    } else v
}

@Composable
fun PlayerScreen(route: Route.Player, onBack: () -> Unit) {
    LoadState(key = route, load = { BiliApi.playUrl(route.bvid, route.cid) }) { info ->
        PlayerContent(route, info, onBack)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(route: Route.Player, info: PlayInfo, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var quality by remember { mutableStateOf<Int?>(null) }
    var speedIdx by remember { mutableStateOf(1) }
    var danmakuOn by remember { mutableStateOf(true) }
    var danmaku by remember { mutableStateOf<List<Danmaku>>(emptyList()) }

    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controls by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var seekHint by remember { mutableStateOf<String?>(null) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    // 装载 / 切换清晰度：保留当前位置
    LaunchedEffect(quality) {
        val resume = if (player.currentMediaItem != null) player.currentPosition else route.startSec * 1000
        val video = pickVideo(info, quality)
        player.setMediaSource(buildSource(video, info.audio))
        player.prepare()
        if (resume > 0) player.seekTo(resume)
        player.playWhenReady = true
    }
    LaunchedEffect(Unit) { danmaku = BiliApi.danmaku(route.cid) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED) { controls = true; lastTouch = System.currentTimeMillis() }
            }
            override fun onPlayerError(e: PlaybackException) { error = "播放失败：${e.errorCodeName}" }
        }
        player.addListener(l)
        onDispose {
            runCatching { player.removeListener(l) }
        }
    }

    // 生命周期：退到后台暂停；离开时上报进度并释放
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    DisposableEffect(Unit) {
        onDispose {
            val sec = player.currentPosition / 1000
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                BiliApi.heartbeat(route.aid, route.cid, sec)
            }
            player.release()
        }
    }

    // 进度刷新 + 每 15 秒上报一次历史
    LaunchedEffect(player) {
        var tick = 0
        while (true) {
            position = player.currentPosition
            delay(500)
            if (++tick % 30 == 0 && player.isPlaying) {
                BiliApi.heartbeat(route.aid, route.cid, player.currentPosition / 1000)
            }
            if (controls && System.currentTimeMillis() - lastTouch > 4500 && player.isPlaying) controls = false
        }
    }
    LaunchedEffect(seekHint) { if (seekHint != null) { delay(900); seekHint = null } }
    LaunchedEffect(controls) {
        runCatching { if (controls) playFocus.requestFocus() else rootFocus.requestFocus() }
    }

    fun touch() { lastTouch = System.currentTimeMillis() }
    fun seekBy(ms: Long) {
        val to = (player.currentPosition + ms).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(to)
        position = to
        seekHint = (if (ms > 0) "快进 " else "快退 ") + "${kotlin.math.abs(ms) / 1000} 秒  ·  ${formatDuration(to / 1000)}"
    }

    BackHandler(enabled = controls) { controls = false }
    BackHandler(enabled = !controls) { onBack() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { ev ->
                touch()
                if (controls) return@onPreviewKeyEvent false
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.nativeKeyEvent.keyCode) {
                    AKeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-10_000); true }
                    AKeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(10_000); true }
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER,
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        controls = true; true
                    }
                    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN -> { controls = true; true }
                    else -> false
                }
            }
            .focusRequester(rootFocus).focusable()
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (danmakuOn && danmaku.isNotEmpty()) {
            DanmakuLayer(danmaku, { player.currentPosition }, isPlaying)
        }

        if (buffering && error == null) {
            Text(
                "缓冲中…", color = Color.White, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(0.5f)).padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        error?.let {
            Text(
                it, color = Color.White, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(0.6f)).padding(24.dp)
            )
        }
        seekHint?.let {
            Text(
                it, color = Color.White, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(0.55f)).padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }

        if (controls) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f))))
                    .padding(horizontal = 48.dp, vertical = 28.dp)
            ) {
                GlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 18.dp)) {
                        Text(
                            route.title, color = GlassColors.TextPrimary, fontSize = 20.sp,
                            maxLines = 1
                        )
                        Row(
                            Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatDuration(position / 1000), color = GlassColors.TextSecondary,
                                fontSize = 14.sp
                            )
                            Box(
                                Modifier.weight(1f).padding(horizontal = 14.dp).height(6.dp)
                                    .clip(RoundedCornerShape(50)).background(Color.White.copy(0.2f))
                            ) {
                                val f = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                                Box(
                                    Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(50))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(GlassColors.Blue, GlassColors.Pink)
                                            )
                                        )
                                )
                            }
                            Text(
                                formatDuration(duration / 1000), color = GlassColors.TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            GlassButton("⏪ 10秒", { seekBy(-10_000) }, fontSize = 16)
                            GlassButton(
                                if (isPlaying) "⏸ 暂停" else "▶ 播放",
                                { if (player.isPlaying) player.pause() else player.play() },
                                Modifier.focusRequester(playFocus), fontSize = 16
                            )
                            GlassButton("10秒 ⏩", { seekBy(10_000) }, fontSize = 16)
                            val names = info.qualities.toMap()
                            val cur = pickVideo(info, quality).quality
                            GlassButton("清晰度 ${names[cur] ?: cur}", {
                                val ids = info.videos.map { it.quality }.distinct().sortedDescending()
                                quality = ids[(ids.indexOf(cur) + 1) % ids.size]
                            }, fontSize = 16)
                            GlassButton("倍速 ${SPEEDS[speedIdx]}x", {
                                speedIdx = (speedIdx + 1) % SPEEDS.size
                                player.setPlaybackSpeed(SPEEDS[speedIdx])
                            }, fontSize = 16)
                            GlassButton("弹幕 ${if (danmakuOn) "开" else "关"}", {
                                danmakuOn = !danmakuOn
                            }, fontSize = 16)
                        }
                    }
                }
            }
        }
    }
}

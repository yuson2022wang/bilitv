package com.example.bilitv.data

data class VideoItem(
    val bvid: String,
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String,
    val cover: String,
    val author: String = "",
    val play: String = "",
    val duration: String = "",
    val progressSec: Long = 0,
)

data class UserInfo(
    val mid: Long,
    val name: String,
    val face: String,
    val level: Int = 0,
    val coins: Double = 0.0,
)

data class VideoDetail(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val desc: String,
    val cover: String,
    val ownerMid: Long,
    val ownerName: String,
    val ownerFace: String,
    val view: String,
    val danmaku: String,
    val like: String,
    val coin: String,
    val favorite: String,
    val pages: List<Pair<Long, String>>,
)

data class FavFolder(val id: Long, val title: String, val count: Int)

data class Comment(val user: String, val face: String, val text: String, val like: Int)

data class UpUser(val mid: Long, val name: String, val face: String, val sign: String)

data class QrSession(val url: String, val key: String)

sealed class QrState {
    object Waiting : QrState()
    object Scanned : QrState()
    object Expired : QrState()
    object Success : QrState()
}

data class DashStream(val url: String, val backups: List<String>, val quality: Int, val codec: String)

data class PlayInfo(
    val videos: List<DashStream>,
    val audio: DashStream?,
    val qualities: List<Pair<Int, String>>,
)

data class Danmaku(val timeMs: Long, val text: String, val color: Int, val mode: Int)

fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> "%.1f亿".format(n / 100_000_000.0)
    n >= 10_000 -> "%.1f万".format(n / 10_000.0)
    else -> n.toString()
}

fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = sec % 3600 / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

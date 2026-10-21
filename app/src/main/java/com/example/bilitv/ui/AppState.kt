package com.example.bilitv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.bilitv.data.UserInfo

object AppState {
    var user by mutableStateOf<UserInfo?>(null)
}

sealed class Route {
    object Main : Route()
    object Login : Route()
    data class Detail(val bvid: String) : Route()
    data class Player(
        val bvid: String,
        val aid: Long,
        val cid: Long,
        val title: String,
        val startSec: Long = 0,
    ) : Route()
    data class Up(val mid: Long, val name: String) : Route()
}

enum class Tab(val label: String) {
    Search("搜索"), Home("推荐"), Dynamic("动态"), Me("我的")
}

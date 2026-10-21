package com.example.bilitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilitv.data.BiliApi
import com.example.bilitv.data.Session
import com.example.bilitv.data.VideoItem
import com.example.bilitv.ui.AppState
import com.example.bilitv.ui.Avatar
import com.example.bilitv.ui.DetailScreen
import com.example.bilitv.ui.DynamicScreen
import com.example.bilitv.ui.GlassBackground
import com.example.bilitv.ui.GlassClickable
import com.example.bilitv.ui.GlassColors
import com.example.bilitv.ui.GlassPanel
import com.example.bilitv.ui.HomeScreen
import com.example.bilitv.ui.LoginScreen
import com.example.bilitv.ui.MeScreen
import com.example.bilitv.ui.PlayerScreen
import com.example.bilitv.ui.Route
import com.example.bilitv.ui.SearchScreen
import com.example.bilitv.ui.Tab
import com.example.bilitv.ui.UpScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { GlassBackground { App() } }
        }
    }
}

@Composable
private fun App() {
    val stack = remember { mutableStateListOf<Route>(Route.Main) }
    var tab by remember { mutableStateOf(Tab.Home) }
    val route = stack.last()

    // 启动时用已保存的 Cookie 恢复登录状态
    LaunchedEffect(Unit) {
        if (Session.hasLoginCookie) AppState.user = runCatching { BiliApi.nav() }.getOrNull()
    }

    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val openVideo: (String) -> Unit = { push(Route.Detail(it)) }
    val openItem: (VideoItem) -> Unit = { push(Route.Detail(it.bvid)) }
    val openUp: (Long, String) -> Unit = { mid, name -> push(Route.Up(mid, name)) }
    val toLogin = { push(Route.Login) }

    // 播放器自己处理返回键；其它页面统一出栈
    BackHandler(enabled = stack.size > 1 && route !is Route.Player) { pop() }

    when (route) {
        Route.Main -> Row(Modifier.fillMaxSize()) {
            NavRail(tab, { tab = it }, toLogin)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    Tab.Search -> SearchScreen(openItem)
                    Tab.Home -> HomeScreen(openItem)
                    Tab.Dynamic -> DynamicScreen(openItem, toLogin)
                    Tab.Me -> MeScreen(openItem, openUp, toLogin)
                }
            }
        }
        Route.Login -> LoginScreen(onDone = { pop() }, onCancel = { pop() })
        is Route.Detail -> DetailScreen(
            bvid = route.bvid,
            onPlay = { push(it) },
            onOpenVideo = openVideo,
            onOpenUp = openUp,
            onLogin = toLogin,
        )
        is Route.Player -> PlayerScreen(route, onBack = { pop() })
        is Route.Up -> UpScreen(route.mid, route.name, openItem)
    }
}

@Composable
private fun NavRail(current: Tab, onSelect: (Tab) -> Unit, onLogin: () -> Unit) {
    GlassPanel(
        Modifier.fillMaxHeight().padding(16.dp).width(132.dp),
        RoundedCornerShape(32.dp)
    ) {
        Column(
            Modifier.fillMaxHeight().padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val user = AppState.user
            if (user != null) {
                Avatar(user.face, 52)
            } else {
                Text("哔哩TV", color = GlassColors.TextPrimary, fontSize = 18.sp)
            }
            Tab.values().forEach { t ->
                val selected = t == current
                GlassClickable({ onSelect(t) }, Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), 1.05f) { focused ->
                    Text(
                        t.label,
                        color = if (selected || focused) Color.White else GlassColors.TextSecondary,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 14.dp)
                    )
                }
            }
            if (user == null) {
                GlassClickable(onLogin, Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), 1.05f) {
                    Text(
                        "登录", color = GlassColors.Accent, fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center).padding(vertical = 14.dp)
                    )
                }
            }
        }
    }
}

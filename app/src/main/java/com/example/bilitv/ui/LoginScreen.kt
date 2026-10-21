package com.example.bilitv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bilitv.data.BiliApi
import com.example.bilitv.data.QrSession
import com.example.bilitv.data.QrState
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    var qr by remember { mutableStateOf<QrSession?>(null) }
    var state by remember { mutableStateOf<QrState>(QrState.Waiting) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(round) {
        qr = null; error = null; state = QrState.Waiting
        val session = try {
            BiliApi.qrGenerate()
        } catch (e: Exception) {
            error = e.message ?: "获取二维码失败"; return@LaunchedEffect
        }
        qr = session
        while (true) {
            delay(2000)
            val s = try { BiliApi.qrPoll(session.key) } catch (e: Exception) { continue }
            state = s
            if (s == QrState.Success) {
                AppState.user = runCatching { BiliApi.nav() }.getOrNull()
                onDone()
                return@LaunchedEffect
            }
            if (s == QrState.Expired) return@LaunchedEffect
        }
    }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        GlassPanel(shape = RoundedCornerShape(36.dp)) {
            Row(
                Modifier.padding(48.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(300.dp).clip(RoundedCornerShape(24.dp)).background(Color.White),
                    Alignment.Center
                ) {
                    val s = qr
                    when {
                        s != null -> {
                            val bmp = remember(s.url) { qrBitmap(s.url) }
                            Image(
                                bitmap = bmp.asImageBitmap(), contentDescription = "登录二维码",
                                modifier = Modifier.size(272.dp)
                            )
                            if (state == QrState.Expired) {
                                Box(
                                    Modifier.fillMaxSize().background(Color.White.copy(0.92f)),
                                    Alignment.Center
                                ) { Text("二维码已失效", color = Color.Black, fontSize = 18.sp) }
                            }
                        }
                        error != null -> Text(error!!, color = Color.Black, fontSize = 14.sp)
                        else -> CircularProgressIndicator(color = GlassColors.Blue)
                    }
                }
                Column(Modifier.width(360.dp)) {
                    Text("扫码登录", color = GlassColors.TextPrimary, fontSize = 34.sp)
                    Text(
                        when (state) {
                            QrState.Waiting -> "请使用哔哩哔哩手机客户端扫描二维码"
                            QrState.Scanned -> "已扫描，请在手机上确认登录"
                            QrState.Expired -> "二维码已失效，请刷新"
                            QrState.Success -> "登录成功"
                        },
                        color = GlassColors.TextSecondary, fontSize = 18.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 28.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GlassButton("刷新二维码", { round++ })
                        GlassButton("返回", onCancel)
                    }
                }
            }
        }
    }
}

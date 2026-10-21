package com.example.bilitv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object GlassColors {
    val Base = Color(0xFF080B16)
    val Blue = Color(0xFF4F8CFF)
    val Pink = Color(0xFFFF6FB5)
    val Teal = Color(0xFF3DE0C8)
    val TextPrimary = Color(0xFFF5F7FF)
    val TextSecondary = Color(0xB3F5F7FF)
    val Accent = Color(0xFF7FB2FF)
}

/** 缓慢漂移的彩色光斑背景，给玻璃面板提供折射的“内容”。 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val p by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse),
        label = "p"
    )
    Box(modifier.fillMaxSize().background(GlassColors.Base)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun blob(color: Color, cx: Float, cy: Float, r: Float) = drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(cx, cy), radius = r
                ),
                radius = r, center = Offset(cx, cy)
            )
            blob(GlassColors.Blue, w * (0.15f + 0.25f * p), h * (0.2f + 0.2f * p), w * 0.45f)
            blob(GlassColors.Pink, w * (0.85f - 0.25f * p), h * (0.75f - 0.15f * p), w * 0.42f)
            blob(GlassColors.Teal, w * (0.5f + 0.15f * p), h * (0.95f - 0.3f * p), w * 0.35f)
        }
        content()
    }
}

/** 玻璃面板：半透明渐变填充 + 高光描边；获得焦点时放大并提亮。 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    focused: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val fillTop = if (focused) 0.34f else 0.16f
    val fillBottom = if (focused) 0.18f else 0.06f
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(fillTop), Color.White.copy(fillBottom))
                )
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(if (focused) 0.95f else 0.55f),
                        Color.White.copy(if (focused) 0.45f else 0.08f),
                        Color.White.copy(if (focused) 0.7f else 0.25f),
                    )
                ),
                shape = shape
            ),
        content = content
    )
}

/** 可聚焦、可点击的玻璃容器，遥控器友好。 */
@Composable
fun GlassClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    focusScale: Float = 1.06f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(160), label = "s")
    GlassPanel(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = shape,
        focused = focused,
    ) { content(focused) }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
    fontSize: Int = 18,
) {
    GlassClickable(onClick, modifier, RoundedCornerShape(50), 1.08f) { focused ->
        Text(
            text,
            color = if (focused) Color.White else GlassColors.TextPrimary,
            fontSize = fontSize.sp,
            modifier = Modifier.padding(padding).align(Alignment.Center)
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = GlassColors.TextPrimary, fontSize = 26.sp,
        modifier = modifier.padding(bottom = 8.dp)
    )
}


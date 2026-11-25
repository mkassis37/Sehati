package com.mkassis37.sehati

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SehatiSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF087F88),
                        0.20f to Color(0xFF5BC9C9),
                        0.47f to Color(0xFFF5FFFF),
                        0.72f to Color(0xFFE8FAF8),
                        1.0f to Color(0xFF00616D)
                    )
                )
            )
    ) {
        // Soft turquoise light blooms recreate the misty, luminous background
        // of the approved reference without changing the launcher icon asset.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val glow = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF).copy(alpha = 0.92f), Color(0xFFB9F5F0).copy(alpha = 0.35f), Color.Transparent),
                center = Offset(w * 0.50f, h * 0.43f),
                radius = w * 0.76f
            )
            drawRect(glow)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFB7FFFF).copy(alpha = 0.62f), Color.Transparent),
                    center = Offset(w * 0.08f, h * 0.19f),
                    radius = w * 0.34f
                ),
                radius = w * 0.34f,
                center = Offset(w * 0.08f, h * 0.19f)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.72f), Color.Transparent),
                    center = Offset(w * 0.94f, h * 0.53f),
                    radius = w * 0.30f
                ),
                radius = w * 0.30f,
                center = Offset(w * 0.94f, h * 0.53f)
            )

            val waves = listOf(
                Triple(Color(0xFF29C5C0), 0.735f, 0.075f),
                Triple(Color(0xFF08A5A8), 0.795f, 0.075f),
                Triple(Color(0xFF007986), 0.875f, 0.105f),
                Triple(Color(0xFF005B68), 0.935f, 0.060f)
            )
            waves.forEachIndexed { index, wave ->
                val path = Path().apply {
                    moveTo(0f, h * wave.second)
                    cubicTo(
                        w * 0.28f, h * (wave.second - wave.third),
                        w * 0.58f, h * (wave.second + wave.third),
                        w, h * (wave.second - 0.025f)
                    )
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path, wave.first.copy(alpha = if (index == 0) 0.70f else 0.92f))
            }

            val ecg = Path().apply {
                moveTo(0f, h * 0.835f)
                lineTo(w * 0.25f, h * 0.835f)
                lineTo(w * 0.29f, h * 0.835f)
                lineTo(w * 0.315f, h * 0.82f)
                lineTo(w * 0.34f, h * 0.86f)
                lineTo(w * 0.37f, h * 0.79f)
                lineTo(w * 0.405f, h * 0.875f)
                lineTo(w * 0.445f, h * 0.825f)
                lineTo(w * 0.47f, h * 0.84f)
                lineTo(w * 0.51f, h * 0.835f)
                lineTo(w, h * 0.835f)
            }
            drawPath(
                ecg,
                Color(0xFF9CE8E1).copy(alpha = 0.90f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .offset(y = (-12).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "شعار صحتي",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(218.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "صحتي",
                color = Color(0xFF006B78),
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "—  Sehati  —",
                color = Color(0xFF087F80),
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = "معًا... نحو صحة أفضل",
                color = Color(0xFF006B78),
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(7) { index ->
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(if (index == 3) 9.dp else 8.dp)
                ) {
                    drawCircle(
                        color = if (index == 3) Color.White else Color(0xFF8CCBC8).copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

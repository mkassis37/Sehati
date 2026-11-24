package com.mkassis37.sehati

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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
                    listOf(Color(0xFFB9F0ED), Color(0xFFF7FFFF), Color(0xFFBDECE8))
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val waves = listOf(
                Triple(Color(0xFF27B9B2), 0.73f, 0.09f),
                Triple(Color(0xFF078E98), 0.80f, 0.08f),
                Triple(Color(0xFF006B78), 0.88f, 0.14f)
            )
            waves.forEachIndexed { index, wave ->
                val path = Path().apply {
                    moveTo(0f, h * wave.second)
                    cubicTo(w * 0.28f, h * (wave.second - wave.third), w * 0.58f, h * (wave.second + wave.third), w, h * (wave.second - 0.025f))
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path, wave.first.copy(alpha = if (index == 0) 0.72f else 0.92f))
            }
            val ecg = Path().apply {
                moveTo(0f, h * 0.84f)
                lineTo(w * 0.32f, h * 0.84f)
                lineTo(w * 0.36f, h * 0.84f)
                lineTo(w * 0.39f, h * 0.80f)
                lineTo(w * 0.42f, h * 0.88f)
                lineTo(w * 0.46f, h * 0.76f)
                lineTo(w * 0.50f, h * 0.90f)
                lineTo(w * 0.54f, h * 0.83f)
                lineTo(w * 0.59f, h * 0.84f)
                lineTo(w, h * 0.84f)
            }
            drawPath(ecg, Color(0xFF9CE8E1), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .offset(y = (-24).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "شعار صحتي",
                modifier = Modifier.size(184.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "صحتي",
                color = Color(0xFF006B78),
                fontSize = 42.sp,
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "صحتي | Sehati",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

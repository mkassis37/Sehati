package com.mkassis37.sehati

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * Displays the exact splash artwork approved by the user.
 * Keep the launcher icon separate; this screen must not redraw or replace it.
 */
@Composable
fun SehatiSplashScreen() {
    Image(
        painter = painterResource(R.drawable.sehati_splash),
        contentDescription = "شاشة بداية صحتي",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit
    )
}

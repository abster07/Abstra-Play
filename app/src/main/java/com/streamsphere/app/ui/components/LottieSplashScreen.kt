package com.streamsphere.app.ui.screens // Adjust this to match your package structure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.streamsphere.app.R

/**
 * A dedicated Splash Screen component that plays the StreamSphere Lottie animation.
 * Once the animation finishes, it triggers [onFinished] to navigate to the main app.
 */
@Composable
fun LottieSplashScreenComponent(onFinished: () -> Unit) {
    // 1. Load the composition from your res/raw/streamsphere.json
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.streamsphere) 
    )
    
    // 2. Control the animation state
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1, // Plays exactly once
        restartOnPlay = false
    )

    // 3. The UI Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Matches your system splash background
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(300.dp) // Adjust size as needed
        )
    }

    // 4. Trigger the transition to Main UI when animation hits 100%
    if (progress == 1f) {
        LaunchedEffect(Unit) {
            onFinished()
        }
    }
}
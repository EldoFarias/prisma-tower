package com.farias.prismatower.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.farias.prismatower.audio.Sfx
import com.farias.prismatower.audio.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opening: the screen starts black, the 3D tower fades in behind while the logo pops with a bounce
 * and a light sweep. Tap anywhere to skip.
 */
@Composable
fun SplashScreen(sound: SoundManager, onFinish: () -> Unit) {
    val curtain = remember { Animatable(1f) }
    val title = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }

    fun finish() {
        if (done) return
        done = true
        onFinish()
    }

    LaunchedEffect(Unit) {
        launch { curtain.animateTo(0.25f, tween(1400)) }
        delay(350)
        sound.play(Sfx.WHOOSH, 0.8f)
        title.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f))
        delay(1500)
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = curtain.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
        contentAlignment = Alignment.Center,
    ) {
        Logo(
            Modifier
                .padding(bottom = 80.dp)
                .graphicsLayer {
                    val t = title.value
                    alpha = t.coerceIn(0f, 1f)
                    scaleX = 0.5f + 0.5f * t
                    scaleY = 0.5f + 0.5f * t
                },
            intro = true,
        )
    }
}

package com.farias.prismatower.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farias.prismatower.R
import com.farias.prismatower.ads.AdsManager
import com.farias.prismatower.audio.Sfx
import com.farias.prismatower.audio.SoundManager
import com.farias.prismatower.data.Progress
import com.farias.prismatower.notify.DailyReminder
import kotlinx.coroutines.delay

const val VIDEO_COINS = 40
private const val EXIT_WINDOW_MS = 2000L

/** Day the chest popped up automatically, so it shows only once per day. */
private var chestAutoShownDay = 0

fun skipChestToday() {
    chestAutoShownDay = Progress.today()
}

@Composable
fun HomeScreen(
    ads: AdsManager,
    sound: SoundManager,
    onPlay: () -> Unit,
    onDaily: () -> Unit,
    onShop: () -> Unit,
    onStats: () -> Unit,
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showMissions by remember { mutableStateOf(false) }
    var showChest by remember { mutableStateOf(false) }

    var backPressedAt by remember { mutableLongStateOf(0L) }
    var showExitHint by remember { mutableStateOf(false) }
    BackHandler {
        when {
            showSettings || showMissions || showChest -> {
                showSettings = false
                showMissions = false
                showChest = false
            }
            SystemClock.uptimeMillis() - backPressedAt < EXIT_WINDOW_MS -> (context as? Activity)?.finish()
            else -> {
                backPressedAt = SystemClock.uptimeMillis()
                showExitHint = true
            }
        }
    }
    LaunchedEffect(backPressedAt) {
        if (backPressedAt == 0L) return@LaunchedEffect
        delay(EXIT_WINDOW_MS)
        showExitHint = false
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Progress.setNotifications(granted)
        DailyReminder.sync(context)
    }

    fun setNotifications(on: Boolean) {
        if (!on) {
            Progress.setNotifications(false)
            DailyReminder.sync(context)
        } else if (Build.VERSION.SDK_INT >= 33 && !DailyReminder.canNotify(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Progress.setNotifications(true)
            DailyReminder.sync(context)
        }
    }

    LaunchedEffect(Unit) {
        if (Progress.levelsWon >= 2 && !Progress.notifAsked && Progress.notificationsOn) {
            Progress.markNotifAsked()
            setNotifications(true)
        } else if (Progress.chestAvailable() && chestAutoShownDay != Progress.today() && Progress.levelsWon >= 1) {
            chestAutoShownDay = Progress.today()
            delay(600)
            showChest = true
        }
    }

    fun watchForCoins() {
        if (ads.adsReady && ads.rewardedReady) {
            ads.showRewarded {
                Progress.addCoins(VIDEO_COINS)
                sound.play(Sfx.COIN)
                Toast.makeText(context, context.getString(R.string.coins_reward, VIDEO_COINS), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, R.string.no_video, Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Darken the top and bottom so the texts read well over the moving tower.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color(0xB0000000), 0.32f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color(0xD0000000))),
        )
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoinPill(Progress.coins, onPlus = { watchForCoins() })
                Spacer(Modifier.weight(1f))
                GlassCircleButton(onClick = onStats) { TrophyIcon(Modifier.fillMaxSize()) }
                Spacer(Modifier.width(10.dp))
                GlassCircleButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = Palette.TextPrimary)
                }
            }
            Logo()
            if (Progress.bestScore > 0) {
                Text(stringResource(R.string.best_x, Progress.bestScore), style = Styles.plain(15, Palette.TextSecondary, FontWeight.Bold))
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (Progress.winStreak >= 2) {
                    Text(stringResource(R.string.win_streak, Progress.winStreak), style = Styles.glow(17, Color(0xFFFB923C), FontWeight.Black))
                    Spacer(Modifier.height(10.dp))
                }
                PlayButton(stringResource(R.string.play_level, Progress.level)) {
                    sound.play(Sfx.WHOOSH, 0.7f)
                    onPlay()
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val dailyDone = Progress.dailyDoneToday()
                    HomeTile(
                        stringResource(if (dailyDone) R.string.daily_done else R.string.daily_short),
                        Modifier.weight(1f), if (dailyDone) 0 else 1,
                        { if (!dailyDone) { sound.play(Sfx.WHOOSH, 0.7f); onDaily() } },
                    ) { Icon(Icons.Default.Star, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(26.dp)) }
                    HomeTile(stringResource(R.string.shop), Modifier.weight(1f), 0, onShop) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(26.dp))
                    }
                    HomeTile(stringResource(R.string.missions), Modifier.weight(1f), Progress.claimableMissions, { showMissions = true }) {
                        MissionIcon(Modifier.size(26.dp))
                    }
                    HomeTile(stringResource(R.string.chest), Modifier.weight(1f), if (Progress.chestAvailable()) 1 else 0, { showChest = true }) {
                        ChestIcon(Modifier.size(28.dp), shaking = Progress.chestAvailable())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            BannerAd(ads)
        }

        if (showSettings) SettingsDialog(ads, ::setNotifications) { showSettings = false }
        if (showMissions) MissionsDialog(sound) { showMissions = false }
        if (showChest) ChestDialog(ads, sound) { showChest = false }

        AnimatedVisibility(
            visible = showExitHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
        ) {
            Text(
                stringResource(R.string.press_back_exit),
                style = Styles.plain(15, Color.White, FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xE6120B2E))
                    .border(1.dp, Palette.GlassBorder, RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** "PRISMA TOWER" with a light sweep running over it every few seconds. */
@Composable
fun Logo(modifier: Modifier = Modifier, intro: Boolean = false) {
    val shine = remember { Animatable(-1.2f) }
    LaunchedEffect(Unit) {
        delay(if (intro) 500 else 900)
        while (true) {
            shine.snapTo(-1.2f)
            shine.animateTo(1.2f, tween(1100))
            delay(3600)
        }
    }
    Column(
        modifier
            .padding(top = 8.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val s = shine.value
                val x = size.width * (0.5f + 0.6f * s)
                val band = size.width * 0.16f
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), Color.Transparent),
                        start = Offset(x - band, 0f),
                        end = Offset(x + band, size.height),
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PRISMA", style = Styles.gradient(54))
        Text("TOWER", style = Styles.glow(26, Color.White, FontWeight.Black).copy(letterSpacing = 12.sp))
    }
}

@Composable
private fun PlayButton(text: String, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "play")
    val pulse by transition.animateFloat(1f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val shape = RoundedCornerShape(26.dp)
    val theme = Palette.theme
    Box(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF06B6D4), Color(0xFF8B5CF6), Color(0xFFDB2777))))
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, theme.accent.copy(alpha = 0.12f)))))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(22.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.15f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.15f, size.height)
                    close()
                }
                drawPath(path, Color.White)
            }
            Spacer(Modifier.width(12.dp))
            FitText(text, Styles.plain(24, Color.White, FontWeight.Black).copy(letterSpacing = 1.sp), minSp = 14f)
        }
    }
}

@Composable
private fun HomeTile(label: String, modifier: Modifier, badge: Int, onClick: () -> Unit, height: Dp = 78.dp, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(Color(0x66100A26))
                .border(1.dp, if (badge > 0) Color(0xFF34D399) else Palette.GlassBorder, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(Modifier.height(6.dp))
            FitText(label, Styles.plain(13, Palette.TextPrimary, FontWeight.Bold), minSp = 8f)
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Palette.Danger),
                contentAlignment = Alignment.Center,
            ) { Text(if (badge > 9) "9+" else badge.toString(), style = Styles.plain(11, Color.White, FontWeight.Black)) }
        }
    }
}

@Composable
private fun MissionIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFF34D399), Offset(w * 0.15f, h * 0.08f), Size(w * 0.7f, h * 0.86f), CornerRadius(w * 0.12f))
        val line = Color.White.copy(alpha = 0.9f)
        for (i in 0 until 3) {
            val y = h * (0.3f + i * 0.22f)
            drawCircle(line, w * 0.05f, Offset(w * 0.32f, y))
            drawLine(line, Offset(w * 0.42f, y), Offset(w * 0.72f, y), w * 0.08f)
        }
    }
}

@Composable
fun TrophyIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val gold = Brush.verticalGradient(listOf(Color(0xFFFFE680), Palette.Gold, Color(0xFFD97706)))
        val cup = Path().apply {
            moveTo(w * 0.2f, h * 0.08f)
            lineTo(w * 0.8f, h * 0.08f)
            cubicTo(w * 0.8f, h * 0.5f, w * 0.65f, h * 0.62f, w * 0.5f, h * 0.62f)
            cubicTo(w * 0.35f, h * 0.62f, w * 0.2f, h * 0.5f, w * 0.2f, h * 0.08f)
            close()
        }
        drawPath(cup, gold)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.07f)
        drawArc(Palette.Gold, 90f, 180f, false, Offset(w * 0.04f, h * 0.14f), Size(w * 0.26f, h * 0.26f), style = stroke)
        drawArc(Palette.Gold, -90f, 180f, false, Offset(w * 0.70f, h * 0.14f), Size(w * 0.26f, h * 0.26f), style = stroke)
        drawRect(Palette.Gold, Offset(w * 0.44f, h * 0.6f), Size(w * 0.12f, h * 0.18f))
        drawRoundRect(gold, Offset(w * 0.26f, h * 0.78f), Size(w * 0.48f, h * 0.14f), CornerRadius(w * 0.04f))
    }
}

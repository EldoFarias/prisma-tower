package com.farias.prismatower.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farias.prismatower.R
import com.farias.prismatower.audio.Sfx
import com.farias.prismatower.audio.SoundManager
import com.farias.prismatower.data.Progress
import com.farias.prismatower.game.GameHost
import com.farias.prismatower.gl.BallLook
import com.farias.prismatower.gl.BallStyle
import com.farias.prismatower.gl.Looks
import com.farias.prismatower.gl.TowerLook

private fun col(f: FloatArray, a: Float = 1f) = Color(f[0], f[1], f[2], a)

/**
 * Towers and balls. The top of the screen stays clear: the live 3D tower behind shows the item
 * being looked at, so the player sees exactly what they buy.
 */
@Composable
fun ShopScreen(host: GameHost, sound: SoundManager, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var towerSel by remember { mutableStateOf(Progress.towerId) }
    var ballSel by remember { mutableStateOf(Progress.ballId) }
    DisposableEffect(Unit) { onDispose { host.applyLooks() } }
    fun preview() = host.applyLooks(towerSel, ballSel)
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xB0000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Palette.TextPrimary)
                }
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.shop), style = Styles.gradient(30), modifier = Modifier.weight(1f))
                CoinPill(Progress.coins)
            }
            Spacer(Modifier.weight(0.75f))

            val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Color(0xF0181036), Color(0xFA0B0720))))
                    .border(1.dp, Palette.GlassBorder, shape)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabChip(stringResource(R.string.towers), tab == 0, Modifier.weight(1f)) { tab = 0 }
                    TabChip(stringResource(R.string.balls), tab == 1, Modifier.weight(1f)) { tab = 1 }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (tab == 0) {
                        Looks.towers.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { t ->
                                    ItemCard(
                                        name = stringResource(t.nameRes), price = t.price,
                                        owned = Progress.ownsTower(t.id), inUse = Progress.towerId == t.id,
                                        selected = towerSel == t.id, modifier = Modifier.weight(1f),
                                        onClick = { towerSel = t.id; preview(); sound.play(Sfx.PICK, 0.6f) },
                                    ) { TowerThumb(t) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        Looks.balls.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { b ->
                                    ItemCard(
                                        name = stringResource(b.nameRes), price = b.price,
                                        owned = Progress.ownsBall(b.id), inUse = Progress.ballId == b.id,
                                        selected = ballSel == b.id, modifier = Modifier.weight(1f),
                                        onClick = { ballSel = b.id; preview(); sound.play(Sfx.PICK, 0.6f) },
                                    ) { BallThumb(b) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                // Action for the item being previewed.
                if (tab == 0) {
                    val t = Looks.tower(towerSel)
                    ActionButton(Progress.ownsTower(t.id), Progress.towerId == t.id, t.price,
                        onBuy = {
                            if (Progress.buyTower(t.id, t.price)) { Progress.selectTower(t.id); sound.play(Sfx.COIN) } else sound.play(Sfx.INVALID)
                        },
                        onUse = { Progress.selectTower(t.id); sound.play(Sfx.PLACE) })
                } else {
                    val b = Looks.ball(ballSel)
                    ActionButton(Progress.ownsBall(b.id), Progress.ballId == b.id, b.price,
                        onBuy = {
                            if (Progress.buyBall(b.id, b.price)) { Progress.selectBall(b.id); sound.play(Sfx.COIN) } else sound.play(Sfx.INVALID)
                        },
                        onUse = { Progress.selectBall(b.id); sound.play(Sfx.PLACE) })
                }
            }
        }
    }
}

@Composable
private fun ActionButton(owned: Boolean, inUse: Boolean, price: Int, onBuy: () -> Unit, onUse: () -> Unit) {
    when {
        inUse -> SecondaryButton(stringResource(R.string.in_use)) {}
        owned -> PrimaryButton(stringResource(R.string.use), onClick = onUse)
        else -> PrimaryButton(
            if (Progress.coins >= price) stringResource(R.string.buy_for, price) else stringResource(R.string.need_coins, price - Progress.coins),
            enabled = Progress.coins >= price,
            onClick = onBuy,
        )
    }
}

@Composable
private fun TabChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(42.dp)
            .clip(shape)
            .background(
                if (active) Brush.horizontalGradient(listOf(Color(0xFF06B6D4), Color(0xFF8B5CF6)))
                else Brush.horizontalGradient(listOf(Palette.Glass, Palette.Glass)),
            )
            .border(1.dp, if (active) Color.White.copy(alpha = 0.3f) else Palette.GlassBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Styles.plain(16, Color.White, FontWeight.ExtraBold)) }
}

@Composable
private fun ItemCard(
    name: String,
    price: Int,
    owned: Boolean,
    inUse: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    thumb: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Color(0x33FFFFFF) else Palette.Glass)
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Cyan else if (inUse) Color(0xFF34D399) else Palette.GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            thumb()
            if (inUse) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF34D399)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            } else if (!owned) {
                Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.TopEnd).size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        FitText(name, Styles.plain(13, Palette.TextPrimary, FontWeight.Bold), minSp = 9f)
        if (!owned) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(price.toString(), style = Styles.plain(13, Palette.Gold, FontWeight.Black))
            }
        } else {
            Text(" ", style = Styles.plain(13))
        }
    }
}

/** Little drawing of a tower look: background, pillar and stacked rings with a gap. */
@Composable
fun TowerThumb(t: TowerLook) {
    Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
        drawRect(Brush.verticalGradient(listOf(col(t.bgTop), col(t.bgBottom))))
        val a = t.aurora
        if (size.minDimension > 2f) {
            drawCircle(Brush.radialGradient(listOf(col(a[0], 0.55f), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.2f), size.width * 0.7f))
            drawCircle(Brush.radialGradient(listOf(col(a[1], 0.4f), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.85f), size.width * 0.6f))
        }
        val cx = size.width / 2f
        val pw = size.width * 0.16f
        drawRect(
            Brush.horizontalGradient(listOf(col(t.pillar), Palette.lighter(col(t.pillar), 0.35f), col(t.pillar)), startX = cx - pw / 2, endX = cx + pw / 2),
            Offset(cx - pw / 2f, 0f), Size(pw, size.height),
        )
        for (i in 0 until 4) {
            val y = size.height * (0.18f + i * 0.22f)
            val c = if (i % 2 == 0) col(t.ringA) else col(t.ringB)
            ring(cx, y, size.width * 0.38f, size.height * 0.06f, c, gapLeft = i % 2 == 0, danger = if (i == 2) col(t.danger) else null)
        }
    }
}

private fun DrawScope.ring(cx: Float, y: Float, rx: Float, h: Float, c: Color, gapLeft: Boolean, danger: Color?) {
    val dark = Palette.darker(c, 0.35f)
    // Side (thickness) then top
    drawOval(dark, Offset(cx - rx, y - h * 0.4f), Size(rx * 2f, h * 1.6f))
    drawOval(Brush.verticalGradient(listOf(Palette.lighter(c, 0.3f), c), startY = y - h, endY = y + h), Offset(cx - rx, y - h), Size(rx * 2f, h * 1.6f))
    // Gap: a dark wedge cut in front
    val gx = if (gapLeft) cx - rx * 0.55f else cx + rx * 0.2f
    drawOval(Color(0x99000000), Offset(gx, y - h * 0.2f), Size(rx * 0.35f, h * 1.1f))
    if (danger != null) {
        drawOval(danger, Offset(cx + (if (gapLeft) 0.15f else -0.6f) * rx, y - h * 0.15f), Size(rx * 0.4f, h * 0.9f))
    }
}

/** Little drawing of a ball look. */
@Composable
fun BallThumb(b: BallLook) {
    Canvas(Modifier.fillMaxSize(0.8f)) {
        val r = size.minDimension / 2f
        if (r < 1f) return@Canvas
        val c = Offset(size.width / 2f, size.height / 2f)
        val c1 = col(b.c1)
        val c2 = col(b.c2)
        drawCircle(Brush.radialGradient(listOf(col(b.trail, 0.45f), Color.Transparent), c, r * 1.25f), r * 1.25f, c)
        val brush = when (b.style) {
            BallStyle.RAINBOW -> Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red), c)
            BallStyle.BEACH -> Brush.sweepGradient(listOf(c1, c1, c2, c2, Color(0xFFFACC15), Color(0xFFFACC15), Color(0xFF3B82F6), Color(0xFF3B82F6), c1), c)
            BallStyle.STRIPES -> Brush.linearGradient(listOf(c1, c2, c1, c2, c1, c2, c1), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r))
            BallStyle.METAL, BallStyle.DISCO -> Brush.linearGradient(listOf(Palette.lighter(c1, 0.5f), c1, c2, c1), start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r))
            BallStyle.PLANET -> Brush.verticalGradient(listOf(c1, c2, c1, Palette.lighter(c1, 0.3f), c2), startY = c.y - r, endY = c.y + r)
            BallStyle.LAVA -> Brush.radialGradient(listOf(c1, Color(0xFFFF8A00), c2), Offset(c.x - r * 0.2f, c.y - r * 0.2f), r * 1.2f)
            BallStyle.NEON -> Brush.radialGradient(listOf(Color.White, c1, c2), c, r)
            else -> Brush.radialGradient(listOf(Palette.lighter(c1, 0.4f), c1, Palette.darker(c1, 0.35f)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.4f)
        }
        drawCircle(brush, r, c)
        if (b.style == BallStyle.EIGHT) {
            drawCircle(Color.White, r * 0.42f, Offset(c.x, c.y - r * 0.1f))
            drawCircle(Color.Black, r * 0.16f, Offset(c.x, c.y - r * 0.2f))
            drawCircle(Color.Black, r * 0.19f, Offset(c.x, c.y + r * 0.06f))
        }
        if (b.style == BallStyle.DISCO) {
            for (i in 0 until 5) for (j in 0 until 5) {
                if ((i + j) % 2 == 0) drawRect(Color.White.copy(alpha = 0.35f), Offset(c.x - r * 0.7f + i * r * 0.3f, c.y - r * 0.7f + j * r * 0.3f), Size(r * 0.16f, r * 0.16f))
            }
        }
        // Shading and highlight
        drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color(0x55000000)), Offset(c.x - r * 0.3f, c.y - r * 0.3f), r * 1.6f), r, c)
        drawCircle(Color.White.copy(alpha = 0.75f), r * 0.16f, Offset(c.x - r * 0.38f, c.y - r * 0.4f))
    }
}

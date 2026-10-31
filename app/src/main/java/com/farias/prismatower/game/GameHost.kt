package com.farias.prismatower.game

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farias.prismatower.R
import com.farias.prismatower.audio.Haptic
import com.farias.prismatower.audio.Haptics
import com.farias.prismatower.audio.Sfx
import com.farias.prismatower.audio.SoundManager
import com.farias.prismatower.data.MissionType
import com.farias.prismatower.data.Progress
import com.farias.prismatower.gl.Looks
import java.util.Calendar
import java.util.TimeZone

/** A floating text over the 3D view ("+3", "PERFEITO!"), at a screen position 0..1. */
class Popup(val id: Long, val text: String, val x: Float, val y: Float, val kind: Int, val born: Long)

/**
 * Owns the [TowerGame] (which lives on the GL thread) and mirrors what the UI needs as Compose state
 * on the main thread. Commands go to the GL thread through [GLSurfaceView.queueEvent].
 */
class GameHost(context: Context) : TowerGame.Callbacks {

    enum class Phase { MENU, PLAYING, DEAD, WON }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val sound = SoundManager.get(app)
    val game = TowerGame(this)
    var view: GLSurfaceView? = null

    var phase by mutableStateOf(Phase.MENU)
        private set
    var level by mutableIntStateOf(1)
        private set
    var daily by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    /** Score of the whole run (kept while the player keeps winning). */
    var score by mutableIntStateOf(0)
        private set
    private var runBase = 0
    var combo by mutableIntStateOf(0)
        private set
    var onFire by mutableStateOf(false)
        private set
    var touched by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    /** Rewarded "continue" already used on this level. */
    var revived by mutableStateOf(false)
        private set
    var newBest by mutableStateOf(false)
        private set
    /** Coins given for the last win (before any video bonus). */
    var lastReward by mutableIntStateOf(0)
        private set
    /** Progress (0..1) reached when the ball died. */
    var deathProgress by mutableFloatStateOf(0f)
        private set
    val popups = mutableStateListOf<Popup>()

    /** Endless mode ("free fall") run. */
    var endless by mutableStateOf(false)
        private set
    /** This level is a boss tower. */
    var boss by mutableStateOf(false)
        private set
    /** Rings passed in this level (the endless depth). */
    var depth by mutableIntStateOf(0)
        private set
    var shields by mutableIntStateOf(0)
        private set
    var coinsThisLevel by mutableIntStateOf(0)
        private set
    /** String resource of a mechanic shown for the first time in this level (0 = none). */
    var notice by mutableIntStateOf(0)
        private set
    /** Deaths on the current level number (to offer the shield). */
    var failsHere by mutableIntStateOf(0)
        private set
    private var failsLevel = -1
    private var bounces = 0
    private var glassCount = 0
    private var portalCount = 0

    private var passed = 0
    private var smashed = 0
    private var bestComboThisLevel = 0
    private var popupId = 0L
    private var lastBounceSound = 0L

    // ------------------------------------------------------------------ commands

    private fun gl(block: () -> Unit) {
        val v = view
        if (v != null) v.queueEvent(block) else block()
    }

    fun applyLooks(
        towerId: String = if (Progress.trialGames > 0 && Progress.trialTower.isNotEmpty()) Progress.trialTower else Progress.towerId,
        ballId: String = if (Progress.trialGames > 0 && Progress.trialBall.isNotEmpty()) Progress.trialBall else Progress.ballId,
        trailId: String = Progress.trailId,
    ) {
        val t = Looks.tower(towerId)
        val b = Looks.ball(ballId)
        val trail = com.farias.prismatower.data.Trails.byId(trailId).style
        com.farias.prismatower.audio.MusicPlayer.select(
            com.farias.prismatower.audio.MusicPlayer.trackFor(towerId, boss && phase == Phase.PLAYING),
        )
        gl { game.tower = t; game.ball = b; game.trailStyle = trail }
    }

    fun showMenu() {
        phase = Phase.MENU
        paused = false
        popups.clear()
        applyLooks()
        gl { game.startAttract() }
    }

    fun play(n: Int, keepScore: Boolean = false) {
        daily = false
        endless = false
        level = n
        start(LevelCache.get(n), keepScore)
        LevelCache.prefetch(n + 1)
    }

    fun playDaily() {
        daily = true
        endless = false
        level = dayIndex()
        start(LevelCache.daily(dayIndex()), false)
        LevelCache.prefetch(Progress.level)
    }

    fun playEndless() {
        daily = false
        endless = true
        level = 0
        start(LevelCache.endless(), false)
    }

    fun retry() {
        when {
            endless -> playEndless()
            daily -> playDaily()
            else -> play(level)
        }
    }

    /** One extra shield for this level (rewarded video). */
    fun addShield() {
        shields += 1
        sound.play(Sfx.REVIVE, 0.8f)
        gl { game.sim.shields += 1 }
    }

    fun clearNotice() {
        notice = 0
    }

    fun next() = play(Progress.level, keepScore = true)

    private fun start(lv: TowerLevel, keepScore: Boolean) {
        Progress.ensureMissionDay()
        runBase = if (keepScore) score else 0
        score = runBase
        progress = 0f
        combo = 0
        onFire = false
        touched = false
        paused = false
        revived = false
        newBest = false
        passed = 0
        smashed = 0
        bestComboThisLevel = 0
        popups.clear()
        depth = 0
        endlessPaid = 0
        shields = 0
        coinsThisLevel = 0
        bounces = 0
        glassCount = 0
        portalCount = 0
        boss = lv.boss
        val key = if (endless) -1 else if (daily) -2 else level
        if (key != failsLevel) { failsLevel = key; failsHere = 0 }
        notice = when {
            SliceType.GLASS in lv.mechanics && "glass" !in Progress.seenMechanics -> { Progress.markMechanicSeen("glass"); R.string.mech_glass }
            lv.hasBlink && "blink" !in Progress.seenMechanics -> { Progress.markMechanicSeen("blink"); R.string.mech_blink }
            SliceType.PORTAL in lv.mechanics && "portal" !in Progress.seenMechanics -> { Progress.markMechanicSeen("portal"); R.string.mech_portal }
            lv.platforms.any { it.coin >= 0 } && "coins" !in Progress.seenMechanics -> { Progress.markMechanicSeen("coins"); R.string.mech_coins }
            else -> 0
        }
        phase = Phase.PLAYING
        applyLooks()
        Progress.useTrialGame()
        gl { game.startPlay(lv) }
    }

    fun revive() {
        if (phase != Phase.DEAD) return
        revived = true
        phase = Phase.PLAYING
        sound.play(Sfx.REVIVE)
        gl { game.revive() }
    }

    /** Stops drawing the 3D view while a screen covers it completely (the splash). */
    var renderPaused = false
        set(value) {
            field = value
            applyRenderMode()
        }

    fun applyRenderMode() {
        view?.renderMode = if (renderPaused) GLSurfaceView.RENDERMODE_WHEN_DIRTY else GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    fun turn(radians: Float) {
        if (phase != Phase.PLAYING || paused) return
        if (!touched) touched = true
        game.turn(radians) // thread-safe
    }

    fun pauseGame(p: Boolean) {
        if (phase != Phase.PLAYING && p) return
        paused = p
        gl { game.paused = p }
    }

    // ------------------------------------------------------------------ callbacks (GL thread)

    override fun onEvent(e: TowerEvent, game: TowerGame) {
        if (game.mode != TowerGame.Mode.PLAY) return
        val sim = game.sim
        val sc = sim.score
        val prog = sim.progress
        val x = game.ballScreenX
        val y = game.ballScreenY
        val fire = sim.onFire
        val d = sim.next
        main.post { handle(e, sc, prog, x, y, fire, d) }
    }

    override fun onFrame(game: TowerGame) = Unit

    private fun handle(e: TowerEvent, simScore: Int, prog: Float, x: Float, y: Float, fire: Boolean, d: Int) {
        if (phase != Phase.PLAYING) return
        score = runBase + simScore
        progress = prog
        onFire = fire
        depth = d
        when (e) {
            is TowerEvent.Coin -> {
                coinsThisLevel++
                Progress.addCoins(COIN_VALUE)
                sound.play(Sfx.COIN, 0.7f, 1.2f)
                popup("+$COIN_VALUE", x, y - 0.04f, 2)
            }
            is TowerEvent.GlassBreak -> {
                glassCount++
                sound.play(Sfx.GLASS, 0.6f, 1.3f)
                haptic(Haptic.PLACE)
            }
            is TowerEvent.Portal -> {
                portalCount++
                passed += 2
                combo += 2
                sound.play(Sfx.WHOOSH, 1f, 1.5f)
                haptic(Haptic.COMBO)
                popup("+${e.points}", x, y, 1)
            }
            is TowerEvent.Shield -> {
                shields = (shields - 1).coerceAtLeast(0)
                passed++
                combo = 0
                sound.play(Sfx.REVIVE)
                haptic(Haptic.HEAVY)
                popup("🛡", x, y - 0.06f, 4)
            }
            is TowerEvent.Bounce -> {
                bounces++
                combo = 0
                val now = SystemClock.uptimeMillis()
                if (now - lastBounceSound > 90) {
                    lastBounceSound = now
                    sound.play(Sfx.BOUNCE, 0.7f, 0.92f + (Math.random() * 0.16).toFloat())
                }
                haptic(Haptic.TICK)
            }
            is TowerEvent.Pass -> {
                passed++
                combo = e.combo
                bestComboThisLevel = maxOf(bestComboThisLevel, e.combo)
                sound.play(Sfx.PASS, 0.75f, (0.9f + 0.1f * e.combo).coerceAtMost(1.9f))
                popup("+${e.points}", x, y, if (e.combo >= 2) 1 else 0)
                if (e.combo == 2) popup("×2", x, y - 0.06f, 2)
            }
            is TowerEvent.Smash -> {
                smashed++
                passed++
                combo = 0
                sound.play(Sfx.GLASS, 1f, 0.9f + (Math.random() * 0.25).toFloat())
                haptic(Haptic.CLEAR)
                popup("+${e.points}", x, y, 3)
            }
            TowerEvent.FireOn -> {
                sound.play(Sfx.COMBO)
                haptic(Haptic.COMBO)
                popup("🔥", x, y - 0.08f, 4)
            }
            is TowerEvent.Die -> {
                deathProgress = prog
                phase = Phase.DEAD
                sound.play(Sfx.GAME_OVER)
                haptic(Haptic.HEAVY)
                finishStats(won = false)
            }
            TowerEvent.Win -> {
                phase = Phase.WON
                sound.play(Sfx.RECORD)
                haptic(Haptic.COMBO)
                finishStats(won = true)
            }
        }
    }

    private fun finishStats(won: Boolean) {
        Progress.addStats(
            bosses = if (won && boss) 1 else 0,
            perfect = if (won && bounces == 0 && !endless) 1 else 0,
            coins = coinsThisLevel,
            glass = glassCount,
            portals = portalCount,
        )
        coinsThisLevel = 0
        glassCount = 0
        portalCount = 0
        if (!won) failsHere += 1
        Progress.onRings(passed, smashed, bestComboThisLevel)
        Progress.missionEvent(MissionType.RINGS, passed)
        Progress.missionEvent(MissionType.SMASH, smashed)
        Progress.missionEvent(MissionType.SCORE, score, asMax = true)
        passed = 0
        smashed = 0
        newBest = Progress.onScore(score)
        if (endless) {
            newBest = Progress.onEndless(depth) || newBest
            // Coins for the depth reached (only the part not paid yet if the run was continued).
            val total = depth / 4 + if (won) 500 else 0
            lastReward = (total - endlessPaid).coerceAtLeast(0)
            endlessPaid = total
            Progress.addCoins(lastReward)
            return
        }
        if (won && !endless) {
            Progress.onLevelWon(level, daily)
            Progress.missionEvent(MissionType.LEVELS, 1)
            Progress.missionEvent(MissionType.STREAK, Progress.winStreak, asMax = true)
            lastReward = if (daily) Progress.onDailyWon()
            else (Progress.levelReward(level) * (if (boss) 3 else 1)).also { Progress.addCoins(it) }
        } else {
            lastReward = 0
        }
    }

    private var endlessPaid = 0

    /** Ends a lost attempt for good (the player gave up the "continue"). */
    fun giveUp() {
        if (phase == Phase.DEAD) Progress.onLevelLost()
    }

    private fun popup(text: String, x: Float, y: Float, kind: Int) {
        val now = SystemClock.uptimeMillis()
        popups.removeAll { now - it.born > 1200 }
        if (popups.size > 8) popups.removeAt(0)
        popups.add(Popup(popupId++, text, x, y, kind, now))
    }

    private fun haptic(kind: Haptic) {
        if (Progress.vibrationOn) Haptics.play(app, kind)
    }

    companion object {
        const val COIN_VALUE = 2

        /** Local calendar day number (same challenge for everyone on the same date). */
        fun dayIndex(): Int {
            val c = Calendar.getInstance()
            val offset = TimeZone.getDefault().getOffset(c.timeInMillis)
            return ((c.timeInMillis + offset) / 86_400_000L).toInt()
        }
    }
}

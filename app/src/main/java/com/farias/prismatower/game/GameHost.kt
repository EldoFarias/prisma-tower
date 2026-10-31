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

    fun applyLooks(towerId: String = Progress.towerId, ballId: String = Progress.ballId) {
        val t = Looks.tower(towerId)
        val b = Looks.ball(ballId)
        gl { game.tower = t; game.ball = b }
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
        level = n
        start(TowerLevels.level(n), keepScore)
    }

    fun playDaily() {
        daily = true
        level = dayIndex()
        start(TowerLevels.daily(dayIndex()), false)
    }

    fun retry() {
        if (daily) playDaily() else play(level)
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
        phase = Phase.PLAYING
        applyLooks()
        gl { game.startPlay(lv) }
    }

    fun revive() {
        if (phase != Phase.DEAD) return
        revived = true
        phase = Phase.PLAYING
        sound.play(Sfx.REVIVE)
        gl { game.revive() }
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
        main.post { handle(e, sc, prog, x, y, fire) }
    }

    override fun onFrame(game: TowerGame) = Unit

    private fun handle(e: TowerEvent, simScore: Int, prog: Float, x: Float, y: Float, fire: Boolean) {
        if (phase != Phase.PLAYING) return
        score = runBase + simScore
        progress = prog
        onFire = fire
        when (e) {
            is TowerEvent.Bounce -> {
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
        Progress.onRings(passed, smashed, bestComboThisLevel)
        Progress.missionEvent(MissionType.RINGS, passed)
        Progress.missionEvent(MissionType.SMASH, smashed)
        Progress.missionEvent(MissionType.SCORE, score, asMax = true)
        passed = 0
        smashed = 0
        newBest = Progress.onScore(score)
        if (won) {
            Progress.onLevelWon(level, daily)
            Progress.missionEvent(MissionType.LEVELS, 1)
            Progress.missionEvent(MissionType.STREAK, Progress.winStreak, asMax = true)
            lastReward = if (daily) Progress.onDailyWon() else Progress.levelReward(level).also { Progress.addCoins(it) }
        } else {
            lastReward = 0
        }
    }

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
        /** Local calendar day number (same challenge for everyone on the same date). */
        fun dayIndex(): Int {
            val c = Calendar.getInstance()
            val offset = TimeZone.getDefault().getOffset(c.timeInMillis)
            return ((c.timeInMillis + offset) / 86_400_000L).toInt()
        }
    }
}

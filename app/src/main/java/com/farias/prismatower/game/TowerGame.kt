package com.farias.prismatower.game

import com.farias.prismatower.gl.Ambient
import com.farias.prismatower.gl.BallLook
import com.farias.prismatower.gl.Looks
import com.farias.prismatower.gl.TowerLook
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A paint splat left by a bounce, stuck to a ring. */
class Splat(val ring: Int, val angle: Float, val born: Float, val color: FloatArray, val seed: Float, val spin: Float)

/** A ring flying apart after the ball went through it (or smashed it). */
class Break(val ring: Int, val born: Float, val rotation: Float, val smash: Boolean)

/**
 * Everything the 3D view shows, updated on the GL thread every frame: the simulation, the camera and
 * all effects. Commands from the UI arrive through [GameHost] (queued on the GL thread).
 */
class TowerGame(private val callbacks: Callbacks) {

    interface Callbacks {
        /** Called on the GL thread; implementations hop to the main thread. */
        fun onEvent(e: TowerEvent, game: TowerGame)
        fun onFrame(game: TowerGame)
    }

    enum class Mode { ATTRACT, PLAY }

    var mode = Mode.ATTRACT
        private set
    var sim = TowerSim(LevelCache.get(3))
        private set
    var tower: TowerLook = Looks.towers[0]
    var ball: BallLook = Looks.balls[0]
    var paused = false

    /** Seconds since the game object started (drives shader animations). */
    var clock = 0f
        private set
    var cameraY = sim.ballY + 2f
        private set
    var shake = 0f
        private set
    var sinceBounce = 9f
        private set
    var deathTime = -1f
        private set
    var winTime = -1f
        private set

    val splats = ArrayList<Splat>()
    val breaks = ArrayList<Break>()
    val particles = Particles(1600)
    /** Recent ball heights for the comet trail: a ring buffer, oldest first via [trailAt]. */
    private val trailBuf = FloatArray(TRAIL)
    var trailCount = 0
        private set
    private var trailHead = 0
    fun trailAt(i: Int): Float = trailBuf[(trailHead - trailCount + i + TRAIL * 2) % TRAIL]

    /** Ball squash & stretch, updated every frame. */
    var ballSy = 1f
        private set
    var ballSxz = 1f
        private set

    /** Ball position on screen (0..1), for floating texts. */
    @Volatile var ballScreenX = 0.5f
    @Volatile var ballScreenY = 0.5f

    private val events = ArrayList<TowerEvent>()
    private val rnd = Random(System.nanoTime())
    private val inputLock = Any()
    private var pendingTurn = 0f
    private var lastTurnAt = 0f
    /** The player has touched the tower at least once this level. */
    var touched = false
        private set

    // ------------------------------------------------------------------ commands (GL thread)

    fun startAttract() {
        mode = Mode.ATTRACT
        // Use a level that is already built (never stall the menu), and get the next ones ready.
        val pick = 3 + rnd.nextInt(30)
        val lv = LevelCache.ready(pick) ?: (3..33).firstNotNullOfOrNull { LevelCache.ready(it) } ?: LevelCache.get(3)
        begin(lv)
        LevelCache.prefetch(3 + rnd.nextInt(30))
    }

    fun startPlay(level: TowerLevel) {
        mode = Mode.PLAY
        begin(level)
    }

    private fun begin(level: TowerLevel) {
        sim = TowerSim(level)
        cameraY = sim.ballY + 2f
        splats.clear()
        breaks.clear()
        particles.clear()
        trailCount = 0
        deathTime = -1f
        winTime = -1f
        sinceBounce = 9f
        touched = false
        paused = false
        synchronized(inputLock) { pendingTurn = 0f }
    }

    fun revive() {
        if (!sim.dead) return
        events.clear()
        sim.revive(events)
        deathTime = -1f
        handle(events)
    }

    /** UI thread: the finger moved [radians] around the tower. */
    fun turn(radians: Float) {
        synchronized(inputLock) { pendingTurn += radians }
    }

    // ------------------------------------------------------------------ frame

    fun update(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 1f / 20f)
        clock += dt
        val turn = synchronized(inputLock) { val t = pendingTurn; pendingTurn = 0f; t }
        if (paused) return
        if (mode == Mode.PLAY) {
            if (turn != 0f) {
                touched = true
                lastTurnAt = clock
            }
            if (!sim.dead && !sim.won) sim.rotation += turn
        } else {
            // Demo: a relaxed bot plays by itself; the tower also drifts slowly.
            sim.rotation += TowerBot.turn(sim, 3.2f, dt)
        }
        // Fixed sub-steps keep the physics identical at any frame rate.
        var left = dt
        while (left > 1e-5f) {
            val h = min(left, 1f / 120f)
            events.clear()
            sim.step(h, events)
            handle(events)
            left -= h
        }
        sinceBounce += dt
        if (mode == Mode.ATTRACT && (sim.won && clock - winTime > 2.5f || sim.dead && clock - deathTime > 1.2f)) startAttract()

        // Camera follows the ball down (never up), smoothly.
        val target = sim.ballY + 2.2f
        if (target < cameraY) cameraY += (target - cameraY) * min(1f, dt * 9f)
        shake *= exp(-dt * 9f)

        // Trail
        trailBuf[trailHead] = sim.ballY
        trailHead = (trailHead + 1) % TRAIL
        if (trailCount < TRAIL) trailCount++
        updateBallScale()
        if (sim.onFire && !sim.dead) emitFire()
        emitAmbient(dt)
        particles.update(dt)
        for (i in splats.indices.reversed()) {
            val sp = splats[i]
            if (sp.ring < sim.next - 1 || clock - sp.born > 6f) splats.removeAt(i)
        }
        for (i in breaks.indices.reversed()) if (clock - breaks[i].born > 1.2f) breaks.removeAt(i)
        callbacks.onFrame(this)
    }

    private fun handle(list: List<TowerEvent>) {
        for (e in list) {
            when (e) {
                is TowerEvent.Bounce -> {
                    sinceBounce = 0f
                    if (splats.size > 28) splats.removeAt(0)
                    splats.add(Splat(e.platform, e.localAngle, clock, ball.trail, rnd.nextFloat() * 6.28f, rnd.nextFloat() * 6.28f))
                    splash(Tower.platformTop(e.platform), ball.trail, 10, 3.5f)
                }
                is TowerEvent.Pass -> {
                    breaks.add(Break(e.platform, clock, sim.ringRotation(e.platform), false))
                    burstRing(e.platform, 14 + e.combo * 4, false)
                }
                is TowerEvent.Smash -> {
                    breaks.add(Break(e.platform, clock, sim.ringRotation(e.platform), true))
                    burstRing(e.platform, 60, true)
                    shake = 0.35f
                }
                is TowerEvent.Die -> {
                    deathTime = clock
                    shake = 0.5f
                    splash(sim.ballY, floatArrayOf(1f, 0.25f, 0.3f), 40, 7f)
                }
                TowerEvent.Win -> {
                    winTime = clock
                    confetti()
                }
                TowerEvent.FireOn -> splash(sim.ballY, floatArrayOf(1f, 0.55f, 0.1f), 24, 5f)
            }
            callbacks.onEvent(e, this)
        }
    }

    // ------------------------------------------------------------------ particle helpers

    private fun ballX() = 0f
    private fun ballZ() = Tower.BALL_PATH_R

    private fun splash(y: Float, color: FloatArray, n: Int, speed: Float) {
        repeat(n) {
            val a = rnd.nextFloat() * 6.283f
            val up = 0.3f + rnd.nextFloat()
            particles.add(
                ballX(), y, ballZ(),
                cos(a) * speed * rnd.nextFloat(), up * speed, sin(a) * speed * rnd.nextFloat(),
                color[0], color[1], color[2], 1f,
                0.10f + rnd.nextFloat() * 0.14f, 0.35f + rnd.nextFloat() * 0.35f, gravity = 22f,
            )
        }
    }

    private fun burstRing(ring: Int, n: Int, smash: Boolean) {
        val y = Tower.platformTop(ring)
        val c = ringColor(ring)
        repeat(n) {
            val a = rnd.nextFloat() * 6.283f
            val r = Tower.RING_INNER + rnd.nextFloat() * (Tower.RING_OUTER - Tower.RING_INNER)
            val out = 3f + rnd.nextFloat() * (if (smash) 9f else 5f)
            val bright = rnd.nextFloat() < 0.3f
            particles.add(
                r * sin(a), y, r * cos(a),
                sin(a) * out, 1.5f + rnd.nextFloat() * 4f, cos(a) * out,
                if (bright) 1f else c[0], if (bright) 1f else c[1], if (bright) 1f else c[2], 1f,
                0.12f + rnd.nextFloat() * 0.2f, 0.5f + rnd.nextFloat() * 0.5f, gravity = 14f,
            )
        }
    }

    private fun emitFire() {
        repeat(2) {
            val a = rnd.nextFloat() * 6.283f
            val rr = Tower.BALL_R * 0.7f * rnd.nextFloat()
            val hot = rnd.nextFloat()
            particles.add(
                ballX() + rr * cos(a), sim.ballY + rnd.nextFloat() * 0.2f, ballZ() + rr * sin(a),
                (rnd.nextFloat() - 0.5f) * 1.2f, 2.5f + rnd.nextFloat() * 2f, (rnd.nextFloat() - 0.5f) * 1.2f,
                1f, 0.35f + 0.5f * hot, 0.05f + 0.2f * hot, 1f,
                0.22f + rnd.nextFloat() * 0.18f, 0.35f + rnd.nextFloat() * 0.25f, gravity = -3f,
            )
        }
    }

    private var ambientAcc = 0f
    /** Air particles (part of the detailed scenery). */
    var ambientOn = true

    /** Snow, petals, embers, fireflies or sparkles drifting in the air, by tower look. */
    private fun emitAmbient(dt: Float) {
        val kind = tower.env.ambient
        if (kind == Ambient.NONE || !ambientOn) return
        val rate = when (kind) { Ambient.SNOW -> 28f; Ambient.PETALS -> 9f; Ambient.EMBERS -> 16f; Ambient.FIREFLIES -> 6f; else -> 8f }
        ambientAcc += dt * rate
        val c = tower.env.ambientColor
        while (ambientAcc >= 1f) {
            ambientAcc -= 1f
            val x = (rnd.nextFloat() - 0.5f) * 18f
            val z = -8f + rnd.nextFloat() * 14f
            when (kind) {
                Ambient.SNOW -> particles.add(x, cameraY + 6f, z, 0.3f, -1.5f - rnd.nextFloat(), 0f, c[0], c[1], c[2], 0.55f,
                    0.05f + rnd.nextFloat() * 0.06f, 7f, gravity = 0f)
                Ambient.PETALS -> particles.add(x, cameraY + 6f, z, 0.9f + rnd.nextFloat(), -1.2f, 0.3f, c[0], c[1], c[2], 0.7f,
                    0.09f + rnd.nextFloat() * 0.06f, 8f, gravity = 0f)
                Ambient.EMBERS -> particles.add(x, cameraY - 16f, z, (rnd.nextFloat() - 0.5f) * 0.6f, 2.5f + rnd.nextFloat() * 2f, 0f,
                    c[0], c[1] * (0.6f + 0.4f * rnd.nextFloat()), c[2], 0.9f, 0.05f + rnd.nextFloat() * 0.07f, 7f, gravity = 0f)
                Ambient.FIREFLIES -> particles.add(x, cameraY - 6f + rnd.nextFloat() * 8f, z, (rnd.nextFloat() - 0.5f) * 0.8f,
                    (rnd.nextFloat() - 0.5f) * 0.6f, 0f, c[0], c[1], c[2], 0.9f, 0.07f, 3f + rnd.nextFloat() * 2f, gravity = 0f)
                else -> particles.add(x, cameraY - 8f + rnd.nextFloat() * 12f, z, 0f, 0.3f, 0f, c[0], c[1], c[2], 0.8f,
                    0.04f + rnd.nextFloat() * 0.05f, 1.6f + rnd.nextFloat() * 1.5f, gravity = 0f)
            }
        }
    }

    private fun confetti() {
        val cols = listOf(tower.ringA, tower.ringB, ball.trail, floatArrayOf(1f, 0.85f, 0.2f), floatArrayOf(1f, 1f, 1f))
        repeat(160) {
            val a = rnd.nextFloat() * 6.283f
            val sp = 4f + rnd.nextFloat() * 9f
            val c = cols[rnd.nextInt(cols.size)]
            particles.add(
                ballX(), sim.ballY + 0.3f, ballZ(),
                cos(a) * sp, 6f + rnd.nextFloat() * 9f, sin(a) * sp * 0.6f,
                c[0], c[1], c[2], 1f,
                0.14f + rnd.nextFloat() * 0.16f, 1.2f + rnd.nextFloat() * 0.8f, gravity = 16f,
            )
        }
    }

    /** Ring colour: a soft gradient down the tower between the theme's two colours. */
    fun ringColor(ring: Int): FloatArray = ringColor(ring, FloatArray(3))

    /** Same, written into [out] (no allocation). */
    fun ringColor(ring: Int, out: FloatArray): FloatArray {
        val t = 0.5f + 0.5f * sin(ring * 0.55f)
        val a = tower.ringA
        val b = tower.ringB
        out[0] = a[0] + (b[0] - a[0]) * t; out[1] = a[1] + (b[1] - a[1]) * t; out[2] = a[2] + (b[2] - a[2]) * t
        return out
    }

    private fun updateBallScale() {
        val t = sinceBounce
        val squash = 0.32f * exp(-t * 13f) * cos(t * 28f)
        val stretch = (-sim.vy / Tower.MAX_FALL_V).coerceIn(0f, 1f) * 0.18f
        ballSy = 1f - squash + stretch
        ballSxz = 1f + squash * 0.6f - stretch * 0.45f
    }

    companion object {
        const val TRAIL = 14
        val TWO_PI = (2 * PI).toFloat()
    }
}

/** A pool of glowing particles (positions, velocities, colours) updated on the CPU. */
class Particles(val max: Int) {
    val px = FloatArray(max); val py = FloatArray(max); val pz = FloatArray(max)
    val vx = FloatArray(max); val vy = FloatArray(max); val vz = FloatArray(max)
    val r = FloatArray(max); val g = FloatArray(max); val b = FloatArray(max); val a = FloatArray(max)
    val size = FloatArray(max); val life = FloatArray(max); val maxLife = FloatArray(max); val grav = FloatArray(max)
    var count = 0
        private set

    fun clear() { count = 0 }

    fun add(
        x: Float, y: Float, z: Float, vx0: Float, vy0: Float, vz0: Float,
        cr: Float, cg: Float, cb: Float, ca: Float, s: Float, l: Float, gravity: Float,
    ) {
        val i = if (count < max) count++ else kotlin.random.Random.nextInt(max)
        px[i] = x; py[i] = y; pz[i] = z
        vx[i] = vx0; vy[i] = vy0; vz[i] = vz0
        r[i] = cr; g[i] = cg; b[i] = cb; a[i] = ca
        size[i] = s; life[i] = l; maxLife[i] = l; grav[i] = gravity
    }

    fun update(dt: Float) {
        var i = 0
        val drag = exp(-dt * 1.8f)
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = count - 1
                if (i != last) copy(last, i)
                count--
                continue
            }
            vy[i] -= grav[i] * dt
            vx[i] *= drag; vz[i] *= drag
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * dt
            i++
        }
    }

    private fun copy(from: Int, to: Int) {
        px[to] = px[from]; py[to] = py[from]; pz[to] = pz[from]
        vx[to] = vx[from]; vy[to] = vy[from]; vz[to] = vz[from]
        r[to] = r[from]; g[to] = g[from]; b[to] = b[from]; a[to] = a[from]
        size[to] = size[from]; life[to] = life[from]; maxLife[to] = maxLife[from]; grav[to] = grav[from]
    }
}

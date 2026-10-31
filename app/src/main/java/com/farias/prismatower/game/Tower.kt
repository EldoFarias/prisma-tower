package com.farias.prismatower.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Pure rules of the helix tower (no Android): a central pillar with ring platforms stacked downwards.
 * Every ring is split in [SLICES] slices; the player spins the tower and the ball keeps bouncing at
 * a fixed spot in front of the camera. Units are "world units" shared with the 3D renderer.
 */

/**
 * What a slice of a ring is. GLASS cracks on the first bounce and breaks on the second (the ball falls
 * through). PORTAL swallows the ball and drops it two rings lower.
 */
enum class SliceType { SOLID, GAP, DANGER, GLASS, PORTAL }

/** How a whole ring moves on its own, on top of the tower rotation. */
enum class Motion { NONE, SPIN, SWING }

/** One ring of the tower. [slices] has [Tower.SLICES] entries. The last ring of a level is the goal. */
class Platform(
    val slices: Array<SliceType>,
    val motion: Motion = Motion.NONE,
    /** Radians per second (SPIN) or swing amplitude in radians (SWING). */
    val speed: Float = 0f,
    val phase: Float = 0f,
    val goal: Boolean = false,
    /** Seconds per cycle when the solid slices fade in and out (0 = always there). */
    val blink: Float = 0f,
    /** Slice with a floating coin over it (always a gap or portal), or -1. */
    val coin: Int = -1,
) {
    /** For blinking rings: are the solid slices gone (passable) at time [t]? */
    fun faded(t: Float): Boolean = blink > 0f && sin(t * 6.2832f / blink + phase) < -0.25f

    /** Extra rotation of this ring at time [t] (seconds). */
    fun spin(t: Float): Float = when (motion) {
        Motion.NONE -> 0f
        Motion.SPIN -> speed * t + phase
        Motion.SWING -> speed * sin(t * 1.6f + phase)
    }
}

class TowerLevel(
    val number: Int,
    val platforms: List<Platform>,
    /** Every 10th level: a taller "boss" tower with a big reward. */
    val boss: Boolean = false,
    /** The endless mode tower. */
    val endless: Boolean = false,
) {
    /** Mechanics that appear in this level (for the "new!" notice). */
    val mechanics: Set<SliceType> by lazy {
        val m = HashSet<SliceType>()
        for (p in platforms) for (t in p.slices) if (t == SliceType.GLASS || t == SliceType.PORTAL) m.add(t)
        m
    }
    val hasBlink: Boolean by lazy { platforms.any { it.blink > 0f } }
}

object Tower {
    const val SLICES = 12
    val SLICE_ANGLE = (2 * PI / SLICES).toFloat()

    // Geometry (world units) — shared with the renderer.
    const val SPACING = 3.2f
    const val THICKNESS = 0.42f
    const val PILLAR_R = 0.95f
    const val RING_INNER = 1.0f
    const val RING_OUTER = 2.7f
    const val BALL_PATH_R = 1.85f
    const val BALL_R = 0.36f

    // Physics
    const val GRAVITY = 42f
    const val BOUNCE_V = 12.6f
    const val MAX_FALL_V = 26f

    /** Platforms passed in a row (without touching) that light the ball on fire. */
    const val FIRE_AT = 3

    fun platformTop(index: Int): Float = -index * SPACING + THICKNESS / 2f

    /** Angle in [0, 2π). */
    fun wrap(a: Float): Float {
        val twoPi = (2 * PI).toFloat()
        return a - twoPi * floor(a / twoPi)
    }

    /** Slice index under world angle 0 (the ball) for a ring rotated by [rotation]. */
    fun sliceUnderBall(rotation: Float): Int =
        (floor(wrap(-rotation) / SLICE_ANGLE).toInt()).coerceIn(0, SLICES - 1)
}

// ------------------------------------------------------------------------------------ levels

/** Endless, deterministic levels: the same number always builds the same tower. */
object TowerLevels {

    /** 0 = first levels, 1 = fully hard (reached around level 120). */
    fun difficulty(n: Int): Float = ((n - 1) / 120f).coerceIn(0f, 1f)

    fun platformCount(n: Int): Int = when {
        n <= 2 -> 10
        n <= 5 -> 14
        isBoss(n) -> min(26 + (n * 0.4f).roundToInt(), 64)
        else -> min(16 + (n * 0.35f).roundToInt(), 48)
    }

    /** Level numbers of the boss towers. */
    fun isBoss(n: Int): Boolean = n >= 10 && n % 10 == 0

    // First level each mechanic can appear in.
    const val COINS_FROM = 3
    const val GLASS_FROM = 15
    const val BLINK_FROM = 22
    const val PORTAL_FROM = 30

    /**
     * Endless mode: one very tall tower made of checked levels of rising difficulty glued together.
     * Different every run ([seed]).
     */
    fun endless(seed: Long): TowerLevel {
        val list = ArrayList<Platform>()
        for (c in 0 until 24) {
            val lv = level(4 + c * 6, salt = seed * 31 + c)
            for (p in lv.platforms) if (!p.goal) list.add(p)
        }
        list.add(Platform(Array(Tower.SLICES) { SliceType.SOLID }, goal = true))
        return TowerLevel(0, list, endless = true)
    }

    /** Daily challenge: harder than usual, the same for everyone that day. */
    fun daily(day: Int): TowerLevel = level(40 + day % 50, salt = day * 2_654_435_761L)

    /** Finger speed the levels are checked against (radians/second): comfortable for a person. */
    const val HUMAN_SPEED = 5.5f

    /**
     * Builds level [number]. Every candidate is played by [TowerBot] at human speed; a level is only
     * kept if the bot beats it, so no level is ever impossible.
     */
    fun level(number: Int, salt: Long = 0L): TowerLevel {
        val n = number.coerceAtLeast(1)
        var easing = 0f
        for (attempt in 0 until 40) {
            val lv = build(n, (difficulty(n) - easing).coerceAtLeast(0f), salt + attempt * 104_729L)
            if (TowerBot.beats(lv, HUMAN_SPEED)) return lv
            if (attempt % 4 == 3) easing += 0.1f
        }
        return build(n, 0f, salt)
    }

    private fun build(n: Int, d: Float, salt: Long): TowerLevel {
        val rnd = Random(n * 7_919L + 13 + salt)
        val count = platformCount(n)
        val boss = isBoss(n)
        val list = ArrayList<Platform>(count + 1)
        // Breathing room every few rings, harder clusters in between.
        var prevGap = 0
        for (i in 0 until count) {
            val wave = 0.75f + 0.25f * sin(i * 0.9f + n)
            val p = platform(n, (d * wave + if (boss) 0.12f else 0f).coerceAtMost(1f), i, rnd, prevGap, count)
            list.add(p)
            prevGap = p.slices.indexOfFirst { it == SliceType.GAP }.coerceAtLeast(0)
        }
        list.add(Platform(Array(Tower.SLICES) { SliceType.SOLID }, goal = true))
        return TowerLevel(n, list, boss = isBoss(n))
    }

    private fun platform(n: Int, d: Float, index: Int, rnd: Random, prevGap: Int, count: Int): Platform {
        val s = Array(Tower.SLICES) { SliceType.SOLID }
        // First ring is always friendly.
        if (index == 0) {
            for (k in 0 until 3) s[(4 + k) % Tower.SLICES] = SliceType.GAP
            return Platform(s)
        }
        val pattern = pickPattern(n, d, rnd)
        val start = rnd.nextInt(Tower.SLICES)
        fun set(k: Int, t: SliceType) {
            s[((start + k) % Tower.SLICES + Tower.SLICES) % Tower.SLICES] = t
        }
        val gapW = when {
            d < 0.15f -> 3
            d < 0.45f -> if (rnd.nextFloat() < 0.6f) 2 else 3
            d < 0.75f -> if (rnd.nextFloat() < 0.6f) 2 else 1
            else -> if (rnd.nextFloat() < 0.6f) 1 else 2
        }
        val dangers = when {
            n <= 2 -> 0
            n <= 5 -> 1
            else -> (1 + d * 4.5f + rnd.nextFloat() * 1.5f).toInt()
        }
        when (pattern) {
            0 -> { // classic: one gap, scattered danger
                for (k in 0 until gapW) set(k, SliceType.GAP)
                placeDangers(s, dangers, rnd)
            }
            1 -> { // two gaps facing each other
                val w = maxOf(1, gapW - 1)
                for (k in 0 until w) {
                    set(k, SliceType.GAP)
                    set(6 + k, SliceType.GAP)
                }
                placeDangers(s, dangers, rnd)
            }
            2 -> { // a long danger arc next to the gap
                for (k in 0 until gapW) set(k, SliceType.GAP)
                val arc = (2 + d * 4).toInt().coerceAtMost(6)
                for (k in 0 until arc) set(gapW + 1 + k, SliceType.DANGER)
            }
            3 -> { // checker: danger every other slice around the gap
                for (k in 0 until gapW) set(k, SliceType.GAP)
                var k = gapW + 1
                while (k < Tower.SLICES - 1) {
                    set(k, SliceType.DANGER)
                    k += 2
                }
            }
            else -> { // sparse: lots of gaps, a breather
                for (k in 0 until Tower.SLICES step 3) set(k, SliceType.GAP)
                for (k in 0 until gapW - 1) set(k + 1, SliceType.GAP)
            }
        }
        // Early levels: never put danger right below the previous gap.
        if (d < 0.35f) {
            for (k in -1..1) {
                val idx = ((prevGap + k) % Tower.SLICES + Tower.SLICES) % Tower.SLICES
                if (s[idx] == SliceType.DANGER) s[idx] = SliceType.SOLID
            }
        }
        // Always at least one solid slice to bounce on and one gap.
        if (s.none { it == SliceType.GAP }) s[start] = SliceType.GAP
        if (s.none { it == SliceType.SOLID }) s[(start + 6) % Tower.SLICES] = SliceType.SOLID

        // Glass: some solid slices crack on the first bounce and break on the second.
        if (n >= GLASS_FROM && rnd.nextFloat() < 0.18f + 0.2f * d) {
            val g = 2 + rnd.nextInt(3)
            var placed = 0
            var k = rnd.nextInt(Tower.SLICES)
            var guard = 0
            while (placed < g && guard++ < 24) {
                if (s[k] == SliceType.SOLID) { s[k] = SliceType.GLASS; placed++ }
                k = (k + 1) % Tower.SLICES
            }
        }
        // Portal: a gap that drops the ball two rings lower (never right above the goal).
        if (n >= PORTAL_FROM && index < count - 3 && rnd.nextFloat() < 0.07f + 0.06f * d) {
            val gi = s.indexOfFirst { it == SliceType.GAP }
            if (gi >= 0) s[gi] = SliceType.PORTAL
        }
        // A coin floating over a gap, to grab on the way down.
        var coin = -1
        if (n >= COINS_FROM && rnd.nextFloat() < 0.32f) {
            val gaps = s.indices.filter { s[it] == SliceType.GAP }
            if (gaps.isNotEmpty()) coin = gaps[rnd.nextInt(gaps.size)]
        }
        // Blinking rings: the solid part fades away now and then.
        val blink = if (n >= BLINK_FROM && rnd.nextFloat() < 0.08f + 0.1f * d) 2.4f + rnd.nextFloat() * 1.6f else 0f

        val motion = when {
            n < 12 -> Motion.NONE
            rnd.nextFloat() < 0.10f + 0.25f * d -> if (rnd.nextBoolean()) Motion.SPIN else Motion.SWING
            else -> Motion.NONE
        }
        val speed = when (motion) {
            Motion.NONE -> 0f
            Motion.SPIN -> (0.6f + 1.2f * d) * (if (rnd.nextBoolean()) 1f else -1f)
            Motion.SWING -> 0.6f + 0.6f * d
        }
        return Platform(s, motion, speed, rnd.nextFloat() * 6.28f, blink = blink, coin = coin)
    }

    private fun pickPattern(n: Int, d: Float, rnd: Random): Int {
        if (n <= 2) return if (rnd.nextFloat() < 0.7f) 0 else 4
        val r = rnd.nextFloat()
        return when {
            r < 0.12f -> 4
            r < 0.30f -> 1
            r < 0.30f + 0.25f * d -> 2
            r < 0.36f + 0.40f * d -> 3
            else -> 0
        }
    }

    private fun placeDangers(s: Array<SliceType>, count: Int, rnd: Random) {
        var left = count
        var guard = 0
        while (left > 0 && guard++ < 50) {
            val k = rnd.nextInt(Tower.SLICES)
            if (s[k] != SliceType.SOLID) continue
            // Keep a solid slice on both sides of each gap so landings next to it are fair.
            val l = s[(k + Tower.SLICES - 1) % Tower.SLICES]
            val r = s[(k + 1) % Tower.SLICES]
            if (l == SliceType.GAP || r == SliceType.GAP) continue
            s[k] = SliceType.DANGER
            left--
        }
    }
}

// ------------------------------------------------------------------------------------ simulation

/** Something that happened during a step, for sounds, effects and the UI. */
sealed class TowerEvent {
    /** Bounced on [platform]; [localAngle] is where on the ring (for the paint splat). */
    data class Bounce(val platform: Int, val localAngle: Float, val near: Boolean = false, val cracked: Boolean = false) : TowerEvent()
    /** Fell through the gap of [platform]; [combo] = rings passed in a row so far. */
    data class Pass(val platform: Int, val combo: Int, val points: Int, val near: Boolean = false) : TowerEvent()
    /** A cracked glass slice of [platform] broke under the ball. */
    data class GlassBreak(val platform: Int) : TowerEvent()
    /** The ball went into a portal on [platform] and came out two rings lower. */
    data class Portal(val platform: Int, val points: Int) : TowerEvent()
    /** Grabbed the coin floating over [platform]. */
    data class Coin(val platform: Int) : TowerEvent()
    /** A shield saved the ball from the danger slice of [platform] (the ring breaks). */
    data class Shield(val platform: Int) : TowerEvent()
    /** The burning ball smashed [platform]. */
    data class Smash(val platform: Int, val points: Int) : TowerEvent()
    /** Touched a danger slice of [platform]. */
    data class Die(val platform: Int) : TowerEvent()
    object Win : TowerEvent()
    object FireOn : TowerEvent()
}

/**
 * The ball and the tower over time. The UI feeds the tower rotation; [step] moves the ball and
 * reports what happened. Rings above [next] are gone (passed or smashed).
 */
class TowerSim(val level: TowerLevel) {
    var rotation = 0f
    var time = 0f
        private set
    var ballY = Tower.platformTop(0) + 2.2f
        private set
    var vy = 0f
        private set
    /** First ring still on the tower. */
    var next = 0
        private set
    /** Rings passed in a row without bouncing. */
    var combo = 0
        private set
    var score = 0
        private set
    var dead = false
        private set
    var won = false
        private set
    val onFire: Boolean get() = combo >= Tower.FIRE_AT
    /** Hits on red slices it can survive (bought with a video before the level). */
    var shields = 0
    /** Coins grabbed in this level. */
    var coins = 0
        private set
    /** Glass state per slice: 0 whole, 1 cracked, 2 broken. */
    val glass = ByteArray(level.platforms.size * Tower.SLICES)
    private val coinTaken = BooleanArray(level.platforms.size)

    fun coinTaken(i: Int): Boolean = coinTaken[i]

    /** What slice [k] of ring [i] behaves like at time [t] (glass state, fading rings). */
    fun typeAt(i: Int, k: Int, t: Float = time): SliceType {
        val p = level.platforms[i]
        val type = p.slices[k]
        return when {
            type == SliceType.GLASS && glass[i * Tower.SLICES + k] >= 2 -> SliceType.GAP
            type == SliceType.SOLID && p.faded(t) -> SliceType.GAP
            else -> type
        }
    }

    /** Is the slice next to [slice] (on the side the ball is closest to) a red one? For slow motion. */
    private fun nearDanger(i: Int, slice: Int): Boolean {
        val local = Tower.wrap(-ringRotation(i)) / Tower.SLICE_ANGLE - slice // 0..1 within the slice
        val left = level.platforms[i].slices[(slice + Tower.SLICES - 1) % Tower.SLICES] == SliceType.DANGER
        val right = level.platforms[i].slices[(slice + 1) % Tower.SLICES] == SliceType.DANGER
        return (left && local < 0.3f) || (right && local > 0.7f)
    }

    val total: Int get() = level.platforms.size - 1
    /** 0..1 progress to the goal. */
    val progress: Float get() = (next.toFloat() / total).coerceIn(0f, 1f)

    /** Rotation of ring [i] in the world right now (tower + its own motion). */
    fun ringRotation(i: Int): Float = rotation + level.platforms[i].spin(time)

    /** Slice of ring [i] under the ball right now. */
    fun sliceUnderBall(i: Int): Int = Tower.sliceUnderBall(ringRotation(i))

    fun step(dt: Float, events: MutableList<TowerEvent>) {
        if (dead || won) return
        time += dt
        vy = (vy - Tower.GRAVITY * dt).coerceAtLeast(-Tower.MAX_FALL_V)
        val oldBottom = ballY - Tower.BALL_R
        ballY += vy * dt
        if (vy > 0f) return
        // Cross every ring top passed this step (fast falls can cross more than one).
        while (next < level.platforms.size) {
            val top = Tower.platformTop(next)
            val bottom = ballY - Tower.BALL_R
            if (!(oldBottom >= top && bottom <= top)) break
            val p = level.platforms[next]
            if (p.goal) {
                ballY = top + Tower.BALL_R
                vy = Tower.BOUNCE_V * 0.7f
                won = true
                score += 10 * (1 + combo)
                events.add(TowerEvent.Win)
                return
            }
            val slice = sliceUnderBall(next)
            if (p.coin == slice && !coinTaken[next]) {
                coinTaken[next] = true
                coins++
                events.add(TowerEvent.Coin(next))
            }
            val gi = next * Tower.SLICES + slice
            when (typeAt(next, slice)) {
                SliceType.GAP -> {
                    combo++
                    val pts = combo
                    score += pts
                    events.add(TowerEvent.Pass(next, combo, pts, nearDanger(next, slice)))
                    if (combo == Tower.FIRE_AT) events.add(TowerEvent.FireOn)
                    next++
                }
                SliceType.PORTAL -> {
                    // Drops two rings at once: both count as passed.
                    val pts = 3 + combo * 2
                    combo += 2
                    score += pts
                    events.add(TowerEvent.Portal(next, pts))
                    val skip = minOf(2, level.platforms.size - 1 - next)
                    next += skip
                    ballY = Tower.platformTop(next) + 1.4f
                    vy = minOf(vy, -Tower.BOUNCE_V * 0.4f)
                    if (combo >= Tower.FIRE_AT && combo - 2 < Tower.FIRE_AT) events.add(TowerEvent.FireOn)
                    return
                }
                SliceType.GLASS, SliceType.SOLID, SliceType.DANGER -> {
                    val type = typeAt(next, slice)
                    if (onFire) {
                        val pts = 2 * combo
                        score += pts
                        events.add(TowerEvent.Smash(next, pts))
                        combo = 0
                        next++
                        // The smash eats some speed: land softly on the next ring.
                        vy = maxOf(vy, -Tower.BOUNCE_V * 0.6f)
                        return
                    }
                    if (type == SliceType.DANGER) {
                        if (shields > 0) {
                            shields--
                            events.add(TowerEvent.Shield(next))
                            combo = 0
                            next++
                            vy = Tower.BOUNCE_V * 0.4f
                            return
                        }
                        ballY = top + Tower.BALL_R
                        vy = 0f
                        dead = true
                        events.add(TowerEvent.Die(next))
                        return
                    }
                    if (type == SliceType.GLASS) {
                        if (glass[gi] >= 1) {
                            // Second hit: it shatters and the ball keeps falling.
                            glass[gi] = 2
                            events.add(TowerEvent.GlassBreak(next))
                            combo++
                            score += combo
                            events.add(TowerEvent.Pass(next, combo, combo, false))
                            if (combo == Tower.FIRE_AT) events.add(TowerEvent.FireOn)
                            next++
                            continue
                        }
                        glass[gi] = 1
                    }
                    ballY = top + Tower.BALL_R
                    vy = Tower.BOUNCE_V
                    combo = 0
                    val local = Tower.wrap(-ringRotation(next))
                    events.add(TowerEvent.Bounce(next, local, nearDanger(next, slice), type == SliceType.GLASS))
                    return
                }
            }
        }
    }

    /** Second chance after a rewarded video: the ring that killed the ball is cleared. */
    fun revive(events: MutableList<TowerEvent>) {
        if (!dead) return
        dead = false
        val pts = 1
        score += pts
        events.add(TowerEvent.Smash(next, pts))
        next++
        vy = Tower.BOUNCE_V * 0.5f
        combo = 0
    }

    /** Angle offset (radians) from the ball to the centre of the nearest gap of ring [i] (for bots and hints). */
    fun nearestGapOffset(i: Int): Float? {
        val p = level.platforms.getOrNull(i) ?: return null
        val rot = ringRotation(i)
        var best: Float? = null
        for (k in 0 until Tower.SLICES) {
            if (p.slices[k] != SliceType.GAP && p.slices[k] != SliceType.PORTAL) continue
            val center = (k + 0.5f) * Tower.SLICE_ANGLE
            // World angle of that slice centre is center + rot; the ball is at 0.
            var delta = Tower.wrap(center + rot)
            if (delta > PI) delta -= (2 * PI).toFloat()
            // Rotation needed to bring it to the ball: -delta.
            if (best == null || abs(delta) < abs(best)) best = delta
        }
        return best?.let { -it }
    }

    companion object {
        /** Sanity helper: time (s) for a free fall of [h] units. */
        fun fallTime(h: Float): Float = sqrt(2 * h / Tower.GRAVITY)
    }
}

/**
 * A careful player: before each landing it picks a slice it can reach in time — a gap if possible,
 * otherwise a safe solid slice — and turns the tower at most [maxSpeed] rad/s. Used to validate levels.
 */
object TowerBot {

    fun beats(level: TowerLevel, maxSpeed: Float): Boolean {
        val sim = TowerSim(level)
        val ev = ArrayList<TowerEvent>()
        val dt = 1f / 60f
        var t = 0f
        while (!sim.won && !sim.dead && t < 240f) {
            sim.rotation += turn(sim, maxSpeed, dt)
            ev.clear()
            sim.step(dt, ev)
            t += dt
        }
        return sim.won
    }

    /** Rotation to apply this frame. */
    fun turn(sim: TowerSim, maxSpeed: Float, dt: Float): Float {
        val i = sim.next
        val p = sim.level.platforms.getOrNull(i) ?: return 0f
        if (p.goal) return 0f
        val tl = timeToLand(sim, i)
        val reach = maxSpeed * tl * 0.85f
        val landTime = sim.time + tl
        var bestGap: Float? = null
        var bestSafe: Float? = null
        for (k in 0 until Tower.SLICES) {
            val type = sim.typeAt(i, k, landTime)
            if (type == SliceType.DANGER) continue
            // Tower rotation that puts slice k's centre under the ball at landing time.
            val ringRot = -((k + 0.5f) * Tower.SLICE_ANGLE)
            var delta = Tower.wrap(ringRot - p.spin(landTime) - sim.rotation)
            if (delta > PI) delta -= (2 * PI).toFloat()
            if (abs(delta) > reach) continue
            if (type == SliceType.GAP || type == SliceType.PORTAL) {
                if (bestGap == null || abs(delta) < abs(bestGap)) bestGap = delta
            } else if (bestSafe == null || abs(delta) < abs(bestSafe)) bestSafe = delta
        }
        val target = bestGap ?: bestSafe ?: (sim.nearestGapOffset(i) ?: 0f)
        return target.coerceIn(-maxSpeed * dt, maxSpeed * dt)
    }

    /** Seconds until the ball bottom reaches the top of ring [i]. */
    private fun timeToLand(sim: TowerSim, i: Int): Float {
        val h = sim.ballY - Tower.BALL_R - Tower.platformTop(i)
        val g = Tower.GRAVITY
        val v = sim.vy
        // h + v t - g/2 t² = 0
        val disc = v * v + 2 * g * h
        return if (disc <= 0f) 0f else ((v + sqrt(disc)) / g).coerceAtLeast(0f)
    }
}

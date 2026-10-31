import com.farias.prismatower.game.*
import kotlin.math.abs

/** Bot: every frame rotates the tower (limited speed, like a finger) to put the nearest gap of the next ring under the ball. */
fun play(level: TowerLevel, maxRotSpeed: Float = 5.5f): Pair<Boolean, Float> {
    val sim = TowerSim(level)
    val ev = ArrayList<TowerEvent>()
    val dt = 1f / 60f
    var t = 0f
    while (!sim.won && !sim.dead && t < 300f) {
        sim.rotation += TowerBot.turn(sim, maxRotSpeed, dt)
        ev.clear()
        sim.step(dt, ev)
        t += dt
    }
    return sim.won to t
}

fun main() {
    var fails = 0
    var maxT = 0f
    val t0 = System.currentTimeMillis()
    for (n in 1..400) {
        val lv = TowerLevels.level(n)
        val (won, t) = play(lv)
        if (!won) { fails++; if (fails < 10) println("FAIL level $n") }
        maxT = maxOf(maxT, t)
        if (n in listOf(1, 2, 5, 10, 20, 50, 100, 200, 400)) {
            val dangers = lv.platforms.sumOf { p -> p.slices.count { it == SliceType.DANGER } }
            val moving = lv.platforms.count { it.motion != Motion.NONE }
            println("L$n rings=${lv.platforms.size - 1} dangers=$dangers moving=$moving botTime=${"%.1f".format(t)}s won=$won")
        }
    }
    // Daily
    for (d in 0 until 30) if (!play(TowerLevels.daily(20261000 + d)).first) { fails++; println("FAIL daily $d") }
    // Determinism
    val a = TowerLevels.level(77); val b = TowerLevels.level(77)
    check(a.platforms.indices.all { a.platforms[it].slices.contentEquals(b.platforms[it].slices) }) { "not deterministic" }
    // Danger kills, fire smashes
    println("fails=$fails maxBotTime=${"%.1f".format(maxT)}s gen+play=${System.currentTimeMillis() - t0}ms")
    if (fails > 0) kotlin.system.exitProcess(1)
}

package com.farias.prismatower.game

import java.util.concurrent.Executors

/**
 * Levels are generated and checked by the bot, which takes a few milliseconds on a phone. This keeps
 * the next ones ready (built on a background thread) so starting a level never stutters.
 */
object LevelCache {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "levels").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val cache = LinkedHashMap<Long, TowerLevel>(16, 0.75f, true)
    private val pending = HashSet<Long>()

    private fun key(n: Int, salt: Long) = n.toLong() * 1_000_003L + salt

    /** The level, from the cache when ready, else built now. */
    fun get(n: Int, salt: Long = 0L, build: () -> TowerLevel = { TowerLevels.level(n, salt) }): TowerLevel {
        val k = key(n, salt)
        synchronized(this) { cache[k]?.let { return it } }
        val lv = build()
        put(k, lv)
        return lv
    }

    fun daily(day: Int): TowerLevel = get(-day, 7L) { TowerLevels.daily(day) }

    /** Builds a level in the background if it isn't ready yet. */
    fun prefetch(n: Int) {
        val k = key(n, 0L)
        synchronized(this) {
            if (cache.containsKey(k) || !pending.add(k)) return
        }
        worker.execute {
            val lv = TowerLevels.level(n)
            put(k, lv)
            synchronized(this) { pending.remove(k) }
        }
    }

    /** Returns a ready level if there is one (never blocks). */
    fun ready(n: Int): TowerLevel? = synchronized(this) { cache[key(n, 0L)] }

    private fun put(k: Long, lv: TowerLevel) = synchronized(this) {
        cache[k] = lv
        while (cache.size > 12) cache.remove(cache.keys.first())
    }
}

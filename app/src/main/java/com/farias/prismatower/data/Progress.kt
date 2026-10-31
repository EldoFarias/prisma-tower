package com.farias.prismatower.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

/**
 * Everything the player keeps between sessions: level, coins, themes, records, missions, chest and
 * settings. Backed by SharedPreferences and exposed as Compose state so screens update by themselves.
 */
object Progress {

    private const val PREFS = "prisma_tower"
    private lateinit var prefs: SharedPreferences

    /** Next level to play (1-based). */
    var level by mutableIntStateOf(1)
        private set
    var coins by mutableIntStateOf(0)
        private set
    var soundOn by mutableStateOf(true)
        private set
    var musicOn by mutableStateOf(true)
        private set
    var vibrationOn by mutableStateOf(true)
        private set
    /** Scenery, clouds and air particles. Turned off by itself on slow phones. */
    var detailedScenery by mutableStateOf(true)
        private set
    var notificationsOn by mutableStateOf(true)
        private set
    var notifAsked by mutableStateOf(false)
        private set
    var towerId by mutableStateOf("neon")
        private set
    var ballId by mutableStateOf("pearl")
        private set
    var ownedTowers by mutableStateOf(setOf("neon"))
        private set
    var ownedBalls by mutableStateOf(setOf("pearl"))
        private set
    var tutorialDone by mutableStateOf(false)
        private set
    var lastReviewAsk by mutableStateOf(0L)
        private set

    // Statistics
    var levelsWon by mutableIntStateOf(0)
        private set
    var levelsLost by mutableIntStateOf(0)
        private set
    var ringsPassed by mutableIntStateOf(0)
        private set
    var ringsSmashed by mutableIntStateOf(0)
        private set
    var bestScore by mutableIntStateOf(0)
        private set
    var bestCombo by mutableIntStateOf(0)
        private set
    /** Most consecutive wins without losing. */
    var winStreak by mutableIntStateOf(0)
        private set
    var bestWinStreak by mutableIntStateOf(0)
        private set

    // Daily missions
    private var missionDay = 0
    var missionProgress by mutableStateOf(List(Missions.COUNT) { 0 })
        private set
    var missionClaimed by mutableStateOf(List(Missions.COUNT) { false })
        private set

    // Daily chest (7-day calendar)
    var chestLastDay by mutableIntStateOf(0)
        private set
    var chestRun by mutableIntStateOf(0)
        private set

    val isReady: Boolean get() = ::prefs.isInitialized

    fun init(context: Context) {
        if (isReady) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        level = prefs.getInt("level", 1)
        coins = prefs.getInt("coins", 100) // a small welcome gift
        soundOn = prefs.getBoolean("sound", true)
        musicOn = prefs.getBoolean("music", true)
        vibrationOn = prefs.getBoolean("vibration", true)
        detailedScenery = prefs.getBoolean("detailed_scenery", true)
        notificationsOn = prefs.getBoolean("notifications", true)
        notifAsked = prefs.getBoolean("notif_asked", false)
        towerId = prefs.getString("tower", "neon") ?: "neon"
        ballId = prefs.getString("ball", "pearl") ?: "pearl"
        ownedTowers = prefs.getStringSet("owned_towers", setOf("neon"))?.toSet() ?: setOf("neon")
        ownedBalls = prefs.getStringSet("owned_balls", setOf("pearl"))?.toSet() ?: setOf("pearl")
        tutorialDone = prefs.getBoolean("tutorial_done", false)
        lastReviewAsk = prefs.getLong("last_review_ask", 0L)
        levelsWon = prefs.getInt("levels_won", 0)
        levelsLost = prefs.getInt("levels_lost", 0)
        ringsPassed = prefs.getInt("rings_passed", 0)
        ringsSmashed = prefs.getInt("rings_smashed", 0)
        bestScore = prefs.getInt("best_score", 0)
        bestCombo = prefs.getInt("best_combo", 0)
        winStreak = prefs.getInt("win_streak", 0)
        bestWinStreak = prefs.getInt("best_win_streak", 0)
        missionDay = prefs.getInt("mission_day", 0)
        missionProgress = (prefs.getString("mission_progress", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
            .let { if (it.size == Missions.COUNT) it else List(Missions.COUNT) { 0 } }
        missionClaimed = (prefs.getString("mission_claimed", "") ?: "").split(",").map { it == "1" }
            .let { if (it.size == Missions.COUNT) it else List(Missions.COUNT) { false } }
        chestLastDay = prefs.getInt("chest_last_day", 0)
        chestRun = prefs.getInt("chest_run", 0)
        dailyLastDay = prefs.getInt("daily_last_day", 0)
        dailyRun = prefs.getInt("daily_run", 0)
    }

    // ------------------------------------------------------------------ coins

    fun addCoins(amount: Int) {
        if (amount <= 0) return
        coins += amount
        prefs.edit().putInt("coins", coins).apply()
    }

    fun spend(amount: Int): Boolean {
        if (coins < amount) return false
        coins -= amount
        prefs.edit().putInt("coins", coins).apply()
        return true
    }

    // ------------------------------------------------------------------ levels and records

    /** Records a won level: advances to the next one and updates records. */
    fun onLevelWon(number: Int, daily: Boolean) {
        if (!daily && number >= level) level = number + 1
        levelsWon += 1
        winStreak += 1
        bestWinStreak = maxOf(bestWinStreak, winStreak)
        prefs.edit()
            .putInt("level", level)
            .putInt("levels_won", levelsWon)
            .putInt("win_streak", winStreak)
            .putInt("best_win_streak", bestWinStreak)
            .apply()
    }

    /** Coins for finishing level [n]. */
    fun levelReward(n: Int): Int = (10 + n / 3).coerceAtMost(60)

    /** Returns true when [score] is a new best. */
    fun onScore(score: Int): Boolean {
        if (score <= bestScore) return false
        bestScore = score
        prefs.edit().putInt("best_score", score).apply()
        return true
    }

    fun onLevelLost() {
        levelsLost += 1
        winStreak = 0
        prefs.edit().putInt("levels_lost", levelsLost).putInt("win_streak", 0).apply()
    }

    fun onRings(passed: Int, smashed: Int, combo: Int) {
        ringsPassed += passed
        ringsSmashed += smashed
        bestCombo = maxOf(bestCombo, combo)
        prefs.edit().putInt("rings_passed", ringsPassed).putInt("rings_smashed", ringsSmashed)
            .putInt("best_combo", bestCombo).apply()
    }

    // ------------------------------------------------------------------ settings

    fun toggleSound() {
        soundOn = !soundOn
        prefs.edit().putBoolean("sound", soundOn).apply()
    }

    fun toggleMusic() {
        musicOn = !musicOn
        prefs.edit().putBoolean("music", musicOn).apply()
    }

    fun setDetailedScenery(on: Boolean) {
        detailedScenery = on
        prefs.edit().putBoolean("detailed_scenery", on).apply()
    }

    fun toggleVibration() {
        vibrationOn = !vibrationOn
        prefs.edit().putBoolean("vibration", vibrationOn).apply()
    }

    fun setNotifications(on: Boolean) {
        notificationsOn = on
        prefs.edit().putBoolean("notifications", on).apply()
    }

    fun markNotifAsked() {
        notifAsked = true
        prefs.edit().putBoolean("notif_asked", true).apply()
    }

    fun markTutorial(done: Boolean) {
        tutorialDone = done
        prefs.edit().putBoolean("tutorial_done", done).apply()
    }

    fun markReviewAsked(now: Long) {
        lastReviewAsk = now
        prefs.edit().putLong("last_review_ask", now).apply()
    }

    // ------------------------------------------------------------------ towers and balls

    fun ownsTower(id: String) = id in ownedTowers
    fun ownsBall(id: String) = id in ownedBalls

    fun buyTower(id: String, price: Int): Boolean {
        if (ownsTower(id)) return true
        if (!spend(price)) return false
        ownedTowers = ownedTowers + id
        prefs.edit().putStringSet("owned_towers", ownedTowers).apply()
        return true
    }

    fun buyBall(id: String, price: Int): Boolean {
        if (ownsBall(id)) return true
        if (!spend(price)) return false
        ownedBalls = ownedBalls + id
        prefs.edit().putStringSet("owned_balls", ownedBalls).apply()
        return true
    }

    /** Unlocks without charging (mystery prize). */
    fun giveTower(id: String) {
        ownedTowers = ownedTowers + id
        prefs.edit().putStringSet("owned_towers", ownedTowers).apply()
    }

    fun giveBall(id: String) {
        ownedBalls = ownedBalls + id
        prefs.edit().putStringSet("owned_balls", ownedBalls).apply()
    }

    fun selectTower(id: String) {
        if (!ownsTower(id)) return
        towerId = id
        prefs.edit().putString("tower", id).apply()
    }

    fun selectBall(id: String) {
        if (!ownsBall(id)) return
        ballId = id
        prefs.edit().putString("ball", id).apply()
    }

    // ------------------------------------------------------------------ daily missions

    /** Read-only, safe to call while drawing the UI. */
    fun todaysMissions(): List<Mission> = Missions.forDay(today())

    fun missionProgressOf(index: Int): Int = if (missionDay == today()) missionProgress.getOrElse(index) { 0 } else 0

    fun missionClaimedOf(index: Int): Boolean = missionDay == today() && missionClaimed.getOrElse(index) { false }

    /** Starts a fresh set of missions when the day changes. Call from events, not from drawing. */
    fun ensureMissionDay() {
        val t = today()
        if (missionDay == t) return
        missionDay = t
        missionProgress = List(Missions.COUNT) { 0 }
        missionClaimed = List(Missions.COUNT) { false }
        saveMissions()
    }

    private fun saveMissions() {
        prefs.edit()
            .putInt("mission_day", missionDay)
            .putString("mission_progress", missionProgress.joinToString(","))
            .putString("mission_claimed", missionClaimed.joinToString(",") { if (it) "1" else "0" })
            .apply()
    }

    /** Reports progress for missions of [type]. Returns the missions completed by this event. */
    fun missionEvent(type: MissionType, value: Int, asMax: Boolean = false): List<Mission> {
        if (value <= 0) return emptyList()
        ensureMissionDay()
        val missions = todaysMissions()
        val progress = missionProgress.toMutableList()
        val done = ArrayList<Mission>()
        var changed = false
        missions.forEachIndexed { i, m ->
            if (m.type != type || progress[i] >= m.target) return@forEachIndexed
            val next = (if (asMax) maxOf(progress[i], value) else progress[i] + value).coerceAtMost(m.target)
            if (next != progress[i]) {
                progress[i] = next
                changed = true
                if (next >= m.target) done.add(m)
            }
        }
        if (changed) {
            missionProgress = progress
            saveMissions()
        }
        return done
    }

    val claimableMissions: Int
        get() {
            val missions = todaysMissions()
            return missions.indices.count { missionProgressOf(it) >= missions[it].target && !missionClaimedOf(it) }
        }

    fun claimMission(index: Int): Int {
        ensureMissionDay()
        val missions = todaysMissions()
        val m = missions.getOrNull(index) ?: return 0
        if (missionProgress[index] < m.target || missionClaimed[index]) return 0
        missionClaimed = missionClaimed.toMutableList().also { it[index] = true }
        saveMissions()
        addCoins(m.reward)
        return m.reward
    }

    // ------------------------------------------------------------------ daily challenge

    const val DAILY_REWARD = 60
    var dailyLastDay by mutableIntStateOf(0)
        private set
    var dailyRun by mutableIntStateOf(0)
        private set

    fun dailyDoneToday(): Boolean = dailyLastDay == today()

    /** Days in a row with the challenge done (still counts until the end of today). */
    fun dailyStreak(): Int = if (dailyLastDay == today() || dailyLastDay == yesterday()) dailyRun else 0

    /** Coins for today's challenge: grows a little with the streak. */
    fun dailyReward(): Int = DAILY_REWARD + 10 * dailyStreak().coerceAtMost(7)

    fun onDailyWon(): Int {
        if (dailyDoneToday()) return 0
        val reward = dailyReward()
        dailyRun = if (dailyLastDay == yesterday()) dailyRun + 1 else 1
        dailyLastDay = today()
        prefs.edit().putInt("daily_last_day", dailyLastDay).putInt("daily_run", dailyRun).apply()
        addCoins(reward)
        return reward
    }

    // ------------------------------------------------------------------ daily chest

    val CHEST_REWARDS = listOf(20, 30, 40, 50, 70, 90, 150)

    fun chestAvailable(): Boolean = chestLastDay != today()

    fun chestIndex(): Int = if (chestLastDay == yesterday()) chestRun % CHEST_REWARDS.size else 0

    fun openChest(multiplier: Int): Int {
        if (!chestAvailable()) return 0
        val idx = chestIndex()
        val reward = CHEST_REWARDS[idx] * multiplier
        chestRun = if (chestLastDay == yesterday()) chestRun + 1 else 1
        chestLastDay = today()
        prefs.edit().putInt("chest_last_day", chestLastDay).putInt("chest_run", chestRun).apply()
        addCoins(reward)
        return reward
    }

    // ------------------------------------------------------------------ dates (yyyyMMdd, local time)

    fun today(): Int = dayOf(Calendar.getInstance())

    fun yesterday(): Int = dayOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) })

    private fun dayOf(c: Calendar): Int =
        c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

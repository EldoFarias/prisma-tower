package com.farias.prismatower

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.farias.prismatower.ads.AdsManager
import com.farias.prismatower.audio.MusicPlayer
import com.farias.prismatower.audio.SoundManager
import com.farias.prismatower.data.Progress
import com.farias.prismatower.game.GameHost
import com.farias.prismatower.notify.DailyReminder
import com.farias.prismatower.ui.GameScreen
import com.farias.prismatower.ui.HomeScreen
import com.farias.prismatower.ui.Scaled
import com.farias.prismatower.ui.ShopScreen
import com.farias.prismatower.ui.SplashScreen
import com.farias.prismatower.ui.StatsScreen
import com.farias.prismatower.ui.TowerView
import com.farias.prismatower.ui.skipChestToday

enum class Screen { SPLASH, HOME, GAME, SHOP, STATS }

class MainActivity : ComponentActivity() {

    private lateinit var ads: AdsManager
    private lateinit var host: GameHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Progress.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        DailyReminder.createChannel(this)
        DailyReminder.sync(this)
        ads = AdsManager(this)
        ads.start()
        val sound = SoundManager.get(this)
        MusicPlayer.init(this)
        // Build the player's next level (and a few for the menu demo) in the background right away.
        com.farias.prismatower.game.LevelCache.prefetch(Progress.level)
        for (n in listOf(5, 9, 14, 21)) com.farias.prismatower.game.LevelCache.prefetch(n)
        host = GameHost(this)
        host.showMenu()

        val initial = debugStartScreen()

        setContent {
            var screen by remember { mutableStateOf(initial) }
            fun go(s: Screen) {
                if (s == Screen.HOME && screen == Screen.GAME) host.showMenu()
                screen = s
            }
            val sw = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp
            val appScale = if (sw >= 600) (sw / 420f).coerceIn(1f, 1.6f) else 1f
            Box(Modifier.fillMaxSize()) {
                // The 3D tower lives behind every screen: a demo on the menus, the real game while playing.
                TowerView(host, Modifier.fillMaxSize())
                Scaled(appScale) {
                    Crossfade(targetState = screen, animationSpec = tween(300), label = "screen") { current ->
                        when (current) {
                            Screen.SPLASH -> {
                                androidx.compose.runtime.DisposableEffect(Unit) {
                                    host.renderPaused = true
                                    onDispose { host.renderPaused = false }
                                }
                                SplashScreen(sound) { host.renderPaused = false; go(Screen.HOME) }
                            }
                            Screen.HOME -> HomeScreen(
                                ads = ads,
                                sound = sound,
                                onPlay = {
                                    ads.onNewGame {
                                        host.play(Progress.level)
                                        go(Screen.GAME)
                                    }
                                },
                                onDaily = {
                                    ads.onNewGame {
                                        host.playDaily()
                                        go(Screen.GAME)
                                    }
                                },
                                onShop = { go(Screen.SHOP) },
                                onStats = { go(Screen.STATS) },
                            )
                            Screen.GAME -> GameScreen(
                                host = host,
                                ads = ads,
                                sound = sound,
                                onGreatMoment = { ReviewPrompt.maybeAsk(this@MainActivity) },
                                onHome = { go(Screen.HOME) },
                            )
                            Screen.SHOP -> ShopScreen(host, ads, sound) { go(Screen.HOME) }
                            Screen.STATS -> {
                                // The statistics screen covers the 3D view completely: don't draw it.
                                androidx.compose.runtime.DisposableEffect(Unit) {
                                    host.renderPaused = true
                                    onDispose { host.renderPaused = false }
                                }
                                StatsScreen { go(Screen.HOME) }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Debug builds only: open a screen directly, e.g.
     * `adb shell am start -n <pkg>/com.farias.prismatower.MainActivity --es screen game --ei level 12`.
     */
    private fun debugStartScreen(): Screen {
        if (!BuildConfig.DEBUG) return Screen.SPLASH
        val target = intent.getStringExtra("screen") ?: return Screen.SPLASH
        if (intent.getBooleanExtra("landscape", false)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (target != "chest") skipChestToday()
        return when (target) {
            "game" -> {
                host.play(intent.getIntExtra("level", 1))
                Screen.GAME
            }
            "daily" -> {
                host.playDaily()
                Screen.GAME
            }
            "shop" -> Screen.SHOP
            "stats" -> Screen.STATS
            else -> Screen.HOME
        }
    }

    override fun onResume() {
        super.onResume()
        Progress.ensureMissionDay()
        MusicPlayer.onResume()
        host.view?.onResume()
    }

    override fun onPause() {
        super.onPause()
        MusicPlayer.onPause()
        host.view?.onPause()
        if (host.phase == GameHost.Phase.PLAYING && host.touched) host.pauseGame(true)
    }
}

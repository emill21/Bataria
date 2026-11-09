package com.batterypulse.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.batterypulse.app.data.Changelog
import com.batterypulse.app.data.Prefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sampler.schedule(this)
        val prefs = Prefs(this)
        val latest = BuildConfig.VERSION_CODE
        setContent {
            BatariaWearTheme {
                var screen by remember {
                    mutableStateOf(
                        when {
                            !prefs.onboardingDone -> Screen.Welcome
                            prefs.lastSeenVersion < latest && Changelog.load(this).isNotEmpty() -> Screen.WhatsNew
                            else -> Screen.Main
                        }
                    )
                }
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) }, label = "root") { s ->
                    when (s) {
                        Screen.Welcome -> WelcomeScreen {
                            prefs.onboardingDone = true
                            prefs.lastSeenVersion = latest
                            screen = Screen.Main
                        }
                        Screen.WhatsNew -> WhatsNewScreen(Changelog.load(this@MainActivity).take(2)) {
                            prefs.lastSeenVersion = latest
                            screen = Screen.Main
                        }
                        Screen.Main -> BatariaWearApp(
                            onReplayIntro = { screen = Screen.Welcome },
                            onWhatsNew = { screen = Screen.WhatsNew },
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Sampler.refreshSurfaces(this)
    }

    enum class Screen { Welcome, WhatsNew, Main }
}

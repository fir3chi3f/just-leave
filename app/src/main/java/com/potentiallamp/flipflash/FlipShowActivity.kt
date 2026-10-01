package com.potentiallamp.flipflash

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch

/**
 * Launch this from the Razr's cover screen. It waits ("armed") until the phone is flipped open,
 * then plays [ShowScript]. Press and hold to freeze the frame; release to close.
 *
 * Flip-open is detected two ways, whichever fires first:
 *  1. Jetpack WindowManager reports a hinge ([FoldingFeature]) — only the main screen has one.
 *  2. The window's smallest width jumps up, i.e. we moved from the small screen to the big one.
 */
class FlipShowActivity : ComponentActivity() {

    private val controller = ShowController()
    private lateinit var showView: FlipShowView

    /** Smallest width (dp) of the window when the activity started. */
    private var startSmallestWidthDp = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startSmallestWidthDp = resources.configuration.smallestScreenWidthDp

        showView = FlipShowView(this, controller, onClose = ::finishAndRemoveTask)
        setContentView(showView)
        hideSystemBars()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@FlipShowActivity)
                    .windowLayoutInfo(this@FlipShowActivity)
                    .collect { info ->
                        if (info.displayFeatures.any { it is FoldingFeature }) flipOpened()
                    }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.smallestScreenWidthDp > startSmallestWidthDp * GROWTH_THRESHOLD) flipOpened()
        // System bars can come back when moving between displays.
        hideSystemBars()
    }

    private fun flipOpened() {
        controller.onFlipOpened(SystemClock.uptimeMillis())
        showView.refresh()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        /** Main screen is far wider than the cover screen; 25% growth is a safe signal. */
        const val GROWTH_THRESHOLD = 1.25f
    }
}

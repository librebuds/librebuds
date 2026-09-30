/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
    Modified for LibreBuds (2026): adapted to FreeBuds; see NOTICE.
*/


package io.github.librebuds.popup

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.core.net.toUri
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.R
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import io.github.librebuds.ui.model.BatteryStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The case-open popup: a card at the bottom of the screen with the model name, its artwork and the
 * left/right/case battery. It only shows information; it never connects to or takes over the earbuds.
 * All calls must come from the main thread.
 */
@SuppressLint("InflateParams", "ClickableViewAccessibility")
class PopupWindow(
    context: Context,
    private val onCloseCallback: () -> Unit = {}
) : CasePopup {
    // A receiver's context is a restricted wrapper; the application context outlives it.
    private val context: Context = context.applicationContext
    private val mView: View
    private var isClosing = false
    private var autoCloseHandler = Handler(Looper.getMainLooper())
    private var autoCloseRunnable: Runnable? = null
    private var stateCollector: Job? = null
    private var model: PopupModel? = null

    override val isOpen: Boolean
        get() = mView.parent != null && !isClosing

    /** The profile of the earbuds this popup shows, once opened. */
    override val profileId: String?
        get() = model?.profileId

    @Suppress("DEPRECATION")
    private val mParams: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        height = WindowManager.LayoutParams.WRAP_CONTENT
        val displayMetrics = this@PopupWindow.context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        width = if (screenWidthDp >= 600) {
            (400 * displayMetrics.density).toInt()
        } else {
            WindowManager.LayoutParams.MATCH_PARENT
        }
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        dimAmount = 0.3f
        flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
    }

    private val mWindowManager: WindowManager

    init {
        val layoutInflater = this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mView = layoutInflater.inflate(R.layout.popup_window, null)
        mParams.x = 0
        mParams.y = 0

        mView.setOnClickListener {
            close()
        }

        mView.findViewById<ImageButton>(R.id.close_button).setOnClickListener {
            close()
        }

        @Suppress("DEPRECATION")
        mView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        // A tap on the dimmed area above the card arrives as ACTION_OUTSIDE (FLAG_WATCH_OUTSIDE_TOUCH).
        mView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                close()
                true
            } else {
                false
            }
        }
        mWindowManager = this.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    /** Shows the popup; returns false when the window could not be added (for example, the overlay permission was revoked). */
    fun open(model: PopupModel, art: PopupArt): Boolean {
        try {
            if (mView.windowToken == null && mView.parent == null && !isClosing) {
                this.model = model
                mView.findViewById<TextView>(R.id.name).text = model.title
                showStatus(model.connected)
                showBatteries(model.batteries)
                showArt(art)

                mWindowManager.addView(mView, mParams)

                val displayMetrics = mView.context.resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels

                mView.translationY = screenHeight.toFloat()
                mView.alpha = 1f

                val translationY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, screenHeight.toFloat(), 0f)

                ObjectAnimator.ofPropertyValuesHolder(mView, translationY).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                    start()
                }

                collectState()

                autoCloseRunnable = Runnable { close() }
                autoCloseHandler.postDelayed(autoCloseRunnable!!, POPUP_AUTO_CLOSE_MILLIS)
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e("PopupWindow", "Error opening popup: ${e.message}")
            removeView()
            onCloseCallback()
            return false
        }
    }

    /** Replaces the shown model, for example with the exact battery once the earbuds connected. */
    fun update(model: PopupModel) {
        this.model = model
        if (!isOpen) return
        mView.findViewById<TextView>(R.id.name).text = model.title
        showStatus(model.connected)
        showBatteries(model.batteries)
    }

    private fun showStatus(connected: Boolean) {
        mView.findViewById<TextView>(R.id.status).setText(if (connected) R.string.popup_connected else R.string.connecting)
    }

    /** Follows the live connection while open; the beacon values stay until the earbuds connect. */
    private fun collectState() {
        val app = LibreBudsApp.from(context)
        stateCollector?.cancel()
        stateCollector = app.appScope.launch {
            app.repository.state.collect { state ->
                model?.let { update(it.withState(state)) }
            }
        }
    }

    private fun showArt(art: PopupArt) {
        val slot = mView.findViewById<FrameLayout>(R.id.popup_art)
        val clip = if (art.variant == ArtVariant.VIDEO) videoFor(art) else null
        if (clip != null) showVideo(slot, clip, art.avdRes) else showAnimation(slot, art.avdRes)
    }

    /** The clip matching the current light or dark mode. */
    private fun videoFor(art: PopupArt): Int? {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night) art.videoDark else art.videoLight
    }

    /**
     * While a clip plays the card takes the clip's own background colour (fixed light/dark values) so
     * there is no seam around the video; the drawing keeps the dynamic system colours.
     */
    private fun matchCardToVideo(playing: Boolean) {
        mView.findViewById<View>(R.id.popup_card).backgroundTintList =
            if (playing) context.getColorStateList(R.color.popup_video_background) else null
    }

    private fun showAnimation(slot: FrameLayout, avdRes: Int) {
        matchCardToVideo(false)
        slot.removeAllViews()
        val image = ImageView(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = context.getColorStateList(R.color.popup_text)
            setImageResource(avdRes)
        }
        slot.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        (image.drawable as? Animatable)?.start()
    }

    /** Plays the clip once, muted and without taking audio focus, so the user's music keeps playing. */
    private fun showVideo(slot: FrameLayout, videoRes: Int, fallbackAvdRes: Int) {
        matchCardToVideo(true)
        slot.removeAllViews()
        val video = VideoView(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
            setOnPreparedListener { player ->
                player.setVolume(0f, 0f)
                player.isLooping = false
            }
            // Returning true also stops VideoView from showing its error dialog, which needs an activity.
            setOnErrorListener { _, what, extra ->
                Log.w("PopupWindow", "Popup clip failed ($what, $extra); showing the drawing")
                showAnimation(slot, fallbackAvdRes)
                true
            }
            setVideoURI("android.resource://${context.packageName}/$videoRes".toUri())
        }
        slot.addView(video, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        video.start()
    }

    private fun showBatteries(batteryList: List<Battery>) {
        showBattery(R.id.left_battery, R.string.left, batteryList.find { it.component == BatteryComponent.LEFT })
        showBattery(R.id.right_battery, R.string.right, batteryList.find { it.component == BatteryComponent.RIGHT })
        showBattery(R.id.case_battery, R.string.case_short, batteryList.find { it.component == BatteryComponent.CASE })
    }

    private fun showBattery(viewId: Int, labelRes: Int, battery: Battery?) {
        val known = battery != null && battery.status != BatteryStatus.DISCONNECTED
        val level = if (known) context.getString(R.string.percent, battery.level) else context.getString(R.string.battery_unknown)
        val text = mView.findViewById<TextView>(viewId)
        text.text = context.getString(R.string.popup_battery, context.getString(labelRes), level)
        val charging = known && battery.status == BatteryStatus.CHARGING
        text.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (charging) R.drawable.ic_charging else 0, 0)
    }

    override fun close() {
        try {
            if (isClosing || mView.parent == null) return
            isClosing = true

            autoCloseRunnable?.let { autoCloseHandler.removeCallbacks(it) }
            stateCollector?.cancel()
            stateCollector = null

            ObjectAnimator.ofFloat(mView, "translationY", mView.height.toFloat()).apply {
                duration = 500
                interpolator = AccelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removeView()
                        isClosing = false
                        onCloseCallback()
                    }
                })
                start()
            }
        } catch (e: Exception) {
            Log.e("PopupWindow", "Error closing popup: ${e.message}")
            removeView()
            isClosing = false
            onCloseCallback()
        }
    }

    private fun removeView() {
        autoCloseRunnable?.let { autoCloseHandler.removeCallbacks(it) }
        stateCollector?.cancel()
        stateCollector = null
        try {
            if (mView.parent != null) {
                mWindowManager.removeView(mView)
            }
        } catch (e: Exception) {
            Log.e("PopupWindow", "Error removing view: ${e.message}")
        }
    }
}

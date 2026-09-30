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
    Modified for LibreBuds (2026): adapted to FreeBuds, adds the case-open island, and restarts
    the auto-close timer when a drag springs back; see NOTICE.
*/

package io.github.librebuds.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.Log.e
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import io.github.librebuds.R
import io.github.librebuds.ui.model.Battery
import io.github.librebuds.ui.model.BatteryComponent
import kotlin.math.abs

enum class IslandType {
    CONNECTED,
    TAKING_OVER,
    MOVED_TO_REMOTE,
    MOVED_TO_OTHER_DEVICE,
    CASE_OPEN,
}

class IslandWindow(private val context: Context) {
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    @SuppressLint("InflateParams")
    private val islandView: View = LayoutInflater.from(context).inflate(R.layout.island_window, null)
    private var isClosing = false
    private var params: WindowManager.LayoutParams? = null

    private var initialY = 0f
    private var initialTouchY = 0f
    private var lastTouchY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var isBeingDragged = false
    private var autoCloseHandler: Handler? = null
    private var autoCloseRunnable: Runnable? = null
    private var initialHeight = 0
    private var screenHeight = 0
    private var isDraggingDown = false
    private var lastMoveTime = 0L
    private var yMovement = 0f
    private var dragDistance = 0f

    private var initialConnectedTextY = 0f
    private var initialDeviceTextY = 0f
    private var initialBatteryViewY = 0f
    private var initialIconViewY = 0f
    private var initialTextSeparation = 0f

    private val containerView = FrameLayout(context)

    private lateinit var springAnimation: SpringAnimation
    private val flingAnimator = ValueAnimator()

    private var host: IslandHost? = null
    private var type = IslandType.CONNECTED
    private var autoCloseMillis = 4500L

    val isVisible: Boolean
        get() = containerView.parent != null && containerView.visibility == View.VISIBLE

    /** Refreshes the battery ring with the latest levels while the island is shown. */
    fun update(batteries: List<Battery>) {
        if (!isVisible) return
        updateBatteryDisplay(batteries)
        if (type == IslandType.CASE_OPEN) showBatteryLine(batteries)
    }

    /** The case-open subtitle: left, right and case levels, a charging icon after each one charging. */
    private fun showBatteryLine(batteryList: List<Battery>) {
        val line = SpannableStringBuilder()
        val batteryText = islandView.findViewById<TextView>(R.id.island_battery_text)
        for (part in islandBatteryParts(batteryList)) {
            if (line.isNotEmpty()) line.append(" · ")
            val label = when (part.component) {
                BatteryComponent.LEFT -> R.string.island_left_short
                BatteryComponent.RIGHT -> R.string.island_right_short
                else -> R.string.case_short
            }
            line.append(context.getString(R.string.popup_battery, context.getString(label), context.getString(R.string.percent, part.level)))
            if (part.charging) {
                val size = batteryText.textSize.toInt()
                val icon = context.getDrawable(R.drawable.ic_charging)!!.mutate().apply {
                    setTint(batteryText.currentTextColor)
                    setBounds(0, 0, size, size)
                }
                // The word stays in the text, so a screen reader says "charging" where the icon is drawn.
                val start = line.length
                line.append(context.getString(R.string.island_charging))
                line.setSpan(ImageSpan(icon, DynamicDrawableSpan.ALIGN_CENTER), start, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        islandView.findViewById<TextView>(R.id.island_connected_text).text = line
    }

    @SuppressLint("SetTextI18n")
    private fun updateBatteryDisplay(batteryList: List<Battery>?) {
        if (batteryList == null || batteryList.isEmpty()) return

        val leftBattery = batteryList.find { it.component == BatteryComponent.LEFT }
        val rightBattery = batteryList.find { it.component == BatteryComponent.RIGHT }

        val leftLevel = leftBattery?.level ?: 0
        val rightLevel = rightBattery?.level ?: 0

        val batteryText = islandView.findViewById<TextView>(R.id.island_battery_text)
        val batteryProgressBar = islandView.findViewById<ProgressBar>(R.id.island_battery_progress)

        val displayBatteryLevel = when {
            leftLevel > 0 && rightLevel > 0 -> minOf(leftLevel, rightLevel)
            leftLevel > 0 -> leftLevel
            rightLevel > 0 -> rightLevel
            else -> null
        }

        if (displayBatteryLevel != null) {
            batteryText.text = "$displayBatteryLevel%"
            batteryProgressBar.progress = displayBatteryLevel
            batteryProgressBar.isIndeterminate = false
        } else {
            batteryText.text = "?"
            batteryProgressBar.progress = 0
            batteryProgressBar.isIndeterminate = false
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun show(name: String, batteryPercentage: Int, host: IslandHost, type: IslandType = IslandType.CONNECTED, reversed: Boolean = false, otherDeviceName: String? = null, autoCloseMillis: Long = 4500L) {
        if (host.islandOpen) return
        else host.islandOpen = true
        this.host = host
        this.type = type
        this.autoCloseMillis = autoCloseMillis

        val displayMetrics = Resources.getSystem().displayMetrics
        val width = (displayMetrics.widthPixels * 0.95).toInt()
        screenHeight = displayMetrics.heightPixels

        val batteryList = host.batteries()
        val batteryText = islandView.findViewById<TextView>(R.id.island_battery_text)
        val batteryProgressBar = islandView.findViewById<ProgressBar>(R.id.island_battery_progress)

        val displayBatteryLevel = if (batteryList.isNotEmpty()) {
            val leftBattery = batteryList.find { it.component == BatteryComponent.LEFT }
            val rightBattery = batteryList.find { it.component == BatteryComponent.RIGHT }

            when {
                (leftBattery?.level ?: 0) > 0 && (rightBattery?.level ?: 0) > 0 ->
                    minOf(leftBattery!!.level, rightBattery!!.level)
                (leftBattery?.level ?: 0) > 0 -> leftBattery!!.level
                (rightBattery?.level ?: 0) > 0 -> rightBattery!!.level
                batteryPercentage > 0 -> batteryPercentage
                else -> null
            }
        } else if (batteryPercentage > 0) {
            batteryPercentage
        } else {
            null
        }

        if (displayBatteryLevel != null) {
            batteryText.text = "$displayBatteryLevel%"
            batteryProgressBar.progress = displayBatteryLevel
        } else {
            batteryText.text = "?"
            batteryProgressBar.progress = 0
        }

        batteryProgressBar.isIndeterminate = false
        islandView.findViewById<TextView>(R.id.island_device_name).text = name

        val actionButton = islandView.findViewById<ImageButton>(R.id.island_action_button)
        val batteryBg = islandView.findViewById<ProgressBar>(R.id.island_battery_bg)
        if (type == IslandType.MOVED_TO_OTHER_DEVICE && !reversed) {
            actionButton.visibility = View.VISIBLE
            actionButton.setOnClickListener {
                host.takeOver()
                close()
            }
            batteryText.visibility = View.GONE
            batteryProgressBar.visibility = View.GONE
            batteryBg.visibility = View.GONE
        } else {
            actionButton.visibility = View.GONE
            batteryText.visibility = View.VISIBLE
            batteryProgressBar.visibility = View.VISIBLE
            batteryBg.visibility = View.VISIBLE
        }

        containerView.removeAllViews()
        val containerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        containerView.addView(islandView, containerParams)

        params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        islandView.visibility = View.VISIBLE
        containerView.visibility = View.VISIBLE

        containerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return@setOnTouchListener false)
                    flingAnimator.cancel()

                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)

                    initialY = containerView.translationY
                    initialTouchY = event.rawY
                    lastTouchY = event.rawY
                    initialHeight = islandView.height
                    isBeingDragged = false
                    isDraggingDown = false
                    lastMoveTime = System.currentTimeMillis()
                    dragDistance = 0f

                    captureInitialPositions()

                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val deltaY = event.rawY - initialTouchY
                    val moveDelta = event.rawY - lastTouchY
                    dragDistance += abs(moveDelta)

                    isDraggingDown = moveDelta > 0

                    val currentTime = System.currentTimeMillis()
                    val timeDelta = currentTime - lastMoveTime
                    if (timeDelta > 0) {
                        yMovement = moveDelta / timeDelta * 10
                    }
                    lastMoveTime = currentTime

                    if (abs(deltaY) > 5 || isBeingDragged) {
                        isBeingDragged = true

                        // Cancel auto close timer when dragging starts
                        autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return@setOnTouchListener false)

                        val dampedDeltaY = if (deltaY > 0) {
                            initialY + (deltaY * 0.6f)
                        } else {
                            initialY + (deltaY * 0.9f)
                        }
                        containerView.translationY = dampedDeltaY

                        if (isDraggingDown && deltaY > 0) {
                            val stretchAmount = (deltaY * 0.5f).coerceAtMost(200f)
                            applyCustomStretchEffect(stretchAmount)
                        }
                    }

                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val yVelocity = velocityTracker?.yVelocity ?: 0f

                    if (isBeingDragged) {
                        val currentTranslationY = containerView.translationY
                        val significantDrag = abs(dragDistance) > 80

                        when {
                            yVelocity < -1200 || (currentTranslationY < -80 && !isDraggingDown) -> {
                                animateDismissWithInertia(yVelocity)
                            }
                            yVelocity > 1200 || (isDraggingDown && significantDrag) -> {
                                animateExpandWithStretch(yVelocity)
                            }
                            else -> {
                                springBackWithInertia(yVelocity)
                                // ACTION_DOWN stopped the timer; without this the island stays until swiped away.
                                resetAutoCloseTimer()
                            }
                        }
                    } else if (dragDistance < 10) {
                        resetAutoCloseTimer()
                    }

                    velocityTracker?.recycle()
                    velocityTracker = null
                    isBeingDragged = false
                    true
                }
                else -> false
            }
        }

        when (type) {
            IslandType.CONNECTED -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_connected_text)
            }
            IslandType.TAKING_OVER -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_taking_over_text)
            }
            IslandType.MOVED_TO_REMOTE -> {
                islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_moved_to_remote_text)
            }
            IslandType.MOVED_TO_OTHER_DEVICE -> {
                if (otherDeviceName == null || otherDeviceName.isEmpty()) {
                    e("IslandWindow", "Other device name is null or empty for MOVED_TO_OTHER_DEVICE type")
                }
                if (reversed) {
                    islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_moved_to_other_device_reversed_text)
                } else {
                    islandView.findViewById<TextView>(R.id.island_connected_text).text = context.getString(R.string.island_moved_to_other_device_text, otherDeviceName)
                }
            }
            IslandType.CASE_OPEN -> showBatteryLine(batteryList)
        }

        try {
            windowManager.addView(containerView, params)
        } catch (e: Exception) {
            // Nothing was shown: free the slot so a later show() is not ignored, and skip the
            // animation setup below, which assumes an attached view.
            e.printStackTrace()
            host.islandOpen = false
            return
        }

        islandView.post {
            initialHeight = islandView.height
            captureInitialPositions()
        }

        springAnimation = SpringAnimation(containerView, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_MEDIUM)
        }

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.5f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.5f, 1f)
        val translationY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -200f, 0f)
        ObjectAnimator.ofPropertyValuesHolder(containerView, scaleX, scaleY, translationY).apply {
            duration = 700
            interpolator = AnticipateOvershootInterpolator()
            start()
        }

        resetAutoCloseTimer()
    }

    private fun captureInitialPositions() {
        val connectedText = islandView.findViewById<TextView>(R.id.island_connected_text)
        val deviceText = islandView.findViewById<TextView>(R.id.island_device_name)
        val batteryView = islandView.findViewById<FrameLayout>(R.id.island_battery_container)
        val iconView = islandView.findViewById<ImageView>(R.id.island_icon_view)

        connectedText.post {
            initialConnectedTextY = connectedText.y
            initialDeviceTextY = deviceText.y
            initialTextSeparation = deviceText.y - (connectedText.y + connectedText.height)

            if (batteryView != null) initialBatteryViewY = batteryView.y
            initialIconViewY = iconView.y
        }
    }

    private fun applyCustomStretchEffect(stretchAmount: Float) {
        try {
            val mainLayout = islandView.findViewById<LinearLayout>(R.id.island_window_layout)
            islandView.findViewById<TextView>(R.id.island_connected_text)
            val deviceText = islandView.findViewById<TextView>(R.id.island_device_name)
            islandView.findViewById<FrameLayout>(R.id.island_battery_container)
            islandView.findViewById<ImageView>(R.id.island_icon_view)

            val stretchFactor = 1f + (stretchAmount / 300f).coerceAtMost(4.0f)
            val newMinHeight = (initialHeight * stretchFactor).toInt()
            mainLayout.minimumHeight = newMinHeight

            val textMarginIncrease = (stretchAmount * 0.8f).toInt()

            val deviceTextParams = deviceText.layoutParams as LinearLayout.LayoutParams
            deviceTextParams.topMargin = textMarginIncrease
            deviceText.layoutParams = deviceTextParams

            val background = mainLayout.background
            if (background is GradientDrawable) {
                val cornerRadius = 56f
                background.cornerRadius = cornerRadius
            }

            if (params != null) {
                params!!.height = screenHeight

                val containerParams = containerView.layoutParams
                containerParams.height = screenHeight
                containerView.layoutParams = containerParams

                try {
                    windowManager.updateViewLayout(containerView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetAutoCloseTimer() {
        autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return)
        autoCloseHandler = Handler(Looper.getMainLooper())
        autoCloseRunnable = Runnable { close() }
        autoCloseHandler?.postDelayed(autoCloseRunnable!!, autoCloseMillis)
    }

    private fun springBackWithInertia(velocity: Float) {
        springAnimation.cancel()
        flingAnimator.cancel()

        springAnimation.setStartVelocity(velocity)

        val baseStiffness = SpringForce.STIFFNESS_MEDIUM
        val dynamicStiffness = baseStiffness * (1f + (abs(velocity) / 3000f))
        springAnimation.spring = SpringForce(0f)
            .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
            .setStiffness(dynamicStiffness)

        resetStretchEffects()

        if (params != null) {
            params!!.height = WindowManager.LayoutParams.WRAP_CONTENT
            try {
                windowManager.updateViewLayout(containerView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        springAnimation.start()
    }

    private fun resetStretchEffects() {
        try {
            val mainLayout = islandView.findViewById<LinearLayout>(R.id.island_window_layout)
            val deviceText = islandView.findViewById<TextView>(R.id.island_device_name)

            val heightAnimator = ValueAnimator.ofInt(mainLayout.minimumHeight, initialHeight)
            heightAnimator.duration = 300
            heightAnimator.interpolator = OvershootInterpolator(1.5f)
            heightAnimator.addUpdateListener { animation ->
                mainLayout.minimumHeight = animation.animatedValue as Int
            }

            val deviceTextParams = deviceText.layoutParams as LinearLayout.LayoutParams
            val textMarginAnimator = ValueAnimator.ofInt(deviceTextParams.topMargin, 0)
            textMarginAnimator.duration = 300
            textMarginAnimator.interpolator = OvershootInterpolator(1.5f)
            textMarginAnimator.addUpdateListener { animation ->
                deviceTextParams.topMargin = animation.animatedValue as Int
                deviceText.layoutParams = deviceTextParams
            }

            heightAnimator.start()
            textMarginAnimator.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateDismissWithInertia(velocity: Float) {
        springAnimation.cancel()
        flingAnimator.cancel()

        val baseDistance = -screenHeight
        val velocityFactor = (abs(velocity) / 2000f).coerceIn(0.5f, 2.0f)
        val targetDistance = baseDistance * velocityFactor

        val baseDuration = 400L
        val velocityDurationFactor = (1500f / (abs(velocity) + 1500f))
        val duration = (baseDuration * velocityDurationFactor).toLong().coerceIn(200L, 500L)

        flingAnimator.setFloatValues(containerView.translationY, targetDistance)
        flingAnimator.duration = duration
        flingAnimator.addUpdateListener { animation ->
            containerView.translationY = animation.animatedValue as Float

            val progress = animation.animatedFraction
            containerView.scaleX = 1f - (progress * 0.5f)
            containerView.scaleY = 1f - (progress * 0.5f)

            containerView.alpha = 1f - progress
        }
        flingAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                forceClose()
            }
        })

        flingAnimator.interpolator = DecelerateInterpolator(1.2f)
        flingAnimator.start()
    }

    private fun animateExpandWithStretch(velocity: Float) {
        springAnimation.cancel()
        flingAnimator.cancel()

        val baseDuration = 600L
        val velocityFactor = (1800f / (abs(velocity) + 1800f)).coerceIn(0.5f, 1.5f)
        val expandDuration = (baseDuration * velocityFactor).toLong().coerceIn(300L, 700L)

        if (params != null) {
            params!!.height = screenHeight
            try {
                windowManager.updateViewLayout(containerView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val containerAnimator = ValueAnimator.ofFloat(containerView.translationY, screenHeight * 0.6f)
        containerAnimator.duration = expandDuration
        containerAnimator.interpolator = DecelerateInterpolator(0.8f)
        containerAnimator.addUpdateListener { animation ->
            containerView.translationY = animation.animatedValue as Float
        }

        val stretchAnimator = ValueAnimator.ofFloat(0f, 1f)
        stretchAnimator.duration = expandDuration
        stretchAnimator.interpolator = OvershootInterpolator(0.5f)
        stretchAnimator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            animateCustomStretch(progress)
        }

        val normalizeAnimator = ValueAnimator.ofFloat(1.0f, 0.0f)
        normalizeAnimator.duration = 300
        normalizeAnimator.startDelay = expandDuration - 150
        normalizeAnimator.interpolator = AccelerateInterpolator(1.2f)
        normalizeAnimator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            containerView.alpha = progress

            if (progress < 0.7f) {
                islandView.findViewById<ImageView>(R.id.island_icon_view).visibility = View.GONE
            }
        }
        normalizeAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                host?.openApp()
                forceClose()
            }
        })

        containerAnimator.start()
        stretchAnimator.start()
        normalizeAnimator.start()
    }

    private fun animateCustomStretch(progress: Float) {
        try {
            val mainLayout = islandView.findViewById<LinearLayout>(R.id.island_window_layout)
            val connectedText = islandView.findViewById<TextView>(R.id.island_connected_text)
            val deviceText = islandView.findViewById<TextView>(R.id.island_device_name)

            val targetHeight = (screenHeight * 0.7f).toInt()
            val currentHeight = initialHeight + ((targetHeight - initialHeight) * progress)
            mainLayout.minimumHeight = currentHeight.toInt()

            val mainLayoutParams = mainLayout.layoutParams
            mainLayoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            mainLayout.layoutParams = mainLayoutParams

            val targetMargin = (400 * progress).toInt()
            val deviceTextParams = deviceText.layoutParams as LinearLayout.LayoutParams
            deviceTextParams.topMargin = targetMargin
            deviceText.layoutParams = deviceTextParams

            val baseTextSize = 24f
            deviceText.textSize = baseTextSize + (progress * 8f)

            val baseSubTextSize = 16f
            connectedText.textSize = baseSubTextSize + (progress * 4f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { close() }
            return
        }
        try {
            if (isClosing) return
            isClosing = true

            host?.islandOpen = false
            autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return)

            resetStretchEffects()

            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, containerView.scaleX, 0.5f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, containerView.scaleY, 0.5f)
            val translationY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, containerView.translationY, -200f)
            ObjectAnimator.ofPropertyValuesHolder(containerView, scaleX, scaleY, translationY).apply {
                duration = 700
                interpolator = AnticipateOvershootInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        cleanupAndRemoveView()
                    }
                })
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Even if animation fails, ensure we cleanup
            cleanupAndRemoveView()
        }
    }

    private fun cleanupAndRemoveView() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { cleanupAndRemoveView() }
            return
        }
        try {
            containerView.visibility = View.GONE
        } catch (e: Exception) {
            e("IslandWindow", "Error setting visibility: $e")
        }
        try {
            if (containerView.parent != null) {
                windowManager.removeView(containerView)
            }
        } catch (e: Exception) {
            e("IslandWindow", "Error removing view: $e")
        }
        isClosing = false
        // Make sure all animations are canceled
        try {
            springAnimation.cancel()
        } catch (e: Exception) {
            e("IslandWindow", "Error cancelling spring animation $e")
        }
        try {
            flingAnimator.cancel()
        } catch (e: Exception) {
            e("IslandWindow", "Error cancelling fling animation $e")
        }
    }

    fun forceClose() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { forceClose() }
            return
        }
        try {
            if (isClosing) return
            isClosing = true

            host?.islandOpen = false
            autoCloseHandler?.removeCallbacks(autoCloseRunnable ?: return)

            // Cancel all ongoing animations
            springAnimation.cancel()
            flingAnimator.cancel()

            // Immediately remove the view without animations
            cleanupAndRemoveView()
        } catch (e: Exception) {
            e.printStackTrace()
            isClosing = false
        }
    }
}

// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import android.content.Context
import android.content.Intent
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.MainActivity
import io.github.librebuds.overlay.IslandHost
import io.github.librebuds.overlay.IslandType
import io.github.librebuds.overlay.IslandWindow
import io.github.librebuds.ui.model.Battery
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The case-open popup as the LibrePods island at the top of the screen: the model name, the lower
 * earbud level in the ring and left/right/case below the name. Like the card it only shows
 * information; it never connects to or takes over the earbuds. Main thread only.
 */
class PopupIsland(context: Context, private val onClosed: () -> Unit) : CasePopup {
    // A receiver's context is a restricted wrapper; the application context outlives it.
    private val context: Context = context.applicationContext
    private val window = IslandWindow(this.context)
    private var model: PopupModel? = null
    private var stateCollector: Job? = null

    // IslandWindow clears islandOpen as soon as it starts closing (timeout, swipe, close()) or when
    // the window could not be added; that is the popup's close signal.
    private val host = object : IslandHost {
        override var islandOpen = false
            set(value) {
                val closing = field && !value
                field = value
                if (closing) closed()
            }

        override fun batteries(): List<Battery> = model?.batteries.orEmpty()

        override fun takeOver() = Unit

        override fun openApp() {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    override val isOpen: Boolean
        get() = host.islandOpen

    override val profileId: String?
        get() = model?.profileId

    /** Shows the island; returns false when the window could not be added (for example, the overlay permission was revoked). */
    fun open(model: PopupModel): Boolean {
        this.model = model
        window.show(model.title, 0, host, IslandType.CASE_OPEN, autoCloseMillis = POPUP_AUTO_CLOSE_MILLIS)
        if (!host.islandOpen) return false
        collectState()
        return true
    }

    override fun close() {
        if (host.islandOpen) window.close()
    }

    /** Follows the live connection while open; the beacon values stay until the earbuds connect. */
    private fun collectState() {
        val app = LibreBudsApp.from(context)
        stateCollector?.cancel()
        stateCollector = app.appScope.launch {
            app.repository.state.collect { state ->
                val updated = model?.withState(state) ?: return@collect
                model = updated
                window.update(updated.batteries)
            }
        }
    }

    private fun closed() {
        stateCollector?.cancel()
        stateCollector = null
        onClosed()
    }
}

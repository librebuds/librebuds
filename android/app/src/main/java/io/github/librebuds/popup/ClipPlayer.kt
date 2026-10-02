// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.popup

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface

/**
 * Plays one popup clip, muted and once, without touching the main thread for the slow parts: the
 * player is created, given its data source and prepared ([MediaPlayer.prepareAsync]) on a media
 * thread as soon as the popup is built, before its window is added, so the clip is ready when the
 * card's surface appears. It starts when both the preparation and the surface are done
 * ([ClipStart]). Starting and releasing (which tears the decoder down) also happen on the media
 * thread, and so do the player's own callbacks and time updates: it is created on that thread's looper.
 * [prepare], [attach] and [release] are called on the main thread; [onError] runs there.
 */
class ClipPlayer(private val context: Context, private val videoRes: Int, private val onError: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val start = ClipStart(::startPlayback)

    // Main thread only.
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var released = false

    fun prepare() {
        media.post {
            val created = try {
                MediaPlayer().apply {
                    context.resources.openRawResourceFd(videoRes).use { setDataSource(it) }
                    setVolume(0f, 0f)
                    isLooping = false
                    // Created on the media thread's looper, so these callbacks run there, not on the main thread.
                    setOnPreparedListener { main.post { start.onPrepared() } }
                    setOnErrorListener { _, what, extra ->
                        Log.w(TAG, "Popup clip failed ($what, $extra)")
                        main.post { fail() }
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Popup clip failed (${e.message})")
                main.post { fail() }
                return@post
            }
            main.post {
                if (released) {
                    media.post { created.release() }
                } else {
                    player = created
                    surface?.let { attachSurface(created, it) }
                }
            }
        }
    }

    /** The card's TextureView has its surface. */
    fun attach(texture: SurfaceTexture) {
        if (released) return
        val target = Surface(texture)
        surface = target
        player?.let { attachSurface(it, target) }
    }

    fun release() {
        if (released) return
        released = true
        val old = player
        val oldSurface = surface
        player = null
        surface = null
        media.post {
            old?.let { runCatching { it.release() } }
            oldSurface?.release()
        }
    }

    private fun attachSurface(target: MediaPlayer, surface: Surface) {
        media.post {
            runCatching { target.setSurface(surface) }
            main.post { if (!released) start.onSurface() }
        }
    }

    private fun startPlayback() {
        if (released) return
        val target = player ?: return
        media.post { runCatching { target.start() } }
    }

    private fun fail() {
        if (released) return
        release()
        onError()
    }

    private companion object {
        const val TAG = "ClipPlayer"

        /** Shared by every popup: one clip at a time, and its decoder setup and events off the main thread. */
        val media: Handler by lazy { Handler(HandlerThread("librebuds-clip").apply { start() }.looper) }
    }
}

/**
 * Starts the clip exactly once, as soon as both the player is prepared and the surface is attached,
 * in whichever order they come.
 */
class ClipStart(private val start: () -> Unit) {
    private var prepared = false
    private var surface = false
    private var started = false

    fun onPrepared() {
        prepared = true
        maybeStart()
    }

    fun onSurface() {
        surface = true
        maybeStart()
    }

    private fun maybeStart() {
        if (prepared && surface && !started) {
            started = true
            start()
        }
    }
}

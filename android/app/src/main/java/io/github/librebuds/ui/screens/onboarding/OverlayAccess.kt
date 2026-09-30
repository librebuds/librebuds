// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.librebuds.state.AppPreferences

/**
 * The "Display over other apps" grant as the UI sees it. [requested] records that the user was sent
 * to the system page at least once, so a grant still missing afterwards can be explained ([hint]).
 */
class OverlayAccess(
    val granted: Boolean,
    val requested: Boolean,
    val request: () -> Unit,
) {
    val hint: PermissionHint get() = OnboardingState.hintFor(OnboardingState.OVERLAY, granted, requested)
}

/** Tracks the overlay grant; it is re-read whenever the app resumes, for example back from the system settings. */
@Composable
fun rememberOverlayAccess(preferences: AppPreferences): OverlayAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var requested by remember { mutableStateOf(preferences.wasRequested(OnboardingState.OVERLAY)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = Settings.canDrawOverlays(context)
                requested = preferences.wasRequested(OnboardingState.OVERLAY)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return OverlayAccess(granted, requested) {
        // Picked up by the ON_RESUME re-read when the user comes back.
        preferences.markRequested(OnboardingState.OVERLAY)
        openOverlaySettings(context)
    }
}

/** App info, where runtime permissions and (via the ⋮ menu) "Allow restricted settings" live. */
fun openAppDetailsSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri()
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri()
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

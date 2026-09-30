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
package io.github.librebuds.ui.screens.onboarding

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.librebuds.R
import io.github.librebuds.beacon.BeaconScanner
import io.github.librebuds.state.AppPreferences
import io.github.librebuds.ui.components.ListItemOrientation
import io.github.librebuds.ui.components.StyledButton
import io.github.librebuds.ui.components.StyledList
import io.github.librebuds.ui.components.StyledListItem
import io.github.librebuds.ui.components.StyledListScope
import io.github.librebuds.ui.icons.MaterialIcons

/** Current permission items, a hint per item key, and ways to ask for one or open App info. */
class PermissionRequests(
    val items: List<PermissionItem>,
    val hints: Map<String, PermissionHint>,
    val request: (PermissionItem) -> Unit,
    val openAppInfo: () -> Unit,
)

/** Tracks the onboarding permissions; the overlay grant is re-read whenever the app resumes. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberPermissionRequests(preferences: AppPreferences): PermissionRequests {
    val context = LocalContext.current
    val overlay = rememberOverlayAccess(preferences)

    // Connect and scan share the "Nearby devices" group, so Android asks for them together.
    val bluetoothState = rememberMultiplePermissionsState(
        listOf(OnboardingState.BLUETOOTH_CONNECT, OnboardingState.BLUETOOTH_SCAN)
    ) { result ->
        // The beacon scan could not start without this permission; start it now, not at the next app start.
        if (result[OnboardingState.BLUETOOTH_SCAN] == true && preferences.popupEnabled) BeaconScanner.start(context)
    }
    val notificationState = rememberMultiplePermissionsState(listOf(OnboardingState.POST_NOTIFICATIONS))

    val runtime = bluetoothState.permissions + notificationState.permissions
    val granted = runtime.filter { it.status.isGranted }.map { it.permission }.toSet()
    val items = OnboardingState.items(Build.VERSION.SDK_INT, granted, overlay.granted)
    val hints = runtime.associate {
        it.permission to OnboardingState.hintFor(
            key = it.permission,
            granted = it.status.isGranted,
            wasRequested = preferences.wasRequested(it.permission),
            shouldShowRationale = it.status.shouldShowRationale,
        )
    } + (OnboardingState.OVERLAY to overlay.hint)

    fun requestOrOpenSettings(state: MultiplePermissionsState, key: String) {
        val permission = state.permissions.firstOrNull { it.permission == key }
        val blocked = permission != null && OnboardingState.shouldOpenSettings(
            granted = permission.status.isGranted,
            wasRequested = preferences.wasRequested(key),
            shouldShowRationale = permission.status.shouldShowRationale,
        )
        if (blocked) {
            // Android no longer shows the dialog; the user can only grant it in the app's settings.
            openAppDetailsSettings(context)
        } else {
            state.permissions.forEach { preferences.markRequested(it.permission) }
            state.launchMultiplePermissionRequest()
        }
    }

    return PermissionRequests(
        items = items,
        hints = hints,
        request = { item ->
            when (item.key) {
                OnboardingState.BLUETOOTH_CONNECT, OnboardingState.BLUETOOTH_SCAN -> requestOrOpenSettings(bluetoothState, item.key)
                OnboardingState.POST_NOTIFICATIONS -> requestOrOpenSettings(notificationState, item.key)
                OnboardingState.OVERLAY -> overlay.request()
            }
        },
        openAppInfo = { openAppDetailsSettings(context) },
    )
}

private class PermissionText(val title: Int, val reason: Int, val icon: ImageVector)

private fun textFor(key: String): PermissionText = when (key) {
    OnboardingState.BLUETOOTH_CONNECT -> PermissionText(R.string.permission_bluetooth_connect, R.string.permission_bluetooth_connect_reason, MaterialIcons.bluetooth)
    OnboardingState.BLUETOOTH_SCAN -> PermissionText(R.string.permission_bluetooth_scan, R.string.permission_bluetooth_scan_reason, MaterialIcons.bluetooth_searching)
    OnboardingState.POST_NOTIFICATIONS -> PermissionText(R.string.permission_notifications, R.string.permission_notifications_reason, MaterialIcons.notifications)
    else -> PermissionText(R.string.permission_overlay, R.string.permission_overlay_reason, MaterialIcons.stack)
}

@Composable
fun PermissionsPage(
    items: List<PermissionItem>,
    hints: Map<String, PermissionHint>,
    onRequest: (PermissionItem) -> Unit,
    onOpenAppInfo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StyledList(title = stringResource(R.string.required_permissions)) {
            items.filter { it.required }.forEach {
                PermissionRow(it, onRequest)
                PermissionHintRow(hints[it.key] ?: PermissionHint.NONE, onOpenAppInfo)
            }
        }
        StyledList(title = stringResource(R.string.optional_permissions)) {
            items.filterNot { it.required }.forEach {
                PermissionRow(it, onRequest)
                PermissionHintRow(hints[it.key] ?: PermissionHint.NONE, onOpenAppInfo)
            }
        }
    }
}

/** Explains a grant that did not go through and offers App info; nothing for [PermissionHint.NONE]. */
@Composable
fun StyledListScope.PermissionHintRow(hint: PermissionHint, onOpenAppInfo: () -> Unit) {
    val text = when (hint) {
        PermissionHint.NONE -> return
        PermissionHint.RESTRICTED_SETTINGS -> R.string.permission_restricted_hint
        PermissionHint.APP_INFO -> R.string.permission_app_info_hint
    }
    StyledListItem(
        name = stringResource(R.string.open_app_info),
        description = stringResource(text),
        // Vertical: the paragraph needs the row's full width.
        orientation = ListItemOrientation.Vertical,
        onClick = onOpenAppInfo
    )
}

@Composable
private fun StyledListScope.PermissionRow(
    item: PermissionItem,
    onRequest: (PermissionItem) -> Unit
) {
    val text = textFor(item.key)
    val title = stringResource(text.title)
    val iconColor by animateColorAsState(
        if (item.granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    )
    val containerColor by animateColorAsState(
        if (item.granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    )

    StyledListItem(
        name = title,
        description = stringResource(text.reason),
        orientation = ListItemOrientation.Horizontal,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(containerColor, MaterialShapes.SoftBurst.normalized().toShape()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = text.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconColor
                )
            }
        },
        trailingContent = {
            if (item.granted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.granted),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                StyledButton(
                    onClick = { onRequest(item) },
                    backdrop = rememberLayerBackdrop()
                ) {
                    Text(text = stringResource(R.string.grant), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    )
}

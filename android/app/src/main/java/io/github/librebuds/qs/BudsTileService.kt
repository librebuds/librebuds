// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.qs

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.librebuds.LibreBudsApp
import io.github.librebuds.R

/** Quick Settings tile: current noise mode and battery; a tap opens the quick-settings dialog. */
class BudsTileService : TileService() {
    override fun onTileAdded() {
        super.onTileAdded()
        render()
    }

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    // Below API 34 only the Intent overload of startActivityAndCollapse exists.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickSettingsDialogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val model = tileModel(LibreBudsApp.from(this).repository.state.value)
        tile.state = if (model.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = model.label
        tile.subtitle = model.subtitle
        tile.icon = Icon.createWithResource(this, R.drawable.ic_buds)
        tile.updateTile()
    }
}

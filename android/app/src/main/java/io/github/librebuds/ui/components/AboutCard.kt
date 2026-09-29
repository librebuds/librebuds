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

package io.github.librebuds.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.librebuds.R

@Composable
fun AboutCard(
    model: String?,
    firmware: String?,
    serial: String?
) {
    val notReported = stringResource(R.string.not_reported)

    StyledList (title = stringResource(R.string.about)) {
        StyledListItem(
            name = stringResource(R.string.model_name),
            description = model ?: notReported
        )

        StyledListItem(
            name = stringResource(R.string.version),
            description = firmware ?: notReported
        )

        StyledListItem (
            name = stringResource(R.string.serial_number),
            description = serial ?: notReported
        )
    }
}

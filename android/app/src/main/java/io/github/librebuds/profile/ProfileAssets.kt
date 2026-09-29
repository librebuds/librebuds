// LibreBuds - Copyright (C) 2026 LibreBuds contributors - SPDX-License-Identifier: GPL-3.0-or-later
package io.github.librebuds.profile

import android.content.Context
import io.github.librebuds.protocol.profile.ProfileRegistry

/** Profiles ship as assets copied from the repository's profiles/ directory at build time. */
object ProfileAssets {
    fun load(context: Context): ProfileRegistry {
        val assets = context.assets
        val documents = assets.list("profiles").orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
            .map { name -> assets.open("profiles/$name").bufferedReader().use { it.readText() } }
        return ProfileRegistry.fromJson(documents)
    }
}

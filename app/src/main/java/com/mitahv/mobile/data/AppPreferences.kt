package com.mitahv.mobile.data

import android.content.Context
import com.mitahv.mobile.model.AppThemeMode
import com.mitahv.mobile.model.HvSite

internal class AppPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("obelisk_settings", Context.MODE_PRIVATE)

    var site: HvSite
        get() = HvSite.entries.firstOrNull { it.name == preferences.getString("site", null) } ?: HvSite.Main
        set(value) { preferences.edit().putString("site", value.name).apply() }

    var theme: AppThemeMode
        get() = AppThemeMode.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: AppThemeMode.System
        set(value) { preferences.edit().putString("theme", value.name).apply() }

    var autoNextRound: Boolean
        get() = preferences.getBoolean("auto_next_round", false)
        set(value) { preferences.edit().putBoolean("auto_next_round", value).apply() }

    var dynamicColor: Boolean
        get() = preferences.getBoolean("dynamic_color", true)
        set(value) { preferences.edit().putBoolean("dynamic_color", value).apply() }
}

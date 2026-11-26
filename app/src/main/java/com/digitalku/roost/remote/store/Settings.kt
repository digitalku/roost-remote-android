package com.digitalku.roost.remote.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark: the system's choice, or one of the two whatever the system says. */
enum class ThemeChoice { System, Light, Dark }

/** The app's own settings, in its private preferences. Nothing here is a library's. */
class Settings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val themeState = MutableStateFlow(readTheme())

    val theme: StateFlow<ThemeChoice> = themeState.asStateFlow()

    fun setTheme(choice: ThemeChoice) {
        preferences.edit().putString(THEME, choice.name).apply()
        themeState.value = choice
    }

    private fun readTheme(): ThemeChoice {
        val stored = preferences.getString(THEME, null)
        return ThemeChoice.entries.firstOrNull { it.name == stored } ?: ThemeChoice.System
    }

    private companion object {
        const val THEME = "theme"
    }
}

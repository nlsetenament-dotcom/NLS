package com.nls.chat.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.nls.chat.data.local.PreferencesManager
import com.nls.chat.utils.Constants

object ThemeManager {

    fun applyTheme(context: Context) {
        val theme = PreferencesManager(context).appTheme

        when (theme) {
            Constants.THEME_DARK,
            Constants.THEME_BW,
            Constants.THEME_AMOLED -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }

    fun setTheme(context: Context, theme: String) {
        PreferencesManager(context).appTheme = theme
        applyTheme(context)
    }
}
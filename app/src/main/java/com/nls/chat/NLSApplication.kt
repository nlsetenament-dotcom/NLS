package com.nls.chat

import android.app.Application
import com.nls.chat.utils.ThemeManager

class NLSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyTheme(this)
    }
}

package com.example.graduation_project.data.local

import android.content.Context

class DisplaySettingsStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, SCALE_MEDIUM)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()

    var isHighContrast: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    companion object {
        private const val PREF_NAME = "display_settings"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        const val SCALE_SMALL = 0.85f
        const val SCALE_MEDIUM = 1.0f
        const val SCALE_LARGE = 1.15f
    }
}

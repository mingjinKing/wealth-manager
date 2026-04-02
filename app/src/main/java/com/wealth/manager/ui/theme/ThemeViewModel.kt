package com.wealth.manager.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    private val _currentThemeColor = MutableStateFlow(loadThemeColor())
    val currentThemeColor: StateFlow<Color> = _currentThemeColor.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        if (key == "primary_color") {
            _currentThemeColor.value = loadThemeColor()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun loadThemeColor(): Color {
        val colorArgb = prefs.getInt("primary_color", ThemeGold.toArgb())
        return Color(colorArgb)
    }

    fun setThemeColor(color: Color) {
        prefs.edit().putInt("primary_color", color.toArgb()).apply()
        // 这里手动更新一次，确保当前实例立即响应
        _currentThemeColor.value = color
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

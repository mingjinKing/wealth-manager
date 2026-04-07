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

    private val _showAssetSelection = MutableStateFlow(loadShowAssetSelection())
    val showAssetSelection: StateFlow<Boolean> = _showAssetSelection.asStateFlow()

    private val _assetPasswordProtection = MutableStateFlow(loadAssetPasswordProtection())
    val assetPasswordProtection: StateFlow<Boolean> = _assetPasswordProtection.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        if (key == "primary_color") {
            _currentThemeColor.value = loadThemeColor()
        }
        if (key == "show_asset_selection") {
            _showAssetSelection.value = loadShowAssetSelection()
        }
        if (key == "asset_password_protection") {
            _assetPasswordProtection.value = loadAssetPasswordProtection()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun loadThemeColor(): Color {
        val colorArgb = prefs.getInt("primary_color", ThemeGold.toArgb())
        return Color(colorArgb)
    }

    private fun loadShowAssetSelection(): Boolean {
        return prefs.getBoolean("show_asset_selection", false)
    }

    private fun loadAssetPasswordProtection(): Boolean {
        return prefs.getBoolean("asset_password_protection", false)
    }

    fun setShowAssetSelection(show: Boolean) {
        prefs.edit().putBoolean("show_asset_selection", show).apply()
        _showAssetSelection.value = show
    }

    fun setThemeColor(color: Color) {
        prefs.edit().putInt("primary_color", color.toArgb()).apply()
        _currentThemeColor.value = color
    }

    fun setAssetPasswordProtection(enabled: Boolean) {
        prefs.edit().putBoolean("asset_password_protection", enabled).apply()
        _assetPasswordProtection.value = enabled
    }

    fun hasAssetPassword(): Boolean {
        return prefs.getString("asset_password", null) != null
    }

    fun setAssetPassword(password: String) {
        prefs.edit().putString("asset_password", password).apply()
    }

    fun verifyAssetPassword(password: String): Boolean {
        return prefs.getString("asset_password", null) == password
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

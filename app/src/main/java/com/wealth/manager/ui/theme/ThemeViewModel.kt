package com.wealth.manager.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import com.wealth.manager.util.LogCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val logCollector: LogCollector
) : ViewModel() {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _currentThemeColor = MutableStateFlow(loadThemeColor())
    val currentThemeColor: StateFlow<Color> = _currentThemeColor.asStateFlow()

    private val _showAssetSelection = MutableStateFlow(loadShowAssetSelection())
    val showAssetSelection: StateFlow<Boolean> = _showAssetSelection.asStateFlow()

    private val _refundOnDeletion = MutableStateFlow(loadRefundOnDeletion())
    val refundOnDeletion: StateFlow<Boolean> = _refundOnDeletion.asStateFlow()

    private val _assetPasswordProtection = MutableStateFlow(loadAssetPasswordProtection())
    val assetPasswordProtection: StateFlow<Boolean> = _assetPasswordProtection.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        when (key) {
            "primary_color" -> _currentThemeColor.value = loadThemeColor()
            "show_asset_selection" -> _showAssetSelection.value = loadShowAssetSelection()
            "refund_on_deletion" -> _refundOnDeletion.value = loadRefundOnDeletion()
            "asset_password_protection" -> _assetPasswordProtection.value = loadAssetPasswordProtection()
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

    private fun loadRefundOnDeletion(): Boolean {
        return prefs.getBoolean("refund_on_deletion", true)
    }

    private fun loadAssetPasswordProtection(): Boolean {
        return prefs.getBoolean("asset_password_protection", false)
    }

    fun setShowAssetSelection(show: Boolean) {
        prefs.edit().putBoolean("show_asset_selection", show).apply()
        _showAssetSelection.value = show
    }

    fun setRefundOnDeletion(refund: Boolean) {
        prefs.edit().putBoolean("refund_on_deletion", refund).apply()
        _refundOnDeletion.value = refund
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

    fun uploadLogs(deviceId: String, onComplete: (Boolean, String) -> Unit) {
        logCollector.uploadAll(context, deviceId, onComplete)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

package com.wealth.manager.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wealth.manager.data.dao.AssetDao
import com.wealth.manager.data.entity.AssetEntity
import com.wealth.manager.data.entity.AssetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssetViewModel @Inject constructor(
    private val assetDao: AssetDao
) : ViewModel() {

    private val _assets = MutableStateFlow<List<AssetEntity>>(emptyList())
    val assets: StateFlow<List<AssetEntity>> = _assets.asStateFlow()

    private val _totalAssets = MutableStateFlow(0.0)
    val totalAssets: StateFlow<Double> = _totalAssets.asStateFlow()

    private val _totalLiabilities = MutableStateFlow(0.0)
    val totalLiabilities: StateFlow<Double> = _totalLiabilities.asStateFlow()

    init {
        loadAssets()
    }

    private fun loadAssets() {
        viewModelScope.launch {
            assetDao.getAllAssets().collect {
                _assets.value = it
            }
        }
        viewModelScope.launch {
            assetDao.getTotalAssets().collect {
                _totalAssets.value = it ?: 0.0
            }
        }
        viewModelScope.launch {
            assetDao.getTotalLiabilities().collect {
                _totalLiabilities.value = it ?: 0.0
            }
        }
    }

    fun addAsset(
        name: String,
        type: AssetType,
        balance: Double,
        customType: String? = null,
        isHidden: Boolean = false
    ) {
        viewModelScope.launch {
            assetDao.insertAsset(
                AssetEntity(
                    name = name,
                    type = type,
                    customType = if (type == AssetType.CUSTOM) customType else null,
                    balance = balance,
                    icon = getDefaultIcon(type),
                    color = getDefaultColor(type),
                    isHidden = isHidden
                )
            )
        }
    }

    fun updateAssetBalance(asset: AssetEntity, newBalance: Double) {
        viewModelScope.launch {
            assetDao.updateAsset(asset.copy(balance = newBalance))
        }
    }

    fun updateAssetHidden(asset: AssetEntity, isHidden: Boolean) {
        viewModelScope.launch {
            assetDao.updateAsset(asset.copy(isHidden = isHidden))
        }
    }

    fun deleteAsset(asset: AssetEntity) {
        viewModelScope.launch {
            assetDao.deleteAsset(asset)
        }
    }

    private fun getDefaultIcon(type: AssetType): String = when (type) {
        AssetType.CASH -> "💵"
        AssetType.BANK -> "💳"
        AssetType.ALIPAY -> "📱"
        AssetType.INVESTMENT -> "📈"
        AssetType.CREDIT_CARD -> "💳"
        AssetType.LOAN -> "🤝"
        AssetType.DEPOSIT -> "🏦"
        AssetType.HOUSING_FUND -> "🏠"
        AssetType.ENTERPRISE_ANNUITY -> "🏢"
        AssetType.OTHER -> "💰"
        AssetType.CUSTOM -> "🛠️"
    }

    private fun getDefaultColor(type: AssetType): String = when (type) {
        AssetType.CASH -> "#4CAF50"
        AssetType.BANK -> "#2196F3"
        AssetType.ALIPAY -> "#00BCD4"
        AssetType.INVESTMENT -> "#FF9800"
        AssetType.CREDIT_CARD -> "#F44336"
        AssetType.LOAN -> "#9C27B0"
        AssetType.DEPOSIT -> "#3F51B5"
        AssetType.HOUSING_FUND -> "#009688"
        AssetType.ENTERPRISE_ANNUITY -> "#795548"
        AssetType.OTHER -> "#607D8B"
        AssetType.CUSTOM -> "#795548"
    }
}

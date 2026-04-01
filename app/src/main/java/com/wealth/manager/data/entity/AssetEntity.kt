package com.wealth.manager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AssetType {
    CASH,               // 现金
    BANK,               // 银行卡
    ALIPAY,             // 支付宝/微信
    INVESTMENT,         // 投资
    CREDIT_CARD,        // 信用卡 (负债)
    LOAN,               // 借入/借出
    DEPOSIT,            // 存款
    HOUSING_FUND,       // 公积金
    ENTERPRISE_ANNUITY, // 企业年金
    OTHER,              // 恢复 OTHER 以避免读取旧数据时枚举转换失败
    CUSTOM              // 自定义
}

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AssetType,
    val customType: String? = null, // 自定义账户类型的名称
    val balance: Double, // 当前余额
    val icon: String = "💰",
    val color: String = "#4CAF50",
    val isHidden: Boolean = false, // 是否不计入总资产
    val createdAt: Long = System.currentTimeMillis()
)

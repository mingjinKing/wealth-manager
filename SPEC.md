# 知财 (Wealth Manager) - 技术规格说明书

> 版本：v2.4 | 更新：2026-04-04 | 状态：**v1.0 已上线**

---

## 1. 项目概述

| 项目 | 内容 |
|------|------|
| **项目名称** | 知财 |
| **项目类型** | Android 原生应用 |
| **Slogan** | 知其财，治其财 |
| **产品定位** | 像钱迹一样方便快捷的记账工具，加上 AI 消费结构透视能力 |
| **产品哲学** | 极简操作，静默守护，适时出现（详见 Wiki 产品哲学文档）|
| **最低支持** | Android 7.0（API 24）|

---

## 2. 技术栈

| 层级 | 技术选型 |
|------|----------|
| **前端** | Android Native（Kotlin + Jetpack Compose）|
| **本地数据库** | SQLite（通过 Room 抽象）|
| **AI 能力** | LLM API（MiniMax/DeepSeek/阿里百炼），现有算力余额覆盖 |
| **网络** | Retrofit + OkHttp |
| **依赖注入** | Hilt |
| **异步处理** | Kotlin Coroutines + Flow |
| **架构模式** | MVVM + 简化 Clean Architecture（2层：data + presentation）|
| **后台任务** | WorkManager（哇时刻定时计算）|

---

## 3. Room 数据库设计（5张表）

> ⚠️ **以代码实现为准**，文档如有不一致请以代码为准并更新本文档。

### 3.1 CategoryEntity（分类表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| name | String | 分类名称 |
| icon | String | emoji 图标 |
| color | String | 颜色（十六进制，如 `#ffc880`）|
| type | String | 类型：`"EXPENSE"`（支出）或 `"INCOME"`（收入），默认 `"EXPENSE"` |
| isDefault | Boolean | 是否为默认分类，默认 `true` |

**默认支出分类**（8个）：🍗 餐饮、🛒 购物、🚇 交通、🎮 娱乐、🏠 居住、💊 医疗、📚 学习、📦 其他
**默认收入分类**（8个）：💰 工资、🧧 奖金、🚲 兼职、📈 理财、🎁 礼金、📝 报销、♻️ 转卖、💸 其他收入

> **收入/支出区分**：不通过 `ExpenseEntity.type` 区分，而是通过关联的 `CategoryEntity.type` 区分。

### 3.2 ExpenseEntity（消费记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| amount | Double | 金额（**正数**，支出/收入由 Category.type 区分）|
| categoryId | Long | 分类ID（外键，指向 CategoryEntity）|
| note | String | 备注（最多50字）|
| date | Long | 日期时间戳 |
| createdAt | Long | 创建时间，默认 `System.currentTimeMillis()` |

### 3.3 WeekStatsEntity（周统计表）

| 字段 | 类型 | 说明 |
|------|------|------|
| weekStartDate | Long | **主键**，周开始日期时间戳 |
| totalAmount | Double | 周消费总额（**不含收入**）|
| categoryBreakdown | String | 分类消费明细（JSON 格式）|
| wowTriggered | Boolean | 是否触发哇时刻 |
| savedAmount | Double | 本周节省金额（正数=少花了）|

### 3.4 AssetEntity（资产表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| name | String | 资产名称 |
| type | AssetType | 资产类型枚举（见下）|
| customType | String? | 自定义账户类型名称（当 type=CUSTOM 时）|
| balance | Double | 当前余额 |
| icon | String | emoji 图标，默认 `"💰"` |
| color | String | 颜色（十六进制），默认 `"#4CAF50"` |
| isHidden | Boolean | 是否不计入总资产，默认 `false` |
| createdAt | Long | 创建时间，默认 `System.currentTimeMillis()` |

**AssetType 枚举**：CASH / BANK / ALIPAY / INVESTMENT / CREDIT_CARD / LOAN / DEPOSIT / HOUSING_FUND / ENTERPRISE_ANNUITY / OTHER / CUSTOM

### 3.5 BudgetEntity（预算表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| amount | Double | 预算金额 |
| month | String | 预算月份（格式：`"yyyy-MM"`，如 `"2026-04"`）|
| categoryId | Long? | 分类ID（**可为空**，`null` = 全局预算）|

---

## 4. 页面路由

| 页面 | 路由 | 说明 |
|------|------|------|
| 首页 | `/dashboard` | startDestination |
| 记账页 | `/add` | 新建账单 |
| 记账页（带ID）| `/add/{expenseId}` | 编辑账单 |
| 透视页 | `/insights` | 算算账 Tab |
| 成就页 | `/achievements` | 进阶 Tab |
| 资产管理 | `/assets` | 侧边抽屉 |
| 分类管理 | `/categories` | 侧边抽屉 |
| 导入数据 | `/import` | 侧边抽屉 |

**BottomNav 3栏**：🏠 首页 / 💡 算算账 / ⭐ 进阶

---

## 5. 哇时刻机制 ⭐

**触发公式**：
```
savedAmount = last_4_week_avg - current_week_spend
wow_triggered = savedAmount > ¥100 AND savedAmount > last_4_week_avg × 20%
```

**触发条件（两者同时满足）**：
1. 绝对节省 > ¥100
2. 相对节省 > 历史均值 × 20%

---

## 6. 非功能需求

- **最低支持**：Android 7.0（API 24）
- **离线优先**：数据本地存储，不上传第三方服务器
- **隐私第一**：用户财务数据不上传第三方服务器

---

## 7. 合规边界

- **不能做**：AI 直接替用户下单、投资建议模糊表述
- **能做**：信息聚合 + 客观呈现、风险提示、知识问答式助理
- **定位**：记账工具 + 消费洞察，信息服务而非投资顾问

---

## 8. 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| **v2.4** | 2026-04-04 | 旺财 Micro Agent v1.9.1 上线（DeepSeek-v3.2 + 工具系统 + 规则引擎模块化）；透视页 AI 复盘功能上线；AgentContext 持久化；API Key 加密存储 |
| **v2.3** | 2026-04-02 | v1.0 已上线；精简产品哲学（保留定位），修正 AssetEntity.balance、BudgetEntity.month 格式、AssetType 枚举；与 Wiki 产品哲学文档分离 |
| v2.2 | 2026-04-02 | 补充产品哲学（极简操作、静默守护、适时出现）、旺财人格定义 |
| v2.1 | 2026-04-02 | 名称统一为知财、BottomNav 修正、补充 Room 5张表完整架构 |
| v2.0 | 2026-03-31 | 浅色主题、首页日账单模式、记账页重构 |

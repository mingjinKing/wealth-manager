# 知财 (Wealth Manager) - 项目规格说明书

> V1 聚焦版 —— 纯工具定位
> 版本：v2.1 | 更新：2026-04-02 | 变更：名称统一为知财、BottomNav修正、资产管理/预算管理数据模型补充

---

## 1. 项目概述

**项目名称**：知财
**项目类型**：Android 原生应用
**一句话描述**：像钱迹一样方便快捷的记账工具，加上 AI 消费结构透视能力。

---

## 2. 核心功能

- **快速记账**：3秒内完成一笔记账，支持备注、日期、分类、收入/支出
- **日账单视图**：按天分组的消费明细，清晰看到每日收支
- **消费结构透视**：AI 优化建议（待接入 LLM）+ 哇时刻成就感
- **资产管理**：管理用户的资产账户（现金、银行卡、支付宝等）
- **预算管理**：按月设置分类预算，追踪预算执行情况
- **钱迹数据导入**：CSV 格式导入，历史数据无缝衔接
- **本地优先存储**：SQLite + Room，隐私第一

---

## 3. 技术架构

| 层级 | 技术选型 |
|------|----------|
| **前端** | Android Native（Kotlin + Jetpack Compose） |
| **本地数据库** | SQLite（通过 Room 抽象） |
| **AI 能力** | LLM API（MiniMax/DeepSeek/阿里百炼），V1 使用已有算力资源 |
| **网络** | Retrofit + OkHttp |
| **依赖注入** | Hilt |
| **异步处理** | Kotlin Coroutines + Flow |
| **架构模式** | MVVM + 简化 Clean Architecture（2层：data + presentation）|
| **后台任务** | WorkManager（哇时刻定时计算，规划中）|

---

## 4. Room 数据库设计（5张表）

### 4.1 CategoryEntity（分类表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| name | String | 分类名称 |
| icon | String | emoji 图标 |
| color | String | 颜色（十六进制，如 #ffc880）|
| type | String | 类型："EXPENSE"（支出）或 "INCOME"（收入），默认 "EXPENSE" |
| isDefault | Boolean | 是否为默认分类，默认 true |

### 4.2 ExpenseEntity（消费记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| amount | Double | 金额（正数，支出/收入由 Category.type 区分）|
| categoryId | Long | 分类ID（外键，指向 CategoryEntity）|
| note | String | 备注（最多50字）|
| date | Long | 日期时间戳 |
| createdAt | Long | 创建时间，默认 System.currentTimeMillis() |

> **收入/支出区分方式**：不通过 ExpenseEntity.type 字段区分，而是通过关联的 Category.type 属性区分（CategoryEntity 支持 type 字段）。

### 4.3 WeekStatsEntity（周统计表）

| 字段 | 类型 | 说明 |
|------|------|------|
| weekStartDate | Long | 主键，周开始日期时间戳 |
| totalAmount | Double | 周消费总额（含支出，正数）|
| categoryBreakdown | String | 分类消费明细（JSON 格式）|
| wowTriggered | Boolean | 是否触发哇时刻 |
| savedAmount | Double | 本周节省金额（正数=少花了）|

### 4.4 AssetEntity（资产表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| name | String | 资产名称 |
| amount | Double | 金额 |
| type | String | 类型（现金/银行卡/支付宝/微信/投资等）|

### 4.5 BudgetEntity（预算表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| categoryId | Long | 分类ID（外键）|
| amount | Double | 预算金额 |
| month | Int | 预算月份（格式：YYYYMM）|

---

## 5. V1 MVP 功能列表

### 5.1 消费记录模块

- 添加消费/收入记录（金额、分类、日期、备注）
- 消费分类管理（默认分类带 emoji 图标：餐饮/购物/交通/娱乐/居住/医疗/学习/其他）
- 消费记录按天分组展示（今日/昨日/具体日期）
- 点击条目 → 进入编辑页
- 长按条目 → 删除确认对话框
- 编辑消费记录（预填数据，保存为更新）

### 5.2 数据存储模块

- Room 数据库：5张表（消费记录、分类、周统计、资产、预算）
- 本地数据持久化

### 5.3 资产管理模块

- 查看资产列表
- 添加/编辑/删除资产账户
- 按类型分类（现金、银行卡、支付宝、微信、投资等）

### 5.4 预算管理模块

- 按月设置分类预算
- 追踪预算执行进度
- 预算超支提醒

### 5.5 AI 透视模块（V1）

- **优化建议**：LLM 生成具体可执行的省钱建议（消费透视页，待接入 API）
- AI 建议不在首页展示，统一归入「透视」页

### 5.6 第一个"哇"时刻 ⭐

**定义**：每周日向用户展示"因为知财，你本周少花了 ¥XX"。

**触发公式**：
```
savedAmount = last_4_week_avg - current_week_spend
wow_triggered = savedAmount > ¥100 AND savedAmount > last_4_week_avg × 20%
```

**详细说明**：
- `last_4_week_avg`：用户历史最近4周消费均值（滚动计算）
- `current_week_spend`：本周消费总额
- `savedAmount`：节省金额（正数=少花了）
- 触发条件：**绝对节省 > ¥100** 且 **相对节省 > 历史均值20%**
- 新用户（不足4周数据）：使用默认基准值

**哇时刻展示内容**：
- 节省金额（金色强调）
- 等价物展示（"相当于：一顿双人火锅"，≤2项）
- 成就徽章

### 5.7 基础 UI（v2.1）

| 页面 | 功能 |
|------|------|
| **首页/仪表盘** | 月支出+收入概览 + 近7日支出 + 按天分组账单 + 哇时刻预览 + FAB居中 |
| **记账页** | 分类emoji选择 + 备注输入 + 日期选择 + 金额键盘 + 计算器 |
| **透视页** | AI 优化建议列表（待 LLM 接入）|
| **成就页** | 哇时刻 + 成就列表 + 查看透视入口 |
| **资产管理** | 资产账户列表 + 添加/编辑/删除 |
| **分类管理** | 分类列表 + 添加/编辑/删除 |
| **导入数据** | CSV 格式数据导入 |

**BottomNav 3栏**（v2.1）：

| 图标 | 标签 | 页面 | 路由 |
|------|------|------|------|
| 🏠 | 首页 | 仪表盘 | `/dashboard` |
| 💡 | 算算账 | AI 建议页 | `/insights` |
| 🏆 | 进阶 | 哇时刻+成就 | `/achievements` |

> startDestination = `/dashboard`（首页）

**侧边抽屉菜单**：

| 菜单项 | 图标 | 路由 |
|--------|------|------|
| 资产管理 | AccountBalanceWallet | `/assets` |
| 分类管理 | List | `/categories` |
| 导入数据 | Download | `/import` |

---

## 6. 非功能需求

- 最低支持 Android 7.0（API 24）
- 离线优先，数据本地存储
- 用户隐私数据不上传第三方服务器

---

## 7. v2.1 变更日志

| 变更项 | 变更内容 |
|--------|----------|
| 名称统一 | "消费透视镜" → "知财" |
| BottomNav 修正 | 标签从"透视/成就" → "算算账/进阶" |
| startDestination | 明确为 `/dashboard`（首页）|
| 月度概览 | 新增"本月收入"字段 |
| 资产管理 | 新增资产管理模块（AssetEntity）|
| 预算管理 | 新增预算管理模块（BudgetEntity）|
| 数据模型 | 补充 Room 5张表完整架构 |

### 历史版本

| 版本 | 日期 | 关键变更 |
|------|------|----------|
| v2.0 | 2026-03-31 | 浅色主题、首页日账单模式、记账页重构、导航精简为3栏 |
| v1.2 | 2026-03-31 | 哇时刻公式明确、AI方案确定、颜色系统统一 |
| v1.3 | 2026-03-31 | 启动页改为添加页、BottomNav精简为3栏 |

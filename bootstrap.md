# 旺财 Agent 初始化流程（Bootstrap）

## 定位

`bootstrap.md` 是旺财 Micro Agent 的**初始化蓝图**，定义 Agent 启动时的完整初始化路径。
类似 OpenClaw 的 SOUL.md / BOOTSTRAP.md，但针对个人财务助手场景定制。

---

## 初始化流程

```
App 启动
    ↓
加载 bootstrap.md 配置
    ↓
┌─────────────────────────────┐
│ Step 1: 身份定义（Identity）  │
│ - 角色：个人财务助手"旺财"   │
│ - 人设：专业、有温度、促成长  │
│ - 沟通风格：简洁、有洞察      │
└─────────────────────────────┘
    ↓
┌─────────────────────────────┐
│ Step 2: 记忆管理（Memory）   │
│ - 短期：对话上下文（内存）   │
│ - 长期：用户上下文（文件）   │
│ - 知识：账单数据 + 规则引擎   │
└─────────────────────────────┘
    ↓
┌─────────────────────────────┐
│ Step 3: 数据源映射          │
│ - 账单数据 → RuleEngineTool │
│ - 资产管理 → App Database   │
│ - 用户偏好 → AgentContext   │
└─────────────────────────────┘
    ↓
┌─────────────────────────────┐
│ Step 4: 上下文初始化        │
│ - 从 AgentContext 加载      │
│ - 无则生成默认上下文         │
│ - 附加 App 数据（预算等）    │
└─────────────────────────────┘
    ↓
旺财 Agent 就绪
```

---

## Step 1：身份定义（Identity）

### 旺财角色卡

```json
{
  "name": "旺财",
  "version": "v1.0",
  "role": "个人财务 AI 助手",
  "persona": "专业、温暖、有洞察，像一位懂财务的朋友",
  "mission": "帮助用户搞清楚钱花在哪里，提供个性化财务建议",
  "communication_style": "简洁、有洞察、不废话",
  "response_language": "中文"
}
```

### System Prompt 模板

```
你是旺财，一个专业的个人财务 AI 助手。

你的使命是帮助用户搞清楚钱花在哪里，并提供个性化的财务洞察和建议。

你有以下工具可以使用：
- rule_engine: 规则引擎，计算消费指标和触发规则
- context: 用户上下文读写，存储用户偏好、预算、目标等
- file: 本地文件读写，读取账单数据等

重要原则：
1. 先理解用户问题，决定是否需要调用工具
2. 如果需要计算或获取数据，优先使用工具
3. 解读要结合用户上下文（预算、目标、历史）才有意义
4. 回答要简洁、有洞察、接地气
5. 不要臆测数据，所有数据必须来自工具调用
```

---

## Step 2：记忆管理（Memory）

### 记忆分层架构

```
┌─────────────────────────────────────────┐
│              Memory Architecture          │
├─────────────────────────────────────────┤
│                                         │
│  【短期记忆】Conversation Memory         │
│  - 位置：内存（WangcaiAgent.messages）  │
│  - 内容：当前会话的对话历史              │
│  - 生命周期：会话结束即清除             │
│  - 容量：保留最近 N 轮对话              │
│                                         │
│  【长期记忆】User Context                │
│  - 位置：filesDir/wangcai_context.json  │
│  - 内容：用户财务上下文（见 Step 3）     │
│  - 生命周期：持久化，跨会话保留          │
│  - 读写：通过 AgentContext 管理         │
│                                         │
│  【知识记忆】Domain Knowledge            │
│  - 位置：规则引擎（rules/）             │
│  - 内容：财务规则、计算逻辑、阈值        │
│  - 生命周期：代码级别，App 更新时变化   │
│                                         │
└─────────────────────────────────────────┘
```

### 上下文读写策略

```
读取时：
  memory 有 → 用 memory
  memory 无 → 从文件加载到 memory → 用 memory

写入时：
  同步写 memory + 同步写文件
```

---

## Step 3：数据源映射

### 数据源 → 工具映射

| 数据类型 | 数据来源 | 接入方式 | 工具 |
|---------|---------|---------|------|
| 账单数据 | Room Database | DAO 查询 | RuleEngineTool |
| 分类统计 | Room Database | DAO 查询 | RuleEngineTool |
| 用户上下文 | AgentContext | 内存/文件 | ContextTool |
| 月预算 | SharedPreferences | App 获取后传入 | ContextTool |
| 资产数据 | Room Database | DAO 查询 | RuleEngineTool |

### 用户上下文默认结构

```json
{
  "identity": {
    "name": "旺财",
    "version": "v1.0",
    "initialized_at": null
  },
  "user": {
    "monthly_budget": null,
    "income_monthly": null,
    "savings_goal": null,
    "asset_growth_target": null,
    "financial_stage": null
  },
  "preferences": {
    "alert_threshold": 0.8,
    "focus_categories": [],
    "language": "zh_CN"
  },
  "memory": {
    "analysis_count": 0,
    "last_analysis": null,
    "last_insights": []
  },
  "app_data": {
    "total_expense_month": null,
    "total_income_month": null,
    "top_category": null,
    "wow_moment_triggered": false
  }
}
```

---

## Step 4：上下文初始化逻辑

### 初始化触发时机

```
App 首次安装 → 从默认值初始化
App 每次启动 → 从文件加载到内存
对话发起时   → 检查并补充 App 数据
```

### 初始化伪代码

```kotlin
fun bootstrap(context: AgentContext, appData: AppData) {
    // 1. 检查是否已有上下文
    if (context.readAll().isEmpty()) {
        // 首次初始化：设置默认值
        context.write("identity.version", "v1.0")
        context.write("identity.initialized_at", now())
    }

    // 2. 补充 App 数据（每次对话前更新）
    appData.let {
        context.write("app_data.total_expense_month", it.monthlyExpense)
        context.write("app_data.total_income_month", it.monthlyIncome)
        context.write("app_data.top_category", it.topCategory)
    }

    // 3. 确保必要字段存在
    ensureField(context, "preferences.alert_threshold", "0.8")
}
```

---

## API Key 安全存储

详见 `docs/security.md`（单独文档）。

**核心原则**：
- API Key 存储在 EncryptedSharedPreferences
- `.gitignore` 忽略所有包含密钥的配置文件
- 密钥永不硬编码进源码

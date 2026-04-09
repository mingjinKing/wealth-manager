# 💰 消费透视镜 (Consumer Telescope)

[![Android CI](https://github.com/mingjinKing/wealth-manager/actions/workflows/android.yml/badge.svg)](https://github.com/mingjinKing/wealth-manager/actions)
[![Unit Tests](https://img.shields.io/badge/Tests-53%20passed-brightgreen)](https://github.com/mingjinKing/wealth-manager/actions)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> 财富管理行为层的智能记账工具 —— 记账的"第二眼"

## 一句话定位

**像钱迹一样方便快捷的记账工具，加上 AI 消费结构透视能力。**

钱迹告诉你花了多少，消费透视镜告诉你钱花得"值不值"。

---

## 核心功能（V1）

- **快速记账**：3秒内完成一笔记账，极简流程
- **消费结构透视**：消费对比 + 趋势分析 + 优化建议
- **钱迹数据导入**：CSV 格式导入，历史数据无缝衔接
- **第一个"哇"时刻**：每周展示"因为消费透视镜，你少花了 ¥XX"

---

## V1 不做什么

~~社区功能~~ · ~~游戏化~~ · ~~多平台同步~~ · ~~理财建议~~

> 极简出发，只做记账 + 透视 + 导入。

---

## 技术架构

| 模块 | 方案 |
|------|------|
| 记账入口 | Android 原生（Kotlin + Jetpack Compose）|
| 数据存储 | SQLite（Room 抽象）|
| AI 分析 | 本地推理小模型 / 外部 API |
| 合规定位 | 记账工具 + 消费洞察，信息服务 |

---

## 文档

- [SPEC.md](./SPEC.md) — 技术规格说明书
- [docs/](./docs/) — 产品文档目录

## 团队协作

本项目采用 gstack 虚拟团队工作流：
- 🏛️ CEO/Founder 评审
- ⚙️ 工程负责人评审
- 🎨 设计师评审
- 🧪 QA 验证
- 🚢 自动发布

---

*骑之可寿，载你成长*

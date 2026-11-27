# 文档导航与目录说明

先按角色阅读，不需要逐个翻阅目录。

| 角色 | 首选文档 |
|---|---|
| 技术支持工程师 / AI Agent | [AI Agent 部署入口](AI_AGENT_DEPLOYMENT.md) |
| Windows 部署 | [Windows 部署手册](03-Windows部署手册.md) |
| Linux 部署 | [Linux 部署手册](04-Linux部署手册.md) |
| macOS ARM64 本机运行 | [macOS 运行指南](05-macOS运行指南.md) |
| Windows 现场排障 | [Windows 现场部署与排障](Windows现场部署与排障手册.md) |
| 第三方 SDK 集成 | [JavaScript SDK 接入](SDK接入文档.md) |
| 开发人员 | [开发协作规范](开发协作规范.md) |
| 测试人员 | [测试计划与用例](测试计划与用例.md) |

## 仓库目录

| 目录 | 内容 | 面向对象 |
|---|---|---|
| src/main | Java 服务、网页 Demo、浏览器 SDK | 开发人员 |
| src/test | 自动化测试和测试音频 | 开发 / 测试人员 |
| scripts | 平台打包、启动辅助、测试和契约校验脚本 | 开发 / 发布人员 |
| deploy | 各平台部署包模板及生成目录 | 发布人员 |
| docs | 当前有效的部署、集成、支持、开发和测试文档 | 所有人 |
| docs/archive | 历史设计、阶段性报告和旧计划，仅供追溯 | 项目维护者 |
| ops/demo-site | 公开演示站点的 Nginx 运维配置和记录，不用于客户本地部署 | 项目维护者 |
| .codex/skills | 唯一的项目开发 Skill；部署任务统一引用 AI Agent 部署入口 | AI 编码 Agent |

## 维护约定

- 客户部署只认本页列出的 Windows / Linux 正式手册；macOS 脚本用于开发和本机验证。
- AI Agent 部署只认 AI_AGENT_DEPLOYMENT.md，不在 Skill 中重复维护部署步骤。
- deploy 目录是发布包模板；ops/demo-site 是公开演示站点运维配置，两者用途不同。
- 历史设计、测试报告和专项优化记录放在 docs/archive，不作为当前部署步骤。
- 测试代码和测试音频留在 src/test；测试脚本留在 scripts，不复制到 docs。

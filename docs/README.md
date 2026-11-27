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
| 发布人员 | [版本发布与客户交付规范](07-版本发布与客户交付规范.md) |
| 发布/合规核验 | [第三方组件与许可证清单](08-第三方组件与许可证清单.md) |

## 仓库目录

| 目录 | 内容 | 面向对象 |
|---|---|---|
| src/main | Java 服务、网页 Demo、浏览器 SDK | 开发人员 |
| src/test | 自动化测试和测试音频 | 开发 / 测试人员 |
| scripts | 平台打包、启动辅助、测试和契约校验脚本 | 开发 / 发布人员 |
| deploy | 各平台部署包模板和配置，不是最终交付目录 | 发布人员 |
| dist | 本地构建生成的完整平台部署包，不提交 Git | 发布人员 |
| docs | 当前有效的部署、集成、支持、开发、测试和发布文档 | 所有人 |
| docs/archive | 历史设计、阶段性报告和旧计划，仅供追溯 | 项目维护者 |
| ops/demo-site | 公开演示站点的 Nginx 运维配置和记录，不用于客户本地部署 | 项目维护者 |
| .codex/skills | 唯一的项目开发 Skill；部署任务统一引用 AI Agent 部署入口 | AI 编码 Agent |

## 维护约定

- 客户部署只认本页列出的 Windows / Linux 正式手册；macOS 脚本用于开发和本机验证。
- AI Agent 部署只认 AI_AGENT_DEPLOYMENT.md，不在 Skill 中复制部署步骤。
- deploy 目录只存放受版本控制的模板；dist 是本地生成的发布目录并已加入 .gitignore。
- 正式版本以 pom.xml 的 project.version 为准，Git 标签使用 vX.Y.Z；发布前必须完成三平台 CI、目标平台实机验收和第三方许可核验。
- 历史设计、测试报告和专项优化记录放在 docs/archive，不作为当前部署步骤。
- 测试代码和测试音频留在 src/test；测试脚本留在 scripts，不复制到 docs。

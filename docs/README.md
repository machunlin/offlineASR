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
| 开发人员 | [开发环境搭建](02-开发环境搭建.md)、[开发协作规范](开发协作规范.md) |
| 测试人员 | [测试计划与用例](测试计划与用例.md) |
| 发布人员 | [版本发布与客户交付规范](07-版本发布与客户交付规范.md) |
| 发布/合规核验 | [第三方组件与许可证清单](08-第三方组件与许可证清单.md)、[第三方组件与再分发说明](../THIRD_PARTY_NOTICES.md) |

## 仓库目录

| 目录 | 内容 | 面向对象 |
|---|---|---|
| src/main | Java 服务、网页 Demo、浏览器 SDK | 开发人员 |
| src/test | 自动化测试和测试音频 | 开发 / 测试人员 |
| scripts | 构建、打包、启动、诊断和契约校验脚本 | 开发 / 发布人员 |
| deploy | 平台配置模板、启动模板和许可材料 | 发布人员 |
| dist | 本地生成的应用包，不提交 Git | 发布人员 |
| docs | 当前有效的部署、集成、开发、测试和发布文档 | 所有人 |
| docs/archive | 历史设计、阶段性报告和旧计划，仅供追溯 | 项目维护者 |
| ops/demo-site | 公开演示站点运维配置，不用于客户本地部署 | 项目维护者 |
| .codex/skills | 项目开发 Skill；部署任务统一引用 AI Agent 部署入口 | AI 编码 Agent |

## 维护约定

- 客户部署只认本页列出的 Windows / Linux 正式手册；macOS 脚本用于开发和本机验证。
- 第三方固定版本、官方地址、直接下载地址、SHA-256 和许可状态统一维护在 `docs/08-第三方组件与许可证清单.md`。
- 项目不维护第三方资产自动下载脚本，不把 Java Runtime、Native Runtime、模型或 FFmpeg 打入应用包。
- Fat JAR 必须保留 sherpa-onnx Java Wrapper，但不得包含 `sherpa-onnx-native-lib-*.jar`、JNI 或 ONNX Runtime。
- 历史设计和测试报告放在 `docs/archive`，不作为当前部署步骤。
- 正式版本以 `pom.xml` 的 `project.version` 为准；发布前必须完成三平台 CI、目标平台实机验收和第三方许可核验。

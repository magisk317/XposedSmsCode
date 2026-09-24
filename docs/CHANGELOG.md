# 更新日志 (CHANGELOG)

本日志记录了项目重构后的主要变更。

---
## [v3.3.6] - 2026-09-23
- 版本：`versionCode 130` / `versionName 3.3.6`。
- `[ui]` 概览与设置页改用共享 ui-kit 组件。
- `[ui]` 下拉刷新与列表回顶对齐共享 ui-kit。
- `[deps]` Miuix 升级至 0.9.4。

> Full Changelog: https://gitlab.com/magisk3171/XposedSmsCode/-/compare/v3.3.5...v3.3.6

---

## [v3.3.5] - 2026-09-22
- 版本：`versionCode 129` / `versionName 3.3.5`。
- `[ui]` 双架构与主题页优化。
- `[notification]` 通知回退路径调整。
- `[prefs]` 偏好存储迁移。
- `[ci]` 构建产物优化。
- `[deps]` 升级部分依赖。

> Full Changelog: https://gitlab.com/magisk3171/XposedSmsCode/-/compare/v3.3.4...v3.3.5

---

## [v3.3.4] - 2026-09-18
- 版本：`versionCode 128` / `versionName 3.3.4`。
- 账号解封，恢复模块仓库更新。
- `[ui]` 概览页新增 QQ 频道入口。
- `[entitlement]` 移除残留依赖。
- `[ci]` 发布流水线接入共享 toolkit。
- `[deps]` 升级部分依赖。

> Full Changelog: https://gitlab.com/magisk3171/XposedSmsCode/-/compare/v3.3.3...v3.3.4

---
## [v3.3.3] - 2026-09-15
- 版本：`versionCode 127` / `versionName 3.3.3`。
- `[ui]` 统一 UI 组件与通用设置。
- `[entitlement]` 激活界面支持发放时间与设备 ID 复制。
- `[prefs]` 隔离 Hook 与 App 的偏好读取。
- `[logging]` 统一日志导出并在写入时脱敏。
- `[build]` 升级 Kotlin、AGP 及依赖，收口版本目录。
- `[ci]` 升级发布工具链。

> Full Changelog: https://gitlab.com/magisk3171/XposedSmsCode/-/compare/v3.3.2...v3.3.3

---

## [v3.3.2] - 2026-08-31
- 版本：`versionCode 126` / `versionName 3.3.2`。
- `[sms]` 优化短信解析与分发。
- `[activation]` 更新设备激活流程。
- `[notification]` 优化通知转发与验证码自动输入。
- `[diagnostics]` 改进日志保存与导出。
- `[build]` 更新依赖与发布工具链。

> Full Changelog: https://gitlab.com/magisk3171/XposedSmsCode/-/compare/v3.3.1...v3.3.2

---

## [v3.3.1] - 2026-07-26
- 版本：`versionCode 124` / `versionName 3.3.1`。
- `[architecture]` 抽出独立 `:hook` 模块，复用共享 Hook 入口、Billing runtime 与 Preview SDK 约定，收敛 runtime / UI 边界并清理旧兼容资源。
- `[hook/sms]` 加固短信解析 IPC、分发去重与进程生命周期恢复，补齐 Google Messages、MMS、Telephony Provider 和收件箱观察链路。
- `[notification/auto-input]` 统一应用自持有通知及偏好桥接，完善验证码复制、取消、自动输入结果和 Hook 进程重启链路。
- `[telemetry]` 为应用启动、短信解析、拦截、通知、自动输入、记录操作及 Xposed 服务生命周期补齐 OpenTelemetry 观测，并让 Hook 侧共享安装标识与发布开关。
- `[log/prefs/security]` 统一日志、脱敏与动态开关链路，设置恢复改为原子操作，并加固跨进程通信与重启门控。
- `[ui]` 主界面改用共享 `MainTabScaffold` 与 ui-kit 组件，整理设置页、更新入口和关于页链接。
- `[ci/deps]` 对齐 GitHub / GitLab 发布并发、Renovate 通知和共享 CI 工具链，更新构建约定及核心子模块指针。

> Full Changelog: https://gitlab.com/magisk3171/XposedSmsCode/-/compare/v3.3.0...v3.3.1

---

## [v3.3.0] - 2026-06-16
- 版本：`versionCode 119` / `versionName 3.3.0`。
- ⚠️ **重要提示**：本版本仅支持 LibXposed API 102，低于此版本的用户请务必升级框架（[点击下载最新框架](https://lsposed.zip)）。
- `[xposed]` 升级适配 LibXposed API 102，并完整实现模块热重载 (Hot Reload) 能力，不再保留 API 101 支持。
- `[build]` 优化工程结构，将各共享子模块收敛至 `smscode/` 目录。
- `[deps]` 升级核心与非主要依赖库，包含自动化 lockfile 清理等。

## [v3.2.11] - 2026-06-10
- 版本：`versionCode 117` / `versionName 3.2.11`。
- `[hook/sms]` 重构并复用共享模块（`smscode-core`）的短信分发、黑名单去重与操作逻辑，补齐短信 SIM 卡路由透传，增强多卡场景处理。
- `[xposed]` 修复正式发版（Release build）中错误剥离/混淆 libxposed 入口类的问题；修复应用更新后目标 Hook 进程未能自动重启的问题。
- `[notification/auto-input]` 对齐验证码通知动作（Action）处理行为；无障碍自动输入链路补充生命周期诊断日志，并优化广播注册异常的捕获逻辑。
- `[records/ui]` 验证码与短信记录持久化并新增 SIM 卡槽备注展示；记录查询逻辑统一重构至 Runtime 门面；修复 Edge-to-Edge 设计在底部导航栏的遮挡问题。
- `[architecture/security]` 修复发送者 UID（sendingUid）获取前缺乏 SDK 版本校验导致的兼容性异常；同步安全更新 Netty 基础组件以修复相关漏洞；整理工程模块边界文档并新增架构层级阻断测试。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.10...v3.2.11

---

## [v3.2.10] - 2026-06-08
- 版本：`versionCode 116` / `versionName 3.2.10`。
- `[release]` 收敛到单一 libxposed API 101+ 发布线；legacy 构建迁到 `legacy` 分支与手动 CI，正式发布不再携带 legacy/api101 flavor。
- `[architecture]` 将 api101 源集提升为主源集，移除 `xposed-stub` 与 legacy 适配层，简化桥接、偏好读取和启动诊断链路。
- `[submodule/build]` 同步构建与共享模块指针，将验证码模块目录改为 `domain`、`hook`、`rule`、`runtime`、`contract`、`verification`、`xposed` 等短名。
- `[runtime/provider]` 增加 `ProviderCallerGuard` 与 Provider/DB 合同测试，修复调用方校验、备份/数据抽取、数据库访问和偏好读取边界。
- `[hook/sms]` 补齐 MIUI Phone 短信进程与作用域覆盖，放宽 libxposed API 101+ 识别，并增强短信分发、MMS/Telephony hook 与诊断日志。
- `[notification/input]` 修复 app-owned 验证码通知因 IPC token 缺失被拒收的问题；发送侧补齐 token，接收侧走受信任路径，失败回退 phone-owned 通知。
- `[notification/input]` 自动输入改为先模拟输入，失败后再无障碍 fallback。
- `[records/ui]` 修复记录页空状态英文硬编码，并优化滑动删除、撤销、日志清理、无障碍提示、预测返回和 Haze 2 适配。
- `[billing/ui]` Play 渠道接入 Google Play Billing，捐赠入口迁到首页概览卡片。
- `[deps/toolchain]` 升级到 Java 26 源兼容、Java 25 字节码、Kotlin 2.4.0、AGP 9.3.0-alpha10、Gradle 9.6.0-rc-1，并刷新 lockfile。
- `[deps/toolchain]` 清理 Gradle 10 前置废弃警告，并隔离子模块 lockfile。
- `[ci/release]` Renovate/Dependabot 支持锁文件刷新、依赖图校验和分组规则收敛；GitHub Release 与 Xposed-Modules-Repo 改为直接发布。
- `[ci/release]` 发布正文和 Telegram 发布通知统一读取本版本 changelog 块。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.9...v3.2.10

---

## [v3.2.9] - 2026-05-15
- 版本：`versionCode 114` / `versionName 3.2.9`。
- `[rules]` 新增外置 `smscode-rules` 官方规则目录：APK 内置离线快照，运行时支持远程刷新、本地缓存与只读展示；验证码匹配改为“用户自定义优先 + 官方规则按优先级 + 内置通用兜底”的分层合并模型，保持旧版用户规则导入导出格式不变。
- `[diagnostics]` 日志系统切换到共享 JSONL 形态并支持按天轮转（默认保留 2 天，最低 1 天）；设置页新增“详细日志”预览能力，可查看文件列表与格式化 JSON 后再执行分享/清空，定位问题更直接。
- `[auto-input]` 自动输入链路新增 `attemptId` 贯穿调度；系统注入改为高优先级主路径（成功则终止广播），无障碍作为失败兜底回退；`KillMe` 改为等待本次自动输入结果后再执行，并补齐监听预注册与超时兜底，降低误杀和时序竞态。
- `[prefs/hook]` `api101` 下的 hook 配置与 kill 控制从旧 Provider 读写路径迁移为更清晰的桥接与控制链路，补齐 `CorePrefsBridge`、镜像同步与控制接收器，减少跨进程配置读取漂移。
- `[build/submodule]` 构建侧纳入 `hook`、`contract`、`rule` 依赖，新增 `smscode-rules` 内容子模块并同步 `build-logic`/`smscode-core`/`magisk-ui-kit` 指针，保持主工程与子模块能力一致。
- `[deps/security]` 依赖治理从 Dependabot 迁移到自托管 Renovate，启用 OSV 安全联动、强制依赖修复和全模块 lockfile；同步修复 Netty `4.1.133.Final` 系列安全升级并更新 AGP/KSP/Gradle 相关版本与校验。
- `[ci/release]` CI 补强依赖图提交流程（校验、重试、失败治理），通知流改为 `workflow_run` 并对齐 arm64 产物投递策略，发版链路与元数据同步检查继续收敛。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.8...v3.2.9

---

## [v3.2.8] - 2026-04-24
- 版本：`versionCode 113` / `versionName 3.2.8`。
- `[runtime/db]` 将 Room DAO 接口重构为 `suspend` 函数，并在 `DBManager` 中通过 `runBlocking` 安全调用，优化主线程性能并确保数据库操作符合 Room 并发规范。
- `[deps]` 升级核心依赖库：Kotlin `2.3.21`、Navigation `2.9.8`、Compose BOM `2026.04.01`。
- `[ci]` 优化 CI 工作流，移除对外部相邻仓库的冗余检查逻辑，提升构建流水线运行效率。
- `[submodules]` 同步 `smscode-core` 子模块指针，解决 `mokkery` 引用冲突，保持 API 抽象层与实现层的一致性。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.7...v3.2.8

---

## [v3.2.7] - 2026-04-11
- 版本：`versionCode 112` / `versionName 3.2.7`。
- 发布说明：Play 渠道继续提供 `api101`；GitHub 渠道同时提供 `api101` 与 `legacy`。下载前请先确认框架类型，避免安装错误变体。
- `[core/runtime]` 新增 `RuntimeBackupFacade` 与 `RuntimeStoreFacade`，备份恢复、发布导入导出入口与应用配置持久化改经 facade 暴露，继续减少 `core` 对 runtime 内部 feature 实现的直接感知。
- `[quality]` 修复应用列表按使用时长排序时的权限门控问题，避免 `UsageStats` 访问在未授权场景下触发 lint blocker；`core/runtime/app` 三个模块的 `check` 现已重新保持通过。
- `[records]` 验证码记录补齐 `processedTime` 贯穿链路，记录插入、数据库迁移与记录页展示进一步对齐，便于后续导出、恢复与排序保持一致。
- `[activation/framework]` 启动阶段增加已知不兼容框架拦截，首页/设置页进一步依赖激活诊断信息驱动状态展示，减少“模块已恢复工作但界面仍停留旧状态”的误判。
- `[build/ci]` CI、tag 发版工作流与 `release_tag.sh` 对齐到新的质量门：发包前会先跑 `:core:check`、`:runtime:check`、`:app:check`，同时同步 release guard 与 Fastlane 元数据流程。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.6...v3.2.7

---

## [v3.2.6] - 2026-04-02
- 版本：`versionCode 111` / `versionName 3.2.6`。
- 发布说明：Play 渠道继续提供 `api101`；GitHub 渠道同时提供 `api101` 与 `legacy`。下载前请先确认框架类型，避免安装错误变体。
- `[ui]` 首页、记录、菜单、对话框与主要设置入口继续做统一化整理，整体视觉语言更一致。
- `[ui]` 主导航、概览卡片、记录列表和设置页的交互细节继续打磨，减少不同页面之间观感割裂的问题。
- `[activation]` 修复激活状态刷新不及时的问题，减少模块已经恢复工作但首页状态卡仍停留在旧状态的情况。
- `[auto-input]` 对齐观察侧调度与共享去重逻辑，降低重复识别、重复通知与重复自动输入验证码的概率。
- `[logs]` 导出日志包文件名增加命名空间整理，连续多次导出时更容易区分不同来源，减少覆盖和反馈混淆。
- `[api101/legacy]` 同步共享子模块和构建逻辑，继续收敛两条发版链路的实现差异，减少后续维护和发版漂移。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.5...v3.2.6

---

## [v3.2.5] - 2026-03-28
- 版本：`versionCode 110` / `versionName 3.2.5`。
- 发布说明：Play 渠道继续提供 `api101`；GitHub 渠道同时提供 `api101` 与 `legacy`。两套 SDK 均已对齐 Android 17 适配，请按框架类型选择安装包。
- `[api101/legacy]` 继续重构验证码主链路：共享 `smscode-core` verification pipeline，并将 `runtime` 从 `storage` 中拆出，统一 dispatch、observer、通知、自动输入与记录能力，减少 flavor 之间的实现漂移。
- `[api101/legacy]` 修复验证码解析结果在 app 侧 `Bundle` 解包时丢失的问题，恢复部分场景下通知、记录与自动输入不触发的链路。
- `[api101/legacy]` 改进无障碍自动输入重试、输入节流与前台包名判定，降低部分机型上的自动输入失败概率。
- `[ui]` 设置页数值输入继续做规范化处理，并同步简化无障碍服务说明文案与设置项文案复制逻辑。
- `[build/ci]` 对齐共享子模块基础设施、发布产物工作流与依赖强制维护脚本，降低后续发版维护成本。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.4...v3.2.5

---

## [v3.2.4] - 2026-03-26
- 版本：`versionCode 109` / `versionName 3.2.4`。
- 发布说明：Play 渠道继续提供 `api101`；GitHub 渠道同时提供 `api101` 与 `legacy`。下载前请先确认框架类型，避免下错包。
- `[github/fdroid]` 自动输入新增无障碍输入框路径，优先查找当前验证码输入框直接填入，失败后再回退到模拟按键输入，并在设置页补充“输入框辅助”入口。
- `[play api101]` 移除无障碍服务声明，保留现有自动输入链路，避免 Play 渠道继续携带对应服务入口。
- `[legacy]` 继续强化验证码自动输入稳定性：观察器改为优先查询触发短信、跳过已读短信，并增加观察侧短窗口去重，降低部分机型重复自动输入、多次处理同一短信的问题。
- `[api101/legacy]` 增加仅详细日志模式下启用的敏感日志开关，并补充短信投递诊断，便于分析验证码未触发、投递失败或链路重复问题。
- `[api101/legacy]` 新增验证码短信规则管理入口与交互优化，设置页恢复可关闭 Snackbar，单选对话框改为点击即应用。
- `[api101/legacy]` 验证码通知支持更细粒度的 owner 配置与权限引导，应用自持有通知路径的兜底行为进一步加固。
- `[api101/legacy]` 修复 Android 16 上的短信拦截兼容性问题，并修正 `Rule` 表迁移中 `check` 列的 SQLite 关键字转义。
- `[build]` 更新 Compose、Kover、`androidx.browser` 与 CI 依赖，保持构建链路与发布环境同步。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.3...v3.2.4

---

## [v3.2.3] - 2026-03-23
- 版本：`versionCode 107` / `versionName 3.2.3`。
- 发布说明：Play 渠道继续提供 `api101`；GitHub 渠道同时提供 `api101` 与 `legacy`。下载前请先确认框架类型，避免下错包。
- `[api101/legacy]` 重构为 split shared layers，并同步更新 `smscode-core` 子模块，统一共享 hook、日志与输入基础能力，减少 flavor 之间的实现漂移。
- `[legacy]` 首页激活状态改为接受 `sms_handler` heartbeat，修复旧框架下“已激活但状态卡误判未激活”的问题。
- `[api101/legacy]` 强化验证码 Toast 去重与短信分发防重，降低重复提示、重复处理与重复自动输入概率。
- `[legacy]` 进一步避免 `InboundSmsHandler` 重复初始化与重复分发，兼容旧框架重复加载场景并提升稳定性。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.2...v3.2.3

---

## [v3.2.2] - 2026-03-22
- 版本：`versionCode 106` / `versionName 3.2.2`。
- 发布说明：Play 渠道继续提供 `api101`；GitHub 渠道同时提供 `api101` 与 `legacy`。下载前请先确认框架类型，避免下错包。
- `[api101]` Play 版继续面向 libxposed API 101，新框架用户请选择 `api101` 包。
- `[legacy]` 恢复 legacy 风味构建与旧 Xposed 入口；legacy 框架（API < 100）现在可升级到 3.2.2，但必须安装 `legacy` 包。
- `[api101/legacy]` 更新检查按 Xposed API flavor 匹配发布资产，减少 `api101` / `legacy` 安装包选错概率。
- `[api101/legacy]` 状态卡新增激活诊断展示，并记录短信/Provider 链路的激活线索，便于排查未激活或注入失败。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.1...v3.2.2

---

## [v3.2.1] - 2026-03-19
- 版本：`versionCode 104` / `versionName 3.2.1`。
- 仅支持 libxposed API 101；legacy 框架（API < 100）请停留在 3.2.0 或更低版本。
- 升级到 3.2.1 需要框架版本 >= 7607。
- 设置读取链路调整：RemotePreferences 优先、Provider 兜底，降低前台读取失败概率。
- 自动输入增加“近期去重缓存”，减少重复输入。
- 移除 legacy 入口、旧配置迁移与兼容依赖。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.2.0...v3.2.1

---

## [v3.2.0] - 2026-03-19
- 版本：`versionCode 103` / `versionName 3.2.0`。
- 兼容 libxposed 新框架 API 101（仅支持 API 101）。
- 抽取 `smscode-core` 共享模块，统一 hook/权限/系统注入与日志能力。
- 激活判定改为 service/binder 状态兜底，首页激活显示修正。
- 短信链路增强：observer 兜底自动输入、dispatch/provider 诊断日志完善、去重修复。
- 删除/广播回调兼容性增强，权限授予与 sendingUid 获取路径改进并补充诊断。
- 自杀链路日志增强，便于确认是否被系统重启。
- 日志包新增 logcat 抓取；恢复流程修复 SAF 持久权限。
- 依赖/构建更新（Kotlin/Koin/Gradle），CI 支持 submodule 拉取。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.9...v3.2.0

---

## [v3.1.9] - 2026-03-16
- 版本：`versionCode 102` / `versionName 3.1.9`。
- 从 beta 转为正式版，收敛冲突提示、默认作用域（含 `system`）与繁中缺失文案等变更。
- 适配 Android 16 权限授予链路并增强 Hook 诊断输出。
- HyperOS 3 改用 Provider 自杀路径，提升 kill 稳定性。
- 设置页支持一键导出分享日志包，构建签名启用 v1/v2/v3。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.8...v3.1.9

---

## [v3.1.9-beta] - 2026-03-09
- 版本：`versionCode 101` / `versionName 3.1.9-beta`。
- 冲突检测弹窗调整为“仅退出”路径，明确提示需先卸载冲突模块后再继续。
- 默认 Xposed 作用域补充 `system`，改善 system_server 相关场景下的注入覆盖率。
- 修复繁体中文（zh-TW）设置分组与备份项字符串缺失问题，提升多语言一致性。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.8...v3.1.9-beta

---

## [v3.1.8] - 2026-03-08
- 版本：`versionCode 100` / `versionName 3.1.8`。
- 聚焦验证码主链路：移除历史转发/通知转发/WebUI 相关遗留代码与配置入口，收敛为短信解析、拦截与自动输入核心能力。
- 修复 Xposed 自动输入注入方法解析兼容性问题（#165），降低不同系统实现下的注入失败风险。
- 新增中继冲突仲裁与启动风险确认弹窗，避免与其它短信中继方案并存时发生行为冲突。
- 精简应用黑名单与记录页面，并将恢复策略切换为 Kill Action，减少复杂恢复路径带来的不确定性。
- 构建增强：支持 `buildTs` 覆盖属性，便于 CI/本地构建注入统一时间戳。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.7...v3.1.8

---

## [v3.1.7] - 2026-03-07
- 版本：`versionCode 99` / `versionName 3.1.7`。
- 本版本涉及版本切换与分包调整，请务必提前备份数据，避免意外丢失。
- 预告：下个版本将移除转发、通知相关功能，请提前做好迁移准备。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.6...v3.1.7

---

## [v3.1.6] - 2026-03-03
### 版本总览
- `v3.1.6` 为 `v3.1.6-beta` 到 `v3.1.6-beta.5` 的正式版收敛，以下汇总本版本主要能力变更；每个 beta 的细节仍保留在本文件后续章节。

### 核心能力升级 (Core Features)
- 转发体系重构：引入发送通道（Webhook/Telegram 等）与公共模板配置，支持按通道独立启停、独立配置与状态反馈。
- 普通短信转发策略升级：新增“按通道控制普通短信转发”，并统一短信/应用通知的转发结果记录语义。
- 应用通知能力上线：新增应用通知转发链路（系统 Hook + 监听兜底）、应用控制页与应用详情页，支持按应用粒度控制与诊断。
- 历史能力：曾新增内置 WebUI（发送通道与记录管理）；该能力已在近期版本移除。

### 记录与设置演进 (Records & Settings)
- 记录中心增强：支持验证码/普通短信/应用通知分类型开关、保留条数与导出范围。
- 模板与配置体验优化：变量体系补齐（卡槽/来源等）、模板编辑与预览增强、通道详情返回自动保存。
- 自动输入与拦截链路持续优化：补充字符间隔设置、黑名单规则与行为链路稳定性改进。
- 更新与保活增强：新增非 Play 应用内升级流程（含 APK 摘要与签名一致性校验）及后台保活选项。

### 稳定性与诊断增强 (Stability & Diagnostics)
- 修复“应用控制”大位图崩溃风险：主题截图与图标解码增加尺寸保护，应用列表支持分段渲染。
- 短信链路可观测性升级：新增 `event_id` 全链路追踪，阻断路径补充明确原因（`blacklist_block` / `pref_block_sms`）。
- 跨进程与跨 UID 场景加固：清理误导性 `EACCES` 回退噪声，补强 token 读取与系统进程兼容逻辑。
- 发送通道体验优化：新增删除后 5 秒撤销（Undo）能力，降低误删成本。

### 工程与发布 (Engineering & Release)
- 版本升级到 `versionCode 97` / `versionName 3.1.6`。
- 构建链路与静态检查持续清理（Gradle nightly / Detekt），并将 Renovate 基线分支调整为 `beta` 以对齐自动化流程。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.5...v3.1.6
> Incremental since beta.5: https://github.com/magisk317/XposedSmsCode/compare/v3.1.6-beta.5...v3.1.6

---

## [v3.1.6-beta.5] - 2026-03-03
### 功能与体验 (Features & UX)
- 新增更新安全校验：安装前执行 APK `SHA256` 校验与签名一致性校验，不满足条件自动回退外链下载。
- 新增“后台保活”分组：支持“强停后自恢复”与“恢复失败时前台拉起一次”两项能力（默认关闭）。
- 新增 SIM 卡槽备注：可自定义模板变量 `{{CARD_SLOT}}` 文案，便于多卡场景区分。
- 新增自动输入“字符间隔(ms)”配置，优化部分机型输入稳定性。

### 记录与转发 (Records & Forwarding)
- 记录页能力增强：支持按验证码/普通短信/应用通知分别设置开关、条数上限与导出范围。
- 记录详情与列表展示优化：转发结果更易读，普通短信无验证码时补充发件人/号码展示。
- 转发链路健壮性增强：补充异常兜底与执行调度保护，修复普通短信记录与状态异常场景。

### 设置与 WebUI（历史） (Settings & WebUI - Historical)
- 通道配置页优化：返回即自动保存，移除退出弹窗；模板与状态相关体验持续收敛。
- WebUI 设置增强（历史）：补齐实验项控制与更多配置项映射。
- 通知设置联动：关闭“显示状态栏通知”时，自动隐藏“自动取消通知/通知保留时间”选项。

### 修复与工程 (Fixed & Engineering)
- 修复日志目录清理失败场景，增加 `su` 回退路径。
- 修复多处记录状态判定与展示问题，降低“已转发却显示未转发”概率。
- 持续升级构建工具链并清理静态检查告警（Gradle nightly / Detekt）。
- 升级到 `versionCode 96` / `versionName 3.1.6-beta.5`。

### 提交明细 (Commit Details)
- `6fc5455` fix(ci): allow nightly gradle wrapper in detekt workflows
- `4953208` build(wrapper): update gradle wrapper to nightly 20260302000223
- `7f9290f` fix(detekt): clean warnings and harden exception handling
- `6eca9fa` fix: decouple interception from forwarding and refine status/settings
- `0fbdf5d` fix: implement a complete KillMeAction self-termination flow
- `0b9a461` feat(settings): add launcher icon visibility toggle
- `062ffef` feat(sender): auto-save on back and remove exit dialog
- `e2e484b` fix(log): cleanup temp log directories with su fallback
- `d42f083` fix(record): decouple forwarding status from interception and refine status rendering
- `1f9905c` fix(forward): restore plain SMS record insertion for status tracking
- `bb001a8` feat(ui): improve template preview and forwarding-result readability
- `43b491f` fix(sender-list): prevent FAB from overlapping bottom action buttons
- `383d4c2` feat(webui): align settings groups and improve app/record operations
- `0860439` feat(record): split tab settings and support scoped export
- `7692d17` fix(record): apply per-type switches and retention limits
- `aba41d7` refactor(sender-form): group forwarding toggles in shared section
- `f26f017` feat(sender-list): compact cards and hide rules entry
- `81cf556` feat(auto-input): add configurable character input interval
- `d0d384f` feat(update): add non-Play in-app upgrade flow and recovery settings
- `be52786` fix(settings): link notification options visibility to status bar toggle

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.6-beta.4...v3.1.6-beta.5

---

## [v3.1.6-beta.4] - 2026-03-01
### 功能与体验 (Features & UX)
- 历史能力：新增内置 WebUI（发送通道与记录管理），并在高级设置增加 WebUI 局域网访问开关；该能力已在近期版本移除。
- 增强应用通知转发诊断：补充 `trace/event_id` 贯穿日志、投递回执与更细粒度链路追踪。
- Webhook 测试日志增强：补充请求上下文，便于定位 4xx/5xx 与配置差异问题。

### 日志与运维 (Log & Ops)
- 日志导出升级：支持打包分享应用日志 + LSPosed 日志，并完善日志目录清理能力。
- 日志打包链路加固：修复导出路径与存储细节，降低导出失败概率并统一私有目录策略。

### 修复与稳定性 (Fixed & Stability)
- 修复应用列表切换/搜索后的视图与过滤状态异常。
- 修复系统通知转发配置读取不稳定问题（system_server 侧容错增强）。
- 修复邮件通道 SMTP 传输回退与 provider 兼容问题。
- 修复备份还原后转发状态不一致问题，补齐恢复一致性。
- 修复短信记录指纹与 `msg_type` 对齐问题，避免短信/应用通知状态串写。
- 修复 DataStore/SharedPreferences 类型不一致导致的读取异常。
- 优化短信自动删除流程：提升触发时机与重试可靠性。

### 工程与测试 (Engineering & Test)
- 升级 Gradle Wrapper 夜版工具链。
- 补齐 JUnit Platform Launcher 运行时依赖，修复单测运行环境。
- 记录页 Haze 分层细节修复，减少视觉错位。

### 提交明细 (Commit Details)
- `99a284a` build(wrapper): update gradle wrapper to nightly 20260301003351
- `f06809d` fix(test): add junit platform launcher runtime for unit tests
- `bfaf804` fix(records-ui): align haze layering with fixed header tabs
- `8ee8638` feat(webui): add Ktor web ui with sender CRUD and responsive records
- `6bf187c` feat(settings): add WebUI LAN toggle in advanced settings
- `d520969` fix(app-config): keep viewport stable and sync search filter state
- `a4433e9` fix(notification): harden app forwarding lookup in system_server
- `57d0e84` feat(auto-input): read blocked state from provider with file fallback
- `a731ae9` fix(webui): preserve app list scroll position after toggle refresh
- `73beb27` fix(email): add smtp transport fallback and keep mail provider classes
- `7dde188` docs(donations): add 2026-03-01 wechat sponsorship record
- `8046638` feat(log): support bundled log share and full log cleanup
- `dc8ec7d` feat(forward): add trace logs across forwarding flow
- `cbe8435` feat(webhook): enhance header config and test log context
- `91f8e27` docs(donations): update donor entry
- `b409f24` fix(backup): support database restore option and preserve forward state
- `2319201` fix(db): align sms fingerprint with msg_type
- `492d586` feat(log): harden log bundle export and private storage
- `25237e4` fix(prefs): tolerate datastore/sharedprefs type mismatch
- `02e644c` feat(sms): improve auto-delete reliability with observer flow

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.6-beta.3...v3.1.6-beta.4

---

## [v3.1.6-beta.3] - 2026-02-28
### 功能与体验 (Features & UX)
- 应用控制页重构：底部 Tab“黑名单”更名为“应用”，新增“应用控制”页与每应用详情页。
- 应用详情支持双开关联动（自动输入拦截 / 应用通知转发）与近期转发日志查看，便于快速排查。
- 通道配置升级：新增“应用通知配置”，模板编辑体验与“短信公共配置”保持一致，并支持“卡槽→应用”语义替换。

### 转发链路与记录 (Forwarding & Records)
- 新增应用通知转发链路（支持系统通知 Hook 与监听服务兜底），并接入去重与更完整的状态回写。
- 新增通道级“转发应用通知”开关：应用通知需同时满足“应用侧开启 + 通道侧开启”才会转发。
- 转发结果展示统一：转发目标改为具体通道名，结果文案统一为“成功转发到X个通道”，短信与应用通知记录行为对齐。

### 修复与工程 (Fixed & Engineering)
- 修复 Telegram MarkdownV2 字符转义问题，提升消息渲染稳定性。
- 修复结果实体混淆问题与多处构建/依赖兼容性细节。
- 升级到 `versionCode 94` / `versionName 3.1.6-beta.3`。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.6-beta.2...v3.1.6-beta.3

---

## [v3.1.6-beta.2] - 2026-02-26
### 重要提醒 (Important Notice)
- 本版本重构了转发体系，升级后会清除旧转发数据，请务必先备份。

### 功能与体验 (Features & UX)
- 新增发送通道顶部“公共配置”卡片，可统一配置设备识别号与转发模板。
- 转发模板支持更丰富变量：卡槽/SubId/来源姓名/来源归属/软件版本等。
- 新增模板快速填充与变量插入能力，提升模板编辑效率。
- 未命名通道显示优化：空名称时显示通道类型。

### 修复与优化 (Fixed & Improved)
- 补齐短信转发链路中的卡槽与来源元数据透传与解析。
- 模板渲染后自动移除空值整行，输出内容更整洁。
- 记录页样式优化：顶部计数标题居中、数量使用括号、去除不必要色块背景。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.6-beta...v3.1.6-beta.2

---

## [v3.1.6-beta] - 2026-02-25
### 功能与体验 (Features & UX)
- 新增验证码转发能力：支持 `Webhook` + `Telegram` 双通道，并记录每条短信的转发状态、目标、时间与结果（#119）。
- 转发设置升级为通道独立配置：支持各通道单独启用、单独配置、独立保存反馈与配置状态显示（已配置/未配置）。
- 新增“非验证码短信转发”通道级开关：每个通道可独立决定是否转发普通短信，且普通短信仅转发正文，自动忽略验证码结构化字段（#120）。

### 修复 (Fixed)
- 修复验证码提取在中英混合场景下可能误取品牌词的问题，优先提取数字验证码（如 Wise/PayPal 场景，#117）。
- 修复记录详情中“转发目标”泄露具体配置的问题，现仅展示渠道名称（#118）。
- 修复 `LicenseActivity` 从后台恢复时可能出现的灰屏问题（#116）。
- 修复飞书机器人 webhook 兼容性：按飞书要求发送 `msg_type/content`，并按业务返回码判定成功/失败。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.5...v3.1.6-beta

---

## [v3.1.5] - 2026-02-25
> **注：** v3.1.5 为自 v3.1.4 迭代了三版 Beta 后的正式版，以下是相比于最后一次内测（Beta 3）所新增/修改的内容。整体功能介绍请结合下方 beta 系列的日志。

### 修复 (Fixed)
- 修复验证码记录列表中，App 包名及图标信息未锁定（滑动列表可能复用错乱）以及左对齐排版不居中的问题（#110）。

### 功能与体验 (Features & UX)
- 恢复了实验性功能内的“标记为已读”功能并解除了置灰不可点状态，同时更新多语言免责说明。
- 拦截屏幕UI优化：统一了黑名单各项规则（号码、号段、内容）的正则或分隔符说明，并在弹窗输入中增加了多规则格式（中英文分隔符、竖线）校验拦截。

### 工程与自动化 (Chore & Build)
- 补充并更新 `renovate.json` 规则：开启 Fork 自动化更新处理、设置 baseBranches 和包名过滤策略。
- 升级 Gradle Wrapper 到最新的 Nightly 测试版本。
- 修复 Detekt 代码检查中基于 `int:LOG_LEVEL` 引入的无用警告误报。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.5-beta.3...v3.1.5

---

## [v3.1.5-beta.3] - 2026-02-24
### 修复 (Fixed)
- 修复 Hook 进程中因 `Context/dataDir` 与未解锁阶段 CE 偏好读取导致的验证码解析失败问题（#108）。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.5-beta.2...v3.1.5-beta.3

---

## [v3.1.5-beta.2] - 2026-02-23
### 修复 (Fixed)
- 修复 DataStore 在并发访问下可能创建重复活跃实例并触发 `FileStorage.createConnection` 崩溃的问题（#104）。
- 修复 Android 16 系统 Hook 兼容性问题：改为方法集合匹配，避免 `NoSuchMethodError` 等签名漂移导致的 Hook 失败（#105）。
- 修复 Android 13 下状态栏沉浸不完整问题，改为官方 `enableEdgeToEdge(SystemBarStyle...)` 实现（#106）。

### 功能与体验 (Features & UX)
- 调整通知相关设置：将 Toast 提示与状态栏通知放到同一分组，避免语义分散（#103）。
- 优化通知文案，明确“状态栏通知（部分系统显示为电话服务）”与 Toast 的区别（#103）。
- 将“标记为已读”暂时关闭并置灰，设置中明确当前不可用（#103）。
- 完成一轮 M3 Expressive 一致性优化：记录详情动作区升级、对话框 ButtonGroup 语义统一、加载指示器 token 收敛（#100/#101/#102）。

### 构建与发布 (Build & Release)
- 升级到 `versionCode 89` / `versionName 3.1.5-beta.2`。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.5-beta...v3.1.5-beta.2

---

## [v3.1.5-beta] - 2026-02-23
### 修复 (Fixed)
- 修复短信拦截链路在进程回收场景下的稳定性问题（#81）。
- 修复短信删除在部分系统短信数据库通路中的失效问题（#85）。
- 修复“记录/拦截”页面保存反馈缺失，补齐 Toast 提示（#81、#86）。
- 修复拦截规则说明中中英文 regex 指引不一致的问题（#87）。

### 功能与体验 (Features & UX)
- 新增可配置短信黑名单：支持号码/号段/正则/内容匹配，以及“删除短信/阻断广播”动作（#84）。
- 设置页新增“自动输入后自杀”开关，并将触发时机对齐自动输入阶段（#90）。
- 统一对话框按钮语义为 M3 层级并重构记录详情交互（字段点击复制、动作精简）（#94）。
- 新增 dynamic color 主题能力，并保留 pure black 主题表现（#93）。
- 迁移到 Material 3 下拉刷新并统一加载反馈体验（#92）。

### 架构与质量 (Architecture & Quality)
- 完成 DBProvider 到 Room 通路迁移阶段一，降低 legacy API 依赖并增强诊断（#96）。
- 清理当前 code scanning 的 detekt open 告警（复杂度/魔法数字/超长行）（#95）。

### 构建与依赖 (Build & Dependencies)
- 升级依赖：`nl.littlerobots.version-catalog-update` `1.0.1 -> 1.1.0`（#91）。
- 例行更新 Gradle Wrapper 夜版工具链（#97）。
- 升级到 `versionCode 88` / `versionName 3.1.5`。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.4...v3.1.5-beta

---

## [v3.1.4] - 2026-02-21
### 修复 (Fixed)
- 修复验证码测试弹窗在部分场景下无响应的问题（#80）。
- 修复 Android 13 环境下自动输入相关 Hook 的兼容性问题（#78）。
- 补齐设置页国际化文案，修复中文环境中模糊配置项回退英文的问题。

### 功能与体验 (Features & UX)
- 新增隐藏图标恢复能力与秘密代码/快捷方式入口（#79）。
- README 结构调整：星图与下载按钮前置，并补充原始项目致谢与兼容性说明。

### 构建与 CI (Build & CI)
- 发布流程切换为语义化标签触发（`vX.Y.Z`），修复旧标签格式导致的触发/命名问题。
- Draft Release 默认内置 Google Play / GitHub 下载按钮（本仓库与 Xposed 模块仓库同步）。
- 新增 README 徽章自动同步工作流，并补充提交活跃度/贡献者等徽章。
- 调整 Gradle Wrapper 定时更新策略，优化自动更新时效。
- 升级到 `versionCode 87` / `versionName 3.1.4`。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.3...v3.1.4

---

## [v3.1.3] - 2026-02-19
### 修复与兼容性 (Fixes & Compatibility)
- 新增 Android 16（SDK 36）`PermissionManagerService` 兼容 Hook，适配系统 API 变化。
- 增强 `onPackageInstalled` 参数校验与异常日志记录，降低运行时崩溃风险。
- 改进 `getAllUserIds` 返回值兼容逻辑，同时适配 `IntArray` / `List<UserInfo>`。

### 文档与发布 (Docs & Release)
- README 徽章与下载入口样式统一，提升版本/下载信息可见性。
- 完成与 `Xposed-Modules-Repo` 的模块仓库对接验证。

### 构建依赖 (Build)
- Gradle Wrapper 更新至 nightly。
- KSP 升级至 `2.3.6`。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.2...v3.1.3

---

## [v3.1.2] - 2026-02-19
### 功能与工程 (Features & Engineering)
- 新增验证码自动填充后的自动回车能力，完善自动化登录流程。
- 引入输入注入回退策略，提升不同系统环境下的自动填充成功率。
- 构建系统全面迁移至 Gradle Kotlin DSL（KTS），并统一依赖版本管理。

### 国际化与发布 (i18n & Release)
- 多页面国际化完善，并补充系统版本代号展示。
- CI 支持按 tag 后缀自动分发至 Google Play 多轨道并同步发布说明。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.1...v3.1.2

---

## [v3.1.1] - 2026-02-06
### 质量与自动化 (Quality & Automation)
- 深度集成 Detekt 分析与自动修复流水线，持续收敛代码规范问题。
- 引入 Dependabot 自动化依赖维护，提升依赖更新效率与安全性。
- CI 构建并行化与权限声明完善，优化构建速度与流程安全。

### 稳定性修复 (Stability)
- 修复设置布局重叠、Haze 首次绘制刷新等 UI 稳定性问题。
- 修正 PrefsProvider 访问策略与关键 Hook 引用问题。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.0...v3.1.1

---

## [v3.1.0] - 2026-02-02
### 合规与体验 (Compliance & UX)
- 移除敏感安装权限与应用内自动安装链路，适配 Google Play 合规要求。
- 重构更新引导逻辑，优先跳转商店或外部下载页。

### 界面优化 (UI)
- 重写主界面布局，优化 Edge-to-Edge 下的无缝模糊与转场动画体验。
- 修复 Telegram 换行渲染及系统输入 Hook 稳定性问题。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.7...v3.1.0

---

## [v3.0.7] - 2026-02-02
### 视觉与适配 (UI & Insets)
- 优化 Edge-to-Edge Insets 处理，减少内容与系统栏重叠问题。
- 更新单色启动图标，提升 Android 13+ 主题图标适配。

### CI/CD
- 增强 CI 构建摘要与 Debug 产物上传能力，改进发布通知策略。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.6...v3.0.7

---

## [v3.0.6] - 2026-01-31
### 兼容性与性能 (Compatibility & Performance)
- 升级 compile/target SDK 至 36.1（Baklava）并下调 minSdk 至 24。
- 优化低版本 `sendingUid` 反射路径，增强跨 ROM 兼容性。
- 重构输入注入器缓存与调度逻辑，改善自动填充性能与稳定性。

### 稳定性与发布 (Stability & Release)
- 强化 PrefsProvider 访问控制和实体存储容错能力。
- CI 增加 symbols/mapping 上传，改进崩溃排查支持。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.5...v3.0.6

---

## [v3.0.5] - 2026-01-30
### 修复与增强 (Fixes & Enhancements)
- 修复通知自动取消、Sticky 通知清理与线程泄漏相关问题。
- 重构 `EntityStoreManager`，增强空文件与异常数据处理能力。
- 新增验证码记录滑动删除与设置同步暴露能力。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.4...v3.0.5

---

## [v3.0.4] - 2026-01-30
### 文档与社区 (Docs & Community)
- 新增 Telegram 群组入口并统一 README/应用内社区信息。
- 隐私政策改为在线查看模式，降低维护成本并保证内容实时更新。

### 构建与发布 (Build & Release)
- 集成 Google Play 发布工作流，优化分包构建与依赖版本。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.3...v3.0.4

---

## [v3.0.3] - 2026-01-29
### 优化 (Optimized)
- **Material 3 Expressive 深度迁移**：全面升级至 `androidx.compose.material3:material3:1.5.0-alpha12`。
- **Kotlin for Compose 最佳实践**：
    - 全面应用属性代理 (`by`)，淘汰 `.value` 手写访问。
    - 引入 `@Immutable` 注解与不可变数据模型，极大提升重组性能。
- **UI/UX 增强**：
    - 主题切换支持点击坐标扩散动效。
    - 列表项支持 `animateItem()` 物理移动感动效。
    - 文本交互增强，支持 `SelectionContainer` 与 `basicMarquee`。
- **稳定性修复**：
    - 彻底修复 `SmsParseAction` 中的线程同步与属性重分配问题。
    - 清理了所有不必要的非空断言 (`!!`) 与弃用的 Material 3 API 警告。
    - 优化了 JDK 25 下的原生访问权限配置。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.2...v3.0.3

---

## [v3.0.2] - 2026-01-28
### 修复 (Fixed)
- 修复了自动填写权限逻辑。
- 修复了剪贴板静默失败的问题。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.1...v3.0.2

---

## [v3.0.1] - 2026-01-28
### 发布 (Released)
- v3.0.1 版本发布，包含多处稳定性改进。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.0...v3.0.1

---

## [v3.0.0] - 2026-01-28
### 发布 (Released)
- **UI 大版本更新**：由 Android View 彻底迁移至 Jetpack Compose。
- **架构重构**：采用 Single Activity 架构，引入 Compose Navigation。
- **自动化**：集成 GitHub Actions CI 自动化构建流程。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/2.5.1_53...v3.0.0

---

## [2.5.1_53] - 2026-01-28
### 变更 (Changed)
- 提升版本代码至 53。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/releases/tag/2.5.1_53

---

> [!NOTE]
> 之前的历史日志请参考原始项目：[Original LOG-CN.md](https://github.com/tianma8023/XposedSmsCode/blob/master/LOG-CN.md)

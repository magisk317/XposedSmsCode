# XposedSmsCode 架构收口说明

本文档描述当前 `XposedSmsCode` 的主项目分层、共享子模块边界与开发约束。

## 当前分层

### `app`

- Android application 壳、manifest entry、Receiver / Service 和最终 APK 打包。
- 主项目 composition root：把 `core` 的 ViewModel、runtime gateway 实现和应用生命周期装配到一起。
- 负责把 `smscode-rules` 内容子模块同步为 APK assets。
- libxposed metadata 仍由 `app/src/main/resources/META-INF/xposed/` 打包，但 entry/runtime 实现归 `hook` 所有。
- 不重新定义已经下沉到 `smscode-core` 的验证码主链基础设施，也不承载 Xposed runtime 实现。

### `core`

- 历史命名的 UI core / presentation 层，不是底层 core。
- 承载 Compose UI、导航、ViewModel、主题和应用内 UI gateway 消费端。
- 只通过 `runtime.bridge.Ui*Access` 等 gateway 使用项目运行时；Koin 对 runtime 实现的绑定归 `app` composition root。
- 不直接引用 `Runtime*Facade`、`AppDatabase`、`DBManager`、`DBProvider` 或 update/store 实现类。
- update 检查结果、策略判断与 Play 更新动作统一经 `UiUpdateAccess`。

### `hook`

- 主项目专属 Xposed Android 层。
- 承载 libxposed entry、hook 宿主适配、`XposedRuntimeInstaller`、`CorePrefsBridge` 和项目专属 hooks/actions。
- 可依赖 `runtime` gateway/实现与共享 `smscode-core:hook`，但不得反向依赖 `app` 或 UI `core`。
- 反射入口类名保持 `io.github.magisk317.smscode.xp.LibXposedEntry`，由 app 中的 metadata 引用并随最终 APK 合并。

### `runtime`

- 项目专属运行时与数据主层。
- 承载 `common/constant`、runtime-only `common/utils`、`data/db`、`data/prefs`、
  `data/update`、`feature/backup`、`feature/store`、`forwarder/*`。
- 允许继续保留现有 Kotlin package，不强制做包名重命名。
- 对外优先暴露 facade，减少上层直接依赖实现细节。
- 不引入 Compose/UI API。

### `smscode-core`

- 继续作为验证码主链和跨项目公共能力的唯一共享实现来源。
- 当前 Gradle 子模块为 `contract`、`db`、`domain`、`hook`、`rule`、`runtime`、`verification`。
- `contract` 放接口、DTO、跨进程/跨模块契约。
- `domain` 放领域模型和值对象。
- `hook` 放共享 hook infra、libxposed 适配、系统输入与 fallback policy。
- `runtime` 放日志、备份、规则目录、更新、包环境等可复用运行时能力。
- `verification` 放短信分发、解析、通知、自动输入、去重等共享策略。
- `db` 放跨项目共享的 Room 数据层（验证码记录/规则实体、DAO 与 schema 迁移；xinyi v32->v33 迁移时将数据层统一到本模块）。

### `smscode-core:hook` 宿主契约（2026-10-15）

- `HookHostCapabilities`（`io.github.magisk317.smscode.xposed.hook`，smscode-core 0273466 引入）是共享 hook 层消费的唯一宿主能力契约；域 hooks（转发主链、保活、验证偏好）不进入契约。
- 本仓经 `hook` 模块 `XscHookHostCapabilities`（`io.github.magisk317.smscode.xp.hook.helper`）实现契约；`SmsHookBridgeHelper` + `SmsHookRuntimeSession` 负责 hook 生命周期（`@Volatile` 字段 + `synchronized` resolve/heartbeat 注入），已移除 per-hook runtime context 对象。
- xinyi-relay 为对等参考实现（`modules/hook/entry` 的 `XinyiHookHostCapabilities` + 同名 `SmsHookRuntimeSession`）。
- 派发标记为三参：`VerificationSmsIntentHookSupport.markDispatchHandled(intent, action, DISPATCH_HANDLER_KEY)`，本仓 `DISPATCH_HANDLER_KEY = "sms_handler"`，与 xinyi 对齐。
- 契约测试位于 `app/src/test/kotlin/io/github/magisk317/smscode/xp/hook/code/`（`SmsHandlerHookContractTest`、`MmsMessagesHookContractTest`），固定"先标记 handled 再解析"的顺序与适配器接线。
- 契约变更必须同批同步两仓适配器，并 bump 所有消费 `smscode-core:hook` 的父仓 gitlink。

### `smscode-rules`

- 内容型子模块，提供官方验证码规则快照与远程目录结构。
- 仅通过 generated assets 打入 APK，不作为 Gradle/Kotlin 代码模块参与编译。
- 官方规则只读展示，用户自定义规则仍由本地 DB、备份、导入导出链路承载。

## 依赖方向

- `app -> core, hook, runtime, smscode-core:*`（最终 composition/packaging root）
- `core -> runtime` 的公开 UI gateway/model、`magisk-ui-kit` 与共享领域模型；不得绑定 runtime facade 实现
- `hook -> runtime, magisk-xposed-kit, smscode-core:*`（`smscode-core` 全部 7 个模块）
- `runtime -> smscode-core:*`（`smscode-core` 全部 7 个模块）
- `smscode-rules` 不参与 Kotlin 依赖图，只作为 APK assets 输入。

约束：

- `core` 是 UI/展示层，不得被当作底层 contract 模块继续塞共享业务逻辑。
- `core` 不得直接引用 runtime 的 facade、DB/update/store 实现类；实现绑定只能在 `app` composition root。
- `runtime` 不得引入 Compose/UI API。
- `hook` 不得依赖 `app`/`core`；`app` 不得承载 `io.github.magisk317.smscode.xp` 生产源码。
- 可复用短信策略应优先下沉到 `smscode-core:verification` 或 `smscode-core:hook`，主项目 `hook` 只保留项目专属 Android/Xposed 宿主适配。

## 构建治理

- 统一通过 `build-logic` convention plugin 提供 distribution flavor 和打包规则。
- 跨仓共享子模块 pin 矩阵（build-logic / magisk-ui-kit / magisk-xposed-kit / smscode-core / smscode-rules 的统一 sha）以 AGENTS.md「Cross-Repo Submodule Pin Matrix」一节为唯一真源；任何子模块源仓推进后，按该矩阵同批 bump 全部消费方父仓。
- 当前主线 distribution flavor：
  - `play`: Play AAB 发布渠道，禁用 APK assemble。
  - `github`: GitHub APK 发布渠道。
  - `fdroid`: 当前禁用。
- 正式发布线已收敛到 libxposed API 102 热重载主路径。
- `app/src/main/resources/META-INF/xposed/` 是当前 libxposed 元数据来源，包含
  `module.prop`、`java_init.list`、`scope.list`。
- `legacy` 仅保留为独立分支和独立 `legacy-ci.yml` 工作流，不再作为当前主线 flavor 维护。

## 当前边界闸门

- `verifyModuleBoundaries`: 根级汇总入口，聚合主模块 Gradle 依赖方向检查和下列局部边界 task。
- `verifyMainModuleDependencies`: 禁止 `runtime` 反向依赖 `app/core`，禁止 `core` 依赖 `app`，
  并禁止 `smscode-core:*` 依赖主项目或 `magisk-ui-kit`。
- `runtime:verifyNoComposeUiLeak`: 禁止 runtime 源码引入 Compose / UI API。
- `core:verifyNoRuntimeStorageImplLeak`: 禁止 core 直接绑定任意 `Runtime*Facade`，并禁止存储、更新和 feature internals；UI 只能消费 runtime gateway/model。
- `app:verifyNoLocalVerificationEngine`: 禁止 app 重新引入共享验证码引擎基础设施，也禁止 `app` 拥有 `io.github.magisk317.smscode.xp` 生产源码；Xposed entry/runtime 必须位于 `hook`。
- `scripts/checks/verify_shared_submodule_compat.sh`: 验证根边界、`smscode-core` domain 单测、
  verification detekt、hook/runtime lint、`core` 和 `app:check` 的兼容链路。

## 继续优化的方向

1. 继续收窄 `runtime.bridge.Ui*Access`，逐步以 UI DTO/port 替代 DB entity 和 manager 暴露；runtime 实现绑定保持在 `app` composition root。
2. 继续把主项目 `hook` 内可复用的短信策略下沉到 `smscode-core:verification`，`hook` 只保留宿主适配。
3. 视风险决定是否把主项目 `core` 重命名为 `ui-core` / `presentation`；当前先通过文档和边界闸门消除歧义。
4. 继续把测试按模块语义归位，避免 `app` 承载 runtime/core 的单元测试；app 可保留最终 APK metadata/R8 集成契约测试。

## 平台兼容性：Android 17 (API 37)

| 影响等级 | 问题 | 状态 |
|---------|------|------|
| 🔴 严重 | `SMS_RECEIVED_ACTION` 对 OTP 短信施加 3 小时延迟（`SmsIntentHookSupport.kt:16`）。Xposed hook 可能不受限制，需实测确认。 | 待验证 |
| ⚠️ 高 | 应用内存限制（基于设备 RAM）。需建立内存基准。 | 待验证 |
| ⚠️ 中 | PendingIntent mutability 显式声明。 | 已合规 |
| ⚠️ 中 | 内部文件写入权限收紧（`MODE_WORLD_READABLE` 在 targetSdk 37 抛异常）。 | 已合规 |

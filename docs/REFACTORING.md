# XposedSmsCode 架构收口说明

本文档描述当前 `XposedSmsCode` 的主项目分层、共享子模块边界与开发约束。

## 当前分层

### `app`

- Android application 壳、manifest entry、Receiver / Service。
- libxposed entry、hook 宿主适配、migration / transition、shared `smscode-core` 装配桥。
- 负责把 `smscode-rules` 内容子模块同步为 APK assets。
- 不重新定义已经下沉到 `smscode-core` 的验证码主链基础设施。

### `core`

- 历史命名的 UI core / presentation 层，不是底层 core。
- 承载 Compose UI、导航、ViewModel、主题、应用内 UI facade 和 Koin UI 装配。
- 允许依赖 `runtime` facade、`magisk-ui-kit` 和共享领域模型。
- 不直接依赖 `AppDatabase`、`DBManager`、`DBProvider`。
- 不直接依赖 update 实现类，例如下载、安装、校验器。
- update 检查结果、策略判断与 Play 更新动作统一经 `RuntimeUpdateFacade`。

### `runtime`

- 项目专属运行时与数据主层。
- 承载 `common/constant`、runtime-only `common/utils`、`data/db`、`data/prefs`、
  `data/update`、`feature/backup`、`feature/store`、`forwarder/*`。
- 允许继续保留现有 Kotlin package，不强制做包名重命名。
- 对外优先暴露 facade，减少上层直接依赖实现细节。
- 不引入 Compose/UI API。

### `smscode-core`

- 继续作为验证码主链和跨项目公共能力的唯一共享实现来源。
- 当前 Gradle 子模块为 `contract`、`domain`、`hook`、`rule`、`runtime`、`verification`、
  `xposed`。
- `contract` 放接口、DTO、跨进程/跨模块契约。
- `domain` 放领域模型和值对象。
- `hook` / `xposed` 放共享 hook infra、libxposed 适配、系统输入与 fallback policy。
- `runtime` 放日志、备份、规则目录、更新、包环境等可复用运行时能力。
- `verification` 放短信分发、解析、通知、自动输入、去重等共享策略。

### `smscode-rules`

- 内容型子模块，提供官方验证码规则快照与远程目录结构。
- 仅通过 generated assets 打入 APK，不作为 Gradle/Kotlin 代码模块参与编译。
- 官方规则只读展示，用户自定义规则仍由本地 DB、备份、导入导出链路承载。

## 依赖方向

- `app -> core, runtime, smscode-core:domain, smscode-core:runtime, smscode-core:verification, smscode-core:xposed`
- `core -> runtime, magisk-ui-kit, smscode-core:domain, smscode-core:runtime`
- `runtime -> smscode-core:domain, smscode-core:runtime, smscode-core:xposed`
- `smscode-rules` 不参与 Kotlin 依赖图，只作为 APK assets 输入。

约束：

- `core` 是 UI/展示层，不得被当作底层 contract 模块继续塞共享业务逻辑。
- `core` 不得直接依赖 runtime 的 DB/update/store 实现类。
- `runtime` 不得引入 Compose/UI API。
- `app` 不得重新放回本地验证码引擎基础设施。
- `app/xp` 新增可复用短信策略时，应优先考虑下沉到 `smscode-core:verification` 或
  `smscode-core:xposed`，app 侧只保留 Android/Xposed 宿主适配。

## 构建治理

- 统一通过 `build-logic` convention plugin 提供 distribution flavor 和打包规则。
- 当前主线 distribution flavor：
  - `play`: Play AAB 发布渠道，禁用 APK assemble。
  - `github`: GitHub APK 发布渠道。
  - `fdroid`: 当前禁用。
- 正式发布线已收敛到 libxposed API 101+ 主路径。
- `app/src/main/resources/META-INF/xposed/` 是当前 libxposed 元数据来源，包含
  `module.prop`、`java_init.list`、`scope.list`。
- `legacy` 仅保留为独立分支和独立 `legacy-ci.yml` 工作流，不再作为当前主线 flavor 维护。

## 当前边界闸门

- `verifyModuleBoundaries`: 根级汇总入口，聚合主模块 Gradle 依赖方向检查和下列局部边界 task。
- `verifyMainModuleDependencies`: 禁止 `runtime` 反向依赖 `app/core`，禁止 `core` 依赖 `app`，
  并禁止 `smscode-core:*` 依赖主项目或 `magisk-ui-kit`。
- `runtime:verifyNoComposeUiLeak`: 禁止 runtime 源码引入 Compose / UI API。
- `core:verifyNoRuntimeStorageImplLeak`: 禁止 core 直接依赖 runtime 存储、更新和部分 feature internals；
  `ui/record` 的记录查询、删除、恢复、导出必须通过 `RuntimeCodeRecordFacade`。
- `app:verifyNoLocalVerificationEngine`: 禁止 app 重新引入已经共享化的验证码引擎基础设施，
  并禁止 `app/xp` hook 代码直接借用 `core.ui.record` UI 实现。
- `scripts/verify_shared_submodule_compat.sh`: 验证根边界、`smscode-core` domain 单测、
  verification detekt、hook/runtime/xposed lint、`core` 和 `app:check` 的兼容链路。

## 继续优化的方向

1. 继续增加 runtime facade，减少 `core/app` 对 runtime 具体实现和 UI 实现的交叉感知；记录恢复文件链路已迁到 `RuntimeCodeRecordRestoreFacade`，记录页存储与导出入口已收口到 `RuntimeCodeRecordFacade`。
2. 继续把 `app/xp/hook/code` 内可复用的短信策略下沉到 `smscode-core:verification`，app 侧保留宿主适配。
3. 视风险决定是否把主项目 `core` 重命名为 `ui-core` / `presentation`；当前先通过文档和边界闸门消除歧义。
4. 继续把测试按模块语义归位，避免 `app` 承载 runtime/core 的测试。

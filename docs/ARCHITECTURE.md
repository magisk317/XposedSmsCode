# 系统架构与代码分层

最后更新：2026-10-20

> 本文是 XposedSmsCode 的架构地图与命名指南。分层论证、边界细则与重构历程见
> [REFACTORING.md](REFACTORING.md)；协作纪律、子模块 pin 矩阵与 hook 契约见
> [../AGENTS.md](../AGENTS.md)。

## 总览

XposedSmsCode（XSC）是一个 Android/Kotlin LSPosed/Xposed 模块：在宿主短信进程中 hook
验证码短信，完成识别、剪贴板、通知展示与可选自动输入；同时以一个普通 Android app
提供规则管理、记录查询与设置。

两个执行面：

- **app 进程**：普通应用进程，承载 Compose UI、配置、记录查询、更新检查。
- **Xposed 进程**：注入宿主应用（短信/电话等）的 hook 侧，入口类
  `io.github.magisk317.smscode.xp.LibXposedEntry` 随最终 APK 合并，但源码位于 `hook`
  模块而非 `app`。

两端不共享进程内存，经 `smscode-core:hook` 的 `HookHostCapabilities` 契约与
`runtime` 的 gateway 协作。

## 命名消歧指南

本仓有几处模块名与直觉不符，新增代码或 review 前先读这张表：

| 名字 | 实际含义 | 不是什么 |
|---|---|---|
| `:presentation` | **UI / presentation 层**（Compose、导航、ViewModel、主题、UI gateway 消费端） | 2026-10-20 由 `:core` 更名对齐；不是底层 core，不承载共享业务逻辑 |
| `:smscode-core:*` | 跨项目共享领域层（contract/domain/hook/rule/runtime/verification/db 七个模块） | 不是主项目私有代码 |
| `:runtime` | 主项目私有的运行时与数据主层（db/prefs/update/backup/store/forwarder） | 不是共享运行时能力 |
| `:smscode-core:runtime` | 可复用的共享运行时能力（日志、备份、规则目录、更新、包环境） | 不含主项目数据实现 |
| `:hook` | 主项目专属 Xposed 宿主适配（entry、installer、prefs bridge、项目专属 hooks） | 不是共享 hook 基础设施 |
| `:smscode-core:hook` | 共享 hook 基础设施（libxposed 适配、系统输入、fallback policy） | 不含宿主适配 |
| `app/`（目录） | application 壳 + composition root | 不承载 `io.github.magisk317.smscode.xp` 生产源码（归 `hook`） |
| `src/nonPlay` | `play` 与 `github` 两个 flavor 共享的非 Play 源集 | 不是独立 flavor |

硬规则：

- `:presentation` 是 UI/展示层，不得被当作底层 contract 模块继续塞共享业务逻辑。
- 可复用的短信策略优先下沉到 `smscode-core:verification` 或 `smscode-core:hook`，
  主项目 `hook` 只保留宿主适配。
- `:core` → `:presentation` 的更名已于 2026-10-20 落地：模块目录、Gradle 坐标、
  Kotlin namespace、R/BuildConfig 引用、CI 与脚本任务路径同步切换；新代码一律写 `:presentation`。

## Gradle 坐标与物理路径

源码目录与 Gradle 坐标不总是 1:1：`smscode/core/*` 是子模块检出目录，经
`settings.gradle.kts` 的 `project(...).projectDir` 重映射为 `:smscode-core:*`。
查找真实路径时以 settings 的映射为准，不要假设「Gradle 名 = 仓库根下同名目录」。

| Gradle 坐标 | 物理目录 |
|---|---|
| `:app` | `app/` |
| `:presentation` | `presentation/` |
| `:hook` | `hook/` |
| `:runtime` | `runtime/` |
| `:smscode-core:hook` | `smscode/core/hook/`（子模块） |
| `:smscode-core:domain` | `smscode/core/domain/`（子模块） |
| `:smscode-core:runtime` | `smscode/core/runtime/`（子模块） |
| `:smscode-core:contract` | `smscode/core/contract/`（子模块） |
| `:smscode-core:rule` | `smscode/core/rule/`（子模块） |
| `:smscode-core:verification` | `smscode/core/verification/`（子模块） |
| `:smscode-core:db` | `smscode/core/db/`（子模块） |
| `:magisk-ui-kit`（+ `:billing` / `:update`） | `magisk-ui-kit/`（子模块） |
| `:magisk-xposed-kit`（+ `:logging` / `:logging-core` / `:diagnostics` / `:permission` / `:entitlement`） | `magisk-xposed-kit/`（子模块） |

注意 XSC **不接入** `:magisk-xposed-kit:keepalive`——该模块供 xinyi-relay 与
MiPushFramework 使用，本仓的保活策略留在 `runtime`/`hook` 主项目侧。

## 模块地图

每个模块一行职责加一条硬约束；完整允许/禁止清单与论证见 REFACTORING.md。

| 模块 | 职责 | 硬约束 |
|---|---|---|
| `:app` | application 壳、manifest entry、Receiver/Service、composition root、APK 打包、`smscode-rules` assets 同步 | 不重新定义已下沉 `smscode-core` 的验证码主链；不承载 `...smscode.xp` 生产源码 |
| `:presentation` | UI/presentation：Compose、导航、ViewModel、主题、UI gateway 消费端 | 不直接引用 `Runtime*Facade`、`AppDatabase`、`DBManager`、`DBProvider` 或 update/store 实现类；只经 `runtime.bridge.Ui*Access` gateway |
| `:hook` | libxposed entry、hook 宿主适配、`XposedRuntimeInstaller`、`CorePrefsBridge`、项目专属 hooks/actions | 不得依赖 `:app` 或 UI `:presentation` |
| `:runtime` | 数据主层：`common/constant`、`data/db`、`data/prefs`、`data/update`、`feature/backup`、`feature/store`、`forwarder/*` | 禁止 Compose/UI API；对外优先 facade |
| `:smscode-core:*` | 共享验证码主链与跨项目公共能力（唯一共享实现来源） | 不得反向依赖主项目模块或 `:magisk-ui-kit` |
| `:magisk-ui-kit` | 共享 UI chrome（搜索面、tab scaffold、系统栏 scrim） | 父模块只保留路由/业务差异，不复制 chrome 样式 |
| `:magisk-xposed-kit:*` | 共享 Xposed 侧能力：logging / logging-core / diagnostics / permission / entitlement | — |
| `smscode/rules` | 内容型子模块：官方验证码规则快照与远程目录结构 | 只作 APK assets，不进 Kotlin 依赖图 |

## 共享子模块生态

全部以 git submodule 挂载、按 SHA pin（无 tag）。pin 矩阵以 AGENTS.md
「Cross-Repo Submodule Pin Matrix」为唯一真源；实查当前检出用
`git submodule status`。任何子模块源仓推进后，按矩阵同批 bump 全部消费方父仓，
不允许跨仓出现等价但分叉的 SHA。

| 子模块 | 内容 |
|---|---|
| `build-logic` | convention 插件（见下节） |
| `magisk-ui-kit` | 共享 UI chrome：root + `billing` + `update` |
| `magisk-xposed-kit` | 共享 Xposed 能力：logging / logging-core / diagnostics / permission / entitlement（`keepalive` 仅供 xinyi-relay / MiPushFramework） |
| `smscode/core` | 共享领域层 7 模块 |
| `smscode/rules` | 官方验证码规则快照 |

### build-logic convention 插件

以 composite build（`pluginManagement { includeBuild("build-logic") }`）接入；
根 `plugins {}` 声明、模块级按需 apply：

| 插件 | 职责 |
|---|---|
| `magisk.distribution.policy` | 根级：创建 `magiskDistribution` extension |
| `magisk.android.distribution` | 模块级：消费根声明，生成 `distribution` dimension + flavor + BuildConfig 能力字段 |
| `magisk.android.application` / `magisk.android.library` | Android 壳（SDK/JDK/Kotlin 基线） |
| `magisk.android.compose` | Compose 开关 |
| `magisk.android.common` | 上述插件的公共胶水 |
| `magisk.app.packaging` / `magisk.app.signing` | APK 分包与签名 |
| `magisk.mobile.gate` | Play 渠道的 mobile automation 激活门 |
| `magisk.android.room` | Room + KSP 装配 |
| `magisk.kotlin.library` | 纯 Kotlin/JVM 库壳 |
| `magisk.maintenance` | 维护任务挂载 |

机制/数据分离（2026-10-20 起）：共享仓只保留「把声明表变成 AGP flavor」的机制，
各消费者仓在根 `build.gradle.kts` 用自己的 `magiskDistribution {}` 声明矩阵；
模块 apply `magisk.android.distribution` 时若根未声明，直接报错（fail-closed），
不退化成无 flavor 构建。

## 发行渠道

| flavor | 用途 | 状态 |
|---|---|---|
| `play` | Play AAB；billing 由 `magisk-ui-kit:billing` 承载（仅 play variant 装配） | 活跃 |
| `github` | GitHub APK sideload，完整 hook 能力 | 活跃 |
| `fdroid` | — | **已暂停**（与 xinyi-relay 对齐）；恢复点为根 `build.gradle.kts` 中 `magiskDistribution {}` 块注释掉的 `flavor("fdroid")` 行 |

`play` 与 `github` 共享 `src/nonPlay` 源集；`legacy` 仅保留为独立分支与独立工作流，
不再作为当前主线 flavor 维护。

## 依赖方向与边界闸门

依赖方向：

```
app ──> core ──> runtime ──> smscode-core:*
 │        │                    （7 个模块全部）
 │        └──> magisk-ui-kit
 ├──> hook ──> runtime / magisk-xposed-kit / smscode-core:*
 └──> runtime / smscode-core:*

smscode-core:* 不反向依赖任何主项目模块；smscode/rules 不进依赖图。
```

闸门（`./gradlew verifyModuleBoundaries` 聚合）：

| 闸门 | 禁令 |
|---|---|
| `verifyMainModuleDependencies` | `runtime` 不反向依赖 `app`/`presentation`；`presentation` 不依赖 `app`；`smscode-core:*` 不依赖主项目或 `magisk-ui-kit` |
| `runtime:verifyNoComposeUiLeak` | `runtime` 源码禁 Compose/UI API |
| `presentation:verifyNoRuntimeStorageImplLeak` | `presentation` 禁直接绑定任意 `Runtime*Facade`，禁存储/更新/feature internals |
| `app:verifyNoLocalVerificationEngine` | `app` 禁重新引入共享验证码引擎；禁 `...smscode.xp` 生产源码 |

跨仓兼容面另由 toolkit 的 `gradle/verify_consumer_compat.sh` 校验
（配置见 `scripts/config/compat-check.json`）。

## 构建与仓库解析

- `repositoriesMode = FAIL_ON_PROJECT_REPOS`（2026-10-20 与 xinyi-relay /
  MiPushFramework 拉平）：项目级再声明仓库直接构建失败，仓库清单只存在于
  `settings.gradle.kts`。
- 依赖仓库（均带 content 过滤，按组收敛）：
  - `google()` / `mavenCentral()`：AndroidX 与 Kotlin 生态。
  - `JitPack`：仅 `com.github.*`（libsu）。
  - `MagiskMobilePrivate`：GitLab 包注册表，仅 `com.magisk317.mobile`
    （`entitlement-android`）。
- **无需凭据**：两个 GitLab 包注册表所属项目均为 public，匿名读取即可；
  URL 走 `gradleProperty -> 环境变量 -> 默认值` 回退链，不落任何 token，
  与 MiPush（无私有仓）、xinyi（多一个 `rustls` GitLabPackages）写法一致。
- JDK：本地构建用自己的 toolchain；CI JDK 由 toolkit 基础镜像提供
  （`eclipse-temurin:27_35-jdk-resolute`），本仓不重复声明。

## CI 与质量流程

- `.gitlab-ci.yml` 以 `include` 引入 `magisk3171/shared/magisk-ci-toolkit`
  （全 40 位 SHA pin，所有入口一致），阶段 build / release / quality /
  maintenance；发布链、Play beta、telegram 通知、renovate、GitHub 镜像同步
  均走 toolkit 模板，本仓只放项目变量与 rules。
- 本地窄检查优先（命令见 AGENTS.md「Build And Test」），宽校验用
  `detekt` / `koverVerify*` / `check`。
- hook 路径行为（SMS hook、自动输入、剪贴板）必须有机型/LSPosed 运行时证据，
  编译通过不算完成。

## 已知技术债与演进

见 REFACTORING.md「继续优化的方向」「平台兼容性：Android 17 (API 37)」。
当前两项待验证：OTP 短信 3 小时广播延迟对 hook 路径的影响、应用内存限制基准。

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/ARCHITECTURE.md`（本文） | 架构地图、命名消歧、构建与仓库解析 |
| `docs/REFACTORING.md` | 分层论证、边界细则、重构历程、Android 17 兼容性 |
| `AGENTS.md` | 协作纪律、子模块 pin 矩阵、hook 宿主契约 |
| `README.md` / `README-EN.md` | 产品说明 |

## Presentation module baseline (2026-10-26)

`:presentation` stays a single module for now: `app` is its only consumer,
screen domains are already isolated by package (`ui/home`, `ui/record`,
`ui/smscoderule`), and a Gradle split would churn a shipped module without a
boundary it does not already have. The enforced guard is `verifyGodFileLimits`
(scripts/checks/report_god_files.sh): files at or above 900 lines are pinned at
exact counts in scripts/config/god_file_limits.txt - growth fails the build,
shrink requires lowering the reviewed entry. Revisit a module split if
presentation gains a second consumer or the home shell grows another screen
family.

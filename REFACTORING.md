# 重构与现代化报告

本文档概述了 **XposedSmsCode** 项目升级过程中的主要重构和现代化工作。

## 变更摘要

### 1. 语言迁移 (Java 转 Kotlin)
-   **100% 转换**: 整个代码库已从 Java 转换为 **Kotlin**。
-   **现代语法**: 利用了 Kotlin 的特性，如属性 (Properties)、扩展函数 (Extensions)、高阶函数、协程 (Coroutines) 和对象 (Objects)。
-   **空安全**: 改进了整个应用的空安全处理，减少了空指针异常 (NPE)。

### 2. UI 现代化 (Material Design 3)
-   **MD3 主题迁移**: 全局主题升级为 `Theme.Material3`。
-   **动态配色 (Dynamic Colors)**: 在 Application 中启用了 MD3 动态配色功能。
-   **组件升级**:
    -   `Toolbar` -> `MaterialToolbar`: 提升标题栏体验。
    -   `Button` -> `MaterialButton`: 使用 MD3 TonalButton 等样式。
    -   `CheckBox` -> `MaterialCheckBox`: 全局列表项复选框升级。
    -   `FAB` -> MD3 风格 FAB。
-   **进度提示**: 实现了自定义的 MD3 风格进度对话框 (`CircularProgressIndicator`)。
-   **Jetpack Compose**: `FaqFragment` 已完全使用 Compose + Material 3 重写，提供更现代的交互体验。
-   **功能迁移与清理**:
    -   **LSPosed 独占支持**: 移除了对 EdXposed、太极 (TaiChi) 及原版 Xposed 的所有特定逻辑及提示。
    -   **UI 清理**: 移除了首页菜单中针对旧框架的“须知”入口及相关对话框。
    -   **代码瘦身**: 删除了 `PackageUtils` 中用于跳转旧版 Xposed 管理器或太极管理器的冗余代码，并清理了 `Const.kt` 中的相关包名常量。
-   **深色模式支持**: 移除了大量硬编码颜色（如 `@android:color/white`），改用主题属性（如 `?attr/colorSurface`），通过动态配色方案完美适配深色模式。

### 3. 编译警告与 lint 清理
-   **Kotlin 现代化**: 替换了所有过时的 `toLowerCase()` 为 `lowercase()`。
-   **导航 API 迁移**: 替换了过时的 `onBackPressed()` 呼叫，全面转向 `onBackPressedDispatcher`。
-   **编译器与构建脚本清理**: 
    -   移除了不再支持的 `freeCompilerArgs` 标识。
    -   移除了 `gradle.properties` 中过时的 `android.enableResourceOptimizations` 配置。
    -   修正了 `alpha` 构建类型的 `debuggable` 属性，消除了 R8 优化警告。
-   **代码逻辑优化**: 移除了 Xposed Hook 类中冗余的 null 检查（Kotlin 智能类型推断），并精准抑制了 `DBProvider` 等遗留组件的 `DEPRECATION` 警告。
-   **精准警告抑制**: 为 Xposed 必须的 `MODE_WORLD_READABLE` 等 API 添加了 `@Suppress("DEPRECATION")` 标识，确保编译输出清洁。

### 3. 组件现代化 (Component Modernization)
-   **minSdkVersion 升级**: 从 24 进一步提升至 **35 (Android 15)**。
    -   **影响分析**: 应用现在仅支持 Android 15 及以上设备。这允许我们彻底移除针对旧版本的兼容逻辑，大幅简化代码库。
    -   **Edge-to-Edge**: 适配了 Android 15 强制要求的全屏显示 (Edge-to-Edge)，通过 `WindowInsets` 处理确保 UI 在系统栏下方正常显示。
-   **Activity Result API**: 移除了 `startActivityForResult`，使用 `ActivityResultLauncher` 处理权限和文件操作。
-   **Back Press Handling**: 迁移至 `OnBackPressedDispatcher` API。
-   **Menu Provider**: 使用 `MenuProvider` 解耦菜单逻辑。

### 4. 架构现代化
-   **MVVM**: 核心模块迁移至 **MVVM** 架构。
-   **Google 架构组件**: 使用 `ViewModel`, `ViewBinding`, `Room` (替代 GreenDAO)。

### 5. 异步处理
-   **Coroutines**: 全面使用 Kotlin 协程处理异步任务。
### 6. 构建与依赖现代化
-   **依赖库全面升级**: 升级了所有 AndroidX 核心库、Material 组件、Lifecycle、Room、Retrofit、OkHttp 等至 2024/2025 最新稳定版。
-   **构建工具升级**: 适配了 Gradle 9.3+ 和 Java 21。
-   **KSP 迁移**: 替代 KAPT 处理 Room 注解，提升编译速度。

## 已移除的关键库
-   `ButterKnife`
-   `GreenDAO`
-   `RxJava` / `RxAndroid`
-   `Dagger`
-   `MaterialDialogs` (afollestad) -> 迁移至 MD3 `MaterialAlertDialogBuilder`

## 下一步计划
-   **Compose 深度集成**: 继续推进 Jetpack Compose 的 UI 改造。
-   **UI 细节打磨**: 进一步优化 MD3 动态配色和交互动画。
-   **适配 Android 15+**: 完善了 Edge-to-Edge 适配和现代 Hooking 逻辑。
-   **类型安全 Intent/Bundle**: 全面迁移至 AndroidX 的 `BundleCompat` 和 `IntentCompat`。

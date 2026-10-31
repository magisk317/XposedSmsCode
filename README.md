# XposedSmsCode

![Star History Chart](https://api.star-history.com/svg?repos=magisk317/XposedSmsCode&type=Date)

<div align="center">
    <a href="https://play.google.com/store/apps/details?id=com.github.tianma8023.xposed.smscode">
        <img src="https://play.google.com/intl/zh-CN/badges/static/images/badges/zh-cn_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://github.com/magisk317/XposedSmsCode/releases">
        <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/master/badge_github.png" alt="Get it on GitHub" height="80"/>
    </a>
    <a href="https://gitlab.com/magisk3171/XposedSmsCode/-/releases">
        <img src="https://img.shields.io/badge/Get%20it%20on-GitLab-FC6D26?style=for-the-badge&logo=gitlab&logoColor=white" alt="Get it on GitLab" height="40"/>
    </a>
</div>

<div align="center">

<!-- badges:platform:start -->
[![GitLab](https://img.shields.io/badge/GitLab-magisk3171/XposedSmsCode-FC6D26?style=flat-square&logo=gitlab&logoColor=white)](https://gitlab.com/magisk3171/XposedSmsCode) [![CI](https://img.shields.io/gitlab/pipeline-status/magisk3171%2FXposedSmsCode?branch=beta&style=flat-square&logo=gitlab&label=CI)](https://gitlab.com/magisk3171/XposedSmsCode/-/pipelines?ref=beta) [![Latest Release](https://img.shields.io/gitlab/v/release/magisk3171%2FXposedSmsCode?include_prereleases&style=flat-square&logo=gitlab)](https://gitlab.com/magisk3171/XposedSmsCode/-/releases) [![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)
<!-- badges:platform:end -->

<!-- badges:tech:start -->
[![Kotlin](https://img.shields.io/badge/Kotlin-2.5.0--Beta2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Java](https://img.shields.io/badge/Java-27%2B-E76F00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.10.01-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.9.0--rc--1-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.5.0-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-28-brightgreen?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-37-blue?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-102-orange?style=flat-square)](https://github.com/libxposed/api) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)
<!-- badges:tech:end -->

</div>

识别短信验证码的 Xposed 模块：收到验证码短信后自动提取验证码，复制到剪贴板，并可以在支持的应用中自动输入。

如果你还需要把验证码、短信内容或应用通知转发到 Telegram、Webhook、邮箱等通道，可以了解一下新项目 [信驿 Relay](https://gitlab.com/magisk3171/xinyi-relay)。它更偏向消息转发与验证码联动场景；若主要需求是验证码识别与自动输入，继续使用本项目会更合适。

[English Version](./README-EN.md)

# 应用截图
<img src="./art/cn/01.png" width="180"/><img src="./art/cn/02.png" width="180"/><img src="./art/cn/03.png" width="180"/><img src="./art/cn/04.png" width="180"/>

# 交流与反馈
- [Telegram Group](https://t.me/+NR2QaQ4dlEgxYmNl)

# 使用
1. Root 你的设备，安装 Xposed/LSPosed 框架；
2. 安装本模块，在框架中激活并重启；
3. 在模块设置中按需开启复制、Toast、通知、自动输入等能力。

遇到问题请先阅读模块内的「常见问题」，再提交反馈。

# 功能
- 收到验证码短信后提取验证码，并复制到系统剪贴板（可开关）
- 提取到验证码时显示 Toast 提示（可开关）
- 提取到验证码时显示状态栏通知：支持通知归属（模块自身 / 电话服务）、自动取消与保留时间（可开关）
- 在支持的应用中自动输入验证码：无障碍服务辅助定位输入框，支持自动确认/回车、输入延迟与间隔（可开关）
- 拦截验证码短信：提取成功后拦截，短信应用不再收到（实验性）
- 自定义验证码短信关键字，支持正则表达式
- 验证码规则管理：官方规则与自定义匹配规则分层合并，支持配置与规则的备份/恢复
- 验证码历史记录：记录开关、数量上限与 SIM 卡槽备注
- 应用内更新检查：支持启动时自动检查与仅 Wi-Fi 检查
- 运行日志：CI 产出的 debug 构建或本机 debug 版本可导出日志归档并设置保留天数（默认 2 天）；正式版为性能关闭日志打印，设置中不显示日志导出、保留天数与清理入口，需要导出日志请安装 CI 产出的 debug 版本且设备已 root
- Material 3 界面：动态颜色、界面风格（Material / Miuix）、底栏与背景模糊等可视化设置

# 注意
- **此模块适用于偏原生系统，第三方深度定制 Rom 可能存在兼容性差异。**
- **兼容性：最低 Android 9.0（API 28），目标 API 37。**
- **支持 LibXposed API 102。**
- **自动输入需要在系统设置中为本模块开启无障碍服务。**
- **Play 与 GitHub 发行、预留的 F-Droid 配置仅表示发行渠道差异；项目不提供独立 Lite APK，也不以构建变体划分功能层级。**

# 文档
- [更新日志 (Changelog)](docs/CHANGELOG.md)
- [重构汇总 (Refactoring Summary)](docs/REFACTORING.md)
- [系统架构 (Architecture)](docs/ARCHITECTURE.md)
- [隐私政策 (Privacy Policy)](docs/PRIVACY.md)
- [赞助与捐赠 (Donations)](docs/DONATIONS.md)

# 感谢
- [原始项目 (tianma8023/XposedSmsCode)](https://github.com/tianma8023/XposedSmsCode)
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Material Dialogs](https://github.com/afollestad/material-dialogs)
- [EventBus](https://github.com/greenrobot/EventBus)
- [Room](https://developer.android.com/training/data-storage/room)
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)

# 协议
所有的源码均遵循 [GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt) 协议

# 赞助与捐赠
如果本项目对你有帮助，欢迎支持开发者。你的支持会直接用于项目维护与持续迭代。

赞助名单与说明请见：[赞助与捐赠文档](docs/DONATIONS.md)。

| 支付宝收款码 | 微信赞赏码 | 微信收款码 |
| :---: | :---: | :---: |
| ![Alipay](./art/sponsorship/alipay.png) | ![WeChat Appreciation](./art/sponsorship/wx.png) | ![WeChat Collect](./art/sponsorship/wx_collect.png) |

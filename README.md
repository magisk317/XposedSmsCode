# XposedSmsCode

![Star History Chart](https://api.star-history.com/svg?repos=magisk317/XposedSmsCode&type=Date)

<div align="center">
    <a href="https://play.google.com/store/apps/details?id=com.github.tianma8023.xposed.smscode">
        <img src="https://play.google.com/intl/zh-CN/badges/static/images/badges/zh-cn_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://github.com/magisk317/XposedSmsCode/releases">
        <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/master/badge_github.png" alt="Get it on GitHub" height="80"/>
    </a>
</div>

<div align="center">

[![Commits](https://img.shields.io/github/commit-activity/y/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/graphs/commit-activity) [![Last Commit](https://img.shields.io/github/last-commit/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/commits) [![Contributors](https://img.shields.io/github/contributors/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/graphs/contributors) [![CI](https://img.shields.io/github/actions/workflow/status/magisk317/XposedSmsCode/ci.yml?style=flat-square&label=Build&logo=github-actions&logoColor=white)](https://github.com/magisk317/XposedSmsCode/actions/workflows/ci.yml) [![Latest Release](https://img.shields.io/github/v/release/magisk317/XposedSmsCode?include_prereleases&style=flat-square&logo=github)](https://github.com/magisk317/XposedSmsCode/releases) [![Release Date](https://img.shields.io/github/release-date/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/releases) [![Downloads](https://img.shields.io/github/downloads/magisk317/XposedSmsCode/total?style=flat-square&color=blue)](https://github.com/magisk317/XposedSmsCode/releases) [![License](https://img.shields.io/github/license/magisk317/XposedSmsCode?style=flat-square)](LICENSE)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20--RC2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.02.01-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.5.0--nightly-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.2.0--alpha02-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-24-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-36-blue?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-82-orange?style=flat-square)](https://github.com/rovo89/XposedBridge) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)

</div>

识别短信验证码的Xposed模块，并将验证码拷贝到剪切板，亦可以自动输入验证码。

[English Version](./README-EN.md)

# 应用截图
<img src="./art/cn/01.png" width="180"/><img src="./art/cn/02.png" width="180"/><img src="./art/cn/03.png" width="180"/><img src="./art/cn/04.png" width="180"/>

# 交流与反馈
- [Telegram Group](https://t.me/+NR2QaQ4dlEgxYmNl)


# 使用
1. Root你的设备，安装Xposed框架；
2. 安装本模块，激活并重启；
3. Enjoy it！

欢迎反馈，欢迎提出意见或建议。

# 注意
- **此模块适用于偏原生的系统，其他第三方定制Rom可能不适用。**
- **兼容性：最低 Android 7.0（API 24），目标 Android 16（API 36）。**
- **支持 LSPosed / Xposed API 82+（具体取决于系统与框架实现）。**
- **代码库：100% Kotlin + Jetpack Compose + Room + Coroutines**
- **遇到问题请先阅读模块中的"常见问题"**

# 功能
- 收到验证码短信后将验证码提取并复制到系统剪贴板
- 收到验证码时显示 Toast 提示
- 收到验证码时显示系统通知
- 将提取过的验证码短信标记为已读（实验性）
- 验证码提取成功后，自动删除该验证码短信（实验性）
- 拦截并屏蔽特定的验证码短信
- 支持自定义验证码短信关键字（支持正则表达式）
- 支持自定义验证码匹配提取规则（支持导入与导出规则表）
- 在支持的应用中自动输入提取的验证码
- 提取并转发应用通知消息中的验证码

# 待实现
- 定时发送短信

# 发布元数据维护
- Fastlane 元数据目录：`fastlane/metadata/android`
- 发版前同步 Fastlane 更新日志与截图：`scripts/sync_fastlane_metadata.sh`
- 发版前校验版本与发布元数据：`scripts/check_release_guard.sh`
- Fastlane 的 `changelogs/{versionCode}.txt` 由 `distribution/whatsnew` 自动同步生成。

# 文档
- [更新日志 (Changelog)](docs/CHANGELOG.md)
- [重构汇总 (Refactoring Summary)](docs/REFACTORING.md)
- [隐私政策 (Privacy Policy)](docs/PRIVACY.md)
- [赞助与捐赠 (Donations)](docs/DONATIONS.md)

# 感谢
- [原始项目 (tianma8023/XposedSmsCode)](https://github.com/tianma8023/XposedSmsCode)
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
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

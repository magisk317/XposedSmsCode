# XposedSmsCode

![Star History Chart](https://api.star-history.com/svg?repos=magisk317/XposedSmsCode&type=Date)

<div align="center">
    <a href="https://play.google.com/store/apps/details?id=com.github.tianma8023.xposed.smscode">
        <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
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

An Xposed module which recognizes and parses SMS verification codes: when a verification SMS arrives, the code is extracted, copied to the clipboard, and can be auto-inputted into supported apps.

If you also need to forward verification codes, SMS content, or app notifications to Telegram, Webhook, email, or other channels, check out the newer project [Xinyi Relay](https://gitlab.com/magisk3171/xinyi-relay). It focuses on message relay and verification-code workflows; if your main need is SMS code recognition and auto-input, this project remains the better fit.

[中文版说明](./README.md)

# Screenshots
<img src="./art/en/01.png" width="180"/><img src="./art/en/02.png" width="180"/><img src="./art/en/03.png" width="180"/><img src="./art/en/04.png" width="180"/>

# Communication & Feedback
- [Telegram Group](https://t.me/+NR2QaQ4dlEgxYmNl)

# Usage
1. Root your device and install the Xposed/LSPosed framework.
2. Install this module, activate it in the framework, and reboot.
3. Enable the capabilities you need (clipboard, toast, notification, auto-input) in the module settings.

Please read the in-app FAQ first if you encounter any problems, then file feedback.

# Features
- Extract the verification code from an incoming SMS and copy it to the system clipboard (toggleable)
- Show a toast when a verification code is extracted (toggleable)
- Show a status-bar notification when a code is extracted: notification owner (module itself / phone service), auto-cancel and retention time (toggleable)
- Auto-input the verification code into supported apps: an accessibility service locates the input field, with auto-confirm/enter, input delay and interval (toggleable)
- Block verification SMS: once extracted, the SMS is intercepted and no longer reaches the SMS app (experimental)
- Custom verification-SMS keywords with regular-expression support
- Verification-code rules: official rules and custom match rules merged in layers, with config/rule backup and restore
- Verification-code history: record toggle, entry limit and SIM-slot remarks
- In-app update check: auto-check on start and Wi-Fi-only options
- Runtime logs: log sharing, verbose-log mode, retention days and sensitive-field redaction
- Material 3 UI: dynamic color, interface style (Material / Miuix), bottom bar and background blur, and other visual settings

# Attention
- **This module is designed for AOSP-like systems; heavily customized ROMs may have compatibility issues.**
- **Compatibility: minimum Android 9.0 (API 28), target API 37.**
- **Supports LibXposed API 102.**
- **Auto-input requires enabling the module's accessibility service in system settings.**
- **Play and GitHub distributions, plus the reserved F-Droid configuration, identify distribution channels only; the project ships no separate Lite APK and uses no build variants as feature tiers.**

# Documentation
- [Release Logs](docs/CHANGELOG.md)
- [Refactoring Summary](docs/REFACTORING.md)
- [Privacy Policy](docs/PRIVACY.md)
- [Donations](docs/DONATIONS.md)

# Thanks To
- [Original Project (tianma8023/XposedSmsCode)](https://github.com/tianma8023/XposedSmsCode)
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Material Dialogs](https://github.com/afollestad/material-dialogs)
- [EventBus](https://github.com/greenrobot/EventBus)
- [Room](https://developer.android.com/training/data-storage/room)
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)

# License
All code is licensed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt)

# Donation
If you find this project helpful, please consider supporting the developer. Your support directly helps maintenance and iteration.

Donation list and details: [Donations](docs/DONATIONS.md).

| Alipay Receipt | WeChat Appreciation | WeChat Collect |
| :---: | :---: | :---: |
| ![Alipay](./art/sponsorship/alipay.png) | ![WeChat Appreciation](./art/sponsorship/wx.png) | ![WeChat Collect](./art/sponsorship/wx_collect.png) |

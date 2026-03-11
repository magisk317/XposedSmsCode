# XposedSmsCode

![Star History Chart](https://api.star-history.com/svg?repos=magisk317/XposedSmsCode&type=Date)

<div align="center">
    <a href="https://play.google.com/store/apps/details?id=com.github.tianma8023.xposed.smscode">
        <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://github.com/magisk317/XposedSmsCode/releases">
        <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/master/badge_github.png" alt="Get it on GitHub" height="80"/>
    </a>
</div>

<div align="center">

[![Commits](https://img.shields.io/github/commit-activity/y/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/graphs/commit-activity) [![Last Commit](https://img.shields.io/github/last-commit/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/commits) [![Contributors](https://img.shields.io/github/contributors/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/graphs/contributors) [![CI](https://img.shields.io/github/actions/workflow/status/magisk317/XposedSmsCode/ci.yml?style=flat-square&label=Build&logo=github-actions&logoColor=white)](https://github.com/magisk317/XposedSmsCode/actions/workflows/ci.yml) [![Latest Release](https://img.shields.io/github/v/release/magisk317/XposedSmsCode?include_prereleases&style=flat-square&logo=github)](https://github.com/magisk317/XposedSmsCode/releases) [![Release Date](https://img.shields.io/github/release-date/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/releases) [![Downloads](https://img.shields.io/github/downloads/magisk317/XposedSmsCode/total?style=flat-square&color=blue)](https://github.com/magisk317/XposedSmsCode/releases) [![License](https://img.shields.io/github/license/magisk317/XposedSmsCode?style=flat-square)](LICENSE)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20--RC3-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.03.00-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.5.0--nightly-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.2.0--alpha03-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-26-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-36-blue?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)

</div>

An Xposed module which can recognize, parse SMS code and copy it to clipboard when a new message arrives. It can also input SMS code automatically.

[中文版说明](./README.md)

# Screenshots
<img src="./art/en/01.png" width="180"/><img src="./art/en/02.png" width="180"/><img src="./art/en/03.png" width="180"/><img src="./art/en/04.png" width="180"/>

# Communication & Feedback
- [Telegram Group](https://t.me/+NR2QaQ4dlEgxYmNl)

# Usage
1. Root your device and install Xposed Framework.
2. Install and activite this xposed module and then reboot.
3. Enjoy it!

Welcome any feedbacks.

# Attention
- **This module is designed for AOSP-like systems; it may not function correctly on heavily customized ROMs.**
- **Compatibility: Minimum Android 7.0 (API 24), target Android 16 (API 36).**
- **Supports LSPosed / Xposed API 82+ (depends on ROM and framework implementation).**
- **Tech Stack: 100% Kotlin + Jetpack Compose + Room + Coroutines**
- **Please read the FAQ in the app first if you encounter any problems.**

# Features
- Copy verification code to clipboard when a new message arrives.
- Show toast when the verification code is copied.
- Show notification when verification SMS parsed.
- Mark verification SMS as read (experimental).
- Delete verification SMS when it's extracted successfully (experimental).
- Block verification SMS if it's extracted successfully.
- Custom keywords about verification code message (regular expressions allowed).
- Support the SMS code match rules customization, importation and exportation.
- Auto-input SMS code.
- **Compatible with Android 7+ and continuously optimized for newer Android versions**
- **Material Design 3 (MD3) + Material You Dynamic Color**
- **100% Kotlin + Coroutines + Room Database**
- **Modern UI built with Jetpack Compose**
- **Settings page fully migrated to Jetpack Compose**

# Release Metadata
- Fastlane metadata location: `fastlane/metadata/android`
- Sync Fastlane changelogs/screenshots before release: `scripts/sync_fastlane_metadata.sh`
- Validate release metadata and tag consistency: `scripts/check_release_guard.sh`
- Fastlane changelog files `changelogs/{versionCode}.txt` are synchronized from `distribution/whatsnew`.

# Documentation
- [Release Logs](docs/CHANGELOG.md)
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
If you find this project helpful, please consider rewarding the developer with a cup of coffee. Your support is the greatest motivation for my persistent maintenance!

| Alipay Receipt | WeChat Appreciation | WeChat Collect |
| :---: | :---: | :---: |
| ![Alipay](./art/sponsorship/alipay.png) | ![WeChat Appreciation](./art/sponsorship/wx.png) | ![WeChat Collect](./art/sponsorship/wx_collect.png) |

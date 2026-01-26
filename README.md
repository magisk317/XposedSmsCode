# XposedSmsCode
![Total Downloads](https://img.shields.io/github/downloads/magisk317/XposedSmsCode/total) ![Total Stars](https://img.shields.io/github/stars/magisk317/XposedSmsCode?style=social) [![Latest Release](https://img.shields.io/github/v/release/magisk317/XposedSmsCode?label=Latest%20Release)](https://github.com/magisk317/XposedSmsCode/releases)

![Star History Chart](https://api.star-history.com/svg?repos=magisk317/XposedSmsCode&type=Date)

An Xposed module which can recognize, parse SMS code and copy it to clipboard when a new message arrives. It can also input SMS code automatically.

[中文版说明](./README-CN.md)

# Screenshots
<img src="./art/en/01.png" width="180"/><img src="./art/en/02.png" width="180"/><img src="./art/en/03.png" width="180"/>

# Download
- [GitHub Releases](https://github.com/magisk317/XposedSmsCode/releases)
- ~~[LSPosed Repository](https://github.com/Xposed-Modules-Repo/com.github.tianma8023.xposed.smscode/releases/)~~
- ~~[Coolapk](https://www.coolapk.com/apk/com.github.tianma8023.xposed.smscode)~~
- ~~[Xposed Repository](http://repo.xposed.info/module/com.github.tianma8023.xposed.smscode)~~

# Usage
1. Root your device and install Xposed Framework.
2. Install and activite this xposed module and then reboot.
3. Enjoy it!

Welcome any feedbacks.

# Attention
- **This module is suitable for AOSP ROM, it may not work well on other 3rd-party Rom.**
- **Compatibility: Requires Android 15+ (api level ≥ 35).**
- **Support LSPosed (Android 15+)**
- **Read the FAQ in app first if you encounter any problems.**

# Features
- Copy verification code to clipboard when a new message arrives.
- Show toast when the verification code is copied.
- Show notification when verification SMS parsed.
- Mark verification SMS as read(experimental).
- Delete verification SMS when it's extracted successfully(experimental).
- Block verification SMS if it's extracted successfully.
- Custom keywords about verification code message (regular expressions allowed).
- Support the SMS code match rules customization, importation and exportation.
- Auto-input SMS code.
- Various theme color to choose.

# Release Log
[Release Logs](/LOG-EN.md)

# Thanks To
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Material Dialogs](https://github.com/afollestad/material-dialogs)
- [EventBus](https://github.com/greenrobot/EventBus)
- [Room](https://developer.android.com/training/data-storage/room)
- [Gson](https://github.com/google/gson)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Material Components](https://github.com/material-components/material-components-android)

# License
All code is licensed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt) 

# Donation
If you find this project helpful, please consider rewarding the developer with a cup of coffee. Your support is the greatest motivation for my persistent maintenance!

| Alipay Red Packet | Alipay Receipt | WeChat Appreciation |
| :---: | :---: | :---: |
| ![Alipay Red Packet](./art/sponsorship/alipay_pocket.png) | ![Alipay](./art/sponsorship/alipay.png) | ![WeChat](./art/sponsorship/wx.png) |
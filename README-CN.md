# XposedSmsCode
![Total Downloads](https://img.shields.io/github/downloads/magisk317/XposedSmsCode/total) ![Total Stars](https://img.shields.io/github/stars/magisk317/XposedSmsCode?style=social) [![Latest Release](https://img.shields.io/github/v/release/magisk317/XposedSmsCode?label=Latest%20Release)](https://github.com/magisk317/XposedSmsCode/releases)

![Star History Chart](https://api.star-history.com/svg?repos=magisk317/XposedSmsCode&type=Date)

识别短信验证码的Xposed模块，并将验证码拷贝到剪切板，亦可以自动输入验证码。

[English README](./README-EN.md)

# 应用截图
<img src="./art/cn/01.png" width="180"/><img src="./art/cn/02.png" width="180"/><img src="./art/cn/03.png" width="180"/>

# 下载
下载地址:
- [GitHub Releases](https://github.com/magisk317/XposedSmsCode/releases)
- ~~[LSPosed仓库](https://github.com/Xposed-Modules-Repo/com.github.tianma8023.xposed.smscode/releases/)~~
- ~~[酷安](https://www.coolapk.com/apk/com.github.tianma8023.xposed.smscode)~~
- ~~[Xposed仓库](http://repo.xposed.info/module/com.github.tianma8023.xposed.smscode)~~

# 使用
1. Root你的设备，安装Xposed框架；
2. 安装本模块，激活并重启；
3. Enjoy it！

欢迎反馈，欢迎提出意见或建议。

# 注意
- **此模块适用于偏原生的系统，其他第三方定制Rom可能不适用。**
- **兼容性：兼容 Android 15 及以上（API 等级 ≥ 35）设备。**
- **支持 LSPosed (Android 15+)**
- **代码库：100% Kotlin + Jetpack Compose + Room + Coroutines**
- **遇到问题请先阅读模块中的"常见问题"**

# 功能
- 收到验证码短信后将验证码复制到系统剪贴板
- 收到验证码时显示Toast
- 收到验证码时显示通知
- 将验证码短信标记为已读（实验性）
- 验证码提取成功后，删除验证码短信（实验性）
- 拦截验证码短信
- 自定义验证码短信关键字（正则表达式）
- 自定义验证码匹配规则，并支持规则导入导出
- 自动输入验证码
- **全系统 Android 15 (API 35) 深度适配**
- **Material Design 3 (MD3) + Material You 动态配色**
- **100% Kotlin + 协程 (Coroutines) + Room 数据库**
- **Jetpack Compose 现代化 UI (FaqFragment 已迁移)**

# 更新日志
[更新日志](/LOG-CN.md)

# 感谢
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Material Dialogs](https://github.com/afollestad/material-dialogs)
- [EventBus](https://github.com/greenrobot/EventBus)
- [Room](https://developer.android.com/training/data-storage/room)
- [Gson](https://github.com/google/gson)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)


# 协议
所有的源码均遵循 [GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt) 协议

# 赞助与捐赠
如果您觉得本项目对您有所帮助，欢迎给开发者投喂一杯咖啡。您的支持是我坚持维护的最大动力！

| 支付宝红包口令 | 支付宝收款码 | 微信赞赏码 |
| :---: | :---: | :---: |
| ![Alipay Red Packet](./art/sponsorship/alipay_pocket.png) | ![Alipay](./art/sponsorship/alipay.png) | ![WeChat](./art/sponsorship/wx.png) |
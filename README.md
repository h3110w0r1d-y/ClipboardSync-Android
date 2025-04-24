# Clipboard Sync - Android

Clipboard Sync 是一款支持 Android 和 macOS 平台的剪贴板同步工具，通过 **MQTT协议** 进行数据传输，并采用 **AES-256-CBC** 加密，确保数据传输的安全性。

> ⚠️ **注意**：本软件同时也是 Xposed 插件，主要为了解除 Android 10 开始的对后台应用读取剪贴板权限的限制，故仅支持 **Android 10 及以上版本**，同时也借助Xposed做了保活，不需要常驻通知栏，具体代码请查看Hook类。

## 功能特性

- **跨平台剪贴板同步**：无缝同步 Android 和 macOS 的剪贴板内容。
- **高安全性**：采用 MQTT 协议和 AES-256-CBC 加密，保护数据隐私。
- **后台读取剪贴板**：通过 Xposed Hook 系统，仅允许本应用在后台读取剪贴板内容，不影响其他软件。
- **增强保活功能**：通过修改自身的 `oom_adj` 值，提升进程优先级，防止被系统杀死。

## 使用方法

1. **下载软件**：
   - [Android 版本](https://github.com/h3110w0r1d-y/ClipboardSync-Android/releases)
   - [macOS 版本](https://github.com/h3110w0r1d-y/ClipboardSync-macOS/releases)

2. **启用模块**：
   - 安装 [LSPosed](https://github.com/LSPosed/LSPosed)。
   - 在 LSPosed 中启用本模块，勾选 **系统框架**。

3. **配置 MQTT**：
   - 在 Android 客户端设置界面中，输入您的 MQTT Broker 地址、端口、用户名、密码和加密密钥。
   - 确保其他平台客户端使用相同的配置。

4. **启动同步**：
   - 启动 Android 客户端，并确保 macOS 客户端正常运行。
   - 测试剪贴板内容是否可以跨设备同步。

## 系统要求

- Android 10 及以上版本
- 安装 LSPosed 框架

## 许可证

本项目遵循 MIT License。

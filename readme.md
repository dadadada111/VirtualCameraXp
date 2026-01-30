# 项目回顾与使用指南 (Project Review & Usage Guide)


## 1. XVirtualCamera 介绍与改造回顾

### 1.1 背景与问题
原始的 Xposed 摄像头拦截模块存在严重的兼容性问题，主要表现为：
- 无法正确拦截部分应用的摄像头数据（YUV 格式不匹配）。
- 缺乏用户界面，配置繁琐（需手动编辑配置文件）。
- 播放网络流（RTMP/HTTP）时不稳定。

### 1.2 改造方案
我们引入了成熟的开源项目 [XVirtualCamera](https://github.com/sandyz987/XVirtualCamera) 作为基础，并针对本项目需求进行了以下深度改造：

#### A. 核心代码修改
1.  **逻辑分离与增强 (`PlayIjk.kt`)**:
    *   重构了视频源加载逻辑，严格区分 **网络 URL** 和 **本地文件**。
    *   增加了对 `stream.txt` 配置文件的多级查找策略（应用私有目录 -> 全局目录 -> 缓存目录）。
    *   修复了 URL 读取逻辑，支持自动将 `https` 转换为 `http` 以兼容 IjkPlayer。
    *   增加了详细的 Toast 提示和日志，方便调试。
    *   **路径解析优化**: 支持多路径尝试机制，解决 Xposed hook 上下文中 `/sdcard` 路径无法解析的问题。自动尝试 `/sdcard/DCIM/XVirtualCamera/`、`/storage/emulated/0/DCIM/XVirtualCamera/` 等多个路径。

2.  **UI 交互重构 (`MainActivity.kt` & `activity_main.xml`)**:
    *   开发了全新的配置界面。
    *   **可视化配置**: 用户可以直接在 App 内输入 RTMP/HTTP 地址，或查看本地视频路径提示。
    *   **即时保存**: 配置修改后自动保存到本地文件，无需手动操作文件管理器。
    *   **Root 权限写入**: 自动尝试使用 root 权限写入目标应用的缓存目录，解决 Android 10+ 权限限制问题。如果普通方式失败，会自动尝试 root 方式；如果都失败，会显示提示信息。

3.  **构建系统优化**:
    *   配置了 GitHub Actions (`android.yml`)，实现云端自动编译 APK。
    *   编写了 `git_push_retry.py` 脚本，解决了国内网络环境下 GitHub 推送频繁失败的问题。

#### B. 关键问题修复
1.  **路径解析问题** (2026-01-30 修复):
    *   **问题**: 在 Xposed hook 上下文中，硬编码路径 `/sdcard/DCIM/XVirtualCamera/` 可能无法正确解析，导致无法读取配置文件。
    *   **解决方案**: 实现多路径尝试机制，依次尝试 `/sdcard/DCIM/XVirtualCamera/`、`/storage/emulated/0/DCIM/XVirtualCamera/` 以及通过反射获取的外部存储路径。

2.  **权限写入问题** (2026-01-30 修复):
    *   **问题**: Android 10+ 由于权限限制，无法直接写入其他应用的 `externalCacheDir`（`/storage/emulated/0/Android/data/[包名]/cache/`），导致配置无法保存到目标位置。
    *   **解决方案**: 实现双重保存策略：
        *   优先保存到公共目录 `/sdcard/DCIM/XVirtualCamera/[包名]/stream.txt`（始终可写）
        *   同时尝试使用 root 权限写入目标应用的缓存目录（符合原始 readme.md 设计）
        *   如果 root 权限不可用，会显示提示信息，用户可以手动创建文件

---

## 2. 编译后的 APP 使用指南

### 2.1 环境准备
*   **Android 设备**: 需要获取 Root 权限（用于写入目标应用的缓存目录，如果设备未 root 也可以使用，但需要手动创建配置文件）。
*   **框架**: 安装 LSPosed (推荐) 或 EdXposed。
*   **Root 管理器**: 如果设备已 root，建议安装 Magisk 或其他 root 管理器，并授予 XVirtualCamera 应用 root 权限。

### 2.2 安装与激活
1.  安装编译生成的 `app-release.apk`。
2.  打开 **LSPosed Manager**。
3.  在模块列表中找到 **XVirtualCamera** 并启用。
4.  **勾选目标应用**: 选择你需要替换摄像头的应用（例如：抖音、微信、快手等）。**注意：同时也需要勾选“系统框架”和“XVirtualCamera”自身（如果需要）。**
5.  重启手机或在该页面选择“重启用户空间”以生效。



**流地址示例**:
- HTTP/HLS: `http://192.168.1.14:8888/live/stream` 或 `http://192.168.1.14:8888/stream.m3u8`
- RTMP: `rtmp://192.168.1.14:1935/live/stream`

### 2.3 配置视频源
打开 **XVirtualCamera** App，你将看到两种模式：

#### 模式 A: 网络流 (推荐，用于直播)
1.  **输入目标应用包名**（例如：`com.ss.android.ugc.aweme` 表示抖音）
2.  **输入网络流地址**:
    *   在输入框中填写 RTMP 或 HTTP 地址
    *   **示例**: `http://192.168.1.14:8888/live/stream` 或 `rtmp://192.168.1.14:1935/live/stream`
    *   请将 `192.168.1.14` 替换为你电脑的局域网 IP 地址
3.  **保存配置**:
    *   点击 **保存配置** 按钮
    *   应用会自动保存到两个位置：
        *   `/sdcard/DCIM/XVirtualCamera/[包名]/stream.txt` (公共目录，始终可写)
        *   `/storage/emulated/0/Android/data/[包名]/cache/stream.txt` (目标应用缓存目录，需要 root 权限)
    *   如果设备已 root 并授予权限，会自动写入目标位置；如果未 root，会显示提示信息

#### 模式 B: 本地视频
1.  **输入目标应用包名**
2.  **选择本地视频文件**:
    *   点击 **选择视频** 按钮，从文件管理器中选择视频文件
    *   或者手动将视频文件重命名为 `virtual.mp4`，放入以下任一目录：
        *   `/sdcard/DCIM/XVirtualCamera/` (全局生效)
        *   `/sdcard/DCIM/XVirtualCamera/[目标包名]/` (仅对特定 App 生效)
        *   `/storage/emulated/0/Android/data/[包名]/cache/virtual.mp4` (目标应用缓存目录)
3.  **保存配置**: 点击 **保存配置** 按钮

#### 配置优先级说明
插件会按以下顺序查找配置文件：
1.  `/sdcard/DCIM/XVirtualCamera/[包名]/stream.txt` 或 `/storage/emulated/0/DCIM/XVirtualCamera/[包名]/stream.txt`
2.  `/sdcard/DCIM/XVirtualCamera/stream.txt` 或 `/storage/emulated/0/DCIM/XVirtualCamera/stream.txt` (全局配置)
3.  `/storage/emulated/0/Android/data/[包名]/cache/stream.txt` (原始设计路径)

如果找不到 `stream.txt`，会查找 `virtual.mp4` 文件（按相同优先级）。

### 2.4 验证
1.  **强制停止目标应用**（如抖音），确保配置生效。
2.  **重新打开目标应用**，进入拍摄或直播页面。
3.  **检查效果**:
    *   如果配置正确，摄像头画面将被替换为指定的视频或流媒体画面
    *   如果显示"未找到视频源"提示，请检查：
        *   配置文件路径是否正确
        *   网络地址是否可访问（如果是网络流）
        *   文件权限是否正确（如果使用 root 权限写入，确保文件权限为 666）
4.  **查看日志**（可选）:
    *   日志文件位置：`/sdcard/DCIM/XVirtualCamera/logs/xvirtualcamera_[日期].log`
    *   可以通过日志查看配置读取过程和错误信息

### 2.5 常见问题排查

#### 问题 1: 显示"未找到视频源"
**可能原因**:
- 配置文件路径不正确
- 文件权限不足
- 网络地址无法访问（网络流）

**解决方法**:
1.  检查配置文件是否存在：使用文件管理器查看 `/sdcard/DCIM/XVirtualCamera/[包名]/stream.txt` 或 `/storage/emulated/0/Android/data/[包名]/cache/stream.txt`
2.  如果文件不存在，重新在 App 中保存配置
3.  如果设备未 root，需要手动创建文件：`/storage/emulated/0/Android/data/[包名]/cache/stream.txt`，并设置权限为 666
4.  检查网络连接（如果是网络流）

#### 问题 2: Root 权限写入失败
**可能原因**:
- 设备未 root
- Root 权限未授予 XVirtualCamera 应用
- Root 管理器（如 Magisk）未正确配置

**解决方法**:
1.  确保设备已 root 并安装 Magisk 或其他 root 管理器
2.  在 root 管理器中授予 XVirtualCamera 应用 root 权限
3.  如果无法获取 root 权限，可以手动创建配置文件（见问题 1）

#### 问题 3: 网络流无法播放
**可能原因**:
- 网络地址错误或无法访问
- 防火墙阻止连接
- 流媒体服务器未启动

**解决方法**:
1.  在手机浏览器中测试网络地址是否可访问
2.  检查电脑防火墙设置，确保允许相应端口（如 1935、8888）的入站连接
3.  确认流媒体服务器（如 MediaMTX）已启动并正常运行

---



本系统通过 `main.py` 生成直播画面，经由 `MediaMTX` 转发，最后由手机端 `XVirtualCamera` 拉流播放。

### 3.1 启动 MediaMTX (RTMP 服务器)
MediaMTX 是一个轻量级的流媒体服务器，用于中转直播信号。


---


### 关键注意事项
1.  **防火墙**: 确保电脑防火墙允许 `mediamtx.exe` 通过，或者允许 **1935** 端口的入站连接，否则手机无法连接。
2.  **IP 地址**: 手机端配置的 RTMP 地址必须是 **电脑的局域网 IP**，不能是 `127.0.0.1`。
3.  **延迟**: 局域网 RTMP 延迟通常在 1-3 秒左右，属于正常范围。

# 麦克风录音 Hook 调试记录

## 1. 问题背景
用户反馈在 XVirtualCamera 模块中将麦克风音量设置为 0% 后，抖音直播依然能录制到外部环境声音，且模块的静音功能无效。

## 2. 调试过程与尝试方案

### 第一阶段：配置文件读取修复
- **操作**：修改 `AudioHook.kt` 增加多路径读取配置逻辑（尝试应用私有路径 + `/sdcard/DCIM` 全局路径），并在 `MainActivity` 保存配置时尝试放宽文件权限。
- **目的**：排除因 Android 存储沙箱 (Scoped Storage) 导致 Hook 模块无法读取“静音”配置文件的可能性。
- **结果**：无效，问题依旧。

### 第二阶段：日志诊断与权限验证
- **操作**：增强 `HookUtils.kt` 日志功能，尝试将日志写入 SD 卡文件；随后在 `AudioHook` 中增加详细的流程打点。
- **发现的问题**：
    1. **文件权限受限**：抖音进程因沙箱限制无法写入我们创建的日志文件（报 `EACCES`）。
    2. **关键日志缺失**：通过 LSPosed 管理器查看系统日志，发现模块已加载，但**没有**打印出任何处理音频数据的日志。

### 第三阶段：Hook 点位覆盖 (Java 层)
- **操作**：除了原有的 `AudioRecord.read`，新增了对 `AudioRecord` 构造函数、`startRecording` 方法以及 `MediaRecorder` 类的 Hook。
- **目的**：判断抖音是否使用了其他 Java 录音接口。
- **关键发现**：LSPosed 日志中**未出现**任何 `AudioRecord` 或 `MediaRecorder` 的调用记录。
- **结论**：**抖音直播使用的是 Native 层 (C++) 音频采集接口**（如 OpenSL ES 或 AAudio），直接绕过了 Java 层的 `AudioRecord`，导致所有基于 Java 的数据流修改逻辑完全失效。

### 第四阶段：系统级权限拦截 (AppOpsManager)
- **操作**：转向 Hook `AppOpsManager` 系统服务。
- **原理**：Android 系统无论通过 Java 还是 Native 录音，底层都必须经过 `AppOpsManager` 检查 `OP_RECORD_AUDIO` 权限。
- **策略**：
    - 拦截 `startOp`, `noteOp` 及其所有重载方法。
    - 当检测到音量配置为 0 时，拦截 `OP_RECORD_AUDIO` (27) 或 `android:record_audio` 请求。
    - 强制返回 `AppOpsManager.MODE_IGNORED`。
- **预期效果**：系统会告知应用“权限校验通过”，但底层实际上会提供**全静音（空白）的音频流**，从而实现对 Native 音频的完美静音。

## 3. 当前状态
我们已实施了 `AppOpsManager` 的全覆盖 Hook 方案，理论上可以拦截所有层面的录音请求并实现静音。这是解决 Native 音频采集绕过问题的终极方案。

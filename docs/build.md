# 构建与复现

## 环境

安装 Python 3.9+、JDK 17+ 和 Android SDK。需要 SDK Platform 36、Build Tools 36.0.0；真机验证另需 platform-tools 的 `adb`。本项目不自动安装系统工具或接受 SDK 许可。

macOS/Linux 示例（替换路径）：

```sh
export JAVA_HOME="/path/to/jdk"
export ANDROID_SDK_ROOT="/path/to/android-sdk"
python3 tools/bootstrap.py
python3 tools/build.py --variant all
python3 tools/test.py
```

若 SDK 为非标准目录，可分别设置 `ANDROID_JAR` 和 `ANDROID_BUILD_TOOLS`；它们优先于 `ANDROID_SDK_ROOT`。构建脚本不含开发者电脑的固定路径。

Windows PowerShell 环境变量写法：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk"
$env:ANDROID_SDK_ROOT = "C:\path\to\android-sdk"
python tools/bootstrap.py
python tools/build.py --variant all
python tools/test.py
```

Python 构建脚本考虑了 Windows 工具扩展名，但本仓库仅在 macOS 主机上实际构建过 Android APK。这里的 Windows/Linux 指**构建主机**，不意味着已经实现 Windows/Linux 语音应用。

## 依赖准备

`tools/bootstrap.py` 读取 `models/manifest.json`。所有下载都来自显式记录的官方发布地址，下载档案和解包后的文件都校验 SHA-256。仅解包清单中指定的普通文件，不按 tar 内任意路径写入。

```sh
python3 tools/bootstrap.py
python3 tools/bootstrap.py --verify-only
# 共用已下载缓存，也仍然校验哈希
python3 tools/bootstrap.py --cache /path/to/download-cache
```

压缩包可能包含多组模型或 FP32/INT8 版本，下载量大于最终打包量。`.cache/` 和 `.deps/` 可重建；`models/assets/` 为选中的模型文件。模型下载发生在开发准备阶段，示例运行时没有 INTERNET 权限。

## 构建选择

```sh
python3 tools/build.py --variant asr
python3 tools/build.py --variant pen
python3 tools/build.py --variant combined
python3 tools/build.py --variant all
```

`pen` 分支不会验证、下载或打包 ASR 依赖。输出目录保留之前构建过的其他变体，因此判断本次结果时看对应文件名。

| 产物 | 用途 |
|---|---|
| `asr-core.jar` | 纯 Java 合约、会话和初稿状态 |
| `asr-android.aar` | 录音与两遍识别，不内嵌模型或第三方运行库 |
| `asr-ui-android.aar` | 可选的原生转写面板 |
| `asr-models.aar` | 模型资产，可用手动复制 assets 替代 |
| `sherpa-onnx-1.13.8.aar` | 官方推理库，单独依赖 |
| `kotlin-stdlib-2.2.0.jar` | Kotlin 运行依赖，宿主也可通过 Maven 引入 |
| `pen-android.aar` | 笔样本适配器，无权限和 ASR 依赖 |
| `app-{asr,pen,combined}-debug.apk` | 独立安装的示例应用 |

本地生成开发签名，只用于测试。`build/debug.keystore` 被忽略，不应上传或作为正式发布签名。清理该文件后会生成新签名，无法覆盖旧签名的同包名安装；需要保留本地测试密钥，或由宿主提供自己的正式构建流程。

## 真机验证

```sh
adb devices
python3 tools/device_test.py --variant both
# 多设备时明确选择
python3 tools/device_test.py --serial DEVICE_SERIAL --variant asr
```

脚本使用非增量安装，分别启动 ASR / Pen 示例并运行合成样本 instrumentation，不录制麦克风音频。成功要求 Android instrumentation 返回 `-1` 且没有 failure 字段。报告保存在忽略的 `qa/` 目录。

三个包名分别是 `io.github.purryc.demo.asr`、`.pen`、`.combined`。它们不会覆盖原始 Lenovo Pen Lab 应用。

真实麦克风体验由用户在 ASR 面板中主动点击开始；实时文字出现、句尾定稿和断网可用均应在目标设备复核。笔的真实悬浮和倾斜必须用实体笔测试。

## CI

GitHub Actions 只跑纯 Java 状态/配置测试、模块依赖隔离和 Python 语法检查，不下载模型、不录音，也不宣称验证设备性能。发布前需要本地完整构建和真机测试。

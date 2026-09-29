# APP ASR MODULE

面向应用复用的**本地中英文两遍语音识别模块**，附带可独立使用的笔输入模块。

说话时由 **Zipformer** 持续输出临时文字；停顿后 **SenseVoice** 根据原始音频重新识别整句。两者由 **sherpa-onnx** 在设备内运行，**没有 FunASR 服务、Mac 桥接或云端接口**。FunASR 的两遍识别是方案参考，不是本仓库的运行依赖。

**已实现并在真机验证：Android arm64。** Windows、macOS、Linux、iOS 和鸿蒙的接入路线已写入文档，尚未交付这些系统的应用或二进制。

## 只取你需要的部分

| 目标 | 需要的模块 | 不需要的内容 |
|---|---|---|
| 自己做语音界面 | `asr-core` + `asr-android` + 模型与推理库 | 笔模块、示例界面 |
| 直接使用现成转写面板 | 上述依赖 + `asr-ui-android` | 笔模块 |
| 只获取笔数据 | `pen-android` | ASR、模型、麦克风权限 |
| 笔选中内容后语音输入 | 在应用层组合两组模块 | 两个模块不相互依赖 |

三个示例分别位于 [`examples/asr`](examples/asr)、[`examples/pen`](examples/pen)、[`examples/combined`](examples/combined)。组合示例仅示范同时接入两种能力；具体“选区＋语音指令”业务由宿主应用实现。

## 快速开始

需要 Python 3.9+、JDK 17+、Android SDK Platform 36 / Build Tools 36.0.0。将 `JAVA_HOME`、`ANDROID_SDK_ROOT` 指向本机安装目录。无需 Gradle，脚本直接调用标准 Android 构建工具。

```sh
# ASR 才需要准备模型和运行库：固定官方来源，逐个校验 SHA-256
python3 tools/bootstrap.py
python3 tools/test.py
python3 tools/build.py --variant asr

# 只做笔应用时可跳过 bootstrap，不下载语音依赖
python3 tools/build.py --variant pen

# 构建三个示例和全部库，再检查成品依赖隔离
python3 tools/build.py --variant all
python3 tools/test.py
```

产物在 `build/artifacts/`：可复用 JAR/AAR、模型 AAR、独立示例 APK。代码与文档在 GitHub；模型、第三方二进制、APK、设备日志和签名密钥不进入 Git。新机器通过准备脚本重建依赖，使用时无需联网。

详细环境设置和安装步骤见 [构建与复现](docs/build.md)。

## ASR 的使用体验

- 中文、英文及混说共用流式模型；SenseVoice 默认自动判断语言。
- 临时文字与可编辑定稿分开。每句有独立 ID，上一句定稿不清空下一句初稿。
- 录音、流式推理、整句复核分别在三个串行线程工作，复核时继续接收下一句。
- 正常停止会提交末句；取消、关闭面板或切后台会停止录音并丢弃未完成回调。
- 默认一次 60 秒，音频仅在内存里处理；宿主可以配置时限，最高 300 秒。当前不是无限时会议转写服务。
- 最终文字只追加，不用文本生成模型猜测或改写说话内容。

两遍识别并不保证每句话更准确。固定测试中第二遍恢复过初稿遗漏的 `Please`，也把误识别的“标签”恢复为 `button`；中文仍有“得／的”错误。真实口音、噪声和专业词表现需要测量。

## 文档入口

- [Android 接入：现成面板或自定义界面](docs/android-integration.md)
- [ASR 架构、接口、线程和生命周期](docs/asr-architecture.md)
- [Windows / macOS / Linux / iOS / 鸿蒙适配路线](docs/platform-adaptation.md)
- [独立笔模块及硬件边界](docs/pen-integration.md)
- [准确率与延迟评测方法](docs/accuracy-evaluation.md)
- [当前验证证据和未验证项](docs/verification.md)
- [模型、版本、哈希及更换方法](models/README.md)
- [第三方来源与许可边界](third-party/NOTICE.md)

## 版本与边界

首版模块化版本为 `0.1.0`，来自已在联想平板运行的 Lenovo Pen Lab v0.6。Android 示例采用原生 Java/View，最低 API 26，仅打包 arm64-v8a 推理库。当前没有 Maven 发布、Gradle 插件或其他平台的成品 SDK。Android AAR 已独立生成；第三方 Gradle 宿主的集成说明尚需在目标项目验证。

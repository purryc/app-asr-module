# Windows、macOS、Linux、iOS、鸿蒙适配路线

## 当前交付边界

| 平台 | 上游 sherpa-onnx 路线 | 本仓库状态 |
|---|---|---|
| Android arm64 | Java/Kotlin + JNI | 已实现、构建、独立示例真机验证 |
| Windows | C/C++ 或 C#，匹配 CPU 架构的 DLL | 接口与实施路线；未实现应用 |
| macOS | C/C++ 或 Swift，匹配 arm64/x86_64 的库 | 接口与实施路线；未实现应用 |
| Linux | C/C++，目标架构动态库 | 接口与实施路线；未实现应用 |
| iOS/iPadOS | Swift/C API，设备 arm64 原生库 | 接口与实施路线；未实现应用 |
| 鸿蒙原生 | ArkTS + Native API / HAR | 接口与实施路线；未实现应用 |

上游平台与语言支持见 [sherpa-onnx 官方仓库](https://github.com/k2-fsa/sherpa-onnx)。上游能运行模型，与本仓库已经做好对应系统的应用是两件需要分别验证的事。Android APK、Java AAR 不能直接安装到这些系统。

## 可共享与必须替换的内容

可以共享模型文件、tokens、模型版本/哈希、16 kHz 单声道输入约定、VAD 分段策略、两遍任务顺序、phraseId/会话失效规则、样本和准确率评测方法。

必须替换麦克风采集、音频格式转换、原生库加载、权限/音频中断、线程调度、模型路径、生命周期、界面和安装签名。Android 的 AssetManager/AudioRecord/Handler 不能直接搬到 Swift、C# 或 ArkTS。

第一步建议复用同一组 CPU 模型和算法参数，先取得结果一致性，再考虑各平台的 GPU/NPU 加速。不要同时替换模型、采样链路和硬件后端，否则难以定位准确率变化。

## 建议的跨平台内核

这是下一阶段设计，不是当前已生成的二进制。

```text
AudioSource -> PCM normalizer/resampler -> TwoPassCoordinator
                                       ├─ OnlineRecognizer: draft
                                       ├─ VAD: phrase audio + phraseId
                                       └─ OfflineRecognizer: final
Events -> platform main/UI dispatcher -> host application
```

如果确定需要多个原生平台，建议把 `TwoPassStream` 的音频处理与队列调度移植成一个小型 C++ 内核，再提供窄 C ABI。模型解码由 sherpa-onnx 完成，自有内核只处理分段、会话、排队、事件和释放。这样 Swift/C#/ArkTS 都绑定相同逻辑。

建议 C ABI 的职责是 create/configure、accept PCM、stop/flush、cancel、destroy 和事件回调。每个事件包含 session generation、phraseId、type、text；文本和 PCM 的内存所有权/有效期必须写明。不得把原生工作线程回调直接用于更新 UI。模型路径由宿主显式传入，不能依赖工作目录。

## Windows

建议在已有应用使用 C# 时通过官方 C# API 或自有窄 C ABI 集成；C++ 宿主可直接用原生 API。使用 [WASAPI](https://learn.microsoft.com/en-us/windows/win32/coreaudio/wasapi) 获取麦克风流，统一为 16 kHz 单声道。设备常见输出格式需要实测和重采样，不能把 48 kHz 数据直接标成 16 kHz。

打包匹配应用架构的 sherpa-onnx/ONNX Runtime DLL、模型和 tokens，检查依赖库搜索路径与 C/C++ 运行库。处理默认设备切换、拔插、权限禁用、独占占用和睡眠恢复。首版建议先验证 Windows x64，ARM64 另作目标，不复用 x64 二进制。

验收：离线冷启动、全新用户目录可加载、设备切换后无崩溃、UI 连续出字、末句 stop 收尾、退出后麦克风释放。

## macOS

可采用 Swift + [AVAudioEngine](https://developer.apple.com/documentation/avfaudio/avaudioengine) 采集，桥接 C/C++ 识别。为沙盒、麦克风授权及发布签名配置正确的说明与 entitlement；arm64 和 Intel x86_64 分别编译测试，或构建包含两者的库。

音频回调内只搬运数据，不能运行模型推理。模型放应用资源或用户应用数据目录，使用绝对解析路径；避免把模型路径写成开发机器地址。处理采样率/路由切换、睡眠、耳机和蓝牙输入。

验收：离线、Apple Silicon 上原生运行，若支持 Intel 则有独立结果；权限拒绝后可重试；关闭窗口按产品策略立即停止采集。

## Linux

建议 C++ 内核配合桌面现有音频层；桌面发行版可采用 [PipeWire](https://docs.pipewire.org/page_audio.html)，嵌入式可按实际系统使用 ALSA。先限定一个发行版、架构和 libc 基线，随后再扩展，不用“支持 Linux”代替明确兼容范围。

运行库和模型作为应用资源，确保应用可读，依赖库可解析。沙盒包需要明确音频访问能力。验证设备枚举、默认输入切换、采样率、权限错误和无音频服务的错误恢复。

验收：干净目标机器无需 Python/FunASR 服务即可加载模型；没有麦克风时返回可恢复错误；CPU 长时间运行的队列延迟受控。

## iOS / iPadOS

参考 [sherpa-onnx iOS 构建与示例](https://k2-fsa.github.io/sherpa/onnx/ios/index.html)，使用设备 arm64 运行库并桥接 Swift。模型放 App Bundle，或由应用显式管理首次下载后的本地文件；若沿用完全离线承诺，应随包分发或确保首次使用前已准备。

通过 AVAudioSession/AVAudioEngine 配置录音、权限说明和格式转换。处理电话/Siri/耳机等音频中断，退后台时按当前前台识别合约 cancel。模拟器能编译不代表 Apple 设备性能可接受，必须测真机内存、发热、电量与 App 生命周期。

验收：iPhone 与 iPad 分别跑相同样本；处理权限拒绝、音频中断、后台退出、模型内存释放。模型资产体积、分发和签名由 iOS 工程单独处理。

## 鸿蒙原生

参考 [sherpa-onnx 鸿蒙文档](https://k2-fsa.github.io/sherpa/onnx/harmony-os/index.html)，上游提供原生 HAR 构建及 SenseVoice/VAD 示例。基于目标 HarmonyOS SDK 的音频采集接口获得 PCM，通过 Native API 调用推理层，将回调调度回 ArkTS UI。

特别检查目标 HAR 是否已暴露在线识别、离线识别和 VAD 三种能力；不能因为有 SenseVoice 示例就假定两遍接口全部齐备。缺少的在线绑定应在 native 层补齐。模型可放 rawfile，但 native 层需要适配读取资源或复制到应用私有路径，不能假定 Android AssetManager 可复用。

生成 HAP、配置麦克风权限与签名，在真实鸿蒙设备上测试。旧版兼容安卓环境与鸿蒙原生需要区分，原生版本不能把 APK 改后缀当作 HAP。

## 每个平台的完成标准

1. 原生库和两套模型在目标设备离线加载，输出真实结果。
2. 中文、英文、混说使用同一参考集；报告初稿/最终错误率。
3. first-partial、句尾定稿延迟分别测 P50/P95，包含音频采集与队列等待。
4. stop 提交末句；cancel/background 不再送达旧会话结果；新会话不串句。
5. 三线程/队列策略或等价实现不阻塞采集；内存、队列水位、热量受控。
6. 不复制笔模块；ASR-only 应用没有笔 API 依赖。用户音频不用于调试日志或默认保存。

推荐先完成一个桌面平台的共享内核端到端验证，再扩展其余桌面平台；iOS 与鸿蒙各自处理移动端权限、包资源与生命周期。只有通过上述验收后，才把状态从“适配路线”改为“已支持”。

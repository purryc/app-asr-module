# 模型与依赖

事实来源是 [`manifest.json`](manifest.json)。版本、下载 URL、压缩包 SHA-256、选取的文件和每个输出文件 SHA-256 都被固定。脚本校验的是本项目记录的字节一致性，不等同独立验证发布者签名。

| 内容 | 版本/配置 | 用途 |
|---|---|---|
| Zipformer small bilingual | 2023-02-16，默认 chunk，INT8 encoder/joiner + FP32 decoder | 中文/英文实时初稿，约 57.4 MiB |
| SenseVoiceSmall | INT8 2024-07-17，auto language + ITN | 原句重识别，主模型约 228 MiB |
| Silero VAD | 固定下载字节与哈希 | 0.65 秒停顿分段，目标最长 12 秒 |
| sherpa-onnx | Android 1.13.8 | 原生运行库 |
| Kotlin stdlib | 2.2.0 | 官方 Android 封装的依赖 |

仓库不包含上述模型/库二进制。运行 `python3 tools/bootstrap.py` 后：

```text
models/assets/
  sensevoice/model.int8.onnx
  sensevoice/tokens.txt
  sensevoice/silero_vad.onnx
  zipformer/encoder.int8.onnx
  zipformer/decoder.onnx
  zipformer/joiner.int8.onnx
  zipformer/tokens.txt
.deps/
  sherpa-onnx-1.13.8.aar
  kotlin-stdlib-2.2.0.jar
```

模型路径是当前 Android adapter 的明确约定。要替换模型，先记录来源和许可，更新 manifest 的 URL、输入输出映射和哈希；再修改模型配置（可能涉及 modelType、token 规则、采样率、chunk/cache）；最后重新跑相同参考集、stop/分段与生命周期测试。不能仅换文件名就假设其他模型可兼容。

保留模型版本与代码版本的组合记录。模型文件大小、APK 压缩大小、安装占用、运行内存分别测量，不混用数字。

官方资料：[Zipformer](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html)、[SenseVoice](https://k2-fsa.github.io/sherpa/onnx/sense-voice/index.html)。

# 第三方来源和许可边界

本公开仓库主要发布自有模块源码、文档和固定依赖的准备脚本，第三方模型、AAR/JAR 和 APK 不上传到 Git。上游下载来源和精确哈希见 `models/manifest.json`。

- sherpa-onnx 1.13.8：官方 GitHub 发布。随本仓库保留上游 Apache-2.0 许可文本 `sherpa-onnx-LICENSE`。
- Kotlin stdlib 2.2.0：Maven Central 官方坐标 `org.jetbrains.kotlin:kotlin-stdlib:2.2.0`，许可文本见 `kotlin-LICENSE`。
- SenseVoice INT8：k2-fsa 转换版本，原模型来自 SenseVoice。随模型提供的 `SenseVoice-LICENSE` 指向 FunASR 模型许可页面，不能用 sherpa-onnx 的 Apache 许可替代它。
- Zipformer small bilingual 2023-02-16：来自 k2-fsa 的预训练模型发布。下载档案没有单独 LICENSE 文件；模型卡/训练数据许可不能从运行库许可推断。商业分发前由分发方核查适用条款。
- Silero VAD：来自固定的 sherpa-onnx 预训练模型下载地址；运行库打包与模型本身许可需分别核查。
- 测试音频：由 macOS Tingting/Samantha 生成的无个人信息固定句，仅用于回归测试，不包含用户录音。来源和参考句在 `tests/fixtures/manifest.json`。

本仓库自有源码尚未选择开源授权协议。公开可查看与授权任意再分发并非同一含义；如需向第三方授予明确使用许可，由仓库所有者另行确定。这里不改变任何第三方许可。

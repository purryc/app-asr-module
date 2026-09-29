# Android 接入

## 方式一：现成面板

先按 [构建说明](build.md) 生成 AAR/JAR。复制到宿主的 `app/libs/`：

- `asr-core.jar`
- `asr-android.aar`
- `asr-ui-android.aar`
- `asr-models.aar`
- `sherpa-onnx-1.13.8.aar`

Gradle Kotlin DSL 示例（用于已有 Android 项目，本仓库构建本身不依赖 Gradle）：

```kotlin
dependencies {
    implementation(files("libs/asr-core.jar"))
    implementation(files("libs/asr-android.aar"))
    implementation(files("libs/asr-ui-android.aar"))
    implementation(files("libs/asr-models.aar"))
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
}
android {
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
    }
}
```

已有 Kotlin 项目应统一兼容的 stdlib 版本，不要同时引入 Maven stdlib 和复制出的 stdlib JAR。AAR 没有发布到 Maven，也没有 POM 自动传递依赖，需要显式列出这些依赖。上述 Gradle 消费方式尚未在外部宿主项目实测。

Activity 中接入：

```java
private SpeechPanel speech;

@Override public void onCreate(Bundle state) {
    super.onCreate(state);
    speech = new SpeechPanel(this);
    // 在用户点击你的语音按钮时调用 speech.show()
}
@Override protected void onResume() {
    super.onResume();
    speech.onResume();
}
@Override protected void onPause() {
    speech.onPause();
    super.onPause();
}
@Override protected void onDestroy() {
    speech.destroy();
    super.onDestroy();
}
@Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == SpeechPanel.MIC_REQUEST) speech.permissionResult(results);
}
```

导入 `io.github.purryc.asr.ui.SpeechPanel`。面板负责申请麦克风权限。请求码为 410，宿主已有同码权限流程时需调整面板集成，不能把其他权限结果误转发。模块本身不设置窗口样式、保活或后台服务。

## 方式二：自己的界面

省去 `asr-ui-android.aar`，直接使用 `AndroidAsrEngine`。宿主先取得 RECORD_AUDIO 运行时权限，再由明确的用户操作 start。

```java
AsrConfig config = new AsrConfig(60, 2, 4, 0.65f, "auto");
AsrEngine asr = new AndroidAsrEngine(activity, config);
SpeechDrafts pending = new SpeechDrafts();

asr.start(new AsrListener() {
    public void onReady() { /* 显示正在聆听 */ }
    public void onPartial(int id, String text) {
        pending.update(id, text);
        // 展示 pending.text()
    }
    public void onFinal(int id, String text) {
        pending.finish(id);
        // 展示剩余 pending.text()；非空定稿追加到你的文本模型
    }
    public void onLevel(float rms) { /* 可选音量反馈 */ }
    public void onDone() { pending.clear(); /* 复核完成 */ }
    public void onError(String message) { pending.clear(); /* 保留定稿并允许重试 */ }
});
```

相关导入：`io.github.purryc.asr.*`、`io.github.purryc.asr.android.*`。每次 start 之前清空 pending，并在需要时用 `SpeechSession` 过滤自己的异步 UI 操作。所有生命周期方法从主线程调用；正常结束用 stop，切后台用 cancel，销毁用 close。

`AsrConfig` 的参数依次为最大会话秒数、流式线程数、复核线程数、停顿阈值、SenseVoice 语言。`auto`、`zh`、`en` 可选；流式模型始终支持中英双语。固定为 `zh`/`en` 会影响第二遍语言判断，不等于关闭另一种语言的流式输出。

当前公开引擎封装麦克风输入；直接传入文件或 PCM 的生产 API 尚未公开。测试使用包内 `TwoPassStream`，未来文件转写应增加明确的独立 API，而不要伪造麦克风数据。

## 资产和权限

可以不用 `asr-models.aar`，改为把 `models/assets/` 的内容复制到宿主 `src/main/assets/`，保持 `sensevoice/`、`zipformer/` 路径不变。两种方式选一种，避免重复资产。

`asr-android.aar` 声明 RECORD_AUDIO；仍然需要运行时授权。无需 INTERNET、存储权限或 API Key。若宿主有联网权限，那是宿主其他功能，与本模块没有关系。

模型和运行库体积较大。ABI 筛选可避免把不需要的 native 库带入应用。量化模型减少存储，并不等同运行时内存大小；其他机型的性能和平台分发要求需要实测。正式发布请使用宿主的签名和构建流程，评估模型许可、目标商店要求及 ABI/页大小兼容性。

## 最小可运行代码

[`examples/asr/src/MainActivity.java`](../examples/asr/src/MainActivity.java) 是实际参与构建和真机测试的宿主。它只依赖语音模块，不导入笔模块。

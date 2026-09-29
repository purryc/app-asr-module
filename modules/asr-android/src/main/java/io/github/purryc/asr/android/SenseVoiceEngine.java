package io.github.purryc.asr.android;

import android.content.Context;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;

/** Owned exclusively by the inference thread. */
final class SenseVoiceEngine {
  final Context context;
  final AsrConfig options;
  OfflineRecognizer recognizer;

  SenseVoiceEngine(Context context, AsrConfig options) {
    this.context = context.getApplicationContext();
    this.options = options;
  }

  void prepare() throws IOException {
    if (recognizer != null) return;
    OfflineSenseVoiceModelConfig sense = new OfflineSenseVoiceModelConfig();
    sense.setModel("sensevoice/model.int8.onnx");
    sense.setLanguage(options.language);
    sense.setUseInverseTextNormalization(true);
    OfflineModelConfig model = new OfflineModelConfig();
    model.setSenseVoice(sense);
    model.setTokens("sensevoice/tokens.txt");
    model.setNumThreads(options.finalThreads);
    model.setProvider("cpu");
    OfflineRecognizerConfig config = new OfflineRecognizerConfig();
    config.setModelConfig(model);
    recognizer = new OfflineRecognizer(context.getAssets(), config);
  }

  String decode(float[] samples) {
    OfflineStream stream = recognizer.createStream();
    try {
      stream.acceptWaveform(samples, 16000);
      recognizer.decode(stream);
      return recognizer.getResult(stream).getText().trim();
    } finally {
      stream.release();
    }
  }

  Vad createVad() {
    SileroVadModelConfig silero = new SileroVadModelConfig();
    silero.setModel("sensevoice/silero_vad.onnx");
    silero.setMinSilenceDuration(options.pauseSeconds);
    silero.setMinSpeechDuration(0.25f);
    silero.setMaxSpeechDuration(12f);
    VadModelConfig config = new VadModelConfig();
    config.setSileroVadModelConfig(silero);
    config.setNumThreads(1);
    return new Vad(context.getAssets(), config);
  }

  void close() {
    if (recognizer != null) {
      recognizer.release();
      recognizer = null;
    }
  }
}

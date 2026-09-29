package io.github.purryc.asr.android;

import android.content.Context;
import com.k2fsa.sherpa.onnx.*;

/** All access belongs to the streaming executor. */
final class StreamingEngine {
  final Context context;
  final AsrConfig options;
  OnlineRecognizer recognizer;

  StreamingEngine(Context context, AsrConfig options) {
    this.context = context.getApplicationContext();
    this.options = options;
  }

  void prepare() {
    if (recognizer != null) return;
    OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig();
    transducer.setEncoder("zipformer/encoder.int8.onnx");
    transducer.setDecoder("zipformer/decoder.onnx");
    transducer.setJoiner("zipformer/joiner.int8.onnx");
    OnlineModelConfig model = new OnlineModelConfig();
    model.setTransducer(transducer);
    model.setTokens("zipformer/tokens.txt");
    model.setModelType("zipformer");
    model.setNumThreads(options.streamingThreads);
    OnlineRecognizerConfig config = new OnlineRecognizerConfig();
    config.setModelConfig(model);
    config.setEnableEndpoint(false);
    recognizer = new OnlineRecognizer(context.getAssets(), config);
  }

  OnlineStream createStream() {
    return recognizer.createStream("");
  }

  String accept(OnlineStream stream, float[] samples) {
    stream.acceptWaveform(samples, 16000);
    while (recognizer.isReady(stream)) recognizer.decode(stream);
    return recognizer.getResult(stream).getText().trim();
  }

  void close() {
    if (recognizer != null) {
      recognizer.release();
      recognizer = null;
    }
  }
}

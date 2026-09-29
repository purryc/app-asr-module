package io.github.purryc.asr.android;

import com.k2fsa.sherpa.onnx.*;

/** Streaming and VAD share phrase boundaries; final audio goes to another executor. */
final class TwoPassStream {
  interface Listener {
    void draft(int id, String text);

    void phrase(int id, float[] samples, String draft);
  }

  final StreamingEngine engine;
  final Vad vad;
  final Listener listener;
  OnlineStream stream;
  int id;
  String draft = "";

  TwoPassStream(StreamingEngine engine, Vad vad, Listener listener) {
    this.engine = engine;
    this.vad = vad;
    this.listener = listener;
    stream = engine.createStream();
  }

  void accept(float[] samples) {
    String text = engine.accept(stream, samples);
    if (!text.equals(draft)) {
      draft = text;
      listener.draft(id, text);
    }
    vad.acceptWaveform(samples);
    drain();
  }

  void drain() {
    while (!vad.empty()) {
      float[] audio = vad.front().getSamples();
      vad.pop();
      listener.phrase(id, audio, draft);
      stream.release();
      stream = engine.createStream();
      id++;
      draft = "";
    }
  }

  void finish() {
    // Supply context for the streaming tail, but do not add artificial audio to final ASR.
    String text = engine.accept(stream, new float[8000]);
    if (!text.equals(draft)) {
      draft = text;
      listener.draft(id, text);
    }
    vad.flush();
    drain();
  }

  void close() {
    stream.release();
    vad.release();
  }
}

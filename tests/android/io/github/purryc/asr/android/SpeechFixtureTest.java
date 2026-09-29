package io.github.purryc.asr.android;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.*;
import java.util.Arrays;
import org.json.*;

/** Synthetic assets only; exercises the same streaming/VAD boundary code as the microphone. */
public class SpeechFixtureTest extends Instrumentation {
  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  float[] read(String name) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = getTargetContext().getAssets().open("qa/" + name + ".pcm")) {
      byte[] block = new byte[8192];
      int n;
      while ((n = in.read(block)) != -1) bytes.write(block, 0, n);
    }
    byte[] pcm = bytes.toByteArray();
    float[] samples = new float[pcm.length / 2];
    for (int j = 0; j < samples.length; j++)
      samples[j] = (short) ((pcm[2 * j] & 255) | (pcm[2 * j + 1] << 8)) / 32768f;
    return samples;
  }

  JSONObject check(
      String name,
      float[] samples,
      boolean addSilence,
      int minimumPhrases,
      SenseVoiceEngine finalEngine,
      StreamingEngine live)
      throws Exception {
    JSONArray phrases = new JSONArray();
    int[] changes = {0}, fed = {0}, firstPartial = {-1}, firstFinal = {-1};
    TwoPassStream pipeline =
        new TwoPassStream(
            live,
            finalEngine.createVad(),
            new TwoPassStream.Listener() {
              public void draft(int id, String text) {
                if (!text.isEmpty()) {
                  changes[0]++;
                  if (firstPartial[0] < 0) firstPartial[0] = fed[0];
                }
              }

              public void phrase(int id, float[] audio, String draft) {
                if (firstFinal[0] < 0) firstFinal[0] = fed[0];
                long begin = SystemClock.elapsedRealtime();
                String text = finalEngine.decode(audio);
                try {
                  phrases.put(
                      new JSONObject()
                          .put("id", id)
                          .put("draft", draft)
                          .put("final", text)
                          .put("final_ms", SystemClock.elapsedRealtime() - begin));
                } catch (JSONException e) {
                  throw new RuntimeException(e);
                }
              }
            });
    long start = SystemClock.elapsedRealtime();
    try {
      float[] input = Arrays.copyOf(samples, samples.length + (addSilence ? 16000 : 0));
      for (int offset = 0; offset < input.length; offset += 512) {
        fed[0] = Math.min(offset + 512, input.length);
        pipeline.accept(Arrays.copyOfRange(input, offset, fed[0]));
      }
      pipeline.finish();
    } finally {
      pipeline.close();
    }
    if (phrases.length() < minimumPhrases)
      throw new AssertionError(name + ": missing final phrase");
    if (minimumPhrases > 0 && (firstPartial[0] < 0 || firstPartial[0] >= firstFinal[0]))
      throw new AssertionError(name + ": no live draft before final");
    if (minimumPhrases == 0 && phrases.length() != 0)
      throw new AssertionError("Silence produced a final phrase");
    return new JSONObject()
        .put("fixture", name)
        .put("audio_seconds", samples.length / 16000f)
        .put("first_partial_audio_ms", firstPartial[0] / 16)
        .put("partial_changes", changes[0])
        .put("total_compute_ms", SystemClock.elapsedRealtime() - start)
        .put("phrases", phrases);
  }

  void checkTerminalError() throws Exception {
    final AndroidAsrEngine[] engine = new AndroidAsrEngine[1];
    final int[] success = {0}, errors = {0};
    runOnMainSync(
        () -> {
          AndroidAsrEngine asr = new AndroidAsrEngine(getTargetContext());
          engine[0] = asr;
          io.github.purryc.asr.AsrListener listener =
              new io.github.purryc.asr.AsrListener() {
                public void onReady() {
                  success[0]++;
                }

                public void onPartial(int id, String text) {
                  success[0]++;
                }

                public void onFinal(int id, String text) {
                  success[0]++;
                }

                public void onDone() {
                  success[0]++;
                }

                public void onLevel(float rms) {
                  success[0]++;
                }

                public void onError(String message) {
                  errors[0]++;
                }
              };
          AndroidAsrEngine.Job job = asr.new Job(listener);
          job.post(() -> listener.onReady());
          job.fail(new java.io.IOException("synthetic error-path fixture"));
          job.post(() -> listener.onPartial(0, "synthetic stale draft"));
          job.fail(new java.io.IOException("duplicate synthetic error"));
        });
    runOnMainSync(() -> {});
    if (success[0] != 0 || errors[0] != 1) throw new AssertionError("Error was not terminal");
    runOnMainSync(() -> engine[0].close());
    engine[0].capture.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
    engine[0].streaming.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
    engine[0].inference.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
  }

  void checkCancelledLifecycle() throws Exception {
    final AndroidAsrEngine[] instance = new AndroidAsrEngine[1];
    final java.util.concurrent.atomic.AtomicInteger callbacks =
        new java.util.concurrent.atomic.AtomicInteger();
    runOnMainSync(
        () -> {
          AndroidAsrEngine asr = new AndroidAsrEngine(getTargetContext());
          instance[0] = asr;
          io.github.purryc.asr.AsrListener listener =
              new io.github.purryc.asr.AsrListener() {
                public void onReady() {
                  callbacks.incrementAndGet();
                }

                public void onPartial(int id, String text) {
                  callbacks.incrementAndGet();
                }

                public void onFinal(int id, String text) {
                  callbacks.incrementAndGet();
                }

                public void onDone() {
                  callbacks.incrementAndGet();
                }

                public void onError(String message) {
                  callbacks.incrementAndGet();
                }

                public void onLevel(float rms) {
                  callbacks.incrementAndGet();
                }
              };
          asr.start(listener);
          asr.cancel();
          asr.close();
          asr.close();
          try {
            asr.start(listener);
            throw new AssertionError("Closed engine accepted start");
          } catch (IllegalStateException expected) {
          }
        });
    AndroidAsrEngine asr = instance[0];
    if (!asr.capture.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)
        || !asr.streaming.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)
        || !asr.inference.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)) {
      throw new AssertionError("Cancelled engine did not release executors");
    }
    runOnMainSync(() -> {});
    if (callbacks.get() != 0) throw new AssertionError("Cancelled session emitted callback");
    if (asr.engine.recognizer != null || asr.liveEngine.recognizer != null)
      throw new AssertionError("Native model retained after close");
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    SenseVoiceEngine engine = new SenseVoiceEngine(getTargetContext(), new AsrConfig());
    StreamingEngine live = new StreamingEngine(getTargetContext(), new AsrConfig());
    try {
      long init = SystemClock.elapsedRealtime();
      engine.prepare();
      live.prepare();
      result.putLong("init_ms", SystemClock.elapsedRealtime() - init);
      JSONArray tests = new JSONArray();
      for (String name : new String[] {"zh-0", "zh-1", "zh-2", "en-0", "mixed-0"})
        tests.put(check(name, read(name), true, 1, engine, live));
      tests.put(check("stop_flush", read("en-0"), false, 1, engine, live));
      float[] a = read("zh-0"), b = read("en-0"), combined = new float[a.length + 19200 + b.length];
      System.arraycopy(a, 0, combined, 0, a.length);
      System.arraycopy(b, 0, combined, a.length + 19200, b.length);
      tests.put(check("two_phrases", combined, true, 2, engine, live));
      tests.put(check("silence", new float[48000], false, 0, engine, live));
      checkCancelledLifecycle();
      checkTerminalError();
      result.putString(
          "lifecycle",
          "cancel-during-prepare, stale callback suppression, idempotent close, native/thread"
              + " release passed");
      result.putString("results", tests.toString());
      finish(-1, result);
    } catch (Throwable e) {
      result.putString("failure", e.toString());
      finish(0, result);
    } finally {
      live.close();
      engine.close();
    }
  }
}

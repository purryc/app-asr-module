package io.github.purryc.asr.android;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.util.Log;
import io.github.purryc.asr.*;
import java.io.*;
import java.util.concurrent.*;

/**
 * Independent capture, streaming, and final inference threads. Audio stays in bounded session
 * memory.
 */
public final class AndroidAsrEngine implements AsrEngine {
  final Context context;
  final ExecutorService capture = Executors.newSingleThreadExecutor();
  final ExecutorService streaming = Executors.newSingleThreadExecutor();
  final ExecutorService inference = Executors.newSingleThreadExecutor();
  final Handler main = new Handler(Looper.getMainLooper());
  final Object audioLock = new Object();
  final SenseVoiceEngine engine;
  final StreamingEngine liveEngine;
  Job active;
  private final AsrConfig options;
  private boolean closed;

  public AndroidAsrEngine(Context context) {
    this(context, new AsrConfig());
  }

  public AndroidAsrEngine(Context context, AsrConfig options) {
    this.context = context.getApplicationContext();
    this.options = java.util.Objects.requireNonNull(options);
    engine = new SenseVoiceEngine(context, options);
    liveEngine = new StreamingEngine(context, options);
  }

  private void checkThread() {
    if (Looper.myLooper() != Looper.getMainLooper())
      throw new IllegalStateException("Call ASR lifecycle methods on the main thread");
  }

  public void start(AsrListener listener) {
    checkThread();
    if (closed) throw new IllegalStateException("ASR engine is closed");
    java.util.Objects.requireNonNull(listener);
    cancel();
    Job job = new Job(listener);
    active = job;
    capture.execute(job);
  }

  public void stop() {
    checkThread();
    if (active != null) active.stop(false);
  }

  public void cancel() {
    checkThread();
    if (active != null) {
      active.stop(true);
      active = null;
    }
  }

  public void close() {
    checkThread();
    if (closed) return;
    closed = true;
    cancel();
    capture.execute(
        () -> {
          streaming.execute(
              () -> {
                liveEngine.close();
                inference.execute(() -> engine.close());
                inference.shutdown();
              });
          streaming.shutdown();
        });
    capture.shutdown();
  }

  final class Job implements Runnable {
    final AsrListener listener;
    volatile boolean stop, cancelled, failed;
    AudioRecord audio;
    long recordingStarted;
    boolean firstDraft;
    double sumSquares;
    long sampleCount, clipped;
    float peak;

    Job(AsrListener listener) {
      this.listener = listener;
    }

    void post(Runnable task) {
      main.post(
          () -> {
            if (!cancelled && !failed) task.run();
          });
    }

    void stop(boolean discard) {
      if (discard) cancelled = true;
      stop = true;
      synchronized (audioLock) {
        if (audio != null)
          try {
            audio.stop();
          } catch (IllegalStateException ignored) {
          }
      }
    }

    TwoPassStream pipeline;

    void finalizePhrase(int id, float[] samples, String draft) {
      inference.execute(
          () -> {
            if (cancelled || failed) return;
            try {
              long start = SystemClock.elapsedRealtime();
              String text = engine.decode(samples);
              Log.i(
                  "AppAsr",
                  "two-pass phrase="
                      + id
                      + " seconds="
                      + (samples.length / 16000f)
                      + " final_ms="
                      + (SystemClock.elapsedRealtime() - start)
                      + " draft_chars="
                      + draft.length()
                      + " final_chars="
                      + text.length());
              post(() -> listener.onFinal(id, text));
            } catch (Exception | LinkageError e) {
              fail(e);
            }
          });
    }

    synchronized void fail(Throwable e) {
      if (failed) return;
      failed = true;
      Log.e(
          "AppAsr", "sensevoice failure: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      main.post(
          () -> {
            if (!cancelled) listener.onError("离线识别遇到问题，请停止后重试");
          });
      stop(false);
    }

    @Override
    public void run() {
      try {
        inference
            .submit(
                () -> {
                  engine.prepare();
                  return null;
                })
            .get();
        if (stop) return;
        streaming
            .submit(
                () -> {
                  liveEngine.prepare();
                  pipeline =
                      new TwoPassStream(
                          liveEngine,
                          engine.createVad(),
                          new TwoPassStream.Listener() {
                            public void draft(int id, String text) {
                              if (!firstDraft && !text.isEmpty()) {
                                firstDraft = true;
                                Log.i(
                                    "AppAsr",
                                    "first draft after_ms="
                                        + (SystemClock.elapsedRealtime() - recordingStarted));
                              }
                              post(() -> listener.onPartial(id, text));
                            }

                            public void phrase(int id, float[] samples, String draft) {
                              finalizePhrase(id, samples, draft);
                            }
                          });
                  return null;
                })
            .get();
        int minimum =
            AudioRecord.getMinBufferSize(
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IOException("Unsupported microphone format");
        synchronized (audioLock) {
          if (stop) return;
          audio =
              new AudioRecord(
                  MediaRecorder.AudioSource.VOICE_RECOGNITION,
                  16000,
                  AudioFormat.CHANNEL_IN_MONO,
                  AudioFormat.ENCODING_PCM_16BIT,
                  Math.max(minimum, 32768));
          if (audio.getState() != AudioRecord.STATE_INITIALIZED)
            throw new IOException("Microphone unavailable");
          AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
          for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_INPUTS))
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
              audio.setPreferredDevice(device);
              break;
            }
          recordingStarted = SystemClock.elapsedRealtime();
          audio.startRecording();
          if (audio.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
            throw new IOException("Microphone did not start");
        }
        post(() -> listener.onReady());
        short[] block = new short[512];
        long deadline = SystemClock.elapsedRealtime() + options.maxSessionSeconds * 1000L,
            lastMeter = 0;
        while (!stop && SystemClock.elapsedRealtime() < deadline) {
          int n = audio.read(block, 0, block.length, AudioRecord.READ_BLOCKING);
          if (n < 0) {
            if (stop) break;
            throw new IOException("Microphone read failed: " + n);
          }
          if (n == 0) continue;
          float[] samples = new float[n];
          double energy = 0;
          for (int i = 0; i < n; i++) {
            float v = block[i] / 32768f;
            samples[i] = v;
            energy += v * v;
            peak = Math.max(peak, Math.abs(v));
            if (Math.abs(v) >= 0.99f) clipped++;
          }
          sumSquares += energy;
          sampleCount += n;
          if (SystemClock.elapsedRealtime() - lastMeter > 120) {
            lastMeter = SystemClock.elapsedRealtime();
            float rms = (float) Math.sqrt(energy / n);
            post(() -> listener.onLevel(rms));
          }
          streaming.execute(
              () -> {
                if (!cancelled && !failed)
                  try {
                    pipeline.accept(samples);
                  } catch (Exception | LinkageError e) {
                    fail(e);
                  }
              });
        }
      } catch (Exception | LinkageError e) {
        fail(e);
      } finally {
        synchronized (audioLock) {
          if (audio != null) {
            try {
              audio.stop();
            } catch (IllegalStateException ignored) {
            }
            audio.release();
            audio = null;
          }
        }
        Log.i(
            "AppAsr",
            "mic samples="
                + sampleCount
                + " rms="
                + (sampleCount > 0 ? Math.sqrt(sumSquares / sampleCount) : 0)
                + " peak="
                + peak
                + " clipped="
                + clipped);
        streaming.execute(
            () -> {
              try {
                if (pipeline != null && !cancelled && !failed) pipeline.finish();
              } catch (Exception | LinkageError e) {
                fail(e);
              } finally {
                if (pipeline != null) pipeline.close();
              }
              inference.execute(
                  () -> {
                    if (!failed) post(() -> listener.onDone());
                  });
            });
      }
    }
  }
}

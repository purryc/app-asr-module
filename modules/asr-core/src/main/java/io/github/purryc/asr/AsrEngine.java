package io.github.purryc.asr;

/** Host must grant microphone permission and cancel when backgrounded. */
public interface AsrEngine extends AutoCloseable {
  void start(AsrListener listener);

  void stop();

  void cancel();

  @Override
  void close();
}

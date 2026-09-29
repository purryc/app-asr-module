package io.github.purryc.asr.android;

/** Immutable session policy. Asset layout and 16 kHz mono model contract are pinned. */
public final class AsrConfig {
  public final int maxSessionSeconds;
  public final int streamingThreads;
  public final int finalThreads;
  public final float pauseSeconds;
  public final String language;

  public AsrConfig() {
    this(60, 2, 4, 0.65f, "auto");
  }

  public AsrConfig(
      int maxSessionSeconds,
      int streamingThreads,
      int finalThreads,
      float pauseSeconds,
      String language) {
    if (maxSessionSeconds < 1 || maxSessionSeconds > 300)
      throw new IllegalArgumentException("Session must be 1..300 seconds");
    if (streamingThreads < 1 || streamingThreads > 8 || finalThreads < 1 || finalThreads > 8)
      throw new IllegalArgumentException("Threads must be 1..8");
    if (!Float.isFinite(pauseSeconds) || pauseSeconds < 0.2f || pauseSeconds > 3f)
      throw new IllegalArgumentException("Pause must be 0.2..3 seconds");
    if (!("auto".equals(language) || "zh".equals(language) || "en".equals(language)))
      throw new IllegalArgumentException("Language must be auto, zh or en");
    this.maxSessionSeconds = maxSessionSeconds;
    this.streamingThreads = streamingThreads;
    this.finalThreads = finalThreads;
    this.pauseSeconds = pauseSeconds;
    this.language = language;
  }
}

package io.github.purryc.asr;

import io.github.purryc.asr.android.AsrConfig;

public final class AsrConfigTest {
  public static void main(String[] args) {
    AsrConfig c = new AsrConfig();
    if (!c.language.equals("auto") || c.maxSessionSeconds != 60) throw new AssertionError();
    int failures = 0;
    try {
      new AsrConfig(0, 2, 4, .65f, "auto");
    } catch (IllegalArgumentException e) {
      failures++;
    }
    try {
      new AsrConfig(60, 0, 4, .65f, "auto");
    } catch (IllegalArgumentException e) {
      failures++;
    }
    try {
      new AsrConfig(60, 2, 4, Float.NaN, "auto");
    } catch (IllegalArgumentException e) {
      failures++;
    }
    try {
      new AsrConfig(60, 2, 4, .65f, "unknown");
    } catch (IllegalArgumentException e) {
      failures++;
    }
    if (failures != 4) throw new AssertionError();
    System.out.println("AsrConfig: defaults and 4 invalid configurations passed");
  }
}

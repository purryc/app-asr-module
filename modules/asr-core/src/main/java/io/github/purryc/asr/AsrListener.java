package io.github.purryc.asr;

/** Android callbacks run on the main thread. Phrase IDs restart from zero each session. */
public interface AsrListener {
  void onReady();

  void onPartial(int phraseId, String text);

  void onFinal(int phraseId, String text);

  void onDone();

  void onError(String message);

  void onLevel(float rms);
}

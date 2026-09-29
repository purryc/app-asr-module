package io.github.purryc.asr;

public class SpeechSessionTest {
  static void check(boolean result, String message) {
    if (!result) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    SpeechSession s = new SpeechSession();
    check(s.state() == SpeechSession.State.IDLE, "Initially idle");
    int first = s.begin();
    check(s.accepts(first), "Listening accepts current callback");
    check(
        s.processing(first) && s.state() == SpeechSession.State.PROCESSING,
        "Stop waits for final result");
    check(s.finish(first), "Final result accepted while processing");
    check(!s.finish(first), "Duplicate final result rejected");
    int second = s.begin();
    check(!s.accepts(first), "Previous session cannot overwrite new text");
    s.cancel();
    check(
        !s.accepts(second) && s.state() == SpeechSession.State.IDLE,
        "Background/dismiss rejects late results");
    int third = s.begin();
    check(!s.processing(second), "Late end-of-speech cannot change current session");
    check(
        s.accepts(third) && s.state() == SpeechSession.State.LISTENING,
        "Current recording remains live");
    check(s.finish(third), "Error/timeout can close listening session");
    check(!s.accepts(third), "Results after error/timeout rejected");
    System.out.println("Speech session lifecycle: 11 checks passed");
  }
}

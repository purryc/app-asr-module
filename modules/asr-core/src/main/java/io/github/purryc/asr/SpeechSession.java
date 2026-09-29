package io.github.purryc.asr;

/** Main-thread session gate: late callbacks cannot update a cancelled/new session. */
public final class SpeechSession {
  public enum State {
    IDLE,
    LISTENING,
    PROCESSING
  }

  private int generation;
  private State state = State.IDLE;

  public int begin() {
    state = State.LISTENING;
    return ++generation;
  }

  public boolean accepts(int token) {
    return token == generation && state != State.IDLE;
  }

  public boolean processing(int token) {
    if (!accepts(token)) return false;
    state = State.PROCESSING;
    return true;
  }

  public boolean finish(int token) {
    if (!accepts(token)) return false;
    cancel();
    return true;
  }

  public void cancel() {
    generation++;
    state = State.IDLE;
  }

  public State state() {
    return state;
  }
}

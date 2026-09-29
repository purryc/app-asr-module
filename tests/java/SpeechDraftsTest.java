package io.github.purryc.asr;

public final class SpeechDraftsTest {
  static void check(boolean yes) {
    if (!yes) throw new AssertionError();
  }

  public static void main(String[] args) {
    SpeechDrafts d = new SpeechDrafts();
    d.update(0, "hello");
    d.update(1, "下一句");
    d.finish(0);
    check(d.text().equals("下一句"));
    d.update(0, "late callback");
    check(d.text().equals("下一句"));
    d.update(1, "下一句话");
    check(d.text().equals("下一句话"));
    d.finish(1);
    check(d.text().isEmpty());
    d.clear();
    d.update(0, "new session");
    check(d.text().equals("new session"));
    System.out.println("SpeechDraftsTest: 5 assertions passed");
  }
}

package io.github.purryc.asr;

import java.util.TreeMap;

/** UI-thread only. A late final must never erase a newer live phrase. */
public final class SpeechDrafts {
  final TreeMap<Integer, String> phrases = new TreeMap<>();
  int finalized = -1;

  public void update(int id, String text) {
    if (id > finalized) phrases.put(id, text);
  }

  public void finish(int id) {
    finalized = Math.max(finalized, id);
    phrases.headMap(finalized + 1).clear();
  }

  public void clear() {
    phrases.clear();
    finalized = -1;
  }

  public String text() {
    StringBuilder out = new StringBuilder();
    for (String text : phrases.values())
      if (!text.isEmpty()) {
        if (out.length() > 0) out.append('\n');
        out.append(text);
      }
    return out.toString();
  }
}

package com.wokandroll.back.assistant;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Takes the dish markers out of the streamed answer. The model writes {@code [[dish:26]]} after a
 * dish it recommends; the guest sees the text without the marker and a card of dish 26 under it. A
 * marker can arrive split over several pieces, so text that may still become a marker is held back
 * until it is clear; anything that is not a marker is passed on unchanged. The spaces just before a
 * marker go with it, so "Dragon roll [[dish:3]], a classic" reads "Dragon roll, a classic".
 */
final class MarkerFilter {

  private static final String OPEN = "[[dish:";
  private static final int MAX_DIGITS = 6;

  /** Spaces that are shown only if no marker follows them. */
  private final StringBuilder spaces = new StringBuilder();

  /** The start of what may be a marker. */
  private final StringBuilder pending = new StringBuilder();

  private final Set<Long> ids = new LinkedHashSet<>();

  /** The part of {@code chunk} (with what was held back) that can be shown now. */
  String feed(String chunk) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < chunk.length(); i++) {
      take(chunk.charAt(i), out);
    }
    return out.toString();
  }

  /** At the end of the answer: whatever is still held back is plain text. */
  String flush() {
    String rest = spaces.toString() + pending;
    spaces.setLength(0);
    pending.setLength(0);
    return rest;
  }

  /** The dish ids of the markers, in the order they came, without repeats. */
  Set<Long> ids() {
    return ids;
  }

  private void take(char c, StringBuilder out) {
    if (pending.isEmpty()) {
      if (c == ' ') {
        spaces.append(c);
      } else if (c == '[') {
        pending.append(c);
      } else {
        out.append(spaces).append(c);
        spaces.setLength(0);
      }
      return;
    }
    pending.append(c);
    State state = state(pending);
    if (state == State.COMPLETE) {
      String digits = pending.substring(OPEN.length(), pending.length() - 2);
      ids.add(Long.parseLong(digits));
      pending.setLength(0);
      spaces.setLength(0);
    } else if (state == State.NOT_A_MARKER) {
      // Its first character is plain text; a marker may still start at any later one.
      String held = pending.toString();
      pending.setLength(0);
      out.append(spaces).append(held.charAt(0));
      spaces.setLength(0);
      for (int i = 1; i < held.length(); i++) {
        take(held.charAt(i), out);
      }
    }
  }

  private enum State {
    PREFIX,
    COMPLETE,
    NOT_A_MARKER
  }

  private static State state(CharSequence text) {
    int n = text.length();
    int open = Math.min(n, OPEN.length());
    for (int i = 0; i < open; i++) {
      if (text.charAt(i) != OPEN.charAt(i)) {
        return State.NOT_A_MARKER;
      }
    }
    if (n <= OPEN.length()) {
      return State.PREFIX;
    }
    int i = OPEN.length();
    int digits = 0;
    while (i < n && Character.isDigit(text.charAt(i))) {
      digits++;
      i++;
    }
    if (digits > MAX_DIGITS) {
      return State.NOT_A_MARKER;
    }
    if (i == n) {
      return State.PREFIX;
    }
    if (digits == 0 || text.charAt(i) != ']') {
      return State.NOT_A_MARKER;
    }
    if (i + 1 == n) {
      return State.PREFIX;
    }
    if (text.charAt(i + 1) != ']' || i + 2 != n) {
      return State.NOT_A_MARKER;
    }
    return State.COMPLETE;
  }
}

package com.wokandroll.back.assistant;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Takes the dish markers out of the streamed answer. The model writes {@code [[dish:26]]} after a
 * dish it recommends; the guest sees the text without the marker and a card of dish 26 under it. A
 * marker can arrive split over several pieces, so text that may still become a marker is held back
 * until it is clear; anything that is not a marker is passed on unchanged. The spaces just before a
 * marker go with it, so "Dragon roll [[dish:3]], a classic" reads "Dragon roll, a classic".
 *
 * <p>Small fast models do not always copy the marker exactly: {@code [[id 6]]}, {@code [[dish 6]]},
 * {@code [[dish: 6]]} and {@code [[Dish:6]]} are markers too (the word dish or id in any case, a
 * colon and/or spaces, the id), so the guest never sees one.
 */
final class MarkerFilter {

  private static final List<String> WORDS = List.of("dish", "id");
  private static final int MAX_DIGITS = 6;

  /** At most this many spaces between the word and the id. */
  private static final int MAX_SPACES = 2;

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
      // The word has no digits: the digits of the marker are its id.
      ids.add(Long.parseLong(pending.toString().replaceAll("[^0-9]", "")));
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

  /**
   * Whether the text (it starts with "[") is a whole marker, may still become one, or is not one:
   * "[[", "dish" or "id", ":" and/or spaces, the id, "]]".
   */
  private static State state(CharSequence text) {
    int n = text.length();
    if (n == 1) {
      return State.PREFIX;
    }
    if (text.charAt(1) != '[') {
      return State.NOT_A_MARKER;
    }
    int i = 2;
    if (i == n) {
      return State.PREFIX;
    }
    int word = -1;
    for (String candidate : WORDS) {
      int k = 0;
      while (k < candidate.length()
          && i + k < n
          && Character.toLowerCase(text.charAt(i + k)) == candidate.charAt(k)) {
        k++;
      }
      if (k == candidate.length()) {
        word = i + k;
        break;
      }
      if (i + k == n && k > 0) {
        return State.PREFIX; // the text ends inside the word
      }
    }
    if (word < 0) {
      return State.NOT_A_MARKER;
    }
    i = word;
    if (i == n) {
      return State.PREFIX;
    }
    int separator = i;
    if (text.charAt(i) == ':') {
      i++;
    }
    int spaces = 0;
    while (i < n && text.charAt(i) == ' ' && spaces < MAX_SPACES) {
      spaces++;
      i++;
    }
    if (i == separator) {
      return State.NOT_A_MARKER; // "[[idea]]"
    }
    if (i == n) {
      return State.PREFIX;
    }
    int digits = 0;
    while (i < n && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
      digits++;
      i++;
    }
    if (digits > MAX_DIGITS) {
      return State.NOT_A_MARKER;
    }
    if (i == n) {
      return State.PREFIX;
    }
    if (digits == 0) {
      return State.NOT_A_MARKER;
    }
    int closed = 0;
    while (i < n && closed < 2 && text.charAt(i) == ']') {
      closed++;
      i++;
    }
    if (closed == 0 || i < n) {
      return State.NOT_A_MARKER;
    }
    return closed == 2 ? State.COMPLETE : State.PREFIX;
  }
}

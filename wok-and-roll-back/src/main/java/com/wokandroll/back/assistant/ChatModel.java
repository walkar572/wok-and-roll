package com.wokandroll.back.assistant;

import java.util.List;
import java.util.function.Consumer;

/**
 * The language model behind Panko. The real one is {@link ClaudeChatModel}; without an API key the
 * server uses {@link #unavailable()}, and the tests use a fake.
 */
public interface ChatModel {

  /** One message of the conversation for the model. */
  record Turn(boolean fromGuest, String text) {}

  /** How the answer ended. */
  record Outcome(boolean refused, String stopReason) {}

  /**
   * The model could not answer. {@code code} goes to the browser: {@link #BUSY} when it is worth
   * trying again in a moment (rate limit, overload, network), {@link #ERROR} otherwise.
   */
  final class Failure extends RuntimeException {

    public static final String BUSY = "ASSISTANT_BUSY";
    public static final String ERROR = "ASSISTANT_ERROR";

    private final String code;

    public Failure(String code, String message, Throwable cause) {
      super(message, cause);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  /** false when the server has no API key: the chat then answers 503 ASSISTANT_UNAVAILABLE. */
  boolean available();

  /** The model id, for the status endpoint and the log; null when unavailable. */
  String model();

  /**
   * Streams one answer. {@code instructions} is the stable part of the system prompt (the rules and
   * the menu of the country: the same for every guest of the country, so it can be cached); {@code
   * context} is what changes per request (language, city, local time). Every piece of text goes to
   * {@code onText} as soon as it arrives.
   */
  Outcome stream(String instructions, String context, List<Turn> turns, Consumer<String> onText);

  /** The model of a server without an API key. */
  static ChatModel unavailable() {
    return new ChatModel() {
      @Override
      public boolean available() {
        return false;
      }

      @Override
      public String model() {
        return null;
      }

      @Override
      public Outcome stream(
          String instructions, String context, List<Turn> turns, Consumer<String> onText) {
        throw new IllegalStateException("The assistant has no API key");
      }
    };
  }
}

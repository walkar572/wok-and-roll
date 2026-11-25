package com.wokandroll.back.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Panko on Google Gemini, through the REST API of the Gemini API ({@code streamGenerateContent}
 * with server-sent events) and the HTTP client of the JDK: no extra library. The rules and the menu
 * are the first part of the system instruction, the same for every guest of a country, so the
 * implicit cache of the API can reuse them.
 */
final class GeminiChatModel implements ChatModel {

  static final String DEFAULT_MODEL = "gemini-3.5-flash";
  static final String DEFAULT_THINKING = "minimal";
  static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

  /** Thinking (if any) and a short answer. */
  static final int MAX_OUTPUT_TOKENS = 2048;

  /**
   * The answer ended because the content was blocked: the guest gets the "only Wok & Roll" text.
   */
  private static final Set<String> BLOCKED =
      Set.of(
          "SAFETY",
          "RECITATION",
          "BLOCKLIST",
          "PROHIBITED_CONTENT",
          "SPII",
          "IMAGE_SAFETY",
          "LANGUAGE");

  private final HttpClient http;
  private final ObjectMapper json;
  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final String thinkingLevel;
  private final Duration retryAfter;

  GeminiChatModel(
      HttpClient http,
      ObjectMapper json,
      String baseUrl,
      String apiKey,
      String model,
      String thinkingLevel,
      Duration retryAfter) {
    this.http = http;
    this.json = json;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.thinkingLevel = thinkingLevel;
    this.retryAfter = retryAfter;
  }

  static GeminiChatModel create(ObjectMapper json, String apiKey, String model, String thinking) {
    return new GeminiChatModel(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        json,
        BASE_URL,
        apiKey,
        model,
        thinking,
        Duration.ofSeconds(1));
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public String model() {
    return model;
  }

  @Override
  public Outcome stream(
      String instructions, String context, List<Turn> turns, Consumer<String> onText) {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(baseUrl + "/models/" + model + ":streamGenerateContent?alt=sse"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body(instructions, context, turns)))
            .build();

    HttpResponse<Stream<String>> response = send(request);
    // A model that is busy for a moment gets one more try before the guest sees anything.
    if (isBusy(response.statusCode())) {
      response.body().close();
      pause();
      response = send(request);
    }
    if (response.statusCode() != 200) {
      String error;
      try (Stream<String> lines = response.body()) {
        error = lines.collect(Collectors.joining("\n"));
      }
      throw new Failure(
          isBusy(response.statusCode()) ? Failure.BUSY : Failure.ERROR,
          "Gemini API answered " + response.statusCode() + " " + errorOf(error),
          null);
    }

    String finish = null;
    boolean blocked = false;
    try (Stream<String> lines = response.body()) {
      for (String line : (Iterable<String>) lines::iterator) {
        if (!line.startsWith("data:")) {
          continue;
        }
        JsonNode chunk = json.readTree(line.substring("data:".length()));
        if (chunk.path("promptFeedback").hasNonNull("blockReason")) {
          blocked = true;
        }
        JsonNode candidate = chunk.path("candidates").path(0);
        for (JsonNode part : candidate.path("content").path("parts")) {
          if (part.hasNonNull("text") && !part.path("thought").asBoolean(false)) {
            onText.accept(part.get("text").asText());
          }
        }
        if (candidate.hasNonNull("finishReason")) {
          finish = candidate.get("finishReason").asText();
        }
      }
    } catch (IOException | UncheckedIOException e) {
      throw new Failure(Failure.BUSY, "Gemini API stream broke", e);
    }
    return new Outcome(blocked || BLOCKED.contains(finish), blocked ? "BLOCKED" : finish);
  }

  /** The request: the two parts of the system instruction, the conversation, the limits. */
  String body(String instructions, String context, List<Turn> turns) {
    ObjectNode root = json.createObjectNode();
    ArrayNode system = root.putObject("systemInstruction").putArray("parts");
    system.addObject().put("text", instructions);
    system.addObject().put("text", context);
    ArrayNode contents = root.putArray("contents");
    for (Turn turn : turns) {
      ObjectNode content = contents.addObject();
      content.put("role", turn.fromGuest() ? "user" : "model");
      content.putArray("parts").addObject().put("text", turn.text());
    }
    ObjectNode config = root.putObject("generationConfig");
    config.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
    if (thinkingLevel != null && !thinkingLevel.isBlank()) {
      config.putObject("thinkingConfig").put("thinkingLevel", thinkingLevel);
    }
    try {
      return json.writeValueAsString(root);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private HttpResponse<Stream<String>> send(HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofLines());
    } catch (IOException e) {
      throw new Failure(Failure.BUSY, "Gemini API not reachable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Failure(Failure.ERROR, "Interrupted", e);
    }
  }

  private void pause() {
    try {
      Thread.sleep(retryAfter);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Failure(Failure.ERROR, "Interrupted", e);
    }
  }

  private static boolean isBusy(int status) {
    return status == 429 || status == 500 || status == 503 || status == 504;
  }

  /** The status and the message of an error body of the API, for the log. */
  private String errorOf(String body) {
    try {
      JsonNode error = json.readTree(body).path("error");
      String message = error.path("message").asText("");
      return error.path("status").asText("")
          + ": "
          + (message.length() > 200 ? message.substring(0, 200) : message);
    } catch (IOException e) {
      return "";
    }
  }
}

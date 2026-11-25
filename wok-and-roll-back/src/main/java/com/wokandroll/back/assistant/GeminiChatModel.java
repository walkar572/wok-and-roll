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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Panko on Google Gemini, through the REST API of the Gemini API ({@code streamGenerateContent}
 * with server-sent events) and the HTTP client of the JDK: no extra library. The rules and the menu
 * are the first part of the system instruction, the same for every guest of a country, so the
 * implicit cache of the API can reuse them.
 *
 * <p>Panko has more than one model. A free key gets a small quota per model and per day (20
 * requests in November 2026), and every model has its own: a model that answers 429 with a time to
 * wait rests until then, and the next model of the list answers in the meantime.
 */
final class GeminiChatModel implements ChatModel {

  private static final Logger log = LoggerFactory.getLogger(GeminiChatModel.class);

  /** The first answers best; the others take over, in this order, while it is out of quota. */
  static final List<String> DEFAULT_MODELS =
      List.of("gemini-3.5-flash", "gemini-3-flash-preview", "gemini-3.5-flash-lite");

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
  private final List<String> models;
  private final String thinkingLevel;
  private final Duration retryAfter;
  private final Clock clock;

  /** The models out of their quota, and until when. */
  private final Map<String, Instant> resting = new ConcurrentHashMap<>();

  GeminiChatModel(
      HttpClient http,
      ObjectMapper json,
      String baseUrl,
      String apiKey,
      List<String> models,
      String thinkingLevel,
      Duration retryAfter,
      Clock clock) {
    if (models.isEmpty()) {
      throw new IllegalArgumentException("No Gemini model");
    }
    this.http = http;
    this.json = json;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.models = List.copyOf(models);
    this.thinkingLevel = thinkingLevel;
    this.retryAfter = retryAfter;
    this.clock = clock;
  }

  static GeminiChatModel create(
      ObjectMapper json, String apiKey, List<String> models, String thinking) {
    return new GeminiChatModel(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        json,
        BASE_URL,
        apiKey,
        models,
        thinking,
        Duration.ofSeconds(1),
        Clock.systemUTC());
  }

  @Override
  public boolean available() {
    return true;
  }

  /** The first model of the list: the one Panko uses whenever it has quota left. */
  @Override
  public String model() {
    return models.get(0);
  }

  @Override
  public Outcome stream(
      String instructions, String context, List<Turn> turns, Consumer<String> onText) {
    String body = body(instructions, context, turns);
    List<String> errors = new ArrayList<>();
    boolean busy = false;
    List<String> tries = models.stream().filter(model -> !isResting(model)).toList();
    // A model that is busy for a moment gets one more try before the guest sees anything.
    for (int round = 0; round < 2 && !tries.isEmpty(); round++) {
      if (round > 0) {
        pause();
      }
      List<String> busyForAMoment = new ArrayList<>();
      for (String model : tries) {
        HttpResponse<Stream<String>> response = send(request(model, body));
        int status = response.statusCode();
        if (status == 200) {
          return read(model, response, onText);
        }
        String error;
        try (Stream<String> lines = response.body()) {
          error = lines.collect(Collectors.joining("\n"));
        }
        errors.add(model + " answered " + status + " " + errorOf(error));
        busy |= isBusy(status);
        Duration wait = status == 429 ? retryDelay(error) : null;
        if (wait != null) {
          Instant until = clock.instant().plus(wait);
          resting.put(model, until);
          log.warn("Assistant: Gemini {} is out of its quota until {}", model, until);
        } else if (isBusy(status)) {
          busyForAMoment.add(model);
        }
      }
      tries = busyForAMoment;
    }
    if (errors.isEmpty()) {
      throw new Failure(Failure.BUSY, "Every Gemini model is out of its quota", null);
    }
    throw new Failure(
        busy ? Failure.BUSY : Failure.ERROR, "Gemini API: " + String.join("; ", errors), null);
  }

  private boolean isResting(String model) {
    Instant until = resting.get(model);
    if (until == null) {
      return false;
    }
    if (clock.instant().isBefore(until)) {
      return true;
    }
    resting.remove(model, until);
    return false;
  }

  private HttpRequest request(String model, String body) {
    return HttpRequest.newBuilder(
            URI.create(baseUrl + "/models/" + model + ":streamGenerateContent?alt=sse"))
        .timeout(Duration.ofSeconds(60))
        .header("Content-Type", "application/json")
        .header("x-goog-api-key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private Outcome read(
      String model, HttpResponse<Stream<String>> response, Consumer<String> onText) {
    String finish = null;
    boolean blocked = false;
    try (Stream<String> lines = response.body()) {
      for (String line : (Iterable<String>) lines::iterator) {
        if (!line.startsWith("data:")) {
          continue;
        }
        String data = line.substring("data:".length());
        JsonNode chunk = json.readTree(data);
        // An error in the middle of the answer (the model got overloaded while it wrote).
        if (chunk.has("error")) {
          int code = chunk.path("error").path("code").asInt(0);
          throw new Failure(
              isBusy(code) ? Failure.BUSY : Failure.ERROR,
              "Gemini " + model + " broke off with " + code + " " + errorOf(data),
              null);
        }
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
      throw new Failure(Failure.BUSY, "Gemini " + model + " stream broke", e);
    }
    if (blocked) {
      return new Outcome(true, "BLOCKED");
    }
    // Every complete answer ends with a reason: without one the answer was cut off.
    if (finish == null) {
      throw new Failure(Failure.BUSY, "Gemini " + model + " stopped before the end", null);
    }
    return new Outcome(BLOCKED.contains(finish), finish);
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

  /**
   * How long a model out of its quota waits: the {@code RetryInfo} of a 429 ({@code "57748s"} at
   * the end of a free day, a few seconds for the quota per minute); null when the body has none.
   */
  Duration retryDelay(String body) {
    try {
      for (JsonNode detail : json.readTree(body).path("error").path("details")) {
        String delay = detail.path("retryDelay").asText("");
        if (detail.path("@type").asText("").endsWith("RetryInfo") && delay.endsWith("s")) {
          double seconds = Double.parseDouble(delay.substring(0, delay.length() - 1));
          return seconds > 0 ? Duration.ofMillis(Math.round(seconds * 1000)) : null;
        }
      }
    } catch (IOException | NumberFormatException e) {
      return null;
    }
    return null;
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

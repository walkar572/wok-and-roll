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
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * wait rests until then, and the next model of the list answers in the meantime. A model that is
 * overloaded (500, 503, 504; such an answer can take 13 seconds to come) rests for {@link
 * #OVERLOAD_REST}, so the next guests go straight to the next model instead of waiting for it.
 *
 * <p>Speed comes before everything. The API sends the headers of an answer with its first word.
 * When a model has no first word after {@link Timing#firstWord}, the next model is asked too, and
 * the first that speaks answers the guest; the others are cancelled, and a model that was asked
 * earlier and is still quiet rests like an overloaded one. The guest waits for a first word at most
 * {@link Timing#limit}, then hears that Panko is busy.
 */
final class GeminiChatModel implements ChatModel {

  private static final Logger log = LoggerFactory.getLogger(GeminiChatModel.class);

  /**
   * Only fast models, first words in about a second: Panko gives short, simple answers. The others
   * take over, in this order, while the first is overloaded, slow or out of quota.
   */
  static final List<String> DEFAULT_MODELS =
      List.of("gemini-3.1-flash-lite", "gemini-3.5-flash-lite", "gemini-flash-lite-latest");

  static final String DEFAULT_THINKING = "minimal";
  static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

  /** Thinking (if any) and a short answer. */
  static final int MAX_OUTPUT_TOKENS = 1024;

  /** How long an overloaded or slow model is left alone. */
  static final Duration OVERLOAD_REST = Duration.ofMinutes(2);

  /**
   * How long Panko waits.
   *
   * @param retryAfter the pause before one more try of the models that were busy for a moment
   * @param firstWord how long a model has for its first word before the next model is asked too
   * @param limit how long the guest waits for a first word at most, whatever happens
   */
  record Timing(Duration retryAfter, Duration firstWord, Duration limit) {}

  /**
   * A healthy model says its first word in 0.8 to 1.4 s (sometimes 2.3 s), an overloaded one 503
   * after up to 13 s.
   */
  static final Timing TIMING =
      new Timing(Duration.ofSeconds(1), Duration.ofMillis(1500), Duration.ofSeconds(6));

  /** Until when a model rests, and whether for its quota (no use asking) or for an overload. */
  private record Rest(Instant until, boolean quota) {}

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
  private final Timing timing;
  private final Clock clock;

  /** The models that rest, and until when. */
  private final Map<String, Rest> resting = new ConcurrentHashMap<>();

  GeminiChatModel(
      HttpClient http,
      ObjectMapper json,
      String baseUrl,
      String apiKey,
      List<String> models,
      String thinkingLevel,
      Timing timing,
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
    this.timing = timing;
    this.clock = clock;
  }

  static GeminiChatModel create(
      ObjectMapper json, String apiKey, List<String> models, String thinking) {
    return new GeminiChatModel(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        json,
        BASE_URL,
        apiKey,
        models,
        thinking,
        TIMING,
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
    Attempt attempt =
        new Attempt(
            body(instructions, context, turns), System.nanoTime() + timing.limit().toNanos());
    List<String> tries = models.stream().filter(model -> !isResting(model)).toList();
    if (tries.isEmpty()) {
      // Every model rests: an overloaded one may be back already, one out of its quota is not.
      tries =
          models.stream()
              .filter(
                  model -> {
                    Rest rest = resting.get(model);
                    return rest != null && !rest.quota();
                  })
              .toList();
    }
    // A model that is busy for a moment gets one more try before the guest sees anything, while
    // there is time for it.
    for (int round = 0; round < 2 && !tries.isEmpty(); round++) {
      if (round > 0) {
        if (attempt.left() < timing.retryAfter().plus(timing.firstWord()).toNanos()) {
          break;
        }
        pause();
      }
      List<String> busyForAMoment = new ArrayList<>();
      Outcome outcome = race(attempt, tries, busyForAMoment, onText);
      if (outcome != null) {
        return outcome;
      }
      tries = busyForAMoment;
    }
    if (attempt.errors.isEmpty()) {
      throw new Failure(Failure.BUSY, "Every Gemini model is out of its quota", null);
    }
    throw new Failure(
        attempt.busy ? Failure.BUSY : Failure.ERROR,
        "Gemini API: " + String.join("; ", attempt.errors),
        null);
  }

  /** One question of a guest: the request, until when a first word may come, what went wrong. */
  private static final class Attempt {
    final String body;
    final long deadline;
    final List<String> errors = new ArrayList<>();
    boolean busy;

    Attempt(String body, long deadline) {
      this.body = body;
      this.deadline = deadline;
    }

    /** The time left for a first word, in nanoseconds of {@link System#nanoTime}. */
    long left() {
      return deadline - System.nanoTime();
    }
  }

  /** What came back for the request to one model: its answer, or why there is none. */
  private record Reply(String model, HttpResponse<Stream<String>> response, Throwable error) {}

  /**
   * Asks the models in turn, the next one as soon as the one before fails or has been quiet for
   * {@link Timing#firstWord}; the first that speaks answers the guest. Null when none did.
   */
  private Outcome race(
      Attempt attempt, List<String> tries, List<String> busyForAMoment, Consumer<String> onText) {
    BlockingQueue<Reply> replies = new LinkedBlockingQueue<>();
    AtomicBoolean over = new AtomicBoolean();
    Map<String, CompletableFuture<HttpResponse<Stream<String>>>> running = new LinkedHashMap<>();
    long firstWord = timing.firstWord().toNanos();
    int next = 0;
    long nextStart = System.nanoTime();
    try {
      while (true) {
        long now = System.nanoTime();
        if (next < tries.size() && (running.isEmpty() || now - nextStart >= 0)) {
          long left = attempt.deadline - now;
          if (left >= firstWord) {
            String model = tries.get(next++);
            running.put(model, start(model, attempt.body, Duration.ofNanos(left), replies, over));
            nextStart = now + firstWord;
          } else {
            next = tries.size(); // too late to ask one more model
          }
        }
        if (running.isEmpty()) {
          if (next >= tries.size()) {
            return null;
          }
          continue;
        }
        long until = next < tries.size() ? Math.min(nextStart, attempt.deadline) : attempt.deadline;
        Reply reply = replies.poll(Math.max(0, until - now), TimeUnit.NANOSECONDS);
        if (reply == null) {
          if (attempt.left() <= 0) {
            // Time is up: the models still quiet were too slow, the guest hears that Panko is busy.
            running.keySet().forEach(model -> slow(attempt, model));
            return null;
          }
          continue;
        }
        String model = reply.model();
        running.remove(model);
        nextStart = System.nanoTime(); // one model less: the next may start at once
        if (reply.error() != null) {
          Throwable error =
              reply.error() instanceof CompletionException && reply.error().getCause() != null
                  ? reply.error().getCause()
                  : reply.error();
          if (error instanceof HttpTimeoutException) {
            slow(attempt, model);
          } else {
            attempt.errors.add(model + " not reachable: " + error);
            attempt.busy = true;
          }
          continue;
        }
        HttpResponse<Stream<String>> response = reply.response();
        int status = response.statusCode();
        if (status == 200) {
          resting.remove(model);
          // The models asked before it are still quiet: they were slower, the next guests skip
          // them.
          for (String quiet : running.keySet()) {
            if (tries.indexOf(quiet) < tries.indexOf(model)) {
              restOverloaded(quiet, "slower than " + model);
            }
          }
          try {
            return read(model, response, onText);
          } catch (SilentBreak e) {
            // It broke off before its first word: the guest saw nothing, another model answers.
            attempt.errors.add(e.getMessage());
            attempt.busy = true;
            continue;
          }
        }
        String body;
        try (Stream<String> lines = response.body()) {
          body = lines.collect(Collectors.joining("\n"));
        }
        attempt.errors.add(model + " answered " + status + " " + errorOf(body));
        attempt.busy |= isBusy(status);
        Duration wait = status == 429 ? retryDelay(body) : null;
        if (wait != null) {
          Instant back = clock.instant().plus(wait);
          resting.put(model, new Rest(back, true));
          log.warn("Assistant: Gemini {} is out of its quota until {}", model, back);
        } else if (isBusy(status)) {
          busyForAMoment.add(model);
          if (status != 429) {
            restOverloaded(model, String.valueOf(status));
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Failure(Failure.ERROR, "Interrupted", e);
    } finally {
      // The requests still running lost: cancel them, and close the answers that came anyway.
      over.set(true);
      running.values().forEach(future -> future.cancel(true));
      closeAll(replies);
    }
  }

  /** Sends the request; its reply goes to the queue, or is closed when the race is over. */
  private CompletableFuture<HttpResponse<Stream<String>>> start(
      String model,
      String body,
      Duration timeout,
      BlockingQueue<Reply> replies,
      AtomicBoolean over) {
    CompletableFuture<HttpResponse<Stream<String>>> sent =
        http.sendAsync(request(model, body, timeout), HttpResponse.BodyHandlers.ofLines());
    sent.whenComplete(
        (response, error) -> {
          replies.add(new Reply(model, response, error));
          if (over.get()) {
            closeAll(replies);
          }
        });
    return sent;
  }

  private static void closeAll(BlockingQueue<Reply> replies) {
    for (Reply reply = replies.poll(); reply != null; reply = replies.poll()) {
      if (reply.response() != null) {
        reply.response().body().close();
      }
    }
  }

  /** No first word in time: the guest does not wait for it, and the next guests skip it. */
  private void slow(Attempt attempt, String model) {
    attempt.errors.add(model + " had no first word in time");
    attempt.busy = true;
    restOverloaded(model, "no first word in time");
  }

  /** The model is overloaded or slow: the next guests go straight to the next model for a while. */
  private void restOverloaded(String model, String why) {
    if (resting.put(model, new Rest(clock.instant().plus(OVERLOAD_REST), false)) == null) {
      log.info(
          "Assistant: Gemini {} is overloaded ({}), the next model answers for {} s",
          model,
          why,
          OVERLOAD_REST.toSeconds());
    }
  }

  /** The answer broke off before any text reached the guest, so another model can still answer. */
  private static final class SilentBreak extends RuntimeException {
    SilentBreak(String message) {
      super(message, null, false, false);
    }
  }

  private boolean isResting(String model) {
    Rest rest = resting.get(model);
    if (rest == null) {
      return false;
    }
    if (clock.instant().isBefore(rest.until())) {
      return true;
    }
    resting.remove(model, rest);
    return false;
  }

  /**
   * The timeout is the time to the headers of the answer, and the API sends them with the first
   * word: the time that is left for a first word.
   */
  private HttpRequest request(String model, String body, Duration timeout) {
    return HttpRequest.newBuilder(
            URI.create(baseUrl + "/models/" + model + ":streamGenerateContent?alt=sse"))
        .timeout(timeout)
        .header("Content-Type", "application/json")
        .header("x-goog-api-key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private Outcome read(
      String model, HttpResponse<Stream<String>> response, Consumer<String> onText) {
    String finish = null;
    boolean blocked = false;
    boolean shown = false;
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
          String message = model + " broke off with " + code + " " + errorOf(data);
          if (isBusy(code) && code != 429) {
            restOverloaded(model, String.valueOf(code));
          }
          if (isBusy(code) && !shown) {
            throw new SilentBreak(message);
          }
          throw new Failure(isBusy(code) ? Failure.BUSY : Failure.ERROR, "Gemini " + message, null);
        }
        if (chunk.path("promptFeedback").hasNonNull("blockReason")) {
          blocked = true;
        }
        JsonNode candidate = chunk.path("candidates").path(0);
        for (JsonNode part : candidate.path("content").path("parts")) {
          if (part.hasNonNull("text") && !part.path("thought").asBoolean(false)) {
            String text = part.get("text").asText();
            shown |= !text.isEmpty();
            onText.accept(text);
          }
        }
        if (candidate.hasNonNull("finishReason")) {
          finish = candidate.get("finishReason").asText();
        }
      }
    } catch (IOException | UncheckedIOException e) {
      if (!shown) {
        throw new SilentBreak(model + " stream broke: " + e.getMessage());
      }
      throw new Failure(Failure.BUSY, "Gemini " + model + " stream broke", e);
    }
    if (blocked) {
      return new Outcome(true, "BLOCKED");
    }
    // Every complete answer ends with a reason: without one the answer was cut off.
    if (finish == null) {
      if (!shown) {
        throw new SilentBreak(model + " stopped before its first word");
      }
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

  private void pause() {
    try {
      Thread.sleep(timing.retryAfter());
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

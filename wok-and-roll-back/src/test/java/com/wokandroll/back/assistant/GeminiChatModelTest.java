package com.wokandroll.back.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.wokandroll.back.assistant.ChatModel.Failure;
import com.wokandroll.back.assistant.ChatModel.Outcome;
import com.wokandroll.back.assistant.ChatModel.Turn;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Gemini on a local fake of its REST API: no key and no network. */
class GeminiChatModelTest {

  /** An answer of the fake API, sent after {@code delay} (headers and all, like the real one). */
  private record Reply(int status, String body, Duration delay) {
    Reply(int status, String body) {
      this(status, body, Duration.ZERO);
    }
  }

  /** Time is short in the tests too, but a slow test machine is not a slow model. */
  private static final GeminiChatModel.Timing TIMING =
      new GeminiChatModel.Timing(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(30));

  private final ObjectMapper json = new ObjectMapper();
  private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();
  private final List<String> paths = new CopyOnWriteArrayList<>();
  private final List<String> keys = new CopyOnWriteArrayList<>();
  private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();

  /** How far the clock moves while the fake API answers a request. */
  private volatile Duration requestTakes = Duration.ZERO;

  private ExecutorService threads;
  private HttpServer server;
  private GeminiChatModel model;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          paths.add(exchange.getRequestURI().toString());
          keys.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
          bodies.add(json.readTree(exchange.getRequestBody()));
          Reply reply = replies.isEmpty() ? new Reply(500, "{}") : replies.removeFirst();
          clock.advance(requestTakes);
          try {
            Thread.sleep(reply.delay());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
          byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(reply.status(), bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    // A model slow to answer does not hold up the next one.
    threads = Executors.newCachedThreadPool();
    server.setExecutor(threads);
    server.start();
    model = model("gemini-3.5-flash");
  }

  private GeminiChatModel model(String... names) {
    return model(TIMING, names);
  }

  private GeminiChatModel model(GeminiChatModel.Timing timing, String... names) {
    return new GeminiChatModel(
        HttpClient.newHttpClient(),
        json,
        "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
        "test-key",
        List.of(names),
        "minimal",
        timing,
        clock);
  }

  /** A clock the test moves by hand (and the fake API, while it answers). */
  private static final class TestClock extends Clock {
    private volatile Instant now = Instant.parse("2026-11-25T16:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  private final TestClock clock = new TestClock();

  /** The 429 of a model out of its quota, with the time to wait. */
  private static String quotaExhausted(String retryDelay) {
    return "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"quota\","
        + "\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.QuotaFailure\"},"
        + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\""
        + retryDelay
        + "\"}]}}";
  }

  private static String modelOf(String path) {
    return path.substring("/v1beta/models/".length(), path.indexOf(':'));
  }

  @AfterEach
  void stop() {
    server.stop(0);
    threads.shutdownNow();
  }

  private static String sse(String... chunks) {
    StringBuilder text = new StringBuilder();
    for (String chunk : chunks) {
      text.append("data: ").append(chunk).append("\r\n\r\n");
    }
    return text.toString();
  }

  private static String parts(String finish, String... texts) {
    StringBuilder list = new StringBuilder();
    for (String text : texts) {
      list.append(list.isEmpty() ? "" : ",").append(text);
    }
    return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
        + list
        + "]}"
        + (finish == null ? "" : ",\"finishReason\":\"" + finish + "\"")
        + "}]}";
  }

  private List<String> ask(StringBuilder shown) {
    List<String> pieces = new ArrayList<>();
    model.stream(
        "RULES AND MENU",
        "Answer in Ukrainian.",
        List.of(new Turn(true, "Hi"), new Turn(false, "Hello!"), new Turn(true, "A roll?")),
        piece -> {
          pieces.add(piece);
          shown.append(piece);
        });
    return pieces;
  }

  @Test
  void streamsTheTextAndSendsTheRulesTheContextAndTheConversation() {
    replies.add(
        new Reply(
            200,
            sse(
                parts(null, "{\"text\":\"thinking...\",\"thought\":true}", "{\"text\":\"Try \"}"),
                parts(null, "{\"text\":\"the dragon roll [[dish:3]].\"}"),
                parts("STOP", "{\"text\":\"\"}"))));
    StringBuilder shown = new StringBuilder();

    List<String> pieces = ask(shown);

    assertThat(shown.toString()).isEqualTo("Try the dragon roll [[dish:3]].");
    assertThat(pieces).doesNotContain("thinking...");
    assertThat(paths)
        .containsExactly("/v1beta/models/gemini-3.5-flash:streamGenerateContent?alt=sse");
    assertThat(keys).containsExactly("test-key");
    JsonNode body = bodies.get(0);
    assertThat(body.at("/systemInstruction/parts/0/text").asText()).isEqualTo("RULES AND MENU");
    assertThat(body.at("/systemInstruction/parts/1/text").asText())
        .isEqualTo("Answer in Ukrainian.");
    assertThat(body.path("contents").findValuesAsText("role"))
        .containsExactly("user", "model", "user");
    assertThat(body.at("/contents/2/parts/0/text").asText()).isEqualTo("A roll?");
    assertThat(body.at("/generationConfig/thinkingConfig/thinkingLevel").asText())
        .isEqualTo("minimal");
    assertThat(body.at("/generationConfig/maxOutputTokens").asInt())
        .isEqualTo(GeminiChatModel.MAX_OUTPUT_TOKENS);
  }

  @Test
  void theEndOfTheAnswerSaysWhetherItWasBlocked() {
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Hi!\"}"))));
    replies.add(new Reply(200, sse(parts("SAFETY", "{\"text\":\"\"}"))));
    replies.add(new Reply(200, sse("{\"promptFeedback\":{\"blockReason\":\"OTHER\"}}")));
    replies.add(new Reply(200, sse(parts("MAX_TOKENS", "{\"text\":\"Long\"}"))));

    assertThat(model.stream("r", "c", List.of(new Turn(true, "q")), text -> {}))
        .isEqualTo(new Outcome(false, "STOP"));
    assertThat(model.stream("r", "c", List.of(new Turn(true, "q")), text -> {}))
        .isEqualTo(new Outcome(true, "SAFETY"));
    assertThat(model.stream("r", "c", List.of(new Turn(true, "q")), text -> {}))
        .isEqualTo(new Outcome(true, "BLOCKED"));
    assertThat(model.stream("r", "c", List.of(new Turn(true, "q")), text -> {}))
        .isEqualTo(new Outcome(false, "MAX_TOKENS"));
  }

  @Test
  void aBusyModelGetsOneMoreTry() {
    replies.add(new Reply(503, "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Here I am\"}"))));
    StringBuilder shown = new StringBuilder();

    ask(shown);

    assertThat(shown.toString()).isEqualTo("Here I am");
    assertThat(paths).hasSize(2);
  }

  @Test
  void stillBusyAfterTheSecondTryIsBusyAndOtherErrorsAreErrors() {
    replies.add(new Reply(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}"));
    replies.add(new Reply(503, "{\"error\":{\"status\":\"UNAVAILABLE\",\"message\":\"busy\"}}"));
    replies.add(
        new Reply(400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"bad level\"}}"));

    assertThatThrownBy(() -> ask(new StringBuilder()))
        .isInstanceOfSatisfying(
            Failure.class,
            e -> {
              assertThat(e.code()).isEqualTo(Failure.BUSY);
              assertThat(e.getMessage()).contains("503").contains("UNAVAILABLE: busy");
            });
    assertThatThrownBy(() -> ask(new StringBuilder()))
        .isInstanceOfSatisfying(
            Failure.class,
            e -> {
              assertThat(e.code()).isEqualTo(Failure.ERROR);
              assertThat(e.getMessage()).contains("400").contains("bad level");
            });
    assertThat(paths).hasSize(3);
  }

  @Test
  void aModelOutOfItsQuotaRestsWhileTheNextAnswers() {
    model = model("main", "spare");
    replies.add(new Reply(429, quotaExhausted("3600s")));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"From the spare\"}"))));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Spare again\"}"))));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Main is back\"}"))));

    StringBuilder first = new StringBuilder();
    ask(first);
    StringBuilder second = new StringBuilder();
    ask(second);
    clock.advance(Duration.ofSeconds(3601));
    StringBuilder third = new StringBuilder();
    ask(third);

    assertThat(first.toString()).isEqualTo("From the spare");
    assertThat(second.toString()).isEqualTo("Spare again");
    assertThat(third.toString()).isEqualTo("Main is back");
    // No request is spent on the main model while it rests.
    assertThat(paths.stream().map(GeminiChatModelTest::modelOf))
        .containsExactly("main", "spare", "spare", "main");
    assertThat(model.model()).isEqualTo("main");
  }

  @Test
  void whenEveryModelRestsTheGuestHearsBusyWithoutARequest() {
    model = model("main", "spare");
    replies.add(new Reply(429, quotaExhausted("57748s")));
    replies.add(new Reply(429, quotaExhausted("20.5s")));

    assertThatThrownBy(() -> ask(new StringBuilder()))
        .isInstanceOfSatisfying(
            Failure.class,
            e -> {
              assertThat(e.code()).isEqualTo(Failure.BUSY);
              assertThat(e.getMessage()).contains("main answered 429", "spare answered 429");
            });
    assertThatThrownBy(() -> ask(new StringBuilder()))
        .isInstanceOfSatisfying(Failure.class, e -> assertThat(e.code()).isEqualTo(Failure.BUSY));
    assertThat(paths).hasSize(2);

    // The quota per minute of the spare is back after 21 seconds.
    clock.advance(Duration.ofSeconds(21));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Back\"}"))));
    StringBuilder shown = new StringBuilder();
    ask(shown);
    assertThat(shown.toString()).isEqualTo("Back");
    assertThat(modelOf(paths.get(2))).isEqualTo("spare");
  }

  @Test
  void anOverloadedModelIsLeftAloneForAMomentAndTheNextGuestsGoStraightToTheNext() {
    model = model("main", "spare");
    String overloaded =
        "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\",\"message\":\"demand\"}}";
    replies.add(new Reply(503, overloaded));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Spare\"}"))));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Spare again\"}"))));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Main\"}"))));

    StringBuilder first = new StringBuilder();
    ask(first);
    StringBuilder second = new StringBuilder();
    ask(second);
    clock.advance(GeminiChatModel.OVERLOAD_REST.plusSeconds(1));
    StringBuilder third = new StringBuilder();
    ask(third);

    assertThat(first + " | " + second + " | " + third).isEqualTo("Spare | Spare again | Main");
    assertThat(paths.stream().map(GeminiChatModelTest::modelOf))
        .containsExactly("main", "spare", "spare", "main");
  }

  @Test
  void aModelThatBreaksOffBeforeItsFirstWordHandsOverToTheNext() {
    model = model("main", "spare");
    replies.add(
        new Reply(
            200,
            sse("{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\",\"message\":\"demand\"}}")));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"From the spare\"}"))));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Spare again\"}"))));

    StringBuilder first = new StringBuilder();
    ask(first);
    StringBuilder second = new StringBuilder();
    ask(second);

    assertThat(first.toString()).isEqualTo("From the spare");
    assertThat(second.toString()).isEqualTo("Spare again");
    // The main model broke off overloaded, so it rests: the second guest goes straight to the
    // spare.
    assertThat(paths.stream().map(GeminiChatModelTest::modelOf))
        .containsExactly("main", "spare", "spare");
  }

  @Test
  void whenEveryModelRestsAnOverloadedOneIsStillTriedButOneOutOfQuotaIsNot() {
    model = model("main", "spare");
    replies.add(new Reply(429, quotaExhausted("57748s")));
    replies.add(new Reply(503, "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"));
    replies.add(new Reply(503, "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"));
    assertThatThrownBy(() -> ask(new StringBuilder())).isInstanceOf(Failure.class);

    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Back\"}"))));
    StringBuilder shown = new StringBuilder();
    ask(shown);

    assertThat(shown.toString()).isEqualTo("Back");
    // main (quota), spare (503), spare once more after the pause; then only spare.
    assertThat(paths.stream().map(GeminiChatModelTest::modelOf))
        .containsExactly("main", "spare", "spare", "spare");
  }

  @Test
  void aModelWithoutAFirstWordInTimeIsLeftBehindAndTheNextAnswers() {
    model =
        model(
            new GeminiChatModel.Timing(
                Duration.ZERO, Duration.ofMillis(300), Duration.ofSeconds(30)),
            "main",
            "spare");
    replies.add(
        new Reply(200, sse(parts("STOP", "{\"text\":\"Too late\"}")), Duration.ofSeconds(4)));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"From the spare\"}"))));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Spare again\"}"))));

    long started = System.nanoTime();
    StringBuilder first = new StringBuilder();
    ask(first);
    Duration took = Duration.ofNanos(System.nanoTime() - started);
    StringBuilder second = new StringBuilder();
    ask(second);

    assertThat(first + " | " + second).isEqualTo("From the spare | Spare again");
    // The guest did not wait the 4 seconds of the main model.
    assertThat(took).isLessThan(Duration.ofSeconds(3));
    // The slow model rests: the second guest goes straight to the spare.
    assertThat(paths.stream().map(GeminiChatModelTest::modelOf))
        .containsExactly("main", "spare", "spare");
  }

  @Test
  void theLastModelGetsAllTheTimeThatIsLeft() {
    model =
        model(
            new GeminiChatModel.Timing(
                Duration.ZERO, Duration.ofMillis(300), Duration.ofSeconds(30)),
            "main");
    replies.add(
        new Reply(200, sse(parts("STOP", "{\"text\":\"Slow but here\"}")), Duration.ofMillis(800)));
    StringBuilder shown = new StringBuilder();

    ask(shown);

    // No other model could answer, so it was not left behind after 300 ms.
    assertThat(shown.toString()).isEqualTo("Slow but here");
  }

  @Test
  void afterTheLimitNoModelIsTriedAndTheGuestHearsBusyAtOnce() {
    model =
        model(
            new GeminiChatModel.Timing(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(6)),
            "a",
            "b",
            "c");
    requestTakes = Duration.ofSeconds(3);
    String overloaded = "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}";
    replies.add(new Reply(503, overloaded));
    replies.add(new Reply(503, overloaded));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Too late\"}"))));

    assertThatThrownBy(() -> ask(new StringBuilder()))
        .isInstanceOfSatisfying(Failure.class, e -> assertThat(e.code()).isEqualTo(Failure.BUSY));
    // a at 0 s, b at 3 s; at 6 s no time is left for c, nor for one more try of a and b.
    assertThat(paths.stream().map(GeminiChatModelTest::modelOf)).containsExactly("a", "b");
  }

  @Test
  void anErrorOfOneModelGoesToTheNextAndIsNotTriedAgain() {
    model = model("main", "spare");
    replies.add(new Reply(404, "{\"error\":{\"status\":\"NOT_FOUND\",\"message\":\"no model\"}}"));
    replies.add(new Reply(503, "{\"error\":{\"status\":\"UNAVAILABLE\",\"message\":\"busy\"}}"));
    replies.add(new Reply(200, sse(parts("STOP", "{\"text\":\"Here\"}"))));
    StringBuilder shown = new StringBuilder();

    ask(shown);

    assertThat(shown.toString()).isEqualTo("Here");
    // main (404), spare (503), then only spare once more after the pause.
    assertThat(paths.stream().map(GeminiChatModelTest::modelOf))
        .containsExactly("main", "spare", "spare");
  }

  @Test
  void anAnswerCutOffOrBrokenOffIsBusy() {
    replies.add(new Reply(200, sse(parts(null, "{\"text\":\"- Crunchy\"}"))));
    replies.add(
        new Reply(
            200,
            sse(
                parts(null, "{\"text\":\"Hi\"}"),
                "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\",\"message\":\"overloaded\"}}")));

    assertThatThrownBy(() -> ask(new StringBuilder()))
        .isInstanceOfSatisfying(
            Failure.class,
            e -> {
              assertThat(e.code()).isEqualTo(Failure.BUSY);
              assertThat(e.getMessage()).contains("stopped before the end");
            });
    assertThatThrownBy(() -> ask(new StringBuilder()))
        .isInstanceOfSatisfying(
            Failure.class,
            e -> {
              assertThat(e.code()).isEqualTo(Failure.BUSY);
              assertThat(e.getMessage()).contains("broke off with 503", "overloaded");
            });
  }

  @Test
  void theTimeToWaitComesFromTheRetryInfoOfTheError() {
    assertThat(model.retryDelay(quotaExhausted("57748s"))).isEqualTo(Duration.ofSeconds(57748));
    assertThat(model.retryDelay(quotaExhausted("0.5s"))).isEqualTo(Duration.ofMillis(500));
    assertThat(model.retryDelay(quotaExhausted("soon"))).isNull();
    assertThat(model.retryDelay("{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}")).isNull();
    assertThat(model.retryDelay("not json")).isNull();
  }

  @Test
  void theModelsAreAListSeparatedByCommasElseTheDefaults() {
    List<String> defaults = GeminiChatModel.DEFAULT_MODELS;
    assertThat(AssistantConfig.models(" a , b,,", defaults)).containsExactly("a", "b");
    assertThat(AssistantConfig.models("gemini-3.8-flash", defaults))
        .containsExactly("gemini-3.8-flash");
    assertThat(AssistantConfig.models(" ", defaults)).isEqualTo(defaults);
    assertThat(AssistantConfig.models(null, defaults)).isEqualTo(defaults);
    assertThat(defaults.get(0)).isEqualTo("gemini-3.1-flash-lite");
    // Speed first: only the fast Flash-Lite models, none that thinks for seconds.
    assertThat(defaults).allMatch(name -> name.contains("flash-lite"));
  }

  @Test
  void theProviderIsTheOneAskedForElseTheOneWithAKey() {
    assertThat(AssistantConfig.provider("", "", "g-key")).isEqualTo("gemini");
    assertThat(AssistantConfig.provider("", "a-key", "g-key")).isEqualTo("anthropic");
    assertThat(AssistantConfig.provider(" Gemini ", "a-key", "g-key")).isEqualTo("gemini");
    assertThat(AssistantConfig.provider("", "", "")).isEqualTo("none");
  }
}

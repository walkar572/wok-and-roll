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
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Gemini on a local fake of its REST API: no key and no network. */
class GeminiChatModelTest {

  private record Reply(int status, String body) {}

  private final ObjectMapper json = new ObjectMapper();
  private final Deque<Reply> replies = new ArrayDeque<>();
  private final List<String> paths = new ArrayList<>();
  private final List<String> keys = new ArrayList<>();
  private final List<JsonNode> bodies = new ArrayList<>();
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
          byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(reply.status(), bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
    model =
        new GeminiChatModel(
            HttpClient.newHttpClient(),
            json,
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta",
            "test-key",
            "gemini-3.5-flash",
            "minimal",
            Duration.ZERO);
  }

  @AfterEach
  void stop() {
    server.stop(0);
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
  void theProviderIsTheOneAskedForElseTheOneWithAKey() {
    assertThat(AssistantConfig.provider("", "", "g-key")).isEqualTo("gemini");
    assertThat(AssistantConfig.provider("", "a-key", "g-key")).isEqualTo("anthropic");
    assertThat(AssistantConfig.provider(" Gemini ", "a-key", "g-key")).isEqualTo("gemini");
    assertThat(AssistantConfig.provider("", "", "")).isEqualTo("none");
  }
}

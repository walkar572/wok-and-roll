package com.wokandroll.back.assistant;

import com.wokandroll.back.assistant.AssistantDtos.ChatRequest;
import com.wokandroll.back.assistant.AssistantDtos.StatusResponse;
import com.wokandroll.back.assistant.AssistantService.Answer;
import com.wokandroll.back.assistant.AssistantService.Chat;
import com.wokandroll.back.assistant.AssistantService.TooManyMessagesException;
import com.wokandroll.back.assistant.ChatModel.Failure;
import com.wokandroll.back.common.error.ErrorResponse;
import com.wokandroll.back.common.web.RequestIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Panko, the AI chat (api-contract.md section 3.21). The answer comes as server-sent events: {@code
 * delta} with a piece of text, then {@code done} with the dish cards, or {@code error}.
 */
@RestController
@Tag(name = "Assistant")
public class AssistantController {

  private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

  /** The longest an answer may stream before the server closes it. */
  static final long TIMEOUT_MS = 120_000;

  private final AssistantService service;
  private final ExecutorService executor;

  public AssistantController(
      AssistantService service, @Qualifier("assistantExecutor") ExecutorService executor) {
    this.service = service;
    this.executor = executor;
  }

  @GetMapping("/api/v1/assistant")
  @Operation(summary = "Is the AI chat switched on")
  @ApiResponse(responseCode = "200", description = "enabled is false without an API key")
  public StatusResponse status() {
    return service.status();
  }

  @PostMapping("/api/v1/assistant/chat")
  @Operation(
      summary = "Ask Panko a question",
      description =
          "The whole conversation comes every time; the server keeps nothing. The answer is a"
              + " text/event-stream: event delta {text}, then event done {dishes, refused}, or"
              + " event error {code, message} (ASSISTANT_BUSY, ASSISTANT_ERROR).")
  @ApiResponse(
      responseCode = "200",
      description = "The answer as server-sent events",
      content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE))
  @ApiResponse(
      responseCode = "400",
      description =
          "VALIDATION_ERROR: bad country or language, no messages, over 20 messages, a question"
              + " over 500 characters, the last message not from the user",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "429",
      description = "TOO_MANY_MESSAGES: 20 questions in 10 minutes, see Retry-After",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "ASSISTANT_UNAVAILABLE: the server has no API key",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public SseEmitter chat(
      @Valid @RequestBody ChatRequest request,
      HttpServletRequest http,
      HttpServletResponse response) {
    Chat chat = service.prepare(request, http.getRemoteAddr());
    // A proxy must pass every piece on at once, not wait for the whole answer.
    response.setHeader("X-Accel-Buffering", "no");
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
    SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    String requestId = RequestIdFilter.currentId();
    executor.execute(() -> stream(chat, emitter, requestId));
    return emitter;
  }

  private void stream(Chat chat, SseEmitter emitter, String requestId) {
    if (requestId != null) {
      MDC.put(RequestIdFilter.MDC_KEY, requestId);
    }
    try {
      Answer answer = service.answer(chat, text -> send(emitter, "delta", Map.of("text", text)));
      Map<String, Object> done = new LinkedHashMap<>();
      done.put("dishes", answer.dishes());
      done.put("refused", answer.refused());
      send(emitter, "done", done);
      emitter.complete();
    } catch (GuestLeft e) {
      log.info("Assistant: the guest left before the end of the answer");
    } catch (Failure e) {
      log.warn("Assistant: {} ({})", e.getMessage(), e.code());
      fail(emitter, e.code(), "Panko cannot answer right now");
    } catch (RuntimeException e) {
      log.error("Assistant: unexpected error", e);
      fail(emitter, Failure.ERROR, "Panko cannot answer right now");
    } finally {
      MDC.remove(RequestIdFilter.MDC_KEY);
    }
  }

  private static void fail(SseEmitter emitter, String code, String message) {
    try {
      send(emitter, "error", Map.of("code", code, "message", message));
      emitter.complete();
    } catch (GuestLeft e) {
      // Nobody to tell.
    }
  }

  private static void send(SseEmitter emitter, String event, Object data) {
    try {
      emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
    } catch (IOException | IllegalStateException e) {
      throw new GuestLeft(e);
    }
  }

  /** The browser closed the stream (or it timed out): stop asking the model. */
  private static final class GuestLeft extends RuntimeException {
    GuestLeft(Exception cause) {
      super(cause);
    }
  }

  @ExceptionHandler(TooManyMessagesException.class)
  ResponseEntity<ErrorResponse> tooManyMessages(TooManyMessagesException ex) {
    return ResponseEntity.status(ex.getStatus())
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
  }
}

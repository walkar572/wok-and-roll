package com.wokandroll.back.assistant;

import com.wokandroll.back.common.web.CountryCodes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Bodies of the assistant API (api-contract.md section 3.21, v1.14). */
public final class AssistantDtos {

  /** The languages of the web interface; the assistant answers in the one it is given. */
  public static final String LANGUAGES = "uz|kk|ka|uk|zh|en-GB|en-US";

  /** The longest message a guest may send, in characters. */
  public static final int MAX_USER_TEXT = 500;

  /** The longest earlier answer of the assistant that may come back in the history. */
  public static final int MAX_ASSISTANT_TEXT = 2000;

  /** At most this many messages of the conversation are sent back, the newest last. */
  public static final int MAX_MESSAGES = 20;

  private AssistantDtos() {}

  /** GET /assistant: is the AI chat switched on on this server. */
  public record StatusResponse(
      @Schema(description = "false when the server has no ANTHROPIC_API_KEY") boolean enabled,
      @Schema(example = "claude-opus-5-5", nullable = true) String model) {}

  /** One message of the conversation, as the browser keeps it. */
  public record ChatMessage(
      @Schema(
              example = "user",
              allowableValues = {"user", "assistant"})
          @NotNull
          @Pattern(regexp = "user|assistant", message = "must be user or assistant")
          String role,
      @Schema(example = "Something for two, without meat?")
          @NotBlank
          @Size(max = MAX_ASSISTANT_TEXT)
          String text) {}

  /**
   * POST /assistant/chat. The whole conversation comes every time (the server keeps nothing); the
   * last message is the guest's question.
   */
  public record ChatRequest(
      @Schema(example = "UZB")
          @NotNull
          @Pattern(regexp = CountryCodes.PATTERN, message = CountryCodes.MESSAGE)
          String country,
      @Schema(example = "Tashkent", nullable = true) @Size(max = 80) String city,
      @Schema(example = "en-GB")
          @NotNull
          @Pattern(regexp = LANGUAGES, message = "must be one of uz, kk, ka, uk, zh, en-GB, en-US")
          String language,
      @NotNull @Size(min = 1, max = MAX_MESSAGES) List<@Valid @NotNull ChatMessage> messages) {}
}

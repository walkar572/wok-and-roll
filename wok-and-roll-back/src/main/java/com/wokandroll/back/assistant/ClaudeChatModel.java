package com.wokandroll.back.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaFallbacksParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Panko on Claude, through the official Anthropic Java SDK (the beta Messages endpoint, for the
 * server-side refusal fallback). The answer is streamed; thinking runs as the model decides
 * (adaptive) and is not shown.
 *
 * <p>Cost: the rules and the menu are one system block with a 1-hour cache breakpoint, so every
 * guest of a country reads the same cached prefix; only the language, the city and the time (the
 * second block) and the conversation are new input.
 */
final class ClaudeChatModel implements ChatModel {

  /** Room for adaptive thinking at low effort and a short answer. */
  static final long MAX_TOKENS = 4096;

  private final AnthropicClient client;
  private final String model;
  private final BetaOutputConfig.Effort effort;

  ClaudeChatModel(AnthropicClient client, String model, String effort) {
    this.client = client;
    this.model = model;
    this.effort = BetaOutputConfig.Effort.of(effort);
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public String model() {
    return model;
  }

  /**
   * The models with the server-side refusal fallback ({@code fallbacks: "default"}): when the model
   * declines, the API answers with a fallback model in the same call. Others (Haiku) have none, and
   * the parameter would be refused.
   */
  static boolean hasFallbacks(String model) {
    return model.startsWith("claude-opus-5")
        || model.startsWith("claude-fable-5")
        || model.startsWith("claude-sonnet-5-5");
  }

  @Override
  public Outcome stream(
      String instructions, String context, List<Turn> turns, Consumer<String> onText) {
    MessageCreateParams.Builder params =
        MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .systemOfBetaTextBlockParams(
                List.of(
                    BetaTextBlockParam.builder()
                        .text(instructions)
                        .cacheControl(
                            BetaCacheControlEphemeral.builder()
                                .ttl(BetaCacheControlEphemeral.Ttl.TTL_1H)
                                .build())
                        .build(),
                    BetaTextBlockParam.builder().text(context).build()))
            .outputConfig(BetaOutputConfig.builder().effort(effort).build());
    if (hasFallbacks(model)) {
      params
          .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
          .fallbacks(BetaFallbacksParam.ofDefault());
    }
    for (Turn turn : turns) {
      if (turn.fromGuest()) {
        params.addUserMessage(turn.text());
      } else {
        params.addAssistantMessage(turn.text());
      }
    }

    AtomicReference<BetaStopReason> stop = new AtomicReference<>();
    try (StreamResponse<BetaRawMessageStreamEvent> response =
        client.beta().messages().createStreaming(params.build())) {
      response.stream()
          .forEach(
              event -> {
                event
                    .contentBlockDelta()
                    .flatMap(delta -> delta.delta().text())
                    .ifPresent(text -> onText.accept(text.text()));
                event
                    .messageDelta()
                    .flatMap(delta -> delta.delta().stopReason())
                    .ifPresent(stop::set);
              });
    } catch (AnthropicServiceException e) {
      // 429 rate limit, 529 overloaded, 503: busy for a moment; anything else is a real error.
      int status = e.statusCode();
      String code = status == 429 || status == 503 || status == 529 ? Failure.BUSY : Failure.ERROR;
      throw new Failure(code, "Anthropic API answered " + status, e);
    } catch (AnthropicIoException e) {
      throw new Failure(Failure.BUSY, "Anthropic API not reachable", e);
    } catch (AnthropicException e) {
      throw new Failure(Failure.ERROR, "Anthropic API call failed", e);
    }
    BetaStopReason reason = stop.get();
    return new Outcome(
        BetaStopReason.REFUSAL.equals(reason), reason == null ? null : reason.toString());
  }
}

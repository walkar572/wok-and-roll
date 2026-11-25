package com.wokandroll.back.assistant;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Switches Panko's AI on when the server has an API key: Claude (Anthropic) or Gemini (Google).
 * Settings (environment, never in the repository):
 *
 * <ul>
 *   <li>{@code ANTHROPIC_API_KEY} or {@code GEMINI_API_KEY}: the key of the provider. With both,
 *       Claude is used unless {@code ASSISTANT_PROVIDER} says otherwise.
 *   <li>{@code ASSISTANT_PROVIDER}: {@code anthropic} or {@code gemini} (optional).
 *   <li>{@code ASSISTANT_MODEL}: default {@code claude-opus-5-5}. For Gemini a list separated by
 *       commas, the first answers while it has quota left; default {@code
 *       gemini-3.1-flash-lite,gemini-3.5-flash-lite,gemini-3-flash-preview}.
 *   <li>{@code ASSISTANT_EFFORT}: how much the model thinks before it answers. Claude: effort,
 *       default {@code low}; Gemini: thinking level, default {@code minimal}. Short answers, fast
 *       and cheap.
 * </ul>
 */
@Configuration
class AssistantConfig {

  private static final Logger log = LoggerFactory.getLogger(AssistantConfig.class);

  static final String DEFAULT_MODEL = "claude-opus-5-5";

  @Bean
  ChatModel chatModel(
      @Value("${ASSISTANT_PROVIDER:}") String provider,
      @Value("${ANTHROPIC_API_KEY:}") String anthropicKey,
      @Value("${GEMINI_API_KEY:}") String geminiKey,
      @Value("${ASSISTANT_MODEL:}") String model,
      @Value("${ASSISTANT_EFFORT:}") String effort,
      ObjectMapper json) {
    switch (provider(provider, anthropicKey, geminiKey)) {
      case "anthropic" -> {
        if (anthropicKey.isBlank()) {
          return off("ASSISTANT_PROVIDER is anthropic but there is no ANTHROPIC_API_KEY");
        }
        String name = orDefault(model, DEFAULT_MODEL);
        String level = orDefault(effort, "low");
        log.info("Assistant: AI chat on, Claude {}, effort {}", name, level);
        return new ClaudeChatModel(
            AnthropicOkHttpClient.builder()
                .apiKey(anthropicKey)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .build(),
            name,
            level);
      }
      case "gemini" -> {
        if (geminiKey.isBlank()) {
          return off("ASSISTANT_PROVIDER is gemini but there is no GEMINI_API_KEY");
        }
        List<String> names = models(model, GeminiChatModel.DEFAULT_MODELS);
        String level = orDefault(effort, GeminiChatModel.DEFAULT_THINKING);
        log.info("Assistant: AI chat on, Gemini {}, thinking {}", String.join(" > ", names), level);
        return GeminiChatModel.create(json, geminiKey, names, level);
      }
      case "none" -> {
        return off("no ANTHROPIC_API_KEY or GEMINI_API_KEY");
      }
      default -> {
        return off("unknown ASSISTANT_PROVIDER (anthropic or gemini)");
      }
    }
  }

  /** The provider to use: the one asked for, else the one with a key (Claude first), else none. */
  static String provider(String asked, String anthropicKey, String geminiKey) {
    if (asked != null && !asked.isBlank()) {
      return asked.strip().toLowerCase(Locale.ROOT);
    }
    if (anthropicKey != null && !anthropicKey.isBlank()) {
      return "anthropic";
    }
    if (geminiKey != null && !geminiKey.isBlank()) {
      return "gemini";
    }
    return "none";
  }

  /** A list of models separated by commas, in the order to try them; the defaults when empty. */
  static List<String> models(String setting, List<String> defaults) {
    List<String> names =
        setting == null
            ? List.of()
            : Arrays.stream(setting.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    return names.isEmpty() ? defaults : names;
  }

  private static ChatModel off(String why) {
    log.info("Assistant: {}, the AI chat is off", why);
    return ChatModel.unavailable();
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.strip();
  }

  /** The streams run on virtual threads: a guest waiting for an answer holds no request thread. */
  @Bean(destroyMethod = "shutdown")
  ExecutorService assistantExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}

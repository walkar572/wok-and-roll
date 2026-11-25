package com.wokandroll.back.assistant;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Switches Panko's AI on when the server has an Anthropic API key. Settings (environment): {@code
 * ANTHROPIC_API_KEY} (required for the AI; never in the repository), {@code ASSISTANT_MODEL}
 * (default {@code claude-opus-5-5}), {@code ASSISTANT_EFFORT} (default {@code low}: short chat
 * answers, fast and cheap).
 */
@Configuration
class AssistantConfig {

  private static final Logger log = LoggerFactory.getLogger(AssistantConfig.class);

  static final String DEFAULT_MODEL = "claude-opus-5-5";

  @Bean
  ChatModel chatModel(
      @Value("${ANTHROPIC_API_KEY:}") String apiKey,
      @Value("${ASSISTANT_MODEL:" + DEFAULT_MODEL + "}") String model,
      @Value("${ASSISTANT_EFFORT:low}") String effort) {
    if (apiKey == null || apiKey.isBlank()) {
      log.info("Assistant: no ANTHROPIC_API_KEY, the AI chat is off");
      return ChatModel.unavailable();
    }
    log.info("Assistant: AI chat on, model {}, effort {}", model, effort);
    return new ClaudeChatModel(
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(60))
            .maxRetries(1)
            .build(),
        model,
        effort);
  }

  /** The streams run on virtual threads: a guest waiting for an answer holds no request thread. */
  @Bean(destroyMethod = "shutdown")
  ExecutorService assistantExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}

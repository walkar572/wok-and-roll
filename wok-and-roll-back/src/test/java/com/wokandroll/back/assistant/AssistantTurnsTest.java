package com.wokandroll.back.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wokandroll.back.assistant.AssistantDtos.ChatMessage;
import com.wokandroll.back.assistant.ChatModel.Turn;
import com.wokandroll.back.common.error.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantTurnsTest {

  private static ChatMessage user(String text) {
    return new ChatMessage("user", text);
  }

  private static ChatMessage panko(String text) {
    return new ChatMessage("assistant", text);
  }

  @Test
  void dropsTheGreetingAndJoinsMessagesOfTheSameSide() {
    List<Turn> turns =
        AssistantService.turns(
            List.of(
                panko("Hi, I am Panko!"),
                user("Something spicy?"),
                user("  Without meat  "),
                panko("Try the tofu wok."),
                user("And a drink?")));

    assertThat(turns)
        .containsExactly(
            new Turn(true, "Something spicy?\n\nWithout meat"),
            new Turn(false, "Try the tofu wok."),
            new Turn(true, "And a drink?"));
  }

  @Test
  void theLastMessageMustBeTheGuests() {
    assertThatThrownBy(() -> AssistantService.turns(List.of(user("Hi"), panko("Hello!"))))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
              assertThat(e.getFields()).containsKey("messages");
            });
  }

  @Test
  void aQuestionHasAtMost500Characters() {
    assertThat(AssistantService.turns(List.of(user("a".repeat(500))))).hasSize(1);

    assertThatThrownBy(
            () -> AssistantService.turns(List.of(user("a".repeat(501)), panko("?"), user("ok"))))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getFields()).containsOnlyKeys("messages[0].text"));
    // An earlier answer of Panko may be longer (the DTO allows 2000).
    assertThat(AssistantService.turns(List.of(user("Hi"), panko("b".repeat(1500)), user("ok"))))
        .hasSize(3);
  }
}

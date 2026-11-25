package com.wokandroll.back.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wokandroll.back.catalog.dto.SetItemResponse;
import com.wokandroll.back.catalog.repository.CatalogRepository.DishText;
import com.wokandroll.back.catalog.service.DishFacts.Facts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DishFactsTest {

  @Test
  void aDishIsReadFromItsNameAndIngredients() {
    assertThat(DishFacts.ofDish("Miso mushroom rice", "Shiitake, seasonal greens, miso."))
        .contains(new Facts(false, false, false));
    assertThat(DishFacts.ofDish("Gyoza", "Pan-fried chicken dumplings."))
        .contains(new Facts(true, false, false));
    assertThat(DishFacts.ofDish("California roll", "Crab, avocado, cucumber, sesame."))
        .contains(new Facts(false, true, false));
    assertThat(DishFacts.ofDish("Firecracker udon", "Udon noodles, chicken, chili, sesame."))
        .contains(new Facts(true, false, true));
    assertThat(DishFacts.ofDish("Spicy egg fried rice", "Wok-fried rice, kimchi, fried egg."))
        .hasValueSatisfying(
            facts -> {
              assertThat(facts.vegetarian()).isTrue();
              assertThat(facts.spicy()).isTrue();
            });
    // whole words only: "Hamachi" is fish, not ham; "peel" has no eel
    assertThat(DishFacts.ofDish("Hamachi", "Yellowtail, lemon peel."))
        .contains(new Facts(false, true, false));
  }

  @Test
  void withoutIngredientsNothingIsKnown() {
    assertThat(DishFacts.ofDish("Chef's special", null)).isEmpty();
    assertThat(DishFacts.ofDish("Chef's special", " ")).isEmpty();
  }

  @Test
  void aSetHasWhatItsDishesHave() {
    Map<Long, DishText> dishes =
        Map.of(
            3L, new DishText("California roll", "Crab, avocado, cucumber, sesame."),
            8L, new DishText("Gyoza", "Pan-fried chicken dumplings."),
            9L, new DishText("Edamame", "Steamed soy beans with sea salt."),
            31L, new DishText("Lunch combo", "Edamame only, says the text."),
            40L, new DishText("Chef's special", null),
            41L, new DishText("Mystery set", "Anything."));
    Map<Long, List<SetItemResponse>> sets =
        Map.of(
            31L,
            List.of(
                new SetItemResponse(3, "California roll", 1), new SetItemResponse(8, "Gyoza", 1)),
            41L,
            List.of(new SetItemResponse(9, "Edamame", 1), new SetItemResponse(40, "Special", 1)),
            9L,
            List.of());

    Map<Long, Facts> facts = DishFacts.of(dishes, sets);

    // read from its dishes, not from the words of its own description
    assertThat(facts.get(31L)).isEqualTo(new Facts(true, true, false));
    // one dish of the set is not known, so the set is not known
    assertThat(facts).doesNotContainKey(41L).doesNotContainKey(40L);
    // an empty list of dishes is a dish, read as usual
    assertThat(facts.get(9L)).isEqualTo(new Facts(false, false, false));
    assertThat(DishFacts.ofSet(List.of())).isEqualTo(Optional.empty());
  }
}

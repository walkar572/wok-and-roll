package com.wokandroll.back.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkerFilterTest {

  /** Feeds the pieces one by one, like a stream, and returns everything that was shown. */
  private static String run(MarkerFilter filter, String... pieces) {
    StringBuilder shown = new StringBuilder();
    for (String piece : pieces) {
      shown.append(filter.feed(piece));
    }
    return shown.append(filter.flush()).toString();
  }

  @Test
  void takesTheMarkersOutAndKeepsTheirIds() {
    MarkerFilter filter = new MarkerFilter();

    String shown = run(filter, "Try the Dragon roll [[dish:3]] and a Mango tea [[dish:41]].");

    assertThat(shown).isEqualTo("Try the Dragon roll and a Mango tea.");
    assertThat(filter.ids()).containsExactly(3L, 41L);
  }

  @Test
  void findsAMarkerSplitOverManyPieces() {
    MarkerFilter filter = new MarkerFilter();

    String shown = run(filter, "Philadelphia [", "[di", "sh:", "1", "2]", "], a classic");

    assertThat(shown).isEqualTo("Philadelphia, a classic");
    assertThat(filter.ids()).containsExactly(12L);
  }

  @Test
  void findsAMarkerSentOneCharacterAtATime() {
    MarkerFilter filter = new MarkerFilter();
    String text = "Udon [[dish:5]] is spicy";

    String shown =
        run(filter, text.chars().mapToObj(c -> String.valueOf((char) c)).toArray(String[]::new));

    assertThat(shown).isEqualTo("Udon is spicy");
    assertThat(filter.ids()).containsExactly(5L);
  }

  @Test
  void passesOnTextThatOnlyLooksLikeAMarker() {
    for (String text :
        List.of(
            "[note] two [[brackets]] here",
            "a [[dish:]] marker without an id",
            "a [[dish:12] half marker",
            "a [[dish:1234567]] too long id",
            "a [[dish:12x]] letter",
            "[[[dish:7]] three brackets")) {
      MarkerFilter filter = new MarkerFilter();
      String shown = run(filter, text);
      if (text.startsWith("[[[")) {
        assertThat(shown).isEqualTo("[ three brackets");
        assertThat(filter.ids()).containsExactly(7L);
      } else {
        assertThat(shown).as(text).isEqualTo(text);
        assertThat(filter.ids()).as(text).isEmpty();
      }
    }
  }

  @Test
  void showsWhatIsHeldBackWhenTheAnswerEnds() {
    MarkerFilter filter = new MarkerFilter();

    assertThat(filter.feed("Ends with [[dis")).isEqualTo("Ends with");
    assertThat(filter.flush()).isEqualTo(" [[dis");
    assertThat(filter.ids()).isEmpty();
  }

  @Test
  void keepsSpacesThatNoMarkerFollows() {
    MarkerFilter filter = new MarkerFilter();

    assertThat(run(filter, "two  spaces ", " and [a] bracket "))
        .isEqualTo("two  spaces  and [a] bracket ");
  }

  @Test
  void countsADishOnce() {
    MarkerFilter filter = new MarkerFilter();

    run(filter, "Ramen [[dish:8]]. Yes, ramen [[dish:8]] again, and gyoza [[dish:2]]");

    assertThat(filter.ids()).containsExactly(8L, 2L);
  }

  @Test
  void alsoTakesTheIdFormOfASpareModel() {
    MarkerFilter filter = new MarkerFilter();

    String shown =
        run(filter, "Shrimp rolls [[i", "d:13]] for 269 ₴, or [[dish:5]]; [[idea]] [[id:]]");

    assertThat(shown).isEqualTo("Shrimp rolls for 269 ₴, or; [[idea]] [[id:]]");
    assertThat(filter.ids()).containsExactly(13L, 5L);
  }

  @Test
  void takesOutTheMarkersAFastModelWritesInItsOwnWay() {
    MarkerFilter filter = new MarkerFilter();

    // The answer of the screenshot: "[[id 6]]" with a space, split over pieces.
    String shown =
        run(
            filter,
            "We have the Miso mushroom rice [[id 6]] for 45 000 soʻm, the Vegetable spring rolls [[id",
            " 18]] for 32 000 soʻm, and the Mochi ice cream [[id 20]] for 36 000 soʻm!");

    assertThat(shown)
        .isEqualTo(
            "We have the Miso mushroom rice for 45 000 soʻm, the Vegetable spring rolls for 32 000"
                + " soʻm, and the Mochi ice cream for 36 000 soʻm!");
    assertThat(filter.ids()).containsExactly(6L, 18L, 20L);
  }

  @Test
  void takesEveryFormOfTheMarker() {
    for (String marker :
        List.of(
            "[[dish:7]]",
            "[[dish 7]]",
            "[[dish: 7]]",
            "[[Dish:7]]",
            "[[DISH 7]]",
            "[[id:7]]",
            "[[id 7]]",
            "[[ID: 7]]")) {
      MarkerFilter filter = new MarkerFilter();

      assertThat(run(filter, "Udon " + marker + ", spicy")).as(marker).isEqualTo("Udon, spicy");
      assertThat(filter.ids()).as(marker).containsExactly(7L);
    }
  }

  @Test
  void leavesTextThatOnlyStartsLikeAMarker() {
    for (String text :
        List.of(
            "[Dragon roll] is great",
            "[[identity]] and [[dishes]]",
            "[[dish 7] one bracket short",
            "[dish 7] one bracket",
            "[[dish   7]] three spaces",
            "[[dish:7 ]] a space at the end")) {
      MarkerFilter filter = new MarkerFilter();

      assertThat(run(filter, text)).as(text).isEqualTo(text);
      assertThat(filter.ids()).as(text).isEmpty();
    }
  }
}
